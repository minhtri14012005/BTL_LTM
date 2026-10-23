import { Link, useParams } from "react-router-dom";
import { api } from "@/lib/api";
import { type Membership, statusNames } from "@/lib/types";
import { type Lesson } from "@/features/teaching/teaching-api";
import { useResource } from "@/hooks/use-resource";
import { Button } from "@/components/ui/button";
import { Card, CardHeader, CardTitle, CardContent } from "@/components/ui/card";
import { Loading, ErrorState, EmptyState } from "@/components/feedback";
import {
  StudentFrame,
  useStudentUpdates,
  studentError,
} from "./student-shared";
export function StudentClassPage() {
  const { id } = useParams();
  const resource = useResource(
    async (signal) => {
      if (!id || !/^\d+$/.test(id)) throw new Error("Mã lớp không hợp lệ.");
      const members = await api<Membership[]>("/api/student/classes", {
        signal,
      });
      const member = members.find((m) => m.courseClassId === Number(id));
      if (member?.status !== "APPROVED")
        throw new Error(
          "Bạn chưa được duyệt hoặc đã bị thu hồi quyền vào lớp. Lịch sử vẫn có trong hồ sơ.",
        );
      try {
        return {
          member,
          lessons: await api<Lesson[]>(`/api/student/classes/${id}/lessons`, {
            signal,
          }),
        };
      } catch (e) {
        throw new Error(studentError(e));
      }
    },
    [id],
  );
  useStudentUpdates(resource.reload);
  return (
    <StudentFrame title={resource.data?.member.className || "Lớp học"}>
      <div className="flex flex-wrap gap-3">
        <Button variant="outline" onClick={resource.reload}>
          Làm mới buổi học
        </Button>
        <Button asChild variant="outline">
          <Link to={`/app/sinh_vien/boards?classId=${id}`}>Bảng điểm lớp</Link>
        </Button>
      </div>
      {resource.loading ? (
        <Loading />
      ) : resource.error ? (
        <ErrorState message={resource.error} retry={resource.reload} />
      ) : (
        resource.data && (
          <>
            <section className="welcome-banner">
              <p className="eyebrow">{resource.data.member.classCode}</p>
              <h2 className="mt-3 text-2xl font-semibold">
                {resource.data.member.subjectName}
              </h2>
              <p className="mt-2">
                {resource.data.member.academicYearName} ·{" "}
                {resource.data.member.requiredLessons} buổi quy định
              </p>
              <p className="mt-2 text-sm">
                Chỉ các buổi đã được giảng viên công bố xuất hiện dưới đây.
              </p>
            </section>
            {!resource.data.lessons.length ? (
              <EmptyState
                title="Chưa có buổi học để hiển thị"
                description="Danh sách tự cập nhật khi giảng viên công bố buổi."
              />
            ) : (
              <div className="course-grid">
                {resource.data.lessons.map((l) => (
                  <Card key={l.id}>
                    <CardHeader className={`tone-${(l.number - 1) % 4}`}>
                      <p className="eyebrow">BUỔI {l.number}</p>
                      <CardTitle>{l.title}</CardTitle>
                      <span className="status-badge w-fit">
                        {statusNames[l.status]}
                      </span>
                    </CardHeader>
                    <CardContent className="space-y-4 pt-5">
                      <ol className="space-y-2 text-sm">
                        {l.games.map((g) => (
                          <li key={g.id}>
                            {g.position}. {g.modeLabel} ·{" "}
                            {statusNames[g.status]} · {g.score}/{g.budget} điểm
                          </li>
                        ))}
                      </ol>
                      <p className="font-semibold">Điểm buổi: {l.score}/100</p>
                      <Button asChild className="w-full">
                        <Link to={`/app/sinh_vien/lessons/${l.id}`}>
                          {l.status === "COMPLETED"
                            ? "Xem kết quả buổi"
                            : "Vào buổi học"}
                        </Link>
                      </Button>
                    </CardContent>
                  </Card>
                ))}
              </div>
            )}
          </>
        )
      )}
    </StudentFrame>
  );
}
