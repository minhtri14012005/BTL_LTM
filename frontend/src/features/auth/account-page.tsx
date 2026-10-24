import { PageHeader } from "@/components/page-layout";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { useSession } from "@/lib/session";
import { roleNames, statusNames } from "@/lib/types";
import { UserRound } from "lucide-react";
import { Link } from "react-router-dom";
export function AccountPage() {
  const { user } = useSession();
  if (!user) return null;
  return (
    <div className="max-w-3xl space-y-6">
      <PageHeader title="Tài khoản của bạn" eyebrow="KHÔNG GIAN CÁ NHÂN" />
      <Card>
        <CardHeader>
          <div className="mb-2 flex size-14 items-center justify-center rounded-2xl bg-accent text-primary">
            <UserRound />
          </div>
          <CardTitle>{user.hoTen}</CardTitle>
          <p className="text-sm text-muted-foreground">
            {roleNames[user.role]}
          </p>
        </CardHeader>
        <CardContent>
          <Tabs defaultValue="identity">
            <TabsList aria-label="Thông tin tài khoản">
              <TabsTrigger value="identity">Thông tin</TabsTrigger>
              <TabsTrigger value="session">Phiên truy cập</TabsTrigger>
            </TabsList>
            <TabsContent value="identity">
              <dl className="account-details">
                <dt>Họ và tên</dt>
                <dd>{user.hoTen}</dd>
                <dt>Tên đăng nhập</dt>
                <dd>{user.username}</dd>
                {user.role === "SINH_VIEN" && (
                  <>
                    <dt>Mã sinh viên</dt>
                    <dd>{user.maSinhVien || "Chưa bổ sung"}</dd>
                  </>
                )}
                <dt>Trạng thái</dt>
                <dd>{statusNames[user.status] || user.status}</dd>
              </dl>
            </TabsContent>
            <TabsContent value="session">
              <p className="text-sm leading-7 text-muted-foreground">
                Phiên đăng nhập được máy chủ xác thực. Khi đăng xuất hoặc phiên
                bị thu hồi, quyền truy cập và kết nối trực tiếp được kết thúc.
              </p>
            </TabsContent>
          </Tabs>
        </CardContent>
      </Card>
      {user.role === "SINH_VIEN" && (
        <Button asChild>
          <Link to="/app/sinh_vien/profile/edit">
            Cập nhật thông tin cá nhân
          </Link>
        </Button>
      )}
    </div>
  );
}
