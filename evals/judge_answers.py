from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Any

from common import CALIBRATION_DIR, REPORTS_DIR, load_rubric, load_schema, read_jsonl, sha256_text, utc_timestamp, write_json
from judges import available_backends, get_backend


def latest_run_dir() -> Path:
    runs = sorted(path for path in REPORTS_DIR.iterdir() if path.is_dir())
    if not runs:
        raise FileNotFoundError("no eval runs found")
    return runs[-1]


def build_prompt(case_id: str, generation_input: str, answer: str, dimension: str, sample: int) -> str:
    return (
        f"Dimension: {dimension}\n"
        f"Case ID: {case_id}\n"
        f"Rubric:\n{load_rubric(dimension)}\n\n"
        f"Independent judgment sample: {sample} of 2\n\n"
        f"Question and supplied context:\n{generation_input}\n\n"
        f"Answer:\n{answer}\n"
    )


def judge_run(run_dir: Path, backend_name: str, model: str | None) -> dict[str, Any]:
    answers = json.loads((run_dir / "answers.json").read_text())
    inputs_path = run_dir / "generation-inputs.json"
    inputs = json.loads(inputs_path.read_text()) if inputs_path.exists() else []
    inputs_by_id = {str(row["id"]): str(row["prompt"]) for row in inputs}
    scores_path = run_dir / "scores.json"
    scores = json.loads(scores_path.read_text()) if scores_path.exists() else []
    score_by_id = {str(row["id"]): row for row in scores}
    schema = load_schema("judgment.schema.json")
    schema_path = Path(__file__).resolve().parent / "schemas" / "judgment.schema.json"
    backend = get_backend(backend_name, model=model)
    judgments: list[dict[str, Any]] = []
    for answer_row in answers:
        answer = str(answer_row["answer"])
        case_id = str(answer_row["id"])
        score = score_by_id.get(case_id)
        if score is not None and not score.get("passed") and int(sha256_text(case_id)[:8], 16) % 5:
            continue
        refused = answer_row.get("structured", {}).get("answerable") is False
        dimensions = ["refusal"] if refused else ["groundedness", "completeness", "citation-entailment"]
        for dimension in dimensions:
            samples = []
            for sample in (1, 2):
                prompt = build_prompt(case_id, inputs_by_id.get(case_id, "Unavailable"), answer, dimension, sample)
                result = backend.run(prompt, schema, schema_path)
                samples.append(result.payload)
            judgments.append({
                "id": answer_row["id"],
                "dimension": dimension,
                "samples": samples,
                "disagreement": samples[0]["verdict"] != samples[1]["verdict"],
            })
    payload = {
        "timestamp": utc_timestamp(),
        "backend": backend_name,
        "model": model,
        "runDir": str(run_dir),
        "judgments": judgments,
    }
    write_json(run_dir / f"judgments-{backend_name}.json", payload)
    return payload


def calibrate() -> dict[str, Any]:
    labels = read_jsonl(CALIBRATION_DIR / "tesla-2025-v1.labels.jsonl")
    return {
        "labelCount": len(labels),
        "dimensions": sorted({row["dimension"] for row in labels}),
        "status": "manual-review-required",
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--backend", default="codex", choices=available_backends())
    parser.add_argument("--model")
    parser.add_argument("--run-dir")
    parser.add_argument("--calibrate", action="store_true")
    args = parser.parse_args()

    if args.calibrate:
        print(json.dumps(calibrate(), indent=2, sort_keys=True))
        return

    run_dir = Path(args.run_dir) if args.run_dir else latest_run_dir()
    print(json.dumps(judge_run(run_dir, args.backend, args.model), indent=2, sort_keys=True))


if __name__ == "__main__":
    main()
