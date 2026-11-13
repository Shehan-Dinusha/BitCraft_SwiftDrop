# Member 7 test evidence

Backend tests and coverage were refreshed for PR 5 on 13 November 2026 against `d60985bd8de25b3d3c26ea57d9861202e39b0ad0`. Application source was unchanged. The capture script archives reports after verification; it does not run tests itself.

## Recorded results

| Evidence | Result | Source time |
| --- | --- | --- |
| Merchant Surefire suites | 69 tests; 0 failures, 0 errors, 0 skipped; 17 suites | 13 November 2026, approximately 21:15 Asia/Colombo |
| Entire merchant module line coverage | 77.58% (488 / 629 lines) | 13 November 2026; exact UTC time in manifest |
| Controller line coverage | 78.41% (69 / 88 lines) | Same refreshed coverage report |
| Service line coverage | 87.23% (362 / 415 lines) | Same refreshed coverage report |
| Controllers + services line coverage | 85.69% (431 / 503 lines) | Same refreshed coverage report |

The refreshed report includes the PR 4 receipt-list controller. Controller and service coverage each exceed the guide's 70% aim. A clean verification was used to reset accumulated JaCoCo execution data before the refreshed report. Reverify if application source changes before submission.

## Files

- [Manifest and source timestamps](snapshot/manifest.json): records capture HEAD as context, not a certified test-run commit.
- [Per-suite test summary](snapshot/test-summary.csv).
- [Surefire reports](snapshot/surefire-reports/): original text summaries plus XML with machine properties and captured stdout/stderr removed. Test cases, counts, durations and failures are retained.
- [Full archived JaCoCo report](snapshot/jacoco-html/index.html): open locally for package/class/source detail. Includes the original CSV/XML and relative assets.
- [Coverage summary CSV](snapshot/coverage-summary.csv).
- [Coverage summary PNG](snapshot/coverage-summary.png) and [SVG](snapshot/coverage-summary.svg): rendered from CSV, **not a browser screenshot**. The guide's final coverage screenshot/PDF can be captured from the refreshed HTML report during submission preparation.
- [Earlier PR 3 verification](pr3-results.md): historical results; its test counts and limitations belong to that earlier run.

## What the tests establish

The archived suites cover API-key hashing, validation and rotation, approval authorization and events, unapproved merchant restrictions, B2B normalization and forwarding with mocked downstream dependencies, CSV ingestion, and merchant receipt-list authorization/pagination. Refer to the individual test case names in the XML for exact assertions. MockMvc/service tests are not proof of gateway filter behavior or live RabbitMQ delivery.

## Frontend verification follow-up

On 13 November 2026, `npm ci --no-audit --no-fund` installed 236 packages from the existing lockfile. `npm run lint` and `npm run build` then passed. The previous missing installed `recharts` error is resolved. Vite built 2,256 modules and reported a non-blocking warning for the JavaScript chunk exceeding 500 kB after minification. The successful build ran outside the agent filesystem sandbox after the sandbox blocked Vite configuration access.

These checks used the local working tree with existing React Router package changes (`^6.28.0` to `^7.18.5`). Installation did not edit those package files. This is local validation evidence, not a claim that a clean checkout of the current committed dependency set was tested. Recheck the exact package files included in the final PR. No live browser workflow was verified.

## What remains unverified

- Frontend lint/build against the final PR dependency set if it differs from the validated local package files.
- The complete live registration/approval/IAM claim refresh flow.
- B2B gateway route and API-key/JWT filter integration.
- Real order persistence, dispatch event consumption and dashboard tracking in one distributed run.
- A browser screenshot/PDF of the refreshed coverage HTML for the submission pack; the supplied PNG is explicitly a CSV summary.

Follow the [demo checklist](../../design/m7/demo-guide.md) to capture those results. Never replace pending checks with a pass based solely on a successful unit test.

## Refresh evidence

From the repository root, run backend verification and explicitly refresh the merchant JaCoCo report (if Maven's verify lifecycle has not generated it):

```powershell
mvn -f backend/pom.xml -pl merchant-b2b-service -am verify
mvn -f backend/pom.xml -pl merchant-b2b-service -am jacoco:report
powershell -NoProfile -ExecutionPolicy Bypass -File docs/test-reports/m7/capture-evidence.ps1 -VerificationCommand 'mvn -f backend/pom.xml -pl merchant-b2b-service -am verify jacoco:report'
```

Then update this README's counts/timestamps, open the archived JaCoCo `index.html`, and capture the final coverage screenshot/PDF. The capture script copies only the merchant module's existing reports into this Member 7 folder and regenerates summary images and diagram exports; it does not execute tests. It is intended to refresh the same report set; if suites are removed, inspect the archive for stale files before submission.

Do not claim shared-contract counts from this archive: it deliberately contains only Member 7 report files. The earlier session's shared-contract checks passed, but those reports belong to another module.
