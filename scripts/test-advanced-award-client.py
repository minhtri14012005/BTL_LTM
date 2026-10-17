"""T13 real three-mode achievements and optional genuine pending/crash/restart handshake.
Only owned test Server/Chrome/test DB; no mock achievement payloads in smoke.
"""
import argparse, importlib.util, json, os, secrets, subprocess, time, uuid
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('browser',ROOT/'scripts/browser-cdp.py');browser=importlib.util.module_from_spec(spec);spec.loader.exec_module(browser)

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--origin',default='http://192.168.1.234:8088');parser.add_argument('--port',type=int,default=9227);parser.add_argument('--restart-check',action='store_true');args=parser.parse_args()
    fixture=ROOT/'target/t13-restart-fixture';connector=ROOT/'.cache/maven/com/mysql/mysql-connector-j/9.7.0/mysql-connector-j-9.7.0.jar'
    if args.restart_check:
        fixture.mkdir(parents=True,exist_ok=True)
        subprocess.run(['javac','-d',str(fixture),str(ROOT/'scripts/AwardRestartFixture.java')],cwd=ROOT,check=True)
    cdp=browser.CDP(args.port);tabs=[];report=dict(status='RUNNING',checks=[],sameMachine=True,gameIds=[]);images=ROOT/'docs/assets/advanced-awards-2026-10-17';images.mkdir(parents=True,exist_ok=True)
    prefix='award_t13_'+secrets.token_hex(4);password=secrets.token_urlsafe(24);users={};hold=None;hold_log=None;release=None
    def passed(text):report['checks'].append(text);print('PASS '+text,flush=True)
    def req(t,method,path,body=None):
        return t.eval('(async()=>{const c=await fetch("/api/auth/csrf").then(r=>r.json());const r=await fetch('+json.dumps(path)+',{method:'+json.dumps(method)+',headers:{"Content-Type":"application/json",[c.headerName]:c.token},'+('body:'+json.dumps(json.dumps(body))+',' if body is not None else '')+'});return {status:r.status,body:await r.text()};})()')
    def api(t,method,path,body=None):
        r=req(t,method,path,body);assert 200<=r['status']<300,(path,r);return json.loads(r['body']) if r['body'] else None
    def login(t,label):
        t.fill(dict(username=prefix+'_'+label,password=password));t.submit('#login-form');t.wait("document.querySelector('#connection-status')?.dataset.state==='ready'")
    def player(label):
        t=cdp.tab(args.origin);tabs.append(t);cdp.call('Emulation.setDeviceMetricsOverride',dict(width=1440,height=1000,deviceScaleFactor=1,mobile=False),t.session)
        api(t,'POST','/api/auth/register',dict(username=prefix+'_'+label,displayName='<b>T13 A</b>' if label=='a' else 'T13 '+label,password=password));login(t,label);users[label]=api(t,'GET','/api/auth/me');return t
    def ready(players,index):
        for t in players:t.wait(f"document.querySelector('#game-page')?.dataset.index==='{index}'&&!!document.querySelector('#continue-stage')&&!document.querySelector('#continue-stage').disabled",timeout=25);t.click('#continue-stage')
    def start(host,players,room,defer_ready=False):
        for t in players:t.route('room/'+str(room['id']),'#waiting-room')
        host.route('room/'+str(room['id']),'#waiting-room');host.wait("!!document.querySelector('#start-game')&&!document.querySelector('#start-game').disabled");host.click('#start-game');host.wait("document.querySelector('#game-page')?.dataset.phase==='INTRO'")
        game=int(host.eval("location.hash.split('/')[2]"));report['gameIds'].append(game)
        if not defer_ready:ready(players,1)
        return game
    def question(players,index):
        if index in [5,9]:ready(players,index)
        local=(index-1)%4+1
        for n,t in enumerate(players):
            afk=(index<=4 and n==0 and local<=2) or (index>4 and n==1 and local>2)
            if afk:continue
            correct=(index<=4) or n==0 or (n==1 and local<=2) or (n==2 and local==1)
            t.wait(f"document.querySelector('#game-page')?.dataset.index==='{index}'&&document.querySelector('#game-page')?.dataset.phase==='QUESTION_OPEN'&&!!document.querySelector('#submit-answer')&&!!document.querySelector('#answer-text,[data-option=D]')&&!document.querySelector('#answer-text,[data-option=D]').disabled",timeout=25)
            if 5<=index<=8:t.click('[data-option='+('D' if correct else 'A')+']')
            else:t.fill(dict(answerText='yes' if correct else 'wrong'))
            t.click('#submit-answer');t.wait("!!document.querySelector('#answer-accepted')||document.querySelector('#game-page')?.dataset.phase!=='QUESTION_OPEN'")
    try:
        host,a,b,c=[player(label) for label in ['host','a','b','c']];players=[a,b,c]
        policy=api(host,'GET','/api/awards/policy')['policy'];assert policy['policyId']=='AWARD_V2' and policy['advanced']==dict(multiMinModes=3,multiMinCorrect=2,multiAccuracyPercent=50,comebackMinStages=2)
        stages=[]
        for mode in ['RIDDLE','QUIZ','CLUES']:
            item=dict(content='Question',options=dict(A='A',B='B',C='C',D='D'),correctAnswer='D') if mode=='QUIZ' else dict(content='Question',acceptedAnswers=['yes'])
            if mode=='CLUES':item['hints']=[dict(offsetMs=0,text='Open hint')]
            bank=api(host,'POST','/api/quizzes',dict(title=prefix+' '+mode,mode=mode,visibility='PRIVATE',questions=[item for _ in range(4)]))
            stages.append(dict(mode=mode,quizId=bank['id'],questionCount=4,questionDurationMs=3000))
        room=api(host,'POST','/api/rooms',dict(requestId=str(uuid.uuid4()),config=dict(name=prefix,maxPlayers=3,hostParticipation='SPECTATOR',stages=stages)))
        host.route('room/'+str(room['id']),'#open-room');host.click('#open-room');host.wait("!!document.querySelector('#start-game')")
        for t in players:t.route('join','#join-form');t.fill(dict(code=room['roomCode'],participation='PLAYER'));t.submit('#join-form');t.wait("!!document.querySelector('#waiting-room')")
        passed('AWARD_V2 signed thresholds and real three-mode private-pack Room load through Server API')
        game=start(host,players,room)
        for index in range(1,13):question(players,index)
        a.wait("document.querySelector('.award-panel')?.dataset.awardState==='AWARDS_DECIDED'",timeout=25)
        decision=api(a,'GET',f'/api/games/{game}/awards');uid=users['a']['id'];bid=users['b']['id'];cid=users['c']['id']
        multi=[x['userId'] for x in decision['awards'] if x['type']=='MULTITALENT'];comeback=[x for x in decision['awards'] if x['type']=='COMEBACK']
        assert sorted(multi)==sorted([uid,bid]) and len(comeback)==1 and comeback[0]['userId']==uid
        assert comeback[0]['evidence']['stageOneRank']==3 and comeback[0]['evidence']['finalEligibleRank']==1 and comeback[0]['evidence']['stageOneEndQuestion']==4
        assert not any(x['userId']==cid for x in decision['awards'])
        own=api(a,'GET','/api/profiles/me');peer=api(b,'GET','/api/profiles/me');assert own['stats']['correct']==10 and own['stats']['participated']==12 and peer['stats']['correct']==8 and peer['stats']['participated']==12
        assert own['stats']['cups']==1 and own['stats']['officialGames']==1 and own['stats']['officialWins']==1
        assert api(b,'GET',f'/api/games/{game}/awards')['awards']==decision['awards']
        a.eval("document.querySelector('.award-panel').scrollIntoView({block:'start'})");a.screenshot(images/'ceremony-1440.png')
        passed('Two eligible Players receive Multitalent, unique last-to-first receives Comeback, real AFK denominators and base cups remain correct')
        report['componentChecks']=a.eval('''(async()=>{const {ceremonyView}=await import('/js/features/results/awards.js');const pending=ceremonyView({awardState:'AWARDS_PENDING',awards:[{type:'MULTITALENT'},{type:'COMEBACK'}]},1);if(pending.querySelector('.award-badge'))throw Error('Pending exposed badge');return ['Pending never renders uncommitted advanced badge'];})()''')
        a.route('profile','#profile-page');a.wait("!!document.querySelector('.badge-list')");assert a.eval("document.querySelector('.badge-list').textContent.includes('Chuyên gia đa tài')&&document.querySelector('.badge-list').textContent.includes('Lội ngược dòng')")
        assert not a.eval("!!document.querySelector('#profile-page h1 b')")
        cdp.call('Emulation.setDeviceMetricsOverride',dict(width=360,height=1000,deviceScaleFactor=1,mobile=False),a.session);assert a.eval('document.documentElement.scrollWidth<=innerWidth');a.screenshot(images/'own-profile-360.png')
        b.route('profile','#profile-page');b.wait("!!document.querySelector('.badge-list')");assert b.eval("document.querySelector('.badge-list').textContent.includes('Chuyên gia đa tài')&&!document.querySelector('.badge-list').textContent.includes('Lội ngược dòng')")
        assert set(api(b,'GET',f'/api/profiles/{uid}'))=={'userId','displayName','stats'}
        a.route('history/'+str(game),'#history-detail');a.wait("document.querySelector('.award-panel')?.dataset.awardState==='AWARDS_DECIDED'");a.reload();a.wait("document.querySelector('.award-panel')?.dataset.awardState==='AWARDS_DECIDED'");assert api(a,'GET',f'/api/games/{game}/awards')['awards']==decision['awards']
        passed('Ceremony/own profile/History retain committed badges and IDs; public profile stays A15-safe; 360px and text safety pass')
        if args.restart_check:
            recovery=start(host,players,room,defer_ready=True);report['restartGame']=recovery
            ready_file=ROOT/f'target/t11-award-hold-{recovery}.ready';held=ready_file.with_suffix('.held');release=ready_file.with_suffix('.release')
            hold_log=(ROOT/'target/t13-restart-hold.log').open('w',encoding='utf-8')
            hold=subprocess.Popen(['java','--class-path',str(fixture)+os.pathsep+str(connector),'AwardRestartFixture',str(recovery),str(uid)],cwd=ROOT,stdout=hold_log,stderr=subprocess.STDOUT)
            deadline=time.monotonic()+30
            while not ready_file.exists():assert hold.poll() is None and time.monotonic()<deadline,'Pending helper not ready';time.sleep(.1)
            ready(players,1)
            for index in range(1,13):question(players,index)
            deadline=time.monotonic()+30
            while not held.exists():assert hold.poll() is None and time.monotonic()<deadline,'Genuine pending hold lost; no fake snapshot';time.sleep(.1)
            pending=api(host,'GET',f'/api/games/{recovery}/awards');assert pending['awardState']=='AWARDS_PENDING' and not pending['awards']
            request=ROOT/f'target/t13-restart-{prefix}.request.json';signal=request.with_suffix('.continue');request.write_text(json.dumps(dict(game=recovery,release=str(release),signal=str(signal))),encoding='utf-8');print('RESTART_READY '+str(request),flush=True)
            deadline=time.monotonic()+180
            while not signal.exists():assert time.monotonic()<deadline,'Owned restart handshake timeout';time.sleep(.1)
            for t,label in [(host,'host'),(a,'a')]:t.reload();t.wait("!!document.querySelector('#login-form')");login(t,label)
            a.route('history/'+str(recovery),'#history-detail');a.wait("document.querySelector('.award-panel')?.dataset.awardState==='AWARDS_DECIDED'",timeout=25)
            recovered=api(a,'GET',f'/api/games/{recovery}/awards');assert [x['userId'] for x in recovered['awards'] if x['type']=='COMEBACK']==[uid] and len([x for x in recovered['awards'] if x['type']=='MULTITALENT'])==2
            assert api(a,'GET',f'/api/games/{game}/awards')['awards']==decision['awards'] and api(a,'GET',f'/api/games/{recovery}/awards')['awards']==recovered['awards']
            a.eval("document.querySelector('.award-panel').scrollIntoView({block:'start'})");a.screenshot(images/'restart-recovered-360.png')
            passed('Real crash after FINISHED/PENDING recovers advanced awards once and preserves prior ledger IDs')
        current=api(host,'GET',f'/api/rooms/{room["id"]}');api(host,'POST',f'/api/rooms/{room["id"]}/close',dict(requestId=str(uuid.uuid4()),revision=current['revision']))
        before=api(a,'GET','/api/profiles/me');a.click('#logout')
        a.wait("!!document.querySelector('#login-form')&&document.querySelector('#view').textContent.includes('Đã đăng xuất.')")
        login(a,'a');assert api(a,'GET','/api/profiles/me')==before
        passed('Committed advanced profile survives Room close and logout/login')
        assert not cdp.errors,cdp.errors;report.update(status='PASS',browser=cdp.version)
    except Exception as error:report.update(status='FAIL',error=str(error));raise
    finally:
        (ROOT/'target/t13-advanced-client-tests.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
        for t in tabs:cdp.call('Target.disposeBrowserContext',dict(browserContextId=t.context))
        cdp.socket.close()
        if hold and hold.poll() is None:
            if release:release.write_text('Release owned SQL lock',encoding='utf-8')
            try:hold.wait(timeout=10)
            except subprocess.TimeoutExpired:hold.terminate()
        if hold_log:hold_log.close()
        print(json.dumps(dict(status=report['status'],checks=len(report['checks']))))

if __name__=='__main__':main()
