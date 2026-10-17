"""T08 real HTTP/WS/MySQL/LAN UI; only the first successful Kick receipt is dropped locally."""
import argparse
import importlib.util
import json
import os
import secrets
import subprocess
import struct
import time
import uuid
import zlib
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('browser',ROOT/'scripts/browser-cdp.py');browser=importlib.util.module_from_spec(spec);spec.loader.exec_module(browser)
SOCKET_OBSERVER=r'''(()=>{const Original=WebSocket;window.kickFrames=[];window.WebSocket=class extends Original{constructor(...args){super(...args);window.kickSocket=this;this.addEventListener('message',e=>{try{kickFrames.push(JSON.parse(e.data));}catch{}});}};})()'''

def main():
    p=argparse.ArgumentParser();p.add_argument('--origin',default='http://192.168.1.234:8088');p.add_argument('--port',type=int,default=9227);p.add_argument('--restart-check',action='store_true');a=p.parse_args()
    cdp=browser.CDP(a.port);tabs=[];report={'status':'RUNNING','kind':'real MySQL/HTTP/WS UI; dropped receipt at client','checks':[]};images=ROOT/'docs/assets/kick-2026-10-17';images.mkdir(parents=True,exist_ok=True)
    def passed(label):report['checks'].append(label);print('PASS '+label,flush=True)
    def tab():
        t=cdp.tab(a.origin);tabs.append(t);t.eval(SOCKET_OBSERVER);t.eval('window.confirm=()=>true');return t
    def req(t,method,path,body=None):
        return t.eval('(async()=>{const c=await fetch("/api/auth/csrf").then(r=>r.json());const r=await fetch('+json.dumps(path)+',{method:'+json.dumps(method)+',headers:{"Content-Type":"application/json",[c.headerName]:c.token},'+('body:'+json.dumps(json.dumps(body))+',' if body is not None else '')+'});return {status:r.status,body:await r.text()};})()')
    def api(t,method,path,body=None):
        r=req(t,method,path,body);assert 200<=r['status']<300,(path,r);return json.loads(r['body']) if r['body'] else None
    prefix='kick_'+secrets.token_hex(4);password=secrets.token_urlsafe(24);accounts={}
    def player(label):
        t=tab();name=prefix+'_'+label;api(t,'POST','/api/auth/register',dict(username=name,displayName='Kick '+label,password=password));t.fill(dict(username=name,password=password));t.submit('#login-form');t.wait("document.querySelector('#connection-status')?.dataset.state==='ready'");accounts[label]=api(t,'GET','/api/auth/me');return t
    def room(host,label,count=2):
        bank=api(host,'POST','/api/quizzes',dict(title=prefix+label,mode='RIDDLE',visibility='PRIVATE',questions=[dict(content='Question '+str(i),acceptedAnswers=['answer']) for i in range(count)]))
        r=api(host,'POST','/api/rooms',dict(requestId=str(uuid.uuid4()),config=dict(name=prefix+label,maxPlayers=3,hostParticipation='SPECTATOR',stages=[dict(mode='RIDDLE',quizId=bank['id'],questionCount=count,questionDurationMs=60000)])))
        host.route('room/'+str(r['id']),'#open-room');host.click('#open-room');host.wait("!!document.querySelector('#start-game')");return r
    def media_room(host):
        banks={}
        fixture=ROOT/'target/t08-kick-image.png'
        def chunk(kind,data):return struct.pack('!I',len(data))+kind+data+struct.pack('!I',zlib.crc32(kind+data)&0xffffffff)
        fixture.write_bytes(b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('!2I5B',8,8,8,2,0,0,0))+chunk(b'IDAT',zlib.compress((b'\0'+bytes((24,124,218))*8)*8))+chunk(b'IEND',b''))
        for mode,count in [('SONG',2),('IMAGE_WORD',1)]:
            host.route('quizzes/new','#quiz-form');host.fill(dict(mode=mode,title=prefix+' '+mode,visibility='PRIVATE'))
            for number in range(count):
                if number:host.click('#add-question')
                row='.question-editor:last-child ';host.fill(dict(content=mode+' '+str(number+1),acceptedAnswers='answer'),row)
                host.upload(row+('[name=video]' if mode=='SONG' else '[name=image]'),ROOT/'src/test/resources/quiz/song-av.mp4' if mode=='SONG' else fixture)
            host.submit('#quiz-form');host.wait("location.hash.endsWith('/edit')&&!!document.querySelector('#quiz-form')")
            bid=int(host.eval("location.hash.split('/')[2]"));banks[mode]=api(host,'GET',f'/api/quizzes/{bid}')
        song,image=banks['SONG'],banks['IMAGE_WORD']
        r=api(host,'POST','/api/rooms',dict(requestId=str(uuid.uuid4()),config=dict(name=prefix+'X',maxPlayers=3,hostParticipation='SPECTATOR',stages=[dict(mode=mode,quizId=banks[mode]['id'],questionCount=2 if mode=='SONG' else 1,questionDurationMs=60000) for mode in ['SONG','IMAGE_WORD']])))
        host.route('room/'+str(r['id']),'#open-room');host.click('#open-room');host.wait("!!document.querySelector('#start-game')")
        return r,dict(video=f'/api/quizzes/{song["id"]}/videos/'+song['questions'][0]['mediaRef'][7:],image=f'/api/quizzes/{image["id"]}/images/'+image['questions'][0]['imageRef'][7:])
    def media_status(t,path):return t.eval('fetch('+json.dumps(path)+').then(r=>r.status)')
    def join(t,r):t.route('join','#join-form');t.fill(dict(code=r['roomCode'],participation='PLAYER'));t.submit('#join-form');t.wait("!!document.querySelector('#waiting-room')")
    def start(host):host.wait("!document.querySelector('#start-game').disabled");host.click('#start-game');host.wait("document.querySelector('#game-page')?.dataset.phase==='INTRO'");return int(host.eval("location.hash.split('/')[2]"))
    def ready(t):
        t.wait("!!document.querySelector('#continue-stage')&&!document.querySelector('#continue-stage').disabled");t.click('#continue-stage')
    def answer(t):t.wait("!!document.querySelector('#answer-text')&&!document.querySelector('#answer-text').disabled");t.fill(dict(answerText='answer'));t.click('#submit-answer')
    try:
        adminName='kickadm_'+secrets.token_hex(5);adminSecret=secrets.token_urlsafe(30)+'!Aa7';env=os.environ.copy();env.update(MULTIGAME_ADMIN_USERNAME=adminName,MULTIGAME_ADMIN_DISPLAY_NAME='Kick operator',MULTIGAME_ADMIN_PASSWORD=adminSecret)
        command=['java','-jar',str(ROOT/'target/multigame-0.0.1-SNAPSHOT.jar'),'--provision-admin','--spring.datasource.url=jdbc:mysql://127.0.0.1:3306/quizz_task2_test?connectionTimeZone=UTC&connectTimeout=3000&socketTimeout=3000','--debug=false','--logging.level.root=WARN','--logging.level.org.springframework=WARN','--logging.level.org.hibernate=WARN']
        process=subprocess.run(command,cwd=ROOT,env=env,text=True,encoding='utf-8',capture_output=True,timeout=60);env.pop('MULTIGAME_ADMIN_PASSWORD');assert process.returncode==0,process.stdout+process.stderr
        admin=tab();admin.fill(dict(username=adminName,password=adminSecret));admin.submit('#login-form');admin.wait("location.pathname==='/admin'&&!!document.querySelector('#admin-page')")
        host=player('host');b=player('b');c=player('c');d=player('d');yh=player('yh');yc=player('yc');yd=player('yd')
        x,media=media_room(host);y=room(yh,'Y',1)
        for t in [b,c,d]:join(t,x)
        for t in [yc,yd]:join(t,y)
        gx=start(host)
        for t in [b,c,d]:ready(t)
        for t in [host,b,c,d]:t.wait("document.querySelector('#game-page')?.dataset.phase==='QUESTION_OPEN'")
        media={kind:path+f'?gameSessionId={gx}' for kind,path in media.items()};assert media_status(b,media['video'])==200
        answer(b);b.wait("!!document.querySelector('#answer-accepted')");bid=accounts['b']['id'];report.update(gameX=gx,roomX=x['id'],roomY=y['id'])
        admin.route(f'admin?tab=games&game={gx}','#admin-kick-panel');admin.wait(f"document.querySelector('#admin-kick-panel').dataset.gameId==='{gx}'")
        row=f'.admin-kick-player[data-user-id="{bid}"]';hostRow=f'.admin-kick-player[data-user-id="{accounts["host"]["id"]}"]'
        assert admin.eval(f'document.querySelector({json.dumps(hostRow+" [data-action=kick]")}).disabled')
        before=api(b,'GET',f'/api/games/{gx}/snapshot');assert before['player']['alreadyAnswered']
        admin.click(row+' [data-action=kick]');assert admin.eval(f'!document.querySelector({json.dumps(row+" .confirm-panel")}).hidden');admin.click(row+' .confirm-panel > button');assert api(b,'GET',f'/api/games/{gx}/snapshot')['player']['alreadyAnswered']
        passed('Real ACTIVE Game roster and Host guard; opening/canceling confirm does not Kick')
        admin.eval(r'''window.kickRequests=[];window.dropKickReceipt=true;const realFetch=window.fetch;window.fetch=async function(path,options){const response=await realFetch.apply(this,arguments);if(options?.method==='POST'&&String(path).endsWith('/kick')){kickRequests.push(JSON.parse(options.body));if(dropKickReceipt&&response.ok){dropKickReceipt=false;window.committedKick=await response.clone().json();throw TypeError('Controlled receipt drop');}}return response;};''')
        admin.click(row+' [data-action=kick]');admin.fill(dict(kickReason='T08 real UI moderation'),row+' ');admin.submit(row+' .kick-form')
        admin.wait(f'document.querySelector({json.dumps(row)})?.dataset.kickStatus==="PENDING"&&document.querySelector({json.dumps(row+" button[type=submit]")}).textContent.includes("Thử lại")')
        b.wait("location.hash==='#/home'&&!document.querySelector('#game-page')&&document.querySelector('#view').textContent.includes('quản trị viên')")
        admin.screenshot(images/'admin-pending-1440.png');b.screenshot(images/'target-lobby-1440.png')
        assert admin.eval('committedKick.status==="KICK_APPLIED"&&kickRequests.length===1');assert req(b,'GET',f'/api/games/{gx}/snapshot')['status']==403;assert req(b,'GET',f'/api/rooms/{x["id"]}')['status']==403
        assert media_status(b,media['video'])==403 and media_status(c,media['video'])==200
        assert b.eval("!document.querySelector('#song-video')")
        passed('Server Kick commits despite dropped HTTP receipt; ADMIN stays pending and target returns to lobby without logout')
        admin.click(row+' button[type=submit]');admin.wait(f'document.querySelector({json.dumps(row)})?.dataset.kickStatus==="KICK_APPLIED"')
        assert admin.eval('kickRequests.length===2&&JSON.stringify(kickRequests[0])===JSON.stringify(kickRequests[1])');admin.screenshot(images/'admin-applied-1440.png')
        passed('Uncertain Kick retries the exact same UUID/payload and only acknowledges Server receipt')
        for kind in ['ANSWER','USE_SPIN','USE_STAR','CONTINUE','RECONNECT']:
            requestId=str(uuid.uuid4());frame=dict(v=1,kind='COMMAND',requestId=requestId,type=kind,target=dict(kind='GAME',id=gx),questionIndex=None if kind=='RECONNECT' else 1,payload=dict(text='answer') if kind=='ANSWER' else {})
            b.eval('kickSocket.send('+json.dumps(json.dumps(frame))+')');b.wait(f'kickFrames.some(m=>m.requestId==={json.dumps(requestId)}&&m.kind==="ERROR"&&m.code==="GAME_KICKED")')
        b.eval('window.afterKickFence=kickFrames.length')
        passed('Old authenticated socket rejects Answer/Spin/Star/Continue/Reconnect for X; cookie remains valid')
        assert api(host,'GET',f'/api/games/{gx}/snapshot')['phase']=='QUESTION_OPEN'
        join(b,y);gy=start(yh);report['gameY']=gy;b.wait(f'location.hash==="#/game/{gy}"&&!!document.querySelector("#game-page")')
        for t in [b,yc,yd]:ready(t)
        b.wait("document.querySelector('#game-page')?.dataset.phase==='QUESTION_OPEN'")
        passed('After release, same user joins and starts real Game Y while X remains ACTIVE')
        replay=api(admin,'POST',f'/api/admin/games/{gx}/kick',admin.eval('kickRequests[0]'));assert replay['replayed']
        assert b.eval(f'location.hash==="#/game/{gy}"')
        answer(c);answer(d)
        for t in [c,d]:t.wait("document.querySelector('#game-page')?.dataset.index==='2'&&document.querySelector('#game-page').dataset.phase==='QUESTION_OPEN'")
        answer(c);answer(d)
        for t in [c,d]:
            t.wait("document.querySelector('#game-page')?.dataset.index==='3'&&document.querySelector('#game-page').dataset.phase==='INTRO'");ready(t)
        c.wait("document.querySelector('#game-page')?.dataset.index==='3'&&document.querySelector('#game-page').dataset.phase==='QUESTION_OPEN'")
        assert media_status(c,media['image'])==200 and media_status(b,media['image'])==403
        passed('Video/image URLs enforce per-Game restriction; eligible peer keeps media access and target media is disposed')
        answer(c);answer(d);host.wait("!!document.querySelector('#final-summary')")
        assert b.eval(f'location.hash==="#/game/{gy}"&&!kickFrames.slice(afterKickFence).some(m=>m.target?.id==={gx}&&m.payload?.gameSessionId==={gx})')
        assert api(b,'GET',f'/api/games/{gy}/snapshot')['status']=='ACTIVE'
        passed('X progresses/finishes without new X gameplay frames reaching old socket; replay/finish X cannot interrupt Y')
        history=api(b,'GET',f'/api/games/history/{gx}');own=[a for q in history['questions'] for a in q['answers'] if a['userId']==bid]
        assert len(own)==1 and own[0]['answerStatus']=='CORRECT' and own[0]['scoreAfter']==10
        assert any(r['userId']==bid and r['kind']=='KICKED_BY_ADMIN' for r in history['restrictions']);assert req(b,'GET',f'/api/games/{gx}/snapshot')['status']==403
        assert all(media_status(b,path)==200 for path in media.values())
        passed('Own FINISHED History retains accepted Answer/score, marks restriction and has no N+1 outcome')
        for t in [b,yc,yd]:answer(t)
        for t in [b,yc,yd,yh]:t.wait("!!document.querySelector('#final-summary')")
        cdp.call('Emulation.setDeviceMetricsOverride',dict(width=360,height=900,deviceScaleFactor=1,mobile=False),admin.session);admin.screenshot(images/'admin-360.png');assert admin.eval('document.documentElement.scrollWidth<=innerWidth')
        b.route('history/'+str(gx),'#history-detail');b.wait("document.querySelector('#history-detail').textContent.includes('quản trị viên')");b.screenshot(images/'target-history-1440.png')
        b.route('game/'+str(gx),'#view');b.wait("location.hash==='#/home'&&!document.querySelector('#game-page')")
        for path in ['/js/features/admin/kick.js','/styles/features/admin.css']:assert req(admin,'GET',path)['status']==200
        assert not cdp.errors,cdp.errors;passed('Y completes normally; restricted X route remains blocked, History and 360px admin/assets work')
        if a.restart_check:
            request=ROOT/'target'/f't08-restart-{prefix}.request.json';signal=request.with_suffix('.continue')
            request.write_text(json.dumps(dict(gameX=gx,gameY=gy,signal=str(signal))),encoding='utf-8');print('RESTART_READY '+str(request),flush=True)
            deadline=time.monotonic()+150
            while not signal.exists():
                if time.monotonic()>deadline:raise AssertionError('Owned server restart handshake timed out')
                time.sleep(.1)
            b.reload();b.wait("!!document.querySelector('#login-form')");b.fill(dict(username=prefix+'_b',password=password));b.submit('#login-form');b.wait("document.querySelector('#connection-status')?.dataset.state==='ready'")
            assert req(b,'GET',f'/api/games/{gx}/snapshot')['status']==403
            assert api(b,'GET',f'/api/games/history/{gx}')['restrictions'][0]['userId']==bid
            assert api(b,'GET',f'/api/games/{gy}/snapshot')['status']=='FINISHED'
            passed('Real owned-server restart preserves durable X restriction and own History while Y remains readable')
        report['status']='PASS';report['browser']=cdp.version
    except Exception as error:report.update(status='FAIL',error=str(error));raise
    finally:
        (ROOT/'target/t08-kick-client-tests.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
        for t in tabs:cdp.call('Target.disposeBrowserContext',dict(browserContextId=t.context))
        cdp.socket.close()

if __name__=='__main__':main()
