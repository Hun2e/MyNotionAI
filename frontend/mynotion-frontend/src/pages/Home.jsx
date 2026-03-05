import { Link } from "react-router-dom";
import { useAuthStore, useCalendarStore } from "../store";
import "./Home.css";

export default function Home() {
  const user = useAuthStore((state) => state.user);
  const events = useCalendarStore((state) => state.events);

  const today = new Date().toISOString().slice(0, 10);
  const todayEvents = events.filter((event) => {
    const raw = event.startAt || event.date;
    if (!raw) return false;
    return String(raw).slice(0, 10) === today;
  });

  return (
    <div className="dashboard-page">
      <section className="hero-card">
        <p className="eyebrow">Dashboard</p>
        <h1>{user?.nickname || "사용자"}님, 오늘 일정 확인해볼까요?</h1>
        <p className="hero-summary">
          오늘 일정 {todayEvents.length}개가 잡혀 있습니다. AI 패널에서 일정 생성,
          요약, 리마인드 안내를 요청할 수 있습니다.
        </p>
        <Link className="hero-link" to="/calendar">
          캘린더에서 자세히 보기
        </Link>
      </section>

      <section className="cards-grid">
        <article className="metric-card">
          <p>전체 일정</p>
          <strong>{events.length}</strong>
        </article>
        <article className="metric-card">
          <p>오늘 일정</p>
          <strong>{todayEvents.length}</strong>
        </article>
      </section>

      <section className="today-card">
        <h2>오늘 일정</h2>
        {todayEvents.length === 0 ? (
          <p className="empty-text">오늘 등록된 일정이 없습니다.</p>
        ) : (
          <ul>
            {todayEvents.map((event) => (
              <li key={event.id}>
                <span>{event.title}</span>
                <small>{event.startAt || event.date}</small>
              </li>
            ))}
          </ul>
        )}
      </section>
    </div>
  );
}
