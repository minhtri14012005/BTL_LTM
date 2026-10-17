import {h,button,notice} from "/js/shared/dom.js";
import {icon} from "/js/shared/icons.js";
import {MODE_LABELS} from "/js/shared/mode-labels.js";

/** Presentation of committed room DTO only; no independent state/subscription. */
export function inviteCard(room) {
  const code=h("strong",{id:"room-code",tabIndex:0,"aria-label":"Mã phòng"},room.roomCode);
  const result=h("span",{id:"copy-room-status",role:"status","aria-live":"polite",className:"muted"});
  return h("section",{className:"card room-invite"},
    h("div",{},h("p",{className:"eyebrow"},"MỜI BẠN CÙNG CHƠI"),h("h2",{},"Chia sẻ mã phòng"),h("p",{className:"muted"},"Mã chỉ dùng để tham gia khi phòng đang chờ.")),
    h("div",{className:"room-code"},code,button("Sao chép mã",async()=>{
      try {await navigator.clipboard.writeText(room.roomCode);result.textContent="Đã sao chép mã phòng.";}
      catch {
        code.focus();const range=document.createRange();range.selectNodeContents(code);
        const selection=window.getSelection();selection.removeAllRanges();selection.addRange(range);
        result.textContent="Đã chọn mã. Dùng Ctrl/Cmd+C hoặc nhấn giữ để sao chép.";
      }
    },{className:"secondary",id:"copy-room-code"})),result);
}

export function rosterCard(app,room,removeControl,synced) {
  const players=room.members.filter(m=>m.participation==="PLAYER").length;
  const status=app.transport.status;
  const ownConnection=status==="ready"?(synced?"Đã đồng bộ phòng":"Đang đồng bộ phòng…"):status==="connecting"?"Đang kết nối…":status==="replaced"?"Kết nối đã được thay thế":"Mất kết nối · giữ nguyên thành viên";
  return h("section",{className:"card room-roster","aria-labelledby":"roster-title"},
    h("div",{className:"card-top"},h("h2",{id:"roster-title"},"Đội hình phòng"),h("span",{id:"player-count",className:"badge"},`${players}/${room.maxPlayers} người chơi`)),
    h("p",{className:"muted roster-summary"},`${room.members.length-players} người quan sát · tối thiểu 3 người chơi để Start`),
    h("div",{id:"roster"},room.members.map(m=>h("div",{className:"member","data-user-id":m.userId},
      h("span",{className:`member-avatar ${m.host?"is-host":""}`,"aria-hidden":"true"},Array.from(m.displayName||m.username||"?")[0].toLocaleUpperCase()),
      h("div",{className:"member-details"},h("strong",{},m.displayName||m.username||`User #${m.userId}`),
        h("small",{},`${m.host?"Host · ":""}${m.participation==="PLAYER"?"Người chơi":"Quan sát"}${m.userId===app.user.id?" · Bạn":""}`),
        m.userId===app.user.id?h("small",{className:"member-connection","data-state":status,role:"status"},ownConnection):null),removeControl(m)))),
    h("p",{className:"muted room-presence-note"},"Danh sách thể hiện tư cách thành viên, không phải trạng thái online của từng người. Ngắt kết nối không tự rời phòng."));
}

export function planCard(room) {
  const stages=room.configVersion===2?room.stages:[{orderIndex:1,mode:"QUIZ",title:room.quizTitle,deleted:room.quizDeleted,questionDurationMs:room.questionDurationMs}];
  return h("section",{className:"card room-plan","aria-labelledby":"plan-title"},
    h("div",{className:"card-top"},h("h2",{id:"plan-title"},"Kế hoạch trận"),h("span",{className:"badge"},`${stages.length} màn`)),
    h("ol",{className:"room-stage-list"},stages.map(s=>h("li",{"data-stage":s.orderIndex},
      h("span",{className:"stage-number","aria-hidden":"true"},s.orderIndex),
      h("div",{},h("p",{className:"eyebrow"},MODE_LABELS[s.mode]||s.mode),h("h3",{},s.title),
        h("p",{className:"muted"},`${s.questionCount==null?"Số câu chọn khi Start":`${s.questionCount} câu`} · ${s.questionDurationMs/1000} giây/câu`),
        s.deleted?notice("Nguồn đã bị xóa. Host cần chọn bộ khác trước Start.","error"):null)))),
    h("p",{className:"room-plan-note"},icon("info"),room.configVersion===2?"Intro tối đa 10 giây/màn. Quiz có Decision 7 giây. Kế hoạch được đóng băng khi Start.":`Quiz v1 · Decision ${room.decisionDurationMs/1000} giây.`));
}
