/**
 * 统一 HTTP 边界：Bearer 令牌、X-Client-Menu（后端 logback 按菜单标记请求）、超时与错误转换。
 *
 * 请求发出时记下当时的令牌；响应回来时若身份已经变化（退出或换号），结果一律丢弃，
 * 避免异步返回把前一个账号的数据写回界面。
 */
export class ApiError extends Error {
  constructor(
    message: string,
    readonly status: number,
  ) {
    super(message);
    this.name = 'ApiError';
  }
}

/** 响应返回时身份已变化；调用方应静默忽略。 */
export class StaleSessionError extends Error {
  constructor() {
    super('会话已变化');
    this.name = 'StaleSessionError';
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
    if (owner !== hooks.token()) throw new StaleSessionError();
    if (!response.ok) {
      if (response.status === 401 && owner) hooks.unauthorized();
      throw new ApiError(data?.message || `请求失败（${response.status}）`, response.status);
    }
    return data as T;
  } catch (error) {
    if (error instanceof ApiError || error instanceof StaleSessionError) throw error;
    if (error instanceof DOMException && error.name === 'AbortError')
      throw new ApiError('请求超时，请检查网络后重试', 0);
    throw new ApiError('无法连接服务器，请稍后重试', 0);
  } finally {
    clearTimeout(timer);
  }
}
