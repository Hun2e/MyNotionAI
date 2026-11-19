# 대용량 대화 로그 기준선 측정
- 브랜치: feature/load-test-large
- 상태: 완료
- 선행 조건: `feature/load-test` 머지(PR #10) → **충족**. k6 스크립트(`load-test/`)와 첫 기준선(`docs/performance/20261117-baseline.md`)이 main에 있다.
- 이 작업은 **측정과 기록만** 한다. 코드는 바꾸지 않는다(threshold 기본값 변경은 사용자 확정 후 별도 커밋, 아래 6절).

## 배경 (왜 하는가)
1. **첫 기준선에서는 대화 로그 조회의 문제가 보이지 않았다.** 사용자당 대화 로그 200건에서 `GET /ai/chat-logs` p95는 11.49ms로 일정 조회(10.85ms)와 차이가 없었다. 앱 CPU 평균 0.8%, Hikari 활성 커넥션 최대 1로 서버는 거의 놀고 있었다.
2. **코드에는 데이터가 많아질수록 느려질 구조가 있다.** `AiService.getChatLogs`는 `ChatLogRepository.findByUserIdOrderByCreatedAtDesc(userId)`로 사용자의 대화 로그를 **전부** 읽은 뒤 Java에서 `stream().limit(safeLimit)`로 30건만 남긴다. 응답은 항상 30건이지만, DB에서 읽고 엔티티로 만드는 행 수는 로그 수 n에 비례한다(O(n)). 200건에서는 이 비용이 측정 오차에 묻힌다.
3. **이 측정의 목적은 두 가지다.**
   - **2단계 '대화 로그 페이징'의 before 수치**: 로그 수를 늘린 조건에서 지금 구현이 얼마나 느려지는지 재 둔다. 페이징 구현 후 같은 조건으로 after를 잰다.
   - **threshold 확정 근거**: 지금 `P95_*_MS` 값(300/300/500ms)은 근거 없는 임시값이다. 사용자 결정에 따라 이 대용량 측정 후에 확정한다.

> **학습 포인트**
> - **데이터 양이 성능을 바꾼다.** 같은 코드, 같은 부하라도 테이블에 쌓인 행 수에 따라 응답 시간이 달라진다. O(n) 조회는 개발 환경의 적은 데이터에서는 드러나지 않는다.
> - **LIMIT을 DB에서 하느냐, 애플리케이션에서 하느냐**: DB에서 `LIMIT 30`을 하면 인덱스에서 30행만 읽고 멈춘다. 애플리케이션에서 자르면 n행을 모두 네트워크로 받아 엔티티로 만든 뒤 대부분 버린다. 이 측정은 그 차이를 숫자로 남긴다.
> - **대조군**: 로그 수와 상관없는 일정 API를 함께 재서, 대화 로그 조회만 느려졌는지 아니면 머신·환경 탓에 전체가 느려졌는지 구분한다.

## 할 일 (범위)

### 1. 측정 매트릭스
사용자 수·VU·기간·조회 `limit` 등 다른 조건은 **첫 기준선과 같게** 두고, 사용자당 대화 로그 수만 3단계로 바꾼다. 단계마다 **볼륨을 초기화**(`docker compose down -v`)한 뒤 측정한다.

| 단계 | `SEED_ANALYZE_PER_USER` | 사용자당 대화 로그 | 전체 `chat_logs` 행(10명) | setup 요청 수 | 예상 setup 시간 |
|---|---|---|---|---|---|
| A | 100 (기본값) | 200 | 2,000 | 1,310 | 3.8초 (첫 기준선 실측) |
| B | 1,000 | 2,000 | 20,000 | 10,310 | 약 30초 (추정) |
| C | 5,000 | 10,000 | 100,000 | 50,310 | 약 2.5분 (추정) |

- setup 요청 수 = 회원가입 10 + 일정 300 + analyze `10 × SEED_ANALYZE_PER_USER`. analyze 1회에 대화 로그 2건(USER·ASSISTANT)과 `ai_actions` 1건이 쌓인다.
- 단계 값 근거:
  - **A는 첫 기준선과 같은 조건을 다시 잰다.** 같은 날·같은 환경에서 재야 B·C와 비교할 수 있다. 첫 기준선 수치(11.49ms)와 비슷하게 나오는지가 재현성 확인도 된다.
  - **B·C는 10배·50배**다. 로그 스케일로 3점을 찍으면 "로그 수에 비례해 늘어나는지"를 볼 수 있다. 10,000건은 "매일 AI와 20번 대화(로그 40건)하는 사용자가 약 250일 쓴 양"으로, 실제 사용에서 나올 수 있는 크기다.
  - **C에서 멈추는 이유**: 50,000건을 넘기면 setup이 수 분을 넘어가고 `--out json` 파일도 커진다. C에서도 차이가 작으면 단계를 더 늘릴지는 질문/이슈로 결정한다.
- 순서는 A → B → C로 한 번씩 측정한다. 각 단계는 1회 측정이 기본이며, 측정 중 잠자기·에러 등으로 오염되면 그 단계만 볼륨 초기화부터 다시 한다.

### 2. 실행 방법 (스크립트 수정 없음)
`load-test/` 코드를 읽은 결과, **환경 변수만으로 이 측정을 할 수 있다.** 스크립트를 고치지 않는다.
- `SEED_ANALYZE_PER_USER`: 0 이상 정수이고 상한이 없다(`lib/config.js`의 `numberValue('SEED_ANALYZE_PER_USER', 0, Infinity, true)`).
- `SETUP_TIMEOUT`: 기간 문자열(`15m` 등)로 바꿀 수 있다.
- `chat_logs` 검사(`대화 로그 수 일치`)는 `min(CHAT_LOGS_LIMIT, SEED_ANALYZE_PER_USER × 2)`라서 세 단계 모두 30건을 기대한다. 그대로 맞다.
- 시딩 수는 setup 성공으로 보장된다. `seedChatLogs`가 analyze 응답이 200이 아니면 예외를 던지므로, summary JSON의 `setupCompleted: true`면 사용자당 로그가 `SEED_ANALYZE_PER_USER × 2`건 쌓인 것이다.
- **`SETUP_TIMEOUT`은 세 단계 모두 `15m`로 통일한다.** C의 예상 setup 시간(약 2.5분)은 기본값 5분의 절반이다. 그런데 로그가 쌓일수록 INSERT가 느려지면(인덱스 크기 증가) 5분을 넘길 수 있고, 그러면 C 측정 전체를 버리게 된다. 제한 시간은 상한일 뿐 측정값에 영향을 주지 않으므로 넉넉히 둔다.

실행 예시(README의 측정 순서와 같다. 저장소 루트에서 Bash로 실행하고 `LOAD_TEST_PASSWORD`는 README대로 입력받는다):
```bash
# 단계별로 STAGE와 SEED_ANALYZE만 바꾼다. (A=100, B=1000, C=5000)
export STAGE=c SEED_ANALYZE=5000
export RUN_ID="large-${STAGE}-$(date +%Y%m%d-%H%M%S)"

caffeinate -i -s docker run --rm -i \
  --network mynotion-net \
  --user "$(id -u):$(id -g)" \
  --workdir /scripts \
  -v "$PWD/load-test:/scripts" \
  -e BASE_URL=http://app:8080/api \
  -e LOAD_TEST_PASSWORD \
  -e RUN_ID \
  -e SEED_ANALYZE_PER_USER="$SEED_ANALYZE" \
  -e SETUP_TIMEOUT=15m \
  grafana/k6:0.57.0 run --out "json=/scripts/results/${RUN_ID}-metrics.json" /scripts/baseline.js
```
- 단계마다 `docker compose down -v` → `docker compose up -d --build` → 4개 서비스 healthy 확인 → 위 명령 순서로 진행한다.
- 첫 기준선 1차 측정이 Mac 잠자기로 오염되었으므로 **`caffeinate -i -s`를 반드시 쓴다.** 덮개를 열어 두고 전원을 연결한다.

### 3. 시딩 방식 유지
- 대화 로그 시딩은 지금처럼 **`setup()`에서 `POST /ai/analyze`를 호출**하는 방식을 유지한다. 현재 `AiService.analyzeScheduleText`는 정규식 규칙 기반이라 외부 호출이 없다.
- 이 전제는 **3단계(LLM 연동) 전에만** 성립한다. 2단계 페이징 after 측정도 3단계 전에 해야 같은 방식으로 시딩할 수 있다.
- SQL 직접 시딩은 이번에 하지 않는다. 장단점은 질문/이슈에 남긴다.

### 4. 대조군 유지
- `read`(일정 조회)·`write`(일정 등록) 시나리오를 그대로 같이 돌린다. VU 배분도 read 12 / chat_logs 4 / write 4로 같다.
- 일정 API는 대화 로그를 읽지 않으므로, **로그 수가 늘어도 일정 API 수치는 변하지 않아야 한다.**
- 대조군 판정 기준(제안): 단계 A 대비 p95 증가가 **20% 이내**면 "변화 없음"으로 본다. 넘으면 대화 로그 조회가 CPU·커넥션을 더 쓰면서 같은 머신의 다른 요청까지 느려졌을 가능성이 있으므로, 서버 지표와 함께 관찰에 적는다. 기준값은 측정 전 사용자와 조정할 수 있다.

### 5. 관찰 항목
모든 수치는 **유지 구간 5분** 기준이다(첫 기준선과 같은 방식). 워밍업·감소·setup 구간은 섞지 않는다.

**① k6 클라이언트 측** (`--out json` 시계열에서 시나리오 시작 후 60~360초만 집계)
- 엔드포인트별(`events_list`, `chat_logs_list`, `events_create`) 요청 수, RPS, p50, p95, p99, 에러율
- 단계 간 비교: `chat_logs_list` p95의 단계 A 대비 배수, 같은 단계 안의 `chat_logs_list` p95 ÷ `events_list` p95

**② 서버 측** (Prometheus, 같은 유지 구간 끝 시점에 `[300s]` 범위)
| 항목 | PromQL(예시) | 보려는 것 |
|---|---|---|
| 엔드포인트별 p50/p95/p99·RPS | 첫 기준선 문서의 `histogram_quantile`·`rate` 쿼리 그대로 | 클라이언트 수치와 같은 경향인지 |
| 5xx RPS | 첫 기준선과 같음 | 에러 없음 확인 |
| JVM 힙 사용량 최대 | `max_over_time(sum(jvm_memory_used_bytes{application="mynotion-backend", area="heap"})[300s:15s])` | 요청당 엔티티를 많이 만들수록 힙이 더 출렁이는지 |
| GC 횟수·시간 | `sum(rate(jvm_gc_pause_seconds_count{application="mynotion-backend"}[300s]))`, `sum(rate(jvm_gc_pause_seconds_sum{...}[300s]))`, `max(max_over_time(jvm_gc_pause_seconds_max{...}[300s]))` | GC가 더 자주·길게 일어나는지 |
| 메모리 할당 속도 | `rate(jvm_gc_memory_allocated_bytes_total{application="mynotion-backend"}[300s])` | 버려지는 엔티티만큼 할당이 늘어나는지 |
| HikariCP 활성/대기/풀 최대 | 첫 기준선과 같음 | 커넥션을 더 오래 잡는지, 대기가 생기는지 |
| HikariCP 커넥션 사용 시간 | `rate(hikaricp_connections_usage_seconds_sum{...}[300s]) / rate(hikaricp_connections_usage_seconds_count{...}[300s])` | 요청당 커넥션 점유 시간(모든 엔드포인트 평균) |
| 앱 CPU 평균/최대, 시스템 CPU 평균 | 첫 기준선과 같음 | 엔티티 변환 비용이 CPU에 보이는지 |

- GC·Hikari 사용 시간 지표명은 Micrometer 기본값을 보고 쓴 **예상 이름**이다. 측정 전에 `docker compose exec app curl -s http://localhost:8081/actuator/prometheus | grep -E '^(jvm_gc|hikaricp_connections_usage)'`로 실제 이름을 확인하고, 다르면 실제 이름으로 바꿔 쓰고 질문/이슈에 기록한다.
- 힙 최대값은 GC 시점에 따라 흔들리므로, 단계 비교는 GC 횟수·할당 속도를 우선 본다.

**③ 범위 밖 (기본)**
- MySQL 버퍼 풀 적중률 등 상시 수집 지표(mysqld exporter가 없어 compose 수정 필요). 쿼리 실행 시간·읽은 행 수는 사용자 결정에 따라 `performance_schema`로 측정 직후 확인한다.
- 컨테이너별 CPU(`docker stats`)는 **선택**이다. 재면 결과 문서에 적고, 안 재면 "미측정"으로 둔다.

### 6. threshold 확정 절차
threshold는 "이 정도면 빠르다"는 목표가 아니라, **기본 데이터(단계 A)로 돌렸을 때 성능이 크게 나빠지면 잡아내는 회귀 감지선**으로 정한다. `lib/config.js`의 기본값은 기본 데이터(로그 200건) 실행에 적용되기 때문이다.

1. **PM이 후보 값을 제안한다** (측정 후, 결과 문서의 '관찰'에 적는다).
   - 산정 방식(제안): `후보 = 단계 A 유지 구간 클라이언트 p95 × 여유 배수 K`를 **50ms 단위로 올림**한다. K는 3~5 중에서 고른다. 같은 머신에서 k6와 서버가 함께 돌아 흔들림이 있으므로 여유를 크게 둔다.
     - 예시(계산 방식 설명용, 확정값 아님): p95가 11ms이고 K=4면 44ms → 50ms.
   - 대상: `P95_READ_MS`, `P95_CHAT_LOGS_MS`, `P95_WRITE_MS`. `MAX_FAIL_RATE`(0.01)와 check 성공률(0.99)은 바꿀 근거가 없으면 유지를 제안한다.
   - 함께 적는다: 단계 B·C 결과가 그 후보를 **통과하는지/실패하는지**. 단계 C에서 `chat_logs_list`가 실패한다면, 그 자체가 페이징 after의 목표("C에서도 통과")가 된다.
2. **사용자가 값을 확정한다.** 확정 내용(값, K, 근거 단계)을 이 지시서의 '사용자 결정'에 기록한다.
3. **확정 후 기본값을 바꿀지 결정한다.** 바꾸기로 하면 이 브랜치에서 **별도 커밋**으로 `load-test/lib/config.js`의 `DEFAULTS.P95_*`와 `load-test/README.md`의 threshold 표·"임시값" 문구만 고친다. 바꾸지 않으면 결과 문서에 "후보만 기록, 기본값 유지"라고 적는다.

### 7. 결과 문서 (`docs/performance/YYYYMMDD-baseline-large.md`, 측정한 날짜)
첫 기준선 문서와 같은 형식이다. **실측값만** 적고, 못 잰 값은 "미측정"으로 둔다(추정값 금지).
```markdown
# 대용량 대화 로그 기준선 측정 (YYYY-MM-DD)
## 환경
- 측정 커밋, 프로필, k6 이미지, MySQL·JVM 버전, 머신, Docker Desktop 할당 자원
- k6와 서버가 같은 머신이라는 점, caffeinate 사용
## 시나리오
- 첫 기준선과 같은 조건 + 단계별 SEED_ANALYZE_PER_USER, SETUP_TIMEOUT=15m
- 단계별 setup 요청 수와 실제 setup 시간
## 결과: k6 클라이언트 측 (유지 구간)
| 단계 | 로그/사용자 | 엔드포인트 | 요청 수 | RPS | p50 | p95 | p99 | 에러율 |
## 결과: 서버 측 (Prometheus)
| 단계 | 엔드포인트별 p95 | 힙 최대 | GC 횟수/s | GC 시간/s | 할당 속도 | Hikari 활성 최대/대기 최대 | 커넥션 사용 시간 | 앱 CPU 평균/최대 |
- 사용한 PromQL
## 대조군 변화
- 단계 A 대비 events_list·events_create p95 변화율과 판정(변화 없음/있음)
## k6 summary 발췌 (단계별, 전체 실행 집계)
## threshold 후보
- 산정 방식, 후보 값, 단계별 통과/실패
## 관찰 및 다음 단계에 쓸 점
## 측정 기록
- 단계별 측정 시각(KST), 잠자기 확인 결과, 버린 측정과 이유
```

## 하지 않을 일 (범위 밖)
- 성능 개선 전부: 대화 로그 페이징(DB `LIMIT`/`Pageable`), 인덱스 추가, 캐싱, 쿼리·JVM·커넥션 풀 튜닝. 페이징은 2단계 별도 작업이다.
- 운영 코드(`backend/**`)·설정(`application*.yml`) 수정
- `docker-compose.yml`, `monitoring/**` 수정(mysqld exporter 추가 등)
- `load-test/` 스크립트 수정. 단, 6절의 threshold 기본값 변경(사용자 확정 후, 별도 커밋)은 예외다.
- CI(GitHub Actions) 연동
- **처리 한계(포화점) 측정**: VU를 늘려 RPS가 더 이상 오르지 않는 지점을 찾는 측정은 별도 작업으로 한다. 이번에는 VU 20, think time 1초를 유지한다.
- SQL 직접 시딩, 테스트 전용 시딩 API 추가
- `AGENTS.md`, 루트 `README.md` 수정(PM이 검증 후 반영)

## 완료 조건 (검증 가능한 항목)
- [x] 단계 A·B·C 각각 볼륨 초기화 후 측정했고, k6 **종료 코드 0**이다
- [x] 단계별 summary JSON에 `setupCompleted: true`가 있고, 실제 setup 시간이 `SETUP_TIMEOUT` 안이다
- [x] 단계별로 `chat_logs` 검사(`대화 로그 수 일치`, 30건)를 포함한 checks 성공률이 0.99를 넘는다
- [x] `docs/performance/YYYYMMDD-baseline-large.md`에 단계별 클라이언트 측·서버 측 결과표가 있고, 모든 수치가 실측값이다
- [x] GC·Hikari 사용 시간 지표명을 `/actuator/prometheus`에서 확인했고, 결과 문서의 PromQL이 실제 이름을 쓴다
- [x] 대조군(`events_list`, `events_create`)의 단계 A 대비 변화율과 판정을 기록했다
- [x] 측정 구간에 **잠자기가 없음**을 확인했다(`pmset -g log | grep -E 'Sleep|Wake'`로 단계별 측정 시각 확인, 결과를 '측정 기록'에 적음)
- [x] threshold 후보(산정 방식·값·단계별 통과 여부)를 결과 문서에 적었고, 사용자 확정 내용을 이 지시서 '사용자 결정'에 기록했다
- [x] 기본값을 바꾸기로 했다면 `load-test/lib/config.js`·`load-test/README.md` 변경이 별도 커밋이고, 기본값으로 단계 A 조건을 짧게 실행해 threshold가 통과한다(종료 코드 0)
- [x] `backend/` 변경 없음(`git diff --stat main -- backend` 결과 없음). 운영 코드 영향이 없지만 확인용으로 `cd backend && JAVA_HOME=$(/usr/libexec/java_home -v 17) mvn test` 1회 실행해 **Tests run 56, Failures 0, Errors 0** 유지
- [x] 수정·추가 파일은 결과 문서, 이 지시서(검증 기록), 그리고 (확정 시) `load-test/lib/config.js`·`load-test/README.md`뿐이다. `load-test/results/`는 git에 포함되지 않는다

## 참고 파일
- `docs/performance/20261117-baseline.md` (첫 기준선: 형식, 유지 구간 집계 방식, PromQL, 측정 기록)
- `docs/tasks/20261116-load-test.md` (질문/이슈, 검증 기록, 시딩이 `/ai/analyze`에 의존하는 이유)
- `load-test/README.md` (측정 순서, 환경 변수, `--out json`과 유지 구간 추출)
- `load-test/lib/config.js` (`DEFAULTS`, `SEED_ANALYZE_PER_USER`·`SETUP_TIMEOUT` 검증 규칙)
- `load-test/baseline.js` (`setup()` 순차 시딩, `chatLogs` 검사, `handleSummary`의 `setupCompleted`)
- `backend/src/main/java/com/mynotionai/service/AiService.java` (`analyzeScheduleText`: INSERT만 함, `getChatLogs`: 전체 조회 후 메모리 `limit`)
- `backend/src/main/java/com/mynotionai/repository/ChatLogRepository.java` (`findByUserIdOrderByCreatedAtDesc`)
- `backend/src/main/resources/db/migration/V1__init_schema.sql` (`chat_logs.content TEXT`, `idx_chat_user_created (user_id, created_at)`)
- `backend/src/main/resources/application.yml` (관리 포트 8081, `http.server.requests` 히스토그램)
- `monitoring/prometheus/prometheus.yml` (수집 주기 15초), `docker-compose.yml` (`JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=75.0`, 컨테이너 메모리 제한 없음)

## 질문/이슈 (Codex 작성)
아래는 PM(Claude)이 확인하지 못한 추측과 결정이 필요한 항목이다. 측정 전 또는 측정 중 확인하고 결과를 이어서 적는다.

- **[PM 사전 표시] 예상 setup 시간**: 첫 기준선 1,310건 3.8초(요청당 약 2.9ms)를 **선형으로 늘린 추정**이다(B 약 30초, C 약 2.5분). `analyzeScheduleText`는 기존 로그를 읽지 않고 INSERT만 하므로 선형에 가까울 것으로 보지만, `chat_logs`·`ai_actions` 인덱스가 커지면 느려질 수 있다. 단계별 실제 setup 시간을 기록한다.
- **[PM 사전 표시] 10,000건에서 응답 크기와 메모리**: 응답은 코드상 항상 30건이라 **크기는 단계와 상관없이 같을 것**이다. 반면 요청 1번에 MySQL에서 10,000행을 받아 `ChatLog` 엔티티 10,000개를 만들므로 요청당 메모리 할당이 크게 늘 것으로 예상하지만 크기는 모른다. 힙 최대는 약 5.8GB(Docker 할당 약 7.7GB × 75%)로 추정되어 메모리 부족보다는 GC 빈도 증가로 보일 가능성이 크다. `jvm_memory_max_bytes`로 실제 힙 최대를 확인한다.
- **[PM 사전 표시] GC·Hikari 지표명**: `jvm_gc_pause_seconds_*`, `jvm_gc_memory_allocated_bytes_total`, `hikaricp_connections_usage_seconds_*`는 Micrometer(Spring Boot 3.2.0) 기본 이름을 기억으로 적은 것이다. 실제 `/actuator/prometheus` 출력에서 확인하고, 다르면 실제 이름과 라벨(`gc`, `action`, `cause` 등)을 적는다.
- **[PM 사전 표시] 측정 총 소요 시간**: 단계마다 스택 재기동·healthy 대기(약 2분, 미측정) + setup + 시나리오 7분이다. A 약 9분, B 약 10분, C 약 12분으로 **측정만 약 30~35분**, 유지 구간 추출·Prometheus 조회·문서 작성까지 합치면 더 걸린다. 모두 추정이다.
- **[PM 사전 표시] `--out json` 파일 크기**: 시계열 출력에는 setup 요청도 들어간다. C는 setup 요청이 약 5만 건이라 파일이 수백 MB가 될 수 있다(첫 기준선 파일 크기는 기록하지 않아 근거 없음). 디스크 여유를 확인하고, 유지 구간 추출 시 `scenario` 태그로 setup 요청을 걸러낸다. 파일 크기를 기록해 둔다.
- **[PM 사전 표시] 느려짐이 작을 수 있음**: 10,000행이 인덱스 순서로 버퍼 풀에 캐시되어 있으면 C에서도 p95 차이가 생각보다 작을 수 있다. C에서 단계 A 대비 차이가 측정 흔들림(약 1~2ms) 수준이면 아래 '단계 D 추가'를 결정한다.
- **[PM 사전 표시] 대조군이 흔들릴 가능성**: k6·앱·MySQL이 한 머신에서 돌아서, 대화 로그 조회가 무거워지면 일정 API도 함께 느려질 수 있다. 그 경우 "데이터 양 때문"과 "자원 경쟁 때문"을 서버 CPU·Hikari 지표로 구분해 관찰에 적는다.
- **[결정 필요] SQL 직접 시딩을 쓸지**: 이번에는 `/ai/analyze` 시딩을 유지한다.
  - SQL 직접 시딩의 장점: 수만~수십만 건도 수 초에 넣을 수 있고, 3단계 LLM 연동 후에도 외부 호출 없이 같은 데이터를 만들 수 있다.
  - 단점: compose MySQL에 직접 접속(루트 비밀번호 필요)하는 별도 스크립트가 생기고, 앱을 거치지 않아 `created_at`·`role`·`content` 분포가 실제 경로와 달라질 수 있다. 스키마가 바뀌면 시딩 SQL도 같이 고쳐야 한다.
  - 제안: C까지는 API 시딩으로 충분하다. 단계 D가 필요하거나 3단계 이후 재측정이 필요해질 때 별도 작업으로 검토한다.
- **[결정 필요] 단계 D(사용자당 20,000건, `SEED_ANALYZE_PER_USER=10000`) 추가 여부**: C에서 차이가 작을 때만 고려한다. setup 요청 약 10만 건, 예상 5분 이상(추정)이다.
- **[결정 필요] MySQL 지표를 볼지**: compose 수정 없이 측정 후 읽기 전용으로 볼 수 있는 방법이 있다. 예: `performance_schema.events_statements_summary_by_digest`에서 `chat_logs` 조회의 평균 실행 시간·`SUM_ROWS_EXAMINED`를 읽거나, `EXPLAIN`으로 `idx_chat_user_created` 사용 여부를 확인한다. MySQL 루트 접속이 필요하다. 포함하면 "DB에서 읽은 행 수가 n에 비례한다"를 직접 보여 줄 수 있다. 기본은 범위 밖이다.
- **[결정 필요] threshold 기본값 변경을 이 브랜치에서 할지**: 6절대로 이 브랜치의 별도 커밋을 제안한다. 측정 문서와 분리하고 싶으면 작은 별도 작업으로 뺀다.
- **[참고] 포화점 측정**: VU를 늘려 처리 한계를 찾는 측정은 이번 범위 밖이다. 페이징 before/after 비교에 필요하다고 판단되면 별도 지시서로 제안한다.

### 사용자 결정
- 2026-11-17: **지시서 승인**(측정 시작은 별도 지시 후). 측정 장소(노트북/데스크톱)는 미정.
- SQL 직접 시딩: 사용하지 않는다(단계 C까지 `/ai/analyze` 시딩 유지).
- 단계 D(사용자당 2만 건): 단계 C 결과를 보고 차이가 작을 때만 추가를 결정한다.
- MySQL 지표: **범위에 포함**한다. compose 수정 없이 측정 직후 읽기 전용으로 `performance_schema.events_statements_summary_by_digest`(`chat_logs` 조회의 평균 실행 시간·`SUM_ROWS_EXAMINED`)와 `EXPLAIN`(`idx_chat_user_created` 사용 여부)을 확인해 결과 문서에 적는다. 단계마다 볼륨을 초기화하므로 통계는 단계별로 분리된다.
- threshold 기본값 변경: 확정 후 이 브랜치에서 별도 커밋으로 한다.
- 2026-11-17: **threshold 안 2 확정** — `P95_READ_MS` 50, `P95_CHAT_LOGS_MS` **30**, `P95_WRITE_MS` 50 (ms). `MAX_FAIL_RATE` 0.01, checks 0.99는 유지. 근거: 기준 p95 max(A, A2) = read 9.82 / chat_logs 10.69 / write 11.66ms. read·write는 K=3~4를 50ms 단위로 올림, chat_logs는 기준의 약 2.8배인 30ms로 두어 기본 데이터는 통과하고 단계 C 조건(38.43ms)은 실패하게 했다. 2단계 페이징 after의 목표: **단계 C 조건에서도 chat_logs p95 30ms 통과**.
- 기본값 변경은 이 브랜치의 별도 커밋으로 한다(`load-test/lib/config.js`의 `DEFAULTS.P95_*`, `load-test/README.md`의 threshold 표·"임시값" 문구).

## 검증 기록 (2026-11-17, Claude, threshold 확정 전 중간 기록)
- 측정 커밋 `af3e70e`, 결과 문서 `docs/performance/20261117-baseline-large.md`(커밋 `4cc75b2`)
- 단계 A·B·C와 A2(A 재측정) 모두 볼륨 초기화 후 측정, k6 종료 코드 0, `setupCompleted: true`, checks 100%, 측정 구간 잠자기 이벤트 0건
- 실제 setup 시간: A 6.3초 / B 32.8초 / C 53.5초 / A2 4.3초 (`SETUP_TIMEOUT=15m` 이내, 예상보다 빠름)
- 핵심 결과(유지 구간 k6 p95, chat-logs): A 7.73ms / A2 10.69ms / B 13.67ms / C 38.43ms. MySQL 1회당 읽은 행 200 / 2,000 / 10,007, 평균 실행 시간 0.451 / 2.744 / 12.078ms
- 대조군(일정 조회·등록) p95는 단계 간 변화 없음(증가 20% 이내, A·A2 간 흔들림 약 2~3ms)
- GC·Hikari 지표명을 측정 전 `/actuator/prometheus`에서 확인(PM 사전 표시 해소)
- `--out json` 크기: A 26.9MB / B 47.7MB / C 140.2MB / A2 26.9MB (PM 사전 표시 해소)
- 단계 D: C에서 차이가 뚜렷해 추가하지 않음(사용자 결정 조건)
- `backend/` 변경 없음, `mvn test` (JDK 17) Tests run 56, Failures 0, Errors 0. `load-test/results/`는 git 제외 확인
- threshold 확정(안 2): read 50 / chat_logs 30 / write 50ms → 별도 커밋 `4511f20`(`load-test/lib/config.js`·`load-test/README.md`만 변경)
- 확정 기본값 확인 실행(12:13~12:15 KST, 볼륨 초기화 스택, 기본 데이터=단계 A 조건, 기간만 30s/1m/30s): k6 종료 코드 0, chat_logs p95 6.58ms(<30), events_list 6.02ms(<50), events_create 10.66ms(<50), 실패율 3개 시나리오 0, checks 3,592/3,592

### 측정 중 문제와 처리
- 단계 A 분석에서 PromQL 중괄호를 Python `str.format`이 자리표시자로 해석해 Prometheus 조회 실패. 다음 단계의 볼륨 초기화로 A의 서버 측 지표는 복구 불가 → 스크립트 수정 후 B·C 정상 수집, A는 A2로 재측정했다.
