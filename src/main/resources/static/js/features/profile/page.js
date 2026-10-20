import {h,button,link} from "/js/shared/dom.js";
import {loadingState,errorState,emptyState} from "/js/shared/ui.js";
import {achievementStats,awardLabel,awardEmblem,pageLinks} from "/js/shared/achievement-ui.js";
import {MODE_LABELS} from "/js/shared/mode-labels.js";
import {icon} from "/js/shared/icons.js";

const awardTime=ms=>h("time",{dateTime:new Date(ms).toISOString()},new Date(ms).toLocaleString("vi-VN"));
const awardMode=key=>MODE_LABELS[key]|| (key==="ALL"?"Toàn trận":key);
function profileHeader(data,own,refresh){
  return h("header",{className:"card profile-hero"},
    h("span",{className:"profile-insignia","aria-hidden":"true"},icon("medal")),
    h("div",{className:"profile-identity"},h("p",{className:"eyebrow"},own?"HỒ SƠ CỦA BẠN":"HỒ SƠ CÔNG KHAI"),h("h1",{},data.displayName),h("p",{className:"muted"},"Thành tích chính thức từ các trận đủ điều kiện.")),
    button("Cập nhật",refresh,{className:"secondary",id:"refresh-profile"}));
}
function badgeCollection(badges){
  if(!badges.length)return emptyState("Danh hiệu sẽ xuất hiện sau khi hoàn tất xét thưởng.","Chưa có danh hiệu");
  const items=badges.map(b=>h("article",{className:"profile-badge","data-award-type":b.type,"data-mode":b.modeKey},
    h("div",{className:"profile-badge-top"},awardEmblem(b.type),h("span",{className:"badge-earned-count"},`${b.count} lần`)),
    h("div",{className:"profile-badge-details"},h("h3",{},awardLabel(b.type)),h("p",{className:"award-mode"},awardMode(b.modeKey)),h("p",{className:"muted badge-earned-date"},"Gần nhất ",awardTime(b.lastEarnedAtMs)))));
  return h("section",{className:"badge-list","aria-labelledby":"badge-heading"},
    h("div",{className:"achievement-section-heading"},h("div",{},h("h2",{id:"badge-heading"},"Bộ sưu tập danh hiệu"),h("p",{className:"muted"},"Những danh hiệu đã nhận, cùng số lần đạt và lần gần nhất."))),
    h("div",{className:"profile-badge-grid"},items));
}
function awardHistory(awards){
  return h("section",{className:"award-history","aria-labelledby":"award-history-heading"},
    h("div",{className:"achievement-section-heading"},h("div",{},h("h2",{id:"award-history-heading"},"Lịch sử nhận thưởng"),h("p",{className:"muted"},"Từng lần trao đã được ghi nhận. Mở trận để xem kết quả tương ứng."))),
    awards.items.length?h("ol",{className:"earned-awards"},awards.items.map(a=>h("li",{"data-award-id":a.awardId},
      h("div",{className:"award-receipt-time"},awardTime(a.earnedAtMs)),awardEmblem(a.type),
      h("div",{className:"earned-award-details"},h("h3",{},awardLabel(a.type)),h("p",{className:"award-mode"},awardMode(a.modeKey))),
      link(["Xem trận ",h("span",{},`#${a.gameSessionId}`)],`history/${a.gameSessionId}`,"text-link award-game-link")))):emptyState("Chưa có lần trao thưởng nào trong hồ sơ.","Danh sách trống"),
    awards.items.length?pageLinks("profile?tab=awards",awards):null);
}

export function profilePage(app,userId=null,tab="summary",page=0){
  const own=userId==null || userId===app.user.id;
  if(!own || tab!=="awards")tab="summary";
  const content=h("div",{className:"profile-content"},loadingState()),root=h("section",{id:"profile-page",className:"achievement-page"},content);
  const controller=new AbortController();let disposed=false,working=false;
  const load=async()=>{
    if(disposed || working)return;working=true;
    content.replaceChildren(loadingState());
    try{
      const data=await app.api.request("GET",own?"/api/profiles/me":`/api/profiles/${userId}`,null,{signal:controller.signal});
      const awards=own && tab==="awards"?await app.api.request("GET",`/api/profiles/me/awards?page=${page}&size=20`,null,{signal:controller.signal}):null;
      if(disposed)return;
      const tabs=own?h("nav",{className:"achievement-tabs profile-tabs","aria-label":"Nội dung hồ sơ"},link([icon("grid"),"Tổng quan"],"profile","button secondary"),link([icon("sort"),"Lịch sử nhận thưởng"],"profile?tab=awards","button secondary")):null;
      if(tabs){const active=tabs.children[tab==="awards"?1:0];active.setAttribute("aria-current","page");active.classList.replace("secondary","primary");}
      content.replaceChildren(...[profileHeader(data,own,load),tabs,
        tab==="summary"?h("section",{className:"profile-overview","aria-labelledby":"profile-stats-heading"},h("h2",{id:"profile-stats-heading",className:"sr-only"},"Thống kê chính thức"),achievementStats(data.stats)):null,
        own && tab==="summary"?badgeCollection(data.badges):null,awards?awardHistory(awards):null].filter(node=>node!=null));
    }catch(error){if(!disposed)content.replaceChildren(errorState(error.message,load));}finally{working=false;}
  };
  app.profileController={refresh:load,dispose(){disposed=true;controller.abort();}};
  load();return root;
}
