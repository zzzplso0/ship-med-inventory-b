package com.shipmed.shipmed.global.init;

import com.shipmed.shipmed.domain.user.entity.Role;
import com.shipmed.shipmed.domain.user.entity.User;
import com.shipmed.shipmed.domain.user.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class InitialAdminRunner implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    private final String loginId;
    private final String name;
    private final String password;

    public InitialAdminRunner(UserRepository userRepository, PasswordEncoder passwordEncoder,
                              @Value("${shipmed.initial-admin.login-id}") String loginId,
                              @Value("${shipmed.initial-admin.name}") String name,
                              @Value("${shipmed.initial-admin.password}") String password) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.loginId = loginId;
        this.name = name;
        this.password = password;
    }

    @Override
    public void run(String... args) throws Exception {
        if (userRepository.existsByRole(Role.ADMIN)) {
            log.info("이미 ADMIN 계정이 존재합니다. ADMIN 초기화 작업을 건너뜁니다.");
            return;
        }

        userRepository.save(new User(
                loginId, name, passwordEncoder.encode(password), Role.ADMIN, null
        ));

        log.info("첫 관리자 계정을 생성했습니다.");
    }
}
