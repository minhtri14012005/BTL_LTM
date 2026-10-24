import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { FormField } from "@/components/ui/form";
import { Input } from "@/components/ui/input";
import { api } from "@/lib/api";
import { useState, type FormEvent } from "react";
import { Link } from "react-router-dom";
import { toast } from "sonner";
import {
  academicBase,
  adminError,
  offeringLabel,
  type AdminUser,
  type Catalog,
} from "./admin-api";
import { FormError, NativeSelect } from "./admin-shared";

export function AssignClasses({
  catalog,
  saved,
}: {
  catalog: Catalog & { teachers: AdminUser[] };
  saved: () => void;
}) {
  const [teacher, setTeacher] = useState<AdminUser | null>(null),
    [selected, setSelected] = useState<number[]>([]),
    [classQuery, setClassQuery] = useState("");
  const [pending, setPending] = useState(false),
    [error, setError] = useState(""),
    [applied, setApplied] = useState<number[]>([]);
  async function submit(event: FormEvent) {
    event.preventDefault();
    if (!teacher || !selected.length || pending) return;
    setPending(true);
    setError("");
    const completed: number[] = [];
    try {
      for (const id of selected) {
        await api(`${academicBase}/classes/${id}/teachers/${teacher.id}`, {
          method: "PUT",
        });
        completed.push(id);
      }
      toast.success(
        `Đã phân công ${teacher.displayName} vào ${completed.length} lớp.`,
      );
      saved();
    } catch (error) {
      setApplied((previous) => [...previous, ...completed]);
      setSelected((previous) =>
        previous.filter((id) => !completed.includes(id)),
      );
      setError(
        `${completed.length ? `Đã lưu ${completed.length} lớp; các lớp còn lại chưa được xác nhận. ` : ""}${adminError(error)}`,
      );
    } finally {
      setPending(false);
    }
  }
  return (
    <Card>
      <CardHeader>
        <CardTitle>Phân công giảng viên</CardTitle>
        <p className="text-sm leading-6 text-muted-foreground">
          Chọn một giảng viên rồi đánh dấu một hoặc nhiều lớp. Các phân công đã
          có được giữ nguyên.
        </p>
      </CardHeader>
      <CardContent className="space-y-4">
        <FormField label="Giảng viên được phân công">
          {(props) => (
            <NativeSelect
              {...props}
              value={teacher?.id || ""}
              disabled={pending}
              onChange={(e) => {
                setTeacher(
                  catalog.teachers.find(
                    (t) => t.id === Number(e.target.value),
                  ) || null,
                );
                setSelected([]);
                setApplied([]);
              }}
            >
              <option value="">Chọn giảng viên</option>
              {catalog.teachers.map((t) => (
                <option key={t.id} value={t.id}>
                  {t.displayName} ({t.username})
                </option>
              ))}
            </NativeSelect>
          )}
        </FormField>
        {!catalog.teachers.length && (
          <p className="text-sm text-muted-foreground">
            Chưa có giảng viên đang hoạt động hợp lệ.{" "}
            <Link
              className="text-primary underline"
              to="/app/admin/users?role=GIANG_VIEN"
            >
              Cấp tài khoản giảng viên
            </Link>
          </p>
        )}
        <form className="space-y-4" onSubmit={submit}>
          <fieldset disabled={pending} className="min-w-0 space-y-4">
            <Input
              aria-label="Lọc lớp để phân công"
              placeholder="Lọc tên hoặc mã lớp…"
              value={classQuery}
              onChange={(e) => setClassQuery(e.target.value)}
            />
            <div className="max-h-64 space-y-2 overflow-y-auto rounded-xl border p-3">
              {catalog.classes
                .filter((c) =>
                  `${c.code} ${c.name}`
                    .toLocaleLowerCase("vi")
                    .includes(classQuery.toLocaleLowerCase("vi")),
                )
                .map((c) => {
                  const assigned =
                    !!teacher &&
                    (c.teacherIds.includes(teacher.id) ||
                      applied.includes(c.id));
                  return (
                    <label
                      key={c.id}
                      className="flex min-h-11 cursor-pointer items-start gap-3 rounded-lg p-2 hover:bg-muted"
                    >
                      <input
                        type="checkbox"
                        className="mt-1 size-4 shrink-0 accent-primary"
                        checked={assigned || selected.includes(c.id)}
                        disabled={!teacher || assigned || pending}
                        onChange={(e) =>
                          setSelected((ids) =>
                            e.target.checked
                              ? [...ids, c.id]
                              : ids.filter((id) => id !== c.id),
                          )
                        }
                      />
                      <span className="min-w-0 text-sm leading-6">
                        <span className="block font-medium">{c.name}</span>
                        <span className="text-xs text-muted-foreground">
                          {c.code} · {offeringLabel(catalog, c.offeringId)}
                          {assigned ? " · Đã phân công" : ""}
                        </span>
                      </span>
                    </label>
                  );
                })}
              {!catalog.classes.some((c) =>
                `${c.code} ${c.name}`
                  .toLocaleLowerCase("vi")
                  .includes(classQuery.toLocaleLowerCase("vi")),
              ) && (
                <p className="text-sm text-muted-foreground">
                  {catalog.classes.length
                    ? "Không tìm thấy lớp phù hợp."
                    : "Hãy tạo lớp học trước."}
                </p>
              )}
            </div>
            <FormError message={error} />
            <Button
              type="submit"
              disabled={pending || !teacher || !selected.length}
            >
              {pending ? "Đang phân công…" : `Phân công ${selected.length} lớp`}
            </Button>
          </fieldset>
        </form>
      </CardContent>
    </Card>
  );
}
