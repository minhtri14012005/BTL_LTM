import { useState } from "react";
import { FormDialog } from "@/components/page-layout";
import { Button } from "@/components/ui/button";
import { Confirm } from "@/components/ui/confirm";
import { FormField } from "@/components/ui/form";
import { Input } from "@/components/ui/input";
import { api, post } from "@/lib/api";
import { toast } from "sonner";
import { adminError, type AdminUser } from "./admin-api";
import { FormError } from "./admin-shared";

export function TeacherActions({
  user,
  saved,
}: {
  user: AdminUser;
  saved: () => void;
}) {
  const [mode, setMode] = useState<"edit" | "password" | "delete" | null>(null);
  const [name, setName] = useState(user.displayName),
    [username, setUsername] = useState(user.username),
    [password, setPassword] = useState("");
  const [pending, setPending] = useState(false),
    [error, setError] = useState("");
  function open(value: typeof mode) {
    setName(user.displayName);
    setUsername(user.username);
    setPassword("");
    setError("");
    setMode(value);
  }
  async function submit() {
    if (pending || !mode) return;
    setPending(true);
    setError("");
    try {
      const url = `/api/admin/teachers/${user.id}`;
      if (mode === "edit")
        await api(url, {
          method: "PUT",
          body: JSON.stringify({
            expectedVersion: user.banVersion,
            username,
            hoTen: name.trim(),
          }),
        });
      else
        await post(`${url}/${mode}`, {
          expectedVersion: user.banVersion,
          ...(mode === "password" ? { password } : {}),
        });
      setPassword("");
      setMode(null);
      saved();
      toast.success(
        mode === "delete"
          ? "Đã xóa tài khoản giảng viên. Lớp và lịch sử được giữ nguyên."
          : "Đã cập nhật tài khoản giảng viên.",
      );
    } catch (e) {
      setError(adminError(e));
    } finally {
      setPending(false);
    }
  }
  if (user.deletedAtMs) return null;
  return (
    <>
      <div className="flex flex-wrap gap-2">
        <Button size="sm" variant="outline" onClick={() => open("edit")}>
          Chỉnh sửa
        </Button>
        <Button size="sm" variant="outline" onClick={() => open("password")}>
          Đặt lại mật khẩu
        </Button>
        <Button size="sm" variant="destructive" onClick={() => open("delete")}>
          Xóa tài khoản
        </Button>
      </div>
      <FormDialog
        open={mode === "edit" || mode === "password"}
        onOpenChange={(value) => {
          if (!value && !pending) open(null);
        }}
        title={
          mode === "password"
            ? "Đặt lại mật khẩu giảng viên"
            : "Chỉnh sửa giảng viên"
        }
      >
        <form
          className="space-y-4"
          onSubmit={(e) => {
            e.preventDefault();
            void submit();
          }}
        >
          <p className="text-sm text-muted-foreground">
            {user.displayName} · {user.username}
          </p>
          {mode === "edit" ? (
            <>
              <FormField label="Họ tên">
                {(p) => (
                  <Input
                    {...p}
                    required
                    maxLength={100}
                    value={name}
                    disabled={pending}
                    onChange={(e) => setName(e.target.value)}
                  />
                )}
              </FormField>
              <FormField label="Username">
                {(p) => (
                  <Input
                    {...p}
                    required
                    pattern="[A-Za-z0-9_]{3,32}"
                    value={username}
                    disabled={pending}
                    onChange={(e) => setUsername(e.target.value)}
                  />
                )}
              </FormField>
            </>
          ) : (
            <>
              <FormField label="Mật khẩu mới">
                {(p) => (
                  <Input
                    {...p}
                    type="password"
                    autoComplete="new-password"
                    required
                    minLength={8}
                    maxLength={72}
                    value={password}
                    disabled={pending}
                    onChange={(e) => setPassword(e.target.value)}
                  />
                )}
              </FormField>
              <p className="text-sm text-muted-foreground">
                Phiên đăng nhập cũ sẽ bị thu hồi. Mật khẩu tối thiểu 8 ký tự,
                tối đa 72 byte UTF-8.
              </p>
            </>
          )}
          <FormError message={error} />
          <Button type="submit" disabled={pending}>
            {pending ? "Đang lưu…" : "Lưu thay đổi"}
          </Button>
        </form>
      </FormDialog>
      <Confirm
        open={mode === "delete"}
        onOpenChange={(value) => {
          if (!value && !pending) open(null);
        }}
        title="Xóa tài khoản giảng viên?"
        description={`${user.displayName} · ${user.username}. Tài khoản sẽ mất quyền đăng nhập và phân công. Các lớp, sinh viên, buổi học, nội dung và kết quả được giữ nguyên để phân công người tiếp quản.`}
        pending={pending}
        error={error}
        onConfirm={() => void submit()}
      />
    </>
  );
}
