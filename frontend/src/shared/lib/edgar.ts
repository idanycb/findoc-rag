import { apiCall } from '@/shared/lib/api';
import type {
  EdgarCompany,
  EdgarFiling,
  EdgarImportRequest,
  EdgarImportResult,
} from '@/shared/types';

export const EDGAR_SUPPORTED_FORMS = ['10-K', '10-K/A', '10-Q', '10-Q/A'] as const;
export type EdgarSupportedForm = (typeof EDGAR_SUPPORTED_FORMS)[number];

export async function searchEdgarCompanies(
  query: string,
  token: string
): Promise<EdgarCompany[]> {
  const q = query.trim();
  if (!q) return [];

  return apiCall<EdgarCompany[]>(
    `/edgar/companies?q=${encodeURIComponent(q)}`,
    undefined,
    token
  );
}

export async function fetchEdgarFilings(
  companyId: string,
  token: string
): Promise<EdgarFiling[]> {
  const results = await Promise.allSettled(
    EDGAR_SUPPORTED_FORMS.map((form) => fetchFilingsByForm(companyId, form, token))
  );
  const filings = results.flatMap((result) =>
    result.status === 'fulfilled' ? (result.value ?? []) : []
  );
  const firstFailure = results.find((result) => result.status === 'rejected');
  if (filings.length === 0 && firstFailure?.status === 'rejected') {
    throw firstFailure.reason;
  }
  return sortFilingsNewestFirst(dedupeFilings(filings));
}

export async function importEdgarFiling(
  request: EdgarImportRequest,
  token: string
): Promise<EdgarImportResult> {
  return apiCall<EdgarImportResult>(
    '/edgar/filings/import',
    {
      method: 'POST',
      body: JSON.stringify(request),
    },
    token
  );
}

async function fetchFilingsByForm(
  companyId: string,
  formType: EdgarSupportedForm,
  token: string
): Promise<EdgarFiling[]> {
  const params = new URLSearchParams();
  params.set('type', formType);
  return apiCall<EdgarFiling[]>(
    `/edgar/companies/${encodeURIComponent(companyId)}/filings?${params.toString()}`,
    undefined,
    token
  );
}

function filingAccession(filing: EdgarFiling): string {
  return String(filing.accessionNumber || filing.accession || '').trim();
}

function filingTimestamp(filing: EdgarFiling): number {
  const value = filing.filingDate || filing.reportDate || '';
  const time = Date.parse(value);
  return Number.isNaN(time) ? 0 : time;
}

function dedupeFilings(filings: EdgarFiling[]): EdgarFiling[] {
  const seen = new Set<string>();
  const unique: EdgarFiling[] = [];
  for (const filing of filings) {
    const key = filingAccession(filing) || `${filing.formType || filing.form}-${filing.filingDate}`;
    if (seen.has(key)) continue;
    seen.add(key);
    unique.push(filing);
  }
  return unique;
}

function sortFilingsNewestFirst(filings: EdgarFiling[]): EdgarFiling[] {
  return [...filings].sort((a, b) => filingTimestamp(b) - filingTimestamp(a));
}
