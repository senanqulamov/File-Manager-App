"""Check tracked local documentation links and keep runtime state out of source."""

from pathlib import Path
import re
import subprocess
import sys
from urllib.parse import unquote, urlsplit
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
tracked = subprocess.check_output(
    ["git", "ls-files", "-z"], cwd=ROOT
).decode("utf-8").split("\0")
paths = {name for name in tracked if name}
errors = []
links_checked = 0

for name in sorted(paths):
    path = ROOT / name
    parts = Path(name).parts
    if any(part in {"target", "docket-data", ".idea", ".build", "__pycache__"} for part in parts):
        errors.append(f"Runtime/build/editor file is tracked: {name}")
    if path.suffix.lower() in {".class", ".p12", ".pfx", ".jks", ".key"} or name.endswith((".mv.db", ".trace.db")):
        errors.append(f"Generated or private file is tracked: {name}")
    if path.name == ".env" or (path.name.startswith(".env.") and path.name != ".env.example"):
        errors.append(f"Local environment file is tracked: {name}")
    if not path.is_file():
        errors.append(f"Tracked file missing from checkout: {name}")
        continue
    if path.suffix == ".svg":
        try:
            ET.parse(path)
        except ET.ParseError as exc:
            errors.append(f"Invalid SVG {name}: {exc}")
    if path.suffix.lower() != ".md":
        continue
    content = re.sub(r"```.*?```", "", path.read_text(encoding="utf-8"), flags=re.S)
    references = re.findall(r"!?\[[^\]]*\]\(([^\s)]+)(?:\s+[^)]*)?\)", content)
    references += re.findall(r'(?:src|href)=["\']([^"\']+)["\']', content)
    for reference in references:
        url = urlsplit(reference)
        if url.scheme or url.netloc or not url.path:
            continue
        target = (path.parent / unquote(url.path)).resolve()
        try:
            relative = target.relative_to(ROOT).as_posix()
        except ValueError:
            errors.append(f"Link escapes repository in {name}: {reference}")
            continue
        if relative not in paths:
            errors.append(f"Missing/untracked local link in {name}: {reference}")
        links_checked += 1

if errors:
    print("\n".join(errors), file=sys.stderr)
    sys.exit(1)
print(f"OK: {len(paths)} tracked files; {links_checked} local documentation links; SVG syntax and repository hygiene.")
