import contextlib
import importlib.util
import io
import json
import pathlib
import unittest
import urllib.error
import urllib.parse
from unittest.mock import Mock, patch


spec = importlib.util.spec_from_file_location(
    "smoke_query", pathlib.Path(__file__).with_name("smoke-query-server.py")
)
smoke_query = importlib.util.module_from_spec(spec)
spec.loader.exec_module(smoke_query)


class SmokeTest(unittest.TestCase):
    def setUp(self):
        self.responses = {
            "/health": (200, {"status": "ok"}),
            "/api/v1/matches/upcoming?page=0": (400, {"code": "INVALID_REQUEST"}),
            "/openapi.json": (404, {}),
            "/swagger": (404, {}),
            "/api/v1/notification-targets": (404, {}),
            "/api/v1/matches/upcoming": (200, {"category": "upcoming", "groups": []}),
            "/api/v1/news": (200, {"items": []}),
        }
        self.private = True
        self.requests = []
        self.opener = Mock()
        self.opener.open.side_effect = self.respond

    def respond(self, request, timeout):
        self.requests.append(request)
        parsed = urllib.parse.urlsplit(request.full_url)
        path = parsed.path + ("?" + parsed.query if parsed.query else "")
        status, payload = self.responses[path]
        if self.private and not request.has_header("X-serverless-authorization"):
            status, payload = 403, {}
        body = io.BytesIO(json.dumps(payload).encode())
        if status >= 300:
            raise urllib.error.HTTPError(request.full_url, status, "error", {}, body)
        body.status = status
        return body

    def run_smoke(self, token="test-token", private=True, url="https://query.run.app"):
        with patch.object(smoke_query.urllib.request, "build_opener", return_value=self.opener):
            with contextlib.redirect_stdout(io.StringIO()):
                smoke_query.smoke(url, token, private)

    def test_private_service_checks_real_contracts_and_keeps_token_off_unauthenticated_probe(self):
        self.run_smoke()
        self.assertEqual(len(self.requests), 8)
        self.assertFalse(self.requests[0].has_header("X-serverless-authorization"))
        self.assertTrue(all(
            request.get_header("X-serverless-authorization") == "Bearer test-token"
            for request in self.requests[1:]
        ))

    def test_public_service_works_without_credentials(self):
        self.private = False
        self.run_smoke(token="", private=False)
        self.assertEqual(len(self.requests), 7)
        self.assertTrue(all(not request.has_header("X-serverless-authorization") for request in self.requests))

    def test_unexpected_public_first_deployment_is_rejected(self):
        self.private = False
        with self.assertRaisesRegex(ValueError, "reject unauthenticated"):
            self.run_smoke()

    def test_broken_upstream_wrong_json_and_exposed_routes_fail_the_gate(self):
        for path, bad_response in (
            ("/api/v1/news", (503, {"code": "UPSTREAM_NETWORK_FAILURE"})),
            ("/api/v1/news", (200, {"items": "not a list"})),
            ("/api/v1/matches/upcoming", (200, [])),
            ("/api/v1/matches/upcoming?page=0", (400, {"code": "OTHER"})),
            ("/api/v1/notification-targets", (200, {})),
            ("/openapi.json", (302, {})),
        ):
            with self.subTest(path=path, response=bad_response):
                with patch.dict(self.responses, {path: bad_response}):
                    with self.assertRaises(ValueError):
                        self.run_smoke()

    def test_invalid_destinations_never_receive_credentials(self):
        for url in ("http://query.run.app", "https://example.com", "https://query.run.app@evil.test"):
            with self.subTest(url=url):
                with self.assertRaises(ValueError):
                    self.run_smoke(url=url)
        self.opener.open.assert_not_called()


if __name__ == "__main__":
    unittest.main()
