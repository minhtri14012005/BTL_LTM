"""T06 read-only DTO/navigation fixtures; real interruption smoke is separate."""
import argparse
import importlib.util
import json
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('browser',ROOT/'scripts/browser-cdp.py')
browser=importlib.util.module_from_spec(spec);spec.loader.exec_module(browser)
SETUP=r'''(async()=>{
 const v=await import('/js/features/results/view.js'),{historyDetail,historyList}=await import('/js/features/results/history.js');
 const {roomReturn}=await import('/js/features/results/navigation.js'),{awardStateView,AWARD_STATES}=await import('/js/features/results/awards.js');
 const {gamePage}=await import('/js/features/game/page.js');
 const checks=[],check=(name,ok)=>{if(!ok)throw Error(name);checks.push(name);},flush=()=>new Promise(r=>setTimeout(r,0));
 const members=[1,2,3,4].map(userId=>({userId,displayName:userId===1?'<img src=x onerror=alert(1)>':('Người chơi có tên dài '.repeat(3)+userId),rank:1,score:20,totalCorrectAnswerTimeMs:1000,totalAnswerTimeMs:3000,participation:'PLAYER',role:userId===1?'HOST':'MEMBER'}));
 const snapshot={gameSessionId:61,roomId:7,status:'FINISHED',runtimeState:'FINISHED',schemaVersion:2,phase:'FINISHED',revision:9,serverTimeMs:10000,deadlineEpochMs:null,questionIndex:2,questionCount:2,
   endReason:'COMPLETED',hasOfficialWinner:true,winners:[1,2,3,4],members,results:[],player:null,question:null,readyPlayers:[],
   stages:[{mode:'RIDDLE',sourceQuizId:4,firstQuestionIndex:1,questionCount:1,title:'Đố mẹo'},{mode:'QUIZ',sourceQuizId:5,firstQuestionIndex:2,questionCount:1,title:'Quiz'}]};
 const questions=[{questionIndex:1,mode:'RIDDLE',content:'Câu đã chấm',scoredAtMs:1000,payload:{acceptedAnswers:['hố']},answers:[{userId:1,answerStatus:'CORRECT',answerTimeMs:1000,submittedAnswer:{text:'hố'},result:{userId:1,outcome:'CORRECT',scoreDelta:20,scoreAfter:20,answerTimeMs:1000,winStreak:1,loseStreak:0}}]},
   {questionIndex:2,mode:'QUIZ',content:'Câu chưa chấm',scoredAtMs:null,correctAnswer:'A',options:{A:'Một',B:'Hai',C:'Ba',D:'Bốn'},answers:[{userId:1,answerStatus:'ACCEPTED_UNSCORED',selectedOption:'B',answerTimeMs:2000,result:null}]}];
 const calls=[],room={id:7,hostUserId:1,status:'WAITING'};
 const app={user:{id:1},renderVersion:1,api:{request:async(method,path)=>{calls.push({method,path});if(method!=='GET')throw Error('Unexpected mutation');if(path.startsWith('/api/rooms/'))return room;if(path==='/api/games/61/awards')return {awardState:'AWARDS_NOT_QUALIFIED',qualification:{reasonCodes:['CONFIGURED_TOO_SHORT']},awards:[]};if(path==='/api/games/history/61')return {startedAtMs:1000,finishedAtMs:11000,finalSnapshot:snapshot,questions};return {items:[{gameSessionId:61,roomId:7,schemaVersion:2,quizTitleSnapshot:'Trận đã hoàn thành',endReason:'COMPLETED',hasOfficialWinner:true,finishedAtMs:11000}],totalElements:1,size:12};}}};
 const root=document.querySelector('#view');
 const final=v.finalSummary(snapshot,{selfId:1});
 check('All four rank-one co-winners stay in Top 1–3; no arbitrary top-three truncation',final.querySelectorAll('.result-top-card').length===4&&[...final.querySelectorAll('.result-top-card')].every(n=>n.dataset.rank==='1'));
 check('Ranking/name display stays server-driven and safely escaped',!final.querySelector('img')&&final.textContent.includes('<img')&&final.querySelectorAll('.result-top-card .badge').length===4);
 for(const endReason of ['CANCELLED','SERVER_INTERRUPTED']){
   const bad={...snapshot,endReason},summary=v.finalSummary(bad),board=v.standings(bad);
   check(endReason+' cannot show podium/Winner even with contradictory legacy flag',!summary.querySelector('.result-top')&&!board.querySelector('.winner')&&summary.textContent.includes('không có Official Winner'));
 }
 check('Own stats respect v2 correct-only time and v1 total time',v.ownStats(snapshot,1).textContent.includes('1.000 s')&&v.ownStats({...snapshot,schemaVersion:1},1).textContent.includes('3.000 s'));
 check('No award UI is inferred without a T11 contract',awardStateView()===null&&!final.querySelector('[data-award-state]'));
 check('Future award states are presentation-only and never claim cup receipt',AWARD_STATES.every(state=>{const n=awardStateView(state);return n.dataset.awardState===state&&!n.textContent.includes('đã nhận cúp');})&&calls.length===0);
 root.replaceChildren(await historyDetail(app,61));await flush();
 check('History/Room/committed awards GET only; no START/create or award mutation',calls.length===3&&calls.every(c=>c.method==='GET')&&calls.some(c=>c.path==='/api/games/61/awards')&&!calls.some(c=>/start/.test(c.path)));
 check('History has own highlight, actual mode result and accepted-unscored detail',!!root.querySelector('.current-player')&&!!root.querySelector('[data-mode=RIDDLE]')&&root.querySelector('[data-mode=RIDDLE]').textContent.includes('+20')&&!!root.querySelector('[data-status=ACCEPTED_UNSCORED]'));
 check('Unscored question never reveals correct Quiz option',!root.querySelector('.history-question[data-index="2"] .correct')&&!root.querySelector('.history-question[data-index="2"]').textContent.includes('Đáp án đã công bố'));
 check('WAITING Host action navigates to same Room and never starts a Game',root.querySelector('.result-room-return a').hash==='#/room/7'&&root.querySelector('.result-room-return a').textContent.includes('Chơi tiếp'));
 for(const status of ['ACTIVE','DRAFT','CLOSED']){
   room.status=status;const back=roomReturn(app,7);await flush();
   check(status+' Room cannot expose Play Again',back.node.dataset.roomStatus===status&&!back.node.textContent.includes('Chơi tiếp'));back.dispose();
 }
 let resolveRoom;const delayed={...app,api:{request:()=>new Promise(resolve=>resolveRoom=resolve)}};
 const stale=roomReturn(delayed,7);stale.dispose();resolveRoom({...room,status:'WAITING'});await flush();
 check('Disposed Room lookup cannot add stale navigation',!stale.node.querySelector('a'));
 const forbidden=roomReturn({...app,api:{request:async()=>{throw Object.assign(Error('Denied'),{code:'FORBIDDEN'});}}},7);await flush();
 check('Removed/inaccessible Room preserves History without false Room link',forbidden.node.dataset.roomStatus==='UNAVAILABLE'&&!forbidden.node.querySelector('a[href="#/room/7"]')&&!!forbidden.node.querySelector('a[href="#/home"]'));
 room.status='WAITING';const hostReturn=roomReturn(app,7);await flush();
 check('Waiting Host can configure next match without sending START',!!hostReturn.node.querySelector('a[href="#/room/7/edit"]'));hostReturn.dispose();
 const playerReturn=roomReturn({...app,user:{id:2}},7);await flush();
 check('Waiting Player gets Room return and no Host configuration',playerReturn.node.querySelector('a').textContent==='Quay lại phòng'&&!playerReturn.node.querySelector('a[href$="/edit"]'));playerReturn.dispose();
 let aborted=false,reads=0;
 const aborting=roomReturn({...app,api:{request:(method,path,body,{signal})=>{reads++;return new Promise((resolve,reject)=>signal.addEventListener('abort',()=>{aborted=true;reject(Error('aborted'));}));}}},7);
 aborting.refresh();aborting.dispose();await flush();
 check('Room return coalesces reads and aborts when leaving History',reads===1&&aborted&&!aborting.node.querySelector('a'));
 const changing={...app,user:{id:1},api:{request:()=>new Promise(resolve=>resolveRoom=resolve)}};
 const switched=roomReturn(changing,7);changing.user={id:2};resolveRoom(room);await flush();
 check('Account switch cannot install previous user Room actions',!switched.node.querySelector('a'));switched.dispose();
 const legacy=v.finalSummary({...snapshot,schemaVersion:1,endReason:'ONE_SURVIVOR',winners:[1],members:members.map((m,i)=>({...m,rank:i+1}))},{selfId:1});
 check('Legacy result keeps stored ranks/score/time without v2 reinterpretation',legacy.querySelectorAll('.result-top-card').length===3&&legacy.textContent.includes('3.000 s'));
 const empty=await historyList({...app,api:{request:async()=>({items:[],size:12,totalElements:0})}});
 check('Empty History has an explicit state without fake games',!!empty.querySelector('.empty-state')&&!empty.querySelector('.history-card'));
 let finishHistory,staleRoomReads=0;
 const old={...app,renderVersion:1,api:{request:async(method,path)=>{if(path.startsWith('/api/rooms/')){staleRoomReads++;return room;}return new Promise(resolve=>finishHistory=resolve);}}};
 const pendingHistory=historyDetail(old,61);old.renderVersion++;finishHistory({finalSnapshot:snapshot,questions,startedAtMs:1000,finishedAtMs:11000});await pendingHistory;
 check('Stale History response cannot install a Room lookup after route exit',staleRoomReads===0);
 // Final view owns one Room GET across renders and disposes its display timer.
 const realSet=window.setInterval,realClear=window.clearInterval,active=new Set();let serial=0,roomReads=0;
 window.setInterval=()=>{const id=++serial;active.add(id);return id;};window.clearInterval=id=>active.delete(id);
 const game={user:{id:1},games:new Map(),transport:{status:'offline'},connect(){},api:{request:async(method,path)=>{if(path.startsWith('/api/rooms/')){roomReads++;return {...room,status:'WAITING'};}return snapshot;}}};
 try {await gamePage(game,61);game.gameController.update();game.gameController.update();await flush();check('Final rerenders coalesce Room lookup and own one timer',roomReads===1&&active.size===1);game.gameController.dispose();check('Final dispose releases its timer',active.size===0);}
 finally {game.gameController?.dispose();window.setInterval=realSet;window.clearInterval=realClear;}
 window.resultTest={app,snapshot,questions,room,async mount(kind){
   app.resultRoomController?.dispose();app.awardController?.dispose();
   room.status='WAITING';snapshot.endReason=kind==='cancelled'?'CANCELLED':kind==='interrupted'?'SERVER_INTERRUPTED':'COMPLETED';snapshot.hasOfficialWinner=kind==='completed'||kind==='legacy';
   snapshot.schemaVersion=kind==='legacy'?1:2;
   if(kind==='list')root.replaceChildren(await historyList(app));else root.replaceChildren(await historyDetail(app,61));await flush();
 }};
 await resultTest.mount('completed');return checks;
})()'''

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--origin',default='http://127.0.0.1:8088');parser.add_argument('--port',type=int,default=9227);parser.add_argument('--output-dir',default=str(ROOT/'docs/assets/results-2026-10-16'));args=parser.parse_args()
    cdp=browser.CDP(args.port);tab=cdp.tab(args.origin);report={'status':'RUNNING','kind':'read-only DTO/UI fixtures','checks':[],'layouts':[]}
    try:
        report['checks']=tab.eval(SETUP);out=Path(args.output_dir);out.mkdir(parents=True,exist_ok=True)
        for width in [360,390,768,1440]:
            cdp.call('Emulation.setDeviceMetricsOverride',dict(width=width,height=1100,deviceScaleFactor=1,mobile=width<500),tab.session)
            for kind in ['completed','cancelled','interrupted','legacy','list']:
                tab.eval('resultTest.mount('+json.dumps(kind)+')');assert tab.eval('document.documentElement.scrollWidth<=innerWidth'),(width,kind)
                report['layouts'].append({'width':width,'kind':kind})
                if (width,kind) in [(1440,'completed'),(360,'cancelled'),(390,'interrupted'),(360,'legacy'),(1440,'list')]:tab.screenshot(out/(kind+'-'+str(width)+'.png'))
        assert not cdp.errors,cdp.errors;report['status']='PASS'
    except Exception as error:
        report.update(status='FAIL',error=str(error));raise
    finally:
        cdp.call('Target.closeTarget',{'targetId':tab.target});(ROOT/'target/t06-results-ui-tests.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
        print(json.dumps({'status':report['status'],'checks':len(report['checks']),'layouts':len(report['layouts'])}))

if __name__=='__main__':main()
