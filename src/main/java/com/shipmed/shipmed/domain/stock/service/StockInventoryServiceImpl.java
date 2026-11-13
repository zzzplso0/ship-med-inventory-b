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

        return createResult(inventoryRows, expiringLotRows);
    }

    private List<StockInventoryDto> createResult(List<StockInventoryRow> inventoryRows, List<StockExpiringLotRow> expiringLotRows) {
        List<StockInventoryDto> result = new ArrayList<>(inventoryRows.size());

        for (StockInventoryRow inventoryRow : inventoryRows) {
            List<StockExpiringLotDto> expiringSoon = new ArrayList<>();

            for (StockExpiringLotRow lot : expiringLotRows) {
                if (inventoryRow.medicationId().equals(lot.medicationId())) {
                    expiringSoon.add(new StockExpiringLotDto(lot.lotId(), lot.quantity(), lot.expiryDate()));
                }
            }

            result.add(new StockInventoryDto(
                    inventoryRow.medicationId(), inventoryRow.medicationName(), inventoryRow.unit(),
                    inventoryRow.available(), inventoryRow.requirementSet(), inventoryRow.minQuantity(),
                    inventoryRow.shortage(), expiringSoon));
        }
        return result;
    }
}
