# Buy02 finishing tasks

Verified baseline: main a3c5485, 2026-11-03. Search, analytics and checkout PRs are
merged. GitHub run 41612053898 passed tests, browser scenarios and SonarQube.
These are proposed owners; coordinate before editing shared files.

## P0 — Seller-specific order progress (current branch)

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

## P1 — Order archive/removal

Owner: Abdelillah; Ayoub reviews analytics preservation.

- Add authenticated per-user archive/hide API and UI for finished orders.
- Keep snapshots and analytics; one user's action must not hide another user's order.
- Define eligible statuses; test ownership, repeat archive, lists and direct details.

## P1 — Reliable stock recovery

Owners: Anass + Abdelillah; Ayoub reviews Product Service contract.

- Replace log-only failed stock restoration with persisted, retryable recovery work.
- Make release/reservation operations idempotent before retrying; never restore twice.
- Cover cancellation, failed order persistence, concurrent checkout and unavailable
  Product Service, including restart/recovery and duplicate requests.
- Check ambiguous database/network outcomes before compensating a reservation.

## P1 — Consistent API errors

Owner: each developer for owned services; agree on the contract together.

- Implement the required {code, message, details} response consistently, preserving
  frontend compatibility during rollout. Validate 400/401/403/404/409/502 paths.
- Render field errors and safe messages; never return credentials or stack traces.

## P1 — Delivery and merge requirements

Owners: all three; repository owner for GitHub settings.

- Main ruleset has review requirements but an empty required-check list. Require
  Tests and SonarQube gates and resolve review conversations before merging.
- Repair/verify Jenkins Docker-in-Docker startup, then run build, tests, all seven
  Sonar gates, packaging and staging deployment. Preserve actual run evidence.
- Test service restarts and discovery convergence. Verify real routed API readiness;
  an unauthenticated 401 alone does not prove that the backend can be reached.
- Smoke-test staging, verify health checks and demonstrate rollback on a disposable
  deployment. GitHub Actions success does not establish a successful Jenkins deploy.

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
start archive and stock recovery in separate branches after coordinating the
shared order contract. No old order statuses or application data were rewritten.


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
Abdelillah before editing the shared order service. Stock recovery, the API error
contract, required CI checks and Jenkins staging validation remain open above.


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

After review/merge, the next remaining code task is reliable stock recovery,
followed by consistent API errors. Required merge checks and the Jenkins staging
pipeline remain shared delivery tasks. Old incorrectly delivered orders still
require the separately documented manual audit.
