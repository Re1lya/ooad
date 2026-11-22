# Cdwy 统一权限管理系统

面向对象分析与设计课程项目，围绕企业统一权限治理实现一套可配置、可追溯的 RBAC1 权限管理系统。系统把用户、部门、应用、角色、菜单、按钮和 API 资源组织成完整授权链，并提供结构化设计与面向对象设计两种后端实现。

**小组：Cdwy**
**成员：温家正、姚景云、杜奕含、陈浩贤**

> GitHub 页面展示的是项目说明和静态截图，不会启动 Spring Boot，也不能直接操作系统。要使用真正的前端，请先按“启动系统”运行后端，再打开下面的访问地址。

## 你应该先看哪里

| 目标 | 入口 |
|---|---|
| 直接进入权限台账前端 | `http://127.0.0.1:18081/access-registry/index.html` |
| 查看原管理控制台 | `http://127.0.0.1:18081/ui/index.html` |
| 了解 Vue 前端构建 | [`frontend-access-registry/README.md`](frontend-access-registry/README.md) |
| 了解 API 设计 | [`docs/first-review/api-design.md`](docs/first-review/api-design.md) |
| 了解数据库与 ER 图 | [`docs/first-review/database-design.md`](docs/first-review/database-design.md) |
| 了解第二阶段交付物 | [`docs/second-review/README.adoc`](docs/second-review/README.adoc) |
| 按角色阅读项目 | [`docs/team-guide.md`](docs/team-guide.md) |

## 当前版本完成了什么

- 账号登录、JWT 认证、当前用户信息和修改密码。
- 用户、部门、接入应用、角色、菜单、按钮和 API 资源管理。
- 用户分配角色，角色授予菜单、按钮和 API 权限，并支持撤回授权。
- RBAC1 角色继承，支持多级继承和多继承，阻止自继承与循环继承。
- 权限来源查询，可以说明一项有效权限来自哪个角色或继承链。
- 员工信息同步的 Excel 预览、校验、变化摘要和确认导入流程。
- 审计日志查询，记录登录、授权、撤权、导入和关键管理操作。
- 同一套 HTTP API、数据库和 Vue 前端下的结构化与面向对象双后端实现。

## 前端入口

仓库里有两个可访问的界面，它们由同一个 Spring Boot 服务提供：

| 页面 | 地址 | 用途 |
|---|---|---|
| 权限台账 Vue 前端 | `http://127.0.0.1:18081/access-registry/index.html` | 当前整合的现代化管理台，覆盖工作台、用户、部门、应用、角色、继承、权限、API 资源、授权和审计 |
| 原管理控制台 | `http://127.0.0.1:18081/ui/index.html` | 仓库原有的管理页面，用于兼容和对照 |

下面的图片只用于说明视觉布局，不能替代运行中的页面：

![结构化实现下的权限台账前端静态预览](docs/design/phase-two/full-dual-structured/overview.png)

如果你在 GitHub 上只看到这张图，说明你当前停留在 README 页面；这不是前端没有更新。启动服务后访问上面的 `127.0.0.1` 地址，才会进入可登录、可点击、会调用 API 的页面。

## 启动系统

### 环境要求

- JDK 21
- Maven 3.9，或仓库内的 `mvnw.cmd`
- Node.js 20.19+ 或 22.12+（只有重新构建前端时需要）
- Windows PowerShell

### 构建后端和原管理控制台

在 PowerShell 中执行：

```powershell
git clone https://github.com/yyyjjjj949/ooad.git
cd ooad
.\scripts\build-first-review.ps1
```

构建脚本会安装仓库内的 CloneFactory 依赖、构建 `frontend/` 中的原管理控制台，并执行 Maven `clean verify`。权限台账 Vue 前端的开发与构建命令见下一节。JaCoCo 报告位于 `target/site/jacoco/index.html`。

### 启动并打开权限台账

```powershell
.\scripts\start-first-review.ps1 -AdminUsername admin -Port 18081
```

启动脚本会在当前终端要求你输入本次演示的管理员密码。密码不会写进仓库，也没有固定的公共初始密码。启动成功后，在浏览器打开权限台账：

```text
http://127.0.0.1:18081/access-registry/index.html
```

也可以打开原管理控制台：

```text
http://127.0.0.1:18081/ui/index.html
```

演示默认使用 H2 内存数据库，停止服务后数据会清空。运行期间请保持启动窗口打开，结束时在该窗口按 `Ctrl+C`。

### 单独开发 Vue 前端

先启动后端，再打开另一个 PowerShell 窗口：

```powershell
cd frontend-access-registry
npm ci
npm run dev
```

开发地址是 `http://127.0.0.1:5173`，前端的 `/api` 请求默认代理到 `http://127.0.0.1:18081`。完成修改后，使用下面的命令把生产资源写入 Spring Boot 静态目录：

```powershell
npm run build:backend
```

之后重新构建并启动后端即可。

## 结构化与面向对象双设计

启动时通过 `PERMISSION_BACKEND_STRATEGY` 选择后端实现，默认使用结构化实现。两种实现共用登录、HTTP API、数据库表和前端页面，业务结果保持一致。

| 实现 | 组织方式 | 典型调用链 |
|---|---|---|
| 结构化 `structured` | Service 按事务脚本组织步骤，直接协调持久化操作 | Controller → UseCase → Structured Service → Repository → 数据库 |
| 面向对象 `ddd` | 应用服务协调领域对象和仓储端口，持久化由适配器完成 | Controller → UseCase → DDD Application Service → Domain / Repository Port → JPA Adapter → 数据库 |

切换到面向对象实现：

```powershell
$env:PERMISSION_BACKEND_STRATEGY = 'ddd'
.\scripts\start-first-review.ps1 -AdminUsername admin -Port 18081
```

使用结构化实现时设置为 `structured`，或清除该环境变量后重新启动。可以通过 `GET /api/system-info` 查看当前装载的策略。

## RBAC1 权限模型

系统按下面的授权关系计算有效权限：

```text
用户 → 用户角色关系 → 角色 → 角色继承 → 菜单 / 按钮 / API 资源
```

角色继承只扩大可计算的权限来源，不直接复制权限记录。系统在写入继承关系时检查自继承和环，查询有效权限时沿继承链汇总并去重。这样既能表达“管理员继承普通管理员”的层次，也能保留每条授权的来源，便于审计和撤回。

## API、数据库与设计材料

- [`docs/first-review/api-design.md`](docs/first-review/api-design.md)：接口用途、资源关系、请求参数和设计理由。
- [`docs/first-review/openapi.json`](docs/first-review/openapi.json)：可导入 Apifox 的机器可读接口描述。
- [`docs/first-review/database-design.md`](docs/first-review/database-design.md)：实体、属性、关系、约束和索引。
- [物理 ER 图](docs/design/images/er-physical.png) · [概念 ER 图](docs/design/images/er-core.png)。
- [`docs/design/README.md`](docs/design/README.md)：类图、顺序图、结构图、ER 图及可编辑来源。
- [`docs/second-review/01-requirements.adoc`](docs/second-review/01-requirements.adoc)：需求规格与功能/非功能需求。
- [`docs/second-review/03-detailed-design.adoc`](docs/second-review/03-detailed-design.adoc)：架构、模块、类图和实现说明。
- [`docs/second-review/04-test-traceability.adoc`](docs/second-review/04-test-traceability.adoc)：需求与测试追踪关系。
- [`docs/second-review/05-deployment.adoc`](docs/second-review/05-deployment.adoc)：部署与环境配置说明。

## 技术栈

| 层次 | 技术 |
|---|---|
| 后端 | Java 21、Spring Boot、Spring MVC、Spring Security、Bean Validation |
| 持久化 | Spring Data JPA、Hibernate；演示使用 H2，部署使用 MySQL |
| 认证 | JWT、BCrypt |
| 对象映射 | CloneFactory、Lombok |
| 前端 | Vue 3、TypeScript、Vite、Vue Router |
| 测试 | JUnit、Maven Surefire、JaCoCo |

## 仓库结构

```text
frontend/                                  原管理控制台前端
frontend-access-registry/                  整合后的 Vue 权限台前端源码
src/main/java/                             Spring Boot 后端源码
src/main/resources/static/ui/              原管理控制台静态资源
src/main/resources/static/access-registry/ 权限台前端生产构建资源
src/test/                                  后端测试
api/                                       API 契约和 OpenAPI 材料
docs/                                      需求、设计、数据库和验收文档
scripts/                                   构建、启动和验证脚本
deploy/                                    Docker 部署配置
third-party/clonefactory/                  CloneFactory 源码与构建文件
```

## 数据与安全

演示账号由启动脚本创建，密码由运行者输入。不要把密码、JWT、密钥或包含真实员工信息的 Excel 文件提交到仓库；员工数据应由授权管理员在运行中的系统里完成导入。部署环境的数据库连接、密钥和账号凭据应通过环境变量或 Docker Secret 提供。

## 许可证与课程用途

本仓库用于 Cdwy 小组课程设计、检查和答辩演示。第三方依赖遵循各自许可证，CloneFactory 相关源码和构建文件保存在 `third-party/clonefactory/`。
