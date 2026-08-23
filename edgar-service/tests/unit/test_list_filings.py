import pytest

from app.service import list_filings

from .support import (
    AMENDMENT_10K,
    DEFAULT_ACCESSION,
    ORIGINAL_10K,
    ORIGINAL_10Q,
    FakeFiling,
    amendment_10k,
    amendment_10q,
    original_10k,
    original_10q,
    patch_company,
)


def test_list_filings_limit_one_requests_exact_10k(monkeypatch):
    calls = []
    patch_company(monkeypatch, {"10-K": [FakeFiling()]}, calls)

    result = list_filings("AAPL", form="10-K", limit=1)

    assert len(result) == 1
    assert result[0].accessionNumber == DEFAULT_ACCESSION
    assert result[0].amendsAccessionNumber is None
    assert calls == [("10-K", False)]


def test_list_filings_larger_limit_returns_a_collection(monkeypatch):
    patch_company(monkeypatch, {"10-K": [FakeFiling(), FakeFiling()]})

    assert len(list_filings("AAPL", form="10-K", limit=5)) == 2


def test_list_filings_returns_empty_when_no_filings(monkeypatch):
    patch_company(monkeypatch, {})

    assert list_filings("AAPL", form="10-K", limit=5) == []


@pytest.mark.parametrize("accession", ["", "  "])
def test_list_filings_rejects_blank_accession(monkeypatch, accession):
    patch_company(monkeypatch, {"10-K": [FakeFiling(accession=accession)]})

    with pytest.raises(ValueError, match="accession number"):
        list_filings("AAPL", form="10-K", limit=1)


@pytest.mark.parametrize("form", ["8-K", "20-F"])
def test_list_filings_rejects_unsupported_forms(form):
    with pytest.raises(ValueError, match=form):
        list_filings("AAPL", form=form, limit=5)


@pytest.mark.parametrize("form", ["10-Q", "10-K/A", "10-Q/A"])
def test_list_filings_accepts_supported_forms(monkeypatch, form):
    patch_company(monkeypatch, {form: [FakeFiling(form=form)]})

    assert len(list_filings("AAPL", form=form, limit=1)) == 1


def test_list_filings_10ka_attaches_matching_original(monkeypatch):
    calls = []
    patch_company(monkeypatch, {"10-K/A": [amendment_10k()], "10-K": [original_10k()]}, calls)

    result = list_filings("AAPL", form="10-K/A", limit=1)

    assert result[0].accessionNumber == AMENDMENT_10K
    assert result[0].amendsAccessionNumber == ORIGINAL_10K
    assert ("10-K/A", True) in calls
    assert ("10-K", False) in calls


def test_list_filings_10qa_attaches_matching_original(monkeypatch):
    patch_company(monkeypatch, {"10-Q/A": [amendment_10q()], "10-Q": [original_10q()]})

    result = list_filings("AAPL", form="10-Q/A", limit=1)

    assert result[0].amendsAccessionNumber == ORIGINAL_10Q


def test_list_filings_matches_each_amendment_to_its_own_period(monkeypatch):
    fy23 = original_10k(
        accession="0000320193-23-000100",
        filing_date="2023-11-03",
        period_of_report="2023-09-30",
    )
    a23 = amendment_10k(
        accession="0000320193-24-000011",
        filing_date="2024-01-15",
        period_of_report="2023-09-30",
    )
    patch_company(monkeypatch, {"10-K/A": [a23, amendment_10k()], "10-K": [fy23, original_10k()]})

    by_accession = {
        row.accessionNumber: row.amendsAccessionNumber
        for row in list_filings("AAPL", form="10-K/A", limit=5)
    }

    assert by_accession["0000320193-24-000011"] == "0000320193-23-000100"
    assert by_accession[AMENDMENT_10K] == ORIGINAL_10K


def test_list_filings_multiple_amendments_can_share_the_same_original(monkeypatch):
    first = amendment_10k(accession="0000320193-25-000010")
    second = amendment_10k(accession="0000320193-25-000020", filing_date="2025-03-01")
    patch_company(monkeypatch, {"10-K/A": [first, second], "10-K": [original_10k()]})

    by_accession = {
        row.accessionNumber: row.amendsAccessionNumber
        for row in list_filings("AAPL", form="10-K/A", limit=5)
    }

    assert by_accession["0000320193-25-000010"] == ORIGINAL_10K
    assert by_accession["0000320193-25-000020"] == ORIGINAL_10K


@pytest.mark.parametrize(
    "filings_by_form",
    [
        pytest.param({"10-K/A": [amendment_10k()]}, id="missing"),
        pytest.param(
            {"10-K/A": [amendment_10k()], "10-K": [original_10k(period_of_report="2023-09-30")]},
            id="wrong-period",
        ),
        pytest.param(
            {"10-K/A": [amendment_10k()], "10-Q": [original_10q(period_of_report="2024-09-28")]},
            id="wrong-form",
        ),
    ],
)
def test_list_filings_leaves_amends_null_when_unmatched(monkeypatch, filings_by_form):
    patch_company(monkeypatch, filings_by_form)

    result = list_filings("AAPL", form="10-K/A", limit=1)

    assert result[0].amendsAccessionNumber is None
