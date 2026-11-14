# Member 9: Real email notifications with Azure Communication Services

notification-service can now send **real email** through Azure Communication Services (ACS).
SMS and push stay simulated. The default is still `mock`, so development, tests and CI never
send real email.

Status on 2026-11-14: implemented, unit/integration tested (87 tests pass), and verified live.
A real order created through the gateway delivered an order-confirmation email to a real inbox.

## 1. What changed

| Area | Change |
|---|---|
| Email provider | New `EmailSender` interface with `MockEmailSender` (default) and `AzureCommunicationEmailSender`. Selected by `NOTIFICATION_EMAIL_PROVIDER` in `EmailSenderConfig` |
| Azure sender | Calls the ACS REST API `POST /emails:send?api-version=2023-03-31`, signed with HMAC-SHA256 using the resource access key. Uses the JDK HTTP client, so no new dependency. Only HTTP 202 counts as sent |
| Dispatcher | `MultiChannelNotificationDispatcher` hands EMAIL to the configured provider after the address check. SMS and PUSH remain simulated |
| Triggers | Critical events now send **SMS + EMAIL**: order created, out for delivery, failed. Delivered was already EMAIL. Picked up stays SMS only |
| In-app inbox | One inbox row per recipient per event, even when two channels fire |
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
`DoNotReply@`.

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

Expect `EMAIL | PARCEL_CREATED | SENT`. The SMS row shows `FAILED` with "No valid SMS
destination" when the account has no phone number. That is expected: the sign-up form does
not collect one, and SMS is simulated anyway.

**Step 4. Check the service log** for `Azure accepted email for n***@... (operation <id>)`.
Only the masked address appears.

**Step 5. Check the inbox.** The message "SwiftDrop Order Confirmed" comes from
`DoNotReply@...azurecomm.net`. Look in spam or junk the first time: Azure-managed domains
have no reputation with university mail filters.

**Step 6. Check the in-app inbox** has one row per parcel (not two):

```powershell
Invoke-RestMethod http://localhost:8080/api/v1/notifications/me -Headers @{ Authorization = "Bearer <customer token>" }
```

### Verification run on 2026-11-14

| Check | Result |
|---|---|
| Direct ACS send test (REST + HMAC, same scheme as the Java sender) | HTTP 202, operation status Succeeded |
| `mvn -f backend/pom.xml -pl notification-service -am verify` | BUILD SUCCESS, 87 tests, including `BrokerNotificationIT` (Testcontainers) |
| Startup with `NOTIFICATION_EMAIL_PROVIDER=azure` | `Email provider: azure (...)` |
| Order `SD-QE82-BQC3` booked through the gateway | EMAIL `SENT` to the customer's IAM address; Azure accepted it |
| Order `SD-FBHQ-PCB4` | EMAIL `SENT`, operation id logged |
| In-app inbox via gateway | One "SwiftDrop Order Confirmed" row per parcel |
| Log scan for the full recipient address | Not present; only `n***@uom.lk` |

Out for delivery, delivered and failed use the same email path. They were covered by tests,
not run live, because the delivery-execution service was not part of this demo.

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
| `SENT` but nothing in the inbox | Check spam/junk; check ACS **Insights** in the portal |
