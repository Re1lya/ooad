/** 与后端 OpenAPI 对齐的数据模型。 */

export interface Login {
  username: string;
  password: string;
}

export interface Token {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
  user: UserView;
}

export interface UserView {
  id: number;
  username: string;
  displayName: string;
  enabled: boolean;
  administrator: boolean;
  departmentId: number | null;
  personType: string | null;
  expiresAt: string | null;
  companyName: string | null;
  sourceDepartment: string | null;
  jobTitle: string | null;
  phone: string | null;
}

export interface UserPage {
  page: number;
  pageSize: number;
  total: number;
  items: UserView[];
}

export interface UserCreate {
  username: string;
  displayName: string;
  password: string;
  departmentId: number | null;
  personType: string;
  expiresOn: string | null;
}

export interface UserUpdate {
  displayName: string;
  departmentId: number | null;
  personType: string;
  expiresOn: string | null;
}

export interface DepartmentView {
  id: number;
  name: string;
  parentId: number | null;
  managerName: string | null;
}

export interface DepartmentInput {
  name: string;
  parentId: number | null;
}

export interface RoleView {
  id: number;
  code: string;
  name: string;
  description: string | null;
  enabled: boolean;
}

export interface SystemView {
  id: number;
  code: string;
  name: string;
  baseUrl: string | null;
  enabled: boolean;
}

export type PermissionType = 'MENU' | 'BUTTON';

export interface PermissionView {
  id: number;
  systemId: number;
  code: string;
  name: string;
  type: PermissionType;
  menuPath: string | null;
  parentId: number | null;
  active: boolean;
  sourceDescription: string | null;
  sourceGrants: string | null;
}

export interface PermissionTree {
  permission: PermissionView;
  granted: boolean;
  children: PermissionTree[];
}

export interface ResourceView {
  id: number;
  systemId: number;
  name: string;
  method: string;
  path: string;
  active: boolean;
  code: string | null;
}

export interface HierarchyEdge {
  seniorRoleId: number;
  seniorRoleName: string;
  inheritedRoleId: number;
  inheritedRoleName: string;
}

export interface PermissionSource {
  assignedRoleId: number;
  assignedRoleName: string;
  permissionRoleId: number;
  permissionRoleName: string;
  inherited: boolean;
  path: string[];
}

export interface PermissionOrigin {
  permission: PermissionView;
  sources: PermissionSource[];
}

export interface SystemInfo {
  backendStrategy: string;
  authorizationModel: string;
  modules: Record<string, string>;
}

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

export interface AuditActor {
  id: number;
  name: string;
  roles: string[];
  deleted: boolean;
}

export interface AuditEntry {
  id: number;
  systemCode: string;
  at: string;
  actor: AuditActor;
  type: string;
  objectType: string;
  objectId: string;
  target: string;
  before: Record<string, unknown> | null;
  after: Record<string, unknown> | null;
  termId: string | null;
  courseId: string | null;
  classId: string | null;
}

export interface AuditPage {
  page: number;
  pageSize: number;
  total: number;
  items: AuditEntry[];
}

export interface ApiErrorBody {
  code: string;
  message: string;
}
