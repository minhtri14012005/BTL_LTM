import { useEffect } from "react";
import { Navigate, Outlet, Route, Routes, useLocation } from "react-router-dom";
import {
  initializeSession,
  refreshSession,
  useSession,
  logout,
} from "@/lib/session";
import type { Role } from "@/lib/types";
import { LoginPage } from "@/features/auth/login-page";
import { AccountPage } from "@/features/auth/account-page";
import { DashboardPage } from "@/features/dashboard/dashboard-page";
import { ClassPage } from "@/features/dashboard/class-page";
import { AppShell } from "@/components/app-shell";
import { ErrorState, Loading } from "@/components/feedback";
import { Button } from "@/components/ui/button";
import { toast } from "sonner";
import { AcademicPage } from "@/features/admin/academic-page";
import { AdminClassesPage } from "@/features/admin/classes-page";
import { AdminUsersPage } from "@/features/admin/users-page";
import { TeachingClassPage } from "@/features/teaching/class-page";
import { TeachingLessonPage } from "@/features/teaching/lesson-page";
import { BankPage } from "@/features/teaching/bank-page";
import { StudentDashboard } from "@/features/student/dashboard";
import { StudentClassPage } from "@/features/student/class-page";
import { StudentLessonPage } from "@/features/student/lesson-page";
import {
  StudentProfilePage,
  StudentIdentityEditor,
} from "@/features/student/profile-page";
import { StudentBoardsPage } from "@/features/student/boards-page";

export function RoleGate({ role }: { role: Role }) {
  const { user } = useSession();
  return user?.role === role ? (
    <Outlet />
  ) : (
    <ErrorState message="Bạn không có quyền truy cập trang này." />
  );
}
function AuthGate() {
  const { user } = useSession(),
    location = useLocation();
  if (!user)
    return <Navigate to="/login" state={{ from: location.pathname }} replace />;
  if (!user.profileComplete)
    return (
      <div className="mx-auto max-w-xl space-y-4 p-6">
        <ErrorState message="Hồ sơ chưa có mã sinh viên. Vui lòng hoàn thiện hồ sơ trước khi vào lớp." />
        <StudentIdentityEditor reload={() => {}} />
        <Button
          onClick={() => {
            void logout().catch((error) => toast.error(error.message));
          }}
        >
          Đăng xuất
        </Button>
      </div>
    );
  return <Outlet />;
}
function Home() {
  const { user } = useSession();
  return (
    <Navigate
      to={user ? `/app/${user.role.toLowerCase()}` : "/login"}
      replace
    />
  );
}
export function App() {
  const { loading, error } = useSession();
  useEffect(() => {
    void initializeSession();
  }, []);
  if (loading)
    return (
      <div className="mx-auto max-w-5xl p-6">
        <Loading label="Đang xác thực phiên…" />
      </div>
    );
  if (error)
    return (
      <div className="mx-auto max-w-xl p-6">
        <ErrorState
          message={error}
          retry={() => {
            void refreshSession();
          }}
        />
      </div>
    );
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route element={<AuthGate />}>
        <Route element={<AppShell />}>
          <Route path="/app" element={<Home />} />
          <Route element={<RoleGate role="SINH_VIEN" />}>
            <Route
              path="/app/sinh_vien/lessons/:id"
              element={<StudentLessonPage />}
            />
            <Route
              path="/app/sinh_vien/profile"
              element={<StudentProfilePage />}
            />
            <Route
              path="/app/sinh_vien/boards"
              element={<StudentBoardsPage />}
            />
          </Route>
          <Route element={<RoleGate role="GIANG_VIEN" />}>
            <Route path="/app/giang_vien/banks" element={<BankPage />} />
            <Route
              path="/app/giang_vien/lessons/:id"
              element={<TeachingLessonPage />}
            />
          </Route>
          <Route element={<RoleGate role="ADMIN" />}>
            <Route path="/app/admin/academic" element={<AcademicPage />} />
            <Route path="/app/admin/classes" element={<AdminClassesPage />} />
            <Route path="/app/admin/users" element={<AdminUsersPage />} />
          </Route>
          {(["ADMIN", "GIANG_VIEN", "SINH_VIEN"] as Role[]).map((role) => (
            <Route key={role} element={<RoleGate role={role} />}>
              <Route
                path={`/app/${role.toLowerCase()}`}
                element={
                  role === "SINH_VIEN" ? (
                    <StudentDashboard />
                  ) : (
                    <DashboardPage />
                  )
                }
              />
              <Route
                path={`/app/${role.toLowerCase()}/account`}
                element={<AccountPage />}
              />
              <Route
                path={`/app/${role.toLowerCase()}/classes/:id`}
                element={
                  role === "GIANG_VIEN" ? (
                    <TeachingClassPage />
                  ) : role === "SINH_VIEN" ? (
                    <StudentClassPage />
                  ) : (
                    <ClassPage />
                  )
                }
              />
            </Route>
          ))}
        </Route>
      </Route>
      <Route path="/" element={<Home />} />
      <Route
        path="*"
        element={
          <div className="mx-auto max-w-xl space-y-4 p-6">
            <ErrorState message="Không tìm thấy trang bạn yêu cầu." />
            <a className="text-primary underline" href="/app">
              Về trang tổng quan
            </a>
          </div>
        }
      />
    </Routes>
  );
}
