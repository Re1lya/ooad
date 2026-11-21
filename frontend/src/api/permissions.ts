import { request } from './http';
import type { Permission, PermissionTree, PermissionType, Resource } from './types';

export interface PermissionInput {
  systemId: number;
  code: string;
  name: string;
  type: PermissionType;
  menuPath: string | null;
  parentId: number | null;
}

export const permissionsApi = {
  list: (systemId?: number) => request<Permission[]>('/api/permissions', { query: { systemId } }),
  tree: (systemId?: number) =>
    request<PermissionTree[]>('/api/permissions/tree', { query: { systemId } }),
  create: (body: PermissionInput) =>
    request<Permission>('/api/permissions', { method: 'POST', body }),
  update: (
    id: number,
    body: { name: string; type: PermissionType; menuPath: string | null; parentId: number | null },
  ) => request<Permission>(`/api/permissions/${id}`, { method: 'PUT', body }),
  status: (id: number, active: boolean) =>
    request<Permission>(`/api/permissions/${id}/status`, { method: 'PUT', body: { active } }),
  remove: (id: number) => request<void>(`/api/permissions/${id}`, { method: 'DELETE' }),
  resources: (id: number) => request<Resource[]>(`/api/permissions/${id}/resources`),
  bind: (id: number, resourceId: number) =>
    request<void>(`/api/permissions/${id}/resources/${resourceId}`, { method: 'PUT' }),
  unbind: (id: number, resourceId: number) =>
    request<void>(`/api/permissions/${id}/resources/${resourceId}`, { method: 'DELETE' }),
};
