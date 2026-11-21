# 验证记录

- 后端：`mvn verify`（连接真实MySQL，每个测试事务回滚），182个后端验收、错误码契约及领域测试，失败0、错误0、跳过0。
- 前端：vue-tsc和Vite生产构建通过，静态资源位于`src/main/resources/static/ui`。
- 浏览器：两种模式各3组真实Edge场景，共46项检查；包含连续后退/前进、继承与撤回、三表预览确认及设置员工密码，无未捕获JavaScript异常。[业务记录](../design/phase-two/ui-verification.json)、[导航记录](../design/phase-two/navigation-verification.json)。
- 图：18张图同时生成PNG/SVG；完成自动布局检查及[PNG目视复核](../design/phase-two/visual-review.json)。
- 数据库：12张持久化表（11张业务表+1张审计日志技术表），14外键；测试库由部署建表脚本在MySQL 8.4上建立，两种模式以validate启动；服务器使用同一脚本。
- OpenAPI：66个操作与Spring MVC实际路由逐项集合对比，内部引用完整。

| JaCoCo类型 | 已覆盖 | 未覆盖 | 比例 |
|---|---:|---:|---:|
| line | 2947 | 193 | 93.85% |
| branch | 923 | 183 | 83.45% |
| instruction | 16373 | 1132 | 93.53% |
| method | 822 | 73 | 91.84% |
| class | 207 | 0 | 100.0% |

当前报告为本分支重新测得，不沿用归档版本的测试数量或覆盖率。统计包括注解处理器生成的CloneFactory类；不代表100%路径已覆盖。

构建生成的完整JaCoCo报告：`target/site/jacoco/index.html`；可复核指标见[verification.json](verification.json)。

重现顺序：`scripts/build-first-review.ps1` → `python scripts/build-review-diagrams.py` → `python scripts/build-review-docs.py`。浏览器检查连接本机MySQL上的独立测试库，截图使用合成数据。前端验收命令和隔离环境变量见[运行说明](demo.md#浏览器回归)。
