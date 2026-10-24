import { EmptyState } from "@/components/feedback";
import { FormDialog } from "@/components/page-layout";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Confirm } from "@/components/ui/confirm";
import { FormField } from "@/components/ui/form";
import { Input } from "@/components/ui/input";
import { Table } from "@/components/ui/table";
import { api } from "@/lib/api";
import { useState, type FormEvent } from "react";
import { toast } from "sonner";
import {
  academicBase,
  adminError,
  type Catalog,
  type Named,
  type Semester,
} from "./admin-api";
import { FormError, NativeSelect } from "./admin-shared";

const sections = {
  years: "Năm học",
  semesters: "Học kỳ",
  subjects: "Môn học",
  offerings: "Môn theo học kỳ",
};
type Section = keyof typeof sections;
export function MetadataPanel({
  section,
  catalog,
  saved,
}: {
  section: Exclude<Section, "offerings">;
  catalog: Catalog;
  saved: () => void;
}) {
  const [open, setOpen] = useState(false);
  const [edit, setEdit] = useState<Named | null>(null),
    [remove, setRemove] = useState<Named | null>(null);
  const [code, setCode] = useState(""),
    [name, setName] = useState(""),
    [year, setYear] = useState("");
  const [pending, setPending] = useState(false),
    [error, setError] = useState(""),
    [query, setQuery] = useState("");
  const rows = catalog[section].filter((r) =>
    `${r.name} ${r.code}`
      .toLocaleLowerCase("vi")
      .includes(query.toLocaleLowerCase("vi")),
  );
  const label = sections[section].toLocaleLowerCase("vi");
  function reset() {
    setOpen(false);
    setEdit(null);
    setCode("");
    setName("");
    setError("");
  }
  async function submit(event: FormEvent) {
    event.preventDefault();
    if (!name.trim()) {
      setError("Tên không được chỉ gồm khoảng trắng.");
      return;
    }
    setPending(true);
    setError("");
    try {
      const body = {
        code,
        name: name.trim(),
        ...(section === "semesters" ? { academicYearId: Number(year) } : {}),
      };
      await api(`${academicBase}/${section}${edit ? `/${edit.id}` : ""}`, {
        method: edit ? "PUT" : "POST",
        body: JSON.stringify(body),
      });
      toast.success(`Đã ${edit ? "cập nhật" : "tạo"} ${label}.`);
      reset();
      saved();
    } catch (error) {
      setError(adminError(error));
    } finally {
      setPending(false);
    }
  }
  async function deleteRow() {
    if (!remove || pending) return;
    setPending(true);
    setError("");
    try {
      await api(`${academicBase}/${section}/${remove.id}`, {
        method: "DELETE",
      });
      toast.success(`Đã xóa ${label}.`);
      setRemove(null);
      saved();
    } catch (error) {
      setRemove(null);
      setError(adminError(error));
    } finally {
      setPending(false);
    }
  }
  return (
    <div className="space-y-5">
      <Button
        onClick={() => {
          setEdit(null);
          setCode("");
          setName("");
          setError("");
          setOpen(true);
        }}
      >
        Tạo {label}
      </Button>
      <FormDialog
        open={open}
        onOpenChange={setOpen}
        title={edit ? `Sửa ${label}` : `Tạo ${label}`}
      >
        <Card>
          <CardHeader>
            <CardTitle>
              {edit ? `Sửa ${label}: ${edit.name}` : `Tạo ${label}`}
            </CardTitle>
          </CardHeader>
          <CardContent>
            <form onSubmit={submit} className="space-y-4">
              <fieldset disabled={pending} className="min-w-0 space-y-4">
                {section === "semesters" && (
                  <FormField label="Năm học">
                    {(props) => (
                      <NativeSelect
                        {...props}
                        required
                        value={year}
                        disabled={!!edit || pending}
                        onChange={(e) => setYear(e.target.value)}
                      >
                        <option value="">Chọn năm học</option>
                        {catalog.years.map((y) => (
                          <option key={y.id} value={y.id}>
                            {y.name} ({y.code})
                          </option>
                        ))}
                      </NativeSelect>
                    )}
                  </FormField>
                )}
                <FormField
                  label={`Mã ${label}`}
                  hint="Tối đa 40 ký tự: chữ, số, gạch ngang hoặc gạch dưới."
                >
                  {(props) => (
                    <Input
                      {...props}
                      required
                      pattern="[A-Za-z0-9][A-Za-z0-9_-]{0,39}"
                      maxLength={40}
                      value={code}
                      onChange={(e) => setCode(e.target.value)}
                    />
                  )}
                </FormField>
                <FormField label={`Tên ${label}`}>
                  {(props) => (
                    <Input
                      {...props}
                      required
                      maxLength={section === "subjects" ? 200 : 120}
                      value={name}
                      onChange={(e) => setName(e.target.value)}
                    />
                  )}
                </FormField>
                <FormError message={error} />
                <div className="flex flex-wrap gap-2">
                  <Button
                    type="submit"
                    disabled={pending || (section === "semesters" && !year)}
                  >
                    {pending
                      ? "Đang lưu…"
                      : edit
                        ? "Lưu thay đổi"
                        : `Tạo ${label}`}
                  </Button>
                  {edit && (
                    <Button type="button" variant="outline" onClick={reset}>
                      Hủy sửa
                    </Button>
                  )}
                </div>
                {section === "semesters" && !catalog.years.length && (
                  <p className="text-sm text-muted-foreground">
                    Hãy tạo năm học ở bước 1 trước.
                  </p>
                )}
              </fieldset>
            </form>
          </CardContent>
        </Card>
      </FormDialog>
      {!open && <FormError message={error} />}
      <div className="space-y-4">
        <h2 className="text-lg font-semibold">
          Danh sách {label} ({catalog[section].length})
        </h2>
        {catalog[section].length > 0 && (
          <Input
            aria-label={`Tìm ${label}`}
            placeholder={`Tìm tên hoặc mã ${label}…`}
            value={query}
            onChange={(e) => setQuery(e.target.value)}
          />
        )}
        {!rows.length ? (
          <EmptyState
            title={query ? "Không tìm thấy dữ liệu" : `Chưa có ${label}`}
            description={
              query
                ? "Thử tên hoặc mã khác."
                : `Nhấn Tạo ${label} để tiếp tục tổ chức đào tạo.`
            }
          />
        ) : (
          <Card className="overflow-hidden">
            <Table>
              <thead>
                <tr>
                  <th>Mã</th>
                  <th>Tên</th>
                  {section === "semesters" && <th>Năm học</th>}
                  <th>Thao tác</th>
                </tr>
              </thead>
              <tbody>
                {rows.map((row) => (
                  <tr key={row.id}>
                    <td>{row.code}</td>
                    <td>{row.name}</td>
                    {section === "semesters" && (
                      <td>
                        {
                          catalog.years.find(
                            (y) => y.id === (row as Semester).academicYearId,
                          )?.name
                        }
                      </td>
                    )}
                    <td>
                      <div className="flex gap-1">
                        <Button
                          size="sm"
                          variant="ghost"
                          disabled={pending}
                          aria-label={`Sửa ${row.name}`}
                          onClick={() => {
                            setOpen(true);
                            setEdit(row);
                            setCode(row.code);
                            setName(row.name);
                            setError("");
                            setYear(
                              String((row as Semester).academicYearId || ""),
                            );
                          }}
                        >
                          Sửa
                        </Button>
                        <Button
                          size="sm"
                          variant="ghost"
                          disabled={pending}
                          aria-label={`Xóa ${row.name}`}
                          onClick={() => setRemove(row)}
                        >
                          Xóa
                        </Button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </Table>
          </Card>
        )}
      </div>
      <Confirm
        open={!!remove}
        onOpenChange={(open) => {
          if (!pending && !open) setRemove(null);
        }}
        title={`Xóa ${label}?`}
        description={`Xóa “${remove?.name}” (${remove?.code}). Hệ thống sẽ từ chối nếu dữ liệu đã có liên kết; không xóa lịch sử học tập.`}
        pending={pending}
        onConfirm={deleteRow}
      />
    </div>
  );
}
