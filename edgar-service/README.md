# EDGAR Service

A small FastAPI sidecar that wraps [edgartools](https://github.com/dgunning/edgartools) and exposes JSON endpoints for searching SEC filers, listing their filings, and extracting narrative from 10-K / 10-Q filings and amendments. It exists so the rest of the stack can talk to SEC EDGAR over plain HTTP instead of embedding a Python library and its data-fetching quirks into every service that needs filings.

Runs on port `8100`. Python 3.13, managed with [uv](https://github.com/astral-sh/uv).

## Why it's a separate service

edgartools is Python-only and does a fair amount of network I/O, caching, and HTML parsing under the hood. Keeping it behind a thin API means:

- callers in any language get a stable JSON contract and don't inherit the library's surface area,
- SEC-specific concerns (identity/User-Agent, rate limits, local caching) live in one place,
- the messy bits — inconsistent return types, section labelling — get normalised here once.

## Endpoints

| method & path                                         | purpose                                                      |
| ----------------------------------------------------- | ------------------------------------------------------------ |
| `GET /health`                                         | liveness check                                               |
| `GET /companies?q=&limit=`                            | full-text filer search (CIKs zero-padded to 10 digits)       |
| `GET /companies/{ticker_or_cik}/filings?form=&limit=` | list a filer's recent filings of one form, newest first      |
| `GET /filings/sections?ticker=&accession=`            | extract 10-K / 10-Q (and amendment) sections from one filing |

That's the summary. The **authoritative, always-current contract** — every param, response schema, and example — is generated from the code by FastAPI. Boot the service and open:

- **`/docs`** — Swagger UI (try requests in the browser; handy when wiring up an integration)
- **`/redoc`** — reference-style rendering
- **`/openapi.json`** — the raw spec (import into Postman/Insomnia or generate a client)

A few behaviours worth knowing up front, since they're contract decisions rather than obvious defaults:

- Listing is limited to `10-K`, `10-K/A`, `10-Q`, `10-Q/A`; any other `form` (including `8-K`) returns `422`. Each request is that exact form — listing `10-K` does not include `10-K/A`. Section extraction only resolves those same forms — an accession that is not one of them returns `404`.
- Section labels differ by form on purpose — 10-K items are returned bare (`Item 1A`), 10-Q items keep the part qualifier (`Part II Item 1A`), because 10-Q item numbers restart per part.
- Named narrative outside numbered items is retained when detected, including `Explanatory Note`, `Introductory Note`, and `Forward-Looking Note`. Duplicate or overlapping named/item sections are collapsed, with numbered items preferred.
- `fiscalPeriod` is inferred: `FY` for 10-K variants, `null` otherwise.
- Amendments (`10-K/A`, `10-Q/A`) include `amendsAccessionNumber`: the original `10-K` / `10-Q` with the same period of report, filed on or before the amendment. It points at that original, not the preceding amendment, so several `/A` filings can share one accession. SEC does not store that link, so the sidecar infers it. The field is `null` on ordinary 10-K / 10-Q filings and when no original can be matched.
- A found filing with no extractable narrative (for example a certification-only 10-K/A) returns `200` with `sections: []` and `hasSearchableSections: false`. `404` means the filer or accession was not found.
- Section lookup accepts canonical dashed accessions and 18-digit dashless accessions. Dashless input is converted to the canonical dashed form before calling edgartools so it cannot be misread as a collection index.
- A filing whose accession number is blank after normalisation is a `422`, not an empty `accessionNumber`.
- Errors: `404` (filer/filing not found), `422` (bad params, unsupported form, or filing metadata that cannot be normalised), `502` (upstream edgartools/SEC failure — detail is generic, full traceback is logged server-side).

## Configuration

All via environment variables.

| variable               | required | default            | purpose                                                                                                                             |
| ---------------------- | -------- | ------------------ | ----------------------------------------------------------------------------------------------------------------------------------- |
| `EDGAR_IDENTITY`       | yes\*    | —                  | User-Agent the SEC requires on every request, e.g. `findoc-analyzer contact@example.com`. `SEC_USER_AGENT` is accepted as an alias. |
| `EDGAR_LOCAL_DATA_DIR` | no       | `/tmp/edgar/data`  | where edgartools stores downloaded data                                                                                             |
| `EDGAR_CACHE_DIR`      | no       | `/tmp/edgar/cache` | edgartools cache directory                                                                                                          |

\* Not enforced at startup — the service logs a warning and runs without it, but the SEC will throttle or reject anonymous traffic, so treat it as required in any real deployment.

## Running locally

```bash
uv sync
uv run uvicorn app.main:app --reload --port 8100
```

The service reads config from the process environment. It does **not** auto-load a `.env` file, so either export the vars or point uv at a file:

```bash
uv run --env-file .env uvicorn app.main:app --port 8100
```

`edgar-service/.env` is gitignored. For this repo, the `EDGAR_IDENTITY` value mirrors the one in the root `.env`.

## Docker

```bash
docker build -t edgar-service .
docker run -p 8100:8100 -e EDGAR_IDENTITY="findoc-analyzer you@example.com" edgar-service
```

Under the root `docker-compose.yml` the service is built from `./edgar-service`, reads the shared `.env`, and has `EDGAR_IDENTITY` injected for it — no per-service env file needed in that path.

## Tests

Split by scope:

```
tests/
  unit/         pure functions — no network, no HTTP
    support/    shared fakes and filing factories (not collected)
  integration/  FastAPI routes via TestClient, service layer mocked
  e2e/          drives the full app against the real SEC, marked `e2e` and opt-in
```

The default run stays fast and offline (the `e2e` marker is deselected in `pyproject.toml`):

```bash
uv run pytest
```

Run the end-to-end tests against the real SEC when you want a full-stack check. They skip themselves if no identity is set:

```bash
uv run --env-file .env pytest -m e2e
```

The e2e tests are deliberately thin — they're slow and subject to SEC rate limits and data drift. Route and error-mapping coverage lives in `integration/test_routes.py`. Unit tests are split by concern under `unit/` and share doubles from `unit/support/`. The e2e ones only confirm the real SEC integration still holds together.

## Integrating into another project

- Talk to it over HTTP; treat it as an internal service, not something to expose publicly.
- Always set `EDGAR_IDENTITY` to a real app name + contact — the SEC uses it to identify traffic.
- Point `EDGAR_LOCAL_DATA_DIR` / `EDGAR_CACHE_DIR` at a persistent volume if you want caching to survive restarts; the `/tmp` defaults don't.
- The `502` responses wrap any upstream failure, so callers should retry with backoff rather than treating them as permanent.
- Section text is returned as-is from edgartools with per-line trailing whitespace stripped; it's plain text, not HTML.
- Treat `amendsAccessionNumber` as best-effort. It is inferred from period of report, not an SEC-provided pointer, and will be `null` when the original cannot be matched.
- Treat `hasSearchableSections: false` as a valid filing with no analyzable narrative, not a missing accession.

## Layout

```
app/
  main.py      FastAPI app, routing, HTTP error mapping
  service.py   edgartools calls + all the normalising logic
  schemas.py   pydantic response models
```

`main.py` stays deliberately thin: it validates input, calls `service.py`, and maps exceptions to status codes. Everything else — talking to edgartools, reshaping its output, handling its inconsistencies — lives in `service.py`.
