/** 部门的显示名：员工信息同步建立“分公司 / 部门”两级，不同分公司下常有同名部门，只写名字分不清。 */
import type { DepartmentView } from '@/api/types';

export interface DepartmentOption {
  id: number;
  /** 从一级部门起的完整路径，如“道路工程分公司 / 综合办公室”。 */
  label: string;
  depth: number;
}

/** 按树的先序排好的全部部门，带完整路径。 */
export function departmentOptions(list: DepartmentView[]): DepartmentOption[] {
  const children = new Map<number | null, DepartmentView[]>();
  for (const d of list) {
    const key = d.parentId != null && list.some((p) => p.id === d.parentId) ? d.parentId : null;
    children.set(key, [...(children.get(key) ?? []), d]);
  }
  const out: DepartmentOption[] = [];
  const walk = (parent: number | null, prefix: string, depth: number): void => {
    for (const d of children.get(parent) ?? []) {
      const label = prefix ? `${prefix} / ${d.name}` : d.name;
      out.push({ id: d.id, label, depth });
      walk(d.id, label, depth + 1);
    }
  };
  walk(null, '', 0);
  return out;
}
