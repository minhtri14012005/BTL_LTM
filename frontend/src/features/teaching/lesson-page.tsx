import { useCallback, useState } from "react";
import { useParams } from "react-router-dom";
import { api } from "@/lib/api";
import { statusNames } from "@/lib/types";
import { Button } from "@/components/ui/button";
import { Card, CardHeader, CardTitle, CardContent } from "@/components/ui/card";
import { Loading, ErrorState } from "@/components/feedback";
import {
  TeachingFrame,
  useTeachingAction,
  useTeachingSnapshot,
  FormError,
  Notice,
} from "./teaching-shared";
import { LessonPlan } from "./lesson-plan";
import { GameContent } from "./game-content";
import { WheelControls } from "./wheel-controls";
import {
  ObjectiveControls,
  AssignmentControls,
  TeacherResults,
} from "./activity-controls";
export function TeachingLessonPage() {
  const { id } = useParams();
  const lessonId = Number(id);
  if (!Number.isSafeInteger(lessonId) || lessonId < 1)
    return <ErrorState message="Mã buổi không hợp lệ." />;
  return <LessonRuntime key={lessonId} id={lessonId} />;
}
function LessonRuntime({ id }: { id: number }) {
  const live = useTeachingSnapshot(id),
    action = useTeachingAction();
  const [edit, setEdit] = useState(false);
  const [busyContents, setBusyContents] = useState<Record<number, boolean>>({});
  const onContentBusy = useCallback((gameId: number, busy: boolean) => {
    setBusyContents((previous) =>
      previous[gameId] === busy ? previous : { ...previous, [gameId]: busy },
    );
  }, []);
  const data = live.data;
  const disabled =
    action.pending ||
    live.disabled ||
    Object.values(busyContents).some(Boolean);
  return (
    <TeachingFrame
      title={data?.lesson.title || "Buổi học"}
      classId={data?.lesson.courseClassId}
    >
      <div className="flex flex-wrap gap-3">
        <Button
          variant="outline"
          onClick={() => {
            void live.refresh();
          }}
        >
          Làm mới trạng thái
        </Button>
        {data?.lesson.status === "DRAFT" && (
          <Button
            variant="outline"
            disabled={disabled}
            onClick={() => setEdit(!edit)}
          >
            {edit ? "Đóng kế hoạch" : "Sửa kế hoạch buổi"}
          </Button>
        )}
      </div>
      {live.error && (
        <ErrorState
          message={live.error}
          retry={() => {
            void live.refresh();
          }}
        />
      )}
      {!data && !live.error ? (
        <Loading />
      ) : (
        data && (
          <>
            <Notice>
              Buổi {data.lesson.number} · {statusNames[data.lesson.status]} ·
              Tổng ngân sách{" "}
              {data.lesson.games
                .reduce((a, g) => a + Number(g.budget), 0)
                .toFixed(2)}
              /100.{" "}
              {live.disabled
                ? "Đang chờ snapshot/kết nối mới nhất; điều khiển tạm khóa."
                : "Đã nhận trạng thái Server."}
            </Notice>
            <FormError message={action.error} />
            {edit && data.lesson.status === "DRAFT" && (
              <Card className="space-y-4 p-5">
                <p className="text-sm text-muted-foreground">
                  Đổi mode sẽ xóa nội dung snapshot của kế hoạch cũ. Ngân hàng
                  nguồn được giữ nguyên.
                </p>
                <LessonPlan
                  key={data.lesson.revision}
                  initial={data.lesson}
                  pending={disabled}
                  error={action.error}
                  onSave={async (plan) => {
                    await action.run(async () => {
                      await api(`/api/teaching/lessons/${id}`, {
                        method: "PUT",
                        body: JSON.stringify({
                          revision: data.lesson.revision,
                          plan,
                        }),
                      });
                      setEdit(false);
                      await live.refresh();
                    }, "Đã lưu kế hoạch buổi.");
                  }}
                />
              </Card>
            )}
            {data.lesson.status === "DRAFT" && (
              <div className="flex flex-wrap items-center gap-3">
                <span className="status-badge">
                  {data.lesson.visible ? "Đã công bố" : "Đang ẩn"}
                </span>
                <Button
                  variant="outline"
                  disabled={disabled}
                  onClick={() => {
                    void action.run(async () => {
                      await api(`/api/teaching/lessons/${id}/visibility`, {
                        method: "PUT",
                        body: JSON.stringify({
                          revision: data.lesson.revision,
                          visible: !data.lesson.visible,
                        }),
                      });
                      await live.refresh();
                    });
                  }}
                >
                  {data.lesson.visible ? "Ẩn buổi học" : "Công bố buổi học"}
                </Button>
                <Button
                  disabled={disabled || !data.lesson.visible}
                  onClick={() => {
                    void action.run(
                      () =>
                        live.command("START_LESSON", {
                          revision: data.lesson.revision,
                        }),
                      "Đã bắt đầu buổi học.",
                    );
                  }}
                >
                  Bắt đầu buổi học
                </Button>
              </div>
            )}
            <div className="grid gap-6">
              {data.lesson.games.map((game) => (
                <Card key={game.id} className="overflow-hidden">
                  <CardHeader className={`tone-${game.position - 1}`}>
                    <p className="eyebrow">
                      TRÒ {game.position} · {game.modeLabel}
                    </p>
                    <CardTitle>{game.title}</CardTitle>
                    <p className="text-sm">
                      {statusNames[game.status]} · Ngân sách {game.budget} điểm
                    </p>
                  </CardHeader>
                  <CardContent className="space-y-5 pt-5">
                    {game.mode !== "LUCKY_WHEEL" && (
                      <GameContent
                        lesson={data.lesson}
                        game={game}
                        refresh={live.refresh}
                        locked={
                          action.pending ||
                          live.disabled ||
                          Object.entries(busyContents).some(
                            ([id, busy]) => Number(id) !== game.id && busy,
                          )
                        }
                        onBusyChange={onContentBusy}
                      />
                    )}
                    {game.status === "READY" && (
                      <Button
                        disabled={
                          disabled ||
                          data.lesson.status !== "RUNNING" ||
                          data.lesson.games.some(
                            (g) =>
                              g.position < game.position &&
                              g.status !== "COMPLETED",
                          )
                        }
                        onClick={() => {
                          void action.run(
                            () =>
                              live.command("START_LESSON_GAME", {
                                position: game.position,
                                revision: data.lesson.revision,
                              }),
                            "Đã bắt đầu trò.",
                          );
                        }}
                      >
                        Bắt đầu trò {game.position}
                      </Button>
                    )}
                    {game.mode === "LUCKY_WHEEL" && game.status !== "READY" && (
                      <WheelControls
                        snapshot={data}
                        game={game}
                        disabled={disabled || game.status !== "RUNNING"}
                        run={action.run}
                        refresh={live.refresh}
                      />
                    )}
                    {["QUIZ", "FILL_BLANK"].includes(game.mode) &&
                      game.status === "RUNNING" && (
                        <ObjectiveControls
                          snapshot={data}
                          game={game}
                          disabled={disabled}
                          command={live.command}
                          run={action.run}
                        />
                      )}
                    {game.mode === "ASSIGNMENT" && (
                      <AssignmentControls
                        snapshot={data}
                        game={game}
                        disabled={disabled || game.status === "COMPLETED"}
                        command={live.command}
                        run={action.run}
                      />
                    )}
                    {game.status === "COMPLETED" && (
                      <TeacherResults snapshot={data} game={game} />
                    )}
                  </CardContent>
                </Card>
              ))}
            </div>
          </>
        )
      )}
    </TeachingFrame>
  );
}
