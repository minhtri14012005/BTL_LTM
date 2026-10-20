import {icon} from "/js/shared/icons.js";
import {emptyState,confirmPanel,setConfirmation} from "/js/shared/ui.js";
import {h,heading,link,notice,button,action} from "/js/shared/dom.js";
import {uuid} from "/js/core/protocol.js";
export async function roomList(app,page=0) {
  const result=await app.api.request("GET",`/api/rooms?page=${page}&size=12`);
  return h("section",{className:"lobby-page rooms-page"},h("header",{className:"card rooms-heading"},h("span",{className:"rooms-heading-icon","aria-hidden":"true"},icon("inbox")),heading("Phòng của tôi","Một mã mời, một cuộc chơi cùng bạn bè.",h("div",{className:"actions"},link("Tạo phòng","rooms/new","button"),link("Nhập mã phòng","join","button secondary")))),
    h("p",{className:"room-list-summary muted",role:"status"},`${result.totalElements} phòng trong danh sách của bạn`),
    result.items.length?h("div",{className:"card-grid lobby-rooms"},result.items.map(r=>roomCard(app,r,result.items.length===1&&page>0?page-1:page))):emptyState("Tạo phòng hoặc nhập mã được chia sẻ để tham gia.","Chưa có phòng nào"),
    h("nav",{className:"pagination","aria-label":"Phân trang phòng"},page>0?link("← Trang trước",`rooms?page=${page-1}`,"button secondary"):null,h("span",{},`Trang ${page+1}`),(page+1)*result.size<result.totalElements?link("Trang sau →",`rooms?page=${page+1}`,"button secondary"):null));
}
export function roomCard(app,room,page,allowDelete=true) {
  const host=room.hostUserId===app.user.id;
  const card=h("article",{className:"card room-list-card","data-room-id":room.id,"data-status":room.status});
  const confirm=confirmPanel(host?
    "Xóa phòng này? Phòng sẽ đóng và được gỡ khỏi danh sách của tất cả thành viên. Lịch sử trận vẫn được giữ.":
    "Rời và xóa phòng này khỏi danh sách của bạn? Phòng của những người khác và lịch sử trận vẫn được giữ.","room-delete-confirm");
  confirm.prepend(h("h3",{},room.name));
  let request;
  confirm.append(h("div",{className:"actions"},button("Giữ phòng",()=>setConfirmation(confirm,false),{className:"secondary"}),
    button(host?"Xác nhận xóa":"Xác nhận rời",()=>action(app,card,()=>{
      if(!request)request={requestId:uuid(),revision:room.revision};
      return app.api.request("DELETE",`/api/rooms/${room.id}`,request);
    },()=>{
      app.rooms.delete(room.id);app.flash=host?"Đã xóa phòng. Lịch sử trận được giữ lại.":"Đã rời và xóa phòng khỏi danh sách của bạn.";
      app.navigate(`rooms?page=${page}`);
    }),{className:"danger",id:`confirm-delete-room-${room.id}`})));
  const trash=button([icon("trash"),host?"Xóa phòng":"Rời phòng"],event=>setConfirmation(confirm,confirm.hidden,event.currentTarget),{className:"secondary danger room-trash",id:`delete-room-${room.id}`,
    "aria-label":`${host?"Xóa":"Rời"} phòng ${room.name}`,title:room.status==="ACTIVE"?"Thao tác bị khóa khi trận đang chạy":host?"Xóa phòng":"Rời và bỏ khỏi danh sách",disabled:room.status==="ACTIVE"});
  const badge=h("span",{className:"badge room-status","data-status":room.status},app.statusLabel(room.status));
  card.append(...[h("div",{className:"card-top"},badge,h("span",{className:`room-owner${host?" is-host":""}`},host?icon("trophy"):icon("gamepad"),host?"Host":"Thành viên")),
    h("h2",{},link(room.name,`room/${room.id}`)),
    h("p",{className:"room-occupancy"},icon("gamepad"),`${room.members.filter(m=>m.participation==="PLAYER").length}/${room.maxPlayers} người chơi`),
    h("p",{className:"muted room-observers"},`${room.members.filter(m=>m.participation==="SPECTATOR").length} người quan sát`),
    h("div",{className:"room-card-footer"},link("Mở phòng",`room/${room.id}`,"button secondary room-open"),allowDelete?trash:null),
    allowDelete && room.status==="ACTIVE"?h("p",{className:"muted room-lock-note"},"Trận đang diễn ra; thao tác xóa/rời tạm khóa."):null].filter(node=>node!=null));
  if(allowDelete)card.append(confirm);
  return card;
}
