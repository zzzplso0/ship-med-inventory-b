package com.shipmed.shipmed.domain.usage;

import com.shipmed.shipmed.FixedClockConfiguration;
import com.shipmed.shipmed.TestcontainersConfiguration;
import com.shipmed.shipmed.domain.medication.entity.Medication;
import com.shipmed.shipmed.domain.medication.repository.MedicationRepository;
import com.shipmed.shipmed.domain.ship.entity.Ship;
import com.shipmed.shipmed.domain.ship.repository.ShipRepository;
import com.shipmed.shipmed.domain.stock.entity.StockLot;
import com.shipmed.shipmed.domain.stock.repository.StockLotRepository;
import com.shipmed.shipmed.domain.usage.dto.request.UsageCreateRequest;
import com.shipmed.shipmed.domain.usage.dto.response.UsageResult;
import com.shipmed.shipmed.domain.usage.entity.RecordStatus;
import com.shipmed.shipmed.domain.usage.exception.UsageInsufficientStockException;
import com.shipmed.shipmed.domain.usage.repository.UsageLotAllocationRepository;
import com.shipmed.shipmed.domain.usage.repository.UsageRecordRepository;
import com.shipmed.shipmed.domain.usage.service.UsageService;
import com.shipmed.shipmed.domain.user.entity.Role;
import com.shipmed.shipmed.domain.user.entity.User;
import com.shipmed.shipmed.domain.user.repository.UserRepository;
import com.shipmed.shipmed.global.security.principal.CustomUserDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 오늘 = 2026-07-22(UTC). FixedClockConfiguration.
// 동시성 테스트는 두 스레드가 각자 커밋해야 하므로 @Transactional을 두지 않고, 만든 데이터는 테스트마다 지운다.
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, FixedClockConfiguration.class})
@SpringBootTest
class UsageIntegrationTest {

	private static final String KEY = "key-1";
	private static final Instant RECEIVED_AT = Instant.parse("2026-03-01T09:00:00Z");
	private static final String REASON = "수량 오기입";

	@Autowired
	private WebApplicationContext context;

	@Autowired
	private UsageService usageService;

	@Autowired
	private StockLotRepository stockLotRepository;

	@Autowired
	private UsageRecordRepository usageRecordRepository;

	@Autowired
	private UsageLotAllocationRepository usageLotAllocationRepository;

	@Autowired
	private ShipRepository shipRepository;

	@Autowired
	private MedicationRepository medicationRepository;

	@Autowired
	private UserRepository userRepository;

	private MockMvc mockMvc;
	private Ship ship;
	private Medication medication;
	private User officer1;
	private User officer2;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

		ship = shipRepository.save(new Ship("선박"));
		medication = medicationRepository.save(new Medication("아스피린", null, "정"));
		officer1 = userRepository.save(new User("usage_officer_1", "의무사관1", "encoded", Role.MEDICAL_OFFICER, ship));
		officer2 = userRepository.save(new User("usage_officer_2", "의무사관2", "encoded", Role.MEDICAL_OFFICER, ship));
	}

	@AfterEach
	void tearDown() {
		usageLotAllocationRepository.deleteAllInBatch();
		usageRecordRepository.deleteAllInBatch();
		stockLotRepository.deleteAllInBatch();
		userRepository.deleteAll(List.of(officer1, officer2));
		medicationRepository.delete(medication);
		shipRepository.delete(ship);
	}

	@Test
	@DisplayName("유효기한이 빠른 로트부터 차감해 201, 같은 키로 다시 보내면 200과 같은 usageId이고 추가 차감은 없다")
	void givenTwoLots_whenUseTwiceWithSameKey_thenDeductsOnceByFefo() throws Exception {
		StockLot lotA = saveLot("A", 100, "2027-01-31");
		StockLot lotB = saveLot("B", 30, "2026-08-31");

		mockMvc.perform(postRequest(KEY, medication.getId(), 40))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.allocations[0].lotId").value(lotB.getId()))
				.andExpect(jsonPath("$.allocations[0].quantity").value(30))
				.andExpect(jsonPath("$.allocations[1].lotId").value(lotA.getId()))
				.andExpect(jsonPath("$.allocations[1].quantity").value(10));
		Long usageId = usageRecordRepository.findAll().get(0).getId();

		mockMvc.perform(postRequest(KEY, medication.getId(), 40))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.usageId").value(usageId));

		assertThat(remaining(lotA)).isEqualTo(90);
		assertThat(remaining(lotB)).isZero();
	}

	@Test
	@DisplayName("만료된 로트는 빼고 계산해 부족하면 409 INSUFFICIENT_STOCK이고 아무것도 차감·기록하지 않는다")
	void givenExpiredLot_whenUseMoreThanAvailable_thenReturns409() throws Exception {
		StockLot lotX = saveLot("X", 50, "2026-07-22");
		saveLot("Y", 50, "2026-07-21");

		mockMvc.perform(postRequest(KEY, medication.getId(), 60))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("INSUFFICIENT_STOCK"))
				.andExpect(jsonPath("$.requested").value(60))
				.andExpect(jsonPath("$.available").value(50));

		assertThat(remaining(lotX)).isEqualTo(50);
		assertThat(usageRecordRepository.count()).isZero();
	}

	@Test
	@DisplayName("같은 키로 수량이 다른 요청이면 409 IDEMPOTENCY_KEY_REUSED이고 추가 차감은 없다")
	void givenSameKeyWithOtherQuantity_whenUse_thenReturns409() throws Exception {
		StockLot lot = saveLot("A", 100, "2027-01-31");
		mockMvc.perform(postRequest(KEY, medication.getId(), 40)).andExpect(status().isCreated());

		mockMvc.perform(postRequest(KEY, medication.getId(), 30))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("IDEMPOTENCY_KEY_REUSED"));

		assertThat(remaining(lot)).isEqualTo(60);
	}

	@Test
	@DisplayName("없는 약품이면 404")
	void givenMissingMedication_whenUse_thenReturns404() throws Exception {
		mockMvc.perform(postRequest(KEY, Long.MAX_VALUE, 1))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error").value("MEDICATION_NOT_FOUND"));
	}

	@Test
	@DisplayName("수량이 0이면 400이고 fieldErrors에 quantity가 있다")
	void givenZeroQuantity_whenUse_thenReturns400WithFieldErrors() throws Exception {
		mockMvc.perform(postRequest(KEY, medication.getId(), 0))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors.quantity").exists());
	}

	@Test
	@DisplayName("Idempotency-Key가 100자를 넘으면 400이고 fieldErrors에 헤더 이름이 있다")
	void givenTooLongKey_whenUse_thenReturns400WithFieldErrors() throws Exception {
		mockMvc.perform(postRequest("a".repeat(101), medication.getId(), 1))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors['Idempotency-Key']").exists());
	}

	@Test
	@DisplayName("두 의무사관이 동시에 사용하면 하나만 성공하고 다른 하나는 재고 부족이다")
	void givenTwoOfficers_whenUseConcurrently_thenOnlyOneSucceeds() throws Exception {
		StockLot lot = saveLot("A", 10, "2027-01-31");

		List<Object> outcomes = runConcurrently(
				() -> usageService.create(ship.getId(), officer1.getId(), "key-1", request(7)),
				() -> usageService.create(ship.getId(), officer2.getId(), "key-2", request(5)));

		assertThat(outcomes).filteredOn(UsageResult.class::isInstance).hasSize(1);
		assertThat(outcomes).filteredOn(UsageInsufficientStockException.class::isInstance).hasSize(1);
		assertThat(remaining(lot)).isIn(3, 5);
		assertThat(usageRecordRepository.count()).isEqualTo(1);
	}

	@Test
	@DisplayName("같은 키로 동시에 두 번 보내면 하나는 새로 기록하고 다른 하나는 같은 기록을 돌려준다")
	void givenSameKey_whenUseConcurrently_thenRecordsOnce() throws Exception {
		StockLot lot = saveLot("A", 100, "2027-01-31");
		Callable<UsageResult> task = () -> usageService.create(ship.getId(), officer1.getId(), KEY, request(40));

		List<Object> outcomes = runConcurrently(task, task);

		assertThat(outcomes).allMatch(UsageResult.class::isInstance);
		List<UsageResult> results = outcomes.stream().map(UsageResult.class::cast).toList();
		assertThat(results).extracting(UsageResult::created).containsExactlyInAnyOrder(true, false);
		assertThat(results.get(0).usageDto().usageId()).isEqualTo(results.get(1).usageDto().usageId());
		assertThat(remaining(lot)).isEqualTo(60);
		assertThat(usageRecordRepository.count()).isEqualTo(1);
	}

	@Test
	@DisplayName("취소하면 원래 로트로 복원하고 기록과 분배내역을 남기며, 다시 취소해도 추가 복원은 없다")
	void givenUsageOverTwoLots_whenCancelTwice_thenRestoresOnce() throws Exception {
		StockLot lotA = saveLot("A", 100, "2027-01-31");
		StockLot lotB = saveLot("B", 30, "2026-08-31");
		Long usageId = use(officer1, KEY, 40);

		mockMvc.perform(cancelRequest(usageId, REASON, officer1))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("CANCELLED"))
				.andExpect(jsonPath("$.cancelledBy").value("의무사관1"))
				.andExpect(jsonPath("$.restored.length()").value(2));
		mockMvc.perform(cancelRequest(usageId, REASON, officer1))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("CANCELLED"));

		assertThat(remaining(lotA)).isEqualTo(100);
		assertThat(remaining(lotB)).isEqualTo(30);
		assertThat(usageLotAllocationRepository.count()).isEqualTo(2);
	}

	@Test
	@DisplayName("전량 차감한 로트를 취소로 복원하면 그 수량을 다시 사용할 수 있다")
	void givenFullyUsedLot_whenCancelAndUseAgain_thenUsesRestoredQuantity() throws Exception {
		StockLot lot = saveLot("A", 100, "2027-01-31");
		Long usageId = use(officer1, KEY, 100);

		mockMvc.perform(cancelRequest(usageId, REASON, officer1)).andExpect(status().isOk());
		assertThat(remaining(lot)).isEqualTo(100);

		mockMvc.perform(postRequest("key-2", medication.getId(), 50)).andExpect(status().isCreated());
		assertThat(remaining(lot)).isEqualTo(50);
	}

	@Test
	@DisplayName("취소된 기록의 키로 같은 사용을 다시 보내면 200과 CANCELLED이고 다시 차감하지 않는다")
	void givenCancelledUsage_whenUseAgainWithSameKey_thenReturnsCancelled() throws Exception {
		StockLot lot = saveLot("A", 100, "2027-01-31");
		Long usageId = use(officer1, KEY, 40);
		mockMvc.perform(cancelRequest(usageId, REASON, officer1)).andExpect(status().isOk());

		mockMvc.perform(postRequest(KEY, medication.getId(), 40))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.usageId").value(usageId))
				.andExpect(jsonPath("$.status").value("CANCELLED"));

		assertThat(remaining(lot)).isEqualTo(100);
	}

	@Test
	@DisplayName("남이 기록한 사용을 취소하면 403이고 잔량과 상태는 그대로다")
	void givenOthersUsage_whenCancel_thenReturns403() throws Exception {
		StockLot lot = saveLot("A", 100, "2027-01-31");
		Long usageId = use(officer1, KEY, 40);

		mockMvc.perform(cancelRequest(usageId, REASON, officer2))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error").value("ACCESS_DENIED"));

		assertThat(remaining(lot)).isEqualTo(60);
		assertThat(usageRecordRepository.findById(usageId).orElseThrow().getStatus()).isEqualTo(RecordStatus.ACTIVE);
	}

	@Test
	@DisplayName("없는 사용기록을 취소하면 404")
	void givenMissingUsage_whenCancel_thenReturns404() throws Exception {
		mockMvc.perform(cancelRequest(Long.MAX_VALUE, REASON, officer1))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error").value("USAGE_NOT_FOUND"));
	}

	@Test
	@DisplayName("취소 사유가 공백이면 대상 확인 전에 400이고 fieldErrors에 reason이 있다")
	void givenBlankReason_whenCancel_thenReturns400WithFieldErrors() throws Exception {
		mockMvc.perform(cancelRequest(Long.MAX_VALUE, " ", officer1))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors.reason").exists());
	}

	@Test
	@DisplayName("관리자가 취소하면 403")
	void givenAdmin_whenCancel_thenReturns403() throws Exception {
		User admin = new User("usage_admin", "관리자", "encoded", Role.ADMIN, null);

		mockMvc.perform(cancelRequest(Long.MAX_VALUE, REASON, admin))
				.andExpect(status().isForbidden());
	}

	// 두 작업을 같은 순간에 출발시키고, 각 결과 또는 던진 예외를 모은다.
	private List<Object> runConcurrently(Callable<UsageResult> first, Callable<UsageResult> second) throws Exception {
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			List<Future<UsageResult>> futures = new ArrayList<>();
			for (Callable<UsageResult> task : List.of(first, second)) {
				futures.add(executor.submit(() -> {
					start.await();
					return task.call();
				}));
			}
			start.countDown();

			List<Object> outcomes = new ArrayList<>();
			for (Future<UsageResult> future : futures) {
				try {
					outcomes.add(future.get());
				} catch (ExecutionException e) {
					outcomes.add(e.getCause());
				}
			}
			return outcomes;
		} finally {
			executor.shutdown();
		}
	}

	private StockLot saveLot(String lotNumber, int quantity, String expiryDate) {
		return stockLotRepository.save(new StockLot(lotNumber, quantity, LocalDate.parse(expiryDate), RECEIVED_AT,
				officer1, ship, medication));
	}

	private int remaining(StockLot lot) {
		return stockLotRepository.findById(lot.getId()).orElseThrow().getRemainingQuantity();
	}

	private UsageCreateRequest request(int quantity) {
		return new UsageCreateRequest(medication.getId(), quantity);
	}

	private Long use(User officer, String key, int quantity) {
		return usageService.create(ship.getId(), officer.getId(), key, request(quantity)).usageDto().usageId();
	}

	private MockHttpServletRequestBuilder cancelRequest(Long usageId, String reason, User user) {
		String body = """
				{"reason":"%s"}
				""".formatted(reason);

		return post("/api/usages/" + usageId + "/cancellation")
				.contentType(MediaType.APPLICATION_JSON).content(body)
				.with(user(CustomUserDetails.from(user)));
	}

	private MockHttpServletRequestBuilder postRequest(String key, Long medicationId, int quantity) {
		String body = """
				{"medicationId":%d,"quantity":%d}
				""".formatted(medicationId, quantity);

		return post("/api/ships/" + ship.getId() + "/usages")
				.header("Idempotency-Key", key)
				.contentType(MediaType.APPLICATION_JSON).content(body)
				.with(user(CustomUserDetails.from(officer1)));
	}
}
