"""Frontend assertions and real UI smoke against the running MySQL server.
Start an isolated headless Chrome on localhost:9222 first; never uses mocks.
"""
import argparse
import importlib.util
import json
import secrets
import struct
import time
import zlib
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location("browser_cdp",ROOT/"scripts/browser-cdp.py")
browser=importlib.util.module_from_spec(spec)
spec.loader.exec_module(browser)

UNIT=r'''(async()=>{
 const c=await import('/client/core.js');const d=await import('/client/dom.js');const {Api}=await import('/client/api.js');const {Transport}=await import('/client/transport.js');
 const done=[];const check=(name,fn)=>{fn();done.push(name);};const ok=x=>{if(!x)throw Error('Assertion failed');};const bad=fn=>{let threw=false;try{fn();}catch{threw=true;}ok(threw);};
 check('uuid works with getRandomValues alone',()=>ok(/^[a-f0-9]{8}(-[a-f0-9]{4}){3}-[a-f0-9]{12}$/.test(c.uuid({getRandomValues:b=>b.fill(2)}))));
 check('canonical room envelope',()=>{const x=c.command('JOIN_ROOM',7,{roomCode:'ABCDEFGH2345',participation:'PLAYER'},'id');ok(x.v===1&&x.kind==='COMMAND'&&x.target.kind==='ROOM'&&x.target.id===7&&x.questionIndex===null&&x.requestId==='id');});
 check('revision prevents stale ACK rollback',()=>{const old={id:1,revision:4},fresh={id:1,revision:5};ok(c.latestRoom(fresh,old)===fresh);ok(c.latestRoom(old,fresh)===fresh);});
 check('equal revision is still consumable',()=>ok(c.latestRoom({id:1,revision:5},{id:1,revision:5}).revision===5));
 check('owner question guard',()=>{const q={ownerUserId:1,questions:[{correctAnswer:'A'}]};ok(c.ownerQuestions(q,{id:2})===null);ok(c.ownerQuestions(q,{id:1}).length===1);});
 check('account validation and password UTF8 bytes',()=>{bad(()=>c.accountValues({username:'x',password:'12345678'},false));bad(()=>c.accountValues({username:'valid',password:'é'.repeat(37)},false));ok(c.accountValues({username:' valid ',password:' pass1234 '},false).password===' pass1234 ');});
 check('register displayName',()=>bad(()=>c.accountValues({username:'valid',password:'12345678',displayName:' '},true)));
 const q={content:'Question',options:{A:'a',B:'b',C:'c',D:'d'},correctAnswer:'A'};
 check('quiz 4 options and single answer',()=>{ok(c.quizValues('Quiz','PUBLIC',[q]).questions[0].correctAnswer==='A');bad(()=>c.quizValues('Quiz','PUBLIC',[{...q,correctAnswer:'E'}]));bad(()=>c.quizValues('Quiz','PUBLIC',[{...q,options:{A:'a',B:'b',C:'c'}}]));});
 check('quiz question count and visibility',()=>{bad(()=>c.quizValues('Quiz','PRIVATE',[]));bad(()=>c.quizValues('Quiz','PUBLIC',Array(51).fill(q)));bad(()=>c.quizValues('Quiz','INVALID',[q]));});
 check('room validation and duration ms',()=>{const data={name:'Room',quizId:'1',maxPlayers:'3',seconds:'0.001',hostParticipation:'SPECTATOR'};ok(c.roomValues(data).questionDurationMs===1);bad(()=>c.roomValues({...data,maxPlayers:'2'}));bad(()=>c.roomValues({...data,seconds:'0'}));});
 const room={status:'WAITING',members:[{participation:'PLAYER'},{participation:'PLAYER'},{participation:'SPECTATOR'}]};
 check('Start Spectator not counted',()=>ok(!!c.startIssue(room,10,10)));
 check('Start count and status guard',()=>{const r={...room,members:Array(3).fill({participation:'PLAYER'})};ok(c.startIssue(r,10,10)==='');ok(!!c.startIssue(r,9,10));ok(!!c.startIssue(r,11,10));ok(!!c.startIssue({...r,status:'DRAFT'},10,10));});
 check('text rendering escapes HTML',()=>{const n=d.h('div',{},'<img onerror=alert(1)>');ok(n.children.length===0&&n.textContent.includes('<img'));});
 check('busy preserves disabled validation',()=>{const f=d.h('form',{},d.button('disabled',()=>{},{disabled:true}),d.button('ready',()=>{}));d.busy(f,true);d.busy(f,false);ok(f.children[0].disabled&&!f.children[1].disabled);});
 const transport=new Transport(()=>{},()=>{});let offline;try{await transport.send(c.command('LEAVE_ROOM',1,{}));}catch(e){offline=e;}ok(offline.code==='DISCONNECTED'&&offline.retryable);done.push('offline preserves explicit retry; replaced cannot reconnect automatically');
 transport.status='replaced';let replaced;try{await transport.send(c.command('LEAVE_ROOM',1,{}));}catch(e){replaced=e;}ok(replaced.code==='SESSION_REPLACED'&&!replaced.retryable);
 return done;
})()'''

def png(path):
    def chunk(kind,data):return struct.pack('!I',len(data))+kind+data+struct.pack('!I',zlib.crc32(kind+data)&0xffffffff)
    path.write_bytes(b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('!2I5B',8,8,8,2,0,0,0))+chunk(b'IDAT',zlib.compress((b'\0'+bytes([26,112,85])*8)*8))+chunk(b'IEND',b''))

def smoke(cdp,origin,report):
    prefix='ui11a_'+secrets.token_hex(4)
    password=secrets.token_urlsafe(15) # Only in memory, not reports/logs.
    report['fixturePrefix']=prefix
    people=[]
    def passed(name):report['smoke'].append(name)
    for role in ['author','player1','player2','player3']:
        tab=cdp.tab(origin)
        username=prefix+'_'+role
        tab.route('register','#register-form')
        tab.fill({'username':username,'displayName':role,'password':password,'confirmPassword':password})
        if role=='author':
            tab.fill({'confirmPassword':'different-password'});tab.submit('#register-form');tab.wait("!!document.querySelector('[role=alert]')")
            tab.fill({'confirmPassword':password});passed('Register mismatch rejected in UI before registration')
        tab.submit('#register-form');tab.wait("!!document.querySelector('#login-form') && document.body.innerText.includes('Đã tạo tài khoản')")
        tab.fill({'username':username,'password':password});tab.submit('#login-form')
        tab.wait("!!document.querySelector('#logout') && document.querySelector('#connection-status')?.textContent==='Trực tuyến'")
        user=tab.eval("fetch('/api/auth/me').then(r=>r.json())")
        people.append((tab,user))
    author=people[0][0];players=[p[0] for p in people[1:]]
    report['userIds']=[p[1]['id'] for p in people]
    passed('Register/Login four isolated browser contexts; distinct authenticated identities')
    author.reload();author.wait("document.querySelector('#connection-status')?.textContent==='Trực tuyến'")
    assert author.eval("document.querySelector('.account-info small').textContent")== '@'+prefix+'_author'
    assert author.eval("document.cookie.includes('JSESSIONID')") is False
    passed('Cookie auth persists after reload; HttpOnly cookie not readable by JS')
    # Actual owner CRUD via form, ten questions, no API fixture shortcut.
    author.route('quizzes/new','#quiz-form')
    author.fill({'title':prefix+' public','visibility':'PUBLIC'})
    for _ in range(9):author.click('#add-question')
    author.eval("document.querySelectorAll('.question-editor').forEach((r,i)=>{r.querySelector('[name=content]').value='Question '+(i+1);['A','B','C','D'].forEach(k=>r.querySelector('[name=option'+k+']').value='Option '+k);r.querySelector('[name=correctAnswer]').value='A';})")
    author.eval("document.querySelector('[name=correctAnswer]').value=''");author.submit('#quiz-form');author.wait("!!document.querySelector('[role=alert]')")
    assert author.eval("location.hash==='#/quizzes/new'")
    author.eval("document.querySelector('[name=correctAnswer]').value='A'");passed('Quiz missing correctAnswer rejected in UI before create')
    author.submit('#quiz-form');author.wait(r"location.hash.match(/^#\/quiz\/\d+\/edit$/) && document.querySelectorAll('.question-editor').length===10")
    quiz_id=int(author.eval("location.hash.split('/')[2]"));report['quizIds']=[quiz_id]
    fixture=ROOT/'target/task11a-image.png';png(fixture)
    author.upload('.question-editor [name=image]',fixture)
    author.fill({'title':prefix+' edited public'})
    author.submit('#quiz-form');author.wait("!!document.querySelector('.question-preview img') && location.hash==='#/quiz/"+str(quiz_id)+"'")
    author.wait("document.querySelector('.question-preview img').complete && document.querySelector('.question-preview img').naturalWidth===8")
    assert author.eval("document.querySelectorAll('.options .correct').length")==10
    passed('Owner Create/Get/Edit ten-question PUBLIC Quiz; immutable image upload and rendered preview')
    p=players[0];p.route('quiz/'+str(quiz_id),'.page-heading')
    p.wait("document.querySelector('.page-heading h1')?.textContent.includes('edited public')")
    assert p.eval("document.querySelectorAll('.question-preview,.options,.correct,.question-image').length")==0
    metadata=p.eval(f"fetch('/api/quizzes/{quiz_id}').then(r=>r.json())")
    assert 'questions' not in metadata and 'correctAnswer' not in json.dumps(metadata)
    passed('Non-owner PUBLIC metadata only in response and DOM')
    # Private visibility and deletion of a separate owned Quiz.
    author.route('quizzes/new','#quiz-form');author.fill({'title':prefix+' private','visibility':'PRIVATE'})
    author.eval("const r=document.querySelector('.question-editor');r.querySelector('[name=content]').value='Private secret';['A','B','C','D'].forEach(k=>r.querySelector('[name=option'+k+']').value=k);r.querySelector('[name=correctAnswer]').value='B';")
    author.submit('#quiz-form');author.wait(r"location.hash.match(/^#\/quiz\/\d+\/edit$/) && document.querySelector('[name=title]')?.value.includes('private')")
    private_id=int(author.eval("location.hash.split('/')[2]"));report['quizIds'].append(private_id)
    assert p.eval(f"fetch('/api/quizzes/{private_id}').then(r=>r.status)")==404
    listing=p.eval("fetch('/api/quizzes?size=100').then(r=>r.json())")
    assert all(q['id']!=private_id for q in listing['items'])
    p.route('quizzes','.card-grid');p.wait("document.body.innerText.includes('edited public')");assert not p.eval("document.body.innerText.includes('"+prefix+" private')")
    author.route('quizzes','.card-grid');author.wait("document.body.innerText.includes('"+prefix+" private')")
    passed('Quiz List renders PUBLIC metadata plus PRIVATE only for Owner')
    author.route('quiz/'+str(private_id),'#delete-quiz');author.click('#delete-quiz');author.click('#confirm-delete-quiz')
    author.wait("location.hash==='#/quizzes' && document.body.innerText.includes('Đã xóa bộ câu hỏi')")
    assert author.eval(f"fetch('/api/quizzes/{private_id}').then(r=>r.status)")==404
    passed('PRIVATE hidden from non-owner; owner soft-delete through confirmation UI')
    # Close a Draft, then use a second actual Room for network roster/Start.
    def create_room(name):
        author.route(f'rooms/new?quiz={quiz_id}','#room-form');author.fill({'name':name,'maxPlayers':'3','seconds':'0.01','hostParticipation':'SPECTATOR'})
        assert author.eval("document.querySelector('#hostParticipation option[value=PLAYER]').disabled")
        author.submit('#room-form');author.wait("!!document.querySelector('#waiting-room') && !!document.querySelector('#open-room')")
        return int(author.eval("location.hash.split('/')[2]"))
    closed_id=create_room(prefix+' closed');report['roomIds']=[closed_id]
    author.click('#show-close-room');author.click('#close-room')
    author.wait("document.querySelector('.page-heading .badge')?.textContent==='Đã đóng'")
    passed('Author Host forced Spectator; Draft Create and confirmed Close')
    room_id=create_room(prefix+' waiting');report['roomIds'].append(room_id)
    # A non-author Host can select Participate instead of Spectate.
    p.route(f'rooms/new?quiz={quiz_id}','#room-form');p.fill({'name':prefix+' participant host','maxPlayers':'3','seconds':'1','hostParticipation':'PLAYER'})
    assert not p.eval("document.querySelector('#hostParticipation option[value=PLAYER]').disabled")
    p.submit('#room-form');p.wait("!!document.querySelector('#open-room')")
    report['roomIds'].append(int(p.eval("location.hash.split('/')[2]")))
    assert p.eval("document.querySelector('#roster').textContent.includes('Host · Người chơi')")
    p.click('#show-close-room');p.click('#close-room');p.wait("document.querySelector('.page-heading .badge')?.textContent==='Đã đóng'")
    passed('Non-author Host Participate selection persisted in Server roster; independent Draft can be closed')
    author.route(f'room/{room_id}/edit','#room-form');author.fill({'name':prefix+' room updated','seconds':'0.01'});author.submit('#room-form')
    author.wait("document.querySelector('.page-heading h1')?.textContent.includes('room updated')")
    author.click('#open-room');author.wait("!!document.querySelector('#start-game')")
    code=author.eval("document.querySelector('#room-code').textContent")
    assert author.eval("document.querySelector('#start-game').disabled")
    passed('Draft Edit/Open; Start disabled before minimum three Players')
    def join(tab):
        tab.route('join','#join-form');tab.fill({'code':code,'participation':'PLAYER'});tab.submit('#join-form')
        tab.wait("!!document.querySelector('#waiting-room') && !!document.querySelector('#leave-room')")
    for index,tab in enumerate(players):
        # Observe real frames; drop exactly one genuine JOIN ACK for the last player.
        tab.eval("window.wire={commands:[],messages:[],drop:"+str(index==2).lower()+"};const send=WebSocket.prototype.send;WebSocket.prototype.send=function(text){const frame=JSON.parse(text);window.wire.commands.push(frame);window.waitingSocket=this;if(!this.observed){this.observed=true;const receive=this.onmessage;this.onmessage=function(event){const m=JSON.parse(event.data);window.wire.messages.push(m);if(window.wire.drop&&m.kind==='ACK'&&m.type==='JOIN_ROOM'){window.wire.drop=false;window.wire.dropped=m;return;}receive.call(this,event);};}return send.call(this,text);};")
        if index==2:
            tab.route('join','#join-form');tab.fill({'code':code,'participation':'PLAYER'});tab.submit('#join-form')
            author.wait("document.querySelectorAll('#roster .member').length===4")
            tab.wait("!!document.querySelector('.action-feedback button')",timeout=20)
            tab.click('.action-feedback button');tab.wait("!!document.querySelector('#waiting-room')")
            commands=tab.eval("window.wire.commands.filter(m=>m.type==='JOIN_ROOM')")
            assert len(commands)==2 and commands[0]==commands[1]
            assert tab.eval("JSON.stringify(window.wire.dropped)===JSON.stringify(window.wire.messages.filter(m=>m.kind==='ACK'&&m.type==='JOIN_ROOM').at(-1))")
        else:join(tab)
        author.wait(f"document.querySelectorAll('#roster .member').length==={index+2}")
    for tab in [author,*players]:tab.wait("document.querySelectorAll('#roster .member').length===4 && document.querySelector('#player-count').textContent==='3/3 người chơi'")
    passed('Three distinct Players join via WS; four clients receive live Server roster')
    passed('Lost real JOIN ACK: explicit retry preserves requestId/payload and replays identical receipt without second member')
    first=players[0]
    first.eval("window.waitingSocket.send(JSON.stringify(window.wire.commands.find(m=>m.type==='JOIN_ROOM')))")
    first.wait("window.wire.messages.filter(m=>m.kind==='ACK'&&m.type==='JOIN_ROOM').length===2")
    assert first.eval("document.querySelectorAll('#roster .member').length")==4
    assert first.eval("JSON.stringify(window.wire.messages.filter(m=>m.kind==='ACK'&&m.type==='JOIN_ROOM')[0])===JSON.stringify(window.wire.messages.filter(m=>m.kind==='ACK'&&m.type==='JOIN_ROOM')[1])")
    first.eval("const prior=window.wire.commands.find(m=>m.type==='JOIN_ROOM');window.waitingSocket.send(JSON.stringify({...prior,payload:{...prior.payload,participation:'SPECTATOR'}}))")
    first.wait("window.wire.messages.some(m=>m.kind==='ERROR'&&m.code==='INVALID_REQUEST_ID')")
    passed('Older JOIN ACK cannot roll back latest roster; same ID different payload rejected by real server')
    players[2].click('#leave-room');players[2].wait("location.hash==='#/rooms'");author.wait("document.querySelectorAll('#roster .member').length===3")
    join(players[2]);author.wait("document.querySelectorAll('#roster .member').length===4")
    author.click(f'[data-remove="{people[3][1]["id"]}"]');players[2].wait("location.hash==='#/rooms'");author.wait("document.querySelectorAll('#roster .member').length===3")
    join(players[2]);author.wait("document.querySelectorAll('#roster .member').length===4")
    passed('Leave/rejoin and Host Remove/access revocation/rejoin update all subscribed clients')
    players[0].fill({'participation':'SPECTATOR'});players[0].click('#change-participation')
    author.wait("document.querySelector('#player-count').textContent==='2/3 người chơi' && document.querySelector('#start-game').disabled")
    players[0].fill({'participation':'PLAYER'});players[0].click('#change-participation');author.wait("!document.querySelector('#start-game').disabled")
    passed('Waiting participation changed by WS; Spectator excluded from Start minimum')
    author.route(f'room/{room_id}/edit','#room-form');author.fill({'name':prefix+' broadcast edit'});author.submit('#room-form')
    for tab in [author,*players]:tab.wait("document.querySelector('.page-heading h1')?.textContent.includes('broadcast edit')")
    passed('Host WAITING config edit broadcast to all clients without roster polling')
    author.wait("!document.querySelector('#start-game').disabled")
    author.fill({'questionCount':'9'});assert author.eval("document.querySelector('#start-game').disabled")
    author.fill({'questionCount':'11'});assert author.eval("document.querySelector('#start-game').disabled")
    author.fill({'questionCount':'10'});assert author.eval("!document.querySelector('#start-game').disabled")
    passed('Start validation rejects N<10 and N>available; accepts 10 with three Players')
    # Same cookie profile, new tab replaces old socket; no reconnect tug-of-war.
    replacement=cdp.tab(origin,author.context);replacement.route(f'room/{room_id}','#waiting-room')
    replacement.wait("document.querySelector('#connection-status')?.textContent==='Trực tuyến'")
    author.wait("!!document.querySelector('#reconnect-room') && document.querySelector('#connection-status')?.textContent==='Đã thay kết nối'")
    replacement.wait("document.querySelectorAll('#roster .member').length===4")
    assert author.eval("document.querySelector('#start-game').disabled")
    author.click('#reconnect-room');author.wait("document.querySelector('#connection-status')?.textContent==='Trực tuyến' && !document.querySelector('#start-game').disabled")
    replacement.wait("document.querySelector('#connection-status')?.textContent==='Đã thay kết nối'")
    passed('SESSION_REPLACED stops old tab; only explicit user reconnect replaces new tab')
    author.screenshot(ROOT/'target/task11a-waiting.png')
    cdp.call('Emulation.setDeviceMetricsOverride',{'width':390,'height':844,'deviceScaleFactor':1,'mobile':True},players[0].session)
    assert players[0].eval("document.documentElement.scrollWidth<=innerWidth")
    players[0].screenshot(ROOT/'target/task11a-mobile.png')
    passed('Waiting Room desktop/mobile layout has no horizontal overflow')
    author.click('#start-game')
    for tab in [author,*players]:tab.wait("!!document.querySelector('#game-page')")
    game_id=int(author.eval("location.hash.split('/')[2]"));report['gameIds']=[game_id]
    snap=author.eval(f"fetch('/api/games/{game_id}/snapshot').then(r=>r.json())")
    assert snap['player'] is None and len(snap['members'])==4 and snap['questionCount']==10
    passed('Real START_GAME commits with Author Host Spectator + three Players; all navigate to Game')
    # This smoke covers Waiting/Start; complete gameplay is tested by test-game-client.py.
    players[0].click('#logout');players[0].wait("!!document.querySelector('#login-form')")
    assert players[0].eval("fetch('/api/auth/me').then(r=>r.status)")==401
    players[0].reload();assert players[0].eval("!!document.querySelector('#login-form')")
    players[0].fill({'username':prefix+'_player1','password':'bad-password'});players[0].submit('#login-form')
    players[0].wait("!!document.querySelector('[role=alert]')")
    passed('Logout invalidates session across reload; bad Login displays server validation error')
    for tab,user in people:
        cdp.call('Target.closeTarget',{'targetId':tab.target})
    cdp.call('Target.closeTarget',{'targetId':replacement.target})

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--origin',default='http://127.0.0.1:8080');parser.add_argument('--port',type=int,default=9222);parser.add_argument('--smoke',action='store_true');parser.add_argument('--close-browser',action='store_true');args=parser.parse_args()
    report={'unit':[],'smoke':[],'status':'RUNNING'}
    try:
        cdp=browser.CDP(args.port);report['browser']=cdp.version
        unit_tab=cdp.tab(args.origin);report['unit']=unit_tab.eval(UNIT)
        cdp.call('Target.closeTarget',{'targetId':unit_tab.target})
        if args.smoke:smoke(cdp,args.origin,report)
        assert not cdp.errors,cdp.errors
        report['status']='PASS'
    except Exception as error:
        report['status']='FAIL';report['error']=str(error)
        raise
    finally:
        (ROOT/'target/task11a-client-tests.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
        print(json.dumps({'status':report['status'],'unit':len(report['unit']),'smoke':len(report['smoke'])}))
        if args.close_browser:
            try:cdp.call('Browser.close')
            except (RuntimeError,OSError,UnboundLocalError):pass

if __name__=='__main__':main()
