import { useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { api } from "@/lib/api";
import { useSession, refreshSession } from "@/lib/session";
import type { Membership } from "@/lib/types";
import { statusNames } from "@/lib/types";
import { modeNames, type Mode } from "@/features/teaching/teaching-api";
import type { Board } from "@/features/teaching/teaching-api";
import { useResource } from "@/hooks/use-resource";
import { Card } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";
import { Loading, ErrorState, EmptyState } from "@/components/feedback";
import {
  StudentFrame,
  NativeSelect,
  FormError,
  useStudentAction,
  useStudentUpdates,
  type Profile,
  type History,
  type Award,
} from "./student-shared";
export function StudentProfilePage() {
  const { user } = useSession();
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
        api<History>(`/api/learning/history?${query}`, { signal }),
        api<Membership[]>("/api/student/classes", { signal }),
        api<Award[]>(`/api/learning/achievements?${awardsQuery}`, { signal }),
      ]);
      const rankClass = members.find(
        (m) =>
          m.status === "APPROVED" &&
          (!classId || String(m.courseClassId) === classId) &&
          (!subjectId || String(m.subjectId) === subjectId) &&
          (!academicYearId || String(m.academicYearId) === academicYearId),
      );
      const board = rankClass
        ? await api<Board>(
            `/api/learning/classes/${rankClass.courseClassId}/leaderboard`,
            { signal },
          )
        : null;
      return {
        profile,
        history,
        members,
        awards,
        rankClass,
        ownRank: board?.students.find((s) => s.userId === user!.id),
      };
    },
    [user!.id, page, classId, subjectId, academicYearId],
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
    <StudentFrame title="Hồ sơ & thành tích">
      {resource.loading ? (
        <Loading />
      ) : resource.error ? (
        <ErrorState message={resource.error} retry={resource.reload} />
      ) : (
        resource.data && (
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
            <div className="grid gap-4 sm:grid-cols-3">
              {[
                [
                  resource.data.profile.stats.totalPoints,
                  "Tổng điểm đã ghi nhận",
                ],
                [
                  resource.data.profile.stats.completedLessons,
                  "Buổi hoàn thành",
                ],
                [
                  `${resource.data.profile.stats.quizCorrect}/${resource.data.profile.stats.quizQuestions}`,
                  "Số câu Quiz đúng / tổng câu",
                ],
              ].map(([value, label]) => (
                <Card className="p-5" key={label}>
                  <p className="text-2xl font-semibold">{value}</p>
                  <p className="text-sm text-muted-foreground">{label}</p>
                </Card>
              ))}
            </div>
            {resource.data.rankClass && (
              <Card className="space-y-2 p-5">
                <h2 className="font-semibold">
                  Thứ hạng tại {resource.data.rankClass.className}
                </h2>
                <p>
                  Hạng số: {resource.data.ownRank?.position ?? "Chưa có điểm"} ·
                  Điểm trong lớp: {resource.data.ownRank?.score ?? 0}
                </p>
                <span className="status-badge">
                  Rank của lớp: {resource.data.ownRank?.rank || "Chưa có mức"}
                </span>
              </Card>
            )}
            <div className="flex flex-wrap gap-3">
              <NativeSelect
                label="Môn học trong lịch sử"
                value={subjectId}
                onChange={(v) => filter("subjectId", v)}
                options={[
                  { value: "", label: "Tất cả môn" },
                  ...Array.from(
                    new Map(
                      resource.data.members.map((m) => [
                        m.subjectId,
                        { value: String(m.subjectId), label: m.subjectName },
                      ]),
                    ).values(),
                  ),
                ]}
              />
              <NativeSelect
                label="Năm học trong lịch sử"
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
              <NativeSelect
                label="Lớp trong lịch sử"
                value={classId}
                onChange={(v) => filter("classId", v)}
                options={[
                  { value: "", label: "Tất cả lớp" },
                  ...resource.data.members
                    .filter(
                      (m) =>
                        (!subjectId || String(m.subjectId) === subjectId) &&
                        (!academicYearId ||
                          String(m.academicYearId) === academicYearId),
                    )
                    .map((m) => ({
                      value: String(m.courseClassId),
                      label: m.className,
                    })),
                ]}
              />
            </div>
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
                    <p className="text-xs text-muted-foreground">
                      {new Date(a.earnedAtMs).toLocaleString("vi-VN")}
                    </p>
                  </Card>
                ))}
              </div>
            )}
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
                          {g.position}. {modeNames[g.mode as Mode] || g.mode} ·{" "}
                          {g.participating ? "Đã tham gia" : "Không tham gia"} ·{" "}
                          {g.score} điểm
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
                Trang {page + 1} · {resource.data.history.totalElements} buổi
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
        )
      )}
      <StudentIdentityEditor
        key={`${user!.hoTen}:${user!.maSinhVien}`}
        reload={resource.reload}
      />
    </StudentFrame>
  );
}
export function StudentIdentityEditor({ reload }: { reload: () => void }) {
  const { user } = useSession();
  const [name, setName] = useState(user!.hoTen),
    [code, setCode] = useState(user!.maSinhVien || "");
  const action = useStudentAction();
  return (
    <Card className="space-y-4 p-5">
      <h2 className="font-semibold">Cập nhật hồ sơ</h2>
      <form
        className="grid gap-3 sm:grid-cols-2"
        onSubmit={(e) => {
          e.preventDefault();
          if (!name.trim() || !/^[A-Za-z0-9][A-Za-z0-9_-]{0,31}$/.test(code)) {
            action.setError(
              "Nhập họ tên và mã SV gồm 1–32 chữ, số, dấu gạch dưới hoặc gạch ngang.",
            );
            return;
          }
          void action.run(async () => {
            await api("/api/auth/profile", {
              method: "PUT",
              body: JSON.stringify({ hoTen: name.trim(), maSinhVien: code }),
            });
            await refreshSession();
            reload();
          }, "Đã cập nhật hồ sơ.");
        }}
      >
        <label className="space-y-2 text-sm">
          Họ và tên
          <Input
            aria-label="Họ và tên trong hồ sơ"
            required
            maxLength={100}
            value={name}
            disabled={action.pending}
            onChange={(e) => setName(e.target.value)}
          />
        </label>
        <label className="space-y-2 text-sm">
          Mã sinh viên
          <Input
            aria-label="Mã sinh viên trong hồ sơ"
            required
            maxLength={32}
            pattern={"[A-Za-z0-9][A-Za-z0-9_\\-]{0,31}"}
            value={code}
            disabled={action.pending}
            onChange={(e) => setCode(e.target.value)}
          />
        </label>
        <Button type="submit" disabled={action.pending || !name.trim()}>
          Lưu hồ sơ
        </Button>
      </form>
      <FormError message={action.error} />
    </Card>
  );
}
