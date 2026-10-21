/* =====================================================================
 * Smart Contact Manager - interface behaviour (vanilla JS, no dependencies)
 * ===================================================================== */
(function () {
    'use strict';

    function csrfHeaders() {
        var headers = { 'Accept': 'application/json' };
        var token = document.querySelector('meta[name="_csrf"]');
        var header = document.querySelector('meta[name="_csrf_header"]');
        if (token && header) headers[header.content] = token.content;
        return headers;
    }

    /* ---- off-canvas sidebar (mobile / tablet) ---- */
    function initSidebar() {
        var toggle = document.getElementById('menu-toggle');
        var overlay = document.getElementById('sidebar-overlay');
        if (!toggle) return;
        function setOpen(open) {
            document.body.classList.toggle('sidebar-open', open);
            toggle.setAttribute('aria-expanded', open ? 'true' : 'false');
        }
        toggle.addEventListener('click', function () { setOpen(!document.body.classList.contains('sidebar-open')); });
        if (overlay) overlay.addEventListener('click', function () { setOpen(false); });
        document.addEventListener('keydown', function (e) { if (e.key === 'Escape') setOpen(false); });
        document.querySelectorAll('.sidebar a.item').forEach(function (a) { a.addEventListener('click', function () { setOpen(false); }); });
    }

    /* ---- highlight the current page in the sidebar ---- */
    function initActiveNav() {
        var path = window.location.pathname;
        document.querySelectorAll('.sidebar a.item[data-match]').forEach(function (a) {
            if (new RegExp(a.getAttribute('data-match')).test(path)) {
                a.classList.add('active');
                a.setAttribute('aria-current', 'page');
            }
        });
    }

    /* ---- show / hide password ---- */
    function initPasswordToggles() {
        document.querySelectorAll('[data-toggle-password]').forEach(function (btn) {
            btn.addEventListener('click', function () {
                var input = document.querySelector(btn.getAttribute('data-toggle-password'));
                if (!input) return;
                var show = input.type === 'password';
                input.type = show ? 'text' : 'password';
                btn.classList.toggle('is-visible', show);
                btn.setAttribute('aria-pressed', show ? 'true' : 'false');
            });
        });
    }

    /* ---- photo preview + client-side type/size check (the server validates again) ---- */
    function initPhotoPreview() {
        document.querySelectorAll('input[type=file][data-preview]').forEach(function (input) {
            var holder = input.closest('.photo-upload');
            var img = holder ? holder.querySelector('img') : null;
            var original = img ? img.src : null;
            var maxBytes = parseInt(input.getAttribute('data-max-bytes') || '2097152', 10);
            var allowed = ['image/jpeg', 'image/png', 'image/gif', 'image/webp'];
            var error = document.createElement('div');
            error.className = 'field-error';
            input.parentNode.appendChild(error);

            input.addEventListener('change', function () {
                error.textContent = '';
                var file = input.files && input.files[0];
                if (!file) { if (img && original) img.src = original; return; }
                if (allowed.indexOf(file.type) === -1) {
                    error.textContent = 'Please choose a JPG, PNG, GIF or WebP image.';
                    input.value = '';
                    if (img && original) img.src = original;
                    return;
                }
                if (file.size > maxBytes) {
                    error.textContent = 'That image is too large. Maximum size is ' + Math.round(maxBytes / 1048576) + ' MB.';
                    input.value = '';
                    if (img && original) img.src = original;
                    return;
                }
                if (img) img.src = URL.createObjectURL(file);
            });
        });
    }

    /* ---- confirmation before destructive POST forms (delete contact / account) ---- */
    function initConfirmForms() {
        document.querySelectorAll('form[data-confirm]').forEach(function (form) {
            form.addEventListener('submit', function (e) {
                e.preventDefault();
                var title = form.getAttribute('data-confirm-title') || 'Are you sure?';
                var text = form.getAttribute('data-confirm-text') || '';
                function proceed() { HTMLFormElement.prototype.submit.call(form); }
                if (!form.checkValidity()) { form.reportValidity(); return; }
                if (window.Swal) {
                    Swal.fire({
                        title: title, text: text, icon: 'warning', showCancelButton: true,
                        confirmButtonText: 'Yes, continue', cancelButtonText: 'Cancel',
                        confirmButtonColor: '#b4443c', cancelButtonColor: '#14213d', reverseButtons: true
                    }).then(function (result) { if (result.isConfirmed) proceed(); });
                } else if (window.confirm(title + '\n' + text)) {
                    proceed();
                }
            });
        });
    }

    /* ---- live search suggestions (GET /search/{query}, only the user's own contacts) ---- */
    function initLiveSearch() {
        var input = document.getElementById('global-search');
        var box = document.getElementById('search-suggestions');
        if (!input || !box) return;
        var timer = null, counter = 0;

        function hide() { box.classList.remove('show'); box.textContent = ''; }
        function line(text, className) { var d = document.createElement('div'); d.className = className; d.textContent = text; return d; }

        function render(list, q) {
            box.textContent = '';
            if (!list.length) {
                box.appendChild(line('No contacts found', 'none'));
            } else {
                list.slice(0, 6).forEach(function (c) {
                    var a = document.createElement('a');
                    a.href = '/user/' + encodeURIComponent(c.cid) + '/contact';
                    var wrap = document.createElement('span');
                    var name = document.createElement('strong'); name.textContent = c.name || 'Unnamed';
                    var sub = document.createElement('small'); sub.textContent = c.email || c.phone || '';
                    wrap.appendChild(name); wrap.appendChild(sub); a.appendChild(wrap);
                    box.appendChild(a);
                });
                var more = document.createElement('a');
                more.className = 'more';
                more.href = '/user/show-contacts?q=' + encodeURIComponent(q);
                more.textContent = 'See all results';
                box.appendChild(more);
            }
            box.classList.add('show');
        }

        input.addEventListener('input', function () {
            clearTimeout(timer);
            var q = input.value.trim();
            if (q.length < 2) { hide(); return; }
            timer = setTimeout(function () {
                var ticket = ++counter;
                // "/" and "\" cannot travel inside a URL path segment, so treat them as spaces.
                fetch('/search/' + encodeURIComponent(q.replace(/[\/\\]/g, ' ')), { headers: csrfHeaders(), credentials: 'same-origin' })
                    .then(function (r) { return r.ok ? r.json() : []; })
                    .then(function (list) { if (ticket === counter) render(Array.isArray(list) ? list : [], q); })
                    .catch(hide);
            }, 250);
        });
        document.addEventListener('click', function (e) { if (!box.contains(e.target) && e.target !== input) hide(); });
        input.addEventListener('keydown', function (e) { if (e.key === 'Escape') hide(); });
    }

    /* ---- success messages fade away on their own ---- */
    function initFlash() {
        document.querySelectorAll('.flash.alert-success').forEach(function (el) {
            setTimeout(function () { el.style.transition = 'opacity .5s'; el.style.opacity = '0'; setTimeout(function () { el.remove(); }, 600); }, 6000);
        });
    }

    document.addEventListener('DOMContentLoaded', function () {
        initSidebar();
        initActiveNav();
        initPasswordToggles();
        initPhotoPreview();
        initConfirmForms();
        initLiveSearch();
        initFlash();
    });
})();

/* ---- avatar fallback: uploaded file -> legacy /img file -> default avatar ---- */
function avatarFallback(img) {
    var legacy = img.getAttribute('data-legacy');
    if (legacy && !img.getAttribute('data-tried-legacy')) {
        img.setAttribute('data-tried-legacy', '1');
        img.src = legacy;
        return;
    }
    img.onerror = null;
    img.src = img.getAttribute('data-fallback') || '/img/avatar-default.svg';
}

/* =====================================================================
 * Razorpay donation flow
 *   1. POST /user/create_order  -> server creates the order (only the amount is sent)
 *   2. Razorpay Checkout opens with the server-created order
 *   3. POST /user/update_order  -> server verifies the signature and marks the order PAID
 * The browser never decides whether a payment succeeded; only the server's answer counts.
 * ===================================================================== */
let donationInFlight = false;

function formatMoney(paise, currency) {
    try {
        return new Intl.NumberFormat('en-IN', { style: 'currency', currency: currency || 'INR' }).format(paise / 100);
    } catch (e) {
        return (paise / 100).toFixed(2) + ' ' + currency;
    }
}

function donationAmountValue() {
    const input = document.getElementById('payment_field');
    const value = input ? Number(input.value) : NaN;
    return Number.isFinite(value) && value >= 1 ? value : null;
}

function updateDonateLabel() {
    const label = document.querySelector('#donate-btn .donate-label');
    if (!label || donationInFlight) return;
    const amount = donationAmountValue();
    label.textContent = amount ? 'Donate \u20B9' + amount.toLocaleString('en-IN') + ' Now' : 'Donate Now';
}

function selectDonationAmount(amount, el) {
    const input = document.getElementById('payment_field');
    if (!input || donationInFlight) return;
    input.value = amount;
    document.querySelectorAll('.donation-chip').forEach(function (chip) { chip.classList.remove('selected'); });
    if (el) el.classList.add('selected');
    updateDonateLabel();
}

function onDonationAmountTyped() {
    document.querySelectorAll('.donation-chip').forEach(function (chip) { chip.classList.remove('selected'); });
    updateDonateLabel();
}

function setDonationBusy(busy, text) {
    const btn = document.getElementById('donate-btn');
    if (!btn) return;
    btn.disabled = busy;
    const spinner = btn.querySelector('.spinner-border');
    if (spinner) spinner.classList.toggle('d-none', !busy);
    const label = btn.querySelector('.donate-label');
    if (label && busy && text) label.textContent = text;
    document.querySelectorAll('.donation-chip').forEach(function (chip) { chip.disabled = busy; });
    const input = document.getElementById('payment_field');
    if (input) input.disabled = busy;
}

function showDonationMessage(type, text) {
    const box = document.getElementById('donation-status');
    if (!box) return;
    if (!type || !text) {
        box.className = 'd-none';
        box.textContent = '';
        return;
    }
    box.className = 'alert alert-' + type + ' mt-3 mb-0';
    box.textContent = text;            // textContent: never interpret server/Razorpay text as HTML
}

function finishDonation() {
    donationInFlight = false;
    setDonationBusy(false);
    updateDonateLabel();
}

async function donationPost(url, body) {
    const headers = { 'Content-Type': 'application/json', 'Accept': 'application/json' };
    // Works whether or not CSRF protection is switched on (see the <meta> tags in normal/base.html).
    const token = document.querySelector('meta[name="_csrf"]');
    const header = document.querySelector('meta[name="_csrf_header"]');
    if (token && header) headers[header.content] = token.content;

    const res = await fetch(url, { method: 'POST', headers: headers, body: JSON.stringify(body), credentials: 'same-origin' });
    const type = res.headers.get('content-type') || '';
    if (!type.includes('application/json')) {
        // e.g. the session expired and we were redirected to the login page
        throw new Error('Your session has expired. Please log in again.');
    }
    const data = await res.json();
    if (!res.ok) throw new Error(data.msg || 'Request failed. Please try again.');
    return data;
}

function reportDonationFailure(orderId, cancelled, reason) {
    // Informational only. The server never treats this as proof of anything.
    donationPost('/user/payment_failed', { razorpay_order_id: orderId, cancelled: cancelled, reason: reason })
        .catch(function () { /* nothing useful the user can do about it */ });
}

async function paymentStart() {
    if (donationInFlight) return;                       // prevents double clicks / repeated submissions

    const amount = donationAmountValue();
    if (amount === null) {
        showDonationMessage('warning', 'Please choose or enter a donation amount of at least \u20B91.');
        return;
    }
    if (typeof Razorpay === 'undefined') {
        showDonationMessage('danger', 'The payment window could not be loaded. Check your connection and reload the page.');
        return;
    }

    donationInFlight = true;
    showDonationMessage('', '');
    setDonationBusy(true, 'Creating order\u2026');

    let order;
    try {
        order = await donationPost('/user/create_order', { amount: amount });
    } catch (e) {
        showDonationMessage('danger', e.message);
        finishDonation();
        return;
    }
    openDonationCheckout(order);
}

function openDonationCheckout(order) {
    let settled = false;        // true once this checkout attempt has been resolved
    let lastFailure = null;     // description of the latest payment.failed event, if any

    const options = {
        key: order.keyId,                       // public key id supplied by the server (never the secret)
        amount: order.amount,                   // paise, exactly as created on the server
        currency: order.currency,
        name: order.merchantName,
        description: 'Donation of ' + formatMoney(order.amount, order.currency),
        order_id: order.id,
        prefill: { name: order.name || '', email: order.email || '' },
        theme: { color: '#16a085' },

        handler: function (response) {
            settled = true;
            setDonationBusy(true, 'Verifying payment\u2026');
            donationPost('/user/update_order', {
                razorpay_order_id: response.razorpay_order_id,
                razorpay_payment_id: response.razorpay_payment_id,
                razorpay_signature: response.razorpay_signature
            }).then(function (result) {
                showDonationMessage('success', result.msg);
                if (window.Swal) {
                    Swal.fire({ icon: 'success', title: 'Thank you!', text: result.msg });
                }
                const input = document.getElementById('payment_field');
                if (input) input.value = '';
            }).catch(function (e) {
                showDonationMessage('danger', e.message);
            }).finally(finishDonation);
        },

        modal: {
            ondismiss: function () {                // checkout closed without a successful payment
                if (settled) return;
                settled = true;
                if (lastFailure) {
                    reportDonationFailure(order.id, false, lastFailure);
                    showDonationMessage('danger', 'Payment failed: ' + lastFailure + ' You have not been charged for a failed payment.');
                } else {
                    reportDonationFailure(order.id, true, 'Checkout closed by the user');
                    showDonationMessage('warning', 'Payment cancelled. You have not been charged.');
                }
                finishDonation();
            }
        }
    };

    const rzp = new Razorpay(options);
    rzp.on('payment.failed', function (response) {
        const err = (response && response.error) || {};
        lastFailure = err.description || 'The payment could not be completed.';
        // Checkout stays open so the user can retry with another method.
    });
    setDonationBusy(true, 'Waiting for payment\u2026');
    rzp.open();
}
