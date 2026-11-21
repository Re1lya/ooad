import { request } from './http';
import type { Department } from './types';

export interface DepartmentInput {
  name: string;
  parentId: number | null;
  /** 不填时新部门自动编码，修改时保留原编码。 */
  code?: string;
}

export const departmentsApi = {
  list: () => request<Department[]>('/api/departments'),
  create: (body: DepartmentInput) =>
    request<Department>('/api/departments', { method: 'POST', body }),
  update: (id: number, body: DepartmentInput) =>
    request<Department>(`/api/departments/${id}`, { method: 'PUT', body }),
  remove: (id: number) => request<void>(`/api/departments/${id}`, { method: 'DELETE' }),
};
