# Razorpay donations: setup, testing and going live

## How the flow works

1. The user picks an amount on the dashboard and clicks **Donate Now**.
2. The browser sends **only the amount** to `POST /user/create_order`. The server validates it
   (default ₹1 to ₹100,000), creates the order with Razorpay, stores it as `CREATED` for the logged-in user and
   returns the order id, the amount in paise and the **public** key id.
3. Razorpay Checkout opens (UPI, cards, net banking, wallets, subject to your account's eligibility).
4. On success the browser sends the three values Razorpay returned to `POST /user/update_order`.
   The server recomputes `HMAC-SHA256(order_id + "|" + payment_id, key_secret)`, compares it in constant time
   and **only then** marks the order `PAID`. The browser's opinion of success is never used.
5. Failed or cancelled checkouts are reported to `POST /user/payment_failed`. That is informational only:
   it can set `FAILED`/`CANCELLED`, never `PAID`, and never downgrades a `PAID` order.

Safety properties: amount is fixed server-side; the order row is locked during verification so a repeated
verification cannot double-process; the same payment id cannot be attached to two orders; one user cannot
touch another user's order; the key secret exists only on the server.

## Configuration (environment variables, nothing in source control)

| Variable | Meaning |
|---|---|
| `RAZORPAY_KEY_ID` | `rzp_test_...` for testing, `rzp_live_...` for production |
| `RAZORPAY_KEY_SECRET` | the matching secret (never share or commit it) |
| `MAIL_USERNAME`, `MAIL_PASSWORD` | Gmail address and a **new** app password (the old one was committed, so rotate it) |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | optional; defaults point to local MySQL `smartcontact` |

Also set `RAZORPAY_WEBHOOK_SECRET` if you use the webhook (see below). Optional limits in `application.properties`: `razorpay.min-amount`, `razorpay.max-amount`, `razorpay.currency`.

Setting variables:

* **Windows PowerShell (current session):** `$env:RAZORPAY_KEY_ID="rzp_test_xxx"; $env:RAZORPAY_KEY_SECRET="xxx"`
* **Linux/macOS:** `export RAZORPAY_KEY_ID=rzp_test_xxx RAZORPAY_KEY_SECRET=xxx`
* **Eclipse/STS:** Run Configurations → your Spring Boot app → *Environment* tab → add the variables.
* **IntelliJ:** Run Configuration → *Environment variables*.

If the keys are missing the app still starts; donating shows "Donations are temporarily unavailable."

## Testing with Razorpay Test Mode (no real money)

1. In the Razorpay Dashboard switch to **Test Mode**, then *Account & Settings → API Keys → Generate Test Key*.
2. Set the two variables above with those test keys and start the app.
3. Log in, choose an amount, click **Donate Now**. In test mode Checkout shows Razorpay's test options.
   Values from Razorpay's documentation (confirm on their "Test Card Details" page, they can change):
   * Card `4111 1111 1111 1111`, any future expiry, any CVV, any name (OTP page: choose Success).
   * UPI: `success@razorpay` succeeds, `failure@razorpay` fails.
   * Net banking: the mock bank page lets you click Success or Failure.
4. Check results in the database: `SELECT order_id, payment_id, amount_paise, currency, status, failure_reason, created_at, paid_at FROM orders ORDER BY myorder_id DESC;`
   and in the Dashboard under *Transactions* (Test Mode).

Things to try: success, failure (retry in the same window), close the window (CANCELLED), click Donate twice quickly.

## Going live

1. Complete Razorpay KYC/activation and switch the Dashboard to **Live Mode**.
2. Generate **Live** keys and set `RAZORPAY_KEY_ID=rzp_live_...` / `RAZORPAY_KEY_SECRET=...` on the production host's
   environment (never in a file in the repo). Rotate immediately if a secret is ever exposed.
3. Serve the site over **HTTPS**. Razorpay Checkout and session cookies require it in production.
4. In Dashboard → Settings → Payment Capture, check capture is automatic, otherwise authorised payments are refunded after a few days.
5. Make a small real payment yourself and refund it from the Dashboard.

## Database changes (additive, `ddl-auto=update` applies them)

Table `orders` gains nullable columns: `currency`, `amount_paise`, `failure_reason`, `created_at`, `updated_at`, `paid_at`.
Nothing is dropped or renamed; existing rows stay valid (lower-case legacy statuses are understood).

## Commands

```
mvn -Dtest="RazorpaySignatureTest,RazorpayPaymentGatewayTest,PaymentServiceTest,PaymentControllerTest" test
mvn clean package
mvn spring-boot:run
```
The older tests (for example `UserControllerTest`, repository tests needing MySQL) are not part of this change.

## Webhook (recommended: pays are never lost if the browser closes)

Without a webhook, a user who pays and then closes the tab before the verification call leaves the order `CREATED`
although Razorpay has the money. The webhook closes that gap:

1. Dashboard → Settings → Webhooks → *Add new webhook*.
2. URL: `https://<your-domain>/razorpay/webhook` (must be public HTTPS; for local testing use a tunnel such as ngrok).
3. Events: `payment.captured` and `order.paid`.
4. Set a secret and put the same value in `RAZORPAY_WEBHOOK_SECRET`.

The server checks `X-Razorpay-Signature` (HMAC-SHA256 of the raw body) before reading anything, requires the amount and
currency to match the order it created, and is idempotent (it never double-processes a payment already verified by the browser).

## Commands

```
mvn -Dtest="RazorpaySignatureTest,RazorpayPaymentGatewayTest,PaymentServiceTest,PaymentControllerTest,RazorpayWebhookHandlerTest,WebhookControllerTest" test
mvn -Dtest=EndToEndFlowTest test
mvn clean verify
mvn spring-boot:run
```

## Known limitations

* The donation history is only in the `orders` table (no page for it yet).
* Refunds are handled in the Razorpay Dashboard; the app does not record them.
