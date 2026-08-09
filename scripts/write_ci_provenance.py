#!/usr/bin/env python3
"""Write deterministic CI artifact hashes and build provenance metadata."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import subprocess
import tempfile
from pathlib import Path
from typing import Iterable


ROOT = Path(__file__).resolve().parents[1]
MANIFEST_SCHEMA = "com.novacut.ci-artifact-manifest.v1"
PROVENANCE_SCHEMA = "com.novacut.ci-build-provenance.v1"
ARTIFACT_SUFFIXES = {".aab", ".apk", ".sha256", ".signing-cert-sha256"}


class ProvenanceError(RuntimeError):
    pass


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def relative(path: Path, root: Path) -> str:
    return path.relative_to(root).as_posix()


def file_record(path: Path, root: Path, kind: str) -> dict[str, object]:
    if not path.is_file():
        raise ProvenanceError(f"missing {kind} file: {relative(path, root)}")
    return {
        "kind": kind,
        "path": relative(path, root),
        "sizeBytes": path.stat().st_size,
        "sha256": sha256(path),
    }


def artifact_paths(artifact_root: Path) -> list[Path]:
    if not artifact_root.is_dir():
        raise ProvenanceError(f"artifact root does not exist: {artifact_root}")
    paths = [
        path
        for path in artifact_root.rglob("*")
        if path.is_file()
        and (
            path.suffix.lower() in ARTIFACT_SUFFIXES
            or path.name == "output-metadata.json"
        )
    ]
    if not paths:
        raise ProvenanceError(f"artifact root has no APK/AAB outputs: {artifact_root}")
    return sorted(paths)


def git_revision(root: Path) -> str:
    result = subprocess.run(
        ["git", "rev-parse", "HEAD"],
        cwd=root,
        check=False,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.DEVNULL,
    )
    return result.stdout.strip() if result.returncode == 0 else "unknown"


def write_json(path: Path, value: dict[str, object]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(
        json.dumps(value, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )


def build_manifest(
    root: Path,
    artifact_root: Path,
    evidence_files: Iterable[Path],
    source_revision: str,
) -> dict[str, object]:
    artifacts = [file_record(path, root, "artifact") for path in artifact_paths(artifact_root)]
    evidence = [file_record(path, root, "evidence") for path in sorted(evidence_files)]
    return {
        "schema": MANIFEST_SCHEMA,
        "sourceRevision": source_revision,
        "artifacts": artifacts,
        "evidence": evidence,
    }


def verify_manifest(manifest_path: Path, root: Path) -> None:
    try:
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        raise ProvenanceError(f"could not read artifact manifest: {error}") from error
    if manifest.get("schema") != MANIFEST_SCHEMA:
        raise ProvenanceError("artifact manifest schema is unsupported")
    records = [*manifest.get("artifacts", []), *manifest.get("evidence", [])]
    if not records:
        raise ProvenanceError("artifact manifest contains no file records")
    for record in records:
        if not isinstance(record, dict):
            raise ProvenanceError("artifact manifest contains a malformed file record")
        path = root / str(record.get("path", ""))
        if not path.is_file():
            raise ProvenanceError(f"manifested file is missing: {record.get('path')}")
        if record.get("sizeBytes") != path.stat().st_size:
            raise ProvenanceError(f"manifested size is stale: {record.get('path')}")
        if record.get("sha256") != sha256(path):
            raise ProvenanceError(f"manifested SHA-256 is stale: {record.get('path')}")


def build_provenance(
    root: Path,
    manifest_path: Path,
    source_revision: str,
    workflow: str,
    event: str,
    ref: str,
    run_id: str,
    run_attempt: str,
    job: str,
) -> dict[str, object]:
    material_paths = [
        root / "gradle" / "wrapper" / "gradle-wrapper.properties",
        root / "gradle" / "libs.versions.toml",
        root / "scripts" / "capability_registry.json",
        root / "third_party" / "ffmpeg-kit-next" / "native-lock.json",
    ]
    materials = [
        file_record(path, root, "material")
        for path in material_paths
        if path.is_file()
    ]
    return {
        "schema": PROVENANCE_SCHEMA,
        "builder": {
            "id": "github-actions" if workflow else "local",
            "workflow": workflow or "local",
            "job": job or "local",
        },
        "source": {
            "revision": source_revision,
            "ref": ref or "local",
        },
        "invocation": {
            "event": event or "local",
            "runId": run_id or "local",
            "runAttempt": run_attempt or "local",
        },
        "materials": materials,
        "artifactManifest": {
            "path": relative(manifest_path, root),
            "sha256": sha256(manifest_path),
        },
    }


def write_reports(
    root: Path,
    artifact_root: Path,
    report_dir: Path,
    evidence_files: Iterable[Path],
    source_revision: str,
    workflow: str = "",
    event: str = "",
    ref: str = "",
    run_id: str = "",
    run_attempt: str = "",
    job: str = "",
) -> tuple[Path, Path]:
    manifest_path = report_dir / "artifact-manifest.json"
    provenance_path = report_dir / "build-provenance.json"
    manifest = build_manifest(root, artifact_root, evidence_files, source_revision)
    write_json(manifest_path, manifest)
    provenance = build_provenance(
        root,
        manifest_path,
        source_revision,
        workflow,
        event,
        ref,
        run_id,
        run_attempt,
        job,
    )
    write_json(provenance_path, provenance)
    verify_manifest(manifest_path, root)
    return manifest_path, provenance_path


def run_self_tests() -> None:
    with tempfile.TemporaryDirectory() as temp:
        root = Path(temp)
        apk = root / "app" / "build" / "outputs" / "apk" / "debug" / "app-universal-debug.apk"
        metadata = apk.parent / "output-metadata.json"
        sbom = root / "app" / "build" / "reports" / "resolved-sbom" / "cyclonedx.json"
        apk.parent.mkdir(parents=True)
        apk.write_bytes(b"clearcut-ci-apk")
        metadata.write_text('{"elements": []}\n', encoding="utf-8")
        sbom.parent.mkdir(parents=True)
        sbom.write_text('{"components": []}\n', encoding="utf-8")

        report_dir = root / "app" / "build" / "reports" / "ci-provenance"
        manifest_path, provenance_path = write_reports(
            root=root,
            artifact_root=apk.parents[2],
            report_dir=report_dir,
            evidence_files=[sbom],
            source_revision="abc123",
            workflow="self-test",
        )
        first_manifest = manifest_path.read_bytes()
        first_provenance = provenance_path.read_bytes()
        write_reports(
            root=root,
            artifact_root=apk.parents[2],
            report_dir=report_dir,
            evidence_files=[sbom],
            source_revision="abc123",
            workflow="self-test",
        )
        if manifest_path.read_bytes() != first_manifest:
            raise ProvenanceError("identical inputs produced a non-deterministic manifest")
        if provenance_path.read_bytes() != first_provenance:
            raise ProvenanceError("identical inputs produced non-deterministic provenance")

        apk.write_bytes(b"tampered")
        try:
            verify_manifest(manifest_path, root)
        except ProvenanceError:
            return
        raise ProvenanceError("self-test expected tampered artifact verification to fail")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--self-test", action="store_true")
    parser.add_argument("--root", type=Path, default=ROOT)
    parser.add_argument("--artifact-root", type=Path)
    parser.add_argument("--report-dir", type=Path)
    parser.add_argument("--evidence-file", action="append", default=[])
    parser.add_argument("--source-revision", default="")
    parser.add_argument("--workflow", default="")
    parser.add_argument("--event", default="")
    parser.add_argument("--ref", default="")
    parser.add_argument("--run-id", default="")
    parser.add_argument("--run-attempt", default="")
    parser.add_argument("--job", default="")
    args = parser.parse_args()
    if args.self_test:
        run_self_tests()
        print("CI provenance self-tests passed.")
        return 0

    root = args.root.resolve()
    artifact_root = (args.artifact_root or root / "app" / "build" / "outputs" / "apk").resolve()
    report_dir = (args.report_dir or root / "app" / "build" / "reports" / "ci-provenance").resolve()
    evidence_files = [(root / path).resolve() for path in args.evidence_file]
    source_revision = (
        args.source_revision.strip()
        or os.environ.get("CLEARCUT_SOURCE_REVISION", "").strip()
        or os.environ.get("GITHUB_SHA", "").strip()
        or git_revision(root)
    )
    try:
        manifest_path, provenance_path = write_reports(
            root=root,
            artifact_root=artifact_root,
            report_dir=report_dir,
            evidence_files=evidence_files,
            source_revision=source_revision,
            workflow=args.workflow or os.environ.get("GITHUB_WORKFLOW", ""),
            event=args.event or os.environ.get("GITHUB_EVENT_NAME", ""),
            ref=args.ref or os.environ.get("GITHUB_REF", ""),
            run_id=args.run_id or os.environ.get("GITHUB_RUN_ID", ""),
            run_attempt=args.run_attempt or os.environ.get("GITHUB_RUN_ATTEMPT", ""),
            job=args.job or os.environ.get("GITHUB_JOB", ""),
        )
    except ProvenanceError as error:
        print(f"CI provenance failed: {error}")
        return 1

    print(f"CI artifact manifest: {relative(manifest_path, root)}")
    print(f"CI build provenance: {relative(provenance_path, root)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
