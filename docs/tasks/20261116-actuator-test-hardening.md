# Actuator 테스트 보강 (리뷰 🟡 권장 3건 반영)
- 브랜치: feature/actuator-test-hardening
- 상태: 완료

## 배경 (왜 하는가)
PR #6(`feature/observability`, Actuator + Prometheus, 관리 포트 8081)은 main에 머지되었다. 코드 리뷰 `docs/reviews/20261116-observability.md`에서 🔴 필수 수정은 없었다. 다만 **테스트가 회귀를 잡지 못한다**는 🟡 권장 3건이 남았다. 검증 기록의 curl 확인은 사람이 한 번 실행한 결과일 뿐이고, 다음 변경에서 다시 깨져도 `mvn test`는 이를 알려 주지 않는다.

| 리뷰 항목 | 지금 테스트가 놓치는 구체적 시나리오 |
|---|---|
| 🟡 1. API 포트 차단 단언이 "200 아님"뿐 | `EndpointRequest` matcher 초기화 오류나 예외 처리 버그로 `/api/actuator/prometheus`가 **500**을 반환해도 테스트가 통과한다. 정상 차단(403)과 서버 장애를 구별하지 못한다. |
| 🟡 2. Prometheus 테스트가 JVM 지표만 확인 | `application.yml`의 `percentiles-histogram.http.server.requests: true`를 실수로 지우거나 Hikari 풀 계측이 빠져도 테스트가 통과한다. 다음 작업(Grafana p95/p99, DB 풀 대시보드)에 필요한 지표가 사라져도 알 수 없다. |
| 🟡 3. 실제 포트에서 다중 보안 체인 회귀 검증 없음 | 관리 체인의 `securityMatcher`나 `@Order`를 잘못 바꾸면, CORS 설정이 없고 `denyAll()`로 끝나는 관리 체인이 API 요청을 가로챌 수 있다. 그러면 로그인한 사용자의 일정 조회가 403이 되거나 preflight 응답에서 CORS 헤더가 사라진다(프론트엔드 전체 장애). 기존 `CalendarControllerTest`는 인증 객체를 미리 넣는 MVC slice 테스트라서 실제 JWT 필터와 체인 선택을 검증하지 못한다. |

→ 운영 코드는 그대로 두고 **테스트만** 보강한다. 학습 포인트: "테스트가 통과한다"와 "테스트가 회귀를 잡는다"는 다르다. 단언을 좁히고, 검증할 상태를 테스트 안에서 직접 만든다.

## 할 일 (범위)

### 테스트 클래스 배치 결정 (PM 판단)
| 항목 | 위치 | 근거 |
|---|---|---|
| 🟡 1, 🟡 2 | 기존 `config/ActuatorEndpointTest.java` 수정 | Actuator 엔드포인트 자체를 검증하는 내용이라 기존 클래스의 책임과 같다. |
| 🟡 3 | **새 클래스** `config/ApiSecurityChainIntegrationTest.java` | ① 검증 대상이 Actuator가 아니라 **API 체인(JWT·CORS)** 이다. ② 회원가입·로그인 헬퍼가 추가되면 `ActuatorEndpointTest`가 길어지고 책임이 섞인다. ③ 애너테이션·속성을 `ActuatorEndpointTest`와 **똑같이** 맞추면 Spring TestContext 캐시가 같은 컨텍스트를 재사용하므로 서버 기동 비용이 추가되지 않는다. |

> 리뷰 문서는 "같은 통합 테스트 클래스"에 넣자고 제안했지만, 위 근거로 클래스를 분리한다. 대신 **관리 포트를 분리한 운영 구조를 똑같이 재현**하는 조건은 반드시 지킨다. 관리 포트가 API 포트와 같으면 `EndpointRequest`가 `/api/actuator/**`를 매칭해 다중 체인 구성이 운영과 달라진다.

### 1. API 포트 차단 단언 좁히기 (`ActuatorEndpointTest.apiPortDoesNotExposePrometheus`)
- `isNotEqualTo(HttpStatus.OK)`를 `isEqualTo(HttpStatus.FORBIDDEN)`으로 바꾼다.
- 403으로 좁히는 근거:
  - API 체인에는 `httpBasic`·`formLogin`이 없다. 그래서 인증되지 않은 요청은 Spring Security 기본 진입점이 **403**으로 막는다.
  - 2026-11-16 검증 기록에서도 `curl http://localhost:8080/api/actuator/prometheus` 결과가 403이었다.
- `@DisplayName`도 "403으로 차단된다"는 의미가 드러나게 고친다.

### 2. HTTP 히스토그램·Hikari 지표 단언 추가 (`ActuatorEndpointTest`)
- 새 테스트 메서드를 추가한다(이름 예: `managementPrometheusExposesHttpAndHikariMetricsAfterApiRequest`). 기존 JVM 지표 테스트는 그대로 둔다(테스트 하나에 관심사 하나).
- **실행 순서에 의존하지 않도록** 같은 메서드 안에서 다음 순서로 진행한다.
  1. API 포트로 요청 1회: 익명 `GET {API}/api/calendar/events?year=&month=` → 403인지 먼저 단언한다.
     - 403 응답도 `http.server.requests`에 기록된다. Spring Boot 3.2.0의 `WebMvcObservationAutoConfiguration`이 관측 필터를 `Ordered.HIGHEST_PRECEDENCE + 1` 순서로 등록하기 때문이다. 이 필터는 Security 필터 체인(`-100`)보다 바깥에서 동작한다(로컬 JAR을 `javap`으로 확인).
  2. 관리 포트에서 `GET /actuator/prometheus`를 호출한다. → 200
  3. 본문에 `http_server_requests_seconds_bucket`, `hikaricp_connections_active`가 들어 있는지 단언한다.
- 특정 `uri`·`status` 라벨 값까지 단언하지 않는다. 라벨 형식은 프레임워크 버전에 따라 달라질 수 있어 테스트가 쉽게 깨진다. 지표 이름이 있는지만 확인한다.
- `year`·`month` 쿼리 값은 숫자를 직접 쓰지 말고 `YearMonth.now()` 등으로 만든다(매직 넘버 금지).

### 3. 다중 보안 체인 회귀 검증 (`ApiSecurityChainIntegrationTest` 신규)
클래스 설정은 `ActuatorEndpointTest`와 같게 맞춘다.
```java
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ApiSecurityChainIntegrationTest.RANDOM_MANAGEMENT_PORT_PROPERTY  // "management.server.port=0"
)
@ActiveProfiles("test")
@AutoConfigureObservability
```
- `TestRestTemplate`, `@LocalServerPort`를 사용한다. URL 형식은 `"http://localhost:" + serverPort + "/api" + 경로`이다.

테스트 메서드 3개:

**(a) 익명 일정 조회는 403**
- `GET /api/calendar/events?year={올해}&month={이번 달}` (Authorization 헤더 없음) → `HttpStatus.FORBIDDEN`

**(b) 회원가입·로그인으로 얻은 JWT로 일정 조회 200**
1. `POST /api/auth/register`, JSON 본문 `{"email", "password", "nickname"}` → 200, 응답 `accessToken`이 비어 있지 않다.
2. `POST /api/auth/login`, JSON 본문 `{"email", "password"}` → 200, 응답 `accessToken`이 비어 있지 않다.
3. 로그인으로 받은 토큰으로 `GET /api/calendar/events?year=&month=` 호출, 헤더 `Authorization: Bearer {accessToken}` → 200. 본문은 JSON 배열이다(새 사용자이므로 빈 배열).
- 요청 DTO는 `com.mynotionai.dto.UserRegisterRequest`, `UserLoginRequest`의 builder를 그대로 써도 된다. 응답은 `AuthResponse`(필드 `accessToken`, `user`) 또는 `JsonNode`로 받는다.
- 입력 검증 조건(`UserRegisterRequest`): `email` 형식, `password` **8자 이상**, `nickname` **2~50자**. 테스트 상수가 이 조건을 만족해야 한다.
- **이메일은 실행마다 고유하게 만든다** (예: `"chain-" + UUID.randomUUID() + "@mynotion.test"`).
  - 테스트 DB `jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1`은 같은 JVM 안의 모든 테스트가 공유한다.
  - RANDOM_PORT 테스트는 요청이 별도 스레드에서 처리되므로 `@Transactional` 롤백도 되지 않는다.
  - 이메일이 중복되면 `EMAIL_ALREADY_EXISTS`(409)로 실패한다.
- `Bearer ` 접두사, `Authorization` 헤더 이름은 `HttpHeaders.AUTHORIZATION`과 상수를 사용한다.

**(c) 허용 Origin의 preflight에 CORS 헤더 유지**
- `OPTIONS /api/calendar/events`를 `restTemplate.exchange(..., HttpMethod.OPTIONS, ...)`로 보낸다. 요청 헤더:
  - `Origin: http://localhost:5173` (`SecurityConfig.corsConfigurationSource()`에 등록된 허용 Origin 4개 중 프론트엔드 개발 서버 주소)
  - `Access-Control-Request-Method: GET`
  - `Access-Control-Request-Headers: Authorization`
- 단언:
  - 상태 200
  - `Access-Control-Allow-Origin` = `http://localhost:5173`
  - `Access-Control-Allow-Credentials` = `true`
  - `Access-Control-Allow-Methods`에 `GET` 포함
- 헤더 이름은 `HttpHeaders.ORIGIN`, `HttpHeaders.ACCESS_CONTROL_*` 상수를 사용한다.
- 주의: JDK 기본 `HttpURLConnection`은 `Origin`, `Access-Control-Request-*`를 "제한 헤더"로 보고 **조용히 버린다**. 그러면 preflight로 인식되지 않아 403이 나온다. 이 증상이 보이면 `pom.xml`을 고치지 말고 질문/이슈에 남긴다(아래 PM 사전 표시 참고).

### 공통 코딩 규칙
- 상태 코드는 `HttpStatus`를 쓴다. 경로·Origin·비밀번호·닉네임·이메일 도메인·포트 속성은 `private static final` 상수로 둔다(매직 넘버 금지).
- 메서드 30줄 이하. 회원가입·로그인·URL 조립·인증 헤더 생성은 private 헬퍼로 분리한다.
- Java 4칸 들여쓰기, `@DisplayName`과 주석은 한국어.
- 커밋은 작은 단위로 나눈다. 예: ① `Actuator 테스트 단언 강화(API 포트 403, HTTP·Hikari 지표)` ② `API 보안 체인 통합 테스트 추가(JWT 인증, CORS preflight)`

## 하지 않을 일 (범위 밖)
- 운영 코드 수정: `SecurityConfig.java`, `JwtAuthenticationFilter.java`, 컨트롤러, 서비스 등
- 설정 수정: `application.yml`, `application-test.yml`, `pom.xml` (의존성 추가 포함)
- CORS 정책 변경, 허용 Origin 환경 변수화
- 401/403 응답 통일, Security 필터 단계의 `ApiErrorResponse` 형식 적용 (4단계 작업)
- 비즈니스 커스텀 지표(`@Timed`, `Counter` 등) 추가
- liveness·readiness 직접 호출 테스트, 허용되지 않은 Origin 거부 테스트 등 리뷰 🟡 3건 밖의 테스트 추가
- 기존 `CalendarControllerTest` 등 다른 테스트 수정
- `AGENTS.md`, `README.md`, 리뷰 문서 수정 (Claude가 검증 후 반영)
- push, PR 생성, main 커밋

## 완료 조건 (검증 가능한 항목)
- [x] `cd backend && JAVA_HOME=$(/usr/libexec/java_home -v 17) mvn test` 전체 통과 (기존 52개 + 신규 4개 이상, Failures 0, Errors 0)
- [x] `ActuatorEndpointTest.apiPortDoesNotExposePrometheus`가 `HttpStatus.FORBIDDEN`을 단언한다 (`isNotEqualTo(OK)` 제거)
- [x] 같은 테스트 메서드 안에서 API 포트 요청 후 관리 포트 Prometheus 본문에 `http_server_requests_seconds_bucket`, `hikaricp_connections_active`가 있다고 단언한다
- [x] 익명 `GET /api/calendar/events` → 403 단언
- [x] 회원가입(`POST /api/auth/register`)·로그인(`POST /api/auth/login`) 각각 200과 비어 있지 않은 `accessToken` 단언, 로그인 토큰으로 `GET /api/calendar/events` → 200 단언
- [x] `Origin: http://localhost:5173` preflight(`OPTIONS /api/calendar/events`) → 200, `Access-Control-Allow-Origin`·`Access-Control-Allow-Credentials`·`Access-Control-Allow-Methods(GET 포함)` 단언
- [x] 새 테스트 클래스는 `ActuatorEndpointTest`와 같은 `@SpringBootTest`(RANDOM_PORT + `management.server.port=0`)·`@ActiveProfiles("test")`·`@AutoConfigureObservability` 설정을 사용한다
- [x] 회원가입 이메일이 실행마다 고유하다. 테스트를 단독으로 돌리거나 전체를 반복 실행해도 통과한다 (`mvn test -Dtest=ApiSecurityChainIntegrationTest`, `mvn test -Dtest=ActuatorEndpointTest`)
- [x] 매직 넘버 없음(상태 코드는 `HttpStatus`, 문자열·숫자는 상수), 메서드 30줄 이하, 4칸 들여쓰기, 한국어 `@DisplayName`
- [x] 수정·추가 파일은 **테스트 파일 2개뿐**이다 (그 밖의 파일이 필요하면 질문/이슈에 남긴다)
  - 수정: `backend/src/test/java/com/mynotionai/config/ActuatorEndpointTest.java`
  - 추가: `backend/src/test/java/com/mynotionai/config/ApiSecurityChainIntegrationTest.java`

## 참고 파일
- `docs/reviews/20261116-observability.md` (🟡 권장 1~3 원문)
- `docs/tasks/20261116-observability.md` (이전 지시서, 검증 기록의 curl 결과)
- `backend/src/test/java/com/mynotionai/config/ActuatorEndpointTest.java` (수정 대상, 설정·상수 스타일)
- `backend/src/main/java/com/mynotionai/config/SecurityConfig.java` (관리 체인 `@Order(1)`, API 체인 `/auth/**` 공개, CORS 허용 Origin 4개·`allowCredentials(true)`)
- `backend/src/main/java/com/mynotionai/security/JwtAuthenticationFilter.java` (`Authorization: Bearer ` 형식)
- `backend/src/main/java/com/mynotionai/controller/AuthController.java`, `CalendarController.java` (`/auth/register`, `/auth/login`, `/calendar/events?year=&month=`)
- `backend/src/main/java/com/mynotionai/dto/UserRegisterRequest.java`, `UserLoginRequest.java`, `AuthResponse.java` (요청 필드·검증 조건, 응답 `accessToken`)
- `backend/src/main/java/com/mynotionai/exception/ErrorCode.java` (`EMAIL_ALREADY_EXISTS` 409, `INVALID_CREDENTIALS` 401)
- `backend/src/main/resources/application.yml` (관리 포트, 히스토그램 설정)
- `backend/src/test/resources/application-test.yml` (H2 `testdb` 공유, JWT 테스트 키)

## 질문/이슈 (Codex 작성)

### PM 사전 표시 (추측·미검증 항목, 구현 중 확인 후 결과를 아래에 적는다)
1. **`TestRestTemplate`의 HTTP 클라이언트 (추측)**: `pom.xml`에 `okhttp 4.11.0`이 직접 의존성으로 있다. 그래서 Spring Boot 3.2의 `ClientHttpRequestFactories`가 OkHttp를 고를 것으로 추측한다. 다만 오프라인 환경이라 의존성 트리는 확인하지 못했다. OkHttp는 `Origin` 헤더를 보낸다. 만약 JDK `HttpURLConnection`이 선택되어 preflight가 403이 나오면 `pom.xml`·시스템 속성을 임의로 바꾸지 말고 여기에 증상을 적는다.
2. **test 프로필의 `hikaricp_connections_active` (추측)**: `local` 프로필(H2 파일 DB)에서는 curl로 확인했다. test 프로필(H2 인메모리)에서도 Flyway가 기동 중에 커넥션 풀을 만들므로 지표가 있을 것으로 본다. 테스트에서 없다면 원인(풀 지연 초기화 등)을 여기에 적는다.
3. **컨텍스트 재사용 (추측)**: 두 클래스의 설정이 같으면 TestContext 캐시로 컨텍스트 1개를 공유할 것으로 본다. 테스트 로그에서 Spring Boot 배너·`Started` 로그가 한 번만 찍히는지 확인해 적는다(실패 조건은 아님).

### 구현자 질문/이슈

## 검증 기록 (2026-11-16, Claude)
- 커밋(cherry-pick): 구현 `cfe0aa1`, `b87108e` / 리뷰 문서 `6014f12` (🔴 0, 🟡 0, 🟢 3). 커밋 메시지 접두어 없음 확인
- `mvn test` (JDK 17): 전체 2회 반복 모두 Tests run 56, Failures 0, Errors 0 (기존 52 + 신규 4)
- 단독 실행: `-Dtest=ApiSecurityChainIntegrationTest` 3/3, `-Dtest=ActuatorEndpointTest` 5/5 통과
- 회귀 탐지 확인 (임시 worktree에서 일부러 깨뜨린 뒤 되돌림)
  - `percentiles-histogram.http.server.requests`를 `false`로 변경 → `managementPrometheusExposesHttpAndHikariMetricsAfterApiRequest` 실패
  - 관리 체인의 `securityMatcher` 제거(관리 체인이 API 요청을 가로챔) → `registeredUserCanLoginAndReadCalendarWithJwt`, `allowedOriginPreflightRetainsCorsHeaders` 실패
- PM 사전 표시 3건 확인 결과
  1. `TestRestTemplate` 클라이언트: OkHttp가 선택되어 preflight의 `Origin` 헤더가 정상 전달됨(테스트 통과로 확인)
  2. test 프로필에서도 `hikaricp_connections_active` 지표 노출됨
  3. 컨텍스트 재사용: 전체 실행 로그에서 `ApiSecurityChainIntegrationTest`의 `Started` 로그가 없어 `ActuatorEndpointTest` 컨텍스트를 재사용함
