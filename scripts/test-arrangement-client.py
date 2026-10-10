"""Task20: stable-ID arrangements on native browser + real REST/raw WS/MySQL.
No mock success in smoke. Component assertions are reported separately.
"""
import argparse,importlib.util,json,secrets,sys
from pathlib import Path
sys.dont_write_bytecode=True
ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('multimode_tests',ROOT/'scripts/test-multimode-client.py')
mm=importlib.util.module_from_spec(spec);spec.loader.exec_module(mm)
old=mm.old;browser=mm.browser
UNIT=r'''(async()=>{
 const c=await import('/client/core.js'),a=await import('/client/arrangement.js'),g=await import('/client/game-state.js'),ui=await import('/client/game.js'),hist=await import('/client/history.js');
 const done=[],check=(n,f)=>{if(!f())throw Error(n);done.push(n);},bad=f=>{try{f();return false;}catch{return true;}};
 const items=[{id:'a',text:'ha'},{id:'s',text:' '},{id:'b',text:'ha'}],ids=['a','s','b'];
 check('seven actual playable modes',()=>c.PLAYABLE_MODES.length===7&&c.PLAYABLE_MODES.includes('VIETNAMESE_PUZZLE')&&c.PLAYABLE_MODES.includes('ORDERING'));
 check('permutation validates IDs rather than repeated labels',()=>a.permutation(items,ids)&&a.permutation(items,['b','s','a'])&&!a.permutation(items,['a','a','b'])&&!a.permutation(items,['a','s'])&&!a.permutation(items,['a','s','fake']));
 check('move preserves one occurrence of every item',()=>JSON.stringify(a.move(ids,2,-1))===JSON.stringify(['a','b','s'])&&a.permutation(items,a.move(ids,0,1)));
 const {roomEditor}=await import('/client/rooms.js');
 const roomForm=await roomEditor({user:{id:1},api:{request:async()=>({items:[{id:100,ownerUserId:1,mode:'QUIZ',title:'Q',questionCount:1},{id:101,ownerUserId:1,mode:'VIETNAMESE_PUZZLE',title:'V',questionCount:2},{id:102,ownerUserId:1,mode:'ORDERING',title:'O',questionCount:2}],totalElements:3})}},null,100);
 roomForm.querySelector('#add-stage').click();const stage=roomForm.querySelector('.stage-editor:last-child'),kind=stage.querySelector('[name=stageMode]');kind.value='VIETNAMESE_PUZZLE';kind.dispatchEvent(new Event('change'));
 check('changing stage mode creates actual source options',()=>stage.querySelector('[name=quizId]').value==='101'&&stage.querySelector('[name=quizId]').options.length===1);
 kind.value='ORDERING';kind.dispatchEvent(new Event('change'));check('changing again selects matching set and author role',()=>stage.querySelector('[name=quizId]').value==='102'&&roomForm.querySelector('[name=hostParticipation]').value==='SPECTATOR');
 const q={content:'Ghép',pieces:items,correctOrder:ids,acceptedAnswers:['ha ha']};
 check('Vietnamese request preserves literal spaces and aliases',()=>{const x=c.quizValues('V','PRIVATE',[q],'VIETNAMESE_PUZZLE').questions[0];return x.pieces[1].text===' '&&x.correctOrder[1]==='s'&&x.acceptedAnswers[0]==='ha ha'&&!('options'in x);});
 check('Ordering request excludes text aliases/Quiz fields',()=>{const x=c.quizValues('O','PUBLIC',[{content:'Order',items:[{id:'a',text:'Same'},{id:'b',text:'Same'}],correctOrder:['a','b']}],'ORDERING').questions[0];return !('acceptedAnswers'in x)&&!('correctAnswer'in x)&&x.items.length===2;});
 check('editor rejects incomplete/duplicate/unknown canonical IDs',()=>bad(()=>c.quizValues('V','PUBLIC',[{...q,correctOrder:['a','b']}],'VIETNAMESE_PUZZLE'))&&bad(()=>c.quizValues('V','PUBLIC',[{...q,correctOrder:['a','a','b']}],'VIETNAMESE_PUZZLE'))&&bad(()=>c.quizValues('V','PUBLIC',[{...q,correctOrder:['a','s','fake']}],'VIETNAMESE_PUZZLE')));
 const editor=a.arrangementEditor(q,'VIETNAMESE_PUZZLE'),row=document.createElement('div');row.append(editor);check('editor roundtrip retains IDs and space pieces',()=>JSON.stringify(a.readArrangement(row,'VIETNAMESE_PUZZLE'))===JSON.stringify({pieces:items,correctOrder:ids}));
 let changed;const controls=a.arrangementAnswer(items,['a'],true,n=>changed=n);controls.querySelector('[data-piece-id=b]').click();check('choosing repeated piece adds its distinct ID',()=>JSON.stringify(changed)===JSON.stringify(['a','b']));
 const frame=g.gameCommand('ANSWER',7,2,{itemIds:ids});ids.reverse();check('pending retry owns an immutable copied permutation',()=>JSON.stringify(frame.payload.itemIds)===JSON.stringify(['a','s','b'])&&Object.isFrozen(frame.payload.itemIds));
 const p={userId:1,state:'PLAYING',score:0,remainingSpins:0,starAvailable:false,starSelected:false,currentSpin:null,alreadyAnswered:false,submittedAnswer:null};
 const s={gameSessionId:7,roomId:1,schemaVersion:2,revision:2,status:'ACTIVE',runtimeState:'READY',phase:'QUESTION_OPEN',stageIndex:1,stage:{mode:'VIETNAMESE_PUZZLE'},stages:[{mode:'VIETNAMESE_PUZZLE',title:'V'}],questionIndex:1,questionCount:2,readyPlayers:[],serverTimeMs:1000,deadlineEpochMs:9000,player:p,members:[{userId:1,participation:'PLAYER',role:'HOST',displayName:'H',rank:1,score:0,totalCorrectAnswerTimeMs:0}],winners:[],hasOfficialWinner:false,results:[],question:{mode:'VIETNAMESE_PUZZLE',content:'Ghép',options:{},payload:{pieces:items}}};
 let sent;const app={user:{id:1},games:new Map(),transport:{status:'ready',send:async f=>{sent=f;return f.type==='RECONNECT'?{payload:s}:{...f,kind:'ACK',status:'ACCEPTED',revision:3,payload:{selectedOption:null,alreadyAnswered:true,spinEffect:null,starSelected:false,remainingSpins:0,starAvailable:false,remainingSpinPool:[],submittedAnswer:{itemIds:f.payload.itemIds}}};}},api:{request:async()=>s},connect:()=>{}};
 const page=await ui.gamePage(app,7);document.body.append(page);await new Promise(r=>setTimeout(r,0));
 check('unscored arrangement has no secret or Quiz actions',()=>!page.querySelector('#correct-arrangement')&&!page.querySelector('#use-spin')&&!page.querySelector('#remaining-spins')&&page.querySelector('#submit-answer').disabled);
 for(const id of ['b','s','a'])page.querySelector('[data-piece-id='+id+']').click();check('all pieces enable Answer',()=>!page.querySelector('#submit-answer').disabled&&page.querySelector('#joined-pieces').textContent==='ha ha');
 page.querySelector('#submit-answer').click();await new Promise(r=>setTimeout(r,0));check('arrangement ACK locks exact submitted IDs',()=>sent.type==='ANSWER'&&sent.payload.itemIds[0]==='b'&&!page.querySelector('#submit-answer')&&page.querySelector('#answer-accepted'));
 app.gameController.receive({kind:'EVENT',target:{kind:'GAME',id:7},payload:{...s,revision:4,phase:'RESULT',question:{...s.question,payload:{pieces:items,correctOrder:['a','s','b'],acceptedAnswers:['ha ha']}},results:[{userId:1,outcome:'CORRECT',scoreDelta:10}]}});
 check('result publishes aliases after scoring and stale snapshot cannot undo',()=>{app.gameController.receive({kind:'EVENT',target:{kind:'GAME',id:7},payload:s});return page.querySelector('#correct-arrangement').textContent.includes('ha ha')&&page.dataset.phase==='RESULT';});
 app.gameController.dispose();page.remove();
 check('Ordering Result resolves labels instead of undefined Quiz answers',()=>ui.resultPanel({index:2,question:{mode:'ORDERING',payload:{items:[{id:'a',text:'First'},{id:'b',text:'Last'}],correctOrder:['a','b']}},results:[]},[]).textContent.includes('First → Last'));
 return done;
})()'''

def smoke(cdp,origin,report):
 prefix='ui20_'+secrets.token_hex(4);password=secrets.token_urlsafe(15);report['fixturePrefix']=prefix;people=[]
 def passed(name):report['smoke'].append(name);print('PASS '+name,flush=True)
 def snapshot(t,gid):return t.eval(f"fetch('/api/games/{gid}/snapshot').then(r=>r.json())")
 def phase(t,index,name):t.wait(f"document.querySelector('#game-page')?.dataset.index==='{index}'&&document.querySelector('#game-page').dataset.phase==='{name}'",timeout=18)
 for role in ['author','p1','p2','p3']:
  t=cdp.tab(origin);t.eval('window.confirm=()=>true');t.route('register','#register-form');t.fill({'username':prefix+'_'+role,'displayName':role,'password':password,'confirmPassword':password});t.submit('#register-form');t.wait("!!document.querySelector('#login-form')");t.fill({'username':prefix+'_'+role,'password':password});t.submit('#login-form');t.wait("document.querySelector('#connection-status')?.dataset.state==='ready'");u=t.eval("fetch('/api/auth/me').then(r=>r.json())");t.eval(old.OBSERVER);people.append((t,u))
 host=people[0][0];players=[t for t,u in people[1:]];report['userIds']=[u['id'] for t,u in people];passed('4 accounts Register/Login in independent browser contexts')
 host.route('quizzes/new','#quiz-form');host.fill({'title':prefix+' Quiz','visibility':'PUBLIC'});host.eval("document.querySelector('[name=content]').value='Quiz?';['A','B','C','D'].forEach(k=>document.querySelector('[name=option'+k+']').value='Option '+k);document.querySelector('[name=correctAnswer]').value='A'");host.submit('#quiz-form');host.wait(r"location.hash.startsWith('#/quiz/')&&location.hash.endsWith('/edit')&&!!document.querySelector('[name=optionA]')");quiz=int(host.eval("location.hash.split('/')[2]"));sets={'QUIZ':quiz};orders={}
 for mode in ['VIETNAMESE_PUZZLE','ORDERING']:
  host.route('quizzes/new','#quiz-form');host.fill({'mode':mode,'title':prefix+' '+mode,'visibility':'PRIVATE'});host.click('#add-question')
  host.eval("document.querySelectorAll('.question-editor').forEach(r=>r.querySelector('[data-add-item]').click())")
  labels=['ha',' ','ha'] if mode=='VIETNAMESE_PUZZLE' else ['Một','Một','Ba']
  host.eval(f"document.querySelectorAll('.question-editor').forEach((r,i)=>{{r.querySelector('[name=content]').value='Sắp xếp '+(i+1);[...r.querySelectorAll('[name=itemText]')].forEach((n,j)=>n.value={json.dumps(labels,ensure_ascii=False)}[j]);const a=r.querySelector('[name=acceptedAnswers]');if(a)a.value='ha ha';}})")
  host.submit('#quiz-form');host.wait(r"location.hash.startsWith('#/quiz/')&&location.hash.endsWith('/edit')&&document.querySelectorAll('.question-editor').length===2");sid=int(host.eval("location.hash.split('/')[2]"));sets[mode]=sid
  own=host.eval(f"fetch('/api/quizzes/{sid}').then(r=>r.json())");orders[mode]=[q['correctOrder'] for q in own['questions']]
  host.fill({'title':prefix+' '+mode+' edited'});host.submit('#quiz-form');host.wait("!!document.querySelector('.question-preview')");assert not host.eval("document.body.innerText.includes('undefined')")
  assert players[0].eval(f"fetch('/api/quizzes/{sid}').then(r=>r.status)")==404
 report['quizIds']=list(sets.values());passed('Actual author Create/Edit both arrangements, stable opaque IDs/repeated values, other-user PRIVATE concealment')
 host.route('rooms/new?quiz='+str(quiz),'#room-form');host.fill({'name':prefix+' mixed','maxPlayers':'3','hostParticipation':'SPECTATOR'});host.fill({'questionCount':'1','seconds':'10'},'.stage-editor:first-child ')
 for mode in ['VIETNAMESE_PUZZLE','ORDERING']:
  host.click('#add-stage');host.fill({'stageMode':mode},'.stage-editor:last-child ');host.fill({'quizId':str(sets[mode]),'questionCount':'2','seconds':'10'},'.stage-editor:last-child ')
 assert host.eval("document.querySelector('[name=hostParticipation] [value=PLAYER]').disabled");host.submit('#room-form');host.wait("!!document.querySelector('#open-room')");rid=int(host.eval("location.hash.split('/')[2]"));report['roomIds']=[rid];host.click('#open-room');host.wait("!!document.querySelector('#start-game')");code=host.eval("document.querySelector('#room-code').textContent")
 for t in players:t.route('join','#join-form');t.fill({'code':code,'participation':'PLAYER'});t.submit('#join-form');t.wait("!!document.querySelector('#waiting-room')")
 host.wait("!document.querySelector('#start-game').disabled");host.click('#start-game')
 for t in [host,*players]:t.wait("document.querySelector('#game-page')?.dataset.phase==='INTRO'")
 gid=int(host.eval("location.hash.split('/')[2]"));report['gameIds']=[gid];assert snapshot(host,gid)['player'] is None
 def ready():
  for t in players:t.wait("!!document.querySelector('#continue-stage')&&!document.querySelector('#continue-stage').disabled");t.click('#continue-stage')
 ready();phase(host,1,'DECISION');phase(host,1,'QUESTION_OPEN')
 for t in players:t.click('[data-option=A]');t.click('#submit-answer')
 phase(host,2,'INTRO');ready();phase(host,2,'QUESTION_OPEN');passed('Real ordered Quiz→Vietnamese→Ordering plan, Author Host Spectator + 3 Players, common Intro/Quiz Decision')
 cdp.call('Emulation.setDeviceMetricsOverride',{'width':390,'height':844,'deviceScaleFactor':1,'mobile':True},players[0].session)
 for index in range(2,6):
  if index==4:phase(host,index,'INTRO');ready()
  phase(host,index,'QUESTION_OPEN');mode='VIETNAMESE_PUZZLE' if index<=3 else 'ORDERING';local=(index-2) if index<=3 else (index-4);canonical=orders[mode][local]
  before=snapshot(players[0],gid);assert not any(k in before['question']['payload'] for k in ['correctOrder','acceptedAnswers','matchingPolicy']);assert not players[0].eval("!!document.querySelector('#use-spin')||!!document.querySelector('#use-star')")
  if index==2:
   assert players[0].eval("document.querySelector('#submit-answer').disabled");players[0].eval('gameSocket.close()');players[0].wait("document.querySelector('#connection-status')?.dataset.state==='offline'");host.wait("parseFloat(document.querySelector('#game-countdown').textContent)<9");players[0].click('#reconnect-room');players[0].wait("document.querySelector('#connection-status')?.dataset.state==='ready'&&!document.querySelector('[data-piece-id]').disabled");after=snapshot(players[0],gid);assert after['deadlineEpochMs']==before['deadlineEpochMs'] and after['remainingMs']<before['remainingMs'] and after['question']['payload']==before['question']['payload'];players[0].eval("wire.dropType='ANSWER'")
  for pi,t in enumerate(players):
   if index==5 and pi==2:continue
   ids=canonical
   if mode=='VIETNAMESE_PUZZLE' and pi==0:ids=[canonical[2],canonical[1],canonical[0]]
   elif mode=='VIETNAMESE_PUZZLE' and index==2 and pi==2:ids=[canonical[0],canonical[2],canonical[1]]
   elif mode=='ORDERING' and index==4 and pi==1:ids=[canonical[1],canonical[0],canonical[2]]
   for ident in ids:t.click('[data-piece-id="'+ident+'"]')
   assert not t.eval("document.querySelector('#submit-answer').disabled")
   if pi==0 and index==3:
    # Keyboard controls are genuine focusable buttons. Move twice restores submission order.
    first=ids[0];cdp.call('Page.bringToFront',{},t.session);cdp.call('Emulation.setFocusEmulationEnabled',{'enabled':True},t.session);t.eval(f"document.querySelector('#down-{first}').focus()");cdp.call('Input.dispatchKeyEvent',{'type':'keyDown','key':'Enter','code':'Enter','text':'\r','unmodifiedText':'\r','windowsVirtualKeyCode':13},t.session);cdp.call('Input.dispatchKeyEvent',{'type':'keyUp','key':'Enter','code':'Enter','windowsVirtualKeyCode':13},t.session);t.click('#up-'+first)
   t.click('#submit-answer')
   if not(index==2 and pi==0) and not(pi==2 and index<5):t.wait("!!document.querySelector('#answer-accepted')")
  if index==2:
   players[0].wait('!!wire.dropped');request=players[0].eval('wire.dropped.requestId');players[0].eval('gameSocket.close()');players[0].wait("!!document.querySelector('#retry-game-command')");players[0].click('#reconnect-room');players[0].wait("document.querySelector('#connection-status')?.dataset.state==='ready'&&!document.querySelector('#retry-game-command').disabled");players[0].click('#retry-game-command');players[0].wait(f"document.querySelector('#game-ack')?.dataset.requestId==='{request}'");frames=players[0].eval("wire.commands.filter(m=>m.type==='ANSWER'&&m.questionIndex===2)");assert len(frames)==2 and frames[0]==frames[1];passed('Disconnect before Answer and dropped ACK retry after close preserve exact shuffled IDs/deadline/one Answer')
  host.wait(f"wire.messages.some(m=>(m.type==='QUESTION_RESULT'||m.type==='GAME_END')&&m.questionIndex==={index})",timeout=15)
  result=host.eval(f"wire.messages.find(m=>(m.type==='QUESTION_RESULT'||m.type==='GAME_END')&&m.questionIndex==={index}).payload")
  expected=[10,10,0] if index==2 else [20,20,20] if index==3 else [10,0,10] if index==4 else [20,20,0]
  assert [next(r for r in result['results'] if r['userId']==u['id'])['scoreDelta'] for t,u in people[1:]]==expected
  if index==3:players[0].wait("!!document.querySelector('#correct-arrangement')");assert players[0].eval("document.documentElement.scrollWidth<=innerWidth");players[0].screenshot(ROOT/'target/task20-vietnamese-mobile.png')
  if index==4:players[1].wait("!!document.querySelector('#correct-arrangement')");assert players[1].eval("document.querySelector('#game-page').innerText.includes('Sai')");players[1].screenshot(ROOT/'target/task20-ordering-result.png')
 passed('Repeated-text Vietnamese swap is CORRECT; same-label Ordering swap WRONG; stage last +20; NO_ANSWER 0; keyboard/mobile controls')
 for t in [host,*players]:t.wait("!!document.querySelector('#final-summary')")
 final=snapshot(host,gid);report['finalSnapshot']=final;assert [m['score'] for m in final['members'] if m['participation']=='PLAYER']==[70,60,40];assert final['hasOfficialWinner'];host.screenshot(ROOT/'target/task20-final.png')
 players[0].route('history/'+str(gid),'#history-detail');assert players[0].eval("document.querySelectorAll('.history-question').length===5&&!document.body.innerText.includes('undefined')&&document.body.innerText.includes('ha ha')&&document.body.innerText.includes('Một → Một → Ba')");players[0].screenshot(ROOT/'target/task20-history-mobile.png')
 host.route('room/'+str(rid),'#waiting-room');host.wait("!!document.querySelector('#start-game')");passed('MySQL-backed Final 70/60/40, correct-only standings, History arrangements/results, Room WAITING')
 for t,u in people:cdp.call('Target.closeTarget',{'targetId':t.target})

def main():
 p=argparse.ArgumentParser();p.add_argument('--origin',default='http://127.0.0.1:8087');p.add_argument('--port',type=int,default=9224);p.add_argument('--smoke',action='store_true');a=p.parse_args();report={'task':20,'status':'RUNNING','unit':[],'smoke':[],'origin':a.origin,'clients':4,'quizIds':[],'roomIds':[],'gameIds':[]}
 try:
  cdp=browser.CDP(a.port);report['browser']=cdp.version;t=cdp.tab(a.origin);report['unit']=t.eval(old.legacy.UNIT)+t.eval(old.UNIT)+t.eval(mm.UNIT)+t.eval(UNIT);cdp.call('Target.closeTarget',{'targetId':t.target})
  if a.smoke:smoke(cdp,a.origin,report)
  assert not cdp.errors,cdp.errors;report['status']='PASS'
 except Exception as e:report['status']='FAIL';report['error']=str(e);raise
 finally:(ROOT/'target/task20-client-tests.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8');print(json.dumps({'status':report['status'],'unit':len(report['unit']),'smoke':len(report['smoke'])}),flush=True)
if __name__=='__main__':main()
