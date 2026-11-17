# 验证记录

- 后端：`mvn verify`，81个后端验收及领域测试，失败0、错误0、跳过0。
- 前端：vue-tsc和Vite生产构建通过，静态资源位于`src/main/resources/static/ui`。
- 浏览器：两种模式各3组真实Edge场景，共46项检查；包含连续后退/前进、继承与撤回、三表预览确认及设置员工密码，无未捕获JavaScript异常。[业务记录](../design/phase-two/ui-verification.json)、[导航记录](../design/phase-two/navigation-verification.json)。
- 图：18张图同时生成PNG/SVG；完成自动布局检查及[PNG目视复核](../design/phase-two/visual-review.json)。
- 数据库：11张持久化表，14外键；MySQL脚本的隔离兼容结构测试通过，未连接真实MySQL或服务器。
- OpenAPI：56个操作与Spring MVC实际路由逐项集合对比，内部引用完整。

| JaCoCo类型 | 已覆盖 | 未覆盖 | 比例 |
|---|---:|---:|---:|
| line | 2303 | 281 | 89.13% |
| branch | 658 | 212 | 75.63% |
| instruction | 11307 | 1559 | 87.88% |
| method | 708 | 115 | 86.03% |
| class | 151 | 5 | 96.79% |

当前报告为本分支重新测得，不沿用归档版本的测试数量或覆盖率。统计包括注解处理器生成的CloneFactory类；不代表100%路径、并发或真实MySQL已验证。

构建生成的完整JaCoCo报告：`target/site/jacoco/index.html`；可复核指标见[verification.json](verification.json)。

重现顺序：`scripts/build-first-review.ps1` → `python scripts/build-review-diagrams.py` → `python scripts/build-review-docs.py`。浏览器检查在独立隔离H2库进行，截图使用合成数据，不代表生产部署。前端验收命令和隔离环境变量见[运行说明](demo.md#浏览器回归)。
