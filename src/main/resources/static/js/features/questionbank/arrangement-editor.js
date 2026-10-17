import {h,button,input} from "/js/shared/dom.js";
import {uuid} from "/js/core/protocol.js";
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
