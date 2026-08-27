# FinDoc Local Evals

This harness is local-only. It does not fetch SEC data unless you run
`tools/record_corpus.py` yourself, and it will not invent gold evidence when the
corpus is missing.

Current dataset state:

- `tesla-2025-v1.jsonl` contains 14 questions across 7 categories.
- 12 cases are corpus-verified; two metadata-contract cases remain `pending`.
- Gold spans carry exact offsets and SHA-256 hashes, which are checked before
  generation inputs are prepared.
- Scoring is deterministic and only grades what the dataset actually verifies.

The committed corpus contains Tesla's 2025 10-K and 10-K/A sidecar responses.
`make eval-ingest` verifies exact section coverage, metadata, nullable EDGAR
pages, chunk offsets, and idempotence against PostgreSQL/pgvector. `make
eval-retrieval` runs the real local MiniLM model, enforces tenant isolation, and
writes `backend/target/eval-reports/retrieval-tesla-2025-v1.json`.

`run_eval.py --stage prepare-generation` freezes verified gold context with the
exact production prompt into an immutable local run directory and makes no
external request. `run_eval.py --stage generation` deliberately sends those
public SEC fixture spans to the Gemini API using the production model and
structured answer contract. The API key is read only from `GEMINI_API_KEY` and
is never written to a report. Start with `--case-id` for a low-token canary.

`make eval-production` is the separate end-to-end canary: it runs the real
MiniLM/pgvector retrieval, `AnswerQuestionService`, production prompt adapter,
and Gemini model for the critical Tesla amendment question. It is skipped unless
the key is deliberately loaded and remains outside CI.

The judge command expects an `answers.json` in that run directory. It evaluates
each selected dimension twice, flags verdict disagreement, and keeps the Codex
backend isolated in an empty read-only working directory.
