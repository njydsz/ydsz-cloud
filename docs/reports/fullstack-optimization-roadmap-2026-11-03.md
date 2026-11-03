# 云顶全栈研发优化路线图（2026-11-03）

> 分析范围：`ydsz-cloud`（Java 21 / Spring Boot 4.1 / PostgreSQL 18 / 9 个业务微服务）与 `ydsz-micro`（Vue 3 / TypeScript / Vite / pnpm + Turborepo / 9 个业务子应用）。
>
> 方法：只读静态审计；所有关键结论均已用源码、SQL、测试或 CI 配置交叉核验。对标 OWASP API Security Top 10（2023）、W3C WCAG 2.2、Google SRE 与微服务测试策略、Core Web Vitals、PostgreSQL 迁移与数据建模最佳实践。

## 一、总体判断

当前平台已经具备较完整的企业级中后台骨架：后端 DDD 分层、统一响应、i18n、权限、审计、幂等、缓存、Testcontainers 集成测试和 ArchUnit 守护均已落地；前端也已形成 ydsz-ui、ydsz-vue、微前端内核、请求层、权限指令、i18n、单元/E2E/视觉/无障碍/性能测试体系。主要短板不在“有没有能力”，而在关键链路没有闭环：数据库初始化脚本不能安全升级，OpenAPI 事实源未在构建期稳定产出，生成后的 SDK 基本未被业务消费，API 权限码没有进入 RBAC 授权链路，E2E 断言选择器与真实 DOM 不一致，覆盖率门禁未覆盖 apps 业务代码。

建议把后续优化拆成三个阶段：先完成 P0 止血与可信门禁，再用 1～2 个版本完成契约和测试体系重构，最后持续进行性能、体验和平台能力增强。P0 完成后，项目才具备稳定发布与可靠回归验证的基础。

## 二、已验证的关键问题

### P0：发布可信度与数据安全

1. 数据库“初始化脚本”实际包含 173 组 `DROP TABLE ... CASCADE` 后重建表。该文件位于 [V26.10.01.sql](D:/Code/open/ydsz-cloud/data/sqls/flyway/V26.10.01.sql:7)，作为升级脚本会清空已有数据；同时 [pom.xml](D:/Code/open/ydsz-cloud/pom.xml:61) 明确禁止 Flyway/Liquibase，因此当前脚本既不安全，也没有自动版本执行保障。

2. 数据库布尔列统一为 `SMALLINT`，但多个 Mapper XML 使用 PostgreSQL 布尔字面量，例如 [UserRecentMapper.xml](D:/Code/open/ydsz-cloud/ydsz-nextwiki/ydsz-nextwiki-infra/src/main/resources/mapper/UserRecentMapper.xml:25)、[SpaceMapper.xml](D:/Code/open/ydsz-cloud/ydsz-nextwiki/ydsz-nextwiki-infra/src/main/resources/mapper/SpaceMapper.xml:38)、[GenDatasourceMapper.xml](D:/Code/open/ydsz-cloud/ydsz-generator/ydsz-generator-infra/src/main/resources/mapper/GenDatasourceMapper.xml:21) 和 [RulePackMapper.xml](D:/Code/open/ydsz-cloud/ydsz-literule/ydsz-literule-infra/src/main/resources/mapper/RulePackMapper.xml:34)。这会造成 SQL 类型不匹配或数据库方言耦合。

3. 前端 E2E 主容器断言使用 `.app-container`，见 [smoke.e2e.ts](D:/Code/open/ydsz-micro/tests/e2e/specs/smoke.e2e.ts:22)；真实页面只存在 `#subapp-container` / `.subapp-container`，见 [subapp/index.vue](D:/Code/open/ydsz-micro/main/src/views/_core/subapp/index.vue:238)。12 个 E2E spec 均沿用旧选择器，且 CI 没有运行 `test:e2e`，因此端到端冒烟实际处于“未验证”状态。

4. API 权限码链路未闭环。system 模块已能扫描并维护 API 权限，例如 [ApiPermissionController.java](D:/Code/open/ydsz-cloud/ydsz-system/ydsz-system-web/src/main/java/com/njydsz/system/web/controller/ApiPermissionController.java:87) 与数据库表 [V26.10.01.sql](D:/Code/open/ydsz-cloud/data/sqls/flyway/V26.10.01.sql:6555)；但 userinfo 的 `/auth/codes` 只返回菜单和按钮权限，不下发 API 权限码，见 [AuthServiceImpl.java](D:/Code/open/ydsz-cloud/ydsz-userinfo/ydsz-userinfo-server/src/main/java/com/njydsz/userinfo/server/auth/AuthServiceImpl.java:605)。前端 `v-permission` 按 API 权限码做 AND 校验，见 [directive.ts](D:/Code/open/ydsz-micro/comm/effects/access/src/directive.ts:111)，于是页面按钮与运行时权限码存在确定性错位。

5. OpenAPI 事实源不可靠。后端把 `springdoc-openapi-maven-plugin` 绑定到 compile，但生成时读取 `http://localhost:${server.port}/v3/api-docs`，见 [pom.xml](D:/Code/open/ydsz-cloud/pom.xml:1306) 和 [openapi-spec profile](D:/Code/open/ydsz-cloud/pom.xml:1498)。普通构建不会启动服务实例，CI 也未稳定产出带版本的 OpenAPI artifact。

### P1：架构、测试与契约治理

1. 测试分布偏 SmokeTest。后端约有 251 个测试文件，其中大量是 Controller SmokeTest；Domain 层测试接近空白，Repository 测试主要集中在少数模块。现有 Testcontainers 基础设施是正确方向，见 [AbstractContainerTestBase.java](D:/Code/open/ydsz-cloud/ydsz-system/ydsz-system-infra/src/test/java/com/njydsz/infra/AbstractContainerTestBase.java:1)，但尚未覆盖所有关键业务不变量与真实 SQL。

2. Controller 层仍存在局部 try-catch，例如 [DictItemController.java](D:/Code/open/ydsz-cloud/ydsz-system/ydsz-system-web/src/main/java/com/njydsz/system/web/controller/DictItemController.java:333)、[GlobalSearchController.java](D:/Code/open/ydsz-cloud/ydsz-system/ydsz-system-web/src/main/java/com/njydsz/system/web/controller/GlobalSearchController.java:334) 和 [LockAdminController.java](D:/Code/open/ydsz-cloud/ydsz-system/ydsz-system-web/src/main/java/com/njydsz/system/web/controller/LockAdminController.java:334)。这会把异常映射、日志、响应包装职责重新散落到接口层。

3. 前端覆盖率配置没有纳入 `apps/*/src/views`，只覆盖公共 composables、UI kit、effects、stores、utils、locales 和部分 main 代码，见 [vitest.config.ts](D:/Code/open/ydsz-micro/vitest.config.ts:53)。当前阈值虽为 statements 25%、branches 15%、functions 20%、lines 25%，但业务视图仍基本不受门禁保护。

4. 前端存在客户端假分页。例如 [agent/index.vue](D:/Code/open/ydsz-micro/apps/agent-web/src/views/agent/index.vue:48) 使用 `slice(0, pageNum * pageSize)`；大量列表的 vxe grid 使用 `height: 'auto'`，默认无法获得行虚拟滚动收益。数据规模增长后会造成 DOM、内存和首屏渲染持续恶化。

5. OpenAPI SDK 与静态源码解析 API 双轨并存。仓库同时提供 `gen:api`、`gen:api:legacy` 和 Python 契约生成入口，见 [package.json](D:/Code/open/ydsz-micro/package.json:76)。业务代码主要消费静态生成的 `apps/*/src/api/*.ts`，而 OpenAPI SDK 目录基本只在 mock、生成器和自身文件中出现，造成契约漂移检测与实际调用链脱节。

6. 下载、导出、排序、筛选契约不统一。后端统一分页对象以 `orderItems` 为核心，前端仍存在 `sortBy/sortDir`、`sortField/sortOrder`、`orderBy/sort` 等多种口径；文件下载返回 `void` 或依赖 Blob 的方式并存。对于企业级平台，这会增加前后端联调、权限控制和大文件导出的维护成本。

7. 事务声明需要审查。全仓存在大量 `@Transactional`，但未统一显式声明 `rollbackFor = Exception.class`。跨服务、消息、文件等组合操作应逐个确认异常边界、事务传播和幂等语义。

### P2：体验与性能增强

1. 可访问性基础不足。真实后台布局文件未发现 `<main>` landmark、skip-link、`role="main"` 等基础结构；当前 a11y 测试 fixture 以手写 HTML 为主，不能证明真实路由、菜单、表格和弹窗达到 WCAG 2.2 AA。自动化扫描只能覆盖部分规则，仍需键盘走查与人工辅助技术测试。

2. 移动端策略尚未体系化。`useIsMobile` 主要服务弹层组件，业务视图缺少统一断点、底部导航、触控目标、横向滚动和表格移动模式。响应式不应只依赖组件库自身行为。

3. 构建产物预算存在“目标值”和“棘轮放行值”并存的问题。性能预算、视觉基线、主应用与 workflow-web 的产物大小需要统一口径，并按核心页面分别设限。

4. i18n 仍是预算制而非清零制。业务视图中的校验文案、空态描述和操作提示仍有硬编码中文。建议新增硬门禁、存量按模块棘轮下降。

## 三、行业对标结论

### 安全与授权

OWASP API Security Top 10（2023）把对象级授权、认证、对象属性授权、资源消耗、功能级授权列为前五类风险。本项目已有网关认证、注解权限、限流、幂等、审计和敏感数据保护基础，但“扫描出的 API 权限码如何进入角色授权、如何由前端正确消费、如何防止越权对象访问”仍缺少端到端验证。建议把对象级授权纳入 Service 层强制校验，把功能级授权纳入统一权限注册与自动化测试，并对分页、详情、导出、批量操作建立跨租户和跨角色测试矩阵。

### 测试体系

微服务测试应采用测试金字塔与契约测试结合：单元测试覆盖领域不变量；组件/集成测试覆盖数据库、事务、缓存和外部适配；Consumer-Driven Contract 覆盖前后端与 Feign 调用；少量 E2E 只验证核心业务旅程。当前 SmokeTest 数量过多、深度断言不足，建议将“接口可启动”降级为健康检查，把 CI 预算投入到真实业务断言、数据库隔离、契约漂移和核心用户旅程。

### 数据与发布

PostgreSQL 数据建模强调把业务不变量下沉到主键、唯一约束、外键、检查约束和真实查询索引上。项目已有统一 ID、审计字段、租户字段和大量普通索引，但核心表仍需补充唯一约束、外键或受控反规范化策略。数据库变更应遵循 expand/contract：先 additive 变更并双写/回填，再切换读取，最后清理旧字段；禁止把 DROP + CREATE 当升级路径。

### 前端体验与性能

WCAG 2.2 要求键盘焦点可见、可操作、状态可感知；Core Web Vitals 以真实用户 P75 为观察口径，LCP 建议不高于 2.5 秒、INP 不高于 200 毫秒、CLS 不高于 0.1。性能优化应优先减少首屏 JS、拆分按路由和重型编辑器 chunk、为图片与列表预留尺寸、将长任务移出主线程，再用实验室指标与真实用户监控共同验证。

## 四、分阶段优化路线图

### 阶段 0：P0 止血与可信门禁（建议 1～2 周）

1. 将数据库初始化与升级彻底拆分。保留一个只用于空库初始化的 bootstrap 脚本，新增 `V*.upgrade.sql` 只允许 additive / expand-contract 变更；建立脚本 lint，禁止生产升级脚本出现 `DROP TABLE`、无备份的 `DROP COLUMN`、无 WHERE 条件的大批量 DDL。同步修正 README、AGENTS.md、CLAUDE.md 中“已启用 Flyway”的表述。

2. 修复所有 Mapper XML 布尔字面量：统一使用 `0/1` 或参数绑定；增加 H2/PostgreSQL 兼容的 Mapper 集成测试，至少覆盖每个模块的“未删除”“启用”“默认”查询。SQL 审计脚本纳入 CI。

3. 建立可重复的 OpenAPI 产出机制。后端通过离线 MockMvc、测试专用运行时或 springdoc 静态扫描生成 spec，不再依赖 localhost；每个服务发布带 commit SHA 的 OpenAPI artifact。前端只保留一条生成轨，并让实际业务视图消费生成后的 endpoint client/type。

4. 修复 E2E 基础链路。统一真实容器选择器为 `#subapp-container`，把 smoke、登录、核心列表、表单、详情、审批/文件/消息等关键旅程接入 CI；首次运行前修复失败断言，禁止用 `.first()` 掩盖选择器错误。

5. 打通 API 权限码闭环。由 system 扫描并维护 API 权限注册表，userinfo 的角色权限加载器聚合 API/menu/button 三类码，`/auth/codes` 统一返回带类型标识的权限集合；前端 `v-permission` 与后端 `@AuthApiPermission` 共用同一码表，CI 校验页面权限码与后端扫描结果一致率 100%。

验收：初始化与升级脚本职责分离；生产升级脚本破坏性语句为 0；Mapper 布尔字面量违规为 0；OpenAPI 构建期产出成功率 100%；E2E smoke 在 CI 中真实通过；页面 API 权限码与后端注册码一致率 100%。

### 阶段 1：契约、测试与数据约束（建议 2～6 周）

1. 统一分页与排序契约。后端对外只暴露统一 `PageQuery` / `PageResponse` 语义，OpenAPI 声明 `pageNum`、`pageSize`、`orderItems`、筛选字段、最大页长和默认排序；前端通过生成类型约束列表请求，禁止业务页面私造排序字段。

2. 统一文件与导出契约。上传返回标准化元数据；下载统一为文件名、大小、MIME、存储 key、过期时间或下载 URL；超过阈值的导出改异步任务，返回任务 ID、进度、结果文件和失败原因；导出必须继承与列表一致的权限和数据范围。

3. 建立数据库约束矩阵。为核心表补齐唯一约束（如租户内编码、用户名、配置项、字典项、角色编码等）、外键或显式受控引用策略、检查约束和外键列索引。所有约束变更先在迁移演练环境执行 `EXPLAIN ANALYZE` 与并发写入验证。

4. 重构测试金字塔。Domain 单元测试优先覆盖状态机、金额计算、权限判断、幂等键、租户隔离和领域不变量；Repository IT 使用 Testcontainers 覆盖真实 SQL、索引命中、分页排序和逻辑删除；Feign 引入 CDC/Pact 或等价契约测试；Controller 只保留少量协议边界测试。

5. 清理接口层异常处理与事务边界。Controller 统一返回 `YdszResponse<T>` / `PageResponse<T>`，异常交由全局 advice；逐项审查 `@Transactional`，显式声明 rollbackFor、propagation、timeout，并为跨服务写操作补充 outbox、幂等键和可重试策略。

6. 建立错误码—i18n—前端展示联合门禁。后端错误码必须存在中文与英文消息；前端错误映射必须覆盖所有用户可见错误；重复错误码、缺失 i18n、静态硬编码 UI 文案均进入增量门禁。

验收：核心表唯一/外键约束覆盖率达到评审清单 100%；Domain 核心行覆盖率不低于 60%；每个业务模块至少有 Repository IT 与契约测试；增量代码行覆盖率不低于 40%；生产故障复现用例进入回归库。

### 阶段 2：前端架构与性能增强（建议 3～8 周）

1. 统一表格实现策略。优先迁移到支持服务端分页、行虚拟化、列虚拟化、固定列、排序、筛选、批量选择和键盘操作的统一表格；对确实需要 vxe 的高级场景，强制开启虚拟滚动并设置最大渲染行数，禁止 `height: 'auto'` 承载万级数据。

2. 优化请求与缓存分层。区分首屏、路由级、用户触发和后台刷新请求；列表筛选防抖，重复请求去重，缓存按租户 + 用户 + 查询条件隔离；401/403/409/422/429/5xx 分别有明确用户反馈和恢复动作。

3. 优化构建产物。主应用和 workflow-web 先做 bundle analysis，拆分设计器、LogicFlow/BPMN、图表、Excel、VXE 等重型依赖；按路由和子应用懒加载，统一 manualChunks 策略，清理重复依赖，建立 PR 体积预算与真实性能采样。

4. 建立体验基线。为核心页面定义 LCP、INP、CLS、TTFB、首屏请求数、可交互时间、表格 1 万行滚动 FPS、表单完成时间等指标；实验室 Lighthouse/Playwright 与真实用户 RUM 分开呈现，避免只优化单一环境。

5. 完善可访问性。布局加入 `<main>`、跳转主内容链接、面包屑 landmark、标题层级、焦点管理和可见 focus；表格、弹窗、抽屉、下拉、通知、表单错误和权限隐藏操作均需键盘可用；在真实业务路由上运行 axe 与人工走查。

6. 建立移动体验规范。明确手机、平板、桌面三档信息架构；高频操作使用底部可达区；复杂表格提供卡片模式；图片、地图、编辑器、审批流设置专门降级方案；触控目标满足 WCAG 2.2 要求。

验收：核心页面移动端可用性评审通过；真实路由 axe critical/serious 为 0；核心页面 P75 LCP ≤ 2.5s、INP ≤ 200ms、CLS ≤ 0.1；万行表格滚动帧率 ≥ 50 FPS；首屏 JS  gzip 按模块持续下降。

## 五、建议新增的工程门禁

后端建议增加：SQL 危险变更检测、Mapper 类型检查、OpenAPI artifact 校验、破坏性 API diff、错误码唯一性与 i18n 完整性、权限码一致性、事务边界扫描、ArchUnit、增量 JaCoCo、Testcontainers IT、契约测试和依赖漏洞扫描。

前端建议增加：TypeScript 严格检查、生成 SDK 消费率、E2E smoke、关键旅程 E2E、真实页面 a11y、视觉回归、bundle budget、Web Vitals、i18n 硬编码增量清零、敏感信息扫描和 Playwright 浏览器矩阵。

门禁应采用“新代码严格、存量棘轮”的方式，避免一次性放大维护成本。每个门槛都必须有可执行脚本、明确 owner、失败修复指引和例外白名单，禁止以注释、`|| true` 或手工跳过让质量链路假绿。

## 六、优先级汇总

| 优先级 | 核心事项 | 主要收益 |
|---|---|---|
| P0 | 数据库升级安全、Mapper 类型、OpenAPI artifact、E2E 选择器、API 权限闭环 | 防止数据丢失、发布假绿和前后端权限错位 |
| P1 | 分页/导出契约、数据库约束、测试金字塔、Controller/事务治理、错误码 i18n | 降低联调成本，提升回归可信度与线上可靠性 |
| P2 | 表格虚拟化、请求缓存、Bundle/Web Vitals、A11y、移动端、i18n 清零 | 提升规模承载能力、交互体验与产品专业度 |

## 七、最终建议

先把“可信发布”作为下一阶段第一目标：没有安全升级脚本、稳定 OpenAPI、真实 E2E 和权限闭环，后续性能与体验优化都会建立在不可验证的基础上。随后按“契约先行、测试托底、数据约束保障、体验性能并行”的顺序推进。建议每两周交付一个可观测的纵向切片，例如“用户管理从权限码注册→角色授权→前端按钮→API 鉴权→E2E 全链路”，而不是按技术层横向铺开，这样可以尽快形成端到端样板并复制到其他模块。
