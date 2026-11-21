import type { AuditEntry, PersonType } from '@/api/types';

export const personTypes: [PersonType, string][] = [
  ['EMPLOYEE', '正式员工'],
  ['EXTERNAL', '外部人员'],
  ['TEMPORARY', '临时人员'],
];

export const personLabel = (value: PersonType | null | undefined): string =>
  personTypes.find(([key]) => key === value)?.[1] ?? '—';

const AUDIT_TYPES: Record<string, string> = {
  ROLE_INHERITANCE_ADDED: '建立继承',
  ROLE_INHERITANCE_REMOVED: '解除继承',
};

/** 审计操作名称：已知类型给出中文，其余保留原值。 */
export const auditTitle = (entry: AuditEntry): string => AUDIT_TYPES[entry.type] ?? entry.type;

export function auditTime(at: string): string {
  const date = new Date(at);
  const today = new Date();
  const hm = date.toLocaleTimeString('zh-CN', {
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  });
  if (date.toDateString() === today.toDateString()) return `今天 ${hm}`;
  return `${date.toLocaleDateString('zh-CN', { month: '2-digit', day: '2-digit' })} ${hm}`;
}

/** 有效期至：后端存的是次日零点（不含），显示为前一天。 */
export function expiresLabel(expiresAt: string | null): string {
  if (!expiresAt) return '长期';
  return new Date(new Date(expiresAt).getTime() - 1).toLocaleDateString('en-CA', {
    timeZone: 'Asia/Shanghai',
  });
}
