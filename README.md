# MyNotionAI Monorepo

백엔드와 프론트엔드가 아래처럼 분리되어 있습니다.

## Project Structure

- `backend/`
  - Spring Boot API 서버
  - 주요 실행: `backend/run-backend-h2.cmd`
  - 주요 설정: `backend/pom.xml`, `backend/src/main/resources/application.yml`
- `frontend/mynotion-frontend/`
  - React + Vite 웹 프론트엔드
  - 주요 실행: `npm run dev`
  - 주요 설정: `frontend/mynotion-frontend/package.json`

## Quick Start

1. Backend
   - `backend/run-backend-h2.cmd`
2. Frontend
   - `cd frontend/mynotion-frontend`
   - `npm run dev -- --host 127.0.0.1 --port 5173`

## URLs

- Frontend: `http://127.0.0.1:5173`
- Backend API: `http://127.0.0.1:8080/api`
