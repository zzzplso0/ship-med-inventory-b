package com.shipmed.shipmed.domain.stock;

import com.shipmed.shipmed.FixedClockConfiguration;
import com.shipmed.shipmed.TestcontainersConfiguration;
import com.shipmed.shipmed.domain.medication.entity.Medication;
import com.shipmed.shipmed.domain.medication.repository.MedicationRepository;
import com.shipmed.shipmed.domain.ship.entity.Ship;
import com.shipmed.shipmed.domain.ship.repository.ShipRepository;
import com.shipmed.shipmed.domain.stock.entity.StockLot;
import com.shipmed.shipmed.domain.stock.repository.StockLotRepository;
import com.shipmed.shipmed.domain.user.entity.Role;
import com.shipmed.shipmed.domain.user.entity.User;
import com.shipmed.shipmed.domain.user.repository.UserRepository;
import com.shipmed.shipmed.global.security.principal.CustomUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 오늘 = 2026-07-22(UTC). FixedClockConfiguration.
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, FixedClockConfiguration.class})
@Transactional
@SpringBootTest
class StockLotIntegrationTest {

	private static final String RECEIVED_AT = "2026-03-01T09:00:00Z";

	@Autowired
	private WebApplicationContext context;

	@Autowired
	private StockLotRepository stockLotRepository;

	@Autowired
	private ShipRepository shipRepository;

	@Autowired
	private MedicationRepository medicationRepository;

	@Autowired
	private UserRepository userRepository;

	private MockMvc mockMvc;
	private Ship ship;
	private Medication medication;
	private User officerUser;
	private CustomUserDetails officer;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

		ship = shipRepository.save(new Ship("선박"));
		medication = medicationRepository.save(new Medication("아스피린", null, "정"));
		// received_by FK가 있으므로 실제로 저장한 계정을 쓴다.
		officerUser = userRepository.save(new User("officer_1", "의무사관", "encoded", Role.MEDICAL_OFFICER, ship));
		officer = CustomUserDetails.from(officerUser);
	}

	@Test
	@DisplayName("만료일이 오늘이면 201이고 입고 시각과 입고자가 기록된다")
	void givenExpiryToday_whenReceive_thenReturns201AndRecordsReceiver() throws Exception {
		mockMvc.perform(postRequest(ship.getId(), body("L-001", 10, "2026-07-22", RECEIVED_AT)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.lotId").exists())
				.andExpect(jsonPath("$.remainingQuantity").value(10))
				.andExpect(jsonPath("$.status").value("ACTIVE"));

		StockLot lot = stockLotRepository.findAll().get(0);
		assertThat(lot.getReceivedAt()).isEqualTo(Instant.parse(RECEIVED_AT));
		assertThat(lot.getReceivedBy().getId()).isEqualTo(officerUser.getId());
	}

	@Test
	@DisplayName("만료일이 오늘보다 과거면 400 EXPIRED_ON_ARRIVAL")
	void givenPastExpiryDate_whenReceive_thenReturns400() throws Exception {
		mockMvc.perform(postRequest(ship.getId(), body("L-001", 10, "2026-07-20", RECEIVED_AT)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("EXPIRED_ON_ARRIVAL"));
	}

	@Test
	@DisplayName("입고 시각이 현재보다 미래면 400 RECEIVED_AT_IN_FUTURE")
	void givenFutureReceivedAt_whenReceive_thenReturns400() throws Exception {
		mockMvc.perform(postRequest(ship.getId(), body("L-001", 10, "2026-12-31", "2026-07-23T00:00:00Z")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("RECEIVED_AT_IN_FUTURE"));
	}

	@Test
	@DisplayName("입고 시각이 없으면 400")
	void givenMissingReceivedAt_whenReceive_thenReturns400() throws Exception {
		String body = """
				{"medicationId":%d,"lotNumber":"L-001","quantity":10,"expiryDate":"2026-12-31"}
				""".formatted(medication.getId());

		mockMvc.perform(postRequest(ship.getId(), body))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors.receivedAt").exists());
	}

	@ParameterizedTest
	@ValueSource(ints = {0, -1})
	@DisplayName("수량이 0 또는 음수면 400")
	void givenNonPositiveQuantity_whenReceive_thenReturns400(int quantity) throws Exception {
		mockMvc.perform(postRequest(ship.getId(), body("L-001", quantity, "2026-12-31", RECEIVED_AT)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors.quantity").exists());
	}

	@Test
	@DisplayName("없는 약품이면 404")
	void givenMissingMedication_whenReceive_thenReturns404() throws Exception {
		String body = """
				{"medicationId":%d,"lotNumber":"L-001","quantity":10,"expiryDate":"2026-12-31","receivedAt":"%s"}
				""".formatted(Long.MAX_VALUE, RECEIVED_AT);

		mockMvc.perform(postRequest(ship.getId(), body))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error").value("MEDICATION_NOT_FOUND"));
	}

	@Test
	@DisplayName("같은 선박·약품에 같은 로트 번호가 있으면 409 DUPLICATE_LOT")
	void givenSameLotNumber_whenReceive_thenReturns409() throws Exception {
		stockLotRepository.saveAndFlush(new StockLot("L-001", 10, LocalDate.parse("2026-12-31"),
				Instant.parse(RECEIVED_AT), officerUser, ship, medication));

		// 제약 위반으로 트랜잭션이 중단되므로 응답만 검증한다.
		mockMvc.perform(postRequest(ship.getId(), body("L-001", 5, "2026-12-31", RECEIVED_AT)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("DUPLICATE_LOT"));
	}

	@Test
	@DisplayName("다른 선박에 입고하면 403이고 재고가 생기지 않는다")
	void givenOtherShip_whenReceive_thenReturns403() throws Exception {
		Ship otherShip = shipRepository.save(new Ship("다른 선박"));

		mockMvc.perform(postRequest(otherShip.getId(), body("L-001", 10, "2026-12-31", RECEIVED_AT)))
				.andExpect(status().isForbidden());

		assertThat(stockLotRepository.count()).isZero();
	}

	private String body(String lotNumber, int quantity, String expiryDate, String receivedAt) {
		return """
				{"medicationId":%d,"lotNumber":"%s","quantity":%d,"expiryDate":"%s","receivedAt":"%s"}
				""".formatted(medication.getId(), lotNumber, quantity, expiryDate, receivedAt);
	}

	private MockHttpServletRequestBuilder postRequest(Long shipId, String body) {
		return post("/api/ships/" + shipId + "/lots")
				.contentType(MediaType.APPLICATION_JSON).content(body).with(user(officer));
	}
}
