package com.shipmed.shipmed.domain.user.service;

import com.shipmed.shipmed.domain.ship.entity.Ship;
import com.shipmed.shipmed.domain.ship.repository.ShipRepository;
import com.shipmed.shipmed.domain.user.dto.UserMapperImpl;
import com.shipmed.shipmed.domain.user.dto.request.UserCreateRequest;
import com.shipmed.shipmed.domain.user.dto.response.UserDto;
import com.shipmed.shipmed.domain.user.entity.Role;
import com.shipmed.shipmed.domain.user.entity.User;
import com.shipmed.shipmed.domain.user.repository.UserRepository;
import com.shipmed.shipmed.global.request.PageQuery;
import com.shipmed.shipmed.global.response.PageResponse;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

	private static final String PASSWORD = "Abc123!@";

	@Mock
	private UserRepository userRepository;

	@Mock
	private ShipRepository shipRepository;

	@Mock
	private PasswordEncoder passwordEncoder;

	private UserService userService;

	@BeforeEach
	void setUp() {
		userService = new UserServiceImpl(userRepository, shipRepository, new UserMapperImpl(), passwordEncoder);
	}

	@Nested
	@DisplayName("계정 등록")
	class Create {
		@Test
		@DisplayName("비밀번호를 해시해 저장하고 소속 선박을 응답한다")
		void givenOfficerRequest_whenCreate_thenSavesEncodedPasswordAndReturnsShipId() {
			Ship ship = new Ship("선박");
			ReflectionTestUtils.setField(ship, "id", 1L);
			given(shipRepository.findById(1L)).willReturn(Optional.of(ship));
			given(passwordEncoder.encode(PASSWORD)).willReturn("encoded");

			UserDto result = userService.create(new UserCreateRequest("officer_1", "이름", Role.MEDICAL_OFFICER, PASSWORD, 1L));

			ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
			verify(userRepository).saveAndFlush(captor.capture());
			assertThat(captor.getValue().getPassword()).isEqualTo("encoded");
			assertThat(result.shipId()).isEqualTo(1L);
		}

		@Test
		@DisplayName("로그인 ID 외의 제약 위반은 409로 바꾸지 않고 그대로 던진다")
		void givenOtherConstraintViolation_whenCreate_thenRethrows() {
			DataIntegrityViolationException violation = new DataIntegrityViolationException(
					"위반", new ConstraintViolationException("위반", null, "chk_user_ship"));
			given(passwordEncoder.encode(PASSWORD)).willReturn("encoded");
			given(userRepository.saveAndFlush(any(User.class))).willThrow(violation);

			assertThatThrownBy(() -> userService.create(new UserCreateRequest("admin_1", "이름", Role.ADMIN, PASSWORD, null)))
					.isSameAs(violation);
		}
	}

	@Nested
	@DisplayName("계정 목록")
	class GetAllUsers {
		@Test
		@DisplayName("요청한 페이지를 id 오름차순으로 조회하고 페이지 응답으로 변환한다")
		void givenPageQuery_whenGetAllUsers_thenQueriesSortedByIdAndReturnsPageResponse() {
			Pageable pageable = PageRequest.of(1, 2, Sort.by("id"));
			User user = new User("admin_1", "이름", "encoded", Role.ADMIN, null);
			ReflectionTestUtils.setField(user, "id", 3L);
			given(userRepository.findAll(pageable)).willReturn(new PageImpl<>(List.of(user), pageable, 3));

			PageResponse<UserDto> result = userService.getAllUsers(new PageQuery(1, 2));

			assertThat(result.content()).containsExactly(new UserDto(3L, "admin_1", "이름", Role.ADMIN, null));
			assertThat(result.totalElements()).isEqualTo(3);
		}
	}
}
