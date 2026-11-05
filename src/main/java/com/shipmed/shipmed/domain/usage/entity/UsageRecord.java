package com.shipmed.shipmed.domain.usage.entity;

import com.shipmed.shipmed.domain.medication.entity.Medication;
import com.shipmed.shipmed.domain.ship.entity.Ship;
import com.shipmed.shipmed.domain.user.entity.User;
import com.shipmed.shipmed.global.base.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "tb_usage_record")
@Entity
public class UsageRecord extends BaseEntity {

    @Column(name = "total_quantity", nullable = false)
    private int totalQuantity;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private RecordStatus status;

    @Column(name = "cancel_reason", length = 255)
    private String cancelReason;

    @Column(name = "idempotency_key", length = 255, nullable = false)
    private String idempotencyKey;

    @Column(name = "used_at", nullable = false)
    private Instant usedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cancelled_by")
    private User cancelledBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ship_id", nullable = false)
    private Ship ship;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "medication_id", nullable = false)
    private Medication medication;

    public UsageRecord(int totalQuantity, String idempotencyKey, Instant usedAt,
                       User user, Ship ship, Medication medication) {
        this.totalQuantity = totalQuantity;
        this.status = RecordStatus.ACTIVE;
        this.idempotencyKey = idempotencyKey;
        this.usedAt = usedAt;
        this.user = user;
        this.ship = ship;
        this.medication = medication;
    }
}
