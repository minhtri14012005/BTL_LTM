import {emptyState} from "/js/shared/ui.js";
import {h,link,notice,heading} from "/js/shared/dom.js";
import {standings,finalSummary,gameTitle,resultBadge,officialResult} from "/js/features/results/view.js";
import {historyQuestion,modeBreakdown} from "/js/features/results/timeline.js";
import {roomReturn} from "/js/features/results/navigation.js";
import {awardPanel} from "/js/features/results/awards.js";

const date=value=>value==null?"—":new Date(value).toLocaleString("vi-VN");
export async function historyList(app,page=0) {
  const data=await app.api.request("GET",`/api/games/history?page=${page}&size=12`);
  return h("section",{id:"history-list",className:"history-page"},heading("Lịch sử thi đấu","Kết quả đã lưu của các trận bạn từng tham gia.",link("Home","home","button secondary")),
    h("p",{className:"muted",role:"status"},`${data.totalElements} trận trong lịch sử của bạn`),
    data.items.length?h("div",{className:"history-grid"},data.items.map(g=>h("article",{className:"card history-card"},
      h("div",{className:"card-top"},h("p",{className:"eyebrow"},`TRẬN #${g.gameSessionId}`),h("span",{className:"badge"},g.schemaVersion===2?"Nhiều màn":"Quiz v1")),
      h("h2",{},link(g.quizTitleSnapshot||`Trận nhiều màn #${g.gameSessionId}`,`history/${g.gameSessionId}`)),resultBadge(g),
      h("p",{className:"muted"},`Trận #${g.gameSessionId} · ${date(g.finishedAtMs)}`),
      h("p",{},officialResult(g)?"Có Official Winner":"Không Official Winner"),link("Xem kết quả",`history/${g.gameSessionId}`,"button secondary")))):emptyState("Các trận đã kết thúc sẽ xuất hiện ở đây.","Chưa có lịch sử thi đấu"),
    h("nav",{className:"pagination","aria-label":"Trang lịch sử"},page>0?link("Trang trước",`history?page=${page-1}`,"button secondary"):null,h("span",{},`Trang ${page+1} · ${data.totalElements} trận`),(page+1)*data.size<data.totalElements?link("Trang sau",`history?page=${page+1}`,"button secondary"):null));
}
export async function historyDetail(app,id) {
  const version=app.renderVersion,selfId=app.user?.id;
  const data=await app.api.request("GET",`/api/games/history/${id}`),s=data.finalSnapshot;
  if(version!==app.renderVersion || selfId!==app.user?.id)return h("div");
  const back=roomReturn(app,s.roomId,()=>version===app.renderVersion && selfId===app.user?.id);
  app.resultRoomController=back;
  const awards=awardPanel(app,id,()=>version===app.renderVersion && selfId===app.user?.id);app.awardController=awards;
  return h("section",{id:"history-detail",className:"history-page"},heading(`Lịch sử trận #${id}`,gameTitle(s),link("Danh sách lịch sử","history","button secondary")),
    finalSummary(s,{selfId,roomAction:back.node,historyLink:false}),awards.node,standings(s,selfId),
    data.restrictions?.some(r=>r.userId===selfId)?notice("Bạn đã được quản trị viên đưa ra khỏi trận này. Điểm và câu trả lời đã ghi được giữ nguyên."):null,
    data.decisions?.some(d=>d.userId===selfId)?h("section",{className:"card",id:"history-decisions"},h("h2",{},"Spin / Star đã sử dụng"),
      h("ul",{},data.decisions.filter(d=>d.userId===selfId).map(d=>h("li",{},`Câu ${d.questionIndex} · ${d.action==="USE_SPIN"?"Spin · "+d.spinEffect:"Star"} · ${date(d.committedAtMs)}`)))):null,
    h("p",{className:"muted history-dates"},`Bắt đầu ${date(data.startedAtMs)} · kết thúc ${date(data.finishedAtMs)}`),modeBreakdown(s,data.questions,selfId),
    h("section",{className:"history-timeline","aria-label":"Chi tiết câu hỏi đã lưu"},h("h2",{},"Diễn biến từng câu"),
      data.questions.length?data.questions.map(q=>historyQuestion(s,q,selfId)):notice("Trận kết thúc trước khi mở câu đầu.")));
}
