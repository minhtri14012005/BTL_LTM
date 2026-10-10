"""Task21 IMAGE_WORD: component assertions separate from native browser/REST/raw WS/MySQL smoke."""
import argparse,importlib.util,json,secrets,sys,struct,zlib
from pathlib import Path
sys.dont_write_bytecode=True
ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('arrangement_tests',ROOT/'scripts/test-arrangement-client.py')
a=importlib.util.module_from_spec(spec);spec.loader.exec_module(a)
old=a.old;browser=a.browser
UNIT=r'''(async()=>{
 const c=await import('/client/core.js'),ui=await import('/client/game.js');const done=[],check=(n,f)=>{if(!f())throw Error(n);done.push(n);},bad=f=>{try{f();return false;}catch{return true;}};
 const ref='sha256:'+'a'.repeat(64),q={content:'Nhìn hình đoán cụm từ',acceptedAnswers:['bắt cá'],imageRef:ref};
 check('IMAGE_WORD is playable with text aliases and immutable image ref',()=>{const v=c.quizValues('Hình','PRIVATE',[q],'IMAGE_WORD');return v.questions[0].imageRef===ref&&v.questions[0].acceptedAnswers[0]==='bắt cá'&&!('options'in v.questions[0])&&!('correctAnswer'in v.questions[0]);});
 check('IMAGE_WORD rejects absent/malformed image and empty aliases',()=>bad(()=>c.quizValues('Hình','PUBLIC',[{...q,imageRef:null}],'IMAGE_WORD'))&&bad(()=>c.quizValues('Hình','PUBLIC',[{...q,imageRef:'https://example.test/'}],'IMAGE_WORD'))&&bad(()=>c.quizValues('Hình','PUBLIC',[{...q,acceptedAnswers:[]}],'IMAGE_WORD')));
 const node=ui.image({schemaVersion:2,stages:[{sourceQuizId:9,firstQuestionIndex:2,questionCount:2}],gameSessionId:7,questionIndex:2},{imageRef:ref}),box=document.createElement('div');box.append(node);node.dispatchEvent(new Event('error'));
 check('missing image has visible fallback, no client close/scoring',()=>box.textContent.includes('Câu vẫn tiếp tục theo giờ Server')&&!box.querySelector('img'));
 check('IMAGE_WORD Result prints text aliases instead of Quiz options',()=>ui.resultPanel({index:2,question:{mode:'IMAGE_WORD',payload:{acceptedAnswers:['bắt cá']}},results:[]},[]).textContent.includes('bắt cá'));
 return done;
})()'''

def png(p,color):
 def chunk(k,d):return struct.pack('!I',len(d))+k+d+struct.pack('!I',zlib.crc32(k+d)&0xffffffff)
 p.write_bytes(b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('!2I5B',8,8,8,2,0,0,0))+chunk(b'IDAT',zlib.compress((b'\0'+bytes(color)*8)*8))+chunk(b'IEND',b''))

def smoke(cdp,origin,report):
 prefix='ui21_'+secrets.token_hex(4);password=secrets.token_urlsafe(15);report['fixturePrefix']=prefix;people=[]
 def passed(n):report['smoke'].append(n);print('PASS '+n,flush=True)
 def snapshot(t,gid):return t.eval(f"fetch('/api/games/{gid}/snapshot').then(r=>r.json())")
 def phase(t,i,p):t.wait(f"document.querySelector('#game-page')?.dataset.index==='{i}'&&document.querySelector('#game-page').dataset.phase==='{p}'",timeout=18)
 def put(t,endpoint,body):return t.eval(f"fetch('/api/auth/csrf').then(r=>r.json()).then(c=>fetch({json.dumps(endpoint)},{{method:'PUT',headers:{{'X-CSRF-TOKEN':c.token,'Content-Type':'application/json'}},body:JSON.stringify({json.dumps(body,ensure_ascii=False)})}})).then(async r=>({{status:r.status,body:await r.json()}}))")
 for role in ['author','p1','p2','p3']:
  t=cdp.tab(origin);cdp.call('Network.setCacheDisabled',{'cacheDisabled':True},t.session);t.eval('window.confirm=()=>true');t.route('register','#register-form');t.fill({'username':prefix+'_'+role,'displayName':role,'password':password,'confirmPassword':password});t.submit('#register-form');t.wait("!!document.querySelector('#login-form')");t.fill({'username':prefix+'_'+role,'password':password});t.submit('#login-form');t.wait("document.querySelector('#connection-status')?.dataset.state==='ready'");u=t.eval("fetch('/api/auth/me').then(r=>r.json())");t.eval(old.OBSERVER);people.append((t,u))
 host=people[0][0];players=[t for t,u in people[1:]];report['userIds']=[u['id'] for t,u in people];passed('4 separate browser contexts Register/Login, real cookie/CSRF/raw WS')
 host.route('quizzes/new','#quiz-form');host.fill({'title':prefix+' Quiz','visibility':'PUBLIC'});host.eval("document.querySelector('[name=content]').value='Quiz?';['A','B','C','D'].forEach(k=>document.querySelector('[name=option'+k+']').value='Option '+k);document.querySelector('[name=correctAnswer]').value='A'");host.submit('#quiz-form');host.wait("location.hash.endsWith('/edit')&&!!document.querySelector('[name=optionA]')");quiz=int(host.eval("location.hash.split('/')[2]"))
 files=[]
 for i,color in enumerate([(26,112,85),(112,26,85)]):
  p=ROOT/f'target/task21-upload-{i}.png';png(p,color);files.append(p)
 host.route('quizzes/new','#quiz-form');host.fill({'mode':'IMAGE_WORD','title':prefix+' Image','visibility':'PRIVATE'});host.click('#add-question')
 host.eval("document.querySelectorAll('.question-editor').forEach((r,i)=>{r.querySelector('[name=content]').value='Hình gợi cụm từ '+(i+1);r.querySelector('[name=acceptedAnswers]').value=['Bắt cá','đánh bắt cá'].join(String.fromCharCode(10));})")
 for i in range(2):host.upload(f'.question-editor:nth-child({i+1}) [name=image]',files[i])
 host.wait("document.querySelectorAll('.image-box img').length===2&&[...document.querySelectorAll('.image-box img')].every(n=>n.naturalWidth>0)");host.submit('#quiz-form');host.wait("location.hash.endsWith('/edit')&&document.querySelectorAll('.question-editor').length===2")
 image=int(host.eval("location.hash.split('/')[2]"));source=host.eval(f"fetch('/api/quizzes/{image}').then(r=>r.json())");refs=[q['imageRef'] for q in source['questions']];report['imageRefs']=refs
 host.fill({'title':prefix+' Image edited'});host.submit('#quiz-form');host.wait("document.querySelectorAll('.question-preview img').length===2");assert players[0].eval(f"fetch('/api/quizzes/{image}').then(r=>r.status)")==404
 assert players[0].eval(f"fetch('/api/quizzes/{image}/images/{refs[0][7:]}').then(r=>r.status)")==403
 # Public metadata remains secret-free as well; use actual owner REST edit, then return PRIVATE.
 source=host.eval(f"fetch('/api/quizzes/{image}').then(r=>r.json())")
 body={'title':source['title'],'visibility':'PUBLIC','mode':'IMAGE_WORD','revision':source['revision'],'questions':source['questions']};public=put(host,f'/api/quizzes/{image}',body);assert public['status']==200
 view=players[0].eval(f"fetch('/api/quizzes/{image}').then(r=>r.json())");assert 'questions' not in view and 'imageRef' not in view
 body['visibility']='PRIVATE';body['revision']=public['body']['revision'];private=put(host,f'/api/quizzes/{image}',body);assert private['status']==200;source=private['body']
 passed('IMAGE_WORD real pre-create upload/previews/Create/Edit, private denial and public metadata without image/answers')
 host.route('quizzes/new','#quiz-form');host.fill({'mode':'RIDDLE','title':prefix+' Riddle','visibility':'PRIVATE','content':'Đố sau màn hình','acceptedAnswers':'bắt cá'});host.submit('#quiz-form');host.wait("location.hash.endsWith('/edit')&&!!document.querySelector('[name=acceptedAnswers]')");riddle=int(host.eval("location.hash.split('/')[2]"));report['quizIds']=[quiz,image,riddle]
 host.route('rooms/new?quiz='+str(quiz),'#room-form');host.fill({'name':prefix+' mixed','maxPlayers':'3','hostParticipation':'SPECTATOR'});host.fill({'questionCount':'1','seconds':'10'},'.stage-editor:first-child ')
 for mode,sid,count in [('IMAGE_WORD',image,2),('RIDDLE',riddle,1)]:
  host.click('#add-stage');host.fill({'stageMode':mode},'.stage-editor:last-child ');host.fill({'quizId':str(sid),'questionCount':str(count),'seconds':'10'},'.stage-editor:last-child ')
 assert host.eval("document.querySelector('[name=hostParticipation] [value=PLAYER]').disabled");host.submit('#room-form');host.wait("!!document.querySelector('#open-room')");rid=int(host.eval("location.hash.split('/')[2]"));report['roomIds']=[rid];host.click('#open-room');host.wait("!!document.querySelector('#start-game')");code=host.eval("document.querySelector('#room-code').textContent")
 for t in players:t.route('join','#join-form');t.fill({'code':code,'participation':'PLAYER'});t.submit('#join-form');t.wait("!!document.querySelector('#waiting-room')")
 host.wait("!document.querySelector('#start-game').disabled");host.click('#start-game')
 for t in [host,*players]:phase(t,1,'INTRO')
 gid=int(host.eval("location.hash.split('/')[2]"));report['gameIds']=[gid];assert snapshot(host,gid)['player'] is None
 for t in players:assert t.eval(f"fetch('/api/quizzes/{image}/images/{refs[0][7:]}?gameSessionId={gid}').then(r=>r.status)")==403
 # Edit source AFTER Start through real REST. Immutable game still contains original hint/ref/aliases.
 changed={'title':source['title'],'visibility':'PRIVATE','mode':'IMAGE_WORD','revision':source['revision'],'questions':[{'content':'Source changed after Start','imageRef':refs[1],'acceptedAnswers':['khác']} ]}
 assert put(host,f'/api/quizzes/{image}',changed)['status']==200
 def ready():
  for t in players:t.wait("!!document.querySelector('#continue-stage')&&!document.querySelector('#continue-stage').disabled");t.click('#continue-stage')
 ready();phase(host,1,'DECISION');phase(host,1,'QUESTION_OPEN')
 for t in players:t.click('[data-option=A]');t.click('#submit-answer')
 phase(host,2,'INTRO');ready();phase(host,2,'QUESTION_OPEN')
 for t in [host,*players]:t.wait("document.querySelector('.game-question img')?.naturalWidth>0")
 opened=snapshot(players[0],gid);assert opened['question']['content']=='Hình gợi cụm từ 1' and opened['question']['imageRef']==refs[0] and set(opened['question']['payload'])=={'mediaRef'}
 assert players[0].eval(f"fetch('/api/quizzes/{image}/images/{refs[1][7:]}?gameSessionId={gid}').then(r=>r.status)")==403
 assert not players[0].eval("!!document.querySelector('#use-spin')||!!document.querySelector('#use-star')||!!document.querySelector('#correct-text')")
 passed('Author Spectator +3 Players, Quiz→IMAGE_WORD→RIDDLE; future image denied; immutable snapshot after source edit')
 players[0].eval("wire.dropType='ANSWER'");players[0].fill({'answerText':' BẮT   CÁ '});players[0].click('#submit-answer');players[0].wait('!!wire.dropped');request=players[0].eval('wire.dropped.requestId');players[0].eval('gameSocket.close()');players[0].wait("!!document.querySelector('#retry-game-command')")
 players[1].fill({'answerText':'đánh bắt cá'});players[1].click('#submit-answer');players[1].wait("!!document.querySelector('#answer-accepted')");players[2].fill({'answerText':'bat ca'});players[2].click('#submit-answer')
 host.wait("wire.messages.some(m=>m.type==='QUESTION_RESULT'&&m.questionIndex===2)");players[0].click('#reconnect-room');players[0].wait("document.querySelector('#connection-status')?.dataset.state==='ready'&&!document.querySelector('#retry-game-command').disabled");players[0].click('#retry-game-command');players[0].wait(f"document.querySelector('#game-ack')?.dataset.requestId==='{request}'")
 frames=players[0].eval("wire.commands.filter(m=>m.type==='ANSWER'&&m.questionIndex===2)");assert len(frames)==2 and frames[0]==frames[1]
 result=host.eval("wire.messages.find(m=>m.type==='QUESTION_RESULT'&&m.questionIndex===2).payload");assert [next(r for r in result['results'] if r['userId']==u['id'])['scoreDelta'] for t,u in people[1:]]==[10,10,0]
 passed('Lost text Answer ACK replay after Close/reconnect keeps UUID/text and one Answer; aliases +10, missing Vietnamese accents WRONG0')
 phase(host,3,'QUESTION_OPEN');phase(players[0],3,'QUESTION_OPEN');before=snapshot(players[0],gid)
 blob=(ROOT/'data/quiz-images'/str(image)/(refs[1][7:]+'.png')).resolve();assert blob.is_relative_to((ROOT/'data/quiz-images'/str(image)).resolve()) and blob.is_file();backup=blob.with_name(blob.name+'.task21-owned-backup');assert not backup.exists()
 report['mediaFailureFixture']={'quizId':image,'imageRef':refs[1],'originalRestored':False}
 blob.rename(backup)
 try:
  players[0].eval('gameSocket.close()');players[0].wait("document.querySelector('#connection-status')?.dataset.state==='offline'")
  for pi,t in enumerate(players[1:]):t.fill({'answerText':'bắt cá' if pi==0 else 'sai'});t.click('#submit-answer');t.wait("!!document.querySelector('#answer-accepted')")
  assert snapshot(host,gid)['phase']=='QUESTION_OPEN' # Disconnect after opening still belongs to the wait-set.
  host.wait("parseFloat(document.querySelector('#game-countdown').textContent)<9")
  assert players[0].eval(f"fetch('/api/quizzes/{image}/images/{refs[1][7:]}?gameSessionId={gid}').then(r=>r.status)")==404
  cdp.call('Page.reload',{'ignoreCache':True},players[0].session);players[0].wait("document.querySelector('#connection-status')?.dataset.state==='ready'&&!!document.querySelector('#answer-text')&&!document.querySelector('#answer-text').disabled");players[0].wait("document.querySelector('.game-question').innerText.includes('Không tải được ảnh')")
  after=snapshot(players[0],gid);assert after['deadlineEpochMs']==before['deadlineEpochMs'] and after['remainingMs']<before['remainingMs'] and after['question']['imageRef']==before['question']['imageRef']
  cdp.call('Emulation.setDeviceMetricsOverride',{'width':390,'height':844,'deviceScaleFactor':1,'mobile':True},players[0].session);assert players[0].eval('document.documentElement.scrollWidth<=innerWidth');players[0].screenshot(ROOT/'target/task21-missing-image-mobile.png')
  players[0].fill({'answerText':'bắt cá'});players[0].click('#submit-answer');host.wait("wire.messages.some(m=>m.type==='QUESTION_RESULT'&&m.questionIndex===3)");last=host.eval("wire.messages.find(m=>m.type==='QUESTION_RESULT'&&m.questionIndex===3).payload");assert [next(r for r in last['results'] if r['userId']==u['id'])['scoreDelta'] for t,u in people[1:]]==[20,20,0]
 finally:
  if backup.exists():backup.rename(blob)
  report['mediaFailureFixture']['originalRestored']=blob.is_file()
 passed('Actual owned media temporarily unavailable:404/fallback, offline wait gate, reconnect original deadline, still answer, last IMAGE_WORD +20, file restored')
 phase(host,4,'INTRO');ready();phase(host,4,'QUESTION_OPEN')
 for pi,t in enumerate(players):
  if pi<2:t.fill({'answerText':'bắt cá'});t.click('#submit-answer')
 host.wait("wire.messages.some(m=>m.type==='GAME_END'&&m.questionIndex===4)",timeout=15)
 for t in [host,*players]:t.wait("!!document.querySelector('#final-summary')")
 final=snapshot(host,gid);report['finalSnapshot']=final;assert [m['score'] for m in final['members'] if m['participation']=='PLAYER']==[60,60,10];host.screenshot(ROOT/'target/task21-final.png')
 players[0].route('history/'+str(gid),'#history-detail');players[0].wait("document.querySelectorAll('.history-question img').length===2&&[...document.querySelectorAll('.history-question img')].every(n=>n.naturalWidth>0)");assert players[0].eval("document.querySelectorAll('.history-question').length===4&&!document.body.innerText.includes('undefined')&&document.body.innerText.includes('bắt cá')&&!document.body.innerText.includes('Source changed after Start')");players[0].screenshot(ROOT/'target/task21-history-mobile.png')
 host.route('room/'+str(rid),'#waiting-room');host.wait("!!document.querySelector('#start-game')");passed('Real Final/History images/text/results immutable after edit, NO_ANSWER0, scores60/60/10, Room WAITING')
 for t,u in people:cdp.call('Target.closeTarget',{'targetId':t.target})

def main():
 p=argparse.ArgumentParser();p.add_argument('--origin',default='http://127.0.0.1:8087');p.add_argument('--port',type=int,default=9224);p.add_argument('--smoke',action='store_true');args=p.parse_args();report={'task':21,'status':'RUNNING','unit':[],'smoke':[],'origin':args.origin,'clients':4,'quizIds':[],'roomIds':[],'gameIds':[]}
 try:
  cdp=browser.CDP(args.port);report['browser']=cdp.version;t=cdp.tab(args.origin);report['unit']=t.eval(old.legacy.UNIT)+t.eval(old.UNIT)+t.eval(a.mm.UNIT)+t.eval(a.UNIT)+t.eval(UNIT);cdp.call('Target.closeTarget',{'targetId':t.target})
  if args.smoke:smoke(cdp,args.origin,report)
  assert not cdp.errors,cdp.errors;report['status']='PASS'
 except Exception as e:report['status']='FAIL';report['error']=str(e);raise
 finally:(ROOT/'target/task21-client-tests.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8');print(json.dumps({'status':report['status'],'unit':len(report['unit']),'smoke':len(report['smoke'])}),flush=True)
if __name__=='__main__':main()
