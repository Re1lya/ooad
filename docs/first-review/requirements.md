# 需求与实现追踪

> 第二次检查起，需求基线以 [需求规格说明（第二次检查版）](../second-review/01-requirements.adoc) 为准：RBAC1 与课程项目管理系统接入已纳入范围，编号整理为 FR-01~FR-30、NFR-01~NFR-12。本文保留为第一次检查后的历史追踪。

## 1. 目标与范围

为多个应用集中维护“谁通过什么角色获得哪些前端权限，以及这些权限对应哪些后端API资源”。本轮覆盖RBAC1角色继承、人员与组织目录、Token认证和可演示的授权闭环。管理员负责配置，普通用户查看本人权限并访问已授权的课程示例接口。

范围外为审批复核、HR/OA集成、OIDC、服务端会话管理、互斥约束和真实课程系统对接。这些历史功能与当前迭代分开归档。

## 2. 功能需求

| 编号 | 需求 | 验收结果／规则 |
|---|---|---|
| FR-01 | 账号密码登录 | 有效账号且BCrypt验证通过返回Bearer JWT；错误、停用、过期账号返回401 |
| FR-02 | 本人身份与改密 | 从Token确定主体；本人改密校验原密码，全部旧Token失效，前端要求重新登录 |
| FR-03 | 用户维护 | 管理员分页查询、新建、编辑、单独启停、重置密码、删除；不能通过普通DTO修改administrator |
| FR-04 | 人员身份 | 正式、外部、临时共用UserAccount；后两类须提供期限，不能超过配置上限；正式员工不设临时期限 |
| FR-05 | 部门维护 | 父子部门防环；有用户或子部门时不能删除 |
| FR-06 | 应用维护 | 登记应用，创建后编码固定；单独启停影响该应用下有效权限与API |
| FR-07 | 角色维护 | 业务角色独立管理；多个角色取并集，停用角色不计入有效授权 |
| FR-08 | 前端权限树 | MENU/BUTTON区分，按钮归属同应用菜单；父节点防环，祖先停用使后代无效 |
| FR-09 | API资源维护 | 独立方法+路径+应用唯一，资源独立启停；登记后方法、路径、应用固定，改定义需解绑后重建 |
| FR-10 | 用户角色分配 | M:N关联，重复分配幂等；撤回一个角色不影响其他角色提供的权限 |
| FR-11 | 角色权限分配 | M:N关联，授权以节点为单位，不隐式向子节点或祖先API扩散 |
| FR-12 | 前端权限绑定API | M:N关联，两端同应用；一个按钮可调用多个接口，一个接口可被多个权限使用 |
| FR-13 | 个人导航 | 返回有效授权节点及其祖先；granted=false标记纯导航容器，不把容器转成授权 |
| FR-14 | 真实请求的鉴权 | 受保护业务控制器在执行前验Token，再根据实际应用、HTTP方法、路径匹配有效API资源；无资源或无完整授权链返回403 |
| FR-15 | 状态独立变更 | 5类对象使用独立/status；普通编辑夹带状态返回400；前端确认影响后执行 |
| FR-16 | 演示页面 | 管理端能维护基础对象和3组关系，个人端能查看权限、改密、调用课程示例 |

| FR-17 | 多级角色继承 | 高级角色获得被继承角色权限，支持多级/多继承；移除必要路径即时影响下一次鉴权 |
| FR-18 | 继承图一致性 | 拒绝自继承和环；重复幂等；H2 并发反向加边只成功一边；删除被引用角色返回409 |
| FR-19 | 有效来源解释 | 权限去重并展示直接/继承来源；多来源不互相误撤；停用角色阻断路径；每一来源保留一条最短有效路径 |
| FR-20 | 继承访问边界 | 管理员维护继承与预览角色/用户；普通用户只查本人来源，不获得管理接口 |
| FR-21 | 前端任务闭环 | 实际 API 联动的资源管理、继承、三组授权、个人验证；清楚区分直接关系与最终权限 |
| FR-22 | 交互可用性 | 取消不写、错误可读、提交防重、退出清理身份、连续前进后退正确、390px 窄屏可操作 |

| FR-23 | RBAC1 核心双实现 | 共享端口；structured 与 ddd 条件装配；同一组继承业务场景在两模式执行，实际模式可查询 |
| FR-24 | 公司三表预览 | 管理员三份 XLSX 上传、完整校验、只返回计数和摘要；不写库、更换文件重新预览 |
| FR-25 | 确认三表导入 | 原文件摘要一致后事务写入；来源字段保留，重复复用；新员工停用，无自动个人授权 |

| FR-23 | RBAC1 核心双实现 | 同一端口装载 structured／ddd 之一；共享 HTTP、JWT 和持久化；两模式复用继承业务场景 |
| FR-24 | 公司三表预览 | 管理员 multipart 上传，按真实 XML 行与工作簿关系读表；完整校验后仅返回数量、摘要和处理规则，未写入 |
| FR-25 | 三表确认导入 | 文件摘要匹配后事务提交；自然键幂等、资料保留、模板授权同步；新员工停用，无可用初始密码；管理员单独设置后启用 |

| FR-26 | 全业务双设计 | 十组业务端口分别提供结构化与面向对象实现，启动时条件装配；领域无 JPA 依赖；两模式复用同一组业务验收 |

## 3. 非功能需求

| 编号 | 要求 | 当前实现与验证边界 |
|---|---|---|
| NFR-01 | 认证与身份边界 | BCrypt；JWT验签与有效期；请求仅凭Bearer；登录外API拦截；普通用户不能调用管理接口。生产传输应使用HTTPS |
| NFR-02 | 数据最小输出 | UserView不含密码摘要及凭证版本；输入不包含管理员身份；严格拒绝未知字段 |
| NFR-03 | 一致性 | Service事务、数据库FK/UK；账号敏感写入悲观锁；共享配置删除前检查引用；同一Token在下一次请求读取授权变化 |
| NFR-04 | 可维护性 | 先分层后分模块；Controller 校验并通过用例端口调用；两种实现分别组织业务；DDD 通过领域仓储适配；DTO 隔离实体输出 |
| NFR-05 | 可测试与可追溯 | HTTP集成验收、浏览器工作流、DDL结构对照；图表可重复生成；路由清单与实际映射一致 |
| NFR-06 | 可配置 | Token有效期、签名密钥、外部/临时期限、业务时区、初始化管理员均可从环境配置 |
| NFR-07 | 交互与错误 | 成功200/201/204；400/401/403/404/409分开；分页与空状态；危险状态操作确认；表格窄屏可滚动 |
| NFR-08 | 演示性能边界 | 用户后端分页限制size≤100。其余目录当前全量读取，用户选择器按25条分批切换；用户列表搜索明确限定本页；未做大数据量、吞吐或延迟压测，不承诺生产SLA |
| NFR-09 | 持久化环境 | 默认H2内存用于演示，重启清空；公司实例可使用独立H2文件库，已有库validate。MySQL新库DDL已提供，prod只validate；真实MySQL部署与旧库迁移待验证 |
| NFR-10 | 安全退出语义 | 前端删除本标签页Token。无单Token撤销表/黑名单；被复制的Token在未改密/停用时可持续到exp。不能把客户端退出称作服务端强制撤销 |

## 4. 页面到数据库的追踪

| 页面／动作 | 主要API资源 | Service | 持久化映射 |
|---|---|---|---|
| 登录 | POST /api/auth/login | AuthService、JwtTokenService | UserAccount→sys_user+sys_account；不存Token |
| 用户资料、状态、密码 | /api/users、/{id}/status、/{id}/password | UserService | sys_user、sys_account、sys_department |
| 部门 | /api/departments | DepartmentService | sys_department（自关联） |
| 应用 | /api/systems、/{id}/status | SystemService | biz_system |
| 角色 | /api/roles、/{id}/status | RoleService | sys_role |
| 菜单按钮 | /api/permissions、/tree、/{id}/status | PermissionService | sys_permission（自关联） |
| API资源 | /api/resources、/{id}/status | ApiResourceService | api_resource |
| 用户→角色 | /api/users/{userId}/roles/{roleId} | AuthorizationService | sys_user_role |
| 角色→权限 | /api/roles/{roleId}/permissions/{permissionId} | AuthorizationService | sys_role_permission |
| 权限→API | /api/permissions/{id}/resources/{resourceId} | PermissionService | permission_api |
| 角色继承 | /api/role-hierarchy、/api/roles/{seniorId}/inherits/{inheritedId} | RoleHierarchyService | sys_role_hierarchy，双向角色引用 |
| 权限来源 | /api/roles/{roleId}/effective-permissions、/api/users/{userId}/permission-origins、/api/me/permission-origins | AuthorizationService、RoleHierarchyService | 按当前继承图计算，不保存来源快照 |
| 我的权限与课程示例 | /api/me/permissions、/menu；GET /api/demo/courses | AuthorizationService | 动态组合现有授权，课程示例无课程表 |

## 5. 复杂用例：给教师配置课程读取权限

前置：管理员已登录；用户、应用、角色、菜单、按钮、API资源已存在且有效。

1. 管理员把“教师”角色分给用户，创建用户角色关系。
2. 把“查看课程”按钮授予角色，创建角色权限关系。
3. 把按钮绑定到course应用的GET /api/demo/courses，创建权限API关系。
4. 教师登录后查看个人菜单，按钮为直接授权，祖先菜单可以仅作为容器。
5. 请求课程接口：先验证JWT和账号，再匹配最具体API资源，按当前有效授权并集找到绑定，返回示例数据。
6. 管理员撤回任意必要关系后，教师用相同Token再次请求，返回403；若其他有效角色仍提供相同权限则继续200。

异常分支：对象不存在404；关联跨应用400；账号或Token失效401；没有管理身份或业务授权403；树成环400；删除被引用对象409。实现顺序图与源码见[图索引](../design/README.md)。

## 6. 复杂用例：角色继承与撤权

前置：管理员已建有效的基础角色、业务专员和负责人；基础角色已有按钮权限并绑定 API。

1. 业务专员继承基础角色，负责人继承业务专员；两条直接边分别保存，不重复保存传递闭包。
2. 用户仅分配负责人角色。鉴权从负责人遍历两级继承，获取基础角色权限。
3. 来源显示“负责人→业务专员→基础角色”；同一权限去重。多条等长路径只选其中一条，不保证固定顺序。
4. 普通用户请求实际 API 成功；管理员解除负责人→业务专员后，下一次请求返回403。
5. 若该用户还通过其他直接角色或继承路径取得同一权限，则继续允许；解除一条边不会删除其他授权记录。
6. 尝试基础角色继承负责人，若产生环则返回400且不插入；防环包含停用节点，避免重新启用后成环。

新增测试见 `PhaseTwoRbac1Tests`，前端场景见 `frontend/tests`；[继承顺序图](../design/images/sequence-rbac1.png)。

公司导入和双实现的详细规则见[设计与导入说明](../phase-two/dual-design-and-import.md)。

当前模块到两种源码及领域对象的完整追踪见[全业务双设计](../phase-two/full-dual-design.md)。
