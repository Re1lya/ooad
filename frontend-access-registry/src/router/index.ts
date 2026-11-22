import { createRouter, createWebHashHistory, type RouteLocationRaw } from 'vue-router';
import { can, canVisit, session } from '@/stores/session';

/**
 * meta.menu 是界面上的中文菜单名；路由名（英文）写入每个请求的 X-Client-Menu 头，供后端按菜单分层记日志。
 * meta.path 是该页在 rbac 应用中登记的菜单地址，meta.button 是不单独登记菜单、按按钮授权进入的页面，
 * meta.personal 是任何已登录的人都能进的个人页。
 */
declare module 'vue-router' {
  interface RouteMeta {
    menu?: string;
    public?: boolean;
    path?: string;
    button?: string;
    personal?: boolean;
  }
}

const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/LoginView.vue'),
      meta: { menu: '登录', public: true },
    },
    {
      path: '/',
      component: () => import('@/components/AppShell.vue'),
      children: [
        { path: '', redirect: () => firstAllowed() },
        {
          path: 'overview',
          name: 'overview',
          component: () => import('@/views/OverviewView.vue'),
          meta: { menu: '总览', path: '/overview' },
        },
        {
          path: 'users',
          name: 'users',
          component: () => import('@/views/UsersView.vue'),
          meta: { menu: '用户', path: '/users' },
        },
        {
          path: 'departments',
          name: 'departments',
          component: () => import('@/views/DepartmentsView.vue'),
          meta: { menu: '部门', path: '/departments' },
        },
        {
          path: 'systems',
          name: 'systems',
          component: () => import('@/views/SystemsView.vue'),
          meta: { menu: '接入应用', path: '/systems' },
        },
        {
          path: 'roles',
          name: 'roles',
          component: () => import('@/views/RolesView.vue'),
          meta: { menu: '角色', path: '/roles' },
        },
        {
          path: 'hierarchy',
          name: 'hierarchy',
          component: () => import('@/views/HierarchyView.vue'),
          meta: { menu: '继承图谱', path: '/hierarchy' },
        },
        {
          path: 'permissions',
          name: 'permissions',
          component: () => import('@/views/PermissionsView.vue'),
          meta: { menu: '前端权限', path: '/permissions' },
        },
        {
          path: 'resources',
          name: 'resources',
          component: () => import('@/views/ResourcesView.vue'),
          meta: { menu: 'API 资源', path: '/resources' },
        },
        {
          path: 'grants',
          name: 'grants',
          component: () => import('@/views/GrantsView.vue'),
          meta: { menu: '用户授权', path: '/grants' },
        },
        {
          path: 'effective',
          name: 'effective',
          component: () => import('@/views/EffectiveView.vue'),
          meta: { menu: '权限来源', path: '/effective' },
        },
        {
          path: 'directory',
          name: 'directory',
          component: () => import('@/views/DirectorySyncView.vue'),
          meta: { menu: '员工同步', button: 'rbac.directory.sync' },
        },
        {
          path: 'audit',
          name: 'audit',
          component: () => import('@/views/AuditView.vue'),
          meta: { menu: '审计日志', path: '/audit' },
        },
        {
          path: 'mine',
          name: 'mine',
          component: () => import('@/views/MineView.vue'),
          meta: { menu: '个人中心', personal: true },
        },
      ],
    },
    { path: '/:pathMatch(.*)*', redirect: () => firstAllowed() },
  ],
});

/** 按本人授权判断能否进入某个路由。 */
export function allowed(name: string): boolean {
  const route = router.getRoutes().find((r) => r.name === name);
  if (!route) return false;
  const meta = route.meta;
  if (meta.public || meta.personal) return true;
  if (meta.button) return can(meta.button);
  return !!meta.path && canVisit(meta.path);
}

/** 侧栏顺序里第一个有权限的页面；一个菜单都没有时进个人中心。 */
export function firstAllowed(): RouteLocationRaw {
  const order = ['overview', 'users', 'departments', 'systems', 'roles', 'hierarchy', 'permissions'];
  const rest = ['resources', 'grants', 'effective', 'directory', 'audit'];
  const name = [...order, ...rest].find(allowed);
  return { name: name ?? 'mine' };
}

router.beforeEach((to) => {
  if (!to.meta.public && !session.loggedIn) return { name: 'login', query: { to: to.fullPath } };
  if (to.name === 'login' && session.loggedIn) return firstAllowed();
  if (to.name && !allowed(String(to.name))) return firstAllowed();
  // X-Client-Menu 必须是 ASCII：用英文路由名，中文菜单名仅用于界面展示。
  session.setMenu(String(to.name ?? 'unknown'));
  return true;
});

export default router;
