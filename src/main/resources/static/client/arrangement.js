import {h,button,input} from "./dom.js";
import {uuid} from "./core.js";
export const isArrangement=mode=>mode==="VIETNAMESE_PUZZLE" || mode==="ORDERING";
export function permutation(items,ids){return Array.isArray(items)&&Array.isArray(ids)&&items.length>=2&&items.length<=100&&ids.length===items.length&&new Set(items.map(v=>v.id)).size===items.length&&new Set(ids).size===ids.length&&ids.every(id=>items.some(v=>v.id===id));}
export function move(ids,index,delta){const next=[...ids],to=index+delta;if(to>=0&&to<next.length)[next[index],next[to]]=[next[to],next[index]];return next;}
export function arrangementEditor(q,mode){
 const key=mode==="VIETNAMESE_PUZZLE"?"pieces":"items",values=q[key]||[],order=q.correctOrder||values.map(v=>v.id);
 const list=h("div",{className:"arrangement-editor-list"});
 const renumber=()=>[...list.children].forEach((row,i)=>{row.querySelector("label span").textContent=`Mục ${i+1}`;row.querySelector('[data-move="up"]').disabled=i===0;row.querySelector('[data-move="down"]').disabled=i===list.children.length-1;});
 const add=(v={id:uuid(),text:""})=>{
  if(list.children.length>=100)return;
  const value=input("itemText",{value:v.text,id:"edit-item-"+uuid(),maxLength:300,required:true,"aria-label":"Nội dung mảnh / mục"});
  const row=h("div",{className:"arrangement-editor-row","data-id":v.id},h("label",{className:"field"},h("span",{}),value));
  row.append(button("↑",()=>{if(row.previousElementSibling)list.insertBefore(row,row.previousElementSibling);renumber();},{className:"secondary","data-move":"up","aria-label":"Đưa mục lên"}),button("↓",()=>{if(row.nextElementSibling)list.insertBefore(row.nextElementSibling,row);renumber();},{className:"secondary","data-move":"down","aria-label":"Đưa mục xuống"}),button("Xóa",()=>{if(list.children.length>2)row.remove();renumber();},{className:"secondary"}));
  list.append(row);renumber();
 };
 if(values.length)order.forEach(id=>add(values.find(v=>v.id===id)));else {add();add();}
 return h("div",{className:"arrangement-editor"},h("p",{},mode==="VIETNAMESE_PUZZLE"?"Nhập các mảnh theo một cách ghép đúng. Ghép nguyên văn, không tự thêm dấu cách: giữ khoảng trắng trong mảnh hoặc dùng một mảnh dấu cách. Mỗi mảnh có ID riêng dù chữ giống nhau.":"Nhập các mục theo thứ tự đáp án đúng. Khi chơi Server xáo thứ tự; mỗi mục có ID riêng dù nội dung giống nhau."),list,button("+ Thêm mảnh / mục",()=>add(),{className:"secondary","data-add-item":true}));
}
export function readArrangement(row,mode){const items=[...row.querySelector('.arrangement-editor-list').children].map(r=>({id:r.dataset.id,text:r.querySelector('[name="itemText"]').value}));return {[mode==="VIETNAMESE_PUZZLE"?"pieces":"items"]:items,correctOrder:items.map(v=>v.id)};}
export function arrangementAnswer(items,ids,enabled,onChange){
 const selected=items.filter(v=>ids.includes(v.id)),bank=h("div",{className:"piece-bank","aria-label":"Các mảnh chưa chọn"});
 items.filter(v=>!ids.includes(v.id)).forEach(v=>bank.append(button(v.text,()=>onChange([...ids,v.id]),{id:"piece-"+v.id,"data-piece-id":v.id,"aria-label":v.text.trim()?v.text:"Khoảng trắng",disabled:!enabled,className:"secondary"})));
 const chosen=h("ol",{className:"arranged-items","aria-label":"Thứ tự đã chọn"});
 ids.forEach((id,i)=>{const v=selected.find(v=>v.id===id);if(!v)return;chosen.append(h("li",{"data-item-id":id},h("span",{className:"piece-text"},v.text),button("↑",()=>onChange(move(ids,i,-1)),{id:"up-"+id,"data-move":"up",disabled:!enabled||i===0,className:"secondary","aria-label":"Đưa "+v.text+" lên"}),button("↓",()=>onChange(move(ids,i,1)),{id:"down-"+id,"data-move":"down",disabled:!enabled||i===ids.length-1,className:"secondary","aria-label":"Đưa "+v.text+" xuống"}),button("Bỏ",()=>onChange(ids.filter(x=>x!==id)),{id:"remove-"+id,disabled:!enabled,className:"secondary","aria-label":"Bỏ "+v.text})))});
 return h("div",{className:"arrangement-answer",id:"arrangement-answer"},h("p",{},"Chọn từng mảnh / mục rồi dùng ↑ ↓ để sắp xếp. Có thể dùng Tab và Enter. Gửi khi đã chọn đủ; mọi mục dùng đúng một lần."),bank,chosen,button("Chọn lại",()=>onChange([]),{disabled:!enabled,className:"secondary",id:"reset-arrangement"}));
}
export function correctArrangement(question){const p=question.payload||{},items=p.pieces||p.items||[];return (p.correctOrder||[]).map(id=>items.find(v=>v.id===id)?.text||"");}
