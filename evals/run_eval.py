from __future__ import annotations

import argparse
import hashlib
import json
import os
import time
import urllib.error
import urllib.request
from pathlib import Path
from typing import Any, Callable

from common import (
    DATASETS_DIR,
    CORPUS_DIR,
    REPORTS_DIR,
    ROOT,
    current_commit,
    load_corpus_manifest,
    load_dataset,
    read_json,
    sha256_file,
    sha256_text,
    utc_timestamp,
    write_json,
)
from score_generation import score_case


DEFAULT_MODEL = "gemini-2.5-flash-lite"
GEMINI_BASE_URL = "https://generativelanguage.googleapis.com/v1beta"
RESPONSE_SCHEMA = {
    "type": "object",
    "properties": {
        "answerable": {"type": "boolean"},
        "answer": {"type": "string"},
        "citations": {
            "type": "array",
            "items": {
                "type": "object",
                "properties": {"sourceId": {"type": "string"}},
                "required": ["sourceId"],
                "additionalProperties": False,
            },
        },
    },
    "required": ["answerable", "answer", "citations"],
    "additionalProperties": False,
}


def production_prompt() -> str:
    path = ROOT.parent / "backend" / "src" / "main" / "resources" / "prompts" / "answer_with_context.md"
    if not path.is_file():
        raise FileNotFoundError(f"production answer prompt is missing: {path}")
    return path.read_text()


def render_context(
    case: dict[str, Any], corpus_name: str | None = None
) -> tuple[str, dict[str, dict[str, Any]]]:
    blocks: list[str] = []
    sources: dict[str, dict[str, Any]] = {}
    for index, evidence in enumerate(case.get("goldEvidence", [])):
        text = evidence.get("text") or evidence.get("quote")
        if not text and corpus_name:
            text = corpus_evidence_text(evidence, corpus_name)
        if not text:
            raise ValueError(f"{case['id']} goldEvidence[{index}] is missing text or quote")
        source_id = f"S{index + 1}"
        source = evidence.get("label") or " - ".join(
            str(value)
            for value in (evidence.get("accession"), evidence.get("form"), evidence.get("sectionItem"))
            if value
        )
        if not source:
            raise ValueError(f"{case['id']} goldEvidence[{index}] is missing provenance")
        page = evidence.get("page")
        page_label = "" if page is None else f", Pg {page}"
        sources[source_id] = {
            key: evidence[key]
            for key in ("accession", "form", "sectionItem", "page")
            if evidence.get(key) is not None
        }
        blocks.append(f"[{source_id}; {source}{page_label}] {text}")
    return "\n\n---\n\n".join(blocks) or "No evidence was supplied.", sources


def corpus_evidence_text(evidence: dict[str, Any], corpus_name: str) -> str:
    accession = str(evidence.get("accession") or "")
    section_item = str(evidence.get("sectionItem") or "")
    if not accession or not section_item:
        raise ValueError("corpus-backed gold evidence requires accession and sectionItem")
    fixture = CORPUS_DIR / corpus_name / f"{accession}.json"
    payload = json.loads(fixture.read_text())
    section = next((row for row in payload.get("sections", []) if row.get("item") == section_item), None)
    if section is None:
        raise ValueError(f"gold evidence section is absent from corpus: {accession} {section_item}")
    section_text = str(section.get("text") or "")
    start = int(evidence["charStart"])
    end = int(evidence["charEnd"])
    if start < 0 or end <= start or end > len(section_text):
        raise ValueError(f"invalid gold evidence span: {accession} {section_item} [{start}, {end})")
    quote = section_text[start:end]
    expected_hash = str(evidence.get("quoteSha256") or "")
    actual_hash = hashlib.sha256(quote.encode("utf-8")).hexdigest()
    if not expected_hash or actual_hash != expected_hash:
        raise ValueError(f"gold evidence hash mismatch: {accession} {section_item} [{start}, {end})")
    return quote


def prepare_generation(dataset_name: str, case_ids: set[str] | None = None) -> dict[str, Any]:
    corpus_manifest = load_corpus_manifest(dataset_name)
    if corpus_manifest is None:
        raise FileNotFoundError(
            "record and review evals/corpus/tesla-2025/manifest.json before preparing generation evaluation"
        )
    dataset = load_dataset(dataset_name)
    eligible = [
        case
        for case in dataset
        if case.get("verificationStatus") == "verified" and (not case_ids or case["id"] in case_ids)
    ]
    if case_ids:
        missing = sorted(case_ids - {case["id"] for case in eligible})
        if missing:
            raise ValueError(f"unknown or unverified case IDs: {', '.join(missing)}")
    if not eligible:
        raise RuntimeError("dataset has no verified cases")

    template = production_prompt()
    run_id = f"{utc_timestamp()}-{current_commit()[:12]}"
    run_dir = REPORTS_DIR / run_id
    run_dir.mkdir(parents=True, exist_ok=False)
    prompts = []
    for case in eligible:
        context, sources = render_context(case, str(corpus_manifest["corpus"]))
        system_instruction = template.replace("{{context}}", context)
        rendered = system_instruction + f"\n\nQuestion:\n{case['question']}"
        prompts.append(
            {
                "id": case["id"],
                "systemInstruction": system_instruction,
                "question": case["question"],
                "prompt": rendered,
                "promptSha256": sha256_text(rendered),
                "availableSourceIds": sorted(sources),
                "sources": sources,
            }
        )
    write_json(run_dir / "generation-inputs.json", prompts)
    write_json(
        run_dir / "run-manifest.json",
        {
            "runId": run_id,
            "status": "generation-inputs-prepared",
            "dataset": dataset_name,
            "datasetSha256": sha256_file(DATASETS_DIR / f"{dataset_name}.jsonl"),
            "commit": current_commit(),
            "corpusManifestSha256": corpus_manifest.get("manifestSha256"),
            "productionPromptSha256": sha256_text(template),
            "caseCount": len(prompts),
        },
    )
    return {"runId": run_id, "runDir": str(run_dir), "preparedCaseCount": len(prompts)}


def gemini_request(
    item: dict[str, Any],
    api_key: str,
    model: str = DEFAULT_MODEL,
    opener: Callable[..., Any] = urllib.request.urlopen,
    sleeper: Callable[[float], None] = time.sleep,
) -> dict[str, Any]:
    body = {
        "systemInstruction": {"parts": [{"text": item["systemInstruction"]}]},
        "contents": [{"role": "user", "parts": [{"text": item["question"]}]}],
        "generationConfig": {
            "temperature": 0,
            "seed": 42,
            "maxOutputTokens": 4096,
            "responseMimeType": "application/json",
            "responseJsonSchema": RESPONSE_SCHEMA,
        },
    }
    request = urllib.request.Request(
        f"{GEMINI_BASE_URL}/models/{model}:generateContent",
        data=json.dumps(body).encode("utf-8"),
        headers={"Content-Type": "application/json", "x-goog-api-key": api_key},
        method="POST",
    )
    started = time.perf_counter()
    for attempt in range(3):
        try:
            with opener(request, timeout=45) as response:
                payload = json.loads(response.read())
            break
        except urllib.error.HTTPError as error:
            if error.code not in {429, 500, 502, 503, 504} or attempt == 2:
                detail = error.read().decode("utf-8", errors="replace")[:500]
                raise RuntimeError(f"Gemini request failed with HTTP {error.code}: {detail}") from error
            retry_after = error.headers.get("Retry-After")
            sleeper(float(retry_after) if retry_after else 2**attempt)
    latency_ms = round((time.perf_counter() - started) * 1000)
    candidates = payload.get("candidates") or []
    if not candidates:
        raise RuntimeError(f"Gemini returned no candidate: {payload.get('promptFeedback', {})}")
    candidate = candidates[0]
    parts = candidate.get("content", {}).get("parts", [])
    text = "".join(str(part.get("text", "")) for part in parts)
    try:
        structured = json.loads(text)
    except json.JSONDecodeError as error:
        raise RuntimeError("Gemini returned non-JSON output despite the response schema") from error
    validate_answer(structured)
    return {
        "structured": structured,
        "usageMetadata": payload.get("usageMetadata", {}),
        "latencyMs": latency_ms,
        "finishReason": candidate.get("finishReason"),
    }


def validate_answer(answer: Any) -> None:
    if not isinstance(answer, dict):
        raise ValueError("structured answer must be an object")
    if not isinstance(answer.get("answerable"), bool) or not isinstance(answer.get("answer"), str):
        raise ValueError("structured answer has invalid answerable or answer fields")
    citations = answer.get("citations")
    if not isinstance(citations, list) or any(
        not isinstance(citation, dict) or not isinstance(citation.get("sourceId"), str)
        for citation in citations
    ):
        raise ValueError("structured answer has invalid citations")


def run_generation(run_dir: Path, api_key: str, model: str = DEFAULT_MODEL) -> dict[str, Any]:
    manifest = read_json(run_dir / "run-manifest.json")
    inputs = read_json(run_dir / "generation-inputs.json")
    dataset = {case["id"]: case for case in load_dataset(str(manifest["dataset"]))}
    answers_path = run_dir / "answers.json"
    scores_path = run_dir / "scores.json"
    answers: list[dict[str, Any]] = read_json(answers_path) if answers_path.exists() else []
    inputs_by_id = {item["id"]: item for item in inputs}
    scores: list[dict[str, Any]] = [
        score_case(dataset[answer["id"]], answer["structured"], inputs_by_id[answer["id"]]["sources"])
        for answer in answers
    ]
    completed_ids = {answer["id"] for answer in answers}
    totals: dict[str, int] = {}

    for answer in answers:
        for key, value in answer.get("usageMetadata", {}).items():
            if key.endswith("TokenCount") and isinstance(value, int):
                totals[key] = totals.get(key, 0) + value

    for item in inputs:
        if item["id"] in completed_ids:
            continue
        result = gemini_request(item, api_key, model)
        structured = result["structured"]
        answers.append({"id": item["id"], "answer": structured["answer"], **result})
        scores.append(score_case(dataset[item["id"]], structured, item["sources"]))
        for key, value in result["usageMetadata"].items():
            if key.endswith("TokenCount") and isinstance(value, int):
                totals[key] = totals.get(key, 0) + value
        write_json(answers_path, answers)
        write_json(scores_path, scores)

    write_json(scores_path, scores)

    graded = [score for score in scores if score["graded"]]
    manifest.update(
        {
            "status": "generation-complete",
            "generator": {
                "provider": "Google Gemini API",
                "model": model,
                "endpoint": GEMINI_BASE_URL,
                "temperature": 0,
                "seed": 42,
                "maxOutputTokens": 4096,
                "structuredOutput": True,
            },
            "usageMetadata": totals,
            "deterministicPassCount": sum(bool(score["passed"]) for score in graded),
            "deterministicGradedCount": len(graded),
        }
    )
    write_json(run_dir / "run-manifest.json", manifest)
    return {
        "runId": manifest["runId"],
        "runDir": str(run_dir),
        "caseCount": len(answers),
        "deterministicPassCount": manifest["deterministicPassCount"],
        "usageMetadata": totals,
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--dataset", default="tesla-2025-v1")
    parser.add_argument("--stage", default="prepare-generation", choices=["prepare-generation", "generation"])
    parser.add_argument("--run-dir", type=Path)
    parser.add_argument("--model", default=DEFAULT_MODEL)
    parser.add_argument("--case-id", action="append", default=[])
    args = parser.parse_args()

    if args.stage == "prepare-generation":
        result = prepare_generation(args.dataset, set(args.case_id) or None)
    else:
        api_key = os.environ.get("GEMINI_API_KEY", "").strip()
        if not api_key:
            raise RuntimeError("GEMINI_API_KEY is required for the generation stage")
        if args.run_dir:
            run_dir = args.run_dir.resolve()
        else:
            prepared = prepare_generation(args.dataset, set(args.case_id) or None)
            run_dir = Path(prepared["runDir"])
        result = run_generation(run_dir, api_key, args.model)
    print(json.dumps(result, indent=2, sort_keys=True))


if __name__ == "__main__":
    main()
