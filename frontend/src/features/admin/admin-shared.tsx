import { NavLink, Link } from "react-router-dom";
import { BookOpen, Users, School, ArrowRight, RefreshCw } from "lucide-react";
import type { ComponentProps, ReactNode } from "react";
import { Card, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";
export const adminModules = [
  {
    path: "academic",
    title: "Đào tạo",
    description: "Năm học, học kỳ và môn học.",
    icon: BookOpen,
  },
  {
    path: "classes",
    title: "Lớp & phân công",
    description: "Tạo lớp, quy định số buổi và giao giảng viên.",
    icon: School,
  },
  {
    path: "users",
    title: "Tài khoản đào tạo",
    description: "Cấp tài khoản giảng viên, quản lý sinh viên.",
    icon: Users,
  },
];
export function AdminLinks() {
  return (
    <section
      aria-label="Các nhóm quản lý"
      className="grid gap-4 lg:grid-cols-3"
    >
      {adminModules.map(({ path, title, description, icon: Icon }, i) => (
        <Card key={path} className={`tone-${i}`}>
          <CardHeader>
            <Icon className="size-6 text-primary" />
            <CardTitle>
              <Link
                to={`/app/admin/${path}`}
                className="flex items-center justify-between gap-2 rounded focus-visible:ring-2"
              >
                {title}
                <ArrowRight className="size-4 shrink-0" />
              </Link>
            </CardTitle>
            <p className="text-sm leading-6 text-muted-foreground">
              {description}
            </p>
          </CardHeader>
        </Card>
      ))}
    </section>
  );
}
export function AdminFrame({
  title,
  description,
  children,
  reload,
}: {
  title: string;
  description: string;
  children: ReactNode;
  reload?: () => void;
}) {
  return (
    <div className="space-y-6">
      <nav aria-label="Quản lý đào tạo" className="flex flex-wrap gap-2">
        <NavLink to="/app/admin" end className="admin-nav-link">
          Tổng quan
        </NavLink>
        {adminModules.map((m) => (
          <NavLink
            key={m.path}
            to={`/app/admin/${m.path}`}
            className="admin-nav-link"
          >
            {m.title}
          </NavLink>
        ))}
      </nav>
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <span className="eyebrow">QUẢN TRỊ ĐÀO TẠO</span>
          <h1 className="mt-2 text-2xl font-semibold sm:text-3xl">{title}</h1>
          <p className="mt-2 max-w-3xl text-sm leading-6 text-muted-foreground">
            {description}
          </p>
        </div>
        {reload && (
          <Button variant="outline" onClick={reload}>
            <RefreshCw />
            Làm mới
          </Button>
        )}
      </div>
      {children}
    </div>
  );
}
export function NativeSelect({
  className,
  ...props
}: ComponentProps<"select">) {
  return (
    <select
      className={cn(
        "flex min-h-11 w-full min-w-0 max-w-full rounded-xl border border-input bg-card px-3 py-2 text-base outline-none focus-visible:ring-2 focus-visible:ring-ring disabled:opacity-60",
        className,
      )}
      {...props}
    />
  );
}
export function FormError({ message }: { message: string }) {
  return message ? (
    <p
      role="alert"
      className="rounded-xl bg-destructive/5 p-3 text-sm leading-6 text-destructive"
    >
      {message}
    </p>
  ) : null;
}
export function Pagination({
  page,
  total,
  size = 20,
  onChange,
}: {
  page: number;
  total: number;
  size?: number;
  onChange: (page: number) => void;
}) {
  return (
    <div className="flex flex-wrap items-center justify-between gap-3 text-sm">
      <p className="text-muted-foreground">
        {total} tài khoản · Trang {page + 1}/
        {Math.max(1, Math.ceil(total / size))}
      </p>
      <div className="flex gap-2">
        <Button
          variant="outline"
          disabled={page === 0}
          onClick={() => onChange(page - 1)}
        >
          Trang trước
        </Button>
        <Button
          variant="outline"
          disabled={(page + 1) * size >= total}
          onClick={() => onChange(page + 1)}
        >
          Trang sau
        </Button>
      </div>
    </div>
  );
}
