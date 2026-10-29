# 코드 리뷰: 공통 예외 처리 (PR #1)

- PR: https://github.com/Hun2e/MyNotionAI/pull/1
- 브랜치: `feature/global-exception` (HEAD `359165f`)
- 리뷰어: Codex / 검증: Claude(PM)
- 결과: 🔴 필수 수정 1건, 🟡 권장 6건

## 🔴 필수 수정

### 1. 내부 예외 메시지가 응답에 그대로 노출됨
- 위치: `backend/src/main/java/com/mynotionai/exception/GlobalExceptionHandler.java:66`
- 문제: `ErrorResponse`가 아닌 예외는 `ex.getMessage()`를 그대로 반환하고, 부모 핸들러가 만든 안전한 `body`를 버린다. `ErrorResponse` 분기도 5xx를 걸러내지 않는다.
- 이유:
  - 날짜 형식이 잘못된 JSON을 보내면 `HttpMessageNotReadableException`의 Jackson 파싱 메시지(Java 타입, 변환 실패 상세)가 클라이언트에 노출된다.
  - `GoogleTokenVerifierService.java:22`의 `"GOOGLE_OAUTH_CLIENT_ID is not configured"` 같은 서버 설정 정보가 500 응답에 그대로 나간다. (Claude 확인 완료)
- 수정 제안: 5xx는 일반 메시지로 통일하고 상세는 로그로 남긴다. 4xx는 허용된 안내 메시지나 부모가 만든 `ProblemDetail`의 안전한 설명을 쓴다. 메시지 비노출 테스트를 추가한다.

## 🟡 권장

### 2. 비표준 상태 코드에서 예외 처리 자체가 실패함
- 위치: `GlobalExceptionHandler.java:65`
- 문제: `HttpStatus.valueOf()`는 enum에 없는 코드(예: 599)에서 `IllegalArgumentException`을 던진다.
- 수정 제안: `HttpStatus.resolve()`를 쓰고, 값이 없으면 `HTTP_599` 같은 대체 코드를 쓴다. 현재 서비스에는 비표준 코드가 없어 조건부 위험이다.

### 3. MVC 내부에서 발생한 보안 예외가 500으로 바뀜
- 위치: `GlobalExceptionHandler.java:35`
- 문제: 컨트롤러·서비스에서 `AccessDeniedException`, `AuthenticationException`이 발생하면 catch-all이 `INTERNAL_ERROR`로 처리한다.
- 현재 영향: 직접 보안 예외를 던지는 코드는 없다. 필터 단계에서 거절된 요청은 이 Advice를 거치지 않는다.
- 수정 제안: 보안 예외를 다시 던져 Security 경로로 넘긴다. 필터 단계 응답 통일은 `AuthenticationEntryPoint`와 `AccessDeniedHandler`로 별도 구현한다.

### 4. 공통 핸들러 주요 분기의 회귀 테스트 없음
- 위치: `backend/src/test/java/com/mynotionai/controller/CalendarControllerTest.java:94`
- 수정 제안: 일반 예외의 안전한 500, `ResponseStatusException` 상태 유지, 없는 경로 404, 메서드 불일치 405와 `Allow` 헤더, 잘못된 JSON의 `message` 비노출을 검증한다.

### 5. 비밀번호 해시 저장 테스트가 실제 저장값을 확인하지 않음
- 위치: `backend/src/test/java/com/mynotionai/service/AuthServiceTest.java:99`
- 문제: 인코더 호출만 확인하므로, 평문을 저장하는 잘못된 구현도 통과할 수 있다.
- 수정 제안: `ArgumentCaptor<User>`로 `save()` 인자를 잡아 `passwordHash`가 `HASHED_PASSWORD`인지 검증한다.

### 6. 월별 조회 경계에 대한 테스트 설명이 서로 다름 (기존 버그)
- 위치: `ScheduleServiceTest.java:230`, `ScheduleRepositoryTest.java:112`
- 문제: 서비스 테스트는 "다음 달 1일 0시 직전까지"라고 설명하지만, 저장소 테스트는 `Between`이 양끝을 포함해 **다음 달 1일 0시 일정도 이번 달에 포함된다**는 것을 정답으로 검증한다.
- 수정 제안: 이번 PR에서는 설명만 맞추고, 상한을 제외하는 쿼리 수정(`startAt >= from AND startAt < to`)은 별도 `fix/` 작업으로 진행한다. 이 동작은 이번 PR 전부터 있었다.

### 7. 문서의 "모든 에러 응답" 표현이 실제 범위보다 넓음
- 위치: `AGENTS.md:87`
- 수정 제안: "Spring MVC 예외 처리 경로의 응답"으로 범위를 명시하고, Security 필터 단계 응답 통일은 별도 작업으로 구분한다.

## 🟢 참고 (문제 없음)
- 핸들러 중복 매핑이 없다. 더 구체적인 타입이 catch-all보다 먼저 처리되고, `NoResourceFoundException`은 404로 처리된다.
- `AuthService`와 `ScheduleService`의 예외 교체는 기존 상태 코드, 메시지, 소유권 조회 조건을 그대로 유지한다.
- `BusinessException`은 `RuntimeException`이므로 트랜잭션 롤백 조건이 바뀌지 않는다.
- `application-test.yml`에는 테스트용 가짜 값만 있다. 운영 시크릿은 없다.
- `.claude/CLAUDE.md`의 `@../AGENTS.md` 참조가 올바르다.

## 학습 포인트
1. **예외 처리에는 계층 경계가 있다.** MVC Advice와 Security 필터는 담당 범위가 달라서, 응답 포맷을 통일하려면 각각 처리해야 한다.
2. **클라이언트 메시지와 진단 로그는 목적이 다르다.** 응답에는 사용자가 해결에 필요한 안내만, 로그에는 원인 분석용 상세를 남긴다.
3. **테스트는 "호출했는가"보다 "결과가 맞는가"를 검증해야 한다.** 인코더를 호출한 것과 해시를 저장한 것은 다르고, mock 조회와 실제 SQL 경계 검증도 다르다.

## 처리 계획 (PM 제안)
| 항목 | 처리 |
|---|---|
| 🔴 1, 🟡 2, 4, 7 | 이번 PR에서 수정 |
| 🟡 5 | 이번 PR에서 수정 (테스트 한 곳만 바뀌는 작은 수정) |
| 🟡 6 | 이번 PR은 설명만 정정, 쿼리 수정은 `fix/monthly-query-boundary`로 분리 |
| 🟡 3 | 4단계(인증) 작업에서 Security 오류 응답과 함께 처리 |
