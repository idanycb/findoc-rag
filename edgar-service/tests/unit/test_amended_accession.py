"""Match a 10-K/A or 10-Q/A to the original filing that shares its period of report."""

import pytest

from app.service import load_original_filings, resolve_amended_accession

from .support import (
    ORIGINAL_10K,
    ORIGINAL_10Q,
    FakeCompany,
    amendment_10k,
    amendment_10q,
    original_10k,
    original_10q,
)


def test_load_original_filings_skips_non_amendments():
    assert load_original_filings(FakeCompany({"10-K": [original_10k()]}), "10-K") == []


def test_load_original_filings_fetches_base_form_without_amendment_expansion():
    calls = []
    original = original_10k()

    result = load_original_filings(FakeCompany({"10-K": [original]}, calls), "10-K/A")

    assert calls == [("10-K", False)]
    assert result == [original]


def test_resolve_matches_same_period_10k_and_10q():
    assert resolve_amended_accession(amendment_10k(), [original_10k()]) == ORIGINAL_10K
    assert resolve_amended_accession(amendment_10q(), [original_10q()]) == ORIGINAL_10Q


def test_resolve_accepts_original_filed_on_the_same_day():
    assert (
        resolve_amended_accession(
            amendment_10k(filing_date="2024-11-01"),
            [original_10k(filing_date="2024-11-01")],
        )
        == ORIGINAL_10K
    )


def test_resolve_picks_latest_original_filed_on_or_before_amendment():
    first = original_10k(accession="0000320193-24-000050", filing_date="2024-11-01")
    restated = original_10k(accession="0000320193-25-000001", filing_date="2025-02-01")

    assert resolve_amended_accession(amendment_10k(filing_date="2025-03-01"), [first, restated]) == (
        "0000320193-25-000001"
    )


@pytest.mark.parametrize(
    ("amendment", "originals"),
    [
        pytest.param(amendment_10k(filing_date=None), [original_10k()], id="amendment-date-missing"),
        pytest.param(amendment_10k(), [original_10k(filing_date=None)], id="original-date-missing"),
        pytest.param(
            amendment_10k(report_date="20240928", period_of_report=None),
            [original_10k()],
            id="dashless-report-date",
        ),
    ],
)
def test_resolve_matches_when_dates_are_incomplete_or_unnormalized(amendment, originals):
    assert resolve_amended_accession(amendment, originals) == ORIGINAL_10K


def test_resolve_returns_none_for_non_amendment():
    assert resolve_amended_accession(original_10k(), [original_10k()]) is None


@pytest.mark.parametrize(
    "originals",
    [
        pytest.param([], id="empty"),
        pytest.param([original_10k(period_of_report="2023-09-30")], id="wrong-period"),
        pytest.param([original_10k(report_date="", period_of_report=None)], id="original-missing-report-date"),
        pytest.param(
            [original_10k(accession="0000320193-25-000099", filing_date="2025-06-01")],
            id="filed-later",
        ),
        pytest.param([original_10k(accession="")], id="blank-accession"),
        pytest.param(
            [original_10k(accession="0000320193-24-000099", period_of_report="2024-09-27")],
            id="nearby-period",
        ),
    ],
)
def test_resolve_returns_none_when_original_is_ineligible(originals):
    assert resolve_amended_accession(amendment_10k(), originals) is None


def test_resolve_returns_none_when_amendment_has_no_report_date():
    assert resolve_amended_accession(amendment_10k(report_date="", period_of_report=None), [original_10k()]) is None
