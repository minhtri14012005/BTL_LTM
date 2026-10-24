import { ArrowUpRight, ChevronRight } from "lucide-react";
import type { ReactNode } from "react";
import { Link, NavLink, useLocation } from "react-router-dom";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogTitle,
} from "./ui/dialog";

export function PageHeader({
  title,
  description,
  eyebrow,
  actions,
}: {
  title: string;
  description?: string;
  eyebrow?: string;
  actions?: ReactNode;
}) {
  return (
    <header className="page-heading">
      <div>
        {eyebrow && <p className="eyebrow">{eyebrow}</p>}
        <h1>{title}</h1>
        {description && <p className="page-description">{description}</p>}
      </div>
      {actions && <div className="page-actions">{actions}</div>}
    </header>
  );
}
export function SectionLinks({
  items,
  label,
}: {
  items: { to: string; label: string; end?: boolean }[];
  label: string;
}) {
  return (
    <nav aria-label={label} className="section-links">
      {items.map((item) => (
        <NavLink key={item.to} to={item.to} end={item.end}>
          {item.label}
        </NavLink>
      ))}
    </nav>
  );
}
export function DestinationCard({
  to,
  title,
  description,
  tone = 0,
}: {
  to: string;
  title: string;
  description: string;
  tone?: number;
}) {
  return (
    <Link to={to} className={`destination-card tone-${tone}`}>
      <div className="flex items-start justify-between gap-3">
        <h2 className="font-semibold">{title}</h2>
        <ArrowUpRight className="size-5 shrink-0" />
      </div>
      <p className="mt-2 text-sm leading-6 text-muted-foreground">
        {description}
      </p>
    </Link>
  );
}
export function FormDialog({
  open,
  onOpenChange,
  title,
  description,
  children,
  wide = false,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  title: string;
  description?: string;
  children: ReactNode;
  wide?: boolean;
}) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className={wide ? "max-w-4xl" : "max-w-xl"}>
        <DialogTitle className="pr-8 text-xl font-semibold">
          {title}
        </DialogTitle>
        <DialogDescription className="mb-5 mt-2 text-sm text-muted-foreground">
          {description || "Hoàn tất thông tin bên dưới rồi lưu thay đổi."}
        </DialogDescription>
        {children}
      </DialogContent>
    </Dialog>
  );
}
const labels: Record<string, string> = {
  academic: "Đào tạo",
  classes: "Lớp học",
  assignments: "Phân công",
  users: "Tài khoản đào tạo",
  account: "Tài khoản",
  banks: "Ngân hàng nội dung",
  profile: "Hồ sơ",
  history: "Lịch sử học tập",
  achievements: "Danh hiệu",
  edit: "Chỉnh sửa",
  new: "Tạo mới",
  lessons: "Buổi học",
  settings: "Cấu hình buổi học",
  members: "Thành viên",
  wheel: "Ô vòng quay",
  ranks: "Cấu hình Rank",
  scores: "Bảng điểm",
  games: "Trò chơi",
  boards: "Xếp hạng",
  configuration: "Cấu hình",
  content: "Nội dung",
  results: "Kết quả",
};
export function Breadcrumbs({ home }: { home: string }) {
  const { pathname } = useLocation();
  const parts = pathname.startsWith(home)
    ? pathname.slice(home.length).split("/").filter(Boolean)
    : [];
  return (
    <nav aria-label="Đường dẫn trang" className="breadcrumbs">
      <Link to={home}>Tổng quan</Link>
      {parts.map((part, i) => {
        const name =
          labels[part] ||
          (/^\d+$/.test(part)
            ? parts[i - 1] === "games"
              ? `Trò ${part}`
              : `#${part}`
            : part);
        const to = `${home}/${parts.slice(0, i + 1).join("/")}`;
        const invalidParent =
          part === "games" || (part === "lessons" && i === 0);
        return (
          <span key={to}>
            <ChevronRight className="size-3 shrink-0" />
            {i === parts.length - 1 || invalidParent ? (
              <span aria-current={i === parts.length - 1 ? "page" : undefined}>
                {name}
              </span>
            ) : (
              <Link to={to}>{name}</Link>
            )}
          </span>
        );
      })}
    </nav>
  );
}
