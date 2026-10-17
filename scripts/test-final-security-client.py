"""T18 real HTTP/WS security negatives on the caller's isolated test Server."""
import argparse,importlib.util,json,secrets,uuid
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('browser',ROOT/'scripts/browser-cdp.py');browser=importlib.util.module_from_spec(spec);spec.loader.exec_module(browser)
def main():
    p=argparse.ArgumentParser();p.add_argument('--origin',default='http://192.168.1.234:8088');p.add_argument('--port',type=int,default=9227);args=p.parse_args()
    cdp=browser.CDP(args.port);tabs=[];report=dict(status='RUNNING',checks=[],sameMachine=True);prefix='security_'+secrets.token_hex(4);password=secrets.token_urlsafe(24)
    def passed(text):report['checks'].append(text);print('PASS '+text,flush=True)
    def tab():t=cdp.tab(args.origin);tabs.append(t);return t
    def request(t,method,path,body=None,csrf=True):
        headers='const c=await fetch("/api/auth/csrf").then(r=>r.json());const headers={"Content-Type":"application/json",[c.headerName]:c.token};' if csrf else 'const headers={"Content-Type":"application/json"};'
        return t.eval('(async()=>{'+headers+'const r=await fetch('+json.dumps(path)+',{method:'+json.dumps(method)+',headers,'+('body:'+json.dumps(json.dumps(body))+',' if body is not None else '')+'});return {status:r.status,body:await r.text()};})()')
    def api(t,method,path,body=None):
        response=request(t,method,path,body);assert 200<=response['status']<300,(path,response);return json.loads(response['body']) if response['body'] else None
    def account(label):
        t=tab();api(t,'POST','/api/auth/register',dict(username=prefix+'_'+label,displayName='<svg onload=alert(1)> '+label,password=password,role='ADMIN'));t.fill(dict(username=prefix+'_'+label,password=password));t.submit('#login-form');t.wait("document.querySelector('#connection-status')?.dataset.state==='ready'");assert api(t,'GET','/api/auth/me')['role']=='USER';return t
    def no_secrets(value):
        if isinstance(value,dict):
            assert not set(value)&{'password','passwordHash','sessionId','csrf','authEpoch'},set(value)
            for key,item in value.items():
                if key in {'acceptedAnswers','correctAnswer','correctOrder'}:assert item is None,(key,item)
                no_secrets(item)
        elif isinstance(value,list):
            for item in value:no_secrets(item)
    try:
        anonymous=tab()
        for path in ['/admin','/admin/login','/api/admin/users','/api/admin/games','/api/admin/orphan-rooms','/api/profiles/me','/api/leaderboard']:
            assert request(anonymous,'GET',path)['status']==401,path
        assert request(anonymous,'POST','/api/auth/register',dict(username=prefix+'_csrf',password=password,displayName='CSRF'),False)['status']==403
        passed('Anonymous protected routes return 401 and registration without CSRF returns 403')
        host,a,b,c,outside=[account(x) for x in ['host','a','b','c','outside']]
        for t in [host,a,outside]:
            for path in ['/admin','/admin/login','/api/admin/users','/api/admin/games','/ws/admin']:
                assert request(t,'GET',path)['status']==403,path
        assert request(a,'POST','/api/admin/users/1/ban',dict(requestId=str(uuid.uuid4()),expectedVersion=0,minutes=None,reason='Unauthorized test'))['status']==403
        passed('Forged registration role cannot grant ADMIN; USER/Host fail admin route/API/WS guards')
        bank=api(host,'POST','/api/quizzes',dict(title=prefix,visibility='PRIVATE',mode='RIDDLE',questions=[dict(content='Private source',acceptedAnswers=['unpublished answer'])]))
        room=api(host,'POST','/api/rooms',dict(requestId=str(uuid.uuid4()),config=dict(name=prefix,maxPlayers=3,hostParticipation='SPECTATOR',stages=[dict(mode='RIDDLE',quizId=bank['id'],questionCount=1,questionDurationMs=60000)])))
        host.route('room/'+str(room['id']),'#open-room');host.click('#open-room');host.wait("!!document.querySelector('#start-game')")
        for t in [a,b,c]:t.route('join','#join-form');t.fill(dict(code=room['roomCode'],participation='PLAYER'));t.submit('#join-form');t.wait("!!document.querySelector('#waiting-room')")
        current=api(host,'GET',f'/api/rooms/{room["id"]}');assert request(a,'POST',f'/api/rooms/{room["id"]}/start',dict(requestId=str(uuid.uuid4()),revision=current['revision'],questionCount=1))['status']==403
        assert request(outside,'GET',f'/api/rooms/{room["id"]}')['status']==403
        denied=request(outside,'GET',f'/api/quizzes/{bank["id"]}');assert denied['status']==404 and 'unpublished answer' not in denied['body']
        host.wait("!!document.querySelector('#start-game')&&!document.querySelector('#start-game').disabled");host.click('#start-game');host.wait("document.querySelector('#game-page')?.dataset.phase==='INTRO'");game=int(host.eval("location.hash.split('/')[2]"));report['gameId']=game
        for path in [f'/api/games/{game}/snapshot',f'/api/games/history/{game}',f'/api/games/{game}/awards']:
            assert request(outside,'GET',path)['status']==403,path
        no_secrets(api(a,'GET',f'/api/games/{game}/snapshot'));no_secrets(api(outside,'GET','/api/leaderboard'))
        passed('Host-only START and cross-user Room/Game/private-bank guards hold; INTRO/BXH DTOs expose no secrets or answers')
        refused=request(host,'POST',f'/api/games/{game}/cancel',dict(requestId=str(uuid.uuid4())),False);assert refused['status']==403
        assert api(host,'GET',f'/api/games/{game}/snapshot')['status']=='ACTIVE'
        passed('Missing-CSRF cancel is rejected and leaves the real Game ACTIVE')
        outside.eval("window.securityFrames=[];window.securitySocket=new WebSocket(new URL('/ws',location.href).href.replace(/^http/,'ws'));securitySocket.addEventListener('message',e=>securityFrames.push(JSON.parse(e.data)))")
        outside.wait("securityFrames.some(f=>f.type==='AUTH_READY')")
        for kind,target,payload in [('SUBSCRIBE_ROOM',dict(kind='ROOM',id=room['id']),{}),('RECONNECT',dict(kind='GAME',id=game),{})]:
            rid=str(uuid.uuid4());frame=dict(v=1,kind='COMMAND',requestId=rid,type=kind,target=target,questionIndex=None,payload=payload)
            outside.eval('securitySocket.send('+json.dumps(json.dumps(frame))+')');outside.wait('securityFrames.some(f=>f.requestId==='+json.dumps(rid)+')')
            response=outside.eval('securityFrames.find(f=>f.requestId==='+json.dumps(rid)+')');assert response['kind']=='ERROR' and response['code']=='FORBIDDEN',response
        outside.eval('securitySocket.close()');passed('Raw WebSocket cross-user SUBSCRIBE_ROOM and RECONNECT are rejected by Server')
        api(host,'POST',f'/api/games/{game}/cancel',dict(requestId=str(uuid.uuid4())));history=api(a,'GET',f'/api/games/history/{game}');assert history['finalSnapshot']['endReason']=='CANCELLED'
        assert request(outside,'GET',f'/api/games/history/{game}')['status']==403
        current=api(host,'GET',f'/api/rooms/{room["id"]}');api(host,'POST',f'/api/rooms/{room["id"]}/close',dict(requestId=str(uuid.uuid4()),revision=current['revision']))
        assert api(a,'GET','/api/auth/me')['role']=='USER' and api(a,'GET',f'/api/games/history/{game}')['questions']==history['questions']
        passed('Authorized cancel/Room close preserve History and other users sessions')
        api(outside,'POST','/api/auth/logout')
        for path in ['/api/auth/me','/api/admin/users','/api/profiles/me']:assert request(outside,'GET',path)['status']==401,path
        passed('Logout revokes session access to protected account/profile/admin data')
        assert not cdp.errors,cdp.errors;report.update(status='PASS',browser=cdp.version)
    except Exception as error:report.update(status='FAIL',error=str(error));raise
    finally:
        (ROOT/'target/t18-security-client-tests.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
        for t in tabs:cdp.call('Target.disposeBrowserContext',dict(browserContextId=t.context))
        cdp.socket.close();print(json.dumps(dict(status=report['status'],checks=len(report['checks']))))
if __name__=='__main__':main()
