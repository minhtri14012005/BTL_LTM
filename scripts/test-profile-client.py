"""T12: real test-DB award -> profile/leaderboard, lifecycle and public visibility on LAN origin."""
import argparse, importlib.util, json, os, secrets, subprocess, time, uuid
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('browser',ROOT/'scripts/browser-cdp.py');browser=importlib.util.module_from_spec(spec);spec.loader.exec_module(browser)

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--origin',default='http://192.168.1.234:8088');parser.add_argument('--port',type=int,default=9227);parser.add_argument('--runtime-jar',default=str(ROOT/'target/multigame-0.0.1-SNAPSHOT.jar'));args=parser.parse_args()
    cdp=browser.CDP(args.port);tabs=[];report=dict(status='RUNNING',checks=[],componentChecks=[],sameMachine=True);images=ROOT/'docs/assets/profile-2026-10-17';images.mkdir(parents=True,exist_ok=True)
    prefix='profile_'+secrets.token_hex(4);password=secrets.token_urlsafe(24);users={}
    def passed(text):report['checks'].append(text);print('PASS '+text,flush=True)
    def tab():
        t=cdp.tab(args.origin);tabs.append(t);cdp.call('Emulation.setDeviceMetricsOverride',dict(width=1440,height=1000,deviceScaleFactor=1,mobile=False),t.session);return t
    def req(t,method,path,body=None):
        return t.eval('(async()=>{const c=await fetch("/api/auth/csrf").then(r=>r.json());const r=await fetch('+json.dumps(path)+',{method:'+json.dumps(method)+',headers:{"Content-Type":"application/json",[c.headerName]:c.token},'+('body:'+json.dumps(json.dumps(body))+',' if body is not None else '')+'});return {status:r.status,body:await r.text()};})()')
    def api(t,method,path,body=None):
        r=req(t,method,path,body);assert 200<=r['status']<300,(path,r);return json.loads(r['body']) if r['body'] else None
    def login(t,label):
        t.fill(dict(username=prefix+'_'+label,password=password));t.submit('#login-form');t.wait("document.querySelector('#connection-status')?.dataset.state==='ready'")
    def player(label):
        t=tab();api(t,'POST','/api/auth/register',dict(username=prefix+'_'+label,displayName='<b>Profile A</b>' if label=='a' else 'Profile '+label,password=password));login(t,label);users[label]=api(t,'GET','/api/auth/me');return t
    try:
        host,a,b,c=[player(label) for label in ['host','a','b','c']];uid=users['a']['id'];players=[a,b,c]
        a.route('profile','#profile-page');a.wait("!!document.querySelector('#refresh-profile')")
        zero=api(a,'GET','/api/profiles/me');assert zero['stats']['winRate'] is None and zero['stats']['officialGames']==0
        a.screenshot(images/'empty-1440.png');passed('Own profile has real zero stats and no division by zero')
        report['componentChecks']=a.eval('''(async()=>{
          const {profilePage}=await import('/js/features/profile/page.js');const {leaderboardPage}=await import('/js/features/leaderboard/page.js');const checks=[];
          for(const [name,build,key] of [['profile',profilePage,'profileController'],['leaderboard',leaderboardPage,'leaderboardController']]){
            let aborted=false;const app={user:{id:1},api:{request(method,path,body,{signal}){return new Promise((resolve,reject)=>signal.addEventListener('abort',()=>{aborted=true;reject(Error('aborted'));}));}}};
            const node=build(app);if(!node.querySelector('[aria-busy=true]'))throw Error(name+' loading');app[key].dispose();await Promise.resolve();if(!aborted)throw Error(name+' dispose');checks.push(name+' loading/dispose abort');
            const failing={user:{id:1},api:{request:async()=>{throw Error('Controlled component error');}}};const error=build(failing);await new Promise(resolve=>setTimeout(resolve,0));if(!error.querySelector('[role=alert]'))throw Error(name+' error');failing[key].dispose();checks.push(name+' error');
          }return checks;
        })()''');passed('Separate component fixtures verify loading/error/abort for both features')
        bank=api(host,'POST','/api/quizzes',dict(title=prefix,mode='RIDDLE',visibility='PRIVATE',questions=[dict(content='Question '+str(i),acceptedAnswers=['yes']) for i in range(10)]))
        room=api(host,'POST','/api/rooms',dict(requestId=str(uuid.uuid4()),config=dict(name=prefix,maxPlayers=3,hostParticipation='SPECTATOR',stages=[dict(mode='RIDDLE',quizId=bank['id'],questionCount=10,questionDurationMs=30000)])))
        host.route('room/'+str(room['id']),'#open-room');host.click('#open-room');host.wait("!!document.querySelector('#start-game')")
        for t in players:t.route('join','#join-form');t.fill(dict(code=room['roomCode'],participation='PLAYER'));t.submit('#join-form');t.wait("!!document.querySelector('#waiting-room')")
        host.click('#start-game');host.wait("document.querySelector('#game-page')?.dataset.phase==='INTRO'");game=int(host.eval("location.hash.split('/')[2]"));report['gameId']=game
        for t in players:t.wait("!!document.querySelector('#continue-stage')&&!document.querySelector('#continue-stage').disabled");t.click('#continue-stage')
        for index in range(1,11):
            for t in players:
                t.wait(f'document.querySelector("#game-page")?.dataset.index==="{index}"&&document.querySelector("#game-page")?.dataset.phase==="QUESTION_OPEN"&&!document.querySelector("#answer-text")?.disabled')
                t.fill(dict(answerText='yes'));t.click('#submit-answer');t.wait("!!document.querySelector('#answer-accepted')||!!document.querySelector('#final-summary')||document.querySelector('#game-page')?.dataset.phase!=='QUESTION_OPEN'")
        a.wait("document.querySelector('.award-panel')?.dataset.awardState==='AWARDS_DECIDED'")
        before=api(a,'GET','/api/profiles/me');assert before['stats']['officialGames']==1 and before['stats']['correct']==10 and before['stats']['participated']==10 and before['badges']
        a.route('profile','#profile-page');a.wait("!!document.querySelector('#refresh-profile')");assert a.eval("document.querySelector('#profile-page h1').textContent")== '<b>Profile A</b>' and not a.eval("!!document.querySelector('#profile-page h1 b')")
        a.screenshot(images/'own-1440.png');a.route('profile?tab=awards','#profile-page');a.wait("!!document.querySelector('.earned-awards a')");assert a.eval("document.querySelector('.earned-awards a').getAttribute('href')")==f'#/history/{game}'
        public=api(host,'GET',f'/api/profiles/{uid}');assert set(public)=={'userId','displayName','stats'} and public['stats']==before['stats']
        passed('Real committed awards appear in own stats/badges and paged receipts; public DTO is restricted and name is text')
        ranking=api(a,'GET','/api/leaderboard');own=ranking['own'];assert own and own['stats']==before['stats']
        page=0
        while True:
            data=api(a,'GET',f'/api/leaderboard?page={page}')
            if any(row['userId']==uid for row in data['items']):break
            page+=1;assert page*data['size']<data['totalElements']
        a.route('leaderboard?page='+str(page),'#leaderboard-page');a.wait("!!document.querySelector('.own-standing')");assert int(a.eval("document.querySelector('.own-standing').dataset.userId"))==uid;a.screenshot(images/'leaderboard-1440.png')
        a.click(f'.own-standing a');a.wait("!!document.querySelector('#profile-page #refresh-profile')")
        b.route('profile/'+str(uid),'#profile-page');b.wait("!!document.querySelector('#refresh-profile')");assert not b.eval("!!document.querySelector('.badge-list,.earned-awards')")
        for t in [a,b]:
            cdp.call('Emulation.setDeviceMetricsOverride',dict(width=360,height=900,deviceScaleFactor=1,mobile=False),t.session);assert t.eval('document.documentElement.scrollWidth<=innerWidth')
        b.screenshot(images/'public-360.png');a.route('leaderboard?page='+str(page),'#leaderboard-page');a.wait("!!document.querySelector('.achievement-table')");assert a.eval('document.documentElement.scrollWidth<=innerWidth');a.screenshot(images/'leaderboard-360.png')
        passed('Leaderboard own highlight/off-page own rank, public navigation and 360px responsive layout pass')
        host.route('room/'+str(room['id']),'#waiting-room');host.wait("!!document.querySelector('#start-game')&&!document.querySelector('#start-game').disabled");host.click('#start-game');host.wait("document.querySelector('#game-page')?.dataset.phase==='INTRO'");second=int(host.eval("location.hash.split('/')[2]"))
        assert api(a,'GET','/api/profiles/me')==before
        api(host,'POST',f'/api/games/{second}/cancel',dict(requestId=str(uuid.uuid4())))
        current=api(host,'GET',f'/api/rooms/{room["id"]}');api(host,'POST',f'/api/rooms/{room["id"]}/close',dict(requestId=str(uuid.uuid4()),revision=current['revision']))
        assert api(a,'GET','/api/profiles/me')==before
        a.eval("window.logoutStatus=null;const logoutFetch=window.fetch;window.fetch=function(input,options){const response=logoutFetch.apply(this,arguments);return String(input)==='/api/auth/logout'?response.then(r=>{window.logoutStatus=r.status;return r;}):response;}")
        a.click('#logout');a.wait("window.logoutStatus>=200&&window.logoutStatus<300&&!!document.querySelector('#login-form')")
        origin=a.eval('performance.timeOrigin');cdp.call('Page.reload',{},a.session);a.wait('performance.timeOrigin!=='+str(origin)+"&&!!document.querySelector('#login-form')")
        login(a,'a');a.route('profile','#profile-page');a.wait("!!document.querySelector('#refresh-profile')");assert api(a,'GET','/api/profiles/me')==before
        passed('Same account retains exact committed profile through new Game, cancel, Room close and logout/login')
        name='profileadm_'+secrets.token_hex(5);secret=secrets.token_urlsafe(30)+'!Aa7';env=os.environ.copy();env.update(MULTIGAME_ADMIN_USERNAME=name,MULTIGAME_ADMIN_DISPLAY_NAME='Profile operator',MULTIGAME_ADMIN_PASSWORD=secret)
        cmd=['java','-jar',args.runtime_jar,'--provision-admin','--spring.datasource.url=jdbc:mysql://127.0.0.1:3306/quizz_task2_test?connectionTimeZone=UTC&connectTimeout=3000&socketTimeout=3000','--debug=false','--logging.level.root=WARN','--logging.level.org.springframework=WARN','--logging.level.org.hibernate=WARN']
        provision=subprocess.run(cmd,cwd=ROOT,env=env,text=True,encoding='utf-8',capture_output=True,timeout=60);env.pop('MULTIGAME_ADMIN_PASSWORD');assert provision.returncode==0,provision.stdout+provision.stderr
        admin=tab();admin.fill(dict(username=name,password=secret));admin.submit('#login-form');admin.wait("location.pathname==='/admin'&&!!document.querySelector('#admin-page')")
        receipts=api(host,'GET',f'/api/games/{game}/awards')['awards']
        api(admin,'POST',f'/api/admin/users/{uid}/ban',dict(requestId=str(uuid.uuid4()),expectedVersion=0,minutes=None,reason='T12 visibility proof'))
        assert req(host,'GET',f'/api/profiles/{uid}')['status']==404 and api(host,'GET','/api/leaderboard')['totalElements']==ranking['totalElements']-1
        assert req(a,'GET','/api/profiles/me')['status'] in [401,403]
        api(admin,'POST',f'/api/admin/users/{uid}/unban',dict(requestId=str(uuid.uuid4()),expectedVersion=1,minutes=None,reason='T12 restore visibility'))
        assert api(host,'GET',f'/api/profiles/{uid}')['stats']==before['stats'] and api(host,'GET',f'/api/games/{game}/awards')['awards']==receipts
        origin=a.eval('performance.timeOrigin');cdp.call('Page.reload',{},a.session);a.wait('performance.timeOrigin!=='+str(origin)+"&&!!document.querySelector('#login-form')");login(a,'a');assert api(a,'GET','/api/profiles/me')==before
        passed('Real ADMIN BAN hides public data and blocks own access; UNBAN restores visibility without changing ledger/stats')
        assert not cdp.errors,cdp.errors;report.update(status='PASS',browser=cdp.version)
    except Exception as error:report.update(status='FAIL',error=str(error));raise
    finally:
        (ROOT/'target/t12-profile-client-tests.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
        for t in tabs:cdp.call('Target.disposeBrowserContext',dict(browserContextId=t.context))
        cdp.socket.close();print(json.dumps(dict(status=report['status'],checks=len(report['checks']),components=len(report['componentChecks']))))

if __name__=='__main__':main()
