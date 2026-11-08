package com.shipmed.shipmed.domain.stock.repository;

import com.shipmed.shipmed.domain.stock.entity.StockLot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StockLotRepository extends JpaRepository<StockLot, Long> {

}
