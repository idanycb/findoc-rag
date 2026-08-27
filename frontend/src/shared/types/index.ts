export type UserRole = 'SUPER_ADMIN' | 'ADMIN' | 'MEMBER';
export type DocumentStatus = 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED';
export type DocumentSource = 'UPLOAD' | 'EDGAR' | string;

export interface JwtClaims {
  sub: string;
  userId: string;
  role: UserRole;
  teamId?: string | null;
  exp?: number;
}

export interface UserView {
  id: string;
  username: string;
  role: UserRole;
  teamId: string | null;
}

export interface TeamView {
  id: string;
  name: string;
  createdAt: string;
}

export interface DocumentSummaryResponse {
  id: string;
  fileName: string;
  fileSize: number;
  contentType: string;
  uploadedAt: string;
  status: DocumentStatus;
  source?: DocumentSource | null;
  ticker?: string | null;
  cik?: string | null;
  companyName?: string | null;
  formType?: string | null;
  fiscalPeriod?: string | null;
  reportDate?: string | null;
  filingDate?: string | null;
  accessionNumber?: string | null;
  sourceUrl?: string | null;
}

export interface DocumentDetailResponse extends DocumentSummaryResponse {
  lastAnalyzedAt: string | null;
}

export interface UploadResult {
  documentId: string;
  fileName: string;
  status: DocumentStatus;
  uploadUrl: string;
}

export interface EdgarCompany {
  ticker?: string | null;
  cik?: string | number | null;
  name?: string | null;
  companyName?: string | null;
}

export interface EdgarFiling {
  accession?: string | null;
  accessionNumber?: string | null;
  amendsAccessionNumber?: string | null;
  form?: string | null;
  formType?: string | null;
  filingDate?: string | null;
  reportDate?: string | null;
  fiscalPeriod?: string | null;
  sourceUrl?: string | null;
}

export interface EdgarImportRequest {
  ticker: string;
  accessionNumber: string;
  amendsAccessionNumber?: string | null;
  cik?: string | null;
  companyName?: string | null;
  formType: string;
  fiscalPeriod?: string | null;
  reportDate?: string | null;
  filingDate?: string | null;
  sourceUrl?: string | null;
}

export interface EdgarImportResult {
  documentId?: string | null;
  id?: string | null;
  fileName?: string | null;
  status?: DocumentStatus | string | null;
  message?: string | null;
}

export interface AuthContextType {
  token: string;
  claims: JwtClaims | null;
  isHydrated: boolean;
  logout: () => void;
}
