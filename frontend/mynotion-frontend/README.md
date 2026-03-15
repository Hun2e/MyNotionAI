# MyNotionAI Frontend

MyNotionAI의 웹 프론트엔드입니다. 로그인, 회원가입, 캘린더 화면, 인증 기반 라우팅, 백엔드 API 연동을 담당합니다.

## 기술 스택

- React 19
- Vite 7
- React Router 7
- Axios
- TanStack Query
- Zustand
- ESLint

## 실행 방법

### 사전 요구사항

- Node.js 20 이상
- npm

### 초기 설정

```powershell
cd frontend\mynotion-frontend
Copy-Item .env.example .env
npm install
```

### 환경 변수

```env
VITE_API_URL=http://localhost:8080/api
VITE_APP_NAME=MyNotion AI
VITE_GOOGLE_CLIENT_ID=
```

### 개발 서버 실행

```powershell
npm run dev -- --host 127.0.0.1 --port 5173
```

접속 주소:

- `http://127.0.0.1:5173`

## 사용 가능한 스크립트

- `npm run dev`: Vite 개발 서버 실행
- `npm run build`: 프로덕션 빌드 생성
- `npm run preview`: 빌드 결과 미리보기
- `npm run lint`: ESLint 실행

## 주요 화면

- 홈
- 로그인 / 회원가입
- 캘린더
- 인증 기반 공통 레이아웃

## API 연동 방식

- 기본 API 주소는 `VITE_API_URL`에서 가져옵니다.
- Axios 요청 인터셉터에서 `localStorage`의 `accessToken`을 `Authorization` 헤더에 추가합니다.
- `401` 응답이 오면 저장된 토큰을 제거합니다.

## 트러블슈팅

### 프론트엔드에서 백엔드에 연결되지 않는 경우

- 백엔드가 `http://localhost:8080`에서 실행 중인지 확인합니다.
- `.env`의 `VITE_API_URL` 값이 `http://localhost:8080/api`인지 확인합니다.

### Google 로그인이 동작하지 않는 경우

- `VITE_GOOGLE_CLIENT_ID` 값을 설정합니다.
- 백엔드의 `GOOGLE_OAUTH_CLIENT_ID`와 동일한 클라이언트를 사용해야 합니다.

### 로그인 이후 인증 요청이 실패하는 경우

- `localStorage`에 `accessToken`이 저장되었는지 확인합니다.
- 로그인 응답에서 정상적으로 토큰이 반환되었는지 확인합니다.
