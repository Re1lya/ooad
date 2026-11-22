/**
 * 统一 HTTP 边界：Bearer 令牌、X-Client-Menu（后端按菜单写 logback）、
 * 超时、401 回调与错误转换。
 */
export class ApiError extends Error {
  constructor(
    message: string,
    readonly status: number,
    readonly code?: string,
  ) {
    super(message);
    this.name = 'ApiError';
  }
}

interface HttpHooks {
  token(): string;
  menu(): string;
  unauthorized(): void;
}

let hooks: HttpHooks = { token: () => '', menu: () => 'login', unauthorized: () => undefined };

export function configureHttp(next: HttpHooks): void {
  hooks = next;
}

type Query = Record<string, string | number | boolean | null | undefined>;

export interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE';
  body?: unknown;
  query?: Query;
}

function withQuery(path: string, query?: Query): string {
  if (!query) return path;
  const params = new URLSearchParams();
  for (const [key, value] of Object.entries(query))
    if (value !== undefined && value !== null && value !== '') params.set(key, String(value));
  const text = params.toString();
  return text ? `${path}?${text}` : path;
}

export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const owner = hooks.token();
  const form = options.body instanceof FormData;
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), form ? 180_000 : 15_000);
  const headers: Record<string, string> = { 'X-Client-Menu': hooks.menu() };
  if (owner) headers.Authorization = `Bearer ${owner}`;
  if (options.body !== undefined && !form) headers['Content-Type'] = 'application/json';
  try {
    const response = await fetch(withQuery(path, options.query), {
      method: options.method ?? 'GET',
      headers,
      signal: controller.signal,
      body:
        options.body === undefined
          ? undefined
          : form
            ? (options.body as FormData)
            : JSON.stringify(options.body),
    });
    const data = response.status === 204 ? null : await response.json().catch(() => null);
    if (!response.ok) {
      if (response.status === 401 && owner) hooks.unauthorized();
      throw new ApiError(
        (data && data.message) || `请求失败（${response.status}）`,
        response.status,
        data && data.code,
      );
    }
    return data as T;
  } catch (error) {
    if (error instanceof ApiError) throw error;
    if (error instanceof DOMException && error.name === 'AbortError')
      throw new ApiError('请求超时，请检查网络后重试', 0);
    throw new ApiError('无法连接服务器，请稍后重试', 0);
  } finally {
    clearTimeout(timer);
  }
}
