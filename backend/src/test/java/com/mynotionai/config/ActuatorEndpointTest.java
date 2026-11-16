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
    private static final String HEALTH_PATH = "/actuator/health";
    private static final String PROMETHEUS_PATH = "/actuator/prometheus";

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
    @DisplayName("API 포트에서는 Prometheus가 공개되지 않는다")
    void apiPortDoesNotExposePrometheus() {
        ResponseEntity<String> response = restTemplate.getForEntity(
            LOCALHOST_URL + serverPort + "/api" + PROMETHEUS_PATH, String.class);

        assertThat(response.getStatusCode()).isNotEqualTo(HttpStatus.OK);
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
}
