package com.shipmed.shipmed.domain.stock.repository;

import com.shipmed.shipmed.domain.stock.entity.StockRequirement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface StockRequirementRepository extends JpaRepository<StockRequirement, Long> {

    StockRequirement findByShipIdAndMedicationId(Long shipId, Long medicationId);

    @Query(value = """
        SELECT r
        FROM StockRequirement r
        JOIN FETCH r.medication
        WHERE r.ship.id = :shipId
    """,
    countQuery = """
        SELECT count(r)
        FROM StockRequirement r
        WHERE r.ship.id = :shipId
    """)
    Page<StockRequirement> findAllByShip(Pageable pageable, @Param("shipId") Long shipId);
}
