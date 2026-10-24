import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { useResource } from "@/hooks/use-resource";
import { api, ApiError } from "@/lib/api";
import { useEffect, useState } from "react";
import { AssignmentDeadline } from "./activity-controls";
import { type Game, type Snapshot } from "./teaching-api";
import { Textarea } from "./teaching-shared";

export function GameConfiguration({
  snapshot,
  game,
  disabled,
  run,
  refresh,
  command,
}: {
  command: (type: string, payload: object) => Promise<void>;
  snapshot: Snapshot;
  game: Game;
  disabled: boolean;
  run: (work: () => Promise<unknown>, success?: string) => Promise<boolean>;
  refresh: () => Promise<void>;
}) {
  const objective = ["QUIZ", "FILL_BLANK"].includes(game.mode);
  const [seconds, setSeconds] = useState(
    String((game.durationMs || 30000) / 1000),
  );
  const source = useResource(
    async (signal) => {
      if (!objective) return 0;
      try {
        const data = await api<{ questions: unknown[] }>(
          `/api/teaching/lessons/${snapshot.lesson.id}/games/${game.position}/content`,
          { signal },
        );
        return data.questions.length;
      } catch (error) {
        if (error instanceof ApiError && error.status === 404) return 0;
        throw error;
      }
    },
    [game.id],
  );
  const available = source.data || 0;
  const [count, setCount] = useState(
    String(
      game.questionCount || snapshot.questionCounts[String(game.id)] || "",
    ),
  );
  useEffect(() => {
    if (!game.questionCount && available) setCount(String(available));
  }, [game.questionCount, available]);
  const [budget, setBudget] = useState(String(game.budget));
  const [cells, setCells] = useState(game.wheelCells?.join("\n") || "");
  const [selected, setSelected] = useState(String(game.selectedCount || 1));
  const [error, setError] = useState("");
  useEffect(() => {
    if (game.mode !== "LUCKY_WHEEL" || game.wheelCells) return;
    const controller = new AbortController();
    void api<{ cells: string[] }>(
      `/api/teaching/classes/${snapshot.lesson.courseClassId}/wheel`,
      { signal: controller.signal },
    )
      .then((value) => setCells((old) => old || value.cells.join("\n")))
      .catch(() => {});
    return () => controller.abort();
  }, [game.id]);
  return (
    <Card className="space-y-4 bg-accent/30 p-5">
      <h3 className="font-semibold">Cấu hình chung của trò chơi</h3>
      <p className="text-sm text-muted-foreground">
        Áp dụng cho toàn bộ trò. Cấu hình điểm được giữ nguyên sau khi bắt đầu.
      </p>
      <form
        className="space-y-4"
        onSubmit={(e) => {
          e.preventDefault();
          const durationMs = Number(seconds) * 1000;
          if (
            objective &&
            (!Number.isSafeInteger(durationMs) ||
              durationMs < 1000 ||
              durationMs > 2147483647)
          ) {
            setError("Thời gian mỗi câu không hợp lệ.");
            return;
          }
          if (
            objective &&
            (!Number.isInteger(Number(count)) ||
              Number(count) < 1 ||
              Number(count) > available)
          ) {
            setError("Số câu sử dụng phải từ 1 đến số câu hợp lệ trong bộ.");
            return;
          }
          setError("");
          void run(async () => {
            await api(
              `/api/teaching/lessons/${snapshot.lesson.id}/games/${game.position}/configuration`,
              {
                method: "PUT",
                body: JSON.stringify({
                  revision: snapshot.lesson.revision,
                  budget: Number(budget),
                  durationMs,
                  questionCount: objective ? Number(count) : undefined,
                  wheelCells: cells
                    .split("\n")
                    .map((s) => s.trim())
                    .filter(Boolean),
                  selectedCount: Number(selected),
                }),
              },
            );
            await refresh();
          }, "Đã lưu cấu hình chung.");
        }}
      >
        <fieldset
          disabled={
            disabled || game.status !== "READY" || (objective && source.loading)
          }
          className="grid gap-4 sm:grid-cols-2"
        >
          {objective && (
            <>
              <label className="space-y-2 text-sm">
                Thời gian mỗi câu (giây)
                <Input
                  aria-label="Thời gian mỗi câu (giây)"
                  type="number"
                  min={1}
                  max={2147483}
                  step={1}
                  required
                  value={seconds}
                  onChange={(e) => setSeconds(e.target.value)}
                />
              </label>
              <label className="space-y-2 text-sm">
                Số câu hỏi sử dụng
                <Input
                  aria-label="Số câu hỏi sử dụng"
                  type="number"
                  min={1}
                  max={available || 1}
                  step={1}
                  required
                  value={count}
                  onChange={(e) => setCount(e.target.value)}
                />
                <span className="text-xs text-muted-foreground">
                  Bộ câu hỏi có {available} câu hợp lệ. Sử dụng các câu đầu theo
                  thứ tự đã lưu.
                </span>
              </label>
            </>
          )}
          <label className="space-y-2 text-sm">
            Ngân sách điểm của trò
            <Input
              aria-label="Ngân sách điểm của trò"
              type="number"
              min={0}
              max={100}
              step="0.01"
              required
              value={budget}
              onChange={(e) => setBudget(e.target.value)}
            />
          </label>
          {game.mode === "LUCKY_WHEEL" && (
            <>
              <label className="space-y-2 text-sm">
                Số sinh viên được chọn
                <Input
                  aria-label="Số sinh viên được chọn"
                  type="number"
                  min={1}
                  max={10000}
                  step={1}
                  required
                  value={selected}
                  onChange={(e) => setSelected(e.target.value)}
                />
              </label>
              <label className="space-y-2 text-sm sm:col-span-2">
                Danh sách thử thách (mỗi dòng một thử thách)
                <Textarea
                  aria-label="Danh sách thử thách"
                  required
                  value={cells}
                  onChange={(e) => setCells(e.target.value)}
                />
              </label>
            </>
          )}
          <Button
            type="submit"
            disabled={
              objective && (!available || source.loading || !!source.error)
            }
          >
            Lưu cấu hình chung
          </Button>
        </fieldset>
        {objective && (
          <p className="font-medium">
            Sử dụng {count || 0}/{available} câu · {seconds} giây/câu · Tối đa{" "}
            {budget} điểm. Điểm = (số câu đúng / {count || 0}) × {budget}, làm
            tròn 2 chữ số thập phân. Sai hoặc bỏ qua không được tính là câu
            đúng.
          </p>
        )}
        {source.error && (
          <p role="alert" className="text-destructive">
            {source.error}
          </p>
        )}
        {objective && !source.loading && !available && (
          <p className="text-sm">
            Chọn bộ câu hỏi trong mục Nội dung trò trước khi lưu cấu hình.
          </p>
        )}
        {error && (
          <p role="alert" className="text-destructive">
            {error}
          </p>
        )}
      </form>
      {game.mode === "ASSIGNMENT" && (
        <AssignmentDeadline
          snapshot={snapshot}
          game={game}
          disabled={disabled}
          run={run}
          command={command}
        />
      )}
    </Card>
  );
}
