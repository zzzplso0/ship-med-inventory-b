package com.shipmed.shipmed.global.security.principal;

import com.shipmed.shipmed.domain.user.entity.Role;
import com.shipmed.shipmed.domain.user.entity.User;
import lombok.Getter;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

@Getter
public class CustomUserDetails implements UserDetails, CredentialsContainer {

    private final Long userId;
    private final String loginId;
    private String password;
    private final Role role;
    private final Long shipId;

    private CustomUserDetails(Long userId, String loginId, String password, Role role, Long shipId) {
        this.userId = userId;
        this.loginId = loginId;
        this.password = password;
        this.role = role;
        this.shipId = shipId;
    }

    public static CustomUserDetails from(User user) {
        Long shipId = user.getShip() != null ? user.getShip().getId() : null;

        return new CustomUserDetails(user.getId(), user.getLoginId(), user.getPassword(), user.getRole(), shipId);
    }

    // 인증이 끝나면 세션에 비밀번호 해시를 남기지 않는다.
    @Override
    public void eraseCredentials() {
        this.password = null;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(
                new SimpleGrantedAuthority("ROLE_" + role.name())
        );
    }

    @Override
    public String getUsername() {
        return loginId;
    }

    @Override
    public boolean isAccountNonExpired() {
        return UserDetails.super.isAccountNonExpired();
    }

    @Override
    public boolean isAccountNonLocked() {
        return UserDetails.super.isAccountNonLocked();
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return UserDetails.super.isCredentialsNonExpired();
    }

    @Override
    public boolean isEnabled() {
        return UserDetails.super.isEnabled();
    }
}
