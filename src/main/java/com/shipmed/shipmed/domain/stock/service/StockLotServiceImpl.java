package com.shipmed.shipmed.domain.stock.service;

import com.shipmed.shipmed.domain.medication.entity.Medication;
import com.shipmed.shipmed.domain.medication.exception.MedicationNotFoundException;
import com.shipmed.shipmed.domain.medication.repository.MedicationRepository;
import com.shipmed.shipmed.domain.ship.entity.Ship;
import com.shipmed.shipmed.domain.ship.repository.ShipRepository;
import com.shipmed.shipmed.domain.stock.dto.StockLotMapper;
import com.shipmed.shipmed.domain.stock.dto.request.StockLotCreateRequest;
import com.shipmed.shipmed.domain.stock.dto.response.StockLotDto;
import com.shipmed.shipmed.domain.stock.entity.StockLot;
import com.shipmed.shipmed.domain.stock.exception.StockLotDuplicateException;
import com.shipmed.shipmed.domain.stock.exception.StockLotExpiredOnArrivalException;
import com.shipmed.shipmed.domain.stock.exception.StockLotReceivedAtInFutureException;
import com.shipmed.shipmed.domain.stock.repository.StockLotRepository;
import com.shipmed.shipmed.domain.user.entity.User;
import com.shipmed.shipmed.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;

@Transactional(readOnly = true)
@RequiredArgsConstructor
@Service
public class StockLotServiceImpl implements StockLotService {

    private static final String UQ_LOT = "uq_lot";

    private final StockLotRepository stockLotRepository;
    private final ShipRepository shipRepository;
    private final MedicationRepository medicationRepository;
    private final UserRepository userRepository;
    private final StockLotMapper stockLotMapper;
    private final Clock clock;

    @Transactional
    @Override
    public StockLotDto create(Long shipId, StockLotCreateRequest stockLotCreateRequest, Long userId) {
        Medication medication = medicationRepository.findById(stockLotCreateRequest.medicationId())
                .orElseThrow(MedicationNotFoundException::new);

        LocalDate today = LocalDate.now(clock);

        if (stockLotCreateRequest.expiryDate().isBefore(today)) {
            throw new StockLotExpiredOnArrivalException();
        }

        Instant now = Instant.now(clock);

        if (stockLotCreateRequest.receivedAt().isAfter(now)) {
            throw new StockLotReceivedAtInFutureException();
        }

        Ship ship = shipRepository.getReferenceById(shipId);
        User user = userRepository.getReferenceById(userId);

        StockLot stockLot = new StockLot(
                stockLotCreateRequest.lotNumber(),
                stockLotCreateRequest.quantity(),
                stockLotCreateRequest.expiryDate(),
                stockLotCreateRequest.receivedAt(),
                user,
                ship,
                medication
        );

        try {
            stockLotRepository.saveAndFlush(stockLot);
        } catch (DataIntegrityViolationException e) {
            // 같은 배·약품에 같은 로트 번호. 다른 제약 위반은 409로 바꾸지 않는다.
            if (e.getCause() instanceof ConstraintViolationException cause
                    && UQ_LOT.equals(cause.getConstraintName())) {
                throw new StockLotDuplicateException();
            }
            throw e;
        }

        return stockLotMapper.toDto(stockLot);
    }
}
