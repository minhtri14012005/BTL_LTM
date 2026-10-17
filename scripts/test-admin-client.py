"""T07: owned test MySQL only; CLI-generated secrets stay in process memory."""
import argparse
import importlib.util
import json
import os
import secrets
import subprocess
import uuid
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('browser',ROOT/'scripts/browser-cdp.py')
browser=importlib.util.module_from_spec(spec);spec.loader.exec_module(browser)

def main():
    p=argparse.ArgumentParser();p.add_argument('--origin',default='http://127.0.0.1:8088');p.add_argument('--port',type=int,default=9227);a=p.parse_args()
    report={'kind':'real CLI / MySQL / HTTP / WS / browser','checks':[]};tabs=[];cdp=browser.CDP(a.port)
    images=ROOT/'docs/assets/admin-2026-10-17';images.mkdir(parents=True,exist_ok=True)
    def passed(name):report['checks'].append(name);print('PASS '+name,flush=True)
    def request(tab,method,path,body=None):
        return tab.eval('(async()=>{const c=await fetch("/api/auth/csrf").then(r=>r.json());const r=await fetch('+json.dumps(path)+',{method:'+json.dumps(method)+',headers:{"Content-Type":"application/json",[c.headerName]:c.token},'+('body:'+json.dumps(json.dumps(body))+',' if body is not None else '')+'});return {status:r.status,body:await r.text()};})()')
    def api(tab,method,path,body=None):
        r=request(tab,method,path,body);assert 200<=r['status']<300,(path,r);return json.loads(r['body']) if r['body'] else None
    def tab():
        t=cdp.tab(a.origin);tabs.append(t);return t
    def login(t,name,password,admin=False):
        t.fill(dict(username=name,password=password));t.submit('#login-form')
        t.wait("location.pathname==='/admin'&&!!document.querySelector('#admin-page')" if admin else "document.querySelector('#connection-status')?.dataset.state==='ready'")
    try:
        name='t07admin_'+secrets.token_hex(5);secret=secrets.token_urlsafe(30)+'!Aa7';env=os.environ.copy()
        env.update(MULTIGAME_ADMIN_USERNAME=name,MULTIGAME_ADMIN_DISPLAY_NAME='Operator test',MULTIGAME_ADMIN_PASSWORD=secret)
        cmd=['java','-jar',str(ROOT/'target/multigame-0.0.1-SNAPSHOT.jar'),'--provision-admin','--spring.datasource.url=jdbc:mysql://127.0.0.1:3306/quizz_task2_test?connectionTimeZone=UTC&connectTimeout=3000&socketTimeout=3000','--debug=false','--logging.level.root=WARN','--logging.level.org.springframework=WARN','--logging.level.org.hibernate=WARN']
        for attempt in range(2):
            if attempt:env['MULTIGAME_ADMIN_PASSWORD']=secrets.token_urlsafe(30)+'!Aa7'
            result=subprocess.run(cmd,cwd=ROOT,env=env,text=True,encoding='utf-8',capture_output=True,timeout=50)
            assert result.returncode==0,result.stdout+result.stderr
            assert 'ADMIN provisioned or already present' in result.stdout
            assert 'Tomcat' not in result.stdout and 'SERVER_INTERRUPTED' not in result.stdout
        env.pop('MULTIGAME_ADMIN_PASSWORD');passed('Standalone non-web CLI provisions ADMIN twice idempotently without starting game/web runtime')
        admin=tab();login(admin,name,secret,True)
        assert api(admin,'GET','/api/auth/me')['role']=='ADMIN'
        assert admin.eval("!document.querySelector('#connection-status')&&!document.querySelector('#reconnect-room')&&!document.querySelector('a[href*=ban]')")
        passed('Common login redirects DB ADMIN to /admin; original password survives CLI rerun')
        admin.eval("window.adminReads=[];const oldFetch=window.fetch;window.fetch=function(input,options){adminReads.push({path:String(input),method:options?.method||'GET'});return oldFetch.apply(this,arguments);}")
        for section in ['users','orphans','games']:
            admin.route('admin?tab='+section,'#admin-page');admin.wait(f"document.querySelector('#admin-page').dataset.tab==='{section}'")
        assert admin.eval("adminReads.every(r=>r.method==='GET')&&!document.querySelector('[data-action=kick]')")
        passed('Three admin tabs read real paginated GET endpoints only; no audit/mutation controls')
        prefix='adm_ui_'+secrets.token_hex(4);password=secrets.token_urlsafe(24);users=[]
        for role in ['host','p1','p2','p3']:
            t=tab();api(t,'POST','/api/auth/register',dict(username=prefix+'_'+role,displayName='<img src=x onerror=alert(1)> '+role,password=password,role='ADMIN'));login(t,prefix+'_'+role,password);users.append(t)
            assert api(t,'GET','/api/auth/me')['role']=='USER'
            assert request(t,'GET','/api/admin/users')['status']==403
            assert request(t,'GET','/admin')['status']==403
        host,*players=users
        host.route('admin','#view');host.wait("document.querySelector('#view').textContent.includes('Cần quyền ADMIN')")
        assert host.eval("!document.querySelector('#admin-page')")
        spoof=host.eval("(async()=>{const {adminPage}=await import('/js/features/admin/page.js');try{await adminPage({user:{role:'ADMIN'},api:{request:async(method,path)=>{const r=await fetch(path);if(!r.ok)throw Error(String(r.status));return r.json();}}},'users');return 'bypass';}catch(e){return e.message;}})()")
        assert spoof=='403';passed('Public registration ignores forged ADMIN; USER/Host and edited JS cannot read admin data')
        bank=api(host,'POST','/api/quizzes',dict(title=prefix,mode='RIDDLE',visibility='PUBLIC',questions=[dict(content='Secret question',acceptedAnswers=['secret alias'])]))
        room=api(host,'POST','/api/rooms',dict(requestId=str(uuid.uuid4()),config=dict(name=prefix,maxPlayers=3,hostParticipation='SPECTATOR',stages=[dict(mode='RIDDLE',quizId=bank['id'],questionCount=1,questionDurationMs=60000)])))
        rid=room['id'];host.route('room/'+str(rid),'#open-room');host.click('#open-room');host.wait("!!document.querySelector('#start-game')")
        for t in players:
            t.route('join','#join-form');t.fill(dict(code=room['roomCode'],participation='PLAYER'));t.submit('#join-form');t.wait("!!document.querySelector('#waiting-room')")
        host.wait("!document.querySelector('#start-game').disabled");host.click('#start-game');host.wait("document.querySelector('#game-page')?.dataset.phase==='INTRO'")
        gid=int(host.eval("location.hash.split('/')[2]"));report.update(gameId=gid,roomId=rid)
        games=api(admin,'GET','/api/admin/games?size=100');actual=next(g for g in games['items'] if g['id']==gid)
        assert actual['roomId']==rid and actual['questionCount']==1 and actual['phase']=='INTRO'
        assert not any(word in json.dumps(games) for word in ['secret alias','Secret question','password','roomCode','acceptedAnswers'])
        assert request(admin,'GET',f'/api/games/{gid}/snapshot')['status']==403
        assert request(admin,'POST',f'/api/games/{gid}/cancel',dict(requestId=str(uuid.uuid4())))['status']==403
        env['MULTIGAME_ADMIN_PASSWORD']=secrets.token_urlsafe(30)+'!Aa7'
        during=subprocess.run(cmd,cwd=ROOT,env=env,text=True,encoding='utf-8',capture_output=True,timeout=50)
        env.pop('MULTIGAME_ADMIN_PASSWORD');assert during.returncode==0,during.stdout+during.stderr
        assert api(host,'GET',f'/api/games/{gid}/snapshot')['status']=='ACTIVE'
        assert any(g['id']==gid for g in api(admin,'GET','/api/admin/games?size=100')['items'])
        passed('CLI rerun while Game ACTIVE leaves runtime/occupancy intact')
        admin.click('#refresh-admin');admin.wait(f"document.querySelector('#admin-page')?.textContent.includes('#{gid}')")
        passed('Real ACTIVE Game appears as safe admin metadata; ADMIN cannot snapshot/cancel another Room Game')
        for section in ['games','users','orphans']:
            admin.route('admin?tab='+section,'#admin-page');admin.wait(f"document.querySelector('#admin-page').dataset.tab==='{section}'")
            for width in [1440,360]:
                cdp.call('Emulation.setDeviceMetricsOverride',{'width':width,'height':1000,'deviceScaleFactor':1,'mobile':False},admin.session)
                assert admin.eval('document.documentElement.scrollWidth<=innerWidth'),(section,width)
                assert not admin.eval("!!document.querySelector('#view img')")
                admin.screenshot(images/f'{section}-{width}.png')
        passed('Three admin screens fit 360/1440px; names render as text; local static modules load')
        cdp.call('Page.bringToFront',{},admin.session)
        cdp.call('Emulation.setFocusEmulationEnabled',{'enabled':True},admin.session)
        admin.eval("window.keyboardReads=adminReads.length;document.querySelector('#refresh-admin').focus()")
        assert admin.eval("document.activeElement.id==='refresh-admin'")
        for kind in ['keyDown','keyUp']:
            event={'type':kind,'key':'Enter','code':'Enter','windowsVirtualKeyCode':13}
            if kind=='keyDown':event.update(text='\r',unmodifiedText='\r')
            cdp.call('Input.dispatchKeyEvent',event,admin.session)
        admin.wait("adminReads.length>keyboardReads&&!!document.querySelector('#admin-page')")
        passed('Keyboard Enter activates refresh through the same read-only API')
        host.eval('window.confirm=()=>true');host.click('#cancel-game');host.wait("!!document.querySelector('#final-summary')")
        assert not any(g['id']==gid for g in api(admin,'GET','/api/admin/games?size=100')['items'])
        passed('Cancelled Game leaves active admin list; cancellation remains Host-only')
        admin.click('#logout');admin.wait("location.pathname==='/'&&!!document.querySelector('#login-form')")
        assert request(admin,'GET','/api/admin/users')['status']==401
        passed('ADMIN logout returns to common login, revokes session; admin data becomes 401')
        assert not cdp.errors,cdp.errors;report['status']='PASS';report['browser']=cdp.version
    except Exception as e:
        report['status']='FAIL';report['error']=str(e);raise
    finally:
        (ROOT/'target/t07-admin-client-tests.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
        for t in tabs:cdp.call('Target.disposeBrowserContext',{'browserContextId':t.context})
        cdp.socket.close()

if __name__=='__main__':main()
