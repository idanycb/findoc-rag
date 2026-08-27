from __future__ import annotations

import hashlib
import json
import os
import re
import subprocess
from dataclasses import dataclass
from datetime import UTC, datetime
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parent
DATASETS_DIR = ROOT / "datasets"
RUBRICS_DIR = ROOT / "rubrics"
SCHEMAS_DIR = ROOT / "schemas"
CORPUS_DIR = ROOT / "corpus"
CALIBRATION_DIR = ROOT / "calibration"
REPORTS_DIR = Path(os.environ.get("FINDOC_EVAL_REPORTS_DIR", str(ROOT / "reports"))).resolve()
CACHE_DIR = Path(os.environ.get("FINDOC_EVAL_CACHE_DIR", str(ROOT / ".judge-cache"))).resolve()

REFUSAL_MARKER = "The current document vault does not contain information to answer this question."


def utc_timestamp() -> str:
    return datetime.now(UTC).strftime("%Y%m%dT%H%M%SZ")


def read_json(path: Path) -> Any:
    return json.loads(path.read_text())


def write_json(path: Path, payload: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n")


def read_jsonl(path: Path) -> list[dict[str, Any]]:
    rows: list[dict[str, Any]] = []
    for line in path.read_text().splitlines():
        if line.strip():
            rows.append(json.loads(line))
    return rows


def write_jsonl(path: Path, rows: list[dict[str, Any]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    text = "\n".join(json.dumps(row, sort_keys=True) for row in rows) + "\n"
    path.write_text(text)


def sha256_text(text: str) -> str:
    return hashlib.sha256(text.encode("utf-8")).hexdigest()


def sha256_file(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def load_dataset(name: str) -> list[dict[str, Any]]:
    return read_jsonl(DATASETS_DIR / f"{name}.jsonl")


def load_schema(name: str) -> dict[str, Any]:
    return read_json(SCHEMAS_DIR / name)


def load_rubric(name: str) -> str:
    return (RUBRICS_DIR / f"{name}.md").read_text().strip()


def current_commit() -> str:
    try:
        return subprocess.run(
            ["git", "rev-parse", "HEAD"],
            cwd=ROOT.parent,
            check=True,
            capture_output=True,
            text=True,
        ).stdout.strip()
    except (OSError, subprocess.CalledProcessError):
        return "unknown"


def corpus_manifest_path(dataset: str) -> Path:
    corpus_name = re.sub(r"-v\d+$", "", dataset)
    return CORPUS_DIR / corpus_name / "manifest.json"


def load_corpus_manifest(dataset: str) -> dict[str, Any] | None:
    path = corpus_manifest_path(dataset)
    if path.exists():
        return read_json(path)
    return None


@dataclass(frozen=True)
class EvalCase:
    raw: dict[str, Any]

    @property
    def id(self) -> str:
        return str(self.raw["id"])

    @property
    def question(self) -> str:
        return str(self.raw["question"])

    @property
    def answerable(self) -> bool:
        return bool(self.raw["answerable"])

    @property
    def verification_status(self) -> str:
        return str(self.raw.get("verificationStatus", "pending"))
