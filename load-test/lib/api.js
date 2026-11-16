import http from 'k6/http';
import { config } from './config.js';

function params(token, name) {
  const headers = { 'Content-Type': 'application/json' };
  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }
  return { headers, tags: { name } };
}

export function register(email, nickname) {
  return http.post(`${config.baseUrl}/auth/register`, JSON.stringify({
    email, password: config.password, nickname,
  }), params(null, 'setup_register'));
}

export function login(email) {
  return http.post(`${config.baseUrl}/auth/login`, JSON.stringify({
    email, password: config.password,
  }), params(null, 'setup_login'));
}

export function listEvents(token) {
  const query = `year=${config.readYear}&month=${config.readMonth}`;
  return http.get(`${config.baseUrl}/calendar/events?${query}`, params(token, 'events_list'));
}

export function createEvent(token, event, name = 'setup_events_create') {
  return http.post(`${config.baseUrl}/calendar/events`, JSON.stringify(event), params(token, name));
}

export function analyze(token) {
  return http.post(`${config.baseUrl}/ai/analyze`, JSON.stringify({
    content: 'loadtest 대화 로그 시딩',
  }), params(token, 'setup_analyze'));
}

export function listChatLogs(token) {
  return http.get(`${config.baseUrl}/ai/chat-logs?limit=${config.chatLogsLimit}`,
    params(token, 'chat_logs_list'));
}

export function jsonBody(response) {
  try {
    return response.json();
  } catch (_) {
    return null;
  }
}
