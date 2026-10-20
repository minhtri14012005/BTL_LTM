import {h,button,notice} from "/js/shared/dom.js";
export const isArrangement=mode=>mode==="VIETNAMESE_PUZZLE" || mode==="ORDERING";
export function permutation(items,ids){return Array.isArray(items)&&Array.isArray(ids)&&items.length>=2&&items.length<=100&&ids.length===items.length&&new Set(items.map(v=>v.id)).size===items.length&&new Set(ids).size===ids.length&&ids.every(id=>items.some(v=>v.id===id));}
export function move(ids,index,delta){const next=[...ids],to=index+delta;if(to>=0&&to<next.length)[next[index],next[to]]=[next[to],next[index]];return next;}
export function arrangementAnswer(items,ids,enabled,onChange){
 if(!items.length)return h("div",{id:"arrangement-answer",className:"arrangement-answer"},notice("Chưa nhận được các mục để sắp xếp. Hãy đồng bộ lại trạng thái trận.","error"));
 const selected=items.filter(v=>ids.includes(v.id)),bank=h("div",{className:"piece-bank","aria-label":"Các mảnh chưa chọn"});
 items.filter(v=>!ids.includes(v.id)).forEach(v=>bank.append(button(v.text,()=>onChange([...ids,v.id]),{id:"piece-"+v.id,"data-piece-id":v.id,"aria-label":v.text.trim()?v.text:"Khoảng trắng",disabled:!enabled,className:"secondary"})));
 const chosen=h("ol",{className:"arranged-items","aria-label":"Thứ tự đã chọn"});
 ids.forEach((id,i)=>{const v=selected.find(v=>v.id===id);if(!v)return;chosen.append(h("li",{"data-item-id":id},h("span",{className:"arranged-number","aria-hidden":"true"},i+1),h("span",{className:"piece-text"},v.text),button("↑",()=>onChange(move(ids,i,-1)),{id:"up-"+id,"data-move":"up",disabled:!enabled||i===0,className:"secondary","aria-label":"Đưa "+v.text+" lên"}),button("↓",()=>onChange(move(ids,i,1)),{id:"down-"+id,"data-move":"down",disabled:!enabled||i===ids.length-1,className:"secondary","aria-label":"Đưa "+v.text+" xuống"}),button("Bỏ",()=>onChange(ids.filter(x=>x!==id)),{id:"remove-"+id,disabled:!enabled,className:"secondary","aria-label":"Bỏ "+v.text})))});
 return h("div",{className:"arrangement-answer ordering-answer",id:"arrangement-answer"},h("div",{className:"arrangement-heading"},h("h3",{},"Sắp xếp đáp án"),h("p",{className:"arrangement-progress",role:"status"},`${ids.length}/${items.length} mục đã chọn`)),
  h("p",{className:"arrangement-help"},"Chọn mục, dùng ↑ ↓ để đổi thứ tự. Tab và Enter đều được hỗ trợ."),
  h("div",{className:"ordering-workspace"},h("section",{},h("h4",{},"1. Các mục chưa chọn"),bank),h("section",{},h("h4",{},"2. Thứ tự của bạn"),ids.length?null:h("p",{className:"arrangement-empty"},"Chọn từng mục ở danh sách để bắt đầu."),chosen)),
  h("p",{className:"arrangement-help"},"Gửi khi đã chọn đủ; mỗi mục dùng đúng một lần."),button("Chọn lại",()=>onChange([]),{disabled:!enabled,className:"secondary",id:"reset-arrangement"}));
}
export function correctArrangement(question){const p=question.payload||{},items=p.pieces||p.items||[];return (p.correctOrder||[]).map(id=>items.find(v=>v.id===id)?.text||"");}

// Only append unused IDs. Labels (including literal spaces) are never rewritten.
export function vietnameseAnswer(items,ids,enabled,onChange){
 if(!items.length)return h("div",{id:"arrangement-answer",className:"arrangement-answer"},notice("Chưa nhận được các ô từ của câu hỏi. Hãy đồng bộ lại trạng thái trận.","error"));
 const bank=h("div",{className:"piece-bank","aria-label":"Các ô chưa dùng"});
 const row=h("div",{className:"sentence-row",id:"sentence-row",tabIndex:0,role:"group","aria-label":"Hàng ghép đáp án, cuộn ngang để xem các ô"});
 const add=id=>{if(enabled && !ids.includes(id) && items.some(v=>v.id===id))onChange([...ids,id]);};
 items.filter(v=>!ids.includes(v.id)).forEach(v=>{
  const tile=button(v.text,()=>add(v.id),{id:"piece-"+v.id,"data-piece-id":v.id,className:"secondary word-tile"+(!v.text.trim()?" word-tile--space":""),disabled:!enabled,"aria-label":v.text.trim()?v.text:"Khoảng trắng"});
  let drag=null,suppressClick=false;
  tile.addEventListener("click",e=>{if(suppressClick){e.stopImmediatePropagation();suppressClick=false;}},true);
  tile.onpointerdown=e=>{
   if(!enabled || !e.isPrimary || e.button!==0)return;
   suppressClick=false;
   drag={id:e.pointerId,x:e.clientX,y:e.clientY,ghost:null};tile.setPointerCapture(e.pointerId);
  };
  const clean=()=>{drag?.ghost?.remove();row.classList.remove("drop-active");drag=null;};
  tile.onpointermove=e=>{
   if(!drag || e.pointerId!==drag.id)return;
   if(!drag.ghost && Math.hypot(e.clientX-drag.x,e.clientY-drag.y)>6){
    drag.ghost=h("div",{className:"word-drag-ghost piece-text"},v.text);bank.append(drag.ghost);
   }
   if(drag.ghost){
    drag.ghost.style.left=e.clientX+"px";drag.ghost.style.top=e.clientY+"px";
    const r=row.getBoundingClientRect();row.classList.toggle("drop-active",e.clientX>=r.left && e.clientX<=r.right && e.clientY>=r.top && e.clientY<=r.bottom);
   }
  };
  tile.onpointerup=e=>{
   if(!drag || e.pointerId!==drag.id)return;
   const moved=!!drag.ghost,inside=row.classList.contains("drop-active");
   suppressClick=moved;clean();if(moved && inside)add(v.id);
  };
  tile.onpointercancel=clean;tile.onlostpointercapture=clean;
  bank.append(tile);
 });
 ids.forEach((id,i)=>{const v=items.find(v=>v.id===id);if(v)row.append(h("span",{className:"word-tile placed-word","data-item-id":id},h("small",{},i+1),h("span",{className:"piece-text"},v.text)));});
 if(!ids.length)row.append(h("span",{className:"muted sentence-placeholder"},"Kéo ô từ xuống đây"));
 return h("div",{className:"arrangement-answer vietnamese-answer",id:"arrangement-answer"},
  h("div",{className:"arrangement-heading"},h("h3",{},"Ghép câu trả lời"),h("p",{className:"arrangement-progress",role:"status"},`${ids.length}/${items.length} ô đã dùng`)),
  h("details",{className:"game-help"},h("summary",{id:"arrangement-help"},"Cách ghép các ô"),h("p",{},"Kéo từng ô xuống hàng đáp án theo thứ tự bạn chọn. Bạn cũng có thể chạm hoặc dùng Tab và Enter. Cuộn ngang để xem toàn bộ câu. Giữ nguyên dấu và khoảng trắng; dùng hết mỗi ô đúng một lần.")),
  h("h4",{},"1. Các ô chưa dùng"),bank,h("h4",{},"2. Đáp án của bạn"),row,
  h("div",{className:"actions"},button("Quay lại 1 bước",()=>{if(enabled && ids.length)onChange(ids.slice(0,-1));},{id:"undo-arrangement",className:"secondary",disabled:!enabled||!ids.length}),
   button("Sắp xếp lại từ đầu",()=>{if(enabled)onChange([]);},{id:"reset-arrangement",className:"secondary",disabled:!enabled||!ids.length})));
}
