# Application API error contract

The Gateway, Product, User, Media and Order application APIs return JSON errors
with these fields:

```json
{
  "code": "VALIDATION_FAILED",
  "message": "Invalid input data",
  "details": {
    "shippingAddress.phone": "Phone is required"
  }
}
```

`code` is a stable machine-readable identifier. `message` is suitable for the
user. `details` is an object mapping field/parameter names to validation messages;
it is an empty object for errors without field details. It never includes rejected
values, credentials, exception messages from unexpected failures or stack traces.

| HTTP status | Code | Meaning |
| --- | --- | --- |
| 400 | `VALIDATION_FAILED` | Body/query/header validation failed; inspect details |
| 400 | `BAD_REQUEST` | Malformed JSON, invalid parameter type or rejected input |
| 401 | `UNAUTHORIZED` | Missing, invalid or expired authentication |
| 403 | `FORBIDDEN` | Authenticated caller lacks permission, or CSRF validation failed |
| 404 | `NOT_FOUND` | Resource or application route is missing |
| 409 | `CONFLICT` | Duplicate data, concurrent modification or invalid lifecycle action |
| 502 | `BAD_GATEWAY` | Upstream/provider request failed |
| 503 | `SERVICE_UNAVAILABLE` | A dependency is temporarily unavailable |
| 500 | `INTERNAL_SERVER_ERROR` | Unexpected failure; implementation details are hidden |

Other framework HTTP failures use the corresponding uppercase HTTP status name,
for example `METHOD_NOT_ALLOWED`. HTTP status remains authoritative. Gateway JWT,
authorization and CSRF failures use this contract even though security filters run
before controllers. Upstream error bodies from the business services are forwarded.

Existing metadata (`status`, `error`, `timestamp`, `path`) and legacy field-error
aliases (`fields`, `validationErrors`) remain available where previously supported.
Consumers should use code/message/details. Angular prefers details and accepts
legacy aliases during rollout. Registration, profile editing and checkout display
server field errors beside their inputs; existing API failure messages continue
to appear in the page's alert area.

Ownership failures are 403. In particular, selecting another user's avatar does
not invalidate the current login session. Product endpoints return 401 when the
trusted authentication context is missing, while seller/owner checks remain 403.
