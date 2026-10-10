"""Task23: shared Server clue timeline on real UI/HTTP/raw WS/MySQL.
Reuses project-owned Chrome/CDP and previous component checks; never mocks smoke APIs.
"""
import argparse
import importlib.util
import json
import secrets
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('song_client', ROOT/'scripts/test-song-client.py')
base = importlib.util.module_from_spec(spec)
spec.loader.exec_module(base)
browser = base.browser

UNIT = r'''(async()=>{
 const c=await import('/client/core.js'),cl=await import('/client/clues.js'),g=await import('/client/game-state.js'),ui=await import('/client/game.js');
 const done=[],check=(n,f)=>{if(!f())throw Error(n);done.push(n)},bad=f=>{try{f();return false}catch{return true}};
 const q={content:'Dấu vết',acceptedAnswers:['Bắt cá'],hints:[{offsetMs:0,text:'Một'},{offsetMs:3000,text:'Hai'}]};
 check('CLUES enabled as seventh playable mode',()=>c.PLAYABLE_MODES.length===7&&c.PLAYABLE_MODES.includes('CLUES'));
 check('authoring retains offsets/aliases and excludes Quiz fields',()=>{const s=c.quizValues('Clues','PRIVATE',[q],'CLUES');return s.questions[0].hints[1].offsetMs===3000&&!('options' in s.questions[0])&&!('mediaRef' in s.questions[0])});
 check('reject empty, duplicate, descending, negative and fractional hints',()=>[[],[{offsetMs:-1,text:'x'}],[{offsetMs:1.5,text:'x'}],[{offsetMs:1,text:'x'},{offsetMs:1,text:'y'}],[{offsetMs:2,text:'x'},{offsetMs:1,text:'y'}]].every(hints=>bad(()=>c.quizValues('Clues','PUBLIC',[{...q,hints}],'CLUES'))));
 check('reject blank and oversized hint text',()=>bad(()=>c.quizValues('Clues','PUBLIC',[{...q,hints:[{offsetMs:0,text:' '}]}],'CLUES'))&&bad(()=>c.quizValues('Clues','PUBLIC',[{...q,hints:[{offsetMs:0,text:'x'.repeat(2001)}]}],'CLUES')));
 const editor=cl.clueEditor(q);check('editor converts seconds back to exact milliseconds',()=>cl.readClues(editor)[1].offsetMs===3000);editor.querySelector('.add-hint').click();check('editor adds/removes real hint rows',()=>cl.readClues(editor).length===3);editor.querySelector('.clue-editor:last-child button').click();check('editor removes without losing first hint',()=>cl.readClues(editor).length===2);
 const hints=cl.releasedClues({hints:[{offsetMs:0,text:'<img src=x onerror=alert(1)>'}]});check('hint text is escaped DOM text, never HTML',()=>!hints.querySelector('img')&&hints.textContent.includes('<img'));
 check('no released hints is a valid visible state',()=>cl.releasedClues({hints:[]}).textContent.includes('Chưa có gợi ý'));
 check('CLUES Result uses text aliases',()=>ui.resultPanel({index:1,question:{mode:'CLUES',payload:{acceptedAnswers:['bắt cá']}},results:[]},[]).textContent.includes('bắt cá'));
 return done;
})()'''

def smoke(cdp, origin, report):
    prefix='ui23_'+secrets.token_hex(4)
    password=secrets.token_urlsafe(15)
    report['fixturePrefix']=prefix
    people=[]
    def passed(name):
        report['smoke'].append(name)
        print('PASS '+name,flush=True)
    def snapshot(t,gid):
        return t.eval(f"fetch('/api/games/{gid}/snapshot').then(r=>r.json())")
    def phase(t,i,p):
        t.wait(f"document.querySelector('#game-page')?.dataset.index==='{i}'&&document.querySelector('#game-page').dataset.phase==='{p}'",timeout=32)
    def ready():
        for t in players:
            t.wait("!!document.querySelector('#continue-stage')&&!document.querySelector('#continue-stage').disabled")
            t.click('#continue-stage')
    def put(t,endpoint,body):
        return t.eval(f"fetch('/api/auth/csrf').then(r=>r.json()).then(c=>fetch({json.dumps(endpoint)},{{method:'PUT',headers:{{'X-CSRF-TOKEN':c.token,'Content-Type':'application/json'}},body:JSON.stringify({json.dumps(body,ensure_ascii=False)})}})).then(async r=>({{status:r.status,body:await r.json()}}))")
    for role in ['author','p1','p2','p3']:
        t=cdp.tab(origin)
        cdp.call('Network.setCacheDisabled',{'cacheDisabled':True},t.session)
        cdp.call('Page.addScriptToEvaluateOnNewDocument',{'source':base.old.OBSERVER},t.session)
        t.eval(base.old.OBSERVER)
        username=prefix+'_'+role
        t.eval('window.confirm=()=>true')
        t.route('register','#register-form')
        t.fill({'username':username,'displayName':'Task23 '+role,'password':password,'confirmPassword':password})
        t.submit('#register-form');t.wait("!!document.querySelector('#login-form')")
        t.fill({'username':username,'password':password});t.submit('#login-form')
        t.wait("document.querySelector('#connection-status')?.dataset.state==='ready'")
        user=t.eval("fetch('/api/auth/me').then(r=>r.json())")
        report.setdefault('userIds',[]).append(user['id'])
        people.append((t,username))
    host=people[0][0];players=[p[0] for p in people[1:]]
    host.route('quizzes/new','#quiz-form')
    host.fill({'mode':'CLUES','title':prefix+' CLUES','visibility':'PRIVATE'})
    for number in [1,2]:
        if number==2:host.click('#add-question')
        scope='.question-editor:last-child '
        host.fill({'content':f'Câu dấu vết {number}','acceptedAnswers':'bắt cá\nđánh bắt cá'},scope)
        host.fill({'hintSeconds':'0','hintText':f'Initial {number}'},scope+'.clue-editor:first-child ')
        for sec,text in [(3,f'Released {number}'),(8,f'Future {number}')]:
            host.click(scope+'.add-hint')
            host.fill({'hintSeconds':str(sec),'hintText':text},scope+'.clue-editor:last-child ')
    host.submit('#quiz-form');host.wait("location.hash.endsWith('/edit')&&document.querySelectorAll('.clue-editor').length===6")
    clues=int(host.eval("location.hash.split('/')[2]"))
    source=host.eval(f"fetch('/api/quizzes/{clues}').then(r=>r.json())")
    assert source['questions'][1]['hints'][2]['offsetMs']==8000
    host.route('quiz/'+str(clues),'.question-preview')
    assert host.eval("document.body.innerText.includes('Future 1')&&document.body.innerText.includes('bắt cá')")
    host.route('quizzes/new','#quiz-form');host.fill({'mode':'RIDDLE','title':prefix+' Riddle','visibility':'PRIVATE','content':'Đố sau dấu vết','acceptedAnswers':'bắt cá'})
    host.submit('#quiz-form');host.wait("location.hash.endsWith('/edit')&&!!document.querySelector('[name=acceptedAnswers]')")
    riddle=int(host.eval("location.hash.split('/')[2]"))
    host.route('quizzes/new','#quiz-form');host.fill({'title':prefix+' Quiz','visibility':'PRIVATE','content':'Quiz cuối','optionA':'Đúng','optionB':'Sai B','optionC':'Sai C','optionD':'Sai D','correctAnswer':'A'})
    host.submit('#quiz-form');host.wait("location.hash.endsWith('/edit')&&!!document.querySelector('[name=correctAnswer]')")
    quiz=int(host.eval("location.hash.split('/')[2]"));report['quizIds']=[clues,riddle,quiz]
    assert players[0].eval(f"fetch('/api/quizzes/{clues}').then(r=>r.status)")==404
    passed('Owner CLUES editor/save/get two timelines and aliases; invited non-owner cannot read PRIVATE source')
    def create_room(mixed):
        host.route('rooms/new?quiz='+str(clues),'#room-form')
        host.fill({'name':prefix+(' mixed' if mixed else ' cancel'),'maxPlayers':'3','hostParticipation':'SPECTATOR'})
        host.fill({'questionCount':'2' if mixed else '1','seconds':'10'},'.stage-editor:first-child ')
        if mixed:
            for mode,sid in [('RIDDLE',riddle),('QUIZ',quiz)]:
                host.click('#add-stage');host.fill({'stageMode':mode},'.stage-editor:last-child ')
                host.fill({'quizId':str(sid),'questionCount':'1','seconds':'5'},'.stage-editor:last-child ')
        assert host.eval("document.querySelector('[name=hostParticipation] [value=PLAYER]').disabled")
        host.submit('#room-form');host.wait("!!document.querySelector('#open-room')")
        rid=int(host.eval("location.hash.split('/')[2]"));report['roomIds'].append(rid)
        host.click('#open-room');host.wait("!!document.querySelector('#start-game')")
        code=host.eval("document.querySelector('#room-code').textContent")
        for t in players:
            t.route('join','#join-form');t.fill({'code':code,'participation':'PLAYER'});t.submit('#join-form');t.wait("!!document.querySelector('#waiting-room')")
        host.wait("!document.querySelector('#start-game').disabled");host.click('#start-game')
        for t in [host,*players]:phase(t,1,'INTRO')
        gid=int(host.eval("location.hash.split('/')[2]"));report['gameIds'].append(gid)
        assert snapshot(host,gid)['player'] is None
        return rid,gid
    rid,gid=create_room(True)
    changed={'title':source['title'],'visibility':'PRIVATE','mode':'CLUES','revision':source['revision'],'questions':[{'content':'Changed source','acceptedAnswers':['khác'],'hints':[{'offsetMs':0,'text':'Changed initial'},{'offsetMs':3000,'text':'Changed future'}]}]}
    assert put(host,f'/api/quizzes/{clues}',changed)['status']==200
    ready();phase(host,1,'QUESTION_OPEN')
    first=snapshot(players[0],gid);assert len(first['question']['payload']['hints'])==1 and first['question']['content']=='Câu dấu vết 1'
    for t in [host,*players]:
        assert not t.eval("document.body.innerText.includes('Future 1')||document.body.innerText.includes('Changed source')||!!document.querySelector('#correct-text')||!!document.querySelector('#use-spin')||!!document.querySelector('#use-star')")
        t.wait("document.querySelectorAll('#released-clues li').length===2")
    frames=players[0].eval("wire.messages.filter(m=>m.type==='CLUES_RELEASED')")
    assert frames and all('acceptedAnswers' not in m['payload']['question']['payload'] for m in frames)
    assert snapshot(players[0],gid)['deadlineEpochMs']==first['deadlineEpochMs']
    passed('Shared clue event to four contexts, no future clues/aliases/resources; source edit leaves game snapshot/deadline unchanged')
    players[0].eval("wire.dropType='ANSWER'");players[0].fill({'answerText':' BẮT  CÁ '});players[0].click('#submit-answer');players[0].wait("!!wire.dropped")
    saved=players[0].eval("wire.commands.filter(c=>c.type==='ANSWER').at(-1)")
    players[0].eval('gameSocket.close()');players[0].wait("!!document.querySelector('#reconnect-room')")
    players[0].click('#reconnect-room');players[0].wait("document.querySelector('#connection-status')?.dataset.state==='ready'&&!!document.querySelector('#retry-game-command')")
    players[1].fill({'answerText':'đánh bắt cá'});players[1].click('#submit-answer')
    players[2].fill({'answerText':'bat ca'});players[2].click('#submit-answer')
    phase(host,2,'QUESTION_OPEN')
    players[0].wait("!!document.querySelector('#retry-game-command')&&!document.querySelector('#retry-game-command').disabled")
    players[0].click('#retry-game-command');players[0].wait(f"document.querySelector('#game-ack')?.dataset.requestId==='{saved['requestId']}'")
    commands=players[0].eval("wire.commands.filter(c=>c.type==='ANSWER')")
    assert commands[0]==commands[-1]
    assert not players[0].eval("document.body.innerText.includes('Future 1')")
    report['retryRequestId']=saved['requestId']
    passed('Lost real ANSWER ACK, reconnect and exact-frame retry; early close does not release future hint')
    before=snapshot(players[0],gid)
    players[0].eval('gameSocket.close()');players[0].wait("document.querySelector('#connection-status')?.dataset.state==='offline'")
    players[1].fill({'answerText':'bắt cá'});players[1].click('#submit-answer');players[1].wait("!!document.querySelector('#answer-accepted')")
    host.wait("document.querySelectorAll('#released-clues li').length===2")
    host.wait(f"Date.now()>={before['deadlineEpochMs']}-5000",timeout=12)
    assert snapshot(host,gid)['phase']=='QUESTION_OPEN'
    cdp.call('Page.reload',{'ignoreCache':True},players[0].session)
    players[0].wait("document.querySelector('#connection-status')?.dataset.state==='ready'&&document.querySelectorAll('#released-clues li').length===2&&!!document.querySelector('#answer-text')&&!document.querySelector('#answer-text').disabled")
    after=snapshot(players[0],gid);report['reconnect']={'beforeDeadline':before['deadlineEpochMs'],'afterDeadline':after['deadlineEpochMs'],'remainingMs':after['remainingMs'],'releasedCount':len(after['question']['payload']['hints'])}
    assert after['deadlineEpochMs']==before['deadlineEpochMs'] and 2500<after['remainingMs']<=5000
    assert not players[0].eval("document.querySelector('#game-toasts').innerText.trim()||document.body.innerText.includes('Future 2')")
    cdp.call('Emulation.setDeviceMetricsOverride',{'width':390,'height':844,'deviceScaleFactor':1,'mobile':True},players[0].session)
    players[0].screenshot(ROOT/'target/task23-reconnect-mobile.png')
    players[0].fill({'answerText':'sai'});players[0].click('#submit-answer');players[0].wait("!!document.querySelector('#answer-accepted')")
    for t in [host,players[1],players[2]]:t.wait("document.querySelectorAll('#released-clues li').length===3")
    phase(host,3,'INTRO');ready();phase(host,3,'QUESTION_OPEN')
    for t in players:t.fill({'answerText':'bắt cá'});t.click('#submit-answer')
    phase(host,4,'INTRO');ready();phase(host,4,'DECISION')
    players[0].click('#use-star');players[0].wait("document.querySelector('#star-available').textContent==='Đã dùng'")
    phase(host,4,'QUESTION_OPEN')
    for t in players:t.click('[data-option=A]');t.click('#submit-answer')
    for t in [host,*players]:t.wait("!!document.querySelector('#final-summary')")
    final=snapshot(host,gid);report['finalSnapshot']=final
    assert [m['score'] for m in final['members'] if m['participation']=='PLAYER']==[55,60,30]
    assert final['endReason']=='COMPLETED' and final['hasOfficialWinner']
    host.screenshot(ROOT/'target/task23-final.png')
    players[0].route('history/'+str(gid),'#history-detail')
    assert players[0].eval("document.querySelectorAll('.history-question').length===4&&document.querySelectorAll('.history-question')[0].querySelectorAll('.released-clues li').length===2&&!document.querySelectorAll('.history-question')[0].innerText.includes('Future 1')&&document.body.innerText.includes('Future 2')&&document.body.innerText.includes('bắt cá')&&!document.body.innerText.includes('Changed source')&&!document.body.innerText.includes('undefined')")
    players[0].screenshot(ROOT/'target/task23-history-mobile.png')
    host.route('room/'+str(rid),'#waiting-room');host.wait("!!document.querySelector('#start-game')")
    passed('Offline clue timeline/reconnect no extra time/no toast, wrong+NO_ANSWER, CLUES2→RIDDLE→QUIZ, Final55/60/30 and immutable History/Room')
    rid2,gid2=create_room(False);ready();phase(host,1,'QUESTION_OPEN')
    players[0].fill({'answerText':'bắt cá'});players[0].click('#submit-answer');players[0].wait("!!document.querySelector('#answer-accepted')")
    host.click('#cancel-game')
    for t in [host,*players]:t.wait("!!document.querySelector('#final-summary')")
    cancelled=snapshot(host,gid2);assert cancelled['endReason']=='CANCELLED' and not cancelled['hasOfficialWinner'] and cancelled['winners']==[]
    host.route('history/'+str(gid2),'#history-detail');host.wait("!!document.querySelector('[data-status=ACCEPTED_UNSCORED]')")
    assert host.eval("document.querySelectorAll('.released-clues li').length===1&&!document.body.innerText.includes('Changed future')&&!document.querySelector('#history-detail').innerText.includes('Đáp án đã công bố')")
    host.route('room/'+str(rid2),'#waiting-room');host.wait("!!document.querySelector('#start-game')")
    passed('Host Cancel preserves committed hint prefix/ACCEPTED_UNSCORED, hides future/aliases, Room WAITING and no Official Winner')
    for t,u in people:cdp.call('Target.closeTarget',{'targetId':t.target})

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--origin',default='http://127.0.0.1:8087');parser.add_argument('--port',type=int,default=9224);parser.add_argument('--smoke',action='store_true');args=parser.parse_args()
    report={'task':23,'status':'RUNNING','unit':[],'smoke':[],'origin':args.origin,'clients':4,'quizIds':[],'roomIds':[],'gameIds':[]}
    try:
        cdp=browser.CDP(args.port);report['browser']=cdp.version;t=cdp.tab(args.origin)
        report['unit']=t.eval(base.old.legacy.UNIT)+t.eval(base.old.UNIT)+t.eval(base.a.mm.UNIT)+t.eval(base.a.UNIT)+t.eval(base.base.UNIT)+t.eval(base.UNIT)+t.eval(UNIT)
        cdp.call('Target.closeTarget',{'targetId':t.target})
        if args.smoke:smoke(cdp,args.origin,report)
        assert not cdp.errors,cdp.errors;report['status']='PASS'
    except Exception as exc:
        report['status']='FAIL';report['error']=str(exc);raise
    finally:
        (ROOT/'target/task23-client-tests.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
        print(json.dumps({'status':report['status'],'unit':len(report['unit']),'smoke':len(report['smoke'])}),flush=True)
if __name__=='__main__':main()
