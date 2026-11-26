package com.shipmed.shipmed.domain.notification.batch;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;

@ConditionalOnProperty(name = "shipmed.notification.scheduler-enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Component
public class ExpiryNotificationScheduler {

    private final ExpiryNotificationJobRunner jobRunner;
    private final Clock clock;

    @Scheduled(cron = "0 5 0 * * *", zone = "UTC")
    public void schedule() {
        jobRunner.run(LocalDate.now(clock));
    }
}
