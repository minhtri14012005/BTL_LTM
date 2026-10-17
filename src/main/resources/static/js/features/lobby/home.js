import {h,heading,link,notice} from "/js/shared/dom.js";
import {emptyState} from "/js/shared/ui.js";
import {icon} from "/js/shared/icons.js";
import {roomCard} from "/js/features/lobby/rooms.js";

export async function homePage(app) {
  const [quizzes,rooms,status]=await Promise.all([app.api.request("GET","/api/quizzes?size=1"),app.api.request("GET","/api/rooms?size=5"),app.api.request("GET","/api/system/status")]);
  const recent=rooms.items.length?
    h("div",{className:"card-grid lobby-rooms"},rooms.items.map(room=>roomCard(app,room,0,false))):
    emptyState("Tạo phòng từ bộ câu hỏi hoặc nhập mã do Host chia sẻ để bắt đầu.","Phòng đầu tiên đang chờ bạn");
  return h("section",{className:"lobby-page"},
    heading("Sảnh chơi","Không gian cho những cuộc đấu trí cùng bạn bè.",link("Tạo phòng","rooms/new","button")),
    h("section",{className:"card lobby-hero"},
      h("div",{},h("p",{className:"eyebrow"},"CÙNG NHAU NHẬP CUỘC"),h("h2",{},`Xin chào, ${app.user.displayName}`),
        h("p",{className:"muted"},"Một bộ câu hỏi hay. Một mã phòng. Cả nhóm cùng chơi."),
        h("div",{className:"actions"},link("Nhập mã phòng","join","button"),link("Khám phá bộ câu hỏi","quizzes","button secondary"))),
      h("div",{className:"lobby-hero-mark","aria-hidden":"true"},icon("grid"),h("span",{},"MULTIGAME"))),
    h("div",{className:"stat-grid lobby-stats"},
      metric("Bộ câu hỏi có thể xem",quizzes.totalElements,"grid"),
      metric("Phòng của bạn",rooms.totalElements,"inbox"),
      h("div",{className:"card stat"},icon("info"),h("span",{},"Kết nối Server"),h("strong",{id:"server-status"},status.server==="UP"?"Sẵn sàng":"Chưa sẵn sàng"))),
    status.database.status!=="UP"?notice(status.database.message,"error"):null,
    h("section",{className:"lobby-recent"},heading("Phòng gần đây","Các phòng bạn là thành viên; mở phòng để xem trạng thái mới nhất.",link("Tất cả phòng","rooms","button secondary")),recent),
    h("div",{className:"two-columns lobby-shortcuts"},
      h("section",{className:"card"},h("p",{className:"eyebrow"},"BỘ CÂU HỎI"),h("h2",{},"Chuẩn bị cuộc chơi tiếp theo"),h("p",{className:"muted"},"Khám phá bộ công khai hoặc quản lý bộ riêng theo quyền hiện có."),link("Mở thư viện","quizzes","button secondary")),
      h("section",{className:"card"},h("p",{className:"eyebrow"},"KẾT QUẢ"),h("h2",{},"Nhìn lại từng trận đấu"),h("p",{className:"muted"},"Xem kết quả những trận bạn đã tham gia trong lịch sử thi đấu."),link("Xem lịch sử thi đấu","history","button secondary"))));
}
function metric(label,value,name){return h("div",{className:"card stat"},icon(name),h("span",{},label),h("strong",{},value));}
