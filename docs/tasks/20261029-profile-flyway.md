# 프로필 분리 + Flyway 도입
- 브랜치: feature/profile-flyway
- 상태: 진행중

## 배경 (왜 하는가)
1. **설정이 한 파일에 섞여 있다.** `application.yml` 하나에 MySQL 접속 정보, `show-sql: true`, 개발용 JWT 기본값이 함께 있다. 그래서 로컬과 운영의 설정을 구분할 수 없고, 운영에서 `JWT_SECRET`을 빠뜨려도 기본값으로 앱이 그냥 켜진다.
2. **스키마를 Hibernate가 바꾼다.** `ddl-auto: update`는 엔티티를 보고 테이블을 몰래 수정하지만 컬럼 삭제나 이름 변경은 하지 못한다. 어떤 변경이 언제 DB에 들어갔는지 기록도 남지 않는다.
3. **Mac에서 DB 없이 실행할 방법이 없다.** H2 실행 스크립트 `run-backend-h2.cmd`는 Windows 전용이다.

→ 프로필(`local`/`prod`/`test`)로 설정을 나누고, 스키마는 **Flyway 버전별 SQL 파일**로 관리한다. Hibernate는 엔티티와 테이블이 맞는지 **검사(`validate`)만** 한다.

## 할 일 (범위)

### 1. 의존성 (`pom.xml`)
- `org.flywaydb:flyway-core`, `org.flywaydb:flyway-mysql`를 추가한다. 버전은 Spring Boot 3.2 BOM이 관리하므로 직접 적지 않는다.

### 2. 프로필 분리 (`src/main/resources/`)
| 파일 | 내용 |
|---|---|
| `application.yml` (공통) | 앱 이름, `server`, `logging`, `openai`, `google`, JWT 만료 시간, `spring.profiles.default: local`, `jpa.hibernate.ddl-auto: validate` |
| `application-local.yml` | H2 파일 DB(`jdbc:h2:file:./data/mynotion;MODE=MySQL;AUTO_SERVER=TRUE`), `show-sql: true`, 개발용 JWT 기본값 유지 |
| `application-prod.yml` | MySQL, `username: ${DB_USERNAME}`, `password: ${DB_PASSWORD}`, `jwt.secret: ${JWT_SECRET}` (**기본값 없음** → 누락 시 시작 실패), `show-sql: false`, Flyway `baseline-on-migrate: true`, `baseline-version: 1` |

- 공통 파일에 비밀값 기본값을 남기지 않는다. 개발용 기본값은 `application-local.yml`에만 둔다.
- `prod`의 `baseline-on-migrate`는 이미 `ddl-auto: update`로 테이블이 만들어진 기존 MySQL DB를 "V1이 적용된 상태"로 인정하기 위한 설정이다. 이유를 yml 주석(한국어)으로 남긴다.
- `./data/` 폴더가 git에 올라가지 않도록 `.gitignore`를 확인하고, 없으면 `backend/data/`를 추가한다.

### 3. 첫 마이그레이션 (`src/main/resources/db/migration/V1__init_schema.sql`)
- 현재 엔티티 5개(`User`, `Schedule`, `ChatLog`, `AiAction`, `ReminderLog`)와 **정확히 일치**하는 `CREATE TABLE`을 작성한다.
  - 테이블명, 컬럼명(snake_case), `NOT NULL`, `TEXT`, `VARCHAR(20) DEFAULT 'USER'/'LOCAL'`, enum은 `VARCHAR`
  - 유니크 제약: `users.email`, `users(provider, provider_user_id)`, `uk_reminder_schedule`
  - 인덱스: `idx_user_start`, `idx_user_created`(chat_logs, ai_actions), `idx_schedule`, `idx_reminder_created`, `idx_reminder_user`
  - 외래 키: 각 `user_id`, `schedule_id`, `target_schedule_id`
- MySQL과 H2(`MODE=MySQL`) **둘 다에서 실행되는 문법**만 쓴다. (`ENGINE=`, `CHARSET=` 같은 MySQL 전용 옵션은 넣지 않는다.)
- 인덱스 이름이 테이블 간에 겹치면 H2에서 실패할 수 있다(`idx_user_created`가 두 테이블에 있음). 실패하면 엔티티 `@Index` 이름을 바꾸지 말고 **지시서 질문/이슈에 남긴다.**

### 4. 테스트 설정
- `application-test.yml`: `ddl-auto: create-drop` → `validate`로 바꾸고 Flyway를 켠다. 이제 테스트가 **V1 SQL로 만든 테이블에 엔티티가 맞는지**를 자동으로 검증한다.
- `ScheduleRepositoryTest`에 `@AutoConfigureTestDatabase(replace = Replace.NONE)`를 붙인다. (`@DataJpaTest`는 기본으로 `application-test.yml`의 DB URL을 무시하고 일반 H2로 바꿔치기하기 때문이다.)
- `FlywayMigrationTest` 1개 추가 (`@DataJpaTest` + test 프로필): Flyway가 V1을 성공 상태로 적용했는지 `Flyway` 빈의 `info()`로 검증한다.

### 5. Windows 스크립트
- `run-backend-h2.cmd`의 긴 실행 인자를 `--spring.profiles.active=local` 하나로 바꾼다.

## 하지 않을 일 (범위 밖)
- 엔티티 구조 변경 (컬럼 추가·삭제, 인덱스 이름 변경)
- CORS origin 환경 변수화, `serverTimezone=UTC`·JVM 시간대 정리 (별도 작업)
- `open-in-view` 등 그 밖의 JPA 튜닝
- `AGENTS.md`, `README.md` 수정 (Claude가 검증 후 반영)
- 커밋, 브랜치 전환

## 완료 조건
- [ ] `cd backend && JAVA_HOME=$(/usr/libexec/java_home -v 17) mvn test` 전체 통과 (기존 47개 + 신규)
- [ ] 테스트가 Flyway로 스키마를 만들고 `ddl-auto: validate`로 엔티티를 검증한다
- [ ] Mac에서 `cd backend && JAVA_HOME=$(/usr/libexec/java_home -v 17) mvn spring-boot:run`만으로(환경 변수 없이) `local` 프로필이 켜지고 `GET /api/calendar/events`가 401을 반환한다 (앱 정상 기동 확인)
- [ ] `prod` 프로필에서 `JWT_SECRET`이 없으면 앱이 시작되지 않는다
- [ ] 공통 `application.yml`에 비밀값 기본값이 없다
- [ ] 수정·추가 파일: `pom.xml`, `application.yml`, `application-local.yml`, `application-prod.yml`, `V1__init_schema.sql`, `application-test.yml`, `ScheduleRepositoryTest.java`, `FlywayMigrationTest.java`, `run-backend-h2.cmd`, `.gitignore`

## 참고 파일
- `backend/src/main/resources/application.yml`
- `backend/src/main/java/com/mynotionai/entity/*.java` (`@Table`, `@Index`, `@UniqueConstraint`, `@Column`)
- `backend/src/test/resources/application-test.yml`
- `backend/run-backend-h2.cmd`

## 질문/이슈 (Codex 작성)
