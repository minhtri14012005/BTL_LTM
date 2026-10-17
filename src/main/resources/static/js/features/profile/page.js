import {h,heading,button,link} from "/js/shared/dom.js";
import {loadingState,errorState,emptyState} from "/js/shared/ui.js";
import {achievementStats,achievementTabs,awardLabel,pageLinks} from "/js/shared/achievement-ui.js";
import {MODE_LABELS} from "/js/shared/mode-labels.js";

export function profilePage(app,userId=null,tab="summary",page=0){
  const own=userId==null || userId===app.user.id;
  if(!own || tab!=="awards")tab="summary";
  const content=h("div",{},loadingState()),root=h("section",{id:"profile-page",className:"achievement-page"},achievementTabs("profile"),content);
  const controller=new AbortController();let disposed=false,working=false;
  const load=async()=>{
    if(disposed || working)return;working=true;
    content.replaceChildren(loadingState());
    try{
      const data=await app.api.request("GET",own?"/api/profiles/me":`/api/profiles/${userId}`,null,{signal:controller.signal});
      const awards=own && tab==="awards"?await app.api.request("GET",`/api/profiles/me/awards?page=${page}&size=20`,null,{signal:controller.signal}):null;
      if(disposed)return;
      const tabs=own?h("nav",{className:"achievement-tabs","aria-label":"Nội dung hồ sơ"},link("Tổng quan","profile","button secondary"),link("Danh hiệu đã nhận","profile?tab=awards","button secondary")):null;
      if(tabs){const active=tabs.children[tab==="awards"?1:0];active.setAttribute("aria-current","page");active.classList.replace("secondary","primary");}
      const badgeList=own && tab==="summary"?(data.badges.length?h("section",{className:"card badge-list"},h("h2",{},"Danh hiệu đã ghi nhận"),data.badges.map(b=>h("p",{},`${awardLabel(b.type)}${b.modeKey!=="ALL"?` · ${MODE_LABELS[b.modeKey]||b.modeKey}`:""}: ${b.count} lần · gần nhất ${new Date(b.lastEarnedAtMs).toLocaleString("vi-VN")}`))):emptyState("Danh hiệu sẽ xuất hiện sau khi hoàn tất xét thưởng.","Chưa có danh hiệu")):null;
      const history=awards?(awards.items.length?h("div",{className:"card"},h("h2",{},"Các lần được trao"),h("ul",{className:"earned-awards"},awards.items.map(a=>h("li",{},h("strong",{},awardLabel(a.type)),h("span",{},`${MODE_LABELS[a.modeKey]||"Toàn trận"} · ${new Date(a.earnedAtMs).toLocaleString("vi-VN")}`),link(`Xem trận #${a.gameSessionId}`,`history/${a.gameSessionId}`)))),pageLinks("profile?tab=awards",awards)):emptyState("Chưa có lần trao thưởng nào trong hồ sơ.","Danh sách trống")):null;
      content.replaceChildren(heading(data.displayName,own?"Thành tích chính thức của bạn từ các trận đủ điều kiện.":"Hồ sơ công khai · Thành tích chính thức đã ghi nhận.",button("Cập nhật",load,{className:"secondary",id:"refresh-profile"})),tabs||h("span"),achievementStats(data.stats),badgeList||h("span"),history||h("span"));
    }catch(error){if(!disposed)content.replaceChildren(errorState(error.message,load));}finally{working=false;}
  };
  app.profileController={refresh:load,dispose(){disposed=true;controller.abort();}};
  load();return root;
}
