package com.shipmed.shipmed.domain.ship.repository;

import com.shipmed.shipmed.domain.ship.entity.Ship;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ShipRepository extends JpaRepository<Ship, Long> {

}
