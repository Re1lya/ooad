import { request } from './http';
import type { Permission, PermissionOrigin, PermissionTree } from './types';

export const meApi = {
  /** system 为应用编码：只返回当前用户在该应用内的有效菜单树。 */
  menu: (system?: string) => request<PermissionTree[]>('/api/me/menu', { query: { system } }),
  permissions: () => request<Permission[]>('/api/me/permissions'),
  origins: () => request<PermissionOrigin[]>('/api/me/permission-origins'),
};
