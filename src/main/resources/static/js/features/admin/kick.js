import {h,button,field,input,notice,busy} from "/js/shared/dom.js";
import {confirmPanel,setConfirmation,emptyState} from "/js/shared/ui.js";
import {uuid} from "/js/core/protocol.js";

/** One form owns its pending request; retries never invent a new command ID. */
export async function kickPanel(app,gameId){
  const players=await app.api.request("GET",`/api/admin/games/${gameId}/players`),version=app.renderVersion,user=app.user.id;
  const root=h("section",{className:"card admin-kick-panel",id:"admin-kick-panel","data-game-id":gameId},h("h2",{},`Người tham gia · Trận #${gameId}`),
    notice("Kick chỉ áp dụng cho trận này. Điểm và câu trả lời đã ghi được giữ lại; những người còn lại tiếp tục chơi."));
  if(!players.length){root.append(emptyState("Trận không còn ACTIVE hoặc không có người tham gia."));return root;}
  for(const player of players){
    let request=null,working=false;
    const eligible=player.role!=="HOST" && player.participation==="PLAYER" && player.accountRole!=="ADMIN" && !player.restricted;
    const row=h("section",{className:"admin-kick-player","data-user-id":player.userId}),feedback=h("div",{className:"kick-feedback","aria-live":"polite"});
    const confirm=confirmPanel(`Đưa ${player.displayName} ra khỏi trận #${gameId}?`),reason=input("kickReason",{required:true,maxLength:240,placeholder:"Lý do ngắn gọn"});
    const submit=h("button",{type:"submit",className:"danger"},"Xác nhận Kick"),form=h("form",{className:"kick-form"},field("Lý do",reason),submit);
    const opener=button(player.restricted?"Đã Kick":eligible?"Kick":"Không thể Kick",()=>setConfirmation(confirm,true,opener),{className:"danger secondary","data-action":"kick",disabled:!eligible});
    confirm.append(button("Hủy",()=>setConfirmation(confirm,false),{className:"secondary"}),form);
    form.addEventListener("submit",async event=>{
      event.preventDefault();reason.setCustomValidity(reason.value.trim()?"":"Hãy nhập lý do.");if(working || !form.reportValidity())return;working=true;
      request??={requestId:uuid(),userId:player.userId,reason:reason.value.trim()};
      busy(form,true);reason.disabled=true;feedback.replaceChildren(notice("Đang chờ Server xác nhận…"));row.dataset.kickStatus="PENDING";
      const current=()=>app.renderVersion===version && app.user?.id===user;
      try{
        const ack=await app.api.request("POST",`/api/admin/games/${gameId}/kick`,request);if(!current())return;
        row.dataset.kickStatus=ack.status;opener.disabled=true;opener.textContent="Đã Kick";submit.disabled=true;
        feedback.replaceChildren(notice(ack.status==="ALREADY_APPLIED"?"Server xác nhận Player đã được đưa ra khỏi trận.":"Server đã đưa Player ra khỏi trận.","success"));setConfirmation(confirm,false);
      }catch(error){if(!current())return;
        const uncertain=error.retryable || error.code==="REQUEST_UNCERTAIN";row.dataset.kickStatus=uncertain?"PENDING":"REJECTED";
        feedback.replaceChildren(notice(uncertain?"Chưa xác nhận được kết quả. Bấm thử lại để kiểm tra cùng yêu cầu.":error.message,"error"));
        submit.textContent=uncertain?"Kiểm tra / Thử lại":"Xác nhận Kick";
        if(!uncertain){request=null;reason.disabled=false;}
      }finally{working=false;busy(form,false);if(opener.disabled && row.dataset.kickStatus!=="PENDING" && row.dataset.kickStatus!=="REJECTED")submit.disabled=true;}
    });
    reason.addEventListener("input",()=>reason.setCustomValidity(""));
    row.append(h("div",{className:"card-top"},h("div",{},h("strong",{},player.displayName),h("p",{className:"muted"},`${player.role} · ${player.participation} · #${player.userId}`)),opener),confirm,feedback);root.append(row);
  }
  return root;
}
