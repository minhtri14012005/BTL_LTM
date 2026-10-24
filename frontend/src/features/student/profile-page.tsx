import { EmptyState, ErrorState, Loading } from "@/components/feedback";
import { DestinationCard, SectionLinks } from "@/components/page-layout";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { modeNames, type Mode } from "@/features/teaching/teaching-api";
import { useResource } from "@/hooks/use-resource";
import { api } from "@/lib/api";
import { useSession } from "@/lib/session";
import type { Membership } from "@/lib/types";
import { statusNames } from "@/lib/types";
import { useState } from "react";
import { Link, useLocation, useSearchParams } from "react-router-dom";
import {
  NativeSelect,
  StudentFrame,
  useStudentUpdates,
  type Award,
  type History,
  type Profile,
} from "./student-shared";
export function StudentProfilePage() {
  const { user } = useSession();
  const path = useLocation().pathname;
  const section = path.endsWith("/history")
    ? "history"
    : path.endsWith("/achievements")
      ? "achievements"
      : "overview";
  const [params, setParams] = useSearchParams();
  const [page, setPage] = useState(0);
  const classId = params.get("classId") || "",
    subjectId = params.get("subjectId") || "",
    academicYearId = params.get("academicYearId") || "";
  const resource = useResource(
    async (signal) => {
      const query = new URLSearchParams({ page: String(page), size: "10" });
      if (classId) query.set("classId", classId);
      if (subjectId) query.set("subjectId", subjectId);
      if (academicYearId) query.set("academicYearId", academicYearId);
      const awardsQuery = new URLSearchParams();
      if (subjectId) awardsQuery.set("subjectId", subjectId);
      if (academicYearId) awardsQuery.set("academicYearId", academicYearId);
      const [profile, history, members, awards] = await Promise.all([
        api<Profile>("/api/learning/profile", { signal }),
        section === "history"
          ? api<History>(`/api/learning/history?${query}`, { signal })
          : Promise.resolve({
              items: [],
              page: 0,
              size: 10,
              totalElements: 0,
            } as History),
        api<Membership[]>("/api/student/classes", { signal }),
        section === "achievements"
          ? api<Award[]>(`/api/learning/achievements?${awardsQuery}`, {
              signal,
            })
          : Promise.resolve([] as Award[]),
      ]);
      return {
        profile,
        history,
        members,
        awards,
      };
    },
    [user!.id, section, page, classId, subjectId, academicYearId],
  );
  useStudentUpdates(resource.reload);
  function filter(key: string, value: string) {
    const next = new URLSearchParams(params);
    if (value) next.set(key, value);
    else next.delete(key);
    if (key !== "classId") next.delete("classId");
    setPage(0);
    setParams(next);
  }
  return (
    <StudentFrame
      title={
        section === "history"
          ? "Lịch sử học tập"
          : section === "achievements"
            ? "Danh hiệu cá nhân"
            : "Hồ sơ & thành tích"
      }
    >
      <SectionLinks
        label="Hồ sơ cá nhân"
        items={[
          { to: "/app/sinh_vien/profile", label: "Hồ sơ", end: true },
          { to: "/app/sinh_vien/history", label: "Lịch sử học tập" },
          { to: "/app/sinh_vien/achievements", label: "Danh hiệu" },
        ]}
      />
      {resource.loading ? (
        <Loading />
      ) : resource.error ? (
        <ErrorState message={resource.error} retry={resource.reload} />
      ) : (
        resource.data && (
          <>
            {section === "overview" && (
              <>
                <section className="welcome-banner">
                  <h2 className="text-2xl font-semibold">
                    {resource.data.profile.hoTen}
                  </h2>
                  <p className="mt-2">
                    Mã sinh viên:{" "}
                    <strong>{resource.data.profile.maSinhVien}</strong>
                  </p>
                  <p>{statusNames[resource.data.profile.status]}</p>
                </section>
                <div className="grid gap-4 md:grid-cols-3">
                  <DestinationCard
                    to="/app/sinh_vien/profile/edit"
                    title="Thông tin cá nhân"
                    description="Cập nhật họ tên và mã sinh viên."
                  />
                  <DestinationCard
                    to="/app/sinh_vien/history"
                    title="Lịch sử học tập"
                    description="Xem kết quả từng buổi theo lớp, môn và năm học."
                    tone={1}
                  />
                  <DestinationCard
                    to="/app/sinh_vien/achievements"
                    title="Danh hiệu"
                    description="Những danh hiệu cá nhân được hệ thống ghi nhận."
                    tone={2}
                  />
                </div>
              </>
            )}
            {section !== "overview" && (
              <>
                <div className="flex flex-wrap gap-3">
                  <NativeSelect
                    label={
                      section === "history"
                        ? "Môn học trong lịch sử"
                        : "Môn học của danh hiệu"
                    }
                    value={subjectId}
                    onChange={(v) => filter("subjectId", v)}
                    options={[
                      { value: "", label: "Tất cả môn" },
                      ...Array.from(
                        new Map(
                          resource.data.members.map((m) => [
                            m.subjectId,
                            {
                              value: String(m.subjectId),
                              label: m.subjectName,
                            },
                          ]),
                        ).values(),
                      ),
                    ]}
                  />
                  <NativeSelect
                    label={
                      section === "history"
                        ? "Năm học trong lịch sử"
                        : "Năm học của danh hiệu"
                    }
                    value={academicYearId}
                    onChange={(v) => filter("academicYearId", v)}
                    options={[
                      { value: "", label: "Tất cả năm" },
                      ...Array.from(
                        new Map(
                          resource.data.members.map((m) => [
                            m.academicYearId,
                            {
                              value: String(m.academicYearId),
                              label: m.academicYearName,
                            },
                          ]),
                        ).values(),
                      ),
                    ]}
                  />
                  {section === "history" && (
                    <NativeSelect
                      label="Lớp trong lịch sử"
                      value={classId}
                      onChange={(v) => filter("classId", v)}
                      options={[
                        { value: "", label: "Tất cả lớp" },
                        ...resource.data.members
                          .filter(
                            (m) =>
                              (!subjectId ||
                                String(m.subjectId) === subjectId) &&
                              (!academicYearId ||
                                String(m.academicYearId) === academicYearId),
                          )
                          .map((m) => ({
                            value: String(m.courseClassId),
                            label: m.className,
                          })),
                      ]}
                    />
                  )}
                </div>
              </>
            )}
            {section === "achievements" && (
              <>
                <h2 className="text-xl font-semibold">Danh hiệu</h2>
                {!resource.data.awards.length ? (
                  <p className="text-muted-foreground">
                    Chưa có danh hiệu trong phạm vi đã chọn.
                  </p>
                ) : (
                  <div className="flex flex-wrap gap-3">
                    {resource.data.awards.map((a) => (
                      <Card className="space-y-2 p-4" key={a.id}>
                        <span className="status-badge status-positive">
                          {a.label}
                        </span>
                        <p className="text-sm text-muted-foreground">
                          {a.subjectId
                            ? resource.data!.members.find(
                                (m) => m.subjectId === a.subjectId,
                              )?.subjectName || `Môn #${a.subjectId}`
                            : "Không gắn môn học"}{" "}
                          ·{" "}
                          {a.academicYearId
                            ? resource.data!.members.find(
                                (m) => m.academicYearId === a.academicYearId,
                              )?.academicYearName ||
                              `Năm học #${a.academicYearId}`
                            : "Không gắn năm học"}
                        </p>
                        <p className="text-xs text-muted-foreground">
                          {new Date(a.earnedAtMs).toLocaleString("vi-VN")}
                        </p>
                      </Card>
                    ))}
                  </div>
                )}
              </>
            )}
            {section === "history" && (
              <>
                <h2 className="text-xl font-semibold">Lịch sử học tập</h2>
                {!resource.data.history.items.length ? (
                  <EmptyState
                    title="Chưa có lịch sử"
                    description="Các buổi bạn đã tham dự sẽ xuất hiện tại đây, kể cả khi quyền vào lớp đã thay đổi."
                  />
                ) : (
                  <div className="space-y-4">
                    {resource.data.history.items.map((l) => (
                      <Card key={l.lessonId} className="space-y-3 p-5">
                        <h3 className="font-semibold">
                          Buổi {l.number}: {l.title}
                        </h3>
                        <p className="text-sm text-muted-foreground">
                          {l.subjectName} · {l.academicYearName} · {l.className}
                        </p>
                        <p>
                          {statusNames[l.status]} · Điểm buổi: {l.score}
                        </p>
                        <ul className="space-y-1 text-sm">
                          {l.games.map((g) => (
                            <li key={g.gameId}>
                              {g.position}.{" "}
                              {modeNames[g.mode as Mode] || g.mode} ·{" "}
                              {g.participating
                                ? "Đã tham gia"
                                : "Không tham gia"}{" "}
                              · {g.score} điểm
                            </li>
                          ))}
                        </ul>
                        {resource.data!.members.some(
                          (m) =>
                            m.courseClassId === l.classId &&
                            m.status === "APPROVED",
                        ) && (
                          <Button asChild variant="outline">
                            <Link to={`/app/sinh_vien/lessons/${l.lessonId}`}>
                              Xem buổi học
                            </Link>
                          </Button>
                        )}
                      </Card>
                    ))}
                  </div>
                )}
                <div className="flex flex-wrap gap-3">
                  <Button
                    variant="outline"
                    disabled={!page}
                    onClick={() => setPage((p) => p - 1)}
                  >
                    Lịch sử trước
                  </Button>
                  <span className="self-center text-sm">
                    Trang {page + 1} · {resource.data.history.totalElements}{" "}
                    buổi
                  </span>
                  <Button
                    variant="outline"
                    disabled={
                      (page + 1) * 10 >= resource.data.history.totalElements
                    }
                    onClick={() => setPage((p) => p + 1)}
                  >
                    Lịch sử sau
                  </Button>
                </div>
              </>
            )}
          </>
        )
      )}
    </StudentFrame>
  );
}
export { StudentIdentityEditor } from "./identity-editor";
