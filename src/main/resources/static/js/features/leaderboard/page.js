import {h,button,link,notice} from "/js/shared/dom.js";
import {loadingState,errorState,emptyState} from "/js/shared/ui.js";
import {percentage,pageLinks,rankMark} from "/js/shared/achievement-ui.js";
import {icon} from "/js/shared/icons.js";

export function leaderboardPage(app,page=0){
  const content=h("div",{className:"leaderboard-content"},loadingState()),root=h("section",{id:"leaderboard-page",className:"achievement-page"},content);
  const controller=new AbortController();let disposed=false,working=false;
  const load=async()=>{
    if(disposed || working)return;working=true;
    content.replaceChildren(loadingState());
    try{
      const data=await app.api.request("GET",`/api/leaderboard?page=${page}&size=20`,null,{signal:controller.signal});if(disposed)return;
      const labels=["Hạng","Người chơi","Cúp","Thắng / Trận","Tỷ lệ thắng","Chính xác"];
      const rows=data.items.map(row=>{
        const self=row.userId===app.user.id;
        const cell=(index,value,cls)=>h("td",{role:"cell",headers:`leader-col-${index} leader-row-${row.userId}`,className:cls},h("span",{className:"leader-mobile-label","aria-hidden":"true"},labels[index]),value);
        return h("tr",{role:"row",className:`leaderboard-row${self?" own-standing":""}`,"data-user-id":row.userId,"data-server-rank":row.rank},
          h("th",{role:"rowheader",scope:"row",id:`leader-row-${row.userId}`,headers:"leader-col-0",className:"leader-rank"},rankMark(row.rank)),
          cell(1,h("div",{className:"leader-player"},link(row.displayName,`profile/${row.userId}`),self?h("span",{className:"badge"},"Bạn"):null),"leader-name"),
          cell(2,h("span",{className:"cup-count"},icon("trophy"),row.stats.cups),"leader-cups"),
          cell(3,`${row.stats.officialWins} / ${row.stats.officialGames}`,"leader-wins"),cell(4,percentage(row.stats.winRate),"leader-rate"),cell(5,percentage(row.stats.accuracy),"leader-accuracy"));
      });
      const table=rows.length?h("div",{className:"card achievement-table-scroll",tabIndex:0,"aria-label":"Bảng thành tích chính thức"},h("table",{className:"achievement-table",role:"table"},h("caption",{className:"sr-only"},"Xếp hạng theo cúp, tỷ lệ thắng và độ chính xác. Người chơi bằng cả ba chỉ số được đồng hạng."),h("thead",{role:"rowgroup"},h("tr",{role:"row"},labels.map((name,i)=>h("th",{role:"columnheader",scope:"col",id:`leader-col-${i}`},name)))),h("tbody",{role:"rowgroup"},rows))):emptyState("Chưa có người chơi được xếp hạng ở trang này.","Sảnh danh vọng đang chờ bạn");
      content.replaceChildren(
        h("header",{className:"card leaderboard-hero"},h("span",{className:"leaderboard-insignia","aria-hidden":"true"},icon("trophy")),h("div",{className:"profile-identity"},h("p",{className:"eyebrow"},"THÀNH TÍCH CHÍNH THỨC"),h("h1",{},"Sảnh danh vọng"),h("p",{className:"muted"},"Xếp theo cúp, tỷ lệ thắng và độ chính xác. Bằng cả ba chỉ số được đồng hạng.")),button("Cập nhật",load,{className:"secondary",id:"refresh-leaderboard"})),
        h("div",{className:"leaderboard-context"},data.own?h("p",{className:"own-position"},icon("medal"),"Vị trí của bạn ",h("strong",{},`#${data.own.rank}`)):notice("Bạn chưa xếp hạng: chưa có trận chính thức đủ điều kiện."),
          Number.isFinite(data.evaluatedAtMs)?h("p",{className:"muted leaderboard-evaluated"},"Đánh giá lúc ",h("time",{dateTime:new Date(data.evaluatedAtMs).toISOString()},new Date(data.evaluatedAtMs).toLocaleString("vi-VN"))):null),
        table,pageLinks("leaderboard",data),h("p",{className:"muted leaderboard-policy"},"Chỉ tính thành tích chính thức đã ghi nhận. Giao hữu không cộng vào bảng này."));
    }catch(error){if(!disposed)content.replaceChildren(errorState(error.message,load));}finally{working=false;}
  };
  app.leaderboardController={refresh:load,dispose(){disposed=true;controller.abort();}};
  load();return root;
}
