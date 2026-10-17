"""T03 browser UI fixtures; real auth/room smoke lives in test-client.py."""
import argparse
import importlib.util
import json
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('browser',ROOT/'scripts/browser-cdp.py')
browser=importlib.util.module_from_spec(spec);spec.loader.exec_module(browser)

SETUP=r'''(async()=>{
 const {accountPage}=await import('/js/features/auth/page.js');
 const {homePage}=await import('/js/features/lobby/home.js');
 const {roomList}=await import('/js/features/lobby/rooms.js');
 const {Failure}=await import('/js/core/protocol.js');
 const root=document.querySelector('#view');
 const calls=[],app={renderVersion:1,user:null,rooms:new Map(),navigate(path){this.destination=path;},connect(){this.connected=true;},statusLabel:s=>s,
   api:{request(method,path,data){calls.push({method,path,data});return new Promise((resolve,reject)=>{app.resolve=resolve;app.reject=reject;});},invalidate(){},async csrf(){}}};
 window.t03={app,calls,Failure,accountPage,homePage,roomList,root,
   mount(register=false){app.user=null;app.renderVersion++;root.replaceChildren(accountPage(app,register));},
   submit(){document.querySelector('form').dispatchEvent(new Event('submit',{cancelable:true,bubbles:true}));}};
 t03.mount();return true;
})()'''

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--origin',default='http://127.0.0.1:8088');parser.add_argument('--port',type=int,default=9227);args=parser.parse_args()
    cdp=browser.CDP(args.port);tab=cdp.tab(args.origin)
    report={'status':'RUNNING','kind':'UI fixtures, not network smoke','checks':[]}
    def check(name,expr):
        assert tab.eval(expr),name
        report['checks'].append(name)
    try:
        tab.eval(SETUP)
        tab.fill({'username':'player_t03','password':'password123'})
        tab.click('.password-toggle')
        check('Password visibility is accessible and keeps current-password autocomplete',"document.querySelector('#password').type==='text'&&document.querySelector('#password').autocomplete==='current-password'&&document.querySelector('.password-toggle').getAttribute('aria-pressed')==='true'")
        tab.eval('t03.submit();t03.submit()')
        check('Pending login disables controls and suppresses duplicate submission',"t03.calls.length===1&&document.querySelector('form').getAttribute('aria-busy')==='true'&&document.querySelector('[type=submit]').disabled&&document.querySelector('.auth-progress').textContent.length>0")
        tab.eval("t03.app.reject(new t03.Failure('INVALID_CREDENTIALS'))")
        tab.wait("!!document.querySelector('.action-feedback')")
        check('Credentials error uses existing generic policy and receives focus',"document.querySelector('.action-feedback').textContent===new t03.Failure('INVALID_CREDENTIALS').message&&document.activeElement.className==='action-feedback'&&!document.querySelector('[type=submit]').disabled")
        tab.eval('t03.submit()');tab.eval("t03.app.reject(new t03.Failure('SERVICE_UNAVAILABLE',true))")
        tab.wait("!!document.querySelector('.action-feedback')")
        check('Network error is explicit without automatic replay/account lookup',"t03.calls.length===2&&t03.calls.every(c=>c.path==='/api/auth/login')&&document.querySelector('[role=alert]')&&document.querySelector('.auth-progress').textContent===''")
        tab.eval('t03.submit();t03.mount(true);t03.app.resolve({id:9})')
        check('Late login cannot redirect or connect after leaving page',"!t03.app.destination&&!t03.app.connected&&!!document.querySelector('#register-form')")
        tab.fill({'username':'player_t03','displayName':'Player','password':'password123','confirmPassword':'different123'})
        tab.eval('t03.submit()')
        check('Register mismatch stays local and keeps new-password fields',"t03.calls.length===3&&document.querySelector('[role=alert]')&&document.querySelector('#password').autocomplete==='new-password'")
        tab.eval("t03.app.user={id:1,displayName:'<img src=x onerror=alert(1)>'};t03.app.api.request=async(m,p)=>p.includes('system/status')?{server:'UP',database:{status:'UP'}}:{items:[],totalElements:0,size:12};")
        tab.eval('(async()=>t03.root.replaceChildren(await t03.homePage(t03.app)))()')
        check('Empty lobby shows zero real counts and escapes display name',"!!document.querySelector('.empty-state')&&!document.querySelector('img')&&document.querySelector('.lobby-hero h2').textContent.includes('<img')&&document.querySelectorAll('.room-list-card').length===0")
        tab.eval("t03.app.api.request=async()=>({items:['DRAFT','WAITING','ACTIVE','CLOSED'].map((status,i)=>({id:i+1,name:'<svg onload=alert(1)>',status,hostUserId:1,revision:1,maxPlayers:10,members:[{participation:'PLAYER'},{participation:'SPECTATOR'}]})),totalElements:25,size:12});")
        tab.eval('(async()=>t03.root.replaceChildren(await t03.roomList(t03.app)))()')
        check('Room cards reflect API statuses, PLAYER occupancy and guarded delete',"document.querySelectorAll('.room-list-card').length===4&&[...document.querySelectorAll('.room-occupancy')].every(n=>n.textContent.startsWith('1/10'))&&document.querySelector('#delete-room-3').disabled&&!document.querySelector('svg[onload]')&&!!document.querySelector('a[href=\"#/rooms?page=1\"]')")
        for width in [360,390,768,1440]:
            cdp.call('Emulation.setDeviceMetricsOverride',{'width':width,'height':1000,'deviceScaleFactor':1,'mobile':width<500},tab.session)
            assert tab.eval('document.documentElement.scrollWidth<=innerWidth'),width
        report['checks'].append('Room cards fit 360/390/768/1440px including touch viewport')
        assert not cdp.errors,cdp.errors
        report['status']='PASS'
    except Exception as error:
        report.update(status='FAIL',error=str(error));raise
    finally:
        cdp.call('Target.closeTarget',{'targetId':tab.target})
        (ROOT/'target/t03-auth-lobby-tests.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
        print(json.dumps({'status':report['status'],'checks':len(report['checks'])}))

if __name__=='__main__':main()
