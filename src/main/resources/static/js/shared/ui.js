import {h,button,notice} from "/js/shared/dom.js";
import {icon} from "/js/shared/icons.js";

export function loadingState(label="Đang tải…") {
  return h("div",{className:"loading-state",role:"status","aria-busy":"true"},
    h("div",{className:"loading-label"},h("span",{className:"spinner","aria-hidden":"true"}),label),
    h("div",{className:"skeleton-stack","aria-hidden":"true"},Array.from({length:3},()=>h("div",{className:"skeleton-line"}))));
}
export function emptyState(message,title="Chưa có dữ liệu") {
  return h("section",{className:"card empty-state",role:"status"},icon("inbox"),h("h2",{},title),h("p",{},message));
}
export function errorState(message,retry) {
  return h("section",{className:"card empty-state error-state",role:"alert"},icon("warning"),h("h2",{},"Không thể tải dữ liệu"),h("p",{},message),retry?button("Tải lại",retry,{className:"secondary"}):null);
}
/** Presentation only: caller owns lifetime/deadline, including game toasts. */
export function toast(message,kind="info") {
  const node=notice(message,kind);node.classList.add("toast");return node;
}
let confirmationId=0;
const triggers=new WeakMap();
export function confirmPanel(message,className="") {
  const panel=h("div",{id:`confirmation-${++confirmationId}`,className:`confirm-panel ${className}`,hidden:true,role:"group","aria-label":"Xác nhận thao tác"},
    h("div",{className:"confirm-message"},icon("warning"),h("p",{},message)));
  panel.addEventListener("keydown",event=>{
    if(event.key==="Escape"){event.preventDefault();event.stopPropagation();setConfirmation(panel,false);}
  });
  return panel;
}
/** Inline confirmation, not a modal: no focus trap/global keyboard listener. */
export function setConfirmation(panel,open,trigger=null) {
  if(trigger){triggers.set(panel,trigger);trigger.setAttribute("aria-controls",panel.id);}
  const opener=triggers.get(panel);panel.hidden=!open;
  opener?.setAttribute("aria-expanded",String(open));
  if(open)panel.querySelector("button:not(:disabled)")?.focus();
  else if(opener?.isConnected)opener.focus();
}
