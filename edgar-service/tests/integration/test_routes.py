"""Route-layer integration tests.

These exercise the FastAPI HTTP contract (routing, query validation, and the
exception -> status-code mapping in app.main) with the service layer mocked out,
so they run fast and deterministically with no network. Real SEC calls live in
test_edgar_live.py.
"""

import pytest
from fastapi.testclient import TestClient

from app.main import app
from app.schemas import (
    CompanyMetadata,
    CompanyResult,
    FilingMetadata,
    FilingResult,
    FilingSection,
    FilingSectionsResponse,
)

client = TestClient(app)


def test_health_returns_ok():
    response = client.get("/health")

    assert response.status_code == 200
    assert response.json() == {"status": "ok"}


# --- /companies ---------------------------------------------------------------


def test_companies_returns_mapped_results(monkeypatch):
    monkeypatch.setattr(
        "app.main.search_companies",
        lambda q, limit: [CompanyResult(ticker="AAPL", cik="0000320193", name="Apple Inc.")],
    )

    response = client.get("/companies", params={"q": "apple"})

    assert response.status_code == 200
    assert response.json() == [{"ticker": "AAPL", "cik": "0000320193", "name": "Apple Inc."}]


def test_companies_missing_query_is_422():
    assert client.get("/companies").status_code == 422


@pytest.mark.parametrize("limit", [0, 26])
def test_companies_limit_out_of_range_is_422(limit):
    response = client.get("/companies", params={"q": "apple", "limit": limit})

    assert response.status_code == 422


def test_companies_service_error_maps_to_502(monkeypatch):
    def boom(q, limit):
        raise RuntimeError("edgar exploded")

    monkeypatch.setattr("app.main.search_companies", boom)

    response = client.get("/companies", params={"q": "apple"})

    assert response.status_code == 502
    assert response.json()["detail"] == "EDGAR company search failed."


# --- /companies/{ticker_or_cik}/filings --------------------------------------


def test_company_filings_returns_mapped_results(monkeypatch):
    monkeypatch.setattr(
        "app.main.list_filings",
        lambda ticker_or_cik, form, limit: [
            FilingResult(accessionNumber="0000320193-24-000123", form="10-K")
        ],
    )

    response = client.get("/companies/AAPL/filings")

    assert response.status_code == 200
    assert response.json()[0]["accessionNumber"] == "0000320193-24-000123"


def test_company_filings_not_found_maps_to_404(monkeypatch):
    def missing(ticker_or_cik, form, limit):
        raise LookupError("Company 'ZZZZ' was not found.")

    monkeypatch.setattr("app.main.list_filings", missing)

    response = client.get("/companies/ZZZZ/filings")

    assert response.status_code == 404
    assert response.json()["detail"] == "Company 'ZZZZ' was not found."


def test_company_filings_service_error_maps_to_502(monkeypatch):
    def boom(ticker_or_cik, form, limit):
        raise RuntimeError("edgar exploded")

    monkeypatch.setattr("app.main.list_filings", boom)

    response = client.get("/companies/AAPL/filings")

    assert response.status_code == 502
    assert response.json()["detail"] == "EDGAR filing lookup failed."


@pytest.mark.parametrize("limit", [0, 51])
def test_company_filings_limit_out_of_range_is_422(limit):
    response = client.get("/companies/AAPL/filings", params={"limit": limit})

    assert response.status_code == 422


# --- /filings/sections -------------------------------------------------------


def _sections_response() -> FilingSectionsResponse:
    return FilingSectionsResponse(
        company=CompanyMetadata(ticker="AAPL", cik="0000320193", name="Apple Inc."),
        filing=FilingMetadata(accessionNumber="0000320193-24-000123", form="10-K"),
        sourceUrl="https://www.sec.gov/example",
        sections=[FilingSection(item="Item 1", title="Business", text="Business overview")],
    )


def test_filing_sections_returns_payload(monkeypatch):
    monkeypatch.setattr("app.main.get_filing_sections", lambda ticker, accession: _sections_response())

    response = client.get(
        "/filings/sections", params={"ticker": "AAPL", "accession": "0000320193-24-000123"}
    )

    assert response.status_code == 200
    body = response.json()
    assert body["company"]["ticker"] == "AAPL"
    assert body["sections"][0]["item"] == "Item 1"


def test_filing_sections_missing_params_is_422():
    assert client.get("/filings/sections").status_code == 422


def test_filing_sections_not_found_maps_to_404(monkeypatch):
    def missing(ticker, accession):
        raise LookupError("Filing accession 'x' was not found for 'AAPL'.")

    monkeypatch.setattr("app.main.get_filing_sections", missing)

    response = client.get("/filings/sections", params={"ticker": "AAPL", "accession": "x"})

    assert response.status_code == 404


def test_filing_sections_unsupported_form_maps_to_422(monkeypatch):
    def unsupported(ticker, accession):
        raise ValueError("Section extraction is supported for [...] not 8-K.")

    monkeypatch.setattr("app.main.get_filing_sections", unsupported)

    response = client.get("/filings/sections", params={"ticker": "AAPL", "accession": "x"})

    assert response.status_code == 422


def test_filing_sections_service_error_maps_to_502(monkeypatch):
    def boom(ticker, accession):
        raise RuntimeError("edgar exploded")

    monkeypatch.setattr("app.main.get_filing_sections", boom)

    response = client.get("/filings/sections", params={"ticker": "AAPL", "accession": "x"})

    assert response.status_code == 502
    assert response.json()["detail"] == "EDGAR section extraction failed."
