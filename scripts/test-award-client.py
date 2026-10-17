"""T11 real LAN-origin HTTP/WS/MySQL ceremony. No fabricated results, policy or award payloads."""
import argparse, importlib.util, json, os, secrets, subprocess, uuid, time
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('browser',ROOT/'scripts/browser-cdp.py');browser=importlib.util.module_from_spec(spec);spec.loader.exec_module(browser)

def main():
    p=argparse.ArgumentParser();p.add_argument('--origin',default='http://192.168.1.234:8088');p.add_argument('--port',type=int,default=9227);p.add_argument('--restart-check',action='store_true');p.add_argument('--runtime-jar',default=str(ROOT/'target/multigame-0.0.1-SNAPSHOT.jar'));args=p.parse_args()
    cdp=browser.CDP(args.port);tabs=[];report={'status':'RUNNING','checks':[],'gameIds':[],'sameMachine':True};images=ROOT/'docs/assets/awards-2026-10-17';images.mkdir(parents=True,exist_ok=True)
    prefix='award_'+secrets.token_hex(4);password=secrets.token_urlsafe(24);users={};hold_process=None;hold_log=None
    def passed(label):report['checks'].append(label);print('PASS '+label,flush=True)
    def tab():
        t=cdp.tab(args.origin);cdp.call('Emulation.setDeviceMetricsOverride',dict(width=1440,height=1000,deviceScaleFactor=1,mobile=False),t.session);tabs.append(t);return t
    def req(t,method,path,body=None):return t.eval('(async()=>{const c=await fetch("/api/auth/csrf").then(r=>r.json());const r=await fetch('+json.dumps(path)+',{method:'+json.dumps(method)+',headers:{"Content-Type":"application/json",[c.headerName]:c.token},'+('body:'+json.dumps(json.dumps(body))+',' if body is not None else '')+'});return {status:r.status,body:await r.text()};})()')
    def api(t,method,path,body=None):
        r=req(t,method,path,body);assert 200<=r['status']<300,(path,r);return json.loads(r['body']) if r['body'] else None
    def player(label):
        t=tab();name=prefix+'_'+label;display='Award '+label if label!='a' else '<b>Player A</b>'
        api(t,'POST','/api/auth/register',dict(username=name,displayName=display,password=password));t.fill(dict(username=name,password=password));t.submit('#login-form');t.wait("document.querySelector('#connection-status')?.dataset.state==='ready'");users[label]=api(t,'GET','/api/auth/me');return t
    def create(host,count):
        bank=api(host,'POST','/api/quizzes',dict(title=prefix,mode='RIDDLE',visibility='PRIVATE',questions=[dict(content='Question '+str(i),acceptedAnswers=['yes']) for i in range(count)]))
        room=api(host,'POST','/api/rooms',dict(requestId=str(uuid.uuid4()),config=dict(name=prefix,maxPlayers=3,hostParticipation='SPECTATOR',stages=[dict(mode='RIDDLE',quizId=bank['id'],questionCount=count,questionDurationMs=30000)])))
        host.route('room/'+str(room['id']),'#open-room');host.click('#open-room');host.wait("!!document.querySelector('#start-game')");return room
    def join(t,room):t.route('join','#join-form');t.fill(dict(code=room['roomCode'],participation='PLAYER'));t.submit('#join-form');t.wait("!!document.querySelector('#waiting-room')")
    def start(host,players):
        host.wait("!document.querySelector('#start-game').disabled");host.click('#start-game');host.wait("document.querySelector('#game-page')?.dataset.phase==='INTRO'");game=int(host.eval("location.hash.split('/')[2]"));report['gameIds'].append(game)
        for t in players:t.wait("!!document.querySelector('#continue-stage')&&!document.querySelector('#continue-stage').disabled");t.click('#continue-stage')
        return game
    def answer(t,index,correct=True):
        t.wait(f'document.querySelector("#game-page")?.dataset.index==="{index}"&&document.querySelector("#game-page")?.dataset.phase==="QUESTION_OPEN"&&!!document.querySelector("#answer-text")&&!document.querySelector("#answer-text").disabled')
        t.fill(dict(answerText='yes' if correct else 'wrong'));t.click('#submit-answer');t.wait("!!document.querySelector('#answer-accepted')||!!document.querySelector('#final-summary')||document.querySelector('#game-page')?.dataset.phase!=='QUESTION_OPEN'")
    try:
        name='awardadm_'+secrets.token_hex(5);secret=secrets.token_urlsafe(30)+'!Aa7';env=os.environ.copy();env.update(MULTIGAME_ADMIN_USERNAME=name,MULTIGAME_ADMIN_DISPLAY_NAME='Award operator',MULTIGAME_ADMIN_PASSWORD=secret)
        cmd=['java','-jar',args.runtime_jar,'--provision-admin','--spring.datasource.url=jdbc:mysql://127.0.0.1:3306/quizz_task2_test?connectionTimeZone=UTC&connectTimeout=3000&socketTimeout=3000','--debug=false','--logging.level.root=WARN','--logging.level.org.springframework=WARN','--logging.level.org.hibernate=WARN']
        provision=subprocess.run(cmd,cwd=ROOT,env=env,text=True,encoding='utf-8',capture_output=True,timeout=60);env.pop('MULTIGAME_ADMIN_PASSWORD');assert provision.returncode==0,provision.stdout+provision.stderr
        admin=tab();admin.fill(dict(username=name,password=secret));admin.submit('#login-form');admin.wait("location.pathname==='/admin'&&!!document.querySelector('#admin-page')")
        report['componentChecks']=admin.eval('''(async()=>{
          const {ceremonyView,awardPanel}=await import('/js/features/results/awards.js');const checks=[];
          const check=(name,ok)=>{if(!ok)throw Error(name);checks.push(name);};
          check('Pending never renders a cup',!ceremonyView({awardState:'AWARDS_PENDING',awards:[{type:'CUP',value:1}]},1).querySelector('.award-cup'));
          check('Not qualified never renders a cup',!ceremonyView({awardState:'AWARDS_NOT_QUALIFIED',qualification:{reasonCodes:['NOT_COMPLETED']},awards:[{type:'CUP',value:1}]},1).querySelector('.award-cup'));
          check('Legacy no-policy view never renders a cup',!ceremonyView({awardState:null},1).querySelector('.award-cup'));
          let aborted=false;const app={user:{id:1},renderVersion:1,api:{request(method,path,body,{signal}){return new Promise((resolve,reject)=>signal.addEventListener('abort',()=>{aborted=true;reject(Error('aborted'));}));}}};
          const panel=awardPanel(app,1);panel.dispose();await Promise.resolve();check('Route dispose aborts owned request',aborted);
          return checks;
        })()''');passed('Ceremony component guards and owned request disposal pass in browser')
        host=player('host');a=player('a');b=player('b');c=player('c');players=[a,b,c]
        policy=api(host,'GET','/api/awards/policy')['policy'];assert policy['policyId']=='AWARD_V2' and policy['minConfiguredQuestions']==10 and policy['fastMinCorrect']==5 and policy['advanced']['multiMinModes']==3
        room=create(host,10)
        for t in players:join(t,room)
        host.wait("!!document.querySelector('#start-game')");assert not host.eval("document.body.textContent.includes('AWARD_V2')");passed('Signed policy remains verified via Server API; room omits technical policy banner')
        # Hold delivery of a genuine GET response, not the decision transaction or any gameplay event.
        host.eval('window.holdAwardReply=true;const originalFetch=fetch;window.fetch=async function(path){const r=await originalFetch.apply(this,arguments);if(/\\/api\\/games\\/\\d+\\/awards$/.test(String(path))&&holdAwardReply){window.awardReceiptWaiting=true;await new Promise(resolve=>window.releaseAwardReply=resolve);}return r;};')
        game=start(host,players)
        for i in range(1,11):
            for t in players:answer(t,i)
        host.wait("!!document.querySelector('#final-summary')&&!!window.awardReceiptWaiting")
        assert host.eval("!!document.querySelector('.award-state')&&!document.querySelector('.award-cup')")
        host.eval("document.querySelector('.award-panel').scrollIntoView({block:'start'})");host.screenshot(images/'pending-1440.png');passed('Result is visible while award receipt is withheld; no cup is announced before committed API data is received')
        host.eval('holdAwardReply=false;releaseAwardReply()');host.wait("document.querySelector('.award-panel')?.dataset.awardState==='AWARDS_DECIDED'")
        first=api(host,'GET',f'/api/games/{game}/awards');assert first['qualification']['qualified'] and len([x for x in first['awards'] if x['type']=='PERFECT'])==3 and any(x['type']=='FAST' for x in first['awards'])
        assert host.eval("!document.querySelector('.award-ceremony b')&&document.querySelector('.award-ceremony').textContent.includes('<b>Player A</b>')")
        host.eval("document.querySelector('.award-panel').scrollIntoView({block:'start'})");host.screenshot(images/'decided-1440.png');passed('Real ten-question match awards CUP/FAST/PERFECT; user display name is rendered as text')
        before=first['awards'];host.reload();host.wait("document.querySelector('.award-panel')?.dataset.awardState==='AWARDS_DECIDED'");assert api(host,'GET',f'/api/games/{game}/awards')['awards']==before
        passed('Reload and repeated read return the same ledger IDs without granting again')
        host.route('room/'+str(room['id']),'#waiting-room');game2=start(host,players)
        for i in range(1,10):
            answer(a,i)
            answer(b,i,False);answer(c,i,False)
        answer(a,10);kick=api(admin,'POST',f'/api/admin/games/{game2}/kick',dict(requestId=str(uuid.uuid4()),userId=users['a']['id'],reason='T11 eligible ranking proof'))
        assert kick.get('status',kick.get('result'))=='KICK_APPLIED'
        answer(b,10);answer(c,10);host.wait("document.querySelector('.award-panel')?.dataset.awardState==='AWARDS_DECIDED'")
        second=api(host,'GET',f'/api/games/{game2}/awards');pa=next(p for p in second['participants'] if p['userId']==users['a']['id'])
        assert pa['historyRank']==1 and not pa['eligible'] and pa['officialGameDelta']==0 and not any(x['userId']==users['a']['id'] for x in second['awards'])
        assert any(x['type']=='CUP' and x['userId'] in [users['b']['id'],users['c']['id']] for x in second['awards'])
        host.eval("document.querySelector('.award-panel').scrollIntoView({block:'start'})");host.screenshot(images/'eligible-ranking-1440.png');passed('ADMIN Kick after accepted last Answer preserves final rank 1 while CUP moves to eligible ranking')
        a.wait("location.hash==='#/home'");a.route('history/'+str(game2),'#history-detail');a.wait("document.querySelector('.award-panel')?.dataset.awardState==='AWARDS_DECIDED'")
        assert req(a,'GET',f'/api/games/{game2}/snapshot')['status']==403
        assert not api(a,'GET',f'/api/games/{game2}/awards')['participants'][0].get('private_evidence')
        passed('Restricted user can read retained History/award decision but live Game snapshot remains forbidden')
        cdp.call('Emulation.setDeviceMetricsOverride',dict(width=360,height=900,deviceScaleFactor=1,mobile=False),host.session)
        assert host.eval('document.documentElement.scrollWidth<=innerWidth');host.eval("document.querySelector('.award-panel').scrollIntoView({block:'start'})");host.screenshot(images/'ceremony-360.png')
        short=create(host,1)
        for t in [a,b,c]:join(t,short)
        host.wait("document.body.textContent.includes('chưa đủ 10 câu')");small=start(host,[a,b,c])
        for t in [a,b,c]:answer(t,1)
        host.wait("document.querySelector('.award-panel')?.dataset.awardState==='AWARDS_NOT_QUALIFIED'")
        no=api(host,'GET',f'/api/games/{small}/awards');assert not no['awards'] and all(p['officialGameDelta']==0 for p in no['participants'])
        assert host.eval("!document.querySelector('.award-cup')&&document.documentElement.scrollWidth<=innerWidth")
        host.eval("document.querySelector('.award-panel').scrollIntoView({block:'start'})");host.screenshot(images/'not-qualified-360.png');passed('Short match still plays, ends NOT_QUALIFIED with zero official deltas; 360px page does not overflow')
        if args.restart_check:
            join(a,room)
            for t in [host,b,c]:t.route('room/'+str(room['id']),'#waiting-room')
            recovery=start(host,[a,b,c]);report['restartGame']=recovery
            for i in range(1,10):
                for t in [a,b,c]:answer(t,i)
            ready_file=ROOT/f'target/t11-award-hold-{recovery}.ready';held_file=ready_file.with_suffix('.held');release_file=ready_file.with_suffix('.release')
            hold_log=(ROOT/'target/t11-restart-hold.log').open('w',encoding='utf-8')
            hold_process=subprocess.Popen(['java','--class-path',str(ROOT/'.cache/maven/com/mysql/mysql-connector-j/9.7.0/mysql-connector-j-9.7.0.jar'),str(ROOT/'scripts/AwardRestartFixture.java'),str(recovery),str(users['a']['id'])],cwd=ROOT,stdout=hold_log,stderr=subprocess.STDOUT)
            deadline=time.monotonic()+30
            while not ready_file.exists():
                assert hold_process.poll() is None,'Hold helper exited before ready'
                assert time.monotonic()<deadline,'Hold helper ready timeout'
                time.sleep(.1)
            for t in [a,b,c]:answer(t,10)
            deadline=time.monotonic()+30
            while not held_file.exists():
                assert hold_process.poll() is None,'Hold helper lost pending race; no state fabricated'
                assert time.monotonic()<deadline,'Pending hold timeout'
                time.sleep(.1)
            host.wait("document.querySelector('.award-panel')?.dataset.awardState==='AWARDS_PENDING'")
            assert not api(host,'GET',f'/api/games/{recovery}/awards')['awards']
            request=ROOT/f'target/t11-restart-{prefix}.request.json';signal=request.with_suffix('.continue')
            request.write_text(json.dumps(dict(game=recovery,release=str(release_file),signal=str(signal))),encoding='utf-8');print('RESTART_READY '+str(request),flush=True)
            deadline=time.monotonic()+180
            while not signal.exists():
                assert time.monotonic()<deadline,'Owned Server restart handshake timed out'
                time.sleep(.1)
            host.reload();host.wait("!!document.querySelector('#login-form')");host.fill(dict(username=prefix+'_host',password=password));host.submit('#login-form');host.wait("document.querySelector('#connection-status')?.dataset.state==='ready'")
            host.route('history/'+str(recovery),'#history-detail');host.wait("document.querySelector('.award-panel')?.dataset.awardState==='AWARDS_DECIDED'")
            restored=api(host,'GET',f'/api/games/{recovery}/awards');assert restored['qualification']['qualified']
            assert api(host,'GET',f'/api/games/{game}/awards')['awards']==before
            assert api(host,'GET',f'/api/games/{recovery}/awards')['awards']==restored['awards']
            host.eval("document.querySelector('.award-panel').scrollIntoView({block:'start'})");host.screenshot(images/'restart-recovered-360.png');passed('Real Server restart finalizes a genuinely pending decision once and retains all prior ledger IDs')
        assert not cdp.errors,cdp.errors;report.update(status='PASS',browser=cdp.version)
    except Exception as e:report.update(status='FAIL',error=str(e));raise
    finally:
        (ROOT/'target/t11-award-client-tests.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
        for t in tabs:cdp.call('Target.disposeBrowserContext',dict(browserContextId=t.context))
        cdp.socket.close()
        if hold_process and hold_process.poll() is None:
            if 'release_file' in locals():release_file.write_text('Driver cleanup releases only its SQL lock',encoding='utf-8')
            try:hold_process.wait(timeout=10)
            except subprocess.TimeoutExpired:hold_process.terminate()
        if hold_log:hold_log.close()

if __name__=='__main__':main()
