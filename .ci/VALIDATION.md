# Validation — 2026-10-12

Validated the updated workspace against the running Docker SonarQube server. These results describe local validation, not a completed GitHub Actions run.

| Component | Tests passed | SonarQube coverage | Gate | Unresolved issues |
|---|---:|---:|---|---:|
| product-service | 47 | 93.8% | OK | 0 |
| user-service | 39 | 93.9% | OK | 0 |
| media-service | 32 | 92.9% | OK | 0 |
| gateway | 16 | 98.0% | OK | 0 |
| discovery | 2 | 100.0% | OK | 0 |
| frontend | 138 | 91.1% | OK | 0 |

Frontend runner coverage: 93.08% statements, 83.85% branches, 86.59% functions, and 96.03% lines. All four metrics enforce an 80% minimum. The frontend production build passed.

The bootstrap provisioning tests passed (16 tests). Jenkins credentials and Execute Analysis permission were verified for all six projects. Jenkins validated the updated Jenkinsfile. actionlint 1.7.12 validated the GitHub Actions workflow.

A disposable instance started from `.ci/compose.sonar.yml`, initialized its administrator and gate successfully, and analyzed an intentionally uncovered sample. Its scanner exited nonzero and the coverage gate reported ERROR at 0.0%. The validation stack was removed afterward.

Persistent projects use Buy01 Quality Gate, including 80% overall and new-code coverage, zero overall code smells/bugs/vulnerabilities and zero new issues. No issue suppressions or false-positive transitions were used to achieve these results.

GitHub still rejects protected branches on this private repository’s current plan. The prepared branch-protection JSON cannot enforce merges until the repository has a qualifying plan or the owner explicitly authorizes public visibility. Credentials removed from production defaults still require rotation at their provider.

## OrderService infrastructure update — 2026-10-24

The earlier six-project results above are historical; they do not establish a
successful seventh-project analysis.

Changed files for this infrastructure task:

- `Jenkinsfile`
- `jenkins-lab/docker-entrypoint.sh`
- `jenkins-lab/jcasc/jenkins.yaml`
- `jenkins-lab/sonar-init/bootstrap.py`
- `jenkins-lab/sonar-init/verify-jenkins.js`
- `jenkins-lab/sonar-init/test_bootstrap.py`
- `jenkins-lab/sonar-init/test_configuration.py` (new)
- `.ci/sonar_ci.py`
- `.ci/test_sonar_ci.py` (new)
- `.github/workflows/quality.yml`
- `README.md`
- `jenkins-lab/sonar-init/README.md`
- `.ci/README.md`
- `.ci/VALIDATION.md`

Actual results:

- Bootstrap and Jenkins configuration fixture tests: **22 passed**. This includes
  creating only the missing Order token while preserving the existing six,
  idempotent re-provisioning, exact seven-component mappings, exported Order
  variables, and optional JCasC fallback when either Order file is absent.
- Shared scanner/report tests: **4 passed**. Order is scanned, reported and fails
  the run for a failed scanner, failed gate or missing JaCoCo XML. HTTP/scanner
  outcomes in these tests are controlled fixtures, not live analyses.
- `order-service`: `bash ./mvnw -B clean verify` — **75 tests passed**;
  nonempty `target/site/jacoco/jacoco.xml` generated. JaCoCo reports 90.7% line
  coverage and 74.8% branch coverage. These are not SonarQube coverage/gate results.
- actionlint 1.7.12: updated workflow passed.
- `bash -n` entrypoint, `node --check` both Sonar helper scripts, JCasC YAML parsing,
  and `git diff --check`: passed. YAML parsing is not a live Jenkins schema check.
- Docker Compose configurations validated for root deployment, canonical Jenkins
  Lab, its `sonar-lab` compatibility include, and isolated Actions SonarQube using
  non-production validation values. No containers were created or deployed and
  no volumes were removed or renamed.
- Running SonarQube `/api/system/status`: **UP**. Docker showed SonarQube and its
  PostgreSQL container; no Jenkins controller was running.

Not performed: live Order project/token provisioning, SonarScanner analysis,
Quality Gate evaluation, live JCasC/plugin application, Jenkinsfile validation by
Jenkins, a Jenkins build, GitHub Actions execution, image builds or deployment.
The local secrets directory has no `sonar-bootstrap-token`, so authorized live
provisioning and analysis could not be tested. No success is inferred from health
checks or fixture tests. Existing gate thresholds and all six existing components
remain unchanged.

Reviewed and retained: Order's Maven/JaCoCo configuration, Dockerfile, authenticated
MongoDB configuration and persistent volume, Eureka registration, Gateway route,
existing Jenkins build/package/image/archive/deploy/rollback paths, root Compose
network configuration, and `.ci/compose.sonar.yml`. The isolated Sonar server
contains no per-application service list, so adding an Order container there is
unnecessary. Existing wildcard artifacts already include Order reports. No new
Cart service, authentication mechanism, or internal Gateway secret was added.

The Orders persistence implementation is still absent. Cart/Checkout tests and a
healthy Order container do not prove that a real order can be created. This task
leaves all Orders business code untouched.


## Profile analytics verification — 2026-11-02

Verified locally on `feat/profile-analytics`, based on `main` at `3662056`:

| Component | Tests passed | Maven verify / frontend build |
| --- | ---: | --- |
| Product | 85 | passed |
| User | 39 | passed |
| Media | 32 | passed |
| Order (including analytics) | 201 | passed |
| Gateway | 25 | passed |
| Discovery | 2 | passed |
| Frontend | 208 | passed |

Backend runs used Java 21 and non-production JWT/Cloudinary fixtures. Docker-backed
order and analytics integration tests ran successfully; none were skipped.
Frontend coverage passed all four 80% thresholds. Bootstrap/configuration tests
(22) and shared scanner tests (7) passed; entrypoint Bash syntax and both Sonar
helper JavaScript syntax checks passed. Scanner-test failures printed by fixtures
are intentional test inputs, not live quality-gate outcomes.

The latest main Actions run (`41425674624`) passed tests and packaging but failed
the Order SonarQube gate on `java:S1612` in `OrderFlowIntegrationTest`. This branch
replaces that lambda with `StatusChange::status`. This fixes the reported source
finding; only a new live scan can establish the resulting gate status.

Local SonarQube reports UP, but the Jenkins controller container is in Created
state and has not served its API. No Jenkins build, live Jenkinsfile validation,
or staging deployment is claimed by this local verification. The branch push
runs the GitHub Actions quality workflow; Jenkins analysis/deployment remains
restricted to main. Review the pushed commit's Actions results before merging.

The first analytics branch scan (`41451680620`) exposed four additional findings:
`java:S2143` required `java.time` instead of `java.util.Date` in the aggregation;
`Web:S6821` treated the component's literal `role="SELLER"` as an invalid ARIA role;
and two `Web:S6845` findings rejected tabindex on non-interactive table containers.
The follow-up uses UTC Instant bounds, renames the component input to `audience`,
and removes those tabindex attributes. The actual MongoDB integration tests verify
that the driver encodes Instant date bounds correctly. No rules were suppressed
and no quality-gate thresholds were lowered.

## Real browser testing and local startup — 2026-11-02

Added the host-Docker local runner, separate `buy02-local` Compose overlay, direct
HTTP builder for Eureka with an explicitly qualified load-balanced Product client,
and Puppeteer scenarios using the real HTTPS frontend/gateway/services/databases.
The browser suite creates exact-ID fixtures, runs serially, checks cleanup, and
never uploads images to Cloudinary. Local fixture writes are restricted to the
`buy02-local` Compose project.

Local results before pushing:

- Browser scenarios: **5 passed**, covering search/filtering/pagination, mixed-seller
  cart and checkout, delivery and ownership-scoped analytics, cancellation exclusion,
  inclusive UTC dates, invalid ranges and mobile viewport.
- Angular: **209 tests passed**, coverage thresholds passed, production build passed.
- Media: **37 tests passed** and Maven verify passed. Provider/network upload and
  deletion failures return a controlled 502 message; the HTTP test checks that
  provider details are absent, and the Angular test checks that a failed upload
  keeps the draft without saving a product.
- Order: **202 tests passed**, including HTTP-client builder wiring and real MongoDB
  integration tests. Local registration/cart/analytics checks also passed after
  separating the direct Eureka builder from the Product client's load-balanced one.
- Provisioning/configuration tests: **22 passed**; scanner/report tests: **7 passed**.
- Local Compose configuration, shell/JavaScript syntax, README links, and diff
  whitespace checks passed.

GitHub Actions now builds/starts the local app and runs browser tests after backend
packaging, stops that stack before SonarQube, and retains failure screenshots.
This local report does not claim the new Actions job or a staging deployment has
passed; inspect the resulting remote run before merging.


The first new browser CI run (`41465102937`) started the full stack successfully,
but the independently installed runner Chrome reported CDP `Network.getResponseBody`
errors in three assertions. CI now installs the Chrome build selected by Puppeteer
and logs its version. All five scenarios also passed locally with that exact
Chrome build (`152.0.7977.42`), in addition to the existing local system browser.

The matching browser alone did not eliminate the CI-only CDP buffer failures
(`41466637876`). Browser tests now verify rendered product ordering/dashboard
values and use read-only API requests to check persisted results. Observed mutation
status codes still must succeed, and POST/PATCH actions are never replayed. All
five revised scenarios passed locally with the matching Chrome build. This avoids
depending on DevTools retaining network response bodies while preserving the
business assertions and the real browser/application/database path.


Run `41468236364` passed all five browser scenarios on the hosted runner. Sonar
bootstrap then returned an HTTP error before any scanner ran. Fresh-instance
initialization passed on an isolated local server. CI now checks the parsed UP
status before bootstrap and reports only endpoint/status on initialization errors;
the Compose health check also matches the status field rather than any UP substring.
Ten scanner/readiness/diagnostic fixture tests pass. The local disposable server
and its database were removed without touching the persistent SonarQube stack.


## Disposable SonarQube startup evidence — 2026-11-04

PR #21 is merged as a421524. Main run 41817057073 passed. The earlier scheduled
run 41805873700 passed tests and browser flows, but Compose reported that its
SonarQube container exited (0) before readiness. The workflow removed it without
saving application logs, so the underlying cause cannot be inferred from that
run. No memory, Elasticsearch or database failure is assumed.

The startup helper now saves redacted Compose output and failure diagnostics
before the existing cleanup. It preserves nonzero startup/timeout exit codes and
uses only read-only diagnostic commands. No automatic retry or gate changes were
introduced. The always-uploaded reports/quality artifact already includes its log.

Validation: all 16 CI helper tests passed, including six startup regression tests
covering redaction, original failure codes, diagnostic failure, timeouts, invalid
container IDs and missing prerequisites. All 22 provisioning/configuration tests
passed. Compose configuration and workflow YAML/command/report wiring checks
passed, as did Bash syntax and git diff whitespace checks. These are local checks;
no new hosted run or live reproduction of the historical failure is claimed.


## Effective main CI requirement — 2026-11-04

Applied and read back repository ruleset 25739405. Its required_status_checks
list now contains Tests and SonarQube gates, bound to GitHub Actions integration
15368, with strict_required_status_checks_policy=true. The effective branch-rules
API also reports that check for main. The existing approval requirement, last-push
approval rule, stale-review dismissal, force-push/deletion restrictions, code-quality
rule and empty bypass list were preserved. The current main check was already green.

Saved the applied payload in .ci/main-ruleset.json and replaced obsolete setup
instructions with the active ruleset and its effective-rule verification command.
Validation is live API readback plus JSON/diff checks; no application tests were
rerun for this settings/documentation change. No deliberately failing PR or merge
was created as a test.

Read-only Jenkins inspection: controller Created; nested daemon stopped; host
Docker security options include rootless. Previous daemon logs show permission
denied mounting /sys/kernel/security and containerd startup timeout. Existing
local frontend is healthy on 4200; Jenkins Lab's daemon maps the same host port.
No containers, volumes or credentials were changed by this inspection.


## Jenkins host-daemon preparation — 2026-11-04

Branch `feat/jenkins-host-staging`, based on main c9ca7ed. The host daemon is
rootless; the existing privileged DinD failed during containerd startup. Added a
host-daemon controller profile and a separate staging application overlay. The
launcher preserves the running local app, Sonar/PostgreSQL and Jenkins home.
Staging uses project `buy02-staging`, frontend 4300, gateway loopback 8445, Kafka
loopback 19092 and Mongo loopback 37017/37019/37020/37021. Certificates and Mongo
init scripts are transferred through Docker into dedicated named volumes; no
controller-local bind paths survive in the resolved staging model.

Observed local results:

- Sonar bootstrap completed and preserved/provisioned all seven credentials.
- Jenkins started healthy at http://localhost:8085. Its unprivileged Jenkins user
  (UID 1000, supplementary mapped socket group) successfully queried the host daemon.
- The live controller validated the candidate Jenkinsfile. The verifier confirmed
  all seven loaded tokens match startup secrets and have Execute Analysis access.
- Existing `CheckoutIntegrationTest` ran inside Jenkins against disposable MongoDB:
  3 tests passed, zero skipped. Testcontainers cleaned up its containers.
- Gateway tests: 27 passed, zero skipped, including configurable staging origins and
  rejection of other origins. Provisioning/configuration/Compose tests: 25 passed;
  CI helper tests: 16 passed. Bash syntax and whitespace checks passed.
- Nginx configuration validation passed using the application network. The first
  check found that `request_port` is reserved in the current image; the capture now
  uses `buy02_request_port`.
- Fake certificate transfer into a disposable volume preserved content and produced
  root-owned mode-600 files. The probe volume was removed.
- Local staging preflight found a keystore password mismatch. Re-exported the
  existing matching PEM certificate/key using the configured password, verified it,
  and preserved the old keystore privately in ignored `.local/jenkins-backups/`.
  No credentials or private keys are committed.

The controller uses anonymous checkout for this public repository; private
repositories can supply a GitHub read token. Email notifications require explicit
`CI_NOTIFY_EMAIL` configuration. Main-only analysis/deployment guards remain.

Pending: reviewed merge into main, hosted CI evidence for this branch, then a full
manual Jenkins run with deployment and staging smoke tests. No staging success is
claimed by these preparation checks.


## Required audit corrections — 2026-11-04

Branch fix/audit-required-items repairs standalone Eureka peer identity and adds a
controller-owned Jenkins job for trusted team PR builds/tests. The PR head is
pinned, merged with current main and tested without binding production file
credentials or deploying. Fork/untrusted author and invalid-number tests verify
the job boundary. The main pipeline rejects empty/example Cloudinary settings.

Observed: 30 provisioning/configuration/Compose/PR/deployment-preflight tests and
16 CI helper tests passed. Discovery clean verify passed 2 tests, including actual
peer-list exclusion. Its candidate image became healthy with empty replica lists;
the isolated probe container was removed. Both candidate pipelines validated on
the live Jenkins controller. Main #10 redeployed corrected private Cloudinary
settings successfully. PR check #1 validated PR #26 with current main, publishing
446 passed backend tests, 0 failed/skipped; frontend build and 218 tests passed.

After restarting staging Product Service and confirming real routed search HTTP
200, all 9 browser audit checks passed. The separate real Cloudinary product form
upload/create/edit/delete and authorization probes passed with 0 JavaScript
errors, HTTP 5xx responses or console warnings. Temporary fixtures were cleaned
up; no existing app data or volumes were removed. Credentials remain ignored.

Review/merge, hosted CI for this branch, and deployment of the Eureka/preflight
code from merged main remain separate final checks; see docs/AUDIT-READINESS.md.
