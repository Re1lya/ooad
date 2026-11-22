/** 后端编码到台账用语的对照；没有收录的编码原样显示。 */
import type { PersonType } from '@/api/types';

/** 人员类型：外部与临时人员必须填有效期。 */
export const PERSON_TYPES: { value: PersonType; label: string }[] = [
  { value: 'EMPLOYEE', label: '正式员工' },
  { value: 'EXTERNAL', label: '外部人员' },
  { value: 'TEMPORARY', label: '临时人员' },
];

export function personTypeText(type: string | null): string {
  return PERSON_TYPES.find((t) => t.value === type)?.label ?? type ?? '—';
}

/** 审计类型：权限系统自己记录角色继承的变化，课程系统报上来名单、分组、里程碑与成绩的变化。 */
const AUDIT_TYPES: Record<string, string> = {
  role_inheritance_added: '建立继承',
  role_inheritance_removed: '解除继承',
  roster_changed: '名单变更',
  group_changed: '分组变更',
  milestone_changed: '里程碑变更',
  score_changed: '成绩变更',
};

export function auditTypeText(type: string): string {
  return AUDIT_TYPES[type.toLowerCase()] ?? type;
}

/** 审计对象类型。 */
const OBJECT_TYPES: Record<string, string> = {
  role: '角色',
  term: '学期',
  course: '课程',
  class: '教学班',
  group: '小组',
  milestone: '里程碑',
  student: '学生',
  teacher: '教师',
  submission: '提交',
  oral_record: '答辩记录',
};

export function objectTypeText(type: string): string {
  return OBJECT_TYPES[type.toLowerCase()] ?? type;
}

/** 后端十一组业务模块，对应 /api/system-info 的 modules。 */
const MODULES: Record<string, string> = {
  authentication: '登录认证',
  users: '用户',
  departments: '部门',
  systems: '接入应用',
  roles: '角色',
  permissions: '菜单与按钮',
  resources: 'API 资源',
  authorization: '授权鉴权',
  hierarchy: '角色继承',
  directorySync: '员工信息同步',
  identity: '接入应用身份',
};

export function moduleText(key: string): string {
  return MODULES[key] ?? key;
}

/** 两种设计路线。 */
export function strategyText(strategy: string): string {
  return strategy === 'ddd' ? '面向对象' : strategy === 'structured' ? '结构化' : strategy;
}
