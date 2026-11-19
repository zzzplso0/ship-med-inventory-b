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
import org.springframework.security.web.access.AccessDeniedHandlerImpl;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.AnyRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

@EnableWebSecurity
@Configuration
public class SecurityConfig {

    private static final RequestMatcher API = PathPatternRequestMatcher.withDefaults().matcher("/api/**");

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   AuthenticationEntryPoint entryPoint,
                                                   CustomAccessDeniedHandler customAccessDeniedHandler) throws Exception {
        http
            .csrf(csrf -> csrf.ignoringRequestMatchers("/api/**"))
            .authorizeHttpRequests(auth -> auth
                    // Controller
                    .requestMatchers(HttpMethod.GET, "/login").permitAll()
                    .requestMatchers(HttpMethod.POST, "/login").permitAll()
                    .requestMatchers("/error").permitAll()

                    // 관리자 화면.
                    .requestMatchers("/users").hasRole("ADMIN")
                    .requestMatchers(HttpMethod.POST, "/ships").hasRole("ADMIN")
                    .requestMatchers(HttpMethod.POST, "/medications").hasRole("ADMIN")
                    .requestMatchers(HttpMethod.POST, "/ships/*/requirements").hasRole("ADMIN")

                    // 의무사관 화면.
                    .requestMatchers("/ships/*/lots").hasRole("MEDICAL_OFFICER")
                    .requestMatchers("/ships/*/usages/**").hasRole("MEDICAL_OFFICER")
                    .requestMatchers(HttpMethod.POST, "/usages/*/cancellation").hasRole("MEDICAL_OFFICER")

                    /* RestController */
                    // 로그인.
                    .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()

                    // actuator.
                    .requestMatchers("/actuator/health/**").permitAll()

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

            // 미로그인·권한 없음 처리. /api는 JSON, 화면은 로그인 화면 이동·오류 화면.
            .exceptionHandling(e -> e
                    .defaultAuthenticationEntryPointFor(entryPoint, API)
                    .defaultAuthenticationEntryPointFor(new LoginUrlAuthenticationEntryPoint("/login"), AnyRequestMatcher.INSTANCE)

                    .defaultAccessDeniedHandlerFor(customAccessDeniedHandler, API)
                    .defaultAccessDeniedHandlerFor(new AccessDeniedHandlerImpl(), AnyRequestMatcher.INSTANCE)
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
