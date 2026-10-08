"""Task12: native modules + DOM gameplay against real HTTP/raw WS/MySQL.
Reuses the owned Chrome fixture/tooling of Task11A; no mocked API/clock/engine.
"""
import argparse
import importlib.util
import json
import secrets
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('client_tests', ROOT/'scripts/test-client.py')
legacy=importlib.util.module_from_spec(spec);spec.loader.exec_module(legacy)
browser=legacy.browser

UNIT=r'''(async()=>{
 const g=await import('/client/game-state.js');const ui=await import('/client/game.js');
 const done=[];const check=(n,f)=>{if(!f())throw Error(n);done.push(n);};
 const p={userId:1,state:'PLAYING',remainingSpins:1,currentSpin:null,starSelected:false,starAvailable:true,alreadyAnswered:false};
 const s={gameSessionId:7,roomId:2,revision:4,questionIndex:1,status:'ACTIVE',runtimeState:'READY',phase:'DECISION',player:p,members:[{userId:1,role:'HOST',participation:'PLAYER',displayName:'Host',rank:1,score:20,totalAnswerTimeMs:0,playerState:'PLAYING'}],winners:[],hasOfficialWinner:false};
 check('canonical Game envelope and captured UUID',()=>{let c=g.gameCommand('ANSWER',7,1,{option:'B'},'same');return c.target.kind==='GAME'&&c.questionIndex===1&&c.requestId==='same'&&Object.isFrozen(c.payload);});
 check('Spin then optional Star',()=>g.permissions(s,1,true).spin&&g.permissions({...s,player:{...p,currentSpin:'BONUS',remainingSpins:0}},1,true).star);
 check('Star alone locks Spin',()=>!g.permissions({...s,player:{...p,starSelected:true,starAvailable:false}},1,true).spin);
 check('HARDSHIP forbids Star',()=>!g.permissions({...s,player:{...p,currentSpin:'HARDSHIP'}},1,true).star);
 check('Host eliminated retains Cancel, no actions',()=>{let a=g.permissions({...s,phase:'QUESTION_OPEN',player:{...p,state:'ELIMINATED'}},1,true);return a.cancel&&!a.answer&&!a.spin&&!a.star;});
 check('Host Spectator null player renders and can Cancel',()=>g.permissions({...s,player:null},1,true).cancel&&!g.permissions({...s,player:null},1,true).spin);
 check('member no Cancel and offline no actions',()=>!g.permissions(s,2,true).cancel&&!g.permissions(s,1,false).spin);
 check('alreadyAnswered and pending block actions',()=>!g.permissions({...s,phase:'QUESTION_OPEN',player:{...p,alreadyAnswered:true}},1,true).answer&&!g.permissions(s,1,true,true).spin);
 check('finished and unavailable forbid new actions',()=>!g.permissions({...s,status:'FINISHED'},1,true).cancel&&!g.permissions({...s,runtimeState:'UNAVAILABLE'},1,true).answer);
 check('old snapshot cannot roll back newer revision',()=>g.acceptSnapshot(s,{...s,revision:3})===s);
 check('same revision older server time cannot restore a result window',()=>g.acceptSnapshot({...s,serverTimeMs:100},{...s,serverTimeMs:99})?.serverTimeMs===100);
 check('leaderboard seconds and current user marker',()=>{let n=ui.standings({...s,members:[{...s.members[0],totalAnswerTimeMs:11706}]},1);return n.textContent.includes('11.706 s')&&n.querySelector('.current-player')&&n.querySelectorAll('th').length===4;});
 check('equal revision event remains consumable',()=>g.acceptSnapshot(s,{...s,phase:'RESULT'}).phase==='RESULT');
 check('receipt from older phase cannot restore resources',()=>g.applyReceipt({...s,revision:8,questionIndex:2},{target:{id:7},type:'USE_SPIN',revision:5,questionIndex:1,payload:{remainingSpins:0}}).revision===8);
 check('receipt updates own resource without inventing score',()=>{let x=g.applyReceipt(s,{target:{id:7},type:'USE_SPIN',revision:5,questionIndex:1,payload:{spinEffect:'SAFE',remainingSpins:0,starSelected:false,starAvailable:true,remainingSpinPool:[],alreadyAnswered:false,selectedOption:null}});return x.player.currentSpin==='SAFE'&&x.player.remainingSpins===0&&x.phase==='DECISION';});
 check('countdown uses Server anchor and does not change state',()=>g.countdown(1100,1000,20,119)===1&&g.countdown(1100,1000,20,120)===0&&g.countdown(null,1000,20,120)===null&&s.phase==='DECISION');
 check('CANCELLED/SERVER_INTERRUPTED no official winner',()=>['CANCELLED','SERVER_INTERRUPTED'].every(e=>{let n=ui.finalSummary({...s,status:'FINISHED',endReason:e});return n.textContent.includes('không có Official Winner')&&!n.querySelector('.winner');}));
 check('official co-winners rendered from Server ids',()=>{const members=[s.members[0],{...s.members[0],userId:2,displayName:'Peer'},{...s.members[0],userId:3,rank:3,displayName:'Third'}];return ui.standings({...s,members,hasOfficialWinner:true,winners:[1,2]}).querySelectorAll('.winner').length===2&&ui.standings({...s,members,hasOfficialWinner:false,winners:[]}).querySelectorAll('.winner').length===0;});
 check('RESULT snapshot renders effects and result',()=>ui.resultPanel({index:1,question:{correctAnswer:'A',options:{A:'a'}},results:[{userId:1,outcome:'CORRECT',scoreDelta:10,scoreAfter:30,answerTimeMs:4,winStreak:1,loseStreak:0,hasMomentumAfter:false,hasRecoveryAfter:false,momentumGranted:true}]},s.members).textContent.includes('cho câu sau'));
 // Component tests use controlled promises; real HTTP/WS/MySQL smoke is recorded separately.
 const openState={...s,gameSessionId:42,questionCount:10,quizTitleSnapshot:'DOM unit',serverTimeMs:10000,deadlineEpochMs:20000,results:[],phase:'QUESTION_OPEN',question:{content:'Q',options:{A:'a',B:'b',C:'c',D:'d'},correctAnswer:null},player:{...p,score:20,selectedOption:null}};
 let resolveAction,rejectAction;const fake={user:{id:1},games:new Map(),transport:{status:'ready',send:c=>c.type==='RECONNECT'?Promise.resolve({payload:openState}):new Promise((resolve,reject)=>{resolveAction=resolve;rejectAction=reject;})},api:{request:async()=>openState},connect:()=>{}};
 const page=await ui.gamePage(fake,42);document.body.append(page);await new Promise(r=>setTimeout(r,0));
 page.querySelector('[data-option=A]').click();page.querySelector('#submit-answer').click();
 check('pending Answer locks duplicate submission without acceptance',()=>page.querySelector('#submit-answer').disabled&&!page.querySelector('#answer-accepted'));
 rejectAction(Object.assign(Error('Rejected test action'),{retryable:false}));await new Promise(r=>setTimeout(r,0));
 check('Answer rejection restores choices and does not invent accepted state',()=>!page.querySelector('[data-option=A]').disabled&&!page.querySelector('#answer-accepted'));
 page.querySelector('#submit-answer').click();
 resolveAction({requestId:'accepted-unit',type:'ANSWER',target:{id:42},questionIndex:1,revision:5,payload:{selectedOption:'A',alreadyAnswered:true,spinEffect:null,starSelected:false,remainingSpins:1,starAvailable:true,remainingSpinPool:[]}});
 await new Promise(r=>setTimeout(r,0));
 check('accepted Answer hides submit and still conceals correctness',()=>!page.querySelector('#submit-answer')&&page.querySelector('[data-option=A]').disabled&&!page.querySelector('.answer-correct')&&page.querySelector('#answer-accepted'));
 page.querySelector('#toggle-standings').click();
 const result={...openState,members:[{...openState.members[0],score:16}],revision:6,phase:'RESULT',serverTimeMs:10001,deadlineEpochMs:11501,question:{...openState.question,correctAnswer:'B'},results:[{userId:1,outcome:'WRONG',scoreDelta:-4}],player:{...openState.player,score:16,alreadyAnswered:true,selectedOption:'A'}};
 fake.gameController.receive({target:{kind:'GAME',id:42},kind:'EVENT',payload:result});
 check('result uses Server answer/delta and hidden leaderboard updates',()=>page.querySelector('.answer-correct').dataset.option==='B'&&page.querySelector('.answer-wrong').dataset.option==='A'&&page.querySelector('#game-leaderboard').hidden&&page.querySelector('#standings tbody').textContent.includes('16')&&page.querySelector('#game-toasts').textContent.includes('trừ 4'));
 await new Promise(r=>setTimeout(r,50));const before=page.querySelector('#game-countdown').textContent;
 fake.gameController.receive({target:{kind:'GAME',id:42},kind:'EVENT',payload:result});
 check('duplicate result does not restart its shared countdown',()=>parseFloat(page.querySelector('#game-countdown').textContent)<=parseFloat(before));
 fake.gameController.dispose();page.remove();
 return done;
})()'''

OBSERVER=r'''(()=>{window.wire={commands:[],messages:[],dropType:null};const send=WebSocket.prototype.send;WebSocket.prototype.send=function(text){const c=JSON.parse(text);wire.commands.push(c);window.gameSocket=this;if(!this.observed){this.observed=true;const receive=this.onmessage;this.onmessage=function(event){const m=JSON.parse(event.data);wire.messages.push(m);if(wire.dropType===m.type&&m.kind==='ACK'){wire.dropType=null;wire.dropped=m;return;}receive.call(this,event);};}return send.call(this,text);};})()'''

def smoke(cdp,origin,report):
    prefix='ui12_'+secrets.token_hex(4);password=secrets.token_urlsafe(15)
    report['fixturePrefix']=prefix;report['quizIds']=[];report['roomIds']=[];report['gameIds']=[]
    def passed(name):report['smoke'].append(name);print('PASS '+name,flush=True)
    people=[]
    for role in ['author','player1','player2','player3']:
        t=cdp.tab(origin);t.route('register','#register-form');t.fill({'username':prefix+'_'+role,'displayName':role,'password':password,'confirmPassword':password});t.submit('#register-form');t.wait("!!document.querySelector('#login-form')")
        t.fill({'username':prefix+'_'+role,'password':password});t.submit('#login-form');t.wait("document.querySelector('#connection-status')?.dataset.state==='ready'")
        u=t.eval("fetch('/api/auth/me').then(r=>r.json())");t.eval(OBSERVER);people.append((t,u))
    report['userIds']=[u['id'] for t,u in people];author=people[0][0];players=[t for t,u in people[1:]]
    passed('Four real accounts in isolated Chrome contexts, cookie authentication')
    author.route('quizzes/new','#quiz-form');author.fill({'title':prefix+' quiz','visibility':'PUBLIC'})
    for _ in range(9):author.click('#add-question')
    author.eval("document.querySelectorAll('.question-editor').forEach((r,i)=>{r.querySelector('[name=content]').value='Task12 question '+(i+1);['A','B','C','D'].forEach(k=>r.querySelector('[name=option'+k+']').value='Option '+k);r.querySelector('[name=correctAnswer]').value='A';})")
    author.submit('#quiz-form');author.wait(r"location.hash.match(/^#\/quiz\/\d+\/edit$/) && document.querySelectorAll('.question-editor').length===10")
    quiz=int(author.eval("location.hash.split('/')[2]"));report['quizIds'].append(quiz)
    fixture=ROOT/'target/update-image.png';legacy.png(fixture);author.upload('.question-editor [name=image]',fixture);author.submit('#quiz-form');author.wait("!!document.querySelector('.question-preview img')")
    def start(host,roster,spectator):
        host.route(f'rooms/new?quiz={quiz}','#room-form');host.fill({'name':prefix+' room '+str(len(report['roomIds'])),'maxPlayers':'3','seconds':'2.5','hostParticipation':'SPECTATOR' if spectator else 'PLAYER'});host.submit('#room-form');host.wait("!!document.querySelector('#open-room')")
        room=int(host.eval("location.hash.split('/')[2]"));report['roomIds'].append(room);host.click('#open-room');host.wait("!!document.querySelector('#start-game')")
        code=host.eval("document.querySelector('#room-code').textContent")
        for t in roster:
            if t is host:continue
            t.route('join','#join-form');t.fill({'code':code,'participation':'PLAYER'});t.submit('#join-form');t.wait("!!document.querySelector('#waiting-room')")
        host.wait("!document.querySelector('#start-game').disabled");host.click('#start-game');host.wait("!!document.querySelector('#game-page') && !!document.querySelector('#use-spin')")
        gid=int(host.eval("location.hash.split('/')[2]"));report['gameIds'].append(gid)
        assert snapshot(host,gid)['config']['decisionDurationMs']==7000
        for t in roster:t.wait("!!document.querySelector('#game-page') && !document.querySelector('#use-spin').disabled")
        return gid,room
    def phase(t,index,name):t.wait(f"document.querySelector('#game-page')?.dataset.index==='{index}' && document.querySelector('#game-page').dataset.phase==='{name}'",timeout=16)
    def answer(t,option):t.wait("!!document.querySelector('#submit-answer') && !document.querySelector('[data-option=A]').disabled");t.click(f'[data-option={option}]');t.click('#submit-answer')
    def snapshot(t,gid):return t.eval(f"fetch('/api/games/{gid}/snapshot').then(r=>r.json())")
    # Complete a real 10-question match, participant Host and three independent Players.
    host=players[0];gid,room=start(host,players,False)
    phase(host,1,'DECISION');host.wait("!document.querySelector('#use-spin').disabled");host.click('#use-spin');host.wait("!!document.querySelector('#game-ack')")
    spin=snapshot(host,gid)['player']['currentSpin']
    host.wait("document.querySelector('#game-toasts').textContent.includes('Spin:')")
    host.screenshot(ROOT/'target/update-decision.png')
    host.click('#toggle-standings');assert host.eval("document.querySelector('#game-leaderboard').hidden")
    if spin!='HARDSHIP':host.click('#use-star');host.wait("document.querySelector('#star-available').textContent==='Đã dùng'")
    else:assert host.eval("document.querySelector('#use-star').disabled")
    phase(players[1],1,'DECISION');players[1].click('#use-star');players[1].wait("document.querySelector('#use-spin').disabled && document.querySelector('#star-available').textContent==='Đã dùng'")
    players[1].eval("gameSocket.close()");players[1].wait("!!document.querySelector('#reconnect-room')");players[1].click('#reconnect-room');players[1].wait("document.querySelector('#connection-status')?.dataset.state==='ready' && document.querySelector('#star-available')?.textContent==='Đã dùng'")
    assert snapshot(players[1],gid)['player']['starSelected'] is True
    players[1].wait("!document.querySelector('#game-toasts').textContent.includes('Hope Star')",timeout=4)
    players[1].eval("gameSocket.close()");players[1].wait("!!document.querySelector('#reconnect-room')");players[1].click('#reconnect-room')
    players[1].wait("document.querySelector('#connection-status')?.dataset.state==='ready' && document.querySelector('#star-available')?.textContent==='Đã dùng'")
    assert players[1].eval("!document.querySelector('#game-toasts').textContent.includes('Hope Star')")
    passed('Spin consumes once, optional Star follows; Star alone locks Spin; reconnect no old toast; actual effect '+spin)
    # Lost genuine ANSWER ACK: accepted data stays durable while UI retains the original UUID.
    players[2].eval("wire.dropType='ANSWER'")
    seen_image=False
    for i in range(1,11):
        phase(host,i,'QUESTION_OPEN')
        assert not host.eval("document.querySelector('#game-page').innerText.split('\\n').some(line=>line.trim()==='null')")
        assert snapshot(host,gid)['question']['correctAnswer'] is None
        if host.eval("!!document.querySelector('.game-question img')"):
            host.wait("document.querySelector('.game-question img')?.naturalWidth>0");seen_image=True
        if i==1:
            for t in players:answer(t,'A')
            players[2].wait("!!wire.dropped")
            original=players[2].eval("wire.dropped.requestId")
            players[2].eval("gameSocket.close()")
            players[2].wait("!!document.querySelector('#retry-game-command')")
            players[2].click('#reconnect-room');players[2].wait("document.querySelector('#connection-status')?.dataset.state==='ready' && !document.querySelector('#retry-game-command').disabled")
            players[2].click('#retry-game-command');players[2].wait(f"document.querySelector('#game-ack')?.dataset.requestId==='{original}'")
            commands=players[2].eval("wire.commands.filter(c=>c.type==='ANSWER'&&c.questionIndex===1)");assert len(commands)==2 and commands[0]==commands[1]
            passed('Lost real Answer ACK, disconnect/reconnect and same UUID/frame replay after scoring')
        elif i==2:
            for t in players[:2]:answer(t,'A')
            # Offline Player remains in roster and receives NO_ANSWER at Server deadline.
            players[2].eval("gameSocket.close()");players[2].wait("document.querySelector('#connection-status')?.dataset.state==='offline'")
            host.wait("wire.messages.some(m=>m.type==='QUESTION_RESULT'&&m.questionIndex===2)")
            result=host.eval("wire.messages.find(m=>m.type==='QUESTION_RESULT'&&m.questionIndex===2).payload.results")
            assert next(r for r in result if r['userId']==people[3][1]['id'])['outcome']=='NO_ANSWER'
            players[2].click('#reconnect-room');players[2].wait("document.querySelector('#connection-status')?.dataset.state==='ready'")
            passed('Offline Player counted through deadline; NO_ANSWER and phase advance while disconnected')
        elif i==3:
            answer(host,'B');answer(players[1],'A');answer(players[2],'A')
            phase(host,i,'RESULT')
            assert host.eval("document.querySelector('[data-option=B]').classList.contains('answer-wrong') && document.querySelector('[data-option=A]').classList.contains('answer-correct')")
            assert host.eval("document.querySelector('#game-leaderboard').hidden && !document.querySelector('#question-result') && !document.querySelector('#submit-answer')")
            host.wait("document.querySelector('#game-toasts').textContent.includes('trừ 4')")
            host.screenshot(ROOT/'target/update-result.png')
            host.click('#toggle-standings');assert not host.eval("document.querySelector('#game-leaderboard').hidden")
            cdp.call('Emulation.setDeviceMetricsOverride',{'width':390,'height':844,'deviceScaleFactor':1,'mobile':True},host.session)
            assert host.eval("document.documentElement.scrollWidth<=innerWidth")
            host.screenshot(ROOT/'target/update-game-mobile.png')
            cdp.call('Emulation.setDeviceMetricsOverride',{'width':1440,'height':1000,'deviceScaleFactor':1,'mobile':False},host.session)
            passed('Shared RESULT: chosen wrong red/correct green, actual delta -4, hidden leaderboard updated, responsive gameplay')
        else:
            for t in players:answer(t,'A')
    for t in players:t.wait("!!document.querySelector('#final-summary')")
    end=snapshot(host,gid);assert end['status']=='FINISHED' and end['endReason']=='COMPLETED' and end['hasOfficialWinner']
    assert seen_image
    assert next(m for m in end['members'] if m['userId']==people[3][1]['id'])['score']==112
    assert all(t.eval("document.querySelectorAll('#standings tbody tr').length")==3 for t in players)
    host.screenshot(ROOT/'target/update-final.png')
    for t in players:
        t.route('history','#history-list');t.wait("document.querySelector('#history-list').textContent.includes('Trận #"+str(gid)+"')");t.route(f'history/{gid}','#history-detail');assert t.eval("document.querySelectorAll('.history-question').length")==10
    assert host.eval("document.querySelector('#history-detail').textContent.includes('Không trả lời')")
    host.route(f'room/{room}','#waiting-room');host.wait("!!document.querySelector('#start-game')")
    passed('Three clients completed ten questions; image/private OPEN, expected score112, official Final/History/Room WAITING')
    # Host Author Spectator, three Players; Cancel after one accepted Answer before scoring.
    gid,room=start(author,players,True);phase(author,1,'DECISION')
    assert author.eval("!document.querySelector('#use-spin').disabled") is False
    assert snapshot(author,gid)['player'] is None
    players[0].click('#use-spin');players[0].wait("document.querySelector('#remaining-spins').textContent==='0'")
    before=snapshot(players[0],gid)['player'];players[0].eval("gameSocket.close()");players[0].wait("!!document.querySelector('#reconnect-room')");players[0].click('#reconnect-room');players[0].wait("document.querySelector('#connection-status')?.dataset.state==='ready'")
    after=snapshot(players[0],gid)['player'];assert before['remainingSpins']==after['remainingSpins'] and before['currentSpin']==after['currentSpin']
    phase(author,1,'QUESTION_OPEN');answer(players[0],'D');players[0].wait("document.querySelector('#game-ack')?.textContent.includes('ANSWER')")
    author.eval("window.confirm=()=>true");author.click('#cancel-game');author.wait("!!document.querySelector('#final-summary')")
    end=snapshot(author,gid);assert end['endReason']=='CANCELLED' and not end['hasOfficialWinner'] and not end['winners']
    players[0].route(f'history/{gid}','#history-detail');players[0].wait("!!document.querySelector('[data-status=ACCEPTED_UNSCORED]')")
    assert players[0].eval("document.querySelectorAll('.history-question').length")==1
    assert 'chưa chấm' in players[0].eval("document.querySelector('[data-status=ACCEPTED_UNSCORED]').textContent")
    author.route(f'room/{room}','#waiting-room');author.wait("!!document.querySelector('#start-game')")
    passed('Host Spectator + three Players; Spin reconnect preserves resources; Cancel keeps Accepted-Unscored, no Winner')
    # Six wrong answers eliminate the Host; observing and cancelling remain available.
    gid,room=start(host,players,False)
    for i in range(1,7):
        phase(host,i,'QUESTION_OPEN');answer(host,'B');answer(players[1],'A');answer(players[2],'A')
    host.wait("document.querySelector('#player-state').textContent.includes('Bạn đã bị loại')")
    assert snapshot(host,gid)['player']['score']==-1
    phase(host,7,'DECISION');assert host.eval("document.querySelector('#use-spin').disabled && document.querySelector('#use-star').disabled && !!document.querySelector('#cancel-game')")
    replacement=cdp.tab(origin,host.context);replacement.route(f'game/{gid}','#game-page');replacement.wait("!!document.querySelector('#cancel-game') && !document.querySelector('#cancel-game').disabled")
    host.wait("document.querySelector('#connection-status')?.dataset.state==='replaced'")
    assert host.eval("document.querySelector('#use-spin').disabled")
    assert snapshot(replacement,gid)['player']['score']==-1
    replacement.eval("window.confirm=()=>true");replacement.click('#cancel-game');replacement.wait("!!document.querySelector('#final-summary')")
    replacement.route(f'history/{gid}','#history-detail');assert replacement.eval("document.querySelector('#history-detail').textContent.includes('Đã bị loại')")
    cdp.call('Emulation.setDeviceMetricsOverride',{'width':390,'height':844,'deviceScaleFactor':1,'mobile':True},replacement.session)
    assert replacement.eval("document.documentElement.scrollWidth<=innerWidth")
    replacement.screenshot(ROOT/'target/update-history-mobile.png')
    passed('Host eliminated score frozen; replacement disables old socket; new socket can Cancel; mobile History renders')
    # Permission and expired authentication are actual HTTP outcomes, no mock response.
    author.route(f'history/{gid}','[role=alert]');assert author.eval("document.body.innerText.includes('không có quyền')")
    players[1].route(f'game/{gid}','#game-page');players[1].reload();players[1].wait("!!document.querySelector('#final-summary')")
    players[1].click('#logout');players[1].wait("!!document.querySelector('#login-form')")
    assert players[1].eval("fetch('/api/games/history').then(r=>r.status)")==401
    passed('Outsider History denied; FINISHED reconnect/reload; logout invalidates History access')
    players[2].route(f'game/{gid}','#game-page');players[2].wait("!!document.querySelector('#final-summary')")
    players[2].eval("(async()=>{const c=await fetch('/api/auth/csrf').then(r=>r.json());return fetch('/api/auth/logout',{method:'POST',headers:{[c.headerName]:c.token}}).then(r=>r.status);})()")
    players[2].wait("!!document.querySelector('#login-form')")
    passed('Server session revoked outside App: WS expires and Game returns to Login')
    for t,u in people:cdp.call('Target.closeTarget',{'targetId':t.target})
    cdp.call('Target.closeTarget',{'targetId':replacement.target})

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--origin',default='http://127.0.0.1:8080');parser.add_argument('--port',type=int,default=9223);parser.add_argument('--smoke',action='store_true');parser.add_argument('--close-browser',action='store_true');parser.add_argument('--report-name',default='task12-client-tests.json');a=parser.parse_args()
    if Path(a.report_name).name!=a.report_name or not a.report_name.endswith('.json'):parser.error('--report-name must be a JSON filename inside target')
    report={'status':'RUNNING','unit':[],'smoke':[]}
    try:
        cdp=browser.CDP(a.port);report['browser']=cdp.version;t=cdp.tab(a.origin);report['unit']=t.eval(legacy.UNIT)+t.eval(UNIT);cdp.call('Target.closeTarget',{'targetId':t.target})
        if a.smoke:smoke(cdp,a.origin,report)
        assert not cdp.errors,cdp.errors;report['status']='PASS'
    except Exception as e:report['status']='FAIL';report['error']=str(e);raise
    finally:
        (ROOT/'target'/a.report_name).write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8');print(json.dumps({'status':report['status'],'unit':len(report['unit']),'smoke':len(report['smoke'])}),flush=True)
        if a.close_browser:
            try:cdp.call('Browser.close')
            except (RuntimeError,OSError,UnboundLocalError):pass
if __name__=='__main__':main()
