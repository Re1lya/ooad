# 全业务面向对象设计

以 [全业务双设计说明](full-dual-design.md) 第 3～6 节为当前对象模型、行为与仓储边界。

# RBAC1 业务规则补充

## 设计目标与对象

沿用用户、角色、应用、前端权限、API 资源、部门六类核心概念。角色继承是 Role 对象之间的业务关联，物理实现增加 RoleHierarchy 关联实体，不增加新的人员概念，也不以 Java 类继承表达业务角色等级。

| 对象/组件 | 职责与边界 |
|---|---|
| Role | 角色编码、名称、说明、启用状态；不能从部门层级推导角色权限 |
| RoleHierarchy | seniorRole 与 inheritedRole 两个对象引用；高级角色获得被继承角色权限 |
| RoleHierarchyService | 继承边维护、防环、图解析和有效路径；不负责 JWT |
| RoleHierarchyRepository | JPA 持久化；双端唯一及引用查询，不内置全图业务规则 |
| AuthorizationService | 直接用户角色、角色权限、有效并集、来源、API 鉴权 |
| Permission / ApiResource | 分别描述 MENU/BUTTON 与后端 HTTP 方法/路径，通过 PermissionApi 显式绑定 |
| RoleHierarchyDtos | 只读 record 结果，显式映射，不暴露受管实体或写 final 字段 |

[继承类图](../design/images/class-rbac1.png)中字段引用使用实线关联并标角色名，方法局部/返回使用虚线依赖；Service 位于中心。早期结构化图中 Repository 继承 Spring Data；当前 DDD 实现另有纯领域仓储端口与真实 JPA Adapter，见完整设计。

## 关键不变量

1. 角色不能继承自身。高级角色→被继承角色全图必须无环。
2. 两端存在；新增关系要求角色有效。重复边幂等，移除不存在的边在角色存在时也幂等。
3. 防环包含停用节点；有效遍历遇停用节点则阻断该路径。
4. 关系表只存直接边，传递关系实时解析，不保存闭包与权限来源快照。
5. 同一权限按 ID 去重；不同分配角色及权限所属角色的来源保留，每对来源只提供一条最短有效路径。
6. 删除继承边不删除角色、角色直接权限或其他边。角色仍被任一方向引用时禁止删除。
7. 部门父子关系和菜单父子关系不自动产生角色继承；高级角色不等于系统管理员。

## 建立继承的对象协作

Controller 从路径接收两个角色 ID，将操作交给 RoleHierarchyService；管理身份由统一拦截器校验。Service 在写事务中通过 RoleRepository 按 ID 排序锁定角色，读取两端，校验自继承、重复、状态。从被继承角色开始遍历，若可到达高级角色则拒绝，否则保存 RoleHierarchy。事务完成后返回 204。

同一图写锁用于解除继承及角色删除，防止并发反向加边分别通过检查。当前实现锁全部角色，适合教学规模，未宣称是生产大图的最优锁策略。H2 双线程测试验证一次反向冲突，真实 MySQL 隔离级别和跨实例行为待验证。

[建立继承顺序图](../design/images/sequence-rbac1.png)展示成功路径，源码入口：[RoleHierarchyService](../../src/main/java/com/cdwy/permission/service/rbac/RoleHierarchyService.java)。

## 计算权限与来源

AuthorizationService 从用户直接分配的有效角色开始，获取当前继承图，调用 resolve 进行 BFS；visited 防止菱形重复和异常数据死循环，path 保存最短路径。对所有到达的有效角色读取其直接权限，经菜单祖先及应用可用性校验，合并成按权限 ID 去重的集合。

来源以“分配角色、权限所属角色”为单位记录；一个角色存在多条相等长度路径时选择其中一条，不枚举所有路径、不保证固定顺序。多个有效来源中撤回一个，不应删除其他来源。业务请求再匹配实际应用、HTTP 方法和最具体资源路径，检查权限 API 绑定。

JWT 只携带身份与凭证版本，不携带权限快照。因此同一有效 Token 的后续请求读取到新关系。导航祖先可作为容器显示，不能借此获得其 API 权限。

## 设计取舍

现有结构是 Spring/JPA 分层加领域职责分包，并非已完整落地严格 DDD。无需为本轮引入微服务、全套聚合端口或策略引擎。现有 CloneFactory 用户映射继续沿用，新增 record 采用显式构造；图与文档从真实实现出发。

数据规模增大时，当前逐角色查关系和全目录加载需要批量查询、分页与有验证的缓存失效方案；本轮没有性能 SLA 或大型图压测。RBAC2 互斥约束与行级数据过滤属于后续任务。角色图核心现已提供 structured / ddd 双实现，详见[新增设计对照](dual-design-and-import.md)。

## 验证追踪

FR-17～FR-20 对应 PhaseTwoRbac1Tests 的 7 个集成场景；FR-21/22 对应 frontend/tests 的管理/个人与导航场景。实际测试统计和覆盖率见[验证记录](../first-review/testing.md)，不以图中规则替代运行证据。
