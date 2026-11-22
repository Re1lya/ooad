/** 授权链的显示：后端返回的继承路径是一串角色标识，这里换成角色名。 */
import type { PermissionOrigin, RoleView } from '@/api/types';

/** 角色标识到名字：先取各条来源自带的角色名，再用角色列表补齐路径中间的角色（读不到角色列表时为空）。 */
export function roleNames(origins: PermissionOrigin[], roles: RoleView[] = []): Map<number, string> {
  const names = new Map<number, string>();
  for (const r of roles) names.set(r.id, r.name);
  for (const o of origins)
    for (const s of o.sources) {
      names.set(s.assignedRoleId, s.assignedRoleName);
      names.set(s.permissionRoleId, s.permissionRoleName);
    }
  return names;
}

/** 继承路径，如“课程管理员 → 课程督导 → 教师”；路径上认不出的角色写编号。 */
export function pathText(path: number[], names: Map<number, string>): string {
  return path.map((id) => names.get(id) ?? `#${id}`).join(' → ');
}
