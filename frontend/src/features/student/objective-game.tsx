import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { type Game, type Snapshot } from "@/features/teaching/teaching-api";
import { useSession } from "@/lib/session";
import { useEffect, useRef, useState } from "react";
import { ServerCountdown } from "./student-shared";
export type AnswerProps = {
  snapshot: Snapshot;
  game: Game;
  pending: boolean;
  send: (payload: object) => Promise<boolean>;
};
export function Objective({ snapshot, game, pending, send }: AnswerProps) {
  const { user } = useSession();
  const window = snapshot.window?.gameId === game.id ? snapshot.window : null;
  const draftKey = `objective-draft:${user!.id}:${game.id}:${window?.token || 0}`;
  const [value, setValue] = useState(() => {
    try {
      return sessionStorage.getItem(draftKey) || "";
    } catch {
      return "";
    }
  });
  // Feedback state: null = no feedback, "correct" = correct, "wrong" = wrong
  const [feedback, setFeedback] = useState<"correct" | "wrong" | null>(null);
  const feedbackTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  // Track previous submitted state to detect when server confirms answer
  const prevSubmittedRef = useRef<boolean>(false);
  const prevTokenRef = useRef<number>(window?.token || 0);

  function updateValue(v: string) {
    setValue(v);
    try {
      sessionStorage.setItem(draftKey, v);
    } catch {}
  }

  useEffect(() => {
    if (window?.submitted || window?.status !== "OPEN")
      try {
        sessionStorage.removeItem(draftKey);
      } catch {}
  }, [draftKey, window?.submitted, window?.status]);

  // Reset value and feedback when question changes (token changes)
  useEffect(() => {
    const currentToken = window?.token || 0;
    if (currentToken !== prevTokenRef.current) {
      prevTokenRef.current = currentToken;
      prevSubmittedRef.current = false;
      setValue("");
      setFeedback(null);
      if (feedbackTimer.current) clearTimeout(feedbackTimer.current);
    }
  }, [window?.token]);

  // Detect server answer confirmation and trigger feedback
  useEffect(() => {
    if (!window?.submitted || prevSubmittedRef.current) return;
    // Find the result for the current question to know correct/wrong
    const results =
      (game.mode === "QUIZ" ? snapshot.quiz : snapshot.fillBlank)?.[
        String(game.id)
      ] || [];
    const currentResult = results.find(
      (r) => r.questionIndex === window.questionIndex,
    );
    if (currentResult !== undefined) {
      prevSubmittedRef.current = true;
      if (currentResult.answerValue !== undefined)
        setValue(currentResult.answerValue);
      const isCorrect = !!currentResult.correct;
      setFeedback(isCorrect ? "correct" : "wrong");
      if (feedbackTimer.current) clearTimeout(feedbackTimer.current);
      feedbackTimer.current = setTimeout(() => setFeedback(null), 1800);
    }
  }, [
    window?.submitted,
    window?.questionIndex,
    snapshot.quiz,
    snapshot.fillBlank,
    game.id,
    game.mode,
  ]);

  useEffect(
    () => () => {
      if (feedbackTimer.current) clearTimeout(feedbackTimer.current);
    },
    [],
  );

  const open = window?.status === "OPEN" && game.status === "RUNNING";
  const question = open ? window.question : null;
  const submitted = !!window?.submitted;
  const disabled = pending || !open || submitted;

  // Feedback class helpers
  const feedbackClass =
    feedback === "correct"
      ? "answer-flash-correct"
      : feedback === "wrong"
        ? "answer-flash-wrong"
        : "";

  return (
    <div className="space-y-5">
      {game.status === "COMPLETED" ? (
        <h3 className="text-lg font-semibold">
          Kết quả Server:{" "}
          {
            (
              (game.mode === "QUIZ" ? snapshot.quiz : snapshot.fillBlank)?.[
                String(game.id)
              ] || []
            ).filter((r) => !!r.correct).length
          }
          /
          {snapshot.questionCounts[String(game.id)] ||
            window?.questionCount ||
            "—"}{" "}
          câu đúng · {game.score}/{game.budget} điểm
        </h3>
      ) : (
        <>
          <h3 className="text-xl font-semibold">
            {window
              ? `Câu ${window.questionIndex}/${window.questionCount}`
              : "Chờ mở câu hỏi"}
          </h3>
          {open && (
            <ServerCountdown
              serverTimeMs={snapshot.serverTimeMs}
              deadlineAtMs={window.deadlineAtMs}
            />
          )}
          <p role="status" className="status-badge">
            {submitted
              ? "Server đã nhận câu trả lời"
              : open
                ? "Đang nhận câu trả lời"
                : "Đang chờ câu tiếp theo…"}
          </p>
          {question && (
            <form
              className="space-y-4"
              onSubmit={(e) => {
                e.preventDefault();
                if (!disabled && value.trim())
                  void send({ token: window!.token, value });
              }}
            >
              {game.mode === "FILL_BLANK" ? (
                <label
                  className={`block rounded-2xl bg-accent/30 p-5 text-lg leading-10 transition-all duration-300 ${feedbackClass}`}
                >
                  <span>{question.content.split("{{blank}}")[0]}</span>
                  <Input
                    aria-label="Đáp án điền từ"
                    className={`mx-2 inline-flex w-full sm:w-64 transition-all duration-300 ${feedbackClass}`}
                    value={value}
                    onChange={(e) => updateValue(e.target.value)}
                    disabled={disabled}
                    required
                    maxLength={300}
                    autoComplete="off"
                  />
                  <span>{question.content.split("{{blank}}")[1]}</span>
                </label>
              ) : (
                <>
                  <p className="whitespace-pre-wrap text-lg leading-8">
                    {question.content}
                  </p>
                  {question.imageRef && (
                    <img
                      className="max-h-72 max-w-full rounded-xl"
                      src={`/api/student/lessons/${snapshot.lesson.id}/images/${question.imageRef.slice(7)}?token=${window!.token}`}
                      alt="Hình minh họa câu hỏi"
                    />
                  )}
                  <fieldset
                    disabled={disabled}
                    className="grid gap-3 sm:grid-cols-2"
                  >
                    <legend className="mb-2 text-sm">
                      Chọn một trong bốn đáp án
                    </legend>
                    {Object.entries(question.options || {}).map(
                      ([key, text]) => {
                        const isSelected = value === key;
                        const optionFeedback = isSelected ? feedbackClass : "";
                        return (
                          <label
                            key={key}
                            className={`flex min-w-0 cursor-pointer items-start gap-3 rounded-2xl border p-4 transition-all duration-300 ${isSelected ? "border-primary bg-accent" : ""} ${optionFeedback}`}
                          >
                            <input
                              aria-label={`Đáp án ${key}`}
                              type="radio"
                              name="answer"
                              value={key}
                              checked={isSelected}
                              onChange={() => updateValue(key)}
                              className="mt-1"
                            />
                            <span className="min-w-0 break-words">
                              {key}. {text}
                            </span>
                          </label>
                        );
                      },
                    )}
                  </fieldset>
                </>
              )}
              <Button type="submit" disabled={disabled || !value.trim()}>
                {pending
                  ? "Đang chờ xác nhận…"
                  : submitted
                    ? "Đã gửi đáp án"
                    : "Gửi đáp án"}
              </Button>
            </form>
          )}
        </>
      )}
    </div>
  );
}
