import { request } from './http';
import type { SyncSummary } from './types';

/** 员工信息同步：先预览变化，再凭摘要确认写入；可以重复执行。 */
export const directoryApi = {
  preview: (form: FormData) =>
    request<SyncSummary>('/api/directory/sync/preview', { method: 'POST', body: form }),
  commit: (form: FormData) =>
    request<SyncSummary>('/api/directory/sync/commit', { method: 'POST', body: form }),
};
