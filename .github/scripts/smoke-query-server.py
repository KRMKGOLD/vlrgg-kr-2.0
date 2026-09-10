"""Small remote deployment check; uses only two real upstream lookups."""

import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def smoke(base_url, token="", expect_private=False):
    parsed = urllib.parse.urlsplit(base_url)
    if (
        parsed.scheme != "https"
        or not (parsed.hostname or "").endswith(".run.app")
        or parsed.username or parsed.password or parsed.port
        or parsed.path not in ("", "/") or parsed.query or parsed.fragment
    ):
        raise ValueError("Expected a Cloud Run HTTPS service URL")
    opener = urllib.request.build_opener(NoRedirect)

    def request(path, authorized=True):
        headers = {"X-Serverless-Authorization": f"Bearer {token}"} if token and authorized else {}
        req = urllib.request.Request(base_url.rstrip("/") + path, headers=headers)
        try:
            response = opener.open(req, timeout=25)
        except urllib.error.HTTPError as error:
            response = error
        with response:
            body = response.read(2 * 1024 * 1024 + 1)
            if len(body) > 2 * 1024 * 1024:
                raise ValueError(f"{path}: response exceeded the size limit")
            return response.status, body

    if expect_private:
        if not token:
            raise ValueError("An ID token is required for private deployment checks")
        status, _ = request("/health", authorized=False)
        if status not in (401, 403):
            raise ValueError("First deployment must reject unauthenticated requests")

    checks = [
        ("/health", 200, lambda body: body.get("status") == "ok"),
        ("/api/v1/matches/upcoming?page=0", 400, lambda body: body.get("code") == "INVALID_REQUEST"),
        ("/openapi.json", 404, None),
        ("/swagger", 404, None),
        ("/api/v1/notification-targets", 404, None),
        ("/api/v1/matches/upcoming", 200,
         lambda body: body.get("category") == "upcoming" and isinstance(body.get("groups"), list)),
        ("/api/v1/news", 200, lambda body: isinstance(body.get("items"), list)),
    ]
    for path, expected_status, validate in checks:
        status, body = request(path)
        if status != expected_status:
            raise ValueError(f"{path}: expected HTTP {expected_status}, received {status}")
        if validate:
            payload = json.loads(body)
            if not isinstance(payload, dict) or not validate(payload):
                raise ValueError(f"{path}: unexpected JSON response")
        print(f"OK {path} {status}")


if __name__ == "__main__":
    try:
        smoke(os.environ["SMOKE_URL"], os.environ.get("SMOKE_ID_TOKEN", ""),
              os.environ.get("EXPECT_PRIVATE") == "true")
    except (KeyError, ValueError, OSError) as error:
        # Do not print credentials, raw response bodies or remote exception text.
        print(f"Query deployment smoke failed ({type(error).__name__}).", file=sys.stderr)
        sys.exit(1)
