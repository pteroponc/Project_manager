

const fs=require('node:fs'); const path=require('node:path'); const vm=require('node:vm');
const {architecture,missingArchitecture}=require('./fixtures/architecture.cjs');
const source=fs.readFileSync(path.join(__dirname,'../../main/resources/public/passport-editor.js'),'utf8');
const decode=value=>String(value??'').replaceAll('&lt;','<').replaceAll('&gt;','>').replaceAll('&quot;','"').replaceAll('&#39;',"'").replaceAll('&amp;','&');
function setup(){
  const nodes=new Map();
  function node(){const classes=new Set();return {value:'',textContent:'',_html:'',listeners:{},attrs:{},disabled:false,checked:false,open:false,
    classList:{add:x=>classes.add(x),remove:x=>classes.delete(x),contains:x=>classes.has(x)},
    addEventListener(type,fn){this.listeners[type]=fn;},setAttribute(k,v){this.attrs[k]=v;},removeAttribute(k){delete this.attrs[k];},focus(){},showModal(){this.open=true;},close(){this.open=false;},reportValidity(){return true;},
    get innerHTML(){return this._html;},set innerHTML(value){this._html=value;}};}
  const get=id=>{if(!nodes.has(id)) nodes.set(id,node());return nodes.get(id);};
  const form=get('passport-form'); let inputs=[];
  Object.defineProperty(form,'innerHTML',{get(){return this._html;},set(html){
    this._html=html;inputs=[];
    for(const match of html.matchAll(/<(input|textarea|select|button)\b([^>]*?)(?:>([\s\S]*?)<\/\1>|>)/g)){
      const [,tag,attrs,body='']=match;const input=node();input.name=attrs.match(/\bname="([^"]*)"/)?.[1]??'';input.type=attrs.match(/\btype="([^"]*)"/)?.[1]??tag;
      input.checked=/\bchecked\b/.test(attrs);input.value=decode(attrs.match(/\bvalue="([^"]*)"/)?.[1]??(tag==='textarea'?body:''));
      if(tag==='select') {const choices=[...body.matchAll(/<option([^>]*)>([^<]*)<\/option>/g)];const selected=choices.find(c=>/selected/.test(c[1]))??choices[0];input.value=selected?.[1].match(/value="([^"]*)"/)?.[1]??'';}
      inputs.push(input);
    }
  }});
  form.elements={namedItem:name=>{const items=inputs.filter(i=>i.name===name);return items.length>1?items:items[0];}};
  form.querySelectorAll=selector=>selector==='.field-error'? [...nodes].filter(([k])=>k.startsWith('passport-error-')).map(([,v])=>v)
    :selector==='[aria-invalid]'?inputs.filter(i=>i.attrs['aria-invalid'])
    :selector==='input,select,textarea'?inputs.filter(i=>i.type!=='submit'&&i.type!=='button')
    :inputs.filter(i=>i.name===selector.match(/name="([^"]+)"/)?.[1]&&(!selector.includes(':checked')||i.checked));
  form.querySelector=selector=>selector==='[type="submit"]'?inputs.find(i=>i.type==='submit'):null;
  const calls=[],events={},accepted=[];
  const window={addEventListener:(type,fn)=>{events[type]=fn;}};
  const context=vm.createContext({window,document:{getElementById:get},Number,JSON,encodeURIComponent,
    escapeHtml:v=>String(v??'').replaceAll('&','&amp;').replaceAll('<','&lt;').replaceAll('>','&gt;').replaceAll('"','&quot;'),
    fetchJson:async(url,options)=>{calls.push({url,options});throw Error('offline');}});
  vm.runInContext(fs.readFileSync(path.join(__dirname,'../../main/resources/public/architecture-child-editor.js'),'utf8'),context);
  vm.runInContext(source,context);
  const api=window.PMPassportEditor;
  const start=(data=architecture())=>api.start(data,result=>accepted.push(result));
  const field=name=>form.elements.namedItem(name);
  const set=(name,value)=>{field(name).value=value;form.listeners.input();};
  const save=()=>form.listeners.submit({preventDefault(){}});
  const body=()=>JSON.parse(calls.at(-1).options.body);
  const discard=()=>{get('passport-close').listeners.click();if(get('passport-leave-dialog').open)get('passport-discard').listeners.click();};
  return {api,get,form,start,field,set,save,calls,body,events,accepted,context,discard};
}

module.exports={setup};
