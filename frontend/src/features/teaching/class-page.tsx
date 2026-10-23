import { useState, useEffect } from "react";
import { realtime } from "@/lib/session";
import { Link, useParams } from "react-router-dom";
import { api, post } from "@/lib/api";
import { requestId } from "@/lib/request-id";
import { useResource } from "@/hooks/use-resource";
import type { Course } from "@/lib/types";
import { statusNames } from "@/lib/types";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Card, CardHeader, CardTitle, CardContent } from "@/components/ui/card";
import { Confirm } from "@/components/ui/confirm";
import { Table } from "@/components/ui/table";
import { Tabs, TabsList, TabsTrigger, TabsContent } from "@/components/ui/tabs";
import { Loading, ErrorState, EmptyState } from "@/components/feedback";
import { type Lesson, type Member } from "./teaching-api";
import {
  TeachingFrame,
  useTeachingAction,
  FormError,
  Notice,
  Textarea,
} from "./teaching-shared";
import { LessonPlan } from "./lesson-plan";
import { RankPanel } from "./rank-panel";
import { LessonStructure } from "@/features/dashboard/lesson-structure";
export function TeachingClassPage() {
  const { id } = useParams();
  const classId = Number(id);
  const [tab, setTab] = useState("lessons"),
    [creating, setCreating] = useState(false);
  const action = useTeachingAction();
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
          event.type === "CLASS_MEMBERSHIP_UPDATED" &&
          event.target?.id === classId
        ) {
          if (creating || tab === "wheel" || tab === "ranks") setChanged(true);
          else resource.reload();
        }
      }),
    [classId, creating, tab, resource.reload],
  );
  return (
    <TeachingFrame title={resource.data?.course.name || "Lớp được phân công"}>
      {changed && (
        <Notice>
          Danh sách thành viên đã thay đổi. Làm mới lớp sau khi lưu các chỉnh
          sửa.
        </Notice>
      )}
      <Button
        variant="outline"
        onClick={() => {
          setChanged(false);
          resource.reload();
        }}
      >
        Làm mới lớp
      </Button>
      {resource.loading ? (
        <Loading />
      ) : resource.error ? (
        <ErrorState message={resource.error} retry={resource.reload} />
      ) : (
        resource.data && (
          <>
            <section className="welcome-banner">
              <p className="eyebrow">{resource.data.course.code}</p>
              <h2 className="mt-3 text-2xl font-semibold">Tiến độ buổi học</h2>
              <p className="mt-3">
                Đã tạo {resource.data.progress.created}/
                {resource.data.progress.required} · Đã hoàn thành{" "}
                {resource.data.progress.completed}/
                {resource.data.progress.required}
              </p>
              <p className="mt-3 break-all text-sm">
                Mã tham gia lớp: <strong>{resource.data.room.joinCode}</strong>
              </p>
              <p className="mt-2 text-sm">
                {
                  resource.data.members.filter((m) => m.status === "PENDING")
                    .length
                }{" "}
                yêu cầu chờ duyệt
              </p>
            </section>
            <Tabs value={tab} onValueChange={setTab}>
              <div className="overflow-x-auto">
                <TabsList aria-label="Quản lý lớp">
                  <TabsTrigger value="lessons">Buổi học</TabsTrigger>
                  <TabsTrigger value="members">Thành viên</TabsTrigger>
                  <TabsTrigger value="wheel">Ô vòng quay</TabsTrigger>
                  <TabsTrigger value="ranks">Bảng điểm & Rank</TabsTrigger>
                </TabsList>
              </div>
              <TabsContent value="lessons">
                <div className="space-y-5">
                  <Button
                    disabled={
                      resource.data.progress.created >=
                        resource.data.progress.required || action.pending
                    }
                    onClick={() => setCreating(!creating)}
                  >
                    {creating ? "Đóng biểu mẫu" : "Tạo buổi mới"}
                  </Button>
                  {creating && (
                    <Card className="p-5">
                      <LessonPlan
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
                            setCreating(false);
                            resource.reload();
                          }, "Đã tạo buổi học.");
                        }}
                      />
                    </Card>
                  )}
                  {!resource.data.lessons.length ? (
                    <EmptyState
                      title="Chưa có buổi học"
                      description="Tạo buổi với Vòng quay bắt buộc và hai trò khác nhau; tổng ngân sách không quá 100."
                    />
                  ) : (
                    <div className="course-grid">
                      {resource.data.lessons.map((lesson) => (
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
                          </CardContent>
                        </Card>
                      ))}
                    </div>
                  )}
                </div>
              </TabsContent>
              <TabsContent value="members">
                <Members
                  classId={classId}
                  members={resource.data.members}
                  refresh={resource.reload}
                />
              </TabsContent>
              <TabsContent value="wheel">
                <WheelConfig classId={classId} />
              </TabsContent>
              <TabsContent value="ranks">
                <RankPanel classId={classId} />
              </TabsContent>
            </Tabs>
          </>
        )
      )}
    </TeachingFrame>
  );
}
function Members({
  classId,
  members,
  refresh,
}: {
  classId: number;
  members: Member[];
  refresh: () => void;
}) {
  const action = useTeachingAction();
  const [remove, setRemove] = useState<Member | null>(null);
  async function decision(member: Member, command: string) {
    await action.run(async () => {
      await post(
        `/api/teaching/classes/${classId}/members/${member.studentId}/${command}`,
        { requestId: requestId(), revision: member.revision },
      );
      setRemove(null);
      refresh();
    }, "Đã cập nhật thành viên.");
  }
  return (
    <div className="space-y-4">
      <Notice>
        Duyệt/từ chối một lần cho mỗi lớp. Loại khỏi lớp sẽ chặn đăng ký các lớp
        cùng môn và cùng năm học; lịch sử được giữ.
      </Notice>
      <FormError message={action.error} />
      {!members.length ? (
        <EmptyState
          title="Chưa có yêu cầu tham gia"
          description="Chia sẻ mã lớp cố định cho sinh viên."
        />
      ) : (
        <Card>
          <Table>
            <thead>
              <tr>
                <th>Mã SV</th>
                <th>Họ tên</th>
                <th>Trạng thái</th>
                <th>Thao tác</th>
              </tr>
            </thead>
            <tbody>
              {members.map((m) => (
                <tr key={m.studentId}>
                  <td>{m.maSinhVien}</td>
                  <td>{m.hoTen}</td>
                  <td>{statusNames[m.status]}</td>
                  <td>
                    <div className="flex flex-wrap gap-2">
                      {m.status === "PENDING" && (
                        <>
                          <Button
                            size="sm"
                            disabled={action.pending}
                            onClick={() => {
                              void decision(m, "approve");
                            }}
                          >
                            Duyệt {m.maSinhVien}
                          </Button>
                          <Button
                            variant="outline"
                            size="sm"
                            disabled={action.pending}
                            onClick={() => {
                              void decision(m, "reject");
                            }}
                          >
                            Từ chối {m.maSinhVien}
                          </Button>
                        </>
                      )}
                      {m.status === "APPROVED" && (
                        <Button
                          variant="destructive"
                          size="sm"
                          disabled={action.pending}
                          onClick={() => setRemove(m)}
                        >
                          Loại {m.maSinhVien}
                        </Button>
                      )}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </Table>
        </Card>
      )}
      <Confirm
        open={!!remove}
        onOpenChange={(open) => {
          if (!action.pending && !open) setRemove(null);
        }}
        title="Loại sinh viên khỏi môn trong năm học?"
        description={`${remove?.hoTen} · ${remove?.maSinhVien}. Sinh viên không thể đăng ký lớp khác cùng môn và năm học. Điểm và lịch sử được giữ.`}
        pending={action.pending}
        onConfirm={() => {
          if (remove) void decision(remove, "remove");
        }}
      />
    </div>
  );
}
function WheelConfig({ classId }: { classId: number }) {
  const resource = useResource(
    (signal) =>
      api<{ revision: number; cells: string[] }>(
        `/api/teaching/classes/${classId}/wheel`,
        { signal },
      ),
    [classId],
  );
  return resource.loading ? (
    <Loading />
  ) : resource.error ? (
    <ErrorState message={resource.error} retry={resource.reload} />
  ) : resource.data ? (
    <WheelConfigEditor
      key={resource.data.revision}
      classId={classId}
      config={resource.data}
      refresh={resource.reload}
    />
  ) : null;
}
function WheelConfigEditor({
  classId,
  config,
  refresh,
}: {
  classId: number;
  config: { revision: number; cells: string[] };
  refresh: () => void;
}) {
  const [cells, setCells] = useState(config.cells);
  const action = useTeachingAction();
  return (
    <Card className="space-y-4 p-5">
      <h2 className="text-xl font-semibold">Ô thử thách dùng chung cho lớp</h2>
      <p className="text-sm text-muted-foreground">
        Tối đa 100 ô; cấu hình đã quay được snapshot riêng, chỉnh sửa này không
        đổi kết quả cũ.
      </p>
      {cells.map((cell, index) => (
        <div className="flex items-start gap-2" key={index}>
          <Input
            aria-label={`Ô thử thách ${index + 1}`}
            maxLength={500}
            value={cell}
            disabled={action.pending}
            onChange={(e) =>
              setCells((old) =>
                old.map((s, i) => (i === index ? e.target.value : s)),
              )
            }
          />
          <Button
            variant="outline"
            disabled={action.pending}
            onClick={() => setCells((old) => old.filter((_, i) => i !== index))}
          >
            Xóa ô {index + 1}
          </Button>
        </div>
      ))}
      <div className="flex flex-wrap gap-3">
        <Button
          variant="outline"
          disabled={action.pending || cells.length >= 100}
          onClick={() => setCells((old) => [...old, ""])}
        >
          Thêm ô thử thách
        </Button>
        <Button
          disabled={action.pending}
          onClick={() => {
            if (cells.some((s) => !s.trim())) {
              action.setError("Mỗi ô cần có nội dung.");
              return;
            }
            void action.run(async () => {
              await api(`/api/teaching/classes/${classId}/wheel`, {
                method: "PUT",
                body: JSON.stringify({ revision: config.revision, cells }),
              });
              refresh();
            }, "Đã lưu cấu hình vòng quay.");
          }}
        >
          Lưu ô thử thách
        </Button>
      </div>
      <FormError message={action.error} />
    </Card>
  );
}
