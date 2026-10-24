import { ErrorState, Loading } from "@/components/feedback";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { useTeachingSnapshot } from "@/features/teaching/teaching-shared";
import { statusNames } from "@/lib/types";
import { Link, useParams } from "react-router-dom";
import { Assignment } from "./assignment-game";
import { Objective } from "./objective-game";
import {
  FormError,
  Notice,
  studentError,
  StudentFrame,
  useStudentAction,
} from "./student-shared";
import { StudentWheel } from "./wheel-game";
export function StudentLessonPage() {
  const { id, position } = useParams();
  const lessonId = Number(id);
  return Number.isSafeInteger(lessonId) && lessonId > 0 ? (
    <StudentLesson
      key={`${lessonId}:${position || "overview"}`}
      id={lessonId}
      position={position === undefined ? undefined : Number(position)}
    />
  ) : (
    <ErrorState message="Mã buổi không hợp lệ." />
  );
}
function StudentLesson({ id, position }: { id: number; position?: number }) {
  const live = useTeachingSnapshot(id, "student", position),
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
      {position !== undefined && (
        <Button asChild variant="outline">
          <Link to={`/app/sinh_vien/lessons/${id}`}>Về tổng quan buổi học</Link>
        </Button>
      )}
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
            {position !== undefined &&
              !data.lesson.games.some((g) => g.position === position) && (
                <ErrorState message="Trò không tồn tại hoặc đang được ẩn." />
              )}
            <div
              className={
                position === undefined
                  ? "grid gap-5 lg:grid-cols-3"
                  : "grid gap-6"
              }
            >
              {data.lesson.games
                .filter(
                  (g) => position === undefined || g.position === position,
                )
                .map((game) => (
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
                      {position === undefined ? (
                        <Button asChild>
                          <Link
                            to={`/app/sinh_vien/lessons/${id}/games/${game.position}`}
                          >
                            Vào trò {game.position}
                          </Link>
                        </Button>
                      ) : (
                        <>
                          {["QUIZ", "FILL_BLANK"].includes(game.mode) && (
                            <Notice>
                              Cấu hình chung:{" "}
                              {(game.durationMs || 30000) / 1000} giây/câu ·{" "}
                              {data.questionCounts[String(game.id)] || game.questionCount || 0} câu · Tối
                              đa {game.budget} điểm. Điểm = (số câu đúng / số câu sử dụng) × ngân sách, làm tròn 2 chữ số thập phân.
                            </Notice>
                          )}
                          {game.status === "READY" ? (
                            <p>Chờ giảng viên bắt đầu trò.</p>
                          ) : !game.participating &&
                            game.status === "RUNNING" ? (
                            <div className="space-y-3">
                              <p>
                                Bạn cần tham gia trò để được gửi bài và ghi nhận
                                điểm.
                              </p>
                              <Button
                                disabled={
                                  action.pending || !data.lesson.attending
                                }
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
                          {game.mode === "LUCKY_WHEEL" &&
                            game.status !== "READY" && (
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
                                    () =>
                                      command("SUBMIT_LESSON_ACTION", payload),
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
                                    () =>
                                      command("SUBMIT_LESSON_ACTION", payload),
                                    "Server đã nhận câu trả lời.",
                                  )
                                }
                              />
                            ))}
                          {game.status === "COMPLETED" &&
                            !game.participating && (
                              <p>
                                Không tham gia trò hợp lệ · Điểm Server:{" "}
                                {game.score}
                              </p>
                            )}
                        </>
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
export { Assignment } from "./assignment-game";
export { Objective } from "./objective-game";
export { StudentWheel } from "./wheel-game";
