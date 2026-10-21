# Smart Contact Manager: setup, run and test

Java 17, Spring Boot 3.3.3, Thymeleaf, Spring Security, MySQL.

## 1. Rotate any secret that was ever in the source code (do this first)

The original `application.properties` contained a Gmail password and a database password in plain text.
If the project was ever pushed to GitHub, zipped and shared, or e-mailed, treat them as compromised:

* **Gmail:** Google Account → Security → 2-Step Verification → *App passwords* → revoke the old one, create a new one.
* **MySQL:** `ALTER USER 'root'@'localhost' IDENTIFIED BY '<new password>';` (and prefer a dedicated, non-root user).
* **Razorpay:** Dashboard → Account & Settings → API Keys → regenerate. Never reuse a key that was exposed.
* Removing the text from a later commit is **not** enough: the old value stays in Git history, so the rotation is what protects you.

The code now reads every secret from environment variables; `.env.example` lists them (placeholders only).

## 2. Configure

| Variable | Purpose | Default |
|---|---|---|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | MySQL connection | local MySQL `smartcontact`, user `root` |
| `MAIL_USERNAME`, `MAIL_PASSWORD` | sends password-reset codes | empty (reset email will fail gracefully) |
| `RAZORPAY_KEY_ID`, `RAZORPAY_KEY_SECRET` | donations | empty (donating shows "temporarily unavailable") |
| `RAZORPAY_WEBHOOK_SECRET` | verifies Razorpay webhooks | empty (webhook rejects every call) |
| `UPLOAD_DIR` | photo folder | `user-photos` |
| `REMEMBER_ME_KEY` | "remember me" signing key | random on every start |

Set them for the current terminal:

```bash
# Linux / macOS
set -a; source .env; set +a
# Windows PowerShell
Get-Content .env | Where-Object { $_ -match '^[^#].*=' } | ForEach-Object { $k,$v = $_ -split '=',2; Set-Item "env:$k" $v }
```
In Eclipse/STS: Run Configurations → *Environment* tab. In IntelliJ: Run Configuration → *Environment variables*.

## 3. Database

`spring.jpa.hibernate.ddl-auto=update` is unchanged. The only schema change is **additive**: new nullable columns on
the payment table `orders` (`currency`, `amount_paise`, `failure_reason`, `created_at`, `updated_at`, `paid_at`).
Existing users, contacts and orders are untouched and keep working (older lower-case order statuses are understood).
Create the database once: `CREATE DATABASE smartcontact CHARACTER SET utf8mb4;`

## 4. Run and build

```bash
mvn spring-boot:run          # then open http://localhost:9090
mvn clean verify             # compile, run ALL tests, build the jar
java -jar target/*.jar       # run the built jar (same environment variables)
```

Uploaded photos are stored in `UPLOAD_DIR` (not in the database). Include that folder in your backups.
Photos are only served to logged-in users.

## 5. Tests

```bash
mvn test                                                       # everything (uses in-memory H2, never MySQL)
mvn -Dtest=EndToEndFlowTest test                               # the whole app through the real security chain
mvn -Dtest="PaymentServiceTest,PaymentControllerTest,RazorpayWebhookHandlerTest" test   # payments only
python3 tools/check_wiring.py                                  # templates <-> controllers <-> JS consistency (no Maven needed)
```

Tests use the `test` profile (`src/test/resources/application-test.properties`): H2 in memory with quoted identifiers
(so the production table name `USER` is not changed), a mocked mail service and a mocked Razorpay gateway.
No test sends email, contacts Razorpay or uses real money.

## 6. Security summary

* CSRF protection is **on** everywhere. Forms carry the token automatically; the donation JavaScript sends it in a header.
  Only `/razorpay/webhook` is exempt, and it is authenticated by an HMAC-SHA256 signature of the raw body.
* Logout is a POST. Deleting a contact or an account is a POST with a confirmation dialog (never a GET link).
* Every contact read, update and delete is looked up by *contact id AND the logged-in user's id*; another user's record
  is simply "not found". The acting user always comes from the security context, never from a form field or URL.
* Forms bind to small DTOs (`ContactForm`, `ProfileForm`, `ChangePasswordForm`). Registration only accepts
  name/email/password/about, so `id`, `role` and `enabled` cannot be posted (this closed a hole that let a registration
  overwrite another user).
* Passwords: BCrypt only, policy of 8+ characters with a letter and a number (`app.password.min-length`).
* Password reset: 6-digit `SecureRandom` code, 5-minute lifetime, 5 attempts, single use, 60 s resend cooldown and
  5 codes per session, a replaced code is invalid, the reset step needs a fresh server-side authorization (10 minutes,
  single use), and the answer never reveals whether an email is registered. Codes are never logged.
* Uploads: type decided by file signature (JPEG, PNG, GIF, WebP; SVG refused), 2 MB limit, random file names.
* `/search` returns a small DTO (it used to return the whole owner record including the password hash).
* No secrets in source, logs or front-end code. `User.toString()` no longer prints the password hash.

## 7. What was not verified in the build environment

The environment used to prepare this change had no access to Maven Central, so **`mvn` was never run**. See the
"Verification" section of the hand-over notes. Please run `mvn clean verify` once and send me the output if anything fails.
