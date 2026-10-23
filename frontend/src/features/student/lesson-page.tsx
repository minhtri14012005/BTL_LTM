import { useState } from "react";
import { Link, useParams } from "react-router-dom";
import { useSession } from "@/lib/session";
import { useTeachingSnapshot } from "@/features/teaching/teaching-shared";
import { type Game, type Snapshot } from "@/features/teaching/teaching-api";
import { statusNames } from "@/lib/types";
import { Card, CardHeader, CardTitle, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { ErrorState, Loading } from "@/components/feedback";
import { ServerWheel } from "@/components/server-wheel";
import {
  StudentFrame,
  Notice,
  FormError,
  Textarea,
  ServerCountdown,
  useStudentAction,
  studentError,
} from "./student-shared";
export function StudentLessonPage() {
  const { id } = useParams();
  const lessonId = Number(id);
  return Number.isSafeInteger(lessonId) && lessonId > 0 ? (
    <StudentLesson key={lessonId} id={lessonId} />
  ) : (
    <ErrorState message="Mã buổi không hợp lệ." />
  );
}
function StudentLesson({ id }: { id: number }) {
  const live = useTeachingSnapshot(id, "student"),
    action = useStudentAction();
  const data = live.data;
  async function command(type: string, payload: object = {}) {
    try {
      await live.command(type, payload);
    } catch (e) {
      throw new Error(studentError(e));
    }
  }
  return (
    <StudentFrame title={data?.lesson.title || "Buổi học"}>
      <div className="flex flex-wrap gap-3">
        <Button
          variant="outline"
          onClick={() => {
            void live.refresh();
          }}
        >
          Đọc lại trạng thái
        </Button>
        {data && (
          <Button asChild variant="outline">
            <Link to={`/app/sinh_vien/classes/${data.lesson.courseClassId}`}>
              Về lớp
            </Link>
          </Button>
        )}
      </div>
      <FormError message={action.error} />
      {live.error && (
        <ErrorState
          message={studentError(new Error(live.error))}
          retry={() => {
            void live.refresh();
          }}
        />
      )}
      {!data && !live.error ? (
        <Loading />
      ) : (
        data &&
        (live.disabled ? (
          <Notice>
            Đang chờ snapshot và kết nối mới nhất từ Server. Câu hỏi, điểm và
            các nút gửi tạm ẩn để tránh dùng trạng thái cũ.
          </Notice>
        ) : (
          <>
            <section className="welcome-banner">
              <p className="eyebrow">
                BUỔI {data.lesson.number} · {statusNames[data.lesson.status]}
              </p>
              <h2 className="mt-3 text-2xl font-semibold">
                Điểm buổi: {data.lesson.score}/100
              </h2>
              <p className="mt-2 text-sm">
                Điểm do Server ghi nhận.{" "}
                {data.lesson.attending
                  ? "Bạn đã tham dự buổi."
                  : "Bạn chưa tham dự buổi; tư cách thành viên lớp không tự tạo điểm."}
              </p>
              {data.lesson.status === "RUNNING" && !data.lesson.attending && (
                <Button
                  className="mt-4"
                  disabled={action.pending}
                  onClick={() => {
                    void action.run(
                      () => command("ATTEND_LESSON"),
                      "Server đã xác nhận tham dự buổi.",
                    );
                  }}
                >
                  Tham dự buổi học
                </Button>
              )}
            </section>
            <div className="grid gap-6">
              {data.lesson.games.map((game) => (
                <Card key={game.id} className="overflow-hidden">
                  <CardHeader className={`tone-${game.position - 1}`}>
                    <p className="eyebrow">
                      TRÒ {game.position} · {game.modeLabel}
                    </p>
                    <CardTitle>{game.title}</CardTitle>
                    <p>
                      {statusNames[game.status]} · {game.score}/{game.budget}{" "}
                      điểm
                    </p>
                  </CardHeader>
                  <CardContent className="space-y-5 pt-5">
                    {game.status === "READY" ? (
                      <p>Chờ giảng viên bắt đầu trò.</p>
                    ) : !game.participating && game.status === "RUNNING" ? (
                      <div className="space-y-3">
                        <p>
                          Bạn cần tham gia trò để được gửi bài và ghi nhận điểm.
                        </p>
                        <Button
                          disabled={action.pending || !data.lesson.attending}
                          onClick={() => {
                            void action.run(
                              () =>
                                command("PARTICIPATE_LESSON_GAME", {
                                  position: game.position,
                                }),
                              "Đã tham gia trò.",
                            );
                          }}
                        >
                          Tham gia trò {game.position}
                        </Button>
                        {!data.lesson.attending && (
                          <p className="text-sm text-muted-foreground">
                            Tham dự buổi học trước khi tham gia trò.
                          </p>
                        )}
                      </div>
                    ) : null}
                    {game.mode === "LUCKY_WHEEL" && game.status !== "READY" && (
                      <StudentWheel snapshot={data} game={game} />
                    )}
                    {game.participating &&
                      game.mode !== "LUCKY_WHEEL" &&
                      game.status !== "READY" &&
                      (game.mode === "ASSIGNMENT" ? (
                        <Assignment
                          key={game.id}
                          snapshot={data}
                          game={game}
                          pending={action.pending}
                          send={(payload) =>
                            action.run(
                              () => command("SUBMIT_LESSON_ACTION", payload),
                              "Server đã xác nhận bản nộp.",
                            )
                          }
                        />
                      ) : (
                        <Objective
                          key={`${game.id}:${data.window?.gameId === game.id ? data.window.token : 0}`}
                          snapshot={data}
                          game={game}
                          pending={action.pending}
                          send={(payload) =>
                            action.run(
                              () => command("SUBMIT_LESSON_ACTION", payload),
                              "Server đã nhận câu trả lời.",
                            )
                          }
                        />
                      ))}
                    {game.status === "COMPLETED" && !game.participating && (
                      <p>
                        Không tham gia trò hợp lệ · Điểm Server: {game.score}
                      </p>
                    )}
                  </CardContent>
                </Card>
              ))}
            </div>
            {data.lesson.status === "COMPLETED" && (
              <Notice>
                Buổi học đã hoàn tất đủ ba trò. Điểm chính thức:{" "}
                {data.lesson.score}/100. Xem bảng xếp hạng và hồ sơ để theo dõi
                thành tích.
              </Notice>
            )}
          </>
        ))
      )}
    </StudentFrame>
  );
}
export function StudentWheel({
  snapshot,
  game,
}: {
  snapshot: Snapshot;
  game: Game;
}) {
  const { user } = useSession();
  const wheel = snapshot.wheel;
  return (
    <div className="space-y-4">
      <ServerWheel wheel={wheel} serverTimeMs={snapshot.serverTimeMs} />
      {wheel?.selectedForChallenge ? (
        <div
          role="status"
          className="rounded-2xl border-2 border-primary bg-accent p-5 font-semibold"
        >
          Giảng viên đã chọn bạn để thực hiện vòng quay.{" "}
          {game.status === "COMPLETED"
            ? `Điểm đã chốt: ${game.score}`
            : "Thực hiện thử thách chung và chờ giảng viên xác nhận điểm."}
        </div>
      ) : wheel?.pool.includes(user!.id) &&
        wheel.challengeIndex !== undefined ? (
        <Notice>
          Bạn không bị chọn và được Server ghi nhận mức tối đa: {game.score}/
          {game.budget} điểm.
        </Notice>
      ) : game.participating && game.status !== "COMPLETED" ? (
        <p>
          Đã tham gia. Chờ Server xác nhận tập quay và lựa chọn của giảng viên.
          Điểm hiện tại: {game.score}.
        </p>
      ) : (
        <p>
          Bạn chưa tham gia tập quay hợp lệ. Điểm Server ghi nhận: {game.score}.
        </p>
      )}
    </div>
  );
}
type AnswerProps = {
  snapshot: Snapshot;
  game: Game;
  pending: boolean;
  send: (payload: object) => Promise<boolean>;
};
export function Objective({ snapshot, game, pending, send }: AnswerProps) {
  const [value, setValue] = useState("");
  const window = snapshot.window?.gameId === game.id ? snapshot.window : null;
  const results =
    (game.mode === "QUIZ" ? snapshot.quiz : snapshot.fillBlank)?.[
      String(game.id)
    ] || [];
  const open = window?.status === "OPEN" && game.status === "RUNNING";
  const question = open ? window.question : null;
  const submitted = !!window?.submitted;
  const disabled = pending || !open || submitted;
  return (
    <div className="space-y-5">
      {game.status === "COMPLETED" ? (
        <h3 className="text-lg font-semibold">
          Kết quả Server: {results.filter((r) => !!r.correct).length}/
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
                : "Đang chờ câu tiếp theo / đã hết hạn"}
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
                <label className="block rounded-2xl bg-accent/30 p-5 text-lg leading-10">
                  <span>{question.content.split("{{blank}}")[0]}</span>
                  <Input
                    aria-label="Đáp án điền từ"
                    className="mx-2 inline-flex w-full sm:w-64"
                    value={value}
                    onChange={(e) => setValue(e.target.value)}
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
                      ([key, text]) => (
                        <label
                          key={key}
                          className={`flex min-w-0 cursor-pointer items-start gap-3 rounded-2xl border p-4 ${value === key ? "border-primary bg-accent" : ""}`}
                        >
                          <input
                            aria-label={`Đáp án ${key}`}
                            type="radio"
                            name="answer"
                            value={key}
                            checked={value === key}
                            onChange={() => setValue(key)}
                            className="mt-1"
                          />
                          <span className="min-w-0 break-words">
                            {key}. {text}
                          </span>
                        </label>
                      ),
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
      {results.length > 0 && (
        <div className="rounded-xl bg-muted p-4">
          <p className="mb-2 font-semibold">Kết quả đã được Server xác nhận</p>
          <ul className="space-y-1 text-sm">
            {results.map((r) => (
              <li key={r.questionIndex}>
                Câu {r.questionIndex}: {r.correct ? "Đúng" : "Sai"}
              </li>
            ))}
          </ul>
          <p className="mt-3">
            Điểm hiện tại: {game.score}/{game.budget}
          </p>
        </div>
      )}
    </div>
  );
}
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
