# PR #1 리뷰 지적사항 반영
- 브랜치: feature/global-exception
- 상태: 완료 (Codex 구현, Claude 검증: mvn test 46개 통과)
- 리뷰 문서: `docs/reviews/20261029-global-exception.md`

## 배경 (왜 하는가)
Codex 리뷰에서 에러 응답이 내부 정보(Jackson 파싱 상세, 서버 설정 메시지)를 노출하는 🔴 필수 수정 1건과, 테스트·문서 관련 🟡 권장 항목이 나왔다. 머지 전에 반영한다.

## 할 일 (범위)
1. **[🔴 1] 메시지 비노출** — `GlobalExceptionHandler.handleExceptionInternal`
   - 5xx: 메시지를 `ErrorCode.INTERNAL_ERROR`의 일반 메시지로 통일하고, 상세는 `log.error`로 남긴다.
   - 4xx: `ex.getMessage()`를 쓰지 않는다. 부모가 넘겨준 `body`가 `ProblemDetail`이면 그 `detail`을, `ErrorResponse`이면 `getBody().getDetail()`을 쓰고, 둘 다 없으면 상태 코드의 reason phrase를 쓴다.
   - `HttpMessageNotReadableException`(잘못된 JSON)은 `ErrorCode.INVALID_INPUT` 메시지로 응답한다.
   - 단, `ResponseStatusException`의 4xx reason(예: AiService의 `"title is required"`)은 사용자 안내 메시지이므로 유지한다.
2. **[🟡 2] 비표준 상태 코드** — `HttpStatus.valueOf()` → `HttpStatus.resolve()`. null이면 code를 `"HTTP_" + 숫자`로 하고, 실제 응답 상태는 전달받은 `statusCode`를 유지한다.
3. **[🟡 4] 핸들러 회귀 테스트** — `CalendarControllerTest`에 추가:
   - 잘못된 날짜 문자열 JSON → 400, `message`에 Java 클래스명/Jackson 상세가 없음
   - 서비스가 `RuntimeException`을 던짐 → 500, `code=INTERNAL_ERROR`, 원본 메시지 비노출
   - 서비스가 5xx `ResponseStatusException`(설정 누락 메시지)을 던짐 → 500, 원본 메시지 비노출
   - 서비스가 4xx `ResponseStatusException`을 던짐 → 상태와 reason 유지
   - 지원하지 않는 메서드(예: `PATCH /calendar/events/1`) → 405 + `Allow` 헤더
4. **[🟡 5] 비밀번호 테스트** — `AuthServiceTest.storesHashedPassword`에서 `ArgumentCaptor<User>`로 `save()` 인자의 `passwordHash`가 `HASHED_PASSWORD`이고 `RAW_PASSWORD`가 아닌지 검증한다.
5. **[🟡 6] 테스트 설명 정정** — `ScheduleServiceTest`의 월별 조회 경계 테스트 `@DisplayName`과 주석을, 실제 검증 범위(mock이 반환한 결과를 그대로 변환)에 맞게 고친다. 쿼리는 수정하지 않는다.
6. **[🟡 7] 문서 범위 명시** — `AGENTS.md`의 "모든 에러 응답은 GlobalExceptionHandler가…" 문장을 "Spring MVC 예외 처리 경로의 에러 응답은…"으로 바꾸고, "Security 필터 단계(401/403) 응답 통일은 미구현, 4단계에서 처리" 한 줄을 추가한다.

## 하지 않을 일 (범위 밖)
- 월별 조회 쿼리 경계 수정 (별도 `fix/monthly-query-boundary`)
- MVC 내부 보안 예외 처리, `AuthenticationEntryPoint`/`AccessDeniedHandler` (4단계)
- `AiService`, `GoogleTokenVerifierService`의 `ResponseStatusException` 교체 (3단계)
- 위 목록에 없는 파일 수정, 커밋, 브랜치 전환

## 완료 조건
- [ ] `cd backend && JAVA_HOME=$(/usr/libexec/java_home -v 17) mvn test` 전체 통과
- [ ] 잘못된 JSON / 500 응답의 `message`에 내부 상세가 없음을 테스트로 증명
- [ ] 메서드 30줄 이하, 매직 넘버는 상수, Java 4칸 들여쓰기, 주석은 한국어
- [ ] 수정 파일: `GlobalExceptionHandler.java`, `CalendarControllerTest.java`, `AuthServiceTest.java`, `ScheduleServiceTest.java`, `AGENTS.md` 외 없음

## 참고 파일
- `backend/src/main/java/com/mynotionai/exception/*`
- `backend/src/main/java/com/mynotionai/service/GoogleTokenVerifierService.java:22` (5xx 메시지 노출 사례)

## 질문/이슈 (Codex 작성)
