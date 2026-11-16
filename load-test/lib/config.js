export const DEFAULTS = Object.freeze({
  USER_COUNT: 10,
  SEED_EVENTS_PER_USER: 30,
  SEED_ANALYZE_PER_USER: 100,
  CHAT_LOGS_LIMIT: 30,
  READ_YEAR: 2030,
  READ_MONTH: 1,
  WRITE_YEAR: 2030,
  WRITE_MONTH: 2,
  TARGET_VUS: 20,
  WRITE_RATIO: 0.2,
  CHAT_LOGS_RATIO: 0.2,
  WARMUP: '1m',
  HOLD: '5m',
  RAMPDOWN: '1m',
  THINK_TIME_SEC: 1,
  P95_READ_MS: 300,
  P95_CHAT_LOGS_MS: 300,
  P95_WRITE_MS: 500,
  MAX_FAIL_RATE: 0.01,
  SETUP_TIMEOUT: '5m',
});

export const LIMITS = Object.freeze({
  MIN_PASSWORD_LENGTH: 8,
  MAX_RUN_ID_LENGTH: 40,
  MIN_YEAR: 1000,
  MAX_YEAR: 9998,
  MONTHS_PER_YEAR: 12,
  MAX_CHAT_LOGS_LIMIT: 100,
  MIN_SCENARIO_VUS: 1,
  LOGS_PER_ANALYZE: 2,
  MIN_CHECK_RATE: 0.99,
});

function required(name) {
  const value = __ENV[name];
  if (typeof value !== 'string' || value.trim() === '') {
    throw new Error(`${name} 환경 변수가 필요합니다.`);
  }
  return value;
}

function numberValue(name, minimum, maximum = Infinity, integer = false) {
  const raw = __ENV[name] === undefined ? DEFAULTS[name] : __ENV[name];
  const value = Number(raw);
  if (String(raw).trim() === '' || !Number.isFinite(value)
    || value < minimum || value > maximum || (integer && !Number.isSafeInteger(value))) {
    throw new Error(`${name}의 숫자 범위 또는 정수 여부를 확인하세요.`);
  }
  return value;
}

function duration(name) {
  const value = __ENV[name] === undefined ? DEFAULTS[name] : __ENV[name];
  const format = /^(?:\d+(?:\.\d+)?(?:ns|us|µs|ms|s|m|h))+$/;
  if (!format.test(value) || !/[1-9]/.test(value)) {
    throw new Error(`${name}은 0보다 큰 기간이어야 합니다. 예: 30s, 1m`);
  }
  return value;
}

function baseUrl() {
  const value = required('BASE_URL').replace(/\/+$/, '');
  if (!/^https?:\/\/[^\s/?#]+(?:\/[^\s?#]*)?$/.test(value) || !value.endsWith('/api')) {
    throw new Error('BASE_URL은 /api로 끝나는 HTTP(S) API 주소여야 합니다.');
  }
  return value;
}

function password() {
  const value = required('LOAD_TEST_PASSWORD');
  if (value.length < LIMITS.MIN_PASSWORD_LENGTH) {
    throw new Error(`LOAD_TEST_PASSWORD는 ${LIMITS.MIN_PASSWORD_LENGTH}자 이상이어야 합니다.`);
  }
  return value;
}

function runId() {
  const value = __ENV.RUN_ID === undefined ? `baseline-${Date.now()}` : __ENV.RUN_ID;
  if (!/^[A-Za-z0-9_-]+$/.test(value) || value.length > LIMITS.MAX_RUN_ID_LENGTH) {
    throw new Error(`RUN_ID는 영문·숫자·밑줄·하이픈으로 ${LIMITS.MAX_RUN_ID_LENGTH}자 이내여야 합니다.`);
  }
  return value;
}

function dataConfig() {
  return {
    userCount: numberValue('USER_COUNT', 1, Infinity, true),
    seedEventsPerUser: numberValue('SEED_EVENTS_PER_USER', 0, Infinity, true),
    seedAnalyzePerUser: numberValue('SEED_ANALYZE_PER_USER', 0, Infinity, true),
    chatLogsLimit: numberValue('CHAT_LOGS_LIMIT', 1, LIMITS.MAX_CHAT_LOGS_LIMIT, true),
    readYear: numberValue('READ_YEAR', LIMITS.MIN_YEAR, LIMITS.MAX_YEAR, true),
    readMonth: numberValue('READ_MONTH', 1, LIMITS.MONTHS_PER_YEAR, true),
    writeYear: numberValue('WRITE_YEAR', LIMITS.MIN_YEAR, LIMITS.MAX_YEAR, true),
    writeMonth: numberValue('WRITE_MONTH', 1, LIMITS.MONTHS_PER_YEAR, true),
  };
}

function loadConfig() {
  return {
    targetVus: numberValue('TARGET_VUS', 1, Infinity, true),
    writeRatio: numberValue('WRITE_RATIO', 0, 1),
    chatLogsRatio: numberValue('CHAT_LOGS_RATIO', 0, 1),
    warmup: duration('WARMUP'),
    hold: duration('HOLD'),
    rampdown: duration('RAMPDOWN'),
    thinkTimeSec: numberValue('THINK_TIME_SEC', 0),
    setupTimeout: duration('SETUP_TIMEOUT'),
    p95ReadMs: numberValue('P95_READ_MS', Number.MIN_VALUE),
    p95ChatLogsMs: numberValue('P95_CHAT_LOGS_MS', Number.MIN_VALUE),
    p95WriteMs: numberValue('P95_WRITE_MS', Number.MIN_VALUE),
    maxFailRate: numberValue('MAX_FAIL_RATE', Number.MIN_VALUE, 1),
  };
}

function readConfig() {
  const value = {
    baseUrl: baseUrl(),
    password: password(),
    runId: runId(),
    ...dataConfig(),
    ...loadConfig(),
  };
  if (value.writeRatio + value.chatLogsRatio >= 1) {
    throw new Error('WRITE_RATIO + CHAT_LOGS_RATIO의 합은 1보다 작아야 합니다.');
  }
  if (value.readYear === value.writeYear && value.readMonth === value.writeMonth) {
    throw new Error('조회 월과 쓰기 월은 서로 달라야 합니다.');
  }
  return Object.freeze(value);
}

export const config = readConfig();

export const scenarioVus = Object.freeze({
  read: Math.max(LIMITS.MIN_SCENARIO_VUS,
    Math.round(config.targetVus * (1 - config.writeRatio - config.chatLogsRatio))),
  chat_logs: Math.max(LIMITS.MIN_SCENARIO_VUS, Math.round(config.targetVus * config.chatLogsRatio)),
  write: Math.max(LIMITS.MIN_SCENARIO_VUS, Math.round(config.targetVus * config.writeRatio)),
});
