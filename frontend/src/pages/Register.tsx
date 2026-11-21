import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import {
  Cloud,
  Eye,
  EyeOff,
  Lock,
  Mail,
  User,
} from "lucide-react";
import { register } from "../services/auth";
import "./Auth.css";

function Register() {
  const navigate = useNavigate();

  const [displayName, setDisplayName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
  const [loading, setLoading] = useState(false);

  const handleRegister = async (
    e: React.FormEvent<HTMLFormElement>
  ) => {
    e.preventDefault();
    setError("");
    setSuccess("");

    if (displayName.trim().length === 0) {
      setError("Vui lòng nhập họ tên.");
      return;
    }

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

      await register({
        displayName: displayName.trim(),
        email: email.trim(),
        password,
      });

      setSuccess("Đăng ký thành công! Đang chuyển đến trang đăng nhập...");

      setTimeout(() => {
        navigate("/login", {
          replace: true,
          state: { email: email.trim() },
        });
      }, 1200);
    } catch (err: any) {
      const message =
        err.response?.data?.message ||
        "Đăng ký thất bại. Email có thể đã được sử dụng.";

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
          <h1>Tạo tài khoản mới</h1>
          <p>Đăng ký để bắt đầu sử dụng không gian lưu trữ của bạn.</p>
        </div>

        <form onSubmit={handleRegister}>
          <div className="auth-field">
            <label htmlFor="displayName">Họ và tên</label>

            <div className="auth-input">
              <User size={19} />

              <input
                id="displayName"
                type="text"
                placeholder="Nhập họ và tên"
                value={displayName}
                onChange={(e) => setDisplayName(e.target.value)}
                required
                autoComplete="name"
              />
            </div>
          </div>

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
                placeholder="Tạo mật khẩu từ 5 đến 20 ký tự"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                required
                autoComplete="new-password"
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

          {success && (
            <div className="auth-success" role="status">
              {success}
            </div>
          )}

          <button
            type="submit"
            className="auth-submit"
            disabled={loading}
          >
            {loading ? "Đang tạo tài khoản..." : "Đăng ký"}
          </button>
        </form>

        <div className="auth-footer">
          Đã có tài khoản?
          <Link to="/login"> Đăng nhập</Link>
        </div>

        <div className="auth-copyright">
          © 2026 Spring Mini Drive
        </div>
      </div>
    </div>
  );
}

export default Register;
