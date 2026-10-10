import {h,field,input,button,notice} from "./dom.js";
export function clueEditor(question={}) {
  const list=h("div",{className:"clue-list"});
  const add=(hint={offsetMs:0,text:""})=>{
    const row=h("div",{className:"clue-editor"});
    row.append(field("Mở sau (giây)",input("hintSeconds",{type:"number",required:true,min:0,step:"0.001",value:hint.offsetMs/1000})),
      field("Gợi ý",h("textarea",{name:"hintText",required:true,maxLength:2000,rows:2,value:hint.text})),
      button("Bỏ gợi ý",()=>{if(list.children.length>1)row.remove();},{className:"secondary"}));
    list.append(row);
  };
  (question.hints||[{offsetMs:0,text:""}]).forEach(add);
  return h("section",{className:"clue-authoring"},notice("1–20 gợi ý, mốc tăng dần và không trùng. Mốc0 mở ngay; mọi mốc phải nhỏ hơn thời lượng câu khi cấu hình màn."),
    list,button("+ Thêm gợi ý",()=>{if(list.children.length<20)add({offsetMs:1000*(Number(list.lastChild.querySelector('[name="hintSeconds"]').value)+1),text:""});},{className:"secondary add-hint"}));
}
export function readClues(row) {
  return [...row.querySelectorAll(".clue-editor")].map(hint=>({
    offsetMs:Number(hint.querySelector('[name="hintSeconds"]').value)*1000,
    text:hint.querySelector('[name="hintText"]').value
  }));
}
export function releasedClues(payload,id=null) {
  const hints=payload?.hints||[];
  return h("section",{className:"released-clues",...(id?{id}:{})},h("h4",{},"Gợi ý đã mở"),
    hints.length?h("ol",{},hints.map(hint=>h("li",{"data-offset-ms":hint.offsetMs},h("small",{},(hint.offsetMs/1000)+" s · "),hint.text))):notice("Chưa có gợi ý được Server mở. Câu vẫn tiếp tục theo giờ chung."));
}
