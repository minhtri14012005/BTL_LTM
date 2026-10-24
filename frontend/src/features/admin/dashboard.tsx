import { ErrorState, Loading } from "@/components/feedback";
import { DestinationCard } from "@/components/page-layout";
import { Card } from "@/components/ui/card";
import { useResource } from "@/hooks/use-resource";
import { useSession } from "@/lib/session";
import { loadClassCatalog, validTeachers } from "./admin-api";
import { AdminFrame, AdminLinks } from "./admin-shared";
export function AdminDashboard() {
  const { user } = useSession();
  const resource = useResource(loadClassCatalog, []);
  return (
    <AdminFrame
      title={`Xin chào, ${user!.hoTen}!`}
      description="Tổng quan dữ liệu đào tạo và lối vào các nhóm quản lý."
      reload={resource.reload}
    >
      <AdminLinks />
      {resource.loading ? (
        <Loading />
      ) : resource.error ? (
        <ErrorState message={resource.error} retry={resource.reload} />
      ) : (
        resource.data && (
          <>
            <div className="grid gap-4 sm:grid-cols-3">
              {[
                [resource.data.years.length, "Năm học trong hệ thống"],
                [resource.data.subjects.length, "Môn học trong hệ thống"],
                [resource.data.classes.length, "Lớp trong hệ thống"],
              ].map(([value, label]) => (
                <Card key={label} className="p-5">
                  <p className="text-2xl font-semibold">{value}</p>
                  <p className="mt-2 text-sm text-muted-foreground">{label}</p>
                </Card>
              ))}
            </div>
            <DestinationCard
              to="/app/admin/assignments"
              title="Kiểm tra phân công"
              description={`${resource.data.classes.filter((c) => !validTeachers(c, resource.data!.teachers).length).length} lớp hiện chưa có giảng viên phụ trách hợp lệ. Xem danh sách và phân công theo trạng thái tài khoản hiện tại.`}
              tone={2}
            />
          </>
        )
      )}
    </AdminFrame>
  );
}
