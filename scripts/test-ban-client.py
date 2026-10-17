"""T09 real HTTP/WS/MySQL UI. Uses only the caller's owned test Server and headless Chrome."""
import argparse
import importlib.util
import json
import os
import secrets
import subprocess
import time
import uuid
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('browser',ROOT/'scripts/browser-cdp.py');browser=importlib.util.module_from_spec(spec);spec.loader.exec_module(browser)

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--origin',default='http://192.168.1.234:8088');parser.add_argument('--port',type=int,default=9227);parser.add_argument('--restart-check',action='store_true');parser.add_argument('--runtime-jar',default=str(ROOT/'target/multigame-0.0.1-SNAPSHOT.jar'));args=parser.parse_args()
    cdp=browser.CDP(args.port);tabs=[];report={'status':'RUNNING','checks':[]};images=ROOT/'docs/assets/ban-2026-10-17';images.mkdir(parents=True,exist_ok=True)
    prefix='ban_'+secrets.token_hex(4);password=secrets.token_urlsafe(24);accounts={}
    def passed(label):report['checks'].append(label);print('PASS '+label,flush=True)
    def tab():
        t=cdp.tab(args.origin);cdp.call('Emulation.setDeviceMetricsOverride',dict(width=1440,height=1000,deviceScaleFactor=1,mobile=False),t.session);tabs.append(t);return t
    def raw(t,path):return t.eval('fetch('+json.dumps(path)+').then(async r=>({status:r.status,body:await r.text()}))')
    def req(t,method,path,body=None):
        if method=='GET':return raw(t,path)
        return t.eval('(async()=>{const c=await fetch("/api/auth/csrf").then(r=>r.json());const r=await fetch('+json.dumps(path)+',{method:'+json.dumps(method)+',headers:{"Content-Type":"application/json",[c.headerName]:c.token},body:'+json.dumps(json.dumps(body))+'});return {status:r.status,body:await r.text()};})()')
    def api(t,method,path,body=None):
        result=req(t,method,path,body);assert 200<=result['status']<300,(path,result);return json.loads(result['body']) if result['body'] else None
    def login(t,name,secret=password):
        t.wait("!!document.querySelector('#login-form')");t.fill(dict(username=name,password=secret));t.submit('#login-form');t.wait("document.querySelector('#connection-status')?.dataset.state==='ready'")
    def player(label):
        t=tab();name=prefix+'_'+label;api(t,'POST','/api/auth/register',dict(username=name,displayName='Ban '+label,password=password));login(t,name);accounts[label]=api(t,'GET','/api/auth/me');return t
    def room(host,label,count):
        bank=api(host,'POST','/api/quizzes',dict(title=prefix+label,mode='RIDDLE',visibility='PRIVATE',questions=[dict(content='Question '+str(i),acceptedAnswers=['answer']) for i in range(count)]))
        result=api(host,'POST','/api/rooms',dict(requestId=str(uuid.uuid4()),config=dict(name=prefix+label,maxPlayers=3,hostParticipation='SPECTATOR',stages=[dict(mode='RIDDLE',quizId=bank['id'],questionCount=count,questionDurationMs=60000)])))
        host.route('room/'+str(result['id']),'#open-room');host.click('#open-room');host.wait("!!document.querySelector('#start-game')");return result
    def join(t,r):t.route('join','#join-form');t.fill(dict(code=r['roomCode'],participation='PLAYER'));t.submit('#join-form');t.wait("!!document.querySelector('#waiting-room')")
    def start(host):host.wait("!document.querySelector('#start-game').disabled");host.click('#start-game');host.wait("document.querySelector('#game-page')?.dataset.phase==='INTRO'");return int(host.eval("location.hash.split('/')[2]"))
    def ready(t):t.wait("!!document.querySelector('#continue-stage')&&!document.querySelector('#continue-stage').disabled");t.click('#continue-stage')
    def answer(t):t.wait("!!document.querySelector('#answer-text')&&!document.querySelector('#answer-text').disabled");t.fill(dict(answerText='answer'));t.click('#submit-answer')
    def row(user):return f'.admin-sanction[data-user-id="{user}"]'
    def choose(admin,user,action,reason,minutes=None):
        selector=row(user);admin.click(selector+' [data-action=sanction]');admin.fill(dict(sanctionAction=action,banReason=reason),selector+' ')
        if minutes is not None:admin.fill(dict(banMinutes=minutes),selector+' ')
        admin.submit(selector+' .sanction-form')
    def wait_done(admin,user):admin.wait(f'document.querySelector({json.dumps(row(user))})?.dataset.sanctionStatus==="CONFIRMED"')
    try:
        admin_name='banadm_'+secrets.token_hex(5);admin_secret=secrets.token_urlsafe(30)+'!Aa7';env=os.environ.copy();env.update(MULTIGAME_ADMIN_USERNAME=admin_name,MULTIGAME_ADMIN_DISPLAY_NAME='Ban operator',MULTIGAME_ADMIN_PASSWORD=admin_secret)
        command=['java','-jar',args.runtime_jar,'--provision-admin','--spring.datasource.url=jdbc:mysql://127.0.0.1:3306/quizz_task2_test?connectionTimeZone=UTC&connectTimeout=3000&socketTimeout=3000','--debug=false','--logging.level.root=WARN','--logging.level.org.springframework=WARN','--logging.level.org.hibernate=WARN']
        process=subprocess.run(command,cwd=ROOT,env=env,text=True,encoding='utf-8',capture_output=True,timeout=60);env.pop('MULTIGAME_ADMIN_PASSWORD');assert process.returncode==0,process.stdout+process.stderr
        admin=tab();admin.fill(dict(username=admin_name,password=admin_secret));admin.submit('#login-form');admin.wait("location.pathname==='/admin'&&!!document.querySelector('#admin-page')")
        host=player('host');b=player('b');c=player('c');d=player('d');yh=player('yh');yc=player('yc');yd=player('yd');orphan=player('orphan')
        x=room(host,'X',2);y=room(yh,'Y',1)
        for t in [b,c,d]:join(t,x)
        for t in [yc,yd]:join(t,y)
        gx=start(host)
        for t in [b,c,d]:ready(t)
        b.wait("document.querySelector('#game-page')?.dataset.phase==='QUESTION_OPEN'");answer(b);b.wait("!!document.querySelector('#answer-accepted')")
        # Independent pre-ban HTTP login. The project intentionally keeps only one current WS per user.
        old=tab();login(old,prefix+'_b');old.route('game/'+str(gx),'#game-page');old.wait("!!document.querySelector('#answer-accepted')");bid=accounts['b']['id'];report.update(gameX=gx,roomX=x['id'],roomY=y['id'],userId=bid)
        admin.route('admin?tab=users','#admin-page');admin.wait(f'!!document.querySelector({json.dumps(row(bid))})')
        selector=row(bid);admin.click(selector+' [data-action=sanction]');admin.click(selector+' .confirm-panel > button');assert api(old,'GET','/api/auth/me')['id']==bid
        passed('Common ADMIN login and real users list; cancel confirm does not mutate sanction')
        admin.eval(r'''window.banRequests=[];window.dropBanReceipt=true;const originalFetch=fetch;window.fetch=async function(path,options){const response=await originalFetch.apply(this,arguments);if(options?.method==='POST'&&/\/(temporary-ban|ban|unban)$/.test(String(path))){banRequests.push({path:String(path),body:JSON.parse(options.body)});if(dropBanReceipt&&response.ok){dropBanReceipt=false;window.banCommitted=await response.clone().json();throw TypeError('Controlled lost sanction receipt');}}return response;};''')
        choose(admin,bid,'temporary-ban','T09 UI moderation',2)
        admin.wait(f'document.querySelector({json.dumps(selector)})?.dataset.sanctionStatus==="PENDING"&&document.querySelector({json.dumps(selector+" button[type=submit]")}).textContent.includes("Thử lại")')
        assert admin.eval('banCommitted.committedKind==="TEMP"&&banCommitted.committedVersion===1&&banCommitted.committedUntilMs-banCommitted.effectiveAtMs===120000')
        old.wait("!!document.querySelector('#login-form')&&document.querySelector('#view').textContent.includes('khóa')")
        assert raw(b,'/api/auth/me')['status'] in [401,403];assert raw(old,'/app.js')['status']==200
        admin.screenshot(images/'admin-pending-1440.png');old.screenshot(images/'banned-login-1440.png')
        passed('TEMP commits on Server despite lost receipt; pre-ban sessions blocked and target UI disposes gameplay')
        admin.click(selector+' button[type=submit]');wait_done(admin,bid);assert admin.eval('banRequests.length===2&&JSON.stringify(banRequests[0])===JSON.stringify(banRequests[1])');first=admin.eval('banRequests[0]');receipt=admin.eval('banCommitted');action=receipt['actionId'];report['actionId']=action
        assert admin.eval(f'document.querySelector({json.dumps(selector+" .sanction-status")}).textContent.includes("Ban tạm đến")')
        old.fill(dict(username=prefix+'_b',password=password));old.submit('#login-form');old.wait("document.querySelector('#login-form .action-feedback')?.textContent.includes('khóa tạm thời đến')")
        passed('Retry uses same UUID/body, TEMP expiry comes from Server and banned login is rejected')
        choose(admin,bid,'unban','T09 restore account');wait_done(admin,bid);login(old,prefix+'_b')
        # Reuse old browser cookie through the shared login. No session old epoch is revived.
        assert req(old,'GET',f'/api/games/{gx}/snapshot')['status']==403
        old.wait('true');assert req(old,'GET',f'/api/rooms/{x["id"]}')['status']==403
        replay=api(admin,'POST',first['path'],first['body']);assert replay['replayed'] and replay['current']['kind']=='NONE' and replay['current']['version']==2
        assert api(old,'GET','/api/auth/me')['id']==bid
        passed('UNBAN requires fresh login; replay of old ban returns receipt/current NONE and cannot re-ban or reopen X')
        join(old,y);gy=start(yh);report['gameY']=gy
        old.wait(f'location.hash==="#/game/{gy}"')
        for t in [old,yc,yd]:ready(t)
        old.wait("document.querySelector('#game-page')?.dataset.phase==='QUESTION_OPEN'")
        assert api(old,'GET',f'/api/games/{gy}/snapshot')['status']=='ACTIVE'
        answer(c);answer(d)
        for t in [c,d]:t.wait("document.querySelector('#game-page')?.dataset.index==='2'&&document.querySelector('#game-page').dataset.phase==='QUESTION_OPEN'")
        answer(c);answer(d);host.wait("!!document.querySelector('#final-summary')")
        assert api(old,'GET',f'/api/games/{gy}/snapshot')['status']=='ACTIVE'
        hx=api(old,'GET',f'/api/games/history/{gx}');own=[a for q in hx['questions'] for a in q['answers'] if a['userId']==bid]
        assert len(own)==1 and own[0]['answerStatus']=='CORRECT' and any(r['userId']==bid and r['kind']=='BANNED_BY_ADMIN' for r in hx['restrictions'])
        passed('Legitimate release X permits Game Y; X scoring/finish retains Answer/History without deleting Y')
        for t in [old,yc,yd]:answer(t)
        for t in [old,yc,yd]:t.wait("!!document.querySelector('#final-summary')")
        join(old,x);assert api(old,'GET',f'/api/rooms/{x["id"]}')['status']=='WAITING';old.screenshot(images/'rejoined-room-1440.png')
        passed('Y finishes and releases occupancy before legal rejoin g2 in WAITING Room X')
        z=room(orphan,'Orphan',1);orphan_ban=api(admin,'POST',f'/api/admin/users/{accounts["orphan"]["id"]}/ban',dict(requestId=str(uuid.uuid4()),expectedVersion=0,minutes=None,reason='T09 orphan Host moderation'))
        admin.route('admin?tab=orphans','#admin-page');orow=f'.admin-orphan-action[data-room-id="{z["id"]}"]';admin.wait(f'!!document.querySelector({json.dumps(orow)})')
        admin.click(orow+' [data-action=close-orphan]');admin.fill(dict(orphanReason='T09 close WAITING orphan'),orow+' ');admin.submit(orow+' form');admin.wait(f'document.querySelector({json.dumps(orow+" [data-action=close-orphan]")})?.disabled')
        assert admin.eval(f'document.querySelector({json.dumps(orow)}).closest("tr").cells[4].textContent')=='CLOSED'
        assert api(c,'GET','/api/auth/me')['id']==accounts['c']['id'];admin.screenshot(images/'orphan-closed-1440.png')
        passed('ADMIN closes a genuinely banned Host WAITING room with confirm; another user remains authenticated')
        admin.route('admin?tab=users','#admin-page');admin.wait(f'!!document.querySelector({json.dumps(row(bid))})')
        cdp.call('Emulation.setDeviceMetricsOverride',dict(width=360,height=900,deviceScaleFactor=1,mobile=False),admin.session);admin.screenshot(images/'admin-360.png');assert admin.eval('document.documentElement.scrollWidth<=innerWidth')
        for path in ['/js/features/admin/sanctions.js','/styles/features/admin.css']:assert raw(admin,path)['status']==200
        passed('ADMIN controls load without CDN/assets errors and do not overflow 360px')
        choose(admin,accounts['c']['id'],'ban','T09 WAITING non-Host cleanup');wait_done(admin,accounts['c']['id'])
        host.wait(f'fetch("/api/rooms/{x["id"]}").then(r=>r.json()).then(r=>r.status==="WAITING"&&!r.members.some(m=>m.userId==={accounts["c"]["id"]}))')
        waiting=api(host,'GET',f'/api/rooms/{x["id"]}')
        assert any(m['userId']==bid for m in waiting['members']) and waiting['hostUserId']==accounts['host']['id']
        denied=req(admin,'POST',f'/api/admin/rooms/{x["id"]}/close-orphan',dict(requestId=str(uuid.uuid4()),revision=waiting['revision'],reason='T09 healthy Host guard'))
        assert denied['status']==409 and 'HOST_STILL_AVAILABLE' in denied['body']
        assert api(host,'GET','/api/auth/me')['id']==accounts['host']['id'] and api(old,'GET','/api/auth/me')['id']==bid
        passed('Ban non-Host in WAITING detaches only captured membership; healthy Host room cannot be orphan-closed')
        if args.restart_check:
            # A second real ACTIVE Game supplies a genuinely pending ejection at restart.
            # The owner holds only Room/Game SQL locks; BAN must commit independently on User.
            yh.route('room/'+str(y['id']),'#waiting-room');gy2=start(yh);report['interruptedGame']=gy2
            hold=ROOT/'target'/f't09-hold-{prefix}.request.json';held=hold.with_suffix('.held')
            hold.write_text(json.dumps(dict(gameId=gy2,userId=accounts['yc']['id'],roomId=y['id'],signal=str(held))),encoding='utf-8');print('HOLD_REQUEST '+str(hold),flush=True)
            deadline=time.monotonic()+180
            while not held.exists():
                if time.monotonic()>deadline:raise AssertionError('Owned Room/Game hold handshake timed out')
                time.sleep(.1)
            pending=api(admin,'POST',f'/api/admin/users/{accounts["yc"]["id"]}/ban',dict(requestId=str(uuid.uuid4()),expectedVersion=0,minutes=None,reason='T09 real restart pending ejection'))
            report['pendingAction']=pending['actionId'];passed('BAN commits through real HTTP while old Room/Game locks deliberately delay ejection')
            request=ROOT/'target'/f't09-restart-{prefix}.request.json';signal=request.with_suffix('.continue')
            request.write_text(json.dumps(dict(actionId=action,userId=bid,gameX=gx,gameY=gy,roomX=x['id'],pendingAction=pending['actionId'],pendingUser=accounts['yc']['id'],pendingRoom=y['id'],pendingGame=gy2,release=str(ROOT/'target'/f't09-hold-game-{gy2}.release'),signal=str(signal))),encoding='utf-8');print('RESTART_READY '+str(request),flush=True)
            deadline=time.monotonic()+180
            while not signal.exists():
                if time.monotonic()>deadline:raise AssertionError('Owned Server restart handshake timed out')
                time.sleep(.1)
            old.reload();login(old,prefix+'_b');assert api(old,'GET',f'/api/rooms/{x["id"]}')['status']=='WAITING';assert raw(old,f'/api/games/{gx}/snapshot')['status']==403
            assert api(old,'GET',f'/api/games/{gy}/snapshot')['status']=='FINISHED';assert any(r['kind']=='BANNED_BY_ADMIN' for r in api(old,'GET',f'/api/games/history/{gx}')['restrictions'])
            assert api(old,'GET','/api/auth/me')['id']==bid
            assert api(old,'GET',f'/api/games/{gy2}/snapshot')['endReason']=='SERVER_INTERRUPTED'
            yc.reload();yc.wait("!!document.querySelector('#login-form')");yc.fill(dict(username=prefix+'_yc',password=password));yc.submit('#login-form');yc.wait("document.querySelector('#login-form .action-feedback')?.textContent.includes('vô thời hạn')")
            passed('Real restart reconciles original pending targets without detaching g2; X restriction/History and Y survive')
        assert not cdp.errors,cdp.errors;report.update(status='PASS',browser=cdp.version)
    except Exception as error:report.update(status='FAIL',error=str(error));raise
    finally:
        (ROOT/'target/t09-ban-client-tests.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
        for t in tabs:cdp.call('Target.disposeBrowserContext',dict(browserContextId=t.context))
        cdp.socket.close()

if __name__=='__main__':main()
