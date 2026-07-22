import logging
import os
from dataclasses import dataclass
from typing import Any, Optional

from .schemas import (
    CompanyMetadata,
    CompanyResult,
    FilingMetadata,
    FilingResult,
    FilingSection,
    FilingSectionsResponse,
)

logger = logging.getLogger(__name__)


SUPPORTED_SECTION_FORMS = {"10-K", "10-K/A", "10-Q", "10-Q/A"}

TEN_K_SECTION_TITLES = {
    "Item 1": "Business",
    "Item 1A": "Risk Factors",
    "Item 1B": "Unresolved Staff Comments",
    "Item 1C": "Cybersecurity",
    "Item 2": "Properties",
    "Item 3": "Legal Proceedings",
    "Item 4": "Mine Safety Disclosures",
    "Item 5": "Market for Registrant's Common Equity",
    "Item 6": "Selected Financial Data",
    "Item 7": "Management's Discussion and Analysis (MD&A)",
    "Item 7A": "Quantitative and Qualitative Disclosures About Market Risk",
    "Item 8": "Financial Statements",
    "Item 9": "Controls and Procedures",
    "Item 9A": "Controls and Procedures",
    "Item 9B": "Other Information",
    "Item 9C": "Disclosure Regarding Foreign Jurisdictions That Prevent Inspections",
    "Item 10": "Directors, Executive Officers, and Corporate Governance",
    "Item 11": "Executive Compensation",
    "Item 12": "Security Ownership of Certain Beneficial Owners and Management",
    "Item 13": "Certain Relationships and Related Transactions, and Director Independence",
    "Item 14": "Principal Accounting Fees and Services",
    "Item 15": "Exhibits, Financial Statement Schedules",
    "Item 16": "Form 10-K Summary",
}

TEN_Q_SECTION_TITLES = {
    "Part I Item 1": "Financial Statements",
    "Part I Item 2": "Management's Discussion and Analysis (MD&A)",
    "Part I Item 3": "Quantitative and Qualitative Disclosures About Market Risk",
    "Part I Item 4": "Controls and Procedures",
    "Part II Item 1": "Legal Proceedings",
    "Part II Item 1A": "Risk Factors",
    "Part II Item 2": "Unregistered Sales of Equity Securities and Use of Proceeds",
    "Part II Item 3": "Defaults Upon Senior Securities",
    "Part II Item 4": "Mine Safety Disclosures",
    "Part II Item 5": "Other Information",
    "Part II Item 6": "Exhibits",
}


@dataclass(frozen=True)
class EdgarSettings:
    identity: Optional[str]
    local_data_dir: str
    cache_dir: str

    @classmethod
    def from_env(cls) -> "EdgarSettings":
        return cls(
            identity=os.getenv("EDGAR_IDENTITY") or os.getenv("SEC_USER_AGENT"),
            local_data_dir=os.getenv("EDGAR_LOCAL_DATA_DIR", "/tmp/edgar/data"),
            cache_dir=os.getenv("EDGAR_CACHE_DIR", "/tmp/edgar/cache"),
        )


def configure_edgar(settings: Optional[EdgarSettings] = None) -> None:
    settings = settings or EdgarSettings.from_env()
    os.environ.setdefault("EDGAR_LOCAL_DATA_DIR", settings.local_data_dir)
    os.environ.setdefault("EDGAR_CACHE_DIR", settings.cache_dir)

    if settings.identity:
        from edgar import set_identity

        set_identity(settings.identity)
    else:
        logger.warning(
            "EDGAR_IDENTITY is not set. SEC requests may be rejected; set it to an app/contact user agent."
        )


def search_companies(query: str, limit: int = 10) -> list[CompanyResult]:
    from edgar.entity.search import find_company

    search_results = find_company(query, top_n=limit)
    frame = getattr(search_results, "results", None)
    if frame is None or getattr(frame, "empty", True):
        return []

    rows = frame.head(limit).to_dict(orient="records")
    return [map_company_row(row) for row in rows]


def list_filings(ticker_or_cik: str, form: str = "10-K", limit: int = 20) -> list[FilingResult]:
    company = get_company(ticker_or_cik)
    filings = company.get_filings(form=form)
    if not filings:
        return []

    latest = filings.latest(limit)
    # edgartools returns a single filing when limit == 1 and a collection otherwise.
    items = list(latest) if hasattr(latest, "__iter__") else [latest]
    return [map_filing(filing) for filing in items]


def get_filing_sections(ticker: str, accession: str) -> FilingSectionsResponse:
    company = get_company(ticker)
    filing = resolve_filing(company, accession)
    if filing is None:
        raise LookupError(f"Filing accession '{accession}' was not found for '{ticker}'.")

    form = str(getattr(filing, "form", "") or "")
    if form not in SUPPORTED_SECTION_FORMS:
        raise ValueError(f"Section extraction is supported for {sorted(SUPPORTED_SECTION_FORMS)}, not {form}.")

    report = filing.obj()
    sections = extract_sections(report)
    if not sections:
        raise LookupError(f"No structured sections were extracted for accession '{accession}'.")

    return FilingSectionsResponse(
        company=map_company(company),
        filing=map_filing(filing),
        sourceUrl=str(getattr(filing, "homepage_url", None) or getattr(filing, "url", "")),
        sections=sections,
    )


def get_company(ticker_or_cik: str) -> Any:
    from edgar import Company

    company = Company(ticker_or_cik)
    if getattr(company, "not_found", False):
        raise LookupError(f"Company '{ticker_or_cik}' was not found.")
    return company


def resolve_filing(company: Any, accession: str) -> Optional[Any]:
    accession_candidates = accession_number_candidates(accession)
    forms = ["10-K", "10-K/A", "10-Q", "10-Q/A"]
    for form in forms:
        filings = company.get_filings(form=form)
        if not filings:
            continue
        for candidate in accession_candidates:
            filing = filings.get(candidate)
            if filing:
                return filing
    return None


def extract_sections(report: Any) -> list[FilingSection]:
    form = str(getattr(report, "form", "") or "")
    is_ten_q = form.startswith("10-Q")
    candidates = list((TEN_Q_SECTION_TITLES if is_ten_q else TEN_K_SECTION_TITLES).items())

    sections = []
    seen: set[str] = set()
    for item, fallback_title in candidates:
        section_obj = get_report_section(report, item)
        text = section_text(section_obj)
        if not text:
            continue

        normalized_item = normalize_item(item, section_obj, include_part=is_ten_q)
        if normalized_item in seen:
            continue
        seen.add(normalized_item)

        sections.append(
            FilingSection(
                item=normalized_item,
                title=section_title(section_obj, fallback_title),
                text=text,
                pageNumber=section_page_number(section_obj),
            )
        )

    return sections


def get_report_section(report: Any, item: str) -> Any:
    document = getattr(report, "document", None)
    sections = getattr(document, "sections", None) if document is not None else None
    if sections is not None:
        section = sections.get(item)
        if section is not None:
            return section

    try:
        return report[item]
    except (KeyError, TypeError, AttributeError):
        return None


def section_text(section_or_text: Any) -> str:
    if section_or_text is None:
        return ""
    if isinstance(section_or_text, str):
        return clean_text(section_or_text)
    text_fn = getattr(section_or_text, "text", None)
    if callable(text_fn):
        return clean_text(text_fn())
    return ""


def section_title(section: Any, fallback: str) -> str:
    title = getattr(section, "title", None)
    return str(title).strip() if title else fallback


def normalize_item(fallback_item: str, section: Any, include_part: bool = False) -> str:
    item = getattr(section, "item", None)
    if not item:
        return fallback_item
    item = str(item).upper()
    # 10-Q items restart per part, so the "Part II Item 1" prefix is meaningful.
    # 10-K items are globally numbered and cited without a part (e.g. "Item 1A").
    if include_part:
        part = getattr(section, "part", None)
        if part:
            return f"Part {str(part).upper()} Item {item}"
        return fallback_item
    return f"Item {item}"


def section_page_number(section: Any) -> Optional[int]:
    node = getattr(section, "node", None)
    metadata = getattr(node, "metadata", None)
    if isinstance(metadata, dict):
        page = metadata.get("page") or metadata.get("page_no") or metadata.get("pageNumber")
        if isinstance(page, int):
            return page
    return None


def map_company(company: Any) -> CompanyMetadata:
    return CompanyMetadata(
        ticker=first_text(getattr(company, "ticker", None), call(getattr(company, "get_ticker", None))),
        cik=normalize_cik(getattr(company, "cik", "")),
        name=str(getattr(company, "name", "") or getattr(company, "display_name", "")),
    )


def map_company_row(row: dict[str, Any]) -> CompanyResult:
    return CompanyResult(
        ticker=optional_text(row.get("ticker")),
        cik=normalize_cik(row.get("cik")),
        name=str(row.get("company") or row.get("name") or ""),
    )


def map_filing(filing: Any) -> FilingMetadata:
    form = str(getattr(filing, "form", "") or "")
    return FilingMetadata(
        accessionNumber=str(
            getattr(filing, "accession_number", None) or getattr(filing, "accession_no", "") or ""
        ),
        form=form,
        filingDate=optional_text(getattr(filing, "filing_date", None)),
        reportDate=optional_text(call_or_attr(filing, "period_of_report")),
        fiscalPeriod=infer_fiscal_period(form),
        sourceUrl=optional_text(getattr(filing, "homepage_url", None) or getattr(filing, "url", None)),
    )


def infer_fiscal_period(form: str) -> Optional[str]:
    normalized = form.upper()
    if normalized.startswith("10-K"):
        return "FY"
    return None


def accession_number_candidates(accession: str) -> list[str]:
    normalized = accession.strip()
    stripped = normalized.replace("-", "")
    candidates = [normalized]
    if len(stripped) == 18 and stripped.isdigit():
        dashed = f"{stripped[:10]}-{stripped[10:12]}-{stripped[12:]}"
        candidates.append(dashed)
        candidates.append(stripped)
    return list(dict.fromkeys(candidates))


def clean_text(value: Any) -> str:
    return "\n".join(line.rstrip() for line in str(value or "").strip().splitlines()).strip()


def normalize_cik(value: Any) -> str:
    text = str(value or "").strip()
    if not text:
        return ""
    return text.zfill(10) if text.isdigit() else text


def optional_text(value: Any) -> Optional[str]:
    if value is None:
        return None
    text = str(value).strip()
    return text or None


def first_text(*values: Any) -> Optional[str]:
    for value in values:
        text = optional_text(value)
        if text:
            return text
    return None


def call(value: Any) -> Any:
    if callable(value):
        try:
            return value()
        except Exception:  # noqa: BLE001 - mapping should survive optional metadata failures.
            return None
    return None


def call_or_attr(obj: Any, name: str) -> Any:
    value = getattr(obj, name, None)
    if callable(value):
        return call(value)
    return value


def limit_range(value: int, minimum: int, maximum: int) -> int:
    return max(minimum, min(value, maximum))
