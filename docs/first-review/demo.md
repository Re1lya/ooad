# 运行与演示

## 1. 构建

要求JDK21、Maven3.9、Node.js20.19+/22.12+；项目包含CloneFactory源码依赖。构建前停止当前项目进程，Windows运行中的JAR会锁定文件。

在仓库根目录的PowerShell执行，JDK路径按实际安装位置调整：

```powershell
.\scripts\build-first-review.ps1 -JdkHome 'C:\Users\姚景云\.jdks\corretto-21.0.12.1'
```

脚本依次构建并安装仓库附带的CloneFactory依赖、编译Vue前端、执行Maven clean verify。缓存默认在D:\CodexData\cache，产物在项目target。依赖已齐全时可加-Offline；仅改后端时可加-SkipFrontend。

## 2. 启动

```powershell
.\scripts\start-first-review.ps1 -JdkHome 'C:\Users\姚景云\.jdks\corretto-21.0.12.1' -AdminUsername admin
```

按提示手工设置管理员密码，不显示在屏幕或写入源码。密码至少8字符、UTF-8不超过72字节，大写/小写/数字/符号至少三类。保持终端打开，浏览器访问 [管理页面](http://127.0.0.1:18081/ui/index.html)，使用admin和刚设置的密码登录。端口冲突可加-Port 18082。

当前默认H2内存库，只监听127.0.0.1；停止后演示数据清空。首次管理员是启动配置，不提供公开注册或初始化API。没有配置用户名/密码时不会自动生成一个可登录的项目管理员。

## 3. 在界面完成授权闭环

1. 部门管理：新增“信息技术部”。
2. 应用管理：新增编码`course`、名称“课程系统”。课程示例注解固定使用course编码。
3. 角色管理：新增编码`teacher`、名称“教师”。
4. 用户管理：创建普通员工，例如teacher01，自己设置初始密码。不会自动获得管理员身份或业务权限。
5. 菜单与按钮：在course应用创建MENU，编码`course:menu`、名称“课程管理”、菜单地址`/courses`；再创建BUTTON，编码`course:list`、名称“查看课程”，父菜单选刚创建的菜单。
6. API资源：同应用创建“查询课程列表”，方法`GET`，路径`/api/demo/courses`。菜单地址不是后端API路径。
7. 授权关系第一栏：为teacher01分配教师角色；第二栏：把查看课程按钮授予教师角色；第三栏：把按钮绑定课程API资源。每组关系可单独查看和回收。
8. 管理员退出，普通用户登录；只显示个人工作区。我的权限中祖先菜单可标记“导航容器”，按钮标记“已授权”。调用课程列表接口应成功。
9. 管理员回收必要关系或停用角色，再用普通用户相同Token请求课程API，应返回403；恢复配置后相同有效Token可以恢复访问。若还有其他角色提供相同权限，则不应错误地撤销全部访问权。
10. 用独立状态按钮演示启停。最后本人改密，页面返回登录，旧Token请求应401；新密码可重新登录。

建议用两个独立浏览器上下文或管理员界面配合Postman的普通用户Token演示，无需反复退出切换身份。

## 角色继承演示

1. 在角色管理创建“业务浏览者”“业务专员”“部门负责人”。
2. 在授权工作台“角色→权限”给业务浏览者配置查看课程按钮；“权限→API”绑定 GET /api/demo/courses。
3. 进入角色继承，选择业务专员，建立业务专员→业务浏览者；再选择部门负责人，建立部门负责人→业务专员。
4. 在“用户→角色”只给演示普通用户分配部门负责人，预览最终权限应显示继承来源。
5. 普通用户在另一浏览器上下文登录，进入我的权限，发送验证请求返回200。
6. 管理员解除负责人→专员，普通用户保持相同登录会话刷新后再请求，应403；若其他来源仍提供相同权限则允许。
7. 恢复继承，在浏览者上尝试继承负责人，应提示循环继承且不写入。

高级角色是获得权限的一方。角色图与部门树、菜单树独立，不会因部门职位自动赋权。每次解除、停用前会显示影响确认。

## 4. Postman／IDE HTTP Client

Postman直接导入[openapi.json](openapi.json)。先POST /api/auth/login，复制accessToken；后续Authorization选Bearer Token。

```http
### 登录：实际密码自行填写
POST http://127.0.0.1:18081/api/auth/login
Content-Type: application/json

{"username":"admin","password":"<你设置的密码>"}

### 查看当前身份：把下方占位符替换为accessToken
GET http://127.0.0.1:18081/api/me
Authorization: Bearer <accessToken>

### 角色单独停用：roleId替换为创建返回的ID
PUT http://127.0.0.1:18081/api/roles/<roleId>/status
Authorization: Bearer <管理员accessToken>
Content-Type: application/json

{"enabled":false}

### 调用课程示例：应使用普通用户自己的Token验证
GET http://127.0.0.1:18081/api/demo/courses
Authorization: Bearer <普通用户accessToken>
```

成功查询200，创建201，关联建立/解除和删除204；不要把204无响应体当成错误。各路径的字段与状态码见[API文档](api-design.md)。

## 5. MySQL持久化配置

本轮仅提供MySQL8新空schema脚本，不直接迁移旧库。先在新空库执行[database-mysql.sql](database-mysql.sql)，再用环境变量配置：

```text
SPRING_PROFILES_ACTIVE=prod
PERMISSION_DB_URL=jdbc:mysql://<host>:3306/<new_schema>?connectionTimeZone=UTC
PERMISSION_DB_USERNAME=<db_user>
PERMISSION_DB_PASSWORD=<db_password>
PERMISSION_JWT_SECRET=<至少32字节的随机密钥>
PERMISSION_BOOTSTRAP_USERNAME=<first_admin>
PERMISSION_BOOTSTRAP_PASSWORD=<first_admin_password>
```

prod仅执行Hibernate validate，不自动建表/改旧表。没有配置JWT密钥的默认演示模式会生成进程内随机密钥，重启后旧Token失效；持久化运行应固定为部署密钥。外部/临时期限用PERMISSION_EXTERNAL_MAX_DAYS和PERMISSION_TEMPORARY_MAX_DAYS配置。

仓库交付包含构建、启动和演示所需源码与材料。真实MySQL的启动校验、旧数据转换、备份恢复、服务器部署与真实课程系统对接属于后续验收。

## 浏览器回归

先启动一个全新的隔离 H2 内存演示实例（建议18082端口），指定专用合成管理员账号；不要对真实业务库运行该用例。测试创建演示应用、人员与角色，并执行继承和撤回。重新执行主业务测试前重启内存实例，否则固定 course 编码会冲突。

```powershell
$env:PHASE_TWO_DEMO_ACK='isolated-h2'
$env:PHASE_TWO_URL='http://127.0.0.1:18082'
$env:PHASE_TWO_ADMIN_USERNAME='<隔离演示管理员>'
$env:PHASE_TWO_ADMIN_PASSWORD='<隔离演示密码>'
$env:PHASE_TWO_OUTPUT='<本聊天在 D 盘的独立输出目录>'
npm.cmd run test:e2e --prefix frontend
```

默认使用 Windows Edge；其他位置可设置 EDGE_PATH。脚本输出业务和导航 JSON、桌面与390px截图。管理/个人业务流程14项，连续后退/前进2项。结果校验真实 API 与页面，不用 mock；未设置隔离标志时跳过，跳过不能当作通过。

更新 docs/design/phase-two 的证据与截图后再运行文档生成器，以保持 README、API、数据库和验证统计一致。
