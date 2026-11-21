import { useNavigate } from "react-router-dom";
import { useState } from "react";
import {
  ArrowLeft,
  Check,
  ShieldCheck,
  CreditCard,
  Wallet,
  HardDrive,
  LockKeyhole,
} from "lucide-react";
import "./Payment.css";

function Payment() {
  const navigate = useNavigate();
  const [paymentMethod, setPaymentMethod] = useState("vnpay");

  const price = 29000;

  const formatPrice = (value: number) =>
    new Intl.NumberFormat("vi-VN").format(value) + "đ";

  const handlePayment = () => {
    alert(
      "Giao diện thanh toán đã sẵn sàng. Chức năng kết nối VNPay sẽ được tích hợp với Backend."
    );
  };

  return (
    <div className="payment-page">
      <header className="payment-header">
        <div
          className="payment-logo"
          onClick={() => navigate("/")}
        >
          <HardDrive size={27} />
          <span>Spring Mini Drive</span>
        </div>

        <div className="payment-security">
          <LockKeyhole size={16} />
          <span>Thanh toán an toàn</span>
        </div>
      </header>

      <main className="payment-container">
        <button
          className="payment-back"
          onClick={() => navigate("/premium")}
        >
          <ArrowLeft size={18} />
          Quay lại gói Premium
        </button>

        <div className="payment-heading">
          <h1>Hoàn tất đăng ký Premium</h1>
          <p>
            Chỉ còn một bước nữa để mở rộng không gian lưu trữ
            của bạn.
          </p>
        </div>

        <div className="payment-content">
          <section className="payment-left">
            <div className="payment-card">
              <h2>Thông tin gói dịch vụ</h2>

              <div className="payment-plan">
                <div className="payment-plan-icon">
                  <HardDrive size={28} />
                </div>

                <div className="payment-plan-info">
                  <h3>Spring Mini Drive Premium</h3>
                  <p>Dung lượng lưu trữ 10 GB</p>
                </div>

                <span className="premium-label">PREMIUM</span>
              </div>

              <div className="payment-benefits">
                <div>
                  <Check size={17} />
                  <span>10 GB dung lượng lưu trữ</span>
                </div>
                <div>
                  <Check size={17} />
                  <span>Quản lý và lưu trữ tệp tin</span>
                </div>
                <div>
                  <Check size={17} />
                  <span>Chia sẻ tệp tin thuận tiện</span>
                </div>
                <div>
                  <Check size={17} />
                  <span>Trải nghiệm không gian lưu trữ mở rộng</span>
                </div>
              </div>
            </div>

            <div className="payment-card">
              <h2>Phương thức thanh toán</h2>

              <button
                className={`payment-method ${
                  paymentMethod === "vnpay" ? "selected" : ""
                }`}
                onClick={() => setPaymentMethod("vnpay")}
              >
                <div className="method-icon vnpay-icon">
                  <CreditCard size={24} />
                </div>

                <div className="method-info">
                  <strong>VNPay</strong>
                  <span>Thanh toán qua cổng VNPay</span>
                </div>

                <span className="method-radio">
                  {paymentMethod === "vnpay" && <Check size={14} />}
                </span>
              </button>

              <button
                className={`payment-method ${
                  paymentMethod === "bank" ? "selected" : ""
                }`}
                onClick={() => setPaymentMethod("bank")}
              >
                <div className="method-icon">
                  <Wallet size={24} />
                </div>

                <div className="method-info">
                  <strong>Chuyển khoản ngân hàng</strong>
                  <span>Phương thức sẽ được bổ sung sau</span>
                </div>

                <span className="method-radio">
                  {paymentMethod === "bank" && <Check size={14} />}
                </span>
              </button>
            </div>
          </section>

          <aside className="payment-right">
            <div className="payment-card order-card">
              <h2>Tóm tắt đơn hàng</h2>

              <div className="order-row">
                <span>Gói Premium</span>
                <strong>{formatPrice(price)}</strong>
              </div>

              <div className="order-row">
                <span>Thời hạn</span>
                <strong>1 tháng</strong>
              </div>

              <div className="order-divider" />

              <div className="order-total">
                <span>Tổng thanh toán</span>
                <strong>{formatPrice(price)}</strong>
              </div>

              <button
                className="payment-submit"
                onClick={handlePayment}
              >
                Xác nhận thanh toán
              </button>

              <div className="payment-note">
                <ShieldCheck size={18} />
                <span>
                  Thông tin thanh toán được bảo vệ.
                  Bạn sẽ được chuyển đến cổng thanh toán
                  khi tích hợp VNPay thực tế.
                </span>
              </div>
            </div>

            <p className="payment-support">
              Cần hỗ trợ? Liên hệ đội ngũ Spring Mini Drive.
            </p>
          </aside>
        </div>
      </main>
    </div>
  );
}

export default Payment;
