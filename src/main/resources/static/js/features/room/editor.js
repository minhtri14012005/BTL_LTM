import {h,field,input,select,button,link,notice,heading,values,action} from "/js/shared/dom.js";
import {uuid} from "/js/core/protocol.js";
import {roomValues} from "/js/features/room/validation.js";
import {MODE_LABELS,PLAYABLE_MODES} from "/js/shared/mode-labels.js";
export async function roomEditor(app,id,chosen) {
  const room=id?await app.api.request("GET",`/api/rooms/${id}`):null;
  if(room && (room.hostUserId!==app.user.id || !["DRAFT","WAITING"].includes(room.status)))return notice("Chỉ Host được sửa phòng Draft hoặc Waiting.","error");
  if(room?.configVersion===2 && room.stages.some(s=>!PLAYABLE_MODES.includes(s.mode)))return notice("Chế độ của kế hoạch này chưa có giao diện chơi.","error");
  const legacy=!!room && room.configVersion!==2;
  const first=await app.api.request("GET","/api/quizzes?scope=VISIBLE&size=100");let page=0;
  const catalogue=new Map(first.items.filter(q=>PLAYABLE_MODES.includes(q.mode)).map(q=>[q.id,q]));
  for(const selected of new Set([Number(chosen),room?.quizId,...(room?.stages||[]).map(s=>s.quizId)].filter(Boolean)))if(!catalogue.has(selected)) {
    try {const q=await app.api.request("GET",`/api/quizzes/${selected}`);if(PLAYABLE_MODES.includes(q.mode))catalogue.set(q.id,q);}catch { /* Deleted sources must be replaced; Server still rechecks Start. */ }
  }
  const participation=select("hostParticipation",[["PLAYER","Tham gia chơi"],["SPECTATOR","Quan sát"]],room?.members.find(m=>m.host)?.participation||"SPECTATOR");
  const list=h("div",{id:"stage-list"});let serial=0;const total=h("p",{id:"stage-total",className:"muted"});
  const form=h("form",{id:"room-form",className:"room-editor"},h("section",{className:"card"},h("p",{className:"eyebrow"},"THIẾT LẬP PHÒNG"),h("h2",{},"Không gian của bạn"),h("div",{className:"form-grid"},field("Tên phòng",input("name",{required:true,maxLength:200,value:room?.name||""})),field("Số chỗ chơi",input("maxPlayers",{type:"number",required:true,min:3,max:100,value:room?.maxPlayers||6}),"3–100 người chơi; người quan sát không chiếm chỗ chơi."),field("Vai trò của Host",participation,"Tác giả bất kỳ bộ được chọn chỉ được quan sát."))),h("div",{className:"room-editor-heading"},h("p",{className:"eyebrow"},legacy?"QUIZ V1":"KẾ HOẠCH NHIỀU MÀN"),h("h2",{},"Chọn thử thách cho cả phòng"),h("p",{className:"muted"},"Mỗi chế độ tối đa một màn, tổng tối đa 50 câu. Dùng Lên/Xuống để đổi thứ tự bằng bàn phím hoặc cảm ứng.")),list);
  function choices(mode){return [...catalogue.values()].filter(q=>q.mode===mode).map(q=>[q.id,`${q.title} (${q.questionCount} câu · ${q.ownerUserId===app.user.id?"Của tôi":"Chung"})`]);}
  function authorRule(){const own=[...list.querySelectorAll('[name="quizId"]')].some(p=>catalogue.get(Number(p.value))?.ownerUserId===app.user.id);participation.querySelector('[value="PLAYER"]').disabled=own;if(own)participation.value="SPECTATOR";}
  function refresh(){const rows=[...list.children];rows.forEach((row,i)=>{row.querySelector("legend").textContent=legacy?"Bộ Quiz của phòng cũ":`Màn ${i+1}`;row.querySelector('[data-move="up"]')?.toggleAttribute("disabled",i===0);row.querySelector('[data-move="down"]')?.toggleAttribute("disabled",i===rows.length-1);const kind=row.querySelector('[name="stageMode"]');if(kind)for(const option of kind.options)option.disabled=rows.some(other=>other!==row && other.querySelector('[name="stageMode"]').value===option.value);});const n=rows.reduce((sum,row)=>sum+Number(row.querySelector('[name="questionCount"]')?.value||0),0);total.textContent=legacy?"Phòng cũ giữ luật Quiz v1 và chọn số câu khi Start.":`Tổng ${n}/50 câu · mỗi chế độ dùng một lần`;addButton.disabled=rows.length>=PLAYABLE_MODES.length;authorRule();}
  function add(stage={}) {
    const used=[...list.querySelectorAll('[name="stageMode"]')].map(n=>n.value);const mode=stage.mode||catalogue.get(Number(chosen))?.mode||PLAYABLE_MODES.find(m=>!used.includes(m));
    if(!mode || !PLAYABLE_MODES.includes(mode))return;
    const row=h("fieldset",{className:"card stage-editor"},h("legend",{}));const key=++serial;
    const kind=select("stageMode",PLAYABLE_MODES.map(m=>[m,MODE_LABELS[m]]),mode);kind.id="stage-mode-"+key;
    const picker=select("quizId",choices(mode),String(stage.quizId||Number(chosen)||choices(mode)[0]?.[0]||""));picker.id="stage-quiz-"+key;
    if(!picker.value && picker.options.length)picker.selectedIndex=0;
    const count=input("questionCount",{id:"stage-count-"+key,type:"number",required:true,min:1,max:50,value:stage.questionCount||Math.min(10,catalogue.get(Number(picker.value))?.questionCount||1)});
    const seconds=input("seconds",{id:"stage-seconds-"+key,type:"number",required:true,min:.001,step:.001,value:(stage.questionDurationMs||15000)/1000});
    const hint=h("small",{className:"muted"});
    row.reloadChoices=()=>{const previous=picker.value;picker.replaceChildren(...choices(kind.value).map(([value,title])=>h("option",{value},title)));picker.value=previous;if(!picker.value && picker.options.length)picker.selectedIndex=0;update();};
    function update(){const q=catalogue.get(Number(picker.value));count.max=Math.min(50,q?.questionCount||50);hint.textContent=q?`Có ${q.questionCount} câu; Host chọn ${q.visibility==="PRIVATE"?"bộ riêng của mình":"bộ công khai"}.`:"Chưa có bộ đúng loại. Tạo bộ hoặc tải thêm danh mục.";refresh();}
    picker.onchange=update;count.oninput=refresh;kind.onchange=()=>{picker.replaceChildren(...choices(kind.value).map(([value,title])=>h("option",{value},title)));count.value=Math.min(10,catalogue.get(Number(picker.value))?.questionCount||1);update();};
    if(!legacy)row.append(field("Chế độ",kind));else kind.hidden=true;
    row.append(field("Bộ câu hỏi",picker),hint);if(!legacy)row.append(field("Số câu của màn",count));row.append(field("Thời gian trả lời (giây)",seconds,"Lớn hơn 0; bước 0,001 giây. Server kiểm tra các mốc gợi ý nếu có."));
    if(!legacy)row.append(h("div",{className:"actions"},button("↑ Lên",()=>{if(row.previousElementSibling)list.insertBefore(row,row.previousElementSibling);refresh();},{"data-move":"up",className:"secondary"}),button("↓ Xuống",()=>{if(row.nextElementSibling)list.insertBefore(row.nextElementSibling,row);refresh();},{"data-move":"down",className:"secondary"}),button("Bỏ màn",()=>{if(list.children.length>1){row.remove();refresh();}},{className:"danger text-button"})));
    list.append(row);update();
  }
  const addButton=button("+ Thêm màn",()=>{const used=[...list.querySelectorAll('[name="stageMode"]')].map(n=>n.value);add({mode:PLAYABLE_MODES.find(m=>!used.includes(m))});},{id:"add-stage",className:"secondary",hidden:legacy});
  if(legacy)add({mode:"QUIZ",quizId:room.quizId,questionDurationMs:room.questionDurationMs});
  else if(room?.stages.length)room.stages.forEach(add);else add({mode:catalogue.get(Number(chosen))?.mode||"QUIZ",quizId:Number(chosen)||undefined});
  const more=button("Tải thêm bộ câu hỏi",async()=>{more.disabled=true;try{const next=await app.api.request("GET",`/api/quizzes?scope=VISIBLE&page=${page+1}&size=100`);page++;next.items.filter(q=>PLAYABLE_MODES.includes(q.mode)).forEach(q=>catalogue.set(q.id,q));[...list.children].forEach(row=>row.reloadChoices());more.hidden=(page+1)*next.size>=next.totalElements;}catch(e){form.append(notice(e.message,"error"));}finally{more.disabled=false;}},{className:"secondary",hidden:first.totalElements<=100});
  form.append(h("div",{className:"actions"},addButton,more),total,h("div",{className:"actions"},h("button",{type:"submit",id:"save-room"},id?"Lưu cấu hình":"Tạo Draft"),link("Hủy",id?`room/${id}`:"rooms","button secondary")));
  form.onsubmit=e=>{e.preventDefault();let body;action(app,form,()=>{if(!body){const data=values(form);const stages=legacy?null:[...list.children].map(row=>({mode:row.querySelector('[name="stageMode"]').value,quizId:row.querySelector('[name="quizId"]').value,questionCount:row.querySelector('[name="questionCount"]').value,seconds:row.querySelector('[name="seconds"]').value}));body={requestId:uuid(),...(id?{revision:room.revision}:{}),config:roomValues(data,stages)};}return app.api.request(id?"PUT":"POST",id?`/api/rooms/${id}`:"/api/rooms",body);},saved=>{app.adoptRoom(saved);app.navigate(`room/${saved.id}`);});};
  return h("section",{className:"room-editor-page"},heading(id?"Chỉnh sửa phòng":"Tạo phòng","Xếp thứ tự màn, chọn số câu và thời gian; lưu Draft rồi mở mã mời."),form);
}
