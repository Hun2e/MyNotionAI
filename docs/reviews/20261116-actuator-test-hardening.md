# Actuator 테스트 보강 코드 리뷰

- 대상 HEAD: `b741e20c25ecc886ce11916b08940de41d000a4f`
- 대상 커밋: `b23df77adcf98299400b31a87d521321aa51d115`, `b741e20c25ecc886ce11916b08940de41d000a4f`
- 종합 판정: **🔴 필수 수정 0건 / 🟡 권장 0건 / 🟢 참고 3건. 정적 검토에서 추가 수정이 필요한 결함을 발견하지 못했으며, 이전 리뷰의 🟡 1~3은 코드상 모두 해소되었다.**
- 비교 기준: `origin/main`(`2764865bac46e195e993ad657c2758e8bbf9bf83`) → 대상 HEAD
- 승인 지시서: `feature/actuator-test-hardening:docs/tasks/20261116-actuator-test-hardening.md`
- 이전 리뷰: `docs/reviews/20261116-observability.md`
- 검토 방식: 전체 diff와 관련 인증·조회·테스트 설정을 읽고, Spring Boot 3.2.0의 로컬 JAR을 `javap`으로 확인해 공식 자료와 대조했다. 아래 코드 라인은 대상 HEAD 기준이다. Maven 테스트, 애플리케이션 기동, HTTP 호출, 설정을 바꾸는 변이 검증은 실행하지 않았다.

## 정확성·보안

### 🟢 참고 1. 이전 권장 세 항목의 회귀 시나리오를 직접 단언한다

- 위치: `backend/src/test/java/com/mynotionai/config/ActuatorEndpointTest.java:76`, `backend/src/test/java/com/mynotionai/config/ActuatorEndpointTest.java:81`, `backend/src/test/java/com/mynotionai/config/ApiSecurityChainIntegrationTest.java:65`, `backend/src/test/java/com/mynotionai/config/ApiSecurityChainIntegrationTest.java:84`
- 문제 여부: 지시서가 요구한 회귀 단언의 누락이나 잘못된 기대값을 발견하지 못했다.
- 이유:
  - **이전 🟡 1 해소:** API 포트의 Prometheus 응답을 정확히 `HttpStatus.FORBIDDEN`으로 단언한다. 기존에 통과하던 500·404 응답은 실패한다. `@DisplayName`도 403 차단을 명시한다.
  - **이전 🟡 2 해소:** 같은 메서드에서 익명 일정 조회의 403을 확인한 다음 관리 포트 Prometheus의 200과 `http_server_requests_seconds_bucket`, `hikaricp_connections_active`를 단언한다. 기존 JVM 지표 테스트도 유지했다. 로컬 JAR에서 관측 필터의 등록 순서는 `-2147483647`로 확인했으며, Security 필터보다 먼저 요청을 감싸므로 익명 403 요청도 관측 대상이다. [Spring Boot 3.2.0 공식 설명](https://docs.spring.io/spring-boot/docs/3.2.0/reference/html/actuator.html#actuator.metrics.customizing.per-meter-properties)에 따르면 `percentiles-histogram`은 히스토그램 공개를 제어한다. 현재 저장소에는 이를 대신 켜는 SLO나 다른 히스토그램 설정이 없으므로 해당 설정을 삭제하면 bucket 단언이 실패할 것으로 판단한다. Hikari 계측이 빠져 지표가 사라져도 본문 단언이 실패한다. 이는 코드와 설정에 근거한 판단이며, 실제 설정 삭제 실험 결과는 아니다.
  - **이전 🟡 3 해소:** 별도 API·관리 랜덤 포트를 사용하는 실제 서버 테스트에서 익명 일정 조회 403, 회원가입·로그인 각각 200과 비어 있지 않은 토큰, 로그인 토큰을 넣은 일정 조회 200·빈 JSON 배열을 확인한다. 관리 체인이 API를 가로채면 회원가입·로그인 또는 인증된 일정 조회의 성공 단언이 실패한다. preflight는 실제 `OPTIONS`에 Origin·요청 메서드·Authorization 헤더 이름을 넣고 200, 허용 Origin, credentials, GET 허용을 확인한다. 관리 체인의 CORS 누락도 이 단언으로 드러난다.
- 수정 제안: 현재 단언을 유지한다. 지시서대로 URI·상태 라벨의 구체적 값에 결합하지 않고 필요한 지표 이름을 검사하는 범위가 적절하다.

`pom.xml`에는 OkHttp 4.11.0이 직접 의존성으로 있다. [Spring Boot 3.2.0의 요청 팩터리 선택 코드](https://raw.githubusercontent.com/spring-projects/spring-boot/v3.2.0/spring-boot-project/spring-boot/src/main/java/org/springframework/boot/web/client/ClientHttpRequestFactories.java)는 Apache·Jetty 다음으로 OkHttp를 선택하고 그 뒤에 JDK 기본 팩터리로 내려간다. 따라서 현재 의존성 구성에서 JDK 기본 클라이언트의 제한 헤더 문제를 확정적인 결함으로 볼 근거는 없다. 실제 선택된 클라이언트와 preflight 응답은 실행 검증에서 확인해야 한다.

## 동시성·테스트 간 상태 공유·성능

### 🟢 참고 2. 공유 H2와 월 경계로 인한 순서 의존을 추가하지 않았고 컨텍스트 재사용 조건을 맞췄다

- 위치: `backend/src/test/java/com/mynotionai/config/ApiSecurityChainIntegrationTest.java:26`, `backend/src/test/java/com/mynotionai/config/ApiSecurityChainIntegrationTest.java:66`, `backend/src/test/java/com/mynotionai/config/ApiSecurityChainIntegrationTest.java:133`, `backend/src/test/java/com/mynotionai/config/ActuatorEndpointTest.java:82`
- 문제 여부: 현재 테스트 구성에서 구체적인 상태 충돌이나 실행 순서 의존, 불필요한 컨텍스트 분리 요인을 발견하지 못했다.
- 이유:
  - `test` 프로필의 `jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1`은 같은 JVM에서 공유된다. 실제 HTTP 회원가입은 테스트 스레드의 트랜잭션으로 롤백되지 않지만, UUID 이메일이 기존 사용자와의 중복을 피한다. 기존 테스트에도 전역 사용자 수나 특정 생성 ID에 의존하는 단언, 전체 사용자 삭제가 없다.
  - 회원가입부터 로그인·조회까지 한 메서드에서 완료한다. 새 사용자의 일정은 생성하지 않으며 조회 쿼리는 `userId`로 필터링하므로 다른 사용자의 일정이 빈 배열 단언에 섞이지 않는다. 리마인더도 일정을 새로 만들지 않는다.
  - 두 URL 생성 지점 모두 `YearMonth.now()`를 한 번 호출한 결과에서 연도와 월을 꺼낸다. 연말·월말에 서로 다른 시점의 연도와 월이 섞이지 않는다. 익명 403은 조회 월과 무관하고, 새 사용자는 어느 달에도 일정이 없어 월 경계가 결과를 바꾸지 않는다.
  - 지표 테스트는 자체 API 요청으로 관측할 상태를 만든다. 다른 테스트가 먼저 HTTP 요청을 보내 줄 필요가 없다.
  - 두 클래스의 `RANDOM_PORT`, `management.server.port=0`, `@ActiveProfiles("test")`, `@AutoConfigureObservability` 값이 같다. 별도 설정 클래스·`@MockBean`·`@DirtiesContext`도 없다. [Spring TestContext 캐시 기준](https://docs.spring.io/spring-framework/reference/testing/testcontext-framework/ctx-management/caching.html)에 비추어 같은 JVM에서 컨텍스트를 재사용할 조건을 충족한다. 실제 기동 횟수는 실행 로그로 확인하지 않았다.
- 수정 제안: 현재 데이터 생성·설정 방식을 유지한다. 완료 조건에 있는 각 클래스 단독 실행과 전체 반복 실행으로 실제 순서 독립성 및 컨텍스트 재사용을 확인한다.

## 테스트 누락·가독성·범위

### 🟢 참고 3. 요구한 신규 테스트 네 개와 코딩 규칙·파일 제한을 충족한다

- 위치: `backend/src/test/java/com/mynotionai/config/ActuatorEndpointTest.java:28`, `backend/src/test/java/com/mynotionai/config/ActuatorEndpointTest.java:81`, `backend/src/test/java/com/mynotionai/config/ApiSecurityChainIntegrationTest.java:34`, `backend/src/test/java/com/mynotionai/config/ApiSecurityChainIntegrationTest.java:120`
- 문제 여부: 변경분의 매직 넘버, 30줄 초과 메서드, 들여쓰기 위반, 범위 밖 파일 수정은 발견하지 못했다.
- 이유: 경로·Origin·테스트 비밀번호·닉네임·이메일 구성 요소·Bearer 접두사·포트 속성은 상수로 분리했다. 상태·헤더·메서드는 `HttpStatus`, `HttpHeaders`, `HttpMethod`를 사용한다. 입력은 DTO 검증 조건을 만족하며 테스트 비밀번호는 실제 자격 증명이 아닌 테스트 데이터다. Java 들여쓰기는 4칸이고 `@DisplayName`은 한국어다. 추가·변경 메서드는 선언부터 닫는 중괄호까지 최대 16줄이다. 회원가입·로그인·URL·인증 헤더·인증 응답 검증도 짧은 헬퍼로 분리했다.
- 이유: `git diff origin/main b741e20`은 지정된 테스트 2개뿐이며, `git diff --check origin/main b741e20`도 통과했다. 지표 테스트 1개와 API 보안 테스트 3개가 추가됐다. 운영 코드·설정·의존성은 바뀌지 않았다. liveness·readiness나 거부 Origin 등 지시서가 제외한 테스트를 추가하지 않은 것은 이번 작업의 누락으로 보지 않는다.
- 수정 제안: 현재 파일 범위를 유지한다. 실행 확인이 필요한 완료 조건은 아래와 같이 별도로 남긴다.

## 지시서 검증 항목 대조

아래의 코드 확인은 정적 검토 결과다. 이 리뷰에서 실행 통과를 확인한 것은 `git diff --check`뿐이다.

| 검증 항목 | 코드 확인 | 실행 확인 |
|---|---|---|
| API 포트 Prometheus 정확한 403 | `ActuatorEndpointTest.java:76` | 미실행 |
| 같은 테스트에서 API 요청 후 HTTP bucket·Hikari 지표 확인 | `ActuatorEndpointTest.java:81` | 미실행 |
| 익명 일정 조회 403 | `ApiSecurityChainIntegrationTest.java:56` | 미실행 |
| 회원가입·로그인 200·토큰, 로그인 JWT로 일정 조회 200·빈 배열 | `ApiSecurityChainIntegrationTest.java:65`, `:120` | 미실행 |
| 허용 Origin preflight 200·Origin·credentials·GET | `ApiSecurityChainIntegrationTest.java:84` | 미실행 |
| 두 클래스의 서버·프로필·관측 설정 일치 | 두 클래스의 애너테이션 값 일치 | 기동 횟수 미확인 |
| 고유 이메일·월 경계·순서 독립성 | UUID·단일 `YearMonth`·메서드 안에서 준비와 검증 | 단독·반복 실행 미실행 |
| 기존 52개와 신규 4개 이상의 전체 JDK 17 테스트 통과 | 신규 테스트 메서드 4개 확인 | 미실행 |
| 스타일·테스트 2개 파일 제한 | diff와 메서드 확인 | `git diff --check` 통과 |
| 히스토그램 삭제·관리 체인 오매칭에 대한 변이 검증 | 해당 회귀를 실패시킬 단언 확인 | 설정·코드 변경 실험 미실행 |

지시서의 실행 완료 조건은 검증 담당자가 `cd backend && JAVA_HOME=$(/usr/libexec/java_home -v 17) mvn test`와 각 클래스 단독·반복 실행으로 확인해야 한다.

## 학습 포인트

1. **단언의 허용 범위가 회귀 탐지 범위를 정한다.** “200이 아님”은 서버 오류도 허용하지만 “403과 같음”은 접근 차단과 장애를 구분한다. 지표도 JVM 값만 확인하지 않고 필요한 bucket·풀 지표를 직접 검사해야 설정 회귀를 잡는다.
2. **다중 보안 체인은 실제 서버 요청으로 검증해야 한다.** 인증 객체를 미리 넣는 MVC 테스트와 달리, 회원가입·로그인·Bearer 요청·CORS preflight를 보내면 JWT 처리와 체인 선택을 함께 검증할 수 있다.
3. **컨텍스트 재사용과 데이터 격리는 함께 설계한다.** 같은 설정은 서버 기동 비용을 줄이지만 DB와 지표 상태도 공유한다. 고유 사용자와 테스트 내부의 준비 단계가 순서 의존을 줄인다.
