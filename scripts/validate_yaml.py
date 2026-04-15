#!/usr/bin/env python3
from __future__ import annotations

import sys
from pathlib import Path

try:
    import yaml  # type: ignore
except Exception as exc:  # pragma: no cover
    print(f"[ERROR] PyYAML is required: {exc}")
    sys.exit(2)

ROOT = Path(__file__).resolve().parents[1]
FILES = [
    ROOT / "src" / "main" / "resources" / "plugin.yml",
    ROOT / "src" / "main" / "resources" / "config.yml",
]


def fail(msg: str) -> None:
    print(f"[ERROR] {msg}")
    raise SystemExit(1)


for path in FILES:
    if not path.exists():
        fail(f"Missing required file: {path}")

    try:
        data = yaml.safe_load(path.read_text(encoding="utf-8"))
    except Exception as exc:
        fail(f"Invalid YAML in {path}: {exc}")

    if data is None:
        fail(f"YAML file is empty: {path}")

print("[OK] YAML parsed successfully")

plugin = yaml.safe_load((ROOT / "src" / "main" / "resources" / "plugin.yml").read_text(encoding="utf-8"))
commands = plugin.get("commands") or {}
if "compat" not in commands:
    fail("plugin.yml missing /compat command declaration")

print("[OK] plugin.yml contains /compat command")
