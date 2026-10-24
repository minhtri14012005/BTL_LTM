import { EmptyState } from "@/components/feedback";
import { FormDialog } from "@/components/page-layout";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { FormField } from "@/components/ui/form";
import { Table } from "@/components/ui/table";
import { post } from "@/lib/api";
import { useState, type FormEvent } from "react";
import { Link } from "react-router-dom";
import { toast } from "sonner";
import {
  academicBase,
  adminError,
  offeringLabel,
  type Catalog,
} from "./admin-api";
import { FormError, NativeSelect } from "./admin-shared";

export function OfferingsPanel({
  catalog,
  saved,
}: {
  catalog: Catalog;
  saved: () => void;
}) {
  const [open, setOpen] = useState(false);
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
      setOpen(false);
      saved();
    } catch (error) {
      setError(adminError(error));
    } finally {
      setPending(false);
    }
  }
  return (
    <div className="space-y-5">
      <Button onClick={() => setOpen(true)}>Mở môn theo học kỳ</Button>
      <FormDialog open={open} onOpenChange={setOpen} title="Mở môn theo học kỳ">
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
                <Button
                  disabled={pending || !semester || !subject}
                  type="submit"
                >
                  {pending ? "Đang lưu…" : "Mở môn theo học kỳ"}
                </Button>
              </fieldset>
            </form>
          </CardContent>
        </Card>
      </FormDialog>
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
