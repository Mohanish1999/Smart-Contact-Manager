const { JSDOM } = require('jsdom');
const fs = require('fs');
const R = require('path').resolve(__dirname, '../../src/main/resources') + '/';

// Extract the donation card from the real template and drop Thymeleaf attributes
let tpl = fs.readFileSync(R + 'templates/normal/user_dashboard.html', 'utf8');
const card = tpl.slice(tpl.indexOf('<!-- Razorpay donation card'), tpl.indexOf('</section>')).replace(/\sth:[a-z]+="[^"]*"/g, '');
const script = fs.readFileSync(R + 'static/js/script.js', 'utf8');
const jquery = fs.readFileSync(require.resolve('jquery'), 'utf8');

let pass = 0, fail = 0;
const check = (n, ok) => { ok ? pass++ : fail++; console.log((ok ? 'PASS  ' : 'FAIL  ') + n); };
const tick = (ms = 20) => new Promise(r => setTimeout(r, ms));

function setup({ fetchImpl, checkout }) {
  const dom = new JSDOM(`<!doctype html><body>${card}</body>`, { runScripts: 'dangerously', pretendToBeVisual: true, url: 'http://localhost:9090/user/index' });
  const w = dom.window;
  w.eval(jquery);
  w.fetch = fetchImpl;
  w.Swal = { fire: (o) => { w.__swal = o; } };
  w.Razorpay = function (opts) { w.__opts = opts; this.on = (ev, cb) => { w['__on_' + ev.replace('.', '_')] = cb; }; this.open = () => { w.__opened = (w.__opened || 0) + 1; if (checkout) checkout(w, opts); }; };
  w.eval(script);
  return w;
}
const jsonRes = (status, body) => Promise.resolve({ ok: status < 400, status, headers: { get: () => 'application/json' }, json: () => Promise.resolve(body) });
const ORDER = { status: 'created', id: 'order_1', amount: 25000, currency: 'INR', keyId: 'rzp_test_abc', merchantName: 'SCM', name: 'Asha', email: 'a@x.com' };
const $ = (w, s) => w.document.querySelector(s);
const msg = w => $(w, '#donation-status').textContent;

(async () => {
  // ---------- chips + label
  let w = setup({ fetchImpl: () => jsonRes(200, ORDER) });
  w.document.querySelectorAll('.donation-chip')[1].click();
  check('chip click fills amount and highlights chip', $(w, '#payment_field').value === '250' && w.document.querySelectorAll('.donation-chip')[1].classList.contains('selected'));
  check('button label shows chosen amount', /250/.test($(w, '.donate-label').textContent));
  $(w, '#payment_field').value = '75'; w.onDonationAmountTyped();
  check('typing a custom amount clears chip highlight', !w.document.querySelector('.donation-chip.selected') && /75/.test($(w, '.donate-label').textContent));

  // ---------- validation: no amount -> no request
  let calls = 0;
  w = setup({ fetchImpl: () => { calls++; return jsonRes(200, ORDER); } });
  await w.paymentStart();
  check('no amount: warning shown, no server call', calls === 0 && /at least/.test(msg(w)));

  // ---------- success path
  const urls = [];
  w = setup({
    fetchImpl: (url, init) => { urls.push([url, JSON.parse(init.body)]); return url.endsWith('create_order') ? jsonRes(200, ORDER)
      : jsonRes(200, { status: 'paid', msg: 'Payment successful. Thank you for your donation of \u20B9250.00!', duplicate: false }); },
    checkout: (win, o) => setTimeout(() => o.handler({ razorpay_order_id: 'order_1', razorpay_payment_id: 'pay_1', razorpay_signature: 'sig' }), 5)
  });
  $(w, '#payment_field').value = '250';
  const p = w.paymentStart();
  check('while creating order: button disabled + spinner visible', $(w, '#donate-btn').disabled && !$(w, '.spinner-border').classList.contains('d-none'));
  await w.paymentStart();          // double click while in flight
  await p; await tick(60);
  check('double click did not create a second order', urls.filter(u => u[0].endsWith('create_order')).length === 1);
  check('create_order body contains ONLY the amount', JSON.stringify(urls[0][1]) === '{"amount":250}');
  check('checkout opened once with server order (amount in paise, order id, key)', w.__opened === 1 && w.__opts.amount === 25000 && w.__opts.order_id === 'order_1' && w.__opts.key === 'rzp_test_abc' && w.__opts.currency === 'INR');
  check('prefill uses server-provided name/email', w.__opts.prefill.name === 'Asha' && w.__opts.prefill.email === 'a@x.com');
  const upd = urls.find(u => u[0].endsWith('update_order'));
  check('update_order sent razorpay ids + signature and NO status/amount', upd && Object.keys(upd[1]).sort().join() === 'razorpay_order_id,razorpay_payment_id,razorpay_signature');
  check('success message shown from server response', /Payment successful/.test(msg(w)) && $(w, '#donation-status').classList.contains('alert-success'));
  check('Swal success popup shown', w.__swal && w.__swal.icon === 'success');
  check('UI re-enabled after success, amount cleared', !$(w, '#donate-btn').disabled && $(w, '#payment_field').value === '');

  // ---------- server rejects verification (forged signature)
  w = setup({
    fetchImpl: (url) => url.endsWith('create_order') ? jsonRes(200, ORDER) : jsonRes(400, { status: 'error', msg: 'We could not verify this payment.' }),
    checkout: (win, o) => setTimeout(() => o.handler({ razorpay_order_id: 'order_1', razorpay_payment_id: 'pay_1', razorpay_signature: 'forged' }), 5)
  });
  $(w, '#payment_field').value = '250'; await w.paymentStart(); await tick(60);
  check('verification error: danger message, NO success popup, UI re-enabled', $(w, '#donation-status').classList.contains('alert-danger') && /could not verify/.test(msg(w)) && !w.__swal && !$(w, '#donate-btn').disabled);

  // ---------- cancelled checkout
  const reported = [];
  w = setup({
    fetchImpl: (url, init) => { if (url.endsWith('payment_failed')) { reported.push(JSON.parse(init.body)); return jsonRes(200, { status: 'ok' }); } return jsonRes(200, ORDER); },
    checkout: (win, o) => setTimeout(() => o.modal.ondismiss(), 5)
  });
  $(w, '#payment_field').value = '250'; await w.paymentStart(); await tick(60);
  check('cancel: warning shown, reported as cancelled', $(w, '#donation-status').classList.contains('alert-warning') && /cancelled/.test(msg(w)) && reported.length === 1 && reported[0].cancelled === true && reported[0].razorpay_order_id === 'order_1');
  check('cancel: UI re-enabled so the user can try again', !$(w, '#donate-btn').disabled);

  // ---------- failed payment then closed
  const rep2 = [];
  w = setup({
    fetchImpl: (url, init) => { if (url.endsWith('payment_failed')) { rep2.push(JSON.parse(init.body)); return jsonRes(200, { status: 'ok' }); } return jsonRes(200, ORDER); },
    checkout: (win, o) => setTimeout(() => { win.__on_payment_failed({ error: { description: 'Card declined by bank' } }); o.modal.ondismiss(); }, 5)
  });
  $(w, '#payment_field').value = '250'; await w.paymentStart(); await tick(60);
  check('failed payment: danger message with reason, reported as failed (not cancelled)', /Card declined by bank/.test(msg(w)) && $(w, '#donation-status').classList.contains('alert-danger') && rep2[0].cancelled === false && /Card declined/.test(rep2[0].reason));

  // ---------- dismiss after success must not report a failure
  const rep3 = [];
  w = setup({
    fetchImpl: (url, init) => { if (url.endsWith('payment_failed')) { rep3.push(1); return jsonRes(200, {}); } return url.endsWith('create_order') ? jsonRes(200, ORDER) : jsonRes(200, { msg: 'ok', status: 'paid' }); },
    checkout: (win, o) => setTimeout(() => { o.handler({ razorpay_order_id: 'order_1', razorpay_payment_id: 'p', razorpay_signature: 's' }); o.modal.ondismiss(); }, 5)
  });
  $(w, '#payment_field').value = '250'; await w.paymentStart(); await tick(60);
  check('modal closing right after success does not report a failure', rep3.length === 0);

  // ---------- create_order errors
  w = setup({ fetchImpl: () => jsonRes(503, { status: 'error', msg: 'Donations are temporarily unavailable.' }) });
  $(w, '#payment_field').value = '250'; await w.paymentStart();
  check('server error on create_order: message shown, checkout NOT opened, UI re-enabled', /temporarily unavailable/.test(msg(w)) && !w.__opened && !$(w, '#donate-btn').disabled);

  w = setup({ fetchImpl: () => Promise.resolve({ ok: true, status: 200, headers: { get: () => 'text/html' }, json: () => Promise.reject() }) });
  $(w, '#payment_field').value = '250'; await w.paymentStart();
  check('session expired (login page HTML): clear message', /session has expired/.test(msg(w)) && !w.__opened);

  w = setup({ fetchImpl: () => Promise.reject(new Error('Failed to fetch')) });
  $(w, '#payment_field').value = '250'; await w.paymentStart();
  check('network failure: message shown, UI re-enabled', $(w, '#donation-status').classList.contains('alert-danger') && !$(w, '#donate-btn').disabled);

  w = setup({ fetchImpl: () => jsonRes(200, ORDER) }); delete w.Razorpay;
  $(w, '#payment_field').value = '250'; await w.paymentStart();
  check('Razorpay script blocked/not loaded: friendly message', /could not be loaded/.test(msg(w)));

  // ---------- HTML injection safety
  w = setup({ fetchImpl: () => jsonRes(400, { msg: '<img src=x onerror=alert(1)>' }) });
  $(w, '#payment_field').value = '250'; await w.paymentStart();
  check('server text is rendered as text, never as HTML', !$(w, '#donation-status img') && /<img/.test(msg(w)));

  console.log(`\n${pass} passed, ${fail} failed`);
  process.exit(fail ? 1 : 0);
})();
