"""T01 focused browser component tests; transport/clock fixtures, no DB writes.

Real login/room/game/result/replay coverage stays in test-seven-modes-client.py --smoke.
Uses the project's owned headless Chrome/CDP helper; no npm/build dependency.
"""
import argparse
import importlib.util
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location("browser", ROOT / "scripts/browser-cdp.py")
browser = importlib.util.module_from_spec(spec)
spec.loader.exec_module(browser)

UNIT = r'''(async()=>{
 const {HashRouter}=await import('/js/core/router.js');
 const {waitingPage}=await import('/js/features/room/page.js');
 const {gamePage}=await import('/js/features/game/page.js');
 const {action,disposeMedia}=await import('/js/shared/dom.js');
 const done=[],check=(name,condition)=>{if(!condition)throw Error(name);done.push(name);};
 const flush=()=>new Promise(resolve=>setTimeout(resolve,0));
 let changes=0;
 const listeners=new Set(),target={location:{hash:'#/rooms?page=2',origin:location.origin},
   addEventListener:(type,fn)=>listeners.add(fn),removeEventListener:(type,fn)=>listeners.delete(fn)};
 const router=new HashRouter(()=>changes++,target);router.start();router.start();
 check('Router start is idempotent: one hash listener',listeners.size===1);
 check('Hash route and pagination are unchanged',router.read().parts[0]==='rooms'&&router.read().url.searchParams.get('page')==='2');
 router.navigate('rooms?page=2');check('Same-route navigation rerenders once',changes===1);
 router.dispose();router.dispose();check('Router dispose removes the exact listener',listeners.size===0);

 const room={id:711,revision:1,name:'Lifecycle fixture',hostUserId:1,status:'WAITING',roomCode:'ABCDEFGH2345',maxPlayers:3,
   decisionDurationMs:7000,configVersion:2,stages:[{orderIndex:1,mode:'RIDDLE',ownerUserId:2,title:'Riddle',questionCount:1,questionDurationMs:5000}],
   members:[{userId:1,displayName:'Host',host:true,participation:'SPECTATOR'}]};
 let subscriptions=0,adoptions=0,complete;
 const app={user:{id:1},renderVersion:1,rooms:new Map(),games:new Map(),statusLabel:s=>s,connect:()=>{},
   api:{request:async()=>room},transport:{status:'ready',generation:1,request:()=>{subscriptions++;return new Promise(resolve=>complete=resolve);}},
   adoptRoom(next){adoptions++;this.rooms.set(next.id,next);this.roomController?.update();}};
 const page=await waitingPage(app,room.id),oldController=app.roomController;
 oldController.subscribe();oldController.subscribe();
 check('Waiting room coalesces concurrent subscription requests',subscriptions===1);
 oldController.dispose();complete({payload:{...room,revision:2}});await flush();
 check('Disposed room ignores delayed subscription ACK',adoptions===1&&app.rooms.get(room.id).revision===1);
 const markup=page.innerHTML;oldController.update();await oldController.subscribe();
 check('Disposed room neither renders nor subscribes again',page.innerHTML===markup&&subscriptions===1);
 app.roomController=null;
 app.transport.request=async()=>{subscriptions++;return {payload:room};};
 await waitingPage(app,room.id);await flush();
 await app.roomController.subscribe();
 check('Remounted room subscribes once for its connection generation',subscriptions===2);
 app.transport.generation=2;await app.roomController.subscribe();await app.roomController.subscribe();
 check('New connection generation resubscribes exactly once',subscriptions===3);
 app.roomController.dispose();app.roomController=null;
 let resolveRoom;
 app.api.request=()=>new Promise(resolve=>resolveRoom=resolve);
 const staleRoom=waitingPage(app,room.id);app.renderVersion++;resolveRoom(room);await staleRoom;
 check('Room fetch finishing after navigation cannot install a controller',app.roomController===null);

 const realInterval=window.setInterval,realClear=window.clearInterval,active=new Set();let serial=0;
 window.setInterval=()=>{const id=++serial;active.add(id);return id;};
 window.clearInterval=id=>active.delete(id);
 const snapshot={gameSessionId:72,roomId:711,schemaVersion:1,status:'ACTIVE',runtimeState:'READY',phase:'DECISION',
   questionIndex:1,questionCount:10,revision:1,serverTimeMs:1000,deadlineEpochMs:8000,
   members:[],results:[],player:null,question:null,winners:[],hasOfficialWinner:false};
 const game={user:{id:1},games:new Map(),api:{request:async()=>snapshot},transport:{status:'offline'},connect:()=>{}};
 try{
   for(let i=0;i<3;i++){
     await gamePage(game,72);check('Game mount '+i+' owns exactly one display timer',active.size===1);
     game.gameController.dispose();game.gameController.dispose();
     check('Game unmount '+i+' releases its timer idempotently',active.size===0);
   }
   game.games.clear();let resolveSnapshot;game.api.request=()=>new Promise(resolve=>resolveSnapshot=resolve);
   const staleGame=gamePage(game,72),controller=game.gameController;controller.dispose();
   resolveSnapshot(snapshot);await staleGame;controller.receive({kind:'EVENT',target:{kind:'GAME',id:72},payload:snapshot});
   check('Disposed game ignores delayed snapshot/events without retaining a timer',game.games.size===0&&active.size===0);
 }finally{game.gameController?.dispose();window.setInterval=realInterval;window.clearInterval=realClear;}

 let finishAction,succeeded=false;const form=document.createElement('form'),owner={user:{id:1},renderVersion:1};
 const pending=action(owner,form,()=>new Promise(resolve=>finishAction=resolve),()=>succeeded=true);
 owner.renderVersion++;finishAction({});await pending;
 check('Async action cannot navigate/update a route that has been left',!succeeded);
 const box=document.createElement('div'),video=document.createElement('video');let paused=0,released=0;
 video.pause=()=>paused++;video.load=()=>released++;video.setAttribute('src','/fixture.mp4');box.append(video);disposeMedia(box);
 check('Route cleanup pauses History/editor media and releases its source',paused===1&&released===1&&!video.hasAttribute('src'));
 return done;
})()'''


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--origin', default='http://127.0.0.1:8088')
    parser.add_argument('--port', type=int, default=9227)
    args = parser.parse_args()
    cdp = browser.CDP(args.port)
    tab = cdp.tab(args.origin)
    report = {'status': 'RUNNING', 'kind': 'component fixtures', 'checks': []}
    try:
        report['checks'] = tab.eval(UNIT)
        assert not cdp.errors, cdp.errors
        report['status'] = 'PASS'
    except Exception as error:
        report.update(status='FAIL', error=str(error))
        raise
    finally:
        cdp.call('Target.closeTarget', {'targetId': tab.target})
        (ROOT/'target/t01-lifecycle-client-tests.json').write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
        print(json.dumps({'status': report['status'], 'checks': len(report['checks'])}))


if __name__ == '__main__':
    main()
