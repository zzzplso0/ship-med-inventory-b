package com.shipmed.shipmed.domain.notification;

import com.shipmed.shipmed.FixedClockConfiguration;
import com.shipmed.shipmed.TestcontainersConfiguration;
import com.shipmed.shipmed.domain.medication.entity.Medication;
import com.shipmed.shipmed.domain.medication.repository.MedicationRepository;
import com.shipmed.shipmed.domain.notification.batch.ExpiryNotificationJobRunner;
import com.shipmed.shipmed.domain.ship.entity.Ship;
import com.shipmed.shipmed.domain.ship.repository.ShipRepository;
import com.shipmed.shipmed.domain.stock.entity.StockLot;
import com.shipmed.shipmed.domain.stock.repository.StockLotRepository;
import com.shipmed.shipmed.domain.user.entity.Role;
import com.shipmed.shipmed.domain.user.entity.User;
import com.shipmed.shipmed.domain.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

// 오늘 = 2026-07-22(UTC). FixedClockConfiguration.
// Job 실행 기록(BATCH_ 테이블)은 Job이 직접 커밋하므로 @Transactional을 두지 않고, 만든 데이터는 테스트마다 지운다.
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, FixedClockConfiguration.class})
@SpringBootTest
class ExpiryNotificationIntegrationTest {

	private static final LocalDate TODAY = LocalDate.now(FixedClockConfiguration.FIXED_CLOCK);
	private static final Instant RECEIVED_AT = Instant.parse("2026-03-01T09:00:00Z");

	@MockitoBean
	private JavaMailSender mailSender;

	@Autowired
	private ExpiryNotificationJobRunner jobRunner;

	@Autowired
	private StockLotRepository stockLotRepository;

	@Autowired
	private ShipRepository shipRepository;

	@Autowired
	private MedicationRepository medicationRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private Ship ship1;
	private Ship ship2;
	private Medication medication;
	private User officer1;
	private StockLot lotT;
	private final List<User> users = new ArrayList<>();

	@BeforeEach
	void setUp() {
		ship1 = shipRepository.save(new Ship("선박1"));
		ship2 = shipRepository.save(new Ship("선박2"));
		medication = medicationRepository.save(new Medication("아스피린", null, "정"));

		officer1 = saveOfficer("notification_officer_1", "officer1@example.com", ship1);
		saveOfficer("notification_officer_2", null, ship1);
		saveOfficer("notification_officer_3", "officer3@example.com", ship2);

		lotT = saveLot("T", 10, "2026-07-01");
		saveLot("V", 0, "2026-07-15");
		saveLot("P", 10, "2026-08-21");
		saveLot("Q", 10, "2026-07-29");
		saveLot("R", 10, "2026-08-10");
	}

	@AfterEach
	void tearDown() {
		jdbcTemplate.execute("TRUNCATE batch_job_instance CASCADE");
		stockLotRepository.deleteAllInBatch();
		userRepository.deleteAll(users);
		medicationRepository.delete(medication);
		shipRepository.deleteAll(List.of(ship1, ship2));
	}

	@Test
	@DisplayName("이메일이 있는 의무사관에게 폐기 대상과 D-30·D-7 로트를 담아 1통 보낸다")
	void givenLots_whenRun_thenSendsOneMailToOfficerWithEmail() {
		jobRunner.run(TODAY);

		ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
		verify(mailSender, times(1)).send(captor.capture());

		SimpleMailMessage message = captor.getValue();
		assertThat(message.getTo()).containsExactly("officer1@example.com");
		assertThat(message.getFrom()).isEqualTo("noreply@example.com");
		assertThat(message.getSubject()).isEqualTo("[shipmed] 선박 선박1 폐기 대상 1건, 임박 2건");
		assertThat(message.getText())
				.contains("로트 T /", "로트 P /", "로트 Q /")
				.doesNotContain("로트 V /", "로트 R /");
	}

	@Test
	@DisplayName("같은 날 다시 실행하면 보내지 않는다")
	void givenAlreadyRunToday_whenRunAgain_thenDoesNotSendAgain() {
		jobRunner.run(TODAY);
		jobRunner.run(TODAY);

		verify(mailSender, times(1)).send(any(SimpleMailMessage.class));
	}

	@Test
	@DisplayName("로트T를 폐기하고 다음 날 실행하면 알릴 로트가 없어 보내지 않는다")
	void givenLotTDiscarded_whenRunNextDay_thenDoesNotSend() {
		lotT.discard(officer1, "폐기", RECEIVED_AT);
		stockLotRepository.save(lotT);

		jobRunner.run(TODAY.plusDays(1));

		verify(mailSender, never()).send(any(SimpleMailMessage.class));
	}

	@Test
	@DisplayName("한 명에게 발송이 실패해도 다음 의무사관에게 보낸다")
	void givenFirstSendFails_whenRun_thenSendsToNextOfficer() {
		saveOfficer("notification_officer_4", "officer4@example.com", ship1);
		doThrow(new MailSendException("발송 실패"))
				.doNothing()
				.when(mailSender).send(any(SimpleMailMessage.class));

		jobRunner.run(TODAY);

		verify(mailSender, times(2)).send(any(SimpleMailMessage.class));
	}

	private User saveOfficer(String loginId, String email, Ship ship) {
		User user = userRepository.save(new User(loginId, email, "의무사관", "encoded", Role.MEDICAL_OFFICER, ship));
		users.add(user);
		return user;
	}

	private StockLot saveLot(String lotNumber, int remainingQuantity, String expiryDate) {
		StockLot lot = new StockLot(lotNumber, 10, LocalDate.parse(expiryDate), RECEIVED_AT, officer1, ship1, medication);
		lot.subtractQuantity(10 - remainingQuantity);
		return stockLotRepository.save(lot);
	}
}
