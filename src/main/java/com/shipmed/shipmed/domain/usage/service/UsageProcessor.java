package com.shipmed.shipmed.domain.usage.service;

import com.shipmed.shipmed.domain.medication.entity.Medication;
import com.shipmed.shipmed.domain.medication.exception.MedicationNotFoundException;
import com.shipmed.shipmed.domain.medication.repository.MedicationRepository;
import com.shipmed.shipmed.domain.ship.entity.Ship;
import com.shipmed.shipmed.domain.ship.repository.ShipRepository;
import com.shipmed.shipmed.domain.stock.entity.LotStatus;
import com.shipmed.shipmed.domain.stock.entity.StockLot;
import com.shipmed.shipmed.domain.stock.repository.StockLotRepository;
import com.shipmed.shipmed.domain.usage.dto.UsageMapper;
import com.shipmed.shipmed.domain.usage.dto.request.UsageCreateRequest;
import com.shipmed.shipmed.domain.usage.dto.response.UsageResult;
import com.shipmed.shipmed.domain.usage.entity.UsageLotAllocation;
import com.shipmed.shipmed.domain.usage.entity.UsageRecord;
import com.shipmed.shipmed.domain.usage.exception.UsageIdempotencyKeyReusedException;
import com.shipmed.shipmed.domain.usage.exception.UsageInsufficientStockException;
import com.shipmed.shipmed.domain.usage.repository.UsageLotAllocationRepository;
import com.shipmed.shipmed.domain.usage.repository.UsageRecordRepository;
import com.shipmed.shipmed.domain.user.entity.User;
import com.shipmed.shipmed.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Service
public class UsageProcessor {

    private final MedicationRepository medicationRepository;
    private final StockLotRepository stockLotRepository;
    private final UsageRecordRepository usageRecordRepository;
    private final UsageLotAllocationRepository usageLotAllocationRepository;
    private final ShipRepository shipRepository;
    private final UserRepository userRepository;

    private final Clock clock;
    private final UsageMapper usageMapper;

    private static final Comparator<StockLot> FEFO_ORDER =
            Comparator.comparing(StockLot::getExpiryDate)
                    .thenComparing(StockLot::getReceivedAt)
                    .thenComparing(StockLot::getId);

    /**
     * FEFO 차감으로 사용을 기록한다.
     *
     * <p>같은 키로 처리한 요청이 있으면 차감하지 않고 그 결과를 돌려준다. 멱등 체크는 두 번 한다.
     * 잠금 전 체크는 재전송 요청이 로트 잠금을 잡지 않게 하고, 잠금 뒤 체크는 잠금을 기다리는 동안
     * 같은 키 요청이 먼저 커밋한 경우를 잡는다. 둘 다 통과한 동시 요청은 저장 때 UNIQUE 위반이 나고,
     * 호출한 쪽이 이를 받아 {@link #replay}로 다시 조회한다.
     *
     * @param shipId             사용할 선박 id
     * @param userId             기록하는 의무사관 id
     * @param key                Idempotency-Key 헤더 값
     * @param usageCreateRequest 약품 id와 사용 수량
     * @return 사용 결과. 새로 기록했으면 {@code created=true}, 기존 결과를 돌려줬으면 {@code false}
     * @throws MedicationNotFoundException          약품이 없을 때 (멱등 판정보다 먼저)
     * @throws UsageIdempotencyKeyReusedException   같은 키로 선박·약품·수량이 다른 요청을 보냈을 때
     * @throws UsageInsufficientStockException      가용 재고가 요청 수량보다 적을 때
     * @throws org.springframework.dao.DataIntegrityViolationException 같은 키 동시 요청이 먼저 저장됐을 때
     */
    @Transactional
    public UsageResult record(Long shipId, Long userId, String key, UsageCreateRequest usageCreateRequest) {
        // 1. 약품 확인. 멱등 체크보다 먼저 확인한다.
        Medication medication = medicationRepository.findById(usageCreateRequest.medicationId())
                .orElseThrow(MedicationNotFoundException::new);

        // 2. 멱등 체크. 같은 키로 처리한 요청이 있으면 그 결과를 반환한다.
        Optional<UsageResult> existingResult = findExistingResult(shipId, userId, key, usageCreateRequest);

        if (existingResult.isPresent()) {
            return existingResult.get();
        }

        // 3. 가용 로트 잠금. 데드락을 막기 위해 id 순으로 잠근다.
        List<StockLot> availableForUpdate = stockLotRepository.findAvailableForUpdate(shipId, usageCreateRequest.medicationId(), LotStatus.ACTIVE, LocalDate.now(clock));

        // 4. 잠금 뒤 멱등 재확인. 잠금을 기다리는 동안 같은 키 요청이 먼저 커밋했을 수 있다.
        Optional<UsageResult> resultAfterLock = findExistingResult(shipId, userId, key, usageCreateRequest);

        if (resultAfterLock.isPresent()) {
            return resultAfterLock.get();
        }

        // 5. FEFO 정렬. 유효기한 → 입고 시각 → id 순으로 차감한다.
        List<StockLot> lots = availableForUpdate.stream()
                .sorted(FEFO_ORDER)
                .toList();

        // 6. 재고 검사. 가용 합계가 부족하면 전부 거부한다.
        int available = lots.stream()
                .mapToInt(StockLot::getRemainingQuantity)
                .sum();

        Integer requested = usageCreateRequest.quantity();
        if (available < requested) {
            throw new UsageInsufficientStockException(requested, available);
        }

        // 7. 차감과 분배. 앞 로트부터 필요한 만큼 빼고 로트별 분배를 기록한다.
        User user = userRepository.getReferenceById(userId);
        Ship ship = shipRepository.getReferenceById(shipId);

        UsageRecord usageRecord = new UsageRecord(requested, key, Instant.now(clock), user, ship, medication);

        List<UsageLotAllocation> allocations = new ArrayList<>();
        int remaining = requested;

        for (StockLot lot : lots) {
            if (remaining == 0) break;

            int deducted = Math.min(lot.getRemainingQuantity(), remaining);
            lot.subtractQuantity(deducted);
            allocations.add(new UsageLotAllocation(deducted, usageRecord, lot));
            remaining -= deducted;
        }

        // 8. 저장. 같은 키 동시 요청의 UNIQUE 위반을 여기서 바로 일으키려고 flush한다.
        usageRecordRepository.saveAndFlush(usageRecord);
        usageLotAllocationRepository.saveAll(allocations);

        return new UsageResult(usageMapper.toDto(usageRecord, allocations), true);
    }

    @Transactional(readOnly = true)
    public UsageResult replay(Long shipId, Long userId, String key, UsageCreateRequest usageCreateRequest) {
        return findExistingResult(shipId, userId, key,usageCreateRequest).orElseThrow();
    }


    // 이 사용자가 이 키로 이미 처리한 요청이 있나? 있으면 그때 결과를 응답 형태로 만들어 준다.
    private Optional<UsageResult> findExistingResult(Long shipId, Long userId, String key, UsageCreateRequest usageCreateRequest) {
        Optional<UsageRecord> found = usageRecordRepository.findByUserIdAndIdempotencyKey(userId, key);

        if (found.isEmpty()) {
            return Optional.empty();
        }

        UsageRecord existing = found.get();

        boolean sameRequest = existing.getShip().getId().equals(shipId)
                && existing.getMedication().getId().equals(usageCreateRequest.medicationId())
                && existing.getTotalQuantity() == usageCreateRequest.quantity();

        if (!sameRequest) {
            throw new UsageIdempotencyKeyReusedException();
        }

        List<UsageLotAllocation> allocations = usageLotAllocationRepository.findByUsageRecordIdOrderById(existing.getId());

        return Optional.of(new UsageResult(usageMapper.toDto(existing, allocations), false));
    }
}
