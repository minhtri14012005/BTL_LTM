import {Failure} from "./core.js";

export class Api {
  constructor(onExpired) { this.onExpired=onExpired; this.epoch=0; this.token=null; }
  invalidate() { this.epoch++; this.token=null; }
  async csrf() { if (!this.token) this.token = await this.request("GET", "/api/auth/csrf"); return this.token; }
  async request(method, path, body) {
    const epoch=this.epoch; const headers={}; const mutating=method !== "GET";
    if (mutating) { const csrf=await this.csrf(); headers[csrf.headerName]=csrf.token; }
    const multipart = body instanceof FormData;
    if (body != null && !multipart) headers["Content-Type"]="application/json";
    let response;
    try { response=await fetch(path,{method,headers,credentials:"same-origin",cache:"no-store",signal:AbortSignal.timeout(15000),body:body==null?undefined:multipart?body:JSON.stringify(body)}); }
    catch { throw new Failure(mutating?"REQUEST_UNCERTAIN":"DISCONNECTED", true); }
    if (epoch !== this.epoch) throw new Failure("UNAUTHENTICATED");
    const payload=response.status===204?null:await response.json().catch(() => null);
    if (epoch !== this.epoch) throw new Failure("UNAUTHENTICATED");
    if (!response.ok) {
      if (response.status===401 && payload?.code==="UNAUTHENTICATED") this.onExpired();
      throw new Failure(payload?.code || "SERVICE_UNAVAILABLE",response.status>=500);
    }
    return payload;
  }
}
