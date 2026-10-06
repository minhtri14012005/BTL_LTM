import {h,field,input,link,notice,heading,values,action} from "./dom.js";
import {accountValues} from "./core.js";

export function accountPage(app, register) {
  const form=h("form",{id:register?"register-form":"login-form",className:"card auth-form"},
    h("h2",{},register?"Tạo tài khoản":"Chào mừng trở lại"),
    field("Tên đăng nhập",input("username",{required:true,minLength:3,maxLength:32,pattern:"[a-zA-Z0-9_]{3,32}",autocomplete:"username"}),"3–32 chữ, số hoặc dấu gạch dưới."),
    register?field("Tên hiển thị",input("displayName",{required:true,maxLength:100,autocomplete:"nickname"})):null,
    field("Mật khẩu",input("password",{type:"password",required:true,minLength:8,maxLength:72,autocomplete:register?"new-password":"current-password"}),"Tối thiểu 8 ký tự."),
    register?field("Nhập lại mật khẩu",input("confirmPassword",{type:"password",required:true,autocomplete:"new-password"})):null,
    h("button",{type:"submit",className:"primary"},register?"Tạo tài khoản":"Đăng nhập"),
    h("p",{className:"muted"},register?"Đã có tài khoản? ":"Chưa có tài khoản? ",link(register?"Đăng nhập":"Đăng ký",register?"login":"register")));
  form.addEventListener("submit", event => {
    event.preventDefault();
    action(app,form,async () => {
      const data=values(form);if(register && data.password!==data.confirmPassword) throw new Error("Hai mật khẩu chưa khớp.");
      return app.api.request("POST",register?"/api/auth/register":"/api/auth/login",accountValues(data,register));
    },async user => {
      form.reset();
      if(register) {app.flash="Đã tạo tài khoản. Hãy đăng nhập để tiếp tục.";app.navigate("login");}
      else {app.api.invalidate();await app.api.csrf();app.user=user;app.navigate("home");app.connect();}
    });
  });
  return h("div",{className:"auth-layout"},h("section",{className:"auth-story"},h("p",{className:"eyebrow"},"COMPETITIVE QUIZ"),h("h1",{},"Cùng chơi. Cùng một trận đấu."),h("p",{},"Soạn bộ câu hỏi, mở phòng và mời bạn bè cùng tham gia."),h("div",{className:"story-number"},"01",h("span",{},"Một phòng. Nhiều người chơi."))),form);
}
export async function homePage(app) {
  const [quizzes,rooms,status]=await Promise.all([app.api.request("GET","/api/quizzes?size=1"),app.api.request("GET","/api/rooms?size=5"),app.api.request("GET","/api/system/status")]);
  return h("section",{},heading(`Xin chào, ${app.user.displayName}`,"Chuẩn bị bộ câu hỏi hoặc tham gia một phòng đang chờ.",link("Tạo phòng","rooms/new","button")),
    h("div",{className:"stat-grid"},h("div",{className:"card stat"},h("span",{},"Bộ câu hỏi có thể xem"),h("strong",{},quizzes.totalElements)),h("div",{className:"card stat"},h("span",{},"Phòng của bạn"),h("strong",{},rooms.totalElements)),h("div",{className:"card stat"},h("span",{},"Kết nối Server"),h("strong",{id:"server-status"},status.server==="UP"?"Sẵn sàng":"Chưa sẵn sàng"))),
    h("div",{className:"two-columns"},h("section",{className:"card"},h("h2",{},"Bắt đầu từ bộ câu hỏi"),h("p",{className:"muted"},"Quản lý bộ riêng của bạn hoặc chọn một bộ công khai để mở phòng."),link("Khám phá bộ câu hỏi","quizzes","button secondary")),
      h("section",{className:"card"},h("h2",{},"Bạn đã có mã phòng?"),h("p",{className:"muted"},"Nhập mã được người tổ chức chia sẻ để tham gia."),link("Tham gia phòng","join","button secondary"))),
    h("section",{className:"card recent"},h("h2",{},"Phòng gần đây"),rooms.items.length?rooms.items.map(r => link(`${r.name} · ${app.statusLabel(r.status)}`,`room/${r.id}`,"list-row")):h("p",{className:"muted"},"Chưa có phòng. Tạo phòng đầu tiên hoặc nhập mã để tham gia.")),
    h("div",{className:"actions"},link("Xem lịch sử thi đấu","history","button secondary")),
    status.database.status!=="UP"?notice(status.database.message,"error"):null);
}
