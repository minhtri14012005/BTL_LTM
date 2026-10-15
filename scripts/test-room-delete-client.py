"""Real browser/MySQL smoke: manual Room deletion; no deletion on normal End."""
import argparse, importlib.util, json, secrets, sys
from pathlib import Path
sys.dont_write_bytecode=True
ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('browser',ROOT/'scripts/browser-cdp.py')
browser=importlib.util.module_from_spec(spec);spec.loader.exec_module(browser)

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--origin',default='http://127.0.0.1:8089')
    parser.add_argument('--port',type=int,default=9227)
    args=parser.parse_args();cdp=browser.CDP(args.port)
    report=dict(status='RUNNING',browser=cdp.version,checks=[],roomIds=[])
    tabs=[]
    def passed(name):report['checks'].append(name)
    def api(tab,method,path,body=None):
        return tab.eval('(async()=>{const token=await fetch("/api/auth/csrf").then(r=>r.json());const r=await fetch('+json.dumps(path)+', {method:'+json.dumps(method)+',headers:{"Content-Type":"application/json",[token.headerName]:token.token},'+('body:'+json.dumps(json.dumps(body,ensure_ascii=False))+',' if body is not None else '')+'});const b=await r.json();if(!r.ok)throw Error(r.status+JSON.stringify(b));return b;})()')
    try:
        prefix='trash_'+secrets.token_hex(4);password=secrets.token_urlsafe(20)
        for role in ['host','p1','p2','p3']:
            tab=cdp.tab(args.origin);tabs.append(tab)
            tab.route('register','#register-form');tab.fill(dict(username=prefix+'_'+role,displayName='Room delete '+role,password=password,confirmPassword=password));tab.submit('#register-form')
            tab.wait("!!document.querySelector('#login-form')");tab.fill(dict(username=prefix+'_'+role,password=password));tab.submit('#login-form')
            tab.wait("document.querySelector('#connection-status')?.dataset.state==='ready'")
        host,*players=tabs
        bank=api(host,'POST','/api/quizzes',dict(title=prefix,mode='RIDDLE',visibility='PUBLIC',questions=[dict(content='Cái gì càng lấy bớt đi thì càng lớn?',acceptedAnswers=['hố','cái hố'])]))
        def create():
            room=api(host,'POST','/api/rooms',dict(requestId=host.eval('crypto.randomUUID()'),config=dict(name=prefix,maxPlayers=3,hostParticipation='SPECTATOR',stages=[dict(mode='RIDDLE',quizId=bank['id'],questionCount=1,questionDurationMs=10000)])))
            report['roomIds'].append(room['id']);return room
        def list_card(tab,rid):
            tab.route('rooms','.page-heading');tab.wait(f"!!document.querySelector('[data-room-id=\"{rid}\"]')")
        def remove(tab,rid):
            tab.click('#delete-room-'+str(rid));tab.wait(f"!document.querySelector('[data-room-id=\"{rid}\"] .room-delete-confirm').hidden")
            tab.click('#confirm-delete-room-'+str(rid))
            tab.wait(f"document.querySelector('.page-heading h1')?.textContent==='Phòng của tôi'&&!document.querySelector('[data-room-id=\"{rid}\"]')")
        draft=create();list_card(host,draft['id']);host.click('#delete-room-'+str(draft['id']))
        host.screenshot(ROOT/'target/room-delete-confirm.png')
        assert not host.eval(f"document.querySelector('[data-room-id=\"{draft['id']}\"] .room-delete-confirm').hidden")
        host.click(f'[data-room-id="{draft["id"]}"] .room-delete-confirm .secondary')
        assert host.eval(f"!!document.querySelector('[data-room-id=\"{draft['id']}\"]')")
        remove(host,draft['id']);passed('Host confirmation can keep or delete a Draft')
        closed=create();api(host,'POST',f'/api/rooms/{closed["id"]}/close',dict(requestId=host.eval('crypto.randomUUID()'),revision=closed['revision']))
        list_card(host,closed['id']);remove(host,closed['id']);passed('Closed Room can be manually deleted from My Rooms')
        room=create();room=api(host,'POST',f'/api/rooms/{room["id"]}/open',dict(requestId=host.eval('crypto.randomUUID()'),revision=room['revision']))
        def join(tab):
            tab.route('join','#join-form');tab.fill(dict(code=room['roomCode'],participation='PLAYER'));tab.submit('#join-form');tab.wait("!!document.querySelector('#waiting-room')")
        join(players[0]);list_card(players[0],room['id']);remove(players[0],room['id'])
        assert len(api(host,'GET',f'/api/rooms/{room["id"]}')['members'])==1
        passed('Member trash leaves only that member; Host Room remains')
        for player in players:join(player)
        host.route('room/'+str(room['id']),'#waiting-room');host.wait("document.querySelectorAll('#roster .member').length===4")
        current=api(host,'GET',f'/api/rooms/{room["id"]}')
        started=api(host,'POST',f'/api/rooms/{room["id"]}/start',dict(requestId=host.eval('crypto.randomUUID()'),revision=current['revision'],questionCount=1));game=started['gameSessionId'];report['gameSessionId']=game
        list_card(host,room['id']);assert host.eval(f"document.querySelector('#delete-room-{room['id']}').disabled")
        passed('Trash disabled while a real Game is ACTIVE')
        for tab in tabs:tab.route('game/'+str(game),'#game-page')
        for player in players:
            player.wait("!!document.querySelector('#continue-stage')&&!document.querySelector('#continue-stage').disabled");player.click('#continue-stage')
        for player in players:
            player.wait("!!document.querySelector('#answer-text')&&!document.querySelector('#answer-text').disabled");player.fill(dict(answerText='hố'));player.click('#submit-answer')
        for tab in tabs:tab.wait("!!document.querySelector('#final-summary')",timeout=20)
        before=api(players[0],'GET',f'/api/games/history/{game}')
        assert before['finalSnapshot']['endReason']=='COMPLETED'
        list_card(host,room['id']);assert not host.eval(f"document.querySelector('#delete-room-{room['id']}').disabled")
        assert api(host,'GET',f'/api/rooms/{room["id"]}')['status']=='WAITING'
        passed('Normal End keeps Room visible/reusable; no automatic deletion')
        remove(host,room['id'])
        after=api(players[0],'GET',f'/api/games/history/{game}')
        assert after['questions']==before['questions'] and after['finalSnapshot']['members']==before['finalSnapshot']['members']
        for player in players:
            player.route('rooms','.page-heading');player.wait("document.querySelector('.page-heading h1')?.textContent==='Phòng của tôi'")
            assert not player.eval(f"!!document.querySelector('[data-room-id=\"{room['id']}\"]')")
        host.route('history/'+str(game),'#history-detail');host.wait("!!document.querySelector('#final-summary')")
        passed('Host deletes idle Room for all members; real History remains unchanged')
        assert not cdp.errors,cdp.errors;report['status']='PASS'
    except Exception as error:report['status']='FAIL';report['error']=str(error);raise
    finally:
        for tab in tabs:cdp.call('Target.disposeBrowserContext',dict(browserContextId=tab.context))
        (ROOT/'target/room-delete-browser-tests.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
        print(json.dumps(report,ensure_ascii=False,indent=2))

if __name__=='__main__':main()
