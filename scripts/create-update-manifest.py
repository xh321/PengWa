#!/usr/bin/env python3
"""Generate update.json from Gradle's actual APK output, never guessed version fields."""
import argparse
import hashlib
import json
from pathlib import Path


def create_manifest(apk, metadata, apk_url, notes):
    elements = metadata["elements"]
    if len(elements) != 1 or elements[0].get("filters"):
        raise ValueError("Self-update requires one universal APK")
    element = elements[0]
    with apk.open("rb") as source:
        digest = hashlib.file_digest(source, "sha256").hexdigest()
    return {
        "packageName": metadata["applicationId"],
        "versionCode": element["versionCode"],
        "versionName": element["versionName"],
        "minSdk": metadata["minSdkVersionForDexing"],
        "apkUrl": apk_url,
        "size": apk.stat().st_size,
        "sha256": digest,
        "notes": notes,
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk", required=True, type=Path)
    parser.add_argument("--metadata", required=True, type=Path)
    parser.add_argument("--apk-url", required=True)
    parser.add_argument("--notes", default="修复问题并改进体验。")
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    manifest = create_manifest(args.apk, json.loads(args.metadata.read_text()), args.apk_url, args.notes)
    args.output.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n")


if __name__ == "__main__":
    main()
