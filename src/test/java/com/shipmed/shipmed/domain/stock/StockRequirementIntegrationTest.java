package com.shipmed.shipmed.domain.stock;

import com.shipmed.shipmed.TestcontainersConfiguration;
import com.shipmed.shipmed.domain.medication.entity.Medication;
import com.shipmed.shipmed.domain.medication.repository.MedicationRepository;
import com.shipmed.shipmed.domain.ship.entity.Ship;
import com.shipmed.shipmed.domain.ship.repository.ShipRepository;
import com.shipmed.shipmed.domain.stock.entity.StockRequirement;
import com.shipmed.shipmed.domain.stock.repository.StockRequirementRepository;
import com.shipmed.shipmed.domain.user.entity.Role;
import com.shipmed.shipmed.domain.user.entity.User;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@Transactional
@SpringBootTest
class StockRequirementIntegrationTest {

	@Autowired
	private WebApplicationContext context;

	@MockitoSpyBean
	private StockRequirementRepository stockRequirementRepository;

	@Autowired
	private ShipRepository shipRepository;

	@Autowired
	private MedicationRepository medicationRepository;

	private MockMvc mockMvc;
	private CustomUserDetails admin;
	private Ship ship;
	private Medication medication;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

		ship = shipRepository.save(new Ship("선박"));
		medication = medicationRepository.save(new Medication("모르핀", null, "앰플"));
		admin = principal(10L, Role.ADMIN, null);
	}

	// 설정
	@Test
	@DisplayName("기준이 없으면 201과 설정한 값을 응답한다")
	void givenNoRequirement_whenPut_thenReturns201() throws Exception {
		mockMvc.perform(putRequest(ship.getId(), medication.getId(), "{\"minQuantity\":5}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.shipId").value(ship.getId()))
				.andExpect(jsonPath("$.medicationId").value(medication.getId()))
				.andExpect(jsonPath("$.minQuantity").value(5))
				.andExpect(jsonPath("$.created").doesNotExist());
	}

	@Test
	@DisplayName("다시 설정하면 200이고 수량이 바뀌며 행은 1개다")
	void givenRequirement_whenPut_thenReturns200AndChangesQuantity() throws Exception {
		saveRequirement();
		long before = stockRequirementRepository.count();

		mockMvc.perform(putRequest(ship.getId(), medication.getId(), "{\"minQuantity\":7}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.minQuantity").value(7));

		assertThat(stockRequirementRepository.count()).isEqualTo(before);
		assertThat(stockRequirementRepository.findByShipIdAndMedicationId(ship.getId(), medication.getId()).getMinQuantity())
				.isEqualTo(7);
	}

	@Test
	@DisplayName("최소 수량 0은 허용한다")
	void givenZeroQuantity_whenPut_thenReturns201() throws Exception {
		mockMvc.perform(putRequest(ship.getId(), medication.getId(), "{\"minQuantity\":0}"))
				.andExpect(status().isCreated());
	}

	@ParameterizedTest
	@ValueSource(strings = {"{}", "{\"minQuantity\":-1}"})
	@DisplayName("최소 수량이 없거나 음수면 400")
	void givenInvalidQuantity_whenPut_thenReturns400(String body) throws Exception {
		mockMvc.perform(putRequest(ship.getId(), medication.getId(), body))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors.minQuantity").exists());
	}

	@Test
	@DisplayName("없는 선박이면 404")
	void givenMissingShip_whenPut_thenReturns404() throws Exception {
		mockMvc.perform(putRequest(Long.MAX_VALUE, medication.getId(), "{\"minQuantity\":5}"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error").value("SHIP_NOT_FOUND"));
	}

	@Test
	@DisplayName("없는 약품이면 404")
	void givenMissingMedication_whenPut_thenReturns404() throws Exception {
		mockMvc.perform(putRequest(ship.getId(), Long.MAX_VALUE, "{\"minQuantity\":5}"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error").value("MEDICATION_NOT_FOUND"));
	}

	@Test
	@DisplayName("기존 행 확인을 통과한 뒤 uq_requirement를 위반하면 409")
	void givenRequirementViolationAfterCheck_whenPut_thenReturns409() throws Exception {
		saveRequirement();
		doReturn(null).when(stockRequirementRepository).findByShipIdAndMedicationId(ship.getId(), medication.getId());

		mockMvc.perform(putRequest(ship.getId(), medication.getId(), "{\"minQuantity\":7}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("REQUIREMENT_CONFLICT"));
	}

	// 목록
	@Test
	@DisplayName("소속 의무사관은 약품 이름이 포함된 페이지 응답을 받는다")
	void givenOfficerOfShip_whenGetRequirements_thenReturnsPageWithMedicationName() throws Exception {
		saveRequirement();
		CustomUserDetails officer = principal(20L, Role.MEDICAL_OFFICER, ship);

		mockMvc.perform(get("/api/ships/" + ship.getId() + "/requirements").with(user(officer)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].medicationId").value(medication.getId()))
				.andExpect(jsonPath("$.content[0].medicationName").value("모르핀"))
				.andExpect(jsonPath("$.content[0].minQuantity").value(5))
				.andExpect(jsonPath("$.totalElements").value(1));
	}

	@Test
	@DisplayName("다른 선박의 의무사관이 조회하면 403")
	void givenOfficerOfOtherShip_whenGetRequirements_thenReturns403() throws Exception {
		Ship otherShip = shipRepository.save(new Ship("다른 선박"));
		CustomUserDetails officer = principal(20L, Role.MEDICAL_OFFICER, otherShip);

		mockMvc.perform(get("/api/ships/" + ship.getId() + "/requirements").with(user(officer)))
				.andExpect(status().isForbidden());
	}

	@Test
	@DisplayName("관리자가 없는 선박을 조회하면 404")
	void givenAdminAndMissingShip_whenGetRequirements_thenReturns404() throws Exception {
		mockMvc.perform(get("/api/ships/" + Long.MAX_VALUE + "/requirements").with(user(admin)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error").value("SHIP_NOT_FOUND"));
	}

	private void saveRequirement() {
		stockRequirementRepository.saveAndFlush(new StockRequirement(5, ship, medication));
	}

	private MockHttpServletRequestBuilder putRequest(Long shipId, Long medicationId, String body) {
		return put("/api/ships/" + shipId + "/requirements/" + medicationId)
				.contentType(MediaType.APPLICATION_JSON).content(body).with(user(admin));
	}

	private CustomUserDetails principal(Long userId, Role role, Ship ship) {
		User user = new User("login-" + userId, "이름", "encoded", role, ship);
		ReflectionTestUtils.setField(user, "id", userId);
		return CustomUserDetails.from(user);
	}
}
