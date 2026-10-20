import {h,link,notice} from "/js/shared/dom.js";
import {emptyState} from "/js/shared/ui.js";
import {icon} from "/js/shared/icons.js";
import {roomCard} from "/js/features/lobby/rooms.js";

export async function homePage(app) {
  const [quizzes,rooms,status]=await Promise.all([app.api.request("GET","/api/quizzes?size=1"),app.api.request("GET","/api/rooms?size=5"),app.api.request("GET","/api/system/status")]);
  const recent=rooms.items.length?
    h("div",{className:"card-grid lobby-rooms"},rooms.items.map(room=>roomCard(app,room,0,false))):
    emptyState("Tạo phòng từ bộ câu hỏi hoặc nhập mã do Host chia sẻ để bắt đầu.","Phòng đầu tiên đang chờ bạn");
  return h("section",{className:"lobby-page"},
    h("section",{className:"card lobby-hero"},
      h("div",{className:"lobby-hero-content"},h("p",{className:"eyebrow"},"SẢNH CHƠI MULTIGAME"),h("p",{className:"lobby-greeting"},`Xin chào, ${app.user.displayName}`),h("h1",{},"Cuộc chơi hay hơn khi có bạn."),
        h("p",{className:"muted"},"Một bộ câu hỏi hay. Một mã phòng. Cả nhóm cùng chơi."),
        h("div",{className:"actions"},link([icon("inbox"),"Tạo phòng"],"rooms/new","button"),link("Nhập mã phòng","join","button secondary"))),
      h("div",{className:"lobby-hero-mark","aria-hidden":"true"},h("span",{className:"lobby-art-controller"},icon("gamepad")),h("span",{className:"lobby-art-trophy"},icon("trophy")),h("span",{className:"lobby-art-question"},icon("help")),h("span",{className:"lobby-art-book"},icon("book")))),
    h("div",{className:"stat-grid lobby-stats"},
      metric("Bộ câu hỏi có thể xem",quizzes.totalElements,"grid"),
      metric("Phòng của bạn",rooms.totalElements,"inbox"),
      h("div",{className:"card stat"},icon("info"),h("span",{},"Kết nối Server"),h("strong",{id:"server-status"},status.server==="UP"?"Sẵn sàng":"Chưa sẵn sàng"))),
    status.database.status!=="UP"?notice(status.database.message,"error"):null,
    h("section",{className:"lobby-recent","aria-labelledby":"recent-rooms-title"},h("div",{className:"page-heading"},h("div",{},h("h2",{id:"recent-rooms-title"},"Phòng gần đây"),h("p",{className:"muted"},"Trở lại những phòng bạn đang là thành viên.")),link("Tất cả phòng","rooms","text-link")),recent),
    h("div",{className:"two-columns lobby-shortcuts"},
      h("section",{className:"card lobby-shortcut"},icon("book"),h("div",{},h("h2",{},"Kho câu hỏi"),h("p",{className:"muted"},"Khám phá bộ công khai hoặc quản lý bộ riêng.")),link("Mở thư viện","quizzes","text-link")),
      h("section",{className:"card lobby-shortcut"},icon("sort"),h("div",{},h("h2",{},"Lịch sử thi đấu"),h("p",{className:"muted"},"Nhìn lại kết quả những trận bạn đã tham gia.")),link("Xem lịch sử","history","text-link"))));
}
function metric(label,value,name){return h("div",{className:"card stat"},icon(name),h("span",{},label),h("strong",{},value));}
