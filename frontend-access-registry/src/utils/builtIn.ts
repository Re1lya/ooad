/**
 * 权限系统自身的内置对象，与后端内置目录（ConsoleCatalog）一致。
 * 后端拒绝停用或删除它们（CONSOLE_BUILT_IN），否则所有人都会进不了控制台；界面上也不提供这两个操作。
 */
import { SELF_SYSTEM } from '@/stores/session';

/** 内置管理员角色编码。 */
export const ADMIN_ROLE = 'rbac-admin';

/** 鼠标停在“内置”印记上时的说明。 */
export const BUILT_IN_HINT = '权限系统的内置对象，不能停用或注销，否则所有人都会进不了控制台';

/** 权限系统自身的内置应用。 */
export const isBuiltInSystem = (code: string): boolean => code === SELF_SYSTEM;

/** 内置管理员角色。 */
export const isBuiltInRole = (code: string): boolean => code === ADMIN_ROLE;

/** 控制台的内置菜单与按钮：属于内置应用，编码以 rbac. 开头。 */
export const isBuiltInPermission = (systemCode: string | undefined, code: string): boolean =>
  systemCode === SELF_SYSTEM && code.startsWith(`${SELF_SYSTEM}.`);

/** 控制台的内置管理接口：属于内置应用。 */
export const isBuiltInResource = (systemCode: string | undefined): boolean => systemCode === SELF_SYSTEM;
