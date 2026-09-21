#!/usr/bin/env python3

from __future__ import annotations

import base64
import json
import os
from pathlib import Path
import plistlib
import signal
import subprocess
import sys
import tempfile
import time
import unittest
from unittest.mock import patch

import with_config


SCRIPT = Path(__file__).with_name("with_config.py")
PACKAGE_NAME = "kr.co.cotton.vlrgg_mobile"


def config(package_name: str = PACKAGE_NAME) -> bytes:
    return json.dumps(
        {
            "project_info": {
                "project_number": "123456789",
                "project_id": "vlrgg-test",
                "storage_bucket": "vlrgg-test.appspot.com",
            },
            "client": [
                {
                    "client_info": {
                        "mobilesdk_app_id": "1:123456789:android:abcdef",
                        "android_client_info": {"package_name": package_name},
                    },
                    "api_key": [{"current_key": "AIza-test-key"}],
                }
            ],
        }
    ).encode()


class WithConfigTest(unittest.TestCase):
    def env(self, runner_temp: str, data: bytes = config()) -> dict[str, str]:
        environ = os.environ.copy()
        environ.pop("GITHUB_ACTIONS", None)
        environ["RUNNER_TEMP"] = runner_temp
        environ["FIREBASE_ANDROID_CONFIG_BASE64"] = base64.b64encode(data).decode()
        environ.pop("FIREBASE_ANDROID_CONFIG_SOURCE", None)
        return environ

    def invoke(self, environ: dict[str, str], command: list[str], platform: str = "android") -> subprocess.CompletedProcess[str]:
        return subprocess.run(
            [sys.executable, str(SCRIPT), platform, "--", *command],
            env=environ,
            text=True,
            capture_output=True,
            timeout=15,
        )

    def test_success_injects_private_temp_file_without_leaking_input(self) -> None:
        with tempfile.TemporaryDirectory() as runner_temp:
            child = (
                "import json, os, pathlib; "
                "p=pathlib.Path(os.environ['FIREBASE_ANDROID_CONFIG_FILE']); "
                "assert p.exists() and (p.stat().st_mode & 0o777) == 0o600; "
                "assert 'FIREBASE_ANDROID_CONFIG_BASE64' not in os.environ; "
                "assert 'FIREBASE_ANDROID_CONFIG_SOURCE' not in os.environ; "
                "assert json.loads(p.read_text())['project_info']['project_id']=='vlrgg-test'; "
                "print(p)"
            )
            result = self.invoke(self.env(runner_temp), [sys.executable, "-c", child])
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertFalse(Path(result.stdout.strip()).exists())
            self.assertEqual(list(Path(runner_temp).iterdir()), [])

    def test_propagates_child_failure_and_cleans_up(self) -> None:
        with tempfile.TemporaryDirectory() as runner_temp:
            result = self.invoke(self.env(runner_temp), [sys.executable, "-c", "raise SystemExit(23)"])
            self.assertEqual(result.returncode, 23)
            self.assertEqual(list(Path(runner_temp).iterdir()), [])

    def test_ios_configuration_validation_injection_and_cleanup(self) -> None:
        config = {
            "GOOGLE_APP_ID": "1:123456789:ios:abcdef",
            "GCM_SENDER_ID": "123456789",
            "PROJECT_ID": "vlrgg-test",
            "API_KEY": "AIza-test-key",
            "BUNDLE_ID": "kr.co.cotton.vlrggmobile",
        }
        with tempfile.TemporaryDirectory() as runner_temp:
            environ = self.env(runner_temp)
            environ.pop("FIREBASE_IOS_CONFIG_SOURCE", None)
            environ["FIREBASE_IOS_CONFIG_BASE64"] = base64.b64encode(plistlib.dumps(config)).decode()
            child = (
                "import os,pathlib,plistlib; "
                "p=pathlib.Path(os.environ['FIREBASE_IOS_CONFIG_FILE']); "
                "assert p.name=='GoogleService-Info.plist' and (p.stat().st_mode & 0o777)==0o600; "
                "assert plistlib.loads(p.read_bytes())['BUNDLE_ID']=='kr.co.cotton.vlrggmobile'; "
                "assert not any(k.endswith(('_CONFIG_BASE64','_CONFIG_SOURCE')) for k in os.environ); "
                "print(p)"
            )
            result = self.invoke(environ, [sys.executable, "-c", child], "ios")
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertFalse(Path(result.stdout.strip()).exists())
            failed = self.invoke(environ, [sys.executable, "-c", "raise SystemExit(23)"], "ios")
            self.assertEqual(failed.returncode, 23)
            self.assertEqual(list(Path(runner_temp).iterdir()), [])
            for invalid in (dict(config, BUNDLE_ID="wrong.bundle"), dict(config, GCM_SENDER_ID="987654321")):
                environ["FIREBASE_IOS_CONFIG_BASE64"] = base64.b64encode(plistlib.dumps(invalid)).decode()
                result = self.invoke(environ, [sys.executable, "-c", "pass"], "ios")
                self.assertEqual(result.returncode, 2)
                self.assertNotIn(config["API_KEY"], result.stderr)

    def test_github_actions_masks_known_config_values_before_child_output(self) -> None:
        with tempfile.TemporaryDirectory() as runner_temp:
            environ = self.env(runner_temp)
            environ["GITHUB_ACTIONS"] = "true"
            result = self.invoke(environ, [sys.executable, "-c", "print('child-started')"])
            self.assertEqual(result.returncode, 0, result.stderr)
            lines = result.stdout.splitlines()
            self.assertEqual(lines[-1], "child-started")
            self.assertEqual(
                set(lines[:-1]),
                {
                    "::add-mask::123456789",
                    "::add-mask::vlrgg-test",
                    "::add-mask::vlrgg-test.appspot.com",
                    "::add-mask::1:123456789:android:abcdef",
                    "::add-mask::AIza-test-key",
                },
            )

    def test_rejects_malformed_and_wrong_app_configs(self) -> None:
        with tempfile.TemporaryDirectory() as runner_temp:
            malformed = self.invoke(self.env(runner_temp, b"not-json"), [sys.executable, "-c", "pass"])
            wrong_app = self.invoke(self.env(runner_temp, config("example.wrong")), [sys.executable, "-c", "pass"])
            self.assertEqual(malformed.returncode, 2)
            self.assertEqual(wrong_app.returncode, 2)
            self.assertNotIn("not-json", malformed.stderr)
            self.assertNotIn("AIza-test-key", wrong_app.stderr)

    def test_rejects_missing_or_multiple_inputs(self) -> None:
        with tempfile.TemporaryDirectory() as runner_temp:
            environ = os.environ.copy()
            environ["RUNNER_TEMP"] = runner_temp
            environ.pop("FIREBASE_ANDROID_CONFIG_BASE64", None)
            environ.pop("FIREBASE_ANDROID_CONFIG_SOURCE", None)
            missing = self.invoke(environ, [sys.executable, "-c", "pass"])
            environ["FIREBASE_ANDROID_CONFIG_BASE64"] = base64.b64encode(config()).decode()
            environ["FIREBASE_ANDROID_CONFIG_SOURCE"] = "/unused"
            multiple = self.invoke(environ, [sys.executable, "-c", "pass"])
            self.assertEqual(missing.returncode, 2)
            self.assertEqual(multiple.returncode, 2)

    def test_start_failure_cleans_up(self) -> None:
        with tempfile.TemporaryDirectory() as runner_temp:
            result = self.invoke(self.env(runner_temp), [str(Path(runner_temp) / "missing-command")])
            self.assertEqual(result.returncode, 127)
            self.assertEqual(list(Path(runner_temp).iterdir()), [])

    def test_cleanup_does_not_signal_a_reaped_process_group(self) -> None:
        process = subprocess.Popen([sys.executable, "-c", "pass"], start_new_session=True)
        process.wait(timeout=5)
        with patch.object(with_config.os, "killpg") as killpg:
            with_config._stop_process_group(process)
        killpg.assert_not_called()

    def test_source_file_is_not_deleted(self) -> None:
        with tempfile.TemporaryDirectory() as runner_temp, tempfile.TemporaryDirectory() as source_dir:
            source = Path(source_dir) / "google-services.json"
            source.write_bytes(config())
            environ = os.environ.copy()
            environ["RUNNER_TEMP"] = runner_temp
            environ["FIREBASE_ANDROID_CONFIG_SOURCE"] = str(source)
            environ.pop("FIREBASE_ANDROID_CONFIG_BASE64", None)
            result = self.invoke(environ, [sys.executable, "-c", "pass"])
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertEqual(source.read_bytes(), config())
            self.assertEqual(list(Path(runner_temp).iterdir()), [])

    @unittest.skipUnless(hasattr(os, "killpg"), "requires POSIX process groups")
    def test_sigterm_stops_child_before_cleanup(self) -> None:
        with tempfile.TemporaryDirectory() as runner_temp:
            marker = Path(runner_temp) / "config-path"
            child = (
                "import os, pathlib, time; "
                f"pathlib.Path({str(marker)!r}).write_text(os.environ['FIREBASE_ANDROID_CONFIG_FILE']); "
                "time.sleep(60)"
            )
            process = subprocess.Popen(
                [sys.executable, str(SCRIPT), "android", "--", sys.executable, "-c", child],
                env=self.env(runner_temp),
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                text=True,
            )
            deadline = time.monotonic() + 10
            while not marker.exists() and time.monotonic() < deadline:
                time.sleep(0.02)
            self.assertTrue(marker.exists())
            config_path = Path(marker.read_text())
            process.send_signal(signal.SIGTERM)
            process.communicate(timeout=15)
            self.assertEqual(process.returncode, 128 + signal.SIGTERM)
            self.assertFalse(config_path.exists())
            self.assertEqual([path for path in Path(runner_temp).iterdir() if path != marker], [])

    @unittest.skipUnless(hasattr(os, "killpg"), "requires POSIX process groups")
    def test_sigterm_forces_cleanup_when_child_ignores_signal(self) -> None:
        with tempfile.TemporaryDirectory() as runner_temp:
            marker = Path(runner_temp) / "config-path"
            child = (
                "import os, pathlib, signal, time; "
                "signal.signal(signal.SIGTERM, signal.SIG_IGN); "
                f"pathlib.Path({str(marker)!r}).write_text(os.environ['FIREBASE_ANDROID_CONFIG_FILE']); "
                "time.sleep(60)"
            )
            process = subprocess.Popen(
                [sys.executable, str(SCRIPT), "android", "--", sys.executable, "-c", child],
                env=self.env(runner_temp),
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                text=True,
            )
            deadline = time.monotonic() + 10
            while not marker.exists() and time.monotonic() < deadline:
                time.sleep(0.02)
            self.assertTrue(marker.exists())
            config_path = Path(marker.read_text())
            started = time.monotonic()
            process.send_signal(signal.SIGTERM)
            process.communicate(timeout=6)
            self.assertLess(time.monotonic() - started, 5)
            self.assertEqual(process.returncode, 128 + signal.SIGTERM)
            self.assertFalse(config_path.exists())
            self.assertEqual([path for path in Path(runner_temp).iterdir() if path != marker], [])


if __name__ == "__main__":
    unittest.main()
