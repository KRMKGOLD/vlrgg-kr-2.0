#!/usr/bin/env python3
"""Run a command with a validated Firebase config in a private temp file."""

from __future__ import annotations

import base64
import binascii
import json
import os
from pathlib import Path
import signal
import subprocess
import sys
import tempfile
import time


PACKAGE_NAME = "kr.co.cotton.vlrgg_mobile"
BASE64_ENV = "FIREBASE_ANDROID_CONFIG_BASE64"
SOURCE_ENV = "FIREBASE_ANDROID_CONFIG_SOURCE"
FILE_ENV = "FIREBASE_ANDROID_CONFIG_FILE"
MAX_CONFIG_BYTES = 1_048_576


class ConfigError(ValueError):
    pass


def _read_config(environ: dict[str, str]) -> bytes:
    supplied = [name for name in (BASE64_ENV, SOURCE_ENV) if name in environ]
    if len(supplied) != 1 or not environ[supplied[0]]:
        raise ConfigError(f"set exactly one non-empty {BASE64_ENV} or {SOURCE_ENV}")

    if supplied[0] == BASE64_ENV:
        try:
            data = base64.b64decode(environ[BASE64_ENV], validate=True)
        except (binascii.Error, ValueError) as exc:
            raise ConfigError("Android Firebase config is not valid base64") from exc
    else:
        source = Path(environ[SOURCE_ENV]).expanduser().resolve(strict=True)
        repo_root = Path(__file__).resolve().parents[2]
        if source == repo_root or repo_root in source.parents:
            raise ConfigError("Android Firebase config source must be outside the repository")
        if not source.is_file():
            raise ConfigError("Android Firebase config source is not a file")
        if source.stat().st_size > MAX_CONFIG_BYTES:
            raise ConfigError("Android Firebase config exceeds 1 MiB")
        data = source.read_bytes()

    if not data or len(data) > MAX_CONFIG_BYTES:
        raise ConfigError("Android Firebase config must be between 1 byte and 1 MiB")
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


def run(command: list[str], environ: dict[str, str] | None = None) -> int:
    if not command:
        raise ConfigError("missing command after --")
    source_env = dict(os.environ if environ is None else environ)
    data = _read_config(source_env)
    masks = _validate_config(data)
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
            config_path = Path(temp_dir) / "google-services.json"
            descriptor = os.open(config_path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
            with os.fdopen(descriptor, "wb") as config_file:
                config_file.write(data)

            child_env = source_env.copy()
            child_env.pop(BASE64_ENV, None)
            child_env.pop(SOURCE_ENV, None)
            child_env[FILE_ENV] = str(config_path)
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
    if len(argv) < 3 or argv[1] != "android" or argv[2] != "--":
        print("usage: with_config.py android -- <command> [args...]", file=sys.stderr)
        return 2
    try:
        return run(argv[3:])
    except (ConfigError, OSError) as exc:
        print(f"Firebase config error: {exc}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
