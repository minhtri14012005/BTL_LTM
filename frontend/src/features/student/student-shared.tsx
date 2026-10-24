import { PageHeader } from "@/components/page-layout";
import { NativeSelect as Select } from "@/features/admin/admin-shared";
import { ApiError } from "@/lib/api";
import { realtime, useConnection } from "@/lib/session";
import { useEffect, type ReactNode } from "react";
export {
  FormError,
  Notice,
  ServerCountdown,
  Textarea,
  useTeachingAction as useStudentAction,
} from "@/features/teaching/teaching-shared";
export function NativeSelect({
  label,
  value,
  onChange,
  options,
}: {
  label: string;
  value: string;
  onChange: (value: string) => void;
  options: { value: string; label: string }[];
}) {
  return (
    <label className="min-w-0 flex-1 space-y-1 text-sm">
      <span>{label}</span>
      <Select
        aria-label={label}
        value={value}
        onChange={(e) => onChange(e.target.value)}
      >
        {options.map((o) => (
          <option key={o.value} value={o.value}>
            {o.label}
          </option>
        ))}
      </Select>
    </label>
  );
}
export function StudentFrame({
  title,
  children,
}: {
  title: string;
  children: ReactNode;
}) {
  return (
    <div className="space-y-6">
      <PageHeader title={title} />
      {children}
    </div>
  );
}
export function useStudentUpdates(reload: () => void) {
  const connection = useConnection();
  useEffect(() => {
    if (connection === "connected") reload();
  }, [connection, reload]);
  useEffect(
    () =>
      realtime.listen((e) => {
        if (
          [
            "SCORES_UPDATED",
            "SCORES_INVALIDATED",
            "CLASS_LESSONS_UPDATED",
            "CLASS_MEMBERSHIP_UPDATED",
            "LESSON_ACCESS_REVOKED",
          ].includes(e.type)
        )
          reload();
      }),
    [reload],
  );
}
export function studentError(e: unknown) {
  const key =
    e instanceof ApiError ? e.code : e instanceof Error ? e.message : "";
  return (
    (
      {
        WINDOW_CLOSED:
          "Hạn đã đóng hoặc đã đổi. Đọc lại trạng thái trước khi gửi tiếp.",
        ALREADY_SUBMITTED:
          "Server đã nhận câu trả lời này. Đọc lại trạng thái để xem xác nhận.",
        GAME_PARTICIPATION_REQUIRED: "Bạn cần tham gia trò trước khi gửi bài.",
        SUBJECT_YEAR_REMOVED:
          "Bạn đã bị loại khỏi môn trong năm học này. Lịch sử của bạn vẫn được giữ.",
        FORBIDDEN:
          "Bạn chưa được phép vào lớp hoặc quyền tham gia đã bị thu hồi.",
        LESSON_NOT_FOUND:
          "Buổi học chưa công khai hoặc bạn không có quyền truy cập.",
        IMAGE_NOT_AVAILABLE: "Hình ảnh không còn được phép xem.",
      } as Record<string, string>
    )[key] || (e instanceof Error ? e.message : "Thao tác chưa thành công.")
  );
}
export interface Profile {
  userId: number;
  hoTen: string;
  maSinhVien: string;
  status: string;
  stats: {
    attendedLessons: number;
    completedLessons: number;
    completedGames: number;
    totalPoints: number;
    quizQuestions: number;
    quizCorrect: number;
  };
  achievements: Award[];
}
export interface Award {
  id: number;
  type: string;
  label: string;
  earnedAtMs: number;
  subjectId: number | null;
  academicYearId: number | null;
}
export interface HistoryLesson {
  lessonId: number;
  classId: number;
  className: string;
  subjectId: number;
  subjectName: string;
  academicYearId: number;
  academicYearName: string;
  number: number;
  title: string;
  status: string;
  score: number;
  games: {
    gameId: number;
    position: number;
    mode: string;
    status: string;
    participating: boolean;
    score: number;
  }[];
}
export interface History {
  items: HistoryLesson[];
  page: number;
  size: number;
  totalElements: number;
}
