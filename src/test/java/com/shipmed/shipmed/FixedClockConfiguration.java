package com.shipmed.shipmed;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

// 테스트 기준 시각. 오늘 = 2026-07-22(UTC). requirements 5장.
@TestConfiguration(proxyBeanMethods = false)
public class FixedClockConfiguration {

	public static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-07-22T00:00:00Z"), ZoneOffset.UTC);

	// ClockConfig의 clock 빈과 이름이 겹치지 않게 하고 @Primary로 대신 주입.
	@Bean
	@Primary
	public Clock fixedClock() {
		return FIXED_CLOCK;
	}
}
