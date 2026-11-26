package com.shipmed.shipmed.domain.notification.batch;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.listener.JobExecutionListener;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Slf4j
@RequiredArgsConstructor
@Configuration
public class ExpiryNotificationJobConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;

    @Bean
    public Job expiryNotificationJob(Step expiryNotificationStep) {
        return new JobBuilder("expiryNotificationJob", jobRepository)
                .listener(expiryNotificationJobListener())
                .start(expiryNotificationStep)
                .build();
    }

    @Bean
    public Step expiryNotificationStep(ExpiryNotificationTasklet expiryNotificationTasklet) {
        return new StepBuilder("expiryNotificationStep", jobRepository)
                .tasklet(expiryNotificationTasklet, transactionManager)
                .build();
    }

    private JobExecutionListener expiryNotificationJobListener() {
        return new JobExecutionListener() {
            @Override
            public void afterJob(JobExecution jobExecution) {
                if (jobExecution.getStatus() != BatchStatus.COMPLETED) {
                    log.error("만료·임박 알림 배치가 실패했습니다. today={}, status={}",
                            jobExecution.getJobParameters().getLocalDate("today"), jobExecution.getStatus());
                }
            }
        };
    }
}
