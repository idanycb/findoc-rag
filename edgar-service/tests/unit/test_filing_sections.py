import pytest

from app.service import get_filing_sections

from .support import (
    AMENDMENT_10K,
    ORIGINAL_10K,
    FakeReport,
    amendment_10k,
    original_10k,
    patch_company,
)


def test_get_filing_sections_includes_original_accession_for_amendment(monkeypatch):
    filing = amendment_10k(report=FakeReport(form="10-K/A"))
    patch_company(monkeypatch, {"10-K/A": [filing], "10-K": [original_10k()]})

    result = get_filing_sections("AAPL", AMENDMENT_10K)

    assert result.filing.form == "10-K/A"
    assert result.filing.amendsAccessionNumber == ORIGINAL_10K
    assert result.sections


def test_get_filing_sections_leaves_amends_null_for_original_10k(monkeypatch):
    patch_company(monkeypatch, {"10-K": [original_10k(report=FakeReport())]})

    result = get_filing_sections("AAPL", ORIGINAL_10K)

    assert result.filing.amendsAccessionNumber is None


def test_get_filing_sections_accepts_dashless_amendment_accession(monkeypatch):
    filing = amendment_10k(report=FakeReport(form="10-K/A"))
    patch_company(monkeypatch, {"10-K/A": [filing], "10-K": [original_10k()]})

    result = get_filing_sections("AAPL", AMENDMENT_10K.replace("-", ""))

    assert result.filing.amendsAccessionNumber == ORIGINAL_10K


def test_get_filing_sections_unmatched_amendment_has_null_original(monkeypatch):
    patch_company(monkeypatch, {"10-K/A": [amendment_10k(report=FakeReport(form="10-K/A"))]})

    result = get_filing_sections("AAPL", AMENDMENT_10K)

    assert result.filing.amendsAccessionNumber is None


def test_get_filing_sections_unknown_accession_is_lookup_error(monkeypatch):
    patch_company(monkeypatch, {})

    with pytest.raises(LookupError, match="was not found"):
        get_filing_sections("AAPL", "0000000000-00-000000")


def test_get_filing_sections_no_extractable_sections_is_lookup_error(monkeypatch):
    filing = amendment_10k(report=FakeReport(form="10-K/A", sections={}))
    patch_company(monkeypatch, {"10-K/A": [filing], "10-K": [original_10k()]})

    with pytest.raises(LookupError, match="No structured sections"):
        get_filing_sections("AAPL", AMENDMENT_10K)
