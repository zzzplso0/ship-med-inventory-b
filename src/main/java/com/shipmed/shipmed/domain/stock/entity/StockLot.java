package com.shipmed.shipmed.domain.stock.entity;

import com.shipmed.shipmed.domain.medication.entity.Medication;
import com.shipmed.shipmed.domain.ship.entity.Ship;
import com.shipmed.shipmed.domain.user.entity.User;
import com.shipmed.shipmed.global.base.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "tb_stock_lot")
@Entity
public class StockLot extends BaseEntity {

    @Column(name = "lot_number", length = 50, nullable = false)
    private String lotNumber;

    @Column(name = "initial_quantity", nullable = false)
    private int initialQuantity;

    @Column(name = "remaining_quantity", nullable = false)
    private int remainingQuantity;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private LotStatus status;

    @Column(name = "discarded_reason", length = 255)
    private String discardedReason;

    @Column(name = "expiry_date", nullable = false)
    private LocalDate expiryDate;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(name = "discarded_at")
    private Instant discardedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "received_by", nullable = false)
    private User receivedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "discarded_by")
    private User discardedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ship_id", nullable = false)
    private Ship ship;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "medication_id", nullable = false)
    private Medication medication;

    public StockLot(String lotNumber, int initialQuantity,
                    LocalDate expiryDate, Instant receivedAt,
                    User receivedBy, Ship ship, Medication medication) {
        this.lotNumber = lotNumber;
        this.initialQuantity = initialQuantity;
        this.remainingQuantity = initialQuantity;
        this.status = LotStatus.ACTIVE;
        this.expiryDate = expiryDate;
        this.receivedAt = receivedAt;
        this.receivedBy = receivedBy;
        this.ship = ship;
        this.medication = medication;
    }
}
