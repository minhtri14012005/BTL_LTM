import { Link, useParams } from "react-router-dom";
import { ArrowLeft, CalendarDays } from "lucide-react";
import { api } from "@/lib/api";
import { useSession, realtime } from "@/lib/session";
import type { Course, Lesson, Membership } from "@/lib/types";
import { statusNames } from "@/lib/types";
import { useResource } from "@/hooks/use-resource";
import { useEffect } from "react";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { LessonStructure } from "./lesson-structure";

import { EmptyState, ErrorState, Loading } from "@/components/feedback";

export function ClassPage() {
  const { id } = useParams(),
    { user } = useSession(),
    role = user!.role;
  const resource = useResource(
    async (signal) => {
      if (!id || !/^\d+$/.test(id)) throw new Error("Mã lớp không hợp lệ.");
      if (role === "SINH_VIEN") {
        const memberships = await api<Membership[]>("/api/student/classes", {
          signal,
        });
        const membership = memberships.find(
          (m) => m.courseClassId === Number(id),
        );
        if (!membership || membership.status !== "APPROVED")
          throw new Error("Bạn chưa được cấp quyền truy cập lớp này.");
        // The following API verifies membership and exclusions again on the server.
        const lessons = await api<Lesson[]>(
          `/api/student/classes/${id}/lessons`,
          { signal },
        );
        return {
          name: membership.className,
          code: membership.classCode,
          required: membership.requiredLessons,
          lessons,
        };
      }
      const course = await api<Course>(
        role === "ADMIN"
          ? `/api/admin/academic/classes/${id}`
          : `/api/teaching/classes/${id}`,
        { signal },
      );
      const lessons =
        role === "ADMIN"
          ? null
          : await api<Lesson[]>(`/api/teaching/classes/${id}/lessons`, {
              signal,
            });
      return {
        name: course.name,
        code: course.code,
        required: course.requiredLessons,
        lessons,
      };
    },
    [id, user!.id, role],
  );
  useEffect(
    () =>
      realtime.listen((event) => {
        if (event.type === "LESSON_ACCESS_REVOKED") resource.reload();
      }),
    [resource.reload],
  );
  const data = resource.data;
  return (
    <div className="space-y-6">
      <Button asChild variant="ghost">
        <Link to={`/app/${role.toLowerCase()}`}>
          <ArrowLeft />
          Về tổng quan
        </Link>
      </Button>
      {resource.loading ? (
        <Loading />
      ) : resource.error ? (
        <ErrorState message={resource.error} retry={resource.reload} />
      ) : (
        data && (
          <>
            <section className="welcome-banner">
              <span className="eyebrow">{data.code}</span>
              <h1 className="mt-3 text-3xl font-semibold">{data.name}</h1>
              <p className="mt-3 flex items-center gap-2 text-muted-foreground">
                <CalendarDays className="size-4" />
                {data.required} buổi học theo quy định
              </p>
            </section>
            {data.lessons === null ? (
              <Card className="p-6">
                <h2 className="font-semibold">Thông tin tổ chức lớp</h2>
                <p className="mt-3 leading-7 text-muted-foreground">
                  Mã lớp: {data.code}. Số buổi bắt buộc: {data.required}.
                </p>
              </Card>
            ) : data.lessons.length === 0 ? (
              <EmptyState
                title="Chưa có buổi học để hiển thị"
                description={
                  role === "SINH_VIEN"
                    ? "Buổi học sẽ xuất hiện khi giảng viên công bố nội dung."
                    : "Lớp chưa có buổi học. Nội dung được tổ chức theo số buổi đã quy định."
                }
              />
            ) : (
              <div className="course-grid">
                {data.lessons.map((lesson) => (
                  <Card key={lesson.id}>
                    <CardHeader>
                      <p className="eyebrow">BUỔI {lesson.number}</p>
                      <CardTitle>{lesson.title}</CardTitle>
                      <span className="status-badge w-fit">
                        {statusNames[lesson.status] || lesson.status}
                      </span>
                    </CardHeader>
                    <CardContent>
                      <LessonStructure lesson={lesson} />
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
