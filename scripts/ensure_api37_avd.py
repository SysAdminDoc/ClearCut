#!/usr/bin/env python3
"""Provision and optionally launch ClearCut's API 37 test AVD."""

from __future__ import annotations

import argparse
import os
import subprocess
import sys
import tempfile
import time
from pathlib import Path

AVD_NAME = "clearcut-api37-ps16k"
SYSTEM_IMAGE = "system-images;android-37.0;google_apis_ps16k;x86_64"
DEVICE_NAME = "pixel_6"
BOOT_TIMEOUT_SECONDS = 300
DATA_PARTITION_SIZE_MB = 2047
RAM_SIZE_MB = 4096
DEVICE_READY_STABILITY_SECONDS = 10


class AvdError(RuntimeError):
    """Raised when the Android SDK cannot provision or start the test AVD."""


def find_sdk_root() -> Path:
    for variable in ("ANDROID_HOME", "ANDROID_SDK_ROOT"):
        value = os.environ.get(variable)
        if value:
            root = Path(value).expanduser()
            if root.is_dir():
                return root

    candidates = (
        Path.home() / "AppData" / "Local" / "Android" / "Sdk",
        Path.home() / "Library" / "Android" / "sdk",
        Path.home() / "Android" / "Sdk",
    )
    for root in candidates:
        if root.is_dir():
            return root
    raise AvdError("Android SDK not found; set ANDROID_HOME or ANDROID_SDK_ROOT")


def find_java_home() -> Path | None:
    configured = os.environ.get("JAVA_HOME")
    if configured:
        candidate = Path(configured).expanduser()
        if (candidate / "bin" / ("java.exe" if os.name == "nt" else "java")).is_file():
            return candidate

    candidates = (
        Path(os.environ.get("ProgramFiles", "C:/Program Files"))
        / "Android"
        / "Android Studio"
        / "jbr",
        Path(os.environ.get("ProgramFiles", "C:/Program Files")) / "Java" / "jdk-21",
    )
    for candidate in candidates:
        if (candidate / "bin" / ("java.exe" if os.name == "nt" else "java")).is_file():
            return candidate
    return None


def command_environment() -> dict[str, str]:
    environment = os.environ.copy()
    java_home = find_java_home()
    if java_home is None:
        environment.pop("JAVA_HOME", None)
    else:
        environment["JAVA_HOME"] = str(java_home)
    return environment


def find_tool(sdk_root: Path, name: str) -> Path:
    candidates = (
        sdk_root / "cmdline-tools" / "latest" / "bin" / f"{name}.bat",
        sdk_root / "cmdline-tools" / "latest" / "bin" / name,
        sdk_root / "tools" / "bin" / f"{name}.bat",
        sdk_root / "tools" / "bin" / name,
    )
    for candidate in candidates:
        if candidate.is_file():
            return candidate
    raise AvdError(f"Android SDK tool not found: {name}")


def find_executable(sdk_root: Path, relative: str) -> Path:
    executable = sdk_root / relative
    if executable.is_file():
        return executable
    raise AvdError(f"Android SDK executable not found: {executable}")


def run(
    command: list[str], *, input_text: str | None = None
) -> subprocess.CompletedProcess[str]:
    try:
        return subprocess.run(
            command,
            input=input_text,
            text=True,
            capture_output=True,
            check=True,
            env=command_environment(),
        )
    except FileNotFoundError as error:
        raise AvdError(f"could not execute {command[0]}") from error
    except subprocess.CalledProcessError as error:
        details = (error.stderr or error.stdout or "").strip()
        raise AvdError(
            f"command failed ({error.returncode}): {' '.join(command)}\n{details}"
        ) from error


def avd_manager_command(tool: Path, *arguments: str) -> list[str]:
    # Windows batch files are executable through CreateProcess on supported
    # Python versions; keeping the argument vector intact avoids shell parsing
    # user-controlled AVD names.
    return [str(tool), *arguments]


def avd_exists(avd_manager: Path, name: str) -> bool:
    result = run(avd_manager_command(avd_manager, "list", "avd"))
    return any(
        line.strip().startswith(f"Name: {name}") for line in result.stdout.splitlines()
    )


def avd_home() -> Path:
    configured = os.environ.get("ANDROID_AVD_HOME")
    if configured:
        return Path(configured).expanduser()
    return Path.home() / ".android" / "avd"


def configure_data_partition(name: str) -> None:
    config_path = avd_home() / f"{name}.avd" / "config.ini"
    if not config_path.is_file():
        raise AvdError(f"AVD configuration not found: {config_path}")

    settings = {
        "disk.dataPartition.size": f"{DATA_PARTITION_SIZE_MB}M",
        "hw.ramSize": f"{RAM_SIZE_MB}M",
    }
    lines = config_path.read_text(encoding="utf-8").splitlines()
    updated: list[str] = []
    replaced: set[str] = set()
    for line in lines:
        key = line.partition("=")[0]
        if key in settings:
            if key not in replaced:
                updated.append(f"{key}={settings[key]}")
                replaced.add(key)
            continue
        updated.append(line)
    for key, value in settings.items():
        if key not in replaced:
            updated.append(f"{key}={value}")
    config_path.write_text("\n".join(updated) + "\n", encoding="utf-8")


def provision_avd(sdk_root: Path, name: str) -> bool:
    avd_manager = find_tool(sdk_root, "avdmanager")
    image_dir = (
        sdk_root / "system-images" / "android-37.0" / "google_apis_ps16k" / "x86_64"
    )
    if not image_dir.is_dir():
        raise AvdError(f"required system image is not installed: {SYSTEM_IMAGE}")
    created = not avd_exists(avd_manager, name)
    if created:
        run(
            avd_manager_command(
                avd_manager,
                "create",
                "avd",
                "--name",
                name,
                "--package",
                SYSTEM_IMAGE,
                "--device",
                DEVICE_NAME,
                "--force",
            ),
            input_text="no\n",
        )
    configure_data_partition(name)
    return created


def adb_path(sdk_root: Path) -> Path:
    suffix = "adb.exe" if os.name == "nt" else "adb"
    return find_executable(sdk_root, f"platform-tools/{suffix}")


def emulator_path(sdk_root: Path) -> Path:
    suffix = "emulator.exe" if os.name == "nt" else "emulator"
    return find_executable(sdk_root, f"emulator/{suffix}")


def connected_serial(adb: Path, name: str) -> str | None:
    result = run([str(adb), "devices"])
    for line in result.stdout.splitlines()[1:]:
        fields = line.split()
        if (
            len(fields) < 2
            or not fields[0].startswith("emulator-")
            or fields[1] != "device"
        ):
            continue
        serial = fields[0]
        identity = run([str(adb), "-s", serial, "emu", "avd", "name"])
        reported_name = next(
            (
                candidate.strip()
                for candidate in identity.stdout.splitlines()
                if candidate.strip() != "OK"
            ),
            "",
        )
        if reported_name == name:
            return serial
    return None


def boot_completed(adb: Path, serial: str) -> bool:
    result = subprocess.run(
        [str(adb), "-s", serial, "shell", "getprop", "sys.boot_completed"],
        text=True,
        capture_output=True,
        check=False,
        env=command_environment(),
    )
    return result.returncode == 0 and result.stdout.strip() == "1"


def device_ready(adb: Path, serial: str) -> bool:
    if not boot_completed(adb, serial):
        return False
    checks = (
        (["shell", "service", "check", "activity"], "found"),
        (["shell", "service", "check", "package"], "found"),
        (["shell", "settings", "get", "global", "device_provisioned"], None),
    )
    for arguments, expected in checks:
        result = subprocess.run(
            [str(adb), "-s", serial, *arguments],
            text=True,
            capture_output=True,
            check=False,
            env=command_environment(),
        )
        if result.returncode != 0:
            return False
        if expected is not None and expected not in result.stdout.lower():
            return False
        if arguments[1] == "settings" and result.stdout.strip() not in {"0", "1"}:
            return False
    return True


def launch_and_wait(sdk_root: Path, name: str, timeout_seconds: int) -> str:
    adb = adb_path(sdk_root)
    emulator = emulator_path(sdk_root)
    run([str(adb), "start-server"])

    serial = connected_serial(adb, name)
    if serial is None:
        log_path = Path(tempfile.gettempdir()) / f"{name}-emulator.log"
        log_handle = log_path.open("w", encoding="utf-8")
        creationflags = getattr(subprocess, "CREATE_NO_WINDOW", 0)
        subprocess.Popen(
            [
                str(emulator),
                "-avd",
                name,
                "-no-window",
                "-no-audio",
                "-no-boot-anim",
                "-gpu",
                "swiftshader_indirect",
                "-no-snapshot-load",
                "-wipe-data",
                "-partition-size",
                str(DATA_PARTITION_SIZE_MB),
                "-memory",
                str(RAM_SIZE_MB),
                "-no-snapshot-save",
            ],
            stdout=log_handle,
            stderr=subprocess.STDOUT,
            stdin=subprocess.DEVNULL,
            creationflags=creationflags,
            start_new_session=os.name != "nt",
            env=command_environment(),
        )
        log_handle.close()

    deadline = time.monotonic() + timeout_seconds
    ready_since: float | None = None
    while time.monotonic() < deadline:
        serial = connected_serial(adb, name)
        if serial and device_ready(adb, serial):
            now = time.monotonic()
            if ready_since is None:
                ready_since = now
            elif now - ready_since >= DEVICE_READY_STABILITY_SECONDS:
                return serial
        else:
            ready_since = None
        time.sleep(2)
    raise AvdError(f"{name} did not boot within {timeout_seconds} seconds")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--name", default=AVD_NAME, help=f"AVD name (default: {AVD_NAME})"
    )
    parser.add_argument(
        "--launch",
        action="store_true",
        help="launch the AVD headlessly and wait until Android reports boot completion",
    )
    parser.add_argument(
        "--timeout",
        type=int,
        default=BOOT_TIMEOUT_SECONDS,
        help=f"boot timeout in seconds (default: {BOOT_TIMEOUT_SECONDS})",
    )
    args = parser.parse_args()
    if args.timeout <= 0:
        parser.error("--timeout must be positive")

    try:
        sdk_root = find_sdk_root()
        created = provision_avd(sdk_root, args.name)
        state = "created" if created else "already exists"
        print(f"{args.name}: {state} ({SYSTEM_IMAGE})")
        if args.launch:
            serial = launch_and_wait(sdk_root, args.name, args.timeout)
            print(f"{args.name}: booted as {serial} (headless)")
    except AvdError as error:
        print(f"error: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
