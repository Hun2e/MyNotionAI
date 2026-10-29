# 코드 리뷰: 프로필 분리 + Flyway 도입

- 브랜치: `feature/profile-flyway` (HEAD `9a7d1d2`)
- 작업 지시서: `docs/tasks/20261029-profile-flyway.md`
- 리뷰어: Codex / 검증: Claude(PM)
- 결과: 🔴 필수 수정 1건, 🟡 권장 1건

## 🔴 필수 수정

### 1. 새 MySQL DB에서 enum 컬럼 타입 불일치로 앱 시작 실패
- 위치: `backend/src/main/resources/db/migration/V1__init_schema.sql:32`, `:42`, `:45`
- 문제: `chat_logs.role`, `ai_actions.action_type`, `ai_actions.status`를 `VARCHAR(255)`로 만들었다. 그런데 해당 필드는 `@Enumerated(EnumType.STRING)`만 붙어 있어서, Hibernate 6.3의 `MySQLDialect`는 이 컬럼을 MySQL **native `ENUM`** 타입으로 기대한다.
- 이유:
  - 빈 MySQL에 V1을 적용하면 `VARCHAR` 컬럼이 생긴다. 이어서 `ddl-auto: validate`가 `ENUM`을 기대하므로 "wrong column type"으로 앱 시작이 실패한다.
  - 테스트는 `H2Dialect`로 돌기 때문에 이 문제를 잡지 못한다. H2의 `MODE=MySQL`은 SQL 문법만 흉내 낼 뿐, Hibernate의 `MySQLDialect` 동작까지 재현하지 않는다.
  - (Claude 확인) `hibernate-core-6.3.1.Final` 바이트코드에서 `MySQLDialect.getEnumTypeDeclaration`이 있다. `Dialect.equivalentTypes`는 `VARCHAR`(12) 계열끼리만 같은 타입으로 인정하고 `ENUM`(6000)은 포함하지 않는다.
  - 반대로 `User.provider`, `Schedule.createdBy`는 `columnDefinition = "VARCHAR(20) ..."`이 있어 문제가 없다.
- 수정 제안 (둘 중 하나):
  - **A. VARCHAR로 통일 (PM 추천)**: 세 필드에 `@JdbcTypeCode(SqlTypes.VARCHAR)`를 붙인다. V1은 그대로 둔다. 다른 enum 필드(`provider`, `createdBy`)와 저장 방식이 같아지고, 나중에 enum 값을 추가할 때 DB 변경이 필요 없다.
  - **B. native ENUM 사용**: V1의 세 컬럼을 enum 상수 목록을 나열한 `ENUM(...)` 타입으로 바꾼다. 엔티티는 그대로 두지만, enum 값을 추가할 때마다 `ALTER TABLE` 마이그레이션이 필요하다.
- 검증 방법: 테스트로는 잡히지 않으므로 **실제 빈 MySQL**에 `prod` 프로필로 기동해 확인해야 한다.

## 🟡 권장

### 2. 기존 로컬 H2 파일 DB의 전환 절차가 없음
- 위치: `backend/src/main/resources/application-local.yml:3`, `backend/run-backend-h2.cmd:12`
- 문제: 기존 Windows 스크립트가 `ddl-auto: update`로 만든 `./data/mynotion` 파일을 그대로 쓴다. 이 파일에는 테이블은 있지만 Flyway 이력 테이블이 없다. `local`에는 baseline 설정이 없어서 Flyway가 "비어 있지 않은 스키마"로 판단하고 시작을 거부한다.
- 수정 제안: 자동 baseline이나 자동 삭제는 하지 않는다. README에 "기존 로컬 H2 파일은 백업 후 `backend/data/`를 지우고 다시 실행"하는 절차를 적는다. (Claude가 문서 반영 시 처리)

## 🟢 참고 (문제 없음)
- **V1 구조**: enum 타입을 제외하면 5개 엔티티의 컬럼, null 허용, PK, `TEXT`, 기본값이 일치한다. 유니크 제약 3개, 인덱스 6개, FK 6개도 대상 컬럼과 순서가 맞다.
- **날짜 타입**: `LocalDateTime`에 대해 `MySQLDialect`가 기대하는 `DATETIME(6)`과 일치한다.
- **운영 baseline 동작**:
  - 이력 테이블이 없고 테이블이 있는 DB → 버전 1로 baseline하고 V1을 건너뛴다.
  - 빈 DB → baseline하지 않고 V1을 실행한다. 빈 DB가 V1을 건너뛸 위험은 없다.
- **baseline의 한계**: baseline은 "이미 V1이 적용됐다"는 표시만 남길 뿐, 실제 스키마를 V1과 비교하지 않는다. 기존 DB의 인덱스·제약 이름(`idx_user_created`, Hibernate가 자동 생성한 이름)은 그대로 남는다. 나중에 이름으로 `DROP`/`ALTER`할 때 주의해야 한다.
- **설정 이전**: `format_sql`, `use_sql_comments`, JWT 만료 시간 등이 빠짐없이 옮겨졌다. `prod`는 DB 계정과 JWT에 기본값이 없다.
- **.gitignore**: 프로필 파일 3개의 예외 규칙과 `backend/data/` 제외가 정상 동작한다.
- **테스트 격리**: `testdb`는 `DB_CLOSE_DELAY=-1`이라 테스트 컨텍스트 간에 스키마와 Flyway 이력이 남을 수 있다. 그래서 `FlywayMigrationTest`는 "V1 이력이 성공 상태로 있다"를 검증할 뿐, 자기 컨텍스트에서 새로 실행했음을 보장하지는 않는다. 현재 테스트는 모두 롤백되므로 데이터 간섭은 없다.
- **규칙 준수**: 4칸 들여쓰기, 30줄 이하 메서드, 버전 문자열 상수화, 한국어 주석.

## 학습 포인트
1. **H2의 MySQL 모드는 MySQL이 아니다.** SQL 문법은 흉내 내지만 Hibernate Dialect가 기대하는 타입까지 같지는 않다. 그래서 운영 DB와 같은 DB로 하는 테스트(예: Testcontainers)가 필요해진다.
2. **Flyway baseline은 "검사"가 아니라 "표시"다.** 기존 DB를 V1 상태로 인정할 뿐, 정말 같은지는 사람이 `SHOW CREATE TABLE`로 대조해야 한다.
3. **`ddl-auto: validate` 통과 ≠ 스키마 정확.** validate는 테이블·컬럼·타입만 본다. 유니크 제약, FK, 인덱스는 검사하지 않는다.

## 처리 계획 (PM 제안)
| 항목 | 처리 |
|---|---|
| 🔴 1 | 이번 PR에서 수정. A안(`@JdbcTypeCode(SqlTypes.VARCHAR)`) 적용 후 실제 빈 MySQL에서 `prod` 기동으로 검증 |
| 🟡 2 | 이번 PR에서 README에 로컬 H2 전환 절차 기록 (Claude) |
| 실제 MySQL 테스트 자동화 | 5단계(Docker·CI) 때 Testcontainers 도입 검토 |

## 🔴 1 처리 결과 (Claude 검증)
- A안 적용: `0cd2746 enum 컬럼을 VARCHAR로 매핑해 MySQL 스키마 검증 불일치 해결`
- `mvn test` 48개 통과
- 실제 MySQL 8.0.46 (Docker 일회용 컨테이너, 빈 DB)에서 `prod` 프로필 기동:
  | 엔티티 | 결과 |
  |---|---|
  | 수정 전 (`0cd2746~1`) | `wrong column type encountered in column [action_type] in table [ai_actions]`로 기동 실패 → 리뷰 지적 재현 |
  | 수정 후 | `Successfully applied 1 migration ... now at version v1` → 앱 정상 기동 |
- 생성된 스키마: enum 컬럼 3개 `varchar(255)`, `provider`·`created_by` `varchar(20)`, 인덱스 6개 이름이 V1과 일치
- 검증 후 컨테이너와 테스트용 비밀번호는 삭제했다.
