#!/usr/bin/env python3
"""Build a deterministic applicability manifest from an exported container rootfs."""

from __future__ import annotations

import argparse
import hashlib
import io
import json
import posixpath
import stat
import tarfile
import zipfile
from pathlib import Path
from typing import Any


SCHEMA = "vlrgg-observability-runtime-proof-v1"
EXCLUDED_PATHS = {
    ".dockerenv",
    "etc/hostname",
    "etc/hosts",
    "etc/resolv.conf",
}
MOUNT_ROOTS = {"proc", "sys", "dev"}


class ManifestError(ValueError):
    pass


def _field(digest: "hashlib._Hash", value: bytes) -> None:
    digest.update(len(value).to_bytes(8, "big"))
    digest.update(value)


def _safe_path(raw: str, *, allow_root: bool = False) -> str:
    if not raw or "\x00" in raw or "\\" in raw or raw.startswith("/"):
        raise ManifestError(f"unsafe path: {raw!r}")
    parts = raw.split("/")
    if any(part in ("", ".", "..") for part in parts):
        raise ManifestError(f"unsafe path: {raw!r}")
    normalized = posixpath.normpath(raw)
    if normalized in ("", ".") and not allow_root:
        raise ManifestError(f"unsafe path: {raw!r}")
    if normalized == ".." or normalized.startswith("../"):
        raise ManifestError(f"escaping path: {raw!r}")
    return normalized


def _tar_path(raw: str) -> str:
    if raw in (".", "./"):
        return "."
    if raw.startswith("/"):
        raise ManifestError(f"unsafe tar path: {raw!r}")
    stripped = raw.removeprefix("./")
    if not stripped:
        return "."
    return _safe_path(stripped)


def _link_target(path: str, raw_target: str) -> str:
    if not raw_target or "\x00" in raw_target or "\\" in raw_target:
        raise ManifestError(f"unsafe link target for {path}")
    if raw_target.startswith("/"):
        candidate = raw_target.lstrip("/")
    else:
        candidate = posixpath.join(posixpath.dirname(path), raw_target)
    normalized = posixpath.normpath(candidate)
    if normalized in ("", "."):
        return "."
    if normalized == ".." or normalized.startswith("../"):
        raise ManifestError(f"escaping link target for {path}")
    return _safe_path(normalized)


def _hardlink_target(path: str, raw_target: str) -> str:
    if not raw_target or "\x00" in raw_target or "\\" in raw_target or raw_target.startswith("/"):
        raise ManifestError(f"unsafe hardlink target for {path}")
    target = posixpath.normpath(raw_target)
    if target in ("", ".", "..") or target.startswith("../"):
        raise ManifestError(f"escaping hardlink target for {path}")
    return _safe_path(target)


def _zip_path(raw: str) -> str:
    directory = raw.endswith("/")
    name = _safe_path(raw[:-1] if directory else raw)
    return f"{name}/" if directory else name


def _zip_manifest(data: bytes, archive_path: str) -> tuple[str, list[dict[str, Any]]]:
    digest = hashlib.sha256()
    entries: list[tuple[str, bytes]] = []
    seen: set[str] = set()
    try:
        with zipfile.ZipFile(io.BytesIO(data)) as archive:
            for info in archive.infolist():
                name = _zip_path(info.filename)
                if name in seen:
                    raise ManifestError(f"duplicate ZIP path in {archive_path}: {name}")
                seen.add(name)
                mode = (info.external_attr >> 16) & 0xFFFF
                if stat.S_ISLNK(mode):
                    raise ManifestError(f"ZIP links are not allowed in {archive_path}: {name}")
                file_type = stat.S_IFMT(mode)
                if file_type not in (0, stat.S_IFREG, stat.S_IFDIR):
                    raise ManifestError(f"unsupported ZIP entry type in {archive_path}: {name}")
                if info.flag_bits & 0x1:
                    raise ManifestError(f"encrypted ZIP entry in {archive_path}: {name}")
                content = b"" if info.is_dir() else archive.read(info)
                entries.append((name, content))
    except (OSError, zipfile.BadZipFile, RuntimeError) as error:
        raise ManifestError(f"invalid ZIP archive: {archive_path}") from error

    details: list[dict[str, Any]] = []
    for name, content in sorted(entries):
        content_sha = hashlib.sha256(content).hexdigest()
        _field(digest, name.encode())
        _field(digest, content)
        details.append({"path": name, "sha256": content_sha, "size": len(content)})
    return digest.hexdigest(), details


def rootfs_manifest(tar_path: Path) -> tuple[str, list[dict[str, Any]]]:
    records: dict[str, dict[str, Any]] = {}
    hardlinks: list[tuple[str, str]] = []
    seen: set[str] = set()
    try:
        with tarfile.open(tar_path, "r:*") as archive:
            for member in archive:
                path = _tar_path(member.name)
                if path in seen:
                    raise ManifestError(f"duplicate tar path: {path}")
                seen.add(path)
                if path == ".":
                    if not member.isdir():
                        raise ManifestError("root tar entry must be a directory")
                    continue
                root = path.split("/", 1)[0]
                if root in MOUNT_ROOTS:
                    if path != root or not member.isdir():
                        raise ManifestError(f"populated runtime mount tree: {path}")
                if path in EXCLUDED_PATHS:
                    continue
                record: dict[str, Any] = {
                    "path": path,
                    "mode": member.mode,
                    "uid": member.uid,
                    "gid": member.gid,
                }
                if member.isdir():
                    record["type"] = "directory"
                elif member.isfile():
                    source = archive.extractfile(member)
                    if source is None:
                        raise ManifestError(f"missing file content: {path}")
                    content = source.read()
                    record["type"] = "file"
                    if path == "app/validation.jar" or (
                        path.startswith("app/lib/") and path.endswith(".jar") and "/" not in path[8:]
                    ):
                        zip_sha, entries = _zip_manifest(content, path)
                        record["contentKind"] = "normalizedZip"
                        record["sha256"] = zip_sha
                        record["size"] = sum(entry["size"] for entry in entries)
                        record["zipEntries"] = entries
                    else:
                        record["contentKind"] = "raw"
                        record["sha256"] = hashlib.sha256(content).hexdigest()
                        record["size"] = len(content)
                elif member.issym():
                    record["type"] = "symlink"
                    record["target"] = member.linkname
                    record["resolvedTarget"] = _link_target(path, member.linkname)
                elif member.islnk():
                    target = _hardlink_target(path, member.linkname)
                    record["type"] = "hardlink"
                    record["target"] = member.linkname
                    record["resolvedTarget"] = target
                    hardlinks.append((path, target))
                else:
                    raise ManifestError(f"unsupported tar entry type: {path}")
                records[path] = record
    except (OSError, tarfile.TarError) as error:
        raise ManifestError("invalid rootfs tar") from error

    for path, target in hardlinks:
        target_record = records.get(target)
        if target_record is None or target_record["type"] != "file":
            raise ManifestError(f"invalid hardlink {path} -> {target}")

    digest = hashlib.sha256()
    ordered = [records[path] for path in sorted(records)]
    for record in ordered:
        canonical = {key: record[key] for key in record if key != "zipEntries"}
        _field(digest, json.dumps(canonical, sort_keys=True, separators=(",", ":")).encode())
    return digest.hexdigest(), ordered


def config_manifest(config_path: Path) -> tuple[str, dict[str, Any]]:
    try:
        raw = json.loads(config_path.read_text())
    except (OSError, json.JSONDecodeError) as error:
        raise ManifestError("invalid image config JSON") from error
    if isinstance(raw, list):
        if len(raw) != 1 or not isinstance(raw[0], dict):
            raise ManifestError("image config inspect JSON must contain one object")
        raw = raw[0].get("Config", raw[0])
    elif isinstance(raw, dict) and isinstance(raw.get("Config"), dict):
        raw = raw["Config"]
    if not isinstance(raw, dict):
        raise ManifestError("image config must be an object")
    selected = {
        "Entrypoint": raw.get("Entrypoint"),
        "Cmd": raw.get("Cmd"),
        "User": raw.get("User", ""),
        "WorkingDir": raw.get("WorkingDir", ""),
        "Env": raw.get("Env", []),
    }
    if not isinstance(selected["Env"], list) or not all(isinstance(v, str) for v in selected["Env"]):
        raise ManifestError("image Env must be a string array")
    for key in ("Entrypoint", "Cmd"):
        value = selected[key]
        if value is not None and (not isinstance(value, list) or not all(isinstance(v, str) for v in value)):
            raise ManifestError(f"image {key} must be null or a string array")
    if not isinstance(selected["User"], str) or not isinstance(selected["WorkingDir"], str):
        raise ManifestError("image User and WorkingDir must be strings")
    encoded = json.dumps(selected, sort_keys=True, separators=(",", ":")).encode()
    return hashlib.sha256(encoded).hexdigest(), selected


def build_manifest(rootfs: Path, config: Path, dockerfile: Path) -> dict[str, Any]:
    rootfs_sha, entries = rootfs_manifest(rootfs)
    config_sha, selected_config = config_manifest(config)
    try:
        dockerfile_bytes = dockerfile.read_bytes()
    except OSError as error:
        raise ManifestError("could not read validation Dockerfile") from error
    dockerfile_sha = hashlib.sha256(dockerfile_bytes).hexdigest()
    combined = hashlib.sha256()
    for name, value in (
        ("schema", SCHEMA),
        ("rootfsSha256", rootfs_sha),
        ("configSha256", config_sha),
        ("validationDockerfileSha256", dockerfile_sha),
    ):
        _field(combined, name.encode())
        _field(combined, value.encode())
    return {
        "schema": SCHEMA,
        "rootfsSha256": rootfs_sha,
        "configSha256": config_sha,
        "validationDockerfileSha256": dockerfile_sha,
        "applicabilitySha256": combined.hexdigest(),
        "rootfsEntries": entries,
        "imageConfig": selected_config,
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--rootfs", type=Path, required=True)
    parser.add_argument("--config", type=Path, required=True)
    parser.add_argument("--dockerfile", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()

    manifest = build_manifest(args.rootfs, args.config, args.dockerfile)
    args.output.write_text(json.dumps(manifest, sort_keys=True, separators=(",", ":")) + "\n")
    print(
        "observability-runtime-proof "
        f"rootfsSha256={manifest['rootfsSha256']} "
        f"configSha256={manifest['configSha256']} "
        f"validationDockerfileSha256={manifest['validationDockerfileSha256']} "
        f"applicabilitySha256={manifest['applicabilitySha256']}"
    )


if __name__ == "__main__":
    try:
        main()
    except ManifestError as error:
        raise SystemExit("runtime proof rejected") from error
