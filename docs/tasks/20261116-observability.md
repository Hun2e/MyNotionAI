# Actuator + Prometheus 지표 노출
- 브랜치: feature/observability
- 상태: 대기

## 배경 (왜 하는가)
1. **앱 상태를 볼 수 없다.** 요청 수, 응답 시간, 에러 비율, JVM 메모리, DB 커넥션 풀 사용량 같은 숫자를 지금은 확인할 방법이 없다. 서버가 느려져도 어디가 원인인지 추측할 수밖에 없다.
2. **개선 효과를 숫자로 증명할 수 없다.** 2단계의 Reminder N+1 해결과 대화 로그 페이징을 하더라도, 측정 도구가 없으면 "빨라졌다"를 보여줄 수 없다.
3. 다음 작업(`feature/docker-compose`의 Prometheus·Grafana, `feature/load-test`의 k6)의 **선행 조건**이다. 이번 작업은 앱이 지표를 **내보내는 것**까지만 한다.

→ Spring Boot Actuator와 Micrometer Prometheus registry를 추가해 `/actuator/prometheus`로 지표를 노출한다. 지표 엔드포인트는 **별도 관리 포트(8081)** 로 분리해, 사용자용 API 포트(8080)와 인증 규칙에 섞이지 않게 한다.

## 할 일 (범위)

### 1. 의존성 (`backend/pom.xml`)
- `org.springframework.boot:spring-boot-starter-actuator`
- `io.micrometer:micrometer-registry-prometheus`
- 버전은 Spring Boot 3.2 BOM이 관리하므로 직접 적지 않는다.

### 2. 공통 설정 (`backend/src/main/resources/application.yml`)
```yaml
management:
  server:
    port: 8081            # 관리 포트 분리 (외부 공개 포트 8080과 분리)
  endpoints:
    web:
      exposure:
        include: health, info, prometheus, metrics
  endpoint:
    health:
      probes:
        enabled: true     # /actuator/health/liveness, /readiness (Docker·k8s 헬스체크용)
  metrics:
    tags:
      application: ${spring.application.name}
    distribution:
      percentiles-histogram:
        http.server.requests: true   # Grafana에서 p95/p99 계산용 히스토그램
```
- 관리 포트를 분리하면 `server.servlet.context-path: /api`가 적용되지 않는다. 지표 주소는 `http://localhost:8081/actuator/prometheus`가 된다. 이 동작을 yml 주석(한국어)으로 남긴다.
- `health`는 상세 정보(`show-details`)를 기본값(`never`)으로 둔다. DB 정보 등이 노출되지 않게 하기 위해서다.

### 3. 보안 설정 (`config/SecurityConfig.java`)
- 관리 포트 요청이 기존 `SecurityFilterChain`(JWT 필수)에 막히는지 먼저 확인한다.
  - 막힌다면 **관리 포트 전용** `SecurityFilterChain`을 추가한다. `EndpointRequest.toAnyEndpoint()`로 매칭하고 `@Order`를 기존 체인보다 앞에 둔다. 허용 대상은 `health`, `prometheus`만으로 제한하고, 나머지 actuator 엔드포인트는 `denyAll()`로 막는다.
  - 기존 API 체인(`/auth/**`만 공개)의 동작은 바꾸지 않는다. **8080 포트에서 `/api/actuator/**`가 공개되면 안 된다.**
- 메서드 30줄 이하, 4칸 들여쓰기 규칙을 지킨다.

### 4. 테스트 (`backend/src/test/...`)
- `ActuatorEndpointTest` 추가 (`@SpringBootTest(webEnvironment = RANDOM_PORT)` + test 프로필). 관리 포트도 랜덤으로 띄운다(`management.server.port=0`, `@LocalManagementPort`). 검증 항목:
  - 관리 포트 `GET /actuator/health` → 200, 본문 `status` = `UP`
  - 관리 포트 `GET /actuator/prometheus` → 200, 본문에 `jvm_memory_used_bytes`와 `application="mynotion-backend"` 태그 포함
  - API 포트 `GET /api/actuator/prometheus` → 200이 **아님** (공개되지 않음)
- 상태 코드·포트 같은 숫자는 상수로 정의한다(매직 넘버 금지).

## 하지 않을 일 (범위 밖)
- Docker Compose, Prometheus·Grafana 컨테이너 구성 (다음 작업 `feature/docker-compose`)
- k6 부하 테스트 (`feature/load-test`)
- 비즈니스 커스텀 지표(`@Timed`, `Counter` 등) 추가
- 구조화 로그(JSON), 분산 추적(Tracing)
- 401/403 응답 통일, CORS 환경 변수화 (별도 작업)
- `AGENTS.md`, `README.md` 수정 (Claude가 검증 후 반영)
- 커밋, 브랜치 전환

## 완료 조건 (검증 가능한 항목)
- [ ] `cd backend && JAVA_HOME=$(/usr/libexec/java_home -v 17) mvn test` 전체 통과 (기존 48개 + 신규)
- [ ] `local` 프로필 기동 후 `curl http://localhost:8081/actuator/prometheus` → 200, `http_server_requests_seconds_bucket`·`hikaricp_connections_active`·`jvm_memory_used_bytes` 지표 포함 (API를 한 번 호출한 뒤 확인)
- [ ] `curl http://localhost:8081/actuator/health` → `{"status":"UP"}` (상세 정보 미노출)
- [ ] `curl http://localhost:8080/api/actuator/prometheus` → 공개되지 않음 (403 또는 404)
- [ ] 기존 API 동작 유지: 인증 없는 `GET /api/calendar/events` → 403, `/api/auth/login` 정상
- [ ] 수정·추가 파일: `pom.xml`, `application.yml`, `SecurityConfig.java`, `ActuatorEndpointTest.java` (그 밖의 파일이 필요하면 질문/이슈에 남긴다)

## 참고 파일
- `backend/pom.xml`
- `backend/src/main/resources/application.yml`
- `backend/src/main/java/com/mynotionai/config/SecurityConfig.java`
- `backend/src/main/java/com/mynotionai/security/JwtAuthenticationFilter.java`
- `backend/src/test/resources/application-test.yml`
- `backend/src/test/java/com/mynotionai/controller/CalendarControllerTest.java` (테스트 스타일 참고)

## 질문/이슈 (Codex 작성)
