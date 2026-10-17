"""T06 real Result/History. Optional handshake restarts ONLY the task-owned server."""
import argparse
import importlib.util
import json
import secrets
import time
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('browser',ROOT/'scripts/browser-cdp.py')
browser=importlib.util.module_from_spec(spec);spec.loader.exec_module(browser)
AUDIT=r'''(()=>{window.resultReads=[];window.resultCommands=[];
const fetchBefore=window.fetch;window.fetch=function(input,options){resultReads.push({method:options?.method||'GET',path:typeof input==='string'?input:input.url});return fetchBefore.apply(this,arguments);};
const sendBefore=WebSocket.prototype.send;WebSocket.prototype.send=function(text){resultCommands.push(JSON.parse(text).type);return sendBefore.call(this,text);};})()'''

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--origin',default='http://127.0.0.1:8088');parser.add_argument('--port',type=int,default=9227);parser.add_argument('--restart-check',action='store_true');args=parser.parse_args()
    cdp=browser.CDP(args.port);tabs=[];users=[];report={'status':'RUNNING','kind':'real HTTP/WS/MySQL','checks':[],'gameIds':[]}
    def passed(name):report['checks'].append(name);print('PASS '+name,flush=True)
    def api(tab,method,path,body=None):
        return tab.eval('(async()=>{const c=await fetch("/api/auth/csrf").then(r=>r.json());const r=await fetch('+json.dumps(path)+',{method:'+json.dumps(method)+',headers:{"Content-Type":"application/json",[c.headerName]:c.token},'+('body:'+json.dumps(json.dumps(body))+',' if body is not None else '')+'});if(!r.ok)throw Error(r.status+await r.text());return r.json();})()')
    def login(tab,name,password):
        tab.reload();tab.wait("!!document.querySelector('#login-form')");tab.fill(dict(username=name,password=password));tab.submit('#login-form');tab.wait("document.querySelector('#connection-status')?.dataset.state==='ready'")
    def audit(tab):tab.eval(AUDIT)
    def readonly(tab):assert tab.eval("resultReads.every(r=>r.method==='GET')&&resultCommands.every(t=>['RECONNECT','SUBSCRIBE_ROOM'].includes(t))&&!document.querySelector('[data-award-state]')")
    def history(tab,gid):
        tab.route('history/'+str(gid),'#history-detail');tab.wait("!!document.querySelector('.result-room-return[data-room-status]')")
    try:
        prefix='result_'+secrets.token_hex(4);password=secrets.token_urlsafe(20);names=[]
        for role in ['host','p1','p2','p3']:
            tab=cdp.tab(args.origin);tabs.append(tab);name=prefix+'_'+role;names.append(name)
            tab.route('register','#register-form');tab.fill(dict(username=name,displayName='Result '+role,password=password,confirmPassword=password));tab.submit('#register-form')
            tab.wait("!!document.querySelector('#login-form')");tab.fill(dict(username=name,password=password));tab.submit('#login-form');tab.wait("document.querySelector('#connection-status')?.dataset.state==='ready'")
            users.append(api(tab,'GET','/api/auth/me'))
        host,*players=tabs
        bank=api(host,'POST','/api/quizzes',dict(title=prefix,mode='RIDDLE',visibility='PUBLIC',questions=[dict(content='Cái gì càng lấy bớt đi thì càng lớn?',acceptedAnswers=['hố'])]))
        room=api(host,'POST','/api/rooms',dict(requestId=host.eval('crypto.randomUUID()'),config=dict(name=prefix,maxPlayers=3,hostParticipation='SPECTATOR',stages=[dict(mode='RIDDLE',quizId=bank['id'],questionCount=1,questionDurationMs=60000)])))
        rid=room['id'];report['roomId']=rid
        host.route('room/'+str(rid),'#open-room');host.click('#open-room');host.wait("!!document.querySelector('#start-game')")
        for tab in players:
            tab.route('join','#join-form');tab.fill(dict(code=room['roomCode'],participation='PLAYER'));tab.submit('#join-form');tab.wait("!!document.querySelector('#waiting-room')")
        host.wait("!document.querySelector('#start-game').disabled")
        def start():
            for tab in players:tab.route('room/'+str(rid),'#waiting-room')
            host.click('#start-game')
            for tab in tabs:tab.wait("!!document.querySelector('#game-page')&&document.querySelector('#game-page').dataset.phase==='INTRO'")
            host.wait("!!document.querySelector('#cancel-game')&&!document.querySelector('#cancel-game').disabled")
            gid=int(host.eval("location.hash.split('/')[2]"));report['gameIds'].append(gid);return gid
        gid=start()
        for tab in players:
            tab.wait("!!document.querySelector('#continue-stage')&&!document.querySelector('#continue-stage').disabled");tab.click('#continue-stage')
        for tab in players:
            tab.wait("!!document.querySelector('#answer-text')&&!document.querySelector('#answer-text').disabled");tab.fill(dict(answerText='hố'));tab.click('#submit-answer')
        for tab in tabs:tab.wait("!!document.querySelector('#final-summary')")
        s=api(players[0],'GET',f'/api/games/{gid}/snapshot');assert s['endReason']=='COMPLETED' and s['hasOfficialWinner']
        for tab,user in zip(players,users[1:]):
            member=next(m for m in s['members'] if m['userId']==user['id']);assert tab.eval("Number(document.querySelectorAll('.own-stats-grid dd')[1].textContent)")==member['score']
            for m in [m for m in s['members'] if m['participation']=='PLAYER']:
                assert tab.eval(f"document.querySelector('#standings [data-user-id=\"{m['userId']}\"] td').textContent")==str(m['rank'])
        passed('COMPLETED UI uses real Server ranks/score and spectator/own stats; no reward receipt')
        audit(host);history(host,gid);host.wait("document.querySelector('.result-room-return').dataset.roomStatus==='WAITING'");readonly(host)
        detail=api(host,'GET',f'/api/games/history/{gid}');assert len(detail['questions'])==1
        host.click('.result-room-return a');host.wait("!!document.querySelector('#waiting-room')&&!!document.querySelector('#start-game')");readonly(host)
        assert int(host.eval("location.hash.split('/')[2]"))==rid
        passed('Opening History and Play Again only read/navigate to same WAITING Room; no START/create/award')
        cancel_id=start();host.eval('window.confirm=()=>true');host.click('#cancel-game')
        for tab in tabs:tab.wait("document.querySelector('#final-summary')?.dataset.endReason==='CANCELLED'")
        audit(players[0]);history(players[0],cancel_id);readonly(players[0])
        assert players[0].eval("!document.querySelector('.result-top')&&!document.querySelector('.winner')&&document.querySelector('#final-summary').textContent.includes('không có Official Winner')")
        passed('CANCELLED History shows real reason without winner/podium/award or scoring unopened question')
        host.route('room/'+str(rid),'#start-game');interrupted=start()
        audit(host);history(host,gid);host.wait("document.querySelector('.result-room-return').dataset.roomStatus==='ACTIVE'");readonly(host)
        assert not host.eval("document.querySelector('.result-room-return').textContent.includes('Chơi tiếp')")
        passed('Old History reads current ACTIVE Room and cannot advertise Play Again')
        if args.restart_check:
            request=ROOT/'target'/('t06-restart-'+prefix+'.request.json');signal=request.with_suffix('.continue')
            request.write_text(json.dumps({'gameSessionId':interrupted,'roomId':rid,'signal':str(signal)}),encoding='utf-8');print('RESTART_READY '+str(request),flush=True)
            deadline=time.monotonic()+120
            while not signal.exists():
                if time.monotonic()>deadline:raise AssertionError('Task-owned server restart handshake timed out')
                time.sleep(.1)
            for tab,name in zip(tabs,names):login(tab,name,password)
            for tab in tabs:
                audit(tab);history(tab,interrupted);readonly(tab)
                assert tab.eval("document.querySelector('#final-summary').dataset.endReason==='SERVER_INTERRUPTED'&&!document.querySelector('.result-top')&&!document.querySelector('.winner')")
            passed('Real task-owned server restart persists SERVER_INTERRUPTED History for all four sessions; no false winner')
            host.route('room/'+str(rid),'#show-close-room');host.click('#show-close-room');host.click('#close-room');host.wait("document.querySelector('.page-heading .badge')?.textContent==='Đã đóng'")
            audit(host);history(host,interrupted);host.wait("document.querySelector('.result-room-return').dataset.roomStatus==='CLOSED'");readonly(host)
            assert not host.eval("document.querySelector('.result-room-return').textContent.includes('Chơi tiếp')")
            passed('Current CLOSED Room removes Play Again while History stays readable')
            before=api(host,'GET',f'/api/games/history/{interrupted}')
            host.route('rooms',f'#delete-room-{rid}');host.click(f'#delete-room-{rid}');host.click(f'#confirm-delete-room-{rid}');host.wait("location.hash.startsWith('#/rooms')&&!document.querySelector('#confirm-delete-room-"+str(rid)+"')")
            audit(host);history(host,interrupted);host.wait("document.querySelector('.result-room-return').dataset.roomStatus==='UNAVAILABLE'");readonly(host)
            assert not host.eval("!!document.querySelector('.result-room-return a')")
            after=api(host,'GET',f'/api/games/history/{interrupted}');assert after['questions']==before['questions'] and after['finalSnapshot']['members']==before['finalSnapshot']['members']
            passed('Deleted/inaccessible Room cannot create or redirect to replacement; immutable History remains readable')
        assert not cdp.errors,cdp.errors;report['status']='PASS'
    except Exception as error:
        report.update(status='FAIL',error=str(error));raise
    finally:
        for tab in tabs:cdp.call('Target.disposeBrowserContext',dict(browserContextId=tab.context))
        (ROOT/'target/t06-results-network-tests.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
        print(json.dumps({'status':report['status'],'checks':len(report['checks'])}))

if __name__=='__main__':main()
