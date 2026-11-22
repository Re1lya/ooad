import { request } from './http';
import type { Page, PermissionOrigin, PersonType, Role, User } from './types';

export interface UserCreate {
  username: string;
  displayName: string;
  password: string;
  departmentId: number | null;
  personType: PersonType;
  expiresOn: string | null;
}

export interface UserUpdate {
  displayName: string;
  departmentId: number | null;
  personType: PersonType;
  expiresOn: string | null;
}

export const usersApi = {
  /** 数据库分页（page 从 1 开始）；keyword 按工号、姓名检索。 */
  list: (page: number, pageSize: number, keyword?: string) =>
    request<Page<User>>('/api/users', { query: { page, pageSize, keyword } }),
  get: (id: number) => request<User>(`/api/users/${id}`),
  create: (body: UserCreate) => request<User>('/api/users', { method: 'POST', body }),
  update: (id: number, body: UserUpdate) =>
    request<User>(`/api/users/${id}`, { method: 'PUT', body }),
  status: (id: number, enabled: boolean) =>
    request<User>(`/api/users/${id}/status`, { method: 'PUT', body: { enabled } }),
  password: (id: number, password: string) =>
    request<void>(`/api/users/${id}/password`, { method: 'PUT', body: { password } }),
  remove: (id: number) => request<void>(`/api/users/${id}`, { method: 'DELETE' }),
  roles: (id: number) => request<Role[]>(`/api/users/${id}/roles`),
  assign: (id: number, roleId: number) =>
    request<void>(`/api/users/${id}/roles/${roleId}`, { method: 'PUT' }),
  revoke: (id: number, roleId: number) =>
    request<void>(`/api/users/${id}/roles/${roleId}`, { method: 'DELETE' }),
  origins: (id: number) => request<PermissionOrigin[]>(`/api/users/${id}/permission-origins`),
};
