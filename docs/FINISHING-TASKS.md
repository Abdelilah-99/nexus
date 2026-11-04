# Buy02 finishing tasks

Current baseline: main c9ca7ed, 2026-11-04. Seller fulfillment (PR #21),
order archive (PR #23), consistent errors (PR #24), and required main CI (PR #25)
are merged. The sections below retain their acceptance criteria and dated history.

Current work: `feat/jenkins-host-staging` prepares Jenkins for this rootless Docker
host. Jenkins is healthy on 8085; seven Sonar credentials/analysis permissions and
the candidate Jenkinsfile were verified live. Gateway tests and checkout integration
tests inside Jenkins passed. The README documents the launcher and staging on 4300.

Next required steps:

1. Review and merge this infrastructure PR with green required CI.
2. Run `first-pipeline` manually against merged main; verify tests, all seven gates,
   images, staging deployment and health checks actually run. Save run evidence.
3. Smoke-test staging, service restart/discovery recovery and the team cases below.
   Finish responsive UI/manual acceptance and final demonstration evidence.

A completed controller setup does not establish a successful staging deployment.

## Completed — Seller-specific order progress

Owner: Abdelillah (orders), Ayoub (analytics), Anass (checkout regression).

- Track a fulfillment and timeline per seller; sellers update only their own part.
- Customer overall status is In progress when seller statuses differ, Delivered
  only when every part is delivered. Show individual product/seller progress.
- Seller lists/filtering use that seller's status and product names.
- Cancellation returns only the newly cancelled part's stock. Customer cancellation
  is allowed only if all remaining parts are cancellable; never undo delivered parts.
- Analytics count delivered seller lines, even while other parts remain pending.
- Preserve old records: missing fulfillment data uses the historical overall status.
  Existing incorrect delivered mixed orders require a manual audit with both sellers;
  the database cannot reconstruct delivery evidence that was never recorded.
- Required regression: two sellers, first delivered, second pending; inspect both
  seller pages, client page, analytics, status filters, cancellation and stock.

## Completed — Order archive/removal

Owner: Abdelillah; Ayoub reviews analytics preservation.

- Add authenticated per-user archive/hide API and UI for finished orders.
- Keep snapshots and analytics; one user's action must not hide another user's order.
- Define eligible statuses; test ownership, repeat archive, lists and direct details.

## Completed — Consistent API errors

Owner: each developer for owned services; agree on the contract together.

- Implement the required {code, message, details} response consistently, preserving
  frontend compatibility during rollout. Validate 400/401/403/404/409/502 paths.
- Render field errors and safe messages; never return credentials or stack traces.

## P1 — Delivery and merge requirements

Owners: all three; repository owner for GitHub settings.

- Completed 2026-11-04: main requires Tests and SonarQube gates from GitHub
  Actions and an up-to-date branch, alongside its existing review requirement.
- Verify Jenkins using the host-daemon profile on rootless hosts, then run build, tests, all seven
  Sonar gates, packaging and staging deployment. Preserve actual run evidence.
- Test service restarts and discovery convergence. Verify real routed API readiness;
  an unauthenticated 401 alone does not prove that the backend can be reached.
- Smoke-test staging and verify its health checks. Record a successful Jenkins
  staging deployment separately from GitHub Actions results.

## P2 — Final validation and documentation

- Ayoub: confirm delivered-line accounting and placement-date UTC filters with team;
  mobile/search/filter/analytics acceptance; retain Docker static asset permission fix.
- Anass: product deleted/repriced/out-of-stock after cart review, double confirm,
  lost checkout response, partial reorder and retry without duplicate orders.
- Abdelillah: access to others' orders, forbidden transitions, cancellation races,
  seller-specific search and progress, archive behavior.
- Everyone: cross-account and mixed-seller browser tests, image upload failures,
  responsive UI, updated README/API docs and final demonstration.
- Update stale validation prose only with actual results. Do not treat historical
  reports as evidence for the final branch.

Optional wishlist and extra payment methods are not blockers for Buy02.

## Current implementation — fix/seller-order-progress

Implemented seller fulfillments and histories, customer product delivery badges,
seller-scoped status/search, delivered-line analytics and cancellation stock scoping.
Mixed active states are IN_PROGRESS; all delivered is DELIVERED; all cancelled is
CANCELLED; a finished mixture of delivered/cancelled parts is COMPLETED.

Local checks: 212 order tests passed with disposable MongoDB (zero skipped),
210 Angular tests passed with coverage thresholds, and the production build passed.
Order JaCoCo coverage: 96.1% lines, 86.5% branches. This is local coverage, not a
new SonarQube result. All five real browser scenarios passed on the updated local
app, including first-seller delivery with the other seller pending and separate
analytics. The initial browser attempt hit the removed order container's hostname
in the gateway cache; after service discovery refreshed and the gateway restarted,
the complete suite passed. Discovery/readiness testing remains tracked above.

Next: review this branch, reproduce the two-seller scenario with a new order,
then open a PR and require green remote CI before merging. The owners above can
start archive work after coordinating the shared order contract. No old order
statuses or application data were rewritten.


## SonarQube feedback on first branch run

Run 41663871876 passed backend, frontend and browser tests, but rejected four
java:S1192 repeated-literal findings in AnalyticsRepository and OrderSearchRepository.
Shared Mongo operators/field names now use constants. No rules, exclusions or
quality-gate thresholds were changed; the follow-up remote run verifies the result.


## Update — 2026-11-04

PR #21 merged as a421524 and main run 41817057073 passed. Seller-specific order
progress is implemented and merged; audit only legacy orders whose delivery was
incorrectly recorded by the previous shared status.

The older scheduled SonarQube startup failure has no surviving application logs.
Branch fix/sonar-startup-diagnostics preserves safe startup evidence for the next
failure; its local helper/configuration checks pass. This improves diagnosis and
does not claim to repair an unidentified runtime cause. Review and validate that
branch in hosted CI before merging.

The next feature to implement is per-user archive/hide for terminal orders,
keeping purchase snapshots and analytics and enforcing ownership. Coordinate with
Abdelillah before editing the shared order service. The API error contract, required
CI checks and Jenkins staging validation remain open above.


## Order archive implementation — feat/order-archive

Finished orders can now be hidden/restored per account through PATCH
/orders/{id}/archive with an explicit archived boolean. Customers need a terminal
overall order; sellers need only their own terminal fulfillment. Default lists
exclude personally archived orders; archived=true lists them with existing filters
and pagination. Order details/reorder remain available under ownership checks.
Snapshots, fulfillment history, stock, timestamps and analytics are preserved.
Optimistic version checks prevent lost concurrent visibility/status changes.

Local validation: 223 order tests passed against disposable MongoDB (zero skipped),
214 Angular tests and coverage thresholds passed, and the production build passed.
Archive regressions cover invalid/missing input, authentication/ownership, active
order rejection, repeated requests, restoring personal visibility, independent
seller progress, analytics and unchanged stock. All six browser scenarios passed, including
archive and restore through the real customer/seller UI and unchanged analytics.
Remote CI and SonarQube must also pass before merging.

After review/merge, the next remaining code task is consistent API errors.
Required merge checks and the Jenkins staging pipeline remain shared delivery
tasks. Old incorrectly delivered orders still
require the separately documented manual audit.


Archive branch CI follow-up: run 41822412779 first stopped before browser tests
because its media MongoDB container failed during initialization. The unchanged
rerun passed all six browser scenarios and all functional tests. Sonar then
reported two java:S8924 test static-import findings (verify/never); the follow-up
uses the existing Mockito static-import style, with no changed assertions or gates.


## API error contract — feat/api-error-contract

Implements the subject's shared code/message/details error response across Gateway,
Product, User, Media and Order APIs, including authentication/CSRF failures and safe
unexpected errors. Frontend forms prefer details and retain support for legacy
field aliases. Registration/profile/checkout show field errors inline. Ownership
rejections use 403; missing or invalid authentication uses 401. Existing checkout,
order, archive and analytics behavior is retained.

Local verification: Product 93, User 48, Media 45, Order 231 and Gateway 26 tests
passed (443 total, zero skipped); 218 frontend tests, coverage thresholds and the
production build passed. HTTP contract tests cover validation, malformed input,
status codes and secret/rejected-value protection. All seven browser scenarios passed against the real local HTTPS app, including
gateway/service status codes and field-detail privacy. Remote CI and SonarQube
gates must also pass before merging.

After review/merge, the remaining subject tasks are required green merge checks,
a successful Jenkins build/test/quality/package/staging deployment, and final
integration testing and documentation.


## Main merge protection — 2026-11-04

PR #24 is merged as 876d239. The live Protect main ruleset 25739405 now requires
Tests and SonarQube gates from GitHub Actions (integration 15368), with strict
up-to-date checks. Existing review requirements, code-quality rules and bypass
settings were preserved. Both the ruleset readback and the effective rules for
main were verified; .ci/main-ruleset.json records the applied configuration.

Next required task: Jenkins build → test → quality gate → package → staging.
Current inspection: Jenkins is Created, its nested Docker daemon is stopped, and
the prior daemon logs contain mount-permission failures and containerd startup
timeouts. The host Docker daemon is rootless. The current local app remains
healthy on port 4200, which the existing Jenkins Docker-in-Docker port mapping
also reserves. Plan the staging runtime/ports before starting this stack; preserve
existing application and Jenkins/SonarQube volumes. No staging success is claimed.


## Required audit follow-up — fix/audit-required-items

The full audit is in AUDIT-READINESS.md. This branch adds the controller-owned
Jenkins PR validation job and makes the standalone Eureka identity match its own
service URL, avoiding replication to localhost as a separate peer. The regression
asserts that the peer list is empty and the own URL is recognized.

Staging Cloudinary example values were replaced privately with the working local
configuration, preserving all database/JWT/TLS values. Jenkins was reloaded so its
file credential picks up the correction. No credentials are committed. Main build
#10 passed and redeployed this configuration. Nine staging browser audit checks
and the real seller upload/create/edit/delete flow passed after a service restart.
Jenkins pull-request-checks #1 passed PR #26 merged with current main. The discovery
fix passes regression and isolated runtime checks and awaits reviewed merge/deploy. Historical PRs #1/#3/#10 lack recorded
approval; current required reviews remain enforced and no history is rewritten.
