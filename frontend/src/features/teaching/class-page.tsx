import { ErrorState, Loading } from "@/components/feedback";
import { DestinationCard, SectionLinks } from "@/components/page-layout";
import { Button } from "@/components/ui/button";
import { useResource } from "@/hooks/use-resource";
import { api } from "@/lib/api";
import { realtime } from "@/lib/session";
import type { Course } from "@/lib/types";
import { useEffect, useState } from "react";
import { useParams } from "react-router-dom";
import { ClassLessons } from "./class-lessons";
import { Members } from "./class-members";
import { WheelConfig } from "./class-wheel";
import { RankPanel } from "./rank-panel";
import { type Lesson, type Member } from "./teaching-api";
import { Notice, TeachingFrame } from "./teaching-shared";
export function TeachingClassPage() {
  const { id, section = "overview", action: routeAction } = useParams();
  const classId = Number(id);
  const tab = section,
    creating = routeAction === "new";
  const resource = useResource(
    async (signal) => {
      if (!Number.isSafeInteger(classId) || classId < 1)
        throw new Error("Mã lớp không hợp lệ.");
      const [course, lessons, progress, members, room] = await Promise.all([
        api<Course>(`/api/teaching/classes/${classId}`, { signal }),
        api<Lesson[]>(`/api/teaching/classes/${classId}/lessons`, { signal }),
        api<{ required: number; created: number; completed: number }>(
          `/api/teaching/classes/${classId}/lesson-progress`,
          { signal },
        ),
        api<Member[]>(`/api/teaching/classes/${classId}/members`, { signal }),
        api<{ joinCode: string }>(`/api/teaching/classes/${classId}/room`, {
          signal,
        }),
      ]);
      return { course, lessons, progress, members, room };
    },
    [classId],
  );
  const [changed, setChanged] = useState(false);
  useEffect(
    () =>
      realtime.listen((event) => {
        if (
          ["CLASS_MEMBERSHIP_UPDATED", "CLASS_LESSONS_UPDATED"].includes(event.type) &&
          event.target?.id === classId
        ) {
          if (creating || tab === "wheel" || tab === "ranks") setChanged(true);
          else resource.reload();
        }
      }),
    [classId, creating, tab, resource.reload],
  );
  const base = `/app/giang_vien/classes/${classId}`;
  const sections = [
    { key: "overview", label: "Tổng quan lớp" },
    { key: "lessons", label: "Buổi học" },
    { key: "members", label: "Thành viên" },
    { key: "wheel", label: "Ô vòng quay" },
    { key: "scores", label: "Bảng điểm" },
    { key: "ranks", label: "Cấu hình Rank" },
  ];
  return (
    <TeachingFrame title={resource.data?.course.name || "Lớp được phân công"}>
      <SectionLinks
        label="Quản lý lớp"
        items={sections.map((s) => ({
          to: s.key === "overview" ? base : `${base}/${s.key}`,
          label: s.label,
          end: s.key === "overview",
        }))}
      />
      <div className="flex flex-wrap justify-between gap-3">
        <h2 className="text-lg font-semibold">
          {sections.find((s) => s.key === section)?.label || "Buổi học"}
        </h2>
        <Button
          variant="outline"
          onClick={() => {
            setChanged(false);
            resource.reload();
          }}
        >
          Làm mới lớp
        </Button>
      </div>
      {changed && (
        <Notice>
          Danh sách thành viên đã thay đổi. Làm mới lớp sau khi lưu chỉnh sửa.
        </Notice>
      )}
      {!sections.some((s) => s.key === section) ||
      (routeAction && !(section === "lessons" && routeAction === "new")) ? (
        <ErrorState message="Trang quản lý lớp không tồn tại." />
      ) : resource.loading ? (
        <Loading />
      ) : resource.error ? (
        <ErrorState message={resource.error} retry={resource.reload} />
      ) : (
        resource.data && (
          <>
            {section === "overview" && (
              <>
                <section className="welcome-banner">
                  <p className="eyebrow">{resource.data.course.code}</p>
                  <p className="mt-3">
                    Đã tạo {resource.data.progress.created}/
                    {resource.data.progress.required} · Đã hoàn thành{" "}
                    {resource.data.progress.completed}/
                    {resource.data.progress.required}
                  </p>
                  <p className="mt-3 break-all text-sm">
                    Mã tham gia lớp:{" "}
                    <strong>{resource.data.room.joinCode}</strong>
                  </p>
                  <p className="mt-2 text-sm">
                    {
                      resource.data.members.filter(
                        (m) => m.status === "PENDING",
                      ).length
                    }{" "}
                    yêu cầu chờ duyệt
                  </p>
                </section>
                <div className="grid gap-4 md:grid-cols-2">
                  {sections.slice(1).map((s, i) => (
                    <DestinationCard
                      key={s.key}
                      to={`${base}/${s.key}`}
                      title={s.label}
                      description={
                        {
                          lessons: "Tạo buổi và mở trang quản lý từng trò.",
                          members:
                            "Duyệt yêu cầu và quản lý sinh viên trong lớp.",
                          wheel: "Chuẩn bị danh sách thử thách dùng chung.",
                          scores: "Theo dõi điểm và thứ hạng của lớp.",
                          ranks: "Thiết lập mức Rank và chia sẻ cấu hình.",
                        }[s.key] || ""
                      }
                      tone={i % 4}
                    />
                  ))}
                </div>
              </>
            )}
            {section === "lessons" && (
              <ClassLessons
                classId={classId}
                lessons={resource.data.lessons}
                progress={resource.data.progress}
                refresh={resource.reload}
              />
            )}
            {section === "members" && (
              <Members
                classId={classId}
                members={resource.data.members}
                refresh={resource.reload}
              />
            )}
            {section === "wheel" && <WheelConfig classId={classId} />}
            {(section === "scores" || section === "ranks") && (
              <RankPanel
                classId={classId}
                view={section === "scores" ? "board" : "settings"}
              />
            )}
          </>
        )
      )}
    </TeachingFrame>
  );
}
