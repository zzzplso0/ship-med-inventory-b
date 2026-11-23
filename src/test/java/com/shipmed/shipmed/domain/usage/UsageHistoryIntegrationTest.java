package com.shipmed.shipmed.domain.usage;

import com.shipmed.shipmed.FixedClockConfiguration;
import com.shipmed.shipmed.TestcontainersConfiguration;
import com.shipmed.shipmed.domain.medication.entity.Medication;
import com.shipmed.shipmed.domain.medication.repository.MedicationRepository;
import com.shipmed.shipmed.domain.ship.entity.Ship;
import com.shipmed.shipmed.domain.ship.repository.ShipRepository;
import com.shipmed.shipmed.domain.stock.entity.StockLot;
import com.shipmed.shipmed.domain.stock.repository.StockLotRepository;
import com.shipmed.shipmed.domain.usage.entity.UsageLotAllocation;
import com.shipmed.shipmed.domain.usage.entity.UsageRecord;
import com.shipmed.shipmed.domain.usage.repository.UsageLotAllocationRepository;
import com.shipmed.shipmed.domain.usage.repository.UsageRecordRepository;
import com.shipmed.shipmed.domain.user.entity.Role;
import com.shipmed.shipmed.domain.user.entity.User;
import com.shipmed.shipmed.domain.user.repository.UserRepository;
import com.shipmed.shipmed.global.security.principal.CustomUserDetails;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.time.Instant;
import java.time.LocalDate;

import static org.hamcrest.Matchers.contains;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 오늘 = 2026-07-22(UTC). FixedClockConfiguration.
// 사용 시각을 정해 두어야 하므로 사용 API 대신 기록·분배를 직접 저장한다.
// 저장한 데이터는 MyBatis 조회 전에 flush한다. 같은 MyBatis 조회를 한 테스트에서 두 번 부르면 1차 캐시 결과가 나오므로 한 번씩만 부른다.
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, FixedClockConfiguration.class})
@Transactional
@SpringBootTest
class UsageHistoryIntegrationTest {

	private static final Instant RECEIVED_AT = Instant.parse("2026-03-01T09:00:00Z");

	@Autowired
	private WebApplicationContext context;

	@Autowired
	private EntityManager entityManager;

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
	private Ship ship1;
	private Ship ship2;
	private Medication aspirin;
	private CustomUserDetails officer1;
	private CustomUserDetails admin;

	// 선박 1: 사용#1(아스피린 1정, 07-01), 사용#2(모르핀 2정, 07-10, 취소됨), 사용#3(아스피린 3정, 07-20).
	// 선박 2: 사용#4(아스피린 4정). 기록을 수량으로 구분한다.
	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

		ship1 = shipRepository.save(new Ship("선박1"));
		ship2 = shipRepository.save(new Ship("선박2"));
		aspirin = medicationRepository.save(new Medication("아스피린", null, "정"));
		Medication morphine = medicationRepository.save(new Medication("모르핀", null, "앰플"));
		User officer1User = userRepository.save(new User("officer_1", "의무사관1", "encoded", Role.MEDICAL_OFFICER, ship1));
		User officer2User = userRepository.save(new User("officer_2", "의무사관2", "encoded", Role.MEDICAL_OFFICER, ship2));
		User adminUser = userRepository.save(new User("admin_1", "관리자", "encoded", Role.ADMIN, null));
		officer1 = CustomUserDetails.from(officer1User);
		admin = CustomUserDetails.from(adminUser);

		StockLot aspirinLotA = saveLot("A", ship1, aspirin, officer1User);
		StockLot aspirinLotB = saveLot("B", ship1, aspirin, officer1User);
		StockLot morphineLot = saveLot("M", ship1, morphine, officer1User);
		StockLot ship2Lot = saveLot("S", ship2, aspirin, officer2User);

		// 기간 필터의 양 끝(시작일 00:00, 종료일 23:59:59)에 걸치도록 둔다.
		UsageRecord usage1 = saveUsage(1, "2026-07-01T00:00:00Z", officer1User, ship1, aspirin);
		saveAllocation(1, usage1, aspirinLotA);

		UsageRecord usage2 = saveUsage(2, "2026-07-10T23:59:59Z", officer1User, ship1, morphine);
		saveAllocation(2, usage2, morphineLot);
		usage2.cancel(officer1User, "수량 오기입", Instant.parse("2026-07-11T00:00:00Z"));

		// 로트 두 개에 걸친 사용. 분배는 저장 순서(id)대로 나온다.
		UsageRecord usage3 = saveUsage(3, "2026-07-20T09:00:00Z", officer1User, ship1, aspirin);
		saveAllocation(1, usage3, aspirinLotA);
		saveAllocation(2, usage3, aspirinLotB);

		UsageRecord usage4 = saveUsage(4, "2026-07-15T09:00:00Z", officer2User, ship2, aspirin);
		saveAllocation(4, usage4, ship2Lot);

		entityManager.flush();
	}

	@Test
	@DisplayName("필터가 없으면 그 선박의 기록을 최신순으로, 취소 정보와 로트별 차감 내역을 포함해 돌려준다")
	void givenNoFilter_whenGetHistory_thenReturnsLatestFirstWithDetails() throws Exception {
		mockMvc.perform(get("/api/ships/" + ship1.getId() + "/usages").with(user(officer1)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(3))
				.andExpect(jsonPath("$.content[*].quantity").value(contains(3, 2, 1)))
				.andExpect(jsonPath("$.content[0].allocations[*].lotNumber").value(contains("A", "B")))
				.andExpect(jsonPath("$.content[0].allocations[*].quantity").value(contains(1, 2)))
				.andExpect(jsonPath("$.content[1].status").value("CANCELLED"))
				.andExpect(jsonPath("$.content[1].cancelledAt").value("2026-07-11T00:00:00Z"))
				.andExpect(jsonPath("$.content[1].cancelledBy").value("의무사관1"))
				.andExpect(jsonPath("$.content[1].cancelReason").value("수량 오기입"))
				.andExpect(jsonPath("$.content[1].allocations[0].lotNumber").value("M"))
				.andExpect(jsonPath("$.content[2].allocations[0].lotNumber").value("A"))
				.andExpect(jsonPath("$.content[2].cancelledAt").doesNotExist());
	}

	@Test
	@DisplayName("기간은 시작일·종료일을 포함한다")
	void givenDateRange_whenGetHistory_thenIncludesBothEnds() throws Exception {
		mockMvc.perform(get("/api/ships/" + ship1.getId() + "/usages").with(user(officer1))
						.param("from", "2026-07-01")
						.param("to", "2026-07-10"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[*].quantity").value(contains(2, 1)));
	}

	@Test
	@DisplayName("약품과 상태로 거른다")
	void givenMedicationAndStatus_whenGetHistory_thenFilters() throws Exception {
		mockMvc.perform(get("/api/ships/" + ship1.getId() + "/usages").with(user(officer1))
						.param("medicationId", aspirin.getId().toString())
						.param("status", "ACTIVE"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[*].quantity").value(contains(3, 1)));
	}

	@Test
	@DisplayName("시작일이 종료일보다 늦으면 400")
	void givenFromAfterTo_whenGetHistory_thenReturns400() throws Exception {
		mockMvc.perform(get("/api/ships/" + ship1.getId() + "/usages").with(user(officer1))
						.param("from", "2026-07-10")
						.param("to", "2026-07-01"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors.dateRangeValid").exists());
	}

	@Test
	@DisplayName("관리자는 선박 1의 이력을 볼 수 있다")
	void givenAdmin_whenGetHistory_thenReturns200() throws Exception {
		mockMvc.perform(get("/api/ships/" + ship1.getId() + "/usages").with(user(admin)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(3));
	}

	@Test
	@DisplayName("의무사관이 다른 선박의 이력을 요청하면 403")
	void givenOtherShip_whenOfficerGetsHistory_thenReturns403() throws Exception {
		mockMvc.perform(get("/api/ships/" + ship2.getId() + "/usages").with(user(officer1)))
				.andExpect(status().isForbidden());
	}

	private StockLot saveLot(String lotNumber, Ship ship, Medication medication, User receiver) {
		return stockLotRepository.save(new StockLot(lotNumber, 100, LocalDate.parse("2027-01-31"), RECEIVED_AT,
				receiver, ship, medication));
	}

	private UsageRecord saveUsage(int quantity, String usedAt, User user, Ship ship, Medication medication) {
		return usageRecordRepository.save(new UsageRecord(quantity, "key-" + quantity, Instant.parse(usedAt),
				user, ship, medication));
	}

	private void saveAllocation(int quantity, UsageRecord usageRecord, StockLot lot) {
		usageLotAllocationRepository.save(new UsageLotAllocation(quantity, usageRecord, lot));
	}
}
