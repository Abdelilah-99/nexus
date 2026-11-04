# Buy02 audit readiness

Checked 2026-11-04 against the supplied audit checklist. Current deployed main is
`e9a8397325d4b69a57aa604f351b898c63845c4c`. Required code corrections are on
`fix/audit-required-items` for teammate review and merge.

**Status:** staging Cloudinary is repaired and real seller uploads pass. Jenkins
PR validation is configured and has passed a real PR build. The standalone Eureka
fix passes its regression and isolated runtime check, but still needs a reviewed
merge and deployment through main before the final submission check.

## Checklist results

| Audit item | Result and evidence |
| --- | --- |
| Database collections, fields, relationships | Implemented. MongoDB uses users, products, media metadata, carts, orders and checkout receipts. Carts reference customers; product/media ownership references users; orders embed product/seller/price/category snapshots and seller-specific fulfillment histories. Actual product/order indexes were inspected. |
| Database design explanation | Demonstrate logical references across service databases, immutable purchase snapshots, Decimal128 order amounts, optimistic versions, idempotency and personal archive visibility. Cross-service relations are enforced by services rather than SQL foreign keys. |
| Collaborative branches, PRs, reviews | 23 merged PRs inspected: 20 have APPROVED reviews. Historical PRs #1, #3 and #10 have no recorded approval. Recent PRs #21–#26 have teammate approvals. Current main protection requires reviews and green CI; history is preserved. |
| Orders, profiles, search/filter, shopping cart | Seven existing browser scenarios passed on Jenkins staging: search/filter/sort/pagination, mixed-seller checkout and delivery, cancellation, archive, analytics and API errors. Additional real Cloudinary upload and seller create/edit/delete UI checks now pass there too. |
| No application errors or warnings | Core and seller browser checks recorded 0 unhandled JavaScript errors, 0 HTTP 5xx responses and 0 console warnings after the storage correction. The old discovery instance has a peer-replication timeout in its history; the fix is verified separately and awaits merge/deployment. Standard framework startup cache advisories also exist. |
| Cart retained after refresh | Passed: added two products, increased one quantity to 2, refreshed, and verified both items, quantity 2 and total 31 before checkout. |
| SonarQube and improvements | All seven persistent project gates were OK with 0 unresolved issues during audit. Jenkins #10 passed all gates. Rule-specific improvements are recorded in .ci/VALIDATION.md and docs/FINISHING-TASKS.md. |
| Responsive and usable UI | Desktop functional flows passed; six customer/seller screens fit 390px width without horizontal page overflow. Mobile date-range errors and analytics were tested. Final visual/accessibility demonstration remains a team responsibility. |
| Error handling and validation | Browser verified code/message/details for 400/401/403/404, field details, invalid tokens, missing CSRF, invalid dates and ownership failures. The original storage failure returned a controlled 502; successful uploads now work. |
| Security | HTTPS, JWT, CSRF, role and ownership checks, and gateway removal of forged identity headers are implemented. Browser checks rejected another seller's product update, a client's product creation and anonymous forged X-User-* headers. BCrypt is configured and user DTOs exclude password hashes. Internal services rely on isolated Docker networks. |
| Reviews for every PR | Historical exception remains for #1/#3/#10; approval records cannot be claimed for those PRs. Teammates should explain their actual reviews and contributions. Current required approval, latest-push approval and stale-review rules are active. |
| Jenkins builds/tests/deployments and PR usage | Main first-pipeline #10 passed all stages and deployed staging. New pull-request-checks #1 passed PR #26 merged with current main, including 446 backend tests and frontend build/tests. The PR job is started with Build with Parameters; its stages run automatically and never deploy. GitHub Actions continues enforcing PR Sonar gates. |
| Branch merges and current main | PR #26 is merged and deployed. Main Actions run 41857472865 passed. Required audit fixes on this branch still require their own teammate-reviewed PR and merge. |
| Comprehensive application test | Nine staging browser audit checks passed after restarting Product Service and confirming routed search recovery. Real staging image upload, product creation/edit/deletion and authorization checks also passed with 0 runtime errors. |
| Critical unit/integration tests | Main #10 and PR check #1 published 446 backend tests passed, 0 failed, 0 skipped. Frontend has 218 passing tests across 36 files. MongoDB integration and browser E2E tests exist. Required-fix configuration tests: 30 passed; CI helper tests: 16 passed; discovery verify: 2 passed. |
| Wishlist | Optional bonus; not needed for this submission. |
| Additional payments | Optional bonus. CASH_ON_DELIVERY implements the required Pay on Delivery flow. |

## Required corrections and validation

### Staging image upload

The original staging CLOUDINARY_* values matched .env.example. Updated only those
three private values to the credentials verified in the working local app,
preserving database/JWT/TLS settings and saving a private configuration backup.
Reloaded Jenkins' file credential and ran main deployment #10 successfully.

A real seller browser session then uploaded a small PNG through the product form,
created a product with its HTTPS image, edited the price, and deleted the product.
Role/ownership/header-forgery tests passed. Its temporary accounts/products/media
were removed. No private credentials are committed.

The main pipeline now validates the deployment environment and rejects empty or
example Cloudinary values. This prevents repeating the observed configuration
mistake; it does not replace a real upload check for expired provider credentials.

### Jenkins PR validation

The new job's pipeline and validator live under controller-owned jenkins-lab/jcasc,
not in the PR checkout. A numeric PR number is required; only same-repository,
trusted team PRs into main are accepted. The exact head is verified before merging
current main and running backend/frontend builds and tests. The merge SHA,
coverage and JUnit reports are archived. Production file credentials are not bound
by this job. Forks and untrusted authors are rejected because this lab has access
to the host Docker daemon.

Use pull-request-checks → Build with Parameters for each new team PR, then include
its build link in the manual GitHub review. Existing GitHub Actions Sonar gates
remain required. Use first-pipeline after the reviewed merge for deployment.
PR check #1 successfully exercised reviewed PR #26; run the new audit-fix PR through
this job too before merging it.

### Standalone discovery

The server's default instance hostname differed from its localhost service URL,
so localhost was treated as a replication peer. The hostname and default service
URL now identify the same standalone server. A Spring integration regression
checks own-URL recognition and an empty peer list. An isolated packaged container
became healthy, showed empty replica entries and logged no self-replication errors.
The probe container was removed. This code fix awaits reviewed merge/deployment;
old staging logs are not evidence that it has already been deployed.

## Final submission steps

1. Open the audit-fix PR manually, run its numeric PR through pull-request-checks,
   and have a teammate review it with required CI green.
2. Merge and run first-pipeline from main to deploy the discovery/preflight changes.
3. Repeat the seller image upload and core flow, verify healthy registration/routed
   requests and inspect new discovery logs. Save that final build as audit evidence.
4. Demonstrate database relationships, cart refresh, checkout, separate sellers,
   analytics, validation and mobile pages. Explain historical review exceptions.

## Evidence links

- [Main deployment #10](http://localhost:8085/job/first-pipeline/10/)
- [Jenkins PR validation #1](http://localhost:8085/job/pull-request-checks/1/)
- [Main GitHub CI](https://github.com/AyyoubMh48/buy-02/actions/runs/41857472865)
- [Reviewed PR #26](https://github.com/AyyoubMh48/buy-02/pull/26)
- [Main ruleset](https://github.com/AyyoubMh48/buy-02/rules/25739405)
- [API errors](API-ERRORS.md), [quality history](../.ci/VALIDATION.md)

Browser audit helpers were temporary copies outside Git with the staging project
guard preserved. Fixtures used random account/product IDs and cleanup targeted
only those IDs. Existing accounts, products, orders and volumes were preserved.

The final fixture check found that registration lowercases emails while the old
browser helper cleaned up mixed-case emails. The helper now normalizes them and
verifies removal. This audit's exact generated IDs/emails were identified and
removed; unrelated accounts were preserved.
