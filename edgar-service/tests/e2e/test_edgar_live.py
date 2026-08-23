"""End-to-end tests that drive the full app against the real SEC EDGAR network.

Excluded from the default run (see pyproject `addopts = -m 'not e2e'`). Run with:

    EDGAR_IDENTITY="you you@example.com" uv run pytest -m e2e

Keep these to a thin smoke test: they are slow and subject to SEC rate limits
and data drift. The deterministic HTTP-contract coverage lives in
tests/integration/test_routes.py.
"""

import os

import pytest
from fastapi.testclient import TestClient

from app.main import app

pytestmark = pytest.mark.e2e

client = TestClient(app)


@pytest.fixture(autouse=True)
def _require_identity():
    if not (os.getenv("EDGAR_IDENTITY") or os.getenv("SEC_USER_AGENT")):
        pytest.skip("Set EDGAR_IDENTITY to run live SEC tests.")


def test_companies_search_finds_apple():
    response = client.get("/companies", params={"q": "Apple Inc"})

    assert response.status_code == 200
    tickers = {row["ticker"] for row in response.json()}
    assert "AAPL" in tickers


def test_apple_filings_and_sections_roundtrip():
    filings = client.get("/companies/AAPL/filings", params={"form": "10-K", "limit": 1})
    assert filings.status_code == 200
    assert filings.json(), "expected at least one 10-K"

    accession = filings.json()[0]["accessionNumber"]
    sections = client.get("/filings/sections", params={"ticker": "AAPL", "accession": accession})

    assert sections.status_code == 200
    body = sections.json()
    assert body["hasSearchableSections"] is True
    items = {section["item"] for section in body["sections"]}
    # Risk Factors is always present in a 10-K; edgartools may prefix a Part.
    assert any(item.endswith("Item 1A") for item in items)
