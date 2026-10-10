import {clueEditor,readClues,releasedClues} from "./clues.js";
import {h,field,input,select,button,link,notice,heading,values,action} from "./dom.js";
import {arrangementEditor,readArrangement,isArrangement} from "./arrangement.js";
import {OPTIONS,ownerQuestions,quizValues,MODE_LABELS,PLAYABLE_MODES} from "./core.js";

export async function questionBankList(app,page=0,scope="SHARED",mode="") {
  scope=["MINE","SHARED"].includes(scope)?scope:"SHARED";mode=PLAYABLE_MODES.includes(mode)?mode:"";
  const route=p=>`quizzes?scope=${scope}${mode?"&mode="+mode:""}&page=${p}`;
  const result=await app.api.request("GET",`/api/quizzes?scope=${scope}${mode?"&mode="+mode:""}&page=${page}&size=12`);
  const tabs=h("div",{className:"catalogue-tabs","aria-label":"Nguồn bộ câu hỏi"},
    link("Của tôi","quizzes?scope=MINE"+(mode?"&mode="+mode:""),"catalogue-tab "+(scope==="MINE"?"is-active":"")),
    link("Chung","quizzes?scope=SHARED"+(mode?"&mode="+mode:""),"catalogue-tab "+(scope==="SHARED"?"is-active":"")));
  const filters=h("div",{className:"actions catalogue-filters"},tabs,modeDropdown(scope,mode));
  return h("section",{},heading("Bộ câu hỏi",scope==="MINE"?"Tất cả bộ do bạn tạo, gồm công khai và riêng tư.":"Mọi bộ công khai, gồm cả bộ công khai của bạn.",link("Tạo bộ câu hỏi","quizzes/new","button")),filters,
    result.items.length?h("div",{className:"card-grid"},result.items.map(q => h("article",{className:"card quiz-card"},h("div",{className:"card-top"},h("span",{className:"badge"},q.visibility==="PUBLIC"?"Công khai":"Riêng tư"),h("span",{className:"muted"},q.ownerUserId===app.user.id?"Của bạn":`Tác giả #${q.ownerUserId}`)),h("h2",{},link(q.title,`quiz/${q.id}`)),h("p",{className:"muted"},`${MODE_LABELS[q.mode]||q.mode} · ${q.questionCount} câu hỏi`),h("div",{className:"actions"},link("Xem chi tiết",`quiz/${q.id}`,"button secondary"),PLAYABLE_MODES.includes(q.mode)?link("Dùng để tạo phòng",`rooms/new?quiz=${q.id}`,"text-link"):h("span",{className:"muted"},"Chưa hỗ trợ chơi"))))):notice("Chưa có bộ câu hỏi nào để hiển thị."),
    h("div",{className:"pagination"},page>0?link("← Trang trước",route(page-1),"button secondary"):null,h("span",{},`Trang ${page+1}`),(page+1)*result.size<result.totalElements?link("Trang sau →",route(page+1),"button secondary"):null));
}
function modeDropdown(scope,mode) {
  const choices=[["","Tất cả chế độ"],...PLAYABLE_MODES.map(m=>[m,MODE_LABELS[m]])];
  const icons={"":"✦",QUIZ:"Q",SONG:"♫",VIETNAMESE_PUZZLE:"V",RIDDLE:"?",CLUES:"⌕",IMAGE_WORD:"▧",ORDERING:"↕"};
  const menu=h("details",{className:"mode-filter",id:"filterMode"});
  const summary=h("summary",{className:"mode-filter-trigger","aria-label":"Lọc chế độ: "+(MODE_LABELS[mode]||"Tất cả chế độ")},
    h("span",{className:"mode-filter-value"},MODE_LABELS[mode]||"Tất cả chế độ"),h("span",{className:"mode-filter-chevron","aria-hidden":"true"}));
  const options=choices.map(([value,label])=>h("a",{className:"mode-filter-option"+(value===mode?" is-selected":""),href:`#/quizzes?scope=${scope}${value?"&mode="+value:""}`,"aria-current":value===mode?"true":null,onclick:()=>menu.open=false},
    h("span",{className:"mode-filter-icon","aria-hidden":"true"},icons[value]),
    h("span",{},label),value===mode?h("span",{className:"mode-filter-check","aria-hidden":"true"},"✓"):null));
  menu.append(summary,h("div",{className:"mode-filter-menu"},h("div",{className:"mode-filter-heading"},"CHẾ ĐỘ CHƠI"),options));
  menu.addEventListener("pointerenter",e=>{if(e.pointerType==="mouse")menu.open=true;});
  menu.addEventListener("pointerleave",e=>{if(e.pointerType==="mouse")menu.open=false;});
  menu.addEventListener("focusout",e=>{if(!menu.contains(e.relatedTarget))menu.open=false;});
  menu.addEventListener("keydown",e=>{
    if(e.key==="Escape"){e.preventDefault();menu.open=false;summary.focus();return;}
    const index=options.indexOf(document.activeElement);
    if(["ArrowDown","ArrowUp","Home","End"].includes(e.key)){
      e.preventDefault();menu.open=true;
      const next=e.key==="Home"?0:e.key==="End"?options.length-1:e.key==="ArrowDown"?(index+1)%options.length:(index<0?options.length-1:(index+options.length-1)%options.length);
      options[next].focus();
    }
  });
  return h("div",{className:"catalogue-mode"},h("span",{className:"catalogue-mode-label"},"Lọc chế độ"),menu);
}
export async function questionBankDetail(app,id) {
  const quiz=await app.api.request("GET",`/api/quizzes/${id}`);const questions=ownerQuestions(quiz,app.user);
  const panel=h("section",{},heading(quiz.title,`${MODE_LABELS[quiz.mode]||quiz.mode} · ${quiz.questionCount} câu · ${quiz.visibility==="PUBLIC"?"Công khai":"Riêng tư"}`,PLAYABLE_MODES.includes(quiz.mode)?link("Tạo phòng với bộ này",`rooms/new?quiz=${id}`,"button"):null),
    questions?notice("Bạn là tác giả. Nội dung và đáp án dưới đây chỉ hiển thị với bạn."):notice("Bạn có thể dùng bộ công khai này để mở phòng. Nội dung và đáp án thuộc quyền xem của tác giả."));
  if(questions && PLAYABLE_MODES.includes(quiz.mode)) {
    const controls=h("div",{className:"actions"},link("Chỉnh sửa",`quiz/${id}/edit`,"button secondary"));
    const confirm=h("div",{className:"card delete-confirm",hidden:true},notice("Xóa bộ câu hỏi này?"),button("Giữ lại",()=>confirm.hidden=true,{className:"secondary"}));
    confirm.append(button("Xác nhận xóa",() => action(app,confirm,async () => {
      try{return await app.api.request("DELETE",`/api/quizzes/${id}?revision=${quiz.revision}`);}catch(e){e.retryable=false;throw e;}
    },() => {app.flash="Đã xóa bộ câu hỏi.";app.navigate("quizzes");}),{className:"danger",id:"confirm-delete-quiz"}));
    controls.append(button("Xóa bộ câu hỏi",()=>confirm.hidden=false,{className:"danger secondary",id:"delete-quiz"}));panel.append(controls,confirm);
    for(const q of questions) panel.append(h("article",{className:"card question-preview"},h("h2",{},`${q.position}. ${q.content}`),q.hints?releasedClues({hints:q.hints}):null,q.mediaRef?h("video",{className:"song-video video-preview",controls:true,preload:"metadata",playsInline:true,src:videoOwnerUrl(id,q.mediaRef)}):null,q.imageRef?h("img",{className:"question-image",alt:"Ảnh câu hỏi",src:imageUrl(id,q.imageRef)}):null,(["CLUES","RIDDLE","IMAGE_WORD","SONG","VIETNAMESE_PUZZLE"].includes(quiz.mode))?notice("Đáp án / cách viết được chấp nhận: "+q.acceptedAnswers.join(" / ")):quiz.mode==="ORDERING"?notice("Thứ tự đúng: "+q.correctOrder.map(id=>q.items.find(v=>v.id===id).text).join(" → ")):h("ul",{className:"options"},OPTIONS.map(k => h("li",{className:k===q.correctAnswer?"correct":""},`${k}. ${q.options[k]}`,k===q.correctAnswer?h("span",{className:"badge"},"Đáp án đúng"):null)))));
  }
  return panel;
}
function videoOwnerUrl(id,ref){return `/api/quizzes/${id}/videos/${ref.slice(7)}`;}
function imageUrl(id,ref) { return `/api/quizzes/${id}/images/${ref.slice(7)}`; }
export async function questionBankEditor(app,id) {
  const quiz=id?await app.api.request("GET",`/api/quizzes/${id}`):null;
  if(quiz && !PLAYABLE_MODES.includes(quiz.mode))return notice("Chưa hỗ trợ biên soạn chế độ này.","error");
  if(quiz && !ownerQuestions(quiz,app.user)) return notice("Chỉ tác giả được chỉnh sửa bộ câu hỏi này.","error");
  const list=h("div",{id:"question-list"});const count=h("span",{className:"muted"});
  const title=input("title",{required:true,maxLength:200,value:quiz?.title||""});const visibility=select("visibility",[["PUBLIC","Công khai"],["PRIVATE","Riêng tư"]],quiz?.visibility);
  const mode=select("mode",PLAYABLE_MODES.map(m=>[m,MODE_LABELS[m]]),quiz?.mode||"QUIZ");mode.disabled=!!quiz;
  const form=h("form",{id:"quiz-form"},h("div",{className:"card form-grid"},field("Tên bộ câu hỏi",title),field("Quyền hiển thị",visibility),field("Chế độ",mode,id?"Chế độ cố định sau khi tạo.":"Quiz chọn4 đáp án; Đố mẹo/Đuổi hình/Đoán bài hát/Dấu vết nhập chữ; Vua Tiếng Việt/Trình tự sắp xếp các mục.")),list);
  const renumber=() => {list.querySelectorAll("legend").forEach((n,i) => n.textContent=`Câu ${i+1}`);count.textContent=`${list.children.length}/50 câu`;};
  const add=(q={content:"",options:{A:"",B:"",C:"",D:""},correctAnswer:"",imageRef:null}) => {
    if(list.children.length>=50) return;
    const row=h("fieldset",{className:"card question-editor"},h("legend",{}));row.dataset.imageRef=q.imageRef||"";row.dataset.mediaRef=q.mediaRef||"";
    row.append(field("Nội dung câu hỏi",h("textarea",{name:"content",required:true,maxLength:5000,value:q.content,rows:3})));
    if(mode.value==="CLUES")row.append(clueEditor(q));
    if(isArrangement(mode.value))row.append(arrangementEditor(q,mode.value));
    if(["CLUES","RIDDLE","IMAGE_WORD","SONG","VIETNAMESE_PUZZLE"].includes(mode.value))row.append(field("Đáp án được chấp nhận",h("textarea",{name:"acceptedAnswers",required:true,value:(q.acceptedAnswers||[]).join("\n"),rows:3}),"Mỗi dòng một đáp án; 1–20 dòng, tối đa300 ký tự/dòng. Giữ dấu tiếng Việt; không phân biệt hoa/thường, gộp khoảng trắng."));
    else if(mode.value==="QUIZ")row.append(h("div",{className:"option-grid"},OPTIONS.map(k => field(`Lựa chọn ${k}`,h("textarea",{name:`option${k}`,required:true,maxLength:2000,value:q.options?.[k]||"",rows:2})))),
      field("Đáp án đúng",select("correctAnswer",[["","Chọn một đáp án"],...OPTIONS.map(k => [k,k])],q.correctAnswer)));
    if(mode.value==="SONG") {
      const preview=h("div",{className:"video-box"}),file=input("video",{type:"file",accept:"video/mp4,.mp4"});
      const show=()=>preview.replaceChildren(row.dataset.mediaRef?h("video",{className:"song-video video-preview",controls:true,preload:"metadata",playsInline:true,src:row.dataset.draftVideo==="true" || !id?'/api/quizzes/videos/drafts/'+row.dataset.mediaRef.slice(7):videoOwnerUrl(id,row.dataset.mediaRef)}):h("small",{className:"muted"},"Chưa có video."));show();
      file.onchange=()=>{const selected=file.files[0];if(!selected){show();return;}if(selected.size>20*1024*1024){preview.replaceChildren(notice("Video tối đa20 MiB.","error"));return;}const reader=new FileReader();reader.onload=()=>{if(file.files[0]===selected)preview.replaceChildren(h("video",{className:"song-video video-preview",controls:true,preload:"metadata",playsInline:true,src:reader.result}));};reader.readAsDataURL(selected);};
      row.append(field("Video đã cắt, có hình và tiếng (bắt buộc)",file,"MP4 H.264/AAC-LC, tối đa20 MiB/120 giây/1080p; Server kiểm tra khi lưu."),preview,button("Bỏ video",()=>{row.dataset.mediaRef="";file.value="";show();},{className:"secondary"}));
    }
    if(mode.value==="IMAGE_WORD" || id && mode.value==="QUIZ") {
      const imageBox=h("div",{className:"image-box"});const file=input("image",{type:"file",accept:"image/png,image/jpeg"});
      const showImage=() => {imageBox.replaceChildren(row.dataset.imageRef?h("img",{src:row.dataset.draftImage==="true" || !id?`/api/quizzes/images/drafts/${row.dataset.imageRef.slice(7)}`:imageUrl(id,row.dataset.imageRef),alt:"Ảnh câu hỏi",className:"question-image"}):h("small",{className:"muted"},"Chưa có ảnh."));};showImage();
      file.onchange=()=>{const selected=file.files[0];if(!selected){showImage();return;}const reader=new FileReader();reader.onload=()=>{if(file.files[0]===selected)imageBox.replaceChildren(h("img",{src:reader.result,alt:"Xem trước ảnh đã chọn",className:"question-image"}));};reader.readAsDataURL(selected);};
      row.append(field(mode.value==="IMAGE_WORD"?"Ảnh gợi từ/cụm từ (bắt buộc)":"Ảnh tùy chọn",file,"PNG hoặc JPEG, tối đa 2 MiB; lưu cùng thay đổi."),imageBox,button("Bỏ ảnh",()=>{row.dataset.imageRef="";file.value="";showImage();},{className:"secondary"}));
    } else if(mode.value==="QUIZ")row.append(h("small",{className:"muted"},"Sau khi lưu, bạn có thể thêm ảnh cho từng câu ở màn chỉnh sửa."));
    row.append(button("Xóa câu này",()=>{if(list.children.length>1){row.remove();renumber();}},{className:"text-button danger"}));list.append(row);renumber();
  };
  (quiz?.questions || [undefined]).forEach(q => add(q));
  let previousMode=mode.value;
  mode.onchange=()=>{if(list.children.length && !window.confirm("Đổi chế độ sẽ thay các câu đang soạn. Tiếp tục?")){mode.value=previousMode;return;}previousMode=mode.value;list.replaceChildren();add();};
  const addButton=button("+ Thêm câu hỏi",()=>add(),{className:"secondary",id:"add-question"});
  form.append(h("div",{className:"editor-toolbar"},addButton,count),h("div",{className:"actions"},h("button",{type:"submit",id:"save-quiz"},id?"Lưu thay đổi":"Tạo bộ câu hỏi"),link("Hủy",id?`quiz/${id}`:"quizzes","button secondary")));
  form.addEventListener("submit",event => {
    event.preventDefault();let body;let uploads;
    action(app,form,async () => {
      if(!body) {
        const rows=[...list.children];const data=rows.map(r => isArrangement(mode.value)?({content:r.querySelector('[name="content"]').value,...readArrangement(r,mode.value),...(mode.value==="VIETNAMESE_PUZZLE"?{acceptedAnswers:r.querySelector('[name="acceptedAnswers"]').value.split(/\r?\n/).filter(a=>a.trim())}:{})}):(mode.value==="CLUES" || mode.value==="RIDDLE" || mode.value==="IMAGE_WORD" || mode.value==="SONG")?({...(mode.value==="CLUES"?{hints:readClues(r)}:{}),mediaRef:r.dataset.mediaRef||null,imageRef:r.dataset.imageRef||null,content:r.querySelector('[name="content"]').value,acceptedAnswers:r.querySelector('[name="acceptedAnswers"]').value.split(/\r?\n/).filter(a=>a.trim())}):({content:r.querySelector('[name="content"]').value,options:Object.fromEntries(OPTIONS.map(k => [k,r.querySelector(`[name="option${k}"]`).value])),correctAnswer:r.querySelector('[name="correctAnswer"]').value,imageRef:r.dataset.imageRef||null}));
        const videos=rows.map(r=>r.querySelector('[name="video"]')?.files[0]);
        for(const file of videos)if(file && (file.size>20*1024*1024 || !file.name.toLowerCase().endsWith(".mp4")))throw new Error("Chọn MP4 H.264/AAC-LC, tối đa20 MiB.");
        for(let i=0;i<videos.length;i++)if(videos[i]){
          const upload=new FormData();upload.append("file",videos[i]);
          const media=await app.api.request("POST",id?'/api/quizzes/'+id+'/videos':'/api/quizzes/videos/drafts',upload);
          data[i].mediaRef=media.mediaRef;rows[i].dataset.mediaRef=media.mediaRef;rows[i].dataset.draftVideo=String(!id);rows[i].querySelector('[name="video"]').value="";
        }
        uploads=rows.map(r => r.querySelector('[name="image"]')?.files[0]);
        for(const file of uploads) if(file && (file.size>2*1024*1024 || !["image/png","image/jpeg"].includes(file.type))) throw new Error("Ảnh phải là PNG/JPEG và không vượt 2 MiB.");
        for(let i=0;i<uploads.length;i++) if(uploads[i]) {
          const file=new FormData();file.append("file",uploads[i]);
          const image=await app.api.request("POST",id?`/api/quizzes/${id}/images`:"/api/quizzes/images/drafts",file);
          data[i].imageRef=image.imageRef;rows[i].dataset.imageRef=image.imageRef;rows[i].dataset.draftImage=String(!id);rows[i].querySelector('[name="image"]').value="";
        }
        body=quizValues(title.value,visibility.value,data,mode.value);
      }
      try {

        return await app.api.request(id?"PUT":"POST",id?`/api/quizzes/${id}`:"/api/quizzes",id?{...body,revision:quiz.revision}:body);
      } catch(error) {error.retryable=false;if(error.code==="REQUEST_UNCERTAIN") error.message="Chưa có xác nhận lưu. Mở danh sách hoặc tải lại bộ này để kiểm tra trước khi gửi lại.";throw error;}
    },saved=>{app.flash=id?"Đã lưu bộ câu hỏi.":mode.value==="QUIZ"?"Đã tạo bộ câu hỏi. Bạn có thể thêm ảnh khi chỉnh sửa.":"Đã tạo bộ "+MODE_LABELS[mode.value]+".";app.navigate(id?`quiz/${saved.id}`:`quiz/${saved.id}/edit`);});
  });
  return h("section",{},heading(id?"Chỉnh sửa bộ câu hỏi":"Tạo bộ câu hỏi","Biên soạn Quiz, Đố mẹo, Đuổi hình, Đoán bài hát, Truy tìm dấu vết, Vua Tiếng Việt hoặc Sắp xếp trình tự. Trận nhiều màn chọn1–50 câu tổng cộng; phòng Quiz cũ vẫn cần10–50 câu."),form);
}
