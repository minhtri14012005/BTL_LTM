"""Task24 integration: real seven-stage UI and single/no-Quiz games.
Uses existing Chrome/CDP fixtures and all89 component regressions. No API mocks.
Timing observes Server phases/deadlines; deterministic race proofs stay in Java IT.
"""
import argparse
import importlib.util
import json
import secrets
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('clues_tests',ROOT/'scripts/test-clues-client.py')
base=importlib.util.module_from_spec(spec);spec.loader.exec_module(base)
browser=base.browser


def smoke(cdp,origin,report):
    prefix='ui'+str(report['task'])+'_'+secrets.token_hex(4);password=secrets.token_urlsafe(15)
    report['fixturePrefix']=prefix
    people=[];sets={};sources={}
    def passed(name):
        report['smoke'].append(name);print('PASS '+name,flush=True)
    def snapshot(t,gid):
        return t.eval(f"fetch('/api/games/{gid}/snapshot').then(r=>r.json())")
    def phase(t,i,p):
        t.wait(f"document.querySelector('#game-page')?.dataset.index==='{i}'&&document.querySelector('#game-page').dataset.phase==='{p}'",timeout=32)
    def setup_observer(t):
        cdp.call('Network.setCacheDisabled',{'cacheDisabled':True},t.session)
        cdp.call('Page.addScriptToEvaluateOnNewDocument',{'source':base.base.old.OBSERVER},t.session)
        t.eval(base.base.old.OBSERVER);t.eval('window.confirm=()=>true')
    def login(t,name):
        t.route('login','#login-form');t.fill({'username':name,'password':password});t.submit('#login-form')
        t.wait("document.querySelector('#connection-status')?.dataset.state==='ready'")
    for role in ['author','p1','p2','p3']:
        t=cdp.tab(origin);setup_observer(t);name=prefix+'_'+role
        t.route('register','#register-form');t.fill({'username':name,'displayName':'Task'+str(report['task'])+' '+role,'password':password,'confirmPassword':password});t.submit('#register-form')
        t.wait("!!document.querySelector('#login-form')");login(t,name)
        user=t.eval("fetch('/api/auth/me').then(r=>r.json())");people.append((t,name,user['id']))
    host=people[0][0];players=[p[0] for p in people[1:]]
    report['userIds']=[p[2] for p in people]
    modes=['QUIZ','SONG','VIETNAMESE_PUZZLE','RIDDLE','CLUES','IMAGE_WORD','ORDERING']
    fixture=ROOT/('target/'+report['outputPrefix']+'-image-fixture.png');base.base.base.png(fixture,(24,124,218))
    for mode in modes:
        host.route('quizzes/new','#quiz-form');host.fill({'mode':mode,'title':prefix+' '+mode,'visibility':'PRIVATE'})
        count=10 if mode=='QUIZ' else 1
        for number in range(count):
            if number:host.click('#add-question')
            row='.question-editor:last-child '
            host.fill({'content':mode+' '+str(number+1)},row)
            if mode=='QUIZ':host.fill({'optionA':'Đúng','optionB':'Sai B','optionC':'Sai C','optionD':'Sai D','correctAnswer':'A'},row)
            elif mode in ['VIETNAMESE_PUZZLE','ORDERING']:
                host.fill({'itemText':'bắt ' if mode=='VIETNAMESE_PUZZLE' else 'một'},row+'.arrangement-editor-row:first-child ')
                host.fill({'itemText':'cá' if mode=='VIETNAMESE_PUZZLE' else 'hai'},row+'.arrangement-editor-row:last-child ')
                if mode=='VIETNAMESE_PUZZLE':host.fill({'acceptedAnswers':'bắt cá'},row)
            else:
                host.fill({'acceptedAnswers':'bắt cá\nđánh bắt cá'},row)
                if mode=='SONG':host.upload(row+'[name=video]',ROOT/'src/test/resources/quiz/song-av.mp4')
                if mode=='IMAGE_WORD':host.upload(row+'[name=image]',fixture)
                if mode=='CLUES':
                    host.fill({'hintSeconds':'0','hintText':'Đã mở đầu'},row+'.clue-editor:first-child ')
                    for sec,text in [(1,'Đã mở sau'),(6,'Gợi ý tương lai')]:
                        host.click(row+'.add-hint');host.fill({'hintSeconds':str(sec),'hintText':text},row+'.clue-editor:last-child ')
        host.submit('#quiz-form');host.wait("location.hash.endsWith('/edit')&&!!document.querySelector('#quiz-form')")
        sid=int(host.eval("location.hash.split('/')[2]"));sets[mode]=sid
        sources[mode]=host.eval(f"fetch('/api/quizzes/{sid}').then(r=>r.json())")
        assert sources[mode]['mode']==mode and len(sources[mode]['questions'])==count
        assert players[0].eval(f"fetch('/api/quizzes/{sid}').then(r=>r.status)")==404
    report['quizIds']=list(sets.values());passed('Author UI creates all seven real sets, local video/image uploads, PRIVATE source denied to Players')

    def create_room(plan,label):
        host.route('rooms/new?quiz='+str(sets[plan[0][0]]),'#room-form')
        host.fill({'name':prefix+' '+label,'maxPlayers':'3','hostParticipation':'SPECTATOR'})
        for pos,(mode,count,seconds) in enumerate(plan):
            if pos:host.click('#add-stage');host.fill({'stageMode':mode},'.stage-editor:last-child ')
            host.fill({'quizId':str(sets[mode]),'questionCount':str(count),'seconds':str(seconds)},'.stage-editor:last-child ')
        assert host.eval("document.querySelector('[name=hostParticipation] [value=PLAYER]').disabled")
        host.submit('#room-form');host.wait("!!document.querySelector('#open-room')")
        rid=int(host.eval("location.hash.split('/')[2]"));report['roomIds'].append(rid)
        host.click('#open-room');host.wait("!!document.querySelector('#start-game')")
        code=host.eval("document.querySelector('#room-code').textContent")
        for t in players:
            t.route('join','#join-form');t.fill({'code':code,'participation':'PLAYER'});t.submit('#join-form');t.wait("!!document.querySelector('#waiting-room')")
        host.wait("!document.querySelector('#start-game').disabled");host.click('#start-game')
        for t in [host,*players]:phase(t,1,'INTRO')
        gid=int(host.eval("location.hash.split('/')[2]"));report['gameIds'].append(gid)
        assert snapshot(host,gid)['player'] is None
        return rid,gid
    def ready(index):
        for t in players:
            phase(t,index,'INTRO');t.wait("!!document.querySelector('#continue-stage')&&!document.querySelector('#continue-stage').disabled");t.click('#continue-stage')
    def answer(t,mode,correct=True):
        t.wait("!!document.querySelector('#submit-answer')")
        if mode=='QUIZ':t.click('[data-option='+('A' if correct else 'B')+']')
        elif mode in ['VIETNAMESE_PUZZLE','ORDERING']:
            ids=sources[mode]['questions'][0]['correctOrder'];ids=ids if correct else list(reversed(ids))
            for item_id in ids:t.click('[data-piece-id="'+item_id+'"]')
        else:t.fill({'answerText':' BẮT  CÁ ' if correct else 'bat ca'})
        t.click('#submit-answer')
    def final_and_history(rid,gid,n):
        for t in [host,*players]:t.wait("!!document.querySelector('#final-summary')")
        final=snapshot(host,gid);assert final['endReason']=='COMPLETED' and final['hasOfficialWinner']
        assert all(m['playerState']=='PLAYING' for m in final['members'] if m['participation']=='PLAYER')
        host.route('history/'+str(gid),'#history-detail')
        assert host.eval(f"document.querySelectorAll('.history-question').length==={n}&&!document.body.innerText.includes('undefined')")
        detail=host.eval(f"fetch('/api/games/history/{gid}').then(r=>r.json())")
        host.route('room/'+str(rid),'#waiting-room');host.wait("!!document.querySelector('#start-game')")
        return final,detail

    rid,gid=create_room([(m,10 if m=='QUIZ' else 1,12 if m=='SONG' else 8) for m in modes],'seven')
    # Replace p3 with an independent browser context while INTRO is running.
    replaced=players[2];replacement=cdp.tab(origin);setup_observer(replacement);login(replacement,people[3][1]);replacement.route('game/'+str(gid),'#game-page');phase(replacement,1,'INTRO')
    replaced.wait("document.querySelector('#connection-status')?.dataset.state==='replaced'")
    assert snapshot(replacement,gid)['player']['score']==0
    cdp.call('Target.closeTarget',{'targetId':replaced.target});players[2]=replacement
    # RECONNECT snapshot in INTRO must not mark the Spectator ready or reveal a question.
    intro=snapshot(players[0],gid);assert intro['question'] is None and intro['readyPlayers']==[]
    passed('INTRO reconnect and socket replacement preserve roster/resources; old tab remains replaced and cannot fight reconnect')
    ready(1);phase(host,1,'DECISION')
    assert snapshot(players[0],gid)['player']['remainingSpins']==1
    players[0].click('#use-spin');players[0].wait("document.querySelector('#remaining-spins').textContent==='0'")
    spun=snapshot(players[0],gid)['player']['currentSpin'];report['spinEffect']=spun
    if spun!='HARDSHIP':players[0].click('#use-star');players[0].wait("document.querySelector('#star-available').textContent==='Đã dùng'")
    players[1].click('#use-star');players[1].wait("document.querySelector('#star-available').textContent==='Đã dùng'")
    spin_command=players[0].eval("wire.commands.filter(c=>c.type==='USE_SPIN').at(-1)")
    for index in range(1,11):
        phase(host,index,'QUESTION_OPEN')
        for p,t in enumerate(players):answer(t,'QUIZ',p==0 or p==1 and index==1 or p==2 and index<10)
        if index==1:
            phase(host,1,'RESULT')
            players[0].eval('gameSocket.close()');players[0].wait("document.querySelector('#connection-status')?.dataset.state==='offline'")
            players[0].click('#reconnect-room');players[0].wait("document.querySelector('#connection-status')?.dataset.state==='ready'")
            restored=snapshot(players[0],gid);assert restored['questionIndex']==1 and restored['results']
            report['resultReconnect']={'phase':restored['phase'],'deadlineEpochMs':restored['deadlineEpochMs'],'score':restored['player']['score']}
    phase(host,11,'INTRO')
    # Explicit same-frame replay after the Quiz stage, not a new UUID.
    replay=players[0].eval("(async()=>{const frame="+json.dumps(spin_command)+";gameSocket.send(JSON.stringify(frame));return frame.requestId})()")
    players[0].wait(f"wire.messages.filter(m=>m.requestId==='{replay}'&&m.kind==='ACK').length>=2")
    report['spinRetryRequestId']=replay
    after_quiz=snapshot(players[0],gid)
    assert all(m['playerState']=='PLAYING' and m['score']>=0 for m in after_quiz['members'] if m['participation']=='PLAYER')
    assert next(m['score'] for m in after_quiz['members'] if m['userId']==people[2][2])==0
    assert after_quiz['player']['momentum']
    passed('Ten Quiz questions: Decision7s, one committed Spin/Star, same-frame replay after stage, final Quiz not doubled and floor0/no elimination')

    for index,mode in enumerate(modes[1:],11):
        phase(host,index,'INTRO');ready(index);phase(host,index,'QUESTION_OPEN')
        before=snapshot(players[0],gid)
        assert not players[0].eval("!!document.querySelector('#use-spin')||!!document.querySelector('#use-star')||!!document.querySelector('#correct-text')")
        assert 'acceptedAnswers' not in (before['question']['payload'] or {}) and 'correctOrder' not in (before['question']['payload'] or {})
        if mode=='SONG':
            players[0].wait("document.querySelector('#song-video')?.readyState>=2")
            base.base.native_click(cdp,players[0],'#enable-video-sound')
            players[0].wait("!document.querySelector('#song-video').muted&&!document.querySelector('#song-video').paused")
            players[0].wait(f"Date.now()>={before['question']['payload']['openedAtMs']}+3000")
            players[0].eval('gameSocket.close()');players[0].wait("document.querySelector('#connection-status')?.dataset.state==='offline'")
            cdp.call('Page.reload',{'ignoreCache':True},players[0].session)
            players[0].wait("document.querySelector('#connection-status')?.dataset.state==='ready'&&document.querySelector('#song-video')?.readyState>=2&&!!document.querySelector('#answer-text')&&!document.querySelector('#answer-text').disabled")
            after=snapshot(players[0],gid)
            video=players[0].eval("({playhead:document.querySelector('#song-video').currentTime,estimatedServer:wire.messages.filter(m=>m.kind==='ACK'&&m.type==='RECONNECT').at(-1).payload.serverTimeMs})")
            assert before['deadlineEpochMs']==after['deadlineEpochMs'] and video['playhead']>=2.5
            report['videoReconnect']={'deadlineBefore':before['deadlineEpochMs'],'deadlineAfter':after['deadlineEpochMs'],'remainingMs':after['remainingMs'],**video}
        if mode=='IMAGE_WORD':players[0].wait("document.querySelector('.question-image')?.complete&&document.querySelector('.question-image').naturalWidth>0")
        if mode=='CLUES':
            for t in [host,*players]:t.wait("document.querySelectorAll('#released-clues li').length===2")
            players[0].eval('gameSocket.close()');players[0].wait("document.querySelector('#connection-status')?.dataset.state==='offline'")
            cdp.call('Page.reload',{'ignoreCache':True},players[0].session)
            players[0].wait("document.querySelector('#connection-status')?.dataset.state==='ready'&&document.querySelectorAll('#released-clues li').length===2&&!!document.querySelector('#answer-text')&&!document.querySelector('#answer-text').disabled")
            assert snapshot(players[0],gid)['deadlineEpochMs']==before['deadlineEpochMs']
        if mode=='ORDERING':players[0].eval("wire.dropType='ANSWER'")
        for t in players:answer(t,mode)
        if mode=='ORDERING':
            players[0].wait("!!wire.dropped")
            lost_answer=players[0].eval("wire.commands.filter(c=>c.type==='ANSWER').at(-1)")
    final,history=final_and_history(rid,gid,16)
    first_reward={'BONUS':30,'SAFE':20,'BREAKTHROUGH':30,'SPEED':35,'DECISIVE':40,'HARDSHIP':8}[spun]
    assert [m['score'] for m in final['members'] if m['participation']=='PLAYER']==[first_reward+213,120,209]
    assert {q['mode'] for q in history['questions']}==set(modes)
    for q in history['questions'][10:]:assert all(a['scoreDelta']==20 for a in q['answers'])
    for q in history['questions']:
        if q['mode']=='CLUES':assert len(q['payload']['hints'])==2 and 'Gợi ý tương lai' not in json.dumps(q,ensure_ascii=False)
    report['sevenModeFinal']=final
    players[0].eval('gameSocket.close()');players[0].wait("document.querySelector('#connection-status')?.dataset.state==='offline'")
    players[0].click('#reconnect-room');players[0].wait("document.querySelector('#connection-status')?.dataset.state==='ready'&&!!document.querySelector('#retry-game-command')&&!document.querySelector('#retry-game-command').disabled")
    players[0].click('#retry-game-command');players[0].wait(f"document.querySelector('#game-ack')?.dataset.requestId==='{lost_answer['requestId']}'")
    assert players[0].eval("wire.commands.filter(c=>c.type==='ANSWER').at(-1)")==lost_answer
    assert [m['score'] for m in snapshot(players[0],gid)['members'] if m['participation']=='PLAYER']==[first_reward+213,120,209]
    report['lostAnswerRetryRequestId']=lost_answer['requestId']
    passed('Real committed Answer ACK dropped at Client, reconnect/retry same frame after FINISHED, no duplicate Answer or score')
    host.route('history/'+str(gid),'#history-detail');cdp.call('Emulation.setDeviceMetricsOverride',{'width':390,'height':844,'deviceScaleFactor':1,'mobile':True},host.session);host.screenshot(ROOT/('target/'+report['outputPrefix']+'-seven-history-mobile.png'))
    cdp.call('Emulation.clearDeviceMetricsOverride',session=host.session)
    passed('Real seven-mode game Final/History: all six non-Quiz stage finals+20, Quiz final not doubled, carried Momentum, private media and Server scoring')

    for plan,label,n in [([('RIDDLE',1,5)],'single',1),([('VIETNAMESE_PUZZLE',1,5),('ORDERING',1,5)],'noquiz',2)]:
        r,g=create_room(plan,label)
        assert snapshot(players[0],g)['player']['remainingSpins']==0 and not snapshot(players[0],g)['player']['starAvailable']
        for i,(mode,_,_) in enumerate(plan,1):
            ready(i);phase(host,i,'QUESTION_OPEN')
            for t in players:answer(t,mode)
        f,h=final_and_history(r,g,n)
        assert [m['score'] for m in f['members'] if m['participation']=='PLAYER']==[20*n]*3
        report.setdefault('otherFinals',[]).append(f)
    passed('Single-mode RIDDLE and multiple modes without Quiz complete on UI; zero Spin/Star and shared standings/History')
    r,g=create_room([('CLUES',1,8)],'cancel');ready(1);phase(host,1,'QUESTION_OPEN')
    answer(players[0],'CLUES');players[0].wait("!!document.querySelector('#answer-accepted')");host.click('#cancel-game')
    for t in [host,*players]:t.wait("!!document.querySelector('#final-summary')")
    cancel=snapshot(host,g);assert cancel['endReason']=='CANCELLED' and not cancel['hasOfficialWinner'] and not cancel['winners']
    host.route('history/'+str(g),'#history-detail');host.wait("!!document.querySelector('[data-status=ACCEPTED_UNSCORED]')")
    host.route('room/'+str(r),'#waiting-room');host.wait("!!document.querySelector('#start-game')")
    report['cancelFinal']=cancel;passed('Host Spectator Cancel keeps Accepted-Unscored, no Official Winner, Room reusable')
    for t in [host,*players]:cdp.call('Target.closeTarget',{'targetId':t.target})


def main():
    p=argparse.ArgumentParser();p.add_argument('--origin',default='http://127.0.0.1:8087');p.add_argument('--port',type=int,default=9224);p.add_argument('--smoke',action='store_true');p.add_argument('--output-prefix',default='task24');p.add_argument('--task',type=int,default=24);args=p.parse_args()
    if not args.output_prefix.replace('-','').replace('_','').isalnum():p.error('Output prefix must be a filename stem')
    if (ROOT/('target/'+args.output_prefix+'-client-tests.json')).exists():p.error('Report already exists; choose a new --output-prefix to preserve evidence')
    report={'outputPrefix':args.output_prefix,'task':args.task,'status':'RUNNING','unit':[],'smoke':[],'clients':4,'gameIds':[],'roomIds':[],'sameMachine':True}
    try:
        cdp=browser.CDP(args.port);report['browser']=cdp.version;t=cdp.tab(args.origin)
        report['unit']=t.eval(base.base.old.legacy.UNIT)+t.eval(base.base.old.UNIT)+t.eval(base.base.a.mm.UNIT)+t.eval(base.base.a.UNIT)+t.eval(base.base.base.UNIT)+t.eval(base.base.UNIT)+t.eval(base.UNIT)
        cdp.call('Target.closeTarget',{'targetId':t.target})
        if args.smoke:smoke(cdp,args.origin,report)
        assert not cdp.errors,cdp.errors;report['status']='PASS'
    except Exception as e:
        report['status']='FAIL';report['error']=str(e);raise
    finally:
        (ROOT/('target/'+args.output_prefix+'-client-tests.json')).write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
        print(json.dumps({'status':report['status'],'unit':len(report['unit']),'smoke':len(report['smoke'])}),flush=True)
if __name__=='__main__':main()
