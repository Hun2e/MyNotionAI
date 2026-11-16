package com.mynotionai.config;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ActuatorEndpointTest.RANDOM_MANAGEMENT_PORT_PROPERTY
)
@ActiveProfiles("test")
@AutoConfigureObservability
class ActuatorEndpointTest {

    static final String RANDOM_MANAGEMENT_PORT_PROPERTY = "management.server.port=0";
    private static final String LOCALHOST_URL = "http://localhost:";
    private static final String API_PATH = "/api";
    private static final String MONTHLY_EVENTS_PATH = "/calendar/events?year=%d&month=%d";
    private static final String HEALTH_PATH = "/actuator/health";
    private static final String PROMETHEUS_PATH = "/actuator/prometheus";
    private static final String HTTP_REQUEST_HISTOGRAM_METRIC = "http_server_requests_seconds_bucket";
    private static final String HIKARI_ACTIVE_CONNECTIONS_METRIC = "hikaricp_connections_active";

    @Autowired
    private TestRestTemplate restTemplate;

    @LocalServerPort
    private int serverPort;

    @LocalManagementPort
    private int managementPort;

    @Test
    @DisplayName("관리 포트의 health는 인증 없이 UP 상태를 반환하고 상세 정보를 숨긴다")
    void managementHealthReturnsUpWithoutDetails() {
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(
            managementUrl(HEALTH_PATH), JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().path("status").asText()).isEqualTo("UP");
        assertThat(response.getBody().has("components")).isFalse();
        assertThat(response.getBody().has("details")).isFalse();
    }

    @Test
    @DisplayName("관리 포트의 Prometheus는 JVM 지표와 애플리케이션 태그를 반환한다")
    void managementPrometheusExposesJvmMetricsAndApplicationTag() {
        ResponseEntity<String> response = restTemplate.getForEntity(
            managementUrl(PROMETHEUS_PATH), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains(
            "jvm_memory_used_bytes", "application=\"mynotion-backend\"");
    }

    @Test
    @DisplayName("API 포트의 Prometheus 요청은 403으로 차단된다")
    void apiPortDoesNotExposePrometheus() {
        ResponseEntity<String> response = restTemplate.getForEntity(
            apiUrl(PROMETHEUS_PATH), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("API 요청 후 관리 포트의 Prometheus는 HTTP 히스토그램과 Hikari 지표를 반환한다")
    void managementPrometheusExposesHttpAndHikariMetricsAfterApiRequest() {
        YearMonth currentMonth = YearMonth.now();
        String eventsPath = MONTHLY_EVENTS_PATH.formatted(
            currentMonth.getYear(), currentMonth.getMonthValue());
        ResponseEntity<String> apiResponse = restTemplate.getForEntity(
            apiUrl(eventsPath), String.class);

        assertThat(apiResponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<String> metricsResponse = restTemplate.getForEntity(
            managementUrl(PROMETHEUS_PATH), String.class);

        assertThat(metricsResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(metricsResponse.getBody()).contains(
            HTTP_REQUEST_HISTOGRAM_METRIC, HIKARI_ACTIVE_CONNECTIONS_METRIC);
    }

    @Test
    @DisplayName("관리 포트의 info와 metrics는 인증 없이 접근할 수 없다")
    void otherManagementEndpointsAreDenied() {
        for (String path : new String[]{"/actuator/info", "/actuator/metrics"}) {
            ResponseEntity<String> response = restTemplate.getForEntity(
                managementUrl(path), String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }
    }

    private String managementUrl(String path) {
        return LOCALHOST_URL + managementPort + path;
    }

    private String apiUrl(String path) {
        return LOCALHOST_URL + serverPort + API_PATH + path;
    }
}
