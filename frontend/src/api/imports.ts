import { request } from './http';
import type { ImportSummary } from './types';

/** 员工信息同步：先预览校验，再凭摘要确认写入；可重复执行。 */
export const importsApi = {
  preview: (files: FormData) =>
    request<ImportSummary>('/api/imports/company/preview', { method: 'POST', body: files }),
  commit: (files: FormData) =>
    request<ImportSummary>('/api/imports/company/commit', { method: 'POST', body: files }),
};
