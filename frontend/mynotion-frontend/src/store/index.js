import { create } from "zustand";

export const useAuthStore = create((set) => ({
  user: null,
  token: localStorage.getItem("accessToken") || null,
  isAuthenticated: !!localStorage.getItem("accessToken"),

  setUser: (user) => set({ user }),
  setToken: (token) => {
    localStorage.setItem("accessToken", token);
    set({ token, isAuthenticated: true });
  },
  logout: () => {
    localStorage.removeItem("accessToken");
    set({ user: null, token: null, isAuthenticated: false });
  },
}));

export const useCalendarStore = create((set) => ({
  events: [],
  selectedDate: new Date(),

  setEvents: (events) => set({ events }),
  addEvent: (event) =>
    set((state) => ({
      events: [...state.events, event],
    })),
  updateEvent: (id, event) =>
    set((state) => ({
      events: state.events.map((e) => (e.id === id ? event : e)),
    })),
  deleteEvent: (id) =>
    set((state) => ({
      events: state.events.filter((e) => e.id !== id),
    })),
  setSelectedDate: (date) => set({ selectedDate: date }),
}));

export const useUIStore = create((set) => ({
  theme: localStorage.getItem("theme") || "light",
  sidebarOpen: true,

  toggleTheme: () =>
    set((state) => {
      const newTheme = state.theme === "light" ? "dark" : "light";
      localStorage.setItem("theme", newTheme);
      return { theme: newTheme };
    }),
  toggleSidebar: () =>
    set((state) => ({
      sidebarOpen: !state.sidebarOpen,
    })),
}));
