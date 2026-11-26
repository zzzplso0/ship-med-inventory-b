package com.shipmed.shipmed.domain.notification.batch;

import com.shipmed.shipmed.domain.notification.dto.response.ExpiryNotificationLotRow;
import com.shipmed.shipmed.domain.notification.repository.ExpiryNotificationQueryMapper;
import com.shipmed.shipmed.domain.user.entity.Role;
import com.shipmed.shipmed.domain.user.entity.User;
import com.shipmed.shipmed.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
@StepScope
@Component
public class ExpiryNotificationTasklet implements Tasklet {

    private static final int FIRST_NOTICE_DAYS = 30;
    private static final int SECOND_NOTICE_DAYS = 7;

    private final ExpiryNotificationQueryMapper queryMapper;
    private final UserRepository userRepository;
    private final JavaMailSender mailSender;

    @Value("${shipmed.notification.from}")
    private String from;

    @Value("#{jobParameters['today']}")
    private LocalDate today;

    @Override
    public @Nullable RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        int sent = 0;
        int skipped = 0;
        int failed = 0;

        List<ExpiryNotificationLotRow> rows = queryMapper.selectNotificationLots(
                today, today.plusDays(FIRST_NOTICE_DAYS), today.plusDays(SECOND_NOTICE_DAYS));

        if (rows.isEmpty()) {
            log.info("알릴 로트가 없습니다. today={}", today);
            return RepeatStatus.FINISHED;
        }

        Map<Long, List<ExpiryNotificationLotRow>> lotsByShip = rows.stream()
                .collect(Collectors.groupingBy(ExpiryNotificationLotRow::shipId));

        List<User> officers = userRepository.findByRoleAndShipIdIn(Role.MEDICAL_OFFICER, lotsByShip.keySet());

        for (User officer: officers) {
            if (officer.getEmail() == null) {
                log.warn("이메일이 없어 알림을 건너뜁니다. loginId={}", officer.getLoginId());
                skipped++;
                continue;
            }

            List<ExpiryNotificationLotRow> lots = lotsByShip.get(officer.getShip().getId());

            SimpleMailMessage message = createMessage(officer.getEmail(), lots, today);

            try {
                mailSender.send(message);
                sent++;
            } catch (MailException e) {
                log.error("알림 메일 발송에 실패했습니다. loginId={}", officer.getLoginId(), e);
                failed++;
            }
        }

        log.info("만료·임박 알림 메일: 보냄={}, 건너뜀={}, 실패={}", sent, skipped, failed);

        return RepeatStatus.FINISHED;
    }

    private SimpleMailMessage createMessage(String to, List<ExpiryNotificationLotRow> lots, LocalDate today) {
        List<ExpiryNotificationLotRow> expired = new ArrayList<>();
        List<ExpiryNotificationLotRow> d7 = new ArrayList<>();
        List<ExpiryNotificationLotRow> d30 = new ArrayList<>();

        LocalDate dMinus30 = today.plusDays(FIRST_NOTICE_DAYS);
        LocalDate dMinus7 = today.plusDays(SECOND_NOTICE_DAYS);

        for (ExpiryNotificationLotRow lot: lots) {
            if (lot.expiryDate().isBefore(today)) expired.add(lot);
            else if (lot.expiryDate().equals(dMinus7)) d7.add(lot);
            else if (lot.expiryDate().equals(dMinus30)) d30.add(lot);
        }

        String shipName = lots.get(0).shipName();
        String subject = "[shipmed] 선박 %s 폐기 대상 %d건, 임박 %d건".formatted(shipName, expired.size(), d7.size() + d30.size());

        StringBuilder text = new StringBuilder();
        appendSection(text, "폐기 대상", expired);
        appendSection(text, "유효기한 7일 전", d7);
        appendSection(text, "유효기한 30일 전", d30);

        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text.toString());
        message.setFrom(from);

        return message;
    }

    private void appendSection(StringBuilder text, String title, List<ExpiryNotificationLotRow> lots) {
        if (lots.isEmpty()) return;

        text.append("[").append(title).append("] ").append(lots.size()).append("건\n");
        for (ExpiryNotificationLotRow lot : lots) {
            text.append("- %s / 로트 %s / 잔량 %d / 유효기한 %s\n".formatted(
                    lot.medicationName(), lot.lotNumber(), lot.remainingQuantity(), lot.expiryDate()));
        }
        text.append("\n");
    }
}
