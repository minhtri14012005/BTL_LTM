import { ServerWheel } from "@/components/server-wheel";
import { useEffect, useState } from "react";
import { post } from "@/lib/api";
import { requestId } from "@/lib/request-id";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Card } from "@/components/ui/card";
import { Confirm } from "@/components/ui/confirm";
import { Table } from "@/components/ui/table";
import { type Snapshot, type Game, cents } from "./teaching-api";
import { ScoreInput, Notice } from "./teaching-shared";
export function WheelControls({
  snapshot,
  game,
  disabled,
  run,
  refresh,
}: {
  snapshot: Snapshot;
  game: Game;
  disabled: boolean;
  run: (work: () => Promise<unknown>, success?: string) => Promise<boolean>;
  refresh: () => Promise<void>;
}) {
  const wheel = snapshot.wheel;
  const eligible = snapshot.participants.filter(
    (p) => p.eligible && p.games.includes(1),
  );
  const [pool, setPool] = useState<number[]>([]),
    [k, setK] = useState("1"),
    [grades, setGrades] = useState<Record<string, string>>({}),
    [batch, setBatch] = useState(""),
    [confirm, setConfirm] = useState(false),
    [spinning, setSpinning] = useState(false),
    [validation, setValidation] = useState("");
  useEffect(() => {
    setGrades(
      Object.fromEntries(
        (wheel?.selected || []).map((id) => [
          id,
          String(wheel?.grades[String(id)] ?? 0),
        ]),
      ),
    );
  }, [wheel?.revision]);
  useEffect(() => {
    const left = (wheel?.spinEndsAtMs || 0) - snapshot.serverTimeMs;
    setSpinning(left > 0);
    const timer = setTimeout(() => setSpinning(false), Math.max(0, left));
    return () => clearTimeout(timer);
  }, [wheel?.spinEndsAtMs, snapshot.serverTimeMs]);
  const dirty =
    !!wheel &&
    wheel.selected.some(
      (id) => Number(grades[id] || 0) !== Number(wheel.grades[id] || 0),
    );
  async function act(action: string, extra: object = {}) {
    await post(`/api/teaching/lessons/${snapshot.lesson.id}/wheel/${action}`, {
      requestId: requestId(),
      revision: wheel?.revision || 0,
      ...extra,
    });
    await refresh();
  }
  return (
    <div className="space-y-5">
      <Notice>
        Chỉ người đã tham gia hợp lệ trò Vòng quay mới được đưa vào tập quay.
        Server chọn k người và một thử thách chung; người hợp lệ trong tập quay
        không bị chọn nhận tối đa ngân sách. Người vắng nhận 0.
      </Notice>
      <div className="grid items-start gap-6 lg:grid-cols-2">
        <div className="space-y-4">
          <ServerWheel
            wheel={wheel}
            serverTimeMs={snapshot.serverTimeMs}
            showCells={false}
          />
          {wheel && (
            <div>
              <details className="mb-4 rounded-xl border p-3">
                <summary className="cursor-pointer text-sm font-medium">
                  Các ô thử thách đã snapshot ({wheel.cells.length})
                </summary>
                <ol className="mt-2 max-h-48 list-inside list-decimal overflow-y-auto text-sm leading-7">
                  {wheel.cells.map((cell, i) => (
                    <li key={i}>{cell}</li>
                  ))}
                </ol>
              </details>
              <h3 className="font-semibold">Sinh viên Server chọn</h3>
              <ul className="mt-2 space-y-2">
                {wheel.selected.map((id) => (
                  <li key={id} className="rounded-xl bg-muted p-3">
                    {snapshot.participants.find((p) => p.userId === id)?.hoTen}{" "}
                    ·{" "}
                    {
                      snapshot.participants.find((p) => p.userId === id)
                        ?.maSinhVien
                    }
                  </li>
                ))}
              </ul>
            </div>
          )}
        </div>
        <div className="space-y-4">
          {!wheel ? (
            <>
              <h3 className="font-semibold">Tập sinh viên tham gia quay</h3>
              <Button
                variant="outline"
                disabled={disabled || !eligible.length}
                onClick={() => setPool(eligible.map((p) => p.userId))}
              >
                Chọn tất cả đủ điều kiện
              </Button>
              {eligible.map((p) => (
                <label
                  key={p.userId}
                  className="flex min-h-11 items-center gap-3 rounded-xl border p-3"
                >
                  <input
                    type="checkbox"
                    disabled={disabled}
                    checked={pool.includes(p.userId)}
                    onChange={(e) =>
                      setPool((ids) =>
                        e.target.checked
                          ? [...ids, p.userId]
                          : ids.filter((id) => id !== p.userId),
                      )
                    }
                  />
                  <span>
                    {p.hoTen} · {p.maSinhVien}
                  </span>
                </label>
              ))}
              {!eligible.length && (
                <p className="text-sm text-muted-foreground">
                  Chưa có sinh viên đăng ký trò. Dùng Làm mới trạng thái sau khi
                  sinh viên tham gia.
                </p>
              )}
              <label className="block space-y-2 text-sm">
                Số người k
                <Input
                  aria-label="Số người k"
                  type="number"
                  min={1}
                  max={pool.length || 1}
                  step={1}
                  value={k}
                  disabled={disabled}
                  onChange={(e) => setK(e.target.value)}
                />
              </label>
              <Button
                disabled={disabled || !pool.length}
                onClick={() => {
                  if (
                    !Number.isInteger(Number(k)) ||
                    Number(k) < 1 ||
                    Number(k) > pool.length
                  ) {
                    setValidation("k phải từ 1 đến số người trong tập quay.");
                    return;
                  }
                  void run(() => act("select", { pool, k: Number(k) }));
                }}
              >
                Server chọn k sinh viên
              </Button>
            </>
          ) : wheel.status === "SELECTED" ? (
            <Button
              disabled={disabled}
              onClick={() => {
                void run(() => act("spin"));
              }}
            >
              Quay một lượt chung
            </Button>
          ) : wheel.status === "SPUN" ? (
            <>
              <h3 className="font-semibold">Chấm những người được chọn</h3>
              <ScoreInput
                label="Điểm đồng loạt"
                value={batch}
                onChange={setBatch}
                max={game.budget}
                disabled={disabled}
              />
              <Button
                variant="outline"
                disabled={
                  disabled ||
                  !Number.isFinite(cents(batch)) ||
                  Number(batch) > game.budget
                }
                onClick={() =>
                  setGrades(
                    Object.fromEntries(wheel.selected.map((id) => [id, batch])),
                  )
                }
              >
                Áp dụng cho người được chọn
              </Button>
              {wheel.selected.map((id) => (
                <ScoreInput
                  key={id}
                  label={`Điểm ${snapshot.participants.find((p) => p.userId === id)?.maSinhVien || id}`}
                  value={grades[id] ?? "0"}
                  onChange={(value) =>
                    setGrades((old) => ({ ...old, [id]: value }))
                  }
                  max={game.budget}
                  disabled={disabled}
                />
              ))}
              <Button
                disabled={disabled}
                onClick={() => {
                  if (
                    wheel.selected.some(
                      (id) =>
                        !Number.isFinite(cents(grades[id] || "")) ||
                        Number(grades[id]) > game.budget,
                    )
                  ) {
                    setValidation(
                      "Điểm cần từ 0 đến ngân sách, tối đa hai chữ số thập phân.",
                    );
                    return;
                  }
                  setValidation("");
                  void run(
                    () =>
                      act("grade", {
                        grades: Object.fromEntries(
                          wheel.selected.map((id) => [id, Number(grades[id])]),
                        ),
                      }),
                    "Đã lưu điểm nháp.",
                  );
                }}
              >
                Lưu điểm người được chọn
              </Button>
              <Button
                disabled={disabled || dirty || spinning}
                onClick={() => setConfirm(true)}
              >
                Xem trước & chốt vòng quay
              </Button>
              {dirty && (
                <p className="text-sm text-muted-foreground">
                  Cần lưu điểm nháp trước khi xác nhận.
                </p>
              )}
            </>
          ) : (
            <p className="status-badge status-positive">Vòng quay đã chốt</p>
          )}
          {validation && (
            <p role="alert" className="text-sm text-destructive">
              {validation}
            </p>
          )}
        </div>
      </div>
      <Confirm
        open={confirm}
        onOpenChange={setConfirm}
        title="Xác nhận chốt điểm vòng quay?"
        description="Điểm hiển thị dưới đây là bản xem trước theo ngân sách và điểm nháp đã lưu. Server kiểm tra lại quyền tham gia trước khi chốt; người không hợp lệ nhận 0."
        pending={disabled}
        onConfirm={() => {
          void run(async () => {
            await act("finalize");
            setConfirm(false);
          }, "Đã chốt vòng quay.");
        }}
      >
        <div className="mt-4 max-h-64 overflow-y-auto">
          <Table>
            <thead>
              <tr>
                <th>Sinh viên</th>
                <th>Điểm dự kiến</th>
              </tr>
            </thead>
            <tbody>
              {snapshot.participants.map((p) => (
                <tr key={p.userId}>
                  <td>
                    {p.hoTen} · {p.maSinhVien}
                  </td>
                  <td>
                    {!p.eligible ||
                    !p.games.includes(1) ||
                    !wheel?.pool.includes(p.userId)
                      ? 0
                      : wheel.selected.includes(p.userId)
                        ? (wheel.grades[p.userId] ?? 0)
                        : game.budget}
                  </td>
                </tr>
              ))}
            </tbody>
          </Table>
        </div>
      </Confirm>
    </div>
  );
}
