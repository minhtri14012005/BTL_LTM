import {h,field,input,select,button,link,notice,heading,values,action,busy} from "./dom.js";
import {uuid,roomValues,startIssue,command} from "./core.js";

export async function roomList(app,page=0) {
  const result=await app.api.request("GET",`/api/rooms?page=${page}&size=12`);
  return h("section",{},heading("Phòng của tôi","Các phòng bạn đang là thành viên.",link("Tạo phòng","rooms/new","button")),
    h("div",{className:"actions"},link("Nhập mã phòng","join","button secondary")),
    result.items.length?h("div",{className:"card-grid"},result.items.map(r=>h("article",{className:"card"},h("span",{className:"badge"},app.statusLabel(r.status)),h("h2",{},link(r.name,`room/${r.id}`)),h("p",{className:"muted"},`${r.members.filter(m=>m.participation==="PLAYER").length}/${r.maxPlayers} người chơi · ${r.hostUserId===app.user.id?"Bạn là Host":"Thành viên"}`),link("Mở phòng",`room/${r.id}`,"button secondary")))):notice("Chưa có phòng nào."),
    h("div",{className:"pagination"},page>0?link("← Trang trước",`rooms?page=${page-1}`,"button secondary"):null,h("span",{},`Trang ${page+1}`),(page+1)*result.size<result.totalElements?link("Trang sau →",`rooms?page=${page+1}`,"button secondary"):null));
}
export async function roomEditor(app,id,chosen) {
  const room=id?await app.api.request("GET",`/api/rooms/${id}`):null;
  if(room && (room.hostUserId!==app.user.id || !["DRAFT","WAITING"].includes(room.status))) return notice("Chỉ Host được sửa phòng Draft hoặc Waiting.","error");
  const quizzes=await app.api.request("GET","/api/quizzes?size=100");let page=0;const catalogue=new Map(quizzes.items.map(q=>[q.id,q]));
  const selected=room?.quizId || Number(chosen) || quizzes.items[0]?.id;
  if(selected && !catalogue.has(selected)) {const q=await app.api.request("GET",`/api/quizzes/${selected}`);catalogue.set(q.id,q);}
  const picker=select("quizId",[...catalogue.values()].map(q=>[q.id,`${q.title} (${q.questionCount} câu)`]),String(selected));
  const participation=select("hostParticipation",[["PLAYER","Tham gia chơi"],["SPECTATOR","Quan sát"]],room?.members.find(m=>m.host)?.participation||"SPECTATOR");
  const authorRule=()=>{const own=catalogue.get(Number(picker.value))?.ownerUserId===app.user.id;participation.querySelector('[value="PLAYER"]').disabled=own;if(own) participation.value="SPECTATOR";};picker.onchange=authorRule;authorRule();
  const more=button("Tải thêm bộ câu hỏi",async()=>{more.disabled=true;try{const next=await app.api.request("GET",`/api/quizzes?page=${++page}&size=100`);next.items.forEach(q=>{if(!catalogue.has(q.id)){catalogue.set(q.id,q);picker.append(h("option",{value:q.id},`${q.title} (${q.questionCount} câu)`));}});more.hidden=(page+1)*next.size>=next.totalElements;}catch(e){page--;form.append(notice(e.message,"error"));}finally{more.disabled=false;}},{className:"secondary",hidden:quizzes.totalElements<=100});
  const form=h("form",{id:"room-form",className:"card form-grid"},field("Tên phòng",input("name",{required:true,maxLength:200,value:room?.name||""})),field("Bộ câu hỏi",picker),more,
    field("Số chỗ chơi",input("maxPlayers",{type:"number",required:true,min:3,max:100,value:room?.maxPlayers||6})),
    field("Thời gian trả lời (giây)",input("seconds",{type:"number",required:true,min:.001,step:.001,value:(room?.questionDurationMs||15000)/1000})),
    field("Vai trò của Host",participation,"Tác giả chỉ được quan sát bộ câu hỏi của mình."),notice("Mỗi giai đoạn quyết định kéo dài 5 giây. Cấu hình được cố định khi Start."),
    h("div",{className:"actions"},h("button",{type:"submit",id:"save-room"},id?"Lưu cấu hình":"Tạo Draft"),link("Hủy",id?`room/${id}`:"rooms","button secondary")));
  form.onsubmit=e=>{e.preventDefault();let body;action(app,form,()=>{body??={requestId:uuid(),...(id?{revision:room.revision}:{}),config:roomValues(values(form))};return app.api.request(id?"PUT":"POST",id?`/api/rooms/${id}`:"/api/rooms",body);},saved=>{app.adoptRoom(saved);app.navigate(`room/${saved.id}`);});};
  return h("section",{},heading(id?"Chỉnh sửa phòng":"Tạo phòng","Lưu Draft trước, rồi mở để chia sẻ mã mời."),form);
}
export function joinPage(app) {
  const form=h("form",{id:"join-form",className:"card narrow"},field("Mã phòng",input("code",{required:true,minLength:12,maxLength:12,pattern:"[a-zA-Z2-9]{12}",autocomplete:"off"}),"12 ký tự; phòng phải đang chờ."),field("Tham gia với vai trò",select("participation",[["PLAYER","Người chơi"],["SPECTATOR","Người quan sát"]])),h("button",{type:"submit"},"Tham gia"));
  form.onsubmit=e=>{e.preventDefault();const data=values(form);let frame;action(app,form,async()=>{if(!frame){const roomCode=data.code.trim().toUpperCase();const preview=await app.api.request("GET",`/api/rooms/by-code/${encodeURIComponent(roomCode)}`);frame=command("JOIN_ROOM",preview.id,{roomCode,participation:data.participation});}await app.connect(true);return app.transport.send(frame);},ack=>{app.adoptRoom(ack.payload);app.navigate(`room/${ack.payload.id}`);});};
  return h("section",{},heading("Tham gia phòng","Nhập mã do Host chia sẻ. Danh sách thành viên cập nhật qua WebSocket."),form);
}
export async function waitingPage(app,id) {
  const version=app.renderVersion;const initial=await app.api.request("GET",`/api/rooms/${id}`);
  if(version!==app.renderVersion) return h("div");
  app.adoptRoom(initial);app.activeRoomId=id;let room=app.rooms.get(id),quiz=null,questionCount=10,quizLoading=false,quizError=null;const root=h("section",{id:"waiting-room"});
  const controller={id,update(){room=app.rooms.get(id);render();loadQuiz();},async subscribe(){if(app.roomController!==controller || app.transport.status!=="ready")return;try{const ack=await app.transport.request("SUBSCRIBE_ROOM",id,{});if(app.roomController===controller)app.adoptRoom(ack.payload);}catch(e){if(app.roomController===controller)root.append(notice(e.message,"error"));}}};
  app.roomController=controller;
  function loadQuiz(){if(quizLoading || quiz?.id===room.quizId)return;quizLoading=true;quizError=null;const qid=room.quizId;app.api.request("GET",`/api/quizzes/${qid}`).then(q=>{if(app.roomController===controller && room.quizId===qid){quiz=q;render();}}).catch(e=>{if(app.roomController===controller && room.quizId===qid){quizError=e.message;render();}}).finally(()=>{quizLoading=false;});}
  function restControl(type,label){return button(label,()=>{const body={requestId:uuid(),revision:room.revision};action(app,root,()=>app.api.request("POST",`/api/rooms/${id}/${type}`,body),saved=>app.adoptRoom(saved));},{id:`${type}-room`,className:type==="close"?"danger secondary":""});}
  function wsControl(type,payload,label,success,props={}){return button(label,()=>{const frame=command(type,id,payload);action(app,root,()=>app.transport.send(frame),success);},{disabled:app.transport.status!=="ready",...props});}
  function render(){if(app.roomController!==controller)return;const feedback=root.querySelector('.action-feedback');
    const host=room.hostUserId===app.user.id,waiting=room.status==="WAITING",ready=app.transport.status==="ready";
    const controls=h("div",{className:"actions"});if(host && ["DRAFT","WAITING"].includes(room.status)){controls.append(link("Sửa cấu hình",`room/${id}/edit`,"button secondary"));if(room.status==="DRAFT")controls.append(restControl("open","Mở phòng"));
      const confirm=h("div",{hidden:true,className:"card close-confirm"},notice("Đóng phòng và ngừng nhận thành viên?"));confirm.append(button("Giữ phòng",()=>confirm.hidden=true,{className:"secondary"}),restControl("close","Xác nhận đóng"));controls.append(button("Đóng phòng",()=>confirm.hidden=false,{className:"danger secondary",id:"show-close-room"}),confirm);}
    if(!host && waiting)controls.append(wsControl("LEAVE_ROOM",{},"Rời phòng",()=>{app.rooms.delete(id);app.navigate("rooms");},{id:"leave-room",className:"danger secondary"}));
    const members=h("section",{className:"card"},h("div",{className:"card-top"},h("h2",{},"Thành viên"),h("span",{id:"player-count",className:"badge"},`${room.members.filter(m=>m.participation==="PLAYER").length}/${room.maxPlayers} người chơi`)),h("div",{id:"roster"},room.members.map(m=>h("div",{className:"member","data-user-id":m.userId},h("div",{},h("strong",{},m.displayName||m.username||`User #${m.userId}`),h("small",{},`${m.host?"Host · ":""}${m.participation==="PLAYER"?"Người chơi":"Quan sát"}${m.userId===app.user.id?" · Bạn":""}`)),host && waiting && !m.host?wsControl("REMOVE_MEMBER",{userId:m.userId},"Mời ra",ack=>app.adoptRoom(ack.payload),{className:"text-button danger","data-remove":m.userId}):null))));
    const config=h("section",{className:"card"},h("h2",{},"Cấu hình phòng"),h("dl",{},h("div",{},h("dt",{},"Bộ câu hỏi"),h("dd",{},room.quizTitle)),h("div",{},h("dt",{},"Trả lời"),h("dd",{},`${room.questionDurationMs/1000} giây`)),h("div",{},h("dt",{},"Quyết định"),h("dd",{},"5 giây")),h("div",{},h("dt",{},"Revision"),h("dd",{id:"room-revision"},room.revision))));
    if(waiting){const self=room.members.find(m=>m.userId===app.user.id);const role=select("participation",[["PLAYER","Người chơi"],["SPECTATOR","Người quan sát"]],self?.participation);if(quiz?.ownerUserId===app.user.id)role.querySelector('[value="PLAYER"]').disabled=true;
      const change=button("Lưu vai trò",()=>{const frame=command("JOIN_ROOM",id,{roomCode:room.roomCode,participation:role.value});action(app,root,()=>app.transport.send(frame),ack=>app.adoptRoom(ack.payload));},{disabled:!ready,id:"change-participation",className:"secondary"});config.append(field("Vai trò trước Start",role),change);}
    const start=h("form",{id:"start-form",className:"card"},h("h2",{},"Bắt đầu trận"));
    if(host && waiting){const n=input("questionCount",{type:"number",required:true,min:10,max:Math.min(50,quiz?.questionCount||50),value:questionCount});const issue=notice("");const go=h("button",{type:"submit",id:"start-game"},"Bắt đầu trận");
      const check=()=>{questionCount=Number(n.value);const reason=room.quizDeleted?"Bộ câu hỏi đã bị xóa. Hãy chọn bộ khác.":quizError || (quiz?.id!==room.quizId?"Đang tải số câu của bộ đã chọn…":startIssue(room,questionCount,quiz.questionCount));issue.textContent=reason || "Đã đủ điều kiện phía Client. Server sẽ kiểm tra roster và quyền một lần nữa.";go.disabled=!ready || !!reason;};n.oninput=check;check();start.append(field("Số câu của trận",n),issue,go);if(quizError)start.append(button("Tải lại bộ đã chọn",loadQuiz,{className:"secondary"}));start.onsubmit=e=>{e.preventDefault();const frame=command("START_GAME",id,{revision:room.revision,questionCount});action(app,root,()=>app.transport.send(frame),ack=>app.enterGame(ack.payload.gameSessionId));};
    }else start.append(notice(room.status==="ACTIVE"?"Trận đang diễn ra. Khi Start được công bố, các thành viên đang kết nối sẽ được chuyển vào trận.":host?"Mở phòng và mời ít nhất 3 người chơi trước khi bắt đầu.":"Chờ Host bắt đầu trận."));
    root.replaceChildren(heading(room.name,`${app.statusLabel(room.status)} · Host #${room.hostUserId}`,h("span",{className:"badge"},app.statusLabel(room.status))),
      h("div",{className:"room-code card"},h("span",{},"Mã mời"),h("strong",{id:"room-code"},room.roomCode),button("Sao chép",async()=>{try{await navigator.clipboard.writeText(room.roomCode);}catch{root.append(notice(`Mã phòng: ${room.roomCode}`));}},{className:"secondary"})),controls,h("div",{className:"two-columns"},members,config),start);
    if(feedback)root.append(feedback);if(root.getAttribute('aria-busy')==='true')busy(root,true);
  }
  render();loadQuiz();if(app.transport.status==="ready")controller.subscribe();else app.connect();return root;
}
