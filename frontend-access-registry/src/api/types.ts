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

/** 人员类型：正式员工长期有效；外部、临时人员必须带有效期。 */
export type PersonType = 'EMPLOYEE' | 'EXTERNAL' | 'TEMPORARY';

export interface UserView {
  id: number;
  username: string;
  displayName: string;
  enabled: boolean;
  administrator: boolean;
  departmentId: number | null;
  personType: PersonType | null;
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
  personType: PersonType;
  expiresOn: string | null;
}

export interface UserUpdate {
  displayName: string;
  departmentId: number | null;
  personType: PersonType;
  expiresOn: string | null;
}

export interface DepartmentView {
  id: number;
  name: string;
  parentId: number | null;
  /** 部门编码，全局唯一；员工信息同步建立的部门为 D 加四位编号。 */
  code: string | null;
}

export interface DepartmentInput {
  name: string;
  parentId: number | null;
  /** 新建时留空由后端按编号生成；修改时留空保留原编码。 */
  code: string | null;
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
  /** 随 /auth/me 下发给接入应用的后端权限码，可空。 */
  code: string | null;
  /** 接入应用按 operationId 询问能否调用该接口，可空。 */
  operationId: string | null;
}

export interface ResourceCreate {
  systemId: number;
  name: string;
  method: string;
  path: string;
  code: string | null;
  operationId: string | null;
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
  /** 从被授予的角色到持有权限的角色，沿继承链的角色标识。 */
  path: number[];
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
