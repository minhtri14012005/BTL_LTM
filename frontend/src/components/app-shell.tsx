import { useEffect, useState } from "react";
import { Link, NavLink, Outlet } from "react-router-dom";
import {
  GraduationCap,
  LayoutGrid,
  LogOut,
  Menu,
  UserRound,
  Wifi,
  WifiOff,
  X,
} from "lucide-react";
import { toast } from "sonner";
import { logout, useSession, useConnection } from "@/lib/session";
import { roleNames } from "@/lib/types";
import { Button } from "./ui/button";
import { Confirm } from "./ui/confirm";

export function AppShell() {
  const { user } = useSession(),
    connection = useConnection();
  const [menu, setMenu] = useState(false),
    [confirm, setConfirm] = useState(false),
    [pending, setPending] = useState(false);
  const [online, setOnline] = useState(navigator.onLine);
  useEffect(() => {
    const update = () => setOnline(navigator.onLine);
    window.addEventListener("online", update);
    window.addEventListener("offline", update);
    return () => {
      window.removeEventListener("online", update);
      window.removeEventListener("offline", update);
    };
  }, []);
  async function signOut() {
    setPending(true);
    try {
      await logout();
      toast.success("Đã đăng xuất.");
    } catch (error) {
      toast.error(
        error instanceof Error ? error.message : "Chưa đăng xuất được.",
      );
      setPending(false);
    }
  }
  const home = `/app/${user!.role.toLowerCase()}`;
  return (
    <div className="app-layout">
      <a href="#main-content" className="skip-link">
        Đến nội dung chính
      </a>
      <header className="app-header">
        <Link to={home} className="brand">
          <span className="brand-icon">
            <GraduationCap />
          </span>
          ClassQuest
        </Link>
        <nav
          aria-label="Điều hướng chính"
          className={`main-nav ${menu ? "nav-open" : ""}`}
        >
          <NavLink to={home} end onClick={() => setMenu(false)}>
            <LayoutGrid />
            Tổng quan
          </NavLink>
          <NavLink to={`${home}/account`} onClick={() => setMenu(false)}>
            <UserRound />
            Tài khoản
          </NavLink>
          {user!.role === "SINH_VIEN" && (
            <>
              <NavLink to={`${home}/profile`} onClick={() => setMenu(false)}>
                Hồ sơ
              </NavLink>
              <NavLink to={`${home}/boards`} onClick={() => setMenu(false)}>
                Xếp hạng
              </NavLink>
            </>
          )}
        </nav>
        <div className="flex min-w-0 items-center gap-2 sm:gap-4">
          <span
            className="connection-indicator"
            title={
              connection === "connected"
                ? "Đã kết nối trực tiếp"
                : "Kết nối trực tiếp chưa sẵn sàng"
            }
          >
            {connection === "connected" ? (
              <Wifi className="size-4 text-emerald-700" />
            ) : (
              <WifiOff className="size-4 text-amber-700" />
            )}
          </span>
          <Link to={`${home}/account`} className="user-chip">
            <span className="avatar">
              {user!.hoTen.charAt(0).toLocaleUpperCase("vi")}
            </span>
            <span className="user-name">
              <strong>{user!.hoTen}</strong>
              <small>{roleNames[user!.role]}</small>
            </span>
          </Link>
          <Button
            variant="ghost"
            size="icon"
            aria-label="Đăng xuất"
            onClick={() => setConfirm(true)}
          >
            <LogOut />
          </Button>
          <Button
            variant="ghost"
            size="icon"
            aria-label={menu ? "Đóng menu" : "Mở menu"}
            aria-expanded={menu}
            className="menu-toggle"
            onClick={() => setMenu(!menu)}
          >
            {menu ? <X /> : <Menu />}
          </Button>
        </div>
      </header>
      {(!online ||
        connection === "reconnecting" ||
        connection === "connecting" ||
        connection === "replaced") && (
        <div className="network-notice" role="status">
          {!online
            ? "Mất mạng. Các thao tác cần kết nối máy chủ."
            : connection === "replaced"
              ? "Kết nối trực tiếp đã chuyển sang cửa sổ khác. Tải lại trang nếu muốn sử dụng tại đây."
              : "Đang kết nối trực tiếp với máy chủ…"}
        </div>
      )}
      <main id="main-content" className="app-content">
        <Outlet />
      </main>
      <footer className="app-footer">
        ClassQuest<span>Hành trình thử thách trong lớp học</span>
      </footer>
      <Confirm
        open={confirm}
        onOpenChange={setConfirm}
        title="Đăng xuất khỏi ClassQuest?"
        description={`Kết thúc phiên đăng nhập của ${user!.hoTen} trên trình duyệt này.`}
        onConfirm={signOut}
        pending={pending}
      />
    </div>
  );
}
