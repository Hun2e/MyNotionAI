import { useEffect, useState } from "react";
import { NavLink, Outlet } from "react-router-dom";
import { aiApi, calendarApi, userApi } from "../api";
import { useAuthStore, useCalendarStore } from "../store";
import "./AppLayout.css";

const bypassAuth = import.meta.env.VITE_BYPASS_AUTH === "true";
const suggestedPrompts = [
  "내일 오후 3시에 팀 미팅 추가해줘",
  "오늘 일정 요약해줘",
  "금요일 오전 일정 정리해줘",
];
const demoUser = {
  email: "demo@mynotion.ai",
  nickname: "데모 사용자",
};
const demoEvents = [
  {
    id: "demo-1",
    title: "프로젝트 홈 화면 리뷰",
    memo: "UI 흐름과 와이어프레임 일치 여부 확인",
    startAt: new Date(new Date().setHours(10, 0, 0, 0)).toISOString(),
    endAt: new Date(new Date().setHours(11, 0, 0, 0)).toISOString(),
  },
  {
    id: "demo-2",
    title: "캘린더 인터랙션 점검",
    memo: "월간 보드와 우측 AI 패널 연결 확인",
    startAt: new Date(new Date().setHours(14, 0, 0, 0)).toISOString(),
    endAt: new Date(new Date().setHours(15, 0, 0, 0)).toISOString(),
  },
  {
    id: "demo-3",
    title: "포트폴리오 README 보강",
    memo: "스크린샷, 구조도, 개선 계획 섹션 점검",
    startAt: new Date(new Date().setDate(new Date().getDate() + 2)).toISOString(),
    endAt: new Date(new Date().setDate(new Date().getDate() + 2)).toISOString(),
  },
];

function formatDraftDate(value) {
  if (!value) {
    return "시간 미정";
  }

  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }

  return new Intl.DateTimeFormat("ko-KR", {
    month: "numeric",
    day: "numeric",
    hour: "numeric",
    minute: "2-digit",
  }).format(date);
}

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
            content: "AI 대화 기록을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.",
          },
        ]);
      }
    };

    loadLogs();
  }, []);

  const sendMessage = async (presetValue) => {
    const content = (presetValue ?? input).trim();
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
          content:
            err?.response?.data?.message || "AI 분석 요청을 처리하지 못했습니다.",
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
          content: err?.response?.data?.message || "일정 적용에 실패했습니다.",
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
          content: err?.response?.data?.message || "일정 초안 취소에 실패했습니다.",
        },
      ]);
    }
  };

  return (
    <aside className="ai-panel">
      <div className="ai-head">
        <div>
          <p className="panel-kicker">AI Panel</p>
          <h3>자연어 일정 보조</h3>
        </div>
        <span className="ai-badge">MVP</span>
      </div>

      <p className="ai-subtitle">
        자연어로 일정을 입력하면 초안을 만들고, 적용 또는 취소를 이어서 처리할
        수 있습니다.
      </p>

      <div className="suggested-prompts">
        {suggestedPrompts.map((prompt) => (
          <button
            key={prompt}
            type="button"
            onClick={() => {
              setInput(prompt);
              sendMessage(prompt);
            }}
          >
            {prompt}
          </button>
        ))}
      </div>

      <div className="ai-log">
        {messages.length === 0 ? (
          <div className="ai-msg assistant">
            아래 입력창에 일정을 자연어로 입력해 보세요.
          </div>
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
          <p className="panel-kicker">Draft</p>
          <strong>{draft.title}</strong>
          <p>
            {formatDraftDate(draft.startAt)} - {formatDraftDate(draft.endAt)}
          </p>
          <div className="draft-actions">
            <button type="button" onClick={applyDraft}>
              일정 적용
            </button>
            <button type="button" onClick={cancelDraft} className="secondary">
              초안 취소
            </button>
          </div>
        </div>
      )}

      <div className="ai-input-wrap">
        <textarea
          value={input}
          onChange={(e) => setInput(e.target.value)}
          placeholder="예: 내일 오후 3시에 디자인 리뷰 미팅 추가해줘"
          rows={4}
        />
        <button type="button" onClick={() => sendMessage()} disabled={isSending}>
          {isSending ? "분석 중..." : "AI에게 보내기"}
        </button>
      </div>
    </aside>
  );
}

export default function AppLayout() {
  const user = useAuthStore((state) => state.user);
  const setUser = useAuthStore((state) => state.setUser);
  const logout = useAuthStore((state) => state.logout);
  const setEvents = useCalendarStore((state) => state.setEvents);
  const events = useCalendarStore((state) => state.events);
  const [isBootstrapping, setIsBootstrapping] = useState(true);

  useEffect(() => {
    const bootstrap = async () => {
      if (bypassAuth) {
        setUser(demoUser);
        setEvents(demoEvents);
        setIsBootstrapping(false);
        return;
      }

      try {
        const today = new Date();
        const [profile, monthEvents] = await Promise.all([
          userApi.getProfile(),
          calendarApi.getEvents(today.getMonth() + 1, today.getFullYear()),
        ]);

        setUser(profile);
        setEvents(Array.isArray(monthEvents) ? monthEvents : []);
      } catch (err) {
        if (err?.response?.status === 401) {
          logout();
        }
      } finally {
        setIsBootstrapping(false);
      }
    };

    bootstrap();
  }, [logout, setEvents, setUser]);

  return (
    <div className="app-shell">
      <nav className="side-nav">
        <div className="brand-block">
          <p className="panel-kicker">MyNotionAI</p>
          <div className="brand">Schedule Workspace</div>
          <p className="brand-copy">로그인, 홈, 캘린더, AI 패널을 한 흐름으로 묶은 일정 관리 UI</p>
        </div>

        <div className="nav-group">
          <NavLink to="/" end className="nav-item">
            홈
          </NavLink>
          <NavLink to="/calendar" className="nav-item">
            캘린더
          </NavLink>
        </div>

        <div className="sidebar-card">
          <p className="panel-kicker">Loaded</p>
          <strong>{events.length}</strong>
          <span>현재 월 기준 불러온 일정 수</span>
        </div>
      </nav>

      <div className="main-wrap">
        <header className="top-bar">
          <div>
            <p className="panel-kicker">Workspace</p>
            <p className="top-title">오늘 일정과 AI 작업 흐름을 한 화면에서 관리</p>
            <p className="top-subtitle">
              {bypassAuth
                ? "미리보기 모드입니다. 백엔드 없이 UI 흐름과 레이아웃만 확인할 수 있습니다."
                : isBootstrapping
                ? "초기 데이터를 불러오는 중입니다."
                : "화면 우측 패널에서 자연어 일정 입력과 초안 적용을 바로 진행할 수 있습니다."}
            </p>
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
