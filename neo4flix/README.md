# Neo4flix

A movie recommendation engine built on a **Neo4j graph**, four **Spring Boot microservices**, an **Angular** frontend,
**JWT + TOTP two-factor** authentication and **HTTPS**, all running in **Docker**.

## Quick start

Requirements: Docker with Compose v2, `openssl`, and Python 3 for the optional scripts. You don't need a JDK, Maven or Node,
because everything compiles inside Docker.

```bash
cd neo4flix
cp .env.example .env              # then replace every secret (openssl rand -base64 48)
./scripts/generate-dev-cert.sh    # self-signed certificate for https://localhost
docker compose up -d --build      # first build ≈ 3 minutes
python3 scripts/seed_demo.py      # optional: 10 demo members with ratings and friends
python3 scripts/smoke_test.py     # optional: 42 end-to-end checks
```

Open **https://localhost** and accept the self-signed certificate. Demo accounts: `sofia`, `liam`, `emma`, ... with the
password `Demo!Graph2026`. The admin account comes from `ADMIN_USERNAME` / `ADMIN_PASSWORD` in `.env`.

## Architecture

```
                 ┌──────────────────────────── Docker network "backend" ────────────────────────────┐
 Browser ─HTTPS─▶│ nginx + Angular ──▶ gateway :8080 ──┬──▶ user-service           :8081 ─┐         │
  (443, 80→301)  │ (TLS, CSP, HSTS)   Spring Cloud     ├──▶ movie-service          :8082 ─┤         │
                 │                    Gateway          ├──▶ rating-service         :8083 ─┼──▶ Neo4j│
                 │                                     └──▶ recommendation-service :8084 ─┘   :7687 │
                 └──────────────────────────────────────────────────────────────────────────────────┘
```

| Module | Role |
|---|---|
| `security-common` | Shared library: makes every service an OAuth2 resource server that validates the JWT, maps roles, returns one JSON error format |
| `user-service` | Accounts, password policy, login (issues the JWT), TOTP 2FA, profile, watchlist, friends |
| `movie-service` | Movie CRUD (writes need ADMIN), multi-criteria search, genres, similar movies |
| `rating-service` | Rating CRUD; checks over REST with the movie-service that the movie exists |
| `recommendation-service` | Personalised recommendations (graph traversals) and sharing with friends; checks friendship over REST with the user-service |
| `gateway` | Single entry point: routes `/api/**` to the services |
| `frontend` | Angular 22 app served by nginx, which also terminates TLS and proxies `/api` |

Only nginx is published (ports 80 and 443). The services and the gateway are reachable only on the internal network.
Neo4j's ports are bound to `127.0.0.1` so you can use the Neo4j Browser at http://localhost:7474.

## Graph model

```
(:User {id, username, email, passwordHash, roles, totpEnabled, totpSecret*})      * AES-GCM encrypted
(:Movie {id, title, releaseDate, releaseYear, runtime, director, overview})
(:Genre {name})

(:Movie)-[:IN_GENRE]->(:Genre)
(:User)-[:RATED {score 1..5, review, ratedAt, updatedAt}]->(:Movie)
(:User)-[:IN_WATCHLIST {addedAt}]->(:Movie)
(:User)-[:FRIEND {since}]-(:User)
(:User)-[:SHARED]->(:Share {message, sharedAt, read})-[:WITH]->(:User),  (:Share)-[:ABOUT]->(:Movie)
```

There is one database for the whole graph, because recommendations traverse users, movies and genres together. Each service owns its own labels and relationships.

## Recommendations

| Where | Algorithm |
|---|---|
| `GET /api/recommendations` | **Collaborative**: finds users who rated the same movies as you (similarity = agreement on scores × number of movies in common), then movies they rated 4–5★ that you haven't seen. **Content-based**: your affinity per genre (sum of `score − 3`) applied to unseen movies. **Hybrid** = 0.6 × collaborative + 0.4 × content (each normalised). **Popular** fills the list, and is what new users get. Filters: genre, year range, release-date range. |
| `GET /api/movies/{id}/similar` | Shared genres (Jaccard), same director, and "fans of this movie also loved". |

Every result includes human-readable `reasons`, for example "Loved by carol, dave, who share your taste" or "Matches your taste in Sci-Fi".

## Security

- **HTTPS everywhere**: TLS 1.2/1.3 only, HTTP redirected to HTTPS, HSTS. Use Let's Encrypt in production (below).
- **JWT** (HS256, 1 h): issued by the user-service and validated by **every** service (signature, expiry, issuer, type). It is sent in the
  `Authorization` header and kept in `sessionStorage`, so there are no cookies and no CSRF.
- **Roles**: `USER` / `ADMIN`. Movie writes and `/api/admin/**` require ADMIN, enforced in the backend.
- **Passwords**: BCrypt (cost 12). The policy requires 10–128 characters with lower case, upper case, a digit and a symbol, no spaces,
  and no username. Login failures share one generic message, and 5 failures lock the account for 15 minutes.
- **2FA (TOTP, RFC 6238)**: QR code setup, secret encrypted with AES-256-GCM, ±30 s drift, replay protection, 8 single-use
  recovery codes. Until the second step is done, the login token (`typ=mfa`) is refused by every API.
- **Browser**: strict CSP (`script-src 'self'`, per-request nonce for Angular styles), `X-Frame-Options: DENY`, `nosniff`.
- **Containers** run as a non-root user. Secrets live only in `.env`, which is gitignored.

### Let's Encrypt in production

Point your domain's DNS at the server and set `DOMAIN` and `LETSENCRYPT_EMAIL` in `.env`. Port 80 must be reachable. Then run:

```bash
docker compose up -d
docker compose --profile letsencrypt run --rm certbot
sudo cp -L /etc/letsencrypt/live/$DOMAIN/fullchain.pem /etc/letsencrypt/live/$DOMAIN/privkey.pem certs/
docker compose restart frontend
```

To renew, run `certbot renew` from cron, copy the files the same way, and run `docker compose exec frontend nginx -s reload`.

## API overview

All endpoints are behind `https://<host>/api`.

| Service | Endpoints |
|---|---|
| user | `POST auth/register`, `POST auth/login`, `POST auth/2fa/{setup,enable,verify,disable}`, `GET/PUT/DELETE users/me`, `PUT users/me/password`, `GET/PUT/DELETE users/me/watchlist[/{movieId}]`, `GET users/search?q=`, `GET/PUT/DELETE users/me/friends[/{id}]`, `GET/DELETE admin/users[/{id}]` |
| movie | `GET movies?title=&genre=&year=&yearFrom=&yearTo=&releasedAfter=&releasedBefore=&minRating=&sort=&page=&size=`, `GET movies/{id}`, `GET movies/{id}/similar`, `GET movies/genres`, `POST/PUT/DELETE movies[/{id}]` (ADMIN) |
| rating | `POST ratings`, `PUT/DELETE ratings/{movieId}`, `GET ratings/me[/{movieId}]`, `GET ratings/movie/{movieId}`, `GET ratings/user/{userId}`, `DELETE admin/ratings/{movieId}/{userId}` |
| recommendation | `GET recommendations?genre=&yearFrom=&yearTo=&releasedAfter=&releasedBefore=&strategy=&limit=`, `POST recommendations/share`, `GET recommendations/shared/{inbox,sent,unread-count}`, `POST recommendations/shared/{id}/read`, `DELETE recommendations/shared/{id}` |

## Development

```bash
docker compose up -d neo4j                                   # only the database
mvn -q install                                               # build + unit tests (JDK 21+)
set -a; . ./.env; set +a
java -jar user-service/target/user-service-1.0.0-SNAPSHOT.jar   # likewise for the other services and the gateway
cd frontend && npm ci && npx ng serve                        # http://localhost:4200, /api proxied to localhost:8080
```

Tests: `mvn verify` (JUnit), `cd frontend && npx ng test --watch=false` (Vitest), and `python3 scripts/smoke_test.py` (end to end against a running stack).
