"""Task22: real MP4/HTTP/raw WS/MySQL + isolated Chrome contexts. No mock API.
Uses existing component regressions. Timing waits observe shared Server deadlines, not race proofs."""
import argparse,importlib.util,json,secrets,sys
from pathlib import Path
sys.dont_write_bytecode=True
ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('image_tests',ROOT/'scripts/test-image-word-client.py')
base=importlib.util.module_from_spec(spec);spec.loader.exec_module(base)
a=base.a;old=base.old;browser=base.browser
UNIT=r'''(async()=>{
 const c=await import('/client/core.js'),v=await import('/client/video.js'),ui=await import('/client/game.js');const done=[],check=(n,f)=>{if(!f())throw Error(n);done.push(n);},bad=f=>{try{f();return false;}catch{return true;}};
 const ref='sha256:'+'a'.repeat(64),q={content:'Nghe clip',acceptedAnswers:['Bắt cá','Đánh bắt cá'],mediaRef:ref};
 check('SONG authoring uses aliases/mediaRef, no options/image/correctAnswer',()=>{const s=c.quizValues('Song','PRIVATE',[q],'SONG');return s.questions[0].mediaRef===ref && !('imageRef' in s.questions[0])&&!('options' in s.questions[0])&&!('correctAnswer' in s.questions[0]);});
 check('SONG requires local SHA ref and nonempty aliases',()=>bad(()=>c.quizValues('Song','PRIVATE',[{...q,mediaRef:null}],'SONG'))&&bad(()=>c.quizValues('Song','PRIVATE',[{...q,mediaRef:'https://outside.test/clip'}],'SONG'))&&bad(()=>c.quizValues('Song','PRIVATE',[{...q,acceptedAnswers:[]}],'SONG')));
 check('Server playhead 25sec question at15sec seeks15, never negative/past media end',()=>v.videoPosition(1000,16000,25)===15&&v.videoPosition(1000,0,25)===0&&v.videoPosition(1000,40000,25)===24.98);
 const s={gameSessionId:7,questionIndex:3,stages:[{sourceQuizId:9,firstQuestionIndex:2,questionCount:2}]};
 check('video URL is current stage source, authenticated released media',()=>v.videoUrl(s,{payload:{mediaRef:ref}})==='/api/quizzes/9/videos/'+ref.slice(7)+'?gameSessionId=7');
 check('SONG Result renders aliases rather than Quiz options',()=>ui.resultPanel({index:3,question:{mode:'SONG',payload:{acceptedAnswers:['Bắt cá']}},results:[]},[]).textContent.includes('Bắt cá'));
 return done;
})()'''

def native_click(cdp,t,selector):
 cdp.call('Page.bringToFront',session=t.session)
 point=t.eval("(()=>{const n=document.querySelector("+json.dumps(selector)+");n.scrollIntoView({block:'center'});const b=n.getBoundingClientRect();return{x:b.x+b.width/2,y:b.y+b.height/2}})()")
 for kind in ['mousePressed','mouseReleased']:cdp.call('Input.dispatchMouseEvent',{'type':kind,'x':point['x'],'y':point['y'],'button':'left','clickCount':1},t.session)

def smoke(cdp,origin,report):
 prefix='ui22_'+secrets.token_hex(4);password=secrets.token_urlsafe(15);report['fixturePrefix']=prefix;people=[]
 def passed(n):report['smoke'].append(n);print('PASS '+n,flush=True)
 def snapshot(t,gid):return t.eval(f"fetch('/api/games/{gid}/snapshot').then(r=>r.json())")
 def phase(t,i,p):t.wait(f"document.querySelector('#game-page')?.dataset.index==='{i}'&&document.querySelector('#game-page').dataset.phase==='{p}'",timeout=32)
 def put(t,endpoint,body):return t.eval(f"fetch('/api/auth/csrf').then(r=>r.json()).then(c=>fetch({json.dumps(endpoint)},{{method:'PUT',headers:{{'X-CSRF-TOKEN':c.token,'Content-Type':'application/json'}},body:JSON.stringify({json.dumps(body,ensure_ascii=False)})}})).then(async r=>({{status:r.status,body:await r.json()}}))")
 def ready():
  for t in players:t.wait("!!document.querySelector('#continue-stage')&&!document.querySelector('#continue-stage').disabled");t.click('#continue-stage')
 for role in ['author','p1','p2','p3']:
  t=cdp.tab(origin);cdp.call('Network.setCacheDisabled',{'cacheDisabled':True},t.session);t.eval('window.confirm=()=>true');t.route('register','#register-form');t.fill({'username':prefix+'_'+role,'displayName':role,'password':password,'confirmPassword':password});t.submit('#register-form');t.wait("!!document.querySelector('#login-form')");t.fill({'username':prefix+'_'+role,'password':password});t.submit('#login-form');t.wait("document.querySelector('#connection-status')?.dataset.state==='ready'");u=t.eval("fetch('/api/auth/me').then(r=>r.json())");t.eval(old.OBSERVER);people.append((t,u))
 host=people[0][0];players=[t for t,u in people[1:]];report['userIds']=[u['id'] for t,u in people];passed('4 isolated browser contexts Register/Login with real cookie/CSRF/raw WS')
 host.route('quizzes/new','#quiz-form');host.fill({'title':prefix+' Quiz','visibility':'PUBLIC'});host.eval("document.querySelector('[name=content]').value='Quiz?';['A','B','C','D'].forEach(k=>document.querySelector('[name=option'+k+']').value='Option '+k);document.querySelector('[name=correctAnswer]').value='A'");host.submit('#quiz-form');host.wait("location.hash.endsWith('/edit')&&!!document.querySelector('[name=optionA]')");quiz=int(host.eval("location.hash.split('/')[2]"))
 host.route('quizzes/new','#quiz-form');host.fill({'mode':'SONG','title':prefix+' Song','visibility':'PRIVATE'});host.click('#add-question');host.eval("document.querySelectorAll('.question-editor').forEach((r,i)=>{r.querySelector('[name=content]').value='Clip '+(i+1);r.querySelector('[name=acceptedAnswers]').value=['Bắt cá','đánh bắt cá'].join(String.fromCharCode(10));})")
 for i,name in enumerate(['song-av.mp4','song-av2.mp4']):host.upload(f'.question-editor:nth-child({i+1}) [name=video]',ROOT/'src/test/resources/quiz'/name)
 host.wait("document.querySelectorAll('.video-box video').length===2&&[...document.querySelectorAll('.video-box video')].every(v=>v.readyState>=1)");host.submit('#quiz-form');host.wait("location.hash.endsWith('/edit')&&document.querySelectorAll('[name=video]').length===2")
 song=int(host.eval("location.hash.split('/')[2]"));source=host.eval(f"fetch('/api/quizzes/{song}').then(r=>r.json())");refs=[q['mediaRef'] for q in source['questions']];report['mediaRefs']=refs;host.fill({'title':prefix+' Song edited'});host.submit('#quiz-form');host.wait("document.querySelectorAll('.question-preview video').length===2")
 assert players[0].eval(f"fetch('/api/quizzes/{song}').then(r=>r.status)")==404
 assert players[0].eval(f"fetch('/api/quizzes/{song}/videos/{refs[0][7:]}').then(r=>r.status)")==403
 source=host.eval(f"fetch('/api/quizzes/{song}').then(r=>r.json())");body={'title':source['title'],'visibility':'PUBLIC','mode':'SONG','revision':source['revision'],'questions':source['questions']};pub=put(host,f'/api/quizzes/{song}',body);assert pub['status']==200
 public=players[0].eval(f"fetch('/api/quizzes/{song}').then(r=>r.json())");assert 'questions' not in public and 'mediaRef' not in public
 body['visibility']='PRIVATE';body['revision']=pub['body']['revision'];private=put(host,f'/api/quizzes/{song}',body);assert private['status']==200;source=private['body']
 passed('SONG editor actual upload/preview/Create/Edit; private denial/public metadata without video/aliases')
 host.route('quizzes/new','#quiz-form');host.fill({'mode':'RIDDLE','title':prefix+' Riddle','visibility':'PRIVATE','content':'Đố sau video','acceptedAnswers':'bắt cá'});host.submit('#quiz-form');host.wait("location.hash.endsWith('/edit')&&!!document.querySelector('[name=acceptedAnswers]')");riddle=int(host.eval("location.hash.split('/')[2]"));report['quizIds']=[quiz,song,riddle]
 def create_room(mixed):
  host.route('rooms/new?quiz='+str(quiz if mixed else song),'#room-form');host.fill({'name':prefix+(' mixed' if mixed else ' cancel'),'maxPlayers':'3','hostParticipation':'SPECTATOR'});host.fill({'questionCount':'1','seconds':'5'},'.stage-editor:first-child ')
  if mixed:
   for mode,sid,count,seconds in [('SONG',song,2,25),('RIDDLE',riddle,1,5)]:host.click('#add-stage');host.fill({'stageMode':mode},'.stage-editor:last-child ');host.fill({'quizId':str(sid),'questionCount':str(count),'seconds':str(seconds)},'.stage-editor:last-child ')
  else:host.fill({'seconds':'25'},'.stage-editor:first-child ')
  assert host.eval("document.querySelector('[name=hostParticipation] [value=PLAYER]').disabled");host.submit('#room-form');host.wait("!!document.querySelector('#open-room')");rid=int(host.eval("location.hash.split('/')[2]"));report['roomIds'].append(rid);host.click('#open-room');host.wait("!!document.querySelector('#start-game')");code=host.eval("document.querySelector('#room-code').textContent")
  for t in players:t.route('join','#join-form');t.fill({'code':code,'participation':'PLAYER'});t.submit('#join-form');t.wait("!!document.querySelector('#waiting-room')")
  host.wait("!document.querySelector('#start-game').disabled");host.click('#start-game')
  for t in [host,*players]:phase(t,1,'INTRO')
  gid=int(host.eval("location.hash.split('/')[2]"));report['gameIds'].append(gid);assert snapshot(host,gid)['player'] is None;return rid,gid
 rid,gid=create_room(True)
 for t in players:assert t.eval(f"fetch('/api/quizzes/{song}/videos/{refs[0][7:]}?gameSessionId={gid}').then(r=>r.status)")==403
 changed={'title':source['title'],'visibility':'PRIVATE','mode':'SONG','revision':source['revision'],'questions':[{'content':'Source changed after Start','mediaRef':refs[1],'acceptedAnswers':['khác']}]};assert put(host,f'/api/quizzes/{song}',changed)['status']==200
 ready();phase(host,1,'DECISION');phase(host,1,'QUESTION_OPEN')
 for t in players:t.click('[data-option=A]');t.click('#submit-answer')
 phase(host,2,'INTRO');ready();phase(host,2,'QUESTION_OPEN')
 for t in [host,*players]:t.wait("document.querySelector('#song-video')?.readyState>=2")
 before=snapshot(players[0],gid);assert before['question']['content']=='Clip 1' and set(before['question']['payload'])=={'mediaRef','openedAtMs'}
 assert players[0].eval(f"fetch('/api/quizzes/{song}/videos/{refs[1][7:]}?gameSessionId={gid}').then(r=>r.status)")==403
 r=players[0].eval(f"fetch('/api/quizzes/{song}/videos/{refs[0][7:]}?gameSessionId={gid}',{{headers:{{Range:'bytes=0-31'}}}}).then(async r=>({{status:r.status,range:r.headers.get('Content-Range'),bytes:(await r.arrayBuffer()).byteLength}}))")
 assert r['status']==206 and r['bytes']==32;report['range']=r
 assert not players[0].eval("!!document.querySelector('#use-spin')||!!document.querySelector('#use-star')||!!document.querySelector('#correct-text')")
 players[0].eval('window.originalVideo=document.querySelector("#song-video")');players[0].click('#toggle-standings');assert players[0].eval('document.querySelector("#song-video")===originalVideo')
 # Programmatic click has no user activation: real Chrome denies audible autoplay. No play() mock.
 players[0].click('#enable-video-sound');players[0].wait("document.querySelector('#video-status').textContent.includes('Trình duyệt chưa cho phát')")
 native_click(cdp,players[0],'#enable-video-sound');players[0].wait("!document.querySelector('#song-video').muted&&!document.querySelector('#song-video').paused")
 passed('Actual H.264/AAC decoded; HTTP206 Range, unreleased video403, same video DOM across render, blocked sound + native fallback')
 players[0].wait(f"(Date.now()-{before['question']['payload']['openedAtMs']})>=5000");players[0].eval('gameSocket.close()');players[0].wait("document.querySelector('#connection-status')?.dataset.state==='offline'")
 players[1].fill({'answerText':'đánh bắt cá'});players[1].click('#submit-answer');players[1].wait("!!document.querySelector('#answer-accepted')");players[2].fill({'answerText':'bat ca'});players[2].click('#submit-answer');players[2].wait("!!document.querySelector('#answer-accepted')")
 host.wait(f"(Date.now()-{before['question']['payload']['openedAtMs']})>=15000",timeout=15);assert snapshot(host,gid)['phase']=='QUESTION_OPEN'
 cdp.call('Page.reload',{'ignoreCache':True},players[0].session);players[0].wait("document.querySelector('#connection-status')?.dataset.state==='ready'&&document.querySelector('#song-video')?.readyState>=2&&!!document.querySelector('#answer-text')&&!document.querySelector('#answer-text').disabled")
 after=snapshot(players[0],gid);observed=players[0].eval("({playhead:document.querySelector('#song-video').currentTime,muted:document.querySelector('#song-video').muted,server:Date.now()})");report['reconnect']={'beforeDeadline':before['deadlineEpochMs'],'afterDeadline':after['deadlineEpochMs'],'remainingMs':after['remainingMs'],'openedAtMs':after['question']['payload']['openedAtMs'],**observed}
 assert after['deadlineEpochMs']==before['deadlineEpochMs'] and 5000<after['remainingMs']<=10000 and observed['playhead']>=14.5 and abs(observed['playhead']-(observed['server']-before['question']['payload']['openedAtMs'])/1000)<1
 players[0].eval(old.OBSERVER);players[0].eval("wire.dropType='ANSWER'");players[0].fill({'answerText':' BẮT   CÁ '});players[0].click('#submit-answer');players[0].wait('!!wire.dropped');request=players[0].eval('wire.dropped.requestId');players[0].eval('gameSocket.close()');players[0].wait("!!document.querySelector('#retry-game-command')");host.wait("wire.messages.some(m=>m.type==='QUESTION_RESULT'&&m.questionIndex===2)")
 players[0].click('#reconnect-room');players[0].wait("document.querySelector('#connection-status')?.dataset.state==='ready'&&!document.querySelector('#retry-game-command').disabled");players[0].click('#retry-game-command');players[0].wait(f"document.querySelector('#game-ack')?.dataset.requestId==='{request}'")
 frames=players[0].eval("wire.commands.filter(m=>m.type==='ANSWER'&&m.questionIndex===2)");assert len(frames)==2 and frames[0]==frames[1]
 result=host.eval("wire.messages.find(m=>m.type==='QUESTION_RESULT'&&m.questionIndex===2).payload");assert [next(r for r in result['results'] if r['userId']==u['id'])['scoreDelta'] for t,u in people[1:]]==[10,10,0]
 passed('Disconnect at5sec/reload at15sec of25sec: same deadline/playhead≈15/remaining≈10; lost Answer ACK same UUID replay after close')
 phase(host,3,'QUESTION_OPEN');phase(players[0],3,'QUESTION_OPEN');q3=snapshot(players[0],gid)
 blob=(ROOT/'data/quiz-videos'/str(song)/(refs[1][7:]+'.mp4')).resolve();assert blob.is_relative_to((ROOT/'data/quiz-videos'/str(song)).resolve()) and blob.is_file();backup=blob.with_name(blob.name+'.task22-owned-backup');assert not backup.exists();blob.rename(backup)
 report['mediaFailureFixture']={'quizId':song,'mediaRef':refs[1],'originalRestored':False}
 try:
  assert players[0].eval(f"fetch('/api/quizzes/{song}/videos/{refs[1][7:]}?gameSessionId={gid}').then(r=>r.status)")==404
  cdp.call('Page.reload',{'ignoreCache':True},players[0].session);players[0].wait("document.querySelector('#video-status')?.textContent.includes('Không tải/phát được video')&&!!document.querySelector('#answer-text')&&!document.querySelector('#answer-text').disabled")
  error=snapshot(players[0],gid);assert error['deadlineEpochMs']==q3['deadlineEpochMs'];cdp.call('Emulation.setDeviceMetricsOverride',{'width':390,'height':844,'deviceScaleFactor':1,'mobile':True},players[0].session);assert players[0].eval('document.documentElement.scrollWidth<=innerWidth');players[0].screenshot(ROOT/'target/task22-missing-video-mobile.png')
  for t in players[:2]:t.fill({'answerText':'bắt cá'});t.click('#submit-answer')
  host.wait("wire.messages.some(m=>m.type==='QUESTION_RESULT'&&m.questionIndex===3)",timeout=28);last=host.eval("wire.messages.find(m=>m.type==='QUESTION_RESULT'&&m.questionIndex===3).payload");assert [next(r for r in last['results'] if r['userId']==u['id'])['scoreDelta'] for t,u in people[1:]]==[20,20,0]
 finally:
  if backup.exists():backup.rename(blob)
  report['mediaFailureFixture']['originalRestored']=blob.is_file()
 passed('Actual Server404 + mobile fallback still permits guess; original timer proceeds, last SONG +20/NO_ANSWER0, own media restored')
 phase(host,4,'INTRO');ready();phase(host,4,'QUESTION_OPEN')
 for t in players[:2]:t.fill({'answerText':'bắt cá'});t.click('#submit-answer')
 host.wait("wire.messages.some(m=>m.type==='GAME_END'&&m.questionIndex===4)",timeout=12)
 for t in [host,*players]:t.wait("!!document.querySelector('#final-summary')")
 final=snapshot(host,gid);report['finalSnapshot']=final;assert [m['score'] for m in final['members'] if m['participation']=='PLAYER']==[60,60,10];host.screenshot(ROOT/'target/task22-final.png')
 players[0].route('history/'+str(gid),'#history-detail');players[0].wait("document.querySelectorAll('.history-question video').length===2&&[...document.querySelectorAll('.history-question video')].every(v=>v.readyState>=1)");assert players[0].eval("document.querySelectorAll('.history-question').length===4&&!document.body.innerText.includes('undefined')&&document.body.innerText.includes('bắt cá')&&!document.body.innerText.includes('Source changed after Start')");players[0].screenshot(ROOT/'target/task22-history-mobile.png')
 host.route('room/'+str(rid),'#waiting-room');host.wait("!!document.querySelector('#start-game')");passed('Quiz→SONG2→RIDDLE completed, real Final/History immutable media/aliases/time/rank, Room WAITING')
 rid2,gid2=create_room(False);ready();phase(host,1,'QUESTION_OPEN');snap=snapshot(players[0],gid2);assert snap['player']['remainingSpins']==0 and not snap['player']['starAvailable'];players[0].fill({'answerText':'khác'});players[0].click('#submit-answer');players[0].wait("!!document.querySelector('#answer-accepted')");host.click('#cancel-game')
 for t in [host,*players]:t.wait("!!document.querySelector('#final-summary')")
 cancelled=snapshot(host,gid2);assert cancelled['endReason']=='CANCELLED' and not cancelled['hasOfficialWinner'] and cancelled['winners']==[]
 host.route('history/'+str(gid2),'#history-detail');host.wait("!!document.querySelector('[data-status=ACCEPTED_UNSCORED]')");assert host.eval("!!document.querySelector('.history-question video')&&!document.querySelector('#history-detail').innerText.includes('Đáp án đã công bố')");host.route('room/'+str(rid2),'#waiting-room');host.wait("!!document.querySelector('#start-game')");passed('Pure SONG zero Spin/Star; Host Cancel preserves ACCEPTED_UNSCORED, hides aliases, no Official Winner, media/Room retained')
 for t,u in people:cdp.call('Target.closeTarget',{'targetId':t.target})

def main():
 p=argparse.ArgumentParser();p.add_argument('--origin',default='http://127.0.0.1:8087');p.add_argument('--port',type=int,default=9224);p.add_argument('--smoke',action='store_true');args=p.parse_args();report={'task':22,'status':'RUNNING','unit':[],'smoke':[],'origin':args.origin,'clients':4,'quizIds':[],'roomIds':[],'gameIds':[]}
 try:
  cdp=browser.CDP(args.port);report['browser']=cdp.version;t=cdp.tab(args.origin);report['unit']=t.eval(old.legacy.UNIT)+t.eval(old.UNIT)+t.eval(a.mm.UNIT)+t.eval(a.UNIT)+t.eval(base.UNIT)+t.eval(UNIT);cdp.call('Target.closeTarget',{'targetId':t.target})
  if args.smoke:smoke(cdp,args.origin,report)
  assert not cdp.errors,cdp.errors;report['status']='PASS'
 except Exception as e:report['status']='FAIL';report['error']=str(e);raise
 finally:(ROOT/'target/task22-client-tests.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8');print(json.dumps({'status':report['status'],'unit':len(report['unit']),'smoke':len(report['smoke'])}),flush=True)
if __name__=='__main__':main()
