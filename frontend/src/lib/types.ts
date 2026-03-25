export interface Document {
  id: string;
  fileName: string;
  size?: number;
  type?: string;
  status: 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED';
  aiSummary?: string;
  uploadedAt: string | number | Date;
}

export interface AuthContextType {
  token: string;
  logout: () => void;
}
