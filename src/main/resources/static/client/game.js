import {releasedClues} from "./clues.js";
import {h,button,link,notice,heading} from "./dom.js";
import {gameCommand,permissions,acceptSnapshot,applyReceipt,countdown,effects,outcomes,endReasons,gameMode} from "./game-state.js";

import {arrangementAnswer,correctArrangement,isArrangement,permutation} from "./arrangement.js";
import {songVideo} from "./video.js";
import {MODE_LABELS} from "./core.js";
export function gameTitle(s){return s.quizTitleSnapshot || (s.stages?.length?s.stages.map(stage=>stage.title).join(" → "):"Trận nhiều màn");}
function append(node,...children){node.append(...children.filter(child=>child!=null));}

export function standings(s,selfId=null) {
  const players=s.members.filter(m=>m.participation==="PLAYER").sort((a,b)=>a.rank-b.rank || a.userId-b.userId);
  return h("section",{className:"card"},h("h2",{},"Bảng xếp hạng"),h("div",{className:"table-scroll"},h("table",{className:"standings",id:"standings"},h("thead",{},h("tr",{},["Hạng","Người chơi","Điểm",s.schemaVersion===2?"Thời gian đúng":"Thời gian"].map(x=>h("th",{scope:"col"},x)))),h("tbody",{},players.map(m=>h("tr",{"data-user-id":m.userId,className:m.userId===selfId?"current-player":""},h("td",{},m.rank??"—"),h("td",{},m.displayName,m.userId===selfId?" · Bạn":"",m.role==="HOST"?" · Host":"",s.hasOfficialWinner && s.winners.includes(m.userId)?h("span",{className:"badge winner"},"Winner"):null),h("td",{},m.score),h("td",{},`${((s.schemaVersion===2?m.totalCorrectAnswerTimeMs:m.totalAnswerTimeMs)/1000).toFixed(3)} s`)))))));
}
export function finalSummary(s) {
  return h("section",{className:"card final-summary",id:"final-summary"},h("p",{className:"eyebrow"},"KẾT QUẢ CUỐI TRẬN"),h("h2",{},endReasons[s.endReason]||s.endReason),
    notice(s.hasOfficialWinner?`Winner: ${s.members.filter(m=>s.winners.includes(m.userId)).map(m=>m.displayName).join(", ")}`:"Trận kết thúc bất thường · không có Official Winner.",s.hasOfficialWinner?"success":"info"),
    h("div",{className:"actions"},link("Chi tiết lịch sử",`history/${s.gameSessionId}`,"button"),link("Quay lại phòng",`room/${s.roomId}`,"button secondary"),link("Home","home","button secondary")));
}
export function image(s,question,index=s.questionIndex) {
  if(!question?.imageRef)return null;
  const source=s.schemaVersion===2?s.stages.find(stage=>index>=stage.firstQuestionIndex && index<stage.firstQuestionIndex+stage.questionCount)?.sourceQuizId:s.quizId;
  if(!source)return notice("Không xác định được bộ ảnh của câu hỏi.");
  const hash=question.imageRef.replace(/^sha256:/,"");
  const node=h("img",{className:"question-image",alt:"Ảnh minh họa câu hỏi",src:`/api/quizzes/${source}/images/${hash}?gameSessionId=${s.gameSessionId}`});
  node.addEventListener("error",()=>node.replaceWith(notice("Không tải được ảnh. Câu vẫn tiếp tục theo giờ Server; bạn có thể nhập đáp án phỏng đoán.")));return node;
}
export function resultPanel(result,members,id="question-result") {
  return h("section",{className:"card",id,"data-index":result.index},h("h2",{},`Kết quả câu ${result.index}`),result.question?h("p",{},(["CLUES","RIDDLE","IMAGE_WORD","SONG","VIETNAMESE_PUZZLE"].includes(result.question.mode))?"Đáp án: "+(result.question.payload?.acceptedAnswers||[]).join(" / "):result.question.mode==="ORDERING"?"Thứ tự đúng: "+correctArrangement(result.question).join(" → "):`Đáp án: ${result.question.correctAnswer} · ${result.question.options[result.question.correctAnswer]}`):null,
    result.results.map(r=>{const name=members.find(m=>m.userId===r.userId)?.displayName||`User #${r.userId}`;
      const trace=[r.momentumConsumed?"Đã dùng Momentum":null,r.recoveryConsumed?"Đã dùng Recovery":null,r.momentumGranted?"Nhận Momentum (cho câu sau)":null,r.recoveryGranted?"Nhận Recovery (cho câu sau)":null].filter(Boolean);
      return h("div",{className:"result-row","data-user-id":r.userId},h("strong",{},name),h("span",{},`${outcomes[r.outcome]} · ${r.scoreDelta>=0?"+":""}${r.scoreDelta} → ${r.scoreAfter} điểm · ${(r.answerTimeMs/1000).toFixed(3)} s`),h("small",{},`Streak đúng ${r.winStreak} / sai ${r.loseStreak} · Momentum ${r.hasMomentumAfter?"có":"không"} / Recovery ${r.hasRecoveryAfter?"có":"không"}${trace.length?" · "+trace.join(" · "):""}${r.eliminatedNow?" · Đã bị loại":""}`));}));
}
export async function gamePage(app,id) {
  const root=h("section",{id:"game-page"});
  app.gamePresentation??=new Map();
  const memory=app.gamePresentation.get(id)||{leaderboard:true,actions:new Set(),results:new Set()};
  app.gamePresentation.set(id,memory);
  let state=app.games.get(id)||null,selected=null,textDraft="",arrangementDraft=[],pending=null,working=false,synced=false,reconnecting=false,feedback=null,sampled=performance.now(),clock=null,disposed=false,toast=null,toastUntil=0,animateIndex=null,videoPlayer=null;
  const controller={id,dispose(){disposed=true;clearInterval(clock);videoPlayer?.dispose();},update(){render();},receive(m){
    if(m.target?.kind!=="GAME" || m.target.id!==id || m.kind!=="EVENT")return;
    adopt(m.payload,true);
  },async reconnect(){
    if(disposed || reconnecting || app.transport.status!=="ready")return;
    reconnecting=true;synced=false;render();
    try{const ack=await app.transport.send(gameCommand("RECONNECT",id,null));if(!disposed){adopt(ack.payload);synced=true;feedback=null;}}
    catch(e){if(!disposed)feedback=e.message;}
    finally{reconnecting=false;if(!disposed)render();}
  }};
  app.gameController=controller;
  function remaining(){return state?countdown(state.deadlineEpochMs,state.serverTimeMs,sampled,performance.now()):null;}
  function presenting(){
    return !!state?.question && !!state?.results.length && remaining()>0 && (state.phase==="RESULT" || (state.status==="FINISHED" && state.hasOfficialWinner));
  }
  function showToast(text,kind="success"){toast={text,kind};toastUntil=performance.now()+3000;}
  function adopt(next,event=false){
    if(disposed || next?.gameSessionId!==id)return;
    if(acceptSnapshot(state,next)!==next)return;
    if(state?.questionIndex!==next.questionIndex){selected=null;textDraft="";arrangementDraft=[];animateIndex=null;if(!pending)feedback=null;}
    if(next.phase==="QUESTION_OPEN" && state?.phase==="DECISION" && !pending)feedback=null;
    if(!event && pending?.questionIndex===next.questionIndex && ((pending.type==="USE_SPIN" && next.player?.currentSpin) || (pending.type==="USE_STAR" && next.player?.starSelected)))memory.actions.add(pending.requestId);
    // Carry the existing server-clock estimate across delayed events; never start a fresh 1.5s locally.
    const now=performance.now(),estimated=state?state.serverTimeMs+Math.max(0,now-sampled):next.serverTimeMs;
    state=next;sampled=now-Math.max(0,estimated-next.serverTimeMs);app.games.set(id,state);app.activeRoomId=state.roomId;
    if(next.results.length && !memory.results.has(next.questionIndex)){
      memory.results.add(next.questionIndex);
      if(event && presenting()){
        animateIndex=next.questionIndex;
        const own=next.results.find(r=>r.userId===app.user.id);
        if(own)showToast(own.scoreDelta>0?"Bạn được cộng "+own.scoreDelta+" điểm.":own.scoreDelta<0?"Bạn bị trừ "+Math.abs(own.scoreDelta)+" điểm.":"Điểm không thay đổi.");
      }
    }
    render();
  }
  async function send(){
    if(working || !pending || disposed)return;
    working=true;feedback=null;render();const frame=pending;
    try{
      const ack=await app.transport.send(frame);if(disposed)return;
      state=applyReceipt(state,ack);app.games.set(id,state);pending=null;
      if(!memory.actions.has(ack.requestId)){
        memory.actions.add(ack.requestId);
        // Only an ACK for the current decision may announce an action.
        if(ack.questionIndex===state.questionIndex && state.phase==="DECISION"){
          if(ack.type==="USE_SPIN")showToast("🎡 Bạn nhận được Spin: "+effects[ack.payload.spinEffect]+".");
          if(ack.type==="USE_STAR")showToast(ack.payload.spinEffect?"⭐ Đã thêm Hope Star vào "+effects[ack.payload.spinEffect]+".":"⭐ Bạn đã dùng Hope Star.");
        }
      }
      // Retain the receipt identity for diagnostics without displaying protocol text.
      memory.lastAck=ack;
    }catch(e){
      if(disposed)return;feedback=e.message;
      if(!e.retryable && !["SESSION_REPLACED","DISCONNECTED"].includes(e.code))pending=null;
    }finally{working=false;if(!disposed)render();}
  }
  function begin(type,payload={}){
    if(working || pending)return;
    pending=gameCommand(type,id,type==="CANCEL_GAME"?null:state.questionIndex,payload);send();
  }
  function render(){
    if(disposed || app.gameController!==controller)return;
    if(!state){root.replaceChildren(notice("Đang lấy trạng thái trận…"));return;}
    const arrangementFocus=root.contains(document.activeElement) && document.activeElement.closest("#arrangement-answer")?document.activeElement.id:null;
    const focus=root.contains(document.activeElement) && document.activeElement.id==="answer-text"?{start:document.activeElement.selectionStart,end:document.activeElement.selectionEnd}:null;
    const s=state,p=s.player,ready=app.transport.status==="ready" && synced,policy=permissions(s,app.user.id,ready,!!pending||working),present=presenting(),final=s.status==="FINISHED"&&!present;
    const phase=present?"Kết quả câu":({INTRO:"Giới thiệu màn",DECISION:"Quyết định chiến thuật",QUESTION_OPEN:"Đang trả lời",QUESTION_CLOSED:"Đã đóng câu",SCORING:"Đang chấm",RESULT:"Kết quả câu",FINISHED:"Kết thúc"}[s.phase]);
    root.dataset.phase=s.phase;root.dataset.index=s.questionIndex;root.dataset.revision=s.revision;root.dataset.presenting=String(present);
    const controls=h("div",{className:"actions"});
    controls.append(button(memory.leaderboard?"Ẩn bảng xếp hạng":"Hiện bảng xếp hạng",()=>{memory.leaderboard=!memory.leaderboard;render();},{id:"toggle-standings",className:"secondary","aria-expanded":memory.leaderboard,"aria-controls":"game-leaderboard"}));
    if(s.status==="ACTIVE" && s.members.some(m=>m.userId===app.user.id && m.role==="HOST"))controls.append(button("Hủy trận",()=>{if(window.confirm("Hủy trận? Đáp án chưa chấm sẽ được giữ là đã nhận, chưa chấm."))begin("CANCEL_GAME");},{id:"cancel-game",disabled:!policy.cancel,className:"danger secondary"}));
    const question=h("section",{className:"card game-question"},h("div",{className:"card-top"},h("h2",{id:"game-phase"},phase+" · câu "+s.questionIndex+"/"+s.questionCount),h("span",{className:"countdown",id:"game-countdown","aria-label":"Thời gian còn lại"})));
    const self=h("div",{id:"player-state",className:"player-strip"});
    if(p)append(self,h("span",{},"Điểm: ",h("strong",{id:"player-score"},p.score)),h("span",{},"Spin: ",h("strong",{id:"remaining-spins"},p.remainingSpins)),h("span",{},"Hope Star: ",h("strong",{id:"star-available"},p.starAvailable?"Còn":"Đã dùng")),h("small",{id:"current-decision"},"Spin: "+(effects[p.currentSpin]||"Không dùng")+" · Star: "+(p.starSelected?"đã chọn":"chưa chọn")),p.state==="ELIMINATED"?h("span",{},"Bạn đã bị loại · Đang quan sát"):null);
    else self.append(h("span",{},"Bạn đang quan sát"));
    if(gameMode(s)!=="QUIZ"){self.querySelectorAll("#remaining-spins,#star-available,#current-decision").forEach(n=>n.id==="current-decision"?n.remove():n.parentElement.remove());}
    question.append(self);
    if(s.runtimeState==="UNAVAILABLE")question.append(notice(s.cleanupPending?"Trận không khả dụng. Chưa có kết quả cuối được lưu.":"Trận không khả dụng. Hãy tải lại trạng thái.","error"));
    if(s.runtimeState==="INITIALIZING")question.append(notice("Server đang chuẩn bị màn đầu…"));
    if(!s.question){videoPlayer?.dispose();videoPlayer=null;}
    if(s.phase==="INTRO" && s.stage){
      const players=s.members.filter(m=>m.participation==="PLAYER");
      question.append(h("h3",{id:"intro-title"},`Màn ${s.stageIndex}/${s.stages.length} · ${MODE_LABELS[s.stage.mode]}`),h("p",{},`${s.stage.title} · ${s.stage.questionCount} câu · ${s.stage.questionDurationMs/1000} giây/câu`),notice(s.stage.mode==="QUIZ"?"Chọn một trong4 đáp án. Spin/Star chỉ dùng trong Decision trước câu Quiz.":s.stage.mode==="CLUES"?"Server mở gợi ý theo mốc chung. Nhập đáp án bất cứ lúc nào trước hạn, giữ dấu tiếng Việt. Câu cuối màn đúng được20 điểm.":s.stage.mode==="SONG"?"Xem/nghe video rồi nhập tên bài hát. Bấm Bật tiếng nếu trình duyệt chặn phát; thời gian không dừng. Câu cuối màn đúng được20 điểm.":isArrangement(s.stage.mode)?"Sắp xếp đủ các mảnh / mục rồi gửi. Mỗi mục dùng một lần. Câu cuối màn đúng được20 điểm.":"Nhập đáp án bằng chữ, giữ dấu tiếng Việt. Câu cuối màn đúng được20 điểm."),h("p",{id:"intro-ready"},`${s.readyPlayers.length}/${players.length} người chơi sẵn sàng. Server mở khi đủ hoặc hết10 giây.`));
      if(p)question.append(button(s.readyPlayers.includes(app.user.id)?"Đã sẵn sàng":"Tiếp tục",()=>begin("CONTINUE"),{id:"continue-stage",disabled:!policy.continue}));
      else question.append(notice("Bạn là người quan sát, không cần bấm Tiếp tục."));
    }
    if(s.phase==="DECISION"){
      const ordinary=!!(ready && s.status==="ACTIVE" && s.runtimeState==="READY" && p?.state==="PLAYING" && !pending && !working && !p.currentSpin && !p.starSelected);
      question.append(h("p",{className:"muted"},"Chọn chiến thuật cho câu sắp tới. Không chọn thì chơi thường. Spin trước để có thể thêm Hope Star."),
        h("div",{className:"actions"},button("Dùng Spin",()=>begin("USE_SPIN"),{id:"use-spin",disabled:!policy.spin}),
        button("Dùng Hope Star",()=>begin("USE_STAR"),{id:"use-star",disabled:!policy.star}),
        button("Chơi thường",()=>{feedback="Chơi thường · Chờ câu hỏi mở.";render();},{id:"play-normal",disabled:!ordinary,className:"secondary"})));
    }
    if(s.question){
      append(question,h("h3",{id:"question-content"},s.question.content),image(s,s.question));
      if(gameMode(s)==="CLUES")question.append(releasedClues(s.question.payload,"released-clues"));
      if(gameMode(s)==="SONG") {
        if(videoPlayer?.key!==s.question.id){videoPlayer?.dispose();videoPlayer=songVideo(s,()=>state.serverTimeMs+Math.max(0,performance.now()-sampled),memory);}
        videoPlayer.update(s);question.append(videoPlayer.box);
      } else {videoPlayer?.dispose();videoPlayer=null;}
      if(gameMode(s)==="QUIZ"){
      const options=h("div",{className:"answer-options"});
      for(const k of ["A","B","C","D"]){
        const correct=!!s.question.correctAnswer && k===s.question.correctAnswer;
        const wrong=!!s.question.correctAnswer && p?.selectedOption===k && !correct;
        const pulse=present && animateIndex===s.questionIndex && (correct||wrong);
        const mark=correct?" · Đáp án đúng":wrong?" · Bạn chọn sai":"";
        const node=button(k+". "+s.question.options[k]+mark,()=>{selected=k;render();},{"data-option":k,disabled:!policy.answer,className:"secondary "+(correct?"answer-correct ":wrong?"answer-wrong ":(selected===k||p?.selectedOption===k?"selected ":""))+(pulse?"result-pulse":""),"aria-pressed":selected===k||p?.selectedOption===k});
        if(pulse)node.style.animationDelay="-"+Math.max(0,1500-remaining())+"ms";
        options.append(node);
      }
      question.append(options);
      if(s.phase==="QUESTION_OPEN"){
        if(!p?.alreadyAnswered && p?.state==="PLAYING")question.append(button("Gửi đáp án",()=>begin("ANSWER",{option:selected}),{id:"submit-answer",disabled:!policy.answer || !selected}));
        if(p?.alreadyAnswered)question.append(h("p",{className:"muted",id:"answer-accepted"},"Đã gửi đáp án. Đang chờ kết quả…"));
      }
      } else if(isArrangement(gameMode(s))){
        const items=s.question.payload?.pieces||s.question.payload?.items||[],ids=p?.submittedAnswer?.itemIds??arrangementDraft;
        const result=s.results.find(r=>r.userId===app.user.id);
        question.append(arrangementAnswer(items,ids,policy.answer,next=>{arrangementDraft=next;render();}));
        if(gameMode(s)==="VIETNAMESE_PUZZLE")question.append(h("p",{id:"joined-pieces",className:"piece-text"},ids.map(id=>items.find(v=>v.id===id)?.text||"").join("")));
        if(s.phase==="QUESTION_OPEN" && p?.state==="PLAYING"){
          if(p.alreadyAnswered)question.append(h("p",{id:"answer-accepted",className:"muted"},"Đã gửi đáp án. Đang chờ kết quả…"));
          else question.append(button("Gửi đáp án",()=>begin("ANSWER",{itemIds:arrangementDraft}),{id:"submit-answer",disabled:!policy.answer||!permutation(items,arrangementDraft)}));
        }
        if(result)question.append(notice(outcomes[result.outcome],result.outcome==="CORRECT"?"success":result.outcome==="WRONG"?"error":"info"));
        if(s.question.payload?.correctOrder)question.append(h("p",{id:"correct-arrangement"},gameMode(s)==="VIETNAMESE_PUZZLE"?"Đáp án: "+(s.question.payload.acceptedAnswers||[correctArrangement(s.question).join("")]).join(" / "):"Thứ tự đúng: "+correctArrangement(s.question).join(" → ")));
      } else if(["CLUES","RIDDLE","IMAGE_WORD","SONG"].includes(gameMode(s))){
        const result=s.results.find(r=>r.userId===app.user.id),scored=!!result;
        const answer=h("textarea",{id:"answer-text",name:"answerText",rows:2,maxLength:300,placeholder:"Nhập đáp án…",value:p?.submittedAnswer?.text??textDraft,disabled:!policy.answer,className:scored?(result.outcome==="CORRECT"?"answer-correct":result.outcome==="WRONG"?"answer-wrong":""):""});
        if(p)question.append(h("label",{className:"field"},h("span",{},"Đáp án của bạn"),answer));
        answer.oninput=()=>{textDraft=answer.value;const submit=root.querySelector("#submit-answer");if(submit)submit.disabled=!policy.answer || !textDraft.trim();};
        if(s.phase==="QUESTION_OPEN" && p?.state==="PLAYING"){
          if(p.alreadyAnswered)question.append(h("p",{id:"answer-accepted",className:"muted"},"Đã gửi đáp án. Đang chờ kết quả…"));
          else question.append(button("Gửi đáp án",()=>begin("ANSWER",{text:textDraft}),{id:"submit-answer",disabled:!policy.answer || !textDraft.trim()}));
        }
        if(scored)question.append(notice(outcomes[result.outcome],result.outcome==="CORRECT"?"success":result.outcome==="WRONG"?"error":"info"));
        if(s.question.payload?.acceptedAnswers)question.append(h("p",{id:"correct-text"},"Đáp án được chấp nhận: "+s.question.payload.acceptedAnswers.join(" / ")));
      }
      if(s.status==="FINISHED" && p?.alreadyAnswered && !s.results.length)question.append(notice("Đáp án đã được nhận, chưa chấm. Không cộng thời gian câu này vào tổng xếp hạng."));
    }
    const actionFeedback=h("div",{id:"game-feedback","aria-live":"polite"});
    if(working)actionFeedback.append(notice("Đang gửi…"));
    if(feedback)actionFeedback.append(notice(feedback, pending?"error":"info"));
    if(memory.lastAck)actionFeedback.append(h("span",{id:"game-ack",hidden:true,"data-request-id":memory.lastAck.requestId},memory.lastAck.type));
    if(pending && !working)actionFeedback.append(button("Thử lại cùng yêu cầu",send,{id:"retry-game-command",disabled:!ready,className:"secondary"}));
    const board=standings(s,app.user.id);board.id="game-leaderboard";board.hidden=!memory.leaderboard;board.classList.add("game-leaderboard");
    const toasts=h("div",{className:"game-toasts","aria-live":"polite",id:"game-toasts"});
    if(toast && performance.now()<toastUntil)toasts.append(notice(toast.text,toast.kind));
    const main=h("div",{className:"game-main"},final?finalSummary(s):null,final?null:question,actionFeedback);
    root.replaceChildren(heading(gameTitle(s),"Trận #"+id+(s.stage?` · Màn ${s.stageIndex}/${s.stages.length} · ${MODE_LABELS[s.stage.mode]}`:""),link("Home","home","button secondary")),controls,h("div",{className:"game-layout"+(!memory.leaderboard?" board-hidden":"")},main,board),toasts);
    if(focus){const answer=root.querySelector("#answer-text");if(answer && !answer.disabled){answer.focus({preventScroll:true});answer.setSelectionRange(focus.start,focus.end);}}
    if(arrangementFocus){
      let n=document.getElementById(arrangementFocus);
      if(!n || n.disabled){const match=arrangementFocus.match(/^(piece|remove|up|down)-(.+)$/);if(match)n=document.getElementById((match[1]==="remove"?"piece-":"remove-")+match[2]);}
      if(n && root.contains(n)&&!n.disabled)n.focus({preventScroll:true});
    }
    tick(false);
  }
  function tick(allowRender=true){
    if(disposed || !state)return;
    if(allowRender && root.dataset.presenting==="true" && !presenting()){render();return;}
    const node=root.querySelector("#game-countdown"),ms=remaining();
    videoPlayer?.sync();
    if(node)node.textContent=ms==null?"—":ms===0?"Chờ chuyển câu":(ms/1000).toFixed(1)+" s";
    if(toast && performance.now()>=toastUntil){toast=null;root.querySelector("#game-toasts")?.replaceChildren();}
  }
  clock=setInterval(tick,100);render();
  try{adopt(await app.api.request("GET","/api/games/"+id+"/snapshot"));}
  catch(e){if(!disposed){feedback=e.message;if(!state)root.replaceChildren(notice(e.message,"error"),button("Tải lại",()=>app.render()));}}
  if(!disposed){if(app.transport.status==="ready")controller.reconnect();else app.connect();}
  return root;
}
