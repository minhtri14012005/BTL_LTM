/** One hash listener for the application lifetime. Route rendering belongs to App. */
export class HashRouter {
  constructor(onChange, target=window) {
    this.onChange=onChange;
    this.target=target;
    this.listening=false;
    this.changed=()=>this.onChange();
  }
  start() {
    if(this.listening)return;
    this.target.addEventListener("hashchange",this.changed);
    this.listening=true;
  }
  read() {
    const url=new URL(this.target.location.hash.slice(1)||"/home",this.target.location.origin);
    return {url,parts:url.pathname.split("/").filter(Boolean)};
  }
  navigate(path) {
    if(this.target.location.hash===`#/${path}`)this.onChange();
    else this.target.location.hash=`/${path}`;
  }
  dispose() {
    if(!this.listening)return;
    this.target.removeEventListener("hashchange",this.changed);
    this.listening=false;
  }
}
