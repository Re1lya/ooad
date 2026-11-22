/**
 * 会话：令牌、当前用户、模式信息，以及控制台自身的授权。
 *
 * 令牌存 sessionStorage，关页即清。权限台账也是统一授权管理的一个应用（编码 rbac）：
 * 侧栏菜单和写操作按钮都按本人在 rbac 应用下的有效授权显示，与后端的接口鉴权一致。
 */
import { reactive, readonly } from 'vue';
import { authApi, meApi, systemApi } from '@/api';
import { configureHttp } from '@/api/http';
import type { PermissionTree, SystemInfo, UserView } from '@/api/types';

const TOKEN_KEY = 'rbac-console.token';

/** 本系统在统一授权中的应用编码：控制台的菜单与按钮都登记在这个应用下。 */
export const SELF_SYSTEM = 'rbac';

interface SessionState {
  token: string;
  user: UserView | null;
  info: SystemInfo | null;
  ready: boolean;
  /** 当前菜单名，写入 X-Client-Menu 供后端分层日志使用。
   *  头值必须是 ASCII（fetch 禁止非 ASCII 头值），所以存路由名英文，
   *  中文名只用于界面展示。 */
  menu: string;
  /** 可进入的菜单地址：菜单本身已授权，或其下有已授权的按钮。 */
  paths: Set<string>;
  /** 已授权的按钮权限码，如 rbac.users.write。 */
  buttons: Set<string>;
}

const state = reactive<SessionState>({
  token: sessionStorage.getItem(TOKEN_KEY) ?? '',
  user: null,
  info: null,
  ready: false,
  menu: 'login',
  paths: new Set(),
  buttons: new Set(),
});

configureHttp({
  token: () => state.token,
  menu: () => state.menu,
  // 令牌过期、改密或被停用后旧令牌失效：清掉会话回登录页（哈希路由，改地址即触发路由守卫）
  unauthorized: () => {
    clearSession();
    if (!window.location.hash.startsWith('#/login')) window.location.hash = '#/login';
  },
});

function clearSession(): void {
  state.token = '';
  state.user = null;
  state.info = null;
  state.paths = new Set();
  state.buttons = new Set();
  sessionStorage.removeItem(TOKEN_KEY);
}

/** 展开 rbac 应用的菜单树：菜单本身已授权，或其下有已授权按钮时可见。 */
function absorb(nodes: PermissionTree[], paths: Set<string>, buttons: Set<string>): boolean {
  let any = false;
  for (const node of nodes) {
    const { permission } = node;
    if (!permission.code.startsWith(`${SELF_SYSTEM}.`)) continue;
    const childGranted = absorb(node.children ?? [], paths, buttons);
    if (permission.type === 'BUTTON' && node.granted) buttons.add(permission.code);
    const visible = node.granted || childGranted;
    if (permission.type === 'MENU' && visible && permission.menuPath) paths.add(permission.menuPath);
    any = any || visible;
  }
  return any;
}

async function loadAccess(): Promise<void> {
  const owner = state.token;
  const tree = await meApi.menu(SELF_SYSTEM);
  if (owner !== state.token) return;
  const paths = new Set<string>();
  const buttons = new Set<string>();
  absorb(tree, paths, buttons);
  state.paths = paths;
  state.buttons = buttons;
}

/** 已授权的按钮权限码，例如 can('rbac.users.write')。 */
export function can(code: string): boolean {
  return state.buttons.has(code);
}

/** 能否进入某个菜单地址，例如 canVisit('/users')。 */
export function canVisit(path: string): boolean {
  return state.paths.has(path);
}

export const session = {
  state: readonly(state),

  setMenu(menu: string): void {
    state.menu = menu;
  },

  get loggedIn(): boolean {
    return !!state.token;
  },

  async login(username: string, password: string): Promise<void> {
    const token = await authApi.login({ username, password });
    state.token = token.accessToken;
    sessionStorage.setItem(TOKEN_KEY, token.accessToken);
    state.user = token.user;
    try {
      await loadAccess();
    } catch (error) {
      clearSession();
      throw error;
    }
    void this.loadInfo();
  },

  async restore(): Promise<void> {
    if (!state.token) {
      state.ready = true;
      return;
    }
    try {
      state.user = await meApi.profile();
      await loadAccess();
      void this.loadInfo();
    } catch {
      clearSession();
    } finally {
      state.ready = true;
    }
  },

  async loadInfo(): Promise<void> {
    // 运行模式只给有“工作台”的人看；其他人不去请求，免得在日志里留下一条 403
    if (!canVisit('/overview')) return;
    try {
      state.info = await systemApi.info();
    } catch {
      /* 模式信息取不到不阻塞界面 */
    }
  },

  logout(): void {
    clearSession();
  },
};
