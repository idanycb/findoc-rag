import pytest

from app.schemas import FilingSection
from app.service import extract_sections, normalize_item

from .support import FakeReport, FakeSection


def test_extract_sections_returns_structured_items():
    result = extract_sections(FakeReport())

    assert result[:3] == [
        FilingSection(item="Item 1", title="Item 1. Business", text="Business overview"),
        FilingSection(item="Item 1A", title="Item 1A. Risk Factors", text="Risk text"),
        FilingSection(item="Item 7", title="Item 7. MD&A", text="Management discussion"),
    ]


@pytest.mark.parametrize(
    "report",
    [
        pytest.param(FakeReport(sections={}), id="empty-sections"),
        pytest.param(None, id="missing-report"),
    ],
)
def test_extract_sections_returns_empty_when_nothing_is_extractable(report):
    assert extract_sections(report) == []


def test_extract_sections_skips_blank_section_text():
    report = FakeReport(sections={"Item 1": FakeSection("1", "Business", "  \n")})

    assert extract_sections(report) == []


def test_normalize_item_drops_part_prefix_for_ten_k():
    assert normalize_item("Item 1A", FakeSection("1A", "Item 1A. Risk Factors", "", part="I"), include_part=False) == (
        "Item 1A"
    )


def test_normalize_item_keeps_part_prefix_for_ten_q():
    assert normalize_item("Part II Item 1", FakeSection("1", "Item 1", "", part="II"), include_part=True) == (
        "Part II Item 1"
    )


def test_normalize_item_falls_back_when_ten_q_section_lacks_part():
    assert normalize_item("Part II Item 1", FakeSection("1", "Item 1", "", part=None), include_part=True) == (
        "Part II Item 1"
    )


def test_normalize_item_uses_fallback_when_section_has_no_item():
    assert normalize_item("Item 7", FakeSection(None, "", "")) == "Item 7"
