/** 全部后端端点封装，按业务分组。 */
import { request } from './http';
import type {
  AuditPage,
  DepartmentInput,
  DepartmentView,
  HierarchyEdge,
  Login,
  PermissionOrigin,
  PermissionTree,
  PermissionView,
  ResourceView,
  RoleView,
  SyncSummary,
  SystemInfo,
  SystemView,
  Token,
  UserCreate,
  UserPage,
  UserUpdate,
  UserView,
} from './types';

export const authApi = {
  login: (body: Login) => request<Token>('/api/auth/login', { method: 'POST', body }),
};

export const meApi = {
  profile: () => request<UserView>('/api/me'),
  menu: () => request<PermissionTree[]>('/api/me/menu'),
  permissions: () => request<PermissionView[]>('/api/me/permissions'),
  origins: () => request<PermissionOrigin[]>('/api/me/permission-origins'),
  changePassword: (currentPassword: string, newPassword: string) =>
    request<void>('/api/me/password', { method: 'PUT', body: { currentPassword, newPassword } }),
};

export const usersApi = {
  page: (page: number, pageSize: number, keyword: string) =>
    request<UserPage>('/api/users', { query: { page, pageSize, keyword } }),
  one: (id: number) => request<UserView>(`/api/users/${id}`),
  create: (body: UserCreate) => request<UserView>('/api/users', { method: 'POST', body }),
  update: (id: number, body: UserUpdate) =>
    request<UserView>(`/api/users/${id}`, { method: 'PUT', body }),
  status: (id: number, enabled: boolean) =>
    request<UserView>(`/api/users/${id}/status`, { method: 'PUT', body: { enabled } }),
  setPassword: (id: number, password: string) =>
    request<void>(`/api/users/${id}/password`, { method: 'PUT', body: { password } }),
  remove: (id: number) => request<void>(`/api/users/${id}`, { method: 'DELETE' }),
  roles: (userId: number) => request<RoleView[]>(`/api/users/${userId}/roles`),
  grantRole: (userId: number, roleId: number) =>
    request<void>(`/api/users/${userId}/roles/${roleId}`, { method: 'PUT' }),
  revokeRole: (userId: number, roleId: number) =>
    request<void>(`/api/users/${userId}/roles/${roleId}`, { method: 'DELETE' }),
  origins: (userId: number) => request<PermissionOrigin[]>(`/api/users/${userId}/permission-origins`),
};

export const departmentsApi = {
  list: () => request<DepartmentView[]>('/api/departments'),
  create: (body: DepartmentInput) =>
    request<DepartmentView>('/api/departments', { method: 'POST', body }),
  update: (id: number, body: DepartmentInput) =>
    request<DepartmentView>(`/api/departments/${id}`, { method: 'PUT', body }),
  remove: (id: number) => request<void>(`/api/departments/${id}`, { method: 'DELETE' }),
};

export const systemsApi = {
  list: () => request<SystemView[]>('/api/systems'),
  create: (body: { code: string; name: string; baseUrl: string | null }) =>
    request<SystemView>('/api/systems', { method: 'POST', body }),
  update: (id: number, body: { name: string; baseUrl: string | null }) =>
    request<SystemView>(`/api/systems/${id}`, { method: 'PUT', body }),
  status: (id: number, enabled: boolean) =>
    request<SystemView>(`/api/systems/${id}/status`, { method: 'PUT', body: { enabled } }),
  remove: (id: number) => request<void>(`/api/systems/${id}`, { method: 'DELETE' }),
};

export const rolesApi = {
  list: () => request<RoleView[]>('/api/roles'),
  create: (body: { code: string; name: string; description: string | null }) =>
    request<RoleView>('/api/roles', { method: 'POST', body }),
  update: (id: number, body: { name: string; description: string | null }) =>
    request<RoleView>(`/api/roles/${id}`, { method: 'PUT', body }),
  status: (id: number, enabled: boolean) =>
    request<RoleView>(`/api/roles/${id}/status`, { method: 'PUT', body: { enabled } }),
  remove: (id: number) => request<void>(`/api/roles/${id}`, { method: 'DELETE' }),
  permissions: (id: number) => request<PermissionView[]>(`/api/roles/${id}/permissions`),
  grant: (id: number, permissionId: number) =>
    request<void>(`/api/roles/${id}/permissions/${permissionId}`, { method: 'PUT' }),
  revoke: (id: number, permissionId: number) =>
    request<void>(`/api/roles/${id}/permissions/${permissionId}`, { method: 'DELETE' }),
  effective: (id: number) => request<PermissionOrigin[]>(`/api/roles/${id}/effective-permissions`),
};

export const hierarchyApi = {
  edges: () => request<HierarchyEdge[]>('/api/role-hierarchy'),
  link: (seniorId: number, inheritedId: number) =>
    request<void>(`/api/roles/${seniorId}/inherits/${inheritedId}`, { method: 'PUT' }),
  unlink: (seniorId: number, inheritedId: number) =>
    request<void>(`/api/roles/${seniorId}/inherits/${inheritedId}`, { method: 'DELETE' }),
};

export const permissionsApi = {
  list: (systemId?: number) => request<PermissionView[]>('/api/permissions', { query: { systemId } }),
  tree: (systemId?: number) =>
    request<PermissionTree[]>('/api/permissions/tree', { query: { systemId } }),
  create: (body: {
    systemId: number;
    code: string;
    name: string;
    type: string;
    menuPath: string | null;
    parentId: number | null;
  }) => request<PermissionView>('/api/permissions', { method: 'POST', body }),
  update: (
    id: number,
    body: { name: string; type: string; menuPath: string | null; parentId: number | null },
  ) => request<PermissionView>(`/api/permissions/${id}`, { method: 'PUT', body }),
  status: (id: number, active: boolean) =>
    request<PermissionView>(`/api/permissions/${id}/status`, { method: 'PUT', body: { active } }),
  remove: (id: number) => request<void>(`/api/permissions/${id}`, { method: 'DELETE' }),
  resources: (id: number) => request<ResourceView[]>(`/api/permissions/${id}/resources`),
  bindResource: (id: number, resourceId: number) =>
    request<void>(`/api/permissions/${id}/resources/${resourceId}`, { method: 'PUT' }),
  unbindResource: (id: number, resourceId: number) =>
    request<void>(`/api/permissions/${id}/resources/${resourceId}`, { method: 'DELETE' }),
};

export const resourcesApi = {
  list: () => request<ResourceView[]>('/api/resources'),
  create: (body: { systemId: number; name: string; method: string; path: string; code: string }) =>
    request<ResourceView>('/api/resources', { method: 'POST', body }),
  update: (id: number, name: string) =>
    request<ResourceView>(`/api/resources/${id}`, { method: 'PUT', body: { name } }),
  status: (id: number, active: boolean) =>
    request<ResourceView>(`/api/resources/${id}/status`, { method: 'PUT', body: { active } }),
  remove: (id: number) => request<void>(`/api/resources/${id}`, { method: 'DELETE' }),
};

export const directoryApi = {
  preview: (form: FormData) =>
    request<SyncSummary>('/api/directory/sync/preview', { method: 'POST', body: form }),
  commit: (form: FormData) =>
    request<SyncSummary>('/api/directory/sync/commit', { method: 'POST', body: form }),
};

export const auditApi = {
  page: (query: { system?: string; target?: string; page: number; pageSize: number }) =>
    request<AuditPage>('/api/audit-logs', { query }),
};

export const systemApi = {
  info: () => request<SystemInfo>('/api/system-info'),
};
