export const messages = {
  ACCOUNT_TEMP_BANNED:"Tài khoản đang bị khóa tạm thời.",ACCOUNT_INDEFINITE_BANNED:"Tài khoản bị khóa vô thời hạn.",SESSION_REVOKED:"Phiên đăng nhập đã bị thu hồi. Hãy đăng nhập lại.",STALE_VERSION:"Chế tài đã thay đổi. Tải trạng thái mới trước khi gửi lệnh khác.",BAN_DOWNGRADE_FORBIDDEN:"Cần gỡ ban vô thời hạn trước khi ban tạm bằng yêu cầu mới.",BAN_ADMIN_FORBIDDEN:"Không thể ban ADMIN.",ORPHAN_WAITING_REQUIRED:"Chỉ đóng Room WAITING không có Game ACTIVE.",HOST_STILL_AVAILABLE:"Host còn quyền sử dụng; không thể đóng phòng này bằng quyền quản trị.",
  GAME_KICKED:"Bạn đã bị quản trị viên đưa ra khỏi trận này.",GAME_OPERATION_PENDING:"Server đang hoàn tất thao tác trong trận. Hãy thử lại cùng yêu cầu.",GAME_NOT_ACTIVE:"Trận không còn ACTIVE.",KICK_HOST_FORBIDDEN:"Không thể Kick Host.",KICK_ADMIN_FORBIDDEN:"Không thể Kick ADMIN.",KICK_PLAYER_REQUIRED:"Chỉ Kick Player thuộc trận đang chơi.",REQUEST_ID_REUSED:"Mã yêu cầu đã được dùng cho nội dung khác.",ADMIN_UNAVAILABLE:"Chưa thể xử lý yêu cầu quản trị. Hãy thử lại cùng yêu cầu.",
  INVALID_HINT_TIMELINE:"Mốc gợi ý phải tăng dần và nhỏ hơn thời lượng câu của màn; hãy sửa bộ câu hoặc tăng thời gian màn.", INVALID_ARRANGEMENT:"Chọn đủ mọi mảnh / mục đúng một lần; không gửi ID ngoài câu hiện tại.", INTRO_CLOSED:"Màn giới thiệu đã kết thúc.", ALREADY_READY:"Bạn đã sẵn sàng cho màn này.", INVALID_ANSWER_KIND:"Kiểu đáp án không phù hợp với chế độ của câu.", MODE_NOT_IMPLEMENTED:"Chế độ này chưa được hỗ trợ chơi.", MODE_IMMUTABLE:"Không thể đổi chế độ của bộ đã tạo.", INVALID_QUESTION_COUNT:"Số câu Start phải khớp kế hoạch các màn.",
  QUESTION_CLOSED:"Câu đã đóng; Server không nhận thêm đáp án.", DECISION_CLOSED:"Giai đoạn quyết định đã kết thúc.", ALREADY_ANSWERED:"Server đã có đáp án của bạn cho câu này.", ALREADY_SPUN:"Bạn đã dùng Spin cho câu này.", SPIN_AFTER_STAR:"Star riêng đã khóa Spin của câu này.", SPIN_NOT_AVAILABLE:"Không còn lượt Spin.", STAR_NOT_AVAILABLE:"Hope Star đã được dùng.", STAR_FORBIDDEN_HARDSHIP:"Khó khăn không cho phép Hope Star.", HISTORY_NOT_READY:"Trận chưa có kết quả cuối được lưu.", GAME_NOT_FOUND:"Không tìm thấy trận.", SERVICE_UNAVAILABLE:"Server chưa khả dụng. Hãy lấy lại trạng thái trước khi tiếp tục.",
  INVALID_CREDENTIALS: "Tên đăng nhập hoặc mật khẩu không đúng.", USERNAME_TAKEN: "Tên đăng nhập đã được sử dụng.",
  UNAUTHENTICATED: "Phiên đăng nhập đã hết hạn. Hãy đăng nhập lại.", CSRF_INVALID: "Phiên gửi yêu cầu đã đổi. Tải lại trang rồi thử lại.",
  FORBIDDEN: "Bạn không có quyền thực hiện thao tác này.", QUIZ_AUTHOR_CANNOT_PLAY: "Tác giả chỉ có thể quan sát bộ câu hỏi của mình.",
  QUIZ_NOT_FOUND: "Không tìm thấy bộ câu hỏi hoặc bạn không được phép xem.", ROOM_NOT_FOUND: "Không tìm thấy phòng đang chờ với mã này.",
  NOT_ENOUGH_PLAYERS: "Cần ít nhất 3 người tham gia chơi để bắt đầu.", NOT_ENOUGH_QUESTIONS: "Bộ câu hỏi chưa đủ số câu để bắt đầu.",
  USER_ACTIVE_GAME: "Có thành viên đang ở một trận khác, kể cả người quan sát.", INVALID_STATE: "Trạng thái phòng đã thay đổi. Hãy lấy trạng thái mới.",
  REVISION_CONFLICT: "Dữ liệu đã được cập nhật ở nơi khác. Hãy tải lại trước khi sửa.", ROOM_FULL: "Phòng đã đủ người chơi.",
  INVALID_REQUEST_ID: "Yêu cầu thử lại phải giữ nguyên nội dung ban đầu.", INVALID_MESSAGE: "Yêu cầu không đúng định dạng.",
  VIDEO_TOO_LARGE:"Video tối đa20 MiB.", INVALID_VIDEO:"Cần MP4 H.264/AAC-LC, có hình và tiếng, tối đa120 giây/1080p.", VIDEO_NOT_FOUND:"Video chưa được lưu hoặc không thuộc bộ này.", VIDEO_UNAVAILABLE:"Không đọc được video local. Kiểm tra file và backup.",
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
