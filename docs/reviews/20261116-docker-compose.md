# Docker Compose 구현 코드 리뷰

- 대상 HEAD: `5a6b7cdbd6c8dcca7ea755379c271721cfeb3701`
- 대상 커밋:
  - `a118a1b` 백엔드 멀티 스테이지 Dockerfile과 dockerignore 추가
  - `6fb6230` 앱·MySQL·Prometheus·Grafana Docker Compose 구성 추가
  - `5a6b7cd` Prometheus 스크레이프와 Grafana 데이터소스·대시보드 provisioning 추가
- 비교 기준: `origin/main`의 `363ba3647240d1369b5a2017b8efe3726ab1d3f8` → 대상 HEAD 전체 diff
- 승인 지시서: `feature/docker-compose:docs/tasks/20261116-docker-compose.md` (`e7c04e0791a03e0a352eadffa8fa22ae21ddbf47` 기준). 맨 끝 사용자 결정인 `TZ: Asia/Seoul`과 네트워크 이름 `mynotion-net` 고정까지 대조했다.
- 종합 판정: **🔴 필수 수정 1건 / 🟡 권장 1건 / 🟢 참고 3건. 5xx 오류 비율 패널 수정 후 재확인이 필요하다.**
- 검토 방식: Git으로 지시서·대상 파일·기존 Spring 설정·보안 설정·POM을 읽고 공식 문서와 대조했다. 대상 파일의 줄 번호를 사용한다. 구현 파일 변경, 브랜치 전환, Docker 이미지 pull·빌드·기동, Maven 테스트, 실제 API 호출은 하지 않았다.

## 정확성

### 🔴 필수 수정 1. 5xx가 한 번도 없으면 정상 트래픽도 오류 비율이 빈 값으로 표시된다

- 위치: `monitoring/grafana/dashboards/mynotion-overview.json:91`, 설명 `monitoring/grafana/dashboards/mynotion-overview.json:77`
- 문제: 새 앱에서 정상 API만 호출하고 충분히 스크레이프해도 5xx 패널이 `0%` 대신 빈 값으로 남을 수 있다. 현재 설명은 트래픽이 없을 때만 비율이 표시되지 않는다고 안내한다.
- 이유: HTTP 상태별 지표는 해당 상태의 요청이 관측되어야 생성된다. 5xx가 한 번도 발생하지 않은 상태에서는 `status=~"5.."`인 시계열 자체가 없다. 분자의 `sum(rate(...))`는 값 0이 아니라 빈 벡터이므로, 전체 요청의 rate가 양수여도 나눗셈 결과가 없다. 이는 [Prometheus의 누락 지표 설명](https://prometheus.io/docs/practices/instrumentation/#avoid-missing-metrics)과 [벡터 연산의 매칭 규칙](https://prometheus.io/docs/prometheus/latest/querying/operators/#arithmetic-binary-operators)을 대상 쿼리에 적용한 정적 분석이다. 실행으로 재현하지는 않았다.
- 영향: 지시서의 “API를 몇 번 호출한 뒤 6개 패널에 값 표시” 완료 조건을 정상 요청만으로 충족하지 못한다. 오류가 없는 상태와 관측 데이터가 없는 상태도 구분하기 어렵다.
- 수정 제안: **분자에만** `or vector(0)`을 적용한다. 분모는 기존 전체 요청 rate를 유지해 무트래픽 상태를 계속 구분한다. 예시는 다음과 같다.

```promql
(
  sum(rate(http_server_requests_seconds_count{
    application="mynotion-backend", uri!~"/actuator.*", status=~"5.."
  }[$__rate_interval])) or vector(0)
)
/
sum(rate(http_server_requests_seconds_count{
  application="mynotion-backend", uri!~"/actuator.*"
}[$__rate_interval]))
```

- 지시서와의 관계: 구현은 지시서 표의 예시식과 일치한다. **예시식 자체에 누락 시계열 처리가 빠져 있어 완료 조건과 충돌하는 문제**다. 구현 누락으로만 취급하지 말고, 수정 담당자가 지시서의 `질문/이슈`에 근거를 남기고 PM과 예시식·완료 조건을 맞춘다. 이 리뷰에서는 지시서나 구현을 수정하지 않았다.
- 검증 제안: 새 데이터에서 충분한 스크레이프를 확보한 뒤 ① 정상 트래픽만 있을 때 `0%`, ② 5xx가 섞이면 양의 비율, ③ rate 구간에 트래픽이 없으면 미정의 값 또는 빈 표시가 되는지 확인한다. 전체 나눗셈 결과에 `or vector(0)`을 붙여 관측 부재까지 정상으로 표시하지 않는다.

## 보안

### 🟡 권장 1. MySQL healthcheck가 비밀번호를 프로세스 인수로 전달한다

- 위치: `docker-compose.yml:21`
- 문제: `CMD-SHELL`이 `-p"$$MYSQL_PASSWORD"`를 컨테이너의 실제 비밀번호로 확장해 `mysqladmin`의 명령행 인수로 전달한다. `$$`는 Compose의 선행 치환을 막을 뿐, 실행 시 프로세스 인수 노출을 막지는 않는다.
- 이유: MySQL 클라이언트는 초기화 중 비밀번호 인수를 덮어쓰지만, 그 전 짧은 시간에는 프로세스 정보를 볼 수 있는 주체에게 노출될 수 있다. 항상 평문으로 남는다는 뜻은 아니다. 이 동작과 제한은 [MySQL 8.0 비밀번호 보안 문서](https://dev.mysql.com/doc/refman/8.0/en/password-security-user.html)에 명시되어 있다. 반복되는 healthcheck는 이 전달을 매번 수행한다.
- 수정 제안: 현재 검사의 목적이 초기화용 소켓 서버를 제외하고 TCP 서버가 시작됐는지 확인하는 것이므로, 비밀번호 없는 `mysqladmin ping -h 127.0.0.1 --silent`를 고려한다. `ping`은 인증 거절이어도 서버가 응답하면 성공하므로 지금 명령도 계정·비밀번호의 유효성을 보장하지 않는다. 인증·Flyway 성공 여부는 지시서의 API·DB 검증으로 별도 확인한다. 이 반환 규칙은 [mysqladmin 공식 문서](https://dev.mysql.com/doc/refman/8.0/en/mysqladmin.html)에 설명되어 있다.
- 지시서와의 관계: 지시서에도 같은 `-p` 예시가 있으므로 구현자의 임의 이탈로 판정하지 않았다. 로컬 내부망 구성의 보안 개선 권장으로 남기며, 변경 시 `질문/이슈`에 근거를 기록한다. 인증 성공까지 healthcheck의 책임으로 넓히려면 검사 방식도 따로 합의해야 한다.

### 🟢 참고 1. 비밀값의 이미지 포함과 외부 포트 노출을 막는 구성이 지시서와 맞다

- 위치: `backend/Dockerfile:24`, `backend/Dockerfile:26`, `backend/.dockerignore:6`, `docker-compose.yml:14`, `docker-compose.yml:36`, `docker-compose.yml:42`, `docker-compose.yml:77`, `.env.example:3`
- 문제 여부: 신규 파일에서 실제 비밀값 하드코딩, 비밀값을 전달하는 Docker 빌드 인수·이미지 환경 변수, 앱의 root 실행, 의도하지 않은 호스트 포트 공개는 발견하지 못했다. healthcheck 명령행 인수는 권장 1의 별도 항목이다.
- 이유: 빌드 컨텍스트는 `backend/`이고 Dockerfile은 `pom.xml`·`src/`만 복사한다. 루트 `.env`는 컨텍스트 밖이며 백엔드의 `.env`도 제외한다. 런타임은 전용 `spring` 사용자이고, 공개 포트 8080·9090·3000은 모두 `127.0.0.1`에 묶여 있다. MySQL 3306과 관리 포트 8081의 `ports` 선언은 없다. Grafana는 익명 접근과 회원가입을 명시적으로 끈다. 필수 환경 변수는 `${VAR:?메시지}`를 사용하고 예제에는 더미 값만 있다.
- 이유: `.env`는 기존 `.gitignore`로 제외되며 `.env.example`은 제외되지 않는 것을 확인했다. 다만 환경 변수 외부화가 출력 마스킹까지 제공하지는 않는다. 일반 `docker compose config`는 치환된 환경 변수 값을 출력한다. 이는 [Compose config의 변수 해석·출력 동작](https://docs.docker.com/reference/cli/docker/compose/config/)에 따른 것으로, 승인된 환경 변수 방식 자체의 결함으로 세지 않았다.
- 제안: 현재 포트·권한·외부화 구성을 유지한다. 실값으로 구문·필수값 검증 결과만 필요하면 `docker compose config --quiet`를 사용하고, 전체 config 출력을 리뷰 문서나 공유 로그에 붙이지 않는다. 이 리뷰에서는 실제 `.env`를 읽거나 config 출력을 생성하지 않았다.

## 기동 순서·재시작·데이터 지속성

### 🟢 참고 2. 최초 기동 대기·볼륨·시간대·고정 네트워크는 승인 내용과 일치한다

- 위치: `docker-compose.yml:18`, `docker-compose.yml:21`, `docker-compose.yml:25`, `docker-compose.yml:40`, `docker-compose.yml:45`, `docker-compose.yml:47`, `docker-compose.yml:51`, `docker-compose.yml:62`, `docker-compose.yml:82`, `docker-compose.yml:88`, `docker-compose.yml:100`
- 문제 여부: 정적 구성에서 초기화용 MySQL 서버를 잘못 준비 완료로 판단하는 소켓 검사, 순환 의존성, 볼륨 누락, 사용자 결정 누락은 발견하지 못했다.
- 이유: MySQL 검사는 `127.0.0.1` TCP를 사용하고 60초 유예 후 실패 10회를 허용한다. 앱은 MySQL healthy를 기다린 다음 관리 포트의 readiness를 검사하며, JVM·Flyway를 위한 60초 유예와 실패 5회가 지정되어 있다. 모든 healthcheck에 interval·timeout·retries·start_period가 있다. Prometheus는 앱에 의존하지 않고 Grafana만 Prometheus healthy를 기다린다.
- 이유: MySQL·Prometheus·Grafana 데이터 경로에 각각 이름 있는 볼륨이 연결되어 있고 설정 파일은 읽기 전용이다. 모든 서비스가 이름이 고정된 `mynotion-net`에 연결되며 앱의 `TZ`는 `Asia/Seoul`이다. `restart` 생략은 지시서가 허용한 선택이므로 결함으로 세지 않았다. 다만 의존성 대기는 실행 중 장애 복구를 보장하는 설정은 아니다.
- 제안: 현재 구성을 유지하되 최초 빈 볼륨과 기존 볼륨에서 각각 기동 시간을 확인한다. 선언된 유예·재시도만으로 느린 환경에서도 충분하다고 단정할 수는 없다. 실제 비 root UID, healthcheck의 `wget` 존재, Flyway V1 성공, 재기동 후 계정·지표·대시보드 유지 및 장애 후 복구 동작은 실행 검증 담당자가 확인한다.

## 성능·나머지 대시보드·가독성

### 🟢 참고 3. 이미지 구성·캐시·나머지 PromQL·파일 범위는 지시서를 따른다

- 위치: `backend/Dockerfile:5`, `backend/Dockerfile:13`, `backend/Dockerfile:18`, `backend/Dockerfile:30`, `backend/.dockerignore:1`, `docker-compose.yml:39`, `docker-compose.yml:56`, `docker-compose.yml:73`, `monitoring/prometheus/prometheus.yml:2`, `monitoring/grafana/provisioning/datasources/prometheus.yml:6`, `monitoring/grafana/provisioning/datasources/prometheus.yml:13`, `monitoring/grafana/dashboards/mynotion-overview.json:60`, `monitoring/grafana/dashboards/mynotion-overview.json:153`
- 문제 여부: 빌드 결과물·Maven 캐시가 런타임에 포함되는 구성, 소스 복사 때문에 의존성 레이어가 매번 무효화되는 구성, `latest` 직접 사용, 허용 목록 밖 파일 변경은 발견하지 못했다. 5xx의 빈 시계열 처리는 필수 수정 1에 별도로 기록했다.
- 이유: `pom.xml` → `dependency:go-offline` → `src/` → package 순서라 소스만 변경하면 의존성 레이어를 재사용할 수 있다. 런타임은 JRE 스테이지에 JAR만 가져오고, curl을 설치한 레이어에서 apt 목록도 삭제한다. `.dockerignore`에는 지시서의 7개 제외 패턴이 모두 있다. exec 형식 ENTRYPOINT는 Java가 SIGTERM을 직접 받도록 하고 JVM 옵션은 `JAVA_TOOL_OPTIONS`로 전달한다. `MaxRAMPercentage=75.0`과 메모리 제한 생략은 승인된 선택이다. 실제 이미지 크기·캐시 적중·종료 시간은 측정하지 않았다.
- 이유: Prometheus `v3.16.0`·Grafana `13.3.1`은 버전 태그로 지정되어 있으며 검토일의 [Prometheus 공식 다운로드](https://prometheus.io/download/)와 [Grafana 공식 릴리스](https://github.com/grafana/grafana/releases/tag/v13.3.1)에서 배포 버전을 확인했다. Maven·Temurin·MySQL 태그는 지시서의 지정과 같다. digest 고정이나 더 세밀한 버전 고정은 이번 요구사항에 추가하지 않는다. 레지스트리 manifest·실제 플랫폼별 이미지 기동은 확인하지 않았다.
- 이유: 대시보드 JSON은 구문이 정상이고 6개 패널의 ID가 유일하다. 패널과 각 target이 datasource UID `prometheus`를 참조하며 provisioning UID와 일치한다. 모든 쿼리에 application 필터가 있고 HTTP 4개 패널에는 `uri!~"/actuator.*"`와 `$__rate_interval`이 있다. datasource의 15초 간격은 실제 scrape 간격과 같다. p95·p99는 bucket rate를 `le`로 합산한 후 quantile을 구하고, HikariCP 활성·최대·대기와 JVM 힙 사용·양수 최대값 합산도 지시서 표와 일치한다. 단위는 RPS `reqps`, 응답 시간 `s`, 비율 `percentunit`, 힙 `bytes`로 맞다.
- 이유: 전체 diff는 허용된 신규 8개 파일뿐이다. Java·프로필 설정·마이그레이션·README·AGENTS 변경은 없다. 실행·종료·접속 주소와 포트 정책의 한국어 주석이 있으며 `git diff --check origin/main 5a6b7cd`도 통과했다.
- 제안: 현재 구조와 범위를 유지하며 필수 수정 1을 반영한다. 선택한 Prometheus·Grafana 태그를 지시서의 `질문/이슈`에 기록하라는 요구의 이행 여부는 해당 문서 관리 담당자가 별도 확인한다. 구현 diff에는 그 문서 변경이 없다.

## 검증 범위와 남은 실행 확인

아래 미실행 항목은 이 리뷰에서 새로 발견한 결함 건수가 아니다. 사용자 요청대로 실행 검증은 오케스트레이터에 맡겼으며, 검증 결과가 없다고 실패로 단정하지 않았다.

| 검증 항목 | 이 리뷰에서 확인한 근거 | 남은 확인 |
|---|---|---|
| 변경 범위·공백 오류 | 전체 Git diff, 허용된 신규 8개 파일, `diff --check` | 없음 |
| 대시보드 구조·쿼리·UID·단위 | JSON 구문 분석과 6개 패널 정적 대조 | 실제 패널 값과 5xx 미발생·발생·무트래픽 구분 |
| 필수 비밀값·Git 제외 | `${VAR:?}` 선언, `.env` 제외와 예제 비제외 | 더미 값으로 필수값 누락 시 `config --quiet` 실패 |
| 이미지·플랫폼·기동 도구 | 태그·Dockerfile·healthcheck 선언 | arm64 이미지 가용성, curl·wget 실행, 4개 healthy |
| 앱 기동·API·지표 | DB URL 환경 변수, readiness, scrape 주소, provisioning 경로 | 빈 볼륨 Flyway 성공, 가입·로그인, 타깃 UP, 6개 패널 |
| 포트·사용자·Grafana 인증 | loopback 바인딩, 비공개 포트, USER, 익명 차단 선언 | 실제 포트 공개·비 root UID·익명 접근 차단 |
| 종료·재기동·지속성 | exec ENTRYPOINT, named volume, 종료 안내 | SIGTERM 종료, `down` 후 데이터 유지, `down -v` 후 V1 재적용 |
| 기존 백엔드 회귀 | Java·POM·Spring 설정 무변경 | JDK 17 전체 `mvn test` 통과 |

## 학습 포인트

1. **시계열 부재와 값 0은 다르다.** 오류가 한 번도 없으면 해당 상태 레이블이 생성되지 않을 수 있다. 비율에서는 분자만 0으로 보완해야 정상 트래픽의 0%와 관측 부재를 구분할 수 있다.
2. **환경 변수 외부화와 비밀값 노출 방지는 별개다.** 저장소·이미지에 비밀번호를 쓰지 않아도, 실행 인수와 설정 출력에는 실제 값이 나타날 수 있다. Compose 치환, 컨테이너 셸 확장, 클라이언트의 인수 처리를 각각 확인해야 한다.
3. **healthcheck의 성공이 보장하는 범위를 알아야 한다.** TCP ping은 서버 응답 여부를, 앱 readiness는 트래픽 수용 상태를 판단한다. 인증 성공·마이그레이션·재시작 후 데이터 유지까지 확인하려면 별도 검증이 필요하다.
