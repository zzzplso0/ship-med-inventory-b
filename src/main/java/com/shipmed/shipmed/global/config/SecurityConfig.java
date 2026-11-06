package com.shipmed.shipmed.global.config;

import com.shipmed.shipmed.global.security.handler.CustomAccessDeniedHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;

@EnableWebSecurity
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   AuthenticationEntryPoint entryPoint,
                                                   CustomAccessDeniedHandler customAccessDeniedHandler) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()

                    // 관리자.
                    .requestMatchers(HttpMethod.POST, "/api/users").hasRole("ADMIN")
                    .requestMatchers(HttpMethod.GET, "/api/users").hasRole("ADMIN")
                    .requestMatchers(HttpMethod.POST, "/api/ships").hasRole("ADMIN")
                    .requestMatchers(HttpMethod.POST, "/api/medications").hasRole("ADMIN")
                    .requestMatchers(HttpMethod.PUT, "/api/ships/*/requirements/*").hasRole("ADMIN")

                    // 의무사관.
                    .requestMatchers(HttpMethod.POST, "/api/ships/*/lots").hasRole("MEDICAL_OFFICER")
                    .requestMatchers(HttpMethod.POST, "/api/ships/*/usages").hasRole("MEDICAL_OFFICER")
                    .requestMatchers(HttpMethod.POST, "/api/usages/*/cancellation").hasRole("MEDICAL_OFFICER")

                    // 그 외 조회와 로그아웃.
                    .anyRequest().authenticated())

            // 미로그인 처리.
            .exceptionHandling(e -> e
                    .authenticationEntryPoint(entryPoint)
                    .accessDeniedHandler(customAccessDeniedHandler)
            )

            // 로그아웃은 controller에서 처리.
            .logout(AbstractHttpConfigurer::disable);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
