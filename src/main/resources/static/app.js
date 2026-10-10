import {Api} from "/client/api.js";
import {Transport} from "/client/transport.js";
import {latestRoom} from "/client/core.js";
import {h,button,link,notice} from "/client/dom.js";
import {accountPage,homePage} from "/client/account.js";
import {questionBankList,questionBankDetail,questionBankEditor} from "/client/question-banks.js";
import {roomList,roomEditor,joinPage,waitingPage} from "/client/rooms.js";
import {gamePage} from "/client/game.js";
import {historyList,historyDetail} from "/client/history.js";

class App {
  constructor(){this.user=null;this.rooms=new Map();this.games=new Map();this.renderVersion=0;this.api=new Api(()=>this.expire());this.transport=new Transport(m=>this.event(m),s=>this.connection(s));window.addEventListener("hashchange",()=>this.render());}
  statusLabel(s){return {DRAFT:"Bản nháp",WAITING:"Đang chờ",ACTIVE:"Đang diễn ra",CLOSED:"Đã đóng"}[s]||s;}
  navigate(path){if(location.hash===`#/${path}`)this.render();else location.hash=`/${path}`;}
  clear(){this.api.invalidate();this.transport.close();this.gameController?.dispose();this.gameController=null;this.user=null;this.rooms.clear();this.games.clear();this.gamePresentation?.clear();this.roomController=null;this.activeRoomId=null;}
  expire(){if(!this.user)return;this.clear();this.flash="Phiên đăng nhập đã hết hạn. Hãy đăng nhập lại.";this.navigate("login");}
  async logout(){try{await this.api.request("POST","/api/auth/logout");}catch(e){if(this.user){document.querySelector("#connection-banner").replaceChildren(notice(e.message,"error"));return;}}this.clear();this.flash="Đã đăng xuất.";this.navigate("login");}
  async connect(explicit=false){if(!this.user)return;if(this.transport.status==="replaced" && !explicit)return;try{await this.transport.open();}catch(e){if(explicit)throw e;}}
  connection(state){if(state==="expired"){this.expire();return;}const banner=document.querySelector("#connection-banner");if(!this.user){banner.replaceChildren();return;}
    const labels={ready:"Trực tuyến",connecting:"Đang kết nối",offline:"Mất kết nối",replaced:"Đã thay kết nối"};const badge=document.querySelector("#connection-status");if(badge){badge.textContent=labels[state];badge.dataset.state=state;}
    banner.replaceChildren();if(["offline","replaced"].includes(state))banner.append(notice(state==="replaced"?"Một cửa sổ khác đang dùng kết nối của bạn. Cửa sổ này đã dừng kết nối tự động.":"Kết nối phòng đã ngắt. Dữ liệu hiển thị có thể đã cũ."),button("Kết nối tại đây",()=>this.connect(true).catch(e=>banner.append(notice(e.message,"error"))),{className:"secondary",id:"reconnect-room"}));
    if(state==="ready"){this.roomController?.subscribe();this.gameController?.reconnect();}else{this.roomController?.update();this.gameController?.update();}
  }
  adoptRoom(room){if(!room?.id)return;this.rooms.set(room.id,latestRoom(this.rooms.get(room.id),room));if(this.roomController?.id===room.id)this.roomController.update();}
  event(m){if(m.type==="ROOM_UPDATED")this.adoptRoom(m.payload);else if(m.type==="ROOM_ACCESS_REVOKED"){this.rooms.delete(m.target.id);if(this.activeRoomId===m.target.id){this.flash="Bạn không còn là thành viên phòng.";this.navigate("rooms");}}
    else if(m.type==="GAME_STARTED" && m.payload.roomId===this.activeRoomId)this.enterGame(m.target.id);
    this.gameController?.receive(m);}
  enterGame(id){if(location.hash!==`#/game/${id}`)this.navigate(`game/${id}`);}
  shell(){const side=document.querySelector("#sidebar");side.hidden=!this.user;document.body.classList.toggle("signed-in",!!this.user);side.replaceChildren();if(!this.user){document.querySelector("#connection-banner").replaceChildren();return;}
    side.append(link("M / MultiGame","home","brand"),h("p",{className:"nav-label"},"KHÔNG GIAN CỦA BẠN"),h("nav",{},link("Tổng quan","home"),link("Bộ câu hỏi","quizzes"),link("Phòng của tôi","rooms"),link("Tham gia phòng","join"),link("Lịch sử","history")),h("div",{className:"account-info"},h("strong",{},this.user.displayName),h("small",{},`@${this.user.username}`),h("span",{id:"connection-status",className:"connection"}),button("Đăng xuất",()=>this.logout(),{className:"secondary",id:"logout"})));this.connection(this.transport.status);}
  async render(){const version=++this.renderVersion;this.gameController?.dispose();this.gameController=null;this.roomController=null;const view=document.querySelector("#view");this.shell();view.replaceChildren(notice("Đang tải…"));
    const url=new URL(location.hash.slice(1)||"/home",location.origin);const parts=url.pathname.split("/").filter(Boolean);let page;
    try{if(!this.user)page=accountPage(this,parts[0]==="register");
      else if(!parts.length || parts[0]==="home")page=await homePage(this);
      else if(parts[0]==="quizzes")page=parts[1]==="new"?await questionBankEditor(this):await questionBankList(this,Math.max(0,Number(url.searchParams.get("page"))||0),url.searchParams.get("scope"),url.searchParams.get("mode"));
      else if(parts[0]==="quiz" && Number(parts[1])>0)page=parts[2]==="edit"?await questionBankEditor(this,Number(parts[1])):await questionBankDetail(this,Number(parts[1]));
      else if(parts[0]==="rooms")page=parts[1]==="new"?await roomEditor(this,null,url.searchParams.get("quiz")):await roomList(this,Math.max(0,Number(url.searchParams.get("page"))||0));
      else if(parts[0]==="join")page=joinPage(this);
      else if(parts[0]==="room" && Number(parts[1])>0)page=parts[2]==="edit"?await roomEditor(this,Number(parts[1])):await waitingPage(this,Number(parts[1]));
      else if(parts[0]==="game" && Number(parts[1])>0)page=await gamePage(this,Number(parts[1]));
      else if(parts[0]==="history")page=Number(parts[1])>0?await historyDetail(this,Number(parts[1])):await historyList(this,Math.max(0,Number(url.searchParams.get("page"))||0));
      else page=notice("Không tìm thấy trang.","error");
      if(version!==this.renderVersion)return;view.replaceChildren(page);if(this.flash){view.prepend(notice(this.flash,"success"));this.flash=null;}
    }catch(e){if(version===this.renderVersion)view.replaceChildren(notice(e.message,"error"),button("Tải lại",()=>this.render(),{className:"secondary"}));}
  }
  async start(){try{this.user=await this.api.request("GET","/api/auth/me");}catch(e){if(e.code!=="UNAUTHENTICATED")document.querySelector("#connection-banner").replaceChildren(notice(e.message,"error"));}await this.render();if(this.user)this.connect();}
}
new App().start();
