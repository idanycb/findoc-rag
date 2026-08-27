'use client';

import { useAuth } from '@/context/AuthContext';
import {
  EDGAR_SUPPORTED_FORMS,
  fetchEdgarFilings,
  importEdgarFiling,
  searchEdgarCompanies,
  type EdgarSupportedForm,
} from '@/shared/lib/edgar';
import { formatDate } from '@/shared/lib/auth';
import { ApiError } from '@/shared/lib/api';
import type {
  EdgarCompany,
  EdgarFiling,
  EdgarImportResult,
} from '@/shared/types';
import { useRequireRole } from '@/shared/hooks/useRequireRole';
import {
  Check,
  ChevronDown,
  ExternalLink,
  FileSearch,
  Landmark,
  ListFilter,
  Loader2,
  Search,
} from 'lucide-react';
import Link from 'next/link';
import { useEffect, useMemo, useRef, useState } from 'react';

const EDGAR_ROLES = ['ADMIN', 'MEMBER'] as const;
const ALL_FORM_FILTERS: EdgarSupportedForm[] = [...EDGAR_SUPPORTED_FORMS];

function companyName(company: EdgarCompany): string {
  return (
    company.companyName || company.name || company.ticker || 'Unknown company'
  );
}

function companyId(company: EdgarCompany): string {
  return String(
    company.cik || company.ticker || company.companyName || company.name || ''
  );
}

function companyTicker(company: EdgarCompany): string {
  return String(company.ticker || '').trim();
}

function filingAccession(filing: EdgarFiling): string {
  return String(filing.accessionNumber || filing.accession || '').trim();
}

function filingForm(filing: EdgarFiling): string {
  return filing.formType || filing.form || 'Filing';
}

function formatOptionalDate(value?: string | null): string {
  return value ? formatDate(value) : 'Not reported';
}

function importedDocumentId(result: EdgarImportResult | null): string {
  return String(result?.documentId || result?.id || '').trim();
}

export function EdgarBrowseClient() {
  const { token } = useAuth();
  const { isCheckingAccess } = useRequireRole([...EDGAR_ROLES]);
  const [query, setQuery] = useState('');
  const [companies, setCompanies] = useState<EdgarCompany[]>([]);
  const [selectedCompany, setSelectedCompany] = useState<EdgarCompany | null>(
    null
  );
  const [selectedForms, setSelectedForms] =
    useState<EdgarSupportedForm[]>(ALL_FORM_FILTERS);
  const [filterOpen, setFilterOpen] = useState(false);
  const [filings, setFilings] = useState<EdgarFiling[]>([]);
  const [searching, setSearching] = useState(false);
  const [loadingFilings, setLoadingFilings] = useState(false);
  const [importingAccession, setImportingAccession] = useState('');
  const [importResult, setImportResult] = useState<EdgarImportResult | null>(
    null
  );
  const [error, setError] = useState('');
  const filterRef = useRef<HTMLDivElement>(null);
  const allCheckboxRef = useRef<HTMLInputElement>(null);
  const loadGeneration = useRef(0);

  const selectedCompanyId = useMemo(
    () => (selectedCompany ? companyId(selectedCompany) : ''),
    [selectedCompany]
  );

  const selectedFormSet = useMemo(
    () => new Set<string>(selectedForms),
    [selectedForms]
  );
  const allFormsSelected = selectedForms.length === ALL_FORM_FILTERS.length;
  const filterIsActive = !allFormsSelected;
  const orderedSelectedForms = useMemo(
    () => ALL_FORM_FILTERS.filter((form) => selectedFormSet.has(form)),
    [selectedFormSet]
  );

  const visibleFilings = useMemo(() => {
    if (allFormsSelected) return filings;
    return filings.filter((filing) => selectedFormSet.has(filingForm(filing)));
  }, [allFormsSelected, filings, selectedFormSet]);

  useEffect(() => {
    if (!filterOpen) return;

    const onPointerDown = (event: PointerEvent) => {
      if (!filterRef.current?.contains(event.target as Node)) {
        setFilterOpen(false);
      }
    };
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setFilterOpen(false);
    };

    document.addEventListener('pointerdown', onPointerDown);
    document.addEventListener('keydown', onKeyDown);
    return () => {
      document.removeEventListener('pointerdown', onPointerDown);
      document.removeEventListener('keydown', onKeyDown);
    };
  }, [filterOpen]);

  useEffect(() => {
    if (allCheckboxRef.current) {
      allCheckboxRef.current.indeterminate = filterIsActive;
    }
  }, [filterIsActive]);

  const handleSearch = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!query.trim() || searching) return;
    setError('');
    setImportResult(null);
    setSearching(true);
    try {
      const results = await searchEdgarCompanies(query, token);
      setCompanies(results ?? []);
      setSelectedCompany(null);
      setFilings([]);
      setSelectedForms([...ALL_FORM_FILTERS]);
      setFilterOpen(false);
    } catch (err) {
      setError(
        err instanceof ApiError ? err.message : 'Company search failed.'
      );
    } finally {
      setSearching(false);
    }
  };

  const loadFilings = async (company: EdgarCompany) => {
    const id = companyId(company);
    if (!id) return;
    const generation = ++loadGeneration.current;
    setError('');
    setImportResult(null);
    setSelectedCompany(company);
    setSelectedForms([...ALL_FORM_FILTERS]);
    setFilterOpen(false);
    setLoadingFilings(true);
    try {
      const results = await fetchEdgarFilings(id, token);
      if (generation !== loadGeneration.current) return;
      setFilings(results ?? []);
    } catch (err) {
      if (generation !== loadGeneration.current) return;
      setError(
        err instanceof ApiError ? err.message : 'Could not load filings.'
      );
      setFilings([]);
    } finally {
      if (generation === loadGeneration.current) {
        setLoadingFilings(false);
      }
    }
  };

  const toggleFormFilter = (form: EdgarSupportedForm) => {
    setSelectedForms((current) => {
      if (current.includes(form)) {
        return current.length === 1
          ? current
          : current.filter((value) => value !== form);
      }
      return [...current, form];
    });
  };

  const selectAllFormFilters = () => {
    if (!allFormsSelected) {
      setSelectedForms([...ALL_FORM_FILTERS]);
    }
  };

  const handleImport = async (filing: EdgarFiling) => {
    if (!selectedCompany) return;
    const accession = filingAccession(filing);
    const ticker = companyTicker(selectedCompany);
    const formType = filingForm(filing);
    if (
      !ticker ||
      !accession ||
      !EDGAR_SUPPORTED_FORMS.some((form) => form === formType)
    ) {
      setError(
        'This filing is missing the ticker, accession number, or supported form required for import.'
      );
      return;
    }

    setError('');
    setImportResult(null);
    setImportingAccession(accession);
    try {
      const result = await importEdgarFiling(
        {
          ticker,
          accessionNumber: accession,
          amendsAccessionNumber: filing.amendsAccessionNumber,
          cik: selectedCompany.cik == null ? null : String(selectedCompany.cik),
          companyName: companyName(selectedCompany),
          formType,
          fiscalPeriod: filing.fiscalPeriod,
          reportDate: filing.reportDate,
          filingDate: filing.filingDate,
          sourceUrl: filing.sourceUrl,
        },
        token
      );
      setImportResult(result ?? { message: 'Import queued.' });
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Import failed.');
    } finally {
      setImportingAccession('');
    }
  };

  const importedId = importedDocumentId(importResult);
  const activeFilterLabel = filterButtonBadge(
    orderedSelectedForms,
    allFormsSelected
  );

  if (isCheckingAccess) {
    return (
      <div className="flex-1 flex items-center justify-center text-[#888888]">
        Loading…
      </div>
    );
  }

  return (
    <>
      <div className="flex items-center justify-between bg-white px-5 py-4 border-b border-[#EBEBEB] md:px-7 md:py-5">
        <div>
          <h1 className="text-[22px] font-bold tracking-[-.01em] text-[#111111]">
            SEC EDGAR
          </h1>
          <p className="mt-0.5 text-[11px] uppercase tracking-[.12em] text-[#AAAAAA]">
            Import 10-K, 10-Q, and amendments
          </p>
        </div>
        <Link
          href="/vault"
          className="hidden rounded-lg border border-[#E5E5E5] bg-white px-4 py-2 text-[13px] font-semibold text-[#333333] hover:bg-[#F5F5F5] sm:inline-flex"
        >
          Open Vault
        </Link>
      </div>

      <div className="flex-1 overflow-auto px-5 py-4 md:px-7 md:py-7">
        <div className="grid grid-cols-1 gap-5 xl:grid-cols-[360px_1fr]">
          <section className="rounded-[14px] bg-white p-5 shadow-[0_1px_4px_rgba(0,0,0,.06)]">
            <div className="flex items-start gap-3">
              <div className="flex h-10 w-10 items-center justify-center rounded-[11px] bg-[#F5F5F5]">
                <Landmark size={20} className="text-[#111111]" />
              </div>
              <div>
                <h2 className="text-[15px] font-bold text-[#111111]">
                  Find a public company
                </h2>
                <p className="mt-1 text-[13.5px] leading-[1.55] text-[#888888]">
                  Search by ticker or company name, then import a 10-K, 10-Q, or
                  amendment for grounded chat.
                </p>
              </div>
            </div>

            <form onSubmit={handleSearch} className="mt-5 flex gap-2">
              <div className="flex h-10 flex-1 items-center gap-2 rounded-lg border border-[#E8E8E8] bg-[#F5F5F5] px-3.25">
                <Search size={15} className="text-[#AAAAAA]" />
                <input
                  value={query}
                  onChange={(e) => setQuery(e.target.value)}
                  placeholder="AAPL or Apple"
                  className="min-w-0 flex-1 bg-transparent text-sm text-[#111111] placeholder:text-[#BBBBBB] outline-hidden"
                />
              </div>
              <button
                type="submit"
                disabled={searching || !query.trim()}
                className="rounded-lg bg-[#111111] px-4 text-sm font-semibold text-white disabled:opacity-50"
              >
                {searching ? 'Searching…' : 'Search'}
              </button>
            </form>

            <div className="mt-5 flex flex-col gap-2">
              {companies.map((company) => {
                const active = selectedCompanyId === companyId(company);
                return (
                  <button
                    key={`${companyId(company)}-${companyTicker(company)}`}
                    type="button"
                    onClick={() => void loadFilings(company)}
                    className={`rounded-[11px] border px-4 py-3 text-left transition-colors ${
                      active
                        ? 'border-[#111111] bg-[#F5F5F5]'
                        : 'border-[#EEEEEE] bg-white hover:border-[#BBBBBB]'
                    }`}
                  >
                    <div className="flex items-center justify-between gap-3">
                      <div className="min-w-0">
                        <div className="truncate text-sm font-bold text-[#111111]">
                          {companyName(company)}
                        </div>
                        <div className="mt-0.5 text-xs text-[#888888]">
                          {[
                            company.ticker,
                            company.cik ? `CIK ${company.cik}` : null,
                          ]
                            .filter(Boolean)
                            .join(' · ') || 'SEC company record'}
                        </div>
                      </div>
                      {active && <Check size={16} className="text-[#111111]" />}
                    </div>
                  </button>
                );
              })}
              {!searching && query && companies.length === 0 && (
                <div className="rounded-[11px] border border-dashed border-[#E5E5E5] px-4 py-5 text-center text-sm text-[#888888]">
                  No companies found yet.
                </div>
              )}
            </div>
          </section>

          <section className="rounded-[14px] bg-white p-5 shadow-[0_1px_4px_rgba(0,0,0,.06)]">
            <div className="flex flex-col gap-3 border-b border-[#F5F5F5] pb-4 sm:flex-row sm:items-center sm:justify-between">
              <div>
                <h2 className="text-[15px] font-bold text-[#111111]">
                  Filings
                </h2>
                <p className="mt-1 text-[13px] text-[#888888]">
                  {selectedCompany
                    ? `Showing ${filingsSummary(orderedSelectedForms)} for ${companyName(selectedCompany)}`
                    : 'Select a company to load 10-K, 10-Q, and amendment filings.'}
                </p>
              </div>
              <div className="relative" ref={filterRef}>
                <button
                  type="button"
                  aria-expanded={filterOpen}
                  aria-haspopup="true"
                  onClick={() => setFilterOpen((open) => !open)}
                  className={`inline-flex h-9 items-center gap-2 rounded-lg border px-3 text-[13px] font-semibold ${
                    filterIsActive
                      ? 'border-[#111111] bg-[#111111] text-white'
                      : 'border-[#E5E5E5] bg-white text-[#333333] hover:border-[#BBBBBB]'
                  }`}
                >
                  <ListFilter size={14} />
                  Filter
                  {activeFilterLabel && (
                    <span className="rounded-full bg-white/15 px-2 py-0.5 text-[11px]">
                      {activeFilterLabel}
                    </span>
                  )}
                  <ChevronDown
                    size={14}
                    className={filterOpen ? 'rotate-180' : ''}
                  />
                </button>
                {filterOpen && (
                  <div
                    role="group"
                    aria-label="Filing types"
                    className="absolute right-0 z-10 mt-2 w-48 overflow-hidden rounded-xl border border-[#EEEEEE] bg-white py-1 shadow-[0_8px_24px_rgba(0,0,0,.08)]"
                  >
                    <label className="flex cursor-pointer items-center gap-2.5 px-3 py-2 text-[13px] text-[#111111] hover:bg-[#FAFAFA]">
                      <input
                        ref={allCheckboxRef}
                        type="checkbox"
                        checked={allFormsSelected}
                        onChange={selectAllFormFilters}
                        className="h-3.5 w-3.5 accent-[#111111]"
                      />
                      All filings
                    </label>
                    <div className="my-1 border-t border-[#F0F0F0]" />
                    {ALL_FORM_FILTERS.map((form) => (
                      <label
                        key={form}
                        className="flex cursor-pointer items-center gap-2.5 px-3 py-2 text-[13px] text-[#555555] hover:bg-[#FAFAFA]"
                      >
                        <input
                          type="checkbox"
                          checked={selectedFormSet.has(form)}
                          disabled={
                            selectedFormSet.has(form) &&
                            selectedForms.length === 1
                          }
                          onChange={() => toggleFormFilter(form)}
                          className="h-3.5 w-3.5 accent-[#111111]"
                        />
                        {form}
                      </label>
                    ))}
                  </div>
                )}
              </div>
            </div>

            {error && (
              <div className="mt-4 rounded-[9px] border border-[#FECACA] bg-[#FEF2F2] px-4 py-3 text-[13px] text-[#B91C1C]">
                {error}
              </div>
            )}

            {importResult && (
              <div className="mt-4 rounded-[10px] border border-[#BBF7D0] bg-[#F0FDF4] px-4 py-3 text-[13px] text-[#166534]">
                <div className="font-semibold">
                  {importResult.message || 'Import queued for analysis.'}
                </div>
                <div className="mt-2 flex flex-wrap gap-3">
                  {importedId && (
                    <Link
                      href={`/vault/documents/${importedId}`}
                      className="font-semibold underline"
                    >
                      View document status
                    </Link>
                  )}
                  <Link href="/vault" className="font-semibold underline">
                    Open vault
                  </Link>
                  <Link href="/chat" className="font-semibold underline">
                    Open chat
                  </Link>
                </div>
              </div>
            )}

            <div className="mt-4 flex flex-col gap-3">
              {loadingFilings ? (
                <div className="flex items-center justify-center gap-2 py-10 text-sm text-[#888888]">
                  <Loader2 size={16} className="animate-spin" />
                  Loading filings…
                </div>
              ) : visibleFilings.length === 0 ? (
                <div className="flex flex-col items-center justify-center rounded-xl border border-dashed border-[#E5E5E5] py-12 text-center">
                  <FileSearch size={24} className="text-[#BBBBBB]" />
                  <p className="mt-3 text-sm font-semibold text-[#666666]">
                    {emptyStateTitle(
                      selectedCompany,
                      orderedSelectedForms,
                      filings.length
                    )}
                  </p>
                  <p className="mt-1 max-w-90 text-[13px] leading-normal text-[#AAAAAA]">
                    {emptyStateBody(selectedCompany, orderedSelectedForms)}
                  </p>
                </div>
              ) : (
                visibleFilings.map((filing) => {
                  const accession = filingAccession(filing);
                  const isImporting = importingAccession === accession;
                  return (
                    <article
                      key={`${accession}-${filing.filingDate || filing.reportDate || filingForm(filing)}`}
                      className="rounded-[13px] border border-[#EEEEEE] p-4"
                    >
                      <div className="flex flex-col gap-3 md:flex-row md:items-start md:justify-between">
                        <div>
                          <div className="flex flex-wrap items-center gap-2">
                            <span className="rounded-full bg-[#111111] px-2.5 py-1 text-[11px] font-bold text-white">
                              {filingForm(filing)}
                            </span>
                            {filing.fiscalPeriod && (
                              <span className="rounded-full bg-[#F5F5F5] px-2.5 py-1 text-[11px] font-semibold text-[#666666]">
                                {filing.fiscalPeriod}
                              </span>
                            )}
                          </div>
                          <div className="mt-3 grid grid-cols-1 gap-2 text-[13px] text-[#666666] sm:grid-cols-2">
                            <div>
                              <span className="text-[#AAAAAA]">Filed:</span>{' '}
                              {formatOptionalDate(filing.filingDate)}
                            </div>
                            <div>
                              <span className="text-[#AAAAAA]">Report:</span>{' '}
                              {formatOptionalDate(filing.reportDate)}
                            </div>
                          </div>
                          {accession && (
                            <div className="mt-2 font-mono text-[11px] text-[#AAAAAA]">
                              {accession}
                            </div>
                          )}
                        </div>
                        <div className="flex flex-wrap gap-2">
                          {filing.sourceUrl && (
                            <a
                              href={filing.sourceUrl}
                              target="_blank"
                              rel="noreferrer"
                              className="inline-flex h-9 items-center gap-1.5 rounded-lg border border-[#E5E5E5] px-3 text-[13px] font-semibold text-[#333333] hover:bg-[#F5F5F5]"
                            >
                              <ExternalLink size={14} />
                              SEC
                            </a>
                          )}
                          <button
                            type="button"
                            onClick={() => void handleImport(filing)}
                            disabled={isImporting || !accession}
                            className="h-9 rounded-lg bg-[#111111] px-4 text-[13px] font-semibold text-white disabled:opacity-50"
                          >
                            {isImporting ? 'Importing…' : 'Import'}
                          </button>
                        </div>
                      </div>
                    </article>
                  );
                })
              )}
            </div>
          </section>
        </div>
      </div>
    </>
  );
}

function filterButtonBadge(
  selectedForms: EdgarSupportedForm[],
  allFormsSelected: boolean
): string | null {
  if (allFormsSelected) return null;
  if (selectedForms.length === 1) return selectedForms[0];
  return String(selectedForms.length);
}

function filingsSummary(selectedForms: EdgarSupportedForm[]): string {
  if (selectedForms.length === ALL_FORM_FILTERS.length)
    return 'all supported filings';
  if (selectedForms.length === 1) return formLabel(selectedForms[0]);
  if (selectedForms.length === 2) {
    return `${selectedForms[0]} and ${selectedForms[1]} filings`;
  }
  return `${selectedForms.slice(0, -1).join(', ')}, and ${selectedForms.at(-1)} filings`;
}

function formLabel(form: EdgarSupportedForm): string {
  if (form === '10-K') return 'annual reports';
  if (form === '10-K/A') return 'annual report amendments';
  if (form === '10-Q') return 'quarterly reports';
  return 'quarterly report amendments';
}

function emptyStateTitle(
  selectedCompany: EdgarCompany | null,
  selectedForms: EdgarSupportedForm[],
  totalFilings: number
): string {
  if (!selectedCompany) return 'No company selected.';
  if (selectedForms.length < ALL_FORM_FILTERS.length && totalFilings > 0) {
    return `No ${filingsSummary(selectedForms)} in this list.`;
  }
  return 'No filings were returned.';
}

function emptyStateBody(
  selectedCompany: EdgarCompany | null,
  selectedForms: EdgarSupportedForm[]
): string {
  if (!selectedCompany) {
    return 'Only 10-K, 10-Q, and their amendments can be imported. Search for a company and select it to browse those filings.';
  }
  if (selectedForms.length < ALL_FORM_FILTERS.length) {
    return 'Check All filings to see every 10-K, 10-Q, and amendment returned for this company.';
  }
  return 'Only 10-K, 10-Q, and their amendments can be imported.';
}
