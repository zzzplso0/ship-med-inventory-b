package com.shipmed.shipmed.global.init;

import com.shipmed.shipmed.domain.user.entity.Role;
import com.shipmed.shipmed.domain.user.entity.User;
import com.shipmed.shipmed.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class InitialAdminRunnerTest {

	@Mock
	private UserRepository userRepository;

	@Mock
	private PasswordEncoder passwordEncoder;

	private InitialAdminRunner runner;

	@BeforeEach
	void setUp() {
		runner = new InitialAdminRunner(userRepository, passwordEncoder, "admin", "관리자", "plain");
	}

	@Test
	@DisplayName("관리자가 없으면 해시한 비밀번호로 관리자를 생성한다")
	void givenNoAdmin_whenRun_thenSavesAdminWithEncodedPassword() throws Exception {
		given(userRepository.existsByRole(Role.ADMIN)).willReturn(false);
		given(passwordEncoder.encode("plain")).willReturn("encoded");

		runner.run();

		ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
		verify(userRepository).save(captor.capture());
		assertThat(captor.getValue().getLoginId()).isEqualTo("admin");
		assertThat(captor.getValue().getPassword()).isEqualTo("encoded");
		assertThat(captor.getValue().getRole()).isEqualTo(Role.ADMIN);
		assertThat(captor.getValue().getShip()).isNull();
	}

	@Test
	@DisplayName("관리자가 이미 있으면 추가로 만들지 않는다")
	void givenExistingAdmin_whenRun_thenDoesNotSave() throws Exception {
		given(userRepository.existsByRole(Role.ADMIN)).willReturn(true);

		runner.run();

		verify(userRepository, never()).save(any(User.class));
	}
}
