import {h,button,notice} from "/js/shared/dom.js";
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
  const video=h("video",{id:"song-video",className:"song-video",preload:"auto",playsInline:true,muted:true,"aria-label":"Video và âm thanh câu Đoán tên bài hát"}),status=h("p",{id:"video-status",className:"muted",role:"status","aria-live":"polite"},"Đang tải video. Thời gian câu vẫn tiếp tục…");
  let current=s,disposed=false,failed=false,starting=false,blocked=false;
  video.muted=!memory.soundEnabled;
  const play=button("Bật tiếng / Phát tại thời điểm hiện tại",()=>{memory.soundEnabled=true;blocked=false;video.muted=false;start();},{id:"enable-video-sound",className:"secondary"});
  const mute=button("Tắt tiếng",()=>{video.muted=true;memory.soundEnabled=false;volume();},{id:"mute-video-sound",className:"secondary",hidden:video.muted});
  const box=h("div",{className:"song-player","data-media-state":"loading"},video,h("div",{className:"actions video-actions"},play,mute),status);
  const open=()=>current.phase==="QUESTION_OPEN" && current.status==="ACTIVE" && current.deadlineEpochMs>serverNow();
  function align(){if(video.readyState>=1){const t=videoPosition(current.question.payload.openedAtMs,serverNow(),video.duration);if(Math.abs(video.currentTime-t)>0.65)try{video.currentTime=t;}catch{/* Seekable metadata may still be arriving. */}}}
  function start(){
    if(disposed||failed||starting||!open())return;align();starting=true;
    let playback;try{playback=video.play();}catch(error){playback=Promise.reject(error);}
    Promise.resolve(playback).then(()=>{if(disposed || failed || !open()){video.pause();return;}blocked=false;box.dataset.mediaState="playing";volume();}).catch(error=>{if(!disposed && !failed){blocked=error.name!=="AbortError";box.dataset.mediaState=blocked?"blocked":"loading";status.textContent=blocked?"Trình duyệt chưa cho phát. Bấm Bật tiếng / Phát; bạn vẫn có thể nhập đáp án.":"Đang tải video tại thời điểm hiện tại của câu…";}}).finally(()=>{starting=false;});
  }
  function sync(){
    if(disposed || failed)return;play.disabled=!open();
    if(!open()){video.pause();if(box.dataset.mediaState!=="paused"){box.dataset.mediaState="paused";status.textContent="Video tạm dừng. Trạng thái câu và deadline tiếp tục theo trận.";}return;}
    if(video.readyState>=1 && (serverNow()-current.question.payload.openedAtMs)/1000>=video.duration-0.05){video.pause();if(box.dataset.mediaState!=="ended"){box.dataset.mediaState="ended";status.textContent="Video đã hết; bạn vẫn có thể gửi đáp án trước hạn câu.";}return;}
    align();if(video.readyState>=2 && video.paused && !video.ended && !blocked)start();
  }
  const loaded=()=>{if(!disposed){align();start();}};
  const failedVideo=()=>{if(disposed)return;failed=true;video.pause();play.disabled=true;mute.disabled=true;box.dataset.mediaState="error";status.textContent="Không tải/phát được video. Câu vẫn tiếp tục theo giờ Server; bạn có thể nhập đáp án phỏng đoán.";status.className="notice error";};
  function volume(){if(disposed||failed)return;mute.hidden=video.muted;if(!video.paused){status.textContent=video.muted?"Video đang phát tắt tiếng. Bấm Bật tiếng để nghe; thời gian vẫn chạy theo Server.":"Video đang phát theo thời gian câu của Server.";}}
  const buffering=()=>{if(!disposed && !failed && open()){box.dataset.mediaState="buffering";status.textContent="Đang tải video tại thời điểm hiện tại của câu. Thời gian không dừng.";}};
  const events={loadedmetadata:loaded,canplay:sync,error:failedVideo,waiting:buffering,stalled:buffering,volumechange:volume};
  Object.entries(events).forEach(([type,listener])=>video.addEventListener(type,listener));
  const source=videoUrl(s,s.question);if(source)video.src=source;else failedVideo();
  return {box,key:s.question.id,update(next){if(!disposed){current=next;sync();}},sync,dispose(){if(disposed)return;disposed=true;Object.entries(events).forEach(([type,listener])=>video.removeEventListener(type,listener));video.pause();video.removeAttribute("src");video.load();}};
}
