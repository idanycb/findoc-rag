"""Factories and constants for building test filings. Not collected by pytest."""

from app import service

from .fakes import FakeCompany, FakeFiling

ORIGINAL_10K = "0000320193-24-000100"
AMENDMENT_10K = "0000320193-25-000010"
ORIGINAL_10Q = "0000320193-24-000200"
AMENDMENT_10Q = "0000320193-24-000250"


def original_10k(**overrides):
    return FakeFiling(**{"accession": ORIGINAL_10K, "form": "10-K", **overrides})


def amendment_10k(**overrides):
    return FakeFiling(
        **{"accession": AMENDMENT_10K, "form": "10-K/A", "filing_date": "2025-01-15", **overrides}
    )


def original_10q(**overrides):
    return FakeFiling(
        **{
            "accession": ORIGINAL_10Q,
            "form": "10-Q",
            "filing_date": "2024-08-01",
            "period_of_report": "2024-06-29",
            **overrides,
        }
    )


def amendment_10q(**overrides):
    return FakeFiling(
        **{
            "accession": AMENDMENT_10Q,
            "form": "10-Q/A",
            "filing_date": "2024-09-01",
            "period_of_report": "2024-06-29",
            **overrides,
        }
    )


def patch_company(monkeypatch, filings_by_form, calls=None):
    company = FakeCompany(filings_by_form, calls)
    monkeypatch.setattr(service, "get_company", lambda ticker_or_cik: company)
    return company
