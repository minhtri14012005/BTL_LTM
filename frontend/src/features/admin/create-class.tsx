import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { FormField } from "@/components/ui/form";
import { Input } from "@/components/ui/input";
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

export function CreateClass({
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
