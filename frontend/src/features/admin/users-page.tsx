import { EmptyState, ErrorState, Loading } from "@/components/feedback";
import { FormDialog } from "@/components/page-layout";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Confirm } from "@/components/ui/confirm";
import { FormField } from "@/components/ui/form";
import { Input } from "@/components/ui/input";
import { Table } from "@/components/ui/table";
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { useResource } from "@/hooks/use-resource";
import { api, post } from "@/lib/api";
import { requestId } from "@/lib/request-id";
import type { Role } from "@/lib/types";
import { roleNames, statusNames } from "@/lib/types";
import { useState } from "react";
import { useSearchParams } from "react-router-dom";
import { toast } from "sonner";
import { adminError, usersUrl, type AdminUser, type Page } from "./admin-api";
import {
  AdminFrame,
  FormError,
  NativeSelect,
  Pagination,
} from "./admin-shared";
import { CreateTeacher } from "./create-teacher";

export function AdminUsersPage() {
  const [params, setParams] = useSearchParams();
  const [creating, setCreating] = useState(false);
  const role: Role | "" =
    params.get("role") === "all"
      ? ""
      : params.get("role") === "GIANG_VIEN"
        ? "GIANG_VIEN"
        : params.get("role") === "ADMIN"
          ? "ADMIN"
          : "SINH_VIEN";
  const [page, setPage] = useState(0),
    [query, setQuery] = useState(""),
    [search, setSearch] = useState(""),
    [status, setStatus] = useState("");
  const resource = useResource(
    (signal) =>
      api<Page<AdminUser>>(usersUrl(role, search, page, status), { signal }),
    [role, search, page, status],
  );
  const [target, setTarget] = useState<AdminUser | null>(null),
    [reason, setReason] = useState(""),
    [pending, setPending] = useState(false),
    [error, setError] = useState("");
  async function sanction() {
    if (!target || pending) return;
    if (!reason.trim()) {
      setError("Vui lòng nhập lý do xác nhận thao tác.");
      return;
    }
    setPending(true);
    setError("");
    try {
      await post(
        `/api/admin/users/${target.id}/${target.status === "EXPELLED" ? "restore" : "expel"}`,
        {
          requestId: requestId(),
          expectedVersion: target.banVersion,
          reason: reason.trim(),
        },
      );
      toast.success(
        target.status === "EXPELLED"
          ? "Đã khôi phục tài khoản."
          : "Đã đuổi học và thu hồi phiên truy cập.",
      );
      setTarget(null);
      resource.reload();
    } catch (error) {
      setError(adminError(error));
    } finally {
      setPending(false);
    }
  }
  return (
    <AdminFrame
      title="Tài khoản đào tạo"
      description="Cấp tài khoản giảng viên và quản lý trạng thái sinh viên. Đuổi học toàn hệ thống giữ nguyên điểm, thành tích và lịch sử."
      reload={resource.reload}
    >
      <Tabs
        value={role || "all"}
        onValueChange={(value) => {
          setParams({ role: value });
          setPage(0);
          setQuery("");
          setSearch("");
          setStatus("");
        }}
      >
        <TabsList aria-label="Loại tài khoản">
          <TabsTrigger value="SINH_VIEN">Sinh viên</TabsTrigger>
          <TabsTrigger value="GIANG_VIEN">Giảng viên</TabsTrigger>
          <TabsTrigger value="all">Toàn bộ</TabsTrigger>
          <TabsTrigger value="ADMIN">Quản trị viên</TabsTrigger>
        </TabsList>
      </Tabs>
      {!role && (
        <p className="text-sm text-muted-foreground">
          Bao gồm tài khoản đã xóa. Trạng thái truy cập được hiển thị theo dữ
          liệu hiện tại của hệ thống.
        </p>
      )}
      {role === "GIANG_VIEN" && (
        <Button onClick={() => setCreating(true)}>
          Cấp tài khoản giảng viên
        </Button>
      )}
      <FormDialog
        open={creating}
        onOpenChange={setCreating}
        title="Cấp tài khoản giảng viên"
      >
        <CreateTeacher
          saved={(user) => {
            setCreating(false);
            setPage(0);
            setQuery(user.username);
            setSearch(user.username);
            setStatus("");
            resource.reload();
          }}
        />
      </FormDialog>
      <section className="space-y-4">
        <form
          className="flex flex-col gap-3 sm:flex-row"
          onSubmit={(e) => {
            e.preventDefault();
            setSearch(query);
            setPage(0);
          }}
        >
          <Input
            aria-label="Tìm tài khoản"
            maxLength={100}
            placeholder="Tên, tài khoản hoặc mã sinh viên…"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
          />
          <NativeSelect
            aria-label="Lọc trạng thái tài khoản"
            className="sm:max-w-56"
            value={status}
            onChange={(e) => {
              setStatus(e.target.value);
              setPage(0);
            }}
          >
            <option value="">Tất cả trạng thái</option>
            <option value="ACTIVE">ACTIVE · Đang hoạt động</option>
            <option value="EXPELLED">EXPELLED · Đã đuổi học</option>
          </NativeSelect>
          <Button type="submit" variant="outline">
            Tìm kiếm
          </Button>
        </form>
        {resource.loading ? (
          <Loading />
        ) : resource.error ? (
          <ErrorState message={resource.error} retry={resource.reload} />
        ) : (
          resource.data && (
            <>
              {!resource.data.items.length ? (
                <EmptyState
                  title="Không có tài khoản phù hợp"
                  description="Thay đổi tìm kiếm hoặc bộ lọc. Sinh viên xuất hiện sau khi đăng ký tài khoản."
                />
              ) : (
                <Card className="overflow-hidden">
                  <Table>
                    <thead>
                      <tr>
                        <th>Họ và tên</th>
                        {role === "SINH_VIEN" && <th>Mã sinh viên</th>}
                        <th>Tài khoản</th>
                        {!role && <th>Vai trò</th>}
                        <th>Trạng thái</th>
                        {role === "SINH_VIEN" && <th>Thao tác</th>}
                      </tr>
                    </thead>
                    <tbody>
                      {resource.data.items.map((user) => (
                        <tr key={user.id}>
                          <td className="font-medium">{user.displayName}</td>
                          {role === "SINH_VIEN" && (
                            <td>{user.maSinhVien || "Chưa bổ sung"}</td>
                          )}
                          <td>{user.username}</td>
                          {!role && <td>{roleNames[user.role]}</td>}
                          <td>
                            <span
                              className={`status-badge ${user.status === "ACTIVE" && !user.banned && !user.deletedAtMs ? "status-positive" : ""}`}
                            >
                              {user.deletedAtMs
                                ? "Đã xóa"
                                : user.status === "EXPELLED"
                                  ? "Đã đuổi học"
                                  : user.banned
                                    ? "Bị cấm truy cập"
                                    : statusNames[user.status] || user.status}
                            </span>
                          </td>
                          {role === "SINH_VIEN" && (
                            <td>
                              <Button
                                size="sm"
                                variant={
                                  user.status === "EXPELLED"
                                    ? "outline"
                                    : "destructive"
                                }
                                onClick={() => {
                                  setTarget(user);
                                  setReason("");
                                  setError("");
                                }}
                              >
                                {user.status === "EXPELLED"
                                  ? "Khôi phục"
                                  : "Đuổi học"}
                              </Button>
                            </td>
                          )}
                        </tr>
                      ))}
                    </tbody>
                  </Table>
                </Card>
              )}
              <Pagination
                page={page}
                total={resource.data.totalElements}
                onChange={setPage}
              />
            </>
          )
        )}
      </section>
      <Confirm
        open={!!target}
        onOpenChange={(open) => {
          if (!pending && !open) setTarget(null);
        }}
        title={
          target?.status === "EXPELLED"
            ? "Khôi phục tài khoản sinh viên?"
            : "Đuổi học sinh viên toàn hệ thống?"
        }
        description={`${target?.displayName} · ${target?.maSinhVien || "Chưa có mã sinh viên"} · ${target?.username}. ${target?.status === "EXPELLED" ? "Tài khoản trở về ACTIVE. Tư cách thành viên đã bị giảng viên loại không được khôi phục tự động." : "Chuyển sang EXPELLED vô thời hạn, chặn đăng nhập và thu hồi phiên/WebSocket. Điểm và lịch sử vẫn được giữ."}`}
        pending={pending}
        onConfirm={sanction}
      >
        <div className="mt-4 space-y-3">
          <FormField label="Lý do">
            {(props) => (
              <Input
                {...props}
                required
                maxLength={240}
                value={reason}
                disabled={pending}
                onChange={(e) => setReason(e.target.value)}
              />
            )}
          </FormField>
          <FormError message={error} />
        </div>
      </Confirm>
    </AdminFrame>
  );
}
