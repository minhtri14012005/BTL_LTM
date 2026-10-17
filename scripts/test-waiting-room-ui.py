"""T04 controlled room/quiz timing fixtures; not multiplayer proof."""
import argparse
import importlib.util
import json
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('browser',ROOT/'scripts/browser-cdp.py')
browser=importlib.util.module_from_spec(spec);spec.loader.exec_module(browser)
UNIT=r'''(async()=>{
 const {waitingPage}=await import('/js/features/room/page.js');
 const {roomEditor}=await import('/js/features/room/editor.js');
 const {Failure}=await import('/js/core/protocol.js');
 const checks=[],check=(name,ok)=>{if(!ok)throw Error(name);checks.push(name);};
 const flush=()=>new Promise(resolve=>setTimeout(resolve,0));
 const room={id:91,hostUserId:1,name:'Timing fixture',roomCode:'ABCDEFGH2345',status:'WAITING',revision:1,quizId:11,quizTitle:'Old pack',configVersion:1,questionDurationMs:10000,decisionDurationMs:7000,maxPlayers:3,
   members:[1,2,3].map(userId=>({userId,displayName:'Player '+userId,host:userId===1,participation:'PLAYER'}))};
 const resolves={},requests=[],sent=[];
 const app={renderVersion:1,user:{id:1},rooms:new Map(),games:new Map(),statusLabel:s=>s,connect(){},
   api:{request:async(method,path)=>{if(path==='/api/rooms/91')return room;requests.push(path);return new Promise(resolve=>resolves[path]=resolve);}},
   transport:{generation:1,status:'ready',request:async()=>({payload:room}),send:async frame=>{sent.push(frame);if(sent.length===1)throw new Failure('REQUEST_UNCERTAIN',true);return {payload:app.rooms.get(91)};}},
   adoptRoom(next){this.rooms.set(next.id,next);this.roomController?.update();}};
 const root=await waitingPage(app,91);document.querySelector('#view').replaceChildren(root);await flush();
 app.adoptRoom({...room,revision:2,quizId:12,quizTitle:'Latest pack'});
 resolves['/api/quizzes/11']({id:11,ownerUserId:9,questionCount:10});await flush();
 check('Changing pack during an in-flight metadata fetch starts latest fetch',requests.includes('/api/quizzes/12'));
 resolves['/api/quizzes/12']({id:12,ownerUserId:9,questionCount:25});await flush();
 check('Latest pack count controls Start and stale result cannot replace it',root.querySelector('#questionCount').max==='25'&&!root.querySelector('#start-game').disabled);
 root.querySelector('#questionCount').focus();app.adoptRoom({...app.rooms.get(91),revision:3});
 check('Roster update preserves keyboard focus on surviving control',document.activeElement.id==='questionCount');
 root.querySelector('#change-participation').click();await flush();root.querySelector('.action-feedback button').click();await flush();
 check('Uncertain role change retries identical requestId and payload',sent.length===2&&JSON.stringify(sent[0])===JSON.stringify(sent[1]));
 app.transport.status='offline';app.roomController.update();
 check('Offline disables commands but retains membership and roster',root.querySelector('#change-participation').disabled&&root.querySelectorAll('#roster .member').length===3&&app.rooms.get(91).members.length===3);
 app.roomController.dispose();app.roomController=null;
 const catalogue={items:[{id:11,mode:'QUIZ',ownerUserId:9,title:'Quiz',questionCount:10,visibility:'PUBLIC'},{id:12,mode:'RIDDLE',ownerUserId:9,title:'Riddle',questionCount:10,visibility:'PUBLIC'}],size:100,totalElements:2};
 app.api.request=async()=>catalogue;
 const editor=await roomEditor(app,null);document.querySelector('#view').replaceChildren(editor);
 editor.querySelector('#add-stage').click();
 let rows=[...editor.querySelectorAll('.stage-editor')];
 check('Editor creates unique mode rows and enforces limits in form',rows.length===2&&editor.querySelector('#maxPlayers').min==='3'&&editor.querySelector('#maxPlayers').max==='100'&&rows[1].querySelector('[name=stageMode] [value=QUIZ]').disabled);
 rows[1].querySelector('[data-move=up]').click();rows=[...editor.querySelectorAll('.stage-editor')];
 check('Button reorder changes plan order and updates first/last boundaries',rows[0].querySelector('[name=stageMode]').value==='RIDDLE'&&rows[0].querySelector('[data-move=up]').disabled&&rows[1].querySelector('[data-move=down]').disabled);
 return checks;
})()'''

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--origin',default='http://127.0.0.1:8088');parser.add_argument('--port',type=int,default=9227);args=parser.parse_args()
    cdp=browser.CDP(args.port);tab=cdp.tab(args.origin);report={'status':'RUNNING','kind':'controlled UI fixtures','checks':[]}
    try:
        report['checks']=tab.eval(UNIT);assert not cdp.errors,cdp.errors;report['status']='PASS'
    except Exception as error:
        report.update(status='FAIL',error=str(error));raise
    finally:
        cdp.call('Target.closeTarget',{'targetId':tab.target})
        (ROOT/'target/t04-waiting-room-ui-tests.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
        print(json.dumps({'status':report['status'],'checks':len(report['checks'])}))

if __name__=='__main__':main()
