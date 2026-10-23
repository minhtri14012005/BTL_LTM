import { useState } from "react";
import { Link } from "react-router-dom";
import { api, post } from "@/lib/api";
import { useSession } from "@/lib/session";
import { requestId } from "@/lib/request-id";
import { statusNames, type Membership } from "@/lib/types";
import { useResource } from "@/hooks/use-resource";
import { Button } from "@/components/ui/button";
import { Card, CardHeader, CardTitle, CardContent } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Loading, ErrorState, EmptyState } from "@/components/feedback";
import {
  StudentFrame,
  useStudentAction,
  useStudentUpdates,
  FormError,
  NativeSelect,
  type Profile,
  studentError,
} from "./student-shared";
export function StudentDashboard() {
  const { user } = useSession();
  const resource = useResource(
    async (signal) => {
      const [members, profile] = await Promise.all([
        api<Membership[]>("/api/student/classes", { signal }),
        api<Profile>("/api/learning/profile", { signal }),
      ]);
      return { members, profile };
    },
    [user!.id],
  );
  useStudentUpdates(resource.reload);
  const action = useStudentAction();
  const [code, setCode] = useState(""),
    [query, setQuery] = useState(""),
    [status, setStatus] = useState("ALL");
  const members = resource.data?.members.filter(
    (m) =>
      `${m.className} ${m.classCode} ${m.subjectName} ${m.academicYearName}`
        .toLocaleLowerCase("vi")
        .includes(query.toLocaleLowerCase("vi")) &&
      (status === "ALL" || m.status === status),
  );
  return (
    <StudentFrame title={`Xin chào, ${user!.hoTen}!`}>
      <section className="welcome-banner">
        <p className="eyebrow">LỚP HỌC CỦA TÔI</p>
        <p className="mt-3 font-semibold">
          {user!.hoTen} · {user!.maSinhVien}
        </p>
        <p className="mt-2 text-muted-foreground">
          Tham gia lớp, học cùng giảng viên và theo dõi từng kết quả của bạn.
        </p>
      </section>
      <Card className="space-y-4 p-5">
        <h2 className="text-xl font-semibold">Tham gia bằng mã lớp</h2>
        <form
          className="grid items-end gap-3 sm:grid-cols-[minmax(0,1fr)_16rem_auto]"
          onSubmit={(e) => {
            e.preventDefault();
            void action.run(async () => {
              try {
                await post("/api/student/classes/join", {
                  requestId: requestId(),
                  code: code.trim(),
                });
                setCode("");
                resource.reload();
              } catch (e) {
                throw new Error(studentError(e));
              }
            }, "Đã gửi yêu cầu tham gia lớp.");
          }}
        >
          <Input
            aria-label="Mã tham gia lớp"
            placeholder="Nhập mã do giảng viên cung cấp"
            maxLength={32}
            required
            value={code}
            onChange={(e) => setCode(e.target.value)}
            disabled={action.pending}
          />
          <Button type="submit" disabled={action.pending || !code.trim()}>
            Gửi yêu cầu tham gia
          </Button>
        </form>
        <FormError message={action.error} />
      </Card>
      {resource.loading ? (
        <Loading />
      ) : resource.error ? (
        <ErrorState message={resource.error} retry={resource.reload} />
      ) : (
        resource.data && (
          <>
            <div className="grid gap-4 sm:grid-cols-3">
              {[
                [resource.data.profile.stats.totalPoints, "Điểm tích lũy"],
                [
                  resource.data.profile.stats.completedLessons,
                  "Buổi đã hoàn thành",
                ],
                [resource.data.profile.achievements.length, "Danh hiệu đã đạt"],
              ].map(([value, label]) => (
                <Card className="p-5" key={label}>
                  <p className="text-2xl font-semibold">{value}</p>
                  <p className="text-sm text-muted-foreground">{label}</p>
                </Card>
              ))}
            </div>
            <div className="grid items-end gap-3 sm:grid-cols-[minmax(0,1fr)_16rem_auto]">
              <Input
                aria-label="Tìm lớp học"
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                placeholder="Tên lớp, mã lớp, môn hoặc năm học"
              />
              <NativeSelect
                label="Trạng thái lớp"
                value={status}
                onChange={setStatus}
                options={[
                  { value: "ALL", label: "Tất cả trạng thái" },
                  ...["APPROVED", "PENDING", "REJECTED", "REMOVED"].map(
                    (s) => ({ value: s, label: statusNames[s] }),
                  ),
                ]}
              />
              <Button variant="outline" onClick={resource.reload}>
                Làm mới
              </Button>
            </div>
            {!members?.length ? (
              <EmptyState
                title={
                  resource.data.members.length
                    ? "Không tìm thấy lớp phù hợp"
                    : "Chưa có lớp học"
                }
                description="Nhập mã lớp để gửi yêu cầu. Giảng viên sẽ duyệt trước khi bạn vào buổi học."
              />
            ) : (
              <div className="course-grid">
                {members.map((m, i) => (
                  <Card className="course-card" key={m.courseClassId}>
                    <CardHeader className={`course-cover tone-${i % 4}`}>
                      <p className="course-code">{m.classCode}</p>
                      <CardTitle className="mt-5">{m.className}</CardTitle>
                      <p>
                        {m.subjectName} · {m.academicYearName}
                      </p>
                    </CardHeader>
                    <CardContent className="space-y-4 pt-5">
                      <p>{m.requiredLessons} buổi quy định</p>
                      <span
                        className={`status-badge ${m.status === "APPROVED" ? "status-positive" : ""}`}
                      >
                        {statusNames[m.status]}
                      </span>
                      {m.status === "APPROVED" ? (
                        <Button asChild variant="outline" className="w-full">
                          <Link to={`classes/${m.courseClassId}`}>
                            Xem thông tin lớp
                          </Link>
                        </Button>
                      ) : (
                        <p className="text-sm text-muted-foreground">
                          {m.status === "PENDING"
                            ? "Đang chờ giảng viên xét duyệt. Trạng thái sẽ tự cập nhật."
                            : "Quyền vào buổi học hiện chưa được cấp. Bạn vẫn xem được lịch sử của mình."}
                        </p>
                      )}
                      <Button asChild variant="ghost">
                        <Link
                          to={`/app/sinh_vien/profile?classId=${m.courseClassId}&subjectId=${m.subjectId}&academicYearId=${m.academicYearId}`}
                        >
                          Lịch sử & thành tích lớp
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
