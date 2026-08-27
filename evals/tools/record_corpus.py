from __future__ import annotations

import argparse
import gzip
import hashlib
import json
import urllib.request
import urllib.parse
from pathlib import Path
from typing import Any


def sha256_bytes(payload: bytes) -> str:
    return hashlib.sha256(payload).hexdigest()


def fetch_json(base_url: str, ticker: str, accession: str) -> bytes:
    query = urllib.parse.urlencode({"ticker": ticker, "accession": accession})
    url = f"{base_url}?{query}"
    with urllib.request.urlopen(url) as response:  # noqa: S310
        return response.read()


def record(corpus: str, base_url: str, ticker: str, accessions: list[str], raw_html: str | None) -> dict[str, Any]:
    dataset_dir = Path(__file__).resolve().parents[1] / "corpus" / corpus
    dataset_dir.mkdir(parents=True, exist_ok=True)
    files: list[dict[str, Any]] = []
    for accession in accessions:
        payload = fetch_json(base_url, ticker, accession)
        output_path = dataset_dir / f"{accession}.json"
        output_path.write_bytes(payload)
        digest = sha256_bytes(payload)
        files.append({"path": output_path.name, "sha256": digest, "accession": accession})
    if raw_html:
        raw_path = Path(raw_html)
        compressed = gzip.compress(raw_path.read_bytes())
        output_path = dataset_dir / f"{raw_path.stem}.html.gz"
        output_path.write_bytes(compressed)
        digest = sha256_bytes(compressed)
        files.append({"path": output_path.name, "sha256": digest, "kind": "raw-html"})
    sorted_files = sorted(files, key=lambda row: row["path"])
    set_digest = hashlib.sha256()
    for file in sorted_files:
        set_digest.update(file["path"].encode("utf-8"))
        set_digest.update(file["sha256"].encode("utf-8"))
    manifest = {
        "corpus": corpus,
        "ticker": ticker,
        "files": sorted_files,
        "manifestSha256": set_digest.hexdigest(),
    }
    (dataset_dir / "manifest.json").write_text(json.dumps(manifest, indent=2, sort_keys=True) + "\n")
    return manifest


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--corpus", default="tesla-2025")
    parser.add_argument("--base-url", required=True, help="Sidecar endpoint, e.g. http://localhost:8100/filings/sections")
    parser.add_argument("--ticker", default="TSLA")
    parser.add_argument("--accession", action="append", dest="accessions", required=True)
    parser.add_argument("--raw-html", help="Optional local HTML file to store as gzipped raw corpus evidence.")
    args = parser.parse_args()
    print(json.dumps(
        record(args.corpus, args.base_url, args.ticker, args.accessions, args.raw_html),
        indent=2,
        sort_keys=True,
    ))


if __name__ == "__main__":
    main()
