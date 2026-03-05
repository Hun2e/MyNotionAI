import apiClient from "./client";

// Auth API
export const authApi = {
  register: (email, password, name) =>
    apiClient.post("/auth/register", { email, password, name }),
  login: (email, password) =>
    apiClient.post("/auth/login", { email, password }),
  googleLogin: (idToken) => apiClient.post("/auth/google", { idToken }),
  logout: () => apiClient.post("/auth/logout"),
  refreshToken: () => apiClient.post("/auth/refresh"),
};

// Calendar API
export const calendarApi = {
  getEvents: (month, year) =>
    apiClient.get("/calendar/events", { params: { month, year } }),
  createEvent: (eventData) => apiClient.post("/calendar/events", eventData),
  updateEvent: (id, eventData) =>
    apiClient.put(`/calendar/events/${id}`, eventData),
  deleteEvent: (id) => apiClient.delete(`/calendar/events/${id}`),
};

// AI API
export const aiApi = {
  generateContent: (prompt) => apiClient.post("/ai/generate", { prompt }),
  analyzeContent: (content) => apiClient.post("/ai/analyze", { content }),
};

// User API
export const userApi = {
  getProfile: () => apiClient.get("/user/profile"),
  updateProfile: (userData) => apiClient.put("/user/profile", userData),
};
