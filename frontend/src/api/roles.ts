import { request } from './http';
import type { Edge, Permission, PermissionOrigin, Role } from './types';

export const rolesApi = {
  list: () => request<Role[]>('/api/roles'),
  create: (body: { code: string; name: string; description: string | null }) =>
    request<Role>('/api/roles', { method: 'POST', body }),
  update: (id: number, body: { name: string; description: string | null }) =>
    request<Role>(`/api/roles/${id}`, { method: 'PUT', body }),
  status: (id: number, enabled: boolean) =>
    request<Role>(`/api/roles/${id}/status`, { method: 'PUT', body: { enabled } }),
  remove: (id: number) => request<void>(`/api/roles/${id}`, { method: 'DELETE' }),

  /** 角色继承：高级角色 → 被继承角色。 */
  hierarchy: () => request<Edge[]>('/api/role-hierarchy'),
  inherit: (seniorId: number, inheritedId: number) =>
    request<void>(`/api/roles/${seniorId}/inherits/${inheritedId}`, { method: 'PUT' }),
  uninherit: (seniorId: number, inheritedId: number) =>
    request<void>(`/api/roles/${seniorId}/inherits/${inheritedId}`, { method: 'DELETE' }),
  effective: (id: number) => request<PermissionOrigin[]>(`/api/roles/${id}/effective-permissions`),

  /** 角色的直接权限。 */
  permissions: (id: number) => request<Permission[]>(`/api/roles/${id}/permissions`),
  grant: (id: number, permissionId: number) =>
    request<void>(`/api/roles/${id}/permissions/${permissionId}`, { method: 'PUT' }),
  revoke: (id: number, permissionId: number) =>
    request<void>(`/api/roles/${id}/permissions/${permissionId}`, { method: 'DELETE' }),
};
