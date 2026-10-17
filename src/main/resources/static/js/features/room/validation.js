import {PLAYABLE_MODES} from "/js/shared/mode-labels.js";
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
