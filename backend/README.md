# MyNotion AI Backend

Spring Boot 기반 백엔드 서버입니다.

## 기술 스택

- **Framework**: Spring Boot 3.2.0
- **Build Tool**: Gradle
- **Database**: MySQL
- **ORM**: JPA/Hibernate
- **Security**: Spring Security + JWT
- **AI**: OpenAI GPT API
- **Java Version**: 17+

## 프로젝트 구조

```
backend/
├── src/
│   ├── main/
│   │   ├── java/com/mynotionai/
│   │   │   ├── controller/     # REST API Controllers
│   │   │   ├── service/        # Business Logic
│   │   │   ├── repository/     # Data Access
│   │   │   ├── entity/         # JPA Entities
│   │   │   ├── dto/            # Data Transfer Objects
│   │   │   ├── config/         # Spring Configurations
│   │   │   ├── security/       # Security Configurations
│   │   │   ├── exception/      # Custom Exceptions
│   │   │   └── util/           # Utility Classes
│   │   └── resources/
│   │       ├── application.yml
│   │       └── application-dev.yml
│   └── test/
└── build.gradle
```

## 환경 설정

### required 환경변수

```bash
export OPENAI_API_KEY=your-api-key
export JWT_SECRET=your-secret-key
export DB_PASSWORD=your-db-password
```

또는 `.env` 파일 생성:

```
OPENAI_API_KEY=your-api-key
JWT_SECRET=your-secret-key-minimum-32-chars
DB_PASSWORD=your-password
```

## 실행 방법

### Gradle 설치 (Windows에서)

```powershell
choco install gradle
```

### 프로젝트 빌드

```bash
gradle build
```

### 개발 서버 실행

```bash
gradle bootRun
```

또는 H2 인메모리 DB 사용:

```bash
gradle bootRun --args='--spring.profiles.active=dev'
```

### JAR 파일 생성 및 실행

```bash
gradle build
java -jar build/libs/mynotion-backend-0.0.1-SNAPSHOT.jar
```

## API 서버

- **Base URL**: `http://localhost:8080/api`
- **Health Check**: `GET http://localhost:8080/api/health`

## 주요 모듈

### 1. 인증 (Authentication)

- JWT 기반 토큰 인증
- 사용자 회원가입 및 로그인

### 2. 캘린더 (Calendar)

- 일정 생성, 수정, 삭제
- 일정 조회

### 3. AI 기능

- OpenAI GPT API 연동
- 콘텐츠 생성 및 분석

## 개발 가이드

### 새로운 엔티티 추가

1. `src/main/java/com/mynotionai/entity/` 에서 엔티티 클래스 생성
2. `src/main/java/com/mynotionai/repository/` 에서 Repository 인터페이스 생성
3. `src/main/java/com/mynotionai/service/` 에서 Service 클래스 생성
4. `src/main/java/com/mynotionai/controller/` 에서 Controller 클래스 생성

### 새로운 API 엔드포인트 추가

```java
@PostMapping("/your-endpoint")
public ResponseEntity<?> yourMethod(@RequestBody YourRequest request) {
    // your logic
    return ResponseEntity.ok(new ApiResponse<>(data, "Success"));
}
```

## 테스트

```bash
gradle test
```

## 배포

```bash
gradle build
# build/libs/mynotion-backend-0.0.1-SNAPSHOT.jar 파일 배포
```

## 문제 해결

### Gradle 캐시 문제

```bash
gradle clean build
```

### 의존성 문제

```bash
gradle dependencies
```

## 참고

- [Spring Boot Documentation](https://spring.io/projects/spring-boot)
- [OpenAI API Documentation](https://platform.openai.com/docs)
