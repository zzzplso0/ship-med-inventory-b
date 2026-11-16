package com.shipmed.shipmed.domain.medication.entity;

import com.shipmed.shipmed.global.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "tb_medication")
@Entity
public class Medication extends BaseEntity {

    @Column(name = "name", length = 50, nullable = false)
    private String name;

    @Column(name = "category", length = 30)
    private String category;

    @Column(name = "unit", length = 20, nullable = false)
    private String unit;

    public Medication(String name, String category, String unit) {
        this.name = name;
        this.category = category;
        this.unit = unit;
    }
}
