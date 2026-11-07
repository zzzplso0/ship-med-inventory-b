package com.shipmed.shipmed.domain.auth;

import com.shipmed.shipmed.TestcontainersConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

// MockMvc는 세션 쿠키를 응답에 쓰지 않으므로 실제 서버로 확인한다.
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthSessionCookieTest {

	@LocalServerPort
	private int port;

	@Value("${shipmed.initial-admin.login-id}")
	private String loginId;

	@Value("${shipmed.initial-admin.password}")
	private String password;

	private final HttpClient client = HttpClient.newHttpClient();

	@Test
	@DisplayName("로그인은 세션 쿠키를 발급하고, 로그아웃하면 그 쿠키로 접근할 수 없다")
	void givenLogin_whenLogoutWithCookie_thenCookieNoLongerWorks() throws Exception {
		String body = "{\"loginId\":\"%s\",\"password\":\"%s\"}".formatted(loginId, password);
		HttpResponse<String> login = send(request("/api/auth/login")
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(body)));

		assertThat(login.statusCode()).isEqualTo(200);
		String cookie = login.headers().firstValue("Set-Cookie").orElseThrow().split(";")[0];
		assertThat(cookie).startsWith("JSESSIONID=");

		assertThat(send(request("/api/ships").header("Cookie", cookie).GET()).statusCode()).isEqualTo(200);
		assertThat(send(request("/api/auth/logout").header("Cookie", cookie)
				.POST(HttpRequest.BodyPublishers.noBody())).statusCode()).isEqualTo(200);
		assertThat(send(request("/api/ships").header("Cookie", cookie).GET()).statusCode()).isEqualTo(401);
	}

	private HttpRequest.Builder request(String path) {
		return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
	}

	private HttpResponse<String> send(HttpRequest.Builder builder) throws IOException, InterruptedException {
		return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
	}
}
