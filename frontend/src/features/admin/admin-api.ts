import { api, ApiError } from "@/lib/api";
import type { Course, Role } from "@/lib/types";
export interface Named {
  id: number;
  code: string;
  name: string;
}
export interface Semester extends Named {
  academicYearId: number;
}
export interface Offering {
  id: number;
  semesterId: number;
  subjectId: number;
}
export interface Catalog {
  years: Named[];
  semesters: Semester[];
  subjects: Named[];
  offerings: Offering[];
  classes: (Course & { offeringId: number; semesterId: number })[];
}
export interface AdminUser {
  id: number;
  username: string;
  displayName: string;
  role: Role;
  maSinhVien: string | null;
  status: string;
  banVersion: number;
  deletedAtMs: number | null;
  banned: boolean;
}
export interface Page<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
}
export const academicBase = "/api/admin/academic";
export async function loadCatalog(signal: AbortSignal): Promise<Catalog> {
  const [years, semesters, subjects, offerings, classes] = await Promise.all([
    api<Named[]>(`${academicBase}/years`, { signal }),
    api<Semester[]>(`${academicBase}/semesters`, { signal }),
    api<Named[]>(`${academicBase}/subjects`, { signal }),
    api<Offering[]>(`${academicBase}/offerings`, { signal }),
    api<Catalog["classes"]>(`${academicBase}/classes`, { signal }),
  ]);
  return { years, semesters, subjects, offerings, classes };
}
export function offeringLabel(catalog: Catalog, offeringId: number) {
  const offering = catalog.offerings.find((o) => o.id === offeringId);
  const semester = catalog.semesters.find((s) => s.id === offering?.semesterId);
  return [
    catalog.years.find((y) => y.id === semester?.academicYearId)?.name,
    semester?.name,
    catalog.subjects.find((s) => s.id === offering?.subjectId)?.name,
  ]
    .filter(Boolean)
    .join(" / ");
}
const messages: Record<string, string> = {
  DUPLICATE_RESOURCE:
    "Mã hoặc dữ liệu này đã tồn tại. Vui lòng kiểm tra danh sách.",
  RESOURCE_IN_USE:
    "Dữ liệu đang được sử dụng. Không thể xóa dữ liệu đã gắn với học kỳ, môn hoặc lớp.",
  RESOURCE_NOT_FOUND: "Dữ liệu không còn tồn tại. Hãy tải lại danh sách.",
  INVALID_REQUEST: "Thông tin chưa hợp lệ. Vui lòng kiểm tra các trường.",
  INVALID_RESOURCE: "Dữ liệu liên kết không hợp lệ. Hãy tải lại danh sách.",
  PARENT_CHANGE_FORBIDDEN: "Không thể chuyển học kỳ sang năm học khác.",
  STALE_VERSION:
    "Tài khoản đã thay đổi ở nơi khác. Hãy làm mới danh sách rồi xác nhận lại.",
  USERNAME_TAKEN: "Tên đăng nhập đã được sử dụng.",
};
export function adminError(error: unknown) {
  return error instanceof ApiError
    ? messages[error.code] || error.message
    : error instanceof Error
      ? error.message
      : "Thao tác chưa thành công.";
}
export function usersUrl(role: Role | "", query = "", page = 0, status = "") {
  const params = new URLSearchParams({
    q: query,
    page: String(page),
    size: "20",
  });
  if (role) params.set("role", role);
  if (status) params.set("status", status);
  return `/api/admin/users?${params}`;
}

export async function loadClassCatalog(signal: AbortSignal) {
  const [catalog, teachers] = await Promise.all([
    loadCatalog(signal),
    availableTeachers(signal),
  ]);
  return { ...catalog, teachers };
}
export async function availableTeachers(
  signal: AbortSignal,
): Promise<AdminUser[]> {
  const teachers: AdminUser[] = [];
  let page = 0,
    total = 0;
  do {
    const result = await api<Page<AdminUser>>(
      usersUrl("GIANG_VIEN", "", page, "ACTIVE"),
      { signal },
    );
    teachers.push(
      ...result.items.filter(
        (t) => !t.banned && !t.deletedAtMs && t.status === "ACTIVE",
      ),
    );
    total = result.totalElements;
    page++;
  } while (page * 20 < total);
  return teachers;
}
export function validTeachers(course: Course, teachers: AdminUser[]) {
  return teachers.filter((t) => course.teacherIds.includes(t.id));
}
