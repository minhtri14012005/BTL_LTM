import {h,button,notice} from "./dom.js";
export function videoUrl(s,q,index=s.questionIndex) {
  const source=s.stages?.find(stage=>index>=stage.firstQuestionIndex && index<stage.firstQuestionIndex+stage.questionCount)?.sourceQuizId;
  return source && q?.payload?.mediaRef ? '/api/quizzes/'+source+'/videos/'+q.payload.mediaRef.slice(7)+'?gameSessionId='+s.gameSessionId : null;
}
export function videoPosition(openedAtMs,serverTimeMs,duration) {
  return Math.max(0,Math.min(Number.isFinite(duration)?Math.max(0,duration-0.02):Infinity,(serverTimeMs-openedAtMs)/1000));
}
export function historyVideo(s,q) {
  if(q.mode!=="SONG")return null;
  const video=h("video",{className:"song-video",controls:true,preload:"metadata",playsInline:true,src:videoUrl(s,q,q.questionIndex)}),box=h("div",{},video);
  video.addEventListener("error",()=>box.append(notice("Không tải/phát được video đã lưu. Kết quả lịch sử vẫn được giữ.","error")),{once:true});return box;
}
/** One DOM video per question; ACK, board toggle and reconnect never create another player. */
export function songVideo(s,serverNow,memory) {
  const video=h("video",{id:"song-video",className:"song-video",preload:"auto",playsInline:true,muted:true}),status=h("p",{id:"video-status",className:"muted","aria-live":"polite"});
  let current=s,disposed=false,failed=false,starting=false,blocked=false;
  video.muted=!memory.soundEnabled;
  const play=button("Bật tiếng / Phát tại thời điểm hiện tại",()=>{memory.soundEnabled=true;blocked=false;video.muted=false;start();},{id:"enable-video-sound",className:"secondary"});
  const box=h("div",{className:"song-player"},video,play,status);
  const open=()=>current.phase==="QUESTION_OPEN" && current.status==="ACTIVE" && current.deadlineEpochMs>serverNow();
  function align(){if(video.readyState>=1){const t=videoPosition(current.question.payload.openedAtMs,serverNow(),video.duration);if(Math.abs(video.currentTime-t)>0.65)try{video.currentTime=t;}catch{/* Seekable metadata may still be arriving. */}}}
  function start(){
    if(disposed||failed||starting||!open())return;align();starting=true;
    Promise.resolve(video.play()).then(()=>{if(!disposed){blocked=false;status.textContent=video.muted?"Video đang phát tắt tiếng. Bấm Bật tiếng để nghe; thời gian vẫn chạy theo Server.":"Video đang phát theo thời gian câu của Server.";}}).catch(error=>{if(!disposed){blocked=error.name!=="AbortError";status.textContent=blocked?"Trình duyệt chưa cho phát. Bấm Bật tiếng / Phát; bạn vẫn có thể nhập đáp án.":"Đang tải video tại thời điểm hiện tại của câu…";}}).finally(()=>{starting=false;});
  }
  function sync(){
    if(disposed)return;play.disabled=failed||!open();
    if(!open()){video.pause();return;}
    if(video.readyState>=1 && (serverNow()-current.question.payload.openedAtMs)/1000>=video.duration-0.05){video.pause();status.textContent="Video đã hết; bạn vẫn có thể gửi đáp án trước hạn câu.";return;}
    align();if(video.readyState>=2 && video.paused && !video.ended && !blocked)start();
  }
  video.addEventListener("loadedmetadata",()=>{align();start();});
  video.addEventListener("canplay",sync);
  video.addEventListener("error",()=>{failed=true;video.pause();play.disabled=true;status.textContent="Không tải/phát được video. Câu vẫn tiếp tục theo giờ Server; bạn có thể nhập đáp án phỏng đoán.";status.className="notice error";});
  video.src=videoUrl(s,s.question);
  return {box,key:s.question.id,update(next){current=next;sync();},sync,dispose(){disposed=true;video.pause();video.removeAttribute("src");video.load();}};
}
