package com.shipmed.shipmed.domain.auth;

import com.shipmed.shipmed.TestcontainersConfiguration;
import com.shipmed.shipmed.domain.ship.entity.Ship;
import com.shipmed.shipmed.domain.ship.repository.ShipRepository;
import com.shipmed.shipmed.domain.user.entity.Role;
import com.shipmed.shipmed.domain.user.entity.User;
import com.shipmed.shipmed.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@Transactional
@SpringBootTest
class AuthIntegrationTest {

	private static final String LOGIN_ID = "officer_1";
	private static final String PASSWORD = "Abc123!@";

	@Autowired
	private WebApplicationContext context;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private ShipRepository shipRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Value("${shipmed.initial-admin.login-id}")
	private String adminLoginId;

	@Value("${shipmed.initial-admin.password}")
	private String adminPassword;

	private MockMvc mockMvc;
	private Ship ship;
	private User officer;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

		ship = shipRepository.save(new Ship("선박"));
		officer = userRepository.save(
				new User(LOGIN_ID, "의무사관", passwordEncoder.encode(PASSWORD), Role.MEDICAL_OFFICER, ship));
	}

	// 로그인
	@Test
	@DisplayName("로그인에 성공하면 세션을 만들고 계정 정보를 응답한다")
	void givenValidCredentials_whenLogin_thenCreatesSessionAndReturnsUser() throws Exception {
		MockHttpSession session = (MockHttpSession) login(LOGIN_ID, PASSWORD)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.userId").value(officer.getId()))
				.andExpect(jsonPath("$.loginId").value(LOGIN_ID))
				.andExpect(jsonPath("$.role").value("MEDICAL_OFFICER"))
				.andExpect(jsonPath("$.shipId").value(ship.getId()))
				.andExpect(jsonPath("$.password").doesNotExist())
				.andReturn().getRequest().getSession(false);

		assertThat(session).isNotNull();
	}

	@ParameterizedTest
	@CsvSource({"officer_1,Wrong123!@", "no_such_user,Abc123!@"})
	@DisplayName("잘못된 비밀번호와 없는 계정은 같은 401 LOGIN_FAILED")
	void givenInvalidCredentials_whenLogin_thenReturns401LoginFailed(String loginId, String password) throws Exception {
		login(loginId, password)
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error").value("LOGIN_FAILED"))
				.andExpect(jsonPath("$.message").value("로그인 ID 또는 비밀번호가 올바르지 않습니다."));
	}

	@ParameterizedTest
	@ValueSource(strings = {"{\"password\":\"Abc123!@\"}", "{\"loginId\":\"officer_1\"}"})
	@DisplayName("필수값이 없으면 400")
	void givenMissingField_whenLogin_thenReturns400(String body) throws Exception {
		mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("INVALID_INPUT"));
	}

	// 로그아웃
	@Test
	@DisplayName("로그아웃하면 같은 세션의 요청은 401")
	void givenLoggedIn_whenLogout_thenSameSessionReturns401() throws Exception {
		MockHttpSession session = loginSession(LOGIN_ID, PASSWORD);

		mockMvc.perform(get("/api/ships").session(session)).andExpect(status().isOk());
		mockMvc.perform(post("/api/auth/logout").session(session)).andExpect(status().isOk());

		mockMvc.perform(get("/api/ships").session(session)).andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("로그인하지 않고 로그아웃하면 401")
	void givenNotLoggedIn_whenLogout_thenReturns401() throws Exception {
		mockMvc.perform(post("/api/auth/logout"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
	}

	// 비밀번호
	@Test
	@DisplayName("첫 관리자의 비밀번호는 평문이 아닌 해시로 저장된다")
	void givenInitialAdmin_whenFindInDb_thenPasswordIsEncoded() {
		User admin = userRepository.findByLoginId(adminLoginId).orElseThrow();

		assertThat(admin.getPassword()).isNotEqualTo(adminPassword);
		assertThat(passwordEncoder.matches(adminPassword, admin.getPassword())).isTrue();
	}

	// 전체 흐름
	@Test
	@DisplayName("API로 만든 의무사관으로 로그인하면 역할과 소속 선박 권한이 적용된다")
	void givenOfficerCreatedByApi_whenLogin_thenRoleAndShipAccessApply() throws Exception {
		Ship otherShip = shipRepository.save(new Ship("다른 선박"));
		MockHttpSession adminSession = loginSession(adminLoginId, adminPassword);
		String body = "{\"loginId\":\"officer_2\",\"name\":\"이름\",\"role\":\"MEDICAL_OFFICER\",\"password\":\"%s\",\"shipId\":%d}"
				.formatted(PASSWORD, ship.getId());
		mockMvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON).content(body).session(adminSession))
				.andExpect(status().isCreated());

		MockHttpSession session = loginSession("officer_2", PASSWORD);

		mockMvc.perform(request(ship).session(session)).andExpect(status().isOk());
		mockMvc.perform(request(otherShip).session(session)).andExpect(status().isForbidden());
		mockMvc.perform(get("/api/users").session(session)).andExpect(status().isForbidden());
	}

	private MockHttpServletRequestBuilder request(Ship target) {
		return get("/api/ships/" + target.getId() + "/requirements");
	}

	private MockHttpSession loginSession(String loginId, String password) throws Exception {
		return (MockHttpSession) login(loginId, password)
				.andExpect(status().isOk())
				.andReturn().getRequest().getSession(false);
	}

	private ResultActions login(String loginId, String password) throws Exception {
		String body = "{\"loginId\":\"%s\",\"password\":\"%s\"}".formatted(loginId, password);
		return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body));
	}
}
