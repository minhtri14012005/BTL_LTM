"""T05 controlled UI/clock/media fixtures; network proof uses seven-mode smoke."""
import argparse
import importlib.util
import json
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('browser',ROOT/'scripts/browser-cdp.py')
browser=importlib.util.module_from_spec(spec);spec.loader.exec_module(browser)
SETUP=r'''(async()=>{
 const {gamePage}=await import('/js/features/game/page.js');
 const {songVideo}=await import('/js/features/game/video.js');
 const {Failure}=await import('/js/core/protocol.js');
 const checks=[],check=(name,ok)=>{if(!ok)throw Error(name);checks.push(name);};
 const flush=()=>new Promise(resolve=>setTimeout(resolve,0));
 const modes=['QUIZ','SONG','VIETNAMESE_PUZZLE','RIDDLE','CLUES','IMAGE_WORD','ORDERING'];
 const base={gameSessionId:551,roomId:1,schemaVersion:2,revision:1,status:'ACTIVE',runtimeState:'READY',phase:'QUESTION_OPEN',stageIndex:1,questionIndex:1,questionCount:7,serverTimeMs:10000,deadlineEpochMs:70000,readyPlayers:[],results:[],winners:[],hasOfficialWinner:false,
   player:{userId:1,state:'PLAYING',score:0,remainingSpins:1,starAvailable:true,remainingSpinPool:['BONUS','SAFE'],alreadyAnswered:false},
   members:[1,2,3].map(userId=>({userId,displayName:'Người chơi '+userId,participation:'PLAYER',role:userId===1?'HOST':'MEMBER',score:0,rank:userId,totalCorrectAnswerTimeMs:0})),
   stages:modes.map((mode,i)=>({mode,title:'Màn '+(i+1),firstQuestionIndex:i+1,questionCount:1,questionDurationMs:60000,sourceQuizId:999999}))};
 let page,app,frames=[],fail=null,next=base;
 const mount=async(mode='QUIZ',override={})=>{
   app?.gameController?.dispose();
   const index=modes.indexOf(mode);next={...base,stage:base.stages[index],stageIndex:index+1,questionIndex:index+1,
     question:{id:100+index,mode,content:'Nội dung câu hỏi tiếng Việt — '+mode,
       options:{A:'Lựa chọn thứ nhất',B:'Lựa chọn thứ hai',C:'Lựa chọn thứ ba',D:'Lựa chọn thứ tư'},
       payload:{openedAtMs:10000,hints:[{offsetMs:0,text:'Gợi ý đầu tiên đã mở'}],pieces:[{id:'p1',text:'bắt '},{id:'p2',text:'cá'}],items:[{id:'i1',text:'Một'},{id:'i2',text:'Hai'}]}},...override};
   frames=[];fail=null;
   app={user:{id:1},games:new Map(),connect(){},render(){},api:{request:async()=>next},transport:{status:'ready',send:async frame=>{
     frames.push(frame);if(frame.type==='RECONNECT')return {payload:next};
     if(fail)throw new Failure(fail,true);
     return {...frame,revision:next.revision+1,payload:{alreadyAnswered:true,selectedOption:frame.payload.option,submittedAnswer:frame.payload.text?{text:frame.payload.text}:null,remainingSpins:1,starAvailable:true,remainingSpinPool:[]}};
   }}};
   page=await gamePage(app,551);document.querySelector('#view').replaceChildren(page);await flush();return true;
 };
 window.uxTest={mount,checks,get app(){return app;},get frames(){return frames;},get next(){return next;}};
 await mount();
 check('Mode header, native progress and non-chatty timer are present',!!page.querySelector('#game-mode-name')&&page.querySelector('progress').value===0&&page.querySelector('[role=timer]').getAttribute('aria-live')==='off');
 const option=page.querySelector('[data-option=A]');option.focus();option.click();
 check('Selecting Quiz option keeps keyboard focus and does not reveal correctness',document.activeElement.id==='option-A'&&!page.querySelector('.answer-correct')&&!page.querySelector('#answer-accepted'));
 fail='REQUEST_UNCERTAIN';page.querySelector('#submit-answer').click();await flush();
 check('Uncertain Answer shows explicit retry, no invented acceptance',!!page.querySelector('#retry-game-command')&&!!page.querySelector('#game-feedback [role=alert]')&&!page.querySelector('#answer-accepted'));
 await app.gameController.reconnect();
 check('Reconnect sends only snapshot request, never blind Answer replay',frames.filter(f=>f.type==='ANSWER').length===1&&frames.at(-1).type==='RECONNECT');
 fail=null;page.querySelector('#retry-game-command').click();await flush();
 const answers=frames.filter(f=>f.type==='ANSWER');
 check('Explicit retry keeps original frame and accepted state waits for ACK',answers.length===2&&JSON.stringify(answers[0])===JSON.stringify(answers[1])&&!!page.querySelector('#answer-accepted')&&!page.querySelector('.answer-correct'));
 app.gameController.dispose();
 const realNow=performance.now.bind(performance);let now=1000;
 Object.defineProperty(performance,'now',{value:()=>now,configurable:true});
 try {
   await mount('QUIZ',{serverTimeMs:10000,deadlineEpochMs:11000});now=3000;app.gameController.update();
   check('Client deadline zero changes display only, not phase/score or network',page.querySelector('#game-countdown').textContent==='Chờ chuyển câu'&&app.games.get(551).phase==='QUESTION_OPEN'&&app.games.get(551).player.score===0&&frames.every(f=>f.type==='RECONNECT'));
   fail='QUESTION_CLOSED';page.querySelector('[data-option=A]').click();page.querySelector('#submit-answer').click();await flush();
   check('Late Answer error is visible and never accepted/scored locally',!!page.querySelector('#game-feedback [role=alert]')&&!page.querySelector('#answer-accepted')&&app.games.get(551).player.score===0);
 } finally {app.gameController.dispose();Object.defineProperty(performance,'now',{value:realNow,configurable:true});}
 await mount('RIDDLE');const question=next.question;
 next={...next,revision:2,phase:'SCORING',question:{...question,payload:{acceptedAnswers:['SECRET_UNPUBLISHED']}},results:[{userId:1,outcome:'CORRECT',scoreDelta:20}]};
 app.gameController.receive({kind:'EVENT',target:{kind:'GAME',id:551},payload:next});
 check('SCORING never exposes accepted answers/correct colors ahead of RESULT',!page.querySelector('#correct-text')&&!page.querySelector('.answer-correct')&&!page.textContent.includes('SECRET_UNPUBLISHED'));
 next={...next,revision:3,phase:'RESULT'};app.gameController.receive({kind:'EVENT',target:{kind:'GAME',id:551},payload:next});
 check('Published RESULT renders server answer and outcome',page.querySelector('#correct-text').textContent.includes('SECRET_UNPUBLISHED')&&!!page.querySelector('.answer-correct'));
 await mount('CLUES');
 check('Clues render only received released hints',page.querySelectorAll('#released-clues li').length===1&&page.querySelector('#released-clues').textContent.includes('Gợi ý đầu tiên')&&!page.querySelector('#correct-text'));
 await mount('SONG');
 check('Missing song media is explicit and leaves answer input available',page.querySelector('.song-player').dataset.mediaState==='error'&&!page.querySelector('#answer-text').disabled);
 await mount('IMAGE_WORD');
 check('Missing image is explicit without disabling text answer',!!page.querySelector('[role=alert]')&&!page.querySelector('#answer-text').disabled);
 app.gameController.dispose();
 // Media promise/listener ownership is deterministic here; no claim of native autoplay proof.
 const realPlay=HTMLMediaElement.prototype.play,realLoad=HTMLMediaElement.prototype.load;
 let resolvePlay;
 HTMLMediaElement.prototype.play=function(){return new Promise(resolve=>resolvePlay=resolve);};
 HTMLMediaElement.prototype.load=function(){};
 try {
   let owned=0;
   for(let i=0;i<3;i++){
     const media=songVideo({...base,stage:base.stages[1],question:{id:77,payload:{mediaRef:'sha256:'+'a'.repeat(64),openedAtMs:10000}}},()=>11000,{});
     const video=media.box.querySelector('video');
     media.dispose();check('Media dispose '+i+' releases source and pauses playback',!video.hasAttribute('src')&&video.paused);
   }
   const add=HTMLMediaElement.prototype.addEventListener,remove=HTMLMediaElement.prototype.removeEventListener;
   HTMLMediaElement.prototype.addEventListener=function(...args){owned++;return add.apply(this,args);};
   HTMLMediaElement.prototype.removeEventListener=function(...args){owned--;return remove.apply(this,args);};
   try {
     const media=songVideo({...base,stage:base.stages[1],question:{id:77,payload:{mediaRef:'sha256:'+'a'.repeat(64),openedAtMs:10000}}},()=>11000,{});
     check('Song owns exactly six media event handlers',owned===6);
     media.box.querySelector('#enable-video-sound').click();media.dispose();resolvePlay();await flush();
     check('Disposal removes all media handlers and ignores late play resolution',owned===0&&media.box.querySelector('video').paused&&!media.box.querySelector('video').hasAttribute('src'));
     const failed=songVideo({...base,stage:base.stages[1],question:{id:78,payload:{mediaRef:'sha256:'+'a'.repeat(64),openedAtMs:10000}}},()=>11000,{});
     failed.box.querySelector('#enable-video-sound').click();failed.box.querySelector('video').dispatchEvent(new Event('error'));resolvePlay();await flush();
     check('Late play promise cannot replace a media failure with playing status',failed.box.dataset.mediaState==='error'&&failed.box.querySelector('#enable-video-sound').disabled);
     failed.dispose();check('Failed media also releases every handler',owned===0);
   } finally {HTMLMediaElement.prototype.addEventListener=add;HTMLMediaElement.prototype.removeEventListener=remove;}
 } finally {HTMLMediaElement.prototype.play=realPlay;HTMLMediaElement.prototype.load=realLoad;}
 await mount();return checks;
})()'''

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--origin',default='http://127.0.0.1:8088');parser.add_argument('--port',type=int,default=9227);args=parser.parse_args()
    cdp=browser.CDP(args.port);tab=cdp.tab(args.origin);report={'status':'RUNNING','kind':'UI/clock/media fixtures','checks':[],'layouts':[]}
    try:
        report['checks']=tab.eval(SETUP)
        out=ROOT/'docs/assets/gameplay-2026-10-16';out.mkdir(parents=True,exist_ok=True)
        for width in [360,390,768,1440]:
            cdp.call('Emulation.setDeviceMetricsOverride',{'width':width,'height':1100,'deviceScaleFactor':1,'mobile':width<500},tab.session)
            for mode in ['QUIZ','SONG','VIETNAMESE_PUZZLE','RIDDLE','CLUES','IMAGE_WORD','ORDERING']:
                tab.eval('uxTest.mount('+json.dumps(mode)+')')
                assert tab.eval('document.documentElement.scrollWidth<=innerWidth'),(width,mode)
                report['layouts'].append({'mode':mode,'width':width})
                if width in [360,1440] and mode in ['QUIZ','CLUES','VIETNAMESE_PUZZLE','ORDERING']:
                    tab.eval("document.querySelector('.game-mode-header').scrollIntoView({block:'start'})")
                    tab.screenshot(out/(mode.lower()+'-'+str(width)+'.png'))
                if width==360 and mode in ['SONG','IMAGE_WORD']:
                    tab.eval("document.querySelector('.song-player,.text-answer').scrollIntoView({block:'center'})")
                    tab.screenshot(out/(mode.lower()+'-unavailable-fixture-360.png'))
        tab.eval('uxTest.app.gameController.dispose()');assert not cdp.errors,cdp.errors;report['status']='PASS'
    except Exception as error:
        report.update(status='FAIL',error=str(error));raise
    finally:
        cdp.call('Target.closeTarget',{'targetId':tab.target})
        (ROOT/'target/t05-gameplay-ux-tests.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
        print(json.dumps({'status':report['status'],'checks':len(report['checks']),'layouts':len(report['layouts'])}))

if __name__=='__main__':main()
