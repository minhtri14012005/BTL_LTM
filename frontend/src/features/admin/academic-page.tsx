import { useState, type FormEvent } from "react";
import { useSearchParams, Link } from "react-router-dom";
import { toast } from "sonner";
import { api, post } from "@/lib/api";
import { useResource } from "@/hooks/use-resource";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { FormField } from "@/components/ui/form";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Table } from "@/components/ui/table";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Confirm } from "@/components/ui/confirm";
import { EmptyState, ErrorState, Loading } from "@/components/feedback";
import {
  academicBase,
  adminError,
  loadCatalog,
  offeringLabel,
  type Catalog,
  type Named,
  type Semester,
} from "./admin-api";
import { AdminFrame, NativeSelect, FormError } from "./admin-shared";

const sections = {
  years: "Năm học",
  semesters: "Học kỳ",
  subjects: "Môn học",
  offerings: "Môn theo học kỳ",
};
type Section = keyof typeof sections;
export function AcademicPage() {
  const resource = useResource(loadCatalog, []);
  const [params, setParams] = useSearchParams();
  const tab = (params.get("step") || "years") as Section;
  const current = Object.hasOwn(sections, tab) ? tab : "years";
  return (
    <AdminFrame
      title="Tổ chức đào tạo"
      description="Thiết lập theo thứ tự: năm học → học kỳ → môn học → mở môn theo học kỳ. Sau đó tạo lớp và phân công giảng viên."
      reload={resource.reload}
    >
      {resource.loading ? (
        <Loading />
      ) : resource.error ? (
        <ErrorState message={resource.error} retry={resource.reload} />
      ) : (
        resource.data && (
          <Tabs
            value={current}
            onValueChange={(value) => setParams({ step: value })}
          >
            <div className="overflow-x-auto pb-1">
              <TabsList aria-label="Các bước đào tạo">
                {Object.entries(sections).map(([key, label], i) => (
                  <TabsTrigger key={key} value={key}>
                    {i + 1}. {label}
                  </TabsTrigger>
                ))}
              </TabsList>
            </div>
            {(["years", "semesters", "subjects"] as const).map((key) => (
              <TabsContent key={key} value={key}>
                <MetadataPanel
                  section={key}
                  catalog={resource.data!}
                  saved={resource.reload}
                />
              </TabsContent>
            ))}
            <TabsContent value="offerings">
              <OfferingsPanel catalog={resource.data} saved={resource.reload} />
            </TabsContent>
          </Tabs>
        )
      )}
    </AdminFrame>
  );
}
function MetadataPanel({
  section,
  catalog,
  saved,
}: {
  section: Exclude<Section, "offerings">;
  catalog: Catalog;
  saved: () => void;
}) {
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
    <div className="grid items-start gap-6 xl:grid-cols-[minmax(0,1fr)_minmax(0,1.5fr)]">
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
                : `Tạo ${label} bằng biểu mẫu bên cạnh để tiếp tục tổ chức đào tạo.`
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
function OfferingsPanel({
  catalog,
  saved,
}: {
  catalog: Catalog;
  saved: () => void;
}) {
  const [year, setYear] = useState(""),
    [semester, setSemester] = useState(""),
    [subject, setSubject] = useState("");
  const [pending, setPending] = useState(false),
    [error, setError] = useState("");
  async function submit(event: FormEvent) {
    event.preventDefault();
    setPending(true);
    setError("");
    try {
      await post(`${academicBase}/offerings`, {
        semesterId: Number(semester),
        subjectId: Number(subject),
      });
      toast.success("Đã mở môn theo học kỳ.");
      saved();
    } catch (error) {
      setError(adminError(error));
    } finally {
      setPending(false);
    }
  }
  return (
    <div className="grid items-start gap-6 xl:grid-cols-2">
      <Card>
        <CardHeader>
          <CardTitle>Mở môn theo học kỳ</CardTitle>
          <p className="text-sm leading-6 text-muted-foreground">
            Một môn có thể được tổ chức ở nhiều học kỳ. Mỗi tổ chức môn có các
            lớp riêng.
          </p>
        </CardHeader>
        <CardContent>
          <form className="space-y-4" onSubmit={submit}>
            <fieldset disabled={pending} className="min-w-0 space-y-4">
              <FormField label="Năm học">
                {(props) => (
                  <NativeSelect
                    {...props}
                    required
                    value={year}
                    onChange={(e) => {
                      setYear(e.target.value);
                      setSemester("");
                    }}
                  >
                    <option value="">Chọn năm học</option>
                    {catalog.years.map((y) => (
                      <option key={y.id} value={y.id}>
                        {y.name}
                      </option>
                    ))}
                  </NativeSelect>
                )}
              </FormField>
              <FormField label="Học kỳ">
                {(props) => (
                  <NativeSelect
                    {...props}
                    required
                    value={semester}
                    disabled={!year || pending}
                    onChange={(e) => setSemester(e.target.value)}
                  >
                    <option value="">Chọn học kỳ</option>
                    {catalog.semesters
                      .filter((s) => s.academicYearId === Number(year))
                      .map((s) => (
                        <option key={s.id} value={s.id}>
                          {s.name}
                        </option>
                      ))}
                  </NativeSelect>
                )}
              </FormField>
              <FormField label="Môn học">
                {(props) => (
                  <NativeSelect
                    {...props}
                    required
                    value={subject}
                    onChange={(e) => setSubject(e.target.value)}
                  >
                    <option value="">Chọn môn học</option>
                    {catalog.subjects.map((s) => (
                      <option key={s.id} value={s.id}>
                        {s.name} ({s.code})
                      </option>
                    ))}
                  </NativeSelect>
                )}
              </FormField>
              <FormError message={error} />
              <Button disabled={pending || !semester || !subject} type="submit">
                {pending ? "Đang lưu…" : "Mở môn theo học kỳ"}
              </Button>
            </fieldset>
          </form>
        </CardContent>
      </Card>
      <div className="space-y-4">
        <h2 className="text-lg font-semibold">
          Các môn đã tổ chức ({catalog.offerings.length})
        </h2>
        {!catalog.offerings.length ? (
          <EmptyState
            title="Chưa mở môn nào"
            description="Cần có năm học, học kỳ và môn học trước khi mở môn."
          />
        ) : (
          <Card className="overflow-hidden">
            <Table>
              <thead>
                <tr>
                  <th>Năm học / học kỳ / môn</th>
                  <th>Số lớp</th>
                </tr>
              </thead>
              <tbody>
                {catalog.offerings.map((o) => (
                  <tr key={o.id}>
                    <td>{offeringLabel(catalog, o.id)}</td>
                    <td>
                      {
                        catalog.classes.filter((c) => c.offeringId === o.id)
                          .length
                      }
                    </td>
                  </tr>
                ))}
              </tbody>
            </Table>
          </Card>
        )}
        <Button asChild variant="outline">
          <Link to="/app/admin/classes">Tiếp tục: Tạo lớp & phân công</Link>
        </Button>
      </div>
    </div>
  );
}
