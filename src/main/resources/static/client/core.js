export const OPTIONS = ["A", "B", "C", "D"];
export const messages = {
  QUESTION_CLOSED:"Câu đã đóng; Server không nhận thêm đáp án.", DECISION_CLOSED:"Giai đoạn quyết định đã kết thúc.", ALREADY_ANSWERED:"Server đã có đáp án của bạn cho câu này.", ALREADY_SPUN:"Bạn đã dùng Spin cho câu này.", SPIN_AFTER_STAR:"Star riêng đã khóa Spin của câu này.", SPIN_NOT_AVAILABLE:"Không còn lượt Spin.", STAR_NOT_AVAILABLE:"Hope Star đã được dùng.", STAR_FORBIDDEN_HARDSHIP:"Khó khăn không cho phép Hope Star.", HISTORY_NOT_READY:"Trận chưa có kết quả cuối được lưu.", GAME_NOT_FOUND:"Không tìm thấy trận.", SERVICE_UNAVAILABLE:"Server chưa khả dụng. Hãy lấy lại trạng thái trước khi tiếp tục.",
  INVALID_CREDENTIALS: "Tên đăng nhập hoặc mật khẩu không đúng.", USERNAME_TAKEN: "Tên đăng nhập đã được sử dụng.",
  UNAUTHENTICATED: "Phiên đăng nhập đã hết hạn. Hãy đăng nhập lại.", CSRF_INVALID: "Phiên gửi yêu cầu đã đổi. Tải lại trang rồi thử lại.",
  FORBIDDEN: "Bạn không có quyền thực hiện thao tác này.", QUIZ_AUTHOR_CANNOT_PLAY: "Tác giả chỉ có thể quan sát bộ câu hỏi của mình.",
  QUIZ_NOT_FOUND: "Không tìm thấy bộ câu hỏi hoặc bạn không được phép xem.", ROOM_NOT_FOUND: "Không tìm thấy phòng đang chờ với mã này.",
  NOT_ENOUGH_PLAYERS: "Cần ít nhất 3 người tham gia chơi để bắt đầu.", NOT_ENOUGH_QUESTIONS: "Bộ câu hỏi chưa đủ số câu để bắt đầu.",
  USER_ACTIVE_GAME: "Có thành viên đang ở một trận khác, kể cả người quan sát.", INVALID_STATE: "Trạng thái phòng đã thay đổi. Hãy lấy trạng thái mới.",
  REVISION_CONFLICT: "Dữ liệu đã được cập nhật ở nơi khác. Hãy tải lại trước khi sửa.", ROOM_FULL: "Phòng đã đủ người chơi.",
  INVALID_REQUEST_ID: "Yêu cầu thử lại phải giữ nguyên nội dung ban đầu.", INVALID_MESSAGE: "Yêu cầu không đúng định dạng.",
  INVALID_REQUEST: "Kiểm tra lại các trường đã nhập.", IMAGE_TOO_LARGE: "Ảnh tối đa 2 MiB.", INVALID_IMAGE: "Chọn một ảnh PNG hoặc JPEG hợp lệ.",
  IMAGE_NOT_FOUND: "Ảnh chưa được lưu hoặc không thuộc bộ câu hỏi này.", SESSION_REPLACED: "Kết nối này đã được thay bằng một cửa sổ khác.",
  DISCONNECTED: "Chưa có kết nối thời gian thực. Hãy kết nối lại.", REQUEST_UNCERTAIN: "Chưa nhận được xác nhận. Thử lại cùng yêu cầu để tránh thực hiện hai lần.",
};
export class Failure extends Error {
  constructor(code, retryable = false) { super(messages[code] || "Không thể hoàn tất yêu cầu. Hãy thử lại sau."); this.code = code; this.retryable = retryable; }
}
export function uuid(cryptoApi = globalThis.crypto) {
  const b = cryptoApi.getRandomValues(new Uint8Array(16)); b[6] = (b[6] & 15) | 64; b[8] = (b[8] & 63) | 128;
  const h = [...b].map(x => x.toString(16).padStart(2, "0")).join(""); return `${h.slice(0,8)}-${h.slice(8,12)}-${h.slice(12,16)}-${h.slice(16,20)}-${h.slice(20)}`;
}
export function command(type, id, payload, requestId = uuid()) {
  return Object.freeze({v:1, kind:"COMMAND", requestId, type, target:Object.freeze({kind:"ROOM", id}), questionIndex:null,
    payload:Object.freeze({...payload})});
}
export function latestRoom(previous, incoming) {
  return previous?.id === incoming.id && previous.revision > incoming.revision ? previous : incoming;
}
export function ownerQuestions(quiz, user) { return quiz.ownerUserId === user?.id && Array.isArray(quiz.questions) ? quiz.questions : null; }
export function accountValues(data, register) {
  const username = data.username.trim(); const password = data.password;
  if (!/^[a-zA-Z0-9_]{3,32}$/.test(username)) throw new Error("Tên đăng nhập gồm 3–32 chữ, số hoặc dấu gạch dưới.");
  if (password.length < 8 || password.length > 72 || new TextEncoder().encode(password).length > 72) throw new Error("Mật khẩu gồm 8–72 ký tự, tối đa 72 byte UTF-8.");
  const result = {username, password};
  if (register) { if (!data.displayName.trim() || data.displayName.length > 100) throw new Error("Tên hiển thị cần 1–100 ký tự."); result.displayName = data.displayName.trim(); }
  return result;
}
export function quizValues(title, visibility, questions) {
  if (!title.trim() || title.length > 200) throw new Error("Tên bộ câu hỏi cần 1–200 ký tự.");
  if (!["PUBLIC","PRIVATE"].includes(visibility) || questions.length < 1 || questions.length > 50) throw new Error("Cần 1–50 câu và quyền hiển thị hợp lệ.");
  questions.forEach((q, i) => {
    if (!q.content.trim() || q.content.length > 5000 || Object.keys(q.options).length !== 4 ||
      OPTIONS.some(k => !q.options[k]?.trim() || q.options[k].length > 2000) || !OPTIONS.includes(q.correctAnswer)) throw new Error(`Câu ${i+1}: nhập nội dung, đủ 4 lựa chọn và chọn một đáp án đúng.`);
  });
  return {title:title.trim(), visibility, questions:questions.map(q => ({content:q.content.trim(), options:Object.fromEntries(OPTIONS.map(k => [k,q.options[k].trim()])), correctAnswer:q.correctAnswer, imageRef:q.imageRef || null}))};
}
export function roomValues(data) {
  const config = {quizId:Number(data.quizId), name:data.name.trim(), maxPlayers:Number(data.maxPlayers),
    questionDurationMs:Math.round(Number(data.seconds)*1000), hostParticipation:data.hostParticipation};
  if (!config.name || data.name.length > 200 || !Number.isSafeInteger(config.quizId) || config.quizId < 1 ||
      !Number.isSafeInteger(config.maxPlayers) || config.maxPlayers < 3 || config.maxPlayers > 100 ||
      !Number.isSafeInteger(config.questionDurationMs) || config.questionDurationMs < 1 || !["PLAYER","SPECTATOR"].includes(config.hostParticipation)) throw new Error("Chọn bộ câu hỏi, tên phòng, 3–100 chỗ chơi và thời gian lớn hơn 0.");
  return config;
}
export function startIssue(room, questionCount, available) {
  if (room.status !== "WAITING") return "Phòng phải được mở và đang chờ.";
  if (room.members.filter(m => m.participation === "PLAYER").length < 3) return "Cần ít nhất 3 người chơi; người quan sát không được tính.";
  if (!Number.isInteger(questionCount) || questionCount < 10 || questionCount > 50 || (available != null && questionCount > available)) return "Chọn 10–50 câu, không vượt số câu của bộ đã chọn.";
  if (room.quizDeleted) return "Bộ câu hỏi đã bị xóa. Hãy chọn bộ khác.";
  return "";
}
