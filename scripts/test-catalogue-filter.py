"""Real browser smoke for catalogue hover/touch/keyboard and public demo banks."""
import argparse
import importlib.util
import json
import secrets
import sys
from pathlib import Path

sys.dont_write_bytecode = True
ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('browser', ROOT/'scripts/browser-cdp.py')
browser = importlib.util.module_from_spec(spec)
spec.loader.exec_module(browser)

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--origin',default='http://127.0.0.1:8080')
    parser.add_argument('--port',type=int,default=9225)
    args = parser.parse_args()
    cdp = browser.CDP(args.port)
    report = dict(status='RUNNING',browser=cdp.version,checks=[])
    tab = None
    def passed(name): report['checks'].append(name)
    try:
        tab = cdp.tab(args.origin)
        # A separate browser/account proves PUBLIC access without owner content.
        username = 'filter_'+secrets.token_hex(5)
        password = secrets.token_urlsafe(20)
        tab.route('register','#register-form')
        tab.fill(dict(username=username,displayName='Catalogue smoke reader',password=password,confirmPassword=password))
        tab.submit('#register-form')
        tab.wait("!!document.querySelector('#login-form')")
        tab.fill(dict(username=username,password=password))
        tab.submit('#login-form')
        tab.wait("!!document.querySelector('#logout')")
        tab.route('quizzes?scope=SHARED&mode=CLUES','.mode-filter')
        tab.wait("document.querySelector('.mode-filter-value')?.textContent==='Truy tìm dấu vết'")
        assert not tab.eval("document.querySelector('.mode-filter').open")
        box = tab.eval("(()=>{const r=document.querySelector('.mode-filter-trigger').getBoundingClientRect();return {x:r.x+r.width/2,y:r.y+r.height/2};})()")
        cdp.call('Input.dispatchMouseEvent',dict(type='mouseMoved',**box),tab.session)
        tab.wait("document.querySelector('.mode-filter').open")
        assert tab.eval("document.querySelectorAll('.mode-filter-option').length")==8
        passed('Real mouse hover opens all seven modes plus All')
        tab.eval("Promise.all(document.querySelector('.mode-filter-menu').getAnimations().map(a=>a.finished))")
        tab.screenshot(ROOT/'target/catalogue-filter-desktop.png')
        cdp.call('Input.dispatchMouseEvent',dict(type='mouseMoved',x=5,y=5),tab.session)
        tab.wait("!document.querySelector('.mode-filter').open")
        passed('Mouse leaving closes the menu')
        tab.eval("document.querySelector('.mode-filter-trigger').focus()")
        cdp.call('Input.dispatchKeyEvent',dict(type='keyDown',key='ArrowDown',code='ArrowDown'),tab.session)
        tab.wait("document.querySelector('.mode-filter').open&&document.activeElement.classList.contains('mode-filter-option')")
        cdp.call('Input.dispatchKeyEvent',dict(type='keyDown',key='Escape',code='Escape'),tab.session)
        tab.wait("!document.querySelector('.mode-filter').open&&document.activeElement.matches('summary')")
        passed('Keyboard ArrowDown opens/focuses options; Escape restores trigger')
        # Click selection actually navigates and loads the REST-filtered catalogue.
        tab.click('.mode-filter-trigger')
        tab.click('.mode-filter-option[href*="mode=RIDDLE"]')
        tab.wait("location.hash.includes('scope=SHARED&mode=RIDDLE')&&document.querySelector('.mode-filter-value')?.textContent==='Đố mẹo'")
        tab.click('.catalogue-tab[href*="scope=MINE"]')
        tab.wait("location.hash.includes('scope=MINE&mode=RIDDLE')&&document.querySelector('.mode-filter-value')?.textContent==='Đố mẹo'")
        passed('Selection loads the chosen mode and preserves mode across scope tabs')
        seed = json.loads((ROOT/'data/demo-banks/import-result.json').read_text(encoding='utf-8'))
        for bank in seed['banks']:
            tab.route('quizzes?scope=SHARED&mode='+bank['mode'],'.mode-filter')
            tab.wait(f"!!document.querySelector('a[href=\"#/quiz/{bank['id']}\"]')")
            metadata = tab.eval(f"fetch('/api/quizzes/{bank['id']}').then(r=>r.json())")
            assert metadata['questionCount']==5 and metadata['mode']==bank['mode']
            assert 'questions' not in metadata
            tab.route('quiz/'+str(bank['id']),'.page-heading')
            tab.wait("document.querySelector('.page-heading h1')?.textContent==="+json.dumps(bank['title']))
            assert not tab.eval("!!document.querySelector('.question-preview')")
        passed('Six PUBLIC banks have five questions each; non-owner gets metadata only')
        tab.route('quizzes?scope=SHARED&mode=CLUES','.mode-filter')
        cdp.call('Emulation.setDeviceMetricsOverride',dict(width=390,height=844,deviceScaleFactor=1,mobile=True),tab.session)
        cdp.call('Emulation.setTouchEmulationEnabled',dict(enabled=True,maxTouchPoints=1),tab.session)
        box = tab.eval("(()=>{const r=document.querySelector('summary').getBoundingClientRect();return {x:r.x+r.width/2,y:r.y+r.height/2};})()")
        cdp.call('Input.dispatchTouchEvent',dict(type='touchStart',touchPoints=[box]),tab.session)
        cdp.call('Input.dispatchTouchEvent',dict(type='touchEnd',touchPoints=[]),tab.session)
        tab.wait("document.querySelector('.mode-filter').open")
        assert tab.eval("document.documentElement.scrollWidth<=innerWidth")
        rect = tab.eval("(()=>{const r=document.querySelector('.mode-filter-menu').getBoundingClientRect();return {left:r.left,right:r.right,width:innerWidth};})()")
        assert rect['left']>=0 and rect['right']<=rect['width']
        tab.eval("Promise.all(document.querySelector('.mode-filter-menu').getAnimations().map(a=>a.finished))")
        tab.screenshot(ROOT/'target/catalogue-filter-mobile.png')
        passed('Real touch opens menu at 390px with no horizontal overflow')
        # Independently authenticated Owner can preview real uploaded media.
        author = cdp.tab(args.origin)
        try:
            credentials = json.loads((ROOT/'.env.demo-banks').read_text(encoding='utf-8'))
            author.fill(credentials)
            author.submit('#login-form')
            author.wait("!!document.querySelector('#logout')")
            song = next(b for b in seed['banks'] if b['mode']=='SONG')
            author.route('quiz/'+str(song['id']),'.page-heading')
            author.wait("document.querySelectorAll('video').length===5&&[...document.querySelectorAll('video')].every(v=>v.readyState>=1&&v.duration>0)")
            for i in range(5):
                author.eval(f"(()=>{{const v=document.querySelectorAll('video')[{i}];v.muted=true;return v.play();}})()")
                author.wait(f"document.querySelectorAll('video')[{i}].currentTime>.2")
                author.eval(f"document.querySelectorAll('video')[{i}].pause()")
            passed('Owner previews all five real SONG videos with metadata and browser playback')
            images = next(b for b in seed['banks'] if b['mode']=='IMAGE_WORD')
            author.route('quiz/'+str(images['id']),'.page-heading')
            author.wait("document.querySelectorAll('.question-preview img').length===5&&[...document.querySelectorAll('.question-preview img')].every(i=>i.complete&&i.naturalWidth>0)")
            passed('Owner previews all five real rebus images')
        finally:
            cdp.call('Target.disposeBrowserContext',dict(browserContextId=author.context))
        assert not cdp.errors,cdp.errors
        report['status']='PASS'
    except Exception as error:
        report['status']='FAIL'
        report['error']=str(error)
        raise
    finally:
        if tab: cdp.call('Target.disposeBrowserContext',dict(browserContextId=tab.context))
        (ROOT/'target/catalogue-filter-tests.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
        print(json.dumps(report,ensure_ascii=False,indent=2))

if __name__ == '__main__': main()
