# Admin UI refresh validation — 15 November 2026

The implementation/research record is [admin-dashboard-refresh.md](../../../design/m10/admin-dashboard-refresh.md).

| Check | Result |
|---|---|
| Frontend lint and production build | Passed; existing bundle-size warning remains |
| API generation from static specs | Passed; only changed IAM declarations retained |
| Live admin browser acceptance | 15 checks passed, no unhandled browser exceptions |
| Existing dashboard/customer-widget browser regression | 19 checks passed, no unhandled browser exceptions |
| IAM | 76 tests passed, including all opt-in database/session integration tests; no skips |
| Dispatch | 37 tests passed, no failures/skips |
| Delivery | 98 tests passed, no failures/skips |
| Analytics | 108 unit/controller and 26 PostgreSQL/RabbitMQ integration tests passed, no failures/skips |
| Shared contracts clean install | Passed |
| Complete backend reactor compilation | Passed |
| Real order → assignment → courier delivery → analytics | Passed for delivered, failed and active demo parcels |
| Direct role-change attempt via gateway | 409; account unchanged |

IAM integration tests ran with `-Dswiftdrop.integration-tests=true` in a fresh temporary IAM database, which was removed afterward. The tests never used the demo IAM database. A previously skipped create-user fixture was corrected to include its required role. Backend validation used temporary POM copies changing only the build output for IAM/analytics to `target-admin-ui/review`, avoiding running-JAR locks. Original POMs are unchanged.

## Browser coverage

The [live fixture](../../../design/m10/fixtures/verify-admin-ui.mjs) verifies removed header actions, read-only role badges, account-specific suspension confirmation, cancel without mutation, successful suspension/reactivation persistence and announcements, a simulated status-update failure that stays in the dialog, direct API rejection of role mutation, three real-data charts, consistent metric font, date presets, mobile width and browser errors. It restores its disposable test account to ACTIVE on exit. It uses real IAM tokens and APIs; it never invokes a paid AI model.

The existing fixture covers date filtering, report refresh, empty/error states, customer conversation context/retry/reset, mobile chat, rate-limit UI and persona visibility with intercepted responses. Its courier deliveries fixture was updated for the newer `/deliveries/me` array contract.

## Artifacts

- [Analytics desktop](analytics-desktop.png)
- [User directory desktop](users-desktop.png)
- [Suspension confirmation](suspend-confirmation.png)
- [Analytics mobile](analytics-mobile.png)
- `result.json`: 15 live admin check names.
- `customer-regression-result.json`: 19 fixture check names.
- `backend-results.json`: parsed Maven report totals.

Screenshots use synthetic demo accounts/orders and contain no tokens or model keys. The local application uses the configured Azure provider; these UI tests do not measure model answer quality or cost.

## Current demo

Open [Analytics](http://localhost:5173/ops/analytics) or [User management](http://localhost:5173/ops/users), sign in as `m10.ai.demo` using the existing local demo password. Refresh the page to load the updated UI. The dedicated demo's dispatch and delivery services are now running on 8084/8085 with their own databases and the same demo RabbitMQ virtual host. Existing real accounts were not reassigned to different roles.

The first demo rehearsal exposed missing district/tracking information in delivery events. The producer/consumer code was repaired, regression-tested and restarted. Only that rehearsal's 11 synthetic rejected events were replayed with route/tracking data obtained from their real customer orders; original DLQ records remain available for inspection. New demo deliveries flow normally without that repair step. No volume resets, pushes, merges or AKS deployment were performed.
