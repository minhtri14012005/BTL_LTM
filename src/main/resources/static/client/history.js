import {releasedClues} from "./clues.js";
import {historyVideo} from "./video.js";
import {h,button,link,notice,heading} from "./dom.js";
import {isArrangement,correctArrangement} from "./arrangement.js";
import {endReasons,effects,outcomes} from "./game-state.js";
import {standings,finalSummary,image,resultPanel,gameTitle} from "./game.js";

export async function historyList(app,page=0) {
  const data=await app.api.request("GET",`/api/games/history?page=${page}&size=12`);
  return h("section",{id:"history-list"},heading("Lịch sử thi đấu","Các trận đã kết thúc mà bạn có mặt trong roster.",link("Home","home","button secondary")),
    data.items.length?data.items.map(g=>h("article",{className:"card"},h("h2",{},link(g.quizTitleSnapshot||`Trận nhiều màn #${g.gameSessionId}`,`history/${g.gameSessionId}`)),h("p",{},`Trận #${g.gameSessionId} · ${endReasons[g.endReason]} · ${new Date(g.finishedAtMs).toLocaleString("vi-VN")}`),h("span",{className:"badge"},g.hasOfficialWinner?"Có Official Winner":"Không Official Winner"))):notice("Chưa có trận đã kết thúc trong trang này."),
    h("div",{className:"pagination"},page>0?link("Trang trước",`history?page=${page-1}`,"button secondary"):null,h("span",{},`Trang ${page+1} · ${data.totalElements} trận`),(page+1)*data.size<data.totalElements?link("Trang sau",`history?page=${page+1}`,"button secondary"):null));
}
export async function historyDetail(app,id) {
  const data=await app.api.request("GET",`/api/games/history/${id}`),s=data.finalSnapshot;
  return h("section",{id:"history-detail"},heading(`Lịch sử trận #${id}`,gameTitle(s),link("Danh sách lịch sử","history","button secondary")),finalSummary(s),standings(s),
    h("p",{className:"muted"},`Bắt đầu ${new Date(data.startedAtMs).toLocaleString("vi-VN")} · kết thúc ${new Date(data.finishedAtMs).toLocaleString("vi-VN")}`),
    data.questions.length?data.questions.map(q=>h("article",{className:"card history-question","data-index":q.questionIndex},h("h2",{},`Câu ${q.questionIndex} · ${q.content}`),image(s,q,q.questionIndex),historyVideo(s,q),q.mode==="CLUES"?releasedClues(q.payload):null,q.mode!=="QUIZ"?null:h("ul",{className:"options"},["A","B","C","D"].map(k=>h("li",{className:q.correctAnswer===k?"correct":""},`${k}. ${q.options[k]}`))),(["CLUES","RIDDLE","IMAGE_WORD","SONG","VIETNAMESE_PUZZLE"].includes(q.mode)) && q.payload?.acceptedAnswers?notice("Đáp án đã công bố: "+q.payload.acceptedAnswers.join(" / ")):isArrangement(q.mode) && q.payload?.correctOrder?notice("Thứ tự đã công bố: "+correctArrangement(q).join(q.mode==="VIETNAMESE_PUZZLE"?"":" → ")):q.correctAnswer?notice(`Đáp án đã công bố: ${q.correctAnswer}`):notice("Câu chưa được chấm. Không công bố đáp án đúng."),
      q.answers.length?q.answers.map(a=>h("div",{className:"answer-timeline","data-user-id":a.userId,"data-status":a.answerStatus},h("h3",{},s.members.find(m=>m.userId===a.userId)?.displayName||`User #${a.userId}`),h("p",{},`${outcomes[a.answerStatus]} · ${["CLUES","RIDDLE","IMAGE_WORD","SONG"].includes(q.mode)?"nhập "+(a.submittedAnswer?.text??"—"):isArrangement(q.mode)?"xếp "+(a.submittedAnswer?.itemIds?.map(id=>(q.payload?.pieces||q.payload?.items||[]).find(v=>v.id===id)?.text||id).join(q.mode==="VIETNAMESE_PUZZLE"?"":" → ")??"—"):"chọn "+(a.selectedOption??"—")} · ${a.answerTimeMs==null?"—":(a.answerTimeMs/1000).toFixed(3)+" s"}`),q.mode==="QUIZ"?h("p",{},`Spin: ${effects[a.spinEffect]||"Không dùng / chưa lưu"} · Star: ${a.starSelected==null?"chưa lưu":a.starSelected?"đã chọn":"không chọn"}`):null,a.result?resultPanel({index:q.questionIndex,question:null,results:[a.result]},s.members,null):h("p",{className:"muted"},"Điểm / kết quả: chưa chấm. Thời gian này không cộng vào tổng xếp hạng."))):notice("Không có Answer đã lưu cho câu này."))):notice("Trận kết thúc trước khi mở câu đầu."));
}
