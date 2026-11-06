package com.shipmed.shipmed.global.security;

import com.shipmed.shipmed.TestcontainersConfiguration;
import com.shipmed.shipmed.domain.ship.entity.Ship;
import com.shipmed.shipmed.domain.user.entity.Role;
import com.shipmed.shipmed.domain.user.entity.User;
import com.shipmed.shipmed.global.security.principal.CustomUserDetails;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, AuthorizationIntegrationTest.TestShipController.class})
@SpringBootTest
class AuthorizationIntegrationTest {

	private static final String VALID_BODY = "{\"quantity\":1}";
	private static final String INVALID_BODY = "{}";

	@Autowired
	private WebApplicationContext context;

	private MockMvc mockMvc;
	private CustomUserDetails admin;
	private CustomUserDetails officerOfShip1;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

		Ship ship1 = new Ship("선박1");
		ReflectionTestUtils.setField(ship1, "id", 1L);

		admin = principal(10L, Role.ADMIN, null);
		officerOfShip1 = principal(20L, Role.MEDICAL_OFFICER, ship1);
	}

	@Test
	@DisplayName("미로그인 요청은 401")
	void givenNotLoggedIn_whenRequestShipApi_thenReturns401() throws Exception {
		mockMvc.perform(get("/api/ships/1/inventory"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
	}

	@Test
	@DisplayName("의무사관이 관리자 API를 호출하면 403")
	void givenOfficer_whenCallAdminApi_thenReturns403() throws Exception {
		expectAccessDenied(post("/api/users"), officerOfShip1);
		expectAccessDenied(get("/api/users"), officerOfShip1);
		expectAccessDenied(post("/api/ships"), officerOfShip1);
		expectAccessDenied(post("/api/medications"), officerOfShip1);
		expectAccessDenied(put("/api/ships/1/requirements/1"), officerOfShip1);
	}

	@Test
	@DisplayName("관리자가 의무사관 API를 호출하면 403")
	void givenAdmin_whenCallOfficerApi_thenReturns403() throws Exception {
		expectAccessDenied(jsonPost("/api/ships/1/lots", VALID_BODY), admin);
		expectAccessDenied(jsonPost("/api/ships/1/usages", VALID_BODY), admin);
		expectAccessDenied(post("/api/usages/1/cancellation"), admin);
	}

	@Test
	@DisplayName("의무사관이 다른 선박, 없는 선박, 잘못된 형식의 선박을 요청하면 403")
	void givenOfficer_whenRequestOtherShip_thenReturns403() throws Exception {
		expectAccessDenied(get("/api/ships/2/inventory"), officerOfShip1);
		expectAccessDenied(get("/api/ships/999/inventory"), officerOfShip1);
		expectAccessDenied(get("/api/ships/abc/inventory"), officerOfShip1);
	}

	@Test
	@DisplayName("다른 선박에 잘못된 본문을 보내면 400이 아니라 403")
	void givenOtherShipAndInvalidBody_whenPostUsage_thenReturns403() throws Exception {
		expectAccessDenied(jsonPost("/api/ships/2/usages", INVALID_BODY), officerOfShip1);
	}

	@Test
	@DisplayName("자기 선박에 잘못된 본문을 보내면 400")
	void givenOwnShipAndInvalidBody_whenPostUsage_thenReturns400() throws Exception {
		mockMvc.perform(jsonPost("/api/ships/1/usages", INVALID_BODY).with(user(officerOfShip1)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("INVALID_INPUT"));
	}

	@Test
	@DisplayName("의무사관은 자기 선박을 요청할 수 있다")
	void givenOfficer_whenRequestOwnShip_thenReturns200() throws Exception {
		mockMvc.perform(get("/api/ships/1/inventory").with(user(officerOfShip1)))
				.andExpect(status().isOk());
	}

	@Test
	@DisplayName("관리자는 소속과 무관하게 선박을 조회할 수 있다")
	void givenAdmin_whenRequestAnyShip_thenReturns200() throws Exception {
		mockMvc.perform(get("/api/ships/2/inventory").with(user(admin)))
				.andExpect(status().isOk());
	}

	@Test
	@DisplayName("컨트롤러가 없는 경로는 선박과 무관하게 404")
	void givenUnmappedPath_whenRequest_thenReturns404() throws Exception {
		mockMvc.perform(get("/api/ships/2/unknown").with(user(officerOfShip1)))
				.andExpect(status().isNotFound());
	}

	private void expectAccessDenied(MockHttpServletRequestBuilder request, CustomUserDetails principal) throws Exception {
		mockMvc.perform(request.with(user(principal)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error").value("ACCESS_DENIED"));
	}

	private MockHttpServletRequestBuilder jsonPost(String url, String body) {
		return post(url).contentType(MediaType.APPLICATION_JSON).content(body);
	}

	private CustomUserDetails principal(Long userId, Role role, Ship ship) {
		User user = new User("login-" + userId, "이름", "encoded", role, ship);
		ReflectionTestUtils.setField(user, "id", userId);
		return CustomUserDetails.from(user);
	}

	// 선박 API가 아직 없어 인터셉터와 @Valid의 순서를 확인할 수 있는 테스트 전용 컨트롤러.
	@RestController
	static class TestShipController {

		@GetMapping("/api/ships/{shipId}/inventory")
		void inventory(@PathVariable Long shipId) {
		}

		@PostMapping("/api/ships/{shipId}/usages")
		void usages(@PathVariable Long shipId, @Valid @RequestBody TestRequest request) {
		}
	}

	record TestRequest(@NotNull Integer quantity) {
	}
}
