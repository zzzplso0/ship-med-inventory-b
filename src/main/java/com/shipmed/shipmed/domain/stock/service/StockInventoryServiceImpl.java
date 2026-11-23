package com.shipmed.shipmed.domain.stock.service;

import com.shipmed.shipmed.domain.ship.exception.ShipNotFoundException;
import com.shipmed.shipmed.domain.ship.repository.ShipRepository;
import com.shipmed.shipmed.domain.stock.dto.response.StockExpiringLotDto;
import com.shipmed.shipmed.domain.stock.dto.response.StockExpiringLotRow;
import com.shipmed.shipmed.domain.stock.dto.response.StockInventoryDto;
import com.shipmed.shipmed.domain.stock.dto.response.StockInventoryRow;
import com.shipmed.shipmed.domain.stock.repository.StockInventoryQueryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Transactional(readOnly = true)
@RequiredArgsConstructor
@Service
public class StockInventoryServiceImpl implements StockInventoryService {

    private static final int EXPIRING_SOON_DAYS = 30;

    private final StockInventoryQueryMapper inventoryQueryMapper;
    private final ShipRepository shipRepository;
    private final Clock clock;

    // 두 조회가 같은 스냅샷을 보도록 REPEATABLE READ. READ COMMITTED는 문장마다 스냅샷이 달라 수량이 어긋날 수 있다.
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    @Override
    public List<StockInventoryDto> getInventory(Long shipId) {
        boolean exists = shipRepository.existsById(shipId);

        if (!exists) {
            throw new ShipNotFoundException();
        }

        LocalDate today = LocalDate.now(clock);
        LocalDate expiringUntil = today.plusDays(EXPIRING_SOON_DAYS);

        List<StockInventoryRow> inventoryRows = inventoryQueryMapper.selectInventory(shipId, today);
        List<StockExpiringLotRow> expiringLotRows = inventoryQueryMapper.selectExpiringSoonLots(shipId, today, expiringUntil);
        List<StockExpiringLotRow> expiredLotRows = inventoryQueryMapper.selectExpiredLots(shipId, today);

        return createResult(inventoryRows, expiringLotRows, expiredLotRows);
    }

    private List<StockInventoryDto> createResult(List<StockInventoryRow> inventoryRows,
                                                 List<StockExpiringLotRow> expiringLotRows,
                                                 List<StockExpiringLotRow> expiredLotRows)
    {
        List<StockInventoryDto> result = new ArrayList<>(inventoryRows.size());

        for (StockInventoryRow inventoryRow : inventoryRows) {
            List<StockExpiringLotDto> expiringSoon = lotsOf(inventoryRow.medicationId(), expiringLotRows);
            List<StockExpiringLotDto> expired = lotsOf(inventoryRow.medicationId(), expiredLotRows);

            result.add(new StockInventoryDto(
                    inventoryRow.medicationId(), inventoryRow.medicationName(), inventoryRow.unit(),
                    inventoryRow.available(), inventoryRow.requirementSet(), inventoryRow.minQuantity(),
                    inventoryRow.shortage(), expiringSoon, expired));
        }

        return result;
    }

    private List<StockExpiringLotDto> lotsOf(Long medicationId, List<StockExpiringLotRow> lotRows) {
        List<StockExpiringLotDto> lots = new ArrayList<>();

        for (StockExpiringLotRow lot : lotRows) {
            if (medicationId.equals(lot.medicationId())) {
                lots.add(new StockExpiringLotDto(lot.lotId(), lot.quantity(), lot.expiryDate()));
            }
        }

        return lots;
    }
}
