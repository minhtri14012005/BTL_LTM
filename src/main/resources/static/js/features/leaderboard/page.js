import {h,heading,button,link,notice} from "/js/shared/dom.js";
import {loadingState,errorState,emptyState} from "/js/shared/ui.js";
import {achievementTabs,percentage,pageLinks,rankMark} from "/js/shared/achievement-ui.js";
import {icon} from "/js/shared/icons.js";

export function leaderboardPage(app,page=0){
  const content=h("div",{},loadingState()),root=h("section",{id:"leaderboard-page",className:"achievement-page"},achievementTabs("leaderboard"),content);
  const controller=new AbortController();let disposed=false,working=false;
  const load=async()=>{
    if(disposed || working)return;working=true;
    content.replaceChildren(loadingState());
    try{
      const data=await app.api.request("GET",`/api/leaderboard?page=${page}&size=20`,null,{signal:controller.signal});if(disposed)return;
      const rows=data.items.map(row=>h("tr",{className:row.userId===app.user.id?"own-standing":"","data-user-id":row.userId},
        h("th",{scope:"row"},rankMark(row.rank)),h("td",{},link(row.displayName,`profile/${row.userId}`),row.userId===app.user.id?h("span",{className:"badge"},"Bạn"):null),h("td",{},h("span",{className:"cup-count"},icon("trophy"),row.stats.cups)),h("td",{},`${row.stats.officialWins} / ${row.stats.officialGames}`),h("td",{},percentage(row.stats.winRate)),h("td",{},percentage(row.stats.accuracy))));
      const table=rows.length?h("div",{className:"card achievement-table-scroll",tabIndex:0,"aria-label":"Bảng thành tích, cuộn ngang để xem các cột"},h("table",{className:"achievement-table"},h("caption",{className:"sr-only"},"Cúp, tỷ lệ thắng và độ chính xác chính thức"),h("thead",{},h("tr",{},["Hạng","Người chơi","Cúp","Thắng / Trận","Tỷ lệ thắng","Chính xác"].map(name=>h("th",{scope:"col"},name)))),h("tbody",{},rows))):emptyState("Chưa có người chơi được xếp hạng ở trang này.","Sảnh danh vọng đang chờ bạn");
      content.replaceChildren(heading("Sảnh danh vọng","Cúp → tỷ lệ thắng → độ chính xác. Bằng cả ba chỉ số được đồng hạng.",button("Cập nhật",load,{className:"secondary",id:"refresh-leaderboard"})),
        data.own?notice(`Vị trí của bạn: #${data.own.rank} · ${data.own.stats.cups} cúp · ${data.own.stats.officialWins}/${data.own.stats.officialGames} trận thắng.`,"success"):notice("Bạn chưa xếp hạng: chưa có trận chính thức đủ điều kiện."),table,pageLinks("leaderboard",data),h("p",{className:"muted"},"Chỉ dùng thành tích chính thức đã ghi nhận. Giao hữu không cộng vào bảng này."));
    }catch(error){if(!disposed)content.replaceChildren(errorState(error.message,load));}finally{working=false;}
  };
  app.leaderboardController={refresh:load,dispose(){disposed=true;controller.abort();}};
  load();return root;
}
