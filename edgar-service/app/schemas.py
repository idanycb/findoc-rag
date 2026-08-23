from typing import Optional

from pydantic import BaseModel, ConfigDict, Field


class CompanyResult(BaseModel):
    ticker: Optional[str] = None
    cik: str
    name: str

    model_config = ConfigDict(
        json_schema_extra={
            "example": {"ticker": "AAPL", "cik": "0000320193", "name": "Apple Inc."}
        }
    )


class FilingResult(BaseModel):
    accessionNumber: str = Field(min_length=1, description="Accession of this filing.")
    form: str
    filingDate: Optional[str] = None
    reportDate: Optional[str] = None
    fiscalPeriod: Optional[str] = None
    sourceUrl: Optional[str] = None
    amendsAccessionNumber: Optional[str] = Field(
        default=None,
        description=(
            "Accession of the original 10-K or 10-Q this filing amends, matched by period "
            "of report. Points at the original, not the preceding amendment. Null for "
            "non-amendments and when no original can be matched."
        ),
    )

    model_config = ConfigDict(
        json_schema_extra={
            "example": {
                "accessionNumber": "0000320193-24-000123",
                "form": "10-K",
                "filingDate": "2024-11-01",
                "reportDate": "2024-09-28",
                "fiscalPeriod": "FY",
                "sourceUrl": "https://www.sec.gov/Archives/edgar/data/320193/000032019324000123-index.html",
                "amendsAccessionNumber": None,
            }
        }
    )


class FilingSection(BaseModel):
    item: str
    title: str
    text: str = Field(min_length=1)
    pageNumber: Optional[int] = None

    model_config = ConfigDict(
        json_schema_extra={
            "example": {
                "item": "Item 1A",
                "title": "Risk Factors",
                "text": "The Company's business, reputation, results of operations...",
                "pageNumber": None,
            }
        }
    )


class FilingSectionsResponse(BaseModel):
    company: CompanyResult
    filing: FilingResult
    sourceUrl: str
    sections: list[FilingSection]
    hasSearchableSections: bool = Field(
        description=(
            "True when at least one standard 10-K / 10-Q section was extracted. "
            "False for valid filings with no analyzable narrative, such as a "
            "certification-only 10-K/A."
        ),
    )

    model_config = ConfigDict(
        json_schema_extra={
            "example": {
                "company": {"ticker": "AAPL", "cik": "0000320193", "name": "Apple Inc."},
                "filing": {
                    "accessionNumber": "0000320193-24-000123",
                    "form": "10-K",
                    "filingDate": "2024-11-01",
                    "reportDate": "2024-09-28",
                    "fiscalPeriod": "FY",
                    "sourceUrl": "https://www.sec.gov/Archives/edgar/data/320193/000032019324000123-index.html",
                    "amendsAccessionNumber": None,
                },
                "sourceUrl": "https://www.sec.gov/Archives/edgar/data/320193/000032019324000123-index.html",
                "sections": [
                    {
                        "item": "Item 1A",
                        "title": "Risk Factors",
                        "text": "The Company's business, reputation, results of operations...",
                        "pageNumber": None,
                    }
                ],
                "hasSearchableSections": True,
            }
        }
    )
