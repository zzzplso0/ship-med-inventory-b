package com.shipmed.shipmed.domain.user.repository;

import com.shipmed.shipmed.domain.user.entity.Role;
import com.shipmed.shipmed.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    boolean existsByRole(Role role);
    boolean existsByLoginId(String loginId);
    Optional<User> findByLoginId(String loginId);
}
