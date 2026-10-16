#!/usr/bin/env python3
"""Verify a signed ARM64 compatibility Release without accessing private keys."""

import argparse
import hashlib
import json
import re
import subprocess
import zipfile
from pathlib import Path


def run(tool, *arguments):
    return subprocess.check_output([str(tool), *map(str, arguments)], text=True)


def require(condition, message):
    if not condition:
        raise ValueError(message)


def verify(args):
    apk = args.apk
    badging = run(args.build_tools / "aapt", "dump", "badging", apk)
    package_line = next(line for line in badging.splitlines() if line.startswith("package:"))
    package = dict(re.findall(r"(\w+)='([^']*)'", package_line))
    require(package.get("name") == "dev.jdtech.jellyfin.compat", "Wrong application ID")
    require(package.get("versionName") == args.version, "Wrong version name")
    require(package.get("versionCode") == str(args.version_code), "Wrong version code")
    require("application-label:'Findroid'" in badging, "Wrong display name")
    require("application-debuggable" not in badging, "APK is debuggable")
    require("sdkVersion:'28'" in badging, "Unexpected minimum Android version")
    require("targetSdkVersion:'36'" in badging, "Unexpected target Android version")
    manifest = run(args.build_tools / "aapt", "dump", "xmltree", apk, "AndroidManifest.xml")
    for line in manifest.splitlines():
        if "android:debuggable(" in line or "android:testOnly(" in line:
            require(line.rstrip().endswith("0x0"), "APK is debuggable or test-only")
    # API 28+ selects v3. Explicitly check the v2 block on API 24-27 too;
    # this cryptographic check does not change the app's minimum Android version.
    for scheme, flags in ((3, ()), (2, ("--min-sdk-version", "24", "--max-sdk-version", "27"))):
        signatures = run(
            args.build_tools / "apksigner", "verify", "--verbose", "--print-certs", *flags, apk
        )
        # Build-tools 37 uses "V3.0 Signer:" / "V2 Signer:"; older versions
        # use "Signer #1". The certificate digest remains the same field.
        fingerprints = re.findall(
            r"^.* certificate SHA-256 digest: ([a-fA-F0-9]+)$", signatures, re.MULTILINE
        )
        require(
            [fingerprint.lower() for fingerprint in fingerprints] == [args.certificate_sha256.lower()],
            "Wrong signing certificate or signer count",
        )
        require(
            f"Verified using v{scheme} scheme (APK Signature Scheme v{scheme}): true" in signatures,
            f"APK signature scheme v{scheme} was not verified",
        )
        require("CN=Android Debug" not in signatures, "APK uses an Android Debug certificate")
    run(args.build_tools / "zipalign", "-c", "-P", "16", "4", apk)
    r8_markers = []
    with zipfile.ZipFile(apk) as archive:
        names = archive.namelist()
        for name in names:
            if re.fullmatch(r"classes(?:\d+)?\.dex", name):
                for marker in re.findall(rb"~~R8(\{[^\x00]+\})\x00", archive.read(name)):
                    r8_markers.append(json.loads(marker))
        abis = {name.split("/")[1] for name in names if name.startswith("lib/") and name.endswith(".so")}
        require(abis == {"arm64-v8a"}, "APK is not an ARM64-only build")
        require(
            not any(name.lower().endswith((".jks", ".keystore", ".p12", ".pfx")) for name in names),
            "APK unexpectedly contains a keystore",
        )
    mapping = args.mapping.read_text()
    require("# compiler: R8" in mapping, "R8 mapping is absent")
    mapping_id = re.search(r"^# pg_map_id: (\S+)$", mapping, re.MULTILINE)
    require(mapping_id is not None, "R8 mapping ID is absent")
    require(
        any(
            marker.get("pg-map-id") == mapping_id.group(1)
            and marker.get("compilation-mode") == "release"
            and marker.get("backend") == "dex"
            for marker in r8_markers
        ),
        "R8 mapping does not match the Release APK's DEX marker",
    )
    config = args.mapping.with_name("configuration.txt").read_text()
    require("-dontoptimize" not in config, "R8 optimization was disabled")
    require("-dontshrink" not in config, "R8 shrinking was disabled")
    return {
        "apk": apk.name,
        "sha256": hashlib.sha256(apk.read_bytes()).hexdigest(),
        "application_id": package["name"],
        "version_name": package["versionName"],
        "version_code": int(package["versionCode"]),
        "display_name": "Findroid",
        "debuggable": False,
        "test_only": False,
        "abi": "arm64-v8a",
        "minimum_sdk": 28,
        "target_sdk": 36,
        "certificate_sha256": fingerprints[0],
        "verified_signature_schemes": ["v2", "v3"],
        "zip_alignment": "4-byte files / 16 KiB native libraries",
        "r8_optimization_enabled": True,
        "r8_map_id": mapping_id.group(1),
        "r8_mapping_sha256": hashlib.sha256(args.mapping.read_bytes()).hexdigest(),
        "r8_configuration_sha256": hashlib.sha256(config.encode()).hexdigest(),
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", type=Path)
    parser.add_argument("--build-tools", type=Path, required=True)
    parser.add_argument("--version", required=True)
    parser.add_argument("--version-code", type=int, required=True)
    parser.add_argument("--certificate-sha256", required=True)
    parser.add_argument("--mapping", type=Path, required=True)
    args = parser.parse_args()
    print(json.dumps(verify(args), indent=2))


if __name__ == "__main__":
    main()
