from __future__ import annotations

import json
import subprocess
from abc import ABC, abstractmethod
from dataclasses import dataclass
from pathlib import Path
from tempfile import TemporaryDirectory
from typing import Any

from common import CACHE_DIR, sha256_text, write_json


@dataclass(frozen=True)
class JudgeResult:
    payload: dict[str, Any]
    cached: bool
    cache_key: str


class JudgeBackend(ABC):
    name: str

    def __init__(self, model: str | None = None) -> None:
        self.model = model

    @abstractmethod
    def build_command(self, prompt_path: Path, schema_path: Path, output_path: Path, work_dir: Path) -> list[str]:
        raise NotImplementedError

    def invoke(self, command: list[str], prompt: str) -> subprocess.CompletedProcess[str]:
        return subprocess.run(command, input=prompt, check=True, capture_output=True, text=True)

    def run(self, prompt: str, schema: dict[str, Any], schema_path: Path) -> JudgeResult:
        cache_key = sha256_text(
            json.dumps(
                {
                    "backend": self.name,
                    "model": self.model,
                    "schema": schema,
                    "prompt": prompt,
                },
                sort_keys=True,
            )
        )
        cache_path = CACHE_DIR / f"{cache_key}.json"
        if cache_path.exists():
            return JudgeResult(payload=json.loads(cache_path.read_text()), cached=True, cache_key=cache_key)

        CACHE_DIR.mkdir(parents=True, exist_ok=True)
        with TemporaryDirectory(prefix="findoc-eval-judge-") as tmp:
            work_dir = Path(tmp) / "empty"
            work_dir.mkdir()
            prompt_path = Path(tmp) / "prompt.txt"
            output_path = Path(tmp) / "judgment.json"
            command = self.build_command(prompt_path, schema_path, output_path, work_dir)
            completed = self.invoke(command, prompt)
            payload = json.loads(output_path.read_text())
            validate_payload(payload, schema)
            payload["_meta"] = {
                "backend": self.name,
                "model": self.model,
                "stdout": completed.stdout,
                "stderr": completed.stderr,
                "command": command,
            }
            write_json(cache_path, payload)
            return JudgeResult(payload=payload, cached=False, cache_key=cache_key)


class CodexJudge(JudgeBackend):
    name = "codex"

    def build_command(self, prompt_path: Path, schema_path: Path, output_path: Path, work_dir: Path) -> list[str]:
        command = [
            "codex",
            "exec",
            "--output-schema",
            str(schema_path),
            "--output-last-message",
            str(output_path),
            "--sandbox",
            "read-only",
            "--cd",
            str(work_dir),
            "--skip-git-repo-check",
            "--ephemeral",
            "--ignore-user-config",
            "--ignore-rules",
            "--json",
            "-",
        ]
        if self.model:
            command[2:2] = ["--model", self.model]
        return command


class ClaudeCliJudge(JudgeBackend):
    name = "claude_cli"

    def build_command(self, prompt_path: Path, schema_path: Path, output_path: Path, work_dir: Path) -> list[str]:
        model = self.model or "sonnet"
        return [
            "claude",
            "-p",
            "--output-format",
            "json",
            "--model",
            model,
            "--allowedTools",
            "",
            "--strict-mcp-config",
            f"Return JSON matching this schema exactly: {schema_path.read_text()}",
            f"<PROMPT>\n{prompt_path.read_text()}\n</PROMPT>",
        ]

    def run(self, prompt: str, schema: dict[str, Any], schema_path: Path) -> JudgeResult:
        cache_key = sha256_text(
            json.dumps(
                {
                    "backend": self.name,
                    "model": self.model,
                    "schema": schema,
                    "prompt": prompt,
                },
                sort_keys=True,
            )
        )
        cache_path = CACHE_DIR / f"{cache_key}.json"
        if cache_path.exists():
            return JudgeResult(payload=json.loads(cache_path.read_text()), cached=True, cache_key=cache_key)

        CACHE_DIR.mkdir(parents=True, exist_ok=True)
        with TemporaryDirectory(prefix="findoc-eval-judge-") as tmp:
            work_dir = Path(tmp) / "empty"
            work_dir.mkdir()
            prompt_path = Path(tmp) / "prompt.txt"
            output_path = Path(tmp) / "judgment.json"
            prompt_path.write_text(prompt)
            command = self.build_command(prompt_path, schema_path, output_path, work_dir)
            completed = self.invoke(command, prompt)
            payload = json.loads(completed.stdout)
            validate_payload(payload, schema)
            payload["_meta"] = {
                "backend": self.name,
                "model": self.model,
                "stdout": completed.stdout,
                "stderr": completed.stderr,
                "command": command,
            }
            write_json(cache_path, payload)
            return JudgeResult(payload=payload, cached=False, cache_key=cache_key)


class OllamaJudge(JudgeBackend):
    name = "ollama"

    def build_command(self, prompt_path: Path, schema_path: Path, output_path: Path, work_dir: Path) -> list[str]:
        model = self.model or "llama3.1"
        prompt = (
            "Return JSON only. Match this schema exactly:\n"
            f"{schema_path.read_text()}\n\n"
            f"{prompt_path.read_text()}"
        )
        return ["ollama", "run", model, prompt]

    def run(self, prompt: str, schema: dict[str, Any], schema_path: Path) -> JudgeResult:
        cache_key = sha256_text(
            json.dumps(
                {"backend": self.name, "model": self.model, "schema": schema, "prompt": prompt},
                sort_keys=True,
            )
        )
        cache_path = CACHE_DIR / f"{cache_key}.json"
        if cache_path.exists():
            return JudgeResult(payload=json.loads(cache_path.read_text()), cached=True, cache_key=cache_key)
        CACHE_DIR.mkdir(parents=True, exist_ok=True)
        with TemporaryDirectory(prefix="findoc-eval-ollama-") as tmp:
            tmp_prompt = Path(tmp) / "prompt.txt"
            tmp_prompt.write_text(prompt)
            command = self.build_command(tmp_prompt, schema_path, Path(), Path())
            completed = subprocess.run(command, check=True, capture_output=True, text=True)
        payload = json.loads(completed.stdout)
        validate_payload(payload, schema)
        payload["_meta"] = {
            "backend": self.name,
            "model": self.model,
            "stdout": completed.stdout,
            "stderr": completed.stderr,
            "command": command,
        }
        write_json(cache_path, payload)
        return JudgeResult(payload=payload, cached=False, cache_key=cache_key)


def available_backends() -> list[str]:
    return ["codex", "claude_cli", "ollama"]


def validate_payload(payload: dict[str, Any], schema: dict[str, Any]) -> None:
    required = set(schema.get("required", []))
    missing = sorted(required - set(payload))
    if missing:
        raise ValueError(f"judge output is missing required fields: {missing}")
    properties = schema.get("properties", {})
    for name, definition in properties.items():
        if name not in payload:
            continue
        allowed = definition.get("enum")
        if allowed is not None and payload[name] not in allowed:
            raise ValueError(f"judge output has invalid {name}: {payload[name]!r}")
    confidence = payload.get("confidence")
    if not isinstance(confidence, (int, float)) or not 0 <= confidence <= 1:
        raise ValueError("judge output confidence must be between 0 and 1")


def get_backend(name: str, model: str | None = None) -> JudgeBackend:
    mapping = {
        "codex": CodexJudge,
        "claude_cli": ClaudeCliJudge,
        "ollama": OllamaJudge,
    }
    try:
        return mapping[name](model=model)
    except KeyError as exc:
        raise ValueError(f"unsupported backend: {name}") from exc
