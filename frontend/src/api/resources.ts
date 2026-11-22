import { request } from './http';
import type { Resource } from './types';

export interface ResourceCreate {
  systemId: number;
  name: string;
  method: Resource['method'];
  path: string;
  code: string | null;
}

export const resourcesApi = {
  list: () => request<Resource[]>('/api/resources'),
  create: (body: ResourceCreate) => request<Resource>('/api/resources', { method: 'POST', body }),
  update: (id: number, body: { name: string }) =>
    request<Resource>(`/api/resources/${id}`, { method: 'PUT', body }),
  status: (id: number, active: boolean) =>
    request<Resource>(`/api/resources/${id}/status`, { method: 'PUT', body: { active } }),
  remove: (id: number) => request<void>(`/api/resources/${id}`, { method: 'DELETE' }),
};
