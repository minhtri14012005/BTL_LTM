import {h,link,notice} from "/js/shared/dom.js";
import {endReasons,outcomes} from "/js/features/game/state.js";
import {correctArrangement} from "/js/features/game/arrangement.js";
export const officialResult=s=>!!s.hasOfficialWinner && !["CANCELLED","SERVER_INTERRUPTED"].includes(s.endReason);
export const rankedPlayers=s=>s.members.filter(m=>m.participation==="PLAYER").sort((a,b)=>(a.rank??Infinity)-(b.rank??Infinity) || a.userId-b.userId);
export const resultTime=(s,m)=>s.schemaVersion===2?m.totalCorrectAnswerTimeMs:m.totalAnswerTimeMs;
export const seconds=value=>value==null || !Number.isFinite(value)?"—":`${(value/1000).toFixed(3)} s`;
export function resultBadge(s){return h("span",{className:`badge result-badge ${officialResult(s)?"is-completed":"is-unofficial"}`},endReasons[s.endReason]||s.endReason||"Chưa có lý do kết thúc");}
export function gameTitle(s){return s.quizTitleSnapshot || (s.stages?.length?s.stages.map(stage=>stage.title).join(" → "):"Trận nhiều màn");}
export function standings(s,selfId=null) {
  const players=rankedPlayers(s);
  return h("section",{className:"card"},h("h2",{},"Bảng xếp hạng"),h("div",{className:"table-scroll"},h("table",{className:"standings",id:"standings"},h("thead",{},h("tr",{},["Hạng","Người chơi","Điểm",s.schemaVersion===2?"Thời gian đúng":"Thời gian"].map(x=>h("th",{scope:"col"},x)))),h("tbody",{},players.map(m=>h("tr",{"data-user-id":m.userId,className:m.userId===selfId?"current-player":""},h("td",{},m.rank??"—"),h("td",{},m.displayName,m.userId===selfId?" · Bạn":"",m.role==="HOST"?" · Host":"",officialResult(s) && s.winners.includes(m.userId)?h("span",{className:"badge winner"},"Winner"):null),h("td",{},m.score),h("td",{},seconds(resultTime(s,m)))))))));
}
export function finalSummary(s,{selfId=null,roomAction=null,historyLink=true}={}) {
  const official=officialResult(s),leaders=rankedPlayers(s).filter(m=>m.rank>=1 && m.rank<=3);
  return h("section",{className:"card final-summary",id:"final-summary","data-end-reason":s.endReason},
    h("div",{className:"card-top"},h("p",{className:"eyebrow"},"KẾT QUẢ CUỐI TRẬN"),resultBadge(s)),
    h("h2",{},endReasons[s.endReason]||s.endReason),
    notice(official?`Winner: ${s.members.filter(m=>s.winners.includes(m.userId)).map(m=>m.displayName).join(", ")}`:"Trận kết thúc bất thường · không có Official Winner.",official?"success":"info"),
    official && leaders.length?h("section",{className:"result-top","aria-label":"Nhóm hạng 1 đến 3"},h("h3",{},"Top theo thứ hạng"),h("div",{className:"result-top-grid"},leaders.map(m=>h("article",{className:`result-top-card ${m.userId===selfId?"is-self":""}`,"data-user-id":m.userId,"data-rank":m.rank},
      h("span",{className:"result-rank"},`#${m.rank}`),h("h4",{},m.displayName),h("strong",{},`${m.score} điểm`),h("small",{},seconds(resultTime(s,m))),
      leaders.filter(peer=>peer.rank===m.rank).length>1?h("span",{className:"badge"},"Đồng hạng"):null)))):null,
    ownStats(s,selfId),roomAction,
    h("div",{className:"actions"},historyLink?link("Chi tiết lịch sử",`history/${s.gameSessionId}`,"button secondary"):null,roomAction?null:link("Về sảnh","home","button secondary")));
}
export function ownStats(s,selfId) {
  const member=s.members.find(m=>m.userId===selfId);
  if(!member)return null;
  if(member.participation!=="PLAYER")return notice("Bạn tham gia với vai trò quan sát; không có thứ hạng người chơi.");
  return h("section",{className:"own-result-stats","aria-label":"Thống kê của bạn"},h("h3",{},"Kết quả của bạn"),h("dl",{className:"own-stats-grid"},
    stat("Thứ hạng",member.rank==null?"—":`#${member.rank}`),stat("Điểm cuối",member.score??"—"),stat(s.schemaVersion===2?"Thời gian trả lời đúng":"Tổng thời gian",seconds(resultTime(s,member)))));
}
function stat(label,value){return h("div",{},h("dt",{},label),h("dd",{},value));}
export function image(s,question,index=s.questionIndex,{history=false}={}) {
  if(!question?.imageRef)return null;
  const source=s.schemaVersion===2?s.stages.find(stage=>index>=stage.firstQuestionIndex && index<stage.firstQuestionIndex+stage.questionCount)?.sourceQuizId:s.quizId;
  if(!source)return notice("Không xác định được bộ ảnh của câu hỏi.");
  const hash=question.imageRef.replace(/^sha256:/,"");
  const node=h("img",{className:"question-image",alt:"Ảnh minh họa câu hỏi",src:`/api/quizzes/${source}/images/${hash}?gameSessionId=${s.gameSessionId}`});
  node.addEventListener("error",()=>node.replaceWith(notice(history?"Không tải được ảnh đã lưu. Kết quả lịch sử vẫn được giữ.":"Không tải được ảnh. Câu vẫn tiếp tục theo giờ Server; bạn có thể nhập đáp án phỏng đoán.")));return node;
}
export function resultPanel(result,members,id="question-result") {
  return h("section",{className:"card",id,"data-index":result.index},h("h2",{},`Kết quả câu ${result.index}`),result.question?h("p",{},(["CLUES","RIDDLE","IMAGE_WORD","SONG","VIETNAMESE_PUZZLE"].includes(result.question.mode))?"Đáp án: "+(result.question.payload?.acceptedAnswers||[]).join(" / "):result.question.mode==="ORDERING"?"Thứ tự đúng: "+correctArrangement(result.question).join(" → "):`Đáp án: ${result.question.correctAnswer} · ${result.question.options[result.question.correctAnswer]}`):null,
    result.results.map(r=>{const name=members.find(m=>m.userId===r.userId)?.displayName||`User #${r.userId}`;
      const trace=[r.momentumConsumed?"Đã dùng Momentum":null,r.recoveryConsumed?"Đã dùng Recovery":null,r.momentumGranted?"Nhận Momentum (cho câu sau)":null,r.recoveryGranted?"Nhận Recovery (cho câu sau)":null].filter(Boolean);
      return h("div",{className:"result-row","data-user-id":r.userId},h("strong",{},name),h("span",{},`${outcomes[r.outcome]} · ${r.scoreDelta>=0?"+":""}${r.scoreDelta} → ${r.scoreAfter} điểm · ${(r.answerTimeMs/1000).toFixed(3)} s`),h("small",{},`Streak đúng ${r.winStreak} / sai ${r.loseStreak} · Momentum ${r.hasMomentumAfter?"có":"không"} / Recovery ${r.hasRecoveryAfter?"có":"không"}${trace.length?" · "+trace.join(" · "):""}${r.eliminatedNow?" · Đã bị loại":""}`));}));
}
