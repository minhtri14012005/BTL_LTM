export function h(tag, props={}, ...children) {
  const node=document.createElement(tag);
  for (const [key,value] of Object.entries(props)) {
    if(value==null) continue;
    if(key.startsWith("on")) node.addEventListener(key.slice(2),value);
    else if(key in node && !key.startsWith("aria-")) node[key]=value;
    else node.setAttribute(key,String(value));
  }
  for(const child of children.flat(Infinity)) if(child!=null) node.append(child instanceof Node?child:document.createTextNode(String(child)));
  return node;
}
export function field(label, input, help) { return h("label",{className:"field"},h("span",{},label),input,help?h("small",{},help):null); }
export function input(name, props={}) { return h("input",{name,id:name,...props}); }
export function select(name, items, value) { const node=h("select",{name,id:name},items.map(([v,label]) => h("option",{value:v},label)));node.value=value ?? items[0]?.[0];return node; }
export function button(label, fn, props={}) {return h("button",{type:"button",onclick:fn,...props},label);}
export function link(label,path,cls="") {return h("a",{href:`#/${path}`,className:cls},label);}
export function notice(text, kind="info") {return h("p",{className:`notice ${kind}`,role:kind==="error"?"alert":"status"},text);}
export function heading(title, description, action) {return h("div",{className:"page-heading"},h("div",{},h("h1",{},title),h("p",{className:"muted"},description)),action);}
export function values(form) {return Object.fromEntries(new FormData(form));}
export function busy(form, yes) {form.setAttribute("aria-busy",String(yes));form.querySelectorAll("button").forEach(b => {if(yes){if(b.wasDisabled===undefined)b.wasDisabled=b.disabled;b.disabled=true;}else if(b.wasDisabled!==undefined){b.disabled=b.wasDisabled;delete b.wasDisabled;}});}
export function action(app, target, perform, success) {
  let working=false;
  const run=async () => {
    if(working) return;working=true;const user=app.user?.id;
    busy(target,true);target.querySelector(".action-feedback")?.remove();
    try {const result=await perform();if(app.user?.id!==user) return;await success(result);}
    catch(error) {
      if(app.user?.id!==user) return;
      const feedback=h("div",{className:"action-feedback"},notice(error.message,"error"));
      if(error.retryable) feedback.append(button("Thử lại yêu cầu",run,{className:"secondary"}));target.append(feedback);
    } finally {working=false;busy(target,false);}
  };
  return run();
}
