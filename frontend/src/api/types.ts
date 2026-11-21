/** 与后端 DTO 对应的类型。 */
export type PersonType = 'EMPLOYEE' | 'EXTERNAL' | 'TEMPORARY';
export type PermissionType = 'MENU' | 'BUTTON';

export interface User {
  id: number;
  username: string;
  displayName: string;
  enabled: boolean;
  administrator: boolean;
  departmentId: number | null;
  personType: PersonType;
  expiresAt: string | null;
  companyName?: string | null;
  jobTitle?: string | null;
  phone?: string | null;
  sourceDepartment?: string | null;
}

export interface Page<T> {
  items: T[];
  total: number;
  page: number;
  size: number;
}

export interface Token {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
  user: User;
}

export interface Department {
  id: number;
  name: string;
  parentId: number | null;
  code: string | null;
}

export interface System {
  id: number;
  code: string;
  name: string;
  baseUrl: string | null;
  enabled: boolean;
}

export interface Role {
  id: number;
  code: string;
  name: string;
  description: string | null;
  enabled: boolean;
}

export interface Permission {
  id: number;
  systemId: number;
  code: string;
  name: string;
  type: PermissionType;
  menuPath: string | null;
  parentId: number | null;
  active: boolean;
}

export interface PermissionTree {
  permission: Permission;
  granted: boolean;
  children: PermissionTree[];
}

export interface Resource {
  id: number;
  systemId: number;
  name: string;
  method: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';
  path: string;
  active: boolean;
  code: string | null;
}

export interface Edge {
  seniorRoleId: number;
  seniorRoleName: string;
  inheritedRoleId: number;
  inheritedRoleName: string;
}

export interface Origin {
  assignedRoleId: number;
  assignedRoleName: string;
  permissionRoleId: number;
  permissionRoleName: string;
  inherited: boolean;
  path: number[];
}

export interface PermissionOrigin {
  permission: Permission;
  sources: Origin[];
}

export interface AuditActor {
  id: number | null;
  name: string | null;
  roles: string[];
  deleted: boolean;
}

export interface AuditEntry {
  id: number;
  systemCode: string;
  at: string;
  actor: AuditActor | null;
  type: string;
  objectType: string | null;
  objectId: string | null;
  target: string | null;
  before: Record<string, unknown> | null;
  after: Record<string, unknown> | null;
}

export interface AuditPage {
  page: number;
  pageSize: number;
  total: number;
  items: AuditEntry[];
}

/** 员工信息同步的预览或确认结果（不含员工个人明细）。 */
export interface SyncSummary {
  rows: number;
  companies: number;
  departmentsCreated: number;
  departmentsKept: number;
  usersCreated: number;
  usersUpdated: number;
  usersUnchanged: number;
  roleGrants: number;
  digest: string;
  applied: boolean;
}
