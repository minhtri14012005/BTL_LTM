import { useState } from "react";
import { Confirm } from "@/components/ui/confirm";
import { LessonCreation } from "./lesson-creation";
import { FormError } from "./teaching-shared";
import { EmptyState } from "@/components/feedback";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { LessonStructure } from "@/features/dashboard/lesson-structure";
import { api, post } from "@/lib/api";
import { statusNames } from "@/lib/types";
import { Link, useNavigate, useParams } from "react-router-dom";
import { type Lesson } from "./teaching-api";
import { useTeachingAction } from "./teaching-shared";
export function ClassLessons({
  classId,
  lessons,
  progress,
  refresh,
}: {
  classId: number;
  lessons: Lesson[];
  progress: { created: number; completed: number; required: number };
  refresh: () => void;
}) {
  const { action: routeAction } = useParams();
  const creating = routeAction === "new";
  const navigate = useNavigate();
  const action = useTeachingAction();
  const [deleting, setDeleting] = useState<Lesson | null>(null);
  return (
    <>
      {creating && (
        <Button asChild variant="outline">
          <Link to={`/app/giang_vien/classes/${classId}/lessons`}>
            Về danh sách buổi học
          </Link>
        </Button>
      )}
      <Confirm
        open={!!deleting}
        onOpenChange={(open) => {
          if (!open) setDeleting(null);
        }}
        title={`Xóa Buổi ${deleting?.number}: ${deleting?.title}?`}
        description={
          deleting?.status === "RUNNING"
            ? "Buổi đang diễn ra. Xóa sẽ dừng tất cả trò đang chạy và loại bỏ câu trả lời, bài nộp, điểm và thành tích thuộc buổi này. Các buổi phía sau sẽ được đánh lại số thứ tự."
            : "Xóa buổi này và cấu hình của nó. Các buổi phía sau sẽ được đánh lại số thứ tự; ngân hàng câu hỏi nguồn được giữ nguyên."
        }
        pending={action.pending}
        error={action.error}
        onConfirm={() => {
          if (!deleting) return;
          void action.run(async () => {
            await api(`/api/teaching/lessons/${deleting.id}`, {
              method: "DELETE",
              body: JSON.stringify({ revision: deleting.revision }),
            });
            setDeleting(null);
            refresh();
          }, "Đã xóa buổi học.");
        }}
      />
      <FormError message={action.error} />
      <div className="space-y-5">
        {!creating && (
          <Button
            disabled={progress.created >= progress.required || action.pending}
            onClick={() =>
              navigate(`/app/giang_vien/classes/${classId}/lessons/new`)
            }
          >
            Tạo buổi mới
          </Button>
        )}
        {creating && (
          <Card className="p-5">
            <LessonCreation
              classId={classId}
              pending={action.pending}
              error={action.error}
              onSave={async (plan, banks) => {
                await action.run(async () => {
                  let created = await post<Lesson>(
                    `/api/teaching/classes/${classId}/lessons`,
                    plan,
                  );
                  for (const [position, quizId] of Object.entries(
                    banks || {},
                  )) {
                    if (!quizId) continue;
                    await post(
                      `/api/teaching/lessons/${created.id}/games/${position}/content`,
                      { quizId, lessonRevision: created.revision },
                    );
                    created = await api<Lesson>(
                      `/api/teaching/lessons/${created.id}`,
                    );
                  }
                  navigate(`/app/giang_vien/classes/${classId}/lessons`);
                  refresh();
                }, "Đã tạo buổi học.");
              }}
            />
          </Card>
        )}
        {!creating &&
          (!lessons.length ? (
            <EmptyState
              title="Chưa có buổi học"
              description="Tạo buổi với Vòng quay bắt buộc và hai trò khác nhau; tổng ngân sách không quá 100."
            />
          ) : (
            <div className="course-grid">
              {lessons.map((lesson) => (
                <Card key={lesson.id}>
                  <CardHeader className="tone-0">
                    <p className="eyebrow">BUỔI {lesson.number}</p>
                    <CardTitle>{lesson.title}</CardTitle>
                    <p className="text-sm">
                      {statusNames[lesson.status]} ·{" "}
                      {lesson.visible ? "Đã công bố" : "Đang ẩn"}
                    </p>
                  </CardHeader>
                  <CardContent className="space-y-3 pt-5">
                    <Button asChild className="w-full">
                      <Link to={`/app/giang_vien/lessons/${lesson.id}`}>
                        Soạn & điều khiển buổi
                      </Link>
                    </Button>
                    <LessonStructure lesson={lesson} />
                    <details className="rounded-xl border p-3">
                      <summary className="cursor-pointer text-sm font-medium">
                        Quản lý buổi
                      </summary>
                      <div className="mt-3 flex flex-wrap gap-2">
                        <Button asChild variant="outline">
                          <Link
                            to={`/app/giang_vien/lessons/${lesson.id}/settings`}
                          >
                            Chỉnh sửa cấu hình
                          </Link>
                        </Button>
                        <Button
                          variant="destructive"
                          disabled={
                            lesson.status === "COMPLETED" || action.pending
                          }
                          onClick={() => {
                            action.setError("");
                            setDeleting(lesson);
                          }}
                        >
                          Xóa buổi
                        </Button>
                        {lesson.status === "COMPLETED" && (
                          <p className="text-xs text-muted-foreground">
                            Buổi đã hoàn thành không được xóa. Có thể sao chép
                            cấu hình khi tạo buổi mới cùng môn.
                          </p>
                        )}
                      </div>
                    </details>
                  </CardContent>
                </Card>
              ))}
            </div>
          ))}
      </div>
    </>
  );
}
