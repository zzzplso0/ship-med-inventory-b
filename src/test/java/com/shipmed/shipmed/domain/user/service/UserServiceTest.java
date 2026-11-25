package com.shipmed.shipmed.domain.user.service;

import com.shipmed.shipmed.domain.ship.entity.Ship;
import com.shipmed.shipmed.domain.ship.repository.ShipRepository;
import com.shipmed.shipmed.domain.user.dto.UserMapperImpl;
import com.shipmed.shipmed.domain.user.dto.request.UserCreateRequest;
import com.shipmed.shipmed.domain.user.dto.request.UserEmailUpdateRequest;
import com.shipmed.shipmed.domain.user.dto.response.UserDto;
import com.shipmed.shipmed.domain.user.entity.Role;
import com.shipmed.shipmed.domain.user.entity.User;
import com.shipmed.shipmed.domain.user.exception.UserEmailDuplicateException;
import com.shipmed.shipmed.domain.user.exception.UserNotFoundException;
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
import static org.mockito.Mockito.never;
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
		@DisplayName("비밀번호를 해시하고 이메일을 정규화해 저장하고 소속 선박을 응답한다")
		void givenOfficerRequest_whenCreate_thenSavesEncodedPasswordAndNormalizedEmail() {
			Ship ship = new Ship("선박");
			ReflectionTestUtils.setField(ship, "id", 1L);
			given(shipRepository.findById(1L)).willReturn(Optional.of(ship));
			given(passwordEncoder.encode(PASSWORD)).willReturn("encoded");

			UserDto result = userService.create(new UserCreateRequest("officer_1", " Officer1@Example.com ", "이름", Role.MEDICAL_OFFICER, PASSWORD, 1L));

			ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
			verify(userRepository).saveAndFlush(captor.capture());
			assertThat(captor.getValue().getPassword()).isEqualTo("encoded");
			assertThat(captor.getValue().getEmail()).isEqualTo("officer1@example.com");
			assertThat(result.shipId()).isEqualTo(1L);
		}

		@Test
		@DisplayName("로그인 ID 외의 제약 위반은 409로 바꾸지 않고 그대로 던진다")
		void givenOtherConstraintViolation_whenCreate_thenRethrows() {
			DataIntegrityViolationException violation = new DataIntegrityViolationException(
					"위반", new ConstraintViolationException("위반", null, "chk_user_ship"));
			given(passwordEncoder.encode(PASSWORD)).willReturn("encoded");
			given(userRepository.saveAndFlush(any(User.class))).willThrow(violation);

			assertThatThrownBy(() -> userService.create(new UserCreateRequest("admin_1", "admin1@example.com", "이름", Role.ADMIN, PASSWORD, null)))
					.isSameAs(violation);
		}
	}

	@Nested
	@DisplayName("계정 이메일 수정")
	class UpdateEmail {
		@Test
		@DisplayName("정규화한 값이 지금 이메일과 같으면 중복 확인 없이 그대로 응답한다")
		void givenSameEmail_whenUpdateEmail_thenReturnsWithoutDuplicateCheck() {
			User user = new User("officer_1", "officer1@example.com", "이름", "encoded", Role.MEDICAL_OFFICER, null);
			given(userRepository.findById(1L)).willReturn(Optional.of(user));

			UserDto result = userService.updateEmail(1L, new UserEmailUpdateRequest(" Officer1@Example.com "));

			assertThat(result.email()).isEqualTo("officer1@example.com");
			verify(userRepository, never()).existsByEmail(any());
		}

		@Test
		@DisplayName("다른 계정이 쓰는 이메일이면 UserEmailDuplicateException")
		void givenExistingEmail_whenUpdateEmail_thenThrowsDuplicate() {
			User user = new User("officer_2", null, "이름", "encoded", Role.MEDICAL_OFFICER, null);
			given(userRepository.findById(2L)).willReturn(Optional.of(user));
			given(userRepository.existsByEmail("officer1@example.com")).willReturn(true);

			assertThatThrownBy(() -> userService.updateEmail(2L, new UserEmailUpdateRequest("Officer1@Example.com")))
					.isInstanceOf(UserEmailDuplicateException.class);
		}

		@Test
		@DisplayName("없는 계정이면 UserNotFoundException")
		void givenMissingUser_whenUpdateEmail_thenThrowsNotFound() {
			given(userRepository.findById(99L)).willReturn(Optional.empty());

			assertThatThrownBy(() -> userService.updateEmail(99L, new UserEmailUpdateRequest("new@example.com")))
					.isInstanceOf(UserNotFoundException.class);
		}
	}

	@Nested
	@DisplayName("계정 목록")
	class GetAllUsers {
		@Test
		@DisplayName("요청한 페이지를 id 오름차순으로 조회하고 페이지 응답으로 변환한다")
		void givenPageQuery_whenGetAllUsers_thenQueriesSortedByIdAndReturnsPageResponse() {
			Pageable pageable = PageRequest.of(1, 2, Sort.by("id"));
			User user = new User("admin_1", null, "이름", "encoded", Role.ADMIN, null);
			ReflectionTestUtils.setField(user, "id", 3L);
			given(userRepository.findAll(pageable)).willReturn(new PageImpl<>(List.of(user), pageable, 3));

			PageResponse<UserDto> result = userService.getAllUsers(new PageQuery(1, 2));

			assertThat(result.content()).containsExactly(new UserDto(3L, "admin_1", "이름", Role.ADMIN, null, null));
			assertThat(result.totalElements()).isEqualTo(3);
		}
	}
}
