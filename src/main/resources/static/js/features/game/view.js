import {h,notice} from "/js/shared/dom.js";
import {icon} from "/js/shared/icons.js";
import {MODE_LABELS} from "/js/shared/mode-labels.js";
import {gameMode} from "/js/features/game/state.js";

const icons={QUIZ:"grid",SONG:"music",VIETNAMESE_PUZZLE:"type",RIDDLE:"help",CLUES:"search",IMAGE_WORD:"image",ORDERING:"sort"};
const instructions={
  QUIZ:"Chọn một trong 4 đáp án rồi gửi. Spin và Hope Star chỉ dùng trong giai đoạn Quyết định trước câu Quiz.",
  SONG:"Xem và nghe video rồi nhập tên bài hát. Nếu trình duyệt chặn phát, bấm Bật tiếng / Phát. Thời gian câu vẫn tiếp tục.",
  VIETNAMESE_PUZZLE:"Chạm hoặc kéo từng ô vào hàng đáp án theo thứ tự. Giữ nguyên dấu và khoảng trắng trên các ô; dùng hết mỗi ô đúng một lần.",
  RIDDLE:"Đọc câu đố rồi nhập đáp án. Giữ dấu tiếng Việt; đáp án sẽ được kiểm tra khi câu kết thúc.",
  CLUES:"Các gợi ý được mở theo mốc chung của trận. Bạn có thể nhập và gửi trước khi mọi gợi ý được mở.",
  IMAGE_WORD:"Quan sát hình rồi nhập đáp án. Nếu hình không tải được, bạn vẫn có thể nhập trước hạn câu.",
  ORDERING:"Chọn đủ các mục, dùng Lên/Xuống để sắp xếp rồi gửi. Mỗi mục dùng đúng một lần."
};
export function publishedResult(s){return (s.phase==="RESULT" || s.status==="FINISHED") && !!s.results?.length;}
export function modeInstruction(mode){return instructions[mode]||"Chờ nội dung từ trận đấu.";}
export function modeHeader(s) {
  const mode=gameMode(s),index=Math.max(0,(s.questionIndex||1)-1),total=Math.max(1,s.questionCount||1);
  const completed=publishedResult(s)?Math.min(total,index+1):index;
  return h("section",{className:"card game-mode-header","aria-label":"Tiến trình trận đấu"},
    h("div",{className:"game-mode-title"},h("span",{className:"mode-emblem","aria-hidden":"true"},icon(icons[mode]||"help")),
      h("div",{},h("p",{className:"eyebrow"},s.stage?`MÀN ${s.stageIndex}/${s.stages.length}`:"CHẾ ĐỘ CHƠI · LEGACY"),h("h2",{id:"game-mode-name"},MODE_LABELS[mode]||mode))),
    h("div",{className:"game-progress"},h("span",{},`Câu ${s.questionIndex}/${s.questionCount}`),
      h("progress",{max:total,value:completed,"aria-label":"Số câu đã công bố kết quả"})),
    s.stages?.length>1?h("details",{className:"game-stage-plan"},h("summary",{id:"game-stage-toggle"},"Kế hoạch trận · "+s.stages.length+" màn"),h("ol",{className:"game-stage-track","aria-label":"Thứ tự các màn"},s.stages.map((stage,i)=>h("li",{...(i+1===s.stageIndex?{"aria-current":"step"}:{} )},h("span",{className:"stage-step","aria-hidden":"true"},i+1),MODE_LABELS[stage.mode]||stage.mode)))):null);
}
export function timerPanel() {
  return h("div",{className:"game-timer"},h("span",{className:"timer-caption"},icon("bolt"),"Còn lại"),
    h("span",{className:"countdown",id:"game-countdown",role:"timer","aria-live":"off","aria-label":"Thời gian còn lại"}),
    h("span",{className:"timer-source",id:"deadline-status"},"Theo thời gian trận"));
}
export function interactionStatus(s,{ready,reconnecting,pending,working}) {
  let text;
  if(!ready)text=reconnecting?"Đang đồng bộ trạng thái trận…":"Chưa đồng bộ kết nối. Các thao tác đang tạm khóa; đáp án chưa được tự gửi lại.";
  else if(pending?.type==="ANSWER")text=working?"Đang gửi đáp án. Hãy chờ xác nhận…":"Chưa rõ đáp án đã được nhận hay chưa. Có thể thử lại đúng yêu cầu cũ để lấy xác nhận.";
  else if(s.phase==="QUESTION_CLOSED")text="Câu đã đóng. Đang chờ xử lý đáp án.";
  else if(s.phase==="SCORING")text="Đang chấm câu. Kết quả sẽ hiện khi trận công bố.";
  else if(s.player?.alreadyAnswered && s.phase!=="QUESTION_OPEN" && !publishedResult(s))text="Đáp án đã được nhận. Chờ công bố kết quả.";
  return text?h("div",{id:"game-interaction-status",className:"game-interaction-status"},notice(text)):null;
}
/** Compact HUD, containing only the current viewer's server-published resources. */
export function playerHud(s,effects) {
  const p=s.player;
  if(!p)return h("div",{id:"player-state",className:"player-strip"},h("span",{className:"player-role"},icon("search"),"Bạn đang quan sát"));
  return h("div",{id:"player-state",className:"player-strip"},
    h("span",{className:"player-points"},icon("bolt"),"Điểm",h("strong",{id:"player-score"},p.score)),
    gameMode(s)==="QUIZ"?[
      h("span",{},icon("target"),"Spin",h("strong",{id:"remaining-spins"},p.remainingSpins)),
      h("span",{className:"player-star"},icon("sparkles"),"Hope Star",h("strong",{id:"star-available"},p.starAvailable?"Còn":"Đã dùng")),
      h("small",{id:"current-decision"},"Spin: "+(effects[p.currentSpin]||"Không dùng")+" · Star: "+(p.starSelected?"đã chọn":"chưa chọn"))
    ]:null,
    p.state==="ELIMINATED"?h("span",{className:"player-role"},icon("info"),"Bạn đã bị loại · Đang quan sát"):null);
}
export function gameStandings(board) {
  const title=board.querySelector("h2");title.prepend(icon("trophy"));
  title.after(h("p",{className:"standings-caption"},"Trong trận · Theo thứ hạng Server"));
  board.querySelectorAll("tbody tr").forEach(row=>{row.firstElementChild.classList.add("game-rank");row.firstElementChild.dataset.rank=row.firstElementChild.textContent;});
  const scroll=board.querySelector(".table-scroll");scroll.id="game-standings-scroll";scroll.tabIndex=0;scroll.setAttribute("role","region");scroll.setAttribute("aria-label","Bảng điểm trong trận, gồm thứ hạng, người chơi, điểm và thời gian");
  return board;
}
export function imagePanel(node) {
  if(!node || node.tagName!=="IMG")return node;
  const status=h("p",{className:"muted",role:"status"},"Đang tải hình của câu hỏi…");
  node.addEventListener("load",()=>status.remove(),{once:true});
  node.addEventListener("error",()=>status.remove(),{once:true});
  return h("figure",{className:"game-image"},node,status);
}
