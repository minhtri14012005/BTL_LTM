import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { api } from "@/lib/api";
import { refreshSession, useSession } from "@/lib/session";
import { useState } from "react";
import { FormError, useStudentAction } from "./student-shared";
export function StudentIdentityEditor({ reload }: { reload: () => void }) {
  const { user } = useSession();
  const [name, setName] = useState(user!.hoTen),
    [code, setCode] = useState(user!.maSinhVien || "");
  const action = useStudentAction();
  return (
    <Card className="space-y-4 p-5">
      <h2 className="font-semibold">Cập nhật hồ sơ</h2>
      <form
        className="grid gap-3 sm:grid-cols-2"
        onSubmit={(e) => {
          e.preventDefault();
          if (!name.trim() || !/^[A-Za-z0-9][A-Za-z0-9_-]{0,31}$/.test(code)) {
            action.setError(
              "Nhập họ tên và mã SV gồm 1–32 chữ, số, dấu gạch dưới hoặc gạch ngang.",
            );
            return;
          }
          void action.run(async () => {
            await api("/api/auth/profile", {
              method: "PUT",
              body: JSON.stringify({ hoTen: name.trim(), maSinhVien: code }),
            });
            await refreshSession();
            reload();
          }, "Đã cập nhật hồ sơ.");
        }}
      >
        <label className="space-y-2 text-sm">
          Họ và tên
          <Input
            aria-label="Họ và tên trong hồ sơ"
            required
            maxLength={100}
            value={name}
            disabled={action.pending}
            onChange={(e) => setName(e.target.value)}
          />
        </label>
        <label className="space-y-2 text-sm">
          Mã sinh viên
          <Input
            aria-label="Mã sinh viên trong hồ sơ"
            required
            maxLength={32}
            pattern={"[A-Za-z0-9][A-Za-z0-9_\\-]{0,31}"}
            value={code}
            disabled={action.pending}
            onChange={(e) => setCode(e.target.value)}
          />
        </label>
        <Button type="submit" disabled={action.pending || !name.trim()}>
          Lưu hồ sơ
        </Button>
      </form>
      <FormError message={action.error} />
    </Card>
  );
}
