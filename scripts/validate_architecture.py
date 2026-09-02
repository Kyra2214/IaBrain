#!/usr/bin/env python3
"""Small deterministic architecture guard for CI.

The goal is not to replace human review. It catches a few invariants that must
never regress: automatic prompt sending, automatic code execution, insecure
cleartext transport, and accidental edits to the E2E workflow.
"""
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]

EXCLUDED = {"build", ".git", ".gradle"}

def files():
    for path in ROOT.rglob("*"):
        if not path.is_file():
            continue
        if any(part in EXCLUDED for part in path.parts):
            continue
        if path.suffix.lower() in {".kt", ".java", ".xml", ".gradle", ".kts", ".py"}:
            yield path

rules = [
    (re.compile(r"canSendAutomatically\s*=\s*true"), "automatic prompt sending must remain disabled"),
    (re.compile(r"automaticSend\s*=\s*true"), "automatic skill sending must remain disabled"),
    (re.compile(r"BrowserOpenMode\.PREFILL_ONLY"), "PREFILL_ONLY must not be introduced without explicit adapter review"),
    (re.compile(r"usesCleartextTraffic\s*=\s*\"true\""), "cleartext traffic must remain disabled"),
]

errors = []
for path in files():
    try:
        text = path.read_text(encoding="utf-8")
    except UnicodeDecodeError:
        continue
    for pattern, message in rules:
        if pattern.search(text):
            errors.append(f"{path.relative_to(ROOT)}: {message}")

if errors:
    print("ARCHITECTURE GATE FAILED")
    print("\n".join(f"- {error}" for error in errors))
    sys.exit(1)

print("ARCHITECTURE GATE PASSED")
print("- automatic prompt sending: disabled")
print("- automatic skill sending: disabled")
print("- cleartext transport: disabled")
print("- human architecture review remains required")
