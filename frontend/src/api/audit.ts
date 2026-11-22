import { request } from './http';
import type { AuditPage } from './types';

export const auditApi = {
  /** system 为应用编码；page 从 1 开始。 */
  list: (system: string, page: number, pageSize: number, target?: string) =>
    request<AuditPage>('/api/audit-logs', { query: { system, page, pageSize, target } }),
};
