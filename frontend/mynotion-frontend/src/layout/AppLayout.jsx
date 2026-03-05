import { useEffect, useState } from "react";
import { NavLink, Outlet } from "react-router-dom";
import { aiApi } from "../api";
import { useAuthStore, useCalendarStore } from "../store";
import "./AppLayout.css";

function AiPanel() {
  const addEvent = useCalendarStore((state) => state.addEvent);
  const [input, setInput] = useState("");
  const [messages, setMessages] = useState([]);
  const [draft, setDraft] = useState(null);
  const [isSending, setIsSending] = useState(false);

  useEffect(() => {
    const loadLogs = async () => {
      try {
        const logs = await aiApi.getChatLogs(20);
        const normalized = [...logs]
          .reverse()
          .map((log) => ({
            role: log.role === "USER" ? "user" : "assistant",
            content: log.content,
          }));
        setMessages(normalized);
      } catch (err) {
        setMessages([
          {
            role: "assistant",
            content: "AI 대화 로그를 불러오지 못했어요. 다시 시도해주세요.",
          },
        ]);
      }
    };
    loadLogs();
  }, []);

  const sendMessage = async () => {
    const content = input.trim();
    if (!content || isSending) return;

    setIsSending(true);
    setMessages((prev) => [...prev, { role: "user", content }]);
    setInput("");

    try {
      const response = await aiApi.analyzeContent(content);
      setMessages((prev) => [
        ...prev,
        { role: "assistant", content: response.message },
      ]);
      setDraft(response.resultType === "PROPOSAL" ? response.draft : null);
    } catch (err) {
      setMessages((prev) => [
        ...prev,
        {
          role: "assistant",
          content: err?.response?.data?.message || "AI 분석 요청에 실패했어요.",
        },
      ]);
    } finally {
      setIsSending(false);
    }
  };

  const applyDraft = async () => {
    if (!draft) return;
    try {
      const response = await aiApi.applyDraft(draft);
      addEvent(response.schedule);
      setMessages((prev) => [
        ...prev,
        { role: "assistant", content: response.message },
      ]);
      setDraft(null);
    } catch (err) {
      setMessages((prev) => [
        ...prev,
        {
          role: "assistant",
          content: err?.response?.data?.message || "적용에 실패했어요.",
        },
      ]);
    }
  };

  const cancelDraft = async () => {
    if (!draft) return;
    try {
      const response = await aiApi.cancelDraft(draft, "Canceled from AI panel");
      setMessages((prev) => [
        ...prev,
        { role: "assistant", content: response.message },
      ]);
      setDraft(null);
    } catch (err) {
      setMessages((prev) => [
        ...prev,
        {
          role: "assistant",
          content: err?.response?.data?.message || "취소에 실패했어요.",
        },
      ]);
    }
  };

  return (
    <aside className="ai-panel">
      <h3>AI 어시스턴트</h3>
      <p className="ai-subtitle">자연어로 일정 요청을 보내보세요.</p>

      <div className="ai-log">
        {messages.length === 0 ? (
          <div className="ai-msg assistant">아래 입력창에 메시지를 입력해 시작하세요.</div>
        ) : (
          messages.map((msg, idx) => (
            <div key={`${msg.role}-${idx}`} className={`ai-msg ${msg.role}`}>
              {msg.content}
            </div>
          ))
        )}
      </div>

      {draft && (
        <div className="draft-card">
          <strong>{draft.title}</strong>
          <p>
            {draft.startAt} - {draft.endAt}
          </p>
          <div className="draft-actions">
            <button type="button" onClick={applyDraft}>
              적용
            </button>
            <button type="button" onClick={cancelDraft} className="secondary">
              취소
            </button>
          </div>
        </div>
      )}

      <div className="ai-input-wrap">
        <input
          value={input}
          onChange={(e) => setInput(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === "Enter") sendMessage();
          }}
          placeholder="예: 내일 오후 3시에 팀 미팅 추가해줘"
        />
        <button type="button" onClick={sendMessage} disabled={isSending}>
          {isSending ? "..." : "전송"}
        </button>
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
