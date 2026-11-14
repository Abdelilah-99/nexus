#!/usr/bin/env python3
"""
End-to-end test of a running Neo4flix stack, through the public HTTPS entry point.
Covers every feature of the subject and the security rules; exits with 1 if any check fails.

    python3 scripts/smoke_test.py            # https://localhost
"""
import argparse
import http.client
import ssl
import sys
import time
import urllib.parse

from neo4flix_client import Api, totp

RESULTS = []


def check(name, condition, detail=""):
    RESULTS.append(bool(condition))
    print(("  ✔ " if condition else "  ✘ ") + name + ("" if condition else f"   → {detail}"))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--url", default="https://localhost")
    args = parser.parse_args()
    api = Api(args.url)
    host = urllib.parse.urlparse(args.url).hostname
    suffix = str(int(time.time()))[-6:]
    user, password = f"smoke{suffix}", "Smoke!Test#2026"

    print("HTTPS")
    conn = http.client.HTTPConnection(host, 80, timeout=10)
    conn.request("GET", "/movies/up-2009")
    resp = conn.getresponse()
    check("HTTP redirects to HTTPS", resp.status == 301 and resp.getheader("Location", "").startswith("https://"),
          f"{resp.status} {resp.getheader('Location')}")
    status, _, headers = api.call("GET", "/")
    check("HSTS header", "max-age" in (headers.get("Strict-Transport-Security") or ""))
    csp = headers.get("Content-Security-Policy") or ""
    check("strict CSP (script-src 'self', no unsafe-inline)", "script-src 'self'" in csp and "unsafe-inline" not in csp, csp)
    check("clickjacking protection", headers.get("X-Frame-Options") == "DENY")
    ctx = ssl.create_default_context()
    ctx.check_hostname = False
    ctx.verify_mode = ssl.CERT_NONE
    with ctx.wrap_socket(__import__("socket").create_connection((host, 443)), server_hostname=host) as s:
        check("TLS 1.2+ negotiated", s.version() in ("TLSv1.2", "TLSv1.3"), s.version())

    print("Accounts and JWT")
    status, body, _ = api.call("POST", "/api/auth/register", {"username": user, "email": f"{user}@x.io", "password": "weakpass"})
    check("weak password rejected (400)", status == 400 and "upper-case" in (body or {}).get("message", ""), body)
    status, _, _ = api.call("POST", "/api/auth/register", {"username": user, "email": f"{user}@x.io", "password": password})
    check("registration (201)", status == 201, status)
    status, _, _ = api.call("POST", "/api/auth/register", {"username": user, "email": f"other{user}@x.io", "password": password})
    check("duplicate username refused (409)", status == 409, status)
    session = api.login(user, password)
    token = (session or {}).get("accessToken")
    check("login returns a JWT", token and token.count(".") == 2, session)
    status, body, _ = api.call("POST", "/api/auth/login", {"login": user, "password": "Wrong!Pass123"})
    check("wrong password → 401 with a generic message", status == 401 and body["message"] == "Invalid username or password", body)
    check("no token → 401", api.call("GET", "/api/users/me")[0] == 401)
    check("forged token → 401", api.call("GET", "/api/users/me", token=token[:-4] + "AAAA")[0] == 401)
    check("USER on admin endpoint → 403", api.call("GET", "/api/admin/users", token=token)[0] == 403)
    check("USER cannot create movies → 403",
          api.call("POST", "/api/movies", {"title": "X", "releaseDate": "2020-01-01", "genres": ["Drama"]}, token)[0] == 403)

    print("Movies and search")
    status, page, _ = api.call("GET", "/api/movies?title=matrix")
    check("search by title", status == 200 and any(m["id"] == "the-matrix-1999" for m in page["items"]), page)
    status, page, _ = api.call("GET", "/api/movies?genre=Animation&yearFrom=2000&yearTo=2010&size=100")
    ok = status == 200 and page["total"] > 0 and all(
        "Animation" in m["genres"] and 2000 <= m["releaseYear"] <= 2010 for m in page["items"])
    check("search by genre + year range", ok, page)
    status, page, _ = api.call("GET", "/api/movies?releasedAfter=2015-01-01&releasedBefore=2016-12-31&size=100")
    check("search by release date range",
          status == 200 and page["total"] > 0 and all("2015" <= m["releaseDate"] <= "2016-12-31" for m in page["items"]), page)
    status, movie, _ = api.call("GET", "/api/movies/whiplash-2014")
    check("movie details with average rating", status == 200 and "averageRating" in movie, movie)
    status, similar, _ = api.call("GET", "/api/movies/inception-2010/similar")
    check("similar movies with reasons", status == 200 and similar and similar[0]["reasons"], similar)

    print("Ratings")
    before = movie["ratingCount"]
    status, rating, _ = api.call("PUT", "/api/ratings/whiplash-2014", {"score": 5, "review": "Smoke test"}, token)
    check("rate a movie", status == 200 and rating["score"] == 5, rating)
    status, rating, _ = api.call("PUT", "/api/ratings/whiplash-2014", {"score": 4, "review": "Updated"}, token)
    check("update the rating", status == 200 and rating["score"] == 4, rating)
    check("score outside 1..5 → 400", api.call("PUT", "/api/ratings/whiplash-2014", {"score": 9}, token)[0] == 400)
    check("rating unknown movie → 404", api.call("PUT", "/api/ratings/nope-1900", {"score": 3}, token)[0] == 404)
    _, movie, _ = api.call("GET", "/api/movies/whiplash-2014")
    check("average/count updated in movie-service", movie["ratingCount"] == before + 1, movie)
    _, mine, _ = api.call("GET", "/api/ratings/me", token=token)
    check("my ratings list", any(r["movieId"] == "whiplash-2014" and r["score"] == 4 for r in mine), mine)
    for movie_id in ["the-matrix-1999", "interstellar-2014", "blade-runner-1982"]:
        api.call("PUT", f"/api/ratings/{movie_id}", {"score": 5}, token)

    print("Recommendations")
    status, recs, _ = api.call("GET", "/api/recommendations?limit=10", token=token)
    rated = {"whiplash-2014", "the-matrix-1999", "interstellar-2014", "blade-runner-1982"}
    check("personalised recommendations", status == 200 and len(recs) > 0 and recs[0]["strategy"] != "popular", recs[:1])
    check("never recommends a movie already rated", not any(r["movie"]["id"] in rated for r in recs))
    status, recs, _ = api.call("GET", "/api/recommendations?genre=Animation&limit=10", token=token)
    check("filter recommendations by genre", status == 200 and recs and all("Animation" in r["movie"]["genres"] for r in recs))
    status, recs, _ = api.call("GET", "/api/recommendations?releasedAfter=2010-01-01&limit=10", token=token)
    check("filter recommendations by release date",
          status == 200 and recs and all(r["movie"]["releaseDate"] >= "2010-01-01" for r in recs))

    print("Watchlist, friends and sharing")
    check("add to watchlist", api.call("PUT", "/api/users/me/watchlist/up-2009", None, token)[0] == 204)
    _, items, _ = api.call("GET", "/api/users/me/watchlist", token=token)
    check("watchlist lists it", [i["movieId"] for i in items] == ["up-2009"], items)
    check("remove from watchlist", api.call("DELETE", "/api/users/me/watchlist/up-2009", None, token)[0] == 204)
    friend = f"buddy{suffix}"
    friend_token, friend_id = api.register_or_login(friend, f"{friend}@x.io", password)
    status, share, _ = api.call("POST", "/api/recommendations/share", {"movieId": "up-2009", "friendId": friend_id}, token)
    check("cannot share with a non-friend (403)", status == 403, status)
    check("add friend", api.call("PUT", f"/api/users/me/friends/{friend_id}", None, token)[0] == 204)
    status, share, _ = api.call("POST", "/api/recommendations/share",
                                {"movieId": "up-2009", "friendId": friend_id, "message": "Watch this"}, token)
    check("share with a friend", status == 201, share)
    _, inbox, _ = api.call("GET", "/api/recommendations/shared/inbox", token=friend_token)
    check("friend receives it", inbox and inbox[0]["message"] == "Watch this", inbox)

    print("Two-factor authentication")
    _, setup, _ = api.call("POST", "/api/auth/2fa/setup", None, token)
    status, enabled, _ = api.call("POST", "/api/auth/2fa/enable", {"code": totp(setup["secret"])}, token)
    check("enable 2FA with a TOTP code", status == 200 and len(enabled["recoveryCodes"]) == 8, enabled)
    session = api.login(user, password)
    check("password alone is no longer enough", session["mfaRequired"] and not session["accessToken"], session)
    check("pending 2FA token rejected by the API",
          api.call("GET", "/api/users/me", token=session["mfaToken"])[0] == 401)
    status, done, _ = api.call("POST", "/api/auth/2fa/verify", {"mfaToken": session["mfaToken"], "code": totp(setup["secret"], 1)})
    check("TOTP code completes the login", status == 200 and done["accessToken"], done)
    status, _, _ = api.call("POST", "/api/auth/2fa/verify", {"mfaToken": session["mfaToken"], "code": totp(setup["secret"], 1)})
    check("same code cannot be replayed", status == 401, status)
    token = done["accessToken"]

    print("Cleanup")
    check("delete test accounts", api.call("DELETE", "/api/users/me", {"password": password}, token)[0] == 204
          and api.call("DELETE", "/api/users/me", {"password": password}, friend_token)[0] == 204)

    failed = RESULTS.count(False)
    print(f"\n{len(RESULTS) - failed}/{len(RESULTS)} checks passed")
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
