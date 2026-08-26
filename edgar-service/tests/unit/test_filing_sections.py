import pytest

from app.service import EdgarResourceNotFoundError, get_filing_sections

from .support import (
    AMENDMENT_10K,
    ORIGINAL_10K,
    FakeFiling,
    FakeReport,
    amendment_10k,
    original_10k,
    patch_company,
)


def test_get_filing_sections_includes_original_accession_for_amendment(monkeypatch):
    filing = amendment_10k(report=FakeReport(form="10-K/A"))
    patch_company(monkeypatch, {"10-K/A": [filing], "10-K": [original_10k()]})

    result = get_filing_sections("AAPL", AMENDMENT_10K)

    assert result.filing.accessionNumber == AMENDMENT_10K
    assert result.filing.form == "10-K/A"
    assert result.filing.amendsAccessionNumber == ORIGINAL_10K
    assert result.sections
    assert result.hasSearchableSections is True


def test_get_filing_sections_leaves_amends_null_for_original_10k(monkeypatch):
    patch_company(monkeypatch, {"10-K": [original_10k(report=FakeReport())]})

    result = get_filing_sections("AAPL", ORIGINAL_10K)

    assert result.filing.accessionNumber == ORIGINAL_10K
    assert result.filing.amendsAccessionNumber is None
    assert result.hasSearchableSections is True


def test_get_filing_sections_accepts_dashless_amendment_accession(monkeypatch):
    filing = amendment_10k(report=FakeReport(form="10-K/A"))
    patch_company(monkeypatch, {"10-K/A": [filing], "10-K": [original_10k()]})

    result = get_filing_sections("AAPL", AMENDMENT_10K.replace("-", ""))

    assert result.filing.amendsAccessionNumber == ORIGINAL_10K


def test_get_filing_sections_unmatched_amendment_has_null_original(monkeypatch):
    patch_company(monkeypatch, {"10-K/A": [amendment_10k(report=FakeReport(form="10-K/A"))]})

    result = get_filing_sections("AAPL", AMENDMENT_10K)

    assert result.filing.amendsAccessionNumber is None
    assert result.hasSearchableSections is True


def test_get_filing_sections_unknown_accession_is_not_found_error(monkeypatch):
    patch_company(monkeypatch, {})

    with pytest.raises(EdgarResourceNotFoundError, match="was not found"):
        get_filing_sections("AAPL", "0000000000-00-000000")


@pytest.mark.parametrize(
    "report",
    [
        pytest.param(FakeReport(form="10-K/A", sections={}), id="no-standard-sections"),
        pytest.param(None, id="no-report-object"),
    ],
)
def test_get_filing_sections_without_searchable_narrative_returns_empty(monkeypatch, report):
    patch_company(monkeypatch, {"10-K/A": [amendment_10k(report=report)], "10-K": [original_10k()]})

    result = get_filing_sections("AAPL", AMENDMENT_10K)

    assert result.filing.accessionNumber == AMENDMENT_10K
    assert result.filing.amendsAccessionNumber == ORIGINAL_10K
    assert result.filing.form == "10-K/A"
    assert result.sections == []
    assert result.hasSearchableSections is False


def test_get_filing_sections_original_without_sections_is_not_a_lookup_error(monkeypatch):
    patch_company(monkeypatch, {"10-K": [original_10k(report=FakeReport(sections={}))]})

    result = get_filing_sections("AAPL", ORIGINAL_10K)

    assert result.filing.amendsAccessionNumber is None
    assert result.sections == []
    assert result.hasSearchableSections is False


def test_get_filing_sections_blank_accession_is_normalization_error(monkeypatch):
    patch_company(monkeypatch, {})
    monkeypatch.setattr(
        "app.service.resolve_filing",
        lambda company, accession: FakeFiling(accession="", report=FakeReport()),
    )

    with pytest.raises(ValueError, match="accession number"):
        get_filing_sections("AAPL", ORIGINAL_10K)
