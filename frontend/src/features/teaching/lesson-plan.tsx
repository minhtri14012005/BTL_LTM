import { api } from "@/lib/api";
import { useResource } from "@/hooks/use-resource";
import { useState, type FormEvent, type ReactNode } from "react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { FormField } from "@/components/ui/form";
import { Card } from "@/components/ui/card";
import { requestId } from "@/lib/request-id";
import {
  modeNames,
  planError,
  cents,
  type Lesson,
  type Mode,
} from "./teaching-api";
import { NativeSelect, FormError } from "./teaching-shared";
export function LessonPlan({
  initial,
  classId,
  children,
  submitLabel,
  lockedModes,
  onSave,
  pending,
  error,
}: {
  initial?: Lesson;
  classId?: number;
  children?: ReactNode;
  submitLabel?: string;
  lockedModes?: number[];
  onSave: (plan: object, banks?: Record<number, number>) => Promise<unknown>;
  pending: boolean;
  error: string;
}) {
  const [title, setTitle] = useState(initial?.title || ""),
    [slots, setSlots] = useState(
      initial?.games.map((g) => ({
        mode: g.mode,
        title: g.title,
        budget: String(g.budget),
      })) || [
        { mode: "LUCKY_WHEEL" as Mode, title: "", budget: "" },
        { mode: "QUIZ" as Mode, title: "", budget: "" },
        { mode: "FILL_BLANK" as Mode, title: "", budget: "" },
      ],
    );
  const [banks, setBanks] = useState<Record<number, number>>({});
  const [validation, setValidation] = useState("");
  function patch(index: number, key: string, value: string) {
    if (key === "mode") setBanks((old) => ({ ...old, [index + 1]: 0 }));
    setSlots((old) =>
      old.map((s, i) => (i === index ? { ...s, [key]: value } : s)),
    );
  }
  async function submit(e: FormEvent) {
    e.preventDefault();
    const error = planError(slots);
    if (error || !title.trim()) {
      setValidation(error || "Cần nhập tiêu đề buổi học.");
      return;
    }
    setValidation("");
    await onSave(
      {
        requestId: requestId(),
        title: title.trim(),
        slots: slots.map((s) => ({
          ...s,
          title: s.title.trim() || modeNames[s.mode],
          budget: Number(s.budget),
        })),
      },
      banks,
    );
  }
  const total =
    slots.reduce(
      (sum, s) =>
        sum + (Number.isFinite(cents(s.budget)) ? cents(s.budget) : 0),
      0,
    ) / 100;
  return (
    <form onSubmit={submit} className="space-y-4">
      <fieldset disabled={pending} className="min-w-0 space-y-4">
        <FormField label="Tên buổi học">
          {(p) => (
            <Input
              {...p}
              required
              maxLength={200}
              value={title}
              onChange={(e) => setTitle(e.target.value)}
            />
          )}
        </FormField>
        {initial && (
          <p className="text-sm text-muted-foreground">
            Loại trò đã có nội dung được khóa để bảo toàn snapshot. Cấu hình số
            câu, thời gian, thử thách và hạn nộp nằm ở cấu hình chung của từng
            trò.
          </p>
        )}
        <div className="grid gap-4 lg:grid-cols-3">
          {slots.map((slot, index) => (
            <Card key={index} className={`space-y-4 p-4 tone-${index}`}>
              <h3 className="font-semibold">
                Trò {index + 1}
                {index === 0 ? " · Bắt buộc Vòng quay" : ""}
              </h3>
              {index > 0 && (
                <FormField label={`Mode trò ${index + 1}`}>
                  {(p) => (
                    <NativeSelect
                      {...p}
                      disabled={
                        !!initial && (lockedModes?.includes(index + 1) ?? true)
                      }
                      value={slot.mode}
                      onChange={(e) => patch(index, "mode", e.target.value)}
                    >
                      {(["QUIZ", "FILL_BLANK", "ASSIGNMENT"] as Mode[]).map(
                        (m) => (
                          <option key={m} value={m}>
                            {modeNames[m]}
                          </option>
                        ),
                      )}
                    </NativeSelect>
                  )}
                </FormField>
              )}
              {!initial && index > 0 && (
                <BankChoice
                  key={slot.mode}
                  mode={slot.mode}
                  classId={classId}
                  position={index + 1}
                  value={banks[index + 1] || 0}
                  change={(id) =>
                    setBanks((old) => ({ ...old, [index + 1]: id }))
                  }
                />
              )}
              <FormField label={`Tên hiển thị (tùy chọn)`}>
                {(p) => (
                  <Input
                    {...p}
                    placeholder="Mặc định dùng tên loại trò"
                    maxLength={200}
                    value={slot.title}
                    onChange={(e) => patch(index, "title", e.target.value)}
                  />
                )}
              </FormField>
              <FormField label={`Ngân sách trò ${index + 1}`}>
                {(p) => (
                  <Input
                    {...p}
                    type="number"
                    required
                    min={0}
                    max={100}
                    step="0.01"
                    value={slot.budget}
                    onChange={(e) => patch(index, "budget", e.target.value)}
                  />
                )}
              </FormField>
            </Card>
          ))}
        </div>
        <p
          className={
            total > 100 ? "font-semibold text-destructive" : "font-semibold"
          }
        >
          Tổng ngân sách: {total.toFixed(2)} / 100 điểm
        </p>
        {children}
        <FormError message={validation || error} />
        <Button type="submit" disabled={pending}>
          {pending
            ? "Đang lưu…"
            : submitLabel || (initial ? "Lưu kế hoạch buổi" : "Tạo buổi học")}
        </Button>
      </fieldset>
    </form>
  );
}

function BankChoice({
  mode,
  value,
  change,
  position,
  classId,
}: {
  mode: Mode;
  value: number;
  change: (id: number) => void;
  position: number;
  classId?: number;
}) {
  const [scope, setScope] = useState("MINE");
  const [page, setPage] = useState(0);
  const r = useResource(
    (signal) =>
      api<{
        items: { id: number; title: string; visibility: string }[];
        totalElements: number;
      }>(`/api/quizzes?mode=${mode}&scope=${scope}&size=100&page=${page}&classId=${classId}`, {
        signal,
      }),
    [mode, scope, page, classId],
  );
  return (
    <div className="space-y-3">
      <FormField label={`Nguồn bộ câu hỏi trò ${position}`}>
        {(p) => (
          <NativeSelect
            {...p}
            value={scope}
            onChange={(e) => {
              setScope(e.target.value);
              setPage(0);
              change(0);
            }}
          >
            <option value="MINE">Bộ của tôi</option>
            <option value="SHARED">Bộ Public</option>
          </NativeSelect>
        )}
      </FormField>
      <FormField label={`Bộ câu hỏi / đề bài trò ${position}`}>
        {(p) => (
          <>
            <NativeSelect
              {...p}
              disabled={r.loading || !!r.error}
              value={r.data?.items.some((b) => b.id === value) ? value : 0}
              onChange={(e) => change(Number(e.target.value))}
            >
              <option value={0}>
                {r.loading
                  ? "Đang tải bộ câu hỏi…"
                  : "Chọn bộ câu hỏi / đề bài"}
              </option>
              {r.data?.items.map((b) => (
                <option key={b.id} value={b.id}>
                  {b.title} · {b.visibility}
                </option>
              ))}
            </NativeSelect>
            {r.error && <FormError message={r.error} />}
            {!r.loading && !r.error && !r.data?.items.length && (
              <p className="text-sm text-muted-foreground">
                Chưa có bộ {modeNames[mode]} trong nguồn này. Hãy tạo trong Ngân
                hàng nội dung hoặc chọn nguồn Public.
              </p>
            )}
            {!!r.data && r.data.totalElements > 100 && (
              <div className="flex gap-2">
                <Button
                  type="button"
                  variant="outline"
                  disabled={!page}
                  onClick={() => {
                    setPage((p) => p - 1);
                    change(0);
                  }}
                >
                  Trang trước
                </Button>
                <Button
                  type="button"
                  variant="outline"
                  disabled={(page + 1) * 100 >= r.data.totalElements}
                  onClick={() => {
                    setPage((p) => p + 1);
                    change(0);
                  }}
                >
                  Trang sau
                </Button>
              </div>
            )}
          </>
        )}
      </FormField>
    </div>
  );
}
