import { ErrorState, Loading } from "@/components/feedback";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import {
  loadClassCatalog,
  offeringLabel,
  validTeachers,
} from "@/features/admin/admin-api";
import { AdminFrame } from "@/features/admin/admin-shared";
import { useResource } from "@/hooks/use-resource";
import { Link, useParams } from "react-router-dom";
export function ClassPage() {
  const { id } = useParams();
  const resource = useResource(
    async (signal) => {
      const catalog = await loadClassCatalog(signal);
      const course = catalog.classes.find((c) => String(c.id) === id);
      if (!course) throw new Error("Không tìm thấy lớp.");
      return {
        catalog,
        course,
        teachers: validTeachers(course, catalog.teachers),
      };
    },
    [id],
  );
  return (
    <AdminFrame
      title={resource.data?.course.name || "Thông tin lớp"}
      description="Thông tin tổ chức và phân công của đúng lớp đang mở."
    >
      <Button asChild variant="outline">
        <Link to="/app/admin/classes">Về danh sách lớp</Link>
      </Button>
      {resource.loading ? (
        <Loading />
      ) : resource.error ? (
        <ErrorState message={resource.error} retry={resource.reload} />
      ) : (
        resource.data && (
          <>
            <Card className="space-y-4 p-6">
              <h2 className="text-xl font-semibold">Thông tin tổ chức lớp</h2>
              <dl className="account-details">
                <dt>Mã lớp</dt>
                <dd>{resource.data.course.code}</dd>
                <dt>Môn / học kỳ / năm học</dt>
                <dd>
                  {offeringLabel(
                    resource.data.catalog,
                    resource.data.course.offeringId,
                  )}
                </dd>
                <dt>Số buổi bắt buộc</dt>
                <dd>{resource.data.course.requiredLessons}</dd>
              </dl>
            </Card>
            <Card className="space-y-4 p-6">
              <h2 className="text-xl font-semibold">
                Giảng viên phụ trách hợp lệ
              </h2>
              {resource.data.teachers.length ? (
                <ul className="space-y-2">
                  {resource.data.teachers.map((t) => (
                    <li key={t.id}>
                      {t.displayName} · {t.username}
                    </li>
                  ))}
                </ul>
              ) : (
                <p className="text-muted-foreground">
                  Chưa có giảng viên hợp lệ phụ trách lớp này. Các phân công cũ
                  không xác nhận quyền truy cập hiện tại của tài khoản.
                </p>
              )}
              <Button asChild>
                <Link to="/app/admin/assignments">Mở phân công giảng viên</Link>
              </Button>
            </Card>
          </>
        )
      )}
    </AdminFrame>
  );
}
