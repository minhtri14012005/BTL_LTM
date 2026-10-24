import { ApiError } from "@/lib/api";
import type { Lesson as BaseLesson } from "@/lib/types";
export type Mode = "LUCKY_WHEEL" | "QUIZ" | "FILL_BLANK" | "ASSIGNMENT";
export type ContentMode = Exclude<Mode, "LUCKY_WHEEL">;
export const modeNames: Record<Mode, string> = {
  LUCKY_WHEEL: "Vòng quay may mắn",
  QUIZ: "Quiz",
  FILL_BLANK: "Điền từ vào chỗ trống",
  ASSIGNMENT: "Bài tập",
};
export type Game = BaseLesson["games"][number] & {
  mode: Mode;
  title: string;
  participating: boolean;
  visible?: boolean;
  durationMs?: number;
  questionCount?: number | null;
  wheelCells?: string[] | null;
  selectedCount?: number;
};
export interface Lesson extends Omit<BaseLesson, "games"> {
  attending: boolean;
  courseClassId: number;
  revision: number;
  games: Game[];
}
export interface Question {
  content: string;
  options?: Record<string, string> | null;
  correctAnswer?: string | null;
  acceptedAnswers?: string[] | null;
  matchingPolicy?: "EXACT" | "CASE_INSENSITIVE" | null;
  imageRef?: string | null;
}
export interface Subject { id: number; code: string; name: string; }
export interface Bank {
  subjectId: number;
  subjectCode: string;
  subjectName: string;
  id: number;
  ownerUserId: number;
  title: string;
  visibility: "PUBLIC" | "PRIVATE";
  mode: ContentMode;
  questionCount: number;
  revision: number;
  questions: Question[];
}
export interface Content {
  subjectId: number;
  subjectName: string;
  gameId: number;
  sourceQuizId: number;
  revision: number;
  mode: ContentMode;
  title: string;
  questions: Question[];
}
export interface Member {
  studentId: number;
  hoTen: string;
  maSinhVien: string;
  status: string;
  revision: number;
}
export interface Participant {
  userId: number;
  hoTen: string;
  maSinhVien: string;
  attending: boolean;
  eligible: boolean;
  games: number[];
  scores: Record<string, number>;
  correct: Record<string, number>;
}
export interface Wheel {
  selectedForChallenge?: boolean;
  notification?: string;
  revision: number;
  status: string;
  pool: number[];
  selected: number[];
  cells: string[];
  grades: Record<string, number>;
  challengeIndex?: number;
  spinStartedAtMs?: number;
  spinEndsAtMs?: number;
}
export interface Submission {
  id: number;
  studentId: number;
  text: string;
  receivedAtMs: number;
  answerValue?: string;
}
export interface Assignment {
  latest: Submission[];
  grades: { studentId: number; submissionId: number; score: number }[];
  deadlineAtMs: number | null;
}
export interface Snapshot {
  questionCounts: Record<string, number>;
  quiz: Record<
    string,
    {
      questionIndex: number;
      correct: boolean | number;
      answerValue?: string;
      receivedAtMs: number;
    }[]
  >;
  fillBlank: Record<
    string,
    {
      questionIndex: number;
      correct: boolean | number;
      answerValue?: string;
      receivedAtMs: number;
    }[]
  >;
  lesson: Lesson;
  serverTimeMs: number;
  wheel: Wheel | null;
  window: {
    submitted: boolean;
    questionCount: number;
    gameId: number;
    position: number;
    token: number;
    questionIndex: number;
    status: string;
    deadlineAtMs: number;
    question: Question | null;
  } | null;
  assignment: Record<string, Assignment>;
  participants: Participant[];
}
export interface Board {
  classId: number;
  students: {
    userId: number;
    hoTen: string;
    maSinhVien: string;
    score: number;
    position: number;
    rank: string | null;
  }[];
}
export interface Range {
  name: string;
  from: number;
  to: number | null;
}
export interface RankConfig {
  revision: number;
  ranges: Range[];
}
const errors: Record<string, string> = {
  STUDENT_ACCOUNT_UNAVAILABLE: "Tài khoản sinh viên đang bị ADMIN hạn chế hoặc đã xóa. Không thể duyệt hoặc khôi phục thành viên trong trạng thái này.",
  RESTORE_SOURCE_CLASS_REQUIRED: "Chỉ giảng viên phụ trách lớp đã thực hiện thao tác loại mới được khôi phục trong phạm vi môn/năm học.",
  CLASS_AWAITING_TEACHER: "Lớp chưa có giảng viên tiếp quản. Vui lòng chờ quản trị viên phân công giảng viên mới.",
  INVALID_RANK_RANGES:
    "Khoảng Rank hoặc tên mức chưa hợp lệ. Kiểm tra thứ tự và khoảng chồng lấn.",
  INVALID_SHARE_CODE: "Mã setting phải gồm 32 ký tự lấy từ chức năng chia sẻ.",
  RANK_SHARE_NOT_FOUND: "Không tìm thấy mã setting này.",
  GAME_NOT_FOUND: "Trò không tồn tại hoặc đang được ẩn.",
  GAME_CONFIG_FROZEN: "Trò đã bắt đầu, cấu hình chung được giữ nguyên.",
  INVALID_QUESTION_COUNT:
    "Số câu sử dụng phải lớn hơn 0 và không vượt số câu hợp lệ của bộ.",
  INVALID_GAME_DURATION: "Thời gian mỗi câu phải từ 1 giây trở lên.",
  INVALID_GAME_SCORE:
    "Điểm phải từ 0 đến ngân sách và tối đa hai chữ số thập phân.",
  INVALID_WHEEL_CELLS:
    "Mỗi ô cần có nội dung tối đa 500 ký tự; lớp có tối đa 100 ô.",
  INVALID_WHEEL_K: "Số người k phải nằm trong tập tham gia hợp lệ.",
  LESSON_HIDDEN: "Công bố buổi học trước khi bắt đầu.",
  GAME_NOT_RUNNING: "Trò chưa chạy hoặc đã kết thúc. Hãy làm mới trạng thái.",
  STALE_REVISION: "Dữ liệu đã thay đổi. Hãy làm mới trước khi lưu lại.",
  STALE_WINDOW: "Cửa sổ thời gian đã thay đổi. Hãy tải lại trạng thái.",
  STALE_SUBMISSION: "Sinh viên đã có bản nộp mới. Hãy chấm bản mới nhất.",
  STALE_SUBMISSION_GRADE: "Có bản nộp mới chưa được chấm.",
  CLASS_NOT_ASSIGNED: "Bạn không được phân công lớp này.",
  COMPLETED_LESSON_CANNOT_DELETE:
    "Buổi đã hoàn thành được giữ lại để bảo toàn lịch sử, không thể xóa.",
  CONTENT_MODE_FROZEN:
    "Trò đã có nội dung; giữ loại trò để bảo toàn snapshot. Có thể sửa nội dung tại trang trò chơi.",
  TEMPLATE_NOT_COMPLETED: "Chỉ sao chép cấu hình từ buổi đã hoàn thành.",
  TEMPLATE_SUBJECT_MISMATCH: "Buổi nguồn phải thuộc cùng môn học.",
  AUTOMATIC_GAME_CONTROL: "Trò tự chuyển câu theo deadline chung của Server.",
  LESSON_LIMIT: "Lớp đã tạo đủ số buổi quy định.",
  LESSON_CONFIG_FROZEN: "Buổi đã bắt đầu, không thể sửa kế hoạch.",
  SUBJECT_NOT_FOUND: "Môn học không còn tồn tại. Hãy chọn lại môn do ADMIN tạo.",
  CONTENT_SUBJECT_MISMATCH: "Bộ nội dung phải thuộc đúng môn học của lớp.",
  CONTENT_FROZEN: "Trò đã bắt đầu, không thể sửa nội dung.",
  GAME_CONTENT_REQUIRED: "Hãy chọn và lưu nội dung trước khi bắt đầu trò.",
  PREVIOUS_GAME_INCOMPLETE: "Cần hoàn tất trò trước.",
  INVALID_WHEEL_POOL:
    "Tập quay phải gồm sinh viên đã tham gia hợp lệ trò vòng quay.",
  WHEEL_CONFIG_REQUIRED: "Lớp chưa có ô thử thách.",
  SUBMISSION_NOT_GRADED: "Còn bản nộp chưa được chấm.",
  SUBMISSIONS_STILL_OPEN: "Cần đóng nhận bài trước khi chốt điểm.",
  INVALID_REQUEST: "Dữ liệu chưa hợp lệ. Kiểm tra nội dung và các trường nhập.",
  INVALID_BUDGET: "Điểm phải có tối đa hai chữ số thập phân.",
  LESSON_BUDGET_EXCEEDED: "Tổng ngân sách không được vượt 100 điểm.",
  INVALID_THREE_SLOTS:
    "Cần Vòng quay ở vị trí 1 và hai trò khác nhau ở vị trí 2, 3.",
  WINDOW_ALREADY_OPEN: "Câu hiện tại vẫn đang mở.",
  COMMAND_UNAVAILABLE: "Máy chủ chưa xử lý được lệnh. Hãy tải lại trạng thái.",
};
export function teachingError(e: unknown) {
  const text = e instanceof Error ? e.message : "Thao tác chưa thành công.";
  return errors[e instanceof ApiError ? e.code : text] || text;
}
export function cents(value: string) {
  return /^\d+(\.\d{1,2})?$/.test(value)
    ? Math.round(Number(value) * 100)
    : NaN;
}
export function planError(slots: { mode: Mode; budget: string }[]) {
  if (
    slots.length !== 3 ||
    slots[0].mode !== "LUCKY_WHEEL" ||
    new Set(slots.map((s) => s.mode)).size !== 3
  )
    return errors.INVALID_THREE_SLOTS;
  const values = slots.map((s) => cents(s.budget));
  if (values.some((n) => !Number.isFinite(n) || n < 0 || n > 10000))
    return errors.INVALID_BUDGET;
  return values.reduce((a, b) => a + b, 0) > 10000
    ? errors.LESSON_BUDGET_EXCEEDED
    : "";
}
export function questionError(mode: ContentMode, questions: Question[]) {
  if (!questions.length || questions.length > 50) return "Cần 1–50 câu hỏi.";
  if (mode === "ASSIGNMENT" && questions.length !== 1)
    return "Bài tập có đúng một đề.";
  for (const q of questions) {
    if (!q.content.trim() || q.content.length > 5000)
      return "Nội dung phải có 1–5000 ký tự.";
    if (
      mode === "QUIZ" &&
      (!["A", "B", "C", "D"].every(
        (k) => q.options?.[k]?.trim() && q.options[k].length <= 2000,
      ) ||
        !["A", "B", "C", "D"].includes(q.correctAnswer || ""))
    )
      return "Quiz cần bốn lựa chọn và một đáp án đúng.";
    if (
      mode === "FILL_BLANK" &&
      (q.content.split("{{blank}}").length !== 2 ||
        !q.acceptedAnswers?.length ||
        q.acceptedAnswers.length > 20 ||
        q.acceptedAnswers.some((a) => !a.trim() || a.length > 300) ||
        !q.matchingPolicy)
    )
      return "Mỗi câu cần đúng một {{blank}}, 1–20 đáp án và quy tắc so khớp.";
  }
  return "";
}
