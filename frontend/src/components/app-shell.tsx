import { logout, useConnection, useSession } from "@/lib/session";
import { roleNames } from "@/lib/types";
import {
  Award,
  BookOpen,
  GraduationCap,
  History,
  LayoutGrid,
  Library,
  LogOut,
  Menu,
  School,
  Trophy,
  UserRound,
  Users,
  Wifi,
  WifiOff,
  X,
} from "lucide-react";
import { useEffect, useState } from "react";
import { Link, NavLink, Outlet, useLocation } from "react-router-dom";
import { toast } from "sonner";
import { Breadcrumbs, FormDialog } from "./page-layout";
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
  const location = useLocation();
  useEffect(() => {
    setMenu(false);
    window.scrollTo(0, 0);
  }, [location.pathname]);
  const links = [
    { to: home, label: "Tổng quan", icon: LayoutGrid, end: true },
    ...(user!.role === "ADMIN"
      ? [
          { to: `${home}/academic`, label: "Đào tạo", icon: BookOpen },
          { to: `${home}/classes`, label: "Lớp & phân công", icon: School },
          { to: `${home}/users`, label: "Tài khoản đào tạo", icon: Users },
        ]
      : user!.role === "GIANG_VIEN"
        ? [
            { to: `${home}/classes`, label: "Lớp học", icon: School },
            { to: `${home}/banks`, label: "Ngân hàng nội dung", icon: Library },
          ]
        : [
            { to: `${home}/classes`, label: "Lớp học của tôi", icon: School },
            {
              to: `${home}/profile`,
              label: "Hồ sơ",
              icon: UserRound,
              end: true,
            },
            { to: `${home}/history`, label: "Lịch sử học tập", icon: History },
            { to: `${home}/achievements`, label: "Danh hiệu", icon: Award },
            { to: `${home}/boards`, label: "Bảng xếp hạng", icon: Trophy },
          ]),
    { to: `${home}/account`, label: "Tài khoản", icon: UserRound },
  ];
  const navigation = (
    <nav aria-label="Điều hướng chính" className="sidebar-links">
      {links.map(({ to, label, icon: Icon, end }) => (
        <NavLink
          key={to}
          to={to}
          end={end}
          className={({ isActive }) =>
            isActive ||
            (to === `${home}/classes` &&
              location.pathname.startsWith(`${home}/lessons/`))
              ? "active"
              : undefined
          }
          onClick={() => setMenu(false)}
        >
          <Icon className="size-4 shrink-0" />
          {label}
        </NavLink>
      ))}
    </nav>
  );
  const brand = (
    <Link to={home} className="brand">
      <span className="brand-icon">
        <GraduationCap />
      </span>
      ClassQuest
    </Link>
  );
  return (
    <div className="workspace-shell">
      <a href="#main-content" className="skip-link">
        Đến nội dung chính
      </a>
      <aside className="workspace-sidebar">
        {brand}
        <p className="mt-3 text-xs text-muted-foreground">
          Hành trình thử thách trong lớp học
        </p>
        <p className="mb-3 mt-9 eyebrow">{roleNames[user!.role]}</p>
        {navigation}
      </aside>
      <div className="workspace-main">
        <header className="app-header workspace-header">
          <div className="mobile-brand">{brand}</div>
          <div className="desktop-breadcrumb">
            <Breadcrumbs home={home} />
          </div>
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
              className="workspace-menu-toggle"
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
          <div className="mobile-breadcrumb">
            <Breadcrumbs home={home} />
          </div>
          <Outlet />
        </main>
        <footer className="app-footer">
          ClassQuest<span>Hành trình thử thách trong lớp học</span>
        </footer>
      </div>
      <FormDialog
        open={menu}
        onOpenChange={setMenu}
        title="Điều hướng"
        description="Chọn không gian bạn muốn mở."
      >
        {navigation}
      </FormDialog>
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
