import { createRouter, createWebHashHistory } from 'vue-router';
import { session } from '@/stores/session';

/** meta.menu 会写入每个请求的 X-Client-Menu 头，供后端按菜单分层记日志。 */
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
        { path: '', redirect: '/overview' },
        {
          path: 'overview',
          name: 'overview',
          component: () => import('@/views/OverviewView.vue'),
          meta: { menu: '总览' },
        },
        {
          path: 'users',
          name: 'users',
          component: () => import('@/views/UsersView.vue'),
          meta: { menu: '用户' },
        },
        {
          path: 'departments',
          name: 'departments',
          component: () => import('@/views/DepartmentsView.vue'),
          meta: { menu: '部门' },
        },
        {
          path: 'systems',
          name: 'systems',
          component: () => import('@/views/SystemsView.vue'),
          meta: { menu: '应用' },
        },
        {
          path: 'roles',
          name: 'roles',
          component: () => import('@/views/RolesView.vue'),
          meta: { menu: '角色' },
        },
        {
          path: 'hierarchy',
          name: 'hierarchy',
          component: () => import('@/views/HierarchyView.vue'),
          meta: { menu: '角色继承' },
        },
        {
          path: 'permissions',
          name: 'permissions',
          component: () => import('@/views/PermissionsView.vue'),
          meta: { menu: '前端权限' },
        },
        {
          path: 'resources',
          name: 'resources',
          component: () => import('@/views/ResourcesView.vue'),
          meta: { menu: 'API资源' },
        },
        {
          path: 'grants',
          name: 'grants',
          component: () => import('@/views/GrantsView.vue'),
          meta: { menu: '用户授权' },
        },
        {
          path: 'effective',
          name: 'effective',
          component: () => import('@/views/EffectiveView.vue'),
          meta: { menu: '权限来源' },
        },
        {
          path: 'directory',
          name: 'directory',
          component: () => import('@/views/DirectorySyncView.vue'),
          meta: { menu: '员工同步' },
        },
        {
          path: 'audit',
          name: 'audit',
          component: () => import('@/views/AuditView.vue'),
          meta: { menu: '审计日志' },
        },
        {
          path: 'mine',
          name: 'mine',
          component: () => import('@/views/MineView.vue'),
          meta: { menu: '个人中心' },
        },
      ],
    },
    { path: '/:pathMatch(.*)*', redirect: '/overview' },
  ],
});

router.beforeEach((to) => {
  if (!to.meta.public && !session.loggedIn) return { name: 'login', query: { to: to.fullPath } };
  if (to.name === 'login' && session.loggedIn) return { path: '/overview' };
  // X-Client-Menu 必须是 ASCII：用英文路由名，中文菜单名仅用于界面展示。
  session.setMenu(String(to.name ?? 'unknown'));
  return true;
});

export default router;
