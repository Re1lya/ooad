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

## 对接后端

运行 `npm run build:backend` 更新 `src/main/resources/static/access-registry/`，然后重新打包后端。
启动 Spring Boot 后打开 `http://127.0.0.1:18081/access-registry/index.html`。

所有请求自动携带：

- `Authorization: Bearer <token>`（登录后）
- `X-Client-Menu: <英文路由名>`（供后端 logback 分层日志按菜单标记；头值必须 ASCII，故用路由名而非中文菜单名）

## 页面与主要后端接口

| 页面 | 主要接口 |
|---|---|
| 总览 | `/api/system-info`、各列表计数 |
| 用户 | `/api/users` CRUD、启停、设密、权限来源 |
| 部门 | `/api/departments` 树形 CRUD |
| 接入应用 | `/api/systems` CRUD |
| 角色 | `/api/roles` CRUD + 直接权限授予 |
| 继承图谱 | `/api/role-hierarchy` SVG 有向图 + 立/解继承 |
| 前端权限 | `/api/permissions` 树形 CRUD + 绑定 API 资源 |
| API 资源 | `/api/resources` CRUD |
| 用户授权 | `/api/users/{id}/roles` 盖印式授/收 |
| 权限来源 | `/api/roles/{id}/effective-permissions` 授权链拆解 |
| 员工同步 | `/api/directory/sync/preview → commit`（摘要确认两段式） |
| 审计日志 | `/api/audit-logs` 过滤分页 + 前后对比 |
| 个人中心 | `/api/me` 资料、有效权限、改密 |

## 技术栈

Vue 3.5 + TypeScript + Vite 7 + vue-router 4，无其他运行时依赖。
`src/styles/main.css` 为全部设计 token（配色/字体/表格/印章/弹层），改主题只动这一个文件。
