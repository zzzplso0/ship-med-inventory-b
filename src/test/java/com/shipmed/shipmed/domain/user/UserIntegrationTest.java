package com.shipmed.shipmed.domain.user;

import com.jayway.jsonpath.JsonPath;
import com.shipmed.shipmed.TestcontainersConfiguration;
import com.shipmed.shipmed.domain.ship.entity.Ship;
import com.shipmed.shipmed.domain.ship.repository.ShipRepository;
import com.shipmed.shipmed.domain.user.entity.Role;
import com.shipmed.shipmed.domain.user.entity.User;
import com.shipmed.shipmed.domain.user.repository.UserRepository;
import com.shipmed.shipmed.global.security.principal.CustomUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.Mockito.doReturn;
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
class UserIntegrationTest {

	private static final String LOGIN_ID = "officer_1";
	private static final String PASSWORD = "Abc123!@";

	@Autowired
	private WebApplicationContext context;

	@MockitoSpyBean
	private UserRepository userRepository;

	@Autowired
	private ShipRepository shipRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	private MockMvc mockMvc;
	private CustomUserDetails admin;
	private Ship ship;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

		ship = shipRepository.save(new Ship("선박"));

		User adminUser = new User("login-10", "이름", "encoded", Role.ADMIN, null);
		ReflectionTestUtils.setField(adminUser, "id", 10L);
		admin = CustomUserDetails.from(adminUser);
	}

	// 등록
	@Test
	@DisplayName("의무사관을 등록하면 201이고 비밀번호는 해시로 저장되며 응답에 없다")
	void givenOfficerBody_whenCreate_thenReturns201AndSavesEncodedPassword() throws Exception {
		String response = mockMvc.perform(createRequest(body(LOGIN_ID, "MEDICAL_OFFICER", PASSWORD, ship.getId())))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.shipId").value(ship.getId()))
				.andExpect(jsonPath("$.password").doesNotExist())
				.andReturn().getResponse().getContentAsString();

		Number id = JsonPath.read(response, "$.id");
		User saved = userRepository.findById(id.longValue()).orElseThrow();
		assertThat(passwordEncoder.matches(PASSWORD, saved.getPassword())).isTrue();
	}

	@Test
	@DisplayName("관리자는 shipId 없이 등록된다")
	void givenAdminBody_whenCreate_thenReturns201WithoutShipId() throws Exception {
		mockMvc.perform(createRequest(body("admin_1", "ADMIN", PASSWORD, null)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.shipId").doesNotExist());
	}

	@ParameterizedTest
	@ValueSource(strings = {"Abc123!@", "Abc123!@Abc123!@Abc1", "ABC123!@"})
	@DisplayName("8자, 20자, 대문자만 있는 영문 비밀번호는 201")
	void givenValidPassword_whenCreate_thenReturns201(String password) throws Exception {
		mockMvc.perform(createRequest(body(LOGIN_ID, "MEDICAL_OFFICER", password, ship.getId())))
				.andExpect(status().isCreated());
	}

	@ParameterizedTest
	@ValueSource(strings = {"Abc12!@", "Abc123!@Abc123!@Abc12", "12345!@#", "abcdefg!", "abcd1234", "abc123!^"})
	@DisplayName("길이, 문자 조합, 허용 문자를 위반한 비밀번호는 400")
	void givenInvalidPassword_whenCreate_thenReturns400(String password) throws Exception {
		mockMvc.perform(createRequest(body(LOGIN_ID, "MEDICAL_OFFICER", password, ship.getId())))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors.password").exists());
	}

	@ParameterizedTest
	@MethodSource("invalidBodies")
	@DisplayName("입력 규칙을 위반하면 400이고 저장되지 않는다")
	void givenInvalidBody_whenCreate_thenReturns400AndDoesNotSave(String body) throws Exception {
		long before = userRepository.count();

		mockMvc.perform(createRequest(body))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("INVALID_INPUT"));

		assertThat(userRepository.count()).isEqualTo(before);
	}

	static Stream<String> invalidBodies() {
		return Stream.of(
				"{\"name\":\"이름\",\"role\":\"ADMIN\",\"password\":\"Abc123!@\"}",
				body("a".repeat(31), "ADMIN", PASSWORD, null),
				body(LOGIN_ID, "MEDICAL_OFFICER", PASSWORD, null),
				body("admin_1", "ADMIN", PASSWORD, 1L),
				body(LOGIN_ID, "CAPTAIN", PASSWORD, null)
		);
	}

	@Test
	@DisplayName("없는 선박이면 404")
	void givenMissingShip_whenCreate_thenReturns404() throws Exception {
		mockMvc.perform(createRequest(body(LOGIN_ID, "MEDICAL_OFFICER", PASSWORD, Long.MAX_VALUE)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error").value("SHIP_NOT_FOUND"));
	}

	@Test
	@DisplayName("로그인 ID가 중복이면 409이고 계정이 추가되지 않는다")
	void givenExistingLoginId_whenCreate_thenReturns409AndDoesNotSave() throws Exception {
		saveOfficer();
		long before = userRepository.count();

		mockMvc.perform(createRequest(body(LOGIN_ID, "MEDICAL_OFFICER", PASSWORD, ship.getId())))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("DUPLICATE_LOGIN_ID"));

		assertThat(userRepository.count()).isEqualTo(before);
	}

	@Test
	@DisplayName("중복 확인을 통과한 뒤 uq_user_login을 위반해도 409")
	void givenLoginIdViolationAfterCheck_whenCreate_thenReturns409() throws Exception {
		saveOfficer();
		doReturn(false).when(userRepository).existsByLoginId(LOGIN_ID);

		mockMvc.perform(createRequest(body(LOGIN_ID, "MEDICAL_OFFICER", PASSWORD, ship.getId())))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("DUPLICATE_LOGIN_ID"));
	}

	@Test
	@DisplayName("없는 선박과 중복 로그인 ID가 겹치면 404가 먼저다")
	void givenMissingShipAndExistingLoginId_whenCreate_thenReturns404() throws Exception {
		saveOfficer();

		mockMvc.perform(createRequest(body(LOGIN_ID, "MEDICAL_OFFICER", PASSWORD, Long.MAX_VALUE)))
				.andExpect(status().isNotFound());
	}

	// 목록
	@Test
	@DisplayName("계정 목록을 페이지 응답으로 조회하고 비밀번호는 응답하지 않는다")
	void givenAdmin_whenGetUsers_thenReturnsPageResponseWithoutPassword() throws Exception {
		saveOfficer();

		mockMvc.perform(get("/api/users").with(user(admin)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[?(@.loginId == '" + LOGIN_ID + "')].shipId")
						.value(hasItem(ship.getId().intValue())))
				.andExpect(jsonPath("$.content[0].password").doesNotExist());
	}

	private void saveOfficer() {
		userRepository.saveAndFlush(new User(LOGIN_ID, "의무사관", "encoded", Role.MEDICAL_OFFICER, ship));
	}

	private MockHttpServletRequestBuilder createRequest(String body) {
		return post("/api/users").contentType(MediaType.APPLICATION_JSON).content(body).with(user(admin));
	}

	private static String body(String loginId, String role, String password, Long shipId) {
		return "{\"loginId\":\"%s\",\"name\":\"이름\",\"role\":\"%s\",\"password\":\"%s\",\"shipId\":%s}"
				.formatted(loginId, role, password, shipId);
	}
}
