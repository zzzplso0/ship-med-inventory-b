package com.shipmed.shipmed.domain.stock.repository;

import com.shipmed.shipmed.domain.stock.entity.LotStatus;
import com.shipmed.shipmed.domain.stock.entity.StockLot;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface StockLotRepository extends JpaRepository<StockLot, Long> {

    // 가용 로트를 잠근다. FEFO 정렬은 서비스에서 한다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(value = """
        SELECT s
        FROM StockLot AS s
        WHERE s.ship.id = :shipId AND
              s.medication.id = :medicationId AND
              s.status = :status AND
              s.expiryDate >= :today AND
              s.remainingQuantity > 0
        ORDER BY s.id
    """)
    List<StockLot> findAvailableForUpdate(@Param("shipId") Long shipId,
                                          @Param("medicationId") Long medicationId,
                                          @Param("status") LotStatus status,
                                          @Param("today") LocalDate today);
}
