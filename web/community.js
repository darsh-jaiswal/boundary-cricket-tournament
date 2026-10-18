const status = document.querySelector('#status');
const posts = document.querySelector('#posts');
const composer = document.querySelector('#composer');
let clerk, rows = [];
const el = (tag, text, className) => { const node = document.createElement(tag); if(text !== undefined)node.textContent=text; if(className)node.className=className; return node; };
function message(text, error=false) { status.textContent=text; status.className=error?'error':''; }
async function api(method='GET', body, suffix='') {
  const headers = {};
  if(method !== 'GET') {
    const token = await clerk?.session?.getToken();
    if(!token)throw new Error('Sign in to continue');
    headers.Authorization = `Bearer ${token}`;
    headers['Content-Type'] = 'application/json';
  }
  const response = await fetch('/api/community'+suffix,{method,headers,body:body?JSON.stringify(body):undefined,cache:'no-store',signal:AbortSignal.timeout(15000)});
  const data = await response.json();
  if(!response.ok)throw new Error(data.error || 'Request failed');
  return data;
}
async function load() {
  try { rows=await api(); render(); message(`Showing ${rows.length} recent posts`); }
  catch(error) {message(error.message,true);}
}
function render() {
  posts.replaceChildren();
  const query=document.querySelector('#search').value.toLocaleLowerCase();
  const category=new URLSearchParams(location.search).get('category');
  const visible=rows.filter(row=>(!category || row.category===category)
    && [row.title,row.city,row.body,row.category].join(' ').toLocaleLowerCase().includes(query));
  for(const row of visible) {
    const card=el('article');
    card.append(el('div',`${row.category} · ${row.city}`,'meta'),el('h2',row.title),el('p',row.body,'details'),
      el('p',`${row.author} · ${new Date(row.created).toLocaleString()}`,'meta'));
    const replies=el('div');
    const open=el('button','View replies');
    open.onclick=async()=>{
      open.disabled=true;
      try {
        const items=await api('GET',undefined,'?post='+encodeURIComponent(row.id));
        replies.replaceChildren();
        for(const item of items) {
          const reply=el('div',undefined,'reply');reply.append(el('strong',item.author),el('p',item.body,'details'));replies.append(reply);
        }
        if(items.length===0)replies.append(el('p','No replies yet.'));
        if(clerk?.isSignedIn) {
          const form=el('form'), label=el('label','Public reply'), input=el('textarea');
          input.maxLength=1000;input.required=true;label.append(input);
          const submit=el('button','Post reply');submit.type='submit';form.append(label,submit);
          form.onsubmit=async event=>{event.preventDefault();submit.disabled=true;try{await api('POST',{postId:row.id,body:input.value});input.value='';message('Reply published');open.click();}catch(error){message(error.message,true);}finally{submit.disabled=false;}};
          replies.append(form);
        }
        open.textContent='Refresh replies';
      }catch(error){message(error.message,true);}finally{open.disabled=false;}
    };
    card.append(open,replies);
    if(clerk?.user?.id===row.owner_id) {
      const remove=el('button','Remove my post');remove.onclick=async()=>{
        if(!confirm('Remove this post and its replies?'))return;
        remove.disabled=true;try{await api('DELETE',{id:row.id});await load();}catch(error){message(error.message,true);remove.disabled=false;}
      };card.append(remove);
    }
    posts.append(card);
  }
  if(visible.length===0)posts.append(el('p',query?'No posts match your search.':'No community posts yet.'));
}
function script(url,key) { return new Promise((resolve,reject)=>{const tag=document.createElement('script');tag.src=url;tag.crossOrigin='anonymous';if(key)tag.setAttribute('data-clerk-publishable-key',key);tag.onload=resolve;tag.onerror=()=>reject(new Error('Sign-in could not load'));document.head.append(tag);}); }
document.querySelector('#search').oninput=render;
document.querySelector('#refresh').onclick=load;
document.querySelector('#post-form').onsubmit=async event=>{
  event.preventDefault();const form=event.currentTarget, button=form.querySelector('button');button.disabled=true;
  try {await api('POST',Object.fromEntries(new FormData(form)));form.reset();await load();message('Post published');}
  catch(error){message(error.message,true);}finally{button.disabled=false;}
};
try {
  const {publishableKey}=await api('GET',undefined,'?config=1');
  const domain=atob(publishableKey.split('_')[2]).replace(/\$$/,'');
  if(!/^[a-z0-9.-]+$/i.test(domain))throw new Error('Invalid sign-in configuration');
  await script(`https://${domain}/npm/@clerk/ui@1/dist/ui.browser.js`);
  await script(`https://${domain}/npm/@clerk/clerk-js@6/dist/clerk.browser.js`,publishableKey);
  clerk=window.Clerk;await clerk.load({ui:{ClerkUI:window.__internal_ClerkUICtor}});
  const requestedCategory=new URLSearchParams(location.search).get('category');
  const categorySelect=document.querySelector('[name="category"]');
  if([...categorySelect.options].some(option=>option.value===requestedCategory))categorySelect.value=requestedCategory;
  function account() {
    const target=document.querySelector('#account');target.replaceChildren();
    composer.hidden=!clerk.isSignedIn;
    const signIn=document.querySelector('#sign-in');signIn.replaceChildren();
    const phoneGate=document.querySelector('#phone-gate');phoneGate.replaceChildren();
    if(clerk.isSignedIn){clerk.mountUserButton(target);const verified=clerk.user?.phoneNumbers?.some(phone=>phone.verification?.status==='verified');phoneGate.textContent=verified?`Signed in${clerk.user?.primaryPhoneNumber?.phoneNumber?' as '+clerk.user.primaryPhoneNumber.phoneNumber:''} · phone verified. You can post and reply.`:'Add and verify a phone number from your account menu to post or reply.';composer.hidden=!verified;if(!verified)message('Verify a phone number on your account before posting or replying.',true);}
    else {composer.hidden=true;const button=el('button','Sign in / register');button.onclick=()=>clerk.mountSignIn(signIn);target.append(button);phoneGate.append(el('p','A registered, verified phone number is required for player accounts.'));}
    render();
  }
  clerk.addListener(account);account();await load();
}catch(error){await load();message(`Sign-in unavailable: ${error.message}. Public posts remain readable.`,true);}
