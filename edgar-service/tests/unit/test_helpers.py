import pytest

from app.service import (
    accession_number_candidates,
    collection_as_list,
    filing_report_date,
    is_amendment_form,
    normalize_cik,
    normalize_report_date,
    original_form,
)

from .support import FakeFiling, FakeFilings, original_10k


@pytest.mark.parametrize(
    ("form", "expected"),
    [
        ("10-K/A", True),
        ("10-Q/A", True),
        (" 10-k/a ", True),
        ("10-K", False),
        ("10-Q", False),
        ("8-K", False),
        ("", False),
    ],
)
def test_is_amendment_form(form, expected):
    assert is_amendment_form(form) is expected


@pytest.mark.parametrize(
    ("form", "expected"),
    [("10-K/A", "10-K"), ("10-Q/A", "10-Q"), ("10-K", "10-K"), (" 10-Q/A ", "10-Q")],
)
def test_original_form_strips_amendment_suffix(form, expected):
    assert original_form(form) == expected


@pytest.mark.parametrize(
    ("value", "expected"),
    [
        (None, None),
        ("", None),
        ("   ", None),
        ("2024-09-28", "2024-09-28"),
        ("20240928", "2024-09-28"),
        ("FY2024", "FY2024"),
    ],
)
def test_normalize_report_date(value, expected):
    assert normalize_report_date(value) == expected


@pytest.mark.parametrize(
    ("kwargs", "expected"),
    [
        ({"report_date": "2023-09-30", "period_of_report": "2024-09-28"}, "2023-09-30"),
        ({"report_date": "", "period_of_report": "2024-09-28"}, "2024-09-28"),
        ({"report_date": "", "period_of_report": None}, None),
    ],
)
def test_filing_report_date_source_preference(kwargs, expected):
    assert filing_report_date(FakeFiling(**kwargs)) == expected


def test_collection_as_list_handles_empty_none_iterable_and_scalar():
    scalar = original_10k()

    assert collection_as_list(None) == []
    assert collection_as_list(FakeFilings([])) == []
    assert collection_as_list([1, 2]) == [1, 2]
    assert collection_as_list("0000320193-24-000100") == ["0000320193-24-000100"]
    assert collection_as_list(scalar) == [scalar]


def test_normalize_cik_preserves_empty_and_pads_numeric_values():
    assert normalize_cik("") == ""
    assert normalize_cik(123) == "0000000123"


def test_accession_number_candidates_accepts_dashless_values():
    assert accession_number_candidates("000032019324000123") == [
        "0000320193-24-000123",
    ]
