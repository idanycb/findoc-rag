export const getCookie = (name: string): string => {
  if (typeof document === 'undefined') return '';
  const match = document.cookie.match(new RegExp(`(^| )${name}=([^;]+)`));
  return match ? match[2] : '';
};

export const setCookie = (name: string, value: string, days = 7) => {
  const expires = new Date();
  expires.setTime(expires.getTime() + days * 24 * 60 * 60 * 1000);
  document.cookie = `${name}=${value};path=/;expires=${expires.toUTCString()};SameSite=Lax`;
};

export const deleteCookie = (name: string) => {
  document.cookie = `${name}=;path=/;expires=Thu, 01 Jan 1970 00:00:00 UTC;SameSite=Lax`;
};

export class ApiError extends Error {
  constructor(
    public readonly status: number,
    message: string
  ) {
    super(message);
    this.name = 'ApiError';
  }
}

async function parseErrorMessage(response: Response): Promise<string> {
  try {
    const text = await response.text();
    if (!text.trim()) {
      if (response.status === 401) return 'Unauthorized — please log in again.';
      if (response.status === 403) return 'Forbidden — you do not have permission.';
      return response.statusText || `Error ${response.status}`;
    }
    const json = JSON.parse(text);
    if (typeof json.error === 'string') return json.error;
    if (typeof json.message === 'string') return json.message;
    return response.statusText || `Error ${response.status}`;
  } catch {
    return response.statusText || `Error ${response.status}`;
  }
}

export const apiCall = async <T = unknown>(
  endpoint: string,
  options?: RequestInit,
  token?: string
): Promise<T> => {
  const headers: Record<string, string> = {
    ...(options?.headers && typeof options.headers === 'object'
      ? (options.headers as Record<string, string>)
      : {}),
  };

  const isFormData = options?.body instanceof FormData;
  if (!isFormData) {
    headers['Content-Type'] = 'application/json';
  }

  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }

  const response = await fetch(`/api${endpoint}`, {
    ...options,
    headers,
  });

  if (response.status === 401 && token) {
    deleteCookie('jwtToken');
    if (typeof window !== 'undefined') {
      window.location.replace('/login');
    }
    throw new ApiError(401, 'Session expired');
  }

  if (!response.ok) {
    const msg = await parseErrorMessage(response);
    throw new ApiError(response.status, msg);
  }

  if (response.status === 204 || response.headers.get('content-length') === '0') {
    return null as T;
  }

  const text = await response.text();
  if (!text.trim()) return null as T;

  return JSON.parse(text) as T;
};
