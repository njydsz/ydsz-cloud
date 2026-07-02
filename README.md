# YDSZ PMIS · 项目运营管理系统

> 南京云顶数字科技有限公司 · 软件开发 + 人力外包 双业态 · 业财一体化精细化运营平台
>
> **状态**: `v1.0.0 GA` · **最近交付**: 批次 25 (2026-07-02) · **构建**: 后端 14 模块 `mvn test` 1348+ / 前端 22 测试文件 190+ / 0 error

[![Backend](https://img.shields.io/badge/Spring%20Boot-3.x-6DB33F?logo=springboot)]()
[![Frontend](https://img.shields.io/badge/Vue-3.5-4FC08D?logo=vuedotjs)]()
[![Java](https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk)]()
[![TS](https://img.shields.io/badge/TypeScript-5.x-3178C6?logo=typescript)]()
[![DB](https://img.shields.io/badge/PostgreSQL-18-336791?logo=postgresql)]()
[![License](https://img.shields.io/badge/license-Proprietary-red)]()

---

## 一、一句话定位

以 **WBS 业财一体化锚点** 为底座，以 **EVM 挣值管理** 为预警引擎，以 **双费率(对外报价 + 对内成本)** 为利润核算基础，串联「商机→立项→合同→执行→回款→结项→售后」全生命周期的项目运营管理系统。

## 二、核心数据(v1.0 GA)

| 维度 | 数字 | 说明 |
|------|------|------|
| 后端微服务 | **14 个** | gateway/common/auth/user/config/file/audit/message/notification/workflow/scheduler/project/execution/agent + 父 pom |
| 后端测试 | **172 测试类 / 1348+ 用例 / 100%** | `mvn test` BUILD SUCCESS 跨 14 模块 |
| 前端测试 | **22 文件 / 190+ 用例 / 100%** | vitest 190 用例 + Playwright 4 E2E(批次 25 增) |
| Controller 数 | **66+** | execution 27 + project 8 + user 15 + workflow 3 + 其余 13+ |
| 业务 Service | **120+** | execution 28 + project 8 + user 29 + 其余 50+ |
| 业务枚举 | **70+** | 状态机 / 业务码 / 审批流 |
| 权限码 | **200+** | 前后端 `PermissionCodes` 一一对应 |
| 状态机 | **30+** | OpportunityStatus / InitiationStage / ContractStatus / WbsTaskStatus / InvoiceStatus / PaymentStatus / ChangeStatus / ClosureStatus / WarrantyStatus / OpsTicketStatus 等 |
| 计算引擎 | **13 个** | EvmCalculator / ProfitCalculator / BudgetGuard / WinRateEvaluator / ContractRiskEvaluator / ChangeImpactEvaluator / StageGateValidator / ClosureAdmissionValidator / CreditScoreEvaluator / RiskScoreEvaluator / DualRateProfitCalculator / SlaCalculator / TimeEntryValidator |
| AI Agent | **5 + 4 编排** | RiskWarning / ResourceRecommend / ProfitForecast / WinRatePredict / TimesheetAnomaly · 4 策略(SEQUENTIAL/PARALLEL/VOTING/CASCADE) |
| LLM Provider | **5** | Mock / DashScope(通义千问) / Qianfan(文心) / SpringAI / LlmProviderRouter |
| 业务页面 | **35+** | 一/二/三期全部交付 |
| SQL Flyway 脚本 | **39 个** | V1.0.0_001 ~ V1.0.0_040 |
| 聚合 SQL 视图 | **5 张** | pmis_view_initiation_revenue_cost / pmis_view_initiation_evm / pmis_view_cockpit_overview / pmis_view_risk_dashboard / pmis_view_employee_utilization |
| 批次交付 | **26 批次** | 批次 1-25 已完成 ✅ · 批次 26 (等保测评) 待 SRE 启动 |

## 三、核心特性

### 3.1 业务能力

- **项目全生命周期**: 商机 A/B/C 分级 → 立项(WBS 预算) → 合同(模板/补充/变更/风险) → 执行(WBS/工时/采购/费用) → 成本归集 → 收入确认(终验/里程碑/月) → 利润核算 → 开票/回款 → 变更管理 → 项目结项 → 售后(质保/工单/满意度)
- **EVM 挣值管理**: PV/EV/AC 三量 + CPI/SPI 偏差指数 + EAC/VAC 预测 + 红/黄/绿阈值
- **双费率利润**: 对外 Rate Card(职级+技术栈+客户三元组) + 对内 RateInternal(职级+部门) + 双率对比 + 多版本模拟
- **资源池与 Bench**: 三级池(总部 L13+ / 事业部 L4-L12 / 备用 L1-L3) + 标签 + 预占 + 冲突处理 + Bench 自动入出池 + 闲置成本量化
- **经营驾驶舱**: 6 大 KPI + 3 维下钻(部门/项目类型/客户) + 高管看板 + KPI 趋势 + 预警 banner + 60s 实时刷新
- **AI 多智能体编排**: Blackboard 共享上下文 + 4 编排策略(串行/并行/投票/级联) + 5 内置 Agent + LLM Provider 路由
- **混沌工程**: ChaosService 实验注册 + FeatureFlag 双保险 + 注入统计 + chaos-dashboard 实时监控(批次 24)
- **变更-交付-结项闭环**: 5 类变更(范围/成本/合同/人员/进度) + 8 类项目交付物标准化(CD1-CD5 门径) + 3 类结项(正式/预/强制)准入

### 3.2 技术能力

- **架构**: Spring Boot 3 + Spring Cloud Alibaba 2023 + Nacos + Sentinel + Seata + RocketMQ
- **前端**: Vue 3.5 + TS 5 + Vite 5.4 + Element Plus 2.8 + vxe-table 4 + ECharts 5.5 + Pinia 2
- **数据**: PostgreSQL 18(主) + Redis 7(缓存/分布式锁/会话) + 聚合 SQL View(零 Java JOIN)
- **可观测性**: Sentry(异常) + Logback(链路 TraceId) + Prometheus + Grafana + ELK
- **安全**: 等保 2.0 三级 · AES-256 + SM4 字段加密 · 7 种脱敏策略 · TOTP 2FA · DataScope 6 模式 · 操作/登录/导出/敏感四类审计
- **质量**: 后端 `mvn test` 100% · 前端 `vitest` 100% · `vue-tsc --noEmit` 0 错 · `eslint` 增量 0 错 · Checkstyle 已启用并在 CI 中执行(`failsOnError=true`、`violationSeverity=error`)
- **CI 质量门禁(P0-3)**: SonarQube 扫描已接入 CI,需配置 `SONAR_TOKEN` secret(未配置时自动跳过,后端 `mvn verify sonar:sonar` / 前端 `npx sonar-scanner`) · OWASP dependency-check 已集成,初期 `continue-on-error` 不阻断构建,后续逐步收紧 · JaCoCo 覆盖率已启用为 SonarQube 提供数据 · ZAP baseline(安全扫描,规划中)
- **工程化**: OpenFeign + FallbackFactory · 自研工作流引擎(pmis_flow_*) · JobHandler 跨模块调度 · vxe-table 通用列表组件 · vite-plugin-mock 独立开发

## 四、技术架构

### 4.1 技术选型速查

| 层 | 关键依赖 | 版本 | 用途 |
|---|---|---|---|
| 前端框架 | Vue / Vite / TS | 3.5 / 5.4 / 5.x | 核心 + 构建 + 类型 |
| 前端 UI | Element Plus / vxe-table / ECharts | 2.8 / 4.12 / 5.5 | 组件库 + 高级表格 + 可视化 |
| 前端状态 | Pinia / Vue Router | 2.2 / 4.4 | 状态 + 路由 |
| 前端测试 | vitest / Playwright | 2.1 / 1.49 | 单元 + E2E |
| 后端框架 | Spring Boot / Spring Cloud Alibaba | 3.x / 2023.x | 微服务 |
| 后端 ORM | MyBatis-Plus | 3.5+ | 持久层 |
| 后端治理 | Nacos / Sentinel / Seata / OpenFeign | 2.x / 1.x / 2.x / 4.x | 注册/限流/事务/调用 |
| 数据库 | PostgreSQL | 18 | 主库(分年度分表) |
| 缓存/锁 | Redis | 7 | 会话/分布式锁/幂等 |
| 消息 | RocketMQ | 5.x | 异步事件 |
| 调度 | XXL-JOB | 2.4+ | 分布式任务 |
| AI | Spring AI + AgentScope | - | 多智能体编排 |
| 监控 | Prometheus + Grafana + Sentry + ELK + SkyWalking | - | 指标 + 链路 + 日志 |

### 4.2 微服务清单(14 模块)

| 模块 | artifactId | 端口 | 职责 |
|---|---|---|---|
| API 网关 | ydsz-pmis-gateway | **9000** | 路由 + 鉴权 + 限流 + CORS |
| 公共 | ydsz-pmis-common | — | 统一响应/AOP/注解/Feign/敏感数据/JobHandler |
| 认证 | ydsz-pmis-auth | **9001** | 登录/Token/2FA/登录审计 |
| 用户与资源 | ydsz-pmis-user | **9002** | 用户/角色/部门/考勤/**资源池**/**Bench** |
| 通知 | ydsz-pmis-notification | **9013** | 站内消息/邮件/短信/推送 |
| 工作流 | ydsz-pmis-workflow | **9014** | 自研 pmis_flow_* + BPMN 2.0 解析 |
| 项目 | ydsz-pmis-project | **9015** | 商机/立项/合同/补充/变更/模板 |
| 执行 + 财务 + 报表 | ydsz-pmis-execution | **9016** | WBS/工时/成本/EVM/双费率/**开票/回款/信用**/**驾驶舱/高级报表** |
| AI Agent | ydsz-pmis-agent | **9017** | 5 Agent + 4 编排 + 5 LLM Provider |
| 配置中心 | ydsz-pmis-config | **9018** | 枚举/字典/系统配置/特性开关/混沌配置 |
| 文件 | ydsz-pmis-file | **9019** | MinIO/OSS 上传下载 |
| 审计 | ydsz-pmis-audit | **9020** | 操作/登录/导出/敏感 4 类审计 |
| 消息模板 | ydsz-pmis-message | **9021** | 邮件/短信/站内 ${var} 模板 |
| 调度 | ydsz-pmis-scheduler | **9022** | XXL-JOB 调度 + JobHandler 注册 |

> **架构决策(2026-07-01 修订)**: 原规划 11 微服务,落地时合并 finance/resource/report 到 execution/user/execution(过度拆分导致运维成本与跨服务调用复杂度反增),最终交付 8 业务 + 6 支撑 = 14 模块,172 测试类 100% 通过。详细映射见 [开发计划 5.1 节](file:///d:/Code/ydsz/ydsz-pmis/开发计划.md#五-1-服务拆分)。

### 4.3 模块依赖拓扑

```text
gateway → auth / user / project / execution / agent / notification / file / config / workflow
execution → project / user(Feign) / message / common / scheduler(JobHandler)
project → user(Feign) / workflow(Feign) / common
agent → common / project(Feign) / execution(Feign)
audit → common / user(Feign)
message → common
workflow → common
notification → common / user(Feign) / project(Feign) / execution(Feign)
scheduler → common.feign(ExecutionClient)        # 批次 17: JobHandler 迁至 common,打破循环依赖
```

## 五、快速开始

### 5.1 环境要求

| 工具 | 版本 |
|---|---|
| JDK | 21 |
| Maven | 3.9+ |
| Node.js | ≥ 20 |
| pnpm | ≥ 9 |
| PostgreSQL | 18 |
| Redis | 7 |
| Nacos | 2.x |

### 5.2 本地启动

```bash
# 1. 启动基础设施 (Nacos / Postgres / Redis / MinIO)
cd deploy/docker && docker compose -f docker-compose.base.yml up -d

# 2. 初始化数据库 (39 个 Flyway 脚本自动执行)
psql -U pmis -d pmis -f deploy/sql/V1.0.0_001__init_pmis_schema.sql
# ... 或通过 Spring Boot 启动时 Flyway 自动迁移

# 3. 启动后端 (按依赖顺序)
mvn -pl ydsz-pmis-common,ydsz-pmis-user,ydsz-pmis-auth,ydsz-pmis-config \
    -am install -DskipTests
mvn -pl ydsz-pmis-gateway spring-boot:run   # 端口 9000
# 其它模块同理 spring-boot:run

# 4. 启动前端
cd ydsz-pmis-frontend
pnpm install
pnpm dev    # http://localhost:5173
```

### 5.3 测试命令

```bash
# 后端 - 14 模块 100% 通过
mvn -pl ydsz-pmis-backend -am test

# 前端单元 + 组件
cd ydsz-pmis-frontend && pnpm test

# 前端类型检查
pnpm type-check

# 前端 E2E (Playwright 4 用例)
pnpm test:e2e:smoke                # 冒烟
pnpm test:e2e                      # 全量(03 业务流)

# 性能 (6 JMeter 场景)
jmeter -n -t deploy/perf/jmeter/01-core-read.jmx
```

## 六、仓库结构

```text
ydsz-pmis/
├── ydsz-pmis-backend/          # 后端 14 模块聚合工程
│   ├── ydsz-pmis-gateway/      # 9000 API 网关
│   ├── ydsz-pmis-common/       # 公共组件 (50+ 测试类)
│   ├── ydsz-pmis-auth/         # 9001 认证
│   ├── ydsz-pmis-user/         # 9002 用户/资源/Bench (29 Service)
│   ├── ydsz-pmis-config/       # 9018 枚举/字典/特性开关/混沌
│   ├── ydsz-pmis-file/         # 9019 文件
│   ├── ydsz-pmis-audit/        # 9020 4 类审计
│   ├── ydsz-pmis-message/      # 9021 消息模板
│   ├── ydsz-pmis-notification/ # 9013 通知
│   ├── ydsz-pmis-workflow/     # 9014 自研工作流
│   ├── ydsz-pmis-scheduler/    # 9022 XXL-JOB
│   ├── ydsz-pmis-project/      # 9015 项目 (8 Service)
│   ├── ydsz-pmis-execution/    # 9016 执行/财务/报表 (28 Service)
│   └── ydsz-pmis-agent/        # 9017 AI Agent
├── ydsz-pmis-frontend/         # 前端 (Vue 3.5 + Vite 5.4)
│   ├── src/api/                # 1:1 后端 Controller 封装
│   ├── src/views/              # 35+ 业务页面
│   ├── src/components/common/  # vxe-table 通用列表 + 抽屉表单
│   ├── src/composables/        # useECharts / useFeatureFlag / useReAuth
│   ├── e2e/                    # Playwright 4 E2E
│   └── vitest.config.ts        # 22 测试文件 190+ 用例
├── deploy/                     # 部署全套
│   ├── docker/                 # 14 模块 Dockerfile
│   ├── ansible/                # 多环境编排
│   ├── argo-rollouts/          # 金丝雀(批次 23)
│   ├── sql/                    # 39 Flyway 脚本
│   ├── functional-test/        # UAT + Postman 32 端点
│   ├── perf/jmeter/            # 6 性能场景
│   ├── security/               # 等保 / OWASP / crypto-verify
│   ├── backup/                 # pg_backup + 增量
│   ├── migration/              # 历史数据迁移
│   └── monitoring/             # Prometheus / Grafana / Sentry
├── docs/                       # 完整文档库
│   ├── standards/              # 9 份大厂规范(API/DB/前端/后端/...)
│   ├── operations/             # 运维手册 / 上线 checklist
│   ├── security/               # 等保三级 73 项
│   ├── perf/                   # 性能基线报告
│   ├── rules/                  # 业务规则总册
│   └── pmis-prd-v3.md          # PRD V3.2 需求详细设计
├── 开发计划.md                  # 完整开发计划书(批次化交付 + 验收)
└── README.md                   # 本文件
```

## 七、核心业务规则速查

### 7.1 职级费率体系(L1-L18)

| 职级段 | 月工资 | 公司月总成本 | 对内人天 | 对外人天 | 资源池 |
|---|---|---|---|---|---|
| L1-L3 | 4.5K-5.5K | 6.1K-7.5K | 281-344 | 422-516 | 备用池 |
| L4-L6 | 6K-8K | 8.2K-10.9K | 375-500 | 563-750 | 事业部 |
| L7-L9 | 9K-12K | 12.2K-16.3K | 563-750 | 844-1125 | 事业部 |
| L10-L12 | 13K-16K | 17.7K-21.8K | 814-1001 | 1221-1501 | 事业部(预警减半) |
| L13-L15 | 17K-20K | 23.1K-27.2K | 1063-1251 | 1595-1876 | 总部池 |
| L16-L18 | 19K-21K | 24.6K-27.2K | 1131-1251 | 1697-1876 | 总部池(战略层) |

### 7.2 预警阈值

| 指标 | 绿色 | 黄色 | 红色 |
|---|---|---|---|
| CPI 成本绩效 | ≥ 0.95 | 0.85-0.95 | < 0.85 |
| SPI 进度绩效 | ≥ 0.95 | 0.85-0.95 | < 0.85 |
| 预算占用率 | < 80% | 80%-95% | ≥ 95% |
| Bench 闲置 | — | 7 天 | 15 天 |
| L10+ Bench | — | 3 天(减半) | 7 天(减半) |
| 客户信用 | A 90+ | B 75-89 / C 60-74 | D < 60 |

### 7.3 状态机收敛点(部分)

- **InvoiceStatus**: DRAFT→SUBMITTED→APPROVED→ISSUED→(RED_REVERSED / CANCELLED 终态)
- **ContractStatus**: DRAFT→REVIEWING→APPROVED→SIGNED→(CLOSED / TERMINATED)
- **ChangeStatus**: DRAFT→SUBMITTED→UNDER_REVIEW→APPROVED/REJECTED→(CLOSED / CANCELLED)
- **ClosureStatus**: DRAFT→SUBMITTED→APPROVED→(ARCHIVED / REJECTED)
- **WbsTaskStatus**: PLANNED→IN_PROGRESS→(BLOCKED)→IN_REVIEW→COMPLETED / CANCELLED

## 八、文档导航

### 8.1 立项与需求

| 文档 | 链接 |
|---|---|
| PRD 需求详细设计 V3.2 | [docs/pmis-prd-v3.md](file:///d:/Code/ydsz/ydsz-pmis/docs/pmis-prd-v3.md) |
| PRD HTML 版 | [docs/pmis-prd-v3.html](file:///d:/Code/ydsz/ydsz-pmis/docs/pmis-prd-v3.html) |
| 完整开发计划书 | [开发计划.md](file:///d:/Code/ydsz/ydsz-pmis/开发计划.md) |

### 8.2 工程规范(9 份大厂标准)

| 规范 | 链接 |
|---|---|
| API 接口规范 | [docs/standards/api-spec.md](file:///d:/Code/ydsz/ydsz-pmis/docs/standards/api-spec.md) |
| 后端基础设施 | [docs/standards/backend-infrastructure.md](file:///d:/Code/ydsz/ydsz-pmis/docs/standards/backend-infrastructure.md) |
| 后端编码规范 | [docs/standards/backend-spec.md](file:///d:/Code/ydsz/ydsz-pmis/docs/standards/backend-spec.md) |
| 代码质量(测试/Sonar/Checkstyle) | [docs/standards/code-quality.md](file:///d:/Code/ydsz/ydsz-pmis/docs/standards/code-quality.md) |
| 数据库设计 | [docs/standards/database-spec.md](file:///d:/Code/ydsz/ydsz-pmis/docs/standards/database-spec.md) |
| 文档规范 | [docs/standards/documentation.md](file:///d:/Code/ydsz/ydsz-pmis/docs/standards/documentation.md) |
| 前端规范 | [docs/standards/frontend-spec.md](file:///d:/Code/ydsz/ydsz-pmis/docs/standards/frontend-spec.md) |
| Git 工作流 | [docs/standards/git-workflow.md](file:///d:/Code/ydsz/ydsz-pmis/docs/standards/git-workflow.md) |
| 命名约定 | [docs/standards/naming-convention.md](file:///d:/Code/ydsz/ydsz-pmis/docs/standards/naming-convention.md) |
| 规范总览 | [docs/standards/README.md](file:///d:/Code/ydsz/ydsz-pmis/docs/standards/README.md) |

### 8.3 业务规则与运维

| 文档 | 链接 |
|---|---|
| 业务规则总册 + 单元测试映射 | [docs/rules/rule-verify.md](file:///d:/Code/ydsz/ydsz-pmis/docs/rules/rule-verify.md) |
| 等保 2.0 三级 73 项检查 | [docs/security/dengbao-2.0-3-level-checklist.md](file:///d:/Code/ydsz/ydsz-pmis/docs/security/dengbao-2.0-3-level-checklist.md) |
| 敏感字段加密改造 | [docs/security/encrypted-field-rollout.md](file:///d:/Code/ydsz/ydsz-pmis/docs/security/encrypted-field-rollout.md) |
| 性能基线报告 v1.1 | [docs/perf/baseline-report.md](file:///d:/Code/ydsz/ydsz-pmis/docs/perf/baseline-report.md) |
| 生产运维手册 | [docs/operations/prod-ops-runbook.md](file:///d:/Code/ydsz/ydsz-pmis/docs/operations/prod-ops-runbook.md) |
| 上线后 checklist | [docs/operations/post-deploy-checklist.md](file:///d:/Code/ydsz/ydsz-pmis/docs/operations/post-deploy-checklist.md) |
| 灾备演练记录 | [docs/operations/backup-drill-record-2026-12.md](file:///d:/Code/ydsz/ydsz-pmis/docs/operations/backup-drill-record-2026-12.md) |
| 月度对账报告 | [docs/data/monthly-reconcile-report-2026-12.md](file:///d:/Code/ydsz/ydsz-pmis/docs/data/monthly-reconcile-report-2026-12.md) |
| 灰度发布 | [docs/canary-deployment.md](file:///d:/Code/ydsz/ydsz-pmis/docs/canary-deployment.md) |
| 混沌工程 | [docs/chaos-engineering.md](file:///d:/Code/ydsz/ydsz-pmis/docs/chaos-engineering.md) |
| API 摘要 | [docs/api/openapi-summary.json](file:///d:/Code/ydsz/ydsz-pmis/docs/api/openapi-summary.json) |

## 九、团队

| 角色 | 人数 |
|---|---|
| 项目经理 | 1 |
| 产品经理 | 1 |
| 前端开发 | 2-3 |
| 后端开发 | 3-4 |
| 测试 | 1-2 |
| UI 设计 | 1 |
| 数据工程师(二期+) | 1 |
| AI 工程师(三期+) | 1 |
| 运维工程师(SRE) | 1 |

## 十、版本与许可

- 当前版本: **v1.0.0 GA** (2026-07-02)
- 文档密级: 内部受控
- 仓库: https://gitlab.njydsz.com/ydsz/oursource/ydsz-pmis
- 许可: 南京云顶数字科技有限公司内部使用

---

> 本 README 由 PMIS 团队维护,与代码同步更新(v1.0.0_2026-07-02)。
> 任何变更请走 PR + Code Review 流程,详细规范见 [docs/standards/git-workflow.md](file:///d:/Code/ydsz/ydsz-pmis/docs/standards/git-workflow.md)。
