package com.shipmed.shipmed.global.config;

import com.shipmed.shipmed.global.security.interceptor.ShipAccessInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@RequiredArgsConstructor
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final ShipAccessInterceptor shipAccessInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(shipAccessInterceptor).addPathPatterns("/api/ships/{shipId}/**");
    }
}
