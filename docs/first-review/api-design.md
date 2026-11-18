# API 设计（RBAC1 增强版）

接口清单从通过验收测试的 Spring MVC 路由导出，与 [OpenAPI](openapi.json) 同步生成。当前 61 个 HTTP 操作。

## 约定

- 基址：`http://127.0.0.1:18081`。普通接口为JSON，三表导入为multipart/form-data；登录外均使用 `Authorization: Bearer <accessToken>`。
- 管理员身份只配置系统；业务接口仍需用户角色→角色权限→权限API绑定。
- GET读取；POST创建返回201；PUT替换资料/独立状态或幂等建立关联；DELETE删除/解除关联返回204。
- 普通编辑不接受状态字段。用户、角色、应用为`enabled`；前端权限、API资源为`active`。
- 400：参数、JSON或业务规则错误；401：凭证无效；403：身份或权限不足；404：资源不存在；409：重复或引用冲突。
- 错误体为`{"error":"Conflict","message":"说明"}`；列表为空返回空数组，分页用户返回`items/total/page/size`。
- 登录响应包含`accessToken/tokenType/expiresIn/user`。退出在前端删除Token；没有服务端注销、刷新或会话管理API。
- 用户`page`从0开始，`size`范围1..100，默认25；权限列表与树可用`systemId`筛选。

## 资源与接口必要性

| 方法 | 路径 | 含义／必要性 | 输入DTO | 成功响应 | 访问主体 | 源码 |
|---|---|---|---|---|---|---|
| GET | `/api/apps/{systemCode}/auth/audit-logs` | 查询接入应用审计记录：管理员或在该应用内拥有有效权限的用户可查询；按操作人、对象、时间筛选，时间倒序分页。 | — | 200 | 已登录 | [ApplicationIdentityController.logs](../../src/main/java/com/cdwy/permission/controller/identity/ApplicationIdentityController.java) |
| POST | `/api/apps/{systemCode}/auth/audit-logs` | 写入接入应用审计记录：接入应用在业务事务提交后写入；requestId 幂等，操作人取自JWT并保存姓名与角色快照。 | AuditWrite | 201 | 已登录 | [ApplicationIdentityController.write](../../src/main/java/com/cdwy/permission/controller/identity/ApplicationIdentityController.java) |
| GET | `/api/apps/{systemCode}/auth/me` | 接入应用查询当前身份：课程系统等接入应用携带用户本人JWT调用；返回有效角色编码（含继承）与该应用内菜单、按钮权限码及其绑定的后端API权限码，授权变化下一次调用即生效。 | — | 200 | 已登录 | [ApplicationIdentityController.me](../../src/main/java/com/cdwy/permission/controller/identity/ApplicationIdentityController.java) |
| GET | `/api/apps/{systemCode}/identity/users/{userId}` | 接入应用按统一身份查人：按登录名查询姓名与工号，供课程系统登记教师；应用或用户不存在返回404。 | — | 200 | 已登录 | [ApplicationIdentityController.user](../../src/main/java/com/cdwy/permission/controller/identity/ApplicationIdentityController.java) |
| GET | `/api/audit-logs` | 查看审计记录：管理员按应用编码查看审计记录；本系统角色继承变更使用应用编码 rbac。 | — | 200 | 管理员 | [AuditLogController.list](../../src/main/java/com/cdwy/permission/controller/audit/AuditLogController.java) |
| POST | `/api/auth/login` | 账号密码登录：验证当前账号和密码后签发JWT；不创建登录会话，不在Token保存权限列表。 | Login | 200 | 公开 | [AuthController.login](../../src/main/java/com/cdwy/permission/controller/auth/AuthController.java) |
| GET | `/api/demo/courses` | 课程API鉴权示例：仅为示例静态课程；需登记course应用的GET /api/demo/courses资源、绑定权限并分配角色。 | — | 200 | 业务授权 | [CourseDemoController.list](../../src/main/java/com/cdwy/permission/controller/demo/CourseDemoController.java) |
| GET | `/api/departments` | 查询部门列表：查询部门资源；用户分页，其余为当前小规模演示的完整列表。 | — | 200 | 管理员 | [DepartmentController.list](../../src/main/java/com/cdwy/permission/controller/organization/DepartmentController.java) |
| POST | `/api/departments` | 创建部门：创建独立部门记录，默认启用；不隐式创建授权关系。 | DepartmentInput | 201 | 管理员 | [DepartmentController.create](../../src/main/java/com/cdwy/permission/controller/organization/DepartmentController.java) |
| DELETE | `/api/departments/{id}` | 删除部门：删除该资源；除用户会清理其用户角色关系外，关联引用需要先解除，否则409。 | — | 204 | 管理员 | [DepartmentController.delete](../../src/main/java/com/cdwy/permission/controller/organization/DepartmentController.java) |
| PUT | `/api/departments/{id}` | 编辑部门资料：修改资料；创建时的身份标识保持固定，enabled/active不在此DTO，夹带将返回400。 | DepartmentInput | 200 | 管理员 | [DepartmentController.update](../../src/main/java/com/cdwy/permission/controller/organization/DepartmentController.java) |
| POST | `/api/imports/company/commit` | 确认导入公司三表：管理员上传员工、部门、功能权限三张XLSX；核对原文件摘要后事务导入；自然键复用、来源字段保留；不自动启用账号或授予个人角色。 | ImportCommit | 200 | 管理员 | [CompanyImportController.commit](../../src/main/java/com/cdwy/permission/controller/importing/CompanyImportController.java) |
| POST | `/api/imports/company/preview` | 预览并校验公司三表：管理员上传员工、部门、功能权限三张XLSX；只校验并返回摘要，不写入。 | ImportPreview | 200 | 管理员 | [CompanyImportController.preview](../../src/main/java/com/cdwy/permission/controller/importing/CompanyImportController.java) |
| GET | `/api/me` | 查询本人资料：主体取自Token，不允许指定他人ID。 | — | 200 | 已登录 | [MeController.me](../../src/main/java/com/cdwy/permission/controller/me/MeController.java) |
| GET | `/api/me/menu` | 查询本人导航树：为直接授权节点补齐祖先导航容器；granted=false的容器不获得接口权限。 | — | 200 | 已登录 | [MeController.menu](../../src/main/java/com/cdwy/permission/controller/me/MeController.java) |
| PUT | `/api/me/password` | 修改本人密码：验证原密码，更新BCrypt摘要与凭证版本；全部旧Token失效，需重新登录。 | PasswordChange | 204 | 已登录 | [MeController.password](../../src/main/java/com/cdwy/permission/controller/me/MeController.java) |
| GET | `/api/me/permission-origins` | 查询本人权限来源：只读取当前身份的权限来源，不接受其他用户ID。 | — | 200 | 已登录 | [RoleHierarchyController.me](../../src/main/java/com/cdwy/permission/controller/rbac/RoleHierarchyController.java) |
| GET | `/api/me/permissions` | 查询本人有效权限：按有效用户角色与角色权限取并集，去重并实时过滤停用对象。 | — | 200 | 已登录 | [MeController.permissions](../../src/main/java/com/cdwy/permission/controller/me/MeController.java) |
| GET | `/api/permissions` | 查询前端权限列表：查询前端权限资源；用户分页，其余为当前小规模演示的完整列表。 | — | 200 | 管理员 | [PermissionController.list](../../src/main/java/com/cdwy/permission/controller/permission/PermissionController.java) |
| POST | `/api/permissions` | 创建前端权限：创建独立前端权限记录，默认启用；不隐式创建授权关系。 | PermissionInput | 201 | 管理员 | [PermissionController.create](../../src/main/java/com/cdwy/permission/controller/permission/PermissionController.java) |
| GET | `/api/permissions/tree` | 查询完整前端权限树：管理端以层次展示菜单和按钮，可按systemId筛选；不代表本人有效权限。 | — | 200 | 管理员 | [PermissionController.tree](../../src/main/java/com/cdwy/permission/controller/permission/PermissionController.java) |
| DELETE | `/api/permissions/{id}` | 删除前端权限：删除该资源；除用户会清理其用户角色关系外，关联引用需要先解除，否则409。 | — | 204 | 管理员 | [PermissionController.delete](../../src/main/java/com/cdwy/permission/controller/permission/PermissionController.java) |
| PUT | `/api/permissions/{id}` | 编辑前端权限资料：修改资料；创建时的身份标识保持固定，enabled/active不在此DTO，夹带将返回400。 | PermissionUpdate | 200 | 管理员 | [PermissionController.update](../../src/main/java/com/cdwy/permission/controller/permission/PermissionController.java) |
| GET | `/api/permissions/{id}/resources` | 查询权限API绑定：读取显式关联，便于核对配置，不包含自动推导出的其他关系。 | — | 200 | 管理员 | [PermissionController.resources](../../src/main/java/com/cdwy/permission/controller/permission/PermissionController.java) |
| DELETE | `/api/permissions/{id}/resources/{resourceId}` | 解除权限API绑定：只解除该对关系；其他角色或权限仍可提供访问权，下一次请求实时生效。 | — | 204 | 管理员 | [PermissionController.unbind](../../src/main/java/com/cdwy/permission/controller/permission/PermissionController.java) |
| PUT | `/api/permissions/{id}/resources/{resourceId}` | 建立权限API绑定：以两端ID标识关联；重复建立保持一条关系，唯一约束防止重复。 | — | 204 | 管理员 | [PermissionController.bind](../../src/main/java/com/cdwy/permission/controller/permission/PermissionController.java) |
| PUT | `/api/permissions/{id}/status` | 单独启停前端权限：独立改变active，影响所有关联使用者；保留关系，下一次请求重新计算有效权限。 | Activity | 200 | 管理员 | [PermissionController.status](../../src/main/java/com/cdwy/permission/controller/permission/PermissionController.java) |
| GET | `/api/resources` | 查询API资源列表：查询API资源资源；用户分页，其余为当前小规模演示的完整列表。 | — | 200 | 管理员 | [ApiResourceController.list](../../src/main/java/com/cdwy/permission/controller/resource/ApiResourceController.java) |
| POST | `/api/resources` | 创建API资源：创建独立API资源记录，默认启用；不隐式创建授权关系。 | ResourceCreate | 201 | 管理员 | [ApiResourceController.create](../../src/main/java/com/cdwy/permission/controller/resource/ApiResourceController.java) |
| DELETE | `/api/resources/{id}` | 删除API资源：删除该资源；除用户会清理其用户角色关系外，关联引用需要先解除，否则409。 | — | 204 | 管理员 | [ApiResourceController.delete](../../src/main/java/com/cdwy/permission/controller/resource/ApiResourceController.java) |
| PUT | `/api/resources/{id}` | 编辑API资源资料：修改资料；创建时的身份标识保持固定，enabled/active不在此DTO，夹带将返回400。 | ResourceUpdate | 200 | 管理员 | [ApiResourceController.update](../../src/main/java/com/cdwy/permission/controller/resource/ApiResourceController.java) |
| PUT | `/api/resources/{id}/status` | 单独启停API资源：独立改变active，影响所有关联使用者；保留关系，下一次请求重新计算有效权限。 | Activity | 200 | 管理员 | [ApiResourceController.status](../../src/main/java/com/cdwy/permission/controller/resource/ApiResourceController.java) |
| GET | `/api/role-hierarchy` | 查询角色继承关系：返回显式有向边：高级角色获得被继承角色的有效权限。 | — | 200 | 管理员 | [RoleHierarchyController.edges](../../src/main/java/com/cdwy/permission/controller/rbac/RoleHierarchyController.java) |
| GET | `/api/roles` | 查询角色列表：查询角色资源；用户分页，其余为当前小规模演示的完整列表。 | — | 200 | 管理员 | [RoleController.list](../../src/main/java/com/cdwy/permission/controller/role/RoleController.java) |
| POST | `/api/roles` | 创建角色：创建独立角色记录，默认启用；不隐式创建授权关系。 | RoleCreate | 201 | 管理员 | [RoleController.create](../../src/main/java/com/cdwy/permission/controller/role/RoleController.java) |
| DELETE | `/api/roles/{id}` | 删除角色：删除该资源；除用户会清理其用户角色关系外，关联引用需要先解除，否则409。 | — | 204 | 管理员 | [RoleController.delete](../../src/main/java/com/cdwy/permission/controller/role/RoleController.java) |
| PUT | `/api/roles/{id}` | 编辑角色资料：修改资料；创建时的身份标识保持固定，enabled/active不在此DTO，夹带将返回400。 | RoleUpdate | 200 | 管理员 | [RoleController.update](../../src/main/java/com/cdwy/permission/controller/role/RoleController.java) |
| PUT | `/api/roles/{id}/status` | 单独启停角色：独立改变enabled，影响所有关联使用者；保留关系，下一次请求重新计算有效权限。 | Status | 200 | 管理员 | [RoleController.status](../../src/main/java/com/cdwy/permission/controller/role/RoleController.java) |
| GET | `/api/roles/{roleId}/effective-permissions` | 预览角色有效权限：包含直接权限、传递继承权限与最短有效路径。 | — | 200 | 管理员 | [RoleHierarchyController.role](../../src/main/java/com/cdwy/permission/controller/rbac/RoleHierarchyController.java) |
| GET | `/api/roles/{roleId}/permissions` | 查询角色权限：读取显式关联，便于核对配置，不包含自动推导出的其他关系。 | — | 200 | 管理员 | [GrantController.permissions](../../src/main/java/com/cdwy/permission/controller/rbac/GrantController.java) |
| DELETE | `/api/roles/{roleId}/permissions/{permissionId}` | 解除角色权限：只解除该对关系；其他角色或权限仍可提供访问权，下一次请求实时生效。 | — | 204 | 管理员 | [GrantController.revokePermission](../../src/main/java/com/cdwy/permission/controller/rbac/GrantController.java) |
| PUT | `/api/roles/{roleId}/permissions/{permissionId}` | 建立角色权限：以两端ID标识关联；重复建立保持一条关系，唯一约束防止重复。 | — | 204 | 管理员 | [GrantController.grantPermission](../../src/main/java/com/cdwy/permission/controller/rbac/GrantController.java) |
| DELETE | `/api/roles/{seniorId}/inherits/{inheritedId}` | 解除直接继承：仅解除这条直接边；其他有效路径仍可提供权限。 | — | 204 | 管理员 | [RoleHierarchyController.remove](../../src/main/java/com/cdwy/permission/controller/rbac/RoleHierarchyController.java) |
| PUT | `/api/roles/{seniorId}/inherits/{inheritedId}` | 建立角色继承：高级角色继承权限，幂等；拒绝自继承、停用角色及多级闭环。 | — | 204 | 管理员 | [RoleHierarchyController.inherit](../../src/main/java/com/cdwy/permission/controller/rbac/RoleHierarchyController.java) |
| GET | `/api/system-info` | 查看实际RBAC1设计模式：返回当前实际装载的 structured 或 ddd 实现，不包含密钥。 | — | 200 | 管理员 | [SystemInfoController.info](../../src/main/java/com/cdwy/permission/controller/rbac/SystemInfoController.java) |
| GET | `/api/systems` | 查询应用列表：查询应用资源；用户分页，其余为当前小规模演示的完整列表。 | — | 200 | 管理员 | [SystemController.list](../../src/main/java/com/cdwy/permission/controller/system/SystemController.java) |
| POST | `/api/systems` | 创建应用：创建独立应用记录，默认启用；不隐式创建授权关系。 | SystemCreate | 201 | 管理员 | [SystemController.create](../../src/main/java/com/cdwy/permission/controller/system/SystemController.java) |
| DELETE | `/api/systems/{id}` | 删除应用：删除该资源；除用户会清理其用户角色关系外，关联引用需要先解除，否则409。 | — | 204 | 管理员 | [SystemController.delete](../../src/main/java/com/cdwy/permission/controller/system/SystemController.java) |
| PUT | `/api/systems/{id}` | 编辑应用资料：修改资料；创建时的身份标识保持固定，enabled/active不在此DTO，夹带将返回400。 | SystemUpdate | 200 | 管理员 | [SystemController.update](../../src/main/java/com/cdwy/permission/controller/system/SystemController.java) |
| PUT | `/api/systems/{id}/status` | 单独启停应用：独立改变enabled，影响所有关联使用者；保留关系，下一次请求重新计算有效权限。 | Status | 200 | 管理员 | [SystemController.status](../../src/main/java/com/cdwy/permission/controller/system/SystemController.java) |
| GET | `/api/users` | 查询用户列表：查询用户资源；用户分页，其余为当前小规模演示的完整列表。 | — | 200 | 管理员 | [UserController.list](../../src/main/java/com/cdwy/permission/controller/user/UserController.java) |
| POST | `/api/users` | 创建用户：创建独立用户记录，默认启用；不隐式创建授权关系。 | UserCreate | 201 | 管理员 | [UserController.create](../../src/main/java/com/cdwy/permission/controller/user/UserController.java) |
| DELETE | `/api/users/{id}` | 删除用户：删除该资源；除用户会清理其用户角色关系外，关联引用需要先解除，否则409。 | — | 204 | 管理员 | [UserController.delete](../../src/main/java/com/cdwy/permission/controller/user/UserController.java) |
| GET | `/api/users/{id}` | 查询单个用户：查询单个用户 | — | 200 | 管理员 | [UserController.get](../../src/main/java/com/cdwy/permission/controller/user/UserController.java) |
| PUT | `/api/users/{id}` | 编辑用户资料：修改资料；创建时的身份标识保持固定，enabled/active不在此DTO，夹带将返回400。 | UserUpdate | 200 | 管理员 | [UserController.update](../../src/main/java/com/cdwy/permission/controller/user/UserController.java) |
| PUT | `/api/users/{id}/password` | 管理员设置用户密码：独立凭证资源操作；更新凭证版本，使该用户全部旧Token失效。 | SetPassword | 204 | 管理员 | [UserController.password](../../src/main/java/com/cdwy/permission/controller/user/UserController.java) |
| PUT | `/api/users/{id}/status` | 单独启停用户：独立改变enabled，影响所有关联使用者；保留关系，下一次请求重新计算有效权限。 | Status | 200 | 管理员 | [UserController.status](../../src/main/java/com/cdwy/permission/controller/user/UserController.java) |
| GET | `/api/users/{userId}/permission-origins` | 查询用户权限来源：管理员核对用户最终权限及每个分配角色提供的来源。 | — | 200 | 管理员 | [RoleHierarchyController.user](../../src/main/java/com/cdwy/permission/controller/rbac/RoleHierarchyController.java) |
| GET | `/api/users/{userId}/roles` | 查询用户角色：读取显式关联，便于核对配置，不包含自动推导出的其他关系。 | — | 200 | 管理员 | [GrantController.roles](../../src/main/java/com/cdwy/permission/controller/rbac/GrantController.java) |
| DELETE | `/api/users/{userId}/roles/{roleId}` | 解除用户角色：只解除该对关系；其他角色或权限仍可提供访问权，下一次请求实时生效。 | — | 204 | 管理员 | [GrantController.revokeRole](../../src/main/java/com/cdwy/permission/controller/rbac/GrantController.java) |
| PUT | `/api/users/{userId}/roles/{roleId}` | 建立用户角色：以两端ID标识关联；重复建立保持一条关系，唯一约束防止重复。 | — | 204 | 管理员 | [GrantController.grantRole](../../src/main/java/com/cdwy/permission/controller/rbac/GrantController.java) |

## Postman 验证

1. Import → 选择本目录的`openapi.json`。演示地址为`http://127.0.0.1:18081`，部署后按实际地址调整。
2. 发送登录请求，密码使用启动脚本中手工输入的值，复制`accessToken`。
3. 在集合 Authorization 中选 Bearer Token，粘贴Token；登录请求自身无需认证。
4. 按[演示步骤](demo.md)依次创建对象，记录实际返回ID，再建立三组关系。
5. 改用普通用户Token调用`GET /api/demo/courses`，赋权前403，赋权后200，撤权后再变403。
6. 修改状态使用独立`/status`请求；不要把enabled/active放到资料编辑JSON。

字段、必填项、枚举与JSON示例见OpenAPI的Schemas和Request Body。示例中的尖括号内容必须替换。
