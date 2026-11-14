import {
  ArrowLeft,
  ArrowRight,
  Check,
  Cloud,
  Crown,
  ShieldCheck,
  Zap,
  HardDrive,
  Folder,
  Headphones,
} from "lucide-react";
import { useNavigate } from "react-router-dom";
import "./Premium.css";

function Premium() {
  const navigate = useNavigate();

  const features = [
    {
      icon: HardDrive,
      title: "10 GB dung lượng lưu trữ",
      description: "Không gian lưu trữ rộng rãi cho tài liệu của bạn.",
    },
    {
      icon: Folder,
      title: "Quản lý tài liệu thuận tiện",
      description: "Lưu trữ và sắp xếp tài liệu dễ dàng.",
    },
    {
      icon: Zap,
      title: "Mở rộng không gian lưu trữ",
      description: "Nâng cấp từ gói FREE lên PREMIUM.",
    },
    {
      icon: ShieldCheck,
      title: "Quản lý tài khoản",
      description: "Theo dõi thông tin và trạng thái gói dịch vụ.",
    },
    {
      icon: Headphones,
      title: "Hỗ trợ người dùng",
      description: "Hỗ trợ khi gặp vấn đề trong quá trình sử dụng.",
    },
  ];

  return (
    <div className="premium-page">
      <header className="premium-header">
        <button
          className="premium-back"
          onClick={() => navigate("/")}
        >
          <ArrowLeft size={19} />
          Quay lại Dashboard
        </button>

        <div className="premium-brand">
          <Cloud size={27} />
          <span>Spring Mini Drive</span>
        </div>
      </header>

      <main className="premium-main">
        <section className="premium-intro">
          <div className="premium-eyebrow">
            <Crown size={16} />
            SPRING MINI DRIVE PREMIUM
          </div>

          <h1>
            Không gian lưu trữ
            <span> rộng hơn, trải nghiệm tốt hơn</span>
          </h1>

          <p>
            Nâng cấp tài khoản để mở rộng dung lượng lưu trữ
            và quản lý tài liệu thuận tiện hơn.
          </p>
        </section>

        <section className="premium-plans">
          {/* FREE PLAN */}
          <div className="plan-card">
            <div className="plan-icon free-icon">
              <Cloud size={27} />
            </div>

            <span className="plan-name">GÓI FREE</span>

            <h2>Miễn phí</h2>

            <div className="plan-storage">
              100 <span>MB</span>
            </div>

            <p className="plan-description">
              Phù hợp với nhu cầu lưu trữ cơ bản.
            </p>

            <div className="plan-divider" />

            <ul className="plan-features">
              <li>
                <Check size={17} />
                100 MB dung lượng lưu trữ
              </li>
              <li>
                <Check size={17} />
                Quản lý tài liệu
              </li>
              <li>
                <Check size={17} />
                Truy cập tài khoản
              </li>
            </ul>

            <button
              className="plan-current-button"
              disabled
            >
              Gói cơ bản
            </button>
          </div>

          {/* PREMIUM PLAN */}
          <div className="plan-card premium-plan">
            <div className="plan-popular">
              ĐỀ XUẤT
            </div>

            <div className="plan-icon paid-icon">
              <Crown size={27} />
            </div>

            <span className="plan-name">GÓI PREMIUM</span>

            <h2>Nâng cấp trải nghiệm</h2>

            <div className="plan-storage">
              10 <span>GB</span>
            </div>

            <div className="plan-price">
              <strong>29.000đ</strong>
              <span>/ tháng</span>
            </div>

            <p className="plan-description">
              Dành cho người cần nhiều không gian lưu trữ hơn.
            </p>

            <div className="plan-divider" />

            <ul className="plan-features">
              {features.map((feature) => (
                <li key={feature.title}>
                  <Check size={17} />
                  <span>{feature.title}</span>
                </li>
              ))}
            </ul>

            <button
              className="plan-upgrade-button"
              onClick={() => navigate("/payment")}
            >
              Nâng cấp ngay
              <ArrowRight size={19} />
            </button>

            <p className="plan-note">
              Thanh toán trực tuyến qua VNPay
            </p>
          </div>
        </section>

        <section className="premium-benefits">
          <div>
            <ShieldCheck size={22} />
            <span>Quản lý gói dịch vụ rõ ràng</span>
          </div>

          <div>
            <HardDrive size={22} />
            <span>Không gian lưu trữ mở rộng</span>
          </div>

          <div>
            <Zap size={22} />
            <span>Nâng cấp thuận tiện</span>
          </div>
        </section>
      </main>

      <footer className="premium-footer">
        © 2026 Spring Mini Drive. All rights reserved.
      </footer>
    </div>
  );
}

export default Premium;
