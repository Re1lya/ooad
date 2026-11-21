import { SELF_SYSTEM } from '@/stores/session';

/** 内置管理员角色编码，与后端内置目录一致。 */
export const ADMIN_ROLE = 'rbac-admin';

/** 内置对象的提示：后端拒绝停用或删除它们（CONSOLE_BUILT_IN），界面上也不提供这两个操作。 */
export const BUILT_IN_HINT = '权限系统的内置对象，不能停用或删除，否则所有人都会进不了控制台';

/** 权限系统自身的内置应用。 */
export const isBuiltInSystem = (code: string) => code === SELF_SYSTEM;

/** 内置管理员角色。 */
export const isBuiltInRole = (code: string) => code === ADMIN_ROLE;

/** 控制台的内置菜单与按钮：属于内置应用，编码以 `rbac.` 开头。 */
export const isBuiltInPermission = (systemCode: string | undefined, code: string) =>
  systemCode === SELF_SYSTEM && code.startsWith(`${SELF_SYSTEM}.`);

/** 控制台的内置管理接口：属于内置应用。 */
export const isBuiltInResource = (systemCode: string | undefined) => systemCode === SELF_SYSTEM;
