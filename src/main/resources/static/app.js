import {loadingState,errorState} from "/js/shared/ui.js";
import {Api} from "/js/core/api.js";
import {Transport} from "/js/core/transport.js";
import {messages} from "/js/core/protocol.js";
import {HashRouter} from "/js/core/router.js";
import {latestRoom} from "/js/features/room/state.js";
import {h,button,link,notice,disposeMedia} from "/js/shared/dom.js";
import {icon} from "/js/shared/icons.js";
import {accountPage} from "/js/features/auth/page.js";
import {homePage} from "/js/features/lobby/home.js";
import {questionBankList,questionBankDetail,questionBankEditor} from "/js/features/questionbank/page.js";
import {roomList} from "/js/features/lobby/rooms.js";
import {roomEditor} from "/js/features/room/editor.js";
import {joinPage,waitingPage} from "/js/features/room/page.js";
import {gamePage} from "/js/features/game/page.js";
import {historyList,historyDetail} from "/js/features/results/history.js";
import {adminPage} from "/js/features/admin/page.js";
import {profilePage} from "/js/features/profile/page.js";
import {leaderboardPage} from "/js/features/leaderboard/page.js";

class App {
  constructor(){this.user=null;this.rooms=new Map();this.games=new Map();this.kickedGames=new Set();this.renderVersion=0;this.api=new Api(p=>this.expire(p));this.transport=new Transport(m=>this.event(m),(s,reason)=>this.connection(s,reason));this.router=new HashRouter(()=>this.render());this.router.start();}
  statusLabel(s){return {DRAFT:"Bản nháp",WAITING:"Đang chờ",ACTIVE:"Đang diễn ra",CLOSED:"Đã đóng"}[s]||s;}
  navigate(path){this.router.navigate(path);}
  disposePage(){this.gameController?.dispose();this.roomController?.dispose();this.resultRoomController?.dispose();this.awardController?.dispose();this.profileController?.dispose();this.leaderboardController?.dispose();this.gameController=null;this.roomController=null;this.resultRoomController=null;this.awardController=null;this.profileController=null;this.leaderboardController=null;this.activeRoomId=null;disposeMedia(document.querySelector("#view"));}
  clear(){this.api.invalidate();this.transport.close();this.disposePage();this.user=null;this.rooms.clear();this.games.clear();this.kickedGames.clear();this.gamePresentation?.clear();}
  gameRevoked(id){this.kickedGames.add(id);this.games.delete(id);this.gamePresentation?.delete(id);if(this.gameController?.id===id || this.router.read().parts.join('/')===`game/${id}`){this.disposePage();this.flash="Bạn đã bị quản trị viên đưa ra khỏi trận này.";this.navigate("home");}}
  expire(reason=null){if(!this.user)return;const text=reason?.message || messages[reason?.code] || "Phiên đăng nhập đã hết hạn. Hãy đăng nhập lại.";this.clear();if(location.pathname==="/admin" || location.pathname==="/admin/"){location.replace("/#/login");return;}this.flash=text;this.flashTone="error";this.navigate("login");}
  async logout(){try{await this.api.request("POST","/api/auth/logout");}catch(e){if(this.user){document.querySelector("#connection-banner").replaceChildren(notice(e.message,"error"));return;}}this.clear();if(location.pathname==="/admin" || location.pathname==="/admin/"){location.replace("/#/login");return;}this.flash="Đã đăng xuất.";this.navigate("login");}
  async connect(explicit=false){if(!this.user)return;if(this.transport.status==="replaced" && !explicit)return;try{await this.transport.open();}catch(e){if(explicit)throw e;}}
  connection(state,reason){if(state==="expired"){const [code,until]=String(reason||"").split(":");this.expire({code,message:messages[code]?(messages[code]+(code==="ACCOUNT_TEMP_BANNED" && /^\d+$/.test(until||"")?` Hết hạn theo Server: ${new Date(Number(until)).toLocaleString("vi-VN")}.`:"")):null});return;}const banner=document.querySelector("#connection-banner");if(!this.user){banner.replaceChildren();return;}
    const labels={ready:"Trực tuyến",connecting:"Đang kết nối",offline:"Mất kết nối",replaced:"Đã thay kết nối"};const badge=document.querySelector("#connection-status");if(badge){badge.textContent=labels[state];badge.dataset.state=state;}
    banner.replaceChildren();if(["offline","replaced"].includes(state))banner.append(notice(state==="replaced"?"Một cửa sổ khác đang dùng kết nối của bạn. Cửa sổ này đã dừng kết nối tự động.":"Kết nối phòng đã ngắt. Dữ liệu hiển thị có thể đã cũ."),button("Kết nối tại đây",()=>this.connect(true).catch(e=>banner.append(notice(e.message,"error"))),{className:"secondary",id:"reconnect-room"}));
    if(state==="ready"){this.roomController?.subscribe();this.gameController?.reconnect();this.resultRoomController?.refresh();this.profileController?.refresh();this.leaderboardController?.refresh();}else{this.roomController?.update();this.gameController?.update();}
  }
  adoptRoom(room){if(!room?.id)return;this.rooms.set(room.id,latestRoom(this.rooms.get(room.id),room));if(this.roomController?.id===room.id)this.roomController.update();}
  event(m){if(m.type==="GAME_ACCESS_REVOKED"){this.gameRevoked(m.target.id);return;}if(m.type==="ROOM_UPDATED")this.adoptRoom(m.payload);else if(m.type==="ROOM_ACCESS_REVOKED"){this.rooms.delete(m.target.id);if(this.activeRoomId===m.target.id){this.flash="Bạn không còn là thành viên phòng.";this.navigate("rooms");}}
    else if(m.type==="GAME_STARTED" && m.payload.roomId===this.activeRoomId)this.enterGame(m.target.id);
    this.gameController?.receive(m);}
  enterGame(id){if(location.hash!==`#/game/${id}`)this.navigate(`game/${id}`);}
  shell(){const side=document.querySelector("#sidebar");side.hidden=!this.user;document.body.classList.toggle("signed-in",!!this.user);side.replaceChildren();if(!this.user){document.querySelector("#connection-banner").replaceChildren();return;}
    const {parts,url}=this.router.read();
    const section=({quiz:"quizzes",room:"rooms"})[parts[0]]||parts[0]||"home";
    const adminTab=["games","users","orphans"].includes(url.searchParams.get("tab"))?url.searchParams.get("tab"):"games";
    const navLink=(label,path,name,active)=>{const item=link([icon(name),h("span",{},label)],path);if(active)item.setAttribute("aria-current","page");return item;};
    const brand=path=>link([h("span",{className:"brand-mark","aria-hidden":"true"},icon("gamepad")),h("span",{},"MultiGame")],path,"brand");
    const revealCurrent=()=>{const nav=side.querySelector("nav"),active=nav?.querySelector('[aria-current="page"]');if(active && nav.scrollWidth>nav.clientWidth)nav.scrollLeft=Math.max(0,active.offsetLeft-nav.offsetLeft-12);};
    if(this.user.role==="ADMIN"){
      side.append(brand("admin"),h("nav",{"aria-label":"Điều hướng quản trị"},navLink("Trận đang chơi","admin?tab=games","grid",adminTab==="games"),navLink("Tài khoản","admin?tab=users","inbox",adminTab==="users"),navLink("Phòng thiếu Host","admin?tab=orphans","warning",adminTab==="orphans")),h("div",{className:"account-info"},h("strong",{title:this.user.displayName},this.user.displayName),h("small",{},`@${this.user.username}`),h("span",{className:"badge"},"ADMIN"),button("Đăng xuất",()=>this.logout(),{className:"secondary",id:"logout"})));
      revealCurrent();document.querySelector("#connection-banner").replaceChildren();return;
    }
    side.append(brand("home"),h("p",{className:"nav-label"},"KHÔNG GIAN CỦA BẠN"),h("nav",{"aria-label":"Điều hướng chính"},navLink("Tổng quan","home","home",section==="home"),navLink("Bộ câu hỏi","quizzes","grid",section==="quizzes"),navLink("Phòng của tôi","rooms","inbox",section==="rooms"),navLink("Tham gia phòng","join","check",section==="join"),navLink("Lịch sử","history","sort",section==="history"),navLink("Hồ sơ","profile","medal",section==="profile"),navLink("Sảnh danh vọng","leaderboard","trophy",section==="leaderboard")),h("div",{className:"account-info"},h("strong",{title:this.user.displayName},this.user.displayName),h("small",{},`@${this.user.username}`),h("span",{id:"connection-status",className:"connection"}),button("Đăng xuất",()=>this.logout(),{className:"secondary",id:"logout"})));revealCurrent();this.connection(this.transport.status);}
  async render(){const version=++this.renderVersion;this.disposePage();const view=document.querySelector("#view");this.shell();view.replaceChildren(loadingState());
    const {url,parts}=this.router.read();let page;
    try{if(!this.user)page=accountPage(this,parts[0]==="register");
      else if(this.user.role==="ADMIN")page=await adminPage(this,url.searchParams.get("tab")||"games",Math.max(0,Number(url.searchParams.get("page"))||0),Number(url.searchParams.get("game"))||null);
      else if(parts[0]==="admin")page=notice("Cần quyền ADMIN để xem trang này.","error");
      else if(!parts.length || parts[0]==="home")page=await homePage(this);
      else if(parts[0]==="quizzes")page=parts[1]==="new"?await questionBankEditor(this):await questionBankList(this,Math.max(0,Number(url.searchParams.get("page"))||0),url.searchParams.get("scope"),url.searchParams.get("mode"));
      else if(parts[0]==="quiz" && Number(parts[1])>0)page=parts[2]==="edit"?await questionBankEditor(this,Number(parts[1])):await questionBankDetail(this,Number(parts[1]));
      else if(parts[0]==="rooms")page=parts[1]==="new"?await roomEditor(this,null,url.searchParams.get("quiz")):await roomList(this,Math.max(0,Number(url.searchParams.get("page"))||0));
      else if(parts[0]==="join")page=joinPage(this);
      else if(parts[0]==="room" && Number(parts[1])>0)page=parts[2]==="edit"?await roomEditor(this,Number(parts[1])):await waitingPage(this,Number(parts[1]));
      else if(parts[0]==="game" && Number(parts[1])>0)page=await gamePage(this,Number(parts[1]));
      else if(parts[0]==="history")page=Number(parts[1])>0?await historyDetail(this,Number(parts[1])):await historyList(this,Math.max(0,Number(url.searchParams.get("page"))||0));
      else if(parts[0]==="profile")page=profilePage(this,Number(parts[1])>0?Number(parts[1]):null,url.searchParams.get("tab"),Math.max(0,Number(url.searchParams.get("page"))||0));
      else if(parts[0]==="leaderboard")page=leaderboardPage(this,Math.max(0,Number(url.searchParams.get("page"))||0));
      else page=notice("Không tìm thấy trang.","error");
      if(version!==this.renderVersion){disposeMedia(page);return;}view.replaceChildren(page);if(this.flash){view.prepend(notice(this.flash,this.flashTone||"success"));this.flash=null;this.flashTone=null;}
    }catch(e){if(version===this.renderVersion)view.replaceChildren(errorState(e.message,()=>this.render()));}
  }
  async start(){try{this.user=await this.api.request("GET","/api/auth/me");}catch(e){if(e.code!=="UNAUTHENTICATED")this.flash=e.message;}if(this.user?.role==="ADMIN" && !["/admin","/admin/"].includes(location.pathname)){location.replace("/admin");return;}await this.render();if(this.user && this.user.role!=="ADMIN")this.connect();}
}
new App().start();
