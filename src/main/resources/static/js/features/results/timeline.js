import {h,notice} from "/js/shared/dom.js";
import {releasedClues} from "/js/features/game/clues.js";
import {historyVideo} from "/js/features/game/video.js";
import {isArrangement,correctArrangement} from "/js/features/game/arrangement.js";
import {effects,outcomes} from "/js/features/game/state.js";
import {MODE_LABELS} from "/js/shared/mode-labels.js";
import {image,resultPanel,seconds} from "/js/features/results/view.js";

export function historyQuestion(s,q,selfId) {
  const mode=q.mode||"QUIZ",published=q.scoredAtMs!=null;
  let solution=notice("Câu chưa được chấm. Không công bố đáp án đúng.");
  if(published){
    if(["CLUES","RIDDLE","IMAGE_WORD","SONG","VIETNAMESE_PUZZLE"].includes(mode) && q.payload?.acceptedAnswers)solution=notice("Đáp án đã công bố: "+q.payload.acceptedAnswers.join(" / "));
    else if(isArrangement(mode) && q.payload?.correctOrder)solution=notice("Thứ tự đã công bố: "+correctArrangement(q).join(mode==="VIETNAMESE_PUZZLE"?"":" → "));
    else if(q.correctAnswer)solution=notice(`Đáp án đã công bố: ${q.correctAnswer}`);
  }
  return h("article",{className:"card history-question","data-index":q.questionIndex},
    h("div",{className:"card-top"},h("p",{className:"eyebrow"},`CÂU ${q.questionIndex} · ${MODE_LABELS[mode]||mode}`),h("span",{className:"badge"},published?"Đã chấm":"Chưa chấm")),
    h("h2",{},q.content),image(s,q,q.questionIndex,{history:true}),historyVideo(s,{...q,mode}),mode==="CLUES"?releasedClues(q.payload):null,
    mode==="QUIZ"?h("ul",{className:"options"},["A","B","C","D"].map(k=>h("li",{className:published && q.correctAnswer===k?"correct":""},`${k}. ${q.options?.[k]??"—"}`))):null,solution,
    q.answers.length?q.answers.map(a=>h("section",{className:`answer-timeline ${a.userId===selfId?"is-self":""}`,"data-user-id":a.userId,"data-status":a.answerStatus},
      h("div",{className:"card-top"},h("h3",{},s.members.find(m=>m.userId===a.userId)?.displayName||`User #${a.userId}`,a.userId===selfId?" · Bạn":""),h("span",{className:"badge"},outcomes[a.answerStatus]||a.answerStatus)),
      h("p",{className:"submitted-answer"},`${["CLUES","RIDDLE","IMAGE_WORD","SONG"].includes(mode)?"Nhập: "+(a.submittedAnswer?.text??"—"):isArrangement(mode)?"Xếp: "+(a.submittedAnswer?.itemIds?.map(id=>(q.payload?.pieces||q.payload?.items||[]).find(v=>v.id===id)?.text||id).join(mode==="VIETNAMESE_PUZZLE"?"":" → ")??"—"):"Chọn: "+(a.selectedOption??"—")} · ${seconds(a.answerTimeMs)}`),
      mode==="QUIZ"?h("p",{className:"muted"},`Spin: ${effects[a.spinEffect]||"Không dùng / chưa lưu"} · Star: ${a.starSelected==null?"chưa lưu":a.starSelected?"đã chọn":"không chọn"}`):null,
      a.result?resultPanel({index:q.questionIndex,question:null,results:[a.result]},s.members,null):h("p",{className:"muted"},"Điểm / kết quả: chưa chấm. Thời gian này không cộng vào tổng xếp hạng."))):notice("Không có Answer đã lưu cho câu này."));
}

/** Display-only aggregation of actual per-question result fields, never a ScoreEngine. */
export function modeBreakdown(s,questions,selfId) {
  if(s.schemaVersion!==2 || !s.members.some(m=>m.userId===selfId && m.participation==="PLAYER"))return null;
  const rows=new Map();
  for(const q of questions){
    if(!q.mode)continue;
    if(!rows.has(q.mode))rows.set(q.mode,{mode:q.mode,opened:0,scored:0,correct:0,delta:0});
    const row=rows.get(q.mode);row.opened++;
    const answer=q.answers.find(a=>a.userId===selfId);
    if(q.scoredAtMs!=null && answer?.result && Number.isFinite(answer.result.scoreDelta)){
      row.scored++;if(answer.answerStatus==="CORRECT")row.correct++;row.delta+=answer.result.scoreDelta;
    }
  }
  if(!rows.size)return null;
  return h("section",{className:"card mode-breakdown"},h("h2",{},"Kết quả của bạn theo chế độ"),
    h("p",{className:"muted"},"Tổng hợp từ kết quả câu đã lưu. Điểm từ câu không thay thế điểm cuối hoặc thứ hạng của trận."),
    h("div",{className:"table-scroll"},h("table",{className:"breakdown-table"},h("thead",{},h("tr",{},["Chế độ","Câu đã mở","Đúng / đã chấm","Điểm từ câu"].map(label=>h("th",{scope:"col"},label)))),
      h("tbody",{},[...rows.values()].map(row=>h("tr",{"data-mode":row.mode},h("th",{scope:"row"},MODE_LABELS[row.mode]||row.mode),h("td",{},row.opened),h("td",{},row.scored?`${row.correct}/${row.scored}`:"—"),h("td",{},row.scored?`${row.delta>=0?"+":""}${row.delta}`:"—")))))));
}
