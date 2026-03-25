export const getCookie = (name: string): string => {
  if (typeof document === 'undefined') return '';
  const match = document.cookie.match(new RegExp(`(^| )${name}=([^;]+)`));
  return match ? match[2] : '';
};

export const setCookie = (name: string, value: string, days: number = 7) => {
  const expires = new Date();
  expires.setTime(expires.getTime() + days * 24 * 60 * 60 * 1000);
  document.cookie = `${name}=${value};path=/;expires=${expires.toUTCString()}`;
};

export const deleteCookie = (name: string) => {
  document.cookie = `${name}=;path=/;expires=Thu, 01 Jan 1970 00:00:00 UTC;`;
};

export const apiCall = async (
  endpoint: string,
  options?: RequestInit,
  token?: string
) => {
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...(options?.headers && typeof options.headers === 'object'
      ? (options.headers as Record<string, string>)
      : {}),
  };

  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }

  const response = await fetch(`/api${endpoint}`, {
    ...options,
    headers,
  });

  if (response.status === 401) {
    deleteCookie('jwtToken');
    if (typeof window !== 'undefined') {
      window.location.reload();
    }
  }

  if (!response.ok) {
    throw new Error(`API Error: ${response.statusText}`);
  }

  // Successful DELETE calls often return 204 with no response body.
  if (response.status === 204 || response.status === 205) {
    return null;
  }

  const responseText = await response.text();
  if (!responseText.trim()) {
    return null;
  }

  return JSON.parse(responseText);
};
