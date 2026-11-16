# Docker Compose 로컬 운영 스택 (앱 · MySQL · Prometheus · Grafana)
- 브랜치: feature/docker-compose
- 상태: 완료

## 배경 (왜 하는가)
1. **지표는 나오는데 볼 곳이 없다.** PR #6·#8로 관리 포트 8081에서 `/actuator/health`(liveness·readiness)와 `/actuator/prometheus`가 노출된다. 하지만 지표를 모으는 Prometheus와 그래프로 보여 주는 Grafana가 없어서 숫자를 쌓아 두고 비교할 수 없다.
2. **운영(`prod`) 구성을 한 번에 띄울 방법이 없다.** 저장소에 `Dockerfile`·`docker-compose.yml`이 하나도 없다. `prod` 프로필로 확인하려면 MySQL을 직접 설치하고 환경 변수를 손으로 넣어야 한다.
3. **`application-prod.yml`의 DB 주소가 `localhost`로 고정돼 있다.** (`jdbc:mysql://localhost:3306/mynotion`) 컨테이너 안에서 `localhost`는 **그 컨테이너 자신**이라 MySQL 컨테이너에 닿지 않는다.
4. 로드맵 5단계(운영) 항목이며, 다음 작업 `feature/load-test`(k6)가 이 스택 위에서 부하를 주고 Grafana로 결과를 읽는다. **선행 조건**이다.

→ `docker compose up -d` 한 줄로 앱(`prod` 프로필) · MySQL · Prometheus · Grafana를 띄우고, Grafana 기본 대시보드가 자동 등록되게 한다. **운영 코드(Java)와 yml은 바꾸지 않고**, 환경 변수로 설정을 덮어쓴다.

> **학습 포인트**
> - **컨테이너 네트워크의 `localhost`**: 컨테이너마다 네트워크 공간이 따로라 `localhost`는 자기 자신이다. 다른 컨테이너는 compose **서비스 이름**(`mysql`, `app`)으로 찾는다(compose 내장 DNS).
> - **healthcheck와 기동 순서**: `depends_on`만으로는 "컨테이너가 시작됨"까지만 기다린다. `condition: service_healthy`를 써야 "실제로 요청을 받을 수 있음"까지 기다린다.
> - **설정 외부화(12-Factor III. Config)**: 같은 이미지를 환경 변수만 바꿔 어디서든 실행한다. 비밀값은 이미지·저장소에 넣지 않는다.

## 할 일 (범위)

### 0. DB 주소 문제 해결 방식 (결정 사항)
- `application-prod.yml`은 **수정하지 않는다.** compose의 `app` 서비스에 아래 환경 변수를 넣어 덮어쓴다.
  ```yaml
  SPRING_DATASOURCE_URL: "jdbc:mysql://mysql:3306/mynotion?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true"
  ```
- 원리: Spring Boot의 **relaxed binding**으로 `SPRING_DATASOURCE_URL` → `spring.datasource.url`에 대응한다. OS 환경 변수는 `application-*.yml`보다 **우선순위가 높아서** yml 값을 덮어쓴다.
- 쿼리 파라미터는 기존 yml 값과 똑같이 유지한다(호스트만 `localhost` → `mysql`).

### 1. `backend/Dockerfile` (멀티 스테이지)
- **빌드 스테이지**: `maven:3.9-eclipse-temurin-17`
  - `pom.xml`을 먼저 복사 → `mvn -B dependency:go-offline` → 그다음 `src/` 복사 → `mvn -B -DskipTests package`
  - 이유: 의존성 레이어를 소스와 분리해, 소스만 바뀌면 의존성 다운로드를 다시 하지 않는다(레이어 캐시).
  - `-DskipTests` 이유: 테스트는 `mvn test`(추후 CI)에서 따로 돌린다. 이미지 빌드는 패키징만 한다.
  - `backend/`에 `mvnw`가 없으므로 Maven 이미지를 쓴다. `mvnw` 추가는 하지 않는다.
- **런타임 스테이지**: `eclipse-temurin:17-jre-jammy` (JRE만 포함, Ubuntu 기반, arm64·amd64 모두 제공)
  - 빌드 결과 `target/*.jar`를 `/app/app.jar`로 복사한다.
  - **비 root 실행**: 전용 사용자·그룹(예: `spring`)을 만들고 `USER spring`으로 전환한다. 이유: 컨테이너가 뚫려도 root 권한을 주지 않기 위해서다.
  - `EXPOSE 8080 8081` (문서 역할. 실제 공개 여부는 compose `ports`가 정한다)
  - **healthcheck용 `curl`을 명시적으로 설치**한다(`apt-get install -y --no-install-recommends curl` 후 apt 캐시 삭제). 베이스 이미지에 curl/wget이 들어 있는지에 의존하지 않기 위해서다.
  - `ENTRYPOINT ["java", "-jar", "/app/app.jar"]` — **exec 형식**으로 쓴다. 이유: `java`가 PID 1이 되어 `docker compose stop`의 SIGTERM을 직접 받고 정상 종료한다. JVM 옵션은 `JAVA_TOOL_OPTIONS` 환경 변수로 넘긴다(JVM이 자동으로 읽음).
- **`backend/.dockerignore`**: `target/`, `data/`(로컬 H2 파일), `*.log`, `.idea/`, `*.iml`, `.env`, `.DS_Store`
  - 이유: 빌드 컨텍스트를 줄이고, 로컬 DB 파일·비밀값이 이미지에 들어가지 않게 한다.

### 2. `docker-compose.yml` (저장소 루트)
- `name: mynotion` 지정. 서비스 4개, 사용자 정의 네트워크 1개(기본 네트워크 사용도 가능), 이름 있는 볼륨 `mysql-data`, `prometheus-data`, `grafana-data`.

| 서비스 | 이미지 | 호스트 포트 공개 | healthcheck | depends_on |
|---|---|---|---|---|
| `mysql` | `mysql:8.0` | 공개 안 함 | `mysqladmin ping -h 127.0.0.1` | — |
| `app` | `build: ./backend` | `127.0.0.1:8080:8080`만 | `curl -fsS http://localhost:8081/actuator/health/readiness` | `mysql: service_healthy` |
| `prometheus` | `prom/prometheus` | `127.0.0.1:9090:9090` | `wget -qO- http://localhost:9090/-/healthy` | 없음 (앱과 독립 기동) |
| `grafana` | `grafana/grafana` | `127.0.0.1:3000:3000` | `wget -qO- http://localhost:3000/api/health` | `prometheus: service_healthy` |

#### `mysql`
- 환경 변수: `MYSQL_DATABASE: mynotion`, `MYSQL_USER: ${DB_USERNAME}`, `MYSQL_PASSWORD: ${DB_PASSWORD}`, `MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD}`
- 볼륨: `mysql-data:/var/lib/mysql`
- healthcheck: `mysqladmin ping -h 127.0.0.1 -u"$$MYSQL_USER" -p"$$MYSQL_PASSWORD" --silent`
  - **반드시 `-h 127.0.0.1`(TCP)** 을 쓴다. MySQL 이미지는 첫 기동 때 초기화용 임시 서버를 **소켓 전용**(`--skip-networking`)으로 띄웠다가 재시작한다. `-h localhost`(소켓)로 검사하면 이 임시 서버에서 healthy가 떠서, 앱이 재시작 중인 DB에 접속하다 실패할 수 있다.
  - `$$`는 compose 변수 치환을 막고 컨테이너 안의 환경 변수를 쓰게 하는 표기다.
  - `interval`, `timeout`, `retries`, `start_period`를 명시한다(첫 초기화는 수십 초 걸릴 수 있음).

#### `app`
- `build: ./backend`
- 환경 변수:
  ```yaml
  SPRING_PROFILES_ACTIVE: prod
  SPRING_DATASOURCE_URL: "jdbc:mysql://mysql:3306/mynotion?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true"
  DB_USERNAME: ${DB_USERNAME:?DB_USERNAME가 .env에 필요합니다}
  DB_PASSWORD: ${DB_PASSWORD:?DB_PASSWORD가 .env에 필요합니다}
  JWT_SECRET: ${JWT_SECRET:?JWT_SECRET가 .env에 필요합니다}
  OPENAI_API_KEY: ${OPENAI_API_KEY:-}
  GOOGLE_OAUTH_CLIENT_ID: ${GOOGLE_OAUTH_CLIENT_ID:-}
  JAVA_TOOL_OPTIONS: "-XX:MaxRAMPercentage=75.0"
  ```
  - `${VAR:?메시지}`: 값이 없으면 compose가 **시작 전에 실패**한다(빈 값으로 떠서 런타임에 터지는 것보다 빠르게 알 수 있음).
- healthcheck: `curl -fsS http://localhost:8081/actuator/health/readiness || exit 1`
  - 관리 포트 8081은 컨테이너 **안**에서 접근하므로 호스트에 공개하지 않아도 된다.
  - `readiness`를 쓰는 이유: "트래픽을 받아도 되는 상태"를 뜻하므로 기동 순서 판단에 맞다.
  - JVM 기동 + Flyway 마이그레이션 시간을 고려해 `start_period`를 넉넉히(예: 60초) 준다.
- `depends_on: mysql: condition: service_healthy`
- `restart: unless-stopped`는 선택. 넣는다면 이유를 주석으로 남긴다.

#### `prometheus`
- 설정 파일 `monitoring/prometheus/prometheus.yml`을 읽기 전용으로 마운트(`:ro`).
- 볼륨: `prometheus-data:/prometheus`
- 스크레이프 설정:
  ```yaml
  global:
    scrape_interval: 15s
  scrape_configs:
    - job_name: mynotion-backend
      metrics_path: /actuator/prometheus
      static_configs:
        - targets: ["app:8081"]
  ```
  - `app:8081`: 서비스 이름 + **컨테이너 내부 포트**로 접근한다. 호스트 포트 공개와 무관하다.
- `app`에 `depends_on`을 걸지 않는다. 이유: 앱 기동이 실패해도 Prometheus·Grafana는 떠 있어야 타깃 `DOWN` 상태로 원인을 확인할 수 있다. 앱이 healthy가 되면 다음 스크레이프부터 `UP`이 된다.

#### `grafana`
- 관리자 계정: `GF_SECURITY_ADMIN_USER: ${GRAFANA_ADMIN_USER:?...}`, `GF_SECURITY_ADMIN_PASSWORD: ${GRAFANA_ADMIN_PASSWORD:?...}`
- 익명 접근·회원가입 끔(`GF_AUTH_ANONYMOUS_ENABLED: "false"`, `GF_USERS_ALLOW_SIGN_UP: "false"`).
- 마운트(`:ro`):
  - `monitoring/grafana/provisioning/datasources/prometheus.yml` → Prometheus 데이터소스. **`uid: prometheus`로 고정**하고 `url: http://prometheus:9090`, `isDefault: true`.
  - `monitoring/grafana/provisioning/dashboards/dashboards.yml` → 대시보드 파일 provider (경로: 컨테이너 안 `/var/lib/grafana/dashboards`)
  - `monitoring/grafana/dashboards/mynotion-overview.json` → 기본 대시보드
- 볼륨: `grafana-data:/var/lib/grafana`
- 대시보드 JSON의 패널 데이터소스는 고정 uid(`prometheus`)를 참조한다. 이유: uid가 바뀌면 provisioning한 대시보드가 데이터소스를 못 찾는다.

### 3. Grafana 기본 대시보드 (`mynotion-overview.json`)
- 대시보드 `uid`(예: `mynotion-overview`)와 제목(예: `MyNotion Overview`)을 고정한다. 완료 조건의 API 확인에 쓴다.
- 공통 필터: `application="mynotion-backend"`. 처리량·응답 시간·에러 비율 패널은 `uri!~"/actuator.*"`로 Prometheus 스크레이프 요청을 뺀다(관리 포트 요청도 `http_server_requests`에 기록될 수 있음).
- rate 구간은 Grafana 변수 `$__rate_interval`을 쓴다. 이유: 스크레이프 간격(15초)보다 충분히 긴 구간을 자동으로 잡아 그래프가 끊기지 않는다.

| 패널 | PromQL 예시 |
|---|---|
| 요청 처리량(RPS) | `sum(rate(http_server_requests_seconds_count{application="mynotion-backend", uri!~"/actuator.*"}[$__rate_interval]))` (URI별: `sum by (uri) (...)`) |
| p95 응답 시간 | `histogram_quantile(0.95, sum by (le) (rate(http_server_requests_seconds_bucket{application="mynotion-backend", uri!~"/actuator.*"}[$__rate_interval])))` |
| p99 응답 시간 | 위 식에서 `0.95` → `0.99` |
| 5xx 에러 비율 | `sum(rate(http_server_requests_seconds_count{application="mynotion-backend", uri!~"/actuator.*", status=~"5.."}[$__rate_interval])) / sum(rate(http_server_requests_seconds_count{application="mynotion-backend", uri!~"/actuator.*"}[$__rate_interval]))` |
| HikariCP 커넥션 | `hikaricp_connections_active{application="mynotion-backend"}`, 비교용 `hikaricp_connections_max`, 대기 `hikaricp_connections_pending` |
| JVM 힙 사용량 | `sum(jvm_memory_used_bytes{application="mynotion-backend", area="heap"})`, 비교용 `sum(jvm_memory_max_bytes{application="mynotion-backend", area="heap"} > 0)` |

- p95/p99는 `application.yml`의 `percentiles-histogram.http.server.requests: true` 덕분에 `_bucket` 지표가 있어서 계산할 수 있다.
- 에러 비율은 트래픽이 0이면 `0/0`이라 값이 비어 보인다(정상). 단위는 percent(0.0-1.0)로 지정한다.
- `jvm_memory_max_bytes`는 일부 메모리 풀이 `-1`(제한 없음)이라 `> 0`으로 걸러 합산한다.
- 시간·바이트 단위(`s`, `bytes`)를 패널에 지정한다.

### 4. 비밀값 관리
- **`.env.example`** (저장소 루트, 커밋함): 더미 값만 넣는다.
  ```dotenv
  # 복사해서 사용: cp .env.example .env  (.env는 git에 올리지 않는다)
  MYSQL_ROOT_PASSWORD=change-me-root
  DB_USERNAME=mynotion
  DB_PASSWORD=change-me-db
  # 최소 32자
  JWT_SECRET=change-me-to-a-random-string-at-least-32-chars
  GRAFANA_ADMIN_USER=admin
  GRAFANA_ADMIN_PASSWORD=change-me-grafana
  OPENAI_API_KEY=
  GOOGLE_OAUTH_CLIENT_ID=
  ```
- **`.env`** (git 제외): 실제 값. compose가 루트 `.env`를 자동으로 읽는다.
- **`.gitignore`**: 이미 `.env` 패턴이 있다. `git check-ignore -v .env`로 루트 `.env`가 제외되는지, `.env.example`은 **제외되지 않는지** 확인한다. 부족할 때만 추가한다.
- `docker-compose.yml`, `Dockerfile`, Prometheus·Grafana 설정 파일에 비밀번호·키를 직접 쓰지 않는다. 문서·주석 예시도 더미 값만 쓴다.

### 5. 포트 공개 정책 (보안)
- **8081 관리 포트는 호스트에 공개하지 않는다.** Prometheus는 compose 내부망에서 `app:8081`로 접근한다.
  - 이유: 관리 포트의 `health`·`prometheus`는 인증 없이(`permitAll`) 열려 있다. 지표에는 API 경로 목록, 요청량, JVM·DB 커넥션 풀 상태 같은 **내부 정보**가 담겨 있어 공격자에게 정찰 자료가 된다. 내부망에서만 닿게 하는 것이 1차 방어선이다.
- **MySQL 3306도 공개하지 않는다.** DB 확인은 `docker compose exec mysql ...`로 한다. (호스트에 이미 MySQL이 3306을 쓰고 있어도 충돌하지 않는다.)
- **공개하는 포트(8080·9090·3000)는 `127.0.0.1:`에 묶는다.** 이유: `"8080:8080"`처럼 쓰면 `0.0.0.0`(모든 네트워크 인터페이스)에 열려 같은 와이파이의 다른 기기에서도 접근할 수 있다. 또 Docker의 포트 공개는 호스트 방화벽 규칙을 우회할 수 있다.
- 이 정책을 `docker-compose.yml`에 한국어 주석으로 남긴다.

### 6. 문서
- 루트 `docker-compose.yml` 상단 주석에 실행·종료 방법(`cp .env.example .env` → `docker compose up -d --build` → `docker compose down` / 데이터까지 삭제는 `down -v`)과 접속 주소(8080 API, 9090 Prometheus, 3000 Grafana)를 짧게 적는다.
- `README.md`, `AGENTS.md`는 수정하지 않는다(Claude가 검증 후 반영).

### 생성·수정 파일 목록 (이외 파일이 필요하면 질문/이슈에 남긴다)
- `backend/Dockerfile` (신규)
- `backend/.dockerignore` (신규)
- `docker-compose.yml` (신규)
- `.env.example` (신규)
- `monitoring/prometheus/prometheus.yml` (신규)
- `monitoring/grafana/provisioning/datasources/prometheus.yml` (신규)
- `monitoring/grafana/provisioning/dashboards/dashboards.yml` (신규)
- `monitoring/grafana/dashboards/mynotion-overview.json` (신규)
- `.gitignore` (확인 후 필요할 때만)

## 하지 않을 일 (범위 밖)
- 프론트엔드 컨테이너화 (필요하다고 판단되면 질문/이슈에 남긴다)
- k6 부하 테스트 (다음 작업 `feature/load-test`)
- GitHub Actions CI, 클라우드 배포, 이미지 레지스트리 push
- Redis 등 다른 인프라 추가
- 운영 코드(Java)·`application*.yml` 수정 — DB 주소는 환경 변수로만 덮어쓴다
- 이미 적용된 Flyway 마이그레이션(`V1__init_schema.sql`) 수정, 새 마이그레이션 추가
- Alertmanager·알림 규칙, Loki 등 로그 수집, cAdvisor·node-exporter
- `mvnw` 추가, Spring Boot layered jar·Buildpacks 전환
- `AGENTS.md`, `README.md` 수정
- push, PR 생성, main 커밋

## 완료 조건 (검증 가능한 항목)
- [x] `cp .env.example .env` 후 `docker compose up -d --build` → `docker compose ps`에서 4개 서비스 모두 `healthy`
- [x] `.env`에서 `JWT_SECRET`을 지우고 `docker compose config`를 실행하면 누락 메시지와 함께 실패한다 (`${VAR:?}` 동작 확인, 확인 후 원복)
- [x] API 동작: 아래 순서로 200 응답과 토큰 확인
  ```bash
  curl -s -X POST http://localhost:8080/api/auth/register -H 'Content-Type: application/json' \
    -d '{"email":"compose@test.com","password":"password123","nickname":"compose"}'
  curl -s -X POST http://localhost:8080/api/auth/login -H 'Content-Type: application/json' \
    -d '{"email":"compose@test.com","password":"password123"}'
  ```
- [x] Prometheus 타깃: `curl -s http://localhost:9090/api/v1/targets`에서 `job="mynotion-backend"`의 `health`가 `"up"`
- [x] Prometheus 쿼리: `curl -s 'http://localhost:9090/api/v1/query?query=up{job="mynotion-backend"}'` 결과 값 `1`
- [x] Grafana provisioning 확인 (`.env` 값을 셸에 불러온 뒤 실행: `set -a; . ./.env; set +a`)
  - `curl -s -u "$GRAFANA_ADMIN_USER:$GRAFANA_ADMIN_PASSWORD" http://localhost:3000/api/datasources` → uid `prometheus` 데이터소스 존재
  - `curl -s -u "$GRAFANA_ADMIN_USER:$GRAFANA_ADMIN_PASSWORD" 'http://localhost:3000/api/search?query=MyNotion'` → `mynotion-overview` 대시보드 존재
  - API를 몇 번 호출한 뒤 대시보드 6개 패널(RPS, p95, p99, 5xx 비율, HikariCP, JVM 힙)에 값이 표시됨 (스크린샷 또는 패널 쿼리 API로 확인)
- [x] 포트 정책: 호스트에서 `curl http://localhost:8081/actuator/health` → 연결 실패(공개 안 됨), `docker compose port app 8081` 결과 없음, MySQL 3306도 미공개. 공개 포트는 `docker compose ps`에서 `127.0.0.1:` 바인딩으로 표시
- [x] 비 root 실행: `docker compose exec app id -u` → `0`이 아님
- [x] 재기동 후 Flyway 정상: `docker compose down -v` → `docker compose up -d` → 모든 서비스 healthy, 아래 쿼리에서 V1 `success=1`
  ```bash
  docker compose exec mysql sh -c 'mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" mynotion -e "SELECT version, success FROM flyway_schema_history"'
  ```
- [x] 볼륨 유지: `docker compose down`(볼륨 유지) → `up -d` 후 위에서 가입한 계정으로 로그인 성공
- [x] `cd backend && JAVA_HOME=$(/usr/libexec/java_home -v 17) mvn test` → 기존 56개 전부 통과 (Java 코드 변경 없음)
- [x] 비밀값 하드코딩 없음: `git diff main --stat`에 `.env` 없음, 신규 파일에 실제 비밀값 없음(`.env.example`은 더미 값만)
- [x] 생성·수정 파일이 위 목록과 일치

## 참고 파일
- `backend/src/main/resources/application.yml` (관리 포트 8081, probes, `percentiles-histogram`, `application` 태그)
- `backend/src/main/resources/application-prod.yml` (DB URL `localhost` 고정, `DB_USERNAME`·`DB_PASSWORD`·`JWT_SECRET` 필수, Flyway `baseline-on-migrate`)
- `backend/src/main/resources/db/migration/V1__init_schema.sql`
- `backend/src/main/java/com/mynotionai/config/SecurityConfig.java` (`managementFilterChain`: `health`·`prometheus`만 `permitAll`)
- `backend/src/main/java/com/mynotionai/dto/UserRegisterRequest.java`, `UserLoginRequest.java` (완료 조건 curl 본문)
- `backend/pom.xml` (Java 17, Spring Boot 3.2.0, `mvnw` 없음)
- `.gitignore` (`.env` 이미 포함)
- `docs/tasks/20261116-observability.md` (지표 노출 작업과 검증 기록)

## 질문/이슈 (Codex 작성)

### PM 사전 표시 (Claude가 확인 못 했거나 결정이 필요한 항목)
1. **로컬 Docker 환경**: 작업 환경에서 Docker 29.8.1, Compose v5.5.1, `arm64`(Apple Silicon)를 확인했다. 실제 이미지 pull·기동은 해 보지 않았다.
2. **Apple Silicon 호환**: `maven:3.9-eclipse-temurin-17`, `eclipse-temurin:17-jre-jammy`, `mysql:8.0`, `prom/prometheus`, `grafana/grafana`는 arm64 이미지를 제공하는 것으로 알고 있으나 직접 확인하지 않았다. pull 실패나 `platform` 경고가 나면 기록한다. `platform: linux/amd64` 강제 지정은 하지 않는다(에뮬레이션으로 느려짐).
3. **이미지 태그 고정 여부**: 재현성을 위해 `latest`는 쓰지 않는다. `mysql:8.0`, `maven:3.9-eclipse-temurin-17`, `eclipse-temurin:17-jre-jammy`는 위 표기대로, `prom/prometheus`·`grafana/grafana`는 구현 시점의 **안정 버전 태그(major.minor 이상)** 로 고정하고 선택한 태그를 여기에 적는다. digest(`@sha256`) 고정은 하지 않는다.
4. **healthcheck 도구 존재 여부(추측)**: `prom/prometheus`(busybox 기반)와 `grafana/grafana`에 `wget`이 있다고 가정했다. 없으면 대체 방법(예: Grafana는 `curl`, 또는 해당 서비스 healthcheck 생략 후 `depends_on: condition: service_started`)과 이유를 적는다. 앱 이미지는 `curl`을 직접 설치하므로 가정하지 않는다.
5. **컨테이너 시간대(결정 필요)**: `ReminderService`(`LocalDateTime.now()`)와 `AiService`(`LocalDate.now()`)가 JVM 기본 시간대를 쓴다. 컨테이너 기본값은 UTC라서 로컬(KST) 실행과 비교해 리마인더 판단과 "오늘 일정 요약"이 9시간 어긋날 수 있다. **권장: `app` 서비스에 `TZ: Asia/Seoul` 환경 변수 추가**(코드 수정 없음). 사용자가 다르게 정하지 않으면 권장안으로 진행하고, DB 저장 시각 정책(UTC 통일 등)은 별도 작업으로 남긴다.
6. **DB 주소 덮어쓰기 방식**: `SPRING_DATASOURCE_URL` 환경 변수안을 채택했다. 대안이었던 "`application-prod.yml`을 `${DB_HOST:localhost}` 형태로 바꾸기"는 yml 수정이 필요해 이번 범위(운영 코드·yml 무수정)와 맞지 않는다. 다만 URL 쿼리 파라미터가 yml과 compose 두 곳에 중복되는 단점이 있어, 추후 운영 설정 정리 작업에서 다시 검토할 수 있다.
7. **Flyway `baseline-on-migrate`**: 빈 MySQL(새 볼륨)에서는 baseline 없이 V1이 정상 적용될 것으로 예상한다. 반대로 **테이블은 있는데 `flyway_schema_history`가 없는 스키마**에서는 V1이 건너뛰어진다. 볼륨에 다른 데이터가 남아 이상하면 `down -v`로 초기화한다.
8. **CORS**: `SecurityConfig`에 `http://localhost:5173` 등이 하드코딩돼 있다. 이번 작업은 curl로만 검증하므로 영향 없다. 브라우저에서 프론트(호스트 `npm run dev`) → 컨테이너 API 연결도 같은 origin이라 동작할 것으로 예상하나 확인 대상은 아니다.
9. **JVM 메모리**: `JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=75.0`만 제안했다. compose `mem_limit` 같은 컨테이너 메모리 제한은 넣지 않는다(부하 테스트 작업에서 필요하면 추가).

### 사용자 결정 (2026-11-16, Claude 기록)
- 지시서 승인 완료.
- **시간대**: `app` 서비스에 `TZ: Asia/Seoul` 환경 변수를 추가한다(PM 사전 표시 5 권장안 채택, 코드 수정 없음).
- **네트워크 이름 고정**: 다음 작업 `feature/load-test`의 k6 컨테이너가 `--network`로 붙을 수 있도록 사용자 정의 네트워크를 쓰고 이름을 `mynotion-net`으로 고정한다(`networks: mynotion-net: name: mynotion-net`). 모든 서비스가 이 네트워크에 붙는다.

## 검증 기록 (2026-11-16, Claude)
- 커밋(cherry-pick): 구현 `52e6778`, `d03b5b1`, `9fb80eb` / 리뷰 반영 `1e49346`(🔴 5xx 패널), `a5770bf`(🟡 MySQL healthcheck) / 리뷰 문서 `7ab3f28` (🔴 1, 🟡 1, 🟢 3 → 🔴·🟡 모두 반영)
- 이미지 태그(PM 사전 표시 3): `prom/prometheus:v3.16.0`, `grafana/grafana:13.3.1`. 5개 이미지 모두 arm64 pull 성공(PM 사전 표시 2), Prometheus·Grafana 이미지에 `wget` 존재(PM 사전 표시 4)
- 실행 환경: Docker 29.8.1, Compose v5.5.1, arm64, Docker Desktop CPU 10 / 메모리 약 7.7GB
- `docker compose up -d --build` → 4개 서비스 healthy. 공개 포트는 `127.0.0.1:8080/9090/3000`만, 호스트 8081 연결 실패, `docker compose port app 8081`·`mysql 3306` 매핑 없음
- API: register 200, login 200(토큰), 익명 일정 조회 403. 앱 uid 999(비 root), 컨테이너 시간대 KST
- Prometheus: target `app:8081` health `up`, `up{job="mynotion-backend"}` = 1
- Grafana: datasource uid `prometheus`(기본), 대시보드 `mynotion-overview` 자동 등록, 익명 API 401. 트래픽 후 패널 값: RPS 약 4.0, p95 11.7ms, p99 15.1ms, Hikari active/max/pending 0/10/0, JVM 힙 약 87MB
- 5xx 패널(리뷰 반영 후): 무트래픽 빈 값 / 정상 트래픽 0 / 500 혼합 시 0.35 — 3가지 구분 확인
- `JWT_SECRET` 누락 시 `docker compose config`가 "required variable JWT_SECRET is missing"로 실패
- Flyway: 빈 볼륨 첫 기동 V1 success=1 / `down` 후 `up` 기존 계정 로그인 200 / `down -v` 후 V1 재적용, 기존 계정 401
- `mvn test` (JDK 17): Tests run 56, Failures 0, Errors 0
- 검증 후 `docker compose down -v`로 정리

### 지시서와 달라진 점 (리뷰 반영)
- 5xx 에러 비율 PromQL: 지시서 예시식은 5xx 시계열이 없으면 결과가 비어 완료 조건과 충돌했다. 분자에만 `or vector(0)`을 붙였다(무트래픽은 계속 빈 값으로 구분).
- MySQL healthcheck: 비밀번호가 프로세스 인수로 노출되지 않도록 `mysqladmin ping -h 127.0.0.1 --silent`로 바꿨다. `ping`은 인증 실패여도 서버가 응답하면 성공하므로 검사 의미는 같다.

### 다음 작업(feature/load-test)에 넘길 사실
- 네트워크 이름 `mynotion-net`, 앱 서비스 `app`, 컨테이너 내부 포트 8080 → k6 `BASE_URL=http://app:8080/api`, `--network mynotion-net`
- Micrometer `uri` 라벨에는 context-path `/api`가 붙지 않는다(예: `/calendar/events`, `/auth/login`)
