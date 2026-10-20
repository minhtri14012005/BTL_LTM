import {h,link,notice,button} from "/js/shared/dom.js";

/** Read-only Room lookup. Returning never creates a Room/Game or sends START. */
export function roomReturn(app,roomId,isCurrent=()=>true) {
  const node=h("section",{className:"result-room-return","aria-label":"Trở lại phòng"});
  const userId=app.user?.id;
  let disposed=false,loading=false,request=null;
  const current=()=>!disposed && userId===app.user?.id && isCurrent();
  async function refresh(){
    if(loading || !current())return;
    loading=true;request=new AbortController();node.dataset.roomStatus="LOADING";node.setAttribute("aria-busy","true");node.replaceChildren(notice("Đang kiểm tra trạng thái phòng…"));
    try {
      const room=await app.api.request("GET",`/api/rooms/${roomId}`,null,{signal:request.signal});
      if(!current())return;
      if(room.id!==roomId || !["DRAFT","WAITING","ACTIVE","CLOSED"].includes(room.status))throw new Error("Không xác định được trạng thái phòng.");
      const waiting=room.status==="WAITING",host=room.hostUserId===userId;
      const label=waiting?(host?"Chơi tiếp cùng phòng":"Quay lại phòng"):room.status==="DRAFT"?"Quay lại phòng nháp":room.status==="ACTIVE"?"Xem phòng đang diễn ra":"Xem phòng đã đóng";
      node.replaceChildren(h("h3",{},waiting?"Chuẩn bị trận tiếp theo":"Phòng hiện tại"),
        h("details",{className:"result-room-guide"},h("summary",{},"Thông tin phòng · "+room.status),h("p",{className:"muted",role:"status"},waiting?(host?"Phòng đang chờ và giữ thành viên. Kiểm tra người chơi, cấu hình rồi bắt đầu trận tiếp theo trong phòng.":"Phòng đang chờ. Quay lại để chuẩn bị, đổi vai trò hoặc rời phòng; Host quyết định bắt đầu trận tiếp theo."):room.status==="ACTIVE"?"Phòng hiện có trận khác đang diễn ra. Kết quả đang xem thuộc trận trước.":room.status==="CLOSED"?"Phòng đã đóng, không thể bắt đầu trận mới tại đây. Lịch sử và thành tích đã ghi vẫn được giữ.":"Phòng đang là bản nháp. Host cần mở phòng trước khi bắt đầu."),
        waiting?h("p",{className:"muted"},"Mỗi trận mới có điểm, câu trả lời và tài nguyên riêng."):null),
        h("div",{className:"actions"},link(label,`room/${roomId}`,waiting?"button":"button secondary"),
          waiting && host?link("Cấu hình trận tiếp",`room/${roomId}/edit`,"button secondary"):null,
          button("Cập nhật trạng thái",refresh,{className:"secondary"}),link("Về sảnh","home","button secondary")));
      node.dataset.roomStatus=room.status;
    } catch(error) {
      if(!current())return;
      node.dataset.roomStatus="UNAVAILABLE";
      node.replaceChildren(notice(["FORBIDDEN","ROOM_NOT_FOUND"].includes(error.code)?"Phòng không còn truy cập được. Lịch sử trận vẫn được giữ.":error.message,"error"),h("div",{className:"actions"},button("Kiểm tra lại phòng",refresh,{className:"secondary"}),link("Về sảnh","home","button secondary")));
    } finally {loading=false;node.removeAttribute("aria-busy");}
  }
  refresh();return {node,refresh,dispose(){disposed=true;request?.abort();}};
}
