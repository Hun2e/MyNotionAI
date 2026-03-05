import { useMemo, useState } from "react";
import { useCalendarStore } from "../store";
import "./Calendar.css";

const WEEKDAYS = ["일", "월", "화", "수", "목", "금", "토"];

export default function Calendar() {
  const [currentDate, setCurrentDate] = useState(new Date());
  const events = useCalendarStore((state) => state.events);

  const year = currentDate.getFullYear();
  const month = currentDate.getMonth();
  const firstDay = new Date(year, month, 1).getDay();
  const daysInMonth = new Date(year, month + 1, 0).getDate();

  const days = useMemo(() => {
    const arr = [];
    for (let i = 0; i < firstDay; i += 1) arr.push(null);
    for (let d = 1; d <= daysInMonth; d += 1) arr.push(d);
    return arr;
  }, [firstDay, daysInMonth]);

  const monthEvents = useMemo(() => {
    return events.filter((event) => {
      const raw = event.startAt || event.date;
      if (!raw) return false;
      const date = new Date(raw);
      return date.getFullYear() === year && date.getMonth() === month;
    });
  }, [events, year, month]);

  return (
    <div className="calendar-page">
      <section className="calendar-head">
        <button
          type="button"
          onClick={() => setCurrentDate(new Date(year, month - 1, 1))}
        >
          이전
        </button>
        <h1>
          {year}년 {month + 1}월
        </h1>
        <button
          type="button"
          onClick={() => setCurrentDate(new Date(year, month + 1, 1))}
        >
          다음
        </button>
      </section>

      <section className="calendar-grid">
        {WEEKDAYS.map((day) => (
          <div key={day} className="weekday">
            {day}
          </div>
        ))}
        {days.map((day, idx) => (
          <div key={`${day}-${idx}`} className={`day-cell ${day ? "on" : "off"}`}>
            {day || ""}
          </div>
        ))}
      </section>

      <section className="event-list">
        <h2>이번 달 일정</h2>
        {monthEvents.length === 0 ? (
          <p className="empty">등록된 일정이 없습니다.</p>
        ) : (
          <ul>
            {monthEvents.map((event) => (
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
