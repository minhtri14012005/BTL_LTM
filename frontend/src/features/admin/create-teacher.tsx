import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { FormField } from "@/components/ui/form";
import { Input } from "@/components/ui/input";
import { post } from "@/lib/api";
import type { User } from "@/lib/types";
import { useState, type FormEvent } from "react";
import { toast } from "sonner";
import { adminError } from "./admin-api";
import { FormError } from "./admin-shared";

export function CreateTeacher({ saved }: { saved: (user: User) => void }) {
  const [username, setUsername] = useState(""),
    [name, setName] = useState(""),
    [password, setPassword] = useState("");
  const [pending, setPending] = useState(false),
    [error, setError] = useState("");
  async function submit(event: FormEvent) {
    event.preventDefault();
    if (!name.trim()) {
      setError("Họ tên không được chỉ gồm khoảng trắng.");
      return;
    }
    if (new TextEncoder().encode(password).length > 72) {
      setError("Mật khẩu không được vượt quá 72 byte UTF-8.");
      return;
    }
    setPending(true);
    setError("");
    try {
      const user = await post<User>("/api/admin/teachers", {
        username,
        hoTen: name.trim(),
        password,
      });
      setPassword("");
      setName("");
      setUsername("");
      toast.success(`Đã cấp tài khoản ${user.username}.`);
      saved(user);
    } catch (error) {
      setError(adminError(error));
    } finally {
      setPending(false);
    }
  }
  return (
    <Card>
      <CardHeader>
        <CardTitle>Cấp tài khoản giảng viên</CardTitle>
        <p className="text-sm text-muted-foreground">
          Nhập mật khẩu ban đầu và bàn giao trực tiếp cho giảng viên.
        </p>
      </CardHeader>
      <CardContent>
        <form onSubmit={submit}>
          <fieldset disabled={pending} className="min-w-0 space-y-4">
            <div className="grid gap-4">
              <FormField
                label="Tên đăng nhập giảng viên"
                hint="3–32 ký tự chữ, số hoặc gạch dưới."
              >
                {(props) => (
                  <Input
                    {...props}
                    required
                    minLength={3}
                    maxLength={32}
                    pattern="[A-Za-z0-9_]{3,32}"
                    autoComplete="off"
                    value={username}
                    onChange={(e) => setUsername(e.target.value)}
                  />
                )}
              </FormField>
              <FormField label="Họ tên giảng viên">
                {(props) => (
                  <Input
                    {...props}
                    required
                    maxLength={100}
                    value={name}
                    onChange={(e) => setName(e.target.value)}
                  />
                )}
              </FormField>
              <FormField label="Mật khẩu ban đầu">
                {(props) => (
                  <Input
                    {...props}
                    required
                    type="password"
                    minLength={8}
                    maxLength={72}
                    autoComplete="new-password"
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                  />
                )}
              </FormField>
            </div>
            <FormError message={error} />
            <Button type="submit" disabled={pending}>
              {pending ? "Đang cấp tài khoản…" : "Cấp tài khoản giảng viên"}
            </Button>
          </fieldset>
        </form>
      </CardContent>
    </Card>
  );
}
