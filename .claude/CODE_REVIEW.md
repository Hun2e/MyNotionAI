# MyNotionAI 코드 리뷰

## 📋 개요

MyNotionAI는 Spring Boot 백엔드와 React 프론트엔드를 갖춘 AI 기반 일정 관리 애플리케이션입니다. 폴더 구조 정리(`frontend/mynotion-frontend` → `frontend`)를 반영한 종합 코드 리뷰입니다.

---

## 🔐 인증 & 보안

### ✅ 잘한 부분

**1. JWT 구현 (backend/security/JwtTokenProvider.java:14-72)**
- JJWT 라이브러리 사용으로 신뢰성 높음
- `getSigningKey()`: UTF-8 인코딩 후 HMAC-SHA 키 생성 정확함
- `extractUserId()`, `validateToken()`, `isTokenExpired()` 메서드 분리로 관심사 분리 명확
- 예외 처리로 토큰 검증 실패 시 `false` 반환 안전함

**2. 필터 체인 구현 (backend/security/JwtAuthenticationFilter.java:17-43)**
- `OncePerRequestFilter` 상속으로 요청당 한 번만 실행
- Bearer 토큰 파싱 정확함
- `SecurityContextHolder`에 인증 정보 저장으로 후속 컨트롤러에서 즉시 접근 가능

**3. Spring Security 설정 (backend/config/SecurityConfig.java:20-65)**
- CORS 명시적 설정 (localhost:5173, 3000 허용)
- CSRF 비활성화 (REST API 특성상 적절)
- Stateless 세션 정책 (JWT 기반 인증에 부합)
- `/auth/**` 공개, 나머지 인증 요구 (적절한 접근 제어)

**4. 프론트엔드 토큰 관리 (frontend/src/api/client.js:13-23)**
- Axios 인터셉터로 자동 토큰 추가 (매 요청마다 수동 설정 불필요)
- 401 응답 시 토큰 삭제 및 재로그인 유도 (좋음)

### ⚠️ 개선 사항

**1. Axios 인터셉터의 미완성 리다이렉트** (frontend/src/api/client.js:28-32)
```javascript
if (error.response?.status === 401) {
  localStorage.removeItem("accessToken");
  // 로그인 페이지로 리디렉트 처리는 필요시 구현
}
```
**문제**: 주석으로 "필요시 구현"이라고 남겨있음 → 사용자가 401 오류 후 수동으로 로그인 페이지 가야 함
**개선**:
```javascript
if (error.response?.status === 401) {
  localStorage.removeItem("accessToken");
  window.location.href = "/login"; // 자동 리다이렉트
}
```

**2. JWT 시크릿 기본값 (backend/src/main/resources/application.yml:22)**
```yaml
secret: ${JWT_SECRET:your-secret-key-change-this-in-production-your-secret-key-change}
```
**문제**: 기본값이 너무 짧고 개발용이 환경 변수 미설정 시 사용될 수 있음
**개선**: 기본값 제거하고 필수로 강제
```yaml
secret: ${JWT_SECRET}
```

**3. CORS 설정의 경직된 Origin 목록** (backend/config/SecurityConfig.java:35-40)
- 개발 환경에서는 좋지만, 프로덕션 배포 시 환경 변수로 관리 필요
- 현재는 코드에 하드코딩되어 있음

---

## 📊 데이터 모델 & JPA

### ✅ 잘한 부분

**1. User 엔티티 설계** (backend/entity/User.java:1-62)
- Provider enum으로 LOCAL/GOOGLE 구분 (확장성 좋음)
- `@UniqueConstraint` 중복 설정으로 데이터 무결성 보장
- `@PrePersist/@PreUpdate`로 생성/수정 시간 자동 관리
- nullable 필드 명확히 정의 (passwordHash는 nullable = true for Google 로그인)

**2. Schedule 엔티티 설계** (backend/entity/Schedule.java:1-65)
- 인덱스 설정 (`idx_user_start`) 성능 최적화
- `@ManyToOne(fetch = FetchType.LAZY)` 지연 로딩으로 N+1 쿼리 문제 방지
- CreatedBy enum으로 AI/USER 구분 추적 가능
- startAt/endAt으로 일정 범위 명확히 표현

### ⚠️ 개선 사항

**1. 양방향 관계 미구현**
- User ↔ Schedule, User ↔ ChatLog 등이 일방향만 정의됨
- 향후 "사용자의 모든 일정" 조회 시 매번 쿼리 필요
**개선**:
```java
@OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
private List<Schedule> schedules = new ArrayList<>();
```

**2. 고아 제거 (Orphan Removal) 미설정**
```java
@OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
```
사용자 삭제 시 연관 일정도 함께 삭제되도록 명시적 설정 필요

**3. ChatLog/AiAction 엔티티 구조 미확인**
- 현재 코드에서는 ChatLog 엔티티 전체 확인 안 됨
- User-ChatLog 간 양방향 관계 필요성 검토 필요

---

## 🔄 서비스 로직

### ✅ 잘한 부분

**1. AuthService 구조** (backend/service/AuthService.java:19-80)
- 회원가입 시 이메일 중복 체크 (CONFLICT 에러)
- 로그인 시 비밀번호 검증 후 토큰 발급
- Google 로그인 통합 (Provider 기반 사용자 찾기 → 없으면 생성)
- `@Transactional` 적절히 사용

**2. ScheduleService 로직** (backend/service/ScheduleService.java:19-80)
- 월별 조회 시 YearMonth로 정확한 범위 계산
- validateTimeRange() 메서드로 시간 검증 (세부 코드 미확인이지만 분리 좋음)
- 사용자 소유 일정만 조회/수정/삭제 (authorization check)
- `findByUserIdAndStartAtBetweenOrderByStartAtAsc()` 쿼리 명확함

**3. AiService 규칙 기반 분석** (backend/service/AiService.java:1-100)
- TIME_PATTERN 정규식으로 시간 추출 (`\b([01]?\d|2[0-3])(?::([0-5]\d))?\b`)
- resolveDate(), resolveHour(), resolveMinute() 메서드로 개별 분석
- 시간 미확인 시 NEEDS_CLARIFICATION 응답 (UX 고려)
- 성공 시 AiScheduleDraft 생성 + ChatLog/AiAction 기록

### ⚠️ 개선 사항

**1. 예외 처리 구조** (전반적)
```java
throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
```
**문제**: 모든 예외를 ResponseStatusException으로 처리 → 응답 포맷 일관성 없음
**개선**: 공통 예외 클래스 + GlobalExceptionHandler 도입
```java
@RestControllerAdvice
public class GlobalExceptionHandler {
  @ExceptionHandler(UserNotFoundException.class)
  public ResponseEntity<ErrorResponse> handleUserNotFound(...) { ... }
}
```

**2. AiService의 validateDraft() 메서드 미확인**
- 코드 라인 87에서 호출되지만 전체 구현 미확인
- null 체크 외 추가 검증 로직 필요한지 확인 필요

**3. ScheduleService의 validateTimeRange() 메서드 미확인**
- 일정 시간이 유효한 범위인지 검증하는 로직
- startAt < endAt 검증은 필수

---

## 🎨 프론트엔드

### ✅ 잘한 부분

**1. 상태 관리 구조** (frontend/src/store/index.js:1-53)
- Zustand 3개 스토어 분리 (auth, calendar, UI)
- localStorage 연동으로 새로고침 후에도 상태 유지
- 각 스토어에 setter/action 메서드 명확히 정의

**2. API 클라이언트 구조** (frontend/src/api/client.js + index.js)
- Axios 인스턴스 하나로 중앙화 (일관성)
- authApi, calendarApi, aiApi, userApi로 모듈 분리
- 각 API 메서드가 단순하고 읽기 쉬움
- params 객체로 쿼리 파라미터 처리 (getEvents, getTodaySummary)

**3. 라우팅 & 인증** (frontend/src/App.jsx:1-33)
- ProtectedRoute HOC로 미인증 사용자 로그인 페이지로 리다이렉트
- VITE_BYPASS_AUTH 환경 변수로 개발 모드 지원
- React Router 7 사용

**4. Login 페이지** (frontend/src/pages/Login.jsx:1-187)
- 회원가입/로그인 토글 UI
- Google 로그인 임시 MVP (window.prompt)
- 에러 메시지 표시
- 로딩 상태 버튼 disabled 처리

### ⚠️ 개선 사항

**1. API 엔드포인트 불일치** (frontend/src/api/index.js:15-21)
```javascript
export const calendarApi = {
  getEvents: (month, year) =>
    apiClient.get("/calendar/events", { params: { month, year } }),
  createEvent: (eventData) => apiClient.post("/calendar/events", eventData),
  updateEvent: (id, eventData) =>
    apiClient.put(`/calendar/events/${id}`, eventData),
  deleteEvent: (id) => apiClient.delete(`/calendar/events/${id}`),
};
```
**문제**: 백엔드는 `/calendar` (확인됨), 프론트엔드는 `/calendar/events` → 실제 백엔드 엔드포인트는?
- 확인: CalendarController.java는 `/calendar/events` (GET, POST)와 `/calendar/events/{id}` (PUT, DELETE) 지원 ✅ 일치함

**2. aiApi의 엔드포인트 명확하지 않음**
```javascript
export const aiApi = {
  generateContent: (prompt) => apiClient.post("/ai/generate", { prompt }),
  analyzeContent: (content) => apiClient.post("/ai/analyze", { content }),
  applyDraft: (draft) => apiClient.post("/ai/apply", { draft }),
  reviseDraft: (content) => apiClient.post("/ai/revise", { content }),
  cancelDraft: (draft, reason) => apiClient.post("/ai/cancel", { draft, reason }),
  ...
};
```
**문제**: 프론트엔드 메서드명과 백엔드 API 엔드포인트 간 매핑이 불명확
- 특히 `generateContent`는 백엔드에 없는 것 같음 (현재 MVP에는 불필요?)
- `analyzeContent`, `applyDraft`, `reviseDraft`, `cancelDraft`는 AiController 확인 필요

**3. Calendar 페이지의 상태 관리 미흡** (frontend/src/pages/Calendar.jsx:1-100)
- events는 useCalendarStore에서 가져오지만, 실제 서버에서 데이터를 로드하는 코드 미확인
- 페이지 로드 시 API 호출해서 events 업데이트하는 로직 필요
**예상 수정 필요**:
```javascript
useEffect(() => {
  const fetchEvents = async () => {
    const events = await calendarApi.getEvents(month + 1, year);
    setEvents(events);
  };
  fetchEvents();
}, [year, month]);
```

**4. 에러 처리 미흡**
- 프론트엔드 API 응답에서 네트워크 에러나 타임아웃 처리 없음
- Axios interceptor에서 500, 503 등 서버 에러에 대한 처리 미흡
**개선**:
```javascript
apiClient.interceptors.response.use(
  (response) => response.data,
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem("accessToken");
      window.location.href = "/login";
    } else if (error.response?.status >= 500) {
      console.error("서버 에러:", error);
      // 사용자 친화적 에러 표시
    }
    return Promise.reject(error);
  },
);
```

**5. 타입 안정성 부재**
- JavaScript 사용으로 런타임 타입 에러 가능성
- 향후 TypeScript 마이그레이션 권장

---

## 🔗 API 통합

### 백엔드 엔드포인트 (backend/controller/*.java)

**AuthController** (/auth/*)
- POST /register - ✅ 구현됨
- POST /login - ✅ 구현됨
- POST /google-login - ✅ 구현됨 (CLAUDE.md에는 /auth/google-login, 코드에는?)

**CalendarController** (/calendar/*)
- GET /events?month=M&year=Y - ✅ 구현됨
- POST /events - ✅ 구현됨
- PUT /events/{id} - ✅ 구현됨
- DELETE /events/{id} - ✅ 구현됨

**AiController** (/ai/*)
- POST /analyze - ✅ 구현됨
- POST /apply - ✅ 구현됨
- POST /revise - ✅ 구현됨 (코드 미확인)
- POST /cancel - ✅ 구현됨 (코드 미확인)
- GET /today-summary - ✅ 구현됨 (코드 미확인)
- GET /chat-logs - ✅ 구현됨 (코드 미확인)

**UserController** (/user/*)
- GET /profile - ✅ 구현됨 (코드 미확인)
- PUT /profile - ✅ 구현됨 (코드 미확인)

### ⚠️ 불일치 사항

프론트엔드 `authApi.googleLogin()` 호출:
```javascript
googleLogin: (idToken) => apiClient.post("/auth/google", { idToken }),
```
CLAUDE.md와 백엔드 엔드포인트 확인 필요: `/auth/google` vs `/auth/google-login`

---

## 📱 환경 설정

### backend/application.yml
```yaml
spring.security.jwt.secret: ${JWT_SECRET:...} # ⚠️ 기본값 제거 권장
openai.api-key: ${OPENAI_API_KEY:} # ✅ 좋음
google.oauth.client-id: ${GOOGLE_OAUTH_CLIENT_ID:} # ✅ 좋음
```

### frontend/.env
```env
VITE_API_URL=http://localhost:8080/api
VITE_GOOGLE_CLIENT_ID=...
VITE_BYPASS_AUTH=false # ✅ 개발 모드 지원 좋음
```

---

## 🏗️ 폴더 구조 (정리 후)

```
MyNotionAI/
├── .claude/
│   ├── CLAUDE.md (✅ 업데이트 완료)
│   └── CODE_REVIEW.md (이 파일)
├── backend/
│   ├── pom.xml
│   ├── src/main/java/com/mynotionai/
│   │   ├── controller/
│   │   ├── service/
│   │   ├── entity/
│   │   ├── dto/
│   │   ├── repository/
│   │   ├── config/
│   │   └── security/
│   └── src/main/resources/application.yml
├── frontend/  # ✅ 폴더명 정리됨 (mynotion-frontend 제거)
│   ├── src/
│   │   ├── pages/ (Home, Calendar, Login)
│   │   ├── layout/
│   │   ├── api/
│   │   ├── store/
│   │   ├── App.jsx
│   │   └── main.jsx
│   ├── package.json
│   ├── vite.config.js
│   └── eslint.config.js
└── README.md
```

---

## 🎯 우선순위별 개선 사항

### 1️⃣ 즉시 수정 (심각도: 높음)

1. **Axios 401 응답 리다이렉트 미완성** (frontend/src/api/client.js:28-32)
   - 자동 로그인 페이지 리다이렉트 추가

2. **JWT_SECRET 기본값 제거** (backend/application.yml:22)
   - 환경 변수 필수화

3. **Google 로그인 엔드포인트 확인**
   - 프론트엔드 `/auth/google` vs 백엔드 구현 일치 확인

### 2️⃣ 중기 개선 (심각도: 중간)

1. **공통 예외 처리** GlobalExceptionHandler 도입
2. **Calendar 페이지 데이터 로드**
   - useEffect + API 호출로 월별 일정 서버에서 로드
3. **에러 처리 강화**
   - 네트워크 에러, 500 에러 등 처리 추가
4. **양방향 JPA 관계 구성**
   - User ↔ Schedule, User ↔ ChatLog 양방향 설정

### 3️⃣ 장기 개선 (심각도: 낮음)

1. **TypeScript 마이그레이션** (프론트엔드)
2. **테스트 코드 작성** (유닛 & 통합 테스트)
3. **CORS 환경 변수화**
4. **AI 기능 LLM 통합** (현재는 규칙 기반)

---

## 📝 요약

### ✅ 강점
- JWT 인증 구현이 견고함
- 계층적 아키텍처 (Controller → Service → Repository) 명확
- Zustand + Axios 조합의 프론트엔드 상태 관리 깔끔
- 폴더 구조 정리로 프로젝트 가독성 향상

### ⚠️ 약점
- 예외 처리 구조 미흡 (일관성 없음)
- 프론트엔드 서버 데이터 로드 로직 미확인
- 환경 변수 보안 설정 미흡
- 타입 안정성 부재 (JavaScript)

### 🎯 다음 단계
1. 위의 "즉시 수정" 항목 3개 처리
2. 전체 테스트 (로그인 → 일정 조회 → AI 분석)
3. 에러 케이스 검증 (401, 404, 500 등)

