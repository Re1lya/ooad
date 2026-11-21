import { createRouter, createWebHashHistory, type RouteLocationRaw } from 'vue-router';
import { StaleSessionError } from '@/api/http';
import { canVisit, can, loadIdentity, logout, session } from '@/stores/session';
import { report, ui } from '@/stores/ui';
import { PAGES, pageByName } from './pages';

declare module 'vue-router' {
  interface RouteMeta {
    public?: boolean;
    /** 页码与导航高亮所属的页面（子页面沿用父页面）。 */
    page?: string;
    /** 进入该页还需要的按钮权限。 */
    button?: string;
  }
}

const views: Record<string, () => Promise<unknown>> = {
  overview: () => import('@/views/OverviewView.vue'),
  users: () => import('@/views/UsersView.vue'),
  departments: () => import('@/views/DepartmentsView.vue'),
  systems: () => import('@/views/SystemsView.vue'),
  roles: () => import('@/views/RolesView.vue'),
  hierarchy: () => import('@/views/HierarchyView.vue'),
  permissions: () => import('@/views/PermissionsView.vue'),
  resources: () => import('@/views/ResourcesView.vue'),
  grants: () => import('@/views/GrantsView.vue'),
  effective: () => import('@/views/EffectiveView.vue'),
  audit: () => import('@/views/AuditView.vue'),
  mine: () => import('@/views/MineView.vue'),
  password: () => import('@/views/PasswordView.vue'),
};

export const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/LoginView.vue'),
      meta: { public: true },
    },
    ...PAGES.map((page) => ({
      path: page.path,
      name: page.name,
      component: views[page.name],
      meta: { page: page.name },
    })),
    {
      path: '/users/import',
      name: 'import',
      component: () => import('@/views/ImportView.vue'),
      meta: { page: 'users', button: 'rbac.directory.sync' },
    },
    { path: '/:rest(.*)*', redirect: () => firstAllowed() },
  ],
});

/** 当前身份可以进入的页面。 */
export function allowed(name: string): boolean {
  const page = pageByName(name);
  if (!page) return false;
  return page.personal === true || canVisit(page.path);
}

export function firstAllowed(): RouteLocationRaw {
  const page = PAGES.find((p) => allowed(p.name));
  return { name: page?.name ?? 'mine' };
}

router.beforeEach(async (to) => {
  session.menu = String(to.name ?? '');
  if (session.token && !session.me) {
    ui.pending++;
    try {
      await loadIdentity();
    } catch (error) {
      if (!(error instanceof StaleSessionError)) {
        logout();
        report(error);
      }
    } finally {
      ui.pending--;
    }
  }
  if (to.meta.public) return session.me ? firstAllowed() : true;
  if (!session.me) return { name: 'login' };
  const page = to.meta.page ?? String(to.name);
  if (!allowed(page) || (to.meta.button && !can(to.meta.button))) return firstAllowed();
  return true;
});

// 重新部署后旧页面引用的分块已不存在：整页重新加载到目标地址，取到新版本。
router.onError((error, to) => {
  const message = String((error as Error)?.message ?? error);
  if (
    /dynamically imported module|Importing a module script failed|error loading dynamically/i.test(
      message,
    )
  ) {
    // 10 秒内只重载一次，避免资源确实缺失时反复刷新。
    const last = Number(sessionStorage.getItem('ui-reloaded-at') ?? 0);
    if (Date.now() - last < 10_000) return;
    sessionStorage.setItem('ui-reloaded-at', String(Date.now()));
    window.history.replaceState(null, '', `${window.location.pathname}#${to.fullPath}`);
    window.location.reload();
  }
});
