import { reactive } from 'vue';
import { configureHttp } from '@/api/http';
import { authApi } from '@/api/auth';
import { meApi } from '@/api/me';
import type { PermissionTree, User } from '@/api/types';
import { resetCatalog } from './catalog';
import { clearNotice, ui } from './ui';

const TOKEN_KEY = 'permission-token';

/** 本系统在统一授权中的应用编码：控制台菜单与按钮都登记在这个应用下。 */
export const SELF_SYSTEM = 'rbac';

export interface NavItem {
  code: string;
  name: string;
  path: string;
}

/** 会话：JWT 只存在当前标签页；菜单与按钮来自后端对 rbac 应用的有效授权。 */
export const session = reactive({
  token: sessionStorage.getItem(TOKEN_KEY) ?? '',
  me: null as User | null,
  /** 已授权（或作为已授权按钮祖先）的菜单地址。 */
  paths: new Set<string>(),
  /** 已授权的按钮权限码。 */
  buttons: new Set<string>(),
  menu: '' as string,
});

configureHttp({
  token: () => session.token,
  menu: () => session.menu || 'login',
  unauthorized: () => logout(),
});

export function can(code: string): boolean {
  return session.buttons.has(code);
}

export function canVisit(path: string): boolean {
  return session.paths.has(path);
}

/** 展开 rbac 应用的菜单树：菜单本身已授权，或其下有已授权按钮时可见。 */
function absorb(nodes: PermissionTree[]): boolean {
  let any = false;
  for (const node of nodes) {
    const { permission } = node;
    if (!permission.code.startsWith(`${SELF_SYSTEM}.`)) continue;
    const childGranted = absorb(node.children ?? []);
    if (permission.type === 'BUTTON' && node.granted) session.buttons.add(permission.code);
    const visible = node.granted || childGranted;
    if (permission.type === 'MENU' && visible && permission.menuPath)
      session.paths.add(permission.menuPath);
    any = any || visible;
  }
  return any;
}

export async function loadIdentity(): Promise<void> {
  const owner = session.token;
  const [me, tree] = await Promise.all([authApi.me(), meApi.menu(SELF_SYSTEM)]);
  if (owner !== session.token) return;
  session.paths = new Set();
  session.buttons = new Set();
  absorb(tree);
  session.me = me;
}

export async function login(username: string, password: string): Promise<void> {
  const token = await authApi.login(username, password);
  session.token = token.accessToken;
  sessionStorage.setItem(TOKEN_KEY, token.accessToken);
  await loadIdentity();
}

/** 完全清理身份与目录数据，避免下一个账号看到前一个账号的内容。 */
export function logout(): void {
  session.token = '';
  sessionStorage.removeItem(TOKEN_KEY);
  session.me = null;
  session.paths = new Set();
  session.buttons = new Set();
  ui.confirm?.resolve(false);
  clearNotice();
  resetCatalog();
}
