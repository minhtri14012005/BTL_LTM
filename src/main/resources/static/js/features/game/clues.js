import {h,notice} from "/js/shared/dom.js";
export function releasedClues(payload,id=null) {
  const hints=payload?.hints||[];
  return h("section",{className:"released-clues",...(id?{id}:{})},h("div",{className:"card-top"},h("h4",{},"Gợi ý đã mở"),h("span",{className:"badge"},`${hints.length} gợi ý`)),
    hints.length?h("ol",{"aria-live":"polite","aria-label":"Gợi ý đã được công bố"},hints.map((hint,i)=>h("li",{"data-offset-ms":hint.offsetMs},h("span",{className:"clue-number","aria-hidden":"true"},i+1),h("div",{},h("small",{},(hint.offsetMs/1000)+" s"),h("p",{},hint.text))))):notice("Chưa có gợi ý được Server mở. Câu vẫn tiếp tục theo giờ chung."));
}
