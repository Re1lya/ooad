import { request } from './http';
import type { System } from './types';

export const systemsApi = {
  list: () => request<System[]>('/api/systems'),
  create: (body: { code: string; name: string; baseUrl: string | null }) =>
    request<System>('/api/systems', { method: 'POST', body }),
  update: (id: number, body: { name: string; baseUrl: string | null }) =>
    request<System>(`/api/systems/${id}`, { method: 'PUT', body }),
  status: (id: number, enabled: boolean) =>
    request<System>(`/api/systems/${id}/status`, { method: 'PUT', body: { enabled } }),
  remove: (id: number) => request<void>(`/api/systems/${id}`, { method: 'DELETE' }),
};
