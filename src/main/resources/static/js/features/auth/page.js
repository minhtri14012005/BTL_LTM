import {h,field,input,link,values,busy,notice,button} from "/js/shared/dom.js";
import {icon} from "/js/shared/icons.js";
import {accountValues} from "/js/features/auth/validation.js";

export function accountPage(app, register) {
  const label=register?"Tạo tài khoản":"Đăng nhập";
  const password=input("password",{type:"password",required:true,minLength:8,maxLength:72,autocomplete:register?"new-password":"current-password"});
  const toggle=button("Hiện mật khẩu",()=>{
    const visible=password.type==="password";
    password.type=visible?"text":"password";
    toggle.textContent=visible?"Ẩn mật khẩu":"Hiện mật khẩu";
    toggle.setAttribute("aria-pressed",String(visible));
  },{className:"secondary password-toggle","aria-controls":"password","aria-pressed":"false"});
  const submit=h("button",{type:"submit",className:"primary auth-submit"},label);
  const progress=h("p",{className:"auth-progress",role:"status","aria-live":"polite"});
  const login=link("Đăng nhập","login"),signup=link("Đăng ký","register");
  (register?signup:login).setAttribute("aria-current","page");
  const form=h("form",{id:register?"register-form":"login-form",className:"card auth-form","aria-labelledby":"auth-title"},
    h("nav",{className:"auth-tabs","aria-label":"Tài khoản"},login,signup),
    h("p",{className:"eyebrow"},"TÀI KHOẢN MULTIGAME"),
    h("h1",{id:"auth-title"},register?"Sẵn sàng nhập cuộc?":"Chào mừng trở lại"),
    h("p",{className:"muted auth-description"},register?"Tạo tài khoản để soạn câu hỏi và chơi cùng bạn bè.":"Đăng nhập để trở về phòng và tiếp tục cuộc chơi."),
    field("Tên đăng nhập",input("username",{required:true,minLength:3,maxLength:32,pattern:"[a-zA-Z0-9_]{3,32}",autocomplete:"username",autocapitalize:"none",spellcheck:false}),"3–32 chữ, số hoặc dấu gạch dưới."),
    register?field("Tên hiển thị",input("displayName",{required:true,maxLength:100,autocomplete:"nickname"})):null,
    field("Mật khẩu",password,"Tối thiểu 8 ký tự; tối đa 72 byte UTF-8."),toggle,
    register?field("Nhập lại mật khẩu",input("confirmPassword",{type:"password",required:true,autocomplete:"new-password"})):null,
    submit,progress,
    h("p",{className:"muted auth-switch"},register?"Đã có tài khoản? ":"Chưa có tài khoản? ",link(register?"Đăng nhập":"Đăng ký",register?"login":"register")));
  let submitting=false;
  form.addEventListener("submit",async event=>{
    event.preventDefault();
    if(submitting)return;
    submitting=true;
    const version=app.renderVersion,userId=app.user?.id;
    const current=()=>app.renderVersion===version && app.user?.id===userId;
    form.querySelector(".action-feedback")?.remove();
    busy(form,true);submit.textContent=register?"Đang tạo tài khoản…":"Đang đăng nhập…";progress.textContent="Đang kết nối với Server…";
    try {
      const data=values(form);
      if(register && data.password!==data.confirmPassword)throw new Error("Hai mật khẩu chưa khớp.");
      const user=await app.api.request("POST",register?"/api/auth/register":"/api/auth/login",accountValues(data,register));
      if(!current())return;
      form.reset();
      if(register){app.flash="Đã tạo tài khoản. Hãy đăng nhập để tiếp tục.";app.navigate("login");}
      else {
        app.api.invalidate();await app.api.csrf();
        if(!current())return;
        app.user=user;
        if(user.role==="ADMIN"){location.assign("/admin");return;}
        app.navigate("home");app.connect();
      }
    } catch(error) {
      if(!current())return;
      const feedback=h("div",{className:"action-feedback",tabIndex:-1},notice(error.message,"error"));
      form.append(feedback);feedback.focus();
    } finally {
      submitting=false;busy(form,false);submit.textContent=label;progress.textContent="";
    }
  });
  return h("div",{className:"auth-layout"},
    h("section",{className:"auth-story"},h("a",{href:"#/login",className:"auth-brand"},h("span",{className:"auth-monogram","aria-hidden":"true"},icon("gamepad")),"MultiGame"),
      h("p",{className:"eyebrow"},"PLAY · THINK · CONNECT"),
      h("h2",{},"Cuộc chơi hay hơn khi có bạn."),
      h("p",{},"Biến những câu hỏi thành một buổi chơi cùng nhau. Tạo phòng, chia sẻ mã và sẵn sàng thử sức."),
      h("div",{className:"auth-art","aria-hidden":"true"},h("span",{className:"auth-art-controller"},icon("gamepad")),h("span",{className:"auth-art-question"},icon("help")),h("span",{className:"auth-art-trophy"},icon("trophy")),h("span",{className:"auth-art-book"},icon("book"))),
      h("div",{className:"auth-preview","aria-label":"Cách bắt đầu"},
        h("div",{},icon("grid"),h("strong",{},"Chọn bộ câu hỏi"),h("span",{},"Bộ công khai hoặc bộ riêng của bạn")),
        h("div",{},icon("check"),h("strong",{},"Vào phòng cùng nhau"),h("span",{},"Chia sẻ mã để mời bạn bè")),
        h("div",{},icon("sort"),h("strong",{},"Thử sức qua từng màn"),h("span",{},"Theo dõi kết quả sau trận")))),form);
}
