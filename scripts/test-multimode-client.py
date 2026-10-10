"""Task19: native browser UI, real same-origin REST/raw WS/MySQL.
Component assertions are explicitly separate from the four-account UI smoke.
Uses existing owned Chrome CDP fixture; no application/API mocks in smoke.
"""
import argparse
import importlib.util
import json
import secrets
import sys
sys.dont_write_bytecode=True
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('game_tests',ROOT/'scripts/test-game-client.py')
old=importlib.util.module_from_spec(spec);spec.loader.exec_module(old)
browser=old.browser
UNIT=r'''(async()=>{
 const c=await import('/client/core.js'),g=await import('/client/game-state.js'),ui=await import('/client/game.js');
 const done=[],check=(n,f)=>{if(!f())throw Error(n);done.push(n);},bad=f=>{try{f();return false;}catch{return true;}};
 const q={content:'Có chân?',acceptedAnswers:['cái bàn','bàn']};
 check('RIDDLE editor request excludes Quiz fields',()=>{const x=c.quizValues('R','PRIVATE',[q],'RIDDLE');return x.mode==='RIDDLE'&&x.questions[0].acceptedAnswers.length===2&&!('options'in x.questions[0])&&!('correctAnswer'in x.questions[0]);});
 check('RIDDLE aliases bounds and unsupported modes',()=>bad(()=>c.quizValues('R','PUBLIC',[{...q,acceptedAnswers:[]}],'RIDDLE'))&&bad(()=>c.quizValues('R','PUBLIC',[{...q,acceptedAnswers:['a'.repeat(301)]}],'RIDDLE'))&&bad(()=>c.quizValues('R','PUBLIC',[q],'SONG')));
 const data={name:'Room',maxPlayers:'3',hostParticipation:'SPECTATOR'},stages=[{mode:'QUIZ',quizId:2,questionCount:10,seconds:3},{mode:'RIDDLE',quizId:3,questionCount:2,seconds:8}];
 check('ordered stage plan and ms duration',()=>{const x=c.roomValues(data,stages);return x.stages[1].mode==='RIDDLE'&&x.stages[1].questionDurationMs===8000&&!('quizId'in x);});
 check('duplicate modes, total count, bad duration rejected',()=>bad(()=>c.roomValues(data,[stages[0],stages[0]]))&&bad(()=>c.roomValues(data,stages.map(s=>({...s,questionCount:30}))))&&bad(()=>c.roomValues(data,[{...stages[0],seconds:0}])));
 const {roomEditor}=await import('/client/rooms.js');
 const oldRoom={id:3,hostUserId:1,status:'DRAFT',configVersion:1,stages:[],quizId:2,name:'Legacy',maxPlayers:3,questionDurationMs:5000,members:[{host:true,participation:'SPECTATOR'}]};
 const legacyEditor=await roomEditor({user:{id:1},api:{request:async(method,path)=>path.includes('rooms')?oldRoom:{items:[{id:2,mode:'QUIZ',ownerUserId:1,title:'Old',questionCount:10}],totalElements:1}}},3);
 check('legacy Room editor retains v1 config without null text',()=>!legacyEditor.querySelector('[name=stageMode]')&&!legacyEditor.querySelector('[name=questionCount]')&&!legacyEditor.textContent.includes('null')&&legacyEditor.querySelector('[name=seconds]').value==='5');
 const p={userId:1,state:'PLAYING',score:0,remainingSpins:1,starAvailable:true,starSelected:false,currentSpin:null,alreadyAnswered:false,submittedAnswer:null};
 const s={gameSessionId:4,roomId:1,schemaVersion:2,revision:2,status:'ACTIVE',runtimeState:'READY',phase:'INTRO',stageIndex:1,stage:{mode:'RIDDLE'},stages:[{mode:'RIDDLE',title:'R'}],questionIndex:1,questionCount:1,readyPlayers:[],serverTimeMs:1000,deadlineEpochMs:11000,player:p,members:[{userId:1,participation:'PLAYER',role:'HOST',displayName:'H',rank:1,score:0,totalCorrectAnswerTimeMs:1200}],winners:[],hasOfficialWinner:false,results:[]};
 check('Intro ready excludes Spectator and duplicates',()=>g.permissions(s,1,true).continue&&!g.permissions({...s,player:null},1,true).continue&&!g.permissions({...s,readyPlayers:[1]},1,true).continue);
 check('Continue ACK retains all player resources',()=>{let n=g.applyReceipt(s,{type:'CONTINUE',target:{id:4},questionIndex:1,revision:3,payload:{stageIndex:1,readyPlayers:[1]}});return n.player===p&&n.readyPlayers[0]===1;});
 check('non-Quiz resources remain unavailable',()=>!g.permissions({...s,phase:'DECISION'},1,true).spin&&!g.permissions({...s,phase:'DECISION'},1,true).star);
 check('v2 standings correct-only metric',()=>ui.standings(s,1).textContent.includes('Thời gian đúng')&&ui.standings(s,1).textContent.includes('1.200 s'));
 const initializing={...s,questionIndex:0,stageIndex:0,stage:null,deadlineEpochMs:null,runtimeState:'INITIALIZING'};
 const handoff={user:{id:1},games:new Map(),transport:{status:'ready',send:async()=>({payload:s})},api:{request:async()=>initializing},connect:()=>{}};
 const firstPage=await ui.gamePage(handoff,4);await new Promise(r=>setTimeout(r,0));
 check('Start INITIALIZING nullable stage proceeds to RECONNECT',()=>firstPage.querySelector('#continue-stage')&&!firstPage.querySelector('#continue-stage').disabled);handoff.gameController.dispose();
 const open={...s,phase:'QUESTION_OPEN',question:{mode:'RIDDLE',content:'Có chân?',options:{},payload:{}},deadlineEpochMs:9000};
 let resolveAck;const fake={user:{id:1},games:new Map(),transport:{status:'ready',send:x=>x.type==='RECONNECT'?Promise.resolve({payload:open}):new Promise(r=>resolveAck=r)},api:{request:async()=>open},connect:()=>{}};
 const page=await ui.gamePage(fake,4);document.body.append(page);await new Promise(r=>setTimeout(r,0));
 const text=page.querySelector('#answer-text');text.value='  CÁI   BÀN ';text.dispatchEvent(new Event('input'));text.focus();text.setSelectionRange(3,3);
 fake.gameController.receive({target:{kind:'GAME',id:4},kind:'EVENT',payload:{...open,revision:3}});
 check('text draft/caret survive shared event',()=>page.querySelector('#answer-text').value==='  CÁI   BÀN '&&document.activeElement.id==='answer-text'&&document.activeElement.selectionStart===3);
 page.querySelector('#submit-answer').click();
 check('pending text Answer locks input',()=>page.querySelector('#answer-text').disabled&&!page.querySelector('#answer-accepted'));
 resolveAck({requestId:'text-id',type:'ANSWER',target:{id:4},questionIndex:1,revision:4,payload:{alreadyAnswered:true,selectedOption:null,submittedAnswer:{text:'  CÁI   BÀN '},remainingSpins:1,starAvailable:true,starSelected:false,spinEffect:null}});await new Promise(r=>setTimeout(r,0));
 check('typed ACK preserves input, conceals answers and hides Submit',()=>page.querySelector('#answer-text').disabled&&!page.querySelector('#submit-answer')&&page.querySelector('#answer-text').value==='  CÁI   BÀN '&&!page.querySelector('#correct-text'));
 page.querySelector('#toggle-standings').click();
 const result={...open,revision:5,phase:'RESULT',serverTimeMs:1001,deadlineEpochMs:2501,player:{...p,alreadyAnswered:true,submittedAnswer:{text:'  CÁI   BÀN '}},question:{...open.question,payload:{acceptedAnswers:['cái bàn']}},results:[{userId:1,outcome:'CORRECT',scoreDelta:20}]};
 fake.gameController.receive({target:{kind:'GAME',id:4},kind:'EVENT',payload:result});
 check('RIDDLE result is Server-correct and board stays hidden',()=>page.querySelector('#answer-text').classList.contains('answer-correct')&&page.querySelector('#correct-text').textContent.includes('cái bàn')&&page.querySelector('#game-leaderboard').hidden&&page.querySelector('#game-toasts').textContent.includes('20'));
 fake.gameController.receive({target:{kind:'GAME',id:4},kind:'EVENT',payload:{...result,revision:6,status:'FINISHED',phase:'FINISHED',hasOfficialWinner:true,winners:[1],endReason:'COMPLETED'}});
 check('last RIDDLE result has shared window before Final',()=>page.dataset.presenting==='true'&&!page.querySelector('#final-summary'));
 fake.gameController.dispose();page.remove();return done;
})()'''

def smoke(cdp,origin,report):
 prefix='ui19_'+secrets.token_hex(4);password=secrets.token_urlsafe(15);report['fixturePrefix']=prefix
 people=[]
 def passed(name):report['smoke'].append(name);print('PASS '+name,flush=True)
 def snapshot(t,gid):return t.eval(f"fetch('/api/games/{gid}/snapshot').then(r=>r.json())")
 def phase(t,index,name):t.wait(f"document.querySelector('#game-page')?.dataset.index==='{index}'&&document.querySelector('#game-page').dataset.phase==='{name}'",timeout=18)
 def answer(t,value,text=False):
  t.wait("!!document.querySelector('#submit-answer')")
  if text:t.fill({'answerText':value})
  else:t.click('[data-option='+value+']')
  t.click('#submit-answer')
 for role in ['author','player1','player2','player3']:
  t=cdp.tab(origin);t.eval('window.confirm=()=>true');t.route('register','#register-form');t.fill({'username':prefix+'_'+role,'displayName':role,'password':password,'confirmPassword':password});t.submit('#register-form');t.wait("!!document.querySelector('#login-form')")
  t.fill({'username':prefix+'_'+role,'password':password});t.submit('#login-form');t.wait("document.querySelector('#connection-status')?.dataset.state==='ready'")
  u=t.eval("fetch('/api/auth/me').then(r=>r.json())");t.eval(old.OBSERVER);people.append((t,u))
 host=people[0][0];players=[t for t,u in people[1:]];report['userIds']=[u['id'] for t,u in people]
 passed('Four UI Register/Login accounts in independent browser contexts')
 host.route('quizzes/new','#quiz-form');host.fill({'title':prefix+' Quiz','visibility':'PUBLIC'})
 for _ in range(9):host.click('#add-question')
 host.eval("document.querySelectorAll('.question-editor').forEach((r,i)=>{r.querySelector('[name=content]').value='Quiz '+(i+1);['A','B','C','D'].forEach(k=>r.querySelector('[name=option'+k+']').value='Option '+k);r.querySelector('[name=correctAnswer]').value='A';})")
 host.submit('#quiz-form');host.wait(r"location.hash.match(/^#\/quiz\/\d+\/edit$/)&&document.querySelectorAll('.question-editor').length===10");quiz=int(host.eval("location.hash.split('/')[2]"));report['quizIds']=[quiz]
 host.route('quizzes/new','#quiz-form');host.fill({'mode':'RIDDLE','title':prefix+' Riddle','visibility':'PRIVATE'});host.click('#add-question')
 host.eval("document.querySelectorAll('.question-editor').forEach((r,i)=>{r.querySelector('[name=content]').value='Có chân không đi? '+(i+1);r.querySelector('[name=acceptedAnswers]').value='cái bàn\\nbàn';})")
 host.submit('#quiz-form');host.wait(r"location.hash.match(/^#\/quiz\/\d+\/edit$/)&&document.querySelectorAll('[name=acceptedAnswers]').length===2");riddle=int(host.eval("location.hash.split('/')[2]"));report['quizIds'].append(riddle)
 host.fill({'title':prefix+' Riddle edited'});host.submit('#quiz-form');host.wait("!!document.querySelector('.question-preview')")
 assert host.eval("document.querySelectorAll('.question-preview .options').length===0")
 host.route('quizzes?scope=MINE','.catalogue-filters');host.wait(f"!!document.querySelector('a[href=\"#/quiz/{riddle}\"]')")
 host.route('quizzes?scope=SHARED','.catalogue-filters');host.wait(f"!!document.querySelector('a[href=\"#/quiz/{quiz}\"]')");assert not host.eval(f"!!document.querySelector('a[href=\"#/quiz/{riddle}\"]')")
 p=players[0];p.route('quiz/'+str(quiz),'.page-heading');p.wait("document.body.innerText.includes('Quiz')");assert p.eval("document.querySelectorAll('.question-preview').length===0")
 assert p.eval(f"fetch('/api/quizzes/{riddle}').then(r=>r.status)")==404
 passed('UI Quiz and PRIVATE RIDDLE create/edit; Mine/Shared include own PUBLIC and conceal other private/answers')
 def start(qcount,rcount,seconds=8):
  host.route('rooms/new?quiz='+str(quiz),'#room-form');host.fill({'name':prefix+' Room '+str(len(report['roomIds'])),'maxPlayers':'3','hostParticipation':'SPECTATOR'})
  host.fill({'questionCount':str(qcount),'seconds':str(seconds)},'.stage-editor:first-child ');host.click('#add-stage')
  host.fill({'quizId':str(riddle),'questionCount':str(rcount),'seconds':str(seconds)},'.stage-editor:last-child ')
  host.click('.stage-editor:last-child [data-move=up]');assert host.eval("document.querySelector('.stage-editor [name=stageMode]').value==='RIDDLE'")
  host.click('.stage-editor:first-child [data-move=down]');assert host.eval("document.querySelector('.stage-editor [name=stageMode]').value==='QUIZ'")
  assert host.eval("document.querySelector('[name=hostParticipation] [value=PLAYER]').disabled")
  host.submit('#room-form');host.wait("!!document.querySelector('#open-room')");rid=int(host.eval("location.hash.split('/')[2]"));report['roomIds'].append(rid)
  assert not host.eval("document.body.innerText.includes('Revision')")
  host.click('#open-room');host.wait("!!document.querySelector('#start-game')");code=host.eval("document.querySelector('#room-code').textContent")
  for t in players:
   t.route('join','#join-form');t.fill({'code':code,'participation':'PLAYER'});t.submit('#join-form');t.wait("!!document.querySelector('#waiting-room')")
  host.wait("!document.querySelector('#start-game').disabled");host.click('#start-game')
  for t in [host,*players]:t.wait("document.querySelector('#game-page')?.dataset.phase==='INTRO'")
  gid=int(host.eval("location.hash.split('/')[2]"));report['gameIds'].append(gid)
  assert snapshot(host,gid)['player'] is None
  assert not host.eval("!!document.querySelector('#continue-stage')")
  for t in players:
   t.wait("!!document.querySelector('#continue-stage')&&!document.querySelector('#continue-stage').disabled");t.click('#continue-stage')
  phase(host,1,'DECISION');assert snapshot(host,gid)['v2Config']['decisionDurationMs']==7000
  return gid,rid
 gid,rid=start(10,2,8)
 passed('Ordered Room plan persists; private Author Host Spectator + 3 Players; early Intro ready and 7s Quiz Decision')
 players[0].click('#use-spin');players[0].wait("document.querySelector('#game-ack')?.textContent==='USE_SPIN'");spin=snapshot(players[0],gid)['player']['currentSpin']
 assert players[0].eval("document.querySelector('#game-toasts').textContent.includes('Spin:')")
 if spin!='HARDSHIP':players[0].click('#use-star');players[0].wait("document.querySelector('#star-available').textContent==='Đã dùng'")
 else:assert players[0].eval("document.querySelector('#use-star').disabled")
 players[1].click('#use-star');players[1].wait("document.querySelector('#use-spin').disabled")
 players[0].click('#toggle-standings')
 report['spinEffect']=spin
 passed('Actual Spin consumed once; optional Star after Spin or HARDSHIP block; standalone Star locks Spin')
 for i in range(1,11):
  phase(host,i,'QUESTION_OPEN');assert snapshot(host,gid)['question']['correctAnswer'] is None
  if i==1:
   answer(players[0],'B');answer(players[1],'A')
   players[0].wait("!!document.querySelector('#answer-accepted')");assert not players[0].eval("!!document.querySelector('#submit-answer')")
   # Third online-at-open Player does not answer: genuine Server timeout.
   host.wait("wire.messages.some(m=>m.type==='QUESTION_RESULT'&&m.questionIndex===1)",timeout=12)
   results=host.eval("wire.messages.find(m=>m.type==='QUESTION_RESULT'&&m.questionIndex===1).payload.results")
   assert next(r for r in results if r['userId']==people[3][1]['id'])['outcome']=='NO_ANSWER'
   players[0].wait("!!document.querySelector('.answer-correct')");assert players[0].eval("document.querySelector('[data-option=B]').classList.contains('answer-wrong')")
   host.screenshot(ROOT/'target/task19-quiz-result.png')
   passed('Answer ACK locks/hides Submit; correct/wrong/NO_ANSWER use committed Server result and green/red Quiz options')
  else:
   if i==2:players[2].eval("wire.dropType='ANSWER'")
   for t in players:answer(t,'A')
   if i==2:
    players[2].wait('!!wire.dropped');original=players[2].eval('wire.dropped.requestId');players[2].eval('gameSocket.close()');players[2].wait("!!document.querySelector('#retry-game-command')")
    players[2].click('#reconnect-room');players[2].wait("document.querySelector('#connection-status')?.dataset.state==='ready'&&!document.querySelector('#retry-game-command').disabled")
    players[2].click('#retry-game-command');players[2].wait(f"document.querySelector('#game-ack')?.dataset.requestId==='{original}'")
    commands=players[2].eval("wire.commands.filter(m=>m.type==='ANSWER'&&m.questionIndex===2)");assert len(commands)==2 and commands[0]==commands[1]
    passed('Real lost ACK: disconnect/reconnect preserves Server Answer; retry sends identical UUID/frame after close')
 phase(host,11,'INTRO');assert players[0].eval("document.querySelector('#game-leaderboard').hidden")
 assert not players[0].eval("!!document.querySelector('#use-spin')||!!document.querySelector('#remaining-spins')")
 for t in players:
   t.wait("!!document.querySelector('#continue-stage')&&!document.querySelector('#continue-stage').disabled");t.click('#continue-stage')
 phase(host,11,'QUESTION_OPEN');assert not players[0].eval("!!document.querySelector('#correct-text')")
 before=snapshot(players[0],gid);players[0].eval('gameSocket.close()');players[0].wait("document.querySelector('#connection-status')?.dataset.state==='offline'")
 # Wait for server elapsed duration, not a sleep used to prove a race.
 host.wait("parseFloat(document.querySelector('#game-countdown').textContent)<6.5")
 players[0].click('#reconnect-room');players[0].wait("document.querySelector('#connection-status')?.dataset.state==='ready'&&!document.querySelector('#answer-text').disabled")
 after=snapshot(players[0],gid);assert after['questionIndex']==11 and after['deadlineEpochMs']==before['deadlineEpochMs'] and after['remainingMs']<before['remainingMs'] and after['player']['remainingSpins']==before['player']['remainingSpins']
 answer(players[0],'  CÁI   BÀN ',True);players[0].wait("!!document.querySelector('#answer-accepted')");assert players[0].eval("document.querySelector('#answer-text').disabled&&!document.querySelector('#submit-answer')")
 answer(players[1],'sai',True);answer(players[2],'bàn',True)
 phase(host,11,'RESULT');players[0].wait("!!document.querySelector('#correct-text')")
 assert players[0].eval("document.querySelector('#answer-text').classList.contains('answer-correct')&&document.querySelector('#game-toasts').textContent.includes('10')")
 assert players[1].eval("document.querySelector('#answer-text').classList.contains('answer-wrong')&&document.querySelector('#game-toasts').textContent.includes('không thay đổi')")
 players[0].screenshot(ROOT/'target/task19-riddle-result.png')
 passed('Automatic Quiz->RIDDLE; board toggle retained; reconnect current text question keeps deadline/resources; normalized correct +10 / wrong 0')
 phase(host,12,'QUESTION_OPEN')
 for t in players:answer(t,'bàn',True)
 host.wait("wire.messages.some(m=>m.type==='GAME_END'&&m.questionIndex===12)")
 assert all(r['scoreDelta']==20 for r in snapshot(host,gid)['results'])
 for t in [host,*players]:t.wait("!!document.querySelector('#final-summary')")
 final=snapshot(players[0],gid);assert final['hasOfficialWinner'] and all(m['score']>=0 for m in final['members'] if m['participation']=='PLAYER')
 assert players[0].eval("document.querySelector('#standings').textContent.includes('Thời gian đúng')&&document.querySelector('#game-leaderboard').hidden")
 players[0].route('history/'+str(gid),'#history-detail');assert players[0].eval("document.querySelectorAll('.history-question').length===12&&document.querySelector('#history-detail').textContent.includes('CÁI   BÀN')")
 host.route('room/'+str(rid),'#waiting-room');host.wait("!!document.querySelector('#start-game')")
 passed('Stage-last +20, shared final ranks/correct-only time, real History text/effect timeline, Room returns WAITING')
 cdp.call('Emulation.setDeviceMetricsOverride',{'width':390,'height':844,'deviceScaleFactor':1,'mobile':True},players[0].session)
 assert players[0].eval("document.documentElement.scrollWidth<=innerWidth")
 players[0].screenshot(ROOT/'target/task19-history-mobile.png')
 passed('Mobile multimode History has no horizontal overflow')
 gid2,rid2=start(1,1,8)
 assert snapshot(players[0],gid2)['player']['remainingSpins']==0
 assert players[0].eval("document.querySelector('#use-spin').disabled&&!document.querySelector('#use-star').disabled")
 players[0].click('#use-star');players[0].wait("document.querySelector('#star-available').textContent==='Đã dùng'")
 phase(host,1,'QUESTION_OPEN')
 for t in players:answer(t,'A')
 phase(host,2,'INTRO')
 for t in players:
   t.wait("!!document.querySelector('#continue-stage')&&!document.querySelector('#continue-stage').disabled");t.click('#continue-stage')
 phase(host,2,'QUESTION_OPEN');answer(players[0],'bàn',True);players[0].wait("!!document.querySelector('#answer-accepted')")
 host.click('#cancel-game')
 for t in [host,*players]:t.wait("!!document.querySelector('#final-summary')")
 assert not snapshot(host,gid2)['hasOfficialWinner']
 players[0].route('history/'+str(gid2),'#history-detail');assert players[0].eval("!!document.querySelector('[data-status=ACCEPTED_UNSCORED]')&&!document.body.innerText.includes('Đáp án đã công bố: cái bàn')")
 passed('Quiz count1 yields zeroSpin with usable Star; Cancel keeps typed Answer ACCEPTED_UNSCORED, no aliases/Official Winner')
 for t,u in people:cdp.call('Target.closeTarget',{'targetId':t.target})

def main():
 p=argparse.ArgumentParser();p.add_argument('--origin',default='http://127.0.0.1:8087');p.add_argument('--port',type=int,default=9224);p.add_argument('--smoke',action='store_true');p.add_argument('--report-name',default='task19-client-tests.json');a=p.parse_args()
 if Path(a.report_name).name!=a.report_name or not a.report_name.endswith('.json'):p.error('--report-name must name a JSON file inside target')
 report={'task':19,'status':'RUNNING','unit':[],'smoke':[],'origin':a.origin,'clients':4,'quizIds':[],'roomIds':[],'gameIds':[]}
 try:
  cdp=browser.CDP(a.port);report['browser']=cdp.version;t=cdp.tab(a.origin);report['unit']=t.eval(old.legacy.UNIT)+t.eval(old.UNIT)+t.eval(UNIT);cdp.call('Target.closeTarget',{'targetId':t.target})
  if a.smoke:smoke(cdp,a.origin,report)
  assert not cdp.errors,cdp.errors;report['status']='PASS'
 except Exception as e:report['status']='FAIL';report['error']=str(e);raise
 finally:
  (ROOT/'target'/a.report_name).write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8');print(json.dumps({'status':report['status'],'unit':len(report['unit']),'smoke':len(report['smoke'])}),flush=True)
if __name__=='__main__':main()
