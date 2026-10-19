import { account, verifiedGoogle } from './account.js';
import { photoFor } from './store-catalog.js';

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
function variants() { return catalog.products.flatMap(p=>p.variants.map(v=>({...v,name:p.name,category:p.category,productId:p.id}))); }
function saveBag() { try{localStorage.setItem('boundary-bag',JSON.stringify(bag));sessionStorage.removeItem('boundary-checkout-request');}catch{}renderBag(); }

// Shelves in the same order as the Boundary app. Bottles are not part of the storefront.
const SHELVES=[['tshirt','Jerseys'],['full-sleeve','Full-Sleeve Jerseys'],['cap','Caps']];
function shelves(products) {
  return SHELVES.map(([category,title])=>({category,title,
    items:products.filter(p=>photoFor(p.id)&&p.category===category&&p.variants.length)
  })).filter(shelf=>shelf.items.length);
}
const fromPrice=product=>Math.min(...product.variants.map(v=>v.pricePaise).filter(n=>Number.isSafeInteger(n)&&n>0),Infinity);

function art(product) {
  const node=el('span',undefined,'art'),path=photoFor(product?.id);
  if(path) {
    const img=el('img');img.src=path;img.alt=product.name;img.loading='lazy';img.decoding='async';
    img.onerror=()=>{node.replaceChildren(el('span','Photo unavailable','photo-fallback'));};
    node.append(img);
  } else node.append(el('span','Photo unavailable','photo-fallback'));
  return node;
}
function priceNode(product,paise,from) {
  if(!Number.isSafeInteger(paise)||paise<=0)return el('span','Price coming soon','card-price pending');
  const node=el('span',(from?'From ':'')+money(paise),'card-price');
  return node;
}
function card(product) {
  const item=el('li'),button=el('button',undefined,'card-hit'),low=fromPrice(product);
  button.type='button';
  button.setAttribute('aria-label',`${product.name}, ${low<Infinity?money(low):'price coming soon'}`);
  const info=el('span',undefined,'card-info');
  const varied=product.variants.some(v=>v.pricePaise>0&&v.pricePaise!==low);
  info.append(el('span','Boundary','card-brand'),el('span',product.name,'card-name'),priceNode(product,low,varied));
  button.append(art(product),info);
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
  $('#dialog-art').replaceChildren(art(product));
  $('#dialog-price').replaceChildren(priceNode(product,v?.pricePaise,false));
  $('#dialog-availability').textContent=!v?'':v.available&&catalog?.salesEnabled?`${v.colour} · In stock`
    :v.available?`${v.colour} · In stock · checkout opens soon`:'Save to your bag — stock confirmation pending';
  const add=$('#dialog-add');add.hidden=false;
  add.disabled=busy||!v||!Number.isSafeInteger(v.pricePaise)||v.pricePaise<=0;
  if(v&&!(v.pricePaise>0))message('Price coming soon — this option can’t be added to your bag yet.',false,'#dialog-note');
}
function addToBag(p,v) {
  if(!v||!Number.isSafeInteger(v.pricePaise)||v.pricePaise<=0)return;
  const line=bag.find(i=>i.variantId===v.id);
  if(line){if(line.quantity>=10){message('You already have ten of this option in your bag.',true,'#dialog-note');return;}line.quantity++;}
  else{if(bag.length>=10){message('Your bag holds up to ten product options.',true,'#dialog-note');return;}bag.push({variantId:v.id,quantity:1});}
  saveBag();message(p.name+' added to your bag.',false,'#dialog-note');
}
$('#dialog-variant').onchange=()=>{message('',false,'#dialog-note');updateDialog();};
$('#dialog-add').onclick=()=>{const product=dialogProduct;if(!product||busy)return;addToBag(product,product.variants.find(v=>v.id===$('#dialog-variant').value));};
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
    row.append(art(v?{id:v.productId,name:v.name}:null),description,controls);target.append(row);
  }
  if(!bag.length)target.append(el('p','Your bag is empty. Choose your kit from the collection.'));
  const shipping=catalog.shippingPaise;$('#total').textContent=bag.length?`Total ${money(total+(shipping||0))}${shipping!==null?` · includes ${money(shipping)} delivery`:''}`:'';
  $('#pay').disabled=busy||!valid||!verifiedGoogle(clerk)||!catalog.salesEnabled;
  $('#checkout-status').textContent=!catalog.salesEnabled
    ?'Checkout is paused while the store confirms stock, payment setup and delivery details. Items in your bag are saved, not reserved.'
    :!bag.length?'Add an item to your bag to continue.'
    :!valid?'A selected item is unavailable. Update your bag before checkout.'
    :!verifiedGoogle(clerk)?'Continue with Google to unlock checkout.':'Ready to continue to Razorpay.';
  $('#account-status').textContent=verifiedGoogle(clerk)?'Google account connected.':clerk?.isSignedIn?'Connect Google using the account menu above.':'Continue with Google to place an order.';
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
  if(!verifiedGoogle(clerk)){$('#order-list').textContent='Continue with Google to view your orders.';return;}
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
  event.preventDefault();if(busy||!verifiedGoogle(clerk)||!catalog?.salesEnabled||!bag.length)return;busy=true;renderBag();updateDialog();
  try {
    let requestId=sessionStorage.getItem('boundary-checkout-request');if(!requestId){requestId=crypto.randomUUID();sessionStorage.setItem('boundary-checkout-request',requestId);}
    const order=await api('POST',{action:'checkout',termsVersion:'2026-10-19',requestId,items:bag,shipping:Object.fromEntries(new FormData(event.currentTarget))});
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
