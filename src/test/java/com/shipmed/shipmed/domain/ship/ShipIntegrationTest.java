package com.shipmed.shipmed.domain.ship;

import com.jayway.jsonpath.JsonPath;
import com.shipmed.shipmed.TestcontainersConfiguration;
import com.shipmed.shipmed.domain.ship.entity.Ship;
import com.shipmed.shipmed.domain.ship.repository.ShipRepository;
import com.shipmed.shipmed.domain.user.entity.Role;
import com.shipmed.shipmed.domain.user.entity.User;
import com.shipmed.shipmed.global.security.principal.CustomUserDetails;
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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;
import java.util.stream.IntStream;

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
class ShipIntegrationTest {

	@Autowired
	private WebApplicationContext context;

	@Autowired
	private ShipRepository shipRepository;

	private MockMvc mockMvc;
	private CustomUserDetails admin;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

		admin = principal(10L, Role.ADMIN, null);
	}

	// 등록
	@Test
	@DisplayName("관리자가 선박을 등록하면 201과 id, name을 응답하고 저장한다")
	void givenAdminAndValidName_whenCreate_thenReturns201AndSavesShip() throws Exception {
		String response = mockMvc.perform(createRequest("{\"name\":\"선박A\"}").with(user(admin)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.name").value("선박A"))
				.andReturn().getResponse().getContentAsString();

		Number id = JsonPath.read(response, "$.id");
		Ship saved = shipRepository.findById(id.longValue()).orElseThrow();
		assertThat(saved.getName()).isEqualTo("선박A");
	}

	@Test
	@DisplayName("이름이 공백이면 400이고 저장되지 않는다")
	void givenBlankName_whenCreate_thenReturns400AndDoesNotSave() throws Exception {
		long before = shipRepository.count();

		mockMvc.perform(createRequest("{\"name\":\"   \"}").with(user(admin)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors.name").exists());

		assertThat(shipRepository.count()).isEqualTo(before);
	}

	@Test
	@DisplayName("이름이 51자면 400이고 저장되지 않는다")
	void givenNameOf51Chars_whenCreate_thenReturns400AndDoesNotSave() throws Exception {
		long before = shipRepository.count();

		mockMvc.perform(createRequest("{\"name\":\"" + "가".repeat(51) + "\"}").with(user(admin)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors.name").exists());

		assertThat(shipRepository.count()).isEqualTo(before);
	}

	// 목록
	@Test
	@DisplayName("파라미터가 없으면 첫 페이지 20개를 id 오름차순으로 응답")
	void givenNoPageParameters_whenGetShips_thenReturnsFirst20SortedById() throws Exception {
		List<Ship> ships = saveShips();

		mockMvc.perform(get("/api/ships").with(user(admin)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content.length()").value(20))
				.andExpect(jsonPath("$.content[0].id").value(ships.get(0).getId()))
				.andExpect(jsonPath("$.content[0].name").value("선박1"))
				.andExpect(jsonPath("$.content[19].id").value(ships.get(19).getId()))
				.andExpect(jsonPath("$.page").value(0))
				.andExpect(jsonPath("$.size").value(20))
				.andExpect(jsonPath("$.totalElements").value(25))
				.andExpect(jsonPath("$.totalPages").value(2))
				.andExpect(jsonPath("$.hasNext").value(true));
	}

	@Test
	@DisplayName("마지막 페이지는 남은 개수만 응답하고 hasNext는 false")
	void givenLastPage_whenGetShips_thenReturnsRemainingAndNoNext() throws Exception {
		saveShips();

		mockMvc.perform(get("/api/ships").param("page", "1").with(user(admin)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content.length()").value(5))
				.andExpect(jsonPath("$.hasNext").value(false));
	}

	@Test
	@DisplayName("page 상한은 500이 아니라 200과 빈 content")
	void givenMaxPage_whenGetShips_thenReturns200AndEmptyContent() throws Exception {
		saveShips();

		mockMvc.perform(get("/api/ships").param("page", "21474836").param("size", "100").with(user(admin)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content.length()").value(0))
				.andExpect(jsonPath("$.hasNext").value(false));
	}

	@Test
	@DisplayName("범위를 벗어난 page는 400")
	void givenOutOfRangePage_whenGetShips_thenReturns400() throws Exception {
		mockMvc.perform(get("/api/ships").param("page", "-1").with(user(admin)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("INVALID_INPUT"));
	}

	@Test
	@DisplayName("숫자가 아닌 page의 오류 메시지에 내부 타입 정보를 노출하지 않는다")
	void givenNonNumericPage_whenGetShips_thenHidesTypeInfoInMessage() throws Exception {
		mockMvc.perform(get("/api/ships").param("page", "abc").with(user(admin)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors.page").value("형식이 올바르지 않습니다."));
	}

	@Test
	@DisplayName("의무사관도 선박 목록을 조회할 수 있다")
	void givenOfficer_whenGetShips_thenReturns200() throws Exception {
		List<Ship> ships = saveShips();
		CustomUserDetails officer = principal(20L, Role.MEDICAL_OFFICER, ships.get(0));

		mockMvc.perform(get("/api/ships").with(user(officer)))
				.andExpect(status().isOk());
	}

	private List<Ship> saveShips() {
		return shipRepository.saveAll(IntStream.rangeClosed(1, 25).mapToObj(n -> new Ship("선박" + n)).toList());
	}

	private MockHttpServletRequestBuilder createRequest(String body) {
		return post("/api/ships").contentType(MediaType.APPLICATION_JSON).content(body);
	}

	private CustomUserDetails principal(Long userId, Role role, Ship ship) {
		User user = new User("login-" + userId, "이름", "encoded", role, ship);
		ReflectionTestUtils.setField(user, "id", userId);
		return CustomUserDetails.from(user);
	}
}
