# Cdwy 组员阅读指南

这份仓库包含统一权限管理系统当前版本的前后端代码、两种后端设计、测试、设计图和课程检查材料。

## 建议阅读顺序

1. 先读[首页](../README.md)，了解业务范围和运行方式。
   第二次检查材料（adoc，含需求、数据库、详细设计、测试追踪、部署）见[second-review](second-review/README.adoc)。
2. 读[全业务双设计](phase-two/full-dual-design.md)，核对十组用例及 structured / ddd 对照。
3. 读[需求](first-review/requirements.md)、[API](first-review/api-design.md)和[数据库](first-review/database-design.md)，串起“需求 → 资源 → 接口 → 服务 → 表”。
4. 看[图与截图](design/README.md)，再打开具体类和方法。
5. 按首页启动程序，用[演示步骤](first-review/demo.md)验证授权与撤权。

## 每个板块从哪里进入

| 板块 | 文档 | 关键代码 |
|---|---|---|
| 数据库 | [模型与约束](first-review/database-design.md)、[物理ER](design/images/er-physical.png) | [JPA实体](../src/main/java/com/cdwy/permission/entity)、[仓储适配器](../src/main/java/com/cdwy/permission/infrastructure/persistence) |
| API | [接口设计](first-review/api-design.md)、[OpenAPI](first-review/openapi.json) | [Controller](../src/main/java/com/cdwy/permission/controller)、[HTTP映射](../src/main/java/com/cdwy/permission/controller/common/HttpMapping.java) |
| 架构与双设计 | [架构](first-review/architecture.md)、[双设计](phase-two/full-dual-design.md) | [用例端口](../src/main/java/com/cdwy/permission/service/port)、[DDD](../src/main/java/com/cdwy/permission/service/ddd) |
| RBAC1 | [面向对象设计](phase-two/object-oriented-design.md)、[RBAC1类图](design/images/class-rbac1.png) | [继承结构化服务](../src/main/java/com/cdwy/permission/service/rbac/RoleHierarchyService.java)、[角色图对象](../src/main/java/com/cdwy/permission/service/ddd/domain/model/RoleGraph.java)、[授权快照](../src/main/java/com/cdwy/permission/service/ddd/domain/model/AuthorizationSnapshot.java) |
| 前端 | [交互说明](phase-two/frontend-interaction.md) | [页面](../frontend/src/App.vue)、[工作区组件](../frontend/src/components)、[交互逻辑](../frontend/src/console.ts) |
| 导入 | [双实现与导入](phase-two/dual-design-and-import.md) | [格式解析](../src/main/java/com/cdwy/permission/service/importing/CompanyWorkbookParser.java)、[DDD导入](../src/main/java/com/cdwy/permission/service/ddd/application/importing/DddCompanyImportService.java) |
| 验证 | [测试结果](first-review/testing.md) | [后端测试](../src/test/java/com/cdwy/permission)、[浏览器场景](../frontend/tests) |

## 下载后不启动程序也能看

浏览器打开[全业务双设计说明](review/全业务双设计说明.html)，或[双设计源码阅读](review/双设计源码阅读.html)。请保留整个目录结构；页面中的图片、证据和源码链接使用相对路径。GitHub 文件页展示 HTML 源文件，离线页面需要在下载后打开。

## 协作约定

- 先同步仓库，再为自己的任务建立分支并提交，避免多人覆盖同一文件。
- 变更业务规则时先验证有意义的失败测试，再实现和回归；已有行为补测直接通过应如实记录。
- 改接口时同步 DTO/VO、OpenAPI、需求映射和相关测试；改图优先修改生成脚本再生成 PNG/SVG。
- 工作树中默认没有员工原始表、已导入数据库、公共管理员密码或 JWT 密钥；需要公司数据时由小组在授权范围内另行传递。
- `127.0.0.1` 只能访问自己电脑的实例；仓库地址用于分享代码，不代表已经部署了在线系统。
