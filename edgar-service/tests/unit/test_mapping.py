from app.service import map_company_row, map_filing

from .support import DEFAULT_ACCESSION, DEFAULT_SOURCE_URL, ORIGINAL_10K, FakeFiling, amendment_10k, original_10k


def test_map_company_row_normalizes_shape():
    result = map_company_row({"ticker": "AAPL", "cik": 320193, "company": "Apple Inc.", "score": 99})

    assert result.ticker == "AAPL"
    assert result.cik == "0000320193"
    assert result.name == "Apple Inc."


def test_map_filing_uses_camel_case_and_infers_fy():
    result = map_filing(FakeFiling())

    assert result.accessionNumber == DEFAULT_ACCESSION
    assert result.form == "10-K"
    assert result.filingDate == "2024-11-01"
    assert result.reportDate == "2024-09-28"
    assert result.fiscalPeriod == "FY"
    assert result.sourceUrl == DEFAULT_SOURCE_URL
    assert result.amendsAccessionNumber is None


def test_map_filing_attaches_amends_accession_only_on_amendments():
    matched = map_filing(amendment_10k(), amends_accession=ORIGINAL_10K)
    ignored = map_filing(original_10k(), amends_accession=ORIGINAL_10K)
    blank = map_filing(amendment_10k(), amends_accession="  ")

    assert matched.amendsAccessionNumber == ORIGINAL_10K
    assert ignored.amendsAccessionNumber is None
    assert blank.amendsAccessionNumber is None
