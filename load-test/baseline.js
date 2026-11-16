import { check, sleep } from 'k6';
import { config, LIMITS, scenarioVus } from './lib/config.js';
import {
  register, login, listEvents, createEvent, analyze, listChatLogs, jsonBody,
} from './lib/api.js';

const HTTP_OK = 200;
const HTTP_CONFLICT = 409;
const FIRST_DAY = 1;
const FIRST_VU = 1;
const DATE_FIELD_WIDTH = 2;
const JSON_INDENT = 2;
const SUMMARY_DECIMAL_PLACES = 2;
const EVENT_START_TIME = '09:00:00';
const EVENT_END_TIME = '10:00:00';
const SUMMARY_TREND_STATS = ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'];
const EXPECTED_CHAT_LOGS = Math.min(config.chatLogsLimit,
  config.seedAnalyzePerUser * LIMITS.LOGS_PER_ANALYZE);

function scenario(exec, target) {
  return {
    executor: 'ramping-vus',
    exec,
    startVUs: 0,
    stages: [
      { duration: config.warmup, target },
      { duration: config.hold, target },
      { duration: config.rampdown, target: 0 },
    ],
  };
}

export const options = {
  setupTimeout: config.setupTimeout,
  summaryTrendStats: SUMMARY_TREND_STATS,
  scenarios: {
    read: scenario('read', scenarioVus.read),
    chat_logs: scenario('chatLogs', scenarioVus.chat_logs),
    write: scenario('write', scenarioVus.write),
  },
  thresholds: {
    'http_req_failed{scenario:read}': [`rate<${config.maxFailRate}`],
    'http_req_failed{scenario:chat_logs}': [`rate<${config.maxFailRate}`],
    'http_req_failed{scenario:write}': [`rate<${config.maxFailRate}`],
    'http_req_duration{name:events_list}': [`p(95)<${config.p95ReadMs}`],
    'http_req_duration{name:chat_logs_list}': [`p(95)<${config.p95ChatLogsMs}`],
    'http_req_duration{name:events_create}': [`p(95)<${config.p95WriteMs}`],
    checks: [`rate>${LIMITS.MIN_CHECK_RATE}`],
  },
};

function requireOk(response, operation) {
  if (response.status !== HTTP_OK) {
    throw new Error(`${operation} 실패: HTTP ${response.status}. 대상 스택과 설정을 확인하세요.`);
  }
}

function authenticate(index, runId) {
  const email = `${runId}-${index}@loadtest.local`;
  let response = register(email, `loadtest-${index}`);
  const body = jsonBody(response);
  if (response.status === HTTP_CONFLICT && body && body.code === 'EMAIL_ALREADY_EXISTS') {
    response = login(email);
  }
  requireOk(response, '테스트 사용자 인증');
  const auth = jsonBody(response);
  if (!auth || typeof auth.accessToken !== 'string' || auth.accessToken.trim() === '') {
    throw new Error('인증 응답에 accessToken이 없습니다.');
  }
  return auth.accessToken;
}

function daysInMonth(year, month) {
  // Date.UTC의 month는 0부터 시작하므로 해당 월의 다음 달 0일로 말일을 구한다.
  return new Date(Date.UTC(year, month, 0)).getUTCDate();
}

function eventPayload(year, month, dayIndex, title) {
  const day = dayIndex % daysInMonth(year, month) + FIRST_DAY;
  const monthText = String(month).padStart(DATE_FIELD_WIDTH, '0');
  const dayText = String(day).padStart(DATE_FIELD_WIDTH, '0');
  const date = `${year}-${monthText}-${dayText}`;
  return {
    title,
    startAt: `${date}T${EVENT_START_TIME}`,
    endAt: `${date}T${EVENT_END_TIME}`,
    memo: 'k6 기준선 측정용 일정',
  };
}

function seedEvents(token, userIndex) {
  const days = daysInMonth(config.readYear, config.readMonth);
  for (let index = 0; index < config.seedEventsPerUser; index += 1) {
    const dayIndex = Math.floor(index * days / config.seedEventsPerUser);
    const event = eventPayload(config.readYear, config.readMonth, dayIndex,
      `loadtest-seed-${userIndex}-${index}`);
    const response = createEvent(token, event);
    requireOk(response, '일정 시딩');
    const body = jsonBody(response);
    if (!body || body.id == null) {
      throw new Error('일정 시딩 응답에 id가 없습니다.');
    }
  }
}

function seedChatLogs(token) {
  for (let index = 0; index < config.seedAnalyzePerUser; index += 1) {
    requireOk(analyze(token), '대화 로그 시딩');
  }
}

export function setup() {
  const runId = config.runId === null ? `baseline-${Date.now()}` : config.runId;
  const users = [];
  for (let index = 0; index < config.userCount; index += 1) {
    const token = authenticate(index, runId);
    seedEvents(token, index);
    seedChatLogs(token);
    users.push({ token });
  }
  // 준비 단계는 예외로 중단하고, checks 지표에는 측정 중 검증만 기록한다.
  return { runId, users };
}

function userToken(data) {
  return data.users[(__VU - FIRST_VU) % data.users.length].token;
}

export function read(data) {
  const response = listEvents(userToken(data));
  const body = jsonBody(response);
  check(response, {
    '일정 조회 상태 200': (result) => result.status === HTTP_OK,
    '사전 일정 수 이상 조회': () => Array.isArray(body)
      && body.length >= config.seedEventsPerUser,
  });
  sleep(config.thinkTimeSec);
}

export function chatLogs(data) {
  const response = listChatLogs(userToken(data));
  const body = jsonBody(response);
  check(response, {
    '대화 로그 조회 상태 200': (result) => result.status === HTTP_OK,
    '대화 로그 수 일치': () => Array.isArray(body) && body.length === EXPECTED_CHAT_LOGS,
  });
  sleep(config.thinkTimeSec);
}

export function write(data) {
  const event = eventPayload(config.writeYear, config.writeMonth, __ITER,
    `loadtest-write-${__VU}-${__ITER}`);
  const response = createEvent(userToken(data), event, 'events_create');
  const body = jsonBody(response);
  check(response, {
    '일정 등록 상태 200': (result) => result.status === HTTP_OK,
    '등록 일정 id 존재': () => body !== null && body.id != null,
  });
  sleep(config.thinkTimeSec);
}

function metricSummary(name, metric) {
  const values = Object.entries(metric.values).map(([key, value]) =>
    `${key}=${typeof value === 'number' ? value.toFixed(SUMMARY_DECIMAL_PLACES) : value}`);
  const thresholds = Object.entries(metric.thresholds || {}).map(([rule, result]) =>
    `${rule}: ${result.ok ? '통과' : '실패'}`);
  return `${name}: ${values.join(', ')}${thresholds.length ? ` [${thresholds.join(', ')}]` : ''}`;
}

function summaryData(data) {
  const setupRunId = data.setup_data && data.setup_data.runId;
  return {
    metrics: data.metrics,
    root_group: data.root_group,
    options: data.options,
    state: data.state,
    runId: setupRunId || config.runId || `setup-failed-${Date.now()}`,
    setupCompleted: Boolean(setupRunId),
  };
}

export function handleSummary(data) {
  const summary = summaryData(data);
  const path = `results/${summary.runId}-summary.json`;
  const lines = Object.entries(summary.metrics).sort(([left], [right]) => left.localeCompare(right))
    .map(([name, metric]) => metricSummary(name, metric));
  return {
    [path]: JSON.stringify(summary, null, JSON_INDENT),
    stdout: [
      summary.setupCompleted
        ? 'k6 기준선 요약: 전체 실행 집계이며 유지 구간 전용 수치가 아닙니다.'
        : 'setup이 완료되지 않아 측정 결과가 아니다.',
      `목표 VU: read=${scenarioVus.read}, chat_logs=${scenarioVus.chat_logs}, write=${scenarioVus.write}`,
      ...lines,
      `결과: ${path}`,
      '',
    ].join('\n'),
  };
}
