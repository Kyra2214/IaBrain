#!/usr/bin/env python3
"""Validate AI Brain catalog JSON files without external dependencies."""
from __future__ import annotations

import json
import sys
from pathlib import Path
from urllib.parse import urlparse


def validate(path: Path) -> list[str]:
    errors: list[str] = []
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        return [f"{path}: invalid JSON: {exc}"]

    version = data.get("versao")
    if not isinstance(version, int) or version < 0:
        errors.append(f"{path}: versao must be a non-negative integer")

    entries = data.get("ias")
    if not isinstance(entries, list) or not entries:
        return errors + [f"{path}: ias must be a non-empty array"]

    ids: set[str] = set()
    for index, entry in enumerate(entries):
        prefix = f"{path}: ias[{index}]"
        if not isinstance(entry, dict):
            errors.append(f"{prefix} must be an object")
            continue
        identifier = entry.get("id")
        if not isinstance(identifier, str) or not identifier.strip():
            errors.append(f"{prefix}.id must be a non-empty string")
        elif identifier in ids:
            errors.append(f"{prefix}.id is duplicated: {identifier}")
        else:
            ids.add(identifier)

        for field in ("nome", "descricao", "site"):
            if not isinstance(entry.get(field), str) or not entry[field].strip():
                errors.append(f"{prefix}.{field} must be a non-empty string")

        site = entry.get("site", "")
        parsed = urlparse(site)
        if parsed.scheme != "https" or not parsed.netloc:
            errors.append(f"{prefix}.site must be an HTTPS URL")

        is_adult_catalog = "categoria_id" in entry and "status" in entry
        if is_adult_catalog:
            if not isinstance(entry.get("categoria_id"), str) or not entry["categoria_id"].strip():
                errors.append(f"{prefix}.categoria_id must be a non-empty string")
            if not isinstance(entry.get("status"), str) or not entry["status"].strip():
                errors.append(f"{prefix}.status must be a non-empty string")
        else:
            for field in ("categorias", "idiomas"):
                if not isinstance(entry.get(field), list):
                    errors.append(f"{prefix}.{field} must be an array")

            notas = entry.get("notas")
            if not isinstance(notas, dict):
                errors.append(f"{prefix}.notas must be an object")
            else:
                for key, value in notas.items():
                    if not isinstance(value, (int, float)) or not 0 <= value <= 10:
                        errors.append(f"{prefix}.notas.{key} must be between 0 and 10")

    return errors


def main() -> int:
    paths = [Path(arg) for arg in sys.argv[1:]]
    if not paths:
        paths = list(Path("app/src/main/assets").glob("*_catalogo.json"))
        paths += list(Path("para-subir-no-github").glob("*_catalogo.json"))

    errors = [error for path in paths for error in validate(path)]
    if errors:
        print("\n".join(errors), file=sys.stderr)
        return 1
    print(f"Validated {len(paths)} catalog file(s).")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
