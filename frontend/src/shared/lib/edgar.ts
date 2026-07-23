import { apiCall } from '@/shared/lib/api';
import type {
  EdgarCompany,
  EdgarFiling,
  EdgarImportRequest,
  EdgarImportResult,
} from '@/shared/types';

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
  formType: string,
  token: string
): Promise<EdgarFiling[]> {
  const params = new URLSearchParams();
  if (formType.trim()) params.set('type', formType.trim());

  const suffix = params.toString() ? `?${params.toString()}` : '';
  return apiCall<EdgarFiling[]>(
    `/edgar/companies/${encodeURIComponent(companyId)}/filings${suffix}`,
    undefined,
    token
  );
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
