import { useState, type FormEvent } from "react";
import { Link } from "react-router-dom";
import { toast } from "sonner";
import { api, post } from "@/lib/api";
import { useResource } from "@/hooks/use-resource";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { FormField } from "@/components/ui/form";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Table } from "@/components/ui/table";
import { EmptyState, ErrorState, Loading } from "@/components/feedback";
import {
  academicBase,
  adminError,
  loadCatalog,
  offeringLabel,
  usersUrl,
  type AdminUser,
  type Catalog,
  type Page,
} from "./admin-api";
import {
  AdminFrame,
  NativeSelect,
  FormError,
  Pagination,
} from "./admin-shared";

export function AdminClassesPage() {
  const resource = useResource(loadCatalog, []);
  const [query, setQuery] = useState("");
  return (
    <AdminFrame
      title="Lớp học & phân công"
      description="Mỗi lớp gắn với một môn trong học kỳ và có số buổi bắt buộc. Bạn có thể phân công một giảng viên vào nhiều lớp."
      reload={resource.reload}
    >
      {resource.loading ? (
        <Loading />
      ) : resource.error ? (
        <ErrorState message={resource.error} retry={resource.reload} />
      ) : (
        resource.data && (
          <>
            <div className="grid items-start gap-6 xl:grid-cols-2">
              <CreateClass catalog={resource.data} saved={resource.reload} />
              <AssignClasses catalog={resource.data} saved={resource.reload} />
            </div>
            <section className="space-y-4">
              <div className="flex flex-wrap items-center justify-between gap-3">
                <h2 className="text-xl font-semibold">
                  Danh sách lớp ({resource.data.classes.length})
                </h2>
                <Input
                  className="sm:max-w-sm"
                  aria-label="Tìm lớp quản lý"
                  placeholder="Tìm tên, mã lớp hoặc môn…"
                  value={query}
                  onChange={(e) => setQuery(e.target.value)}
                />
              </div>
              {!resource.data.classes.length ? (
                <EmptyState
                  title="Chưa có lớp học"
                  description="Mở môn theo học kỳ, sau đó tạo lớp và đặt số buổi bắt buộc."
                />
              ) : (
                <Card className="overflow-hidden">
                  <Table>
                    <thead>
                      <tr>
                        <th>Lớp</th>
                        <th>Môn / học kỳ</th>
                        <th>Số buổi bắt buộc</th>
                        <th>Phân công</th>
                      </tr>
                    </thead>
                    <tbody>
                      {resource.data.classes
                        .filter((c) =>
                          `${c.code} ${c.name} ${offeringLabel(resource.data!, c.offeringId)}`
                            .toLocaleLowerCase("vi")
                            .includes(query.toLocaleLowerCase("vi")),
                        )
                        .map((c) => (
                          <tr key={c.id}>
                            <td>
                              <Link
                                className="font-semibold text-primary underline-offset-4 hover:underline"
                                to={`/app/admin/classes/${c.id}`}
                              >
                                {c.name}
                              </Link>
                              <p className="mt-1 text-xs text-muted-foreground">
                                {c.code}
                              </p>
                            </td>
                            <td>
                              {offeringLabel(resource.data!, c.offeringId)}
                            </td>
                            <td>{c.requiredLessons}</td>
                            <td>
                              {c.teacherIds.length
                                ? `${c.teacherIds.length} giảng viên`
                                : "Chưa phân công"}
                            </td>
                          </tr>
                        ))}
                    </tbody>
                  </Table>
                </Card>
              )}
            </section>
          </>
        )
      )}
    </AdminFrame>
  );
}
function CreateClass({
  catalog,
  saved,
}: {
  catalog: Catalog;
  saved: () => void;
}) {
  const [offering, setOffering] = useState(""),
    [code, setCode] = useState(""),
    [name, setName] = useState(""),
    [required, setRequired] = useState("");
  const [pending, setPending] = useState(false),
    [error, setError] = useState("");
  async function submit(event: FormEvent) {
    event.preventDefault();
    if (!name.trim()) {
      setError("Tên lớp không được chỉ gồm khoảng trắng.");
      return;
    }
    const n = Number(required);
    if (!Number.isSafeInteger(n) || n < 1 || n > 2147483647) {
      setError("Số buổi phải là số nguyên dương hợp lệ.");
      return;
    }
    setPending(true);
    setError("");
    try {
      await post(`${academicBase}/classes`, {
        offeringId: Number(offering),
        code,
        name: name.trim(),
        requiredLessons: n,
      });
      toast.success("Đã tạo lớp và phòng cố định.");
      saved();
    } catch (error) {
      setError(adminError(error));
    } finally {
      setPending(false);
    }
  }
  return (
    <Card>
      <CardHeader>
        <CardTitle>Tạo lớp học</CardTitle>
      </CardHeader>
      <CardContent>
        <form onSubmit={submit}>
          <fieldset disabled={pending} className="min-w-0 space-y-4">
            <FormField label="Môn theo học kỳ">
              {(props) => (
                <NativeSelect
                  {...props}
                  required
                  value={offering}
                  onChange={(e) => setOffering(e.target.value)}
                >
                  <option value="">Chọn môn theo học kỳ</option>
                  {catalog.offerings.map((o) => (
                    <option key={o.id} value={o.id}>
                      {offeringLabel(catalog, o.id)}
                    </option>
                  ))}
                </NativeSelect>
              )}
            </FormField>
            {!catalog.offerings.length && (
              <p className="text-sm leading-6 text-muted-foreground">
                Chưa có môn được tổ chức.{" "}
                <Link
                  className="text-primary underline"
                  to="/app/admin/academic?step=offerings"
                >
                  Mở môn theo học kỳ
                </Link>{" "}
                trước khi tạo lớp.
              </p>
            )}
            <div className="grid gap-4 sm:grid-cols-2">
              <FormField label="Mã lớp">
                {(props) => (
                  <Input
                    {...props}
                    required
                    maxLength={40}
                    pattern="[A-Za-z0-9][A-Za-z0-9_-]{0,39}"
                    value={code}
                    onChange={(e) => setCode(e.target.value)}
                  />
                )}
              </FormField>
              <FormField
                label="Số buổi bắt buộc"
                hint="Lớp phải tổ chức hoàn tất đủ số buổi này."
              >
                {(props) => (
                  <Input
                    {...props}
                    type="number"
                    required
                    min={1}
                    max={2147483647}
                    step={1}
                    value={required}
                    onChange={(e) => setRequired(e.target.value)}
                  />
                )}
              </FormField>
            </div>
            <FormField label="Tên lớp">
              {(props) => (
                <Input
                  {...props}
                  required
                  maxLength={120}
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                />
              )}
            </FormField>
            <FormError message={error} />
            <Button type="submit" disabled={pending || !offering}>
              {pending ? "Đang tạo…" : "Tạo lớp học"}
            </Button>
          </fieldset>
        </form>
      </CardContent>
    </Card>
  );
}
function AssignClasses({
  catalog,
  saved,
}: {
  catalog: Catalog;
  saved: () => void;
}) {
  const [query, setQuery] = useState(""),
    [search, setSearch] = useState(""),
    [page, setPage] = useState(0);
  const teachers = useResource(
    (signal) =>
      api<Page<AdminUser>>(usersUrl("GIANG_VIEN", search, page, "ACTIVE"), {
        signal,
      }),
    [search, page],
  );
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
        {teachers.loading ? (
          <p role="status" className="text-sm text-muted-foreground">
            Đang tải giảng viên…
          </p>
        ) : teachers.error ? (
          <ErrorState message={teachers.error} retry={teachers.reload} />
        ) : (
          teachers.data && (
            <>
              <FormField label="Giảng viên được phân công">
                {(props) => (
                  <NativeSelect
                    {...props}
                    value={teacher?.id || ""}
                    disabled={pending}
                    onChange={(e) => {
                      setTeacher(
                        teachers.data!.items.find(
                          (t) => t.id === Number(e.target.value),
                        ) || null,
                      );
                      setSelected([]);
                      setApplied([]);
                    }}
                  >
                    <option value="">Chọn giảng viên</option>
                    {teacher &&
                      !teachers.data!.items.some(
                        (t) => t.id === teacher.id,
                      ) && (
                        <option value={teacher.id}>
                          {teacher.displayName} ({teacher.username})
                        </option>
                      )}
                    {teachers
                      .data!.items.filter((t) => !t.banned)
                      .map((t) => (
                        <option key={t.id} value={t.id}>
                          {t.displayName} ({t.username})
                        </option>
                      ))}
                  </NativeSelect>
                )}
              </FormField>
              <Pagination
                page={page}
                total={teachers.data.totalElements}
                onChange={setPage}
              />
            </>
          )
        )}
        {!teachers.loading &&
          !teachers.error &&
          !teachers.data?.totalElements && (
            <p className="text-sm leading-6 text-muted-foreground">
              Chưa tìm thấy giảng viên hoạt động.{" "}
              <Link
                className="text-primary underline"
                to="/app/admin/users?role=GIANG_VIEN"
              >
                Cấp tài khoản giảng viên
              </Link>{" "}
              nếu cần.
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
              {!catalog.classes.length && (
                <p className="text-sm text-muted-foreground">
                  Hãy tạo lớp học trước.
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
