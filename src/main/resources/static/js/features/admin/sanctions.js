import {h,button,field,input,notice,busy} from "/js/shared/dom.js";
import {confirmPanel,setConfirmation} from "/js/shared/ui.js";
import {uuid} from "/js/core/protocol.js";

export function sanctionLabel(user){
  if(user.deletedAtMs!=null)return "Đã xóa";
  if(!user.banned)return user.banKind==="TEMP"?"Ban tạm đã hết hạn":"Đang hoạt động";
  return user.banKind==="INDEFINITE"?"Ban vô thời hạn":`Ban tạm đến ${new Date(user.bannedUntilMs).toLocaleString("vi-VN")}`;
}

/** Each account owns its immutable pending request and Server version, no timers/subscriptions. */
export function sanctionControl(app,user){
  let current=user,request=null,working=false;
  const version=app.renderVersion,admin=app.user.id,valid=()=>version===app.renderVersion && app.user?.id===admin;
  const root=h("section",{className:"admin-sanction","data-user-id":user.id}),status=h("p",{className:"sanction-status"},sanctionLabel(user)),feedback=h("div",{"aria-live":"polite"});
  root.append(status);
  if(user.role==="ADMIN" || user.deletedAtMs!=null){root.append(h("small",{className:"muted"},"Không áp dụng chế tài tại đây."));return root;}
  const action=h("select",{name:"sanctionAction","aria-label":"Chế tài"},h("option",{value:"temporary-ban"},"Ban tạm thời"),h("option",{value:"ban"},"Ban vô thời hạn"),h("option",{value:"unban"},"Gỡ ban"));
  action.value=user.banned?"unban":"temporary-ban";
  const minutes=input("banMinutes",{id:`banMinutes-${user.id}`,type:"number",min:1,max:525600,step:1,value:60,required:true});
  const reason=input("banReason",{id:`banReason-${user.id}`,required:true,maxLength:240,placeholder:"Lý do quản trị"});
  const submit=h("button",{type:"submit",className:"danger"},"Xác nhận"),confirm=confirmPanel(`Áp dụng chế tài cho @${user.username}?`);
  const opener=button("Quản lý ban",()=>setConfirmation(confirm,true,opener),{className:"secondary","data-action":"sanction"});
  const form=h("form",{className:"sanction-form"},field("Thao tác",action),field("Thời lượng (phút)",minutes,"1–525600 phút; TEMP thay hạn bằng giờ Server + thời lượng mới."),field("Lý do",reason),submit);
  const updateDuration=()=>{minutes.disabled=action.value!=="temporary-ban";minutes.required=!minutes.disabled;};action.addEventListener("change",updateDuration);updateDuration();
  confirm.append(notice("Ban thu hồi mọi phiên cũ. Gỡ ban cần đăng nhập mới và không mở lại Game đã bị loại."),button("Hủy",()=>setConfirmation(confirm,false),{className:"secondary"}),form);
  form.addEventListener("submit",async event=>{
    event.preventDefault();reason.setCustomValidity(reason.value.trim()?"":"Hãy nhập lý do.");if(working || !form.reportValidity())return;
    request??={path:action.value,body:{requestId:uuid(),expectedVersion:current.banVersion,minutes:action.value==="temporary-ban"?Number(minutes.value):null,reason:reason.value.trim()}};
    working=true;busy(form,true);action.disabled=minutes.disabled=reason.disabled=true;root.dataset.sanctionStatus="PENDING";feedback.replaceChildren(notice("Đang chờ Server xác nhận…"));
    try{
      const ack=await app.api.request("POST",`/api/admin/users/${user.id}/${request.path}`,request.body);if(!valid())return;
      current={...current,banKind:ack.current.kind,bannedUntilMs:ack.current.untilMs,banVersion:ack.current.version,banned:ack.current.kind==="INDEFINITE" || ack.current.kind==="TEMP" && ack.serverTimeMs<ack.current.untilMs};
      root.dataset.sanctionStatus="CONFIRMED";status.textContent=sanctionLabel(current);
      feedback.replaceChildren(notice(`${ack.replayed?"Đã đọc lại biên nhận. ":""}${ack.result==="NO_CHANGE"?"Không đổi chế tài.":"Chế tài đã commit."} ${ack.pendingEffects?`${ack.pendingEffects} tác dụng phụ đang được Server xử lý.`:""} Giờ Server: ${new Date(ack.serverTimeMs).toLocaleString("vi-VN")}.`,"success"));
      request=null;setConfirmation(confirm,false);submit.textContent="Xác nhận";
    }catch(error){if(!valid())return;
      const uncertain=error.retryable || error.code==="REQUEST_UNCERTAIN";
      root.dataset.sanctionStatus=uncertain?"PENDING":"REJECTED";submit.textContent=uncertain?"Kiểm tra / Thử lại":"Xác nhận";
      feedback.replaceChildren(notice(uncertain?"Chưa rõ kết quả. Thử lại cùng yêu cầu; không đổi thao tác/thời lượng.":error.message,"error"));
      if(!uncertain)request=null;
      if(error.code==="STALE_VERSION")feedback.append(button("Tải trạng thái mới",()=>app.render(),{className:"secondary"}));
    }finally{working=false;busy(form,false);action.disabled=reason.disabled=!!request;if(request)minutes.disabled=true;else updateDuration();}
  });reason.addEventListener("input",()=>reason.setCustomValidity(""));root.append(opener,confirm,feedback);return root;
}

export function orphanControl(app,room,onClosed=()=>{}){
  const version=app.renderVersion,user=app.user.id;let request=null,working=false,closed=false;
  const root=h("section",{className:"admin-orphan-action","data-room-id":room.id}),confirm=confirmPanel(`Đóng Room #${room.id} đang WAITING?`),feedback=h("div",{"aria-live":"polite"});
  const opener=button("Đóng phòng bỏ lại",()=>setConfirmation(confirm,true,opener),{className:"danger secondary","data-action":"close-orphan"}),reason=input("orphanReason",{id:`orphanReason-${room.id}`,required:true,maxLength:240});
  const submit=h("button",{type:"submit",className:"danger"},"Xác nhận đóng"),form=h("form",{},field("Lý do",reason),submit);
  confirm.append(notice("Server chỉ đóng khi Host vẫn bị ban/không còn quyền và Room WAITING. Không kết thúc Game ACTIVE."),button("Hủy",()=>setConfirmation(confirm,false),{className:"secondary"}),form);
  form.addEventListener("submit",async event=>{
    event.preventDefault();reason.setCustomValidity(reason.value.trim()?"":"Hãy nhập lý do.");if(working || closed || !form.reportValidity())return;
    request??={requestId:uuid(),revision:room.revision,reason:reason.value.trim()};working=true;busy(form,true);reason.disabled=true;
    try{await app.api.request("POST",`/api/admin/rooms/${room.id}/close-orphan`,request);if(app.renderVersion!==version || app.user?.id!==user)return;
      closed=true;opener.disabled=true;opener.textContent="Đã đóng";onClosed();feedback.replaceChildren(notice("Server đã đóng Room.","success"));setConfirmation(confirm,false);
    }catch(error){if(app.renderVersion!==version || app.user?.id!==user)return;feedback.replaceChildren(notice(error.message,"error"));if(!error.retryable && error.code!=="REQUEST_UNCERTAIN"){request=null;reason.disabled=false;}}
    finally{working=false;busy(form,false);submit.disabled=closed;reason.disabled=closed || !!request;}
  });reason.addEventListener("input",()=>reason.setCustomValidity(""));root.append(opener,confirm,feedback);return root;
}
