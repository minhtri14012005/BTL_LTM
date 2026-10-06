import {h,field,input,select,button,link,notice,heading,values,action} from "./dom.js";
import {OPTIONS,ownerQuestions,quizValues} from "./core.js";

export async function quizList(app,page=0) {
  const result=await app.api.request("GET",`/api/quizzes?page=${page}&size=12`);
  return h("section",{},heading("Bộ câu hỏi","Bộ công khai và các bộ riêng do bạn sở hữu.",link("Tạo bộ câu hỏi","quizzes/new","button")),
    result.items.length?h("div",{className:"card-grid"},result.items.map(q => h("article",{className:"card quiz-card"},h("div",{className:"card-top"},h("span",{className:"badge"},q.visibility==="PUBLIC"?"Công khai":"Riêng tư"),h("span",{className:"muted"},q.ownerUserId===app.user.id?"Của bạn":`Tác giả #${q.ownerUserId}`)),h("h2",{},link(q.title,`quiz/${q.id}`)),h("p",{className:"muted"},`${q.questionCount} câu hỏi`),h("div",{className:"actions"},link("Xem chi tiết",`quiz/${q.id}`,"button secondary"),link("Dùng để tạo phòng",`rooms/new?quiz=${q.id}`,"text-link"))))):notice("Chưa có bộ câu hỏi nào để hiển thị."),
    h("div",{className:"pagination"},page>0?link("← Trang trước",`quizzes?page=${page-1}`,"button secondary"):null,h("span",{},`Trang ${page+1}`),(page+1)*result.size<result.totalElements?link("Trang sau →",`quizzes?page=${page+1}`,"button secondary"):null));
}
export async function quizDetail(app,id) {
  const quiz=await app.api.request("GET",`/api/quizzes/${id}`);const questions=ownerQuestions(quiz,app.user);
  const panel=h("section",{},heading(quiz.title,`${quiz.questionCount} câu · ${quiz.visibility==="PUBLIC"?"Công khai":"Riêng tư"}`,link("Tạo phòng với bộ này",`rooms/new?quiz=${id}`,"button")),
    questions?notice("Bạn là tác giả. Nội dung và đáp án dưới đây chỉ hiển thị với bạn."):notice("Bạn có thể dùng bộ công khai này để mở phòng. Nội dung và đáp án thuộc quyền xem của tác giả."));
  if(questions) {
    const controls=h("div",{className:"actions"},link("Chỉnh sửa",`quiz/${id}/edit`,"button secondary"));
    const confirm=h("div",{className:"card delete-confirm",hidden:true},notice("Xóa bộ câu hỏi này?"),button("Giữ lại",()=>confirm.hidden=true,{className:"secondary"}));
    confirm.append(button("Xác nhận xóa",() => action(app,confirm,async () => {
      try{return await app.api.request("DELETE",`/api/quizzes/${id}?revision=${quiz.revision}`);}catch(e){e.retryable=false;throw e;}
    },() => {app.flash="Đã xóa bộ câu hỏi.";app.navigate("quizzes");}),{className:"danger",id:"confirm-delete-quiz"}));
    controls.append(button("Xóa bộ câu hỏi",()=>confirm.hidden=false,{className:"danger secondary",id:"delete-quiz"}));panel.append(controls,confirm);
    for(const q of questions) panel.append(h("article",{className:"card question-preview"},h("h2",{},`${q.position}. ${q.content}`),q.imageRef?h("img",{className:"question-image",alt:"Ảnh câu hỏi",src:imageUrl(id,q.imageRef)}):null,h("ul",{className:"options"},OPTIONS.map(k => h("li",{className:k===q.correctAnswer?"correct":""},`${k}. ${q.options[k]}`,k===q.correctAnswer?h("span",{className:"badge"},"Đáp án đúng"):null)))));
  }
  return panel;
}
function imageUrl(id,ref) { return `/api/quizzes/${id}/images/${ref.slice(7)}`; }
export async function quizEditor(app,id) {
  const quiz=id?await app.api.request("GET",`/api/quizzes/${id}`):null;
  if(quiz && !ownerQuestions(quiz,app.user)) return notice("Chỉ tác giả được chỉnh sửa bộ câu hỏi này.","error");
  const list=h("div",{id:"question-list"});const count=h("span",{className:"muted"});
  const title=input("title",{required:true,maxLength:200,value:quiz?.title||""});const visibility=select("visibility",[["PUBLIC","Công khai"],["PRIVATE","Riêng tư"]],quiz?.visibility);
  const form=h("form",{id:"quiz-form"},h("div",{className:"card form-grid"},field("Tên bộ câu hỏi",title),field("Quyền hiển thị",visibility)),list);
  const renumber=() => {list.querySelectorAll("legend").forEach((n,i) => n.textContent=`Câu ${i+1}`);count.textContent=`${list.children.length}/50 câu`;};
  const add=(q={content:"",options:{A:"",B:"",C:"",D:""},correctAnswer:"",imageRef:null}) => {
    if(list.children.length>=50) return;
    const row=h("fieldset",{className:"card question-editor"},h("legend",{}));row.dataset.imageRef=q.imageRef||"";
    row.append(field("Nội dung câu hỏi",h("textarea",{name:"content",required:true,maxLength:5000,value:q.content,rows:3})),
      h("div",{className:"option-grid"},OPTIONS.map(k => field(`Lựa chọn ${k}`,h("textarea",{name:`option${k}`,required:true,maxLength:2000,value:q.options[k],rows:2})))),
      field("Đáp án đúng",select("correctAnswer",[["","Chọn một đáp án"],...OPTIONS.map(k => [k,k])],q.correctAnswer)));
    if(id) {
      const imageBox=h("div",{className:"image-box"});const file=input("image",{type:"file",accept:"image/png,image/jpeg"});
      const showImage=() => {imageBox.replaceChildren(row.dataset.imageRef?h("img",{src:imageUrl(id,row.dataset.imageRef),alt:"Ảnh câu hỏi",className:"question-image"}):h("small",{className:"muted"},"Chưa có ảnh."));};showImage();
      row.append(field("Ảnh tùy chọn",file,"PNG hoặc JPEG, tối đa 2 MiB; lưu cùng thay đổi."),imageBox,button("Bỏ ảnh",()=>{row.dataset.imageRef="";file.value="";showImage();},{className:"secondary"}));
    } else row.append(h("small",{className:"muted"},"Sau khi lưu, bạn có thể thêm ảnh cho từng câu ở màn chỉnh sửa."));
    row.append(button("Xóa câu này",()=>{if(list.children.length>1){row.remove();renumber();}},{className:"text-button danger"}));list.append(row);renumber();
  };
  (quiz?.questions || [undefined]).forEach(q => add(q));
  const addButton=button("+ Thêm câu hỏi",()=>add(),{className:"secondary",id:"add-question"});
  form.append(h("div",{className:"editor-toolbar"},addButton,count),h("div",{className:"actions"},h("button",{type:"submit",id:"save-quiz"},id?"Lưu thay đổi":"Tạo bộ câu hỏi"),link("Hủy",id?`quiz/${id}`:"quizzes","button secondary")));
  form.addEventListener("submit",event => {
    event.preventDefault();let body;let uploads;
    action(app,form,async () => {
      if(!body) {
        const rows=[...list.children];const data=rows.map(r => ({content:r.querySelector('[name="content"]').value,options:Object.fromEntries(OPTIONS.map(k => [k,r.querySelector(`[name="option${k}"]`).value])),correctAnswer:r.querySelector('[name="correctAnswer"]').value,imageRef:r.dataset.imageRef||null}));
        body=quizValues(title.value,visibility.value,data);uploads=rows.map(r => r.querySelector('[name="image"]')?.files[0]);
        for(const file of uploads) if(file && (file.size>2*1024*1024 || !["image/png","image/jpeg"].includes(file.type))) throw new Error("Ảnh phải là PNG/JPEG và không vượt 2 MiB.");
      }
      try {
        for(let i=0;i<uploads.length;i++) if(uploads[i]) {const file=new FormData();file.append("file",uploads[i]);const image=await app.api.request("POST",`/api/quizzes/${id}/images`,file);body.questions[i].imageRef=image.imageRef;uploads[i]=null;}
        return await app.api.request(id?"PUT":"POST",id?`/api/quizzes/${id}`:"/api/quizzes",id?{...body,revision:quiz.revision}:body);
      } catch(error) {error.retryable=false;if(error.code==="REQUEST_UNCERTAIN") error.message="Chưa có xác nhận lưu. Mở danh sách hoặc tải lại bộ này để kiểm tra trước khi gửi lại.";throw error;}
    },saved=>{app.flash=id?"Đã lưu bộ câu hỏi.":"Đã tạo bộ câu hỏi. Bạn có thể thêm ảnh khi chỉnh sửa.";app.navigate(id?`quiz/${saved.id}`:`quiz/${saved.id}/edit`);});
  });
  return h("section",{},heading(id?"Chỉnh sửa bộ câu hỏi":"Tạo bộ câu hỏi","Mỗi câu có đúng 4 lựa chọn và một đáp án đúng. Cần 10–50 câu để bắt đầu trận."),form);
}
