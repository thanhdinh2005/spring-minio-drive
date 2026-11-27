import { useState } from "react";
import { Link, useLocation, useNavigate } from "react-router-dom";
import { Cloud, Eye, EyeOff, Lock, Mail } from "lucide-react";
import {
  googleLogin,
  login,
  resendVerification,
} from "../services/auth";
import { signInWithGoogle } from "../services/firebase";
import "./Auth.css";

function Login() {
  const navigate = useNavigate();
  const location = useLocation();

  const [email, setEmail] = useState(
    (location.state as { email?: string } | null)?.email ?? ""
  );
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);
  const [emailNotVerified, setEmailNotVerified] = useState(false);
  const [resendInfo, setResendInfo] = useState("");

  const handleGoogleLogin = async () => {
    setError("");
    try {
      setLoading(true);

      const idToken = await signInWithGoogle();
      const result = await googleLogin(idToken);

      localStorage.setItem("accessToken", result.accessToken);
      navigate("/", { replace: true });
    } catch (err: any) {
      // Firebase popup errors có dạng auth/popup-closed-by-user, auth/cancelled-popup...
      if (typeof err?.code === "string" && err.code.startsWith("auth/")) {
        setError("Đăng nhập Google bị hủy hoặc thất bại. Vui lòng thử lại.");
      } else {
        setError(
          err.response?.data?.message || "Đăng nhập Google thất bại."
        );
      }
    } finally {
      setLoading(false);
    }
  };

  const handleResendVerification = async () => {
    setResendInfo("");
    try {
      await resendVerification(email);
      setResendInfo("Đã gửi lại email xác thực. Kiểm tra hộp thư (và spam).");
    } catch {
      setResendInfo("Không gửi được email. Vui lòng thử lại sau.");
    }
  };

  const handleLogin = async (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    setError("");
    setEmailNotVerified(false);
    setResendInfo("");

    if (email.length < 5 || email.length > 40) {
      setError("Email phải có từ 5 đến 20 ký tự.");
      return;
    }

    if (password.length < 5 || password.length > 20) {
      setError("Mật khẩu phải có từ 5 đến 20 ký tự.");
      return;
    }

    try {
      setLoading(true);

      const result = await login({
        email,
        password,
      });

      localStorage.setItem("accessToken", result.accessToken);

      navigate("/", { replace: true });
    } catch (err: any) {
      const message =
        err.response?.data?.message ||
        "Đăng nhập thất bại. Vui lòng kiểm tra email và mật khẩu.";

      setError(message);
      setEmailNotVerified(
        err.response?.data?.code === "ERR_USER_008"
      );
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="auth-page">
      <div className="auth-card">
        <div className="auth-logo">
          <div className="auth-logo-icon">
            <Cloud size={30} />
          </div>

          <span>Spring Mini Drive</span>
        </div>

        <div className="auth-heading">
          <h1>Chào mừng trở lại!</h1>
          <p>Đăng nhập để tiếp tục sử dụng không gian lưu trữ của bạn.</p>
        </div>

        <form onSubmit={handleLogin}>
          <div className="auth-field">
            <label htmlFor="email">Địa chỉ email</label>

            <div className="auth-input">
              <Mail size={19} />

              <input
                id="email"
                type="email"
                placeholder="Nhập email của bạn"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                required
                autoComplete="email"
              />
            </div>
          </div>

          <div className="auth-field">
            <label htmlFor="password">Mật khẩu</label>

            <div className="auth-input">
              <Lock size={19} />

              <input
                id="password"
                type={showPassword ? "text" : "password"}
                placeholder="Nhập mật khẩu"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                required
                autoComplete="current-password"
              />

              <button
                type="button"
                className="password-toggle"
                onClick={() => setShowPassword(!showPassword)}
                aria-label={showPassword ? "Ẩn mật khẩu" : "Hiện mật khẩu"}
              >
                {showPassword ? <EyeOff size={19} /> : <Eye size={19} />}
              </button>
            </div>
          </div>

          {error && (
            <div className="auth-error" role="alert">
              {error}
            </div>
          )}

          {emailNotVerified && (
            <button
              type="button"
              className="auth-resend"
              onClick={handleResendVerification}
            >
              Gửi lại email xác thực
            </button>
          )}

          {resendInfo && (
            <div className="auth-success" role="alert">
              {resendInfo}
            </div>
          )}

          <button
            type="submit"
            className="auth-submit"
            disabled={loading}
          >
            {loading ? "Đang đăng nhập..." : "Đăng nhập"}
          </button>
        </form>

        {import.meta.env.VITE_FIREBASE_PROJECT_ID && (
          <>
            <div className="auth-divider">
              <span>hoặc</span>
            </div>
            <button
              type="button"
              className="auth-submit auth-google"
              onClick={handleGoogleLogin}
              disabled={loading}
            >
              Đăng nhập với Google
            </button>
          </>
        )}

        <div className="auth-footer">
          Chưa có tài khoản?
          <Link to="/register"> Đăng ký ngay</Link>
        </div>

        <div className="auth-copyright">
          © 2026 Spring Mini Drive
        </div>
      </div>
    </div>
  );
}

export default Login;
