package com.shipmed.shipmed.domain.medication;

import com.jayway.jsonpath.JsonPath;
import com.shipmed.shipmed.TestcontainersConfiguration;
import com.shipmed.shipmed.domain.medication.entity.Medication;
import com.shipmed.shipmed.domain.medication.repository.MedicationRepository;
import com.shipmed.shipmed.domain.ship.entity.Ship;
import com.shipmed.shipmed.domain.user.entity.Role;
import com.shipmed.shipmed.domain.user.entity.User;
import com.shipmed.shipmed.global.security.principal.CustomUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@Transactional
@SpringBootTest
class MedicationIntegrationTest {

	private static final String VALID_BODY = "{\"name\":\"약품A\",\"category\":\"진통제\",\"unit\":\"정\"}";

	@Autowired
	private WebApplicationContext context;

	@Autowired
	private MedicationRepository medicationRepository;

	private MockMvc mockMvc;
	private CustomUserDetails admin;
	private CustomUserDetails officer;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

		Ship ship = new Ship("선박");
		ReflectionTestUtils.setField(ship, "id", 1L);

		admin = principal(10L, Role.ADMIN, null);
		officer = principal(20L, Role.MEDICAL_OFFICER, ship);
	}

	// 등록
	@Test
	@DisplayName("관리자가 약품을 등록하면 201과 등록한 값을 응답하고 저장한다")
	void givenAdminAndValidBody_whenCreate_thenReturns201AndSavesMedication() throws Exception {
		String response = mockMvc.perform(createRequest(VALID_BODY).with(user(admin)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.name").value("약품A"))
				.andExpect(jsonPath("$.category").value("진통제"))
				.andExpect(jsonPath("$.unit").value("정"))
				.andReturn().getResponse().getContentAsString();

		Number id = JsonPath.read(response, "$.id");
		Medication saved = medicationRepository.findById(id.longValue()).orElseThrow();
		assertThat(saved.getName()).isEqualTo("약품A");
		assertThat(saved.getCategory()).isEqualTo("진통제");
		assertThat(saved.getUnit()).isEqualTo("정");
	}

	@Test
	@DisplayName("category는 없어도 등록된다")
	void givenNoCategory_whenCreate_thenReturns201() throws Exception {
		mockMvc.perform(createRequest("{\"name\":\"약품A\",\"unit\":\"정\"}").with(user(admin)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.category").doesNotExist());
	}

	@Test
	@DisplayName("의무사관이 약품을 등록하면 403이고 저장되지 않는다")
	void givenOfficer_whenCreate_thenReturns403AndDoesNotSave() throws Exception {
		long before = medicationRepository.count();

		mockMvc.perform(createRequest(VALID_BODY).with(user(officer)))
				.andExpect(status().isForbidden());

		assertThat(medicationRepository.count()).isEqualTo(before);
	}

	@ParameterizedTest
	@MethodSource("invalidBodies")
	@DisplayName("필수값이 없거나 category가 공백이거나 길이를 초과하면 400이고 저장되지 않는다")
	void givenInvalidBody_whenCreate_thenReturns400AndDoesNotSave(String body, String field) throws Exception {
		long before = medicationRepository.count();

		mockMvc.perform(createRequest(body).with(user(admin)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors." + field).exists());

		assertThat(medicationRepository.count()).isEqualTo(before);
	}

	static Stream<Arguments> invalidBodies() {
		return Stream.of(
				Arguments.of("{\"unit\":\"정\"}", "name"),
				Arguments.of("{\"name\":\"약품A\"}", "unit"),
				Arguments.of("{\"name\":\"" + "가".repeat(51) + "\",\"unit\":\"정\"}", "name"),
				Arguments.of("{\"name\":\"약품A\",\"category\":\"\",\"unit\":\"정\"}", "category"),
				Arguments.of("{\"name\":\"약품A\",\"category\":\"   \",\"unit\":\"정\"}", "category"),
				Arguments.of("{\"name\":\"약품A\",\"category\":\"" + "가".repeat(31) + "\",\"unit\":\"정\"}", "category"),
				Arguments.of("{\"name\":\"약품A\",\"unit\":\"" + "가".repeat(21) + "\"}", "unit")
		);
	}

	// 목록
	@Test
	@DisplayName("의무사관도 약품 목록을 페이지 응답으로 조회할 수 있다")
	void givenOfficer_whenGetMedications_thenReturnsPageResponse() throws Exception {
		Medication first = medicationRepository.save(new Medication("약품1", "진통제", "정"));
		medicationRepository.save(new Medication("약품2", null, "병"));

		mockMvc.perform(get("/api/medications").param("size", "1").with(user(officer)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content.length()").value(1))
				.andExpect(jsonPath("$.content[0].id").value(first.getId()))
				.andExpect(jsonPath("$.content[0].name").value("약품1"))
				.andExpect(jsonPath("$.content[0].category").value("진통제"))
				.andExpect(jsonPath("$.content[0].unit").value("정"))
				.andExpect(jsonPath("$.totalElements").value(2))
				.andExpect(jsonPath("$.hasNext").value(true));
	}

	private MockHttpServletRequestBuilder createRequest(String body) {
		return post("/api/medications").contentType(MediaType.APPLICATION_JSON).content(body);
	}

	private CustomUserDetails principal(Long userId, Role role, Ship ship) {
		User user = new User("login-" + userId, "이름", "encoded", role, ship);
		ReflectionTestUtils.setField(user, "id", userId);
		return CustomUserDetails.from(user);
	}
}
