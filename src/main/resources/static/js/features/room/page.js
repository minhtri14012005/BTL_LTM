import {confirmPanel,setConfirmation} from "/js/shared/ui.js";
import {h,field,input,select,button,link,notice,heading,values,action,busy} from "/js/shared/dom.js";
import {uuid,command} from "/js/core/protocol.js";
import {startIssue} from "/js/features/room/validation.js";
import {inviteCard,rosterCard,planCard} from "/js/features/room/view.js";
import {icon} from "/js/shared/icons.js";
export function joinPage(app) {
  const form=h("form",{id:"join-form",className:"card join-form"},h("p",{className:"eyebrow"},"BẠN ĐÃ CÓ MÃ MỜI?"),h("h2",{},"Nhập mã và cùng chơi"),field("Mã phòng",input("code",{required:true,minLength:12,maxLength:12,pattern:"[a-zA-Z2-9]{12}",autocomplete:"off",autocapitalize:"characters",spellcheck:false}),"12 ký tự; phòng phải đang chờ."),field("Tham gia với vai trò",select("participation",[["PLAYER","Người chơi"],["SPECTATOR","Người quan sát"]])),h("button",{type:"submit"},"Tham gia"));
  form.onsubmit=e=>{e.preventDefault();const data=values(form);let frame;action(app,form,async()=>{if(!frame){const roomCode=data.code.trim().toUpperCase();const preview=await app.api.request("GET",`/api/rooms/by-code/${encodeURIComponent(roomCode)}`);frame=command("JOIN_ROOM",preview.id,{roomCode,participation:data.participation});}await app.connect(true);return app.transport.send(frame);},ack=>{app.adoptRoom(ack.payload);app.navigate(`room/${ack.payload.id}`);});};
  return h("section",{className:"join-page"},heading("Tham gia phòng","Nhập mã do Host chia sẻ để trở lại cuộc chơi."),
    h("div",{className:"join-layout"},form,h("aside",{className:"join-guide","aria-labelledby":"join-guide-title"},h("h2",{id:"join-guide-title"},"Chọn cách tham gia"),
      h("section",{},icon("gamepad"),h("div",{},h("h3",{},"Người chơi"),h("p",{className:"muted"},"Trả lời câu hỏi và tham gia các thử thách cùng phòng."))),
      h("section",{},icon("book"),h("div",{},h("h3",{},"Người quan sát"),h("p",{className:"muted"},"Theo dõi trận đấu. Người quan sát không chiếm chỗ chơi."))),
      link("Về phòng của tôi","rooms","text-link"))));
}
export async function waitingPage(app,id) {
  const version=app.renderVersion;const initial=await app.api.request("GET",`/api/rooms/${id}`);
  if(version!==app.renderVersion) return h("div");
  app.adoptRoom(initial);app.activeRoomId=id;let room=app.rooms.get(id),quiz=null,questionCount=10,quizLoading=false,quizError=null;const root=h("section",{id:"waiting-room",className:"waiting-page"});
  let disposed=false,subscribing=false,subscribedGeneration=null;
  const current=()=>!disposed && app.roomController===controller;
  const controller={id,dispose(){disposed=true;},update(){if(!current())return;room=app.rooms.get(id);render();loadQuiz();},async subscribe(){
    const generation=app.transport.generation;
    if(!current() || app.transport.status!=="ready" || subscribing || subscribedGeneration===generation)return;
    subscribing=true;
    try{const ack=await app.transport.request("SUBSCRIBE_ROOM",id,{});if(current() && app.transport.generation===generation){subscribedGeneration=generation;app.adoptRoom(ack.payload);}}
    catch(e){if(current())root.append(notice(e.message,"error"));}
    finally{subscribing=false;if(current() && app.transport.status==="ready" && app.transport.generation!==generation)controller.subscribe();}
  }};
  app.roomController=controller;
  function loadQuiz(){if(!current() || room.configVersion===2)return;if(quizLoading || quiz?.id===room.quizId)return;quizLoading=true;quizError=null;const qid=room.quizId;app.api.request("GET",`/api/quizzes/${qid}`).then(q=>{if(current() && room.quizId===qid){quiz=q;render();}}).catch(e=>{if(current() && room.quizId===qid){quizError=e.message;render();}}).finally(()=>{quizLoading=false;if(current() && room.quizId!==qid)loadQuiz();});}
  function restControl(type,label){return button(label,()=>{const body={requestId:uuid(),revision:room.revision};action(app,root,()=>app.api.request("POST",`/api/rooms/${id}/${type}`,body),saved=>app.adoptRoom(saved));},{id:`${type}-room`,className:type==="close"?"danger secondary":""});}
  function wsControl(type,payload,label,success,props={}){return button(label,()=>{const frame=command(type,id,payload);action(app,root,()=>app.transport.send(frame),success);},{disabled:app.transport.status!=="ready",...props});}
  function render(){if(!current())return;const feedback=root.querySelector('.action-feedback');
    const focusedId=root.contains(document.activeElement)?document.activeElement.id:null;
    // Snapshot renders keep the reader's position within a long roster.
    const rosterScroll=root.querySelector("#roster")?.scrollTop||0;
    const host=room.hostUserId===app.user.id,waiting=room.status==="WAITING",ready=app.transport.status==="ready";
    const controls=h("div",{className:"actions room-host-controls","aria-label":host?"Thao tác Host":"Thao tác thành viên"});if(host && ["DRAFT","WAITING"].includes(room.status)){controls.append(link("Sửa cấu hình",`room/${id}/edit`,"button secondary"));if(room.status==="DRAFT")controls.append(restControl("open","Mở phòng"));
      const confirm=confirmPanel("Đóng phòng và ngừng nhận thành viên?","close-confirm");confirm.append(button("Giữ phòng",()=>setConfirmation(confirm,false),{className:"secondary"}),restControl("close","Xác nhận đóng"));controls.append(button("Đóng phòng",event=>setConfirmation(confirm,true,event.currentTarget),{className:"danger secondary",id:"show-close-room"}),confirm);}
    if(!host && waiting)controls.append(wsControl("LEAVE_ROOM",{},"Rời phòng",()=>{app.rooms.delete(id);app.navigate("rooms");},{id:"leave-room",className:"danger secondary"}));
    const members=rosterCard(app,room,m=>host && waiting && !m.host?wsControl("REMOVE_MEMBER",{userId:m.userId},"Mời ra",ack=>app.adoptRoom(ack.payload),{className:"text-button danger","data-remove":m.userId}):null,subscribedGeneration===app.transport.generation);
    const config=planCard(room);
    if(waiting){const self=room.members.find(m=>m.userId===app.user.id);const role=select("participation",[["PLAYER","Người chơi"],["SPECTATOR","Người quan sát"]],self?.participation);if(quiz?.ownerUserId===app.user.id || room.stages?.some(s=>s.ownerUserId===app.user.id))role.querySelector('[value="PLAYER"]').disabled=true;
      const change=button("Lưu vai trò",()=>{const frame=command("JOIN_ROOM",id,{roomCode:room.roomCode,participation:role.value});action(app,root,()=>app.transport.send(frame),ack=>app.adoptRoom(ack.payload));},{disabled:!ready,id:"change-participation",className:"secondary"});config.append(h("div",{className:"room-role"},h("h3",{},"Vai trò của bạn"),field("Vai trò trước Start",role),change,h("p",{className:"muted"},"Tác giả bộ câu hỏi chỉ được quan sát. Server kiểm tra lại mỗi lần đổi vai trò.")));}
    const start=h("form",{id:"start-form",className:"card room-start"},h("p",{className:"eyebrow"},host?"ĐIỀU KHIỂN HOST":"TRẠNG THÁI TRẬN"),h("h2",{},host?"Sẵn sàng bắt đầu":"Chờ hiệu lệnh từ Host"));
    if(host && waiting){const v2=room.configVersion===2;if(v2)questionCount=room.stages.reduce((n,s)=>n+s.questionCount,0);const n=input("questionCount",{type:"number",required:true,min:v2?1:10,max:v2?50:Math.min(50,quiz?.questionCount||50),value:questionCount,readOnly:v2});const issue=notice("");const go=h("button",{type:"submit",id:"start-game"},"Bắt đầu trận");
      const check=()=>{questionCount=Number(n.value);const reason=v2?startIssue(room,questionCount):room.quizDeleted?"Bộ câu hỏi đã bị xóa. Hãy chọn bộ khác.":quizError || (quiz?.id!==room.quizId?"Đang tải số câu của bộ đã chọn…":startIssue(room,questionCount,quiz.questionCount));issue.textContent=reason || (!ready?"Kết nối lại để gửi yêu cầu bắt đầu.":"Đã đủ số người và cấu hình để gửi yêu cầu bắt đầu.");go.disabled=!ready || !!reason;};n.oninput=check;check();start.append(field("Số câu của trận",n),issue,go);if(quizError)start.append(button("Tải lại bộ đã chọn",loadQuiz,{className:"secondary"}));start.onsubmit=e=>{e.preventDefault();const frame=command("START_GAME",id,{revision:room.revision,questionCount});action(app,root,()=>app.transport.send(frame),ack=>app.enterGame(ack.payload.gameSessionId));};
    }else {const known=[...app.games.values()].find(g=>g.roomId===id && g.status==="ACTIVE");if(known && room.status==="ACTIVE")start.append(link("Trở lại trận đang chơi",`game/${known.gameSessionId}`,"button"));start.append(notice(room.status==="CLOSED"?"Phòng đã đóng và không còn nhận thành viên.":room.status==="ACTIVE"?"Trận đang diễn ra. Thành viên đang kết nối sẽ được chuyển vào trận khi có thông báo bắt đầu.":host?"Mở phòng và mời ít nhất 3 người chơi trước khi bắt đầu.":room.status==="DRAFT"?"Phòng chưa mở. Chờ Host mở mã mời.":"Chờ Host bắt đầu trận."));}
    const self=room.members.find(m=>m.userId===app.user.id);
    root.replaceChildren(heading(room.name,`Phòng #${room.id} · ${host?"Bạn là Host":self?.participation==="PLAYER"?"Bạn tham gia chơi":"Bạn đang quan sát"}`,h("span",{className:"badge room-status","data-status":room.status},app.statusLabel(room.status))),
      inviteCard(room),controls,h("div",{className:"room-layout"},members,h("div",{className:"room-sidebar"},start,config)));
    root.querySelector("#roster").scrollTop=rosterScroll;
    if(feedback)root.append(feedback);if(root.getAttribute('aria-busy')==='true')busy(root,true);
    if(focusedId){const next=root.querySelector(`#${CSS.escape(focusedId)}`);if(next && !next.disabled && next.getClientRects().length)next.focus({preventScroll:true});}
  }
  render();loadQuiz();if(app.transport.status==="ready")controller.subscribe();else app.connect();return root;
}
