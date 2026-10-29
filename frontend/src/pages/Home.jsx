import { Link } from "react-router-dom";
import { useAuthStore, useCalendarStore } from "../store";
import "./Home.css";

const formatDateTime = (value) => {
  if (!value) {
    return "시간 미정";
  }

  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }

  return new Intl.DateTimeFormat("ko-KR", {
    month: "long",
    day: "numeric",
    hour: "numeric",
    minute: "2-digit",
  }).format(date);
};

export default function Home() {
  const user = useAuthStore((state) => state.user);
  const events = useCalendarStore((state) => state.events);

  const today = new Date().toISOString().slice(0, 10);
  const todayEvents = events.filter((event) => {
    const raw = event.startAt || event.date;
    if (!raw) return false;
    return String(raw).slice(0, 10) === today;
  });

  const upcomingEvents = [...events]
    .filter((event) => event.startAt || event.date)
    .sort((a, b) => new Date(a.startAt || a.date) - new Date(b.startAt || b.date))
    .slice(0, 4);

  return (
    <div className="home-page">
      <section className="home-hero">
        <div>
          <p className="section-kicker">Home</p>
          <h1>
            {user?.nickname || "사용자"}님의 일정 흐름을
            <br />
            오늘 기준으로 바로 정리했습니다.
          </h1>
          <p className="home-summary">
            홈에서는 오늘 일정, 다음 일정, 캘린더 이동, AI 패널 연계를 한 번에
            확인할 수 있습니다.
          </p>
        </div>

        <div className="hero-actions">
          <Link className="primary-link" to="/calendar">
            캘린더 열기
          </Link>
          <span className="status-pill">오늘 일정 {todayEvents.length}건</span>
        </div>
      </section>

      <section className="home-metrics">
        <article className="metric-card">
          <p>전체 일정</p>
          <strong>{events.length}</strong>
          <small>현재 불러온 일정 기준</small>
        </article>
        <article className="metric-card accent">
          <p>오늘 일정</p>
          <strong>{todayEvents.length}</strong>
          <small>오늘 날짜와 일치하는 이벤트</small>
        </article>
        <article className="metric-card">
          <p>다음 액션</p>
          <strong>{todayEvents.length > 0 ? "일정 확인" : "일정 추가"}</strong>
          <small>AI 패널 또는 캘린더에서 바로 진행</small>
        </article>
      </section>

      <section className="home-grid">
        <article className="panel-card">
          <div className="panel-head">
            <div>
              <p className="section-kicker">Today</p>
              <h2>오늘 일정</h2>
            </div>
            <span>{todayEvents.length}건</span>
          </div>

          {todayEvents.length === 0 ? (
            <p className="empty-text">
              오늘 등록된 일정이 없습니다. 캘린더에서 일정을 추가하거나 AI 패널에
              자연어로 요청해 보세요.
            </p>
          ) : (
            <ul className="agenda-list">
              {todayEvents.map((event) => (
                <li key={event.id}>
                  <div>
                    <strong>{event.title}</strong>
                    <p>{event.memo || "추가 메모가 없는 일정입니다."}</p>
                  </div>
                  <span>{formatDateTime(event.startAt || event.date)}</span>
                </li>
              ))}
            </ul>
          )}
        </article>

        <article className="panel-card">
          <div className="panel-head">
            <div>
              <p className="section-kicker">Upcoming</p>
              <h2>다음 일정</h2>
            </div>
          </div>

          {upcomingEvents.length === 0 ? (
            <p className="empty-text">아직 불러온 일정이 없습니다.</p>
          ) : (
            <ul className="upcoming-list">
              {upcomingEvents.map((event) => (
                <li key={event.id}>
                  <strong>{event.title}</strong>
                  <span>{formatDateTime(event.startAt || event.date)}</span>
                </li>
              ))}
            </ul>
          )}
        </article>
      </section>
    </div>
  );
}
