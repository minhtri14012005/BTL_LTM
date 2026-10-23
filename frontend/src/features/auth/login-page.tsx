import { useState, type FormEvent } from "react";
import { Navigate, useLocation } from "react-router-dom";
import {
  ArrowRight,
  BookOpen,
  Eye,
  EyeOff,
  GraduationCap,
  LoaderCircle,
} from "lucide-react";
import { login, refreshSession, useSession } from "@/lib/session";
import { ApiError, post } from "@/lib/api";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { FormField } from "@/components/ui/form";

export function LoginPage() {
  const { user } = useSession(),
    location = useLocation();
  const [username, setUsername] = useState(""),
    [password, setPassword] = useState("");
  const [show, setShow] = useState(false),
    [pending, setPending] = useState(false),
    [error, setError] = useState("");
  const from = location.state?.from as string | undefined;
  const [register, setRegister] = useState(false),
    [name, setName] = useState(""),
    [code, setCode] = useState("");
  if (user) {
    const home = `/app/${user.role.toLowerCase()}`;
    return (
      <Navigate
        to={from === home || from?.startsWith(`${home}/`) ? from : home}
        replace
      />
    );
  }
  async function submit(event: FormEvent) {
    event.preventDefault();
    setError("");
    setPending(true);
    try {
      if (register)
        await post("/api/auth/register", {
          username: username.trim(),
          password,
          hoTen: name.trim(),
          maSinhVien: code,
        });
      await login(username.trim(), password);
    } catch (error) {
      if (error instanceof ApiError && error.code === "ALREADY_AUTHENTICATED")
        await refreshSession();
      else
        setError(
          error instanceof Error ? error.message : "Đăng nhập chưa thành công.",
        );
    } finally {
      setPending(false);
    }
  }
  return (
    <div className="login-layout">
      <aside className="login-story">
        <a href="/login" className="brand">
          <span className="brand-icon">
            <GraduationCap />
          </span>
          ClassQuest
        </a>
        <div className="space-y-6">
          <span className="eyebrow">KHÔNG GIAN HỌC TẬP TƯƠNG TÁC</span>
          <h1>
            Học cùng nhau.
            <br />
            <span className="text-primary">Tiến bộ mỗi buổi.</span>
          </h1>
          <p className="max-w-md text-lg leading-8 text-muted-foreground">
            Kết nối lớp học, tham gia hoạt động và theo dõi hành trình học tập
            của bạn.
          </p>
          <div className="login-art" aria-hidden="true">
            <div className="art-orbit" />
            <div className="art-book">
              <BookOpen className="size-20" />
            </div>
            <div className="art-chip">ClassQuest · Hành trình thử thách trong lớp học</div>
          </div>
        </div>
        <p className="text-sm text-muted-foreground">
          Một không gian chung cho giảng viên và sinh viên.
        </p>
      </aside>
      <main className="login-form">
        <div className="w-full max-w-sm">
          <div className="mb-9">
            <span className="eyebrow">CHÀO MỪNG TRỞ LẠI</span>
            <h2 className="mt-3 text-3xl font-semibold tracking-tight">
              {register ? "Đăng ký sinh viên" : "Đăng nhập"}
            </h2>
            <p className="mt-3 leading-6 text-muted-foreground">
              Sử dụng tài khoản của bạn để vào lớp học.
            </p>
          </div>
          <form className="space-y-5" onSubmit={submit}>
            {register && (
              <>
                <FormField label="Họ và tên">
                  {(props) => (
                    <Input
                      {...props}
                      required
                      maxLength={100}
                      value={name}
                      onChange={(e) => setName(e.target.value)}
                      disabled={pending}
                    />
                  )}
                </FormField>
                <FormField label="Mã sinh viên">
                  {(props) => (
                    <Input
                      {...props}
                      required
                      maxLength={32}
                      pattern={"[A-Za-z0-9][A-Za-z0-9_\\-]{0,31}"}
                      value={code}
                      onChange={(e) => setCode(e.target.value)}
                      disabled={pending}
                    />
                  )}
                </FormField>
              </>
            )}
            <FormField label="Tên đăng nhập">
              {(props) => (
                <Input
                  {...props}
                  name="username"
                  autoComplete="username"
                  required
                  maxLength={register ? 32 : 64}
                  pattern={register ? "[A-Za-z0-9_]{3,32}" : undefined}
                  value={username}
                  onChange={(e) => setUsername(e.target.value)}
                  disabled={pending}
                  autoFocus
                />
              )}
            </FormField>
            <FormField label="Mật khẩu">
              {(props) => (
                <div className="relative">
                  <Input
                    {...props}
                    name="password"
                    className="pr-12"
                    type={show ? "text" : "password"}
                    autoComplete="current-password"
                    required
                    maxLength={register ? 72 : 128}
                    minLength={register ? 8 : undefined}
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    disabled={pending}
                  />
                  <Button
                    type="button"
                    variant="ghost"
                    size="icon"
                    className="absolute right-0 top-0"
                    aria-label={show ? "Ẩn mật khẩu" : "Hiện mật khẩu"}
                    aria-pressed={show}
                    onClick={() => setShow(!show)}
                  >
                    {show ? <EyeOff /> : <Eye />}
                  </Button>
                </div>
              )}
            </FormField>
            {error && (
              <p
                role="alert"
                className="rounded-xl bg-destructive/5 p-3 text-sm leading-6 text-destructive"
              >
                {error}
              </p>
            )}
            <Button
              type="submit"
              className="w-full"
              disabled={pending || !username.trim() || !password}
            >
              {pending ? <LoaderCircle className="animate-spin" /> : null}
              {pending
                ? "Đang đăng nhập…"
                : register
                  ? "Tạo tài khoản sinh viên"
                  : "Vào không gian học tập"}
              {!pending && <ArrowRight />}
            </Button>
          </form>
          <Button
            variant="ghost"
            className="mt-4 w-full"
            disabled={pending}
            onClick={() => {
              setRegister(!register);
              setError("");
            }}
          >
            {register ? "Đã có tài khoản? Đăng nhập" : "Đăng ký sinh viên"}
          </Button>
          <p className="mt-8 text-sm leading-6 text-muted-foreground">
            Giảng viên dùng tài khoản được quản trị viên cấp. Nếu gặp vấn đề
            đăng nhập, hãy liên hệ người phụ trách lớp.
          </p>
        </div>
      </main>
    </div>
  );
}
