"""Small localhost CDP client using Python stdlib; owned headless Chrome only."""
import base64
import json
import os
import socket
import struct
import time
import urllib.request
from urllib.parse import urlparse

class CDP:
    def __init__(self, port=9222):
        with urllib.request.urlopen(f"http://127.0.0.1:{port}/json/version", timeout=3) as response:
            version = json.load(response)
        self.version = version["Browser"]
        url = urlparse(version["webSocketDebuggerUrl"])
        self.socket = socket.create_connection((url.hostname, url.port), timeout=20)
        key = base64.b64encode(os.urandom(16)).decode()
        self.socket.sendall((f"GET {url.path} HTTP/1.1\r\nHost: {url.netloc}\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Key: {key}\r\nSec-WebSocket-Version: 13\r\n\r\n").encode())
        data = b""
        while b"\r\n\r\n" not in data:
            data += self.socket.recv(1)
        if not data.startswith(b"HTTP/1.1 101 "):
            raise RuntimeError("CDP websocket upgrade failed")
        self.counter = 0
        self.errors = []

    def exact(self, size):
        result = b""
        while len(result) < size:
            chunk = self.socket.recv(size-len(result))
            if not chunk: raise RuntimeError("CDP disconnected")
            result += chunk
        return result

    def send(self, data, opcode=1):
        mask = os.urandom(4)
        length = len(data)
        head = bytes([0x80|opcode, 0x80|length]) if length < 126 else bytes([0x80|opcode, 0x80|126])+struct.pack("!H", length) if length < 65536 else bytes([0x80|opcode, 0x80|127])+struct.pack("!Q", length)
        self.socket.sendall(head+mask+bytes(b^mask[i%4] for i,b in enumerate(data)))

    def receive(self):
        result = b""
        while True:
            a,b = self.exact(2)
            length = b&127
            if length==126: length=struct.unpack("!H",self.exact(2))[0]
            elif length==127: length=struct.unpack("!Q",self.exact(8))[0]
            mask=self.exact(4) if b&128 else None
            data=self.exact(length)
            if mask: data=bytes(x^mask[i%4] for i,x in enumerate(data))
            opcode=a&15
            if opcode==9: self.send(data,10);continue
            if opcode==8: raise RuntimeError("CDP closed")
            result+=data
            if a&128: return json.loads(result)

    def call(self, method, params=None, session=None):
        self.counter+=1
        request={"id":self.counter,"method":method,"params":params or {}}
        if session: request["sessionId"]=session
        self.send(json.dumps(request).encode())
        while True:
            reply=self.receive()
            if reply.get("method")=="Runtime.exceptionThrown":
                self.errors.append(reply["params"]["exceptionDetails"].get("text","JS exception"))
            if reply.get("id")==self.counter:
                if "error" in reply: raise RuntimeError(f'{method}: {reply["error"]}')
                return reply.get("result",{})

    def tab(self, origin, context=None):
        context=context or self.call("Target.createBrowserContext")["browserContextId"]
        target=self.call("Target.createTarget",{"url":"about:blank","browserContextId":context})["targetId"]
        session=self.call("Target.attachToTarget",{"targetId":target,"flatten":True})["sessionId"]
        tab=Tab(self,session,context,target,origin)
        self.call("Runtime.enable",session=session)
        self.call("Page.enable",session=session)
        self.call("Page.navigate",{"url":origin},session)
        tab.wait("!!document.querySelector('#login-form') || !!document.querySelector('#logout')")
        return tab

class Tab:
    def __init__(self, cdp, session, context, target, origin):
        self.cdp,self.session,self.context,self.target,self.origin=cdp,session,context,target,origin
    def eval(self, code):
        result=self.cdp.call("Runtime.evaluate",{"expression":code,"awaitPromise":True,"returnByValue":True},self.session)
        if "exceptionDetails" in result:
            raise RuntimeError(result["exceptionDetails"].get("exception",{}).get("description",str(result["exceptionDetails"])))
        return result.get("result",{}).get("value")
    def wait(self, code, timeout=12):
        end=time.monotonic()+timeout
        while time.monotonic()<end:
            if self.eval(code): return
            time.sleep(.04) # Poll DOM readiness, never used as a race assertion.
        raise AssertionError(f"Timed out: {code}; page: {self.eval('document.body.innerText')}")
    def route(self, path, selector):
        self.eval(f"location.hash={json.dumps('/'+path)}")
        self.wait(f"!!document.querySelector({json.dumps(selector)})")
    def fill(self, fields, prefix=""):
        self.eval("(()=>{"+"".join(f"{{const n=document.querySelector({json.dumps(prefix+'[name='+json.dumps(k)+']')});if(!n)throw Error('Missing field');n.value={json.dumps(v)};n.dispatchEvent(new Event('input',{{bubbles:true}}));n.dispatchEvent(new Event('change',{{bubbles:true}}));}}" for k,v in fields.items())+"})()")
    def click(self, selector):
        self.eval(f"(()=>{{const n=document.querySelector({json.dumps(selector)});if(!n||n.disabled)throw Error('Unavailable button: '+{json.dumps(selector)});n.click();}})()")
    def submit(self, selector):
        self.eval(f"document.querySelector({json.dumps(selector)}).requestSubmit()")
    def reload(self):
        self.cdp.call("Page.reload",{},self.session)
        self.wait("!!document.querySelector('#logout') || !!document.querySelector('#login-form')")
    def screenshot(self, path):
        image=self.cdp.call("Page.captureScreenshot",{"captureBeyondViewport":False},self.session)["data"]
        path.write_bytes(base64.b64decode(image))
    def upload(self, selector, path):
        root=self.cdp.call("DOM.getDocument",{},self.session)["root"]["nodeId"]
        node=self.cdp.call("DOM.querySelector",{"nodeId":root,"selector":selector},self.session)["nodeId"]
        self.cdp.call("DOM.setFileInputFiles",{"nodeId":node,"files":[str(path)]},self.session)
