import { useState, useEffect } from "react";
import { AdminLinks } from "@/features/admin/admin-shared";
import { Link } from "react-router-dom";
import {
  ArrowRight,
  BookOpen,
  CalendarDays,
  GraduationCap,
  Layers3,
  RefreshCw,
  Search,
} from "lucide-react";
import { api } from "@/lib/api";
import { useSession, realtime } from "@/lib/session";
import type { Course, Membership, Role } from "@/lib/types";
import { statusNames } from "@/lib/types";
import { useResource } from "@/hooks/use-resource";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { EmptyState, ErrorState, Loading } from "@/components/feedback";

interface CourseCard {
  id: number;
  name: string;
  code: string;
  required: number;
  status?: string;
  description: string;
  progress?: { created: number; completed: number };
  pending?: number;
}
interface Dashboard {
  cards: CourseCard[];
  years?: number;
  subjects?: number;
}
async function loadDashboard(
  role: Role,
  signal: AbortSignal,
): Promise<Dashboard> {
  if (role === "SINH_VIEN") {
    const memberships = await api<Membership[]>("/api/student/classes", {
      signal,
    });
    return {
      cards: memberships.map((m) => ({
        id: m.courseClassId,
        name: m.className,
        code: m.classCode,
        required: m.requiredLessons,
        status: m.status,
        description: `${m.subjectName} · ${m.academicYearName}`,
      })),
    };
  }
  const base =
    role === "ADMIN" ? "/api/admin/academic/classes" : "/api/teaching/classes";
  const courses = await api<Course[]>(base, { signal });
  const cards = courses.map((c) => ({
    id: c.id,
    name: c.name,
    code: c.code,
    required: c.requiredLessons,
    description: `${c.teacherIds.length} giảng viên được phân công`,
  }));
  if (role !== "ADMIN") {
    const enriched = await Promise.all(
      cards.map(async (card) => {
        const [progress, members] = await Promise.all([
          api<{ created: number; completed: number }>(
            `/api/teaching/classes/${card.id}/lesson-progress`,
            { signal },
          ),
          api<{ status: string }[]>(
            `/api/teaching/classes/${card.id}/members`,
            { signal },
          ),
        ]);
        return {
          ...card,
          progress,
          pending: members.filter((m) => m.status === "PENDING").length,
        };
      }),
    );
    return {
      cards: enriched,
      subjects: new Set(courses.map((c) => c.subjectId)).size,
    };
  }
  const [years, subjects] = await Promise.all([
    api<unknown[]>("/api/admin/academic/years", { signal }),
    api<unknown[]>("/api/admin/academic/subjects", { signal }),
  ]);
  return { cards, years: years.length, subjects: subjects.length };
}

export function DashboardPage() {
  const { user } = useSession(),
    role = user!.role;
  const { data, loading, error, reload } = useResource(
    (signal) => loadDashboard(role, signal),
    [role, user!.id],
  );
  const [query, setQuery] = useState(""),
    [status, setStatus] = useState("ALL");
  useEffect(
    () =>
      realtime.listen((event) => {
        if (event.type === "CLASS_MEMBERSHIP_UPDATED") reload();
      }),
    [reload],
  );
  const cards =
    data?.cards.filter(
      (c) =>
        `${c.name} ${c.code} ${c.description}`
          .toLocaleLowerCase("vi")
          .includes(query.toLocaleLowerCase("vi")) &&
        (status === "ALL" || c.status === status),
    ) || [];
  return (
    <div className="space-y-8">
      <section className="welcome-banner">
        <div className="relative z-10 max-w-2xl">
          <span className="eyebrow">
            {role === "ADMIN" ? "TỔNG QUAN HỆ THỐNG" : "KHÔNG GIAN LỚP HỌC"}
          </span>
          <h1 className="mt-3 text-2xl font-semibold tracking-tight sm:text-3xl">
            Xin chào, {user!.hoTen}!
          </h1>
          <p className="mt-3 max-w-lg leading-7 text-muted-foreground">
            {role === "ADMIN"
              ? "Theo dõi các năm học, môn học và lớp đang được tổ chức."
              : role === "GIANG_VIEN"
                ? "Các lớp được phân công của bạn, tập trung trong một không gian."
                : "Mỗi buổi học là một bước tiến. Cùng khám phá các lớp học của bạn."}
          </p>
        </div>
        <div className="banner-symbol" aria-hidden="true">
          <GraduationCap />
        </div>
      </section>
      {role === "ADMIN" && <AdminLinks />}
      {loading ? (
        <Loading />
      ) : error ? (
        <ErrorState message={error} retry={reload} />
      ) : (
        data && (
          <>
            <div className="grid gap-4 sm:grid-cols-3">
              <Stat
                icon={<BookOpen />}
                value={data.cards.length}
                label={role === "SINH_VIEN" ? "Lớp đã đăng ký" : "Lớp học"}
              />
              <Stat
                icon={<Layers3 />}
                value={
                  data.subjects ??
                  data.cards.filter((c) => !c.status || c.status === "APPROVED")
                    .length
                }
                label={
                  role === "ADMIN"
                    ? "Môn học"
                    : role === "SINH_VIEN"
                      ? "Lớp đã được duyệt"
                      : "Môn được phân công"
                }
              />
              <Stat
                icon={<CalendarDays />}
                value={
                  data.years ??
                  data.cards.reduce((sum, c) => sum + c.required, 0)
                }
                label={role === "ADMIN" ? "Năm học" : "Tổng số buổi quy định"}
              />
            </div>
            <section className="space-y-5">
              <div className="flex flex-wrap items-center justify-between gap-4">
                <div>
                  <h2 className="text-xl font-semibold">
                    {role === "ADMIN"
                      ? "Các lớp trong hệ thống"
                      : "Lớp học của bạn"}
                  </h2>
                  <p className="mt-1 text-sm text-muted-foreground">
                    {role === "SINH_VIEN"
                      ? "Trạng thái tham gia do giảng viên xét duyệt."
                      : "Thông tin lớp học từ hệ thống đào tạo."}
                  </p>
                </div>
                <Button variant="outline" onClick={reload}>
                  <RefreshCw />
                  Làm mới
                </Button>
              </div>
              {data.cards.length > 0 && (
                <div className="flex flex-col gap-3 sm:flex-row">
                  <div className="relative w-full sm:max-w-sm">
                    <Search className="absolute left-3 top-3.5 size-4 text-muted-foreground" />
                    <Input
                      aria-label="Tìm lớp học"
                      placeholder="Tìm theo tên hoặc mã lớp…"
                      className="pl-10"
                      value={query}
                      onChange={(e) => setQuery(e.target.value)}
                    />
                  </div>
                  {role === "SINH_VIEN" && (
                    <Select value={status} onValueChange={setStatus}>
                      <SelectTrigger aria-label="Trạng thái lớp">
                        <SelectValue />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectItem value="ALL">Tất cả trạng thái</SelectItem>
                        {["APPROVED", "PENDING", "REJECTED", "REMOVED"].map(
                          (s) => (
                            <SelectItem key={s} value={s}>
                              {statusNames[s]}
                            </SelectItem>
                          ),
                        )}
                      </SelectContent>
                    </Select>
                  )}
                </div>
              )}
              {cards.length === 0 ? (
                <EmptyState
                  title={
                    data.cards.length
                      ? "Không tìm thấy lớp phù hợp"
                      : "Chưa có lớp học"
                  }
                  description={
                    data.cards.length
                      ? "Thử tên lớp khác hoặc thay đổi bộ lọc."
                      : role === "SINH_VIEN"
                        ? "Các lớp bạn đăng ký sẽ xuất hiện tại đây sau khi gửi yêu cầu tham gia."
                        : "Lớp học sẽ xuất hiện khi quản trị viên tạo lớp và phân công giảng viên."
                  }
                />
              ) : (
                <div className="course-grid">
                  {cards.map((c, index) => (
                    <Card key={c.id} className="course-card">
                      <CardHeader className={`course-cover tone-${index % 4}`}>
                        <div className="flex items-center justify-between gap-3">
                          <span className="course-code">{c.code}</span>
                          <BookOpen className="size-6 opacity-60" />
                        </div>
                        <CardTitle className="mt-6 text-xl leading-7">
                          {c.name}
                        </CardTitle>
                        <p className="text-sm leading-6 text-foreground/70">
                          {c.description}
                        </p>
                      </CardHeader>
                      <CardContent className="space-y-4 pt-5">
                        {c.progress && (
                          <p className="text-sm leading-6 text-muted-foreground">
                            Đã tạo {c.progress.created}/{c.required} · Hoàn
                            thành {c.progress.completed}/{c.required}
                            <br />
                            {c.pending} yêu cầu chờ duyệt
                          </p>
                        )}
                        <div className="flex flex-wrap items-center justify-between gap-3 text-sm">
                          <span className="flex items-center gap-2 text-muted-foreground">
                            <CalendarDays className="size-4" />
                            {c.required} buổi quy định
                          </span>
                          {c.status && (
                            <span
                              className={`status-badge ${c.status === "APPROVED" ? "status-positive" : ""}`}
                            >
                              {statusNames[c.status] || c.status}
                            </span>
                          )}
                        </div>
                        {!c.status || c.status === "APPROVED" ? (
                          <Button
                            asChild
                            variant="outline"
                            className="w-full justify-between"
                          >
                            <Link to={`classes/${c.id}`}>
                              Xem thông tin lớp
                              <ArrowRight />
                            </Link>
                          </Button>
                        ) : (
                          <p className="text-sm leading-6 text-muted-foreground">
                            {c.status === "PENDING"
                              ? "Bạn có thể xem buổi học khi được giảng viên duyệt."
                              : "Bạn hiện không có quyền truy cập buổi học của lớp này."}
                          </p>
                        )}
                      </CardContent>
                    </Card>
                  ))}
                </div>
              )}
            </section>
          </>
        )
      )}
    </div>
  );
}
function Stat({
  icon,
  value,
  label,
}: {
  icon: React.ReactNode;
  value: number;
  label: string;
}) {
  return (
    <Card className="flex items-center gap-4 p-5">
      <div className="rounded-xl bg-accent p-3 text-primary [&_svg]:size-5">
        {icon}
      </div>
      <div>
        <p className="text-2xl font-semibold tabular-nums">{value}</p>
        <p className="mt-1 text-sm text-muted-foreground">{label}</p>
      </div>
    </Card>
  );
}
