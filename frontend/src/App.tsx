import { useState } from "react";
import {
  HardDrive,
  Search,
  Plus,
  Home,
  Folder,
  Share2,
  Star,
  Trash2,
  Settings,
  CircleHelp,
  Bell,
  Grid2X2,
  ChevronDown,
  CreditCard,
  ShieldCheck,
  Cloud,
  ArrowUpRight,
  Menu,
  X,
} from "lucide-react";

import {
  Routes,
  Route,
  useNavigate,
  Navigate,
} from "react-router-dom";

import Premium from "./pages/Premium";
import Payment from "./pages/Payment";
import Login from "./pages/Login";
import Register from "./pages/Register";
import "./App.css";

function Dashboard() {
  const navigate = useNavigate();

  const [activeMenu, setActiveMenu] = useState("Trang chủ");
  const [showMenu, setShowMenu] = useState(false);

  const menuItems = [
    { name: "Trang chủ", icon: Home },
    { name: "Ổ đĩa của tôi", icon: HardDrive },
    { name: "Được chia sẻ", icon: Share2 },
    { name: "Có gắn dấu sao", icon: Star },
    { name: "Thùng rác", icon: Trash2 },
  ];

  return (
    <div className="app-container">

      {/* SIDEBAR */}
      <aside className={`sidebar ${showMenu ? "sidebar-open" : ""}`}>
        <div className="logo">
          <div className="logo-icon">
            <Cloud size={28} />
          </div>

          <span>Spring Mini Drive</span>

          <button
            className="close-menu"
            onClick={() => setShowMenu(false)}
          >
            <X size={20} />
          </button>
        </div>

        <button className="new-button">
          <Plus size={21} />
          <span>Mới</span>
        </button>

        <nav className="sidebar-nav">
          {menuItems.map((item) => {
            const Icon = item.icon;

            return (
              <button
                key={item.name}
                className={`nav-item ${
                  activeMenu === item.name ? "active" : ""
                }`}
                onClick={() => {
                  setActiveMenu(item.name);
                  setShowMenu(false);
                }}
              >
                <Icon size={19} />
                <span>{item.name}</span>
              </button>
            );
          })}
        </nav>

        <div className="sidebar-divider" />

        <button
          className="nav-item"
          onClick={() => navigate("/payment")}
        >
          <CreditCard size={19} />
          <span>Thanh toán</span>
        </button>

        <button className="nav-item">
          <Settings size={19} />
          <span>Cài đặt</span>
        </button>

        <button className="nav-item">
          <CircleHelp size={19} />
          <span>Trợ giúp</span>
        </button>

        <div className="sidebar-bottom">
          <div className="storage-mini">
            <Cloud size={21} />

            <div>
              <strong>Dung lượng lưu trữ</strong>
              <span>0 MB / 100 MB</span>
            </div>
          </div>

          <div className="storage-progress">
            <div />
          </div>

          <p>Đang sử dụng gói FREE</p>

          <button
            className="upgrade-small"
            onClick={() => navigate("/premium")}
          >
            Nâng cấp dung lượng
            <ArrowUpRight size={15} />
          </button>
        </div>
      </aside>

      {/* MAIN CONTENT */}
      <main className="main-content">

        {/* HEADER */}
        <header className="top-header">
          <button
            className="mobile-menu"
            onClick={() => setShowMenu(!showMenu)}
          >
            <Menu size={22} />
          </button>

          <div className="search-box">
            <Search size={20} />

            <input
              type="text"
              placeholder="Tìm kiếm trong Drive"
            />
          </div>

          <div className="header-actions">
            <button className="icon-button">
              <CircleHelp size={21} />
            </button>

            <button className="icon-button">
              <Settings size={21} />
            </button>

            <button className="icon-button">
              <Bell size={21} />
            </button>

            <button className="avatar">
              H
            </button>
          </div>
        </header>

        {/* PAGE CONTENT */}
        <section className="page-content">

          <div className="welcome-section">
            <div>
              <h1>Trang chủ</h1>

              <p>
                Chào mừng bạn đến với Spring Mini Drive
              </p>
            </div>

            <button className="view-button">
              <Grid2X2 size={18} />
              <ChevronDown size={16} />
            </button>
          </div>

          {/* ACCOUNT CARD */}
          <div className="account-card">
            <div className="account-info">
              <div className="account-avatar">
                H
              </div>

              <div>
                <span className="muted-text">
                  Tài khoản của bạn
                </span>

                <h2>Xin chào, người dùng!</h2>

                <span className="plan-label">
                  <ShieldCheck size={14} />
                  Gói FREE
                </span>
              </div>
            </div>

            <div className="account-storage">
              <span>Dung lượng đã sử dụng</span>

              <strong>0 MB / 100 MB</strong>

              <div className="storage-progress large">
                <div />
              </div>
            </div>
          </div>

          {/* PREMIUM CARD */}
          <div className="premium-card">
            <div className="premium-content">
              <span className="premium-tag">
                <ShieldCheck size={15} />
                SPRING MINI DRIVE PREMIUM
              </span>

              <h2>Nâng cấp không gian lưu trữ</h2>

              <p>
                Mở rộng dung lượng lưu trữ và tận hưởng
                trải nghiệm quản lý tài liệu tốt hơn.
              </p>

              <button
                className="premium-button"
                onClick={() => navigate("/premium")}
              >
                Nâng cấp ngay
                <ArrowUpRight size={17} />
              </button>
            </div>

            <div className="premium-illustration">
              <Cloud size={92} strokeWidth={1.2} />
              <span>PREMIUM</span>
            </div>
          </div>

          {/* QUICK ACCESS */}
          <div className="section-heading">
            <h2>Truy cập nhanh</h2>
          </div>

          <div className="quick-grid">

            <div className="quick-card">
              <div className="quick-icon blue">
                <Folder size={23} />
              </div>

              <div>
                <strong>Ổ đĩa của tôi</strong>
                <span>Quản lý tài liệu</span>
              </div>
            </div>

            <div className="quick-card">
              <div className="quick-icon green">
                <Share2 size={23} />
              </div>

              <div>
                <strong>Được chia sẻ</strong>
                <span>Tài liệu được chia sẻ</span>
              </div>
            </div>

            <div className="quick-card">
              <div className="quick-icon orange">
                <Star size={23} />
              </div>

              <div>
                <strong>Yêu thích</strong>
                <span>Tài liệu quan trọng</span>
              </div>
            </div>

          </div>

        </section>
      </main>
    </div>
  );
}

function ProtectedRoute({ children }: { children: React.ReactNode }) {
  const token = localStorage.getItem("accessToken");

  if (!token) {
    return <Navigate to="/login" replace />;
  }

  return <>{children}</>;
}

// ROUTER
function App() {
  return (
    <Routes>
      <Route path="/login" element={<Login />} />

      <Route path="/register" element={<Register />} />

      <Route
        path="/"
        element={
          <ProtectedRoute>
            <Dashboard />
          </ProtectedRoute>
        }
      />

      <Route
        path="/premium"
        element={
          <ProtectedRoute>
            <Premium />
          </ProtectedRoute>
        }
      />

      <Route
        path="/payment"
        element={
          <ProtectedRoute>
            <Payment />
          </ProtectedRoute>
        }
      />

      <Route
        path="*"
        element={<h1 style={{ padding: 40 }}>404 - Không tìm thấy trang</h1>}
      />
    </Routes>
  );
}

export default App;
