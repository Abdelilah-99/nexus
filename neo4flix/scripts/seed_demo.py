#!/usr/bin/env python3
"""
Fills Neo4flix with demo members so the recommendations have a graph to work on.
Everything goes through the public HTTPS API (register, rate, befriend, share), like real users would.

    python3 scripts/seed_demo.py                       # https://localhost
    python3 scripts/seed_demo.py --url https://my.domain --verify

Re-running is safe: existing accounts are reused and ratings are simply updated.
"""
import argparse
import random

from neo4flix_client import Api

PASSWORD = "Demo!Graph2026"

# Each persona loves some genres and dislikes others; scores are drawn from that taste.
PERSONAS = {
    "sofia": (["Sci-Fi", "Thriller"], ["Animation", "Romance"]),
    "liam": (["Sci-Fi", "Adventure", "Action"], ["Romance", "Music"]),
    "noah": (["Sci-Fi", "Drama"], ["Comedy", "Family"]),
    "emma": (["Animation", "Family", "Fantasy"], ["Horror", "Crime"]),
    "mia": (["Animation", "Comedy", "Music"], ["War", "Horror"]),
    "lucas": (["Crime", "Drama", "Thriller"], ["Animation", "Family"]),
    "olivia": (["Crime", "Drama", "History"], ["Sci-Fi", "Animation"]),
    "ethan": (["Action", "Adventure", "War"], ["Romance", "Music"]),
    "ava": (["Romance", "Comedy", "Music"], ["Horror", "War"]),
    "leo": (["Horror", "Thriller", "Sci-Fi"], ["Family", "Romance"]),
}
FRIENDS = [("sofia", "liam"), ("sofia", "emma"), ("liam", "noah"), ("emma", "mia"), ("lucas", "olivia"),
           ("ethan", "liam"), ("ava", "mia"), ("leo", "sofia")]


def score_for(movie, likes, dislikes, rng):
    genres = set(movie["genres"])
    if genres & set(dislikes):
        return rng.choice([1, 2, 2, 3])
    if genres & set(likes):
        return rng.choice([4, 4, 5, 5, 5])
    return rng.choice([2, 3, 3, 4])


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--url", default="https://localhost")
    parser.add_argument("--verify", action="store_true", help="verify the TLS certificate (production)")
    args = parser.parse_args()
    api = Api(args.url, verify_tls=args.verify)
    rng = random.Random(42)  # same data every run

    status, page, _ = api.call("GET", "/api/movies?size=100")
    if status != 200:
        raise SystemExit(f"cannot list movies ({status}): is the stack running on {args.url}?")
    movies = page["items"]

    accounts = {}
    for name, (likes, dislikes) in PERSONAS.items():
        token, user_id = api.register_or_login(name, f"{name}@demo.neo4flix.local", PASSWORD)
        accounts[name] = (token, user_id)
        liked = [m for m in movies if set(m["genres"]) & set(likes)]
        others = [m for m in movies if m not in liked]
        picks = rng.sample(liked, min(9, len(liked))) + rng.sample(others, min(4, len(others)))
        for movie in picks:
            api.call("PUT", f"/api/ratings/{movie['id']}",
                     {"score": score_for(movie, likes, dislikes, rng), "review": None}, token)
        print(f"  {name:7} rated {len(picks)} movies (likes {', '.join(likes)})")

    for a, b in FRIENDS:
        api.call("PUT", f"/api/users/me/friends/{accounts[b][1]}", None, accounts[a][0])
    api.call("POST", "/api/recommendations/share",
             {"movieId": "arrival-2016", "friendId": accounts["liam"][1], "message": "Smart sci-fi, you'll love it"},
             accounts["sofia"][0])
    print(f"Done: {len(accounts)} members, {len(FRIENDS)} friendships, 1 shared recommendation.")
    print(f"Log in as any of them with the password {PASSWORD}  (e.g. sofia / {PASSWORD})")


if __name__ == "__main__":
    main()
