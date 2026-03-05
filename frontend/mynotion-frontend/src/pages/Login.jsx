import { useState } from "react";
import { authApi } from "../api";
import { useAuthStore } from "../store";
import "./Login.css";

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
      setError(err.response?.data?.message || "Authentication failed.");
    } finally {
      setIsLoading(false);
    }
  };

  const handleGoogleLogin = async () => {
    setError("");
    const idToken = window.prompt(
      "Paste Google ID token here (temporary MVP flow)"
    );

    if (!idToken) {
      return;
    }

    setIsLoading(true);
    try {
      const response = await authApi.googleLogin(idToken.trim());
      applyAuthResponse(response);
    } catch (err) {
      setError(err.response?.data?.message || "Google login failed.");
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="login-container">
      <div className="login-card">
        <h1>MyNotion AI</h1>
        <form onSubmit={handleSubmit}>
          {isSignUp && (
            <input
              type="text"
              placeholder="Nickname"
              value={name}
              onChange={(e) => setName(e.target.value)}
              required
            />
          )}
          <input
            type="email"
            placeholder="Email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            required
          />
          <input
            type="password"
            placeholder="Password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
          />
          {error && <p className="error">{error}</p>}
          <button type="submit" disabled={isLoading}>
            {isLoading ? "Processing..." : isSignUp ? "Sign up" : "Login"}
          </button>
        </form>

        <div className="social-divider">
          <span>or</span>
        </div>
        <button
          type="button"
          className="google-btn"
          onClick={handleGoogleLogin}
          disabled={isLoading}
        >
          Continue with Google
        </button>

        <p>
          {isSignUp ? "Already have an account?" : "Need an account?"}
          <button
            type="button"
            className="toggle-btn"
            onClick={() => setIsSignUp((prev) => !prev)}
          >
            {isSignUp ? "Login" : "Sign up"}
          </button>
        </p>
      </div>
    </div>
  );
}
