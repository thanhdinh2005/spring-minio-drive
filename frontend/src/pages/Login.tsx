import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { Cloud, Eye, EyeOff, Lock, Mail } from "lucide-react";
import { login } from "../services/auth";
import "./Auth.css";

function Login() {
  const navigate = useNavigate();

  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  const handleLogin = async (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    setError("");

    if (email.length < 5 || email.length > 20) {
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

          <button
            type="submit"
            className="auth-submit"
            disabled={loading}
          >
            {loading ? "Đang đăng nhập..." : "Đăng nhập"}
          </button>
        </form>

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
