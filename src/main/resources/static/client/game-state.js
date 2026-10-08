import {uuid} from "./core.js";

export const effects={BONUS:"Tăng thưởng",SAFE:"An toàn",BREAKTHROUGH:"Bứt phá",SPEED:"Tăng tốc",DECISIVE:"Quyết định",HARDSHIP:"Khó khăn"};
export const outcomes={CORRECT:"Đúng",WRONG:"Sai",NO_ANSWER:"Không trả lời",ACCEPTED_UNSCORED:"Đã nhận · chưa chấm"};
export const endReasons={COMPLETED:"Hoàn thành bộ câu hỏi",ONE_SURVIVOR:"Chỉ còn một người chơi",ALL_ELIMINATED:"Tất cả người chơi đã bị loại",CANCELLED:"Host đã hủy trận",SERVER_INTERRUPTED:"Server gián đoạn"};
export function gameCommand(type,id,index,payload={},requestId=uuid()) {
  return Object.freeze({v:1,kind:"COMMAND",requestId,type,target:Object.freeze({kind:"GAME",id}),questionIndex:index,payload:Object.freeze({...payload})});
}
export function permissions(s,userId,connected,pending=false) {
  const active=s?.status==="ACTIVE" && s.runtimeState==="READY" && connected && !pending;
  const playing=active && s.player?.state==="PLAYING";
  const decision=playing && s.phase==="DECISION";
  return {spin:!!(decision && s.player.remainingSpins>0 && !s.player.currentSpin && !s.player.starSelected),
    star:!!(decision && s.player.starAvailable && !s.player.starSelected && s.player.currentSpin!=="HARDSHIP"),
    answer:!!(playing && s.phase==="QUESTION_OPEN" && !s.player.alreadyAnswered),
    cancel:!!(active && s.members.some(m=>m.userId===userId && m.role==="HOST"))};
}
/** Revision is scoped to one Game. Same-revision snapshots/events are consumable. */
export function acceptSnapshot(previous,incoming) {
  return !previous || (incoming.gameSessionId===previous.gameSessionId && (incoming.revision>previous.revision || (incoming.revision===previous.revision && !(incoming.serverTimeMs<previous.serverTimeMs))))?incoming:previous;
}
/** A receipt confirms its original action; never overwrite a newer phase or commit. */
export function applyReceipt(s,ack) {
  if(ack.type==="CANCEL_GAME")return acceptSnapshot(s,ack.payload.finalSnapshot);
  if(!s || ack.target.id!==s.gameSessionId || ack.questionIndex!==s.questionIndex || ack.revision<s.revision || !s.player)return s;
  const p=ack.payload;
  return {...s,revision:ack.revision,player:{...s.player,currentSpin:p.spinEffect,starSelected:p.starSelected,
    remainingSpins:p.remainingSpins,starAvailable:p.starAvailable,remainingSpinPool:p.remainingSpinPool,
    alreadyAnswered:p.alreadyAnswered,selectedOption:p.selectedOption}};
}
export function countdown(deadline,serverTime,sampledAt,now) {
  return deadline==null?null:Math.max(0,deadline-serverTime-Math.max(0,now-sampledAt));
}
