export type UserRole = 'SUPER_ADMIN' | 'ADMIN' | 'MEMBER';
export type DocumentStatus = 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED';

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

export interface AuthContextType {
  token: string;
  claims: JwtClaims | null;
  isHydrated: boolean;
  logout: () => void;
}
