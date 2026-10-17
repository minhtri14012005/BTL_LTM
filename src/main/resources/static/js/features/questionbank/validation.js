import {OPTIONS,PLAYABLE_MODES} from "/js/shared/mode-labels.js";
export function ownerQuestions(quiz, user) { return quiz.ownerUserId === user?.id && Array.isArray(quiz.questions) ? quiz.questions : null; }
export function quizValues(title, visibility, questions, mode="QUIZ") {
  if (!title.trim() || title.length > 200) throw new Error("Tên bộ câu hỏi cần 1–200 ký tự.");
  if (!["PUBLIC","PRIVATE"].includes(visibility) || questions.length < 1 || questions.length > 50) throw new Error("Cần 1–50 câu và quyền hiển thị hợp lệ.");
  if(!PLAYABLE_MODES.includes(mode))throw new Error("Chế độ này chưa hỗ trợ biên soạn.");
  questions.forEach((q, i) => {
    if(mode==="VIETNAMESE_PUZZLE" || mode==="ORDERING") {
      const items=q[mode==="VIETNAMESE_PUZZLE"?"pieces":"items"];
      if(!q.content.trim() || q.content.length>5000 || !Array.isArray(items) || items.length<2 || items.length>100 || items.some(v=>!v || typeof v.text!=="string" || !v.text.length || v.text.length>300 || mode==="ORDERING"&&!v.text.trim() || !/^[A-Za-z0-9_-]{1,64}$/.test(v.id)) || new Set(items.map(v=>v.id)).size!==items.length || !Array.isArray(q.correctOrder) || q.correctOrder.length!==items.length || new Set(q.correctOrder).size!==items.length || q.correctOrder.some(id=>!items.some(v=>v.id===id)))throw new Error(`Câu ${i+1}: cần2–100 mảnh / mục có ID riêng và đủ thứ tự đúng.`);
      if(mode==="VIETNAMESE_PUZZLE" && (!Array.isArray(q.acceptedAnswers)||q.acceptedAnswers.length<1||q.acceptedAnswers.length>20||q.acceptedAnswers.some(a=>typeof a!=="string"||!a.trim()||a.length>300)))throw new Error("Vua Tiếng Việt cần1–20 đáp án chữ được chấp nhận.");
      return;
    }
    if(mode==="CLUES") {
      if(!Array.isArray(q.hints)||q.hints.length<1||q.hints.length>20||q.hints.some((h,j)=>!h||!Number.isSafeInteger(h.offsetMs)||h.offsetMs<0||j>0&&h.offsetMs<=q.hints[j-1].offsetMs||typeof h.text!=="string"||!h.text.trim()||h.text.length>2000))throw new Error("Câu "+(i+1)+": cần1–20 gợi ý với mốc tăng dần, không trùng và nội dung tối đa2000 ký tự.");
    }
    if(mode==="CLUES" || mode==="RIDDLE" || mode==="IMAGE_WORD" || mode==="SONG") {if(mode==="SONG" && !/^sha256:[0-9a-f]{64}$/.test(q.mediaRef||""))throw new Error(`Câu ${i+1}: cần upload video có hình và tiếng.`);if(mode==="IMAGE_WORD" && !/^sha256:[0-9a-f]{64}$/.test(q.imageRef||""))throw new Error(`Câu ${i+1}: cần upload một ảnh gợi từ/cụm từ.`);if(!q.content.trim() || q.content.length>5000 || !Array.isArray(q.acceptedAnswers) || q.acceptedAnswers.length<1 || q.acceptedAnswers.length>20 || q.acceptedAnswers.some(a=>typeof a!=="string" || !a.trim() || a.length>300))throw new Error(`Câu ${i+1}: nhập câu đố và 1–20 đáp án, mỗi đáp án tối đa300 ký tự.`);return;}
    if (!q.content.trim() || q.content.length > 5000 || Object.keys(q.options).length !== 4 ||
      OPTIONS.some(k => !q.options[k]?.trim() || q.options[k].length > 2000) || !OPTIONS.includes(q.correctAnswer)) throw new Error(`Câu ${i+1}: nhập nội dung, đủ 4 lựa chọn và chọn một đáp án đúng.`);
  });
  if(mode==="VIETNAMESE_PUZZLE" || mode==="ORDERING")return {title:title.trim(),visibility,mode,questions:questions.map(q=>({content:q.content.trim(),[mode==="VIETNAMESE_PUZZLE"?"pieces":"items"]:q[mode==="VIETNAMESE_PUZZLE"?"pieces":"items"].map(v=>({...v})),correctOrder:[...q.correctOrder],...(mode==="VIETNAMESE_PUZZLE"?{acceptedAnswers:q.acceptedAnswers.map(a=>a.trim())}:{})}))};
  if(mode==="CLUES" || mode==="RIDDLE" || mode==="IMAGE_WORD" || mode==="SONG")return {title:title.trim(),visibility,mode,questions:questions.map(q=>({content:q.content.trim(),acceptedAnswers:q.acceptedAnswers.map(a=>a.trim()),...(mode==="CLUES"?{hints:q.hints.map(h=>({offsetMs:h.offsetMs,text:h.text.trim()}))}:mode==="IMAGE_WORD"?{imageRef:q.imageRef}:mode==="SONG"?{mediaRef:q.mediaRef}:{})}))};
  return {title:title.trim(), visibility, mode, questions:questions.map(q => ({content:q.content.trim(), options:Object.fromEntries(OPTIONS.map(k => [k,q.options[k].trim()])), correctAnswer:q.correctAnswer, imageRef:q.imageRef || null}))};
}
