import { useState } from "react";
import { authApi } from "../api";
import { useAuthStore } from "../store";
import "./Login.css";

const bypassAuth = import.meta.env.VITE_BYPASS_AUTH === "true";
const highlights = [
  "로그인 후 홈에서 오늘 일정과 진행 상태를 한 번에 확인",
  "캘린더에서 월별 일정을 빠르게 탐색하고 관리",
  "AI 패널에서 자연어로 일정 초안 생성 및 수정",
];

export default function Login() {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [name, setName] = useState("");
  const [isSignUp, setIsSignUp] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState("");

  const setToken = useAuthStore((state) => state.setToken);
  const setUser = useAuthStore((state) => state.setUser);

  const applyAuthResponse = (response) => {
    setToken(response.accessToken);
    setUser(response.user);
    window.location.href = "/";
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError("");
    setIsLoading(true);

    try {
      const response = isSignUp
        ? await authApi.register(email, password, name)
        : await authApi.login(email, password);
      applyAuthResponse(response);
    } catch (err) {
      setError(err.response?.data?.message || "인증 처리에 실패했습니다.");
    } finally {
      setIsLoading(false);
    }
  };

  const handleGoogleLogin = async () => {
    setError("");
    const idToken = window.prompt(
      "현재는 임시 MVP 흐름입니다. Google ID 토큰을 붙여 넣어 주세요."
    );

    if (!idToken) {
      return;
    }

    setIsLoading(true);
    try {
      const response = await authApi.googleLogin(idToken.trim());
      applyAuthResponse(response);
    } catch (err) {
      setError(err.response?.data?.message || "Google 로그인에 실패했습니다.");
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="login-shell">
      <section className="login-hero">
        <p className="login-kicker">AI Schedule Workspace</p>
        <h1>로그인에서 캘린더와 AI 패널까지 한 흐름으로 연결합니다.</h1>
        <p className="login-description">
          MyNotionAI는 인증, 일정 관리, AI 보조 기능을 하나의 화면 경험으로
          묶은 일정 관리 프로젝트입니다.
        </p>

        <div className="login-highlight-list">
          {highlights.map((item) => (
            <article key={item} className="login-highlight-card">
              <span className="login-highlight-dot" />
              <p>{item}</p>
            </article>
          ))}
        </div>
      </section>

      <section className="login-panel">
        <div className="login-card">
          <div className="login-card-head">
            <p className="login-kicker">{isSignUp ? "Create Account" : "Welcome Back"}</p>
            <h2>{isSignUp ? "새 계정을 만들어 시작하세요" : "계정에 로그인하세요"}</h2>
            <p>
              {isSignUp
                ? "이메일 계정을 등록하고 일정 관리 워크스페이스를 시작합니다."
                : "저장된 일정과 AI 기록을 이어서 확인할 수 있습니다."}
            </p>
          </div>

          <form onSubmit={handleSubmit}>
            {isSignUp && (
              <label className="field">
                <span>닉네임</span>
                <input
                  type="text"
                  placeholder="사용할 이름을 입력하세요"
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  required
                />
              </label>
            )}

            <label className="field">
              <span>이메일</span>
              <input
                type="email"
                placeholder="name@example.com"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                required
              />
            </label>

            <label className="field">
              <span>비밀번호</span>
              <input
                type="password"
                placeholder="비밀번호를 입력하세요"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                required
              />
            </label>

            {error && <p className="error">{error}</p>}

            <button type="submit" className="primary-btn" disabled={isLoading}>
              {isLoading
                ? "처리 중..."
                : isSignUp
                  ? "회원가입 후 시작하기"
                  : "로그인"}
            </button>
          </form>

          <div className="social-divider">
            <span>또는</span>
          </div>

          <button
            type="button"
            className="google-btn"
            onClick={handleGoogleLogin}
            disabled={isLoading}
          >
            Google로 계속하기
          </button>
          <p className="helper-text">현재 Google 로그인은 임시 토큰 입력 방식으로 동작합니다.</p>

          {bypassAuth && (
            <button
              type="button"
              className="preview-btn"
              onClick={() => {
                window.location.href = "/";
              }}
            >
              로그인 없이 UI 미리보기
            </button>
          )}

          <p className="switch-copy">
            {isSignUp ? "이미 계정이 있나요?" : "아직 계정이 없나요?"}
            <button
              type="button"
              className="toggle-btn"
              onClick={() => setIsSignUp((prev) => !prev)}
            >
              {isSignUp ? "로그인" : "회원가입"}
            </button>
          </p>
        </div>
      </section>
    </div>
  );
}
