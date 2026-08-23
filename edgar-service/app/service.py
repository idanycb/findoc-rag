import logging
import os
from typing import Any, Optional

from .schemas import (
    CompanyResult,
    FilingResult,
    FilingSection,
    FilingSectionsResponse,
)

logger = logging.getLogger(__name__)


SUPPORTED_FORMS = {"10-K", "10-K/A", "10-Q", "10-Q/A"}

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


def configure_edgar() -> None:
    os.environ.setdefault("EDGAR_LOCAL_DATA_DIR", "/tmp/edgar/data")
    os.environ.setdefault("EDGAR_CACHE_DIR", "/tmp/edgar/cache")
    identity = os.getenv("EDGAR_IDENTITY") or os.getenv("SEC_USER_AGENT")
    if identity:
        from edgar import set_identity

        set_identity(identity)
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
    if form not in SUPPORTED_FORMS:
        raise ValueError(f"Filing lookup is supported for {sorted(SUPPORTED_FORMS)}, not {form}.")
    company = get_company(ticker_or_cik)
    # edgartools expands "10-K" to include "10-K/A" unless amendments=False, but the same
    # flag strips "/A" from "10-K/A". Request exact forms: originals without expansion,
    # amendments with it (which also matches the non-existent "10-K/A/A").
    filings = company.get_filings(form=form, amendments=is_amendment_form(form))
    if not filings:
        return []

    latest = filings.latest(limit)
    # edgartools returns a single filing when limit == 1 and a collection otherwise.
    items = collection_as_list(latest)
    originals = load_original_filings(company, form)
    return [
        map_filing(filing, amends_accession=resolve_amended_accession(filing, originals))
        for filing in items
    ]


def get_filing_sections(ticker: str, accession: str) -> FilingSectionsResponse:
    company = get_company(ticker)
    filing = resolve_filing(company, accession)
    if filing is None:
        raise LookupError(f"Filing accession '{accession}' was not found for '{ticker}'.")

    report = filing.obj()
    sections = extract_sections(report)
    originals = load_original_filings(company, str(getattr(filing, "form", "") or ""))
    return FilingSectionsResponse(
        company=map_company(company),
        filing=map_filing(filing, amends_accession=resolve_amended_accession(filing, originals)),
        sourceUrl=str(getattr(filing, "homepage_url", None) or getattr(filing, "url", "")),
        sections=sections,
        hasSearchableSections=bool(sections),
    )


def get_company(ticker_or_cik: str) -> Any:
    from edgar import Company

    company = Company(ticker_or_cik)
    if getattr(company, "not_found", False):
        raise LookupError(f"Company '{ticker_or_cik}' was not found.")
    return company


def resolve_filing(company: Any, accession: str) -> Optional[Any]:
    accession_candidates = accession_number_candidates(accession)
    for form in sorted(SUPPORTED_FORMS):
        filings = company.get_filings(form=form, amendments=is_amendment_form(form))
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


def map_company(company: Any) -> CompanyResult:
    return CompanyResult(
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


def map_filing(filing: Any, amends_accession: Optional[str] = None) -> FilingResult:
    form = str(getattr(filing, "form", "") or "")
    report_date = filing_report_date(filing)
    accession = optional_text(filing_accession_number(filing))
    if not accession:
        raise ValueError("Filing is missing a usable accession number.")
    return FilingResult(
        accessionNumber=accession,
        form=form,
        filingDate=optional_text(getattr(filing, "filing_date", None)),
        reportDate=report_date,
        fiscalPeriod=infer_fiscal_period(form),
        sourceUrl=optional_text(getattr(filing, "homepage_url", None) or getattr(filing, "url", None)),
        amendsAccessionNumber=optional_text(amends_accession) if is_amendment_form(form) else None,
    )


def is_amendment_form(form: str) -> bool:
    return form.strip().upper().endswith("/A")


def original_form(form: str) -> str:
    stripped = form.strip()
    return stripped[:-2] if is_amendment_form(stripped) else stripped


def load_original_filings(company: Any, form: str) -> list[Any]:
    if not is_amendment_form(form):
        return []
    filings = company.get_filings(form=original_form(form), amendments=False)
    return collection_as_list(filings)


def resolve_amended_accession(filing: Any, originals: list[Any]) -> Optional[str]:
    """Match an amendment to the original filing that shares its period of report.

    SEC submissions do not name the accession being amended. The original 10-K / 10-Q
    for the same report date, filed on or before the amendment, is the reliable proxy.
    When several originals share that period, the latest eligible one is used.
    """
    form = str(getattr(filing, "form", "") or "")
    if not is_amendment_form(form):
        return None

    report_date = filing_report_date(filing)
    if not report_date:
        return None

    amendment_filed = optional_text(getattr(filing, "filing_date", None))
    chosen_date = ""
    chosen_accession: Optional[str] = None
    for original in originals:
        if filing_report_date(original) != report_date:
            continue
        accession = filing_accession_number(original)
        if not accession:
            continue
        original_filed = optional_text(getattr(original, "filing_date", None))
        if amendment_filed and original_filed and original_filed > amendment_filed:
            continue
        if chosen_accession is None or (original_filed or "") >= chosen_date:
            chosen_date = original_filed or ""
            chosen_accession = accession
    return chosen_accession


def filing_accession_number(filing: Any) -> str:
    return str(getattr(filing, "accession_number", None) or getattr(filing, "accession_no", "") or "")


def filing_report_date(filing: Any) -> Optional[str]:
    # EntityFiling.report_date comes from the submissions JSON (no extra I/O).
    # Base Filing.period_of_report may download SGML, so only use it as a fallback.
    raw = getattr(filing, "report_date", None)
    if raw in (None, ""):
        raw = call_or_attr(filing, "period_of_report")
    return normalize_report_date(raw)


def normalize_report_date(value: Any) -> Optional[str]:
    text = optional_text(value)
    if not text:
        return None
    digits = text.replace("-", "")
    if len(digits) == 8 and digits.isdigit():
        return f"{digits[:4]}-{digits[4:6]}-{digits[6:]}"
    return text


def collection_as_list(filings: Any) -> list[Any]:
    if not filings:
        return []
    if isinstance(filings, (str, bytes)):
        return [filings]
    if hasattr(filings, "__iter__"):
        return list(filings)
    return [filings]


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
