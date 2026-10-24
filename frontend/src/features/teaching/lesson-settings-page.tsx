import { ErrorState, Loading } from "@/components/feedback";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { api } from "@/lib/api";
import { Link, useParams } from "react-router-dom";
import { LessonPlan } from "./lesson-plan";
import { modeNames } from "./teaching-api";
import {
  TeachingFrame,
  Notice,
  useTeachingAction,
  useTeachingSnapshot,
} from "./teaching-shared";
export function LessonSettingsPage() {
  const { id } = useParams();
  const lessonId = Number(id);
  if (!Number.isSafeInteger(lessonId) || lessonId < 1)
    return <ErrorState message="Mã buổi không hợp lệ." />;
  return <Settings key={lessonId} id={lessonId} />;
}
function Settings({ id }: { id: number }) {
  const live = useTeachingSnapshot(id),
    action = useTeachingAction();
  const lesson = live.data?.lesson;
  return (
    <TeachingFrame
      title="Chỉnh sửa cấu hình buổi học"
      classId={lesson?.courseClassId}
    >
      <Button asChild variant="outline">
        <Link to={`/app/giang_vien/lessons/${id}`}>Về tổng quan buổi học</Link>
      </Button>
      {live.error ? (
        <ErrorState message={live.error} retry={() => void live.refresh()} />
      ) : !lesson ? (
        <Loading />
      ) : (
        <>
          <Notice>
            Buổi {lesson.number} · {lesson.title}. Lưu cấu hình vào chính buổi
            này, giữ nguyên số thứ tự.
          </Notice>
          {lesson.status === "DRAFT" ? (
            <Card className="p-5">
              <LessonPlan
                key={lesson.revision}
                initial={lesson}
                lockedModes={lesson.games
                  .filter(
                    (game) =>
                      (live.data?.questionCounts[String(game.id)] || 0) > 0,
                  )
                  .map((game) => game.position)}
                pending={action.pending || live.disabled}
                error={action.error}
                onSave={async (plan) => {
                  await action.run(async () => {
                    await api(`/api/teaching/lessons/${id}`, {
                      method: "PUT",
                      body: JSON.stringify({ revision: lesson.revision, plan }),
                    });
                    await live.refresh();
                  }, "Đã lưu cấu hình buổi học.");
                }}
              />
            </Card>
          ) : (
            <Notice>
              Buổi đã bắt đầu: tên và cấu trúc ba trò được khóa để bảo toàn tiến
              trình. Cấu hình của trò chưa bắt đầu vẫn có thể chỉnh tại trang
              riêng.
            </Notice>
          )}
          <div className="grid gap-4 md:grid-cols-3">
            {lesson.games.map((game) => (
              <Card key={game.id} className="space-y-3 p-5">
                <h3 className="font-semibold">{modeNames[game.mode]}</h3>
                <p className="text-sm">
                  {game.status === "READY"
                    ? "Chưa bắt đầu: có thể chỉnh cấu hình chung và nội dung."
                    : "Đã bắt đầu: thời gian, số câu và ngân sách đã được khóa."}
                </p>
                <Button asChild variant="outline">
                  <Link
                    to={`/app/giang_vien/lessons/${id}/games/${game.position}?panel=configuration`}
                  >
                    Cấu hình trò {game.position}
                  </Link>
                </Button>
              </Card>
            ))}
          </div>
        </>
      )}
    </TeachingFrame>
  );
}
