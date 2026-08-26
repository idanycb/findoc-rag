import logging

from fastapi import FastAPI, HTTPException, Query

from .schemas import CompanyResult, FilingResult, FilingSectionsResponse
from .service import (
    EdgarResourceNotFoundError,
    configure_edgar,
    get_filing_sections,
    list_filings,
    search_companies,
)

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

configure_edgar()

app = FastAPI(
    title="FinDoc EDGAR Service",
    version="0.1.0",
    description=(
        "Internal SEC EDGAR sidecar backed by edgartools. Search filers, list their "
        "filings, and extract structured 10-K / 10-Q sections over plain HTTP.\n\n"
        "The interactive contract below is the source of truth — the README only "
        "summarises it."
    ),
)

# Reusable error-response docs so Swagger/ReDoc show the non-2xx contract.
UPSTREAM_ERROR = {502: {"description": "Upstream edgartools/SEC call failed; retry with backoff."}}
NOT_FOUND = {404: {"description": "Filer or filing not found."}}
UNSUPPORTED_FORM = {422: {"description": "Form type is not 10-K, 10-K/A, 10-Q, or 10-Q/A."}}
INVALID_FILING = {422: {"description": "Filing metadata could not be normalized."}}


@app.get("/health", summary="Liveness check", tags=["meta"])
def health() -> dict[str, str]:
    return {"status": "ok"}


@app.get(
    "/companies",
    response_model=list[CompanyResult],
    summary="Search filers",
    description="Full-text search over SEC filers. CIKs are zero-padded to 10 digits.",
    responses=UPSTREAM_ERROR,
    tags=["companies"],
)
def companies(
    q: str = Query(min_length=1, description="Search text, e.g. a company name or ticker."),
    limit: int = Query(default=10, ge=1, le=25, description="Max results to return."),
) -> list[CompanyResult]:
    try:
        return search_companies(q, limit=limit)
    except Exception as exc:  # noqa: BLE001 - convert library/network errors to an API contract.
        logger.exception("Company search failed")
        raise HTTPException(status_code=502, detail="EDGAR company search failed.") from exc


@app.get(
    "/companies/{ticker_or_cik}/filings",
    response_model=list[FilingResult],
    summary="List a filer's filings",
    description=(
        "Lists a filer's most recent filings of a given form, newest first. "
        "The path accepts a ticker (`AAPL`) or a CIK (`320193` or `0000320193`). "
        "For `10-K/A` and `10-Q/A`, `amendsAccessionNumber` is the original filing "
        "with the same period of report, when one can be matched."
    ),
    responses={**NOT_FOUND, **UNSUPPORTED_FORM, **INVALID_FILING, **UPSTREAM_ERROR},
    tags=["companies"],
)
def company_filings(
    ticker_or_cik: str,
    form: str = Query(
        default="10-K",
        min_length=1,
        description="10-K, 10-K/A, 10-Q, or 10-Q/A. Anything else returns 422.",
    ),
    limit: int = Query(default=20, ge=1, le=50, description="Max filings to return."),
) -> list[FilingResult]:
    try:
        return list_filings(ticker_or_cik, form=form, limit=limit)
    except EdgarResourceNotFoundError as exc:
        raise HTTPException(status_code=404, detail=str(exc)) from exc
    except ValueError as exc:
        raise HTTPException(status_code=422, detail=str(exc)) from exc
    except Exception as exc:  # noqa: BLE001 - convert library/network errors to an API contract.
        logger.exception("Filing list failed")
        raise HTTPException(status_code=502, detail="EDGAR filing lookup failed.") from exc


@app.get(
    "/filings/sections",
    response_model=FilingSectionsResponse,
    summary="Extract filing sections",
    description=(
        "Extracts structured sections (Item 1, Item 1A, MD&A, …) from a single filing. "
        "Supported for 10-K, 10-K/A, 10-Q, and 10-Q/A only. 10-K items are returned bare "
        "(`Item 1A`); 10-Q items keep the part qualifier (`Part II Item 1A`). "
        "A found filing with no extractable narrative (e.g. a certification-only 10-K/A) "
        "returns an empty `sections` list and `hasSearchableSections: false`, not 404."
    ),
    responses={**NOT_FOUND, **INVALID_FILING, **UPSTREAM_ERROR},
    tags=["filings"],
)
def filing_sections(
    ticker: str = Query(min_length=1, description="Ticker or CIK of the filer."),
    accession: str = Query(min_length=1, description="Accession number, dashed or dashless."),
) -> FilingSectionsResponse:
    try:
        return get_filing_sections(ticker, accession)
    except EdgarResourceNotFoundError as exc:
        raise HTTPException(status_code=404, detail=str(exc)) from exc
    except ValueError as exc:
        raise HTTPException(status_code=422, detail=str(exc)) from exc
    except Exception as exc:  # noqa: BLE001 - convert library/network errors to an API contract.
        logger.exception("Section extraction failed")
        raise HTTPException(status_code=502, detail="EDGAR section extraction failed.") from exc
