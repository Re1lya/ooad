/** 会话：令牌、当前用户、模式信息。令牌存 sessionStorage，关页即清。 */
import { reactive, readonly } from 'vue';
import { authApi, meApi, systemApi } from '@/api';
import { configureHttp } from '@/api/http';
import type { SystemInfo, UserView } from '@/api/types';

const TOKEN_KEY = 'rbac-console.token';

interface SessionState {
  token: string;
  user: UserView | null;
  info: SystemInfo | null;
  ready: boolean;
  /** 当前菜单名，写入 X-Client-Menu 供后端分层日志使用。
   *  头值必须是 ASCII（fetch 禁止非 ASCII 头值），所以存路由名英文，
   *  中文名只用于界面展示。 */
  menu: string;
}

const state = reactive<SessionState>({
  token: sessionStorage.getItem(TOKEN_KEY) ?? '',
  user: null,
  info: null,
  ready: false,
  menu: 'login',
});

configureHttp({
  token: () => state.token,
  menu: () => state.menu,
  unauthorized: () => clearSession(),
});

function clearSession(): void {
  state.token = '';
  state.user = null;
  sessionStorage.removeItem(TOKEN_KEY);
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
    void this.loadInfo();
  },

  async restore(): Promise<void> {
    if (!state.token) {
      state.ready = true;
      return;
    }
    try {
      state.user = await meApi.profile();
      void this.loadInfo();
    } catch {
      clearSession();
    } finally {
      state.ready = true;
    }
  },

  async loadInfo(): Promise<void> {
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
