import { useMemo, useState } from "react";
import { useCalendarStore } from "../store";
import "./Calendar.css";

const WEEKDAYS = ["일", "월", "화", "수", "목", "금", "토"];

const formatMonthLabel = (date) =>
  new Intl.DateTimeFormat("ko-KR", {
    year: "numeric",
    month: "long",
  }).format(date);

const formatTime = (value) => {
  if (!value) {
    return "";
  }

  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }

  return new Intl.DateTimeFormat("ko-KR", {
    hour: "numeric",
    minute: "2-digit",
  }).format(date);
};

export default function Calendar() {
  const [currentDate, setCurrentDate] = useState(new Date());
  const events = useCalendarStore((state) => state.events);

  const year = currentDate.getFullYear();
  const month = currentDate.getMonth();
  const firstDay = new Date(year, month, 1).getDay();
  const daysInMonth = new Date(year, month + 1, 0).getDate();
  const todayKey = new Date().toISOString().slice(0, 10);

  const days = useMemo(() => {
    const arr = [];
    for (let i = 0; i < firstDay; i += 1) arr.push(null);
    for (let d = 1; d <= daysInMonth; d += 1) arr.push(d);
    return arr;
  }, [firstDay, daysInMonth]);

  const monthEvents = useMemo(() => {
    return events
      .filter((event) => {
        const raw = event.startAt || event.date;
        if (!raw) return false;
        const date = new Date(raw);
        return date.getFullYear() === year && date.getMonth() === month;
      })
      .sort((a, b) => new Date(a.startAt || a.date) - new Date(b.startAt || b.date));
  }, [events, year, month]);

  const eventsByDay = useMemo(() => {
    return monthEvents.reduce((acc, event) => {
      const raw = event.startAt || event.date;
      const key = String(raw).slice(0, 10);
      acc[key] = acc[key] || [];
      acc[key].push(event);
      return acc;
    }, {});
  }, [monthEvents]);

  return (
    <div className="calendar-page">
      <section className="calendar-hero">
        <div>
          <p className="calendar-kicker">Calendar</p>
          <h1>{formatMonthLabel(currentDate)}</h1>
          <p>
            월간 보드에서 날짜별 이벤트를 확인하고, 오른쪽 AI 패널과 함께 일정
            초안을 빠르게 다룰 수 있습니다.
          </p>
        </div>
        <div className="calendar-actions">
          <button
            type="button"
            onClick={() => setCurrentDate(new Date(year, month - 1, 1))}
          >
            이전 달
          </button>
          <button
            type="button"
            className="secondary"
            onClick={() => setCurrentDate(new Date())}
          >
            오늘
          </button>
          <button
            type="button"
            onClick={() => setCurrentDate(new Date(year, month + 1, 1))}
          >
            다음 달
          </button>
        </div>
      </section>

      <section className="calendar-layout">
        <div className="calendar-board">
          <div className="calendar-grid">
            {WEEKDAYS.map((day) => (
              <div key={day} className="weekday">
                {day}
              </div>
            ))}
            {days.map((day, idx) => {
              if (!day) {
                return <div key={`empty-${idx}`} className="day-cell off" />;
              }

              const key = `${year}-${String(month + 1).padStart(2, "0")}-${String(day).padStart(2, "0")}`;
              const dayEvents = eventsByDay[key] || [];
              const isToday = key === todayKey;

              return (
                <div key={key} className={`day-cell on ${isToday ? "today" : ""}`}>
                  <div className="day-number-row">
                    <strong>{day}</strong>
                    {dayEvents.length > 0 && <span>{dayEvents.length}</span>}
                  </div>

                  <div className="day-events">
                    {dayEvents.slice(0, 3).map((event) => (
                      <div key={event.id} className="day-event-chip">
                        <span>{event.title}</span>
                        <small>{formatTime(event.startAt || event.date)}</small>
                      </div>
                    ))}
                  </div>
                </div>
              );
            })}
          </div>
        </div>

        <aside className="calendar-side-panel">
          <div className="side-panel-head">
            <p className="calendar-kicker">Events</p>
            <h2>이번 달 일정</h2>
          </div>

          {monthEvents.length === 0 ? (
            <p className="empty">이번 달에 등록된 일정이 없습니다.</p>
          ) : (
            <ul className="event-list">
              {monthEvents.map((event) => (
                <li key={event.id}>
                  <div>
                    <strong>{event.title}</strong>
                    <p>{event.memo || "추가 메모 없음"}</p>
                  </div>
                  <span>{formatTime(event.startAt || event.date)}</span>
                </li>
              ))}
            </ul>
          )}
        </aside>
      </section>
    </div>
  );
}
