import { EmptyState, ErrorState, Loading } from "@/components/feedback";
import { DestinationCard, FormDialog } from "@/components/page-layout";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { useResource } from "@/hooks/use-resource";
import { api, post } from "@/lib/api";
import { requestId } from "@/lib/request-id";
import { useSession } from "@/lib/session";
import { statusNames, type Membership } from "@/lib/types";
import { useState } from "react";
import { Link, useLocation, useNavigate } from "react-router-dom";
import {
  FormError,
  NativeSelect,
  studentError,
  StudentFrame,
  useStudentAction,
  useStudentUpdates,
} from "./student-shared";
export function StudentDashboard() {
  const { user } = useSession();
  const navigate = useNavigate();
  const listing = useLocation().pathname.endsWith("/classes");
  const [joining, setJoining] = useState(false);
  const resource = useResource(
    async (signal) => {
      const members = await api<Membership[]>("/api/student/classes", {
        signal,
      });
      return { members };
    },
    [user!.id],
  );
  useStudentUpdates(resource.reload);
  const action = useStudentAction();
  const [code, setCode] = useState(""),
    [query, setQuery] = useState(""),
    [status, setStatus] = useState("ALL");
  const members = (resource.data?.members || []).filter(
    (m) =>
      `${m.className} ${m.classCode} ${m.subjectName} ${m.academicYearName}`
        .toLocaleLowerCase("vi")
        .includes(query.toLocaleLowerCase("vi")) &&
      (status === "ALL" || m.status === status),
  );
  return (
    <StudentFrame
      title={listing ? "Lớp học của tôi" : `Xin chào, ${user!.hoTen}!`}
    >
      {!listing && (
        <p className="text-muted-foreground">
          Mở lớp đã được duyệt hoặc theo dõi yêu cầu tham gia của bạn.
        </p>
      )}
      <Button onClick={() => setJoining(true)}>Tham gia bằng mã lớp</Button>
      <FormDialog
        open={joining}
        onOpenChange={setJoining}
        title="Tham gia lớp học"
        description="Nhập mã lớp do giảng viên cung cấp. Yêu cầu cần được giảng viên duyệt."
      >
        <div className="space-y-4">
          <h2 className="text-xl font-semibold">Tham gia bằng mã lớp</h2>
          <form
            className="grid items-end gap-3 sm:grid-cols-[minmax(0,1fr)_auto]"
            onSubmit={(e) => {
              e.preventDefault();
              void action.run(async () => {
                try {
                  await post("/api/student/classes/join", {
                    requestId: requestId(),
                    code: code.trim(),
                  });
                  setCode("");
                  setJoining(false);
                  navigate("/app/sinh_vien/classes");
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
        </div>
      </FormDialog>
      {!listing && (
        <div className="grid gap-4 md:grid-cols-2">
          <DestinationCard
            to="/app/sinh_vien/classes"
            title="Lớp học của tôi"
            description="Xem lớp đã đăng ký, trạng thái tham gia và các buổi học."
          />
          <DestinationCard
            to="/app/sinh_vien/profile"
            title="Hồ sơ & thành tích"
            description="Thông tin cá nhân, danh hiệu và lịch sử học tập."
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
              <div className="flex flex-wrap gap-3">
                <span className="status-badge status-positive">
                  {
                    resource.data.members.filter((m) => m.status === "APPROVED")
                      .length
                  }{" "}
                  lớp đã được duyệt
                </span>
                <span className="status-badge">
                  {
                    resource.data.members.filter((m) => m.status === "PENDING")
                      .length
                  }{" "}
                  yêu cầu đang chờ duyệt
                </span>
              </div>
            )}
            {listing && (
              <>
                <div className="grid items-end gap-3 sm:grid-cols-[minmax(0,1fr)_16rem_auto]">
                  <Input
                    aria-label="Tìm lớp học"
                    value={query}
                    onChange={(e) => setQuery(e.target.value)}
                    placeholder="Tên lớp, mã lớp, môn hoặc năm học"
                  />
                  <NativeSelect
                    label="Trạng thái tham gia"
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
              </>
            )}
            {!(
              listing
                ? members
                : members?.filter((m) => m.status === "APPROVED")
            )?.length ? (
              <EmptyState
                title={
                  resource.data.members.length
                    ? listing
                      ? "Không tìm thấy lớp phù hợp"
                      : "Chưa có lớp được duyệt"
                    : "Chưa có lớp học"
                }
                description="Nhập mã lớp để gửi yêu cầu. Giảng viên sẽ duyệt trước khi bạn vào buổi học."
              />
            ) : (
              <div className="course-grid">
                {(listing
                  ? members
                  : members.filter((m) => m.status === "APPROVED").slice(0, 3)
                ).map((m, i) => (
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
                          <Link
                            to={`/app/sinh_vien/classes/${m.courseClassId}`}
                          >
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
                          to={`/app/sinh_vien/history?classId=${m.courseClassId}&subjectId=${m.subjectId}&academicYearId=${m.academicYearId}`}
                        >
                          Lịch sử học tập của lớp
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
