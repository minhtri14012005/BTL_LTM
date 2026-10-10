export const MODE_LABELS={QUIZ:"Quiz",RIDDLE:"Đố mẹo",SONG:"Đoán tên bài hát",VIETNAMESE_PUZZLE:"Vua Tiếng Việt",CLUES:"Truy tìm dấu vết",IMAGE_WORD:"Đuổi hình bắt chữ",ORDERING:"Sắp xếp trình tự"};
export const PLAYABLE_MODES=["QUIZ","RIDDLE","VIETNAMESE_PUZZLE","ORDERING","IMAGE_WORD","SONG","CLUES"];
export const OPTIONS = ["A", "B", "C", "D"];
export const messages = {
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
export function quizValues(title, visibility, questions, mode="QUIZ") {
  if (!title.trim() || title.length > 200) throw new Error("Tên bộ câu hỏi cần 1–200 ký tự.");
  if (!["PUBLIC","PRIVATE"].includes(visibility) || questions.length < 1 || questions.length > 50) throw new Error("Cần 1–50 câu và quyền hiển thị hợp lệ.");
  if(!PLAYABLE_MODES.includes(mode))throw new Error("Chế độ này chưa hỗ trợ biên soạn.");
  questions.forEach((q, i) => {
    if(mode==="VIETNAMESE_PUZZLE" || mode==="ORDERING") {
      const items=q[mode==="VIETNAMESE_PUZZLE"?"pieces":"items"];
      if(!q.content.trim() || q.content.length>5000 || !Array.isArray(items) || items.length<2 || items.length>100 || items.some(v=>!v || typeof v.text!=="string" || !v.text.length || v.text.length>300 || mode==="ORDERING"&&!v.text.trim() || !/^[A-Za-z0-9_-]{1,64}$/.test(v.id)) || new Set(items.map(v=>v.id)).size!==items.length || !Array.isArray(q.correctOrder) || q.correctOrder.length!==items.length || new Set(q.correctOrder).size!==items.length || q.correctOrder.some(id=>!items.some(v=>v.id===id)))throw new Error(`Câu ${i+1}: cần2–100 mảnh / mục có ID riêng và đủ thứ tự đúng.`);
      if(mode==="VIETNAMESE_PUZZLE" && (!Array.isArray(q.acceptedAnswers)||q.acceptedAnswers.length<1||q.acceptedAnswers.length>20||q.acceptedAnswers.some(a=>typeof a!=="string"||!a.trim()||a.length>300)))throw new Error("Vua Tiếng Việt cần1–20 đáp án chữ được chấp nhận.");
      return;
    }
    if(mode==="CLUES") {
      if(!Array.isArray(q.hints)||q.hints.length<1||q.hints.length>20||q.hints.some((h,j)=>!h||!Number.isSafeInteger(h.offsetMs)||h.offsetMs<0||j>0&&h.offsetMs<=q.hints[j-1].offsetMs||typeof h.text!=="string"||!h.text.trim()||h.text.length>2000))throw new Error("Câu "+(i+1)+": cần1–20 gợi ý với mốc tăng dần, không trùng và nội dung tối đa2000 ký tự.");
    }
    if(mode==="CLUES" || mode==="RIDDLE" || mode==="IMAGE_WORD" || mode==="SONG") {if(mode==="SONG" && !/^sha256:[0-9a-f]{64}$/.test(q.mediaRef||""))throw new Error(`Câu ${i+1}: cần upload video có hình và tiếng.`);if(mode==="IMAGE_WORD" && !/^sha256:[0-9a-f]{64}$/.test(q.imageRef||""))throw new Error(`Câu ${i+1}: cần upload một ảnh gợi từ/cụm từ.`);if(!q.content.trim() || q.content.length>5000 || !Array.isArray(q.acceptedAnswers) || q.acceptedAnswers.length<1 || q.acceptedAnswers.length>20 || q.acceptedAnswers.some(a=>typeof a!=="string" || !a.trim() || a.length>300))throw new Error(`Câu ${i+1}: nhập câu đố và 1–20 đáp án, mỗi đáp án tối đa300 ký tự.`);return;}
    if (!q.content.trim() || q.content.length > 5000 || Object.keys(q.options).length !== 4 ||
      OPTIONS.some(k => !q.options[k]?.trim() || q.options[k].length > 2000) || !OPTIONS.includes(q.correctAnswer)) throw new Error(`Câu ${i+1}: nhập nội dung, đủ 4 lựa chọn và chọn một đáp án đúng.`);
  });
  if(mode==="VIETNAMESE_PUZZLE" || mode==="ORDERING")return {title:title.trim(),visibility,mode,questions:questions.map(q=>({content:q.content.trim(),[mode==="VIETNAMESE_PUZZLE"?"pieces":"items"]:q[mode==="VIETNAMESE_PUZZLE"?"pieces":"items"].map(v=>({...v})),correctOrder:[...q.correctOrder],...(mode==="VIETNAMESE_PUZZLE"?{acceptedAnswers:q.acceptedAnswers.map(a=>a.trim())}:{})}))};
  if(mode==="CLUES" || mode==="RIDDLE" || mode==="IMAGE_WORD" || mode==="SONG")return {title:title.trim(),visibility,mode,questions:questions.map(q=>({content:q.content.trim(),acceptedAnswers:q.acceptedAnswers.map(a=>a.trim()),...(mode==="CLUES"?{hints:q.hints.map(h=>({offsetMs:h.offsetMs,text:h.text.trim()}))}:mode==="IMAGE_WORD"?{imageRef:q.imageRef}:mode==="SONG"?{mediaRef:q.mediaRef}:{})}))};
  return {title:title.trim(), visibility, mode, questions:questions.map(q => ({content:q.content.trim(), options:Object.fromEntries(OPTIONS.map(k => [k,q.options[k].trim()])), correctAnswer:q.correctAnswer, imageRef:q.imageRef || null}))};
}
export function roomValues(data, stages=null) {
  if(stages!==null) {
    const config={name:data.name.trim(),maxPlayers:Number(data.maxPlayers),hostParticipation:data.hostParticipation,stages:stages.map(s=>({mode:s.mode,quizId:Number(s.quizId),questionCount:Number(s.questionCount),questionDurationMs:Math.round(Number(s.seconds)*1000)}))};
    if(!config.name || data.name.length>200 || !Number.isSafeInteger(config.maxPlayers) || config.maxPlayers<3 || config.maxPlayers>100 || !["PLAYER","SPECTATOR"].includes(config.hostParticipation))throw new Error("Nhập tên phòng, 3–100 chỗ chơi và vai trò Host hợp lệ.");
    if(!config.stages.length || config.stages.length>7 || new Set(config.stages.map(s=>s.mode)).size!==config.stages.length || config.stages.reduce((n,s)=>n+s.questionCount,0)>50 || config.stages.some(s=>!PLAYABLE_MODES.includes(s.mode) || !Number.isSafeInteger(s.quizId) || s.quizId<1 || !Number.isInteger(s.questionCount) || s.questionCount<1 || s.questionCount>50 || !Number.isSafeInteger(s.questionDurationMs) || s.questionDurationMs<1))throw new Error("Mỗi chế độ một màn; chọn bộ đúng loại, 1–50 câu/màn, tổng tối đa50 câu và thời gian lớn hơn0.");
    return config;
  }
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
  if(room.configVersion===2) {
    if(!room.stages?.length || room.stages.some(s=>s.deleted || !PLAYABLE_MODES.includes(s.mode)))return "Kế hoạch có bộ đã xóa hoặc chế độ chưa hỗ trợ. Hãy sửa cấu hình.";
    const total=room.stages.reduce((n,s)=>n+s.questionCount,0);
    if(!Number.isInteger(questionCount) || questionCount!==total || total<1 || total>50)return "Số câu Start phải bằng tổng kế hoạch và không vượt50 câu.";
    if(room.members.some(m=>m.participation==="PLAYER" && room.stages.some(s=>s.ownerUserId===m.userId)))return "Tác giả của một bộ trong trận chỉ được quan sát.";
    return "";
  }
  if (!Number.isInteger(questionCount) || questionCount < 10 || questionCount > 50 || (available != null && questionCount > available)) return "Chọn 10–50 câu, không vượt số câu của bộ đã chọn.";
  if (room.quizDeleted) return "Bộ câu hỏi đã bị xóa. Hãy chọn bộ khác.";
  return "";
}
