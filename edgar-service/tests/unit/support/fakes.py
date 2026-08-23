"""Test doubles for edgartools objects. Not collected by pytest."""

DEFAULT_ACCESSION = "0000320193-24-000123"
DEFAULT_SOURCE_URL = (
    "https://www.sec.gov/Archives/edgar/data/320193/000032019324000123-index.html"
)


class FakeFiling:
    def __init__(
        self,
        accession=DEFAULT_ACCESSION,
        form="10-K",
        filing_date="2024-11-01",
        period_of_report="2024-09-28",
        report_date=None,
        homepage_url=DEFAULT_SOURCE_URL,
        report=None,
    ):
        self.accession_number = accession
        self.accession_no = accession
        self.form = form
        self.filing_date = filing_date
        self.period_of_report = period_of_report
        self.report_date = period_of_report if report_date is None else report_date
        self.homepage_url = homepage_url
        self._report = report

    def obj(self):
        return self._report


class FakeFilings:
    """Mimics edgartools: latest(1) returns a single filing, latest(n>1) a collection."""

    def __init__(self, filings):
        self._filings = filings

    def __bool__(self):
        return bool(self._filings)

    def __iter__(self):
        return iter(self._filings)

    def latest(self, limit):
        latest = self._filings[:limit]
        return latest[0] if limit == 1 else latest

    def get(self, accession):
        for filing in self._filings:
            if accession in {filing.accession_number, filing.accession_no}:
                return filing
        return None


class FakeSection:
    def __init__(self, item, title, text, part=None):
        self.item = item
        self.title = title
        self.part = part
        self._text = text

    def text(self):
        return self._text


class FakeReport:
    def __init__(self, form="10-K", sections=None):
        if sections is None:
            sections = {
                "Item 1": FakeSection("1", "Item 1. Business", "Business overview"),
                "Item 1A": FakeSection("1A", "Item 1A. Risk Factors", "Risk text\n"),
                "Item 7": FakeSection("7", "Item 7. MD&A", "Management discussion"),
            }
        self.form = form
        self.document = type("Document", (), {"sections": sections})()


class FakeCompany:
    ticker = "AAPL"
    cik = "0000320193"
    name = "Apple Inc."

    def __init__(self, filings_by_form, calls=None):
        self._filings_by_form = filings_by_form
        self._calls = calls

    def get_filings(self, form, amendments=True, **kwargs):
        if self._calls is not None:
            self._calls.append((form, amendments))
        return FakeFilings(list(self._filings_by_form.get(form, [])))
