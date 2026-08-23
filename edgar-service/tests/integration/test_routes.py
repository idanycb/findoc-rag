"""Route-layer integration tests.

These exercise the FastAPI HTTP contract (routing, query validation, and the
exception -> status-code mapping in app.main). Service calls that would hit
EDGAR are mocked; paths that fail before any network call (e.g. unsupported
form) use the real service. Live SEC coverage lives in test_edgar_live.py.
"""

import pytest
from fastapi.testclient import TestClient

from app.main import app
from app.schemas import (
    CompanyResult,
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
        company=CompanyResult(ticker="AAPL", cik="0000320193", name="Apple Inc."),
        filing=FilingResult(accessionNumber="0000320193-24-000123", form="10-K"),
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


def test_filing_sections_service_error_maps_to_502(monkeypatch):
    def boom(ticker, accession):
        raise RuntimeError("edgar exploded")

    monkeypatch.setattr("app.main.get_filing_sections", boom)

    response = client.get("/filings/sections", params={"ticker": "AAPL", "accession": "x"})

    assert response.status_code == 502
    assert response.json()["detail"] == "EDGAR section extraction failed."


# --- /companies/{ticker_or_cik}/filings unsupported-form validation ----------


def test_company_filings_unsupported_form_maps_to_422():
    response = client.get("/companies/AAPL/filings", params={"form": "8-K"})

    assert response.status_code == 422
    assert "8-K" in response.json()["detail"]


def test_company_filings_10q_returns_200(monkeypatch):
    monkeypatch.setattr(
        "app.main.list_filings",
        lambda ticker_or_cik, form, limit: [
            FilingResult(accessionNumber="0000320193-24-000456", form="10-Q")
        ],
    )

    response = client.get("/companies/AAPL/filings", params={"form": "10-Q"})

    assert response.status_code == 200
    assert response.json()[0]["form"] == "10-Q"
    assert response.json()[0]["amendsAccessionNumber"] is None


def test_company_filings_10ka_includes_amends_accession_number(monkeypatch):
    monkeypatch.setattr(
        "app.main.list_filings",
        lambda ticker_or_cik, form, limit: [
            FilingResult(
                accessionNumber="0000320193-25-000010",
                form="10-K/A",
                amendsAccessionNumber="0000320193-24-000100",
            )
        ],
    )

    response = client.get("/companies/AAPL/filings", params={"form": "10-K/A"})

    assert response.status_code == 200
    body = response.json()[0]
    assert body["form"] == "10-K/A"
    assert body["amendsAccessionNumber"] == "0000320193-24-000100"


def test_filing_sections_includes_amends_accession_number(monkeypatch):
    monkeypatch.setattr(
        "app.main.get_filing_sections",
        lambda ticker, accession: FilingSectionsResponse(
            company=CompanyResult(ticker="AAPL", cik="0000320193", name="Apple Inc."),
            filing=FilingResult(
                accessionNumber="0000320193-25-000010",
                form="10-K/A",
                amendsAccessionNumber="0000320193-24-000100",
            ),
            sourceUrl="https://www.sec.gov/example",
            sections=[FilingSection(item="Item 1A", title="Risk Factors", text="Risk text")],
        ),
    )

    response = client.get(
        "/filings/sections",
        params={"ticker": "AAPL", "accession": "0000320193-25-000010"},
    )

    assert response.status_code == 200
    assert response.json()["filing"]["amendsAccessionNumber"] == "0000320193-24-000100"
