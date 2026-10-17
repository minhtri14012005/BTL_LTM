"""T04: isolated browser sessions against real HTTP/WS + test MySQL only."""
import argparse
import importlib.util
import json
import secrets
import uuid
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('browser',ROOT/'scripts/browser-cdp.py')
browser=importlib.util.module_from_spec(spec);spec.loader.exec_module(browser)
OBSERVER=r'''window.roomWire={sent:[],received:[]};const originalSend=WebSocket.prototype.send;
WebSocket.prototype.send=function(text){roomWire.socket=this;roomWire.sent.push(JSON.parse(text));
 if(!this.roomObserved){this.roomObserved=true;const receive=this.onmessage;this.onmessage=function(event){roomWire.received.push(JSON.parse(event.data));receive.call(this,event);};}
 return originalSend.call(this,text);};'''

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--origin',default='http://127.0.0.1:8088');parser.add_argument('--port',type=int,default=9227);args=parser.parse_args()
    cdp=browser.CDP(args.port);tabs=[];users=[]
    report={'status':'RUNNING','kind':'real HTTP/WS/MySQL smoke','checks':[]}
    def passed(name):report['checks'].append(name);print('PASS '+name,flush=True)
    def api(tab,method,path,body=None):
        return tab.eval('(async()=>{const csrf=await fetch("/api/auth/csrf").then(r=>r.json());const r=await fetch('+json.dumps(path)+',{method:'+json.dumps(method)+',headers:{"Content-Type":"application/json",[csrf.headerName]:csrf.token},'+('body:'+json.dumps(json.dumps(body))+',' if body is not None else '')+'});return {status:r.status,body:await r.json()};})()')
    def ok(tab,method,path,body=None):
        response=api(tab,method,path,body);assert 200<=response['status']<300,response;return response['body']
    def token(tab):return str(uuid.uuid4())
    try:
        prefix='wait_'+secrets.token_hex(4);password=secrets.token_urlsafe(20)
        for role in ['host','author','p2','p3']:
            tab=cdp.tab(args.origin);tabs.append(tab)
            tab.route('register','#register-form');tab.fill(dict(username=prefix+'_'+role,displayName='Waiting '+role,password=password,confirmPassword=password));tab.submit('#register-form')
            tab.wait("!!document.querySelector('#login-form')");tab.fill(dict(username=prefix+'_'+role,password=password));tab.submit('#login-form')
            tab.wait("document.querySelector('#connection-status')?.dataset.state==='ready'")
            users.append(ok(tab,'GET','/api/auth/me'));tab.eval(OBSERVER)
        host,author,p2,p3=tabs
        def bank(tab,title):return ok(tab,'POST','/api/quizzes',dict(title=title,mode='RIDDLE',visibility='PUBLIC',questions=[dict(content='Cái gì càng lấy bớt đi thì càng lớn?',acceptedAnswers=['hố'])]))
        original=bank(host,prefix+' original');replacement=bank(author,prefix+' replacement')
        def config(quiz):return dict(name=prefix,maxPlayers=3,hostParticipation='SPECTATOR',stages=[dict(mode='RIDDLE',quizId=quiz['id'],questionCount=1,questionDurationMs=30000)])
        room=ok(host,'POST','/api/rooms',dict(requestId=token(host),config=config(original)));rid=room['id'];report['roomId']=rid
        host.route('room/'+str(rid),'#waiting-room');host.click('#open-room');host.wait("!!document.querySelector('#start-game')")
        for tab in [author,p2,p3]:
            tab.route('join','#join-form');tab.fill(dict(code=room['roomCode'],participation='PLAYER'));tab.submit('#join-form');tab.wait("!!document.querySelector('#waiting-room')")
        for tab in tabs:tab.wait("document.querySelectorAll('#roster .member').length===4&&document.querySelector('#player-count').textContent==='3/3 người chơi'")
        current=ok(host,'GET',f'/api/rooms/{rid}')
        for tab in tabs:
            snapshot=ok(tab,'GET',f'/api/rooms/{rid}');assert snapshot==current
            assert sorted(tab.eval("[...document.querySelectorAll('#roster .member')].map(n=>Number(n.dataset.userId))"))==sorted(u['id'] for u in users)
        assert not author.eval("!!document.querySelector('#start-game')||!!document.querySelector('#show-close-room')||!!document.querySelector('[data-remove]')")
        passed('Four isolated sessions share exact committed Room snapshot and roster; Host-only controls')
        for path,method,body in [(f'/api/rooms/{rid}','PUT',dict(requestId=token(author),revision=current['revision'],config=config(original))),
                                 (f'/api/rooms/{rid}/close','POST',dict(requestId=token(author),revision=current['revision']))]:
            response=api(author,method,path,body);assert response['status']==403 and response['body']['code']=='FORBIDDEN',response
        author.eval(f"(async()=>{{const {{command}}=await import('/js/core/protocol.js');roomWire.socket.send(JSON.stringify(command('START_GAME',{rid},{{revision:{current['revision']},questionCount:1}})));}})()")
        author.wait("roomWire.received.some(m=>m.kind==='ERROR'&&m.code==='FORBIDDEN')")
        passed('Server rejects member forged Edit/Close HTTP and START_GAME WS commands')
        host.route(f'room/{rid}/edit','#room-form');host.fill({'quizId':str(replacement['id'])});host.submit('#room-form')
        host.wait("!!document.querySelector('.action-feedback [role=alert]')")
        unchanged=ok(host,'GET',f'/api/rooms/{rid}');assert unchanged['revision']==current['revision'] and unchanged['stages'][0]['quizId']==original['id']
        passed('Changing pack to a joined PLAYER author is rejected atomically; plan/revision remain unchanged')
        author.fill({'participation':'SPECTATOR'});author.click('#change-participation')
        author.wait("document.querySelector('#player-count').textContent==='2/3 người chơi'")
        # Changing participation increments the Room revision. Reload the editor before saving.
        host.reload();host.wait("!!document.querySelector('#room-form')&&!document.querySelector('.action-feedback')")
        host.eval(OBSERVER)
        host.fill({'quizId':str(replacement['id'])});host.submit('#room-form');host.wait("!!document.querySelector('#waiting-room')&&document.querySelector('.room-stage-list h3')?.textContent.includes('replacement')")
        for tab in tabs:tab.wait("document.querySelector('.room-stage-list h3')?.textContent.includes('replacement')")
        assert author.eval("document.querySelector('#participation [value=PLAYER]').disabled")
        assert host.eval("document.querySelector('#start-game').disabled")
        passed('Author changes to SPECTATOR; pack edit then succeeds and broadcasts to all sessions')
        # Bypass disabled option to prove the unchanged server rule is authoritative.
        before=ok(host,'GET',f'/api/rooms/{rid}')
        author.eval(f"(async()=>{{const {{command}}=await import('/js/core/protocol.js');roomWire.socket.send(JSON.stringify(command('JOIN_ROOM',{rid},{{roomCode:{json.dumps(room['roomCode'])},participation:'PLAYER'}})));}})()")
        author.wait("roomWire.received.some(m=>m.kind==='ERROR'&&m.code==='QUIZ_AUTHOR_CANNOT_PLAY')")
        assert ok(host,'GET',f'/api/rooms/{rid}')==before
        host.fill({'participation':'PLAYER'});host.click('#change-participation');host.wait("!document.querySelector('#start-game').disabled")
        passed('Server rejects forged author PLAYER transition; non-author Host can play and satisfy minimum')
        before=ok(host,'GET',f'/api/rooms/{rid}')
        p2.eval("roomWire.socket.close()")
        p2.wait("document.querySelector('#connection-status')?.dataset.state==='offline'")
        assert p2.eval("document.querySelector('#change-participation').disabled&&document.querySelectorAll('#roster .member').length===4")
        assert ok(host,'GET',f'/api/rooms/{rid}')==before
        p2.click('#reconnect-room');p2.wait("document.querySelector('#connection-status')?.dataset.state==='ready'&&roomWire.sent.filter(m=>m.type==='SUBSCRIBE_ROOM').length===2")
        p2.wait("document.querySelector('.member-connection')?.textContent==='Đã đồng bộ phòng'")
        assert ok(host,'GET',f'/api/rooms/{rid}')==before
        assert p2.eval("document.querySelectorAll('#roster .member').length===4")
        passed('Disconnect preserves membership/revision; one reconnect resubscribes without duplicate member')
        host.eval("Object.defineProperty(navigator,'clipboard',{value:undefined,configurable:true})");host.click('#copy-room-code')
        host.wait("document.querySelector('#copy-room-status').textContent.length>0")
        assert host.eval("getSelection().toString()===document.querySelector('#room-code').textContent")
        passed('Copy fallback selects real Room code on LAN without Clipboard API')
        out=ROOT/'docs/assets/waiting-room-2026-10-16';out.mkdir(parents=True,exist_ok=True)
        for tab,page,width in [(host,'host',1440),(author,'spectator',360),(p2,'player',390)]:
            cdp.call('Emulation.setDeviceMetricsOverride',dict(width=width,height=1100,deviceScaleFactor=1,mobile=width<500),tab.session)
            assert tab.eval('document.documentElement.scrollWidth<=innerWidth'),page
            tab.screenshot(out/(page+'-'+str(width)+'.png'))
            if page=='spectator':
                tab.eval("document.querySelector('.room-plan').scrollIntoView({block:'start'})")
                tab.screenshot(out/'spectator-plan-360.png')
        passed('Real Host/Player/Spectator screens fit desktop and mobile touch viewports')
        host.click('#start-game')
        for tab in tabs:tab.wait("!!document.querySelector('#game-page')")
        gid=int(host.eval("location.hash.split('/')[2]"));report['gameSessionId']=gid
        snapshot=ok(author,'GET',f'/api/games/{gid}/snapshot');assert snapshot['player'] is None and len(snapshot['members'])==4
        host.eval("roomWire.socket.send(JSON.stringify(roomWire.sent.find(m=>m.type==='START_GAME')))")
        host.wait("roomWire.received.filter(m=>m.kind==='ACK'&&m.type==='START_GAME').length===2")
        acks=host.eval("roomWire.received.filter(m=>m.kind==='ACK'&&m.type==='START_GAME')")
        assert acks[0]==acks[1] and acks[0]['payload']['gameSessionId']==gid
        passed('START enters existing Game for every session; identical command replays same game receipt')
        host.eval('window.confirm=()=>true');host.click('#cancel-game')
        for tab in tabs:tab.wait("!!document.querySelector('#final-summary')")
        assert ok(host,'GET',f'/api/rooms/{rid}')['status']=='WAITING'
        cancelled=ok(host,'GET',f'/api/games/{gid}/snapshot');assert cancelled['status']=='FINISHED' and cancelled['endReason']=='CANCELLED'
        host.route('room/'+str(rid),'#waiting-room');host.wait("document.querySelectorAll('#roster .member').length===4&&!!document.querySelector('#start-game')")
        passed('Existing CANCEL_GAME returns Room to WAITING and keeps all members')
        assert not cdp.errors,cdp.errors;report['status']='PASS'
    except Exception as error:
        report.update(status='FAIL',error=str(error));raise
    finally:
        for tab in tabs:cdp.call('Target.disposeBrowserContext',{'browserContextId':tab.context})
        (ROOT/'target/t04-waiting-room-tests.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
        print(json.dumps({'status':report['status'],'checks':len(report['checks'])}))

if __name__=='__main__':main()
