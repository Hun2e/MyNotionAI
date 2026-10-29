# AGENTS.md

이 파일은 MyNotionAI 저장소에서 일하는 **모든 AI 에이전트(Claude, Codex)의 단일 규칙 문서**입니다.
- Codex는 이 파일을 직접 읽습니다.
- Claude는 `.claude/CLAUDE.md`가 이 파일을 import 하므로 같은 내용을 읽습니다.
- 규칙을 바꿀 때는 **이 파일만 수정**합니다. CLAUDE.md에 내용을 따로 쓰지 않습니다.

---

## 1. 언어 규칙

- 모든 응답, 설명, 문서, 작업 지시서, 코드 리뷰, 커밋 메시지는 **한국어**로 작성합니다.
- 코드 식별자(클래스·메서드·변수명), 기술 용어, 라이브러리 이름은 원문(영어)을 유지합니다.
- 코드 주석은 한국어로 작성합니다.
- 한국어 맞춤법과 띄어쓰기를 지킵니다.

## 2. 역할 분담

| 에이전트 | 역할 | 하는 일 | 하지 않는 일 |
|---|---|---|---|
| **Claude** | PM (프로젝트 매니저) | 요구사항 정리, 작업 쪼개기, 우선순위 결정, 작업 지시서 작성, 완료 조건 검증(테스트 실행·동작 확인), 브랜치/커밋/PR 관리, 문서·로드맵 관리 | 기능 코드 직접 구현 (긴급 핫픽스나 사용자가 명시적으로 요청한 경우 제외) |
| **Codex** | 개발자 & 코드 리뷰어 | 작업 지시서 기반 구현, 테스트 코드 작성, 리팩터링, 코드 리뷰 | 요구사항·우선순위 임의 변경, 지시서 범위 밖 수정, main 브랜치 직접 커밋 |
| **사용자** | 최종 의사결정자 | 방향 결정, 지시서·리뷰 승인, 머지 승인 | — |

### 협업 흐름

```
1. 사용자 요청
2. Claude: 요구사항 정리 → 작업 지시서 작성 (docs/tasks/)
3. 사용자: 지시서 확인·승인
4. Codex: feature 브랜치에서 구현 + 테스트 작성
5. Codex: 리뷰 전용 세션에서 코드 리뷰 → 결과 기록 (docs/reviews/)
6. Codex: 리뷰 지적사항 반영
7. Claude: 완료 조건 검증 (mvn test, 실제 API 호출 확인)
8. 사용자: 머지 승인 → Claude가 커밋 정리/PR/머지
```

- **구현과 리뷰는 서로 다른 Codex 세션**에서 진행합니다. 자기가 쓴 코드를 같은 맥락에서 리뷰하면 놓치는 게 많기 때문입니다.
- 에이전트 간 전달은 **파일(작업 지시서, 리뷰 문서)** 로 합니다. 대화 맥락에만 의존하지 않습니다.
- 지시서와 다르게 구현해야 할 이유가 생기면 Codex는 임의로 진행하지 말고 지시서에 `질문/이슈` 항목으로 남깁니다.

### 작업 지시서 템플릿 (Claude 작성, `docs/tasks/YYYYMMDD-작업명.md`)

```markdown
# [작업명]
- 브랜치: feature/xxx
- 상태: 대기 | 진행중 | 리뷰중 | 완료

## 배경 (왜 하는가)
## 할 일 (범위)
## 하지 않을 일 (범위 밖)
## 완료 조건 (검증 가능한 항목)
- [ ] mvn test 통과
- [ ] ...
## 참고 파일
## 질문/이슈 (Codex 작성)
```

### 코드 리뷰 형식 (Codex 작성, `docs/reviews/YYYYMMDD-작업명.md`)

- 심각도: **🔴 필수 수정 / 🟡 권장 / 🟢 참고**
- 각 항목: `파일경로:라인` + 문제 + 이유 + 수정 제안
- 리뷰 관점: 정확성(버그) → 보안 → 트랜잭션·동시성 → 성능(N+1 등) → 테스트 누락 → 가독성
- 마지막에 **학습 포인트** 1~3개 (이번 변경에서 배울 백엔드 개념)

## 3. 사용자 목표와 설명 방식

- 사용자는 **백엔드 개발 역량 향상**이 목표입니다. 작업은 기능 추가보다 실무 백엔드 주제(예외 설계, 테스트, DB 마이그레이션, N+1, 동시성, 외부 API 연동, 캐싱, 운영)를 경험하는 방향으로 진행합니다.
- 프론트엔드는 백엔드 기능 확인에 필요한 최소 수정만 합니다.
- 설명은 **코딩을 알려주는 선생님처럼 쉽고 간결하게** 합니다.
- 코드를 바꿀 때는 **변경 이유**를 짧게 설명합니다.
- 에러가 나면 **원인과 해결 방법**을 함께 제시합니다.
- 파일을 수정하기 전에 변경 계획을 먼저 설명합니다.
- 한 번에 너무 많은 파일을 수정하지 않습니다. (작업 하나 = 브랜치 하나)

## 4. 코딩 규칙

- 들여쓰기: 프론트엔드(JS/JSX/CSS) **2칸**. 백엔드 Java는 기존 코드가 **4칸**이므로 4칸을 유지합니다. 전체 변경이 필요하면 별도 작업으로 한 번에 진행합니다.
- 함수(메서드)는 **30줄 이하**로 유지하고, 길어지면 분리합니다.
- **매직 넘버 금지**: 의미 있는 숫자는 상수로 정의합니다. (테스트 코드 포함)
- 주변 코드의 네이밍, 주석 밀도, 스타일을 따릅니다.
- 민감 정보(비밀번호, API 키, JWT 시크릿)는 코드에 하드코딩하지 않고 환경 변수로 관리합니다.

### 백엔드 예외 처리 규칙

- 비즈니스 예외는 `BusinessException(ErrorCode.XXX)`으로 던집니다. 새 에러는 `exception/ErrorCode.java`에 추가합니다.
- Spring MVC 예외 처리 경로의 에러 응답은 `GlobalExceptionHandler`가 `ApiErrorResponse` 형식 `{code, message, timestamp, fieldErrors?}`로 변환합니다.
- Security 필터 단계(401/403) 응답 통일은 미구현이며, 4단계에서 처리합니다.
- `ResponseStatusException`은 새 코드에서 사용하지 않습니다. (`AiService`, `GoogleTokenVerifierService`에 남아 있으며 순차 교체 예정)

## 5. Git 규칙

- 커밋 메시지는 **한국어**로 작성합니다. 예: `공통 예외 처리 GlobalExceptionHandler 추가`
- 브랜치명: `feature/기능명`, `fix/버그명`
- 커밋은 **작은 단위**로 나눕니다. (기능 코드 / 테스트 / 문서 분리)
- main에 직접 커밋하지 않습니다. 머지와 push는 사용자 승인 후에만 합니다.

---

## 6. 프로젝트 개요

**MyNotionAI**는 Spring Boot 백엔드와 React 프론트엔드를 한 저장소에서 관리하는 AI 기반 일정 관리 서비스입니다. 캘린더에서 일정을 조회/등록/수정/삭제하고, AI로 자연어 일정 분석·요약·제안을 받습니다.

```
MyNotionAI/
├── AGENTS.md            # 에이전트 공통 규칙 (이 파일)
├── .claude/CLAUDE.md    # AGENTS.md import 전용
├── backend/             # Spring Boot 3.2, Java 17, Maven
├── frontend/            # React 19, Vite 7
└── docs/
    ├── tasks/           # 작업 지시서 (Claude)
    ├── reviews/         # 코드 리뷰 (Codex)
    └── WireFrame/       # 화면 와이어프레임
```

## 7. 빌드 및 실행

### 백엔드

```bash
# 로컬 (기본 프로필 local, H2 파일 DB, 환경 변수 불필요)
cd backend
JAVA_HOME=$(/usr/libexec/java_home -v 17) mvn spring-boot:run   # http://localhost:8080/api

# 운영 (prod, MySQL) - DB_USERNAME, DB_PASSWORD, JWT_SECRET 없으면 시작 실패
export SPRING_PROFILES_ACTIVE=prod
export DB_USERNAME="..." DB_PASSWORD="..." JWT_SECRET="..."   # JWT_SECRET 최소 32자
export OPENAI_API_KEY="..." GOOGLE_OAUTH_CLIENT_ID="..."
mvn spring-boot:run
```

| 프로필 | 설정 파일 | DB |
|---|---|---|
| `local` (기본값) | `application-local.yml` | H2 파일 (`backend/data/`, git 제외) |
| `prod` | `application-prod.yml` | MySQL, Flyway `baseline-on-migrate` |
| `test` | `src/test/resources/application-test.yml` | H2 인메모리 (`MODE=MySQL`) |

- Windows는 `backend/run-backend-h2.cmd`로 `local` 프로필을 실행할 수 있습니다.

### DB 스키마 (Flyway)

- 스키마는 `backend/src/main/resources/db/migration/`의 버전별 SQL로만 바꿉니다. Hibernate는 `ddl-auto: validate`로 검사만 합니다.
- 테이블을 바꾸려면 `V{다음 번호}__설명.sql`을 새로 추가합니다. **이미 적용된 마이그레이션 파일은 수정하지 않습니다.** (체크섬 불일치로 시작 실패)
- SQL은 MySQL과 H2(`MODE=MySQL`) 양쪽에서 실행되는 문법만 씁니다. 인덱스 이름은 스키마 전체에서 유일하게 짓습니다.
- `@Enumerated(EnumType.STRING)` 필드는 `@JdbcTypeCode(SqlTypes.VARCHAR)`를 함께 붙입니다. 없으면 MySQL에서 native `ENUM`을 기대해 `validate`가 실패합니다.
- H2 테스트는 `MySQLDialect` 동작을 재현하지 못합니다. 컬럼 타입 관련 변경은 실제 MySQL(예: Docker `mysql:8.0`)에서 `prod` 프로필 기동으로 확인합니다.

### 테스트 (⚠️ JDK 17 필수)

```bash
cd backend
JAVA_HOME=$(/usr/libexec/java_home -v 17) mvn test
```

- 기본 `java`가 JDK 26이면 Mockito가 클래스를 mock 하지 못해 테스트가 실패합니다. 반드시 JDK 17로 실행합니다.
- 테스트 프로필: `src/test/resources/application-test.yml` (H2)

### 프론트엔드

```bash
cd frontend
cp .env.example .env
npm install
npm run dev      # http://localhost:5173
npm run lint
npm run build
```

환경 변수: `VITE_API_URL`, `VITE_APP_NAME`, `VITE_GOOGLE_CLIENT_ID`, `VITE_BYPASS_AUTH`(개발용 인증 우회)

## 8. 아키텍처

### 백엔드 (`backend/src/main/java/com/mynotionai/`)

| 패키지 | 역할 |
|---|---|
| `controller/` | REST API (Auth, Calendar, AI, User) |
| `service/` | 비즈니스 로직 (Auth, Schedule, AI, GoogleTokenVerifier, Reminder) |
| `repository/` | Spring Data JPA |
| `entity/` | User, Schedule, ChatLog, AiAction, ReminderLog |
| `dto/` | 요청/응답 DTO |
| `exception/` | ErrorCode, BusinessException, ApiErrorResponse, GlobalExceptionHandler |
| `config/` | SecurityConfig (CORS, Stateless, `/auth/**`만 공개) |
| `security/` | JwtTokenProvider, JwtAuthenticationFilter |

- 인증: JWT(JJWT 0.12), 만료 24시간. 컨트롤러에서 `(Long) authentication.getPrincipal()`로 userId를 얻습니다.
- AI: 현재 정규식 규칙 기반(`AiService`). OpenAI 의존성은 있으나 미사용 → LLM 연동 예정.
- 리마인더: `ReminderScheduler`가 매분 실행, 1시간 전 일정에 `ReminderLog`/`ChatLog` 기록.
- DB: MySQL(`prod`), H2(`local`·`test`). 스키마는 Flyway 마이그레이션(`db/migration`)으로 관리하고 Hibernate는 `validate`만 합니다.

### 프론트엔드 (`frontend/src/`)

`pages/`(Home, Calendar, Login), `layout/`, `api/`(Axios), `store/`(Zustand), React Router 7 `ProtectedRoute`, TanStack Query.

## 9. API 엔드포인트 (context-path: `/api`)

| 분류 | 메서드 & 경로 | 설명 |
|---|---|---|
| 인증 | `POST /auth/register` | 회원가입 |
| | `POST /auth/login` | 이메일 로그인 |
| | `POST /auth/google` | Google 로그인 (ID 토큰) |
| 일정 | `GET /calendar/events?year=&month=` | 월별 조회 |
| | `POST /calendar/events` | 등록 |
| | `PUT /calendar/events/{id}` | 수정 |
| | `DELETE /calendar/events/{id}` | 삭제 |
| AI | `POST /ai/analyze` | 자연어 → 일정 초안 |
| | `POST /ai/apply` | 초안 적용 |
| | `POST /ai/revise` | 초안 수정 |
| | `POST /ai/cancel` | 초안 취소 |
| | `GET /ai/today-summary` | 오늘 일정 요약 |
| | `GET /ai/chat-logs` | AI 대화 로그 |
| 사용자 | `GET /user/profile` | 프로필 조회 |
| | `PUT /user/profile` | 프로필 수정 |

## 10. 고도화 로드맵

| 단계 | 주제 | 상태 |
|---|---|---|
| 1. 기초 체력 | 공통 예외 처리 → 프로필 분리 + Flyway → Controller 테스트 → Swagger | 공통 예외 처리 완료, 프로필 분리 + Flyway 진행중 (`feature/profile-flyway`) |
| 2. 도메인 & 데이터 | 일정 겹침 검사, 반복 일정, Reminder N+1 해결, 페이징 | 대기 |
| 3. 외부 연동 | AiService 전략 패턴 분리, LLM 연동 + 타임아웃/재시도/폴백 | 대기 |
| 4. 인증 & 성능 | Refresh Token, Redis 캐싱, 리마인더 중복 방지(ShedLock) | 대기 |
| 5. 운영 | Docker Compose, GitHub Actions CI, Actuator | 대기 |

각 단계 완료 시 **"문제 → 원인 → 해결 → 결과 수치"**를 README 또는 docs에 기록합니다.

## 11. 알려진 제한사항

- Google 로그인이 `window.prompt`로 ID 토큰을 받음 → OAuth 버튼 방식으로 전환 예정
- `JWT_SECRET` 기본값은 `local` 프로필에만 있음 (`prod`는 필수). 운영 배포 시 반드시 설정
- CORS origin이 코드에 하드코딩됨 → 환경 변수화 필요
- 이전 코드 리뷰 결과: `.claude/CODE_REVIEW.md`
