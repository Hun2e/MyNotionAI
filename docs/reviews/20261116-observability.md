# Actuator + Prometheus 구현 코드 리뷰

- 리뷰 대상 HEAD: `81955dc4c7a3d265183df1f3ae57cd786a28eec4`
- 대상 커밋: `aeb7efc322a6a5fa23fa4f64868da0f2fc859395`, `4df97d40c01e0411f23ebb3d268f3b42734e72dd`, `81955dc4c7a3d265183df1f3ae57cd786a28eec4`
- 비교 기준: `bebd0db53241e6ca930a71a7c71d9c946607d4ae` → 대상 HEAD
- 기준 지시서: `feature/observability:docs/tasks/20261116-observability.md`
- 종합 판정: **🔴 필수 수정 0건 / 🟡 권장 3건 / 🟢 참고 3건. 정적 검토에서 필수 수정 결함은 발견하지 못했다. 테스트 보강을 권장하며, 지시서의 실행 검증 완료 여부는 별도 확인이 필요하다.**
- 검토 방식: 대상 파일과 기존 인증·테스트 코드를 읽고, 로컬 캐시의 Spring Boot **3.2.0** JAR을 `javap`으로 확인해 공식 문서와 대조했다. Maven 테스트, 애플리케이션 기동, 실제 HTTP 호출은 실행하지 않았다. 아래 라인은 모두 대상 HEAD 기준이다.

## 정확성·보안

### 🟢 참고 1. 분리된 관리 컨텍스트에서만 새 체인이 선택되는 구조가 맞다

- 위치: `backend/src/main/java/com/mynotionai/config/SecurityConfig.java:56`, `backend/src/main/java/com/mynotionai/config/SecurityConfig.java:59`, `backend/src/main/java/com/mynotionai/config/SecurityConfig.java:69`
- 문제 여부: 포트 분리 시 API 인증을 우회시키는 결함은 발견하지 못했다.
- 이유: Spring Boot 3.2.0의 `EndpointRequest.AbstractRequestMatcher.ignoreApplicationContext()`는 관리 포트 유형이 `DIFFERENT`일 때 서버 namespace가 `management`가 아닌 컨텍스트를 제외한다. 따라서 API 포트의 `/api/actuator/**`는 새 체인의 `permitAll()` 대상이 되지 않고 기존 API 체인으로 넘어간다. 관리 자식 컨텍스트는 부모의 `springSecurityFilterChain`을 사용하므로, 관리 포트에서는 같은 matcher가 관리 컨텍스트의 엔드포인트 경로를 찾아 매칭한다. `@Order(1)`도 기본 순서의 API 체인보다 앞선다.
- 이유: 기존 API 체인의 `/auth/**` 공개, 나머지 인증 필수, JWT 필터, CORS, CSRF 설정은 원본과 동일하다. 익명 API 요청은 기존 체인에서 차단될 것으로 판단하며, CORS 설정 자체의 변경도 없다. 이는 정적 분석 결과이고 실제 응답 확인 결과는 아니다.
- 제안: 현재 체인 분리 방식을 유지한다. 관리 포트가 별도여도 사용자 정의 보안 체인의 영향을 받는다는 점은 [Spring Boot 3.2.0의 Actuator 보안 설명](https://docs.spring.io/spring-boot/docs/3.2.0/reference/html/actuator.html#actuator.endpoints.security)과 일치한다. 실행 회귀 검증은 권장 3을 참고한다.

### 🟢 참고 2. health 상세 비노출과 나머지 엔드포인트 차단 설정이 요구사항에 맞다

- 위치: `backend/src/main/resources/application.yml:27`, `backend/src/main/resources/application.yml:32`, `backend/src/main/resources/application.yml:34`, `backend/src/main/java/com/mynotionai/config/SecurityConfig.java:62`
- 문제 여부: health의 DB 상세 정보 노출이나 info·metrics 공개를 유발하는 설정은 발견하지 못했다.
- 이유: `show-details`를 덮어쓰지 않아 기본값 `never`가 적용된다. 로컬 JAR의 `HealthEndpointProperties`에서도 이를 확인했다. 관리 체인은 `health`, `prometheus`만 허용하고, 노출 목록에 있는 `info`, `metrics`와 discovery 경로는 `denyAll()`로 처리한다. `health` 하위 경로도 matcher에 포함되어 liveness·readiness 접근이 가능하다. 현재 허용 기능은 GET이므로 관리 체인에 남아 있는 기본 CSRF 보호를 해제할 필요도 없다.
- 이유: 관리 포트가 분리되면 `/api` 대신 관리 서버의 기본 경로를 기준으로 `/actuator/**`를 사용한다. 한국어 설정 주석도 이 동작을 설명한다. [health 정보 공개 기본값](https://docs.spring.io/spring-boot/docs/3.2.0/reference/html/actuator.html#actuator.endpoints.health), [관리 엔드포인트 경로 규칙](https://docs.spring.io/spring-boot/docs/3.2.0/reference/html/actuator.html#actuator.monitoring.customizing-management-server-context-path)을 참고했다.
- 제안: 현재 설정을 유지한다. probes가 활성화되면 루트 health 응답에 `groups`가 포함될 수 있으므로, 지시서의 `{"status":"UP"}` 예시와 실제 응답을 비교할 때 그룹 이름과 DB 상세 정보를 구분한다. 현재 테스트처럼 `status`와 `components`·`details` 부재를 확인하면 상세 정보 비노출 의도를 검증할 수 있다.

## 트랜잭션·동시성·성능

비즈니스 서비스, 쿼리, 트랜잭션, 스케줄러 변경은 없다. 이번 diff에서 트랜잭션·동시성 결함이나 N+1을 추가한 흔적은 발견하지 못했다. HTTP 히스토그램 설정은 지시서 범위에 해당하며, 성능 개선 수치나 계측 비용을 실행으로 측정하지는 않았다.

## 테스트 누락·보강

### 🟡 권장 1. API 포트 차단 테스트가 서버 오류도 정상 차단으로 간주한다

- 위치: `backend/src/test/java/com/mynotionai/config/ActuatorEndpointTest.java:70`
- 문제: 상태 코드가 `200 OK`가 아니기만 하면 테스트가 통과하므로 `500 Internal Server Error`도 허용한다.
- 이유: 지시서의 최소 테스트 조건인 “200이 아님”은 충족한다. 다만 완료 조건에서 기대한 응답은 403 또는 404다. matcher 초기화나 오류 처리에 문제가 생겨 서버 오류가 발생해도 현재 단언으로는 정상적인 접근 차단과 구별할 수 없다.
- 수정 제안: `HttpStatus.FORBIDDEN`, `HttpStatus.NOT_FOUND` 중 하나인지 단언한다. 현재 JWT 차인의 익명 요청 차단까지 고정하려면 `HttpStatus.FORBIDDEN`으로 좁힐 수 있다. 상태 코드 숫자를 직접 적지 않는다.

### 🟡 권장 2. JVM 지표만 검사해 HTTP 히스토그램과 DB 풀 지표 누락을 잡지 못한다

- 위치: `backend/src/test/java/com/mynotionai/config/ActuatorEndpointTest.java:55`, `backend/src/test/java/com/mynotionai/config/ActuatorEndpointTest.java:60`
- 문제: Prometheus 응답에서 JVM 지표와 application 태그만 검사한다. 완료 조건에 있는 `http_server_requests_seconds_bucket`, `hikaricp_connections_active`는 검사하지 않는다.
- 이유: `percentiles-histogram.http.server.requests` 설정을 지우거나 DB 풀 계측이 빠져도 이 테스트는 통과할 수 있다. 이번 작업의 목적에는 응답 시간과 DB 커넥션 풀을 관측할 수 있게 하는 것도 포함된다. 현재 구현이 지표를 누락했다는 판정은 아니며, 이를 보장하는 검증이 부족하다는 뜻이다.
- 수정 제안: 해당 테스트 안에서 API 요청이 완료된 뒤 Prometheus를 조회하고 두 지표도 단언한다. 다른 테스트의 실행 순서에 의존하지 않게 한다. 지시서대로 `local` 프로필에서도 API 호출 후 세 지표가 모두 존재하는지 별도 확인한다.

### 🟡 권장 3. 기존 API 인증·로그인·CORS 동작을 다중 체인 구성으로 검증하지 않는다

- 위치: `backend/src/test/java/com/mynotionai/config/ActuatorEndpointTest.java:18`, `backend/src/test/java/com/mynotionai/config/ActuatorEndpointTest.java:66`
- 문제: 실제 API 포트로 보내는 요청은 Prometheus 경로 하나뿐이다. 지시서 완료 조건인 익명 `/api/calendar/events`의 403과 `/api/auth/login` 정상 동작을 확인하는 테스트는 추가되지 않았다. CORS 회귀 검증도 없다.
- 이유: 기존 `CalendarControllerTest`는 인증 객체를 미리 주입하는 MVC slice 테스트라서, 실제 JWT와 두 보안 체인의 선택을 함께 검증하는 근거가 되지 못한다. 설정 diff에는 기존 API 체인 변경이 없지만, 체인을 추가한 뒤에도 API 요청과 preflight가 올바른 체인을 사용하는지는 별도 검증이 유용하다.
- 수정 제안: 같은 통합 테스트 클래스에서 익명 일정 조회 403, 공개 회원가입·로그인으로 얻은 JWT를 사용한 일정 조회 성공을 검증한다. 허용된 프론트엔드 Origin의 API `OPTIONS` 요청에 CORS 응답 헤더가 유지되는지도 확인한다. 메서드는 30줄 이하로 분리하고 소스의 CORS 정책 변경은 이번 범위에 추가하지 않는다.

## 가독성·범위

### 🟢 참고 3. 의존성·코딩 규칙·파일 범위를 지켰다

- 위치: `backend/pom.xml:38`, `backend/src/main/java/com/mynotionai/config/SecurityConfig.java:27`, `backend/src/test/java/com/mynotionai/config/ActuatorEndpointTest.java:26`
- 문제 여부: 새 변경에서 버전 직접 지정, 숫자 하드코딩 규칙 위반, 과도한 메서드 길이, 범위 밖 파일 수정은 발견하지 못했다.
- 이유: Actuator와 Prometheus registry 버전은 Spring Boot 3.2.0 BOM에 맡겼다. 체인 순서는 의미 있는 상수로 분리했고 테스트 상태 코드는 `HttpStatus`, 랜덤 관리 포트 설정은 상수를 사용한다. 포트 숫자를 설정 파일에 선언한 것은 지시서와 같다. 새 Java 메서드는 모두 30줄 이하이고 들여쓰기는 4칸이다. `@AutoConfigureObservability`를 사용해 테스트에서도 계측을 활성화했다.
- 이유: 전체 diff는 지시서의 `pom.xml`, `application.yml`, `SecurityConfig.java`, `ActuatorEndpointTest.java` 네 파일로 한정된다. 기존 CORS의 숫자 상수 등 이번에 바꾸지 않은 부분은 지적 대상에서 제외했다. `git diff --check bebd0db 81955dc`도 통과했다.
- 제안: 현재 범위와 스타일을 유지하며, 권장 테스트를 추가할 때도 같은 규칙을 적용한다.

## 지시서 검증 항목 대조

아래 “테스트 있음”은 코드에 단언이 있다는 뜻이며, 이 리뷰에서 실행해 통과했다는 뜻은 아니다.

| 검증 항목 | 대상 코드의 검증 범위 | 이 리뷰의 실행 확인 |
|---|---|---|
| 관리 health 200·UP·상세 비노출 | 테스트 있음 | 미실행 |
| 관리 Prometheus 200·JVM 지표·application 태그 | 테스트 있음 | 미실행 |
| API 포트 Prometheus 비공개 | 테스트 있음. 403·404로 강화 권장 | 미실행 |
| 관리 info·metrics 차단 | 익명 요청에 403을 기대하는 추가 테스트 있음 | 미실행 |
| liveness·readiness | 설정과 하위 경로 matcher 확인. 직접 호출 테스트 없음 | 미실행 |
| HTTP 히스토그램·Hikari 지표 | 설정·의존성 확인. 응답 단언 없음 | local 기동·호출 미실행 |
| 기존 익명 일정 조회 차단·로그인 정상 | 실제 포트 통합 검증 없음 | 미실행 |
| 전체 JDK 17 Maven 테스트 | 기존 테스트와 신규 4개 테스트가 있음 | 미실행. 전체 통과 여부 판단 유보 |
| 수정·추가 파일 제한 | 지정한 네 파일과 일치 | Git diff로 확인 |

따라서 구현의 필수 수정 사유는 없지만, 이 문서만으로 지시서의 전체 완료 조건이 충족됐다고 판단하지 않는다. 구현자 또는 검증 담당자가 JDK 17 전체 테스트와 `local` 프로필의 실제 API·관리 포트 호출 결과를 확인해야 한다.

## 학습 포인트

1. **관리 포트 분리와 보안 체인 분리는 함께 이해해야 한다.** 별도 관리 서버도 부모의 보안 필터를 사용할 수 있다. `EndpointRequest`가 요청의 애플리케이션 컨텍스트를 구분하기 때문에 사용자 API의 인증 규칙과 관리 엔드포인트의 허용 규칙을 나눌 수 있다.
2. **엔드포인트 노출과 접근 허용은 별개다.** exposure 목록에 등록하면 HTTP 경로가 생기고, `permitAll()`·`denyAll()`이 접근 가능 여부를 결정한다. health의 상세 정보 공개는 다시 별도의 `show-details` 설정으로 제어한다.
3. **관측 기능은 지표의 실제 생성까지 검증해야 한다.** JVM 지표가 있다는 사실만으로 HTTP 히스토그램과 DB 풀 지표까지 보장되지는 않는다. 요청을 먼저 완료하고 필요한 지표를 직접 단언하면 계측 설정의 회귀를 잡을 수 있다.
