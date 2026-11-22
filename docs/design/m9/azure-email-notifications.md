# Member 9: Real email notifications with Azure Communication Services

notification-service sends **real email** through Azure Communication Services (ACS). Email
is the only outbound channel: **SMS has been removed** because Azure SMS needs a purchased
phone number that the student subscription cannot get. In-app push (the notification bell)
is unchanged. The default provider is still `mock`, so development, tests and CI never send
real email.

Status: implemented and verified live on 2026-11-14. Current test results are in [docs/test-reports/m9](../../test-reports/m9/README.md).
A real order created through the gateway delivered an order-confirmation email to a real inbox.

## 1. What changed

| Area | Change |
|---|---|
| Email provider | New `EmailSender` interface with `MockEmailSender` (default) and `AzureCommunicationEmailSender`. Selected by `NOTIFICATION_EMAIL_PROVIDER` in `EmailSenderConfig` |
| Azure sender | Calls the ACS REST API `POST /emails:send?api-version=2023-03-31`, signed with HMAC-SHA256 using the resource access key. Uses the JDK HTTP client, so no new dependency. Only HTTP 202 counts as sent |
| Dispatcher | `MultiChannelNotificationDispatcher` hands EMAIL to the configured provider after the address check. PUSH is recorded for the in-app inbox. Any other channel (such as `SMS`) is logged as `FAILED: Unsupported channel` |
| Triggers | Every trigger sends one EMAIL: order created, picked up, out for delivery, delivered, failed |
| SMS removed | Removed from triggers, dispatcher, preferences (`smsEnabled`, `phoneNumber`), contact lookup, API spec and frontend. `POST /logs/dispatch` rejects `SMS` with 400. Migration `V5__remove_sms_channel.sql` drops the two preference columns and moves the seeded templates to EMAIL; old dispatch-log rows keep `SMS` as history |
| In-app inbox | One inbox row per recipient per event |
| Email design | `EmailHtmlRenderer` builds one layout with copy, accent colour and details chosen per type: a short message, a four-step progress bar (with a red final step when delivery fails) and a details table that shows the tracking number once plus only what matters for that step (route and service, courier, delivery time, or failure reason). No tracking button or link. Code emails show the code once, in a code box. A plain-text copy is always included. Table layout, inline styles, no images; all text is HTML-escaped |
| Frontend | The inbox shows a distinct icon per type, an All/Unread filter and a **Mark all as read** button. Its Settings tab holds the email and lock-screen switches. The old test-email button was removed |
| Access rules (2026-11-16) | Preferences and logs are scoped to the caller; DISPATCHER/SYSTEM_ADMIN can view all logs. `POST /logs/dispatch` lets non-staff send only a test to themselves at their own address, and nobody can send OTP templates through it. One-time codes go only to the registered IAM address |
| Configuration | `NOTIFICATION_EMAIL_PROVIDER`, `ACS_EMAIL_ENDPOINT`, `ACS_EMAIL_ACCESS_KEY`, `ACS_EMAIL_SENDER_ADDRESS` added to `application.yml`, `.env.example`, `docker-compose.yml` and Helm (`values.yaml`; the key is an optional Secret `notification-acs-email`) |

### Privacy and safety

- The access key is never logged, stored, or put in an exception message.
- Logs show only masked addresses (`n***@uom.lk`). Provider errors are reduced to safe
  messages such as `Azure email rejected the message (HTTP 401)`, because exception
  messages are saved in `notification_dispatch_logs.failure_reason`.
- `NOTIFICATION_EMAIL_PROVIDER=azure` fails fast at startup if the endpoint is not
  `https://`, the key is missing or not Base64, or the sender is not an address.
- Recipient addresses still come from IAM by customer ID (`CONTACT_MODE=iam`). Events never
  carry email addresses.

## 2. Azure resources

Created with the Azure CLI in the **Azure for Students** subscription, resource group
`rg-swiftdrop-ai-dev`. That group already has a $5/month budget alert (`swiftdrop-ai-monthly`),
which now covers email cost too.

| Resource | Name | Notes |
|---|---|---|
| Email Communication Service | `swiftdrop-notify-email` | Location `global`, data location Asia Pacific |
| Email domain | `AzureManagedDomain` | Free Azure-managed domain `4084b69f-07a5-416a-aed3-3111f3d0db80.azurecomm.net` |
| Communication Service | `swiftdrop-notify-acs` | Linked to the domain above |

### Settings

| Variable | Value |
|---|---|
| `NOTIFICATION_EMAIL_PROVIDER` | `azure` (or `mock`) |
| `ACS_EMAIL_ENDPOINT` | `https://swiftdrop-notify-acs.asiapacific.communication.azure.com` |
| `ACS_EMAIL_SENDER_ADDRESS` | `DoNotReply@4084b69f-07a5-416a-aed3-3111f3d0db80.azurecomm.net` |
| `ACS_EMAIL_ACCESS_KEY` | Read from Azure when needed (below). Never commit it |

Get the key:

```powershell
az communication list-key -n swiftdrop-notify-acs -g rg-swiftdrop-ai-dev --query primaryKey -o tsv
```

To recreate the resources in another subscription:

```powershell
az extension add -n communication
az provider register -n Microsoft.Communication --wait
az communication email create -n swiftdrop-notify-email -g rg-swiftdrop-ai-dev --location global --data-location "Asia Pacific"
az communication email domain create --domain-name AzureManagedDomain --email-service-name swiftdrop-notify-email `
  -g rg-swiftdrop-ai-dev --location global --domain-management AzureManaged
$domainId = az communication email domain show --domain-name AzureManagedDomain --email-service-name swiftdrop-notify-email -g rg-swiftdrop-ai-dev --query id -o tsv
az communication create -n swiftdrop-notify-acs -g rg-swiftdrop-ai-dev --location global --data-location "Asia Pacific" --linked-domains $domainId
```

The sender domain shown by the domain command (`fromSenderDomain`) is what goes after
`DoNotReply@`. Give the sender the display name **SwiftDrop**, so inboxes show "SwiftDrop" rather than
"DoNotReply":

```powershell
az communication email domain sender-username update --sender-username DoNotReply --username DoNotReply `
  --display-name "SwiftDrop" --domain-name AzureManagedDomain --email-service-name swiftdrop-notify-email -g rg-swiftdrop-ai-dev
```

The address itself stays `DoNotReply@<guid>.azurecomm.net`, and Gmail shows no sender logo: both need a
domain SwiftDrop owns (for example `notify.swiftdrop.lk`), verified in Azure, and a sender logo also needs
BIMI (a DMARC policy at enforcement plus a BIMI DNS record and usually a paid Verified Mark Certificate).

## 3. Run notification-service with Azure email

In the terminal that runs the service:

```powershell
$env:NOTIFICATION_EMAIL_PROVIDER = 'azure'
$env:ACS_EMAIL_ENDPOINT = 'https://swiftdrop-notify-acs.asiapacific.communication.azure.com'
$env:ACS_EMAIL_SENDER_ADDRESS = 'DoNotReply@4084b69f-07a5-416a-aed3-3111f3d0db80.azurecomm.net'
$env:ACS_EMAIL_ACCESS_KEY = az communication list-key -n swiftdrop-notify-acs -g rg-swiftdrop-ai-dev --query primaryKey -o tsv
$env:CONTACT_MODE = 'iam'
mvn -f backend/pom.xml -pl notification-service -am spring-boot:run
```

When the service runs next to the [AI demo launcher](../m10/stages-6-to-11-ai-assistant.md),
also set `$env:SPRING_RABBITMQ_VIRTUAL_HOST` to the `rabbitVirtualHost` in
`backend/analytics-ai-service/target-ai/frontend-demo/session.json`. The launcher uses its own
RabbitMQ virtual host, and notification-service only receives events published there.

For Docker Compose, put the four variables in `.env` (never commit it).

## 4. Check that it works

**Step 1. The service picked Azure.** Its startup log contains:

```text
Email provider: azure (swiftdrop-notify-acs.asiapacific.communication.azure.com)
```

If it says `mock`, `NOTIFICATION_EMAIL_PROVIDER` was not set in that terminal.

**Step 2. Trigger an email.** Register a CUSTOMER with an inbox you can read, sign in, and
book a parcel (customer booking page, or `POST /api/v1/orders` through gateway 8080). This
publishes `order.created`.

**Step 3. Check the dispatch log.**

```powershell
docker exec swiftdrop-notify-postgres psql -U swiftdrop -d swiftdrop_notify -c `
  "select channel, template_code, status, failure_reason from notification_dispatch_logs where reference_id = 'SD-XXXX-XXXX' order by created_at"
```

Expect exactly one row: `EMAIL | PARCEL_CREATED | SENT`.


**Step 4. Check the service log** for `Azure accepted email for n***@... (operation <id>)`.
Only the masked address appears.

**Step 5. Check the inbox.** The message "SwiftDrop Order Confirmed" comes from
**SwiftDrop** (`DoNotReply@...azurecomm.net`). Look in spam or junk the first time: Azure-managed domains
have no reputation with university mail filters.

**Step 6. Check the in-app inbox** has one row per parcel (not two):

```powershell
Invoke-RestMethod http://localhost:8080/api/v1/notifications/me -Headers @{ Authorization = "Bearer <customer token>" }
```

### Verification run on 2026-11-14

| Check | Result |
|---|---|
| Direct ACS send test (REST + HMAC, same scheme as the Java sender) | HTTP 202, operation status Succeeded |
| `mvn -f backend/pom.xml -pl notification-service -am verify` | BUILD SUCCESS, 99 unit tests. Correction: the `*IT` integration tests did not run in this build (Failsafe was not enabled); they run since 2026-11-16, see the test report |
| Startup with `NOTIFICATION_EMAIL_PROVIDER=azure` | `Email provider: azure (...)` |
| Order `SD-QE82-BQC3` booked through the gateway | EMAIL `SENT` to the customer's IAM address; Azure accepted it |
| Order `SD-FBHQ-PCB4` | EMAIL `SENT`, operation id logged |
| In-app inbox via gateway | One "SwiftDrop Order Confirmed" row per parcel |
| Log scan for the full recipient address | Not present; only `n***@uom.lk` |
| After SMS removal: V5 migration on the running database | Applied; `sms_enabled`/`phone_number` dropped; all 5 templates `EMAIL` |
| After SMS removal: order `SD-LYWN-GQZ3` for a Gmail customer | One row, `EMAIL SENT` (no SMS row); Azure operation status `Succeeded` |
| HTML email: order `SD-3M46-66WQ` for the same Gmail customer | `EMAIL SENT`; Azure operation status `Succeeded` |

Picked up, out for delivery, delivered and failed use the same email path. They were covered
by tests, not run live, because the delivery-execution service was not part of this demo.

## 5. Cost and limits

- No idle charge. ACS email is billed per message (a fraction of a cent each); check current
  Azure pricing.
- Azure-managed domains have low sending limits (a small number of emails per minute and
  hour). This is fine for demos, but not for bulk sending. Use a custom verified domain for more.
- The `swiftdrop-ai-monthly` budget only sends alerts. To stop all email sending, set
  `NOTIFICATION_EMAIL_PROVIDER=mock`, or rotate the key:

```powershell
az communication regenerate-key -n swiftdrop-notify-acs -g rg-swiftdrop-ai-dev --key-type primary
```

## 6. Troubleshooting

| Symptom | Fix |
|---|---|
| Startup fails with `ACS_EMAIL_...` message | Set the named variable; endpoint must start with `https://` |
| `failure_reason` = `Azure email rejected the message (HTTP 401)` | Key does not belong to `swiftdrop-notify-acs`, or was rotated. Re-read it |
| `HTTP 400` | Sender address does not match the linked domain |
| `HTTP 429` | Managed-domain sending limit reached. Wait and retry |
| `Azure email service unreachable` | Network or endpoint typo |
| No dispatch row at all | Notification-service is on a different RabbitMQ virtual host from the order service |
| EMAIL row `FAILED`, "No valid EMAIL destination" | IAM has no email for that customer, or `CONTACT_MODE` is not `iam` |
| Row `FAILED`, "Unsupported channel SMS" | A caller still sends `SMS`; use `EMAIL` (SMS was removed) |
| `SENT` but nothing in the inbox | Check spam/junk; check ACS **Insights** in the portal |
