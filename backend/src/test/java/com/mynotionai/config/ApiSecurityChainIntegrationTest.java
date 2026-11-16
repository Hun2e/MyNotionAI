package com.mynotionai.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.mynotionai.dto.AuthResponse;
import com.mynotionai.dto.UserLoginRequest;
import com.mynotionai.dto.UserRegisterRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.time.YearMonth;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ApiSecurityChainIntegrationTest.RANDOM_MANAGEMENT_PORT_PROPERTY
)
@ActiveProfiles("test")
@AutoConfigureObservability
class ApiSecurityChainIntegrationTest {

    static final String RANDOM_MANAGEMENT_PORT_PROPERTY = "management.server.port=0";
    private static final String LOCALHOST_URL = "http://localhost:";
    private static final String API_PATH = "/api";
    private static final String CALENDAR_EVENTS_PATH = "/calendar/events";
    private static final String MONTHLY_EVENTS_PATH = CALENDAR_EVENTS_PATH + "?year=%d&month=%d";
    private static final String REGISTER_PATH = "/auth/register";
    private static final String LOGIN_PATH = "/auth/login";
    private static final String ALLOWED_ORIGIN = "http://localhost:5173";
    private static final String TEST_PASSWORD = "chain-test-password";
    private static final String TEST_NICKNAME = "보안 체인 테스트";
    private static final String EMAIL_PREFIX = "chain-";
    private static final String EMAIL_DOMAIN = "@mynotion.test";
    private static final String BEARER_PREFIX = "Bearer ";

    @Autowired
    private TestRestTemplate restTemplate;

    @LocalServerPort
    private int serverPort;

    @Test
    @DisplayName("익명 사용자의 일정 조회는 API 보안 체인에서 403으로 차단된다")
    void anonymousCalendarRequestIsForbidden() {
        ResponseEntity<String> response = restTemplate.getForEntity(
            monthlyEventsUrl(), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("회원가입과 로그인은 토큰을 반환하고 로그인 토큰으로 일정을 조회할 수 있다")
    void registeredUserCanLoginAndReadCalendarWithJwt() {
        String email = EMAIL_PREFIX + UUID.randomUUID() + EMAIL_DOMAIN;
        ResponseEntity<AuthResponse> registerResponse = register(email);
        assertSuccessfulAuthentication(registerResponse);

        ResponseEntity<AuthResponse> loginResponse = login(email);
        String accessToken = assertSuccessfulAuthentication(loginResponse);
        HttpEntity<Void> request = new HttpEntity<>(authorizationHeaders(accessToken));
        ResponseEntity<JsonNode> response = restTemplate.exchange(
            monthlyEventsUrl(), HttpMethod.GET, request, JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isArray()).isTrue();
        assertThat(response.getBody().isEmpty()).isTrue();
    }

    @Test
    @DisplayName("허용 Origin의 preflight 요청은 API 보안 체인에서 CORS 헤더를 유지한다")
    void allowedOriginPreflightRetainsCorsHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.ORIGIN, ALLOWED_ORIGIN);
        headers.set(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.GET.name());
        headers.set(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, HttpHeaders.AUTHORIZATION);
        HttpEntity<Void> request = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.exchange(
            apiUrl(CALENDAR_EVENTS_PATH), HttpMethod.OPTIONS, request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getFirst(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
            .isEqualTo(ALLOWED_ORIGIN);
        assertThat(response.getHeaders().getAccessControlAllowCredentials()).isTrue();
        assertThat(response.getHeaders().getAccessControlAllowMethods()).contains(HttpMethod.GET);
    }

    private ResponseEntity<AuthResponse> register(String email) {
        UserRegisterRequest request = UserRegisterRequest.builder()
            .email(email)
            .password(TEST_PASSWORD)
            .nickname(TEST_NICKNAME)
            .build();

        return restTemplate.postForEntity(apiUrl(REGISTER_PATH), request, AuthResponse.class);
    }

    private ResponseEntity<AuthResponse> login(String email) {
        UserLoginRequest request = UserLoginRequest.builder()
            .email(email)
            .password(TEST_PASSWORD)
            .build();

        return restTemplate.postForEntity(apiUrl(LOGIN_PATH), request, AuthResponse.class);
    }

    private String assertSuccessfulAuthentication(ResponseEntity<AuthResponse> response) {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getAccessToken()).isNotBlank();
        return response.getBody().getAccessToken();
    }

    private HttpHeaders authorizationHeaders(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, BEARER_PREFIX + accessToken);
        return headers;
    }

    private String monthlyEventsUrl() {
        YearMonth currentMonth = YearMonth.now();
        return apiUrl(MONTHLY_EVENTS_PATH.formatted(
            currentMonth.getYear(), currentMonth.getMonthValue()));
    }

    private String apiUrl(String path) {
        return LOCALHOST_URL + serverPort + API_PATH + path;
    }
}
