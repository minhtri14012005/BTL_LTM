import { ErrorState, Loading } from "@/components/feedback";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { api } from "@/lib/api";
import { statusNames } from "@/lib/types";
import { useCallback, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { TeachingGamePanel } from "./game-panel";
import {
  FormError,
  Notice,
  TeachingFrame,
  useTeachingAction,
  useTeachingSnapshot,
} from "./teaching-shared";
export function TeachingLessonPage() {
  const { id, position } = useParams();
  const lessonId = Number(id);
  if (!Number.isSafeInteger(lessonId) || lessonId < 1)
    return <ErrorState message="Mã buổi không hợp lệ." />;
  return (
    <LessonRuntime
      key={`${lessonId}:${position || "overview"}`}
      id={lessonId}
      position={position === undefined ? undefined : Number(position)}
    />
  );
}
function LessonRuntime({ id, position }: { id: number; position?: number }) {
  const live = useTeachingSnapshot(id),
    action = useTeachingAction();
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
  if (
    data &&
    position !== undefined &&
    !data.lesson.games.some((g) => g.position === position)
  )
    return <ErrorState message="Trò không hợp lệ." />;
  return (
    <TeachingFrame
      title={data?.lesson.title || "Buổi học"}
      classId={data?.lesson.courseClassId}
    >
      {position !== undefined && (
        <Button asChild variant="outline">
          <Link to={`/app/giang_vien/lessons/${id}`}>
            Về tổng quan buổi học
          </Link>
        </Button>
      )}
      <div className="flex flex-wrap gap-3">
        <Button
          variant="outline"
          onClick={() => {
            void live.refresh();
          }}
        >
          Làm mới trạng thái
        </Button>
        {position === undefined && (
          <Button asChild variant="outline">
            <Link to={`/app/giang_vien/lessons/${id}/settings`}>
              Chỉnh sửa cấu hình
            </Link>
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
            {position === undefined && (
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
                {data.lesson.status === "DRAFT" && (
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
                )}
              </div>
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
                      <p className="text-sm">
                        {statusNames[game.status]} ·{" "}
                        {game.visible === false ? "Đang ẩn · " : ""}Ngân sách{" "}
                        {game.budget} điểm
                      </p>
                    </CardHeader>
                    <CardContent className="space-y-5 pt-5">
                      {position === undefined ? (
                        <Button asChild>
                          <Link
                            to={`/app/giang_vien/lessons/${id}/games/${game.position}`}
                          >
                            Quản lý trò {game.position}
                          </Link>
                        </Button>
                      ) : (
                        <>
                          <TeachingGamePanel
                            id={id}
                            data={data}
                            game={game}
                            live={live}
                            action={action}
                            disabled={disabled}
                            busyContents={busyContents}
                            onContentBusy={onContentBusy}
                          />
                        </>
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
