# 权限台账 · 统一权限管理系统前端

统一权限管理系统的 Vue 3 管理台，登录与业务操作使用本仓库的 Spring Boot API。
设计语言：**印鉴台账** —— 宣纸底、墨字、朱砂授权、细横线台账表格、宋体题字、等宽编号。

## 快速开始

```bash
npm ci
npm run dev        # http://127.0.0.1:5173，/api 代理到 http://127.0.0.1:18081
npm run build      # 类型检查 + 产物输出到 dist/
```

后端地址不同时：`VITE_API_TARGET=http://x.x.x.x:port npm run dev`。
登录使用后端已有账号。运行本地演示时，使用后端启动脚本设置的管理员账号和密码。

## 对接后端与部署

运行 `npm run build:backend` 更新 `src/main/resources/static/access-registry/`，然后重新打包后端。
本地启动 Spring Boot 后打开 `http://127.0.0.1:18081/access-registry/index.html`。

部署时由权限系统前端镜像（Nginx）发布在 `/access-registry/`，站点首页跳到这里；原管理控制台仍在 `/ui/`。
`deploy/permission/package-images.sh` 会依次构建两个前端，再打包后端。

所有请求自动携带：

- `Authorization: Bearer <token>`（登录后）
- `X-Client-Menu: <英文路由名>`（供后端 logback 分层日志按菜单标记；头值必须 ASCII，故用路由名而非中文菜单名）

令牌只存在当前标签页；令牌过期、改密或账号被停用后，下一次请求返回 401，界面清掉会话回到登录页。

## 台账本身也由统一授权管理

权限系统在自己的 `rbac` 应用下登记了控制台的菜单与按钮（后端启动时幂等登记）。登录后读取
`/api/me/menu?system=rbac`：侧栏只列本人有权进入的页面，写操作按钮按按钮授权显示，直接输入无权页面的地址会转回第一个有权限的页面。

| 页面 | 进入所需菜单 | 写操作所需按钮 |
|---|---|---|
| 总览 | `rbac.overview` | — |
| 用户 | `rbac.users` | 登记、改资料、启停、重设密码、注销：`rbac.users.write` |
| 部门 | `rbac.departments` | `rbac.departments.write` |
| 接入应用 | `rbac.systems` | `rbac.systems.write` |
| 角色 | `rbac.roles` | 建、改、启停、注销：`rbac.roles.write`；配直接权限：`rbac.grants.write` |
| 继承图谱 | `rbac.hierarchy` | `rbac.hierarchy.write` |
| 前端权限 | `rbac.permissions` | `rbac.permissions.write`；绑定 API：`rbac.resources.write` |
| API 资源 | `rbac.resources` | `rbac.resources.write` |
| 用户授权 | `rbac.grants` | 给人加减角色：`rbac.users.write`（左侧挑人要能读用户列表） |
| 权限来源 | `rbac.effective` | —（按角色查需要能进入继承图谱或用户授权） |
| 员工同步 | 按钮 `rbac.directory.sync` | 同左 |
| 审计日志 | `rbac.audit` | — |
| 个人中心 | 任何已登录的人 | 改本人密码 |

权限系统自身的内置对象——`rbac` 应用、`rbac-admin` 角色、`rbac.` 开头的菜单与按钮、`rbac` 应用下的管理接口——只显示“内置”，
不提供停用和注销（后端同样拒绝，返回 `CONSOLE_BUILT_IN`），以免所有人都进不了控制台。

## 页面与主要后端接口

| 页面 | 主要接口 |
|---|---|
| 总览 | `/api/system-info`、各列表计数 |
| 用户 | `/api/users` 分页检索、CRUD、启停、设密；正式员工长期有效，外部、临时人员必须设有效期 |
| 部门 | `/api/departments` 树形 CRUD，部门编码可留空按编号生成 |
| 接入应用 | `/api/systems` CRUD |
| 角色 | `/api/roles` CRUD + 直接权限授予 |
| 继承图谱 | `/api/role-hierarchy` SVG 有向图 + 立/解继承 |
| 前端权限 | `/api/permissions` 树形 CRUD + 绑定 API 资源 |
| API 资源 | `/api/resources` 登记方法与路径，可带后端权限码与 operationId |
| 用户授权 | `/api/users/{id}/roles` 盖印式授/收 |
| 权限来源 | 按人员：`/api/users/{id}/permission-origins`；按角色：`/api/roles/{id}/effective-permissions`；继承路径写角色名 |
| 员工同步 | `/api/directory/sync/preview → commit`（员工信息表，摘要确认两段式） |
| 审计日志 | `/api/audit-logs` 按应用查看、按目标检索、前后对比 |
| 个人中心 | `/api/me` 资料、有效权限、改密 |

## 浏览器测试

`tests/*.test.cjs` 用 Playwright 驱动真实浏览器，覆盖控制台授权、内置对象、人员与部门、API 资源、授权与权限来源、员工同步与审计。
测试会写入合成数据，只在声明为隔离实例的后端上运行：

```bash
PHASE_TWO_DEMO_ACK=isolated-h2 PHASE_TWO_URL=http://127.0.0.1:18081 \
PHASE_TWO_ADMIN_USERNAME=<管理员> PHASE_TWO_ADMIN_PASSWORD=<密码> \
CHROME_PATH=<Chrome 或 Edge 可执行文件> npm run test:e2e
```

页面不在后端的 `/access-registry/` 时（例如用 Vite 开发服务器），用 `REGISTRY_URL` 指定页面地址；`PHASE_TWO_OUTPUT` 指定截图目录。

## 技术栈

Vue 3.5 + TypeScript + Vite 7 + vue-router 4，无其他运行时依赖；浏览器测试用 playwright-core 与 node:test。
`src/styles/main.css` 为全部设计 token（配色/字体/表格/印章/弹层），改主题只动这一个文件。
