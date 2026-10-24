import { EmptyState, ErrorState, Loading } from "@/components/feedback";
import { FormDialog, SectionLinks } from "@/components/page-layout";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Table } from "@/components/ui/table";
import { useResource } from "@/hooks/use-resource";
import { useState } from "react";
import { Link, useLocation } from "react-router-dom";
import { loadClassCatalog, offeringLabel, validTeachers } from "./admin-api";
import { AdminFrame } from "./admin-shared";
import { AssignClasses } from "./assign-classes";
import { CreateClass } from "./create-class";

export function AdminClassesPage() {
  const resource = useResource(loadClassCatalog, []);
  const [query, setQuery] = useState("");
  const [creating, setCreating] = useState(false);
  const [unassigned, setUnassigned] = useState(false);
  const assigning = useLocation().pathname.endsWith("/assignments");
  return (
    <AdminFrame
      title={assigning ? "Phân công giảng viên" : "Danh sách lớp học"}
      description="Mỗi lớp gắn với một môn trong học kỳ và có số buổi bắt buộc. Bạn có thể phân công một giảng viên vào nhiều lớp."
      reload={resource.reload}
    >
      <SectionLinks
        label="Quản lý lớp học"
        items={[
          { to: "/app/admin/classes", label: "Danh sách lớp", end: true },
          { to: "/app/admin/assignments", label: "Phân công giảng viên" },
        ]}
      />
      {!assigning && (
        <Button onClick={() => setCreating(true)}>Tạo lớp học</Button>
      )}
      {resource.loading ? (
        <Loading />
      ) : resource.error ? (
        <ErrorState message={resource.error} retry={resource.reload} />
      ) : (
        resource.data && (
          <>
            <FormDialog
              open={creating}
              onOpenChange={setCreating}
              title="Tạo lớp học"
            >
              <CreateClass
                catalog={resource.data}
                saved={() => {
                  setCreating(false);
                  resource.reload();
                }}
              />
            </FormDialog>
            {assigning ? (
              <AssignClasses catalog={resource.data} saved={resource.reload} />
            ) : (
              <>
                <section className="space-y-4">
                  <label className="flex items-center gap-2 text-sm">
                    <input
                      type="checkbox"
                      checked={unassigned}
                      onChange={(e) => setUnassigned(e.target.checked)}
                    />
                    Chỉ lớp chưa có giảng viên hợp lệ
                  </label>
                  <div className="flex flex-wrap items-center justify-between gap-3">
                    <h2 className="text-xl font-semibold">
                      Danh sách lớp ({resource.data.classes.length})
                    </h2>
                    <Input
                      className="sm:max-w-sm"
                      aria-label="Tìm lớp quản lý"
                      placeholder="Tìm tên, mã lớp hoặc môn…"
                      value={query}
                      onChange={(e) => setQuery(e.target.value)}
                    />
                  </div>
                  {!resource.data.classes
                    .filter(
                      (c) =>
                        !unassigned ||
                        !validTeachers(c, resource.data!.teachers).length,
                    )
                    .some((c) =>
                      `${c.code} ${c.name} ${offeringLabel(resource.data!, c.offeringId)}`
                        .toLocaleLowerCase("vi")
                        .includes(query.toLocaleLowerCase("vi")),
                    ) ? (
                    <EmptyState
                      title={
                        resource.data.classes.length
                          ? "Không tìm thấy lớp phù hợp"
                          : "Chưa có lớp học"
                      }
                      description="Mở môn theo học kỳ, sau đó tạo lớp và đặt số buổi bắt buộc."
                    />
                  ) : (
                    <Card className="overflow-hidden">
                      <Table>
                        <thead>
                          <tr>
                            <th>Lớp</th>
                            <th>Môn / học kỳ</th>
                            <th>Số buổi bắt buộc</th>
                            <th>Phân công</th>
                          </tr>
                        </thead>
                        <tbody>
                          {resource.data.classes
                            .filter(
                              (c) =>
                                !unassigned ||
                                !validTeachers(c, resource.data!.teachers)
                                  .length,
                            )
                            .filter((c) =>
                              `${c.code} ${c.name} ${offeringLabel(resource.data!, c.offeringId)}`
                                .toLocaleLowerCase("vi")
                                .includes(query.toLocaleLowerCase("vi")),
                            )
                            .map((c) => (
                              <tr key={c.id}>
                                <td>
                                  <Link
                                    className="font-semibold text-primary underline-offset-4 hover:underline"
                                    to={`/app/admin/classes/${c.id}`}
                                  >
                                    {c.name}
                                  </Link>
                                  <p className="mt-1 text-xs text-muted-foreground">
                                    {c.code}
                                  </p>
                                </td>
                                <td>
                                  {offeringLabel(resource.data!, c.offeringId)}
                                </td>
                                <td>{c.requiredLessons}</td>
                                <td>
                                  {validTeachers(c, resource.data!.teachers)
                                    .length
                                    ? `${validTeachers(c, resource.data!.teachers).length} giảng viên hợp lệ`
                                    : c.teacherIds.length
                                      ? "Cần phân công giảng viên hợp lệ"
                                      : "Chưa có giảng viên phụ trách"}
                                  {!validTeachers(c, resource.data!.teachers)
                                    .length && (
                                    <Link
                                      className="mt-2 block text-primary underline"
                                      to="/app/admin/assignments"
                                    >
                                      Phân công người tiếp quản
                                    </Link>
                                  )}
                                </td>
                              </tr>
                            ))}
                        </tbody>
                      </Table>
                    </Card>
                  )}
                </section>
              </>
            )}
          </>
        )
      )}
    </AdminFrame>
  );
}
