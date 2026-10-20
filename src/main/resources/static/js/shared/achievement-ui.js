import {h,link} from "/js/shared/dom.js";
import {icon} from "/js/shared/icons.js";

export const percentage=value=>value==null?"Chưa xếp hạng":new Intl.NumberFormat("vi-VN",{style:"percent",maximumFractionDigits:1}).format(value);
export const awardLabel=type=>({CUP:"Cúp vô địch",FAST:"Tay nhanh",PERFECT:"Bách phát bách trúng",MULTITALENT:"Chuyên gia đa tài",COMEBACK:"Lội ngược dòng"}[type]||type);
const awardIcons=Object.freeze({CUP:"trophy",FAST:"bolt",PERFECT:"target",MULTITALENT:"sparkles",COMEBACK:"comeback"});
export function awardEmblem(type){
  const known=Object.hasOwn(awardIcons,type);
  return h("span",{className:`achievement-emblem achievement-emblem--${known?type.toLowerCase():"default"}`,"aria-hidden":"true"},icon(known?awardIcons[type]:"medal"));
}
export function rankMark(rank){
  const top=Number.isInteger(rank)&&rank>=1&&rank<=3;
  return h("span",{className:`rank-mark${top?` rank-mark--${rank}`:""}`},top?icon("medal"):null,h("span",{},`#${rank}`));
}
export function achievementStats(stats){
  const metrics=[
    ["cups","Cúp",stats.cups,"trophy"],
    ["games","Trận chính thức",stats.officialGames,"gamepad"],
    ["wins","Thắng",stats.officialWins,"medal"],
    ["win-rate","Tỷ lệ thắng",percentage(stats.winRate),"sparkles"],
    ["accuracy","Độ chính xác",percentage(stats.accuracy),"target"],
    ["answers","Câu đúng / tham gia",`${stats.correct} / ${stats.participated}`,"check"]
  ];
  return h("dl",{className:"achievement-stats","aria-label":"Thống kê chính thức"},metrics.map(([key,label,value,mark],index)=>h("div",{className:`achievement-metric metric--${key}${index<3?" metric-primary":""}`,"data-stat":key},h("dt",{},icon(mark),label),h("dd",{},value))));
}
export function achievementTabs(current){
  return h("nav",{className:"achievement-tabs","aria-label":"Thành tích"},[["profile","Hồ sơ của tôi"],["leaderboard","Sảnh danh vọng"]].map(([path,label])=>{const item=link(label,path,`button ${current===path?"primary":"secondary"}`);if(current===path)item.setAttribute("aria-current","page");return item;}));
}
export function pageLinks(path,data){
  const separator=path.includes("?")?"&":"?";
  return h("nav",{className:"pagination","aria-label":"Phân trang"},data.page>0?link("Trang trước",`${path}${separator}page=${data.page-1}`,"button secondary"):null,h("span",{},`Trang ${data.page+1} · ${data.totalElements} mục`),(data.page+1)*data.size<data.totalElements?link("Trang sau",`${path}${separator}page=${data.page+1}`,"button secondary"):null);
}
