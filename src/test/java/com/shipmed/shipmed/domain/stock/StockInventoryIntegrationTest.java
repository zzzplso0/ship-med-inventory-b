package com.shipmed.shipmed.domain.stock;

import com.shipmed.shipmed.FixedClockConfiguration;
import com.shipmed.shipmed.TestcontainersConfiguration;
import com.shipmed.shipmed.domain.medication.entity.Medication;
import com.shipmed.shipmed.domain.medication.repository.MedicationRepository;
import com.shipmed.shipmed.domain.ship.entity.Ship;
import com.shipmed.shipmed.domain.ship.repository.ShipRepository;
import com.shipmed.shipmed.domain.stock.entity.LotStatus;
import com.shipmed.shipmed.domain.stock.entity.StockLot;
import com.shipmed.shipmed.domain.stock.entity.StockRequirement;
import com.shipmed.shipmed.domain.stock.repository.StockLotRepository;
import com.shipmed.shipmed.domain.stock.repository.StockRequirementRepository;
import com.shipmed.shipmed.domain.user.entity.Role;
import com.shipmed.shipmed.domain.user.entity.User;
import com.shipmed.shipmed.domain.user.repository.UserRepository;
import com.shipmed.shipmed.global.security.principal.CustomUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.time.Instant;
import java.time.LocalDate;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 오늘 = 2026-07-22(UTC). FixedClockConfiguration.
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, FixedClockConfiguration.class})
@Transactional
@SpringBootTest
class StockInventoryIntegrationTest {

	private static final Instant RECEIVED_AT = Instant.parse("2026-03-01T09:00:00Z");

	@Autowired
	private WebApplicationContext context;

	@Autowired
	private StockLotRepository stockLotRepository;

	@Autowired
	private StockRequirementRepository stockRequirementRepository;

	@Autowired
	private ShipRepository shipRepository;

	@Autowired
	private MedicationRepository medicationRepository;

	@Autowired
	private UserRepository userRepository;

	private MockMvc mockMvc;
	private Ship ship;
	private Medication morphine;
	private Medication aspirin;
	private User officer;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

		ship = shipRepository.save(new Ship("선박"));
		morphine = medicationRepository.save(new Medication("모르핀", null, "앰플"));
		aspirin = medicationRepository.save(new Medication("아스피린", null, "정"));
		officer = userRepository.save(new User("inventory_officer", "의무사관", "encoded", Role.MEDICAL_OFFICER, ship));
	}

	@Test
	@DisplayName("기준만 있고 로트가 없으면 재고 0 부족 경고, 로트만 있고 기준이 없으면 기준 미설정으로 표시한다")
	void givenRequirementOnlyAndLotOnly_whenGetInventory_thenShowsShortageAndRequirementNotSet() throws Exception {
		stockRequirementRepository.save(new StockRequirement(5, ship, morphine));
		saveLot(aspirin, "A", 5, "2027-01-31");

		mockMvc.perform(get("/api/ships/" + ship.getId() + "/inventory").with(user(CustomUserDetails.from(officer))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0].medicationId").value(morphine.getId()))
				.andExpect(jsonPath("$[0].available").value(0))
				.andExpect(jsonPath("$[0].requirementSet").value(true))
				.andExpect(jsonPath("$[0].minQuantity").value(5))
				.andExpect(jsonPath("$[0].shortage").value(true))
				.andExpect(jsonPath("$[1].medicationId").value(aspirin.getId()))
				.andExpect(jsonPath("$[1].available").value(5))
				.andExpect(jsonPath("$[1].requirementSet").value(false))
				.andExpect(jsonPath("$[1].minQuantity").value(nullValue()))
				.andExpect(jsonPath("$[1].shortage").value(nullValue()));
	}

	@Test
	@DisplayName("만료·폐기·잔량 0 로트는 빼고 합산하고, 오늘부터 30일째까지 만료되는 로트만 FEFO 순으로 임박에 넣는다")
	void givenLotsAroundBoundary_whenGetInventory_thenSumsAvailableAndListsExpiringSoon() throws Exception {
		saveLot(aspirin, "W", 10, "2026-07-21");
		StockLot lotX = saveLot(aspirin, "X", 10, "2026-07-22");
		StockLot lotY = saveLot(aspirin, "Y", 10, "2026-08-21");
		saveLot(aspirin, "Z", 10, "2026-08-22");
		saveLot(aspirin, "EMPTY", 10, "2026-08-01").subtractQuantity(10);
		StockLot discarded = saveLot(aspirin, "DISCARDED", 10, "2026-08-01");
		ReflectionTestUtils.setField(discarded, "status", LotStatus.DISCARDED);
		stockLotRepository.flush();

		mockMvc.perform(get("/api/ships/" + ship.getId() + "/inventory").with(user(admin())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].available").value(30))
				.andExpect(jsonPath("$[0].expiringSoon.length()").value(2))
				.andExpect(jsonPath("$[0].expiringSoon[0].lotId").value(lotX.getId()))
				.andExpect(jsonPath("$[0].expiringSoon[1].lotId").value(lotY.getId()));
	}

	@Test
	@DisplayName("관리자가 없는 선박을 조회하면 404")
	void givenAdminAndMissingShip_whenGetInventory_thenReturns404() throws Exception {
		mockMvc.perform(get("/api/ships/" + Long.MAX_VALUE + "/inventory").with(user(admin())))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error").value("SHIP_NOT_FOUND"));
	}

	private StockLot saveLot(Medication medication, String lotNumber, int quantity, String expiryDate) {
		return stockLotRepository.save(new StockLot(lotNumber, quantity, LocalDate.parse(expiryDate), RECEIVED_AT,
				officer, ship, medication));
	}

	private CustomUserDetails admin() {
		User admin = new User("inventory_admin", "관리자", "encoded", Role.ADMIN, null);
		ReflectionTestUtils.setField(admin, "id", 10L);
		return CustomUserDetails.from(admin);
	}
}
