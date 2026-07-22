from app import service
from app.schemas import FilingSection
from app.service import (
    accession_number_candidates,
    extract_sections,
    list_filings,
    map_company_row,
    map_filing,
    normalize_cik,
    normalize_item,
)


class FakeCompany:
    cik = 320193
    name = "Apple Inc."

    def get_ticker(self):
        return "AAPL"


class FakeFiling:
    accession_number = "0000320193-24-000123"
    accession_no = "0000320193-24-000123"
    form = "10-K"
    filing_date = "2024-11-01"
    period_of_report = "2024-09-28"
    homepage_url = "https://www.sec.gov/Archives/edgar/data/320193/000032019324000123-index.html"


class FakeSection:
    def __init__(self, item, title, text, part=None):
        self.item = item
        self.title = title
        self.part = part
        self._text = text

    def text(self):
        return self._text


class FakeSections(dict):
    def get(self, key, default=None):
        return super().get(key, default)


class FakeDocument:
    sections = FakeSections(
        {
            "Item 1": FakeSection("1", "Item 1. Business", "Business overview"),
            "Item 1A": FakeSection("1A", "Item 1A. Risk Factors", "Risk text\n"),
            "Item 7": FakeSection("7", "Item 7. MD&A", "Management discussion"),
        }
    )


class FakeReport:
    form = "10-K"
    document = FakeDocument()


def test_map_company_row_normalizes_shape():
    result = map_company_row({"ticker": "AAPL", "cik": 320193, "company": "Apple Inc.", "score": 99})

    assert result.ticker == "AAPL"
    assert result.cik == "0000320193"
    assert result.name == "Apple Inc."


def test_map_filing_uses_camel_case_and_infers_fy():
    result = map_filing(FakeFiling())

    assert result.accessionNumber == "0000320193-24-000123"
    assert result.form == "10-K"
    assert result.filingDate == "2024-11-01"
    assert result.reportDate == "2024-09-28"
    assert result.fiscalPeriod == "FY"
    assert result.sourceUrl == "https://www.sec.gov/Archives/edgar/data/320193/000032019324000123-index.html"


def test_extract_sections_returns_structured_items():
    result = extract_sections(FakeReport())

    assert result[:3] == [
        FilingSection(item="Item 1", title="Item 1. Business", text="Business overview"),
        FilingSection(item="Item 1A", title="Item 1A. Risk Factors", text="Risk text"),
        FilingSection(item="Item 7", title="Item 7. MD&A", text="Management discussion"),
    ]


def test_normalize_cik_preserves_empty_and_pads_numeric_values():
    assert normalize_cik("") == ""
    assert normalize_cik(123) == "0000000123"


class FakeFilings:
    """Mimics edgartools: latest(1) returns a single filing, latest(n>1) a collection."""

    def __init__(self, filings):
        self._filings = filings

    def __bool__(self):
        return bool(self._filings)

    def latest(self, limit):
        latest = self._filings[:limit]
        return latest[0] if limit == 1 else latest


def test_list_filings_handles_single_filing_for_limit_one(monkeypatch):
    monkeypatch.setattr(
        service, "get_company", lambda ticker_or_cik: type("C", (), {"get_filings": lambda self, form: FakeFilings([FakeFiling()])})()
    )

    result = list_filings("AAPL", form="10-K", limit=1)

    assert len(result) == 1
    assert result[0].accessionNumber == "0000320193-24-000123"


def test_list_filings_handles_collection_for_larger_limit(monkeypatch):
    monkeypatch.setattr(
        service,
        "get_company",
        lambda ticker_or_cik: type("C", (), {"get_filings": lambda self, form: FakeFilings([FakeFiling(), FakeFiling()])})(),
    )

    result = list_filings("AAPL", form="10-K", limit=5)

    assert len(result) == 2


def test_list_filings_returns_empty_when_no_filings(monkeypatch):
    monkeypatch.setattr(
        service, "get_company", lambda ticker_or_cik: type("C", (), {"get_filings": lambda self, form: FakeFilings([])})()
    )

    assert list_filings("AAPL", form="10-K", limit=5) == []


def test_normalize_item_drops_part_prefix_for_ten_k():
    section = FakeSection("1A", "Item 1A. Risk Factors", "", part="I")

    assert normalize_item("Item 1A", section, include_part=False) == "Item 1A"


def test_normalize_item_keeps_part_prefix_for_ten_q():
    section = FakeSection("1", "Item 1", "", part="II")

    assert normalize_item("Part II Item 1", section, include_part=True) == "Part II Item 1"


def test_normalize_item_falls_back_to_part_qualified_key_when_ten_q_section_lacks_part():
    section = FakeSection("1", "Item 1", "", part=None)

    assert normalize_item("Part II Item 1", section, include_part=True) == "Part II Item 1"


def test_normalize_item_uses_fallback_when_section_has_no_item():
    section = FakeSection(None, "", "")

    assert normalize_item("Item 7", section) == "Item 7"


def test_accession_number_candidates_accepts_dashless_values():
    assert accession_number_candidates("000032019324000123") == [
        "000032019324000123",
        "0000320193-24-000123",
    ]
