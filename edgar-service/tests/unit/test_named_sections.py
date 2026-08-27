from app.schemas import FilingSection
from app.service import dedupe_sections, extract_sections

from .support import FakeReport, FakeSection


def test_extract_sections_includes_explanatory_note_from_document_headings():
    report = FakeReport(
        sections={"Item 1": FakeSection("1", "Item 1. Business", "Business overview")},
        headings=["Explanatory Note", "Item 1. Business"],
        text="Explanatory Note\nThis amendment updates the proxy statement disclosure.\nItem 1. Business\nBusiness overview",
    )

    assert extract_sections(report) == [
        FilingSection(
            item="Explanatory Note",
            title="Explanatory Note",
            text="Explanatory Note\nThis amendment updates the proxy statement disclosure.",
        ),
        FilingSection(item="Item 1", title="Item 1. Business", text="Business overview"),
    ]


def test_extract_sections_keeps_named_sections_reported_by_edgartools():
    report = FakeReport(
        sections={
            "explanatory_note": FakeSection(
                None,
                "Explanatory Note",
                "This amendment updates the proxy statement disclosure.",
                kind="named",
            )
        }
    )

    assert extract_sections(report) == [
        FilingSection(
            item="Explanatory Note",
            title="Explanatory Note",
            text="This amendment updates the proxy statement disclosure.",
        )
    ]


def test_extract_sections_normalizes_named_signatures_key():
    report = FakeReport(
        sections={
            "part_iv_signatures": FakeSection(
                None,
                None,
                "Signed by the registrant.",
                kind="named",
            )
        }
    )

    assert extract_sections(report) == [
        FilingSection(
            item="Signatures",
            title="Signatures",
            text="Signed by the registrant.",
        )
    ]


def test_extract_named_sections_falls_back_to_document_text():
    report = FakeReport(
        sections={"Item 1": FakeSection("1", "Item 1. Business", "Business overview")},
        headings=[],
        text="Explanatory Note\nThis amendment updates the proxy statement disclosure.\nItem 1. Business",
    )

    assert extract_sections(report) == [
        FilingSection(
            item="Explanatory Note",
            title="Explanatory Note",
            text="Explanatory Note\nThis amendment updates the proxy statement disclosure.",
        ),
        FilingSection(item="Item 1", title="Item 1. Business", text="Business overview"),
    ]


def test_named_section_text_fallback_stops_at_part_three():
    report = FakeReport(
        sections={},
        headings=[],
        text="Explanatory Note\nAmendment purpose.\nPART III\nDirectors and governance.",
    )

    assert extract_sections(report) == [
        FilingSection(
            item="Explanatory Note",
            title="Explanatory Note",
            text="Explanatory Note\nAmendment purpose.",
        )
    ]


def test_named_section_heading_does_not_stop_at_heading_mentioned_in_body():
    report = FakeReport(
        sections={},
        headings=["Explanatory Note", "Item 10. Directors"],
        text=(
            "Explanatory Note\nThe original filing omitted Part III, Items\n"
            "10 through 14 because those disclosures would follow in the proxy statement.\n"
            "PART III\nITEM 10. Directors\nGovernance disclosures."
        ),
    )

    assert extract_sections(report) == [
        FilingSection(
            item="Explanatory Note",
            title="Explanatory Note",
            text=(
                "Explanatory Note\nThe original filing omitted Part III, Items\n"
                "10 through 14 because those disclosures would follow in the proxy statement."
            ),
        )
    ]


def test_named_front_matter_that_prefixes_item_text_is_dropped():
    front_matter = FilingSection(
        item="Explanatory Note",
        title="Explanatory Note",
        text="Item 1. Business\nBusiness overview",
    )
    item = FilingSection(item="Item 1", title="Item 1. Business", text="Item 1. Business\nBusiness overview")

    assert dedupe_sections([front_matter, item]) == [item]
