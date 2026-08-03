#!/usr/bin/env python3
"""Verify pinned native artifacts/advisories and emit deterministic SBOMs."""

from __future__ import annotations

import argparse
import copy
import datetime as dt
import hashlib
import json
import tempfile
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
LOCK_PATH = ROOT / "third_party" / "ffmpeg-kit-next" / "native-lock.json"
REPORT_DIR = ROOT / "app" / "build" / "reports" / "native-sbom"
BLOCKING_SEVERITIES = {"HIGH", "CRITICAL"}
RESOLVED_STATUSES = {"not_affected", "fixed"}
REQUIRED_REACHABLE_CVES = {
    "CVE-2026-64830",
    "CVE-2026-65703",
    "CVE-2026-66039",
    "CVE-2026-64835",
    "CVE-2026-66040",
    "CVE-2026-58049",
    "CVE-2026-12706",
    "CVE-2026-66037",
    "CVE-2026-66038",
}
REQUIRED_CVE_COMPONENTS = {
    "CVE-2026-64830": {"vobsub_demuxer"},
    "CVE-2026-65703": {"tdsc_decoder"},
    "CVE-2026-66039": {"mace6_decoder"},
    "CVE-2026-64835": {"adpcm_adx_decoder", "adx_demuxer", "aax_demuxer"},
    "CVE-2026-66040": {"png_encoder", "apng_encoder"},
    "CVE-2026-58049": {"rasc_decoder"},
    "CVE-2026-12706": {"rasc_decoder"},
    "CVE-2026-66037": {"iamf_demuxer"},
    "CVE-2026-66038": {"zlib_decoder"},
}
REQUIRED_PATCH_FLAGS = {
    "--disable-demuxer=vobsub",
    "--disable-decoder=tdsc",
    "--disable-decoder=mace6",
    "--disable-decoder=adpcm_adx",
    "--disable-demuxer=adx",
    "--disable-demuxer=aax",
    "--disable-encoder=png",
    "--disable-encoder=apng",
    "--disable-decoder=rasc",
    "--disable-demuxer=iamf",
    "--disable-decoder=zlib",
}


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def validate(lock: dict, root: Path = ROOT, today: dt.date | None = None) -> None:
    if lock.get("schemaVersion") != 2:
        raise ValueError("unsupported native lock schema")
    artifact = lock["artifact"]
    artifact_path = root / artifact["path"]
    pom_path = root / artifact["pomPath"]
    if not artifact_path.is_file():
        raise ValueError(f"native artifact missing: {artifact_path}")
    if artifact_path.stat().st_size != artifact["size"]:
        raise ValueError("native artifact size mismatch")
    if sha256(artifact_path) != artifact["sha256"]:
        raise ValueError("native artifact SHA-256 mismatch")
    if (
        not pom_path.is_file()
        or pom_path.stat().st_size != artifact["pomSize"]
        or sha256(pom_path) != artifact["pomSha256"]
    ):
        raise ValueError("native POM SHA-256 mismatch")

    source = lock.get("source", {})
    required_source_fields = {
        "repository",
        "commit",
        "ffmpegRevision",
        "ffmpegVersion",
        "securityPatch",
        "securityPatchSha256",
        "builtAt",
        "reviewUntil",
    }
    if not required_source_fields.issubset(source) or any(
        not source.get(field) for field in required_source_fields
    ):
        raise ValueError("native source provenance is incomplete")
    patch_path = root / source["securityPatch"]
    if not patch_path.is_file() or sha256(patch_path) != source["securityPatchSha256"]:
        raise ValueError("native security patch SHA-256 mismatch")

    security = lock.get("security", {})
    if security.get("profile") != "clearcut-disabled-format-surface":
        raise ValueError("native security profile is missing or unsupported")
    disabled_components = set(security.get("disabledComponents", []))
    if not disabled_components:
        raise ValueError("native security profile has no disabled components")
    if set(security.get("requiredAdvisories", [])) != REQUIRED_REACHABLE_CVES:
        raise ValueError("native security profile does not enumerate every required advisory")
    if security.get("reviewUntil") != source["reviewUntil"]:
        raise ValueError("native security and source review horizons differ")
    patch_text = patch_path.read_text(encoding="utf-8")
    missing_patch_flags = sorted(flag for flag in REQUIRED_PATCH_FLAGS if flag not in patch_text)
    if missing_patch_flags:
        raise ValueError(f"native security patch is missing flags: {', '.join(missing_patch_flags)}")

    components = lock.get("components", [])
    if not components or any(not item.get("name") or not item.get("version") for item in components):
        raise ValueError("native component inventory is incomplete")
    current = today or dt.date.today()
    source_review_until = dt.date.fromisoformat(source["reviewUntil"])
    security_review_until = dt.date.fromisoformat(security["reviewUntil"])
    if source_review_until < current or security_review_until < current:
        raise ValueError("native security review expired")

    advisories = lock.get("advisories", [])
    advisories_by_id = {advisory.get("id"): advisory for advisory in advisories}
    if len(advisories_by_id) != len(advisories):
        raise ValueError("native advisory inventory contains duplicate IDs")
    for required_id in sorted(REQUIRED_REACHABLE_CVES):
        advisory = advisories_by_id.get(required_id)
        if advisory is None:
            raise ValueError(f"required native advisory missing: {required_id}")
        if not advisory.get("rationale"):
            raise ValueError(f"required native advisory has no rationale: {required_id}")
        advisory_components = set(advisory.get("disabledComponents", []))
        if advisory_components != REQUIRED_CVE_COMPONENTS[required_id]:
            raise ValueError(f"native advisory component mapping mismatch: {required_id}")
        if not advisory_components.issubset(disabled_components):
            raise ValueError(f"native advisory is not covered by the disabled profile: {required_id}")

    for advisory in advisories:
        if not advisory.get("id") or not advisory.get("rationale"):
            raise ValueError("native advisory inventory is missing an ID or rationale")
        review_until = dt.date.fromisoformat(advisory["reviewUntil"])
        if review_until < current:
            raise ValueError(f"native advisory review expired: {advisory['id']}")
        severity = advisory["severity"].upper()
        status = advisory["status"].lower()
        if severity in BLOCKING_SEVERITIES and status not in RESOLVED_STATUSES:
            raise ValueError(
                f"unresolved {severity} native advisory: {advisory['id']} status={status}"
            )


def cyclonedx(lock: dict) -> dict:
    components = []
    for item in lock["components"]:
        license_entry = (
            {"expression": item["license"]}
            if " WITH " in item["license"]
            else {"license": {"id": item["license"]}}
        )
        component = {
            "type": "library",
            "bom-ref": item["purl"],
            "name": item["name"],
            "version": item["version"],
            "purl": item["purl"],
            "licenses": [license_entry],
        }
        if item["name"] == "ffmpeg-kit-next":
            component["hashes"] = [{"alg": "SHA-256", "content": lock["artifact"]["sha256"]}]
        components.append(component)
    serial_seed = hashlib.sha256(
        json.dumps(components, sort_keys=True, separators=(",", ":")).encode()
    ).hexdigest()
    return {
        "bomFormat": "CycloneDX",
        "specVersion": "1.6",
        "serialNumber": f"urn:uuid:{serial_seed[:8]}-{serial_seed[8:12]}-{serial_seed[12:16]}-{serial_seed[16:20]}-{serial_seed[20:32]}",
        "version": 1,
        "components": components,
    }


def spdx(lock: dict) -> dict:
    packages = []
    for index, item in enumerate(lock["components"], start=1):
        packages.append({
            "SPDXID": f"SPDXRef-Package-{index}",
            "name": item["name"],
            "versionInfo": item["version"],
            "downloadLocation": "NOASSERTION",
            "filesAnalyzed": False,
            "licenseConcluded": item["license"],
            "licenseDeclared": item["license"],
            "externalRefs": [{
                "referenceCategory": "PACKAGE-MANAGER",
                "referenceType": "purl",
                "referenceLocator": item["purl"],
            }],
        })
    namespace_hash = hashlib.sha256(
        json.dumps(packages, sort_keys=True, separators=(",", ":")).encode()
    ).hexdigest()
    return {
        "spdxVersion": "SPDX-2.3",
        "dataLicense": "CC0-1.0",
        "SPDXID": "SPDXRef-DOCUMENT",
        "name": "ClearCut-native-dependencies",
        "documentNamespace": f"https://novacut.local/spdx/{namespace_hash}",
        "creationInfo": {
            "created": lock["source"]["builtAt"],
            "creators": ["Organization: ClearCut"],
        },
        "packages": packages,
    }


def write_json(path: Path, value: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def self_test(lock: dict) -> None:
    validate(lock)
    affected = copy.deepcopy(lock)
    affected_id = sorted(REQUIRED_REACHABLE_CVES)[0]
    next(item for item in affected["advisories"] if item["id"] == affected_id)["status"] = "affected"
    try:
        validate(affected)
        raise AssertionError("affected HIGH advisory was accepted")
    except ValueError as error:
        if "unresolved HIGH" not in str(error):
            raise
    stale = copy.deepcopy(lock)
    stale["source"]["reviewUntil"] = "2000-01-01"
    stale["security"]["reviewUntil"] = "2000-01-01"
    try:
        validate(stale)
        raise AssertionError("expired advisory review was accepted")
    except ValueError as error:
        if "review expired" not in str(error):
            raise
    missing = copy.deepcopy(lock)
    missing["advisories"] = [
        item for item in missing["advisories"] if item["id"] != affected_id
    ]
    try:
        validate(missing)
        raise AssertionError("missing required advisory was accepted")
    except ValueError as error:
        if "required native advisory missing" not in str(error):
            raise
    with tempfile.TemporaryDirectory() as directory:
        first = Path(directory) / "first.json"
        second = Path(directory) / "second.json"
        write_json(first, cyclonedx(lock))
        write_json(second, cyclonedx(lock))
        if first.read_bytes() != second.read_bytes():
            raise AssertionError("CycloneDX output is not deterministic")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args()
    lock = json.loads(LOCK_PATH.read_text(encoding="utf-8"))
    if args.self_test:
        self_test(lock)
        print("native supply-chain self-test passed")
        return 0
    validate(lock)
    write_json(REPORT_DIR / "cyclonedx.json", cyclonedx(lock))
    write_json(REPORT_DIR / "spdx.json", spdx(lock))
    print(f"verified {len(lock['components'])} native components; SBOMs: {REPORT_DIR}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
