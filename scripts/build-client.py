"""Validate/copy native ES modules; no bundler or Node dependency."""
import json
import re
import shutil
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "src/main/resources/static"
DEST = ROOT / "target/client"

def build():
    files = list(SOURCE.rglob("*"))
    modules = list(SOURCE.rglob("*.js"))
    for path in modules:
        content = path.read_text(encoding="utf-8")
        for imported in re.findall(r'from\s+["\']([^"\']+)["\']', content):
            dependency = SOURCE / imported.lstrip("/") if imported.startswith("/") else path.parent / imported
            if not dependency.is_file():
                raise ValueError(f"Missing module: {path.name} -> {imported}")
        if "\ufffd" in content:
            raise ValueError(f"Invalid UTF-8: {path}")
    for path in files:
        if path.is_file():
            destination = DEST / path.relative_to(SOURCE)
            destination.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(path, destination)
    result = {"modules": len(modules), "files": sum(p.is_file() for p in files), "output": str(DEST)}
    (ROOT / "target/client-build.json").write_text(json.dumps(result, indent=2), encoding="utf-8")
    print(json.dumps(result))

if __name__ == "__main__":
    build()
