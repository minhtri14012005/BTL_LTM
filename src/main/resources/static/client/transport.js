import {Failure, command} from "./core.js";

export class Transport {
  constructor(onEvent,onState) { this.onEvent=onEvent; this.onState=onState; this.pending=new Map(); this.socket=null; this.status="offline"; this.ready=null; }
  open() {
    if (this.socket && [WebSocket.OPEN,WebSocket.CONNECTING].includes(this.socket.readyState)) return this.ready;
    const url=new URL("/ws",location.href);url.protocol=location.protocol==="https:"?"wss:":"ws:";
    const socket=new WebSocket(url);this.socket=socket;this.status="connecting";this.onState(this.status);
    this.ready=new Promise((resolve,reject) => {
      const timeout=setTimeout(() => { if(this.socket===socket && this.status==="connecting") socket.close();reject(new Failure("DISCONNECTED")); },10000);
      socket.onmessage=event => {
        if(this.socket!==socket) return;
        let message;try {message=JSON.parse(event.data);}catch {return;}
        if(message.type==="AUTH_READY") {clearTimeout(timeout);this.status="ready";this.generation=message.payload.connectionGeneration;resolve();this.onState("ready");return;}
        if(message.type==="SESSION_REPLACED") {this.status="replaced";this.onState("replaced");socket.close(4002,"SESSION_REPLACED");return;}
        if(message.kind==="ACK" || message.kind==="ERROR") {
          const item=this.pending.get(message.requestId);if(item) {clearTimeout(item.timeout);this.pending.delete(message.requestId);message.kind==="ACK"?item.resolve(message):item.reject(new Failure(message.code,message.retryable));}
          return;
        }
        this.onEvent(message);
      };
      socket.onclose=event => {
        clearTimeout(timeout);if(this.socket!==socket) return;
        this.status=event.code===4002 || this.status==="replaced"?"replaced":event.code===4001?"expired":"offline";
        this.socket=null;for(const item of this.pending.values()) {clearTimeout(item.timeout);item.reject(new Failure(this.status==="replaced"?"SESSION_REPLACED":"REQUEST_UNCERTAIN",this.status!=="replaced"));}this.pending.clear();
        reject(new Failure("DISCONNECTED"));this.onState(this.status);
      };
      socket.onerror=() => { /* Close carries the finite failure policy. */ };
    });
    return this.ready;
  }
  close() { const socket=this.socket;this.socket=null;this.status="offline";socket?.close();for(const item of this.pending.values()) {clearTimeout(item.timeout);item.reject(new Failure("UNAUTHENTICATED"));}this.pending.clear(); }
  send(frame) {
    if(this.status!=="ready" || !this.socket) return Promise.reject(new Failure(this.status==="replaced"?"SESSION_REPLACED":"DISCONNECTED",this.status!=="replaced"));
    const prior=this.pending.get(frame.requestId);if(prior) return prior.promise;
    let resolve,reject;const promise=new Promise((a,b) => {resolve=a;reject=b;});
    const timeout=setTimeout(() => {this.pending.delete(frame.requestId);reject(new Failure("REQUEST_UNCERTAIN",true));},15000);
    this.pending.set(frame.requestId,{promise,resolve,reject,timeout});
    try {this.socket.send(JSON.stringify(frame));}catch {clearTimeout(timeout);this.pending.delete(frame.requestId);reject(new Failure("REQUEST_UNCERTAIN",true));}
    return promise;
  }
  request(type,id,payload) { return this.send(command(type,id,payload)); }
}
