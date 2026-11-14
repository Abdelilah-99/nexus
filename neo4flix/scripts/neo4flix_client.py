"""Tiny HTTPS client for the Neo4flix API (Python standard library only), shared by the scripts."""
import json
import ssl
import urllib.error
import urllib.request


class Api:
    def __init__(self, base_url, verify_tls=False):
        self.base = base_url.rstrip("/")
        # The dev certificate is self-signed: skip verification unless --verify is given.
        self.ctx = ssl.create_default_context()
        if not verify_tls:
            self.ctx.check_hostname = False
            self.ctx.verify_mode = ssl.CERT_NONE

    def call(self, method, path, body=None, token=None):
        """Returns (status, parsed JSON or None, headers)."""
        data = json.dumps(body).encode() if body is not None else None
        req = urllib.request.Request(self.base + path, data=data, method=method)
        req.add_header("Content-Type", "application/json")
        if token:
            req.add_header("Authorization", "Bearer " + token)
        try:
            with urllib.request.urlopen(req, context=self.ctx, timeout=30) as resp:
                raw = resp.read()
                is_json = "json" in (resp.headers.get("Content-Type") or "")
                return resp.status, (json.loads(raw) if raw and is_json else None), resp.headers
        except urllib.error.HTTPError as e:
            raw = e.read()
            try:
                parsed = json.loads(raw) if raw else None
            except ValueError:
                parsed = None
            return e.code, parsed, e.headers

    def login(self, login, password):
        status, body, _ = self.call("POST", "/api/auth/login", {"login": login, "password": password})
        return body if status == 200 else None

    def register_or_login(self, username, email, password):
        self.call("POST", "/api/auth/register", {"username": username, "email": email, "password": password})
        session = self.login(username, password)
        if not session or not session.get("accessToken"):
            raise RuntimeError(f"cannot log in as {username} (2FA on, or wrong password?)")
        return session["accessToken"], session["user"]["id"]


def totp(secret_b32, offset_steps=0):
    """RFC 6238 code, so a script can play the role of the authenticator app."""
    import base64, hashlib, hmac, struct, time
    key = base64.b32decode(secret_b32 + "=" * ((8 - len(secret_b32) % 8) % 8))
    counter = int(time.time()) // 30 + offset_steps
    digest = hmac.new(key, struct.pack(">Q", counter), hashlib.sha1).digest()
    offset = digest[-1] & 0x0F
    code = (struct.unpack(">I", digest[offset:offset + 4])[0] & 0x7FFFFFFF) % 1_000_000
    return f"{code:06d}"
