"""Task18 protocol smoke: owned Chrome contexts -> real REST/raw WS/MySQL.
No v2 UI claim, fake API, test clock or browser user profile. Reuses browser-cdp.py.
Start a server on a test schema (V5), allow its explicit origin, then run this script.
"""
import argparse
import importlib.util
import json
import secrets
import uuid
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('browser_cdp',ROOT/'scripts/browser-cdp.py')
browser=importlib.util.module_from_spec(spec);spec.loader.exec_module(browser)

API=r'''window.api=async(method,path,body)=>{
 if(method!=='GET'){const r=await fetch('/api/auth/csrf');if(!r.ok)throw Error('CSRF '+r.status);window.csrf=(await r.json()).token;}
 const r=await fetch(path,{method,headers:{'Content-Type':'application/json',...(method!=='GET'?{'X-CSRF-TOKEN':window.csrf}:{})},...(body===null?{}:{body:JSON.stringify(body)})});
 const data=r.status===204?null:await r.json();if(!r.ok)throw Error(r.status+' '+JSON.stringify(data));return data;
};window.connect=()=>new Promise((resolve,reject)=>{
 const url=new URL('/ws',location.href);url.protocol='ws:';const s=new WebSocket(url);window.sock=s;
 s.onmessage=e=>{const m=JSON.parse(e.data);window.frames.push(m);if(m.type==='AUTH_READY')resolve(m);};s.onerror=()=>reject(Error('WS error'));s.onclose=e=>window.closedCode=e.code;
});window.frames=[];window.send=frame=>sock.send(JSON.stringify(frame));'''

def main():
 p=argparse.ArgumentParser();p.add_argument('--origin',default='http://127.0.0.1:8087');p.add_argument('--port',type=int,default=9224);p.add_argument('--close-browser',action='store_true');args=p.parse_args()
 report={'task':18,'surface':'Chrome native fetch/WebSocket; no multimode UI','status':'RUNNING','checks':[],'origin':args.origin,'clients':4,'gameIds':[]}
 tabs=[];cdp=None
 def passed(name):report['checks'].append(name);print('PASS '+name,flush=True)
 def call(tab,method,path,body=None):return tab.eval('api('+json.dumps(method)+','+json.dumps(path)+','+json.dumps(body,ensure_ascii=False)+')')
 def frame(kind,target,index,payload,scope='GAME'):return {'v':1,'kind':'COMMAND','requestId':str(uuid.uuid4()),'type':kind,'target':{'kind':scope,'id':target},'questionIndex':index,'payload':payload}
 def response(tab,cmd):
  request=cmd['requestId'];count=tab.eval('frames.filter(m=>m.requestId==='+json.dumps(request)+' && ["ACK","ERROR"].includes(m.kind)).length')
  tab.eval('send('+json.dumps(cmd,ensure_ascii=False)+')');query='frames.filter(m=>m.requestId==='+json.dumps(request)+' && ["ACK","ERROR"].includes(m.kind))'
  tab.wait(query+'.length>'+str(count));return tab.eval(query+'['+str(count)+']')
 def accept(tab,cmd):
  ack=response(tab,cmd);assert ack['kind']=='ACK' and ack['status']=='ACCEPTED',ack;return ack
 def event(tab,kind,index):
  query='frames.find(m=>m.type==='+json.dumps(kind)+'&&m.payload?.questionIndex==='+str(index)+')';tab.wait('!!('+query+')',timeout=16);return tab.eval(query)['payload']
 try:
  cdp=browser.CDP(args.port);report['browser']=cdp.version
  prefix='proto18_'+secrets.token_hex(4);report['fixturePrefix']=prefix;password=secrets.token_urlsafe(18)
  accounts=[]
  for i in range(4):
   tab=cdp.tab(args.origin);tabs.append(tab);tab.eval(API);name=prefix+'_'+str(i)
   call(tab,'POST','/api/auth/register',{'username':name,'displayName':name,'password':password})
   accounts.append(call(tab,'POST','/api/auth/login',{'username':name,'password':password}));tab.eval('connect()')
  host,*players=tabs;passed('Four browser contexts have distinct authenticated accounts and cookie WS connections')
  q=call(host,'POST','/api/quizzes',{'title':prefix+' Quiz','mode':'QUIZ','visibility':'PUBLIC','questions':[{'content':'Pick D','options':{'A':'A','B':'B','C':'C','D':'D'},'correctAnswer':'D'}]*10})
  r=call(host,'POST','/api/quizzes',{'title':prefix+' Riddle','mode':'RIDDLE','visibility':'PRIVATE','questions':[{'content':'Có chân không đi?','acceptedAnswers':['cái bàn','bàn']}]})
  report['quizIds']=[q['id'],r['id']]
  room=call(host,'POST','/api/rooms',{'requestId':str(uuid.uuid4()),'config':{'name':prefix,'maxPlayers':3,'hostParticipation':'SPECTATOR','stages':[{'mode':'QUIZ','quizId':q['id'],'questionCount':10,'questionDurationMs':5000},{'mode':'RIDDLE','quizId':r['id'],'questionCount':1,'questionDurationMs':5000}]}})
  room=call(host,'POST','/api/rooms/'+str(room['id'])+'/open',{'requestId':str(uuid.uuid4()),'revision':room['revision']});report['roomIds']=[room['id']]
  accept(host,frame('SUBSCRIBE_ROOM',room['id'],None,{},'ROOM'))
  for tab in players:accept(tab,frame('JOIN_ROOM',room['id'],None,{'roomCode':room['roomCode'],'participation':'PLAYER'},'ROOM'))
  room=call(host,'GET','/api/rooms/'+str(room['id']));start=frame('START_GAME',room['id'],None,{'revision':room['revision'],'questionCount':11},'ROOM');startAck=accept(host,start);gid=startAck['payload']['gameSessionId'];report['gameIds']=[gid]
  intro=event(host,'INTRO_STARTED',1);assert intro['question'] is None and intro['player'] is None and intro['remainingMs']<=10000
  passed('Private author Host Spectator starts real Quiz -> RIDDLE plan with three other Players')
  cont=frame('CONTINUE',gid,1,{});contAck=accept(players[0],cont)
  for tab in players[1:]:accept(tab,frame('CONTINUE',gid,1,{}))
  event(host,'DECISION_STARTED',1);assert accept(players[0],cont)==contAck
  spin=frame('USE_SPIN',gid,1,{});spinAck=accept(players[0],spin);star=frame('USE_STAR',gid,1,{});starAck=accept(players[1],star)
  passed('Intro opens early; Quiz Spin and Star commit; Continue replay preserves original ACK')
  first=None;firstAck=None
  for index in range(1,11):
   opened=event(host,'QUESTION_START',index);assert opened['question']['correctAnswer'] is None and opened['schemaVersion']==2
   for n,tab in enumerate(players):
    cmd=frame('ANSWER',gid,index,{'option':'D'});ack=accept(tab,cmd)
    if index==1 and n==0:first,firstAck=cmd,ack
   result=event(host,'QUESTION_RESULT',index);assert len(result['results'])==3 and all(x['outcome']=='CORRECT' for x in result['results'])
   assert result['question']['correctAnswer']=='D'
  intro=event(host,'INTRO_STARTED',11);assert intro['stage']['mode']=='RIDDLE' and intro['question'] is None
  for tab in players:accept(tab,frame('CONTINUE',gid,11,{}))
  opened=event(host,'QUESTION_START',11);assert opened['question']['options']=={} and opened['question']['payload']=={}
  passed('Ten real timed Quiz phases/results transition automatically to RIDDLE without exposing future answers')
  # Replace only this fixture socket; accepted state and subscription must be restored by RECONNECT.
  player=players[0];player.eval('connect()');snap=accept(player,frame('RECONNECT',gid,None,{}))['payload'];assert snap['questionIndex']==11 and snap['phase']=='QUESTION_OPEN' and not snap['player']['alreadyAnswered']
  text=frame('ANSWER',gid,11,{'text':'  CÁI   BÀN '});textAck=accept(player,text);assert 'CORRECT' not in json.dumps(textAck) and 'acceptedAnswers' not in json.dumps(textAck)
  for tab in players[1:]:accept(tab,frame('ANSWER',gid,11,{'text':'bàn'}))
  final=event(host,'GAME_END',11);assert final['endReason']=='COMPLETED' and final['hasOfficialWinner'] and final['player'] is None
  assert all(m['playerState']=='PLAYING' for m in final['members'] if m['participation']=='PLAYER')
  assert all(x['scoreDelta']==20 for x in final['results'])
  for cmd,ack in [(first,firstAck),(spin,spinAck),(text,textAck)]:assert accept(player,cmd)==ack
  assert accept(host,start)==startAck
  passed('Browser replacement/reconnect, text normalization, stage-last +20 and original ACK replay after FINISHED')
  history=call(player,'GET','/api/games/history/'+str(gid));assert len(history['questions'])==11 and history['questions'][-1]['payload']['acceptedAnswers']==['cái bàn','bàn']
  room=call(host,'GET','/api/rooms/'+str(room['id']));assert room['status']=='WAITING'
  passed('Final/History real REST preserve correct-only metric, frozen answers and Room returns WAITING')
  # Separate new game verifies Cancel without manufacturing a result for accepted text.
  plan={'name':prefix+' Cancel','maxPlayers':3,'hostParticipation':'SPECTATOR','stages':[{'mode':'RIDDLE','quizId':r['id'],'questionCount':1,'questionDurationMs':5000}]}
  room=call(host,'POST','/api/rooms',{'requestId':str(uuid.uuid4()),'config':plan});report['roomIds'].append(room['id']);room=call(host,'POST','/api/rooms/'+str(room['id'])+'/open',{'requestId':str(uuid.uuid4()),'revision':room['revision']})
  accept(host,frame('SUBSCRIBE_ROOM',room['id'],None,{},'ROOM'))
  for tab in players:accept(tab,frame('JOIN_ROOM',room['id'],None,{'roomCode':room['roomCode'],'participation':'PLAYER'},'ROOM'))
  room=call(host,'GET','/api/rooms/'+str(room['id']));gid=accept(host,frame('START_GAME',room['id'],None,{'revision':room['revision'],'questionCount':1},'ROOM'))['payload']['gameSessionId'];report['gameIds'].append(gid)
  # Filter completed-game frames before assertions on the second target.
  for tab in tabs:tab.eval('frames=frames.filter(m=>m.target?.kind!=="GAME")')
  intro=call(host,'GET','/api/games/'+str(gid)+'/snapshot');assert intro['schemaVersion']==2
  for tab in players:accept(tab,frame('CONTINUE',gid,1,{}))
  event(host,'QUESTION_START',1);accept(player,frame('ANSWER',gid,1,{'text':'bàn'}));cancel=call(host,'POST','/api/games/'+str(gid)+'/cancel',{'requestId':str(uuid.uuid4())})
  assert not cancel['finalSnapshot']['hasOfficialWinner'] and cancel['finalSnapshot']['endReason']=='CANCELLED'
  h=call(player,'GET','/api/games/history/'+str(gid));a=h['questions'][0]['answers'][0];assert a['answerStatus']=='ACCEPTED_UNSCORED' and a['result'] is None and h['questions'][0]['payload']=={}
  passed('Browser REST Cancel keeps accepted text unscored, hides aliases and declares no Official Winner')
  assert not cdp.errors,cdp.errors;report['status']='PASS'
 except Exception as e:
  report['status']='FAIL';report['error']=str(e);raise
 finally:
  (ROOT/'target/task18-browser-protocol.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
  print(json.dumps({'status':report['status'],'checks':len(report['checks'])}),flush=True)
  if cdp:
   for tab in tabs:cdp.call('Target.closeTarget',{'targetId':tab.target})
   if args.close_browser:
    try:cdp.call('Browser.close')
    except (RuntimeError,OSError):pass

if __name__=='__main__':main()
