"""Regenerates the patch bundle links in bundles/ from this repository's GitHub releases.

ReVanced Manager adds a remote patch bundle from a JSON file that points at the release's patches.rvp, and
checks it for updates. The release workflow runs this script after publishing a release; you can also run it by
hand and commit the result:

    python scripts/update_bundle_json.py

Channels:
    stable.json  newest regular release (pre-releases skipped)
    latest.json  newest release, pre-releases included
    dev.json     newest pre-release (only written once there is one)

The repository is taken from $GITHUB_REPOSITORY, or else from the `origin` remote. $GH_TOKEN or $GITHUB_TOKEN is
used if set (for the API rate limit); public repositories work without one. Standard library only.
"""

import json
import os
import pathlib
import re
import subprocess
import urllib.request

ASSET = "patches.rvp"
OUT = pathlib.Path(__file__).resolve().parent.parent / "bundles"


def repository():
    if os.environ.get("GITHUB_REPOSITORY"):
        return os.environ["GITHUB_REPOSITORY"]
    url = subprocess.run(
        ["git", "remote", "get-url", "origin"], capture_output=True, text=True, check=True
    ).stdout.strip()
    match = re.search(r"github\.com[:/]([^/]+/[^/]+?)(?:\.git)?$", url)
    if not match:
        raise SystemExit(f"origin ({url}) is not a GitHub repository; set GITHUB_REPOSITORY=owner/name")
    return match.group(1)


def releases(repo):
    headers = {"Accept": "application/vnd.github+json", "User-Agent": "update_bundle_json.py"}
    token = os.environ.get("GH_TOKEN") or os.environ.get("GITHUB_TOKEN")
    if token:
        headers["Authorization"] = f"Bearer {token}"
    request = urllib.request.Request(f"https://api.github.com/repos/{repo}/releases?per_page=100", headers=headers)
    with urllib.request.urlopen(request) as response:
        data = json.load(response)
    usable = [
        release for release in data
        if not release.get("draft") and any(asset["name"] == ASSET for asset in release.get("assets", []))
    ]
    return sorted(usable, key=lambda release: release["published_at"], reverse=True)


def bundle(release):
    asset = next(asset for asset in release["assets"] if asset["name"] == ASSET)
    return {
        "created_at": release["published_at"].rstrip("Z"),
        "description": release.get("body") or "",
        "download_url": asset["browser_download_url"],
        "signature_download_url": "N/A",
        "version": release["tag_name"],
    }


def main():
    repo = repository()
    found = releases(repo)
    channels = {
        "stable": next((release for release in found if not release["prerelease"]), None),
        "latest": found[0] if found else None,
        "dev": next((release for release in found if release["prerelease"]), None),
    }
    OUT.mkdir(exist_ok=True)
    for name, release in channels.items():
        path = OUT / f"{name}.json"
        if release is None:
            print(f"{name}: no matching release in {repo}; {path.name} left as it is")
            continue
        path.write_text(json.dumps(bundle(release), indent=2) + "\n", encoding="utf-8")
        print(f"{name}: {release['tag_name']}")


if __name__ == "__main__":
    main()
