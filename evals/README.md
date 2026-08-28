# FinDoc Local Evaluations

Local, evidence-locked evaluation harness for FinDoc's SEC ingestion, retrieval, citation, and generation behavior. It separates deterministic checks from paid or model-based judgment and never silently creates gold evidence.

## Current dataset

`tesla-2025-v1` contains 14 cases across amendment purpose, front matter, metadata, version selection, unanswerable questions, citation discipline, and section coverage.

- 12 cases are corpus-verified.
- 2 metadata-contract cases remain `pending` and are excluded from verified generation inputs.
- Gold evidence uses exact character offsets and SHA-256 hashes against the recorded corpus.
- The committed corpus contains Tesla's 2025 `10-K` and `10-K/A` sidecar responses.
- EDGAR page values remain nullable; the suite treats invented page provenance as a failure.

## Layers

| Layer                  | Command                        | External dependency        | What it verifies                                                                               |
| ---------------------- | ------------------------------ | -------------------------- | ---------------------------------------------------------------------------------------------- |
| Harness unit tests     | `cd evals && uv run pytest`    | None                       | Dataset, corpus, scoring, generation, and judge mechanics                                      |
| Ingestion              | `make eval-ingest`             | Docker                     | Flyway schema, recorded section coverage, metadata, offsets, idempotence, and pgvector writes  |
| Retrieval              | `make eval-retrieval`          | Docker; local MiniLM model | Real embedding/retrieval behavior, tenant isolation, amendment precedence, and evidence recall |
| Generation preparation | `make eval-prepare-generation` | None                       | Verified corpus spans rendered with the exact production answer prompt                         |
| Generation             | `make eval-generate`           | Gemini API                 | Structured production-model answers, deterministic scoring, latency, and token usage           |
| Production canary      | `make eval-production`         | Docker and Gemini API      | One critical amendment question through real retrieval and `AnswerQuestionService`             |
| Model judge            | `make eval-judge`              | Selected local CLI/backend | Groundedness, completeness, citation entailment, or refusal judgment sampled twice             |

## Setup

Prerequisites:

- Python 3.13 and [uv](https://docs.astral.sh/uv/) for the Python harness
- Java 21 and Docker for backend ingestion/retrieval evaluations
- `GEMINI_API_KEY` in `backend/.env` for generation or the production canary
- The selected `codex`, `claude`, or `ollama` CLI for model-based judging

Install the Python environment:

```bash
cd evals
uv sync
uv run pytest
```

## Deterministic pipeline

From the repository root:

```bash
make eval-ingest
make eval-retrieval
make eval-prepare-generation
```

Or run all three in sequence:

```bash
make eval
```

`eval-ingest` and `eval-retrieval` invoke Maven Failsafe with the `eval` profile and start an isolated PostgreSQL/pgvector Testcontainer. Retrieval writes `backend/target/eval-reports/retrieval-tesla-2025-v1.json`.

Generation preparation creates an ignored run directory under `evals/reports/<timestamp>-<commit>/` containing:

- `generation-inputs.json` with verified evidence and the rendered production prompt,
- `run-manifest.json` with dataset, corpus, prompt, and Git hashes.

It makes no network or model request.

## Gemini generation

Generate one low-cost canary first:

```bash
make eval-generate CASE_ID=tsla-2025-amendment-purpose
```

Generate every verified case:

```bash
make eval-generate
```

Resume or select a prepared run:

```bash
make eval-generate RUN_DIR=reports/<run-id>
```

Override the production-default model only when deliberately comparing models:

```bash
make eval-generate MODEL=gemini-2.5-flash-lite
```

The command loads `GEMINI_API_KEY` from `backend/.env`. It writes structured answers and deterministic scores after each completed case, so an interrupted run can resume. API keys are never written to reports.

The separate end-to-end canary uses production retrieval instead of frozen gold context:

```bash
make eval-production
```

This is intentionally outside CI and makes a real Gemini request.

## Model-based judging

Judge the latest completed generation run with Codex:

```bash
make eval-judge
```

Select another supported backend or a run directory:

```bash
make eval-judge BACKEND=claude_cli MODEL=sonnet RUN_DIR=reports/<run-id>
make eval-judge BACKEND=ollama MODEL=llama3.1 RUN_DIR=reports/<run-id>
```

Each selected dimension is evaluated twice and disagreement is recorded. Judge results are schema-validated and cached by backend, model, schema, and prompt hash. Codex runs ephemerally in an empty read-only working directory with user configuration and repository rules disabled.

Calibration currently reports label coverage and requires manual review:

```bash
make eval-calibrate
```

## Recording a corpus

Corpus recording is explicit and is the only default workflow that fetches SEC section data:

```bash
make eval-record-corpus \
  BASE_URL=http://localhost:8100/filings/sections \
  TICKER=TSLA \
  ACCESSION='0001628280-25-003063 0001104659-25-042659'
```

Optionally preserve a local raw filing for audit evidence:

```bash
make eval-record-corpus \
  BASE_URL=http://localhost:8100/filings/sections \
  ACCESSION='0001104659-25-042659' \
  RAW_HTML=/path/to/filing.html
```

The recorder writes response files and a sorted SHA-256 manifest under `evals/corpus/<corpus>/`. Review recorded content and dataset spans before treating them as gold evidence.

## Layout

```text
calibration/     manually reviewed judge labels
corpus/          recorded and hashed EDGAR sidecar responses
datasets/        question cases and ingestion expectations
judges/          Codex, Claude CLI, and Ollama adapters
rubrics/         groundedness, completeness, citation, and refusal rules
schemas/         structured judge output contract
tests/           offline harness tests
tools/           explicit corpus recorder
run_eval.py      generation preparation and Gemini execution
judge_answers.py model-based judging and calibration entrypoint
```

`reports/` and `.judge-cache/` are local and ignored by Git.
