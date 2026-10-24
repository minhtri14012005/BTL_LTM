export type Role = "ADMIN" | "GIANG_VIEN" | "SINH_VIEN";
export interface User {
  id: number;
  username: string;
  displayName: string;
  role: Role;
  hoTen: string;
  maSinhVien: string | null;
  status: string;
  profileComplete: boolean;
}
export interface Course {
  id: number;
  code: string;
  name: string;
  requiredLessons: number;
  subjectId: number;
  academicYearId: number;
  teacherIds: number[];
}
export interface Membership {
  subjectId: number;
  academicYearId: number;
  id: number;
  courseClassId: number;
  status: string;
  classCode: string;
  className: string;
  subjectName: string;
  academicYearName: string;
  requiredLessons: number;
}
export interface Lesson {
  id: number;
  number: number;
  title: string;
  status: string;
  visible: boolean;
  score: number;
  games: {
    id: number;
    position: number;
    modeLabel: string;
    status: string;
    budget: number;
    score: number;
  }[];
}
export const roleNames: Record<Role, string> = {
  ADMIN: "Quản trị viên",
  GIANG_VIEN: "Giảng viên",
  SINH_VIEN: "Sinh viên",
};
export const statusNames: Record<string, string> = {
  PENDING: "Chờ duyệt",
  APPROVED: "Đã được duyệt",
  REJECTED: "Đã từ chối",
  REMOVED: "Đã bị loại",
  DRAFT: "Chưa bắt đầu",
  READY: "Sẵn sàng",
  RUNNING: "Đang diễn ra",
  COMPLETED: "Đã hoàn tất",
  ACTIVE: "Đang hoạt động",
  EXPELLED: "Đã đuổi học",
};
