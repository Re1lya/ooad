/** 控制台页面目录：导航分组、页码、标题与说明。菜单页的地址与 rbac 应用中登记的 menuPath 一致。 */
export interface PageInfo {
  name: string;
  path: string;
  title: string;
  caption: string;
  section: string;
  /** true：所有登录用户可见，不受菜单授权控制。 */
  personal?: boolean;
}

export const PAGES: PageInfo[] = [
  {
    name: 'overview',
    path: '/overview',
    title: '工作台',
    caption: '成员、角色、应用与最近的授权变化。',
    section: '工作空间',
  },
  {
    name: 'users',
    path: '/users',
    title: '用户管理',
    caption: '统一身份用户。登录名即工号，账号停用后不能登录任何接入应用。',
    section: '组织与成员',
  },
  {
    name: 'departments',
    path: '/departments',
    title: '部门管理',
    caption: '组织架构：分公司与部门两级，用户按部门归属。',
    section: '组织与成员',
  },
  {
    name: 'systems',
    path: '/systems',
    title: '应用管理',
    caption: '接入统一授权的业务应用。菜单、按钮与接口都登记在应用之下。',
    section: '权限与资源',
  },
  {
    name: 'roles',
    path: '/roles',
    title: '角色管理',
    caption: '用角色组织权限。角色之间可以继承，停用角色会切断经过它的继承。',
    section: '权限与资源',
  },
  {
    name: 'hierarchy',
    path: '/hierarchy',
    title: '角色继承',
    caption: '上层角色获得下层角色的全部权限。继承不能形成循环；停用的角色会切断经过它的路径。',
    section: '权限与资源',
  },
  {
    name: 'permissions',
    path: '/permissions',
    title: '菜单与按钮',
    caption: '各应用的菜单与按钮。用户能看到哪些菜单、能点哪些按钮，由这里的授权决定。',
    section: '权限与资源',
  },
  {
    name: 'resources',
    path: '/resources',
    title: 'API 资源',
    caption: '各应用的后端接口。接口绑定到菜单或按钮，并用权限码参与后端鉴权。',
    section: '权限与资源',
  },
  {
    name: 'grants',
    path: '/grants',
    title: '授权工作台',
    caption: '为角色勾选菜单与按钮。继承得来的权限一并显示，不能在这里取消。',
    section: '权限与资源',
  },
  {
    name: 'effective',
    path: '/effective',
    title: '有效权限',
    caption: '一个人最终拥有的全部权限，并标明每一项来自哪个角色、经过哪条继承。',
    section: '审计',
  },
  {
    name: 'audit',
    path: '/audit',
    title: '审计日志',
    caption: '各应用写入统一身份系统的关键操作记录。',
    section: '审计',
  },
  {
    name: 'mine',
    path: '/mine',
    title: '我的权限',
    caption: '你当前拥有的权限，以及每一项的来源。',
    section: '个人',
    personal: true,
  },
  {
    name: 'password',
    path: '/password',
    title: '修改密码',
    caption: '修改后，所有已登录的会话都需要重新登录。',
    section: '个人',
    personal: true,
  },
];

export const pageByName = (name: string): PageInfo | undefined =>
  PAGES.find((p) => p.name === name);
export const pageNumber = (name: string): string =>
  String(PAGES.findIndex((p) => p.name === name) + 1).padStart(2, '0');
