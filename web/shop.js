import { account, verifiedPhone } from './account.js';

const $ = selector => document.querySelector(selector);
const el = (tag, text, className) => { const node=document.createElement(tag);if(text!==undefined)node.textContent=text;if(className)node.className=className;return node; };
const money = paise => new Intl.NumberFormat('en-IN',{style:'currency',currency:'INR',maximumFractionDigits:2}).format(paise/100);
let catalog, clerk, bag=[], busy=false;
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
const artwork = {
  tshirt:'<path d="M75 38l30 12h30l30-12 49 37-29 38-20-14v117H75V99l-20 14-29-38z" fill="#147653"/><path d="M105 50q15 25 30 0" fill="none" stroke="#d0ed8c" stroke-width="8"/><path d="M93 163h54" stroke="#d0ed8c" stroke-width="3"/>',
  'full-sleeve':'<path d="M75 38l30 12h30l30-12 26 23 38 127-30 11-34-89v106H75V110l-34 89-30-11L49 61z" fill="#135f44"/><path d="M105 50q15 25 30 0" fill="none" stroke="#d0ed8c" stroke-width="8"/><path d="M78 188h84" stroke="#d0ed8c" stroke-width="2"/>',
  cap:'<path d="M40 147q0-98 85-98t77 98z" fill="#147653"/><path d="M40 147q76-25 162 0l29 23q-93 39-191-23" fill="#0d4e37"/><path d="M125 49v75" stroke="#d0ed8c" stroke-width="2"/>',
  bottle:'<rect x="89" y="25" width="62" height="32" rx="8" fill="#142a24"/><path d="M89 53h62l10 27v138q-40 17-82 0V80z" fill="#147653"/><path d="M89 57h62" stroke="#d0ed8c" stroke-width="4"/><path d="M92 90v101" stroke="#ffffff30" stroke-width="7"/>'
};
function art(category) {
  const node=el('div',undefined,'product-art');
  // These paths are trusted local artwork; catalog text is always inserted with textContent.
  const safeCategory=Object.hasOwn(artwork,category)?category:'tshirt';
  node.innerHTML=`<svg viewBox="0 0 240 250" role="img" aria-label="Boundary ${safeCategory} design">${artwork[safeCategory]}<text x="120" y="133" text-anchor="middle" fill="#d0ed8c" font-family="sans-serif" font-weight="800" font-size="24">B.</text></svg>`;
  node.append(el('span','BOUNDARY ORIGINAL'));return node;
}
function renderProducts() {
  $('#products').replaceChildren();const category=$('#category').value;
  for(const p of catalog.products.filter(p=>category==='all'||p.category===category)) {
    const card=el('article',undefined,'product');card.append(art(p.category),el('h3',p.name),el('p',p.description));
    const select=el('select');select.setAttribute('aria-label',`Material and size for ${p.name}`);
    for(const v of p.variants) {const option=el('option',`${v.material} · ${v.size}`);option.value=v.id;select.append(option);}
    const label=el('label','Material / size');label.append(select);
    const price=el('p',undefined,'price'),availability=el('p'),add=el('button','Add to bag');
    const update=()=>{const v=p.variants.find(v=>v.id===select.value);price.textContent=v.pricePaise>0?money(v.pricePaise):'Coming soon';availability.textContent=v.available&&catalog.salesEnabled?`${v.colour} · In stock`: 'Save to your bag ? stock confirmation pending';add.disabled=busy||!Number.isSafeInteger(v.pricePaise)||v.pricePaise<=0;};
    select.onchange=update;
    add.onclick=()=>{const v=p.variants.find(v=>v.id===select.value);if(!v||!Number.isSafeInteger(v.pricePaise)||v.pricePaise<=0)return;const line=bag.find(i=>i.variantId===v.id);if(line){if(line.quantity>=10)return;line.quantity++;}else{if(bag.length>=10){message('Your bag holds up to ten product options.',true);return;}bag.push({variantId:v.id,quantity:1});}saveBag();message(p.name+' added to your bag.');};
    card.append(label,price,availability,add);update();$('#products').append(card);
  }
}
function renderBag() {
  if(!catalog)return;const target=$('#cart');target.replaceChildren();let total=0,valid=bag.length>0;
  for(const line of bag) {
    const v=variants().find(v=>v.id===line.variantId);const row=el('div',undefined,'cart-row'),description=el('div');
    description.append(el('strong',v?.name||'Unavailable item'),el('div',v?`${v.material} · ${v.size}`:'Remove this item','subtle'));
    if(v) {total+=v.pricePaise*line.quantity;description.append(el('div',money(v.pricePaise*line.quantity)));}
    if(!v?.available) {valid=false;description.append(el('div','Saved to bag ? not reserved; stock confirmation pending','subtle'));}
    const controls=el('div'),qty=el('input');qty.type='number';qty.min='1';qty.max='10';qty.value=line.quantity;qty.setAttribute('aria-label','Quantity for '+(v?.name||'item'));qty.disabled=busy;
    qty.onchange=()=>{const n=Number(qty.value);if(Number.isInteger(n)&&n>=1&&n<=10){line.quantity=n;saveBag();}else qty.value=line.quantity;};
    const remove=el('button','Remove');remove.disabled=busy;remove.onclick=()=>{bag=bag.filter(i=>i!==line);saveBag();};controls.append(qty,remove);row.append(description,controls);target.append(row);
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
$('#category').onchange=renderProducts;$('#refresh-orders').onclick=loadOrders;
$('#checkout').onsubmit=async event=>{
  event.preventDefault();if(busy||!verifiedPhone(clerk)||!catalog?.salesEnabled||!bag.length)return;busy=true;renderBag();renderProducts();
  try {
    let requestId=sessionStorage.getItem('boundary-checkout-request');if(!requestId){requestId=crypto.randomUUID();sessionStorage.setItem('boundary-checkout-request',requestId);}
    const order=await api('POST',{action:'checkout',termsVersion:'2026-10-18',requestId,items:bag,shipping:Object.fromEntries(new FormData(event.currentTarget))});
    await loadOrders();await openPayment(order);
  }catch(error){message(error.message,true,'#payment-status');}
  finally{busy=false;renderBag();renderProducts();}
};
try {
  catalog=await api();renderProducts();renderBag();
  message(catalog.salesEnabled?'Choose your kit. Prices and availability are checked again at checkout.':'Collection preview — prices, stock and delivery are being confirmed. You can save items to your bag. Checkout opens after stock and delivery are confirmed.');
  $('#delivery-note').textContent=catalog.dispatchDays?`Dispatch estimate: ${catalog.dispatchDays} days. For delivery, exchanges or refunds contact store support.`:'Delivery and exchange terms will appear before checkout opens.';
  if(catalog.supportEmail){const link=el('a',catalog.supportEmail);link.href='mailto:'+catalog.supportEmail;$('#support').append('Store support: ',link);}
  await account(catalog.publishableKey,$('#account'),value=>{clerk=value;renderBag();loadOrders();});
}catch(error){message(error.message,true);}
