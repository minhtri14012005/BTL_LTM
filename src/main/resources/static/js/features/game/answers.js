import {h,button,notice,field} from "/js/shared/dom.js";
import {outcomes} from "/js/features/game/state.js";
import {publishedResult} from "/js/features/game/view.js";

/** Render only. Controller owns drafts, transport, retry and all state. */
export function quizAnswers(s,{policy,selected,present,animateIndex,remaining,onSelect,onSubmit}) {
  const p=s.player,published=publishedResult(s),options=h("div",{className:"answer-options","aria-label":"Chọn một đáp án"});
  for(const k of ["A","B","C","D"]){
    const correct=published && !!s.question.correctAnswer && k===s.question.correctAnswer;
    const wrong=published && !!s.question.correctAnswer && p?.selectedOption===k && !correct;
    const pulse=present && animateIndex===s.questionIndex && (correct||wrong);
    const mark=correct?" · Đáp án đúng":wrong?" · Bạn chọn sai":"";
    const active=selected===k||p?.selectedOption===k;
    const content=s.question.options?.[k]??"Không có nội dung đáp án";
    const node=button("",()=>onSelect(k),{id:`option-${k}`,"data-option":k,"aria-label":`${k}. ${content}${mark}`,disabled:!policy.answer,className:"secondary "+(correct?"answer-correct ":wrong?"answer-wrong ":active?"selected ":"")+(pulse?"result-pulse":""),"aria-pressed":active});
    node.append(h("span",{className:"option-key","aria-hidden":"true"},k),h("span",{},content+mark));
    if(pulse)node.style.animationDelay="-"+Math.max(0,1500-remaining())+"ms";
    options.append(node);
  }
  const box=h("div",{className:"quiz-answer"},options);
  if(!s.question.options || ["A","B","C","D"].some(k=>s.question.options[k]==null))box.prepend(notice("Nội dung đáp án chưa đầy đủ. Hãy đồng bộ lại trạng thái trận.","error"));
  if(s.phase==="QUESTION_OPEN"){
    if(!p?.alreadyAnswered && p?.state==="PLAYING")box.append(button("Gửi đáp án",onSubmit,{id:"submit-answer",disabled:!policy.answer || !selected}));
    if(p?.alreadyAnswered)box.append(acceptedAnswer());
  }
  return box;
}
export function acceptedAnswer(){return h("p",{className:"notice info",id:"answer-accepted",role:"status"},"Đã gửi đáp án. Đang chờ kết quả…");}
export function textAnswer(s,{policy,textDraft,userId,onInput,onSubmit}) {
  const p=s.player,result=publishedResult(s)?s.results.find(r=>r.userId===userId):null;
  const answer=h("textarea",{id:"answer-text",name:"answerText",rows:3,maxLength:300,placeholder:"Nhập đáp án của bạn…",value:p?.submittedAnswer?.text??textDraft,disabled:!policy.answer,className:result?(result.outcome==="CORRECT"?"answer-correct":result.outcome==="WRONG"?"answer-wrong":""):""});
  answer.oninput=()=>onInput(answer.value);
  const box=h("div",{className:"text-answer"});
  if(p)box.append(field("Đáp án của bạn",answer,"Giữ dấu tiếng Việt. Bấm Gửi đáp án để xác nhận; Enter xuống dòng."));
  if(s.phase==="QUESTION_OPEN" && p?.state==="PLAYING"){
    if(p.alreadyAnswered)box.append(acceptedAnswer());
    else box.append(button("Gửi đáp án",onSubmit,{id:"submit-answer",disabled:!policy.answer || !textDraft.trim()}));
  }
  if(result)box.append(notice(outcomes[result.outcome],result.outcome==="CORRECT"?"success":result.outcome==="WRONG"?"error":"info"));
  if(publishedResult(s) && s.question.payload?.acceptedAnswers)box.append(h("p",{id:"correct-text"},"Đáp án được chấp nhận: "+s.question.payload.acceptedAnswers.join(" / ")));
  return box;
}
