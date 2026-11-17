# 代码架构与实现说明

## 结构

单个 Spring Boot 应用配套一套 Vue3 前端。十组业务用例及角色图算法各有两种实现；启动配置选择，HTTP 与数据库共用。不是微服务拆分。

```text
com.cdwy.permission
├── controller/{auth,user,organization,system,role,permission,resource,rbac,me,importing,common,demo}
├── service/port/{data,error,auth,user,organization,system,role,permission,resource,rbac,importing}
├── service/{user,organization,system,role,permission,resource,rbac,auth,importing} # 结构化服务及共享技术
├── service/structured/rbac         # 结构化角色图算法
├── service/ddd/application/{auth,user,organization,system,role,permission,resource,rbac,importing}
├── service/ddd/domain/{model,repository}
├── infrastructure/{persistence,security}
├── repository/{user,organization,system,role,permission,resource,rbac}
├── entity/{user,organization,system,role,permission,resource,rbac}
├── dto/{auth,user,organization,system,role,permission,resource,common}
├── vo/{importing,rbac}
└── config
```

## 两种调用链与边界

- 结构化：Controller → UseCase → 原业务 Service（事务脚本）→ JPA Repository → Entity/数据库。
- 面向对象：Controller → UseCase → Ddd 应用服务 → 领域对象/领域仓储端口 → JPA Adapter → Spring Data Repository → 数据库。
- HTTP DTO 负责请求格式验证；HttpMapping 负责 DTO→Command、Result→VO/只读视图；业务输入、结果和错误类型位于 service/port。
- DDD 应用层没有 JPA Entity、JpaRepository、Spring Data 或 infrastructure.persistence 依赖；由架构边界测试防止反向依赖。领域模型除 CloneFactory/Lombok 生成支持外不依赖 Spring/JPA。
- 用户身份值枚举与权限类型值枚举位于 service/port/data，物理 ENUM 字符串不改变。Controller 不返回数据库实体。

## 24 个 Spring Service 定义，每种模式实际 13 个

十组业务服务逐项见[完整模块对照](../phase-two/full-dual-design.md)。结构化与 DDD 各十个，共 20 个定义；角色图算法 StructuredRoleGraphService/DddRoleGraphService 两个定义；JwtTokenService、AdminBootstrapService 两个共享定义。

每模式选择十个业务服务、一个角色图算法，加两个共享技术服务，合计 13 个。JpaAdapter 使用 Repository 注解、CredentialAdapter/PathMatcher 使用 Component 注解，不混入 Service 数量。PasswordPolicy、UserViews、HttpMapping、CompanyWorkbookParser、XlsxReader 为辅助类，不是额外 Service。

## 对象职责

Account 管理身份期限、账号开关、受保护管理员、凭证版本及来源同步。AccessWindow 按业务时区计算包含截止日的日期上界。DepartmentNode 维护组织无环。ManagedSystem/AccessRole 维护对象状态与删除约束。FrontendPermission 维护菜单按钮类型、同应用父级、防环与绑定规则。ProtectedResource 维护后端方法/路径和资源状态。RoleGraph 表达业务角色继承图；AuthorizationSnapshot/PermissionDirectory 维护授权来源、有效权限与导航容器。CompanyImportBatch 管理来源摘要、稳定编码及最小授权语义。

领域对象按本次仓储重构需要保留可变属性映射访问器；业务用例使用对象行为维护不变量，持久化重建由适配器处理。该实现是适合当前教学范围的 DDD 风格单体设计，没有宣称已经具备事件溯源或分布式领域架构。

## 持久化与事务

领域仓储端口通过业务对象和 ID 通信，不返回受管 Entity。七个 JPA Adapter 完成类型及关系映射；同型字段使用 CloneFactory。用户分页在数据库执行，其他目录和授权关系当前按快照读取，尚未作规模化优化。

Service 管理事务，跨仓储写入单事务提交。账号敏感写保留悲观行锁；角色继承新增/解除及角色删除共享按 ID 排序的图写锁。数据库保留唯一键、外键、自继承 CHECK，整图无环由业务计算。适配器需在当前事务捕获唯一约束时使用 saveAndFlush，并转成稳定 ConflictException。

UserAccount 通过 SecondaryTable 将账号凭证存到 sys_account，其余资料位于 sys_user；仍是一个人员账号业务对象。物理结构继续为 11 表、14 外键。本次不增加数据库迁移；H2 结构验证不能替代真实 MySQL 验证。

## 登录、授权与状态

保留本地账号 + BCrypt + Bearer JWT。JWT 只携带账号标识、签发/过期时间和凭证版本；请求时重新检查账号状态、期限和版本。密码或启停变化使旧 Token 失效。权限关系不写入 Token，撤权在后续请求实时生效。

业务 API 先选择最具体的注册路径，再检查资源状态、用户有效权限和 PermissionApi 显式绑定；更宽泛授权不能绕过更具体的停用资源。管理员身份只允许配置系统，不自动授予业务 API。

## 前端与技术复用

Vue App 提供工作区和导航；console.ts 负责 HTTP、会话与目录；资源表格/弹窗、继承面板、授权工作台、三表导入和个人空间共用一套页面。前端通过 system-info 读取后端真实模式，不通过隐藏页面来实现授权。

JWT 签名、BCrypt、路径模式解析、XLSX/ZIP/XML 解析及首次管理员初始化属于共享技术能力。CompanyWorkbookParser 输出纯 CompanyImportPlan，不把解析技术塞入领域对象。

## 当前边界与证据

81 个后端测试通过；structured 和 ddd 各 3 组真实 Edge 场景、每模式 23 项检查通过。范围包含当前十组业务，不包括尚未实现的审批、互斥、行级权限、SSO、外部系统联调和分布式性能保障。完整实现、源码路径和验证边界见[全业务双设计](../phase-two/full-dual-design.md)。
