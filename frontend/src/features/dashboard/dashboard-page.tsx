import { EmptyState, ErrorState, Loading } from "@/components/feedback";
import { DestinationCard, PageHeader } from "@/components/page-layout";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { useResource } from "@/hooks/use-resource";
import { api } from "@/lib/api";
import { realtime, useSession } from "@/lib/session";
import type { Course } from "@/lib/types";
import { useEffect, useState } from "react";
import { Link, useLocation } from "react-router-dom";
export function DashboardPage() {
  const { user } = useSession();
  const listing = useLocation().pathname.endsWith("/classes");
  const [query, setQuery] = useState("");
  const resource = useResource(
    async (signal) => {
      const courses = await api<Course[]>("/api/teaching/classes", { signal });
      return Promise.all(
        courses.map(async (course) => {
          const [progress, members] = await Promise.all([
            api<{ created: number; completed: number }>(
              `/api/teaching/classes/${course.id}/lesson-progress`,
              { signal },
            ),
            api<{ status: string }[]>(
              `/api/teaching/classes/${course.id}/members`,
              { signal },
            ),
          ]);
          return {
            ...course,
            progress,
            pending: members.filter((m) => m.status === "PENDING").length,
          };
        }),
      );
    },
    [user!.id],
  );
  useEffect(
    () =>
      realtime.listen((e) => {
        if (
          ["CLASS_MEMBERSHIP_UPDATED", "CLASS_LESSONS_UPDATED"].includes(e.type)
        )
          resource.reload();
      }),
    [resource.reload],
  );
  const courses =
    resource.data?.filter((c) =>
      `${c.name} ${c.code}`
        .toLocaleLowerCase("vi")
        .includes(query.toLocaleLowerCase("vi")),
    ) || [];
  return (
    <div className="space-y-6">
      <PageHeader
        title={listing ? "Lớp được phân công" : `Xin chào, ${user!.hoTen}!`}
        description={
          listing
            ? "Tìm và mở lớp bạn đang phụ trách."
            : "Không gian giảng dạy và chuẩn bị nội dung của bạn."
        }
        actions={
          <Button variant="outline" onClick={resource.reload}>
            Làm mới
          </Button>
        }
      />
      {!listing && (
        <div className="grid gap-4 md:grid-cols-2">
          <DestinationCard
            to="/app/giang_vien/classes"
            title="Lớp được phân công"
            description="Quản lý buổi học, thành viên, điểm và cấu hình của từng lớp."
          />
          <DestinationCard
            to="/app/giang_vien/banks"
            title="Ngân hàng nội dung"
            description="Chuẩn bị câu hỏi, đề bài và sử dụng các bộ được chia sẻ."
            tone={1}
          />
        </div>
      )}
      {resource.loading ? (
        <Loading />
      ) : resource.error ? (
        <ErrorState message={resource.error} retry={resource.reload} />
      ) : (
        resource.data && (
          <>
            {!listing && (
              <div className="grid gap-4 sm:grid-cols-3">
                {[
                  [resource.data.length, "Lớp đang được phân công"],
                  [
                    new Set(resource.data.map((c) => c.subjectId)).size,
                    "Môn trong các lớp phụ trách",
                  ],
                  [
                    resource.data.reduce((sum, c) => sum + c.pending, 0),
                    "Yêu cầu chờ duyệt trong các lớp phụ trách",
                  ],
                ].map(([value, label]) => (
                  <Card key={label} className="p-5">
                    <p className="text-2xl font-semibold">{value}</p>
                    <p className="mt-2 text-sm text-muted-foreground">
                      {label}
                    </p>
                  </Card>
                ))}
              </div>
            )}
            {listing ? (
              <Input
                aria-label="Tìm lớp học"
                placeholder="Tên hoặc mã lớp…"
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                className="max-w-md"
              />
            ) : (
              <h2 className="text-xl font-semibold">Mở lớp học</h2>
            )}
            {!courses.length ? (
              <EmptyState
                title={
                  resource.data.length
                    ? "Không tìm thấy lớp phù hợp"
                    : "Chưa có lớp học"
                }
                description="Lớp xuất hiện sau khi quản trị viên phân công bạn phụ trách."
              />
            ) : (
              <div className="course-grid">
                {(listing ? courses : courses.slice(0, 3)).map((c, i) => (
                  <Card key={c.id} className="course-card">
                    <CardHeader className={`tone-${i % 4}`}>
                      <p className="eyebrow">{c.code}</p>
                      <CardTitle>{c.name}</CardTitle>
                      <p className="text-sm text-muted-foreground">
                        Bạn được phân công phụ trách lớp này
                      </p>
                    </CardHeader>
                    <CardContent className="space-y-4 pt-5">
                      <p className="text-sm">
                        Buổi đã tạo: {c.progress.created}/{c.requiredLessons} ·
                        Buổi lớp đã kết thúc: {c.progress.completed}/
                        {c.requiredLessons}
                      </p>
                      <p className="text-sm">
                        {c.pending} yêu cầu vào lớp đang chờ duyệt
                      </p>
                      <Button asChild variant="outline" className="w-full">
                        <Link to={`/app/giang_vien/classes/${c.id}`}>
                          Xem thông tin lớp
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
    </div>
  );
}
