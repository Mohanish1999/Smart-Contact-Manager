const { JSDOM } = require('jsdom');
const fs = require('fs');
const script = fs.readFileSync(require('path').resolve(__dirname, '../../src/main/resources/static/js/script.js'), 'utf8');
const jquery = fs.readFileSync(require.resolve('jquery'), 'utf8');
let pass = 0, fail = 0;
const check = (n, ok) => { ok ? pass++ : fail++; console.log((ok ? 'PASS  ' : 'FAIL  ') + n); };
const tick = (ms = 30) => new Promise(r => setTimeout(r, ms));

const HTML = `
<meta name="_csrf" content="tok123"><meta name="_csrf_header" content="X-CSRF-TOKEN">
<aside class="sidebar" id="sidebar">
  <a class="item" href="/user/index" data-match="^/user/index$">Dashboard</a>
  <a class="item" href="/user/show-contacts" data-match="^/user/(show-contacts|update-contact|[0-9]+/contact)">Contacts</a>
  <a class="item" href="/user/add-contact" data-match="^/user/add-contact">Add</a>
  <a class="item" href="/user/setting" data-match="^/user/setting">Settings</a>
</aside><div class="sidebar-overlay" id="sidebar-overlay"></div>
<button id="menu-toggle" aria-expanded="false"></button>
<form class="search-box"><input id="global-search" name="q"><div class="suggestions" id="search-suggestions"></div></form>
<div class="password-field"><input type="password" id="pw"><button type="button" class="password-toggle" data-toggle-password="#pw"></button></div>
<div class="photo-upload"><img src="/img/avatar-default.svg" class="avatar"><div><input type="file" id="pi" data-preview data-max-bytes="2097152"></div></div>
<form id="del" action="/user/delete/5" method="post" data-confirm data-confirm-title="Delete?" data-confirm-text="Sure?"><button type="submit">Delete</button></form>
<img id="av" src="/user-photos/x.jpg" data-fallback="/img/avatar-default.svg" data-legacy="/img/old.png" onerror="avatarFallback(this)">
<div class="flash alert-success" id="fl">Saved</div>`;

async function setup(path, fetchImpl, swal) {
  const dom = new JSDOM(`<!doctype html><body>${HTML}</body>`, { runScripts: 'dangerously', pretendToBeVisual: true, url: 'http://localhost:9090' + path });
  const w = dom.window; w.eval(jquery);
  w.fetch = fetchImpl || (() => Promise.resolve({ ok: true, json: () => Promise.resolve([]) }));
  w.Swal = swal; w.URL.createObjectURL = () => 'blob:preview'; w.HTMLFormElement.prototype.reportValidity = () => true;
  if (w.document.readyState === 'loading') { const ready = new Promise(r => w.document.addEventListener('DOMContentLoaded', r)); w.eval(script); await ready; await tick(1); } else { w.eval(script); w.document.dispatchEvent(new w.Event('DOMContentLoaded')); }
  return w;
}
const $ = (w, s) => w.document.querySelector(s);

(async () => {
  // active navigation
  let w = await setup('/user/show-contacts/2');
  check('nav: /user/show-contacts/2 highlights Contacts only', $(w, 'a[href="/user/show-contacts"]').classList.contains('active') && !$(w, 'a[href="/user/index"]').classList.contains('active'));
  w = await setup('/user/17/contact'); check('nav: contact detail page highlights Contacts', $(w, 'a[href="/user/show-contacts"]').classList.contains('active'));
  w = await setup('/user/update-contact/3'); check('nav: edit page highlights Contacts', $(w, 'a[href="/user/show-contacts"]').classList.contains('active'));
  w = await setup('/user/index'); check('nav: dashboard highlights Dashboard only', $(w, 'a[href="/user/index"]').classList.contains('active') && !$(w, 'a[href="/user/setting"]').classList.contains('active'));
  w = await setup('/user/setting'); check('nav: settings highlighted', $(w, 'a[href="/user/setting"]').classList.contains('active'));

  // sidebar
  w = await setup('/user/index');
  $(w, '#menu-toggle').click(); check('sidebar: menu button opens it', w.document.body.classList.contains('sidebar-open') && $(w, '#menu-toggle').getAttribute('aria-expanded') === 'true');
  $(w, '#sidebar-overlay').click(); check('sidebar: overlay click closes it', !w.document.body.classList.contains('sidebar-open'));
  $(w, '#menu-toggle').click(); w.document.dispatchEvent(new w.KeyboardEvent('keydown', { key: 'Escape' })); check('sidebar: Escape closes it', !w.document.body.classList.contains('sidebar-open'));
  $(w, '#menu-toggle').click(); $(w, 'a[href="/user/setting"]').click(); check('sidebar: choosing a link closes it', !w.document.body.classList.contains('sidebar-open'));

  // password toggle
  w = await setup('/user/setting'); const pw = $(w, '#pw'), tg = $(w, '.password-toggle');
  tg.click(); check('password: toggle reveals', pw.type === 'text' && tg.classList.contains('is-visible'));
  tg.click(); check('password: toggle hides again', pw.type === 'password' && !tg.classList.contains('is-visible'));

  // photo preview and validation
  w = await setup('/user/add-contact'); const input = $(w, '#pi'), img = $(w, '.photo-upload img');
  const pick = (file) => { Object.defineProperty(input, 'files', { value: [file], configurable: true }); input.dispatchEvent(new w.Event('change')); };
  pick({ type: 'image/png', size: 1000 }); check('photo: valid image shows a preview', img.src === 'blob:preview' && $(w, '.photo-upload .field-error').textContent === '');
  img.src = '/img/avatar-default.svg';
  pick({ type: 'application/pdf', size: 1000 }); check('photo: non-image refused with a message', /JPG, PNG, GIF or WebP/.test($(w, '.photo-upload .field-error').textContent) && img.src.endsWith('/img/avatar-default.svg'));
  pick({ type: 'image/jpeg', size: 3 * 1024 * 1024 }); check('photo: > 2 MB refused with a message', /too large/.test($(w, '.photo-upload .field-error').textContent) && img.src.endsWith('/img/avatar-default.svg'));
  pick({ type: 'image/svg+xml', size: 100 }); check('photo: SVG refused', /JPG, PNG/.test($(w, '.photo-upload .field-error').textContent));

  // confirm before delete
  let submitted = 0, swalOpts = null, answer = true;
  const swal = { fire: (o) => { swalOpts = o; return Promise.resolve({ isConfirmed: answer }); } };
  w = await setup('/user/show-contacts', null, swal); w.HTMLFormElement.prototype.submit = function () { submitted++; };
  $(w, '#del button').click(); await tick();
  check('delete: asks for confirmation first, with the form\'s own wording', swalOpts && swalOpts.title === 'Delete?' && swalOpts.text === 'Sure?' && swalOpts.icon === 'warning');
  check('delete: confirmed -> the form is submitted (POST)', submitted === 1);
  answer = false; submitted = 0; $(w, '#del button').click(); await tick();
  check('delete: cancelled -> nothing is submitted', submitted === 0);
  w = await setup('/user/show-contacts', null, undefined); w.confirm = () => true; w.HTMLFormElement.prototype.submit = function () { submitted++; }; submitted = 0;
  $(w, '#del button').click(); await tick(); check('delete: still works through window.confirm if SweetAlert is missing', submitted === 1);

  // live search
  const calls = [];
  w = await setup('/user/index', (url, init) => { calls.push([url, init]); return Promise.resolve({ ok: true, json: () => Promise.resolve([{ cid: 7, name: '<b>Rahul</b>', email: 'r@x.com' }, { cid: 8, name: 'Rahul S', phone: '123' }]) }); });
  const search = $(w, '#global-search'), box = $(w, '#search-suggestions');
  search.value = 'r'; search.dispatchEvent(new w.Event('input')); await tick(320);
  check('search: fewer than 2 characters does not call the server', calls.length === 0 && !box.classList.contains('show'));
  search.value = 'Ra/hul'; search.dispatchEvent(new w.Event('input')); await tick(320);
  check('search: calls /search/{query}; "/" cannot break the URL path', calls.length === 1 && calls[0][0] === '/search/Ra%20hul');
  check('search: results link to the contact detail page', box.classList.contains('show') && box.querySelectorAll('a')[0].getAttribute('href') === '/user/7/contact');
  check('search: names are shown as text, never as HTML', box.querySelector('strong').textContent === '<b>Rahul</b>' && !box.querySelector('strong b'));
  check('search: "See all results" goes to the list page with q', /\/user\/show-contacts\?q=Ra%2Fhul$/.test(box.querySelector('a.more').getAttribute('href')));
  search.dispatchEvent(new w.KeyboardEvent('keydown', { key: 'Escape' })); check('search: Escape hides the suggestions', !box.classList.contains('show'));
  w = await setup('/user/index', () => Promise.resolve({ ok: true, json: () => Promise.resolve([]) })); const s2 = $(w, '#global-search');
  s2.value = 'zzz'; s2.dispatchEvent(new w.Event('input')); await tick(320); check('search: empty result shows a friendly message', /No contacts found/.test($(w, '#search-suggestions').textContent));
  w = await setup('/user/index', () => Promise.reject(new Error('offline'))); const s3 = $(w, '#global-search');
  s3.value = 'zzz'; s3.dispatchEvent(new w.Event('input')); await tick(320); check('search: network error is swallowed quietly', !$(w, '#search-suggestions').classList.contains('show'));

  // avatar fallback chain
  w = await setup('/user/index'); const av = $(w, '#av');
  w.avatarFallback(av); check('avatar: first failure tries the legacy /img file', av.src.endsWith('/img/old.png'));
  w.avatarFallback(av); check('avatar: second failure falls back to the default avatar', av.src.endsWith('/img/avatar-default.svg') && av.onerror === null);

  console.log(`\n${pass} passed, ${fail} failed`); process.exit(fail ? 1 : 0);
})();
