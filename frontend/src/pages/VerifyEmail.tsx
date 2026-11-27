import { useEffect, useRef, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { CheckCircle2, Cloud, Loader2, XCircle } from "lucide-react";
import { verifyEmail } from "../services/auth";
import "./Auth.css";

function VerifyEmail() {
  const [searchParams] = useSearchParams();
  const [status, setStatus] = useState<"loading" | "success" | "error">(
    "loading"
  );
  const [message, setMessage] = useState("");
  const calledRef = useRef(false);

  useEffect(() => {
    // StrictMode dev chạy effect 2 lần — chỉ gọi API 1 lần
    // vì token bị xóa sau verify thành công, call thứ 2 sẽ báo invalid
    if (calledRef.current) return;
    calledRef.current = true;

    const token = searchParams.get("token");

    if (!token) {
      setStatus("error");
      setMessage("Link xác thực không hợp lệ hoặc đã hết hạn.");
      return;
    }

    verifyEmail(token)
      .then(() => {
        setStatus("success");
        setMessage("Email đã được xác thực! Bạn có thể đăng nhập ngay.");
      })
      .catch((err: any) => {
        setStatus("error");
        setMessage(
          err.response?.data?.message ||
            "Xác thực thất bại. Link có thể đã hết hạn."
        );
      });
  }, [searchParams]);

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
          <h1>Xác thực email</h1>
          <p>Kiểm tra kết quả xác thực tài khoản của bạn</p>
        </div>

        <div className="verify-status">
          {status === "loading" && (
            <>
              <Loader2 size={44} className="verify-spinner" />
              <p>Đang xác thực...</p>
            </>
          )}

          {status === "success" && (
            <>
              <CheckCircle2 size={44} className="verify-icon verify-icon-success" />
              <p className="verify-message verify-message-success">
                {message}
              </p>
            </>
          )}

          {status === "error" && (
            <>
              <XCircle size={44} className="verify-icon verify-icon-error" />
              <p className="verify-message verify-message-error">
                {message}
              </p>
            </>
          )}
        </div>

        <Link to="/login" className="auth-submit verify-login-link">
          Về trang đăng nhập
        </Link>
      </div>
    </div>
  );
}

export default VerifyEmail;
