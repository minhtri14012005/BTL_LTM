import {loadingState,errorState,toast as toastNotice} from "/js/shared/ui.js";
import {releasedClues} from "/js/features/game/clues.js";
import {h,button,link,notice,heading} from "/js/shared/dom.js";
import {gameCommand,permissions,acceptSnapshot,applyReceipt,countdown,effects,outcomes,gameMode} from "/js/features/game/state.js";
import {arrangementAnswer,vietnameseAnswer,correctArrangement,isArrangement,permutation} from "/js/features/game/arrangement.js";
import {songVideo} from "/js/features/game/video.js";
import {MODE_LABELS} from "/js/shared/mode-labels.js";
import {standings,finalSummary,image} from "/js/features/results/view.js";
import {icon} from "/js/shared/icons.js";
import {modeHeader,timerPanel,interactionStatus,modeInstruction,publishedResult,imagePanel,playerHud,gameStandings} from "/js/features/game/view.js";
import {quizAnswers,textAnswer,acceptedAnswer} from "/js/features/game/answers.js";
import {roomReturn} from "/js/features/results/navigation.js";
import {awardPanel} from "/js/features/results/awards.js";
function append(node,...children){node.append(...children.filter(child=>child!=null));}

export async function gamePage(app,id) {
  const root=h("section",{id:"game-page"});
  if(app.kickedGames?.has(id)){app.gameRevoked(id);return root;}
  app.gamePresentation??=new Map();
  const memory=app.gamePresentation.get(id)||{leaderboard:true,actions:new Set(),results:new Set()};
  app.gamePresentation.set(id,memory);
  // In-memory draft only; bind it to the player, game, question and exact pieces.
  const draftKey=s=>JSON.stringify([app.user.id,id,s?.questionIndex,s?.question?.payload?.pieces]);
  let state=app.games.get(id)||null,selected=null,textDraft="",arrangementDraft=[],pending=null,working=false,synced=false,reconnecting=false,feedback=null,sampled=performance.now(),clock=null,disposed=false,toast=null,toastUntil=0,animateIndex=null,videoPlayer=null;
  let feedbackError=false;
  let resultRoom=null,awards=null;
  const controller={id,dispose(){if(disposed)return;disposed=true;clearInterval(clock);videoPlayer?.dispose();resultRoom?.dispose();awards?.dispose();},update(){render();},receive(m){
    if(disposed || m.target?.kind!=="GAME" || m.target.id!==id || m.kind!=="EVENT")return;
    adopt(m.payload,true);
  },async reconnect(){
    if(disposed || reconnecting || app.transport.status!=="ready")return;
    reconnecting=true;synced=false;render();
    try{const ack=await app.transport.send(gameCommand("RECONNECT",id,null));if(!disposed){adopt(ack.payload);synced=true;feedback=null;resultRoom?.refresh();}}
    catch(e){if(e.code==="GAME_KICKED"){app.gameRevoked?.(id);return;}if(!disposed){feedback=e.message;feedbackError=true;}}
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
    working=true;feedback=null;feedbackError=false;render();const frame=pending;
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
      if(e.code==="GAME_KICKED"){app.gameRevoked?.(id);return;}
      if(disposed)return;feedback=e.message;feedbackError=true;
      if(!e.retryable && !["SESSION_REPLACED","DISCONNECTED"].includes(e.code))pending=null;
    }finally{working=false;if(!disposed)render();}
  }
  function begin(type,payload={}){
    if(working || pending)return;
    pending=gameCommand(type,id,type==="CANCEL_GAME"?null:state.questionIndex,payload);send();
  }
  function render(){
    if(disposed || app.gameController!==controller)return;
    const scrollLeft=root.querySelector("#sentence-row")?.scrollLeft||0;
    if(!state){root.replaceChildren(feedback?errorState(feedback,()=>app.render()):loadingState("Đang lấy trạng thái trận…"));return;}
    const arrangementFocus=root.contains(document.activeElement) && document.activeElement.closest("#arrangement-answer")?document.activeElement.id:null;
    const controlFocus=root.contains(document.activeElement)?document.activeElement.id:null;
    const focus=root.contains(document.activeElement) && document.activeElement.id==="answer-text"?{start:document.activeElement.selectionStart,end:document.activeElement.selectionEnd}:null;
    const s=state,p=s.player,ready=app.transport.status==="ready" && synced,policy=permissions(s,app.user.id,ready,!!pending||working),present=presenting(),final=s.status==="FINISHED"&&!present;
    const phase=present?"Kết quả câu":({INTRO:"Giới thiệu màn",DECISION:"Quyết định chiến thuật",QUESTION_OPEN:"Đang trả lời",QUESTION_CLOSED:"Đã đóng câu",SCORING:"Đang chấm",RESULT:"Kết quả câu",FINISHED:"Kết thúc"}[s.phase]||"Đang chuẩn bị");
    root.dataset.phase=s.phase;root.dataset.index=s.questionIndex;root.dataset.revision=s.revision;root.dataset.presenting=String(present);
    root.dataset.mode=gameMode(s);
    const planOpen=!!root.querySelector(".game-stage-plan")?.open;
    const helpOpen=!!root.querySelector(".game-help")?.open;
    const resultTopOpen=!!root.querySelector(".result-top")?.open;
    const controls=h("div",{className:"actions game-toolbar"});
    controls.append(button(memory.leaderboard?"Ẩn bảng xếp hạng":"Hiện bảng xếp hạng",()=>{memory.leaderboard=!memory.leaderboard;render();},{id:"toggle-standings",className:"secondary","aria-expanded":memory.leaderboard,"aria-controls":"game-leaderboard"}));
    if(s.status==="ACTIVE" && s.members.some(m=>m.userId===app.user.id && m.role==="HOST"))controls.append(button("Hủy trận",()=>{if(window.confirm("Hủy trận? Đáp án chưa chấm sẽ được giữ là đã nhận, chưa chấm."))begin("CANCEL_GAME");},{id:"cancel-game",disabled:!policy.cancel,className:"danger secondary"}));
    const question=h("section",{className:"card game-question","aria-labelledby":"game-phase"},h("div",{className:"game-question-top"},h("div",{},h("p",{className:"eyebrow"},"CÂU "+s.questionIndex+" / "+s.questionCount),h("h2",{id:"game-phase"},h("span",{className:"phase-dot","aria-hidden":"true"}),phase)),timerPanel()));
    if(!s.question)question.append(playerHud(s,effects));
    if(s.runtimeState==="UNAVAILABLE")question.append(notice(s.cleanupPending?"Trận không khả dụng. Chưa có kết quả cuối được lưu.":"Trận không khả dụng. Hãy tải lại trạng thái.","error"));
    if(s.runtimeState==="INITIALIZING")question.append(notice("Server đang chuẩn bị màn đầu…"));
    if(!s.question){videoPlayer?.dispose();videoPlayer=null;}
    if(s.phase==="INTRO" && s.stage){
      const players=s.members.filter(m=>m.participation==="PLAYER");
      question.append(h("div",{className:"game-intro"},h("span",{className:"intro-symbol","aria-hidden":"true"},icon("gamepad")),h("h3",{id:"intro-title"},`Màn ${s.stageIndex}/${s.stages.length} · ${MODE_LABELS[s.stage.mode]}`),h("p",{className:"intro-description"},`${s.stage.title} · ${s.stage.questionCount} câu · ${s.stage.questionDurationMs/1000} giây/câu`),notice(modeInstruction(s.stage.mode)),h("p",{id:"intro-ready"},`${s.readyPlayers.length}/${players.length} người chơi sẵn sàng. Server mở khi đủ hoặc hết 10 giây.`)));
      if(p)question.append(button(s.readyPlayers.includes(app.user.id)?"Đã sẵn sàng":"Tiếp tục",()=>begin("CONTINUE"),{id:"continue-stage",disabled:!policy.continue}));
      else question.append(notice("Bạn là người quan sát, không cần bấm Tiếp tục."));
    }
    if(s.phase==="DECISION"){
      const ordinary=!!(ready && s.status==="ACTIVE" && s.runtimeState==="READY" && p?.state==="PLAYING" && !pending && !working && !p.currentSpin && !p.starSelected);
      question.append(h("h3",{className:"decision-title"},"Tài nguyên câu Quiz"),h("p",{className:"muted"},"Chọn chiến thuật cho câu sắp tới. Không chọn thì chơi thường. Spin trước để có thể thêm Hope Star."),
        h("div",{className:"decision-actions"},button([icon("target"),h("strong",{},"Dùng Spin"),h("small",{},"Nhận hiệu ứng từ trận")],()=>begin("USE_SPIN"),{id:"use-spin",disabled:!policy.spin,className:"secondary decision-spin"}),
        button([icon("sparkles"),h("strong",{},"Dùng Hope Star"),h("small",{},"Tài nguyên một lần")],()=>begin("USE_STAR"),{id:"use-star",disabled:!policy.star,className:"secondary decision-star"}),
        button([icon("check"),h("strong",{},"Chơi thường"),h("small",{},"Giữ tài nguyên của bạn")],()=>{feedback="Chơi thường · Chờ câu hỏi mở.";feedbackError=false;render();},{id:"play-normal",disabled:!ordinary,className:"secondary"})));
      if(p?.remainingSpinPool?.length)question.append(h("div",{className:"spin-pool","aria-label":"Hiệu ứng Spin còn trong tài nguyên của bạn"},h("span",{className:"muted"},"Hiệu ứng còn lại: "),p.remainingSpinPool.map(effect=>h("span",{className:"badge"},effects[effect]||effect))));
    }
    if(s.question){
      append(question,h("h3",{id:"question-content"},s.question.content||"Chưa có nội dung câu hỏi."),imagePanel(image(s,s.question)));
      if(gameMode(s)==="IMAGE_WORD" && !s.question.imageRef)question.append(notice("Hình của câu này chưa khả dụng. Thời gian câu vẫn tiếp tục; bạn có thể nhập đáp án.","error"));
      if(gameMode(s)==="CLUES")question.append(releasedClues(s.question.payload,"released-clues"));
      if(gameMode(s)==="SONG") {
        if(videoPlayer?.key!==s.question.id){videoPlayer?.dispose();videoPlayer=songVideo(s,()=>state.serverTimeMs+Math.max(0,performance.now()-sampled),memory);}
        videoPlayer.update(s);question.append(videoPlayer.box);
      } else {videoPlayer?.dispose();videoPlayer=null;}
      question.append(playerHud(s,effects));
      if(gameMode(s)==="QUIZ"){
        question.append(quizAnswers(s,{policy,selected,present,animateIndex,remaining,onSelect:k=>{selected=k;render();},onSubmit:()=>begin("ANSWER",{option:selected})}));
      } else if(isArrangement(gameMode(s))){
        const items=s.question.payload?.pieces||s.question.payload?.items||[];
        const result=publishedResult(s)?s.results.find(r=>r.userId===app.user.id):null;
        const vua=gameMode(s)==="VIETNAMESE_PUZZLE",editable=policy.answer && (!vua || remaining()>0);
        if(vua && !p?.alreadyAnswered){
          if(memory.vietnameseDraft?.key===draftKey(s))arrangementDraft=[...memory.vietnameseDraft.ids];
          else {arrangementDraft=[];memory.vietnameseDraft={key:draftKey(s),ids:[]};}
        }
        const draftIds=p?.submittedAnswer?.itemIds??arrangementDraft;
        const onChange=next=>{
          if(vua && (!permissions(state,app.user.id,app.transport.status==="ready"&&synced,!!pending||working).answer || remaining()<=0 || draftKey(state)!==draftKey(s)))return;
          arrangementDraft=[...next];
          if(vua)memory.vietnameseDraft={key:draftKey(s),ids:[...next]};
          render();
          if(vua){const row=root.querySelector("#sentence-row");if(row)row.scrollLeft=row.scrollWidth;}
        };
        question.append((vua?vietnameseAnswer:arrangementAnswer)(items,draftIds,editable,onChange));
        if(vua)question.append(h("p",{id:"joined-pieces",className:"piece-text"},draftIds.map(id=>items.find(v=>v.id===id)?.text||"").join("")));
        if(s.phase==="QUESTION_OPEN" && p?.state==="PLAYING"){
          if(p.alreadyAnswered)question.append(acceptedAnswer());
          else question.append(button(vua?"Nộp đáp án":"Gửi đáp án",()=>{if(vua && (!permissions(state,app.user.id,app.transport.status==="ready"&&synced,!!pending||working).answer || remaining()<=0))return;begin("ANSWER",{itemIds:arrangementDraft});},{id:"submit-answer",disabled:!editable||!permutation(items,arrangementDraft)}));
        }
        if(result)question.append(notice(outcomes[result.outcome],result.outcome==="CORRECT"?"success":result.outcome==="WRONG"?"error":"info"));
        if(publishedResult(s) && s.question.payload?.correctOrder)question.append(h("p",{id:"correct-arrangement"},gameMode(s)==="VIETNAMESE_PUZZLE"?"Đáp án: "+(s.question.payload.acceptedAnswers||[correctArrangement(s.question).join("")]).join(" / "):"Thứ tự đúng: "+correctArrangement(s.question).join(" → ")));
      } else if(["CLUES","RIDDLE","IMAGE_WORD","SONG"].includes(gameMode(s))){
        question.append(textAnswer(s,{policy,textDraft,userId:app.user.id,onInput:value=>{textDraft=value;const submit=root.querySelector("#submit-answer");if(submit)submit.disabled=!policy.answer || !textDraft.trim();},onSubmit:()=>begin("ANSWER",{text:textDraft})}));
      }
      if(s.status==="FINISHED" && p?.alreadyAnswered && !s.results.length)question.append(notice("Đáp án đã được nhận, chưa chấm. Không cộng thời gian câu này vào tổng xếp hạng."));
    }
    if(!s.question && ["QUESTION_OPEN","QUESTION_CLOSED","SCORING"].includes(s.phase))question.append(notice("Chưa nhận được nội dung câu hỏi. Hãy kết nối lại để đồng bộ; thời gian trận vẫn tiếp tục.","error"));
    const actionFeedback=h("div",{id:"game-feedback","aria-live":"polite"});
    if(working)actionFeedback.append(notice("Đang gửi…"));
    if(feedback)actionFeedback.append(notice(feedback, feedbackError||pending?"error":"info"));
    if(memory.lastAck)actionFeedback.append(h("span",{id:"game-ack",hidden:true,"data-request-id":memory.lastAck.requestId},memory.lastAck.type));
    if(pending && !working)actionFeedback.append(button("Thử lại cùng yêu cầu",send,{id:"retry-game-command",disabled:!ready,className:"secondary"}));
    const board=gameStandings(standings(s,app.user.id));board.id="game-leaderboard";board.hidden=!memory.leaderboard;board.classList.add("game-leaderboard");
    const toasts=h("div",{className:"game-toasts","aria-live":"polite",id:"game-toasts"});
    if(toast && performance.now()<toastUntil)toasts.append(toastNotice(toast.text,toast.kind));
    if(final && !resultRoom)resultRoom=roomReturn(app,s.roomId,()=>!disposed && app.gameController===controller);
    if(final && !awards)awards=awardPanel(app,id,()=>!disposed && app.gameController===controller);
    append(question,interactionStatus(s,{ready,reconnecting,pending,working}));
    const main=h("div",{className:"game-main"},final?finalSummary(s,{selfId:app.user.id,roomAction:resultRoom.node}):null,final?awards.node:null,final?null:question,actionFeedback);
    root.replaceChildren(h("header",{className:"game-commandbar"},heading(s.quizTitleSnapshot||"Trận nhiều màn","Trận #"+id,h("a",{href:"#/home",className:"button secondary game-home","aria-label":"Về sảnh"},icon("home"),h("span",{className:"game-home-label"},"Về sảnh"))),controls),modeHeader(s),h("div",{className:"game-layout"+(!memory.leaderboard?" board-hidden":"")},main,board),toasts);
    const plan=root.querySelector(".game-stage-plan");if(plan)plan.open=planOpen;
    const help=root.querySelector(".game-help");if(help)help.open=helpOpen;
    const resultTop=root.querySelector(".result-top");if(resultTop)resultTop.open=resultTopOpen;
    if(gameMode(s)==="VIETNAMESE_PUZZLE"){const row=root.querySelector("#sentence-row");if(row)row.scrollLeft=scrollLeft;}
    if(focus){const answer=root.querySelector("#answer-text");if(answer && !answer.disabled){answer.focus({preventScroll:true});answer.setSelectionRange(focus.start,focus.end);}}
    if(arrangementFocus){
      let n=document.getElementById(arrangementFocus);
      if(!n || n.disabled){const match=arrangementFocus.match(/^(piece|remove|up|down)-(.+)$/);if(match)n=document.getElementById((match[1]==="remove"?"piece-":"remove-")+match[2]);}
      if((!n || n.disabled) && gameMode(s)==="VIETNAMESE_PUZZLE")n=root.querySelector("#arrangement-answer .piece-bank button:not(:disabled)")||root.querySelector("#sentence-row");
      if(n && root.contains(n)&&!n.disabled)n.focus({preventScroll:true});
    }
    if(controlFocus && !focus && !arrangementFocus){const n=root.querySelector(`#${CSS.escape(controlFocus)}`);if(n && !n.disabled && n.getClientRects().length)n.focus({preventScroll:true});}
    tick(false);
  }
  function tick(allowRender=true){
    if(disposed || !state)return;
    if(allowRender && root.dataset.presenting==="true" && !presenting()){render();return;}
    const node=root.querySelector("#game-countdown"),ms=remaining();
    if(gameMode(state)==="VIETNAMESE_PUZZLE" && ms===0)root.querySelectorAll("#arrangement-answer button,#submit-answer").forEach(b=>{b.disabled=true;});
    videoPlayer?.sync();
    if(node)node.textContent=ms==null?"—":ms===0?"Chờ chuyển câu":(ms/1000).toFixed(1)+" s";
    const timer=root.querySelector('.game-timer');if(timer)timer.dataset.urgent=String(ms!=null && ms>0 && ms<=5000);
    const deadline=root.querySelector('#deadline-status');if(deadline)deadline.textContent=ms===0?"Chờ Server xác nhận chuyển pha":"Theo thời gian trận";
    if(toast && performance.now()>=toastUntil){toast=null;root.querySelector("#game-toasts")?.replaceChildren();}
  }
  clock=setInterval(tick,100);render();
  try{adopt(await app.api.request("GET","/api/games/"+id+"/snapshot"));}
  catch(e){if(e.code==="GAME_KICKED"){controller.dispose();app.gameRevoked?.(id);return root;}if(!disposed){feedback=e.message;if(!state)root.replaceChildren(errorState(e.message,()=>app.render()));}}
  if(!disposed){if(app.transport.status==="ready")controller.reconnect();else app.connect();}
  return root;
}
