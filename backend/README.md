# MyNotionAI Backend

MyNotionAI의 백엔드 서버입니다. 인증, 일정 관리, 사용자 프로필, AI 기반 일정 보조 기능을 담당합니다.

## 기술 스택

- Java 17
- Spring Boot 3.2.0
- Spring Security
- Spring Data JPA
- Maven
- MySQL, H2

## 주요 API

- `POST /api/auth/register`
- `POST /api/auth/login`
- `POST /api/auth/google`
- `GET /api/calendar/events`
- `POST /api/calendar/events`
- `PUT /api/calendar/events/{id}`
- `DELETE /api/calendar/events/{id}`
- `GET /api/user/profile`
- `PUT /api/user/profile`
- `POST /api/ai/analyze`
- `POST /api/ai/apply`
- `POST /api/ai/revise`
- `POST /api/ai/cancel`
- `GET /api/ai/chat-logs`
- `GET /api/ai/today-summary`

## 실행 방법

### 사전 요구사항

- Java 17 이상
- Maven 3.9 이상

### 환경 변수

```powershell
$env:DB_PASSWORD="your-db-password"
$env:JWT_SECRET="your-jwt-secret"
$env:OPENAI_API_KEY="your-openai-api-key"
$env:GOOGLE_OAUTH_CLIENT_ID="your-google-client-id"
```

### MySQL 기준 실행

```powershell
cd backend
mvn spring-boot:run
```

기본 설정은 `src/main/resources/application.yml`에 있습니다.

### 로컬 H2 기준 실행

```powershell
backend\run-backend-h2.cmd
```

이 스크립트는 `.tools/apache-maven-3.9.9/bin/mvn.cmd`를 사용하며, 실행 로그는 `backend/spring-boot.log`에 기록됩니다.

### 빌드

```powershell
cd backend
mvn clean package
```

생성 산출물:

- `target/mynotion-backend-0.0.1-SNAPSHOT.jar`

### 테스트

```powershell
cd backend
mvn test
```

## 프로젝트 구조

```text
backend
|-- pom.xml
|-- run-backend-h2.cmd
|-- src/main/java/com/mynotionai
|   |-- config
|   |-- controller
|   |-- dto
|   |-- entity
|   |-- repository
|   |-- security
|   `-- service
`-- src/main/resources
    |-- application.yml
    `-- application-dev.yml
```

## 트러블슈팅

### `mvn` 명령을 찾을 수 없는 경우

- Maven이 설치되어 있는지 확인합니다.
- 또는 `backend\run-backend-h2.cmd`를 사용해 실행합니다.

### 데이터베이스 연결 오류가 발생하는 경우

- 기본 datasource는 `jdbc:mysql://localhost:3306/mynotion`입니다.
- 로컬 MySQL 상태와 `DB_PASSWORD` 값을 확인합니다.
- 빠르게 로컬 확인이 필요하면 H2 실행 스크립트를 사용합니다.

### 인증이 필요한 API에서 401이 발생하는 경우

- `/auth/**`를 제외한 API는 JWT 인증이 필요합니다.
- 프론트엔드 요청에 `Authorization: Bearer <token>` 헤더가 포함되는지 확인합니다.

### 프론트엔드에서 CORS 오류가 발생하는 경우

- 현재 허용 origin은 `localhost:5173`, `127.0.0.1:5173`, `localhost:3000`, `127.0.0.1:3000`입니다.
- 다른 포트를 사용한다면 `SecurityConfig`를 수정해야 합니다.
