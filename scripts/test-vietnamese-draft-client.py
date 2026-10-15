"""Browser checks for draft identity, touch dragging and delayed deadline snapshots.

Transport in this focused fixture is simulated; test-arrangement-client.py --smoke
separately exercises the real HTTP/WebSocket/MySQL lifecycle.
"""
import importlib.util
import argparse
import json
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('browser',ROOT/'scripts/browser-cdp.py')
browser=importlib.util.module_from_spec(spec);spec.loader.exec_module(browser)

SETUP=r'''(async()=>{
 const {gamePage}=await import('/client/game.js');
 const pieces=Array.from({length:12},(_,i)=>({id:'w'+i,text:i===0||i===1?'Tiếng Việt ':i===2?' ':'Từ '+i+' '}));
 const s={gameSessionId:8123,roomId:1,schemaVersion:2,revision:1,status:'ACTIVE',runtimeState:'READY',phase:'QUESTION_OPEN',stageIndex:1,stage:{mode:'VIETNAMESE_PUZZLE'},stages:[{mode:'VIETNAMESE_PUZZLE',title:'Ghép câu'}],questionIndex:1,questionCount:2,readyPlayers:[],serverTimeMs:1000,deadlineEpochMs:61000,player:{userId:1,state:'PLAYING',score:0,remainingSpins:0,starAvailable:false,alreadyAnswered:false},members:[],winners:[],hasOfficialWinner:false,results:[],question:{mode:'VIETNAMESE_PUZZLE',content:'Kiểm tra bản nháp',payload:{pieces}}};
 const t={s,frames:[],gamePage};
 t.app={user:{id:1},games:new Map(),api:{request:async()=>t.s},transport:{status:'ready',send:async f=>{t.frames.push(f);if(f.type==='RECONNECT')return {payload:t.s};await new Promise(r=>setTimeout(r,30));return {...f,kind:'ACK',status:'ACCEPTED',revision:t.s.revision+1,payload:{alreadyAnswered:true,submittedAnswer:{itemIds:f.payload.itemIds}}};}},connect:()=>{}};
 window.vuaTest=t;
 document.body.replaceChildren(await gamePage(t.app,8123));await new Promise(r=>setTimeout(r,0));return true;
})()'''

def main():
 parser=argparse.ArgumentParser();parser.add_argument('--port',type=int,default=9225);parser.add_argument('--origin',default='http://127.0.0.1:8087');args=parser.parse_args()
 cdp=browser.CDP(args.port);t=cdp.tab(args.origin);checks=[];status='FAIL'
 def check(name,expr):
  assert t.eval(expr),name
  checks.append(name)
 try:
  t.eval(SETUP)
  cdp.call('Emulation.setDeviceMetricsOverride',dict(width=320,height=800,deviceScaleFactor=1,mobile=True),t.session)
  t.eval('document.querySelector("#piece-w0").scrollIntoView({block:"center"})')
  pos=t.eval('(()=>{const a=document.querySelector("#piece-w0").getBoundingClientRect(),b=document.querySelector("#sentence-row").getBoundingClientRect();return {x:a.x+a.width/2,y:a.y+a.height/2,tx:b.x+b.width/2,ty:b.y+b.height/2}})()')
  cdp.call('Input.dispatchTouchEvent',dict(type='touchStart',touchPoints=[dict(x=pos['x'],y=pos['y'])]),t.session)
  for step in range(1,6):cdp.call('Input.dispatchTouchEvent',dict(type='touchMove',touchPoints=[dict(x=pos['x']+(pos['tx']-pos['x'])*step/5,y=pos['y']+(pos['ty']-pos['y'])*step/5)]),t.session)
  t.wait("document.querySelector('#sentence-row').classList.contains('drop-active')")
  check('Drag preview follows pointer over the highlighted drop area',f"(()=>{{const g=document.querySelector('.word-drag-ghost')?.getBoundingClientRect();return !!g&&Math.abs(g.x+g.width/2-{pos['tx']})<2&&Math.abs(g.y+g.height/2-{pos['ty']})<2&&document.querySelector('#sentence-row').classList.contains('drop-active')}})()")
  cdp.call('Input.dispatchTouchEvent',dict(type='touchEnd',touchPoints=[]),t.session)
  check('Real touch drag preserves Vietnamese case, accents and spaces',"document.querySelector('[data-item-id=w0] .piece-text')?.textContent==='Tiếng Việt '")
  for i in range(1,12):t.click('#piece-w'+str(i))
  check('320px: long sentence stays in one scrollable row without page overflow',"(()=>{const r=document.querySelector('#sentence-row'),a=[...r.children];return r.scrollWidth>r.clientWidth&&r.scrollLeft>0&&a.every(n=>n.offsetTop===a[0].offsetTop)&&document.documentElement.scrollWidth<=innerWidth})()")
  t.screenshot(ROOT/'target/vietnamese-320px.png')
  check('Draft operations send no Answer',"!vuaTest.frames.some(f=>f.type==='ANSWER')")
  t.eval("(async()=>{vuaTest.app.gameController.dispose();document.body.replaceChildren(await vuaTest.gamePage(vuaTest.app,8123));await new Promise(r=>setTimeout(r,0));})()")
  check('Same game/question remount restores draft',"document.querySelectorAll('[data-item-id]').length===12")
  t.eval("vuaTest.s={...vuaTest.s,revision:2,questionIndex:2};vuaTest.app.gameController.receive({kind:'EVENT',target:{kind:'GAME',id:8123},payload:vuaTest.s})")
  check('Next question cannot inherit previous draft',"document.querySelectorAll('[data-item-id]').length===0")
  t.click('#piece-w0');t.click('#piece-w1');t.click('#undo-arrangement');t.click('#undo-arrangement')
  check('Repeated undo restores two separate identical tiles',"!!document.querySelector('#piece-w0')&&!!document.querySelector('#piece-w1')&&document.querySelector('#undo-arrangement').disabled")
  t.eval("document.querySelector('#piece-w0').focus()")
  cdp.call('Input.dispatchKeyEvent',dict(type='keyDown',key='Enter',code='Enter',text='\r',windowsVirtualKeyCode=13),t.session)
  cdp.call('Input.dispatchKeyEvent',dict(type='keyUp',key='Enter',code='Enter',windowsVirtualKeyCode=13),t.session)
  check('Keyboard Enter appends tile and retains usable focus',"!!document.querySelector('[data-item-id=w0]')&&document.activeElement.matches('#arrangement-answer button')")
  t.eval("vuaTest.s={...vuaTest.s,revision:3,deadlineEpochMs:1050};vuaTest.app.gameController.receive({kind:'EVENT',target:{kind:'GAME',id:8123},payload:vuaTest.s})")
  t.wait("[...document.querySelectorAll('#arrangement-answer button,#submit-answer')].every(b=>b.disabled)")
  check('Expired deadline locks editing even before Server Close event',"document.querySelector('#game-page').dataset.phase==='QUESTION_OPEN'&&!vuaTest.frames.some(f=>f.type==='ANSWER')")
  t.eval("vuaTest.app.gameController.dispose()")
  t.eval(SETUP)
  for i in range(12):t.click('#piece-w'+str(i))
  t.eval("document.querySelector('#submit-answer').click();document.querySelector('#submit-answer').click()")
  t.wait("!!document.querySelector('#answer-accepted')")
  check('Double submit sends one Answer; ACK locks all editing',"vuaTest.frames.filter(f=>f.type==='ANSWER').length===1&&[...document.querySelectorAll('#arrangement-answer button')].every(b=>b.disabled)")
  assert not cdp.errors,cdp.errors
  status='PASS'
 finally:
  t.eval('vuaTest?.app.gameController.dispose()');cdp.call('Target.disposeBrowserContext',dict(browserContextId=t.context))
  (ROOT/'target/vietnamese-draft-tests.json').write_text(json.dumps(dict(status=status,checks=checks,transport='simulated',browser=cdp.version),ensure_ascii=False,indent=2),encoding='utf-8')
 print(json.dumps(dict(passed=len(checks))))

if __name__=='__main__':main()
