export interface UserDto {
  id: number;
  email: string;
  role: string;
  companyId: number;
}

export interface AuthResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
  user: UserDto;
}

export class ApiError extends Error {
  status: number;
  title: string;

  constructor(status: number, title: string, detail: string) {
    super(detail);
    this.status = status;
    this.title = title;
  }
}

const TOKEN_KEY = 'tenderops.token';

export function getToken(): string | null {
  return sessionStorage.getItem(TOKEN_KEY);
}

export function setToken(token: string): void {
  sessionStorage.setItem(TOKEN_KEY, token);
}

export function clearToken(): void {
  sessionStorage.removeItem(TOKEN_KEY);
}

/**
 * fetch wrapper: Bearer injection, ProblemDetail parsing, 401 redirect to /login.
 * Money arrives as strings and is never computed with here (CL-06).
 */
export async function api<T>(path: string, options: RequestInit = {}): Promise<T> {
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...((options.headers as Record<string, string>) ?? {}),
  };
  const token = getToken();
  if (token) {
    headers['Authorization'] = 'Bearer ' + token;
  }
  const response = await fetch(path, { ...options, headers });
  if (response.status === 401) {
    clearToken();
    if (window.location.pathname !== '/login') {
      window.location.assign('/login');
    }
    throw new ApiError(401, 'Unauthorized', 'Authentication required');
  }
  if (!response.ok) {
    let title = 'Request failed';
    let detail = 'Request failed with status ' + response.status;
    try {
      const problem = (await response.json()) as { title?: string; detail?: string };
      if (problem.title) title = problem.title;
      if (problem.detail) detail = problem.detail;
    } catch {
      // keep defaults
    }
    throw new ApiError(response.status, title, detail);
  }
  if (response.status === 204) {
    return undefined as T;
  }
  return (await response.json()) as T;
}
