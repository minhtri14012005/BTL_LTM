"""T02 UI checks in owned Chrome: real CSS, keyboard, contrast and viewport layout.

Page-layout cases use explicit DTO/transport fixtures, not real multiplayer proof.
Network/game regression is tested separately by test-seven-modes-client.py --smoke.
"""
import argparse
import importlib.util
import json
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('browser',ROOT/'scripts/browser-cdp.py')
browser=importlib.util.module_from_spec(spec);spec.loader.exec_module(browser)

SETUP=r'''(async()=>{
 const d=await import('/js/shared/dom.js'),ui=await import('/js/shared/ui.js');
 const {icon}=await import('/js/shared/icons.js');
 const {accountPage}=await import('/js/features/auth/page.js');
 const {homePage}=await import('/js/features/lobby/home.js');
 const {roomList}=await import('/js/features/lobby/rooms.js');
 const {roomEditor}=await import('/js/features/room/editor.js');
 const {waitingPage}=await import('/js/features/room/page.js');
 const {questionBankList}=await import('/js/features/questionbank/page.js');
 const {gamePage}=await import('/js/features/game/page.js');
 const {historyDetail}=await import('/js/features/results/history.js');
 const long='Tài khoản / nội dung rất dài '.repeat(4),modes=['QUIZ','SONG','VIETNAMESE_PUZZLE','RIDDLE','CLUES','IMAGE_WORD','ORDERING'];
 const room={id:77,hostUserId:1,name:long,roomCode:'ABCDEFGH2345',status:'WAITING',revision:1,maxPlayers:3,configVersion:2,decisionDurationMs:7000,
   members:[{userId:1,displayName:long,host:true,participation:'SPECTATOR'},{userId:2,displayName:long,participation:'PLAYER'}],
   stages:modes.map((mode,i)=>({orderIndex:i+1,mode,quizId:i+10,ownerUserId:99,title:long,questionCount:1,questionDurationMs:10000}))};
 const snapshot={gameSessionId:88,roomId:77,schemaVersion:1,quizTitleSnapshot:long,status:'ACTIVE',runtimeState:'READY',phase:'QUESTION_OPEN',questionIndex:1,questionCount:10,revision:1,
   serverTimeMs:1000,deadlineEpochMs:31000,player:null,winners:[],hasOfficialWinner:false,results:[],question:{id:4,content:long,options:{A:long,B:long,C:long,D:long}},
   members:[1,2,3].map(userId=>({userId,displayName:long,rank:userId,score:20,totalAnswerTimeMs:1000,participation:'PLAYER',role:'MEMBER'}))};
 const app={user:{id:1,displayName:long},renderVersion:1,rooms:new Map(),games:new Map(),statusLabel:s=>s,connect:()=>{},transport:{status:'offline'},
   api:{request:async(method,path)=>{
     if(path==='/api/system/status')return {server:'UP',database:{status:'UP'}};
     if(path.startsWith('/api/games/history/'))return {startedAtMs:1000,finishedAtMs:2000,finalSnapshot:{...snapshot,status:'FINISHED',endReason:'COMPLETED',hasOfficialWinner:true,winners:[1]},questions:[]};
     if(path.startsWith('/api/games/'))return snapshot;
     if(path.startsWith('/api/rooms/'))return room;
     if(path.startsWith('/api/rooms'))return {items:[room],totalElements:1,page:0,size:12};
     return {items:modes.map((mode,i)=>({id:i+10,ownerUserId:99,title:long,mode,questionCount:10,visibility:'PUBLIC'})),totalElements:7,page:0,size:100};
   }},adoptRoom(next){this.rooms.set(next.id,next);this.roomController?.update();}};
 let pendingResolve;
 const safe=d.button('Giữ lại',()=>ui.setConfirmation(panel,false),{id:'ui-cancel',className:'secondary'});
 const panel=ui.confirmPanel('Xác nhận thao tác với dữ liệu này?');panel.append(safe,d.button('Xác nhận',()=>{},{className:'danger'}));
 const opener=d.button('Mở xác nhận',event=>ui.setConfirmation(panel,true,event.currentTarget),{id:'ui-confirm-open',className:'danger secondary'});
 const field=d.input('ui-name',{required:true});
 const gallery=d.h('section',{id:'ui-gallery'},d.heading('Giao diện dùng chung','Modern Gaming Dashboard · kiểm tra các trạng thái'),
   d.h('div',{className:'card'},d.h('h2',{},'Nút & trạng thái'),d.h('div',{className:'actions'},
     d.button('Primary',()=>{},{id:'ui-primary'}),d.button('Secondary',()=>{},{id:'ui-secondary',className:'secondary'}),
     d.button('Danger',()=>{},{id:'ui-danger',className:'danger'}),d.button('Disabled',()=>{},{disabled:true}),
     d.button('Đang xử lý',()=>{},{disabled:true,'aria-busy':'true'}),
     d.h('button',{type:'button',id:'ui-trash',className:'room-trash','aria-label':'Xóa mục kiểm thử'},icon('trash'))),
     d.field('Tên hiển thị',field,'Nhập tên để tiếp tục.'),opener,panel),
   d.h('div',{className:'two-columns'},ui.emptyState('Chưa có dữ liệu trong trang này.'),ui.errorState('Kết nối chưa khả dụng.',()=>{})),
   d.notice('Thông tin từ Server.'),ui.toast('Đã lưu thay đổi.','success'),d.notice('Kiểm tra thông tin đã nhập.','error'),ui.loadingState());
 const attack='<img src=x onerror=alert(1)>';
 const safeNodes=[d.notice(attack),ui.emptyState(attack),ui.errorState(attack),ui.toast(attack),ui.confirmPanel(attack)];
 const check=(n,c)=>{if(!c)throw Error(n);return n;},checks=[];
 checks.push(check('Every feedback primitive renders untrusted content as text',safeNodes.every(n=>!n.querySelector('img')&&n.textContent.includes(attack))));
 let rejected=false;try{d.h('div',{innerHTML:attack});}catch{rejected=true;}
 checks.push(check('HTML-string escape hatch is rejected by shared DOM helper',rejected));
 checks.push(check('Icons use local SVG geometry with decorative accessibility',icon('trash').getAttribute('aria-hidden')==='true'&&icon('info','Thông tin').getAttribute('role')==='img'));
 const form=d.h('form',{},d.button('Đã khóa',()=>{},{disabled:true}),d.button('Gửi',()=>{}));d.busy(form,true);d.busy(form,false);
 checks.push(check('Busy state restores original validation-disabled buttons',form.firstChild.disabled&&!form.lastChild.disabled&&form.getAttribute('aria-busy')==='false'));
 window.designTest={d,ui,gallery,app,field,checks,async mount(name){
   app.gameController?.dispose();app.roomController?.dispose();app.gameController=null;app.roomController=null;app.renderVersion++;
   const pages={gallery:()=>gallery,login:()=>accountPage(app,false),register:()=>accountPage(app,true),home:()=>homePage(app),rooms:()=>roomList(app),
     editor:()=>roomEditor(app,null),waiting:()=>waitingPage(app,77),catalogue:()=>questionBankList(app),game:()=>gamePage(app,88),history:()=>historyDetail(app,88)};
   const signedIn=!['gallery','login','register'].includes(name);
   document.body.classList.toggle('signed-in',signedIn);
   const side=document.querySelector('#sidebar');side.hidden=!signedIn;
   side.replaceChildren(d.link('M / MultiGame','home','brand'),d.h('nav',{},
     d.link('Tổng quan','home'),d.link('Bộ câu hỏi','quizzes'),d.link('Phòng của tôi','rooms'),d.link('Tham gia phòng','join'),d.link('Lịch sử','history')),
     d.h('div',{className:'account-info'},d.h('strong',{},long),d.h('small',{},'@long_username_for_responsive_test'),d.button('Đăng xuất',()=>{},{className:'secondary'})));
   document.querySelector('#view').replaceChildren(await pages[name]());
 },contrast(){
   const rgb=c=>c.match(/[\d.]+/g).slice(0,3).map(Number).map(v=>{v/=255;return v<=.04045?v/12.92:((v+.055)/1.055)**2.4;});
   const lum=c=>{const r=rgb(c);return .2126*r[0]+.7152*r[1]+.0722*r[2];};
   const ratio=(a,b)=>{const x=lum(a),y=lum(b);return (Math.max(x,y)+.05)/(Math.min(x,y)+.05);};
   const bg=n=>{while(n){const c=getComputedStyle(n).backgroundColor;if(!c.endsWith(', 0)')&&c!=='transparent')return c;n=n.parentElement;}return 'rgb(11,18,30)';};
   const nodes=[...gallery.querySelectorAll('h1,h2,p,input,button:not(:disabled)')];
   const values=nodes.map(n=>({tag:n.tagName,text:n.textContent.slice(0,30),ratio:ratio(getComputedStyle(n).color,bg(n))}));
   return {minimum:Math.min(...values.map(v=>v.ratio)),failed:values.filter(v=>v.ratio<4.5)};
 }};
 await designTest.mount('gallery');return checks;
})()'''

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--origin',default='http://127.0.0.1:8088')
    parser.add_argument('--port',type=int,default=9227)
    args=parser.parse_args();cdp=browser.CDP(args.port);tab=cdp.tab(args.origin)
    report={'status':'RUNNING','checks':[],'layouts':[],'kind':'UI component/DTO fixtures'}
    def check(name,expr):
        assert tab.eval(expr),name
        report['checks'].append(name)
    try:
        report['checks']=tab.eval(SETUP)
        check('Dark color-scheme declared in CSS and HTML',"getComputedStyle(document.documentElement).colorScheme==='dark'&&document.querySelector('meta[name=\"color-scheme\"]').content==='dark'")
        report['contrast']=tab.eval('designTest.contrast()')
        assert not report['contrast']['failed'],report['contrast']
        report['checks'].append('Normal component text meets contrast 4.5:1')
        position=tab.eval("(()=>{const r=document.querySelector('#ui-trash').getBoundingClientRect();return {x:r.x+r.width/2,y:r.y+r.height/2}})()")
        cdp.call('Input.dispatchMouseEvent',{'type':'mouseMoved',**position},tab.session)
        tab.wait("getComputedStyle(document.querySelector('#ui-trash')).backgroundColor==='rgb(56, 30, 44)'")
        check('Icon-button hover keeps its danger surface instead of primary color',"getComputedStyle(document.querySelector('#ui-trash')).backgroundColor==='rgb(56, 30, 44)'")
        tab.eval("designTest.field.checkValidity()")
        check('Native validation exposes linked error and aria-invalid',"designTest.field.getAttribute('aria-invalid')==='true'&&designTest.field.closest('.field').querySelector('[role=alert]').textContent.length>0&&designTest.field.getAttribute('aria-describedby').split(' ').every(id=>document.getElementById(id))")
        tab.eval("designTest.field.value='Player';designTest.field.dispatchEvent(new Event('input',{bubbles:true}))")
        check('Correcting native input clears its error without changing validation rules',"!designTest.field.hasAttribute('aria-invalid')&&designTest.field.closest('.field').querySelector('.field-error').hidden")
        tab.eval("document.querySelector('#ui-primary').focus()")
        cdp.call('Input.dispatchKeyEvent',{'type':'keyDown','key':'Tab','code':'Tab','windowsVirtualKeyCode':9},tab.session)
        cdp.call('Input.dispatchKeyEvent',{'type':'keyUp','key':'Tab','code':'Tab','windowsVirtualKeyCode':9},tab.session)
        check('Keyboard Tab has a visible focus ring',"document.activeElement.id==='ui-secondary'&&parseFloat(getComputedStyle(document.activeElement).outlineWidth)>=3")
        tab.click('#ui-confirm-open')
        check('Confirmation focuses safe action and exposes expanded relationship',"document.activeElement.id==='ui-cancel'&&document.querySelector('#ui-confirm-open').getAttribute('aria-expanded')==='true'")
        cdp.call('Input.dispatchKeyEvent',{'type':'keyDown','key':'Escape','code':'Escape','windowsVirtualKeyCode':27},tab.session)
        check('Escape closes inline confirmation and returns focus',"document.activeElement.id==='ui-confirm-open'&&document.querySelector('#ui-confirm-open').getAttribute('aria-expanded')==='false'&&document.querySelector('.confirm-panel').hidden")
        for width in [360,390,768,1440]:
            cdp.call('Emulation.setDeviceMetricsOverride',{'width':width,'height':1000,'deviceScaleFactor':1,'mobile':False},tab.session)
            for page in ['gallery','login','register','home','rooms','editor','waiting','catalogue','game','history']:
                tab.eval('designTest.mount('+json.dumps(page)+')')
                assert tab.eval('document.documentElement.scrollWidth<=innerWidth'),(width,page,tab.eval('document.documentElement.scrollWidth'))
                report['layouts'].append({'width':width,'page':page,'overflow':False})
                if (width,page) in [(360,'login'),(1440,'home'),(360,'game'),(1440,'gallery')]:
                    tab.screenshot(ROOT/('target/t02-'+page+'-'+str(width)+'.png'))
                if (width,page)==(360,'gallery'):
                    tab.click('#ui-confirm-open')
                    assert tab.eval('document.documentElement.scrollWidth<=innerWidth')
                    tab.screenshot(ROOT/'target/t02-confirm-360.png')
                    tab.click('#ui-cancel')
        report['checks'].append('40 page/viewport fixtures fit without horizontal page overflow at 360–1440px')
        tab.eval("designTest.mount('gallery')")
        cdp.call('Emulation.setEmulatedMedia',{'features':[{'name':'prefers-reduced-motion','value':'reduce'}]},tab.session)
        check('Reduced motion stops decorative spinner/skeleton animation',"[...document.querySelectorAll('.spinner,.skeleton-line')].every(n=>getComputedStyle(n).animationName==='none')")
        cdp.call('Emulation.setEmulatedMedia',{'features':[]},tab.session)
        tab.eval('designTest.app.gameController?.dispose();designTest.app.roomController?.dispose()')
        assert not cdp.errors,cdp.errors
        report['status']='PASS'
    except Exception as error:
        report.update(status='FAIL',error=str(error));raise
    finally:
        cdp.call('Target.closeTarget',{'targetId':tab.target})
        (ROOT/'target/t02-design-system-tests.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
        print(json.dumps({'status':report['status'],'checks':len(report['checks']),'layouts':len(report['layouts'])}))

if __name__=='__main__':main()
