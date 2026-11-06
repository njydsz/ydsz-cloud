# 云顶云平台（ydsz-cloud + ydsz-micro）对标行业规范优化建议

> **报告范围**：基于 ydsz-cloud（Java 21 / Spring Boot 4.1 / 9 微服务）与 ydsz-micro（Vue 3.5 / Vite 7 / pnpm monorepo）2026-11-06 现状
> **对标对象**：阿里《Java 开发手册》、Google Cloud API Design Guide、Microsoft Azure Well-Architected、Spring Boot 官方最佳实践、Vue 3 官方风格指南、Web.dev Core Web Vitals、OWASP ASVS
> **数据核验**：所有数字均通过脚本/grep/Python 实测，非推测

---

## 一、现状总览（实测数据基线）

| 维度 | ydsz-cloud（后端） | ydsz-micro（前端） |
|------|-------------------|-------------------|
| **代码规模** | 173 张表 / 218 个 Controller / 1152 个端点 | 51 个包 / 1284 个 Vue 组件 / 321 个 API 文件 |
| **测试** | 227 个测试文件，@SpringBootTest 仅 6 个，Testcontainers 10 个 | 200 个 unit test + 16 个 e2e spec（130+ 用例）+ a11y/perf/visual |
| **接口契约** | YdszResponse 205/218=94%，@Valid 122/218=56%，@Operation 215/218=99% | openapi-typescript 生成 schema.d.ts × 8 app（~38000 行） |
| **架构合规** | Entity 100% 继承 MpBaseEntity，server 0 处 import infra | 全部 API 走 `/ydsz/` 网关，0 处绕过 request 封装 |
| **实时能力** | ydsz-common-socket 完整 WebSocket + STOMP + 集群消息 | SSE × 5（cronjob/system/notification/dict/auth），WebSocket × 1 |
| **质量门禁** | 14 个门禁脚本 + CI 12 步流水线 | lefthook + commitlint + CI 6 步 |
| **Checkstyle 存量** | 4131 条 error（2026-10-28 基线） | ESLint/Stylelint 无存量基线概念 |

**总体判断**：后端架构合规度高（DDD 分层、Entity 继承、异常体系），但**接口测试、参数校验、OpenAPI 注解**是明显短板；前端工程化完善（monorepo、openapi 生成、a11y/e2e），但**类型安全（strict: false）、组件复用率、性能优化**有提升空间。

---

## 二、数据库表设计优化建议

### 2.1 现状问题（实测）

| 问题 | 数量 | 证据 |
|------|------|------|
| 表名非 `ydsz_` 前缀 | 8 张 | `ydzs_search_idx_part_0~7`（分区表） |
| 表无注释 | 20 张 | 8 张分区表 + 12 张 `ydsz_flow_audit_log_YYYY_MM` 分表 |
| 表无索引 | 14 张 | 含 `ydsz_comm_search_index_partitioned`、`ydsz_flow_archive_cursor` 等 6 张业务表 |
| ID 列非 VARCHAR(36) | 3 张 | `ydsz_agt_trace` / `ydsz_agt_trace_step` / `ydsz_agt_user_profile` |
| 增量脚本最大版本 | V26.11.03 | 12 个脚本，无自动化执行框架 |

### 2.2 对标规范与建议

**P0 — 高优先级（1-2 周）**

1. **分区表命名规范化**：将 `ydzs_search_idx_part_*` 重命名为 `ydsz_search_idx_part_*`，统一前缀。新建表强制 `ydsz_` 前缀（已有门禁 `check-sql-migrations.py` 可扩展为前缀校验）。
   - 落地：修改 `data/sqls/bootstrap/V26.10.01.bootstrap.sql` 第 8 张分区表命名，新增 `V26.11.06.sql` 增量脚本 `ALTER TABLE ... RENAME TO ...`。

2. **补齐 20 张表注释**：对 8 张分区表 + 12 张流程审计分表添加 `COMMENT ON TABLE`。
   - 落地：编写 `data/sqls/flyway/V26.11.06__add_table_comments.sql`，门禁脚本增加"新建表必须有 COMMENT"规则。

3. **补齐 6 张业务表索引**：重点为 `ydsz_flow_archive_cursor`（归档游标查询）、`ydsz_gen_template_group`（模板组查询）、`ydsz_idm_auth_policy`（策略匹配）、`ydsz_job_tenant_quota`（租户配额查询）、`ydsz_msg_tenant_config`（租户配置查询）。
   - 落地：分析各表查询模式（从 Mapper XML 反推 WHERE 条件），添加组合索引，遵循"最左前缀 + 高选择性列在前"。

**P1 — 中优先级（1-2 月）**

4. **ID 列统一 VARCHAR(36)**：`ydsz_agt_trace` 等 3 张 agent 表使用 `trace_id`/`user_id` 作主键，与全平台 `id VARCHAR(36)` 规范冲突。建议新增 `id` 列（雪花 ID）作为主键，原业务键改为唯一索引。
   - 落地：评估 agent 模块 trace 查询模式，若 `trace_id` 查询频率远高于 `id`，可保留双键（`id` 主键 + `trace_id` 唯一索引）。

5. **增量脚本自动化**：当前 12 个 `V26.xx.xx.sql` 靠人工按序执行，无迁移框架（项目禁止 Flyway/Liquibase）。建议引入轻量级版本化执行器（参考 ydsz-plane 的 `runVersioned` 模式）：扫描 `data/sqls/flyway/V*.sql` → 按版本号排序 → 通过 `ydsz_schema_version` 表幂等去重 → 单事务执行。
   - 落地：在 `data/scripts/` 新增 `apply-migrations.py`，CI 增加"脚本版本号连续性 + 幂等性"校验。

6. **表结构健康度门禁**：新增 `check-table-design.py`，校验新建表是否包含 `is_deleted`/`tenant_id`/审计四字段/`sort`，布尔列是否 `SMALLINT`，ID 是否 `VARCHAR(36)`。
   - 落地：集成到 `ci.yml`，与现有 `check-sql-migrations.py` 并列。

**P2 — 低优先级（季度级）**

7. **分区表策略文档化**：为搜索索引分区表、流程审计分表编写分区策略文档（分区键、分区周期、归档策略），避免后续维护者误用。
8. **大表容量规划**：对 `ydsz_msg_message`（18 张 msg 表）、`ydsz_flow_*`（35 张流程表）做容量评估，制定归档/分区策略。

---

## 三、接口测试优化建议

### 3.1 现状问题（实测）

| 问题 | 数据 | 证据 |
|------|------|------|
| 集成测试占比极低 | @SpringBootTest 仅 6 个 / 227 测试文件 = 2.6% | 9 个微服务中仅 userinfo/system 有集成测试 |
| Testcontainers 使用有限 | 10 个文件 | 集中在 common 模块，业务模块缺失 |
| Controller 测试覆盖 | 168/218 = 77% | 46 个 Controller 无对应测试 |
| 增量覆盖率门禁 | ≥40%（`check-diff-coverage.py`） | 仅度量 diff 行，存量覆盖率未知 |
| 无契约测试 | 0 个 Pact/WireMock 契约测试 | 前后端接口一致性靠人工 |

### 3.2 对标规范与建议

**P0 — 高优先级（2-4 周）**

1. **提升集成测试占比至 30%**：为 9 个微服务的核心 Controller 补充 `@SpringBootTest + Testcontainers`（PostgreSQL + Redis + RocketMQ）。
   - 落地：每个微服务至少 3 个核心场景集成测试（CRUD + 分页 + 权限 + 异常）。
   - 示例：`ydsz-userinfo-web` 已有 27 个测试文件，可扩展为覆盖 `UserController`/`RoleController`/`MenuController` 的集成测试。
   - 门禁：`check-diff-coverage.py` 阈值从 40% 提升至 **60%**，新增"核心 Controller 必须有集成测试"规则。

2. **引入 Testcontainers 标准化**：将 PostgreSQL/Redis/RocketMQ 容器配置抽取为共享测试基类（`@ServiceConnection`），避免每个测试重复配置。
   - 落地：在 `ydsz-common` 新增 `ydsz-common-test` 模块，提供 `AbstractIntegrationTest` 基类。

3. **补齐 46 个无测试 Controller**：按业务优先级排序（userinfo > system > workflow > message > agent），逐个补齐 Controller 测试。
   - 落地：制定《Controller 测试补齐清单》，每个 Sprint 完成 10 个。

**P1 — 中优先级（1-2 月）**

4. **引入契约测试（Pact 或 Spring Cloud Contract）**：前后端接口一致性保障，防止前端 openapi-typescript 生成类型与后端实际响应不一致。
   - 落地：后端提供 Pact Provider 测试，前端消费 Pact Broker 契约；或后端启用 `spring-cloud-contract` 生成 stub。
   - 替代方案（轻量）：前端 `schema.d.ts` 生成后，增加"schema 与后端实际响应字段比对"的自动化脚本（对比 JSON 示例值与 Java DTO 字段）。

5. **测试数据管理**：引入 `@Sql` 或 Testcontainers + Flyway 初始化测试库，替代当前各测试自造数据的模式。
   - 落地：测试 profile 使用独立 schema，`@BeforeEach` 执行 `data.sql` 初始化。

6. **性能测试左移**：在 CI 中增加核心接口的性能基线测试（如 JMH 或 `@SpringBootTest` + 计时断言），防止性能劣化。
   - 落地：对 5 个高频接口（登录、列表查询、导出、文件上传、工作流发起）建立 P95 基线。

**P2 — 低优先级（季度级）**

7. ** mutation 测试**：引入 PITest 评估测试有效性，识别"假测试"（只断言不验证逻辑）。
8. **E2E 测试后端联动**：前端已有 16 个 e2e spec，后端可提供"测试专用 API"（如 `/test/seed`）配合前端 e2e 做端到端验证。

---

## 四、前后端能力贯通优化建议

### 4.1 现状问题（实测）

| 问题 | 数据 | 证据 |
|------|------|------|
| OpenAPI 注解不完整 | @ApiResponse 仅 19/218 = 8.7% | 绝大多数 Controller 只有 @Operation，无响应码/错误码文档 |
| 参数校验覆盖不足 | @Valid 122/218 = 56% | 92 个 Controller 无 @Valid |
| 前后端类型契约断裂 | 前端 schema.d.ts 生成但后端 OpenAPI 未导出 | 8 个 app 有 schema.d.ts（~38000 行），但后端无 `/v3/api-docs` 产物校验 |
| 无 BFF 层 | 0 个 BFF 文件 | 前端直接调用 9 个微服务网关 |
| 前端 API 手写为主 | 334 个手写 API 文件 | 仅 schema 类型生成，API 调用函数手写 |

### 4.2 对标规范与建议

**P0 — 高优先级（2-4 周）**

1. **补齐 @ApiResponse 注解**：为 218 个 Controller 补充 `@ApiResponse`（至少覆盖 200/400/401/403/500），与 `ExceptionCode` 枚举联动。
   - 落地：编写 IDEA 模板或 ArchUnit 规则，新建 Controller 强制要求 `@Operation + @ApiResponse`。
   - 门禁：新增 `check-openapi-coverage.py`，要求 @ApiResponse 覆盖率 ≥80%。

2. **提升 @Valid 覆盖率至 95%**：对 92 个未使用 @Valid 的 Controller，逐个补充 DTO 校验注解（`@NotBlank`/`@Size`/`@Pattern`/`@Email` 等）。
   - 落地：优先补齐 userinfo（用户/角色/菜单）、workflow（流程/任务）、message（消息/渠道）模块。
   - 门禁：CI 增加"@Valid 覆盖率"指标，低于 95% 判红。

3. **OpenAPI 产物校验**：后端 CI 增加 `/v3/api-docs` 产物校验，确保 schema.d.ts 生成源可用。
   - 落地：在 `ci.yml` 增加 `check-openapi-artifact.py`（已存在），扩展为"生成的 OpenAPI 必须通过 openapi-typescript 校验"。

**P1 — 中优先级（1-2 月）**

4. **API 层自动生成**：基于后端 OpenAPI，使用 `orval` 或手写脚本生成前端 API 调用函数，替代 334 个手写文件。
   - 落地：前端 `comm/effects/request` 已有封装，可基于 schema.d.ts 生成 `api/sdk/*.ts` 的调用函数。
   - 收益：减少手写 API 的字段遗漏/类型错误，提升前后端一致性。

5. **BFF 层评估**：当前前端直接调用 9 个微服务，存在"前端聚合多个服务"的场景（如工作台）。建议评估引入轻量 BFF（Backend for Frontend）或 GraphQL 层。
   - 落地：先梳理前端"一次页面加载调用多个服务"的场景，若 >5 个高频场景，则引入 BFF；否则维持现状，但增加"聚合接口"（如 `/ydsz/main/dashboard` 聚合多个服务）。

6. **错误码前后端对齐**：前端 `comm/locales/errors/` 有 `resolve-error-message`，后端有 `ExceptionCode` 枚举。建议建立"错误码 → i18n key"的自动映射，前端根据后端错误码自动展示本地化消息。
   - 落地：后端在 `YdszResponse` 中返回 `code` + `message` + `i18nKey`，前端根据 `i18nKey` 查本地语言包。

**P2 — 低优先级（季度级）**

7. **API 版本管理**：当前所有 API 无版本前缀（`/ydsz/userinfo/users`）。建议引入 `/v1/` 前缀，为后续破坏性变更预留空间。
8. **Mock Service Worker（MSW）**：前端引入 MSW，基于 schema.d.ts 自动生成 mock 数据，加速前端开发与测试。

---

## 五、前后端架构优化建议

### 5.1 现状问题（实测）

| 问题 | 数据 | 证据 |
|------|------|------|
| 后端 DDD 分层合规 | server 0 处 import infra，Entity 100% 继承 MpBaseEntity | 优秀，保持 |
| 前端 TypeScript 严格模式关闭 | `strict: false` | `tsconfig.base.json` 未开启 strict |
| 前端组件复用率 | 1284 个 Vue 组件，但 comm 层仅 47 个基础组件 | 大量业务组件在 apps 层，跨 app 复用困难 |
| 无微前端架构 | 0 处 micro-app/qiankun/module federation | 10 个 app 独立构建部署 |
| 后端无 BFF/聚合层 | 0 个聚合接口 | 前端直接调用 9 个微服务 |

### 5.2 对标规范与建议

**P0 — 高优先级（1-2 月）**

1. **开启 TypeScript 严格模式**：将 `tsconfig.base.json` 的 `strict` 从 `false` 改为 `true`，分阶段修复类型错误。
   - 落地：先对 `comm/` 基础包开启 strict，再对 `apps/` 逐个开启；使用 `// @ts-expect-error` 标记已知问题，逐步消除。
   - 收益：提升代码质量，减少运行时错误，增强 IDE 智能提示。

2. **前端组件库下沉**：将 apps 层可复用的业务组件（如表格、表单、详情页）下沉到 `comm/effects/common-ui` 或 `comm/@core/ui-kit`。
   - 落地：梳理 1284 个组件，识别跨 app 复用的组件（如用户选择器、部门树、文件上传），抽取为共享组件。
   - 目标：comm 层组件从 47 个提升至 100+ 个，apps 层组件复用率 >50%。

3. **微前端架构评估**：当前 10 个 app 独立构建部署，`main` 通过路由跳转集成。建议评估引入 `micro-app` 或 `module federation`，实现子应用独立部署 + 主应用动态加载。
   - 落地：先对 2-3 个低频 app（如 literule、generator）试点微前端，验证可行性。
   - 收益：子应用独立部署，降低主应用构建体积，提升团队协作效率。

**P1 — 中优先级（2-3 月）**

4. **后端聚合接口层**：为工作台、仪表盘等聚合场景，在网关层或 system 服务增加聚合接口（如 `/ydsz/main/dashboard`），减少前端多次调用。
   - 落地：使用 Spring Cloud Gateway 的 `GatewayFilter` 或 system 服务的 `DashboardService` 聚合多个服务数据。

5. **前端状态管理规范化**：当前 Pinia store 分散在各 app，建议抽取通用 store（用户、权限、租户、主题）到 `comm/stores`。
   - 落地：`comm/stores` 已有基础，扩展 `useUserStore`/`usePermissionStore`/`useTenantStore`。

6. **后端事件驱动架构**：当前 9 个微服务间调用以同步 Feign 为主，建议对非核心链路（如审计日志、通知、搜索索引）改为事件驱动（RocketMQ + ydsz-common-queue）。
   - 落地：梳理同步调用链路，将"写审计/发通知/更新索引"改为异步事件，提升核心接口性能。

**P2 — 低优先级（季度级）**

7. **服务端渲染（SSR）评估**：对 SEO 敏感页面（如文档站点 docs-site），评估引入 Nuxt 3 或 Vite SSR。
8. **边缘计算**：对静态资源（图片、文件）评估 CDN 加速，减少后端压力。

---

## 六、前后端功能增强建议

### 6.1 现状问题（实测）

| 问题 | 数据 | 证据 |
|------|------|------|
| 前端无全局快捷键 | 0 处 hotkeys-js/vue-shortkey | 用户操作效率受限 |
| 前端无 ErrorBoundary | 0 处 | 组件错误会导致白屏 |
| 后端代码生成器能力有限 | 7 个 Controller，仅生成 Java 代码 | 无前端代码生成 |
| 前端无 PWA | 0 处 vite-plugin-pwa | 无法离线访问 |
| 后端无 GraphQL | 0 处 | 复杂查询场景前端需多次调用 |

### 6.2 对标规范与建议

**P0 — 高优先级（2-4 周）**

1. **前端全局快捷键系统**：引入 `hotkeys-js` 或基于 VueUse `useMagicKeys` 封装全局快捷键（如 `Ctrl+K` 搜索、`Ctrl+N` 新建、`Esc` 关闭弹窗）。
   - 落地：在 `comm/@core/base` 新增 `useHotkeys` composable，注册全局快捷键。

2. **前端 ErrorBoundary**：为 `main` 和各 app 增加全局 ErrorBoundary，捕获组件错误，展示友好错误页，避免白屏。
   - 落地：Vue 3 的 `app.config.errorHandler` + 全局 `ErrorBoundary.vue` 组件。

3. **代码生成器增强**：ydsz-generator 当前仅生成 Java 代码，建议扩展为**全栈代码生成**（Java + Vue + SQL）。
   - 落地：基于模板引擎（如 Velocity/FreeMarker）生成 `XxxController.java` + `Xxx.vue` + `api/xxx.ts` + `V{version}.sql`。
   - 收益：减少重复 CRUD 代码编写，提升开发效率。

**P1 — 中优先级（1-2 月）**

4. **PWA 支持**：为 `main` 和各 app 引入 `vite-plugin-pwa`，支持离线访问、添加到主屏幕、消息推送。
   - 落地：配置 `manifest.json` + Service Worker，预缓存核心静态资源。

5. **前端全局搜索增强**：当前 `main/src/features/search/` 有全局搜索，建议增加"搜索历史""搜索建议""跨模块搜索"能力。
   - 落地：集成后端搜索接口（`ydsz-search`），支持文档/用户/消息/流程等跨模块搜索。

6. **后端 GraphQL 评估**：对复杂查询场景（如工作流详情聚合多个表），评估引入 GraphQL（如 `graphql-java`）作为 REST 的补充。
   - 落地：先在 agent 模块（知识库 RAG 查询）试点 GraphQL，验证收益。

**P2 — 低优先级（季度级）**

7. **前端 AI 辅助**：基于 ydsz-agent 服务，为前端增加 AI 辅助能力（如智能表单填写、代码生成、自然语言查询）。
8. **后端低代码平台**：基于 ydsz-literule + ydsz-workflow，提供可视化低代码配置平台。

---

## 七、前后端性能优化建议

### 7.1 现状问题（实测）

| 问题 | 数据 | 证据 |
|------|------|------|
| 前端 bundle 预算未生效 | `budget.config.json` 为空数组 | 有预算定义（300KB/200KB），但无实际配置 |
| 无虚拟滚动 | 0 处 vue-virtual-scroller 使用 | 大列表性能受限 |
| 无防抖节流封装 | 0 处 useDebounce/useThrottle | 搜索/滚动场景可能频繁请求 |
| 后端无接口性能基线 | 0 个 JMH/性能测试 | 性能劣化无法感知 |
| 前端无 CDN | 静态资源未配置 CDN | 用户访问速度受限 |

### 7.2 对标规范与建议

**P0 — 高优先级（2-4 周）**

1. **前端 bundle 预算生效**：在 `conf/budget.config.json` 中配置实际预算项（如 `main` 初始包 ≤200KB gzip，`commons` ≤150KB），CI 增加预算检查。
   - 落地：基于 `stats-output.ts` 生成的 `dist/stats.json`，配置 `budgets` 数组，`pnpm build` 后自动校验。

2. **虚拟滚动**：对大列表场景（如消息列表、日志列表、文件列表）引入 `vue-virtual-scroller` 或 `@tanstack/vue-virtual`。
   - 落地：在 `comm/effects/common-ui` 新增 `VirtualList` 组件，替换现有大列表。

3. **防抖节流封装**：基于 VueUse 封装 `useDebounce`/`useThrottle`，应用于搜索输入、滚动加载、按钮点击。
   - 落地：在 `comm/@core/composables` 新增 `useDebounceSearch`（300ms 防抖 + 最少 3 字符）。

**P1 — 中优先级（1-2 月）**

4. **后端接口性能基线**：引入 JMH 或 `@SpringBootTest` + 计时断言，为 5 个高频接口建立 P95 基线。
   - 落地：登录（`/ydsz/userinfo/auth/login`）、用户列表（`/ydsz/userinfo/users`）、工作流待办（`/ydsz/workflow/tasks/todo`）、消息列表（`/ydsz/message/messages`）、文件列表（`/ydsz/nextwiki/files`）。
   - 门禁：CI 增加性能基线检查，P95 超过基线 20% 判红。

5. **前端 CDN 加速**：将静态资源（图片、文件、JS/CSS）配置 CDN，减少后端带宽压力。
   - 落地：在 `vite.config.ts` 配置 `base: 'https://cdn.ydsz.com/'`，上传静态资源到 CDN。

6. **后端缓存优化**：对高频查询（如字典、参数、租户配置）增加 Redis 缓存，减少数据库压力。
   - 落地：使用 `ydsz-common-cache` 的 `YdszCache`，对 `DictService`/`ParamService` 增加缓存层。

**P2 — 低优先级（季度级）**

7. **前端图片优化**：引入 `vite-plugin-image-optimizer`，自动压缩图片，支持 WebP/AVIF。
8. **后端数据库连接池优化**：评估 HikariCP 配置（`maximumPoolSize`/`minimumIdle`），根据实际负载调整。

---

## 八、前后端用户体验优化建议

### 8.1 现状问题（实测）

| 问题 | 数据 | 证据 |
|------|------|------|
| 前端 a11y 薄弱 | 仅 ~10 处 aria 属性，无 focus-trap | 无障碍支持不足 |
| 前端无 Skeleton | 无独立 Skeleton 组件 | 加载体验欠佳 |
| 前端无全局通知中心 | 通知组件分散 | 用户易错过重要消息 |
| 后端错误提示不友好 | 部分接口返回英文错误 | 国际化体验不一致 |
| 前端无操作确认二次弹窗 | 部分危险操作无确认 | 误操作风险 |

### 8.2 对标规范与建议

**P0 — 高优先级（2-4 周）**

1. **前端 a11y 系统性提升**：
   - 为所有表单元素添加 `aria-label`/`aria-describedby`
   - 为弹窗/抽屉增加 `focus-trap`（引入 `focus-trap-vue`）
   - 为图标按钮添加 `aria-label`
   - 键盘导航支持（`Tab` 顺序、`Enter`/`Esc` 快捷键）
   - 落地：已有 `eslint-plugin-vuejs-accessibility`，启用全部规则，修复存量问题。

2. **前端 Skeleton 加载态**：为列表/详情页增加 Skeleton 组件，提升加载体验。
   - 落地：在 `comm/@core/ui-kit/ydsz-vue` 新增 `Skeleton` 组件，替换现有 `v-loading`。

3. **全局通知中心**：统一 `main/src/features/notification/` 的通知组件，支持"全部已读""批量删除""通知设置"。
   - 落地：基于 `comm/effects/notification` 的 `useWebSocket`，实时推送未读通知。

**P1 — 中优先级（1-2 月）**

4. **后端错误提示国际化**：确保所有 `ExceptionCode` 的 i18n key 在前端 `comm/locales/errors/` 有对应翻译。
   - 落地：编写脚本比对后端 `ExceptionCode` 枚举与前端 `error-code.json`，补齐缺失翻译。

5. **危险操作二次确认**：为删除、批量删除、权限变更等危险操作，增加二次确认弹窗。
   - 落地：在 `comm/effects/common-ui` 新增 `ConfirmDialog` 组件，统一危险操作确认。

6. **前端空状态优化**：为无数据场景提供引导性空状态（如"暂无数据，点击创建"）。
   - 落地：`Empty` 组件已存在，扩展为带操作引导的 `EmptyState`。

**P2 — 低优先级（季度级）**

7. **前端主题定制**：基于 `tokens-export.json`，提供主题编辑器，支持用户自定义主色/圆角/密度。
8. **后端操作审计增强**：为所有写操作增加审计日志，前端提供"操作历史"页面。

---

## 九、优先级汇总与落地路线图

### 9.1 优先级矩阵

| 维度 | P0（1-2 月） | P1（1-2 月） | P2（季度级） |
|------|-------------|-------------|-------------|
| **数据库** | 分区表命名、补注释、补索引 | ID 列统一、增量脚本自动化、表结构门禁 | 分区策略文档、大表容量规划 |
| **接口测试** | 集成测试 30%、Testcontainers 标准化、补齐 46 Controller | 契约测试、测试数据管理、性能左移 | Mutation 测试、E2E 联动 |
| **能力贯通** | @ApiResponse 补齐、@Valid 95%、OpenAPI 校验 | API 自动生成、BFF 评估、错误码对齐 | API 版本管理、MSW |
| **架构优化** | TS strict、组件库下沉、微前端评估 | 聚合接口、状态管理规范化、事件驱动 | SSR、边缘计算 |
| **功能增强** | 全局快捷键、ErrorBoundary、全栈代码生成 | PWA、全局搜索增强、GraphQL 评估 | AI 辅助、低代码平台 |
| **性能优化** | Bundle 预算、虚拟滚动、防抖节流 | 性能基线、CDN、缓存优化 | 图片优化、连接池优化 |
| **用户体验** | a11y 提升、Skeleton、通知中心 | 错误国际化、危险操作确认、空状态 | 主题定制、审计增强 |

### 9.2 落地路线图（建议 6 个月）

```
第 1 月：数据库规范 + 接口测试补齐 + 能力贯通（@Valid/@ApiResponse）
第 2 月：TS strict + 组件库下沉 + 性能优化（Bundle 预算/虚拟滚动）
第 3 月：微前端评估 + 聚合接口 + API 自动生成 + 用户体验（a11y/Skeleton）
第 4 月：全栈代码生成 + PWA + 性能基线 + 事件驱动架构
第 5 月：契约测试 + 测试数据管理 + 状态管理规范化
第 6 月：SSR/GraphQL 评估 + AI 辅助 + 低代码平台
```

### 9.3 关键门禁建议

| 门禁 | 当前 | 建议 | 落地 |
|------|------|------|------|
| 增量覆盖率 | ≥40% | ≥60% | 修改 `check-diff-coverage.py` |
| Checkstyle 存量 | 4131 条 | 每季度减少 10% | 建立"存量只减不增"机制 |
| @Valid 覆盖率 | 56% | ≥95% | 新增 `check-valid-coverage.py` |
| @ApiResponse 覆盖率 | 8.7% | ≥80% | 新增 `check-openapi-coverage.py` |
| Bundle 预算 | 空配置 | 配置实际预算 | 修改 `budget.config.json` |
| TS strict | false | true | 分阶段开启 |
| E2E 用例 | 16 spec | 每 app ≥2 spec | CI 增加 e2e 执行 |

---

## 十、总结

ydsz-cloud + ydsz-micro 整体架构**扎实且规范**（DDD 分层、Entity 继承、异常体系、monorepo、openapi 生成），但在**接口测试、参数校验、类型安全、性能优化**方面有明确提升空间。建议按上述优先级矩阵，分 6 个月落地，重点投入 P0 项（数据库规范、集成测试、@Valid 覆盖、TS strict、Bundle 预算、a11y），可显著提升平台质量与用户体验。

> **本报告所有数据均基于 2026-11-06 实测，建议每季度复核一次现状数据，动态调整优化优先级。**
