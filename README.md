# otp-service

A standalone OTP service. Several applications use it at once: each is an **app** with its own
settings and its own **API keys**, and no app can see another's codes.

Spring Boot 3.5 · Java 21 · PostgreSQL · Flyway

## How an application uses it

Two calls, with the app's key in `X-Api-Key`:

```bash
# 1. Send a code
curl -X POST http://localhost:8090/v1/otp/send \
  -H "X-Api-Key: otp_xxxxxxxxxxxx_xxxxxxxx..." -H 'Content-Type: application/json' \
  -d '{"phone":"+919876543210","purpose":"login"}'
# 201 {"requestId":"e4e7...","expiresAt":"...","resendAfterSeconds":30}

# 2. Check what the user typed
curl -X POST http://localhost:8090/v1/otp/verify \
  -H "X-Api-Key: otp_xxxxxxxxxxxx_xxxxxxxx..." -H 'Content-Type: application/json' \
  -d '{"requestId":"e4e7...","code":"482913"}'
# 200 {"verified":true,"reason":null,"attemptsRemaining":0}
```

Keep the `requestId` on your server (in the session), never trust one sent back by the browser
alone. `verify` always answers 200; `verified: false` comes with a `reason`:

| reason | What to show the user |
|---|---|
| `INVALID_CODE` | Wrong code; `attemptsRemaining` says how many tries are left |
| `ATTEMPTS_EXHAUSTED` | Too many wrong tries; send a new code |
| `EXPIRED` | The code expired; send a new code |
| `ALREADY_USED` | This code was already used |
| `NOT_FOUND` | Unknown request id (or another app's) |

`send` can refuse with:

| Status | Code | Meaning |
|---|---|---|
| 422 | `VALIDATION_FAILED` | Phone not in `+<country><number>` form, or bad purpose |
| 429 | `RATE_LIMITED` | Resend too soon, too many to this number this hour, or too many from this IP. `Retry-After` says when |
| 502 | `SMS_FAILED` | The SMS provider refused; nothing was stored |
| 401 | `UNAUTHENTICATED` | Missing, wrong or revoked key, or the app is switched off |

## The console

Open **http://localhost:8090/console** and sign in with `OTP_ADMIN_TOKEN`. No build step: it is plain
HTML/JS served by the service.

- **Overview** - how many applications use the service, active keys, codes sent and verified
  (24 h, 7 days), the SMS/WhatsApp split, and a row per application.
- **Applications** - configure a new one (its first key is shown once), change its settings and
  channel, switch it off, issue and revoke keys, and see the codes it sent.
- **OTP log** - every code: number, application, channel, purpose, status, tries, the key that asked
  for it, and the caller's IP. Filter by application, part of a number, or status.

**The code itself is never shown**, because it is never stored - only an HMAC of it.

### Channels

Each application sends on `SMS`, `WHATSAPP` or `RANDOM` (one of the two, picked per send). The log
records the channel each code actually went out on. WhatsApp has no real provider yet: like SMS,
it writes to the log until one is added (implement `com.otpservice.sms.WhatsAppSender`).

## Managing apps and keys

`Authorization: Bearer $OTP_ADMIN_TOKEN` on every `/admin` call.

```bash
ADMIN="Authorization: Bearer $OTP_ADMIN_TOKEN"

# Create an app. The response holds its first API key - shown ONCE, copy it now.
curl -X POST localhost:8090/admin/apps -H "$ADMIN" -H 'Content-Type: application/json' -d '{
  "name": "IVR console",
  "settings": {"otpLength": 6, "ttlSeconds": 300, "maxAttempts": 5,
               "maxSendsPerHour": 5, "resendAfterSeconds": 30,
               "channel": "SMS",
               "senderId": "TRVLSI", "templateId": "1207xxxxxxxxxxxxxxx",
               "messageTemplate": "Your TourTravels login code is {code}. Valid for {minutes} minutes."}}'

curl localhost:8090/admin/apps -H "$ADMIN"                                   # list
curl -X PATCH localhost:8090/admin/apps/<appId> -H "$ADMIN" \
  -H 'Content-Type: application/json' -d '{"ttlSeconds":600}'                # change settings
curl -X PATCH localhost:8090/admin/apps/<appId> -H "$ADMIN" \
  -H 'Content-Type: application/json' -d '{"active":false}'                  # switch an app off

curl -X POST localhost:8090/admin/apps/<appId>/keys -H "$ADMIN" \
  -H 'Content-Type: application/json' -d '{"label":"production"}'            # new key (shown once)
curl localhost:8090/admin/apps/<appId>/keys -H "$ADMIN"                      # list keys (prefix only)
curl -X DELETE localhost:8090/admin/apps/<appId>/keys/<keyId> -H "$ADMIN"    # revoke
```

To rotate a key: create the new one, deploy it to the application, then revoke the old one.

| Setting | Default | Range |
|---|---|---|
| `otpLength` | 6 | 4–8 |
| `ttlSeconds` | 300 | 60–1800 |
| `maxAttempts` | 5 | 1–10 |
| `maxSendsPerHour` (per phone) | 5 | 1–100 |
| `resendAfterSeconds` | 30 | 0–600 |
| `messageTemplate` | "Your verification code is {code}…" | must contain `{code}`; `{minutes}` optional |

## Security, in short

- The code is never stored and never returned: only an HMAC-SHA256 of it, keyed by
  `OTP_CODE_PEPPER` and bound to the request id. A stolen database alone cannot recover codes.
- API keys are stored as SHA-256 of the secret half; the full key is shown once. Comparisons
  are constant-time.
- Wrong guesses are counted under a row lock, so parallel guesses cannot exceed `maxAttempts`.
  A verified code cannot be used again.
- Sends are rate-limited per phone (per app), per resend interval, and per IP across all apps,
  under a Postgres advisory lock so parallel requests cannot slip past a limit.
- Startup refuses a missing or short (< 32 chars) pepper or admin token, and refuses the `log`
  SMS provider unless `OTP_SMS_ALLOW_LOG=true` is set deliberately.

## Running it

### Database (once)

```bash
sudo -u postgres psql -c "CREATE ROLE otp WITH LOGIN PASSWORD 'choose-one';"
sudo -u postgres createdb -O otp otp_service
```

### Start

```bash
export DB_PASSWORD='choose-one'
export OTP_CODE_PEPPER="$(openssl rand -hex 32)"     # keep it: changing it voids live codes
export OTP_ADMIN_TOKEN="$(openssl rand -hex 32)"
export OTP_SMS_ALLOW_LOG=true                        # development: codes go to the log
mvn spring-boot:run                                  # or: java -jar target/otp-service-0.1.0-SNAPSHOT.jar
```

Listens on 8090 (`PORT` to change). `GET /health` answers `{"status":"UP"}`.

| Variable | Required | Default |
|---|---|---|
| `DB_URL` | | `jdbc:postgresql://localhost:5432/otp_service` |
| `DB_USERNAME` | | `otp` |
| `DB_PASSWORD` | yes | |
| `OTP_CODE_PEPPER` | yes, ≥ 32 chars | |
| `OTP_ADMIN_TOKEN` | yes, ≥ 32 chars | |
| `OTP_SMS_PROVIDER` | | `log` |
| `OTP_SMS_ALLOW_LOG` | for `log` (SMS and WhatsApp) | `false` |
| `OTP_WHATSAPP_PROVIDER` | | `log` |
| `OTP_MAX_SENDS_PER_IP` | | `30` per hour |
| `FORWARD_HEADERS` | behind a proxy: `native` | `none` |
| `PORT` | | `8090` |

Behind a reverse proxy, set `FORWARD_HEADERS=native` so the per-IP limit sees the client's address
rather than the proxy's.

## Sending real SMS and WhatsApp: Twilio

Set `OTP_SMS_PROVIDER=twilio` and/or `OTP_WHATSAPP_PROVIDER=twilio`, plus `TWILIO_ACCOUNT_SID`,
`TWILIO_AUTH_TOKEN`, `TWILIO_SMS_FROM` (a Twilio number) and `TWILIO_WHATSAPP_FROM`. Startup refuses if
one is missing. See `deploy/otp.env.example`.

Free trial: messages reach only numbers verified in the Twilio console. WhatsApp sandbox: sender
`+14155238886`, and each receiver must first send `join <your-sandbox-word>` to it on WhatsApp.

## Adding another provider

`log` only writes the message to the log. To add one, implement
`com.otpservice.sms.SmsSender` in its own package, annotate it
`@ConditionalOnProperty(name = "otp.sms.provider", havingValue = "<name>")`, throw
`SmsDeliveryException` when the provider refuses, and start with `OTP_SMS_PROVIDER=<name>`.

**India:** OTP SMS needs DLT registration (TRAI): a registered header (`senderId`) and an approved
template (`templateId`). The app's `messageTemplate` must match the approved text word for word,
with `{code}` where the template has its variable.

## Tests

```bash
mvn test
```

Unit tests run anywhere. `OtpServiceIntegrationTest` starts Postgres with Testcontainers and needs
Docker.
