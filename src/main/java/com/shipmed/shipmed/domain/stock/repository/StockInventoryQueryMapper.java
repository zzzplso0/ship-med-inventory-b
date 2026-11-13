package com.shipmed.shipmed.domain.stock.repository;

import com.shipmed.shipmed.domain.stock.dto.response.StockExpiringLotRow;
import com.shipmed.shipmed.domain.stock.dto.response.StockInventoryRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface StockInventoryQueryMapper {

    List<StockInventoryRow> selectInventory(@Param("shipId") Long shipId, @Param("today") LocalDate today);

    List<StockExpiringLotRow> selectExpiringSoonLots(@Param("shipId") Long shipId, @Param("today") LocalDate today,
                                                     @Param("expiringUntil") LocalDate expiringUntil);
}
