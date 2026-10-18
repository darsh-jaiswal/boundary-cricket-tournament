import { account, verifiedPhone } from './account.js';

const $ = selector => document.querySelector(selector);
const el = (tag, text, className) => { const node=document.createElement(tag);if(text!==undefined)node.textContent=text;if(className)node.className=className;return node; };
const money = paise => new Intl.NumberFormat('en-IN',{style:'currency',currency:'INR',minimumFractionDigits:paise%100?2:0,maximumFractionDigits:2}).format(paise/100);
let catalog, clerk, bag=[], busy=false, dialogProduct=null;
try { const saved=JSON.parse(localStorage.getItem('boundary-bag') || '[]');if(Array.isArray(saved))bag=saved.filter(i=>typeof i.variantId==='string'&&Number.isInteger(i.quantity)&&i.quantity>0&&i.quantity<=10).slice(0,10); }catch{ /* A corrupt local bag starts empty. Server prices remain authoritative. */ }
function message(text,error=false,target='#status') { $(target).textContent=text;$(target).classList.toggle('error',error); }
async function api(method='GET', body, query='') {
  const headers={};
  if(method!=='GET'||query) { const token=await clerk?.session?.getToken();if(!token)throw new Error('Sign in to continue');headers.Authorization=`Bearer ${token}`; }
  if(body)headers['Content-Type']='application/json';
  const response=await fetch('/api/shop'+query,{method,headers,body:body?JSON.stringify(body):undefined,cache:'no-store',signal:AbortSignal.timeout(25000)});
  const data=await response.json();if(!response.ok)throw new Error(data.error||'Store request failed');return data;
}
function variants() { return catalog.products.flatMap(p=>p.variants.map(v=>({...v,name:p.name,category:p.category}))); }
function saveBag() { try{localStorage.setItem('boundary-bag',JSON.stringify(bag));sessionStorage.removeItem('boundary-checkout-request');}catch{}renderBag(); }

// Shelves in the same order as the Boundary app. Bottles are not part of the storefront.
const SHELVES=[['tshirt','Jerseys'],['full-sleeve','Full-Sleeve Jerseys'],['cap','Caps']];
const SHELF_SIZE=5, SAMPLE_NOTE='Sample designs — real stock appears when the shop opens';
const DESIGNS={
  'Boundary Classic':{body:'#147653',stripe:'#d0ed8c',trim:'#0d4e37',pattern:'band'},
  'Green Blaze':{body:'#0f5e4a',stripe:'#f2c94c',trim:'#0a3d30',pattern:'waves'},
  'Royal Rhythm':{body:'#1f4fa8',stripe:'#ff8a3d',trim:'#163a7a',pattern:'sash'},
  'Eternal Whites':{body:'#f7f7f2',stripe:'#147653',trim:'#c9d3c6',pattern:'piping',light:true},
  'Saffron Strike':{body:'#ef8a24',stripe:'#ffffff',trim:'#b9601a',pattern:'waves'},
  'Midnight Pace':{body:'#1b2433',stripe:'#35b07f',trim:'#0f1520',pattern:'sash'},
  'Maroon Heritage':{body:'#7a1f2b',stripe:'#e2a92b',trim:'#53141d',pattern:'band'},
  'Teal Tide':{body:'#0e7c86',stripe:'#b8f0e0',trim:'#0a5960',pattern:'waves'}
};
const HINTS=[['white','Eternal Whites'],['cream','Eternal Whites'],['royal','Royal Rhythm'],['blue','Royal Rhythm'],['navy','Midnight Pace'],['black','Midnight Pace'],
  ['maroon','Maroon Heritage'],['red','Maroon Heritage'],['saffron','Saffron Strike'],['orange','Saffron Strike'],['teal','Teal Tide']];
// Live kit is drawn in the colour it is sold in; unknown colours fall back to the house green.
function designFor(colour='') { const c=String(colour).trim().toLowerCase();return Object.keys(DESIGNS).find(name=>name.toLowerCase()===c)||HINTS.find(([hint])=>c.includes(hint))?.[1]||'Boundary Classic'; }
const SAMPLE_NAMES={tshirt:['Green Blaze Jersey','Royal Rhythm Jersey','Maroon Heritage Jersey','Saffron Strike Jersey','Teal Tide Jersey'],
  'full-sleeve':['Eternal Whites Full Sleeve','Green Blaze Full Sleeve','Royal Rhythm Full Sleeve','Maroon Heritage Full Sleeve','Midnight Pace Full Sleeve'],
  cap:['Midnight Pace Cap','Eternal Whites Cap','Saffron Strike Cap','Maroon Heritage Cap','Teal Tide Cap']};
// Owner-confirmed list prices for each category, shown on samples as "Sample price".
const SAMPLE_PRICES={tshirt:89900,'full-sleeve':120000,cap:39900};
function samples(category) {
  const cap=category==='cap';
  return SAMPLE_NAMES[category].map(name=>{
    const design=Object.keys(DESIGNS).find(d=>name.startsWith(d))||'Boundary Classic',id='sample-'+name.toLowerCase().replaceAll(' ','-');
    return {id,name,category,sample:true,description:'A sample Boundary design that previews the collection. It is not for sale yet.',
      variants:(cap?['Adjustable']:['S','M','L','XL','XXL']).map(size=>({id:`${id}-${size.toLowerCase()}`,material:cap?'Cotton twill':'Performance polyester',size,colour:design,pricePaise:SAMPLE_PRICES[category],available:false}))};
  });
}
function shelves(products) {
  return SHELVES.map(([category,title])=>{
    const items=products.filter(p=>p.category===category&&p.variants.length);
    for(const sample of samples(category)){if(items.length>=SHELF_SIZE)break;items.push(sample);}
    return {category,title,items};
  });
}
const fromPrice=product=>Math.min(...product.variants.map(v=>v.pricePaise).filter(n=>Number.isSafeInteger(n)&&n>0),Infinity);

const SHAPES={shirt:'M37 9Q50 19 63 9L78 14L93 33L82 42L75 36L75 93Q50 96 25 93L25 36L18 42L7 33L22 14Z',
  'full-sleeve':'M37 9Q50 19 63 9L78 14Q85 17 87 26L94 79L84 82L76 40L75 93Q50 96 25 93L24 40L16 82L6 79L13 26Q15 17 22 14Z',
  crown:'M17 66C16 31 36 18 50 18C64 18 84 31 83 66Q50 72 17 66Z',brim:'M13 66Q50 76 87 66Q84 86 50 87Q16 86 13 66Z'};
function pattern(d,cap) {
  if(d.pattern==='waves') {
    let out='';const top=cap?50:58,gap=cap?4:5.5;
    for(let i=0;i<(cap?4:7);i++){const y=top+i*gap;out+=`<path d="M0 ${y}C30 ${y-4} 70 ${y+4} 100 ${y-2}" stroke="${d.stripe}" stroke-width="${(0.8+i*0.35).toFixed(2)}" fill="none" stroke-linecap="round"/>`;}
    return out+(cap?'':`<path d="M80 18Q62 55 74 96" stroke="${d.stripe}" stroke-width="2.2" fill="none" stroke-linecap="round"/>`);
  }
  if(d.pattern==='sash') {
    const [x1,y1,x2,y2]=cap?[20,22,80,70]:[18,12,86,96],o=cap?5:6.5;
    return `<line x1="${x1}" y1="${y1}" x2="${x2}" y2="${y2}" stroke="${d.stripe}" stroke-width="${cap?7:9}"/><line x1="${x1+o}" y1="${y1-o}" x2="${x2+o}" y2="${y2-o}" stroke="${d.trim}" stroke-width="1.2"/>`;
  }
  if(d.pattern==='band') return cap?`<rect y="56" width="100" height="6" fill="${d.stripe}"/>`
    :`<rect y="33" width="100" height="8" fill="${d.stripe}"/><rect y="41" width="100" height="1.2" fill="${d.trim}"/><path d="M0 46H100" stroke="${d.stripe}" stroke-width="1.1"/>`;
  return `<path d="${cap?'M17 64Q50 70 83 64':'M28 38Q31 66 28 94M72 38Q69 66 72 94M37 9.5L22 14.5M63 9.5L78 14.5'}" stroke="${d.stripe}" stroke-width="1.3" fill="none" stroke-linecap="round"/>`;
}
let artCount=0;
// Built only from the constants above (never catalog text), so it is safe to insert as markup.
function artSvg(category,designName) {
  const d=DESIGNS[designName]||DESIGNS['Boundary Classic'],id='art'+(++artCount),cap=category==='cap';
  const shade=`<linearGradient id="${id}s"><stop offset="0" stop-color="#fff" stop-opacity=".13"/><stop offset=".45" stop-color="#fff" stop-opacity="0"/><stop offset="1" stop-color="#000" stop-opacity=".18"/></linearGradient>`;
  const edge='fill="none" stroke="#000" stroke-opacity=".15" stroke-width=".5"';
  const crest=(x,y,r)=>`<circle cx="${x}" cy="${y}" r="${r}" fill="${d.stripe}"/><text x="${x}" y="${(y+r*0.42).toFixed(2)}" text-anchor="middle" font-family="Manrope,sans-serif" font-weight="800" font-size="${(r*1.25).toFixed(2)}" fill="${d.light?'#fff':d.body}">B</text>`;
  const open=`<svg viewBox="0 0 100 100" role="img" aria-label="${designName} design"><defs>${shade}<clipPath id="${id}c"><path d="${cap?SHAPES.crown:SHAPES[category]||SHAPES.shirt}"/></clipPath>${cap?`<clipPath id="${id}b"><path d="${SHAPES.brim}"/></clipPath>`:''}</defs><ellipse cx="50" cy="96.5" rx="28" ry="3.5" fill="#142a24" opacity=".16"/>`;
  if(cap) return open+`<path d="${SHAPES.crown}" fill="${d.body}"/><g clip-path="url(#${id}c)">${pattern(d,true)}<path d="M50 19Q36 30 33 68M50 19Q64 30 67 68" fill="none" stroke="#000" stroke-opacity=".25" stroke-width=".8"/><rect width="100" height="100" fill="url(#${id}s)"/></g>`
    +`<circle cx="50" cy="18.5" r="2.2" fill="${d.trim}"/><circle cx="37" cy="35" r=".9" fill="${d.trim}"/><circle cx="63" cy="35" r=".9" fill="${d.trim}"/>${crest(50,45,6)}<path d="${SHAPES.crown}" ${edge}/>`
    +`<path d="${SHAPES.brim}" fill="${d.light?d.stripe:d.trim}"/><g clip-path="url(#${id}b)"><rect width="100" height="100" fill="url(#${id}s)"/></g><path d="M17 70Q50 89 83 70" fill="none" stroke="${d.light?'#fff':d.stripe}" stroke-opacity=".8" stroke-width=".6" stroke-dasharray="1.6 1.4"/><path d="${SHAPES.brim}" ${edge}/></svg>`;
  const body=SHAPES[category]||SHAPES.shirt,cuffs=category==='full-sleeve'?'M94 79L84 82M6 79L16 82':'M93 33L82 42M7 33L18 42';
  return open+`<path d="${body}" fill="${d.body}"/><g clip-path="url(#${id}c)">${pattern(d,false)}<path d="${cuffs}" stroke="${d.trim}" stroke-width="3.2" stroke-linecap="round"/><rect width="100" height="100" fill="url(#${id}s)"/></g>`
    +`<path d="M37 9Q50 19 63 9" fill="none" stroke="${d.trim}" stroke-width="3.4" stroke-linecap="round"/><path d="M50 15.5V28" stroke="${d.trim}" stroke-width="1.6" stroke-linecap="round"/><circle cx="50" cy="21" r=".8" fill="${d.stripe}"/><circle cx="50" cy="25" r=".8" fill="${d.stripe}"/>`
    +`${crest(38,29,3.6)}<text x="62" y="30.2" text-anchor="middle" font-family="Manrope,sans-serif" font-weight="800" font-size="2.6" letter-spacing=".3" fill="${d.light?d.stripe:'#fff'}" fill-opacity="${d.light?1:.8}">BOUNDARY</text><path d="${body}" ${edge}/></svg>`;
}
function art(category,designName,sample) {
  const node=el('span',undefined,'art'+(DESIGNS[designName]?.light?' light':''));
  node.innerHTML=artSvg(category,designName);
  if(sample)node.append(el('span','Sample','tag'));
  return node;
}
function priceNode(product,paise,from) {
  if(!Number.isSafeInteger(paise)||paise<=0)return el('span','Price coming soon','card-price pending');
  const node=el('span',(from?'From ':'')+money(paise),'card-price');
  if(product.sample)node.append(el('small','Sample price'));
  return node;
}
function card(product) {
  const item=el('li'),button=el('button',undefined,'card-hit'),low=fromPrice(product);
  button.type='button';
  button.setAttribute('aria-label',`${product.name}, ${low<Infinity?money(low):'price coming soon'}${product.sample?', sample design, not for sale':''}`);
  const info=el('span',undefined,'card-info');
  const varied=product.variants.some(v=>v.pricePaise>0&&v.pricePaise!==low);
  info.append(el('span','Boundary','card-brand'),el('span',product.name,'card-name'),priceNode(product,low,varied));
  button.append(art(product.category,designFor(product.variants[0]?.colour),product.sample),info);
  button.onclick=()=>openProduct(product);
  item.append(button);return item;
}
function chevron() { const icon=el('span',undefined,'chevron');icon.setAttribute('aria-hidden','true');icon.innerHTML='<svg viewBox="0 0 24 24"><path d="M9 6l6 6-6 6" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/></svg>';return icon; }
function renderShelves() {
  const root=$('#shelves');root.replaceChildren();
  for(const shelf of shelves(catalog?.products||[])) {
    const section=el('section',undefined,'shelf'),head=el('div',undefined,'shelf-head'),titles=el('div'),title=el('h2',shelf.title);
    section.id='shelf-'+shelf.category;title.id=section.id+'-title';section.setAttribute('aria-labelledby',title.id);
    titles.append(el('p','Boundary Teamwear','shelf-eyebrow'),title);
    const track=el('ul',undefined,'track');track.id=section.id+'-items';
    for(const product of shelf.items)track.append(card(product));
    const toggle=el('button',undefined,'shelf-toggle'),label=el('span','View all');
    toggle.type='button';toggle.setAttribute('aria-expanded','false');toggle.setAttribute('aria-controls',track.id);toggle.append(label,chevron());
    toggle.onclick=()=>{const open=section.classList.toggle('expanded');toggle.setAttribute('aria-expanded',String(open));label.textContent=open?'Show less':'View all';};
    head.append(titles,toggle);section.append(head,track);
    if(shelf.items.some(p=>p.sample))section.append(el('p',SAMPLE_NOTE,'sample-note subtle'));
    root.append(section);
  }
}
function openProduct(product) {
  dialogProduct=product;const select=$('#dialog-variant');select.replaceChildren();
  for(const v of product.variants){const option=el('option',[v.material,v.size,v.colour].filter(Boolean).join(' · '));option.value=v.id;select.append(option);}
  const first=product.variants.find(v=>v.available&&v.pricePaise>0)||product.variants.find(v=>v.pricePaise>0)||product.variants[0];
  if(first)select.value=first.id;
  $('#dialog-title').textContent=product.name;$('#dialog-description').textContent=product.description||'';
  message('',false,'#dialog-note');updateDialog();
  if(!$('#product-dialog').open)$('#product-dialog').showModal();
}
function updateDialog() {
  const product=dialogProduct;if(!product)return;
  const v=product.variants.find(v=>v.id===$('#dialog-variant').value);
  $('#dialog-art').replaceChildren(art(product.category,designFor(v?.colour),product.sample));
  $('#dialog-price').replaceChildren(priceNode(product,v?.pricePaise,false));
  $('#dialog-availability').textContent=product.sample?'Preview only':!v?'':v.available&&catalog?.salesEnabled?`${v.colour} · In stock`
    :v.available?`${v.colour} · In stock · checkout opens soon`:'Save to your bag — stock confirmation pending';
  const add=$('#dialog-add');add.hidden=product.sample;
  add.disabled=busy||product.sample||!v||!Number.isSafeInteger(v.pricePaise)||v.pricePaise<=0;
  if(product.sample)message('Sample design — not for sale yet. Real stock appears when the shop opens.',false,'#dialog-note');
  else if(v&&!(v.pricePaise>0))message('Price coming soon — this option can’t be added to your bag yet.',false,'#dialog-note');
}
function addToBag(p,v) {
  if(!v||!Number.isSafeInteger(v.pricePaise)||v.pricePaise<=0)return;
  const line=bag.find(i=>i.variantId===v.id);
  if(line){if(line.quantity>=10){message('You already have ten of this option in your bag.',true,'#dialog-note');return;}line.quantity++;}
  else{if(bag.length>=10){message('Your bag holds up to ten product options.',true,'#dialog-note');return;}bag.push({variantId:v.id,quantity:1});}
  saveBag();message(p.name+' added to your bag.',false,'#dialog-note');
}
$('#dialog-variant').onchange=()=>{message('',false,'#dialog-note');updateDialog();};
$('#dialog-add').onclick=()=>{const product=dialogProduct;if(!product||product.sample||busy)return;addToBag(product,product.variants.find(v=>v.id===$('#dialog-variant').value));};
$('#product-dialog').onclick=event=>{if(event.target===event.currentTarget)event.currentTarget.close();};
$('#product-dialog').onclose=()=>{dialogProduct=null;};
// The Boundary app hands its bag over as ?bag=variantId:qty,... — only priced options from this catalog are accepted.
function importAppBag() {
  const params=new URLSearchParams(location.search),raw=params.get('bag');if(raw===null)return;
  params.delete('bag');const query=params.toString();history.replaceState(history.state,'',location.pathname+(query?'?'+query:'')+location.hash);
  if(!raw.trim())return;
  const known=new Map(variants().map(v=>[v.id,v])),incoming=[];let skipped=0;
  for(const entry of raw.split(',').slice(0,50)) {
    const match=/^([A-Za-z0-9._-]{1,100}):(\d{1,2})$/.exec(entry.trim()),quantity=match?Number(match[2]):0,v=match&&known.get(match[1]);
    if(!v||!(v.pricePaise>0)||quantity<1||quantity>10||incoming.length>=10||incoming.some(i=>i.variantId===v.id)){if(entry.trim())skipped++;continue;}
    incoming.push({variantId:v.id,quantity});
  }
  // The app's quantity wins for an option already here, so opening the same link twice does not double it.
  for(const line of incoming){const existing=bag.find(i=>i.variantId===line.variantId);if(existing)existing.quantity=line.quantity;else if(bag.length<10)bag.push(line);else skipped++;}
  if(incoming.length)saveBag();
  message(incoming.length?'Your bag from the Boundary app is ready'+(skipped?` · ${skipped} item${skipped===1?'':'s'} could not be added`:'')
    :'Items from the Boundary app are no longer available here.',!incoming.length,'#bag-status');
  $('#bag').scrollIntoView({block:'start'});
}
function renderBag() {
  const count=bag.reduce((n,i)=>n+i.quantity,0);$('#bag-count').textContent=count?String(count):'';$('#bag-count').hidden=!count;
  if(!catalog)return;const target=$('#cart');target.replaceChildren();let total=0,valid=bag.length>0;
  for(const line of bag) {
    const v=variants().find(v=>v.id===line.variantId);const row=el('div',undefined,'cart-row'),description=el('div',undefined,'cart-description');
    description.append(el('strong',v?.name||'Unavailable item'),el('div',v?`${v.material} · ${v.size}`:'Remove this item','subtle'));
    if(v) {total+=v.pricePaise*line.quantity;description.append(el('div',money(v.pricePaise*line.quantity)));}
    if(!v?.available) {valid=false;description.append(el('div','Saved to bag — not reserved; stock confirmation pending','subtle'));}
    const controls=el('div',undefined,'cart-controls'),qty=el('input');qty.type='number';qty.min='1';qty.max='10';qty.value=line.quantity;qty.setAttribute('aria-label','Quantity for '+(v?.name||'item'));qty.disabled=busy;
    qty.onchange=()=>{const n=Number(qty.value);if(Number.isInteger(n)&&n>=1&&n<=10){line.quantity=n;saveBag();}else qty.value=line.quantity;};
    const remove=el('button','Remove');remove.disabled=busy;remove.onclick=()=>{bag=bag.filter(i=>i!==line);saveBag();};controls.append(qty,remove);
    row.append(art(v?.category||'tshirt',designFor(v?.colour),false),description,controls);target.append(row);
  }
  if(!bag.length)target.append(el('p','Your bag is empty. Choose your kit from the collection.'));
  const shipping=catalog.shippingPaise;$('#total').textContent=bag.length?`Total ${money(total+(shipping||0))}${shipping!==null?` · includes ${money(shipping)} delivery`:''}`:'';
  $('#pay').disabled=busy||!valid||!verifiedPhone(clerk)||!catalog.salesEnabled;
  $('#phone-status').textContent=verifiedPhone(clerk)?'Your account phone number is verified.':clerk?.isSignedIn?'Verify a phone number using the account menu above.':'Sign in with a verified phone number to place an order.';
}
async function loadRazorpay() {
  if(window.Razorpay)return;
  await new Promise((resolve,reject)=>{const script=document.createElement('script');script.src='https://checkout.razorpay.com/v1/checkout.js';script.onload=resolve;script.onerror=()=>reject(new Error('Razorpay could not load. Retry from My orders.'));document.head.append(script);});
}
async function openPayment(order) {
  if(order.status==='paid'||order.status==='refunded'){message('Order already '+order.status,false,'#payment-status');return;}
  if(order.status!=='checkout'||!order.razorpayOrderId)throw new Error('This order requires store support review.');
  await loadRazorpay();
  await new Promise(resolve=>{
    const checkout=new window.Razorpay({key:order.keyId,order_id:order.razorpayOrderId,amount:order.amountPaise,currency:'INR',
      name:'Boundary Club Shop',description:'Boundary merchandise',prefill:order.prefill||{},theme:{color:'#147653'},
      modal:{ondismiss:()=>{message('Checkout closed. Your reserved order is saved in My orders.',false,'#payment-status');resolve();}},
      handler:async response=>{try{
        const confirmed=await api('POST',{action:'verify',orderId:order.id,...response});
        if(confirmed.status==='paid'){bag=[];saveBag();message('Payment captured. Order '+order.id+' is confirmed. Dispatch updates come from store support.',false,'#payment-status');}
        else message('Payment is awaiting capture. Use Refresh payment in My orders; do not pay again.',false,'#payment-status');
        await loadOrders();
      }catch(error){message(error.message,true,'#payment-status');}finally{resolve();}}
    });
    checkout.on('payment.failed',()=>message('Payment attempt failed. Razorpay may offer another method; your order remains saved.',true,'#payment-status'));
    checkout.open();
  });
}
async function loadOrders() {
  if(!verifiedPhone(clerk)){$('#order-list').textContent='Sign in and verify a phone number to view your orders.';return;}
  try {
    const rows=await api('GET',undefined,'?orders=1');$('#order-list').replaceChildren();
    for(const order of rows) {
      const card=el('article',undefined,'order');card.append(el('strong',`${order.status.toUpperCase()} · ${money(order.amountPaise)}`),el('p',`${order.id} · ${new Date(order.created).toLocaleString()}`));
      for(const item of order.items)card.append(el('p',`${item.quantity} × ${item.name} · ${item.material} · ${item.size}`));
      const refresh=el('button','Refresh payment');refresh.onclick=async()=>{refresh.disabled=true;try{const updated=await api('GET',undefined,'?order='+encodeURIComponent(order.id));message('Order payment: '+updated.status,false,'#payment-status');await loadOrders();}catch(error){message(error.message,true,'#payment-status');}finally{refresh.disabled=false;}};card.append(refresh);
      if(order.status==='checkout'&&catalog.salesEnabled){const resume=el('button','Resume checkout');resume.disabled=busy;resume.onclick=async()=>{if(busy)return;busy=true;renderBag();try{const current=await api('GET',undefined,'?order='+encodeURIComponent(order.id));await openPayment(current);}catch(error){message(error.message,true,'#payment-status');}finally{busy=false;renderBag();await loadOrders();}};card.append(resume);}
      if(['checkout','review','reserving'].includes(order.status)) { const cancel=el('button','Cancel unpaid order');cancel.onclick=async()=>{cancel.disabled=true;try{await api('POST',{action:'cancel',orderId:order.id});message('Unpaid order cancelled and stock released.',false,'#payment-status');sessionStorage.removeItem('boundary-checkout-request');await loadOrders();}catch(error){message(error.message,true,'#payment-status');}finally{cancel.disabled=false;}};card.append(cancel); }
      if(order.refundStatus)card.append(el('p','Refund: '+order.refundStatus));
      $('#order-list').append(card);
    }
    if(!rows.length)$('#order-list').append(el('p','No merchandise orders yet.'));
  }catch(error){$('#order-list').textContent=error.message;}
}
$('#refresh-orders').onclick=loadOrders;
$('#checkout').onsubmit=async event=>{
  event.preventDefault();if(busy||!verifiedPhone(clerk)||!catalog?.salesEnabled||!bag.length)return;busy=true;renderBag();updateDialog();
  try {
    let requestId=sessionStorage.getItem('boundary-checkout-request');if(!requestId){requestId=crypto.randomUUID();sessionStorage.setItem('boundary-checkout-request',requestId);}
    const order=await api('POST',{action:'checkout',termsVersion:'2026-10-18',requestId,items:bag,shipping:Object.fromEntries(new FormData(event.currentTarget))});
    await loadOrders();await openPayment(order);
  }catch(error){message(error.message,true,'#payment-status');}
  finally{busy=false;renderBag();updateDialog();}
};
renderBag();
try {
  catalog=await api();renderShelves();importAppBag();renderBag();
  message(catalog.salesEnabled?'Choose your kit. Prices and availability are checked again at checkout.':'Collection preview — prices, stock and delivery are being confirmed. You can save items to your bag. Checkout opens after stock and delivery are confirmed.');
  $('#delivery-note').textContent=catalog.dispatchDays?`Dispatch estimate: ${catalog.dispatchDays} days. For delivery, exchanges or refunds contact store support.`:'Delivery and exchange terms will appear before checkout opens.';
  if(catalog.supportEmail){const link=el('a',catalog.supportEmail);link.href='mailto:'+catalog.supportEmail;$('#support').append('Store support: ',link);}
  await account(catalog.publishableKey,$('#account'),value=>{clerk=value;renderBag();loadOrders();});
}catch(error){if(!catalog)renderShelves();message(error.message,true);}
