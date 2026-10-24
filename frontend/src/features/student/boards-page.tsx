import { EmptyState, ErrorState, Loading } from "@/components/feedback";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Table } from "@/components/ui/table";
import { type Board } from "@/features/teaching/teaching-api";
import { useResource } from "@/hooks/use-resource";
import { api } from "@/lib/api";
import { realtime, useSession } from "@/lib/session";
import { type Membership } from "@/lib/types";
import { useEffect } from "react";
import { useSearchParams } from "react-router-dom";
import {
  NativeSelect,
  Notice,
  StudentFrame,
  useStudentUpdates,
} from "./student-shared";
export function StudentBoardsPage() {
  const memberships = useResource(
    (signal) => api<Membership[]>("/api/student/classes", { signal }),
    [],
  );
  useEffect(
    () =>
      realtime.listen((e) => {
        if (
          e.type === "CLASS_MEMBERSHIP_UPDATED" ||
          e.type === "LESSON_ACCESS_REVOKED"
        )
          memberships.reload();
      }),
    [memberships.reload],
  );
  return (
    <StudentFrame title="Bảng xếp hạng">
      {memberships.loading ? (
        <Loading />
      ) : memberships.error ? (
        <ErrorState message={memberships.error} retry={memberships.reload} />
      ) : (
        memberships.data && <BoardSelector members={memberships.data} />
      )}
    </StudentFrame>
  );
}
function BoardSelector({ members }: { members: Membership[] }) {
  const [params, setParams] = useSearchParams();
  const scope = params.get("scope") || "class";
  const selectedClass =
    params.get("classId") ??
    String(members.find((m) => m.status === "APPROVED")?.courseClassId || "");
  const selectedMember = members.find(
    (m) => String(m.courseClassId) === selectedClass,
  );
  const selectedScope =
    params.has("subjectId") && params.has("academicYearId")
      ? params.get("subjectId") && params.get("academicYearId")
        ? `${params.get("subjectId")}:${params.get("academicYearId")}`
        : ""
      : selectedMember
        ? `${selectedMember.subjectId}:${selectedMember.academicYearId}`
        : "";
  const setScope = (scope: string) =>
    setParams((old) => {
      const next = new URLSearchParams(old);
      next.set("scope", scope);
      return next;
    });
  const setClass = (id: string) =>
    setParams((old) => {
      const next = new URLSearchParams(old);
      next.set("classId", id);
      next.delete("subjectId");
      next.delete("academicYearId");
      return next;
    });
  const setSelectedScope = (value: string) =>
    setParams((old) => {
      const [subject, year] = value.split(":");
      const next = new URLSearchParams(old);
      if (subject && year) {
        next.set("subjectId", subject);
        next.set("academicYearId", year);
      } else {
        next.set("subjectId", "");
        next.set("academicYearId", "");
      }
      return next;
    });
  const { user } = useSession();
  const course = members.find(
    (m) => String(m.courseClassId) === selectedClass && m.status === "APPROVED",
  );
  const scopes = Array.from(
    new Map(
      members.map((m) => [
        `${m.subjectId}:${m.academicYearId}`,
        {
          value: `${m.subjectId}:${m.academicYearId}`,
          label: `${m.subjectName} · ${m.academicYearName}`,
        },
      ]),
    ).values(),
  );
  const [subjectId, yearId] = selectedScope.split(":");
  const resource = useResource(
    (signal) =>
      scope === "class"
        ? course
          ? api<Board>(
              `/api/learning/classes/${course.courseClassId}/leaderboard`,
              { signal },
            )
          : Promise.resolve(null)
        : selectedScope
          ? api<Board>(
              `/api/learning/leaderboard?subjectId=${subjectId}&academicYearId=${yearId}`,
              { signal },
            )
          : Promise.resolve(null),
    [scope, course?.courseClassId, selectedScope],
  );
  useStudentUpdates(resource.reload);
  return (
    <>
      <div className="flex flex-wrap gap-3">
        <NativeSelect
          label="Phạm vi bảng xếp hạng"
          value={scope}
          onChange={setScope}
          options={[
            { value: "class", label: "Trong lớp" },
            { value: "global", label: "Theo môn và năm học" },
          ]}
        />
        {scope === "class" ? (
          <NativeSelect
            label="Lớp xem bảng điểm"
            value={selectedClass}
            onChange={setClass}
            options={[
              { value: "", label: "Chọn lớp được duyệt" },
              ...members
                .filter((m) => m.status === "APPROVED")
                .map((m) => ({
                  value: String(m.courseClassId),
                  label: m.className,
                })),
            ]}
          />
        ) : (
          <NativeSelect
            label="Môn và năm học"
            value={selectedScope}
            onChange={setSelectedScope}
            options={[{ value: "", label: "Chọn môn / năm" }, ...scopes]}
          />
        )}
        <Button variant="outline" onClick={resource.reload}>
          Làm mới bảng xếp hạng
        </Button>
      </div>
      <h2 className="text-xl font-semibold">
        {scope === "class"
          ? `Bảng điểm lớp: ${course?.className || "Chưa chọn lớp"}`
          : `Bảng xếp hạng: ${scopes.find((s) => s.value === selectedScope)?.label || "Chưa chọn môn / năm học"}`}
      </h2>
      <Notice>
        {scope === "class"
          ? "Điểm trong lớp và Rank do giảng viên cấu hình. Đồng điểm tại ranh giới cùng nhận mức Rank cao hơn."
          : "Tổng điểm các lớp cùng môn và năm học. Đồng điểm cùng hạng số liên tiếp (Dense Ranking); không có mức Rank tự đặt."}
      </Notice>
      {resource.loading ? (
        <Loading />
      ) : resource.error ? (
        <ErrorState message={resource.error} retry={resource.reload} />
      ) : !resource.data?.students.length ? (
        <EmptyState
          title="Chưa có bảng điểm để hiển thị"
          description="Chọn phạm vi phù hợp. Bảng lớp yêu cầu tư cách thành viên đã được duyệt."
        />
      ) : (
        <Card>
          <Table>
            <thead>
              <tr>
                <th>Hạng số</th>
                <th>Họ và tên</th>
                <th>Mã sinh viên</th>
                <th>Điểm</th>
                {scope === "class" && <th>Rank của lớp</th>}
              </tr>
            </thead>
            <tbody>
              {resource.data.students.map((s) => (
                <tr
                  key={s.userId}
                  className={s.userId === user!.id ? "bg-accent/40" : ""}
                >
                  <td>{s.position}</td>
                  <td>
                    {s.hoTen}
                    {s.userId === user!.id && " (Bạn)"}
                  </td>
                  <td>{s.maSinhVien}</td>
                  <td>{s.score}</td>
                  {scope === "class" && (
                    <td>
                      <span className="status-badge">
                        {s.rank || "Chưa có mức"}
                      </span>
                    </td>
                  )}
                </tr>
              ))}
            </tbody>
          </Table>
        </Card>
      )}
    </>
  );
}
