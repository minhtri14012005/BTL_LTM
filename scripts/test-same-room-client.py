"""T14: three real Games in one Room over HTTP/WS/MySQL; no fake result or timer."""
import argparse,importlib.util,json,secrets,time,uuid
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('browser',ROOT/'scripts/browser-cdp.py');browser=importlib.util.module_from_spec(spec);spec.loader.exec_module(browser)
AUDIT=r'''(()=>{const send=WebSocket.prototype.send,seen=new WeakSet();window.replayFrames=[];window.replaySent=[];
WebSocket.prototype.send=function(text){window.replaySocket=this;if(!seen.has(this)){seen.add(this);this.addEventListener('message',e=>replayFrames.push(JSON.parse(e.data)));}
const frame=JSON.parse(text);replaySent.push(frame);send.call(this,text);
if(frame.type==='START_GAME'&&window.duplicateStart){window.duplicateStart=false;send.call(this,text);const other={...frame,requestId:window.competingStartId};window.otherStartId=other.requestId;send.call(this,JSON.stringify(other));}
};})()'''

def main():
    p=argparse.ArgumentParser();p.add_argument('--origin',default='http://192.168.1.234:8088');p.add_argument('--port',type=int,default=9227);args=p.parse_args()
    cdp=browser.CDP(args.port);tabs=[];report=dict(status='RUNNING',checks=[],gameIds=[],sameMachine=True);prefix='replay_'+secrets.token_hex(4);password=secrets.token_urlsafe(24)
    def passed(text):report['checks'].append(text);print('PASS '+text,flush=True)
    def req(t,method,path,body=None):
        return t.eval('(async()=>{const c=await fetch("/api/auth/csrf").then(r=>r.json());const r=await fetch('+json.dumps(path)+',{method:'+json.dumps(method)+',headers:{"Content-Type":"application/json",[c.headerName]:c.token},'+('body:'+json.dumps(json.dumps(body))+',' if body is not None else '')+'});return {status:r.status,body:await r.text()};})()')
    def api(t,method,path,body=None):
        response=req(t,method,path,body);assert 200<=response['status']<300,(path,response);return json.loads(response['body']) if response['body'] else None
    def player(label):
        t=cdp.tab(args.origin);tabs.append(t);t.eval(AUDIT)
        api(t,'POST','/api/auth/register',dict(username=prefix+'_'+label,displayName='Replay '+label,password=password));t.fill(dict(username=prefix+'_'+label,password=password));t.submit('#login-form');t.wait("document.querySelector('#connection-status')?.dataset.state==='ready'");return t
    def snapshot(t,g):return api(t,'GET',f'/api/games/{g}/snapshot')
    def phase(t,index,name):t.wait(f"document.querySelector('#game-page')?.dataset.index==='{index}'&&document.querySelector('#game-page')?.dataset.phase==='{name}'",timeout=25)
    def join(t,room):
        t.route('join','#join-form');t.fill(dict(code=room['roomCode'],participation='PLAYER'));t.submit('#join-form');t.wait("!!document.querySelector('#waiting-room')")
    def start(host,players,room,spins,star,reconnect=False):
        for t in [host,*players]:t.route('room/'+str(room['id']),'#waiting-room')
        host.wait("!!document.querySelector('#start-game')&&!document.querySelector('#start-game').disabled");host.eval('window.duplicateStart=true;window.competingStartId='+json.dumps(str(uuid.uuid4())));host.click('#start-game')
        for t in [host,*players]:phase(t,1,'INTRO')
        game=int(host.eval("location.hash.split('/')[2]"));report['gameIds'].append(game)
        for t in players:
            own=snapshot(t,game)['player'];assert own['score']==0 and own['remainingSpins']==spins and own['starAvailable']==star and own['winStreak']==0 and own['loseStreak']==0 and not own['momentum'] and not own['recovery'] and not own['starSelected'] and own['currentSpin'] is None and not own['alreadyAnswered'] and own['submittedAnswer'] is None
        if reconnect:
            t=players[0];t.eval('replaySocket.close()');t.wait("document.querySelector('#connection-status')?.dataset.state==='offline'");t.click('#reconnect-room');t.wait("document.querySelector('#connection-status')?.dataset.state==='ready'");phase(t,1,'INTRO')
            restored=snapshot(t,game);assert restored['gameSessionId']==game and restored['player']['score']==0 and len({m['userId'] for m in restored['members']})==len(restored['members'])
            passed('Active Game 2 reconnect restores the same Game and Player without duplicate membership or carried state')
        for t in players:t.wait("!!document.querySelector('#continue-stage')&&!document.querySelector('#continue-stage').disabled");t.click('#continue-stage')
        return game
    def play(players,count,mode,spend=False):
        for index in range(1,count+1):
            if spend and index==1:
                t=players[0];phase(t,index,'DECISION');t.wait("!!document.querySelector('#use-spin')&&!document.querySelector('#use-spin').disabled");t.click('#use-spin');t.wait("document.querySelector('#remaining-spins')?.textContent==='0'&&!!document.querySelector('#use-star')&&!document.querySelector('#use-star').disabled");t.click('#use-star');t.wait("document.querySelector('#star-available')?.textContent==='Đã dùng'")
            for t in players:
                phase(t,index,'QUESTION_OPEN');t.wait("!!document.querySelector('#submit-answer')")
                if mode=='QUIZ':t.click('[data-option=D]')
                else:t.fill(dict(answerText='yes'))
                t.click('#submit-answer');t.wait("!!document.querySelector('#answer-accepted')||document.querySelector('#game-page')?.dataset.phase!=='QUESTION_OPEN'")
        for t in players:t.wait("!!document.querySelector('#final-summary')",timeout=25)
    def returns(host,players):
        host.wait("document.querySelector('.result-room-return')?.dataset.roomStatus==='WAITING'")
        assert host.eval("!!document.querySelector('.result-room-return a[href$=\"/edit\"]')")
        for t in players:
            t.wait("document.querySelector('.result-room-return')?.dataset.roomStatus==='WAITING'");assert t.eval("document.querySelector('.result-room-return a').textContent==='Quay lại phòng'&&!document.querySelector('.result-room-return a[href$=\"/edit\"]')")
            t.click('.result-room-return a');t.wait("!!document.querySelector('#waiting-room')")
        host.click('.result-room-return a');host.wait("!!document.querySelector('#start-game')")
    def edit(host,room,mode,bank,count):
        host.route('history/'+str(report['gameIds'][0]),'#history-detail');host.wait("document.querySelector('.result-room-return')?.dataset.roomStatus==='WAITING'");host.click('.result-room-return a[href$="/edit"]');host.wait("!!document.querySelector('#save-room')")
        host.fill(dict(stageMode=mode));host.fill(dict(quizId=str(bank),questionCount=str(count),seconds='3'));host.submit('#room-form');host.wait("!!document.querySelector('#waiting-room')")
        saved=api(host,'GET',f'/api/rooms/{room["id"]}');assert saved['stages'][0]['quizId']==bank and saved['stages'][0]['questionCount']==count;return saved
    try:
        host,a,b,c,d=[player(x) for x in ['host','a','b','c','d']];players=[a,b,c]
        packs={}
        for mode in ['QUIZ','RIDDLE']:
            q=dict(content='Replay question',options=dict(A='A',B='B',C='C',D='D'),correctAnswer='D') if mode=='QUIZ' else dict(content='Replay question',acceptedAnswers=['yes'])
            packs[mode]=api(host,'POST','/api/quizzes',dict(title=prefix+' '+mode,mode=mode,visibility='PRIVATE',questions=[q for _ in range(10)]))['id']
        room=api(host,'POST','/api/rooms',dict(requestId=str(uuid.uuid4()),config=dict(name=prefix,maxPlayers=4,hostParticipation='SPECTATOR',stages=[dict(mode='QUIZ',quizId=packs['QUIZ'],questionCount=10,questionDurationMs=3000)])))
        host.route('room/'+str(room['id']),'#open-room');host.click('#open-room');host.wait("!!document.querySelector('#start-game')")
        for t in players:join(t,room)
        initial=api(host,'GET',f'/api/rooms/{room["id"]}')
        first=start(host,players,room,1,True);play(players,10,'QUIZ',spend=True)
        a.wait("document.querySelector('.award-panel')?.dataset.awardState==='AWARDS_DECIDED'",timeout=25)
        awards=api(a,'GET',f'/api/games/{first}/awards')['awards'];assert awards
        history=api(a,'GET',f'/api/games/history/{first}');old_answer=a.eval("replaySent.find(f=>f.type==='ANSWER')")
        assert api(host,'GET',f'/api/rooms/{room["id"]}')['members']==initial['members']
        returns(host,players);passed('Completed Game 1 returns WAITING with exact membership; Host/Player return controls and committed awards are real')

        before=api(a,'GET',f'/api/rooms/{room["id"]}')['members'];a.eval('replaySocket.close()');a.wait("document.querySelector('#connection-status')?.dataset.state==='offline'");a.click('#reconnect-room');a.wait("document.querySelector('#connection-status')?.dataset.state==='ready'");a.wait("document.querySelector('.member-connection')?.textContent==='Đã đồng bộ phòng'")
        assert api(a,'GET',f'/api/rooms/{room["id"]}')['members']==before
        passed('Waiting disconnect/reconnect keeps membership IDs and one roster entry per User')
        c.click('#leave-room');c.wait("location.hash==='#/rooms'");host.wait("document.querySelector('#start-game')?.disabled")
        current=api(host,'GET',f'/api/rooms/{room["id"]}');denied=req(host,'POST',f'/api/rooms/{room["id"]}/start',dict(requestId=str(uuid.uuid4()),revision=current['revision'],questionCount=10));assert denied['status']==409
        assert req(c,'GET',f'/api/rooms/{room["id"]}')['status']==403 and api(c,'GET',f'/api/games/{first}/awards')['awards']==awards
        join(d,room);players=[a,b,d];room=edit(host,room,'RIDDLE',packs['RIDDLE'],2)
        passed('Leave reduces roster below minimum and Server rejects START; replacement joins, Host changes real pack through existing editor')
        second=start(host,players,room,0,False,reconnect=True)
        # A late replay targets only old Game 1 and must not produce an Answer in Game 2.
        count='replayFrames.filter(f=>f.kind==="ACK"&&f.requestId==='+json.dumps(old_answer['requestId'])+').length'
        a.eval('window.oldAckCount='+count+';replaySocket.send('+json.dumps(json.dumps(old_answer))+')');a.wait(count+'>window.oldAckCount')
        own=snapshot(a,second)['player'];assert own['score']==0 and not own['alreadyAnswered'] and own['submittedAnswer'] is None
        play(players,2,'RIDDLE');returns(host,players)
        passed('Game 2 starts score/resources/Answer fresh after pack change; stale Game 1 Answer replay cannot change Game 2')
        room=edit(host,room,'QUIZ',packs['QUIZ'],1);third=start(host,players,room,0,True);play(players,1,'QUIZ');returns(host,players)
        passed('Game 3 completes in the same Room with newly computed Quiz resources and no carried score/streak/effect')
        listing=api(host,'GET','/api/games/history?size=100')['items'];assert len(listing)==3 and {x['gameSessionId'] for x in listing}==set(report['gameIds']) and len(set(report['gameIds']))==3
        for game in report['gameIds']:
            events=host.eval('replayFrames.filter(f=>f.type==="GAME_STARTED"&&f.target.id==='+str(game)+')');assert len(events)==1
        assert host.eval('replayFrames.some(f=>f.kind==="ERROR"&&f.requestId===window.otherStartId)')
        passed('Repeated same-request and competing START produce exactly three Games and one GAME_STARTED per Game')
        after=api(a,'GET',f'/api/games/history/{first}')
        for key in ['questions','decisions','restrictions','startedAtMs','finishedAtMs']:assert after.get(key)==history.get(key),key
        assert after['finalSnapshot']['members']==history['finalSnapshot']['members'] and api(a,'GET',f'/api/games/{first}/awards')['awards']==awards
        assert api(c,'GET',f'/api/games/history/{first}')['questions']==history['questions']
        current=api(host,'GET',f'/api/rooms/{room["id"]}');api(host,'POST',f'/api/rooms/{room["id"]}/close',dict(requestId=str(uuid.uuid4()),revision=current['revision']))
        a.route('history/'+str(first),'#history-detail');a.wait("document.querySelector('.result-room-return')?.dataset.roomStatus==='CLOSED'");assert not a.eval("!!document.querySelector('.result-room-return a[href$=\"/edit\"]')")
        assert api(a,'GET',f'/api/games/{first}/awards')['awards']==awards
        cdp.call('Emulation.setDeviceMetricsOverride',dict(width=360,height=1000,deviceScaleFactor=1,mobile=False),a.session);assert a.eval('document.documentElement.scrollWidth<=innerWidth');a.eval("document.querySelector('.result-room-return').scrollIntoView({block:'center'})");a.screenshot(ROOT/'target/t14-closed-history-360.png')
        passed('Game 1 History/Awards and departed Player access survive two new Games and Room close; 360px remains usable')
        assert not cdp.errors,cdp.errors;report.update(status='PASS',browser=cdp.version)
    except Exception as e:report.update(status='FAIL',error=str(e));raise
    finally:
        (ROOT/'target/t14-same-room-client-tests.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
        for t in tabs:cdp.call('Target.disposeBrowserContext',dict(browserContextId=t.context))
        cdp.socket.close();print(json.dumps(dict(status=report['status'],checks=len(report['checks']))))

if __name__=='__main__':main()
