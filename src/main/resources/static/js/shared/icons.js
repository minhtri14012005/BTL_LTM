/** Original stroke icons authored for this project; no CDN/font/HTML injection. */
const paths=Object.freeze({
  home:"m3 10 9-7 9 7M5 9v12h14V9M9 21v-8h6v8",
  trophy:"M8 3h8v6a4 4 0 0 1-8 0V3ZM8 5H4v2a4 4 0 0 0 4 4M16 5h4v2a4 4 0 0 1-4 4M12 13v5M8 21h8M9 18h6v3H9v-3Z",
  medal:"m8 3 4 6 4-6M5 3h4l3 5 3-5h4l-5 8M10 11 5 3M18 16a6 6 0 1 1-12 0 6 6 0 0 1 12 0ZM12 13l2 3-2 3-2-3 2-3Z",
  bolt:"m13 2-9 12h7l-1 8 10-13h-8l1-7Z",
  target:"M22 12a10 10 0 1 1-20 0 10 10 0 0 1 20 0ZM18 12a6 6 0 1 1-12 0 6 6 0 0 1 12 0ZM14 12a2 2 0 1 1-4 0 2 2 0 0 1 4 0Z",
  sparkles:"m12 2 2.5 6.5L21 11l-6.5 2.5L12 20l-2.5-6.5L3 11l6.5-2.5L12 2ZM20 17v5M17.5 19.5h5M4 2v4M2 4h4",
  comeback:"M4 17v-6h6M4 11l5 5 5-9 6 4M20 5v6h-6",
  info:"M22 12a10 10 0 1 1-20 0 10 10 0 0 1 20 0ZM12 8h.01M11 12h1v4h1",
  check:"m5 12 4 4L19 6",
  warning:"M12 3 2 21h20L12 3ZM12 9v5M12 17h.01",
  inbox:"M4 4h16l2 10v6H2v-6L4 4ZM2 14h6l2 3h4l2-3h6",
  trash:"M3 6h18M9 6V4h6v2M5 6l1 14h12l1-14M10 10v6M14 10v6",
  grid:"M3 3h7v7H3V3ZM14 3h7v7h-7V3ZM3 14h7v7H3v-7ZM14 14h7v7h-7v-7Z",
  help:"M9 8a3 3 0 0 1 6 0c0 2-3 2-3 5M12 17h.01M22 12a10 10 0 1 1-20 0 10 10 0 0 1 20 0Z",
  music:"M9 18V5l11-2v13M9 7l11-2M9 18a3 3 0 1 1-3-3c2 0 3 1 3 3ZM20 16a3 3 0 1 1-3-3c2 0 3 1 3 3Z",
  type:"M3 5h12M9 5v15M6 20h6M14 20l4-11 4 11M16 16h4",
  search:"M18 10a8 8 0 1 1-16 0 8 8 0 0 1 16 0Zm-2 6 6 6",
  image:"M3 3h18v18H3V3ZM3 17l5-5 4 4 4-6 5 7M8 7h.01",
  sort:"M6 3v18m-3-3 3 3 3-3M13 5h8M13 12h6M13 19h4"
});
export function icon(name,label=null) {
  if(!Object.hasOwn(paths,name))throw new Error("Unknown icon");
  const svg=document.createElementNS("http://www.w3.org/2000/svg","svg");
  const attributes={viewBox:"0 0 24 24",width:20,height:20,fill:"none",stroke:"currentColor","stroke-width":1.8,"stroke-linecap":"round","stroke-linejoin":"round",class:"icon",focusable:"false"};
  for(const [key,value] of Object.entries(attributes))svg.setAttribute(key,String(value));
  if(label){svg.setAttribute("role","img");svg.setAttribute("aria-label",label);}
  else svg.setAttribute("aria-hidden","true");
  const path=document.createElementNS(svg.namespaceURI,"path");path.setAttribute("d",paths[name]);svg.append(path);
  return svg;
}
