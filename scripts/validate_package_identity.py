#!/usr/bin/env python3
"""Validate ClearCut's frozen Android package identity and migration contract."""
from __future__ import annotations

import argparse
import copy
import json
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
IDENTITY = ROOT / "scripts" / "package_identity.json"
ANDROID_NS = "http://schemas.android.com/apk/res/android"
ANDROID_ATTR = lambda name: f"{{{ANDROID_NS}}}{name}"


class PackageIdentityError(RuntimeError):
    pass


def rel(path: Path, root: Path = ROOT) -> str:
    return path.relative_to(root).as_posix()


def read_text(path: Path, root: Path = ROOT) -> str:
    if not path.is_file():
        raise PackageIdentityError(f"missing required identity file: {rel(path, root)}")
    return path.read_text(encoding="utf-8")


def load_registry(root: Path = ROOT) -> dict[str, Any]:
    path = root / IDENTITY.relative_to(ROOT)
    try:
        data = json.loads(read_text(path, root))
    except json.JSONDecodeError as error:
        raise PackageIdentityError(f"invalid package identity JSON: {error}") from error
    if not isinstance(data, dict):
        raise PackageIdentityError("package identity registry must be an object")
    validate_registry_shape(data)
    return data


def validate_registry_shape(data: dict[str, Any]) -> None:
    if data.get("schemaVersion") != 1:
        raise PackageIdentityError("package identity registry has unsupported schemaVersion")

    required_strings = (
        "publicProductName",
        "retiredPublicProductName",
        "applicationId",
        "namespace",
        "sourcePackage",
    )
    for key in required_strings:
        value = data.get(key)
        if not isinstance(value, str) or not value.strip():
            raise PackageIdentityError(f"package identity field {key!r} must be a non-empty string")

    if data["publicProductName"] == data["retiredPublicProductName"]:
        raise PackageIdentityError("public and retired product names must differ")
    if data["applicationId"] != data["namespace"] or data["namespace"] != data["sourcePackage"]:
        raise PackageIdentityError("applicationId, namespace, and sourcePackage must remain aligned")
    if not re.fullmatch(r"[a-z][a-z0-9_]*(?:\.[a-z][a-z0-9_]*)+", data["applicationId"]):
        raise PackageIdentityError("applicationId is not a valid Android package name")

    policy = data.get("migrationPolicy")
    if not isinstance(policy, dict):
        raise PackageIdentityError("migrationPolicy must be an object")
    expected_policy = {
        "applicationId": "retain-existing-lineage",
        "namespace": "retain-existing-lineage",
        "signingCertificate": "retain-pinned-release-identity",
        "identityChange": "new-install-with-explicit-export-import",
    }
    if policy != expected_policy:
        raise PackageIdentityError(f"migrationPolicy changed unexpectedly: {policy!r}")

    variants = data.get("nonReleaseVariants")
    streaming = variants.get("streaming") if isinstance(variants, dict) else None
    if not isinstance(streaming, dict) or streaming.get("applicationIdSuffix") != ".streaming":
        raise PackageIdentityError("streaming must remain the non-release .streaming variant")

    authorities = data.get("providerAuthorities")
    if authorities != [
        "${applicationId}.androidx-startup",
        "${applicationId}.fileprovider",
    ]:
        raise PackageIdentityError("provider authorities must remain application-scoped")

    shortcut = data.get("shortcutTarget")
    if not isinstance(shortcut, dict):
        raise PackageIdentityError("shortcutTarget must be an object")
    if shortcut.get("package") != data["applicationId"]:
        raise PackageIdentityError("shortcut target package must match applicationId")
    if shortcut.get("class") != f"{data['namespace']}.MainActivity":
        raise PackageIdentityError("shortcut target class must remain MainActivity in the package namespace")
    actions = shortcut.get("actions")
    if actions != [
        f"{data['applicationId']}.action.NEW_PROJECT",
        f"{data['applicationId']}.action.OPEN_RECENT",
    ]:
        raise PackageIdentityError("static shortcut actions must remain stable")

    associations = data.get("archiveAssociations")
    if not isinstance(associations, dict):
        raise PackageIdentityError("archiveAssociations must be an object")
    if associations.get("extensions") != [".clearcut", ".clearcut-template"]:
        raise PackageIdentityError("ClearCut archive extensions must remain stable")
    if associations.get("mimeTypes") != [
        "application/octet-stream",
        "application/json",
        "application/zip",
        "application/xml",
        "text/xml",
        "text/plain",
    ]:
        raise PackageIdentityError("ClearCut archive MIME associations must remain stable")

    surfaces = data.get("publicSurfacePaths")
    if not isinstance(surfaces, list) or not surfaces or any(not isinstance(path, str) for path in surfaces):
        raise PackageIdentityError("publicSurfacePaths must be a non-empty list of paths")
    phrases = data.get("readmePolicyPhrases")
    if not isinstance(phrases, list) or not phrases or any(not isinstance(phrase, str) for phrase in phrases):
        raise PackageIdentityError("readmePolicyPhrases must be a non-empty list of strings")


def gradle_identity(root: Path) -> tuple[str, str, str]:
    text = read_text(root / "app/build.gradle.kts", root)
    namespace = re.search(r"(?m)^\s*namespace\s*=\s*\"([^\"]+)\"", text)
    application_id = re.search(r"(?m)^\s*applicationId\s*=\s*\"([^\"]+)\"", text)
    streaming_suffix = re.search(r"(?m)^\s*applicationIdSuffix\s*=\s*\"([^\"]+)\"", text)
    if not namespace or not application_id or not streaming_suffix:
        raise PackageIdentityError("Gradle must declare namespace, applicationId, and streaming suffix")
    return namespace.group(1), application_id.group(1), streaming_suffix.group(1)


def parse_xml(path: Path, root: Path) -> ET.Element:
    try:
        return ET.parse(path).getroot()
    except (ET.ParseError, OSError) as error:
        raise PackageIdentityError(f"could not parse {rel(path, root)}: {error}") from error


def validate_manifest(root: Path, registry: dict[str, Any]) -> None:
    manifest_path = root / "app/src/main/AndroidManifest.xml"
    manifest = parse_xml(manifest_path, root)
    application = manifest.find("application")
    if application is None:
        raise PackageIdentityError("AndroidManifest.xml has no application element")

    authorities = {
        provider.get(ANDROID_ATTR("authorities"))
        for provider in application.findall("provider")
        if provider.get(ANDROID_ATTR("authorities"))
    }
    expected_authorities = set(registry["providerAuthorities"])
    if authorities != expected_authorities:
        raise PackageIdentityError(
            f"manifest provider authorities {sorted(authorities)} do not match {sorted(expected_authorities)}"
        )

    activity = next(
        (
            item
            for item in application.findall("activity")
            if item.get(ANDROID_ATTR("name")) == ".MainActivity"
        ),
        None,
    )
    if activity is None:
        raise PackageIdentityError("manifest must declare .MainActivity")

    actions = {
        action.get(ANDROID_ATTR("name"))
        for intent_filter in activity.findall("intent-filter")
        for action in intent_filter.findall("action")
        if action.get(ANDROID_ATTR("name"))
    }
    expected_shortcuts = set(registry["shortcutTarget"]["actions"])
    if not expected_shortcuts.issubset(actions):
        raise PackageIdentityError("manifest is missing a registered static shortcut action")

    mime_types = {
        data.get(ANDROID_ATTR("mimeType"))
        for intent_filter in activity.findall("intent-filter")
        for data in intent_filter.findall("data")
        if data.get(ANDROID_ATTR("mimeType"))
    }
    expected_mimes = set(registry["archiveAssociations"]["mimeTypes"])
    if not expected_mimes.issubset(mime_types):
        raise PackageIdentityError("manifest is missing a registered archive MIME association")


def validate_shortcuts(root: Path, registry: dict[str, Any]) -> None:
    shortcut_root = parse_xml(root / "app/src/main/res/xml/shortcuts.xml", root)
    target = registry["shortcutTarget"]
    intents = shortcut_root.findall(".//intent")
    if len(intents) != len(target["actions"]):
        raise PackageIdentityError("shortcuts.xml must keep one intent for each registered static shortcut")
    for intent in intents:
        if intent.get(ANDROID_ATTR("package")) is not None:
            raise PackageIdentityError("shortcuts.xml must use targetPackage, not an invalid package attribute")
        if intent.get(ANDROID_ATTR("targetPackage")) != target["package"]:
            raise PackageIdentityError("shortcut targetPackage changed from the frozen application ID")
        if intent.get(ANDROID_ATTR("targetClass")) != target["class"]:
            raise PackageIdentityError("shortcut targetClass changed from the frozen namespace")
    actual_actions = [intent.get(ANDROID_ATTR("action")) for intent in intents]
    if actual_actions != target["actions"]:
        raise PackageIdentityError("shortcut action order or values changed")


def validate_archive_parser(root: Path, registry: dict[str, Any]) -> None:
    parser_sources = "\n".join(
        read_text(root / relative, root)
        for relative in (
            "app/src/main/java/com/novacut/editor/engine/IncomingDocumentIntentParser.kt",
            "app/src/main/java/com/novacut/editor/engine/PluginRegistry.kt",
        )
    ).lower()
    for extension in registry["archiveAssociations"]["extensions"]:
        if extension.lower() not in parser_sources:
            raise PackageIdentityError(f"archive parser no longer recognizes {extension}")


def validate_app_labels(root: Path, registry: dict[str, Any]) -> None:
    resource_root = root / "app/src/main/res"
    for strings_path in sorted(resource_root.glob("values*/strings.xml")):
        strings = parse_xml(strings_path, root)
        labels = [
            element.text or ""
            for element in strings.findall("string")
            if element.get("name") == "app_name"
        ]
        if labels != [registry["publicProductName"]]:
            raise PackageIdentityError(f"{rel(strings_path, root)} must expose the ClearCut app label")


def validate_public_surfaces(root: Path, registry: dict[str, Any]) -> None:
    retired = registry["retiredPublicProductName"]
    for relative in registry["publicSurfacePaths"]:
        path = root / relative
        text = read_text(path, root)
        if retired in text:
            raise PackageIdentityError(f"{relative} still exposes retired public branding {retired!r}")

    readme = read_text(root / "README.md", root)
    for phrase in registry["readmePolicyPhrases"]:
        if phrase not in readme:
            raise PackageIdentityError(f"README.md is missing package-policy phrase: {phrase}")


def validate(root: Path = ROOT) -> None:
    registry = load_registry(root)
    namespace, application_id, streaming_suffix = gradle_identity(root)
    if namespace != registry["namespace"]:
        raise PackageIdentityError(f"Gradle namespace {namespace!r} disagrees with registry")
    if application_id != registry["applicationId"]:
        raise PackageIdentityError(f"Gradle applicationId {application_id!r} disagrees with registry")
    if streaming_suffix != registry["nonReleaseVariants"]["streaming"]["applicationIdSuffix"]:
        raise PackageIdentityError("Gradle streaming applicationIdSuffix disagrees with registry")

    validate_manifest(root, registry)
    validate_shortcuts(root, registry)
    validate_archive_parser(root, registry)
    validate_app_labels(root, registry)
    validate_public_surfaces(root, registry)


def run_self_test(root: Path = ROOT) -> None:
    registry = load_registry(root)
    validate_registry_shape(registry)

    broken = copy.deepcopy(registry)
    broken["applicationId"] = "com.clearcut.editor"
    try:
        validate_registry_shape(broken)
    except PackageIdentityError:
        pass
    else:
        raise AssertionError("self-test expected application identity drift to fail")

    broken = copy.deepcopy(registry)
    broken["migrationPolicy"]["identityChange"] = "silent-in-place-rename"
    try:
        validate_registry_shape(broken)
    except PackageIdentityError:
        pass
    else:
        raise AssertionError("self-test expected migration-policy drift to fail")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args()
    try:
        if args.self_test:
            run_self_test()
            print("package identity self-tests passed.")
        else:
            validate()
            print("Package identity and migration policy verified.")
    except (PackageIdentityError, AssertionError) as error:
        print(f"package identity validation failed: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
