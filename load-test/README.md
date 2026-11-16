# k6 기준선 부하 테스트

일정 월별 조회·대화 로그 조회·일정 등록의 개선 전 기준선을 측정한다. 운영 코드를 바꾸지 않고 compose 스택의 MySQL에서 실행한다. 운영 DB나 공유 DB에는 실행하지 않는다.

k6 이미지는 **`grafana/k6:0.57.0`**으로 고정한다. `latest`를 사용하지 않는다. [공식 릴리스](https://github.com/grafana/k6/releases/tag/v0.57.0)를 기준으로 선택했으며, 이미지 pull과 실제 실행 확인은 측정 담당자가 수행한다. 외부 JavaScript 라이브러리를 내려받지 않고 k6 내장 모듈만 사용한다.

## 측정 순서

저장소 루트에서 실행한다. compose용 `.env`는 루트 `.env.example`을 참고해 먼저 준비한다. 순서는 **볼륨 초기화 → 스택 기동 → 헬스 체크 → k6 실행**이다.

1. 전용 측정 스택인지 확인한 뒤 볼륨을 초기화한다. MySQL 데이터뿐 아니라 Prometheus·Grafana 데이터도 모두 삭제되므로 보존할 데이터가 있는 스택에서는 실행하지 않는다.

   ```bash
   docker compose down -v
   ```

2. 앱·MySQL·Prometheus·Grafana를 기동한다.

   ```bash
   docker compose up -d --build
   ```

3. 상태를 확인하고 `app`이 `healthy`가 될 때까지 기다린다. 준비되지 않거나 `unhealthy`라면 앱 로그로 원인을 해결한 뒤 진행한다.

   ```bash
   docker compose ps
   docker compose logs app
   ```

4. 결과 디렉터리와 테스트 계정 비밀번호를 준비하고 k6를 실행한다. 다음 예시는 **Bash 기준**이며, Zsh에서는 먼저 `bash`를 실행한다. 비밀번호는 화면에 표시하지 않고 입력받으며 명령 이력에 값을 남기지 않는다.

   ```bash
   mkdir -p load-test/results
   read -r -s -p '테스트 계정 비밀번호(8자 이상): ' LOAD_TEST_PASSWORD
   printf '\n'
   export LOAD_TEST_PASSWORD
   export RUN_ID="baseline-$(date +%Y%m%d-%H%M%S)"

   docker run --rm -i \
     --network mynotion-net \
     --user "$(id -u):$(id -g)" \
     --workdir /scripts \
     -v "$PWD/load-test:/scripts" \
     -e BASE_URL=http://app:8080/api \
     -e LOAD_TEST_PASSWORD \
     -e RUN_ID \
     grafana/k6:0.57.0 run /scripts/baseline.js

   k6_exit_code=$?
   unset LOAD_TEST_PASSWORD
   printf 'k6 종료 코드: %s\n' "$k6_exit_code"
   ```

컨테이너가 compose의 `mynotion-net` 네트워크에서 서비스 이름 `app`을 사용한다. `--workdir /scripts`는 결과 상대 경로를 마운트한 `load-test/`에 맞춘다. `--user`는 호스트 사용자 권한으로 summary를 저장하기 위한 설정이다. `results/`가 없거나 쓰기 권한이 없으면 저장에 실패한다.

같은 조건의 기준선을 비교할 때는 매번 볼륨 초기화부터 다시 진행한다. 동일 `RUN_ID`로 재실행하면 회원가입 중복에 한해서 로그인하지만, **시딩은 다시 수행**되므로 데이터가 누적된다. 새 `RUN_ID`만 지정해도 DB의 기존 데이터는 남으므로 빈 DB 측정을 대신할 수 없다. 스크립트는 데이터를 삭제하지 않는다.

## 환경 변수

숫자·기간·비율·임계값 기본값은 `lib/config.js`의 상수로 관리한다. 값을 바꾸려면 위 `docker run`에서 이미지 이름 앞에 `-e 변수명=값`을 추가한다. 비밀번호를 명령에 직접 적거나 HTTP 디버그 출력에 토큰·비밀번호가 남게 하지 않는다.

| 변수 | 기본값 | 의미 |
|---|---|---|
| `BASE_URL` | 없음, 필수 | `/api`를 포함한 API 주소. compose에서는 `http://app:8080/api` |
| `LOAD_TEST_PASSWORD` | 없음, 필수 | 테스트 사용자 공통 비밀번호, 8자 이상 |
| `RUN_ID` | `baseline-<실행 시각 밀리초>` | 사용자 이메일·summary 파일 접두어. 영문·숫자·밑줄·하이픈, 1~40자. 비교 시 명시 권장 |
| `USER_COUNT` | `10` | 테스트 사용자 수, 1 이상 정수 |
| `SEED_EVENTS_PER_USER` | `30` | 사용자별 사전 일정 수, 0 이상 정수 |
| `SEED_ANALYZE_PER_USER` | `100` | 사용자별 `/ai/analyze` 시딩 호출 수, 0 이상 정수. 호출당 대화 로그 2건 |
| `CHAT_LOGS_LIMIT` | `30` | 대화 로그 조회 개수, 1~100 정수 |
| `READ_YEAR` | `2030` | 조회·시딩 대상 연도 |
| `READ_MONTH` | `1` | 조회·시딩 대상 월, 1~12 |
| `WRITE_YEAR` | `2030` | 쓰기 대상 연도 |
| `WRITE_MONTH` | `2` | 쓰기 대상 월, 1~12 |
| `TARGET_VUS` | `20` | 유지 구간 목표 VU 계산 기준, 1 이상 정수 |
| `WRITE_RATIO` | `0.2` | 쓰기 VU 비율, 0~1 |
| `CHAT_LOGS_RATIO` | `0.2` | 대화 로그 조회 VU 비율, 0~1 |
| `WARMUP` | `1m` | 0 → 시나리오별 목표 VU |
| `HOLD` | `5m` | 목표 VU 유지 |
| `RAMPDOWN` | `1m` | 목표 VU → 0 |
| `THINK_TIME_SEC` | `1` | 반복마다 대기할 초, 0 이상 |
| `SETUP_TIMEOUT` | `5m` | 사용자 생성·시딩의 전체 제한 시간 |
| `P95_READ_MS` | `300` | 일정 조회 p95 임시 상한(ms), 0 초과 |
| `P95_CHAT_LOGS_MS` | `300` | 대화 로그 조회 p95 임시 상한(ms), 0 초과 |
| `P95_WRITE_MS` | `500` | 일정 등록 p95 임시 상한(ms), 0 초과 |
| `MAX_FAIL_RATE` | `0.01` | 각 시나리오 HTTP 실패율 임시 상한, 0 초과~1 이하 |

`BASE_URL`·`LOAD_TEST_PASSWORD`가 없으면 init 단계에서 요청 전에 실패한다. 잘못된 숫자·기간, 두 비율의 합이 1 이상인 경우, 조회 월과 쓰기 월의 연·월이 같은 경우도 실패한다. 연도는 1000~9998 정수로 제한한다. 기간은 `30s`, `1m`, `1m30s` 같은 0보다 큰 단위 포함 값이다.

`READ_YEAR/READ_MONTH`와 `WRITE_YEAR/WRITE_MONTH`는 현재와 먼 고정 월로 두어 리마인더가 측정 중 대화 로그를 추가하지 않게 한다. 기본값은 각각 `2030-01`, `2030-02`다. 측정 시점이 해당 월과 가까워졌다면 두 월 모두 먼 값으로 바꾼다.

## 시딩과 시나리오

`setup()`은 `${RUN_ID}-${i}@loadtest.local` 사용자들을 만들고 `EMAIL_ALREADY_EXISTS`인 경우에만 로그인한다. 기본값에서 사용자별 일정 30건을 조회 월 안에 고르게 배치하고 `/ai/analyze`를 100번 호출한다. 총 인증 10회·일정 등록 300회·analyze 1,000회이며, 사용자별 대화 로그 200건과 AI 액션 100건이 남는다. 준비 단계는 순차 실행하며 실패 시 측정을 중단한다. 기본 `SETUP_TIMEOUT=5m`이 충분한지는 실측으로 확인한다.

현재 `/ai/analyze`는 외부 호출 없는 규칙 기반 구현이다. 시딩 내용은 `loadtest 대화 로그 시딩`이고 상태 200만 검증한다. 일정 초안 적용 API를 호출하지 않으므로 일정을 추가하지 않는다. 향후 LLM 연동 후에는 외부 호출 여부와 시딩 방법을 다시 검토해야 한다.

세 시나리오는 모두 `ramping-vus`로 동시에 시작한다. 각 VU는 `(__VU - 1) % USER_COUNT`로 사용자를 골라 JWT로 요청하고, 반복마다 think time을 둔다.

| 시나리오 | 요청 | 이름 태그 | 기본 유지 VU | 검증 |
|---|---|---|---|---|
| `read` | `GET /calendar/events?year=...&month=...` | `events_list` | 12 | 상태 200, 배열 길이 ≥ 사전 일정 수 |
| `chat_logs` | `GET /ai/chat-logs?limit=...` | `chat_logs_list` | 4 | 상태 200, 배열 길이 = `min(CHAT_LOGS_LIMIT, SEED_ANALYZE_PER_USER × 2)` |
| `write` | `POST /calendar/events` | `events_create` | 4 | 상태 200, 응답 `id` 존재 |

읽기 비율은 `1 - WRITE_RATIO - CHAT_LOGS_RATIO`다. 시나리오별 `TARGET_VUS × 비율`을 반올림하고 최소 1 VU로 정하므로, 작은 목표값이나 비율 0을 지정해도 세 시나리오는 모두 실행되며 실제 합이 `TARGET_VUS`와 다를 수 있다. 기본 비율은 read:chat_logs:write = 6:2:2다.

일정 시각은 오프셋 없는 `yyyy-MM-ddTHH:mm:ss` 형식이며 같은 날 09:00~10:00으로 만든다. 쓰기는 조회 월과 분리한 월에만 넣어 조회 데이터 양을 유지한다. 일정 등록은 대화 로그를 만들지 않으므로 대화 로그 수도 유지된다. `/ai/chat-logs`는 현재 DB에서 전체 로그를 가져와 Java에서 `limit`으로 자르므로, 같은 데이터 양으로 페이징 개선 전후를 비교한다. Reminder N+1 측정은 이 테스트의 범위에 포함하지 않는다.

## Threshold와 결과 해석

**아래 threshold는 근거 있는 성능 목표가 아닌 임시 초기값이다.** 기준선 측정 후 사용자와 확정한다. VU·기간·데이터 양 역시 초기 제안값이다.

| 대상 | 임시 조건 |
|---|---|
| 각 시나리오 HTTP 실패율 | `rate < MAX_FAIL_RATE` (기본 `< 0.01`) |
| `events_list` 지연 | `p(95) < P95_READ_MS` (기본 `< 300ms`) |
| `chat_logs_list` 지연 | `p(95) < P95_CHAT_LOGS_MS` (기본 `< 300ms`) |
| `events_create` 지연 | `p(95) < P95_WRITE_MS` (기본 `< 500ms`) |
| 측정 중 check 성공률 | `rate > 0.99` (`LIMITS.MIN_CHECK_RATE`) |

시딩 요청에는 `setup_register`, `setup_login`, `setup_events_create`, `setup_analyze`라는 별도 이름 태그를 사용한다. 실패율 threshold는 시나리오, 지연 threshold는 측정용 이름 태그로 한정한다. 준비 단계에는 k6 `check()` 대신 실패 시 예외를 사용하므로 시딩 성공이 `checks` 성공률에 섞이지 않는다. 전체 `http_reqs`, `http_req_failed`, `http_req_duration` 집계에는 시딩이 포함되므로 측정용 하위 지표와 구분한다.

threshold 실패 시 k6는 종료 코드 99를 반환한다. 검증할 때는 별도 실행에서 `-e P95_READ_MS=1`처럼 엄격한 임시값을 넣어 실패가 드러나는지 확인한다. 기본값으로 통과했는지 여부도 실측 결과와 함께 남긴다.

`ramping-vus`는 동시 사용자 수를 정하는 **closed model**이다. RPS는 입력값이 아닌 결과값이며 서버 응답이 느려지면 같은 VU에서도 RPS가 감소한다. VU 비율이 요청 수 비율을 보장하지 않는다. k6와 서버가 같은 머신에서 자원을 나눠 쓰므로 절대 성능으로 일반화하지 말고 같은 머신·Docker 할당 자원·데이터·부하 설정에서 전후를 비교한다.

## 결과 위치와 유지 구간 확인

- 전체 summary JSON: `load-test/results/<RUN_ID>-summary.json`
- 콘솔: 전체 메트릭 값과 threshold 통과·실패를 표시하는 한국어 텍스트 요약
- `load-test/results/` 전체는 `load-test/.gitignore`로 제외한다. 같은 `RUN_ID`의 summary는 덮어쓴다.

`handleSummary()`는 k6가 전달한 전체 summary 객체를 JSON으로 저장한다. 토큰·비밀번호를 summary에 추가하거나 콘솔에 출력하지 않는다. 별도의 원격 JavaScript 의존성 없이 요약한다. [공식 handleSummary 문서](https://grafana.com/docs/k6/latest/results-output/end-of-test/custom-summary/)에서 반환 경로와 집계 방식을 확인할 수 있다.

**summary는 워밍업·유지·감소를 합친 집계다. 유지 구간 전용 p95·RPS로 옮겨 적으면 안 된다.** 결과 문서는 유지 구간만 사용해야 하므로 측정 시 시간별 원본이 필요하면 위 실행 명령의 마지막 줄을 다음과 같이 바꾼다.

```bash
grafana/k6:0.57.0 run --out "json=/scripts/results/${RUN_ID}-metrics.json" /scripts/baseline.js
```

이 출력은 요청별 시각·메트릭·태그를 기록하므로 파일이 커질 수 있다. 측정 시나리오가 시작한 시각에 `WARMUP`을 더한 때부터 `HOLD` 동안만 추출한다. 시딩 시각이나 컨테이너 시작 시각을 부하 시작 시각으로 사용하지 않는다. `scenario`·`name` 태그로 시딩과 다른 엔드포인트를 제외하고 해당 구간의 요청 수/시간, 지연 분포, 실패율을 계산한다. k6 콘솔의 실행 단계도 함께 확인한다.

서버 측 지표는 같은 유지 구간으로 Grafana `http://localhost:3000`의 `mynotion-overview` 대시보드 또는 Prometheus `http://localhost:9090`에서 확인한다. Micrometer `uri` 라벨에는 `/api`가 붙지 않는다. `/calendar/events`의 GET·POST와 `/ai/chat-logs` GET의 p95·RPS, HikariCP active/pending, JVM 힙, CPU를 기록한다. 같은 머신의 자원 경쟁을 파악하려면 측정 중 별도 터미널에서 `docker stats`도 확인한다. k6 자체 메트릭의 Prometheus remote write 설정은 포함하지 않는다.

실제 실행·이미지 pull·유지 구간 분석·Grafana 확인과 실측 결과 문서 작성은 측정 담당자가 수행한다. 측정하지 않은 수치는 기록하지 않는다. H2는 스크립트 디버깅용으로만 사용하며 기준선 결과로 기록하지 않는다.
