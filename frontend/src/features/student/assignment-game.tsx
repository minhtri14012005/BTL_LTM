import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { useSession } from "@/lib/session";
import { useState } from "react";
import type { AnswerProps } from "./objective-game";
import { ServerCountdown, Textarea } from "./student-shared";
export function Assignment({ snapshot, game, pending, send }: AnswerProps) {
  const { user } = useSession();
  const draftKey = `student-draft:${user!.id}:${game.id}`;
  const [draft] = useState(() => {
    try {
      return sessionStorage.getItem(draftKey);
    } catch {
      return null;
    }
  });
  const info = snapshot.assignment[String(game.id)],
    latest = info?.latest[0];
  const [text, setText] = useState(draft ?? latest?.text ?? "");
  const [dirty, setDirty] = useState(draft !== null);
  const window = snapshot.window?.gameId === game.id ? snapshot.window : null;
  const open = window?.status === "OPEN" && game.status === "RUNNING";
  const grade = info?.grades.find((g) => g.submissionId === latest?.id);
  return (
    <div className="space-y-4">
      <h3 className="text-lg font-semibold">Bài tập nhập trực tiếp</h3>
      {window?.question && (
        <p className="whitespace-pre-wrap rounded-xl bg-accent/30 p-4 leading-7">
          {window.question.content}
        </p>
      )}
      <p role="status" className="status-badge">
        {game.status === "COMPLETED"
          ? "Đã kết thúc chấm điểm"
          : open
            ? "Đang nhận bài"
            : "Đã đóng nhận / chờ giảng viên mở hạn"}
      </p>
      {info?.deadlineAtMs && (
        <p>
          Hạn nộp Server: {new Date(info.deadlineAtMs).toLocaleString("vi-VN")}
        </p>
      )}
      {open && (
        <ServerCountdown
          serverTimeMs={snapshot.serverTimeMs}
          deadlineAtMs={window.deadlineAtMs}
        />
      )}
      <form
        className="space-y-3"
        onSubmit={async (e) => {
          e.preventDefault();
          const sent = dirty ? text : latest?.text || text;
          if (!open || pending || !sent.trim()) return;
          const ok = await send({ token: window!.token, value: sent });
          if (ok) {
            setDirty(false);
            try {
              sessionStorage.removeItem(draftKey);
            } catch {
              /* Storage can be disabled. */
            }
          }
        }}
      >
        <label className="block space-y-2 text-sm">
          <span>Bài làm của bạn · tối đa 5000 ký tự</span>
          <Textarea
            aria-label="Bài làm của bạn"
            className="min-h-64"
            maxLength={5000}
            value={dirty ? text : latest?.text || text}
            onChange={(e) => {
              setText(e.target.value);
              setDirty(true);
              try {
                sessionStorage.setItem(draftKey, e.target.value);
              } catch {
                /* Keep the in-memory draft. */
              }
            }}
            disabled={pending || !open}
          />
        </label>
        <p className="text-xs text-muted-foreground">
          Có thể nộp lại khi Server còn nhận bài. Chỉ bản hợp lệ cuối cùng được
          chấm.
        </p>
        <Button
          type="submit"
          disabled={
            pending || !open || !(dirty ? text : latest?.text || text).trim()
          }
        >
          {pending
            ? "Đang chờ xác nhận…"
            : latest
              ? "Nộp lại bài tập"
              : "Nộp bài tập"}
        </Button>
      </form>
      {latest && (
        <Card className="space-y-2 p-4">
          <h4 className="font-semibold">Bản nộp đã xác nhận #{latest.id}</h4>
          <p className="text-sm">
            Server nhận: {new Date(latest.receivedAtMs).toLocaleString("vi-VN")}
          </p>
          <p className="max-h-64 overflow-y-auto whitespace-pre-wrap break-words">
            {latest.text}
          </p>
          <p>
            {grade
              ? `Điểm xác nhận cho bản này: ${grade.score}/${game.budget}`
              : "Bản nộp cuối đang chờ chấm"}
          </p>
        </Card>
      )}
      {game.status === "COMPLETED" && (
        <p className="font-semibold">
          Điểm đã chốt: {game.score}/{game.budget}
        </p>
      )}
    </div>
  );
}
