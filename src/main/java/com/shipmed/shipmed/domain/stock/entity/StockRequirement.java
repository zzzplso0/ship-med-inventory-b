package com.shipmed.shipmed.domain.stock.entity;

import com.shipmed.shipmed.domain.medication.entity.Medication;
import com.shipmed.shipmed.domain.ship.entity.Ship;
import com.shipmed.shipmed.global.base.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "tb_stock_requirement")
@Entity
public class StockRequirement extends BaseEntity {

    @Column(name = "min_quantity", nullable = false)
    private int minQuantity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ship_id", nullable = false)
    private Ship ship;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "medication_id", nullable = false)
    private Medication medication;

    public StockRequirement(int minQuantity, Ship ship, Medication medication) {
        this.minQuantity = minQuantity;
        this.ship = ship;
        this.medication = medication;
    }

    public void changeMinQuantity(int newMinQuantity) {
        this.minQuantity = newMinQuantity;
    }
}
