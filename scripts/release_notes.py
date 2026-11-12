"""Prints the CHANGELOG.md section of a version, for the GitHub release (and ReVanced Manager's update notes).

    python scripts/release_notes.py 1.0.0

Sections start with a "## <version>" heading (anything after the version, like a date, is ignored).
"""

import pathlib
import sys

CHANGELOG = pathlib.Path(__file__).resolve().parent.parent / "CHANGELOG.md"


def section(version):
    lines, inside = [], False
    for line in CHANGELOG.read_text(encoding="utf-8").splitlines():
        if line.startswith("## "):
            heading = line[3:].split()
            inside = bool(heading) and heading[0].lstrip("v") == version
            continue
        if inside:
            lines.append(line)
    text = "\n".join(lines).strip()
    if not text:
        raise SystemExit(f"No section for {version} in {CHANGELOG.name}")
    return text


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("Usage: release_notes.py <version>")
    sys.stdout.reconfigure(encoding="utf-8")  # The notes contain non-ASCII text such as "→".
    print(section(sys.argv[1]))
