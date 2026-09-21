#!/usr/bin/env python3
"""Run a command with a validated Firebase config in a private temp file."""

from __future__ import annotations

import base64
import binascii
import json
import os
from pathlib import Path
import plistlib
import signal
import subprocess
import sys
import tempfile
import time


PACKAGE_NAME = "kr.co.cotton.vlrgg_mobile"
MAX_CONFIG_BYTES = 1_048_576


class ConfigError(ValueError):
    pass


def _read_config(environ: dict[str, str], base64_env: str, source_env: str) -> bytes:
    supplied = [name for name in (base64_env, source_env) if name in environ]
    if len(supplied) != 1 or not environ[supplied[0]]:
        raise ConfigError(f"set exactly one non-empty {base64_env} or {source_env}")

    if supplied[0] == base64_env:
        try:
            data = base64.b64decode(environ[base64_env], validate=True)
        except (binascii.Error, ValueError) as exc:
            raise ConfigError("Firebase config is not valid base64") from exc
    else:
        source = Path(environ[source_env]).expanduser().resolve(strict=True)
        repo_root = Path(__file__).resolve().parents[2]
        if source == repo_root or repo_root in source.parents:
            raise ConfigError("Firebase config source must be outside the repository")
        if not source.is_file():
            raise ConfigError("Firebase config source is not a file")
        if source.stat().st_size > MAX_CONFIG_BYTES:
            raise ConfigError("Firebase config exceeds 1 MiB")
        data = source.read_bytes()

    if not data or len(data) > MAX_CONFIG_BYTES:
        raise ConfigError("Firebase config must be between 1 byte and 1 MiB")
    return data


def _validate_config(data: bytes) -> list[str]:
    try:
        config = json.loads(data)
    except (UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise ConfigError("Android Firebase config is not valid JSON") from exc

    if not isinstance(config, dict):
        raise ConfigError("Android Firebase config must be a JSON object")
    if config.get("type") == "service_account" or any(
        key in config for key in ("private_key", "private_key_id", "client_email")
    ):
        raise ConfigError("service-account credentials are not accepted")

    project = config.get("project_info")
    if not isinstance(project, dict) or not all(
        isinstance(project.get(key), str) and project[key].strip()
        for key in ("project_number", "project_id")
    ):
        raise ConfigError("Android Firebase config is missing project fields")
    if not project["project_number"].isascii() or not project["project_number"].isdigit():
        raise ConfigError("Android Firebase project number must contain ASCII digits")
    masks = [project["project_number"], project["project_id"]]
    storage_bucket = project.get("storage_bucket")
    if isinstance(storage_bucket, str) and storage_bucket.strip():
        masks.append(storage_bucket)

    clients = config.get("client")
    if not isinstance(clients, list):
        raise ConfigError("Android Firebase config is missing clients")
    for client in clients:
        if not isinstance(client, dict):
            continue
        client_info = client.get("client_info")
        if not isinstance(client_info, dict):
            continue
        android_info = client_info.get("android_client_info")
        if not isinstance(android_info, dict) or android_info.get("package_name") != PACKAGE_NAME:
            continue
        app_id = client_info.get("mobilesdk_app_id")
        keys = client.get("api_key")
        if (
            isinstance(app_id, str)
            and app_id.startswith(f"1:{project['project_number']}:android:")
            and app_id.rsplit(":", 1)[-1]
            and isinstance(keys, list)
            and any(
                isinstance(key, dict)
                and isinstance(key.get("current_key"), str)
                and key["current_key"].strip()
                for key in keys
            )
        ):
            masks.append(app_id)
            masks.extend(
                key["current_key"]
                for key in keys
                if isinstance(key, dict)
                and isinstance(key.get("current_key"), str)
                and key["current_key"].strip()
            )
            return masks
        raise ConfigError("Android Firebase client is missing app ID or API key")
    raise ConfigError(f"Android Firebase config has no client for {PACKAGE_NAME}")


def _add_github_masks(values: list[str]) -> None:
    for value in values:
        escaped = value.replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")
        print(f"::add-mask::{escaped}", flush=True)


def _validate_ios_config(data: bytes) -> list[str]:
    try:
        config = plistlib.loads(data)
    except Exception as exc:
        raise ConfigError("iOS Firebase config is not a valid plist") from exc
    fields = ("GOOGLE_APP_ID", "GCM_SENDER_ID", "PROJECT_ID", "API_KEY", "BUNDLE_ID")
    if not isinstance(config, dict) or not all(
        isinstance(config.get(key), str) and config[key].strip() for key in fields
    ):
        raise ConfigError("iOS Firebase config is missing required fields")
    if config["BUNDLE_ID"] != "kr.co.cotton.vlrggmobile":
        raise ConfigError("iOS Firebase config has the wrong bundle ID")
    number = config["GCM_SENDER_ID"]
    app_id = config["GOOGLE_APP_ID"]
    if not number.isascii() or not number.isdigit() or not app_id.startswith(f"1:{number}:ios:") or not app_id.rsplit(":", 1)[-1]:
        raise ConfigError("iOS Firebase config has inconsistent project and app IDs")
    return [config[key] for key in fields if key != "BUNDLE_ID"] + [
        config[key] for key in ("STORAGE_BUCKET", "CLIENT_ID", "REVERSED_CLIENT_ID")
        if isinstance(config.get(key), str) and config[key]
    ]


def _stop_process_group(process: subprocess.Popen[bytes], signum: int = signal.SIGTERM) -> None:
    if process.poll() is None:
        try:
            os.killpg(process.pid, signum)
        except ProcessLookupError:
            pass
    try:
        process.wait(timeout=3)
    except subprocess.TimeoutExpired:
        try:
            os.killpg(process.pid, signal.SIGKILL)
        except ProcessLookupError:
            pass
        process.wait()
    try:
        os.killpg(process.pid, signal.SIGTERM)
    except ProcessLookupError:
        return
    deadline = time.monotonic() + 2
    while time.monotonic() < deadline:
        try:
            os.killpg(process.pid, 0)
        except ProcessLookupError:
            return
        time.sleep(0.02)
    try:
        os.killpg(process.pid, signal.SIGKILL)
    except ProcessLookupError:
        return
    deadline = time.monotonic() + 2
    while time.monotonic() < deadline:
        try:
            os.killpg(process.pid, 0)
        except ProcessLookupError:
            return
        time.sleep(0.02)


def run(command: list[str], environ: dict[str, str] | None = None, platform: str = "android") -> int:
    if not command:
        raise ConfigError("missing command after --")
    source_env = dict(os.environ if environ is None else environ)
    prefix = f"FIREBASE_{platform.upper()}_CONFIG"
    data = _read_config(source_env, prefix + "_BASE64", prefix + "_SOURCE")
    masks = _validate_config(data) if platform == "android" else _validate_ios_config(data)
    if source_env.get("GITHUB_ACTIONS") == "true":
        _add_github_masks(masks)

    runner_temp = source_env.get("RUNNER_TEMP")
    temp_parent = Path(runner_temp).resolve(strict=True) if runner_temp else None
    repo_root = Path(__file__).resolve().parents[2]
    if temp_parent is not None and (temp_parent == repo_root or repo_root in temp_parent.parents):
        raise ConfigError("Firebase temporary directory must be outside the repository")
    process: subprocess.Popen[bytes] | None = None
    received_signal: int | None = None
    previous_handlers: dict[int, signal.Handlers] = {}

    def handle_signal(signum: int, _frame: object) -> None:
        nonlocal received_signal
        received_signal = signum
        if process is not None:
            try:
                os.killpg(process.pid, signum)
            except ProcessLookupError:
                pass

    handled_signals = (signal.SIGINT, signal.SIGTERM, signal.SIGHUP)
    try:
        with tempfile.TemporaryDirectory(prefix="vlrgg-firebase-", dir=temp_parent) as temp_dir:
            filename = "google-services.json" if platform == "android" else "GoogleService-Info.plist"
            config_path = Path(temp_dir) / filename
            descriptor = os.open(config_path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
            with os.fdopen(descriptor, "wb") as config_file:
                config_file.write(data)

            child_env = source_env.copy()
            for input_platform in ("ANDROID", "IOS"):
                for suffix in ("BASE64", "SOURCE", "FILE"):
                    child_env.pop(f"FIREBASE_{input_platform}_CONFIG_{suffix}", None)
            child_env[prefix + "_FILE"] = str(config_path)
            for signum in handled_signals:
                previous_handlers[signum] = signal.signal(signum, handle_signal)
            try:
                process = subprocess.Popen(command, env=child_env, start_new_session=True)
                if received_signal is not None:
                    _stop_process_group(process, received_signal)
                    return 128 + received_signal
                while True:
                    try:
                        return_code = process.wait(timeout=0.2)
                        return 128 + received_signal if received_signal is not None else return_code
                    except subprocess.TimeoutExpired:
                        if received_signal is not None:
                            _stop_process_group(process, received_signal)
                            return 128 + received_signal
            except OSError as exc:
                print(f"failed to start command: {exc.strerror or exc.__class__.__name__}", file=sys.stderr)
                return 127 if isinstance(exc, FileNotFoundError) else 126
            finally:
                if process is not None:
                    _stop_process_group(process)
    finally:
        for signum, previous in previous_handlers.items():
            signal.signal(signum, previous)


def main(argv: list[str]) -> int:
    if len(argv) < 3 or argv[1] not in ("android", "ios") or argv[2] != "--":
        print("usage: with_config.py android|ios -- <command> [args...]", file=sys.stderr)
        return 2
    try:
        return run(argv[3:], platform=argv[1])
    except (ConfigError, OSError) as exc:
        print(f"Firebase config error: {exc}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
