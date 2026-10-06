"""Opt-in Task15 benchmark: real cookie HTTP + raw WebSocket, Python stdlib only.
No business engine, API mocks, client timestamps or timer acceleration.
"""
import argparse, base64, concurrent.futures, copy, csv, hashlib, http.cookiejar
import json, os, socket, struct, threading, time, urllib.request, uuid
from pathlib import Path
from urllib.parse import urlparse

FIELDS = ['mode','scenario','clients','rooms','round','trial','user_id','game_id','operation',
          'response_ms','snapshot_response_ms','client_apply_resync_ms','original_recovered',
          'duplicate_side_effects','state_mismatch','invariant_violation','code']

class Client:
    def __init__(self, origin, username, password):
        self.origin=origin; self.cookies=http.cookiejar.CookieJar()
        self.http=urllib.request.build_opener(urllib.request.HTTPCookieProcessor(self.cookies))
        self.csrf=self.request('GET','/api/auth/csrf')
        self.user=self.request('POST','/api/auth/register',{'username':username,'displayName':username,'password':password})
        self.request('POST','/api/auth/login',{'username':username,'password':password})
        self.csrf=self.request('GET','/api/auth/csrf')
        self.model=None; self.wire=None
    def request(self, method, path, body=None):
        headers={'Content-Type':'application/json'}
        if method!='GET' and hasattr(self,'csrf'): headers[self.csrf['headerName']]=self.csrf['token']
        req=urllib.request.Request(self.origin+path, data=None if body is None else json.dumps(body).encode(),headers=headers,method=method)
        with self.http.open(req,timeout=15) as response:
            data=response.read(); return json.loads(data) if data else None
    def connect(self):
        self.wire=Wire(self); return self.wire

class Wire:
    def __init__(self, client):
        self.client=client; self.frames=[]; self.condition=threading.Condition(); self.error=None
        self.send_lock=threading.Lock(); self.closed=False
        url=urlparse(client.origin); port=url.port or 80
        if url.scheme!='http': raise ValueError('This stdlib harness currently requires HTTP/ws; use only localhost/LAN.')
        self.sock=socket.create_connection((url.hostname,port),timeout=15)
        cookie='; '.join(c.name+'='+c.value for c in client.cookies)
        key=base64.b64encode(os.urandom(16)).decode()
        self.sock.sendall((f'GET /ws HTTP/1.1\r\nHost: {url.netloc}\r\nOrigin: {client.origin}\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Key: {key}\r\nSec-WebSocket-Version: 13\r\nCookie: {cookie}\r\n\r\n').encode())
        header=b''
        while b'\r\n\r\n' not in header: header+=self.exact(1)
        if not header.startswith(b'HTTP/1.1 101 '): raise RuntimeError('WebSocket handshake rejected')
        self.reader=threading.Thread(target=self.read,daemon=True); self.reader.start()
        self.wait(lambda m:m.get('type')=='AUTH_READY')
    def exact(self,n):
        value=b''
        while len(value)<n:
            chunk=self.sock.recv(n-len(value))
            if not chunk: raise EOFError('WebSocket disconnected')
            value+=chunk
        return value
    def raw_send(self,data,opcode=1):
        mask=os.urandom(4); n=len(data)
        head=bytes([0x80|opcode,0x80|n]) if n<126 else bytes([0x80|opcode,0xFE])+struct.pack('!H',n) if n<65536 else bytes([0x80|opcode,0xFF])+struct.pack('!Q',n)
        with self.send_lock: self.sock.sendall(head+mask+bytes(b^mask[i%4] for i,b in enumerate(data)))
    def read(self):
        fragment=b''
        try:
            while not self.closed:
                a,b=self.exact(2); n=b&127
                if n==126:n=struct.unpack('!H',self.exact(2))[0]
                elif n==127:n=struct.unpack('!Q',self.exact(8))[0]
                mask=self.exact(4) if b&128 else None; data=self.exact(n)
                if mask:data=bytes(x^mask[i%4] for i,x in enumerate(data))
                opcode=a&15
                if opcode==9:self.raw_send(data,10);continue
                if opcode==8:break
                if opcode==10:continue
                fragment+=data
                if a&128:
                    message=json.loads(fragment); fragment=b''
                    with self.condition:
                        self.frames.append(message)
                        if message.get('kind')=='EVENT' and 'gameSessionId' in message.get('payload',{}):
                            self.client.model=copy.deepcopy(message['payload'])
                        self.condition.notify_all()
        except (OSError,EOFError) as e:
            if not self.closed:self.error=repr(e)
        finally:
            with self.condition:self.condition.notify_all()
    def wait(self,predicate,start=0,timeout=15):
        end=time.monotonic()+timeout
        with self.condition:
            while True:
                for m in self.frames[start:]:
                    if predicate(m):return m
                if self.error:raise RuntimeError(self.error)
                left=end-time.monotonic()
                if left<=0:raise TimeoutError('Required frame not received')
                self.condition.wait(left)
    def response(self,command):
        start=len(self.frames); begin=time.perf_counter_ns()
        self.raw_send(json.dumps(command,separators=(',',':')).encode())
        m=self.wait(lambda m:m.get('requestId')==command['requestId'] and m.get('kind') in ('ACK','ERROR'),start)
        return m,(time.perf_counter_ns()-begin)/1e6
    def close(self):
        self.closed=True
        try:self.raw_send(struct.pack('!H',1000)+b'experiment complete',8)
        except OSError:pass
        try:self.sock.shutdown(socket.SHUT_RDWR)
        except OSError:pass
        self.sock.close();self.reader.join(timeout=2)

class Harness:
    def __init__(self,args):
        self.args=args;self.output=Path(args.output);self.output.mkdir(parents=True,exist_ok=True)
        self.file=(self.output/(args.mode+'-client.csv')).open('w',newline='',encoding='utf-8')
        self.csv=csv.DictWriter(self.file,fieldnames=FIELDS);self.csv.writeheader()
        self.log=(self.output/(args.mode+'-wire.jsonl')).open('w',encoding='utf-8')
        self.counter=0;self.games=[];self.rooms=[];self.lock=threading.Lock()
        prefix='e15_'+uuid.uuid4().hex[:7]+'_'+args.mode[0]
        # Synthetic fixture password, never logs cookies/password or local DB credentials.
        password=hashlib.sha256((str(args.seed)+prefix).encode()).hexdigest()[:24]
        self.author=Client(args.origin,prefix+'_a',password)
        self.clients=[Client(args.origin,prefix+'_'+str(i),password) for i in range(max(args.loads+[3]))]
        self.quiz=self.author.request('POST','/api/quizzes',{'title':prefix,'visibility':'PUBLIC','questions':[
            {'content':'Experiment question '+str(i+1),'options':dict(A='A',B='B',C='C',D='D'),'correctAnswer':'A','imageRef':None} for i in range(10)]})
        self.fixture={'mode':args.mode,'prefix':prefix,'seed':args.seed,'accountIds':[self.author.user['id']]+[c.user['id'] for c in self.clients],
                      'quizId':self.quiz['id'],'gameIds':self.games,'roomIds':self.rooms}
        self.save()
    def save(self):
        (self.output/(self.args.mode+'-fixtures.json')).write_text(json.dumps(self.fixture,indent=2),encoding='utf-8')
    def command(self,type,kind,target,index=None,payload=None):
        return dict(v=1,kind='COMMAND',requestId=str(uuid.uuid4()),type=type,target=dict(kind=kind,id=target),questionIndex=index,payload=payload or {})
    def record(self,**values):
        with self.lock:
            self.counter+=1;self.csv.writerow(dict(mode=self.args.mode,trial=self.counter,**values));self.file.flush()
    def response(self,client,command):
        reply,ms=client.wire.response(command)
        with self.lock:
            self.log.write(json.dumps(dict(userId=client.user['id'],command=command,response=reply))+'\n');self.log.flush()
        if reply.get('code') in ('SERVICE_UNAVAILABLE','AUTH_UNAVAILABLE','RECEIPT_LIMIT_REACHED','GAME_UNAVAILABLE'):
            raise RuntimeError('System error: '+json.dumps(reply))
        return reply,ms
    @staticmethod
    def accepted(reply):
        if reply.get('kind')!='ACK':raise AssertionError('Expected ACCEPTED: '+json.dumps(reply))
    def begin(self,n):
        group=self.clients[:n]; host=group[0]
        room=host.request('POST','/api/rooms',{'requestId':str(uuid.uuid4()),'config':{
            'quizId':self.quiz['id'],'name':'Experiment '+str(len(self.rooms)+1),'maxPlayers':n,
            'questionDurationMs':10000,'hostParticipation':'PLAYER'}})
        self.rooms.append(room['id']);self.save()
        room=host.request('POST',f"/api/rooms/{room['id']}/open",{'requestId':str(uuid.uuid4()),'revision':room['revision']})
        for client in group:
            client.connect();client.model=None
            type='SUBSCRIBE_ROOM' if client is host else 'JOIN_ROOM'
            payload={} if client is host else dict(roomCode=room['roomCode'],participation='PLAYER')
            reply,_=self.response(client,self.command(type,'ROOM',room['id'],payload=payload));self.accepted(reply)
        room=host.request('GET',f"/api/rooms/{room['id']}")
        reply,_=self.response(host,self.command('START_GAME','ROOM',room['id'],payload=dict(revision=room['revision'],questionCount=10)))
        self.accepted(reply);game=reply['payload']['gameSessionId'];self.games.append(game);self.save()
        for c in group:c.wire.wait(lambda m:m.get('type')=='DECISION_STARTED' and m.get('payload',{}).get('gameSessionId')==game)
        return group,room,game
    def reconnect(self,c,room,game,scenario,n,round):
        # Application-level dropped ACK: oracle retains it, Client model does not apply it.
        c.wire.close();begin=time.perf_counter_ns();c.connect()
        if self.args.mode=='proposed':
            cmd=self.command('RECONNECT','GAME',game);reply,ms=self.response(c,cmd);self.accepted(reply)
            c.model=copy.deepcopy(reply['payload']) # ends at actual harness Client model application.
        else:
            cmd=self.command('SUBSCRIBE_ROOM','ROOM',room['id']);reply,ms=self.response(c,cmd);self.accepted(reply)
        applied=(time.perf_counter_ns()-begin)/1e6
        # REST oracle is read AFTER apply and never copied into baseline Client model.
        expected=c.request('GET',f'/api/games/{game}/snapshot')
        def state(s):return {k:(s or {}).get(k) for k in ('status','phase','questionIndex','revision','player','members')}
        mismatch=int(state(c.model)!=state(expected))
        self.record(scenario=scenario,clients=n,rooms=1,round=round,user_id=c.user['id'],game_id=game,operation=cmd['type'],
                    response_ms=ms,snapshot_response_ms=ms if self.args.mode=='proposed' else '',
                    client_apply_resync_ms=applied if self.args.mode=='proposed' else '',state_mismatch=mismatch,code='ACK')
    def reliability(self,round):
        group,room,game=self.begin(3);spins=[];answers=[]
        for c in group:
            cmd=self.command('USE_SPIN','GAME',game,1);ack,_=self.response(c,cmd);self.accepted(ack);spins.append((cmd,ack))
            # Optional Star uses real selected effect; never randomize or override server Spin.
            if ack['payload']['spinEffect']!='HARDSHIP':
                star,_=self.response(c,self.command('USE_STAR','GAME',game,1));self.accepted(star)
            self.reconnect(c,room,game,'reconnect_decision',3,round)
            replay,ms=self.response(c,cmd)
            recovered=int(replay==ack)
            if self.args.mode=='proposed' and not recovered:raise AssertionError('Original Spin receipt was not replayed')
            if self.args.mode=='baseline' and replay.get('kind')!='ERROR':raise AssertionError('Baseline did not disable receipt replay')
            self.record(scenario='lost_spin_ack',clients=3,rooms=1,round=round,user_id=c.user['id'],game_id=game,operation='USE_SPIN',response_ms=ms,
                        original_recovered=recovered,duplicate_side_effects=int(replay.get('kind')=='ACK' and not recovered),code=replay.get('code','ACK'))
        for c in group:c.wire.wait(lambda m:m.get('type')=='QUESTION_START' and m.get('payload',{}).get('questionIndex')==1)
        # Close earlier Clients before last Answer triggers whole-question scoring/next phase.
        for c in group:
            cmd=self.command('ANSWER','GAME',game,1,{'option':'A'});ack,_=self.response(c,cmd);self.accepted(ack);answers.append((cmd,ack));c.wire.close()
        # Server state must have advanced; wait via real events/read, no clock mutation or random race sleep.
        deadline=time.monotonic()+15
        while group[0].request('GET',f'/api/games/{game}/snapshot')['questionIndex']<2:
            if time.monotonic()>deadline:raise TimeoutError('Scoring did not advance')
            time.sleep(.02) # readiness polling, not race proof.
        for c,(cmd,ack) in zip(group,answers):
            # reconnect() handles an already closed socket too.
            self.reconnect(c,room,game,'reconnect_after_answer',3,round)
            replay,ms=self.response(c,cmd);recovered=int(replay==ack)
            if self.args.mode=='proposed' and not recovered:raise AssertionError('Original Answer receipt was not replayed')
            if self.args.mode=='baseline' and replay.get('kind')!='ERROR':raise AssertionError('Baseline unexpectedly replayed')
            self.record(scenario='lost_answer_ack',clients=3,rooms=1,round=round,user_id=c.user['id'],game_id=game,operation='ANSWER',response_ms=ms,
                        original_recovered=recovered,duplicate_side_effects=int(replay.get('kind')=='ACK' and not recovered),code=replay.get('code','ACK'))
        # Cancel then retry validates terminal retention, not just active-phase replay.
        group[0].request('POST',f'/api/games/{game}/cancel',{'requestId':str(uuid.uuid4())})
        for c,(cmd,ack) in zip(group,answers):
            reply,ms=self.response(c,cmd)
            self.record(scenario='retry_finished',clients=3,rooms=1,round=round,user_id=c.user['id'],game_id=game,operation='ANSWER',response_ms=ms,
                        original_recovered=int(reply==ack),code=reply.get('code','ACK'))
            if self.args.mode=='proposed' and reply!=ack:raise AssertionError('FINISHED replay failed')
        self.verify_terminal(group,room,game,3,round)
    def verify_terminal(self,group,room,game,n,round):
        # Final verification without a second business Cancel in baseline.
        history=group[0].request('GET',f'/api/games/history/{game}');final=history['finalSnapshot']
        waiting=group[0].request('GET',f"/api/rooms/{room['id']}")
        q=next(q for q in history['questions'] if q['questionIndex']==1);answers=q['answers']
        violation=int(final['status']!='FINISHED' or final['hasOfficialWinner'] or waiting['status']!='WAITING')
        violation+=int(len(answers)!=n or len({a['userId'] for a in answers})!=n)
        for c in group:
            own=c.request('GET',f'/api/games/{game}/snapshot')['player'];violation+=int(own['remainingSpins']!=0)
        self.record(scenario='durable_invariants',clients=n,rooms=1,round=round,game_id=game,operation='HISTORY',duplicate_side_effects=max(0,len(answers)-n),invariant_violation=violation,code='PASS' if not violation else 'FAIL')
        if violation:raise AssertionError('Durable invariant failed')
        for c in group:c.wire.close()
        group[0].request('POST',f"/api/rooms/{room['id']}/close",{'requestId':str(uuid.uuid4()),'revision':waiting['revision']})
    def load(self,n,round):
        group,room,game=self.begin(n)
        def batch(type,payload):
            gate=threading.Barrier(n)
            def work(c):
                cmd=self.command(type,'GAME',game,1,payload);gate.wait(timeout=15)
                reply,ms=self.response(c,cmd);self.accepted(reply)
                self.record(scenario='load',clients=n,rooms=1,round=round,user_id=c.user['id'],game_id=game,operation=type,response_ms=ms,code='ACK')
            with concurrent.futures.ThreadPoolExecutor(max_workers=n) as pool:list(pool.map(work,group))
        batch('USE_SPIN',{})
        for c in group:c.wire.wait(lambda m:m.get('type')=='QUESTION_START' and m.get('payload',{}).get('questionIndex')==1)
        batch('ANSWER',{'option':'A'})
        group[0].request('POST',f'/api/games/{game}/cancel',{'requestId':str(uuid.uuid4())})
        self.verify_terminal(group,room,game,n,round)
    def run(self):
        try:
            for round in range(1,self.args.reliability_games+1):
                self.reliability(round);print(f'{self.args.mode}: reliability game {round}',flush=True)
            for n in self.args.loads:
                for round in range(1,self.args.rounds+1):
                    self.load(n,round);print(f'{self.args.mode}: {n} Clients round {round}',flush=True)
            self.fixture['status']='PASS';self.save()
        finally:
            for c in self.clients:
                if c.wire:c.wire.close()
            self.file.close();self.log.close();self.save()

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--origin',default='http://127.0.0.1:8080');p.add_argument('--mode',required=True,choices=['baseline','proposed'])
    p.add_argument('--output',required=True);p.add_argument('--seed',type=int,default=15062026)
    p.add_argument('--loads',type=int,nargs='+',default=[3,5,10,20]);p.add_argument('--rounds',type=int,default=3);p.add_argument('--reliability-games',type=int,default=10)
    args=p.parse_args()
    if min(args.loads)<3 or max(args.loads)>100 or args.rounds<1 or args.reliability_games<0:p.error('Invalid load/rounds')
    Harness(args).run()
