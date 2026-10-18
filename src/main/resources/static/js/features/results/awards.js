import {h,notice,button,link} from "/js/shared/dom.js";
import {MODE_LABELS} from "/js/shared/mode-labels.js";
import {awardLabel,awardEmblem,rankMark} from "/js/shared/achievement-ui.js";

/** Server decision states, never inferred from score/rank or a gameplay ACK. */
export const AWARD_STATES=Object.freeze(["AWARDS_PENDING","AWARDS_NOT_QUALIFIED","AWARDS_DECIDED"]);
export function awardStateView(state) {
  if(!AWARD_STATES.includes(state))return null;
  const messages={AWARDS_PENDING:"Đang chờ kết quả xét thưởng.",AWARDS_NOT_QUALIFIED:"Trận không đủ điều kiện xét thưởng.",AWARDS_DECIDED:"Kết quả xét thưởng đã sẵn sàng."};
  return h("section",{className:"card award-state","data-award-state":state},h("h2",{},"Xét thưởng"),notice(messages[state]));
}

const reasons={NOT_COMPLETED:"Trận bị hủy hoặc gián đoạn, không tính thành tích chính thức.",INSUFFICIENT_INITIAL_PLAYERS:"Roster ban đầu chưa đủ người chơi theo chính sách.",INSUFFICIENT_QUESTIONS:"Trận chưa đủ số câu được cấu hình và chấm hoàn tất.",INSUFFICIENT_ELIGIBLE_PLAYERS:"Không còn đủ người đủ điều kiện xét thưởng.",NO_ELIGIBLE_CORRECT:"Nhóm đủ điều kiện không có câu trả lời đúng."};
const title=award=>award.type==="CUP"?"+1 Cúp vô địch":award.type==="FAST"?`Tay nhanh · ${MODE_LABELS[award.modeKey]||award.modeKey}`:awardLabel(award.type);

export function ceremonyView(data,selfId){
  if(!data.awardState)return notice("Trận này không áp dụng chính sách xét thưởng mới. Kết quả thi đấu vẫn được giữ.");
  if(data.awardState==="AWARDS_PENDING")return awardStateView(data.awardState);
  if(!["AWARDS_DECIDED","AWARDS_NOT_QUALIFIED"].includes(data.awardState))return notice("Chưa thể đọc quyết định xét thưởng.","error");
  if(data.awardState==="AWARDS_NOT_QUALIFIED")return h("section",{className:"card award-ceremony","data-award-state":data.awardState},h("p",{className:"eyebrow"},"KẾT QUẢ XÉT THƯỞNG"),h("h2",{},"Trận không đủ điều kiện"),
    ...(data.qualification?.reasonCodes||[]).map(code=>notice(reasons[code]||"Trận không đáp ứng chính sách xét thưởng.")),h("p",{className:"muted"},"Không cộng cúp hoặc số trận chính thức. Điểm và thứ hạng lịch sử không đổi."));
  const participants=data.participants||[],awards=data.awards||[],own=participants.find(p=>p.userId===selfId);
  const podium=participants.filter(p=>p.eligible && p.awardRank<=3).map(p=>h("article",{className:`award-podium-card${p.userId===selfId?" own":""}`,"data-award-rank":p.awardRank},
    h("div",{className:"award-rank"},rankMark(p.awardRank)),h("h3",{},p.displayNameSnapshot),h("p",{className:"muted"},`Hạng lịch sử #${p.historyRank}`),
    ...awards.filter(a=>a.userId===p.userId&&a.type==="CUP"&&a.value===1).map(a=>h("p",{className:"award-cup","data-award-id":a.awardId},awardEmblem(a.type),title(a)))));
  return h("section",{className:"card award-ceremony","data-award-state":data.awardState},h("p",{className:"eyebrow"},"VINH DANH"),h("h2",{},"Thành tích đã được ghi nhận"),h("p",{className:"muted"},"Thứ hạng xét thưởng thuộc nhóm đủ điều kiện; thứ hạng thi đấu gốc vẫn được giữ."),
    h("div",{className:"award-podium"},podium),
    h("div",{className:"award-badges"},awards.filter(a=>a.type!=="CUP").map(a=>{const p=participants.find(p=>p.userId===a.userId);return h("article",{className:`award-badge${a.userId===selfId?" own":""}`,"data-award-id":a.awardId},awardEmblem(a.type),h("h3",{},title(a)),h("p",{},p?.displayNameSnapshot||"Người chơi"));})),
    own?notice(own.eligible?`Của bạn: +${own.officialGameDelta} trận chính thức · +${own.officialWinDelta} trận thắng.`:"Bạn không thuộc nhóm đủ điều kiện nhận thưởng trận này.",own.eligible?"success":"info"):null);
}

/** Owns one abortable GET/poll at a time, stops on terminal/dispose; no WS handler or gameplay timer. */
export function awardPanel(app,gameId,isCurrent=()=>true){
  const node=h("div",{className:"award-panel","aria-live":"polite"});
  const version=app.renderVersion,self=app.user?.id;let disposed=false,timer=null,request=null,attempts=0,working=false;
  const current=()=>!disposed && version===app.renderVersion && self===app.user?.id && isCurrent();
  async function refresh(){
    if(!current()||working)return;clearTimeout(timer);working=true;request=new AbortController();
    if(!node.childNodes.length)node.append(awardStateView("AWARDS_PENDING"));
    try{const data=await app.api.request("GET",`/api/games/${gameId}/awards`,null,{signal:request.signal});if(!current())return;
      node.dataset.awardState=data.awardState||"UNAVAILABLE";node.replaceChildren(ceremonyView(data,self));
      node.append(h("nav",{className:"achievement-tabs","aria-label":"Thành tích toàn Server"},link("Xem hồ sơ","profile","button secondary"),link("Sảnh danh vọng","leaderboard","button secondary")));
      if(data.awardState==="AWARDS_PENDING"){
        if(++attempts<60)timer=setTimeout(refresh,Math.min(5000,1000+attempts*100));
        else node.append(button("Cập nhật xét thưởng",()=>{attempts=0;refresh();},{className:"secondary"}));
      }
    }catch(error){if(current())node.replaceChildren(notice(error.message,"error"),button("Thử đọc lại xét thưởng",()=>{attempts=0;refresh();},{className:"secondary"}));}
    finally{working=false;request=null;}
  }
  refresh();return {node,refresh,dispose(){disposed=true;clearTimeout(timer);request?.abort();}};
}
