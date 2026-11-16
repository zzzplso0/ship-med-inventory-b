package com.shipmed.shipmed.domain.user.entity;

import com.shipmed.shipmed.domain.ship.entity.Ship;
import com.shipmed.shipmed.global.base.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "tb_user")
@Entity
public class User extends BaseEntity {

    @Column(name = "login_id", length = 30, nullable = false)
    private String loginId;

    @Column(name = "name", length = 30, nullable = false)
    private String name;

    @Column(name = "password", length = 255, nullable = false)
    private String password;

    @Enumerated(value = EnumType.STRING)
    @Column(name = "role", length = 20, nullable = false)
    private Role role;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ship_id")
    private Ship ship;

    public User(String loginId, String name, String encodedPassword, Role role, Ship ship) {
        this.loginId = loginId;
        this.name = name;
        this.password = encodedPassword;
        this.role = role;
        this.ship = ship;
    }
}
