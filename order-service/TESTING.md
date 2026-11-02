# Checkout integration tests

`bash ./mvnw -B verify` runs the unit, HTTP contract, and application context tests.

To also run `CheckoutIntegrationTest`, start a disposable MongoDB instance and set
`CHECKOUT_TEST_MONGO_URI` to its database URI before running Maven:

```sh
docker run -d --rm --name buy02-checkout-tests -p 127.0.0.1:27028:27017 mongo:7.0
CHECKOUT_TEST_MONGO_URI=mongodb://127.0.0.1:27028/buy02_checkout_test bash ./mvnw -B verify
docker stop buy02-checkout-tests
```

Use a dedicated test database. The integration suite creates uniquely identified
customers, carts, receipts, and orders. It uses real MongoDB repositories and the
real Orders HTTP endpoint; only Product Service responses are controlled.
It verifies successful checkout, idempotent replay, and stock/price changes between
cart review and order creation. Without the environment variable, these three
integration tests are explicitly skipped.
