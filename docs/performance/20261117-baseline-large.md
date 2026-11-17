# 대용량 대화 로그 기준선 측정 (2026-11-17)

사용자당 대화 로그 수를 200 → 2,000 → 10,000건으로 늘려 가며, 2단계 '대화 로그 페이징' 작업 **전**의 수치를 기록한다. 지시서: `docs/tasks/20261117-load-test-large.md`. 모든 수치는 실측값이며, 측정하지 않은 값은 "미측정"으로 둔다.

## 환경
- 측정 커밋: `af3e70e` (`feature/load-test-large`, `load-test/`·운영 코드는 main `fe0b9b0`과 같음)
- 프로필: `prod` (docker compose 스택, 단계마다 `docker compose down -v`로 볼륨 초기화)
- k6: `grafana/k6:0.57.0` (linux/arm64), 네트워크 `mynotion-net`, `BASE_URL=http://app:8080/api`, `SETUP_TIMEOUT=15m`
- MySQL 8.0.46 (aarch64, `innodb_buffer_pool_size` 128MiB 기본값), 앱 OpenJDK 17.0.21, `JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=75.0`(힙 최대 약 5.8GiB), `TZ=Asia/Seoul`
- 머신: MacBook Pro (Apple M5, 10코어: 성능 4 / 효율 6), 메모리 24GB, 전원 연결. Docker Desktop 할당 CPU 10 / 메모리 약 7.7GB
- **k6와 서버가 같은 머신**에서 동작한다. 결과는 같은 머신·같은 Docker 설정에서의 상대 비교용이다.
- 측정 전체를 `caffeinate -i -s`로 감싸 실행했고, 단계별 측정 구간 전원 로그에 잠자기 이벤트가 0건임을 확인했다.
- Orchestrator Browser 패널에 Grafana 대시보드(11:22:05~)와 Prometheus 그래프(11:23:23~)를 열어 둔 채 측정했다. 단계 A 유지 구간의 앞 약 2분만 브라우저 없이 측정되었고, B·C·A2는 전 구간 열려 있었다.

## 시나리오
첫 기준선(`docs/performance/20261117-baseline.md`)과 같은 조건에서 `SEED_ANALYZE_PER_USER`만 바꿨다.
- 사용자 10명, 사용자별 사전 일정 30건(2030-01), VU 20(read 12 / chat_logs 4 / write 4), `ramping-vus` 워밍업 1m → 유지 5m → 감소 1m, think time 1초
- read: `GET /calendar/events?year=2030&month=1` / chat_logs: `GET /ai/chat-logs?limit=30` / write: `POST /calendar/events`(2030-02)

| 단계 | `SEED_ANALYZE_PER_USER` | 사용자당 대화 로그 | 전체 `chat_logs` 행 | setup 요청 | setup 실측 시간 |
|---|---|---|---|---|---|
| A | 100 | 200 | 2,000 | 1,310 | 6.3초 |
| B | 1,000 | 2,000 | 20,000 | 10,310 | 32.8초 |
| C | 5,000 | 10,000 | 100,000 | 50,310 | 53.5초 |
| A2 (A 재측정) | 100 | 200 | 2,000 | 1,310 | 4.3초 |

- 시딩 수는 MySQL에서 직접 확인했다(사용자당 200 / 2,000 / 10,000건). 단계별 summary JSON은 모두 `setupCompleted: true`다.
- A2는 단계 A의 서버 측 지표 수집이 분석 스크립트 오류로 실패해 같은 조건으로 다시 잰 것이다(아래 '측정 기록').

## 결과: k6 클라이언트 측 (유지 구간 5분)
`--out json` 시계열에서 시나리오 시작 후 60~360초 요청만 집계했다.

| 단계 | 로그/사용자 | 엔드포인트 | 요청 수 | RPS | p50 | p95 | p99 | 에러율 |
|---|---|---|---|---|---|---|---|---|
| A | 200 | `GET /calendar/events` | 3,588 | 11.96 | 4.09ms | 7.85ms | 12.03ms | 0% |
| A | 200 | `GET /ai/chat-logs` | 1,196 | 3.99 | 4.52ms | 7.73ms | 10.92ms | 0% |
| A | 200 | `POST /calendar/events` | 1,191 | 3.97 | 7.51ms | 10.46ms | 12.04ms | 0% |
| A2 | 200 | `GET /calendar/events` | 3,576 | 11.92 | 5.30ms | 9.82ms | 13.90ms | 0% |
| A2 | 200 | `GET /ai/chat-logs` | 1,192 | 3.97 | 5.82ms | 10.69ms | 14.79ms | 0% |
| A2 | 200 | `POST /calendar/events` | 1,192 | 3.97 | 6.17ms | 11.66ms | 16.21ms | 0% |
| B | 2,000 | `GET /calendar/events` | 3,588 | 11.96 | 4.04ms | 5.89ms | 7.31ms | 0% |
| B | 2,000 | `GET /ai/chat-logs` | 1,186 | 3.95 | 11.62ms | 13.67ms | 15.02ms | 0% |
| B | 2,000 | `POST /calendar/events` | 1,192 | 3.97 | 7.52ms | 10.55ms | 11.68ms | 0% |
| C | 10,000 | `GET /calendar/events` | 3,587 | 11.96 | 4.25ms | 7.35ms | 9.30ms | 0% |
| **C** | **10,000** | **`GET /ai/chat-logs`** | 1,156 | 3.85 | **35.73ms** | **38.43ms** | **40.18ms** | 0% |
| C | 10,000 | `POST /calendar/events` | 1,196 | 3.99 | 4.78ms | 8.82ms | 11.39ms | 0% |

## 결과: 서버 측 (Prometheus, 같은 유지 구간 끝 시점 `[300s]`)
단계 A의 서버 측 지표는 수집하지 못했다(미측정). 같은 조건의 A2 값을 쓴다. 응답 시간 백분위는 히스토그램 버킷을 보간한 추정값이다.

| 단계 | chat-logs p95 | events GET p95 | events POST p95 | 5xx RPS |
|---|---|---|---|---|
| A | 미측정 | 미측정 | 미측정 | 미측정 |
| A2 | 8.78ms | 8.45ms | 9.13ms | 0 |
| B | 13.40ms | 5.34ms | 9.95ms | 0 |
| C | 38.86ms | 6.27ms | 7.29ms | 0 |

| 단계 | 메모리 할당 속도 | GC 횟수/초 | GC 시간/초 | GC 정지 최대 | 힙 사용 최대 | Hikari 활성 최대 / 대기 최대 (풀 10) | 커넥션 점유 평균 | 앱 CPU 평균 / 최대 | 시스템 CPU 평균 |
|---|---|---|---|---|---|---|---|---|---|
| A2 | 10.3 MB/s | 0.077 | 0.45 ms/s | 13ms | 170.1MiB | 0 / 0 | 3.13ms | 0.90% / 1.47% | 1.77% |
| B | 24.3 MB/s | 0.161 | 0.34 ms/s | 7ms | 185.4MiB | 0 / 0 | 4.16ms | 0.95% / 1.19% | 2.09% |
| C | **83.6 MB/s** | **0.961** | 2.31 ms/s | 6ms | 146.2MiB | 1 / 0 | **8.22ms** | 1.52% / 1.84% | 2.87% |

- 메모리 할당 속도는 MB = 10^6 bytes 기준이다. 힙 사용 최대는 GC 시점에 따라 흔들려 단계 비교에는 할당 속도·GC 횟수를 우선 본다.
- Hikari 활성 최대 0은 15초 수집 간격 사이에 커넥션을 잡고 있던 순간이 찍히지 않았다는 뜻이다(요청당 점유 시간이 수 ms로 짧음).

컨테이너별 CPU·메모리 (`docker stats --no-stream`, 유지 구간 중간 1회):

| 단계 | 앱 CPU / 메모리 | MySQL CPU / 메모리 | Grafana 메모리 | Prometheus 메모리 | k6 CPU / 메모리 |
|---|---|---|---|---|---|
| A | 6.51% / 484.4MiB | 2.88% / 383.8MiB | 451MiB | 40.2MiB | 2.12% / 75.5MiB |
| A2 | 6.52% / 532.7MiB | 2.40% / 386.7MiB | 397.1MiB | 41.4MiB | 1.84% / 28.8MiB |
| B | 8.16% / 552.8MiB | 3.68% / 404.3MiB | 399.7MiB | 44.1MiB | 2.39% / 28.9MiB |
| C | 12.32% / 490.2MiB | 6.29% / 427.2MiB | 392.6MiB | 37.9MiB | 1.43% / 36.5MiB |

(`docker stats`의 CPU%는 코어 1개 = 100% 기준의 순간값이라 Prometheus `process_cpu_usage`(전체 코어 대비 평균)와 단위가 다르다.)

## 결과: MySQL (`performance_schema`, 단계 전체 실행 기준)
측정 직후 `performance_schema.events_statements_summary_by_digest`에서 읽었다. 단계마다 볼륨을 초기화하므로 통계는 단계별로 분리된다. 시간은 워밍업·감소를 포함한 단계 전체 평균이다.

| 단계 | 쿼리 | 호출 수 | 평균 실행 시간 | 최대 | 1회당 읽은 행 | 1회당 보낸 행 |
|---|---|---|---|---|---|---|
| A | 대화 로그 조회 | 1,428 | 0.451ms | 2.985ms | 200 | 200 |
| A2 | 대화 로그 조회 | 1,427 | 0.494ms | 2.046ms | 200 | 200 |
| B | 대화 로그 조회 | 1,424 | 2.744ms | 4.755ms | 2,000 | 2,000 |
| C | 대화 로그 조회 | 1,390 | **12.078ms** | 22.913ms | **10,007** | **10,007** |
| A / A2 / B / C | 월별 일정 조회(대조군) | 4,213~4,254 | 0.326~0.382ms | 2.40~3.94ms | 30 | 30 |

- 앱은 항상 30건만 응답하지만, **DB는 사용자의 대화 로그 전체를 읽어 앱으로 보낸다.** 1회당 읽은 행·보낸 행이 로그 수와 같다.
- C의 1회당 행 수 10,007은 단계 전체 평균값이다(사용자당 시딩 수는 10,000건으로 직접 확인).
- `EXPLAIN`(같은 형태의 쿼리): 모든 단계에서 `type=ref`, `key=idx_chat_user_created`, `Backward index scan`. 인덱스를 쓰지만 `LIMIT`이 없어 해당 사용자의 행을 끝까지 읽는다. (`rows` 열은 옵티마이저 추정치라 C에서 19,036으로 표시됐다.)

사용한 PromQL:
```promql
histogram_quantile(0.50, sum by (le, uri, method) (rate(http_server_requests_seconds_bucket{application="mynotion-backend", uri=~"/calendar/events|/ai/chat-logs"}[300s])))
histogram_quantile(0.95, sum by (le, uri, method) (rate(http_server_requests_seconds_bucket{application="mynotion-backend", uri=~"/calendar/events|/ai/chat-logs"}[300s])))
histogram_quantile(0.99, sum by (le, uri, method) (rate(http_server_requests_seconds_bucket{application="mynotion-backend", uri=~"/calendar/events|/ai/chat-logs"}[300s])))
sum by (uri, method) (rate(http_server_requests_seconds_count{application="mynotion-backend", uri=~"/calendar/events|/ai/chat-logs"}[300s]))
sum(rate(http_server_requests_seconds_count{application="mynotion-backend", status=~"5.."}[300s])) or vector(0)
max_over_time(hikaricp_connections_active{application="mynotion-backend"}[300s])
max_over_time(hikaricp_connections_pending{application="mynotion-backend"}[300s])
max_over_time(hikaricp_connections_max{application="mynotion-backend"}[300s])
sum(rate(hikaricp_connections_usage_seconds_sum{application="mynotion-backend"}[300s])) / sum(rate(hikaricp_connections_usage_seconds_count{application="mynotion-backend"}[300s]))
max_over_time(sum(jvm_memory_used_bytes{application="mynotion-backend", area="heap"})[300s:15s])
sum(rate(jvm_gc_pause_seconds_count{application="mynotion-backend"}[300s]))
sum(rate(jvm_gc_pause_seconds_sum{application="mynotion-backend"}[300s]))
max(max_over_time(jvm_gc_pause_seconds_max{application="mynotion-backend"}[300s]))
sum(rate(jvm_gc_memory_allocated_bytes_total{application="mynotion-backend"}[300s]))
avg_over_time(process_cpu_usage{application="mynotion-backend"}[300s])
max_over_time(process_cpu_usage{application="mynotion-backend"}[300s])
avg_over_time(system_cpu_usage{application="mynotion-backend"}[300s])
```
GC·Hikari 지표명은 측정 전 `/actuator/prometheus` 출력에서 확인했다(`jvm_gc_pause_seconds_{count,sum,max}`, `jvm_gc_memory_allocated_bytes_total`, `hikaricp_connections_usage_seconds_{count,sum,max}`).

## 대조군 변화
판정 기준(지시서): 단계 A 대비 p95 증가가 20% 이내면 "변화 없음".

| 엔드포인트 | A | A2 | B | C | 판정 |
|---|---|---|---|---|---|
| `GET /calendar/events` p95 | 7.85ms | 9.82ms | 5.89ms (A 대비 −25%) | 7.35ms (A 대비 −6%) | 변화 없음 |
| `POST /calendar/events` p95 | 10.46ms | 11.66ms | 10.55ms (+1%) | 8.82ms (−16%) | 변화 없음 |

- 같은 조건인 A와 A2의 p95가 약 2~3ms 다르다. 이것이 이 환경의 **측정 간 흔들림** 크기다. 대조군의 단계 간 차이는 모두 이 범위 안이거나 감소다.
- 대화 로그 조회의 C(38.43ms)는 A·A2 대비 약 3.6~5.0배로, 흔들림보다 훨씬 크다. **느려진 원인은 데이터 양이고, 머신 자원 경쟁이 아니다**(서버 CPU 1.5%, Hikari 대기 0).

## k6 summary 발췌 (단계별, 전체 실행 집계)
| 단계 | checks | http_reqs (setup 포함) | iterations | threshold 7개 (현재 임시값 p95 300/300/500ms, 실패율 <0.01, checks >0.99) | 결과 JSON 크기 |
|---|---|---|---|---|---|
| A | 14,318 / 14,318 | 8,469 | 7,159 | 모두 통과 | 26.9MB |
| A2 | 14,320 / 14,320 | 8,470 | 7,160 | 모두 통과 | 26.9MB |
| B | 14,310 / 14,310 | 17,465 | 7,155 | 모두 통과 | 47.7MB |
| C | 14,242 / 14,242 | 57,431 | 7,121 | 모두 통과 | 140.2MB |

- 단계별 k6 종료 코드는 모두 0이다. `chat_logs` 검사(`대화 로그 수 일치`, 30건)가 checks에 포함되어 있다.

## threshold 후보 (사용자 확정 전)
지시서 6절 산정 방식: `후보 = 단계 A 유지 구간 p95 × K(3~5)`를 50ms 단위로 올림. 흔들림을 고려해 A와 A2 중 큰 값을 기준으로 삼았다.

| 대상 | 기준 p95 (max(A, A2)) | K=3 | K=4 | K=5 | 현재 임시값 |
|---|---|---|---|---|---|
| `P95_READ_MS` (events_list) | 9.82ms | 50ms | 50ms | 50ms | 300ms |
| `P95_CHAT_LOGS_MS` (chat_logs_list) | 10.69ms | 50ms | 50ms | 100ms | 300ms |
| `P95_WRITE_MS` (events_create) | 11.66ms | 50ms | 50ms | 100ms | 500ms |

단계별 통과 여부(후보 50ms 기준): A·A2·B·C 모두 세 엔드포인트가 통과한다. **단계 C의 대화 로그 조회(38.43ms)도 50ms 아래라서, 이 후보로는 데이터가 10,000건까지 늘어난 회귀를 잡지 못한다.**

PM 제안 선택지:
- **안 1 — 회귀 감지선**: 세 엔드포인트 모두 50ms(K=3~4). 기본 데이터(A)에서 큰 성능 저하를 잡는 용도다. 대용량 회귀는 이 문서 같은 별도 측정으로 본다.
- **안 2 — 감지선 + 대화 로그 목표**: read·write 50ms, chat_logs **30ms**(기준 p95의 약 2.8배). 기본 데이터는 통과하고 단계 C 조건은 실패한다. 페이징 after의 목표를 "단계 C 조건에서도 30ms 통과"로 둘 수 있다.
- 공통: `MAX_FAIL_RATE` 0.01, checks 0.99는 바꿀 근거가 없어 유지를 제안한다.

## 관찰 및 다음 단계에 쓸 점
1. **대화 로그 조회는 로그 수에 비례해 느려진다.** 로그가 50배(200 → 10,000건)가 되자 k6 p95는 약 3.6~5.0배, p50은 약 6~8배, MySQL 평균 실행 시간은 약 25배(0.45 → 12.08ms)가 됐다. 응답은 항상 30건이다.
2. **원인은 애플리케이션에서 자르는 `LIMIT`이다.** `EXPLAIN`상 인덱스(`idx_chat_user_created`)는 쓰지만 DB에 `LIMIT`이 없어 사용자의 행을 전부 읽고 보낸다. 앱은 받은 행을 모두 엔티티로 만든 뒤 30건만 남긴다. 그래서 메모리 할당 속도(10.3 → 83.6 MB/s)와 GC 횟수(0.077 → 0.961회/초), 요청당 커넥션 점유 시간(3.13 → 8.22ms)이 함께 늘었다.
3. **서버는 아직 여유가 있다.** C에서도 앱 CPU 평균 1.5%, Hikari 대기 0이다. 이번 측정은 "데이터가 많아지면 요청 하나가 느려진다"를 보인 것이고, 처리 한계는 측정하지 않았다.
4. **2단계 페이징 after 측정 방법**: 같은 머신·같은 Docker 설정에서 단계 A·B·C를 같은 순서로 다시 재고, 이 문서의 표와 나란히 비교한다. 기대: chat-logs의 1회당 읽은 행이 30으로 고정되고, p95가 단계와 상관없이 대조군 수준에 머무는 것. 시딩이 `/ai/analyze`에 의존하므로 3단계(LLM 연동) 전에 잰다.
5. 단계 D(사용자당 20,000건)는 C에서 이미 차이가 뚜렷해 추가하지 않는다(지시서의 결정 조건: C에서 차이가 작을 때만).

## 측정 기록
| 단계 | k6 실행 (KST) | 소요 | 종료 코드 | 잠자기 이벤트 |
|---|---|---|---|---|
| A | 11:19:04 ~ 11:26:12 | 428초 | 0 | 0 |
| B | 11:26:33 ~ 11:34:08 | 455초 | 0 | 0 |
| C | 11:34:29 ~ 11:42:24 | 475초 | 0 | 0 |
| A2 | 11:42:58 ~ 11:50:04 | 426초 | 0 | 0 |

- 단계 A 직후 분석 스크립트가 PromQL의 중괄호를 Python `str.format` 자리표시자로 해석해(`KeyError`) Prometheus 조회에 실패했다. 다음 단계 시작 때 볼륨 초기화로 Prometheus 데이터가 지워져 **A의 서버 측 지표는 복구할 수 없었다.** 스크립트를 고친 뒤 B·C는 정상 수집했고, A는 같은 조건으로 A2를 다시 측정했다. A의 클라이언트 측 값과 MySQL 통계는 정상 수집되어 그대로 기록했다.
- 재현 순서: 단계마다 `docker compose down -v` → `docker compose up -d --build` → 4개 서비스 healthy → `caffeinate -i -s`로 감싼 k6 실행(`-e SEED_ANALYZE_PER_USER=<값> -e SETUP_TIMEOUT=15m --out json=...`) → 유지 구간 분석·Prometheus 조회 → `performance_schema`·`EXPLAIN` → 다음 단계.
