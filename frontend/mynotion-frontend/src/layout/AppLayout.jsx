import { NavLink, Outlet } from "react-router-dom";
import { useAuthStore } from "../store";
import "./AppLayout.css";

function AiPanel() {
  return (
    <aside className="ai-panel">
      <h3>AI Assistant</h3>
      <p className="ai-subtitle">일정 생성/수정 요청을 자연어로 입력하세요.</p>
      <div className="ai-log">
        <div className="ai-msg assistant">
          오늘 일정 요약이 필요하면 "오늘 일정 요약해줘"라고 입력해보세요.
        </div>
      </div>
      <div className="ai-input-wrap">
        <input placeholder="예: 내일 오후 3시에 팀 미팅 추가해줘" />
        <button type="button">전송</button>
      </div>
    </aside>
  );
}

export default function AppLayout() {
  const user = useAuthStore((state) => state.user);
  const logout = useAuthStore((state) => state.logout);

  return (
    <div className="app-shell">
      <nav className="side-nav">
        <div className="brand">MyNotion AI</div>
        <NavLink to="/" end className="nav-item">
          대시보드
        </NavLink>
        <NavLink to="/calendar" className="nav-item">
          캘린더
        </NavLink>
      </nav>

      <div className="main-wrap">
        <header className="top-bar">
          <div>
            <p className="top-title">AI 일정 비서</p>
            <p className="top-subtitle">일정 관리와 AI 제안을 한 화면에서</p>
          </div>
          <div className="top-actions">
            <span>{user?.nickname || user?.email || "사용자"}</span>
            <button type="button" onClick={logout}>
              로그아웃
            </button>
          </div>
        </header>

        <div className="content-grid">
          <main className="page-content">
            <Outlet />
          </main>
          <AiPanel />
        </div>
      </div>
    </div>
  );
}
