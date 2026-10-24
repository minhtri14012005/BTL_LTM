import { FormDialog } from "@/components/page-layout";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Confirm } from "@/components/ui/confirm";
import { Input } from "@/components/ui/input";
import { Table } from "@/components/ui/table";
import { useState } from "react";
import { type Game, type Snapshot, cents } from "./teaching-api";
import {
  FormError,
  Notice,
  ScoreInput,
  ServerCountdown,
} from "./teaching-shared";
type Props = {
  actionError?: string;
  snapshot: Snapshot;
  game: Game;
  disabled: boolean;
  command: (type: string, payload: object) => Promise<void>;
  run: (work: () => Promise<unknown>, success?: string) => Promise<boolean>;
};
export function QuizProgress({
  snapshot,
  game,
}: {
  snapshot: Snapshot;
  game: Game;
}) {
  const count = snapshot.questionCounts[String(game.id)] || 0;
  const window = snapshot.window?.gameId === game.id ? snapshot.window : null;
  const open = window?.status === "OPEN";
  return (
    <div className="space-y-4">
      <h3 className="font-semibold">
        Tiến trình {game.mode === "QUIZ" ? "Quiz" : "Điền từ"} · Câu{" "}
        {window?.questionIndex || 0}/{count}
      </h3>
      {open && (
        <>
          <ServerCountdown
            serverTimeMs={snapshot.serverTimeMs}
            deadlineAtMs={window.deadlineAtMs}
          />
          <p className="whitespace-pre-wrap rounded-xl bg-accent p-4 text-sm">
            {window.question?.content}
          </p>
          <p className="text-sm text-muted-foreground">
            Server tự động chuyển câu khi hết thời gian.
          </p>
        </>
      )}
      {!open && game.status === "RUNNING" && (
        <p className="text-sm text-muted-foreground">
          {window ? "Đang chờ câu tiếp theo từ Server…" : "Đang khởi động trò…"}
        </p>
      )}
      {game.status === "COMPLETED" && (
        <p className="font-medium text-green-600 dark:text-green-400">
          ✓ Trò đã hoàn thành. Xem kết quả trong tab Kết quả.
        </p>
      )}
    </div>
  );
}
export function TeacherResults({
  snapshot,
  game,
}: {
  snapshot: Snapshot;
  game: Game;
}) {
  return (
    <Card>
      <Table>
        <thead>
          <tr>
            <th>Mã SV</th>
            <th>Họ tên</th>
            <th>Tham gia</th>
            {["QUIZ", "FILL_BLANK"].includes(game.mode) && <th>Số câu đúng</th>}
            <th>Điểm Server</th>
          </tr>
        </thead>
        <tbody>
          {snapshot.participants.map((p) => (
            <tr key={p.userId}>
              <td>{p.maSinhVien}</td>
              <td>{p.hoTen}</td>
              <td>
                {p.games.includes(game.position) ? "Đã tham gia" : "Vắng"}
              </td>
              {["QUIZ", "FILL_BLANK"].includes(game.mode) && (
                <td>{p.correct[game.position] ?? 0}</td>
              )}
              <td>{p.scores[game.position] ?? 0}</td>
            </tr>
          ))}
        </tbody>
      </Table>
    </Card>
  );
}
export function AssignmentDeadline({
  snapshot,
  game,
  disabled,
  command,
  run,
}: Props) {
  const assignment = snapshot.assignment[String(game.id)];
  const window = snapshot.window?.gameId === game.id ? snapshot.window : null;
  const [deadline, setDeadline] = useState("");
  const [error, setError] = useState("");
  const canDeadline = game.status !== "COMPLETED";
  return (
    <div className="space-y-4">
      <h4 className="font-medium">Thời hạn nộp chung cho tất cả sinh viên</h4>
      <p className="text-sm">
        Hạn Server:{" "}
        {assignment?.deadlineAtMs
          ? new Date(assignment.deadlineAtMs).toLocaleString("vi-VN")
          : "Chưa đặt"}
      </p>
      {window?.status === "OPEN" && (
        <ServerCountdown
          serverTimeMs={snapshot.serverTimeMs}
          deadlineAtMs={window.deadlineAtMs}
        />
      )}
      <form
        className="flex flex-col items-start gap-3 sm:flex-row sm:items-end"
        onSubmit={(e) => {
          e.preventDefault();
          const ms = new Date(deadline).getTime();
          if (!Number.isSafeInteger(ms)) {
            setError("Chọn hạn nộp hợp lệ.");
            return;
          }
          setError("");
          void run(
            () =>
              command("SET_ASSIGNMENT_DEADLINE", {
                position: game.position,
                token: snapshot.window?.token || 0,
                deadlineAtMs: ms,
              }),
            "Server đã cập nhật hạn nộp.",
          );
        }}
      >
        <label className="min-w-0 space-y-2 text-sm">
          Hạn nộp mới
          <Input
            aria-label={`Hạn nộp trò ${game.position}`}
            type="datetime-local"
            required
            value={deadline}
            disabled={disabled || !canDeadline}
            onChange={(e) => setDeadline(e.target.value)}
          />
        </label>
        <Button type="submit" disabled={disabled || !canDeadline || !deadline}>
          {window?.status === "CLOSED"
            ? "Mở lại / sửa hạn nộp"
            : "Đặt / sửa hạn nộp"}
        </Button>
      </form>
      {!canDeadline && game.status === "READY" && (
        <p className="text-sm text-muted-foreground">
          Bắt đầu trò để đặt hạn nộp nếu buổi đã chạy.
        </p>
      )}

      {error && (
        <p role="alert" className="text-destructive">
          {error}
        </p>
      )}
    </div>
  );
}
export function AssignmentControls({
  actionError,
  snapshot,
  game,
  disabled,
  command,
  run,
}: Props) {
  const assignment = snapshot.assignment[String(game.id)];
  const window = snapshot.window?.gameId === game.id ? snapshot.window : null;
  const [grades, setGrades] = useState<Record<number, string>>({}),
    [confirm, setConfirm] = useState(false),
    [error, setError] = useState("");
  const [selectedSubmission, setSelectedSubmission] = useState<number>();
  const running = game.status === "RUNNING";
  return (
    <div className="space-y-4">
      <Notice>
        Chấm bản nộp hợp lệ cuối cùng, không cộng các lần nộp. Khi có bản mới,
        điểm chấm bản cũ chưa được dùng để chốt.
      </Notice>
      {running && window?.status === "OPEN" && (
        <Button
          variant="outline"
          disabled={disabled}
          onClick={() => {
            void run(() =>
              command("CLOSE_LESSON_WINDOW", { token: window.token }),
            );
          }}
        >
          Đóng nhận bài
        </Button>
      )}
      {error && (
        <p role="alert" className="text-sm text-destructive">
          {error}
        </p>
      )}
      <h3 className="font-semibold">Bản nộp hợp lệ cuối cùng</h3>
      {!assignment?.latest.length ? (
        <p className="text-sm text-muted-foreground">
          Chưa có bản nộp được Server chấp nhận.
        </p>
      ) : (
        assignment.latest.map((submission) => {
          const person = snapshot.participants.find(
            (p) => p.userId === submission.studentId,
          );
          const grade = assignment.grades.find(
            (g) =>
              g.studentId === submission.studentId &&
              g.submissionId === submission.id,
          );
          return (
            <div
              key={submission.id}
              className="flex flex-wrap items-center justify-between gap-3 rounded-xl border bg-card p-4"
            >
              <div>
                <p className="font-semibold">
                  {person?.hoTen} · {person?.maSinhVien}
                </p>
                <p className="mt-1 text-sm text-muted-foreground">
                  Bản #{submission.id} ·{" "}
                  {grade ? `${grade.score}/${game.budget} điểm` : "Chưa chấm"}
                </p>
              </div>
              <Button
                variant="outline"
                onClick={() => {
                  setError("");
                  setSelectedSubmission(submission.id);
                }}
              >
                Xem bài {person?.maSinhVien || submission.studentId}
              </Button>
              <FormDialog
                open={selectedSubmission === submission.id}
                onOpenChange={(open) => {
                  if (!open) setSelectedSubmission(undefined);
                }}
                title={`Bài làm · ${person?.maSinhVien || submission.studentId}`}
                wide
              >
                <Card className="space-y-3 p-4">
                  <h4 className="font-semibold">
                    {person?.hoTen} · {person?.maSinhVien}
                  </h4>
                  <p className="text-xs text-muted-foreground">
                    Bản #{submission.id} · Server nhận{" "}
                    {new Date(submission.receivedAtMs).toLocaleString("vi-VN")}
                  </p>
                  <p className="max-h-64 overflow-y-auto whitespace-pre-wrap break-words rounded-xl bg-muted p-3">
                    {submission.text}
                  </p>
                  <p className="text-sm">
                    {grade
                      ? `Điểm đã xác nhận cho bản này: ${grade.score}`
                      : "Bản này chưa được chấm"}
                  </p>
                  <FormError message={error || actionError || ""} />
                  <ScoreInput
                    label={`Chấm ${person?.maSinhVien || submission.studentId}`}
                    value={grades[submission.id] ?? String(grade?.score ?? "")}
                    max={game.budget}
                    disabled={disabled || !running}
                    onChange={(value) =>
                      setGrades((old) => ({ ...old, [submission.id]: value }))
                    }
                  />
                  <Button
                    disabled={disabled || !running}
                    onClick={() => {
                      const value =
                        grades[submission.id] ?? String(grade?.score ?? "");
                      if (
                        !Number.isFinite(cents(value)) ||
                        Number(value) > game.budget
                      ) {
                        setError(
                          "Điểm phải trong ngân sách, tối đa hai chữ số thập phân.",
                        );
                        return;
                      }
                      setError("");
                      void run(
                        () =>
                          command("GRADE_ASSIGNMENT", {
                            position: game.position,
                            studentId: submission.studentId,
                            submissionId: submission.id,
                            score: Number(value),
                          }),
                        "Đã chấm bản nộp cuối.",
                      );
                    }}
                  >
                    Xác nhận chấm bản #{submission.id}
                  </Button>
                </Card>
              </FormDialog>
            </div>
          );
        })
      )}
      <Button
        disabled={disabled || !running || !window || window.status === "OPEN"}
        onClick={() => setConfirm(true)}
      >
        Chốt chấm bài tập
      </Button>
      <Confirm
        open={confirm}
        onOpenChange={setConfirm}
        error={actionError}
        title="Kết thúc chấm bài tập?"
        description="Server kiểm tra mọi bản nộp cuối đã có điểm; sau khi chốt không thể mở lại hay nhận thêm bài."
        pending={disabled}
        onConfirm={() => {
          void run(async () => {
            await command("FINALIZE_ASSIGNMENT", { position: game.position });
            setConfirm(false);
          }, "Đã chốt bài tập.");
        }}
      />
    </div>
  );
}
