package com.shipmed.shipmed.domain.ship.entity;

import com.shipmed.shipmed.global.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "tb_ship")
@Entity
public class Ship extends BaseEntity {

    @Column(name = "name", length = 50, nullable = false)
    private String name;

    public Ship(String name) {
        this.name = name;
    }
}
