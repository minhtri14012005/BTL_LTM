import {icon} from "/js/shared/icons.js";
import {emptyState,confirmPanel,setConfirmation} from "/js/shared/ui.js";
import {h,heading,link,notice,button,action} from "/js/shared/dom.js";
import {uuid} from "/js/core/protocol.js";
export async function roomList(app,page=0) {
  const result=await app.api.request("GET",`/api/rooms?page=${page}&size=12`);
  return h("section",{className:"lobby-page"},heading("Phòng của tôi","Tạo cuộc chơi mới hoặc trở lại các phòng bạn là thành viên.",link("Tạo phòng","rooms/new","button")),
    h("div",{className:"actions"},link("Nhập mã phòng","join","button secondary")),
    h("p",{className:"muted",role:"status"},`${result.totalElements} phòng trong danh sách của bạn`),
    result.items.length?h("div",{className:"card-grid lobby-rooms"},result.items.map(r=>roomCard(app,r,result.items.length===1&&page>0?page-1:page))):emptyState("Tạo phòng hoặc nhập mã được chia sẻ để tham gia.","Chưa có phòng nào"),
    h("div",{className:"pagination"},page>0?link("← Trang trước",`rooms?page=${page-1}`,"button secondary"):null,h("span",{},`Trang ${page+1}`),(page+1)*result.size<result.totalElements?link("Trang sau →",`rooms?page=${page+1}`,"button secondary"):null));
}
export function roomCard(app,room,page,allowDelete=true) {
  const host=room.hostUserId===app.user.id;
  const card=h("article",{className:"card room-list-card","data-room-id":room.id});
  const confirm=confirmPanel(host?
    "Xóa phòng này? Phòng sẽ đóng và được gỡ khỏi danh sách của tất cả thành viên. Lịch sử trận vẫn được giữ.":
    "Rời và xóa phòng này khỏi danh sách của bạn? Phòng của những người khác và lịch sử trận vẫn được giữ.","room-delete-confirm");
  let request;
  confirm.append(h("div",{className:"actions"},button("Giữ phòng",()=>setConfirmation(confirm,false),{className:"secondary"}),
    button("Xác nhận xóa",()=>action(app,card,()=>{
      if(!request)request={requestId:uuid(),revision:room.revision};
      return app.api.request("DELETE",`/api/rooms/${room.id}`,request);
    },()=>{
      app.rooms.delete(room.id);app.flash=host?"Đã xóa phòng. Lịch sử trận được giữ lại.":"Đã rời và xóa phòng khỏi danh sách của bạn.";
      app.navigate(`rooms?page=${page}`);
    }),{className:"danger",id:`confirm-delete-room-${room.id}`})));
  const trash=button("",event=>setConfirmation(confirm,confirm.hidden,event.currentTarget),{className:"room-trash",id:`delete-room-${room.id}`,
    "aria-label":`Xóa phòng ${room.name}`,title:room.status==="ACTIVE"?"Không thể xóa khi trận đang chạy":"Xóa phòng",disabled:room.status==="ACTIVE"});
  trash.append(icon("trash"));
  const badge=h("span",{className:"badge room-status","data-status":room.status},app.statusLabel(room.status));
  card.append(h("div",{className:"card-top"},badge,allowDelete?trash:null),
    h("p",{className:"eyebrow"},host?"PHÒNG BẠN TỔ CHỨC":"PHÒNG BẠN THAM GIA"),
    h("h2",{},link(room.name,`room/${room.id}`)),
    h("p",{className:"muted room-occupancy"},`${room.members.filter(m=>m.participation==="PLAYER").length}/${room.maxPlayers} người chơi · ${host?"Bạn là Host":"Thành viên"}`),
    link("Mở phòng",`room/${room.id}`,"button secondary room-open"));
  if(allowDelete)card.append(confirm);
  return card;
}
