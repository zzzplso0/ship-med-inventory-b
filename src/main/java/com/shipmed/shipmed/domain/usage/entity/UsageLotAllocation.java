package com.shipmed.shipmed.domain.usage.entity;

import com.shipmed.shipmed.domain.stock.entity.StockLot;
import com.shipmed.shipmed.global.base.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "tb_usage_lot_allocation")
@Entity
public class UsageLotAllocation extends BaseEntity {

    @Column(name = "allocated_quantity", nullable = false)
    private int allocatedQuantity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usage_id", nullable = false)
    private UsageRecord usageRecord;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lot_id", nullable = false)
    private StockLot stockLot;

    public UsageLotAllocation(int allocatedQuantity, UsageRecord usageRecord, StockLot stockLot) {
        this.allocatedQuantity = allocatedQuantity;
        this.usageRecord = usageRecord;
        this.stockLot = stockLot;
    }
}
