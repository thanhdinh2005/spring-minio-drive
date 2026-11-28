import { useState } from "react";
import { Link } from "react-router-dom";
import {
  Cloud,
  Eye,
  EyeOff,
  Lock,
  Mail,
  MailCheck,
  User,
} from "lucide-react";
import { register, resendVerification } from "../services/auth";
import "./Auth.css";

function Register() {
  const [displayName, setDisplayName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);
  const [registeredEmail, setRegisteredEmail] = useState("");
  const [resendInfo, setResendInfo] = useState("");
  const [resending, setResending] = useState(false);

  const handleRegister = async (
    e: React.FormEvent<HTMLFormElement>
  ) => {
    e.preventDefault();
    setError("");
    setResendInfo("");

    if (displayName.trim().length === 0) {
      setError("Vui lòng nhập họ tên.");
      return;
    }

    if (email.length < 5 || email.length > 40) {
      setError("Email phải có từ 5 đến 40 ký tự.");
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

      setRegisteredEmail(email.trim());
    } catch (err: any) {
      const message =
        err.response?.data?.message ||
        "Đăng ký thất bại. Email có thể đã được sử dụng.";

      setError(message);
    } finally {
      setLoading(false);
    }
  };

  const handleResend = async () => {
    setResendInfo("");
    setResending(true);
    try {
      await resendVerification(registeredEmail);
      setResendInfo("Đã gửi lại email xác thực. Kiểm tra hộp thư (và spam).");
    } catch {
      setResendInfo("Không gửi được email. Vui lòng thử lại sau.");
    } finally {
      setResending(false);
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

        {registeredEmail ? (
          <>
            <div className="auth-heading">
              <h1>Kiểm tra email của bạn</h1>
            </div>

            <div className="verify-status">
              <MailCheck size={44} className="verify-icon verify-icon-success" />

              <p>
                Chúng tôi đã gửi link xác thực tới{" "}
                <strong>{registeredEmail}</strong>. Mở email và bấm vào link
                để kích hoạt tài khoản, sau đó đăng nhập.
              </p>

              <p className="verify-hint">
                Không thấy email? Kiểm tra cả hộp thư Spam/Promotions.
              </p>
            </div>

            <button
              type="button"
              className="auth-resend"
              onClick={handleResend}
              disabled={resending}
            >
              {resending ? "Đang gửi..." : "Gửi lại email xác thực"}
            </button>

            {resendInfo && (
              <div className="auth-success" role="status">
                {resendInfo}
              </div>
            )}

            <Link
              to="/login"
              state={{ email: registeredEmail }}
              className="auth-submit verify-login-link"
            >
              Đến trang đăng nhập
            </Link>
          </>
        ) : (
          <>
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
          </>
        )}

        <div className="auth-copyright">
          © 2026 Spring Mini Drive
        </div>
      </div>
    </div>
  );
}

export default Register;
