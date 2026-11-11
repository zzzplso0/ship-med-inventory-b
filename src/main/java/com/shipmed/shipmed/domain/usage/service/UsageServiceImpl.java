package com.shipmed.shipmed.domain.usage.service;

import com.shipmed.shipmed.domain.stock.repository.StockLotRepository;
import com.shipmed.shipmed.domain.usage.dto.UsageMapper;
import com.shipmed.shipmed.domain.usage.dto.request.UsageCancelRequest;
import com.shipmed.shipmed.domain.usage.dto.request.UsageCreateRequest;
import com.shipmed.shipmed.domain.usage.dto.response.UsageCancelDto;
import com.shipmed.shipmed.domain.usage.dto.response.UsageResult;
import com.shipmed.shipmed.domain.usage.entity.RecordStatus;
import com.shipmed.shipmed.domain.usage.entity.UsageLotAllocation;
import com.shipmed.shipmed.domain.usage.entity.UsageRecord;
import com.shipmed.shipmed.domain.usage.exception.UsageNotFoundException;
import com.shipmed.shipmed.domain.usage.repository.UsageLotAllocationRepository;
import com.shipmed.shipmed.domain.usage.repository.UsageRecordRepository;
import com.shipmed.shipmed.domain.user.entity.User;
import com.shipmed.shipmed.domain.user.repository.UserRepository;
import com.shipmed.shipmed.global.exception.BusinessException;
import com.shipmed.shipmed.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@RequiredArgsConstructor
@Service
public class UsageServiceImpl implements UsageService {

    private static final String UQ_USAGE_IDEMPOTENCY = "uq_usage_idempotency";

    private final UsageProcessor usageProcessor;
    private final UsageRecordRepository usageRecordRepository;
    private final UsageLotAllocationRepository usageLotAllocationRepository;
    private final UserRepository userRepository;
    private final StockLotRepository stockLotRepository;

    private final Clock clock;
    private final UsageMapper usageMapper;

    @Override
    public UsageResult create(Long shipId, Long userId, String idempotencyKey, UsageCreateRequest usageCreateRequest) {
        try {
            return usageProcessor.record(shipId, userId, idempotencyKey, usageCreateRequest);
        } catch (DataIntegrityViolationException e) {
            if (e.getCause() instanceof ConstraintViolationException cause
                    && UQ_USAGE_IDEMPOTENCY.equals(cause.getConstraintName())) {
                return usageProcessor.replay(shipId, userId, idempotencyKey, usageCreateRequest);
            }

            throw e;
        }
    }

    /**
     * 본인이 기록한 사용을 취소하고, 분배내역대로 원래 로트에 복원한다.
     *
     * <p>사용기록을 먼저 잠가 이중 취소를 막는다. 동시에 들어온 두 번째 취소는 잠금을 기다린 뒤
     * {@code CANCELLED}를 보고 복원 없이 기존 결과를 돌려받는다. 복원할 로트는 사용 경로와 같이
     * id 순으로 잠근다. 만료·폐기된 로트도 그대로 복원하고, 가용 여부는 조회 시점에 판정한다.
     *
     * @param usageId            취소할 사용기록 id
     * @param userId             취소하는 의무사관 id
     * @param usageCancelRequest 취소 사유
     * @return 취소 결과. 이미 취소된 기록이면 처음 취소한 내용을 그대로 돌려준다
     * @throws UsageNotFoundException 사용기록이 없을 때
     * @throws BusinessException      본인이 기록하지 않은 사용일 때 ({@code ACCESS_DENIED})
     */
    @Transactional
    @Override
    public UsageCancelDto cancel(Long usageId, Long userId, UsageCancelRequest usageCancelRequest) {
        // 1. 사용기록 잠금. 동시에 들어온 이중 취소는 여기서 기다린다.
        UsageRecord usageRecord = usageRecordRepository.findByIdForUpdate(usageId)
                .orElseThrow(UsageNotFoundException::new);

        // 2. 본인 확인. 대상을 조회해야 판정할 수 있으므로 404 다음에 한다.
        if (!usageRecord.getUser().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }

        // 3. 이중 취소 확인. 이미 취소됐으면 복원 없이 기존 결과를 반환한다.
        List<UsageLotAllocation> allocations = usageLotAllocationRepository.findByUsageRecordIdOrderById(usageId);

        if (usageRecord.getStatus() == RecordStatus.CANCELLED) {
            return usageMapper.toCancelDto(usageRecord, allocations);
        }

        // 4. 복원할 로트 잠금. 사용 경로와 같이 id 순으로 잠가 데드락을 막는다.
        //    반환값은 쓰지 않는다. 잠근 엔티티가 영속성 컨텍스트에 올라가 아래 getStockLot()이 같은 인스턴스를 쓴다.
        //    잠그기 전에는 로트 값을 읽지 않도록 id만 꺼낸다.
        List<Long> lotIds = allocations.stream()
                .map(allocation -> allocation.getStockLot().getId())
                .toList();

        stockLotRepository.findAllByIdForUpdate(lotIds);

        // 5. 복원. 분배내역의 차감량만큼 원래 로트에 되돌린다.
        for (UsageLotAllocation allocation : allocations) {
            allocation.getStockLot().addQuantity(allocation.getAllocatedQuantity());
        }

        // 6. 상태 전이. 기록과 분배내역은 삭제하지 않는다.
        User user = userRepository.getReferenceById(userId);
        usageRecord.cancel(user, usageCancelRequest.reason(), Instant.now(clock));

        return usageMapper.toCancelDto(usageRecord, allocations);
    }
}
