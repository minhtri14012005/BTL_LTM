import {h,button,link,notice,heading} from "./dom.js";
import {gameCommand,permissions,acceptSnapshot,applyReceipt,countdown,effects,outcomes,endReasons} from "./game-state.js";

function append(node,...children){node.append(...children.filter(child=>child!=null));}

export function standings(s) {
  const players=s.members.filter(m=>m.participation==="PLAYER").sort((a,b)=>a.rank-b.rank || a.userId-b.userId);
  return h("section",{className:"card"},h("h2",{},"Bảng xếp hạng"),h("div",{className:"table-scroll"},h("table",{className:"standings",id:"standings"},h("thead",{},h("tr",{},["Hạng","Người chơi","Điểm","Thời gian","Trạng thái"].map(x=>h("th",{scope:"col"},x)))),h("tbody",{},players.map(m=>h("tr",{"data-user-id":m.userId},h("td",{},m.rank??"—"),h("td",{},m.displayName,m.role==="HOST"?" · Host":"",s.hasOfficialWinner && s.winners.includes(m.userId)?h("span",{className:"badge winner"},"Winner"):null),h("td",{},m.score),h("td",{},`${m.totalAnswerTimeMs} ms`),h("td",{},m.playerState==="ELIMINATED"?"Đã bị loại":"Còn chơi")))))));
}
export function finalSummary(s) {
  return h("section",{className:"card final-summary",id:"final-summary"},h("p",{className:"eyebrow"},"KẾT QUẢ CUỐI TRẬN"),h("h2",{},endReasons[s.endReason]||s.endReason),
    notice(s.hasOfficialWinner?`Winner: ${s.members.filter(m=>s.winners.includes(m.userId)).map(m=>m.displayName).join(", ")}`:"Trận kết thúc bất thường · không có Official Winner.",s.hasOfficialWinner?"success":"info"),
    h("div",{className:"actions"},link("Chi tiết lịch sử",`history/${s.gameSessionId}`,"button"),link("Quay lại phòng",`room/${s.roomId}`,"button secondary"),link("Home","home","button secondary")));
}
export function image(s,question) {
  if(!question?.imageRef)return null;
  const hash=question.imageRef.replace(/^sha256:/,"");
  const node=h("img",{className:"question-image",alt:"Ảnh minh họa câu hỏi",src:`/api/quizzes/${s.quizId}/images/${hash}?gameSessionId=${s.gameSessionId}`});
  node.addEventListener("error",()=>node.replaceWith(notice("Không tải được ảnh câu hỏi. Nội dung chữ vẫn có thể xem.")));return node;
}
export function resultPanel(result,members,id="question-result") {
  return h("section",{className:"card",id,"data-index":result.index},h("h2",{},`Kết quả câu ${result.index}`),result.question?h("p",{},`Đáp án: ${result.question.correctAnswer} · ${result.question.options[result.question.correctAnswer]}`):null,
    result.results.map(r=>{const name=members.find(m=>m.userId===r.userId)?.displayName||`User #${r.userId}`;
      const trace=[r.momentumConsumed?"Đã dùng Momentum":null,r.recoveryConsumed?"Đã dùng Recovery":null,r.momentumGranted?"Nhận Momentum (cho câu sau)":null,r.recoveryGranted?"Nhận Recovery (cho câu sau)":null].filter(Boolean);
      return h("div",{className:"result-row","data-user-id":r.userId},h("strong",{},name),h("span",{},`${outcomes[r.outcome]} · ${r.scoreDelta>=0?"+":""}${r.scoreDelta} → ${r.scoreAfter} điểm · ${r.answerTimeMs} ms`),h("small",{},`Streak đúng ${r.winStreak} / sai ${r.loseStreak} · Momentum ${r.hasMomentumAfter?"có":"không"} / Recovery ${r.hasRecoveryAfter?"có":"không"}${trace.length?" · "+trace.join(" · "):""}${r.eliminatedNow?" · Đã bị loại":""}`));}));
}
export async function gamePage(app,id) {
  const root=h("section",{id:"game-page"});let state=app.games.get(id)||null,lastResult=null,selected=null,receipt=null,pending=null,working=false,synced=false,reconnecting=false,feedback=null,sampled=performance.now(),clock=null,disposed=false;
  const controller={id,dispose(){disposed=true;clearInterval(clock);},update(){render();},receive(m){
    if(m.target?.kind!=="GAME" || m.target.id!==id || m.kind!=="EVENT")return;
    adopt(m.payload);},async reconnect(){if(disposed || reconnecting || app.transport.status!=="ready")return;reconnecting=true;synced=false;render();
    try{const ack=await app.transport.send(gameCommand("RECONNECT",id,null));if(!disposed){adopt(ack.payload);synced=true;feedback=null;}}
    catch(e){if(!disposed)feedback=e.message;}finally{reconnecting=false;if(!disposed)render();}}
  };
  app.gameController=controller;
  function adopt(next){if(disposed || next?.gameSessionId!==id)return;const chosen=acceptSnapshot(state,next);if(chosen!==next)return;
    if(state?.questionIndex!==next.questionIndex)selected=null;
    state=next;sampled=performance.now();app.games.set(id,state);app.activeRoomId=state.roomId;
    if(next.results.length)lastResult={index:next.questionIndex,question:next.question,results:next.results};render();
  }
  async function send(){if(working || !pending || disposed)return;working=true;feedback=null;render();const frame=pending;
    try{const ack=await app.transport.send(frame);if(disposed)return;receipt=ack;state=applyReceipt(state,ack);app.games.set(id,state);pending=null;}
    catch(e){if(disposed)return;feedback=e.message;if(!e.retryable && !["SESSION_REPLACED","DISCONNECTED"].includes(e.code))pending=null;}
    finally{working=false;if(!disposed)render();}
  }
  function begin(type,payload={}){pending=gameCommand(type,id,type==="CANCEL_GAME"?null:state.questionIndex,payload);send();}
  function render(){if(disposed || app.gameController!==controller)return;if(!state){root.replaceChildren(notice("Đang lấy trạng thái trận…"));return;}
    const s=state,p=s.player,ready=app.transport.status==="ready" && synced,policy=permissions(s,app.user.id,ready,!!pending||working);
    const phase={DECISION:"Quyết định chiến thuật",QUESTION_OPEN:"Đang trả lời",QUESTION_CLOSED:"Đã đóng câu",SCORING:"Server đang chấm",RESULT:"Kết quả câu",FINISHED:"Kết thúc"}[s.phase];
    root.dataset.phase=s.phase;root.dataset.index=s.questionIndex;root.dataset.revision=s.revision;
    const controls=h("div",{className:"actions"});if(s.status==="ACTIVE" && s.members.some(m=>m.userId===app.user.id && m.role==="HOST"))controls.append(button("Hủy trận",()=>{if(window.confirm("Hủy trận? Đáp án chưa chấm sẽ được giữ là đã nhận, chưa chấm."))begin("CANCEL_GAME");},{id:"cancel-game",disabled:!policy.cancel,className:"danger secondary"}));
    const self=h("section",{className:"card",id:"player-state"},h("h2",{},p?"Trạng thái của bạn":"Host / Người quan sát"));
    if(p)append(self,h("div",{className:"stat-grid"},h("div",{className:"stat"},h("span",{},"Điểm"),h("strong",{id:"player-score"},p.score)),h("div",{className:"stat"},h("span",{},"Spin còn lại"),h("strong",{id:"remaining-spins"},p.remainingSpins)),h("div",{className:"stat"},h("span",{},"Hope Star"),h("strong",{id:"star-available"},p.starAvailable?"Còn":"Đã dùng"))),h("p",{id:"player-effects"},`Streak đúng ${p.winStreak} / sai ${p.loseStreak} · Momentum ${p.momentum?"có":"không"} / Recovery ${p.recovery?"có":"không"}`),h("p",{id:"current-decision"},`Spin: ${effects[p.currentSpin]||"Không dùng"} · Star: ${p.starSelected?"đã chọn":"chưa chọn"}`),p.state==="ELIMINATED"?notice(`Bạn đã bị loại ở câu ${p.eliminatedQuestionIndex}. Điểm được giữ nguyên; bạn vẫn xem trận.`):null);
    else self.append(notice("Bạn đang quan sát. Không có tài nguyên hoặc đáp án của Player."));
    const question=h("section",{className:"card game-question"},h("div",{className:"card-top"},h("h2",{id:"game-phase"},`${phase} · câu ${s.questionIndex}/${s.questionCount}`),h("span",{className:"countdown",id:"game-countdown","aria-label":"Thời gian còn lại"})),h("p",{className:"muted"},"Thời gian hiển thị theo Server. Server quyết định đóng câu và chấm điểm."));
    if(s.runtimeState==="UNAVAILABLE")question.append(notice(s.cleanupPending?"Trận không khả dụng. Cleanup chưa được lưu; chưa có kết quả cuối chính thức.":"Trận không khả dụng. Hãy tải lại trạng thái sau.","error"));
    if(s.phase==="DECISION")question.append(notice("Không bắt buộc dùng tài nguyên. Nếu kết hợp, hãy Spin trước rồi chọn Star. Star riêng khóa Spin; Khó khăn không cho Star."),h("div",{className:"actions"},button("Dùng Spin",()=>begin("USE_SPIN"),{id:"use-spin",disabled:!policy.spin}),button("Dùng Hope Star",()=>begin("USE_STAR"),{id:"use-star",disabled:!policy.star})));
    if(s.question){append(question,h("h3",{id:"question-content"},s.question.content),image(s,s.question));const options=h("div",{className:"answer-options"});
      for(const k of ["A","B","C","D"])options.append(button(`${k}. ${s.question.options[k]}`,()=>{selected=k;render();},{"data-option":k,disabled:!policy.answer,className:(selected===k||p?.selectedOption===k?"selected ":"")+"secondary","aria-pressed":selected===k||p?.selectedOption===k}));
      question.append(options);if(s.phase==="QUESTION_OPEN")append(question,button("Gửi đáp án",()=>begin("ANSWER",{option:selected}),{id:"submit-answer",disabled:!policy.answer || !selected}),p?.alreadyAnswered?notice(`Server đã nhận ${p.selectedOption}. Chờ chấm cả câu; ACK chưa cho biết đúng/sai.`):null);
      if(s.status==="FINISHED" && p?.alreadyAnswered && s.question.correctAnswer==null)question.append(notice(`Đáp án ${p.selectedOption} đã được nhận, chưa chấm. Không cộng thời gian câu này vào tổng xếp hạng.`));
    }
    const actionFeedback=h("div",{id:"game-feedback","aria-live":"polite"});if(working)actionFeedback.append(notice("Đang chờ xác nhận Server…"));if(feedback)actionFeedback.append(notice(feedback,"error"));
    if(receipt)actionFeedback.append(h("p",{id:"game-ack",className:"notice success","data-request-id":receipt.requestId},`Server ACCEPTED ${receipt.type} · câu ${receipt.questionIndex??"—"}`));
    if(pending && !working)actionFeedback.append(button("Thử lại cùng yêu cầu",send,{id:"retry-game-command",disabled:!ready,className:"secondary"}),h("small",{},"Giữ nguyên requestId, câu và nội dung. Xác nhận cũ không ghi đè trạng thái mới."));
    root.replaceChildren(...[heading(s.quizTitleSnapshot,`Trận #${id}`,link("Home","home","button secondary")),controls,s.status==="FINISHED"?finalSummary(s):null,self,question,actionFeedback,lastResult?resultPanel(lastResult,s.members):null,standings(s)].filter(child=>child!=null));tick();
  }
  function tick(){const node=root.querySelector("#game-countdown");if(!node || !state)return;const ms=countdown(state.deadlineEpochMs,state.serverTimeMs,sampled,performance.now());node.textContent=ms==null?"—":ms===0?"Chờ Server chuyển phase":`${(ms/1000).toFixed(1)} s`;}
  clock=setInterval(tick,100);render();
  try{adopt(await app.api.request("GET",`/api/games/${id}/snapshot`));}catch(e){if(!disposed){feedback=e.message;if(!state)root.replaceChildren(notice(e.message,"error"),button("Tải lại",()=>app.render()));}}
  if(!disposed){if(app.transport.status==="ready")controller.reconnect();else app.connect();}return root;
}
