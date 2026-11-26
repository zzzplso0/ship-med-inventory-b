package com.shipmed.shipmed.domain.notification.batch;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.InvalidJobParametersException;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.launch.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.launch.JobRestartException;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Slf4j
@RequiredArgsConstructor
@Component
public class ExpiryNotificationJobRunner {

    private final JobOperator jobOperator;
    private final Job expiryNotificationJob;

    public void run(LocalDate today) {
        JobParameters params = new JobParametersBuilder()
                .addLocalDate("today", today)
                .toJobParameters();

        try {
            jobOperator.start(expiryNotificationJob, params);
        } catch (JobInstanceAlreadyCompleteException e) { // 같은 날짜로 이미 성공한 경우.
            log.info("이미 완료된 날짜입니다. 알림 배치를 건너뜁니다. today={}", today);
        } catch (JobExecutionAlreadyRunningException | JobRestartException | InvalidJobParametersException e) { // 실행 자체를 못 한 경우.
            log.error("알림 배치를 실행하지 못했습니다. today={}", today, e);
        }
    }
}
