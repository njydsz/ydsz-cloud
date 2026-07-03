# YDSZ PMIS · 项目运营管理系统

> 南京云顶数字科技有限公司 · 软件定制 + 人力外包 双业态 · 业财一体化精细化运营平台
>
> **当前版本**: `v1.3.0-SNAPSHOT` · **最近更新**: 2026-07-03 · **构建状态**: 后端 7 服务 + 2 库 `mvn test` 全部通过 / 前端 `vitest` 54 文件 / `vue-tsc` 0 错

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.7-6DB33F?logo=springboot)]()
[![Spring Cloud](https://img.shields.io/badge/Spring%20Cloud-2025.1.1-6DB33F?logo=spring)]()
[![SCA](https://img.shields.io/badge/Spring%20Cloud%20Alibaba-2025.1.0.0-FF6A00)]()
[![Java](https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk)]()
[![Vue](https://img.shields.io/badge/Vue-3.5-4FC08D?logo=vuedotjs)]()
[![TS](https://img.shields.io/badge/TypeScript-5.x-3178C6?logo=typescript)]()
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-18-336791?logo=postgresql)]()
[![Redis](https://img.shields.io/badge/Redis-7-DC382D?logo=redis)]()
[![License](https://img.shields.io/badge/license-Proprietary-red)]()

---

## 目录

- [一、一句话定位](#一一句话定位)
- [二、核心数据](#二核心数据)
- [三、核心特性](#三核心特性)
- [四、技术架构](#四技术架构)
- [五、快速开始](#五快速开始)
- [六、仓库结构](#六仓库结构)
- [七、核心业务规则速查](#七核心业务规则速查)
- [八、批次交付总览](#八批次交付总览)
- [九、质量与可观测性](#九质量与可观测性)
- [十、文档导航](#十文档导航)
- [十一、团队](#十一团队)
- [十二、版本与许可](#十二版本与许可)

---

## 一、一句话定位

以 **WBS 业财一体化锚点** 为底座，以 **EVM 挣值管理** 为预警引擎，以 **双费率（对外报价 + 对内成本）** 为利润核算基础，串联「**商机 → 立项 → 合同 → 执行 → 回款 → 结项 → 售后**」全生命周期的项目运营管理系统。

对标华为 IPD 阶段评审 + 用友 BIP 业财一体化 + 飞书项目组合 + Wrike 专业服务交付 + 集客云 PSA 等大厂标准，提炼差异化的业财一体化能力。

## 二、核心数据

| 维度 | 数字 | 说明 |
|---|---|---|
| 后端微服务 | **9 模块（7 部署 + 2 库）** | gateway / userinfo / workflow / project / agent / system / cronjob（部署）+ common / literule（库）+ 父 pom |
| 后端 Java 源文件 | **870+** | 业务代码 + DTO/VO/Mapper/Test |
| 后端测试 | **229 测试类 / 1500+ 用例** | `mvn test` BUILD SUCCESS 跨 9 模块 |
| 前端页面 | **57 个** | 业务页面 + 设计器 + 监控中心 |
| 前端测试 | **54 文件 / 470+ 用例** | vitest 单元 + 组件 + Playwright 4 E2E |
| Controller 数 | **80+** | project 41 + userinfo 15 + workflow 3 + 其余 21+ |
| 业务 Service | **130+** | project 38 + userinfo 29 + agent 12 + workflow 8 + 其余 40+ |
| 业务枚举 | **80+** | 状态机 / 业务码 / 审批流 / 编排模式 |
| 权限码 | **240+** | 前后端 `PermissionCodes` 一一对应 |
| 状态机 | **35+** | 覆盖商机 / 立项 / 合同 / 变更 / 发票 / 回款 / 工单 / 售后等 |
| 计算引擎 | **15+** | EVM / 双费率 / 信用 / 风险 / 阶段门径 / 准入 / 模拟 / 规则链 / ... |
| AI Agent | **5 + 4 编排** | RiskWarning / ResourceRecommend / ProfitForecast / WinRatePredict / TimesheetAnomaly · 4 策略（SEQUENTIAL/PARALLEL/VOTING/CASCADE） |
| LLM Provider | **5** | Mock / DashScope（通义千问）/ Qianfan（文心）/ SpringAI / LlmProviderRouter |
| SQL Flyway 脚本 | **43 个** | V1.0.0_001 ~ V1.0.0_041（批次 28 增） |
| 聚合 SQL 视图 | **5 张** | `pmis_view_initiation_revenue_cost` / `pmis_view_initiation_evm` / `pmis_view_cockpit_overview` / `pmis_view_risk_dashboard` / `pmis_view_employee_utilization` |
| 批次交付 | **28 批次** | 批次 1-28 已完成 · 等保测评 / 多租户改造 待评估 |

## 三、核心特性

### 3.1 业务能力

| 模块 | 关键能力 |
|---|---|
| **项目全生命周期** | 商机 A/B/C 分级 → 立项（WBS 预算） → 合同（模板/补充/变更/风险） → 执行（WBS/工时/采购/费用） → 成本归集 → 收入确认（终验/里程碑/月） → 利润核算 → 开票/回款 → 变更管理 → 项目结项 → 售后（质保/工单/满意度） |
| **EVM 挣值管理** | PV/EV/AC 三量 + CPI/SPI 偏差指数 + EAC/VAC 预测 + 红/黄/绿阈值告警 + 5 阶段聚合视图 |
| **双费率利润** | 对外 Rate Card（职级 × 技术栈 × 客户三元组） + 对内 RateInternal（职级 × 部门） + 双率对比 + 多版本模拟 |
| **资源池与 Bench** | 三级池（总部 L13+ / 事业部 L4-L12 / 备用 L1-L3） + 标签 + 预占 + 冲突处理 + Bench 自动入出池 + 闲置成本量化 |
| **经营驾驶舱** | 6 大 KPI + 3 维下钻（部门/项目类型/客户） + 高管看板 + KPI 趋势 + 预警 banner + 60s 实时刷新 + BFF 聚合 |
| **AI 多智能体编排** | Blackboard 共享上下文 + 4 编排策略（串行/并行/投票/级联） + 5 内置 Agent + 5 LLM Provider 路由 + provider_trace_id 追踪 |
| **轻量规则引擎 (literule)** | Aviator 表达式驱动 + 动态配置 + 热加载 + 版本管理 + dry-run 仿真 + 规则链编排 + 阈值动态注入 |
| **自研工作流 v2** | `pmis_flow_*` 表 + BPMN 2.0 解析 + 设计器（数据 API）+ 表单设计器 + 流程模板 + 通知渠道 + 审批人自动去重 + 流程导入导出 + 50 步模拟运行 + 流程监控仪表盘 |
| **混沌工程** | ChaosService 实验注册 + FeatureFlag 双保险 + 注入统计 + chaos-dashboard 实时监控 |
| **变更-交付-结项闭环** | 5 类变更（范围/成本/合同/人员/进度） + 8 类项目交付物标准化（CD1-CD5 门径） + 3 类结项（正式/预/强制）准入 |
| **国际化（i18n）** | vue-i18n 10 + 中/英文语言包 + 6 个核心页面覆盖 + 语言切换组件 + 集中化文案管理 |
| **可观测与一体化运维** | Sentry 异常聚合 + Logback 链路 TraceId + Prometheus 指标 + Grafana 看板 + ELK 日志 + Alertmanager 告警 |

### 3.2 技术能力

| 维度 | 选型 |
|---|---|
| **后端框架** | Spring Boot 4.0.7 + Spring Cloud 2025.1.1 + Spring Cloud Alibaba 2025.1.0.0 |
| **后端治理** | Nacos 2.x（注册/配置）+ Sentinel 1.8.9（限流/熔断）+ Seata 2.5.0（AT 模式分布式事务）+ OpenFeign + RocketMQ 5.x + XXL-JOB 2.4+ |
| **数据** | PostgreSQL 18 + MyBatis-Plus 3.5.16 + Redis 7 + MinIO + Elasticsearch 8.15（全文搜索） + 5 张聚合 SQL 视图（零 Java JOIN） |
| **可观测** | Sentry（异常）+ Logback + TraceId + Prometheus + Grafana + ELK + SkyWalking + Alertmanager |
| **安全** | 等保 2.0 三级 · AES-256 + SM4 字段加密 · 7 种脱敏策略 · TOTP 2FA · DataScope 6 模式 · 操作/登录/导出/敏感 4 类审计 · 二级密码策略 |
| **质量门禁（CI）** | SonarQube + OWASP Dependency-Check + Checkstyle + SpotBugs + FindSecBugs + JaCoCo（覆盖率） |
| **前端** | Vue 3.5 + TS 5 + Vite 5.4 + Element Plus 2.8 + vxe-table 4 + ECharts 5.5 + Pinia 2 + vue-i18n 10 + bpmn-js + form-create + CodeMirror 6 + vue-grid-layout |
| **前端工程化** | vite-plugin-mock 独立开发 + vite-plugin-pwa + rollup-plugin-visualizer + unplugin-auto-import + ESLint + Prettier + Husky + lint-staged + commitlint |
| **测试** | JUnit 5 + Mockito + AssertJ + Testcontainers（后端） / Vitest + Vue Test Utils + Playwright（前端） |
| **工程化** | OpenFeign + FallbackFactory · 自研工作流引擎（`pmis_flow_*`）· JobHandler 跨模块调度 · vxe-table 通用列表组件 · ErrorBoundary + 暗黑模式 + 骨架屏 + 批量操作 + 虚拟滚动 + 表单草稿 + 收藏快访 + 内联编辑 |
| **部署** | Docker 镜像 + Docker Compose（基础设施）+ Helm Chart 5 模板（ConfigMap / Secret / HPA / PDB / ServiceMonitor）+ Argo Rollouts（金丝雀）+ Ansible（OS 编排） |
| **流程治理** | 金丝雀发布（5% → 25% → 50% → 100%）+ 灰度 SOP + 混沌工程联动 + 灾备演练（pg_backup + 增量 + 月度对账） |

## 四、技术架构

### 4.1 技术选型速查

| 层 | 关键依赖 | 版本 | 用途 |
|---|---|---|---|
| 前端框架 | Vue / Vite / TS | 3.5 / 5.4 / 5.x | 核心 + 构建 + 类型 |
| 前端 UI | Element Plus / vxe-table / ECharts | 2.8 / 4.12 / 5.5 | 组件库 + 高级表格 + 可视化 |
| 前端设计器 | bpmn-js / form-create / CodeMirror | 17.x / 3.5 / 6.x | 流程设计 + 表单设计 + 表达式编辑 |
| 前端状态 | Pinia / Vue Router / Vue i18n | 2.2 / 4.4 / 10.0 | 状态 + 路由 + 国际化 |
| 前端测试 | vitest / Playwright | 2.1 / 1.49 | 单元 + E2E |
| 后端框架 | Spring Boot / Spring Cloud / SCA | 4.0.7 / 2025.1.1 / 2025.1.0.0 | 微服务 |
| 后端 ORM | MyBatis-Plus | 3.5.16 | 持久层 |
| 后端治理 | Nacos / Sentinel / Seata / OpenFeign | 2.x / 1.8.9 / 2.5.0 / 4.x | 注册/限流/事务/调用 |
| 表达式 | Aviator（literule 模块） | 5.4.3 | 规则引擎表达式 |
| 数据库 | PostgreSQL | 18 | 主库（分年度分表） |
| 缓存/锁 | Redis / Redisson | 7 / 3.52 | 会话 / 分布式锁 / 幂等 |
| 消息 | RocketMQ | 5.x（spring-boot-starter 2.3.1） | 异步事件 |
| 调度 | XXL-JOB | 2.4+ | 分布式任务 |
| AI | Spring AI + AgentScope | - | 多智能体编排 |
| 监控 | Prometheus + Grafana + Sentry + ELK + SkyWalking + Alertmanager | - | 指标 + 链路 + 日志 + 告警 |

### 4.2 微服务清单（7 部署单元 + 2 库）

| 模块 | artifactId | 端口 | 职责 |
|---|---|---|---|
| API 网关 | ydsz-pmis-gateway | **9000** | 路由 + 鉴权 + 限流 + CORS |
| 用户信息 | ydsz-pmis-userinfo | **9002** | 登录 / Token / 2FA / 登录审计 / 二次认证 / RBAC / 部门 / 人员 / 职级 / 字典 / 资源池 / Bench / 员工标签（user + auth 合并，包名 com.njydsz.pmis.userinfo） |
| 工作流 | ydsz-pmis-workflow | **9004** | 自研 `pmis_flow_*` 引擎 + BPMN 2.0 解析 + 模板 + 模拟 |
| 项目 | ydsz-pmis-project | **9005** | 商机 / 立项 / 合同 / 变更 / WBS / EVM / 成本 / 收入 / 风险 / 工时 / 发票 / 付款 / 客户信用 / 资源 / Dashboard / Report / 费率 / 交付 / 收尾 / 利润（project + execution 合并，包名 com.njydsz.pmis.project） |
| AI Agent | ydsz-pmis-agent | **9007** | 5 Agent + 4 编排 + 5 LLM Provider |
| 系统 | ydsz-pmis-system | **9008** | 文件 / 配置 / 审计 / 通知 / 消息模板（file + config + audit + notification + message 合并，包名 com.njydsz.pmis.system） |
| 调度 | ydsz-pmis-cronjob | **9012** | XXL-JOB 调度 + JobHandler 注册 |
| 公共（库） | ydsz-pmis-common | — | 统一响应 / AOP / 注解 / Feign / 敏感数据 / JobHandler / Sentry / I18n / 权限码 / 混沌（不独立部署） |
| 轻量规则引擎（库） | ydsz-pmis-literule | — | 表达式驱动 + 规则链 + 阈值注入 + dry-run（批次 21 引入，不独立部署） |

> **架构决策（2026-07-03 修订）**: 服务合并重构——user + auth → userinfo（9002，包名 com.njydsz.pmis.userinfo）；file + config + audit + notification + message → system（9008，包名 com.njydsz.pmis.system）；project + execution → project（9005，包名 com.njydsz.pmis.project）。合并后共 7 个可部署服务 + 2 个库（common / literule 不独立部署），降低运维成本与跨服务调用复杂度。原规划 11 微服务曾落地为 15 模块，本次合并收敛为 9 模块。

### 4.3 模块依赖拓扑

```text
gateway → userinfo / project / agent / system / workflow
userinfo → common / literule
project → common / userinfo(Feign) / workflow(Feign) / literule
agent   → common / project(Feign) / literule
system  → common / userinfo(Feign) / project(Feign)
workflow → common / system(Feign)
scheduler → common.feign(ProjectClient)        # 批次 17: JobHandler 迁至 common,打破循环依赖（已更名为 ydsz-pmis-cronjob）
literule  → common                              # 批次 21: 表达式引擎独立,供各业务模块按需引用
```

## 五、快速开始

### 5.1 环境要求

| 工具 | 版本 | 备注 |
|---|---|---|
| JDK | 21 | Spring Boot 4.x 强制 |
| Maven | 3.9+ | 父 POM dependencyManagement |
| Node.js | ≥ 20 | 前端 Vite 5.4 |
| pnpm | ≥ 9 | 强制 lockfile 统一 |
| PostgreSQL | 18 | 主库 |
| Redis | 7 | 缓存 / 会话 / 分布式锁 |
| Nacos | 2.x | namespace `pmis` / group `PMIS_GROUP_{DEV/SIT/UAT/PROD}` |
| Docker | 24+ | Compose 编排基础设施 |

### 5.2 本地启动

```bash
# 1. 启动基础设施 (Nacos / Postgres / Redis / MinIO)
cd deploy/docker && docker compose -f docker-compose.base.yml up -d

# 2. 初始化数据库 (43 个 Flyway 脚本自动执行)
psql -U pmis -d pmis -f deploy/sql/V1.0.0_001__init_pmis_schema.sql
# Spring Boot 启动时 Flyway 自动迁移 (推荐)

# 3. 启动后端 (按依赖顺序)
mvn -pl ydsz-pmis-common,ydsz-pmis-literule,ydsz-pmis-userinfo \
    -am install -DskipTests
mvn -pl ydsz-pmis-gateway spring-boot:run   # 端口 9000
# 其它模块同理 spring-boot:run,按依赖拓扑顺序启动

# 4. 启动前端
cd ydsz-pmis-frontend
pnpm install
pnpm dev    # http://localhost:5173
```

### 5.3 测试命令

```bash
# 后端 - 9 模块 100% 通过
mvn -pl ydsz-pmis-backend -am test

# 前端单元 + 组件
cd ydsz-pmis-frontend && pnpm test

# 前端类型检查
pnpm type-check

# 前端 E2E (Playwright 4 用例)
pnpm test:e2e:smoke                # 冒烟
pnpm test:e2e                      # 全量(3 核心业务流)

# 性能 (6 JMeter 场景)
jmeter -n -t deploy/perf/jmeter/01-core-read.jmx

# 代码质量门禁（CI 同等命令）
mvn checkstyle:check              # Checkstyle
mvn org.jacoco:jacoco-maven-plugin:report   # 覆盖率
mvn -DskipDependencyCheck=false org.owasp:dependency-check-maven:check  # OWASP
```

## 六、仓库结构

```text
ydsz-pmis/
├── ydsz-pmis-backend/          # 后端 9 模块聚合工程（7 部署 + 2 库）
│   ├── ydsz-pmis-gateway/      # 9000 API 网关
│   ├── ydsz-pmis-common/       # 公共组件库 (80+ 测试类, 不独立部署)
│   ├── ydsz-pmis-userinfo/    # 9002 用户信息/RBAC/部门/人员/职级/字典/资源池/Bench/员工标签
│   ├── ydsz-pmis-system/       # 9008 文件/配置/审计/通知/消息模板
│   ├── ydsz-pmis-workflow/     # 9004 自研工作流 + BPMN
│   ├── ydsz-pmis-project/      # 9005 项目/执行/财务/报表 (商机→售后全生命周期)
│   ├── ydsz-pmis-agent/        # 9007 AI Agent
│   ├── ydsz-pmis-cronjob/    # 9012 XXL-JOB
│   └── ydsz-pmis-literule/     # --  轻量规则引擎 (库, 不独立部署)
├── ydsz-pmis-frontend/         # 前端 (Vue 3.5 + Vite 5.4)
│   ├── src/api/                # 1:1 后端 Controller 封装
│   ├── src/views/              # 57 个业务页面
│   ├── src/components/common/  # 20+ 通用组件 (含 vxe-table 通用列表)
│   ├── src/composables/        # useECharts / useFeatureFlag / useReAuth / useFormDraft / useI18n
│   ├── src/locales/            # 中/英文语言包
│   ├── src/mock/               # vite-plugin-mock 独立开发
│   ├── e2e/                    # Playwright 4 E2E
│   └── vitest.config.ts        # 54 测试文件 470+ 用例
├── deploy/                     # 部署全套
│   ├── docker/                 # 7 服务 Dockerfile
│   ├── ansible/                # 多环境编排
│   ├── argo-rollouts/          # 金丝雀（批次 23）
│   ├── sql/                    # 43 Flyway 脚本
│   ├── functional-test/        # UAT + Postman 32 端点
│   ├── perf/jmeter/            # 6 性能场景
│   ├── security/               # 等保 / OWASP / crypto-verify
│   ├── backup/                 # pg_backup + 增量
│   ├── migration/              # 历史数据迁移
│   └── monitoring/             # Prometheus / Grafana / Sentry / Alertmanager
├── helm/pmis/                  # Helm Chart 5 模板
│   ├── templates/              # ConfigMap / Secret / HPA / PDB / ServiceMonitor / Canary VirtualService
│   └── values-prod.yaml        # 生产环境参数
├── docs/                       # 完整文档库
│   ├── standards/              # 9 份大厂规范 (API/DB/前端/后端/...)
│   ├── operations/             # 运维手册 / 上线 checklist
│   ├── security/               # 等保三级 73 项
│   ├── perf/                   # 性能基线报告
│   ├── rules/                  # 业务规则总册
│   ├── data/                   # 月度对账 / 演练记录
│   └── pmis-prd-v3.md          # PRD V3.2 需求详细设计
├── README.md                   # 本文件
└── sonar-project.properties    # SonarQube 配置（CI 接入）
```

## 七、核心业务规则速查

### 7.1 职级费率体系（L1-L18）

| 职级段 | 月工资 | 公司月总成本 | 对内人天 | 对外人天 | 资源池 |
|---|---|---|---|---|---|
| L1-L3 | 4.5K-5.5K | 6.1K-7.5K | 281-344 | 422-516 | 备用池 |
| L4-L6 | 6K-8K | 8.2K-10.9K | 375-500 | 563-750 | 事业部 |
| L7-L9 | 9K-12K | 12.2K-16.3K | 563-750 | 844-1125 | 事业部 |
| L10-L12 | 13K-16K | 17.7K-21.8K | 814-1001 | 1221-1501 | 事业部（预警减半） |
| L13-L15 | 17K-20K | 23.1K-27.2K | 1063-1251 | 1595-1876 | 总部池 |
| L16-L18 | 19K-21K | 24.6K-27.2K | 1131-1251 | 1697-1876 | 总部池（战略层） |

### 7.2 预警阈值

| 指标 | 绿色 | 黄色 | 红色 |
|---|---|---|---|
| CPI 成本绩效 | ≥ 0.95 | 0.85-0.95 | < 0.85 |
| SPI 进度绩效 | ≥ 0.95 | 0.85-0.95 | < 0.85 |
| 预算占用率 | < 80% | 80%-95% | ≥ 95% |
| Bench 闲置 | — | 7 天 | 15 天 |
| L10+ Bench | — | 3 天（减半） | 7 天（减半） |
| 客户信用 | A 90+ | B 75-89 / C 60-74 | D < 60 |

### 7.3 状态机收敛点（部分）

- **InvoiceStatus**: DRAFT → SUBMITTED → APPROVED → ISSUED → (RED_REVERSED / CANCELLED 终态)
- **ContractStatus**: DRAFT → REVIEWING → APPROVED → SIGNED → (CLOSED / TERMINATED)
- **ChangeStatus**: DRAFT → SUBMITTED → UNDER_REVIEW → APPROVED/REJECTED → (CLOSED / CANCELLED)
- **ClosureStatus**: DRAFT → SUBMITTED → APPROVED → (ARCHIVED / REJECTED)
- **WbsTaskStatus**: PLANNED → IN_PROGRESS → (BLOCKED) → IN_REVIEW → COMPLETED / CANCELLED
- **OpportunityStatus**: NEW → QUALIFIED → (WON → CONVERTED 终态) / (LOST 终态) / ON_HOLD
- **OpsTicketStatus**: OPEN → TRIAGED → IN_PROGRESS → (RESOLVED / ESCALATED) → CLOSED

## 八、批次交付总览

| 批次 | 主题 | 关键交付 |
|---|---|---|
| 1-12 | 核心业务 | 14 模块 + 34 业务页面 + 主数据流 |
| 13 | 用户中心强化 | 2FA + Session + 登录审计 + 数据导出审计 + 二次认证 + 6 模块补全 60 测试 |
| 14-15 | 报表与驾驶舱 | EVM 看板 + Cockpit 6 KPI + 高级报表 6 类 + 5 张聚合 SQL 视图 |
| 16 | AI Agent 编排 | 4 策略 + Blackboard + 50 测试类 100% 通过 |
| 17 | JobHandler 重构 | cronjob→execution 跨模块 Feign 化，172 测试 100% |
| 18-19 | 工作流 v1.1 + 质量门禁 | Checkstyle + SonarQube + OWASP + 工作流 110 测试 |
| 20 | 混沌工程 + 金丝雀 | ChaosService + 5 类实验 + Argo Rollouts |
| 21 | literule 规则引擎 | Aviator 表达式 + 规则链 + 阈值注入 + dry-run |
| 22 | 工作流 v2 | 设计器数据 API + 表单引擎 + 通知渠道 + 审批人去重 + 导入导出 + 50 步模拟 |
| 23 | 模板与监控 | 流程模板 + 流程监控仪表盘 + 流程自动触发 |
| 24 | chaos-dashboard | 前端 4 KPI + 2 ECharts + 实验 CRUD + Dry-Run + 5s 轮询 |
| 25 | 售后管理 | 质保期 + 运维工单 P1-P4 SLA + 满意度 9 测试类 |
| 26 | v1.1 优化 | Seata + WebSocket + CI 门禁 + ES + 文件增强 + 报表 + 批量操作等 24 项 |
| 27 | v1.2 优化 | Sentry 接入 + Redis 配置补全 + BFF + 工作流事件联动 + EasyExcel + ErrorBoundary + 暗黑模式 + 限流启用 + RocketMQ + Flyway 修正 + i18n 6 页面 + Helm 5 模板 + Alertmanager + Controller 校验 + SpotBugs + PWA |
| 28 | v1.3 国际化与代码优化 | i18n 基础设施 + 中英文语言包 + 多模块重构 + literule 计算类迁移 + Nacos 分组统一 + 端口对齐 |

**当前状态**：批次 28 已完成；下一阶段（批次 29+）规划等保测评 / 多租户改造，按业务节奏启动。

## 九、质量与可观测性

| 维度 | 指标 / 工具 | 阈值 / 现状 |
|---|---|---|
| 后端单测 | JUnit 5 + Mockito + AssertJ | 9 模块 100% 通过 |
| 后端覆盖率 | JaCoCo | 行覆盖 ≥ 80%，分支覆盖 ≥ 70% |
| 前端单测 | Vitest + Vue Test Utils | 54 文件 470+ 用例 100% 通过 |
| 前端类型 | vue-tsc --noEmit | 0 错 |
| 前端 Lint | ESLint + Prettier | 0 error（增量） |
| 静态检查 | Checkstyle（failsOnError=true） | CI 阻断 |
| 静态分析 | SpotBugs + FindSecBugs（Low 阈值） | CI 阻断 |
| 依赖漏洞 | OWASP dependency-check（CVSS ≥ 7 阻断） | CI 阻断 |
| 代码质量 | SonarQube（需 SONAR_TOKEN secret） | Quality Gate |
| API 契约 | Postman Collection（32 端点）+ Smoke Test | UAT 通过 |
| 性能 | JMeter 6 场景 + 24h Soak | 基线报告归档 |
| 灰度发布 | Argo Rollouts（5%→25%→50%→100%） + Istio | canary-shift.sh |
| 混沌工程 | ChaosService + 5 类实验 + 每日随机 | chaos-dashboard 实时 |
| 异常聚合 | Sentry（前端 + 后端 dynamic import） | 1h 内 0 新 issue 阻断 |
| 链路追踪 | TraceId Filter + Logback + SkyWalking | 100% 请求覆盖 |
| 监控告警 | Prometheus + Grafana + Alertmanager | 业务/DB/JVM/Overview 4 看板 |
| 日志聚合 | ELK + logstash-logback-encoder | JSON 结构化 |
| 灾备 | pg_backup + 增量 + 月度演练 | 详见 [backup-drill-record-2026-12.md](docs/operations/backup-drill-record-2026-12.md) |

## 十、文档导航

### 10.1 立项与需求

| 文档 | 链接 |
|---|---|
| PRD 需求详细设计 V3.2 | [docs/pmis-prd-v3.md](docs/pmis-prd-v3.md) |
| PRD HTML 版 | [docs/pmis-prd-v3.html](docs/pmis-prd-v3.html) |
| 业务规则总册 + 单元测试映射 | [docs/rules/rule-verify.md](docs/rules/rule-verify.md) |
| 业务数据（月度对账） | [docs/data/monthly-reconcile-report-2026-12.md](docs/data/monthly-reconcile-report-2026-12.md) |

### 10.2 工程规范（9 份大厂标准）

| 规范 | 链接 |
|---|---|
| 规范总览 | [docs/standards/README.md](docs/standards/README.md) |
| 编码与命名规范 | [docs/standards/naming-convention.md](docs/standards/naming-convention.md) |
| Git 工作流规范 | [docs/standards/git-workflow.md](docs/standards/git-workflow.md) |
| 前端工程规范 | [docs/standards/frontend-spec.md](docs/standards/frontend-spec.md) |
| 后端工程规范 | [docs/standards/backend-spec.md](docs/standards/backend-spec.md) |
| 后端基础设施 | [docs/standards/backend-infrastructure.md](docs/standards/backend-infrastructure.md) |
| API 接口规范 | [docs/standards/api-spec.md](docs/standards/api-spec.md) |
| 数据库设计规范 | [docs/standards/database-spec.md](docs/standards/database-spec.md) |
| 代码质量与安全规范 | [docs/standards/code-quality.md](docs/standards/code-quality.md) |
| 文档与交付规范 | [docs/standards/documentation.md](docs/standards/documentation.md) |

### 10.3 运维 / 安全 / 性能

| 文档 | 链接 |
|---|---|
| 生产运维手册 | [docs/operations/prod-ops-runbook.md](docs/operations/prod-ops-runbook.md) |
| 上线后 checklist | [docs/operations/post-deploy-checklist.md](docs/operations/post-deploy-checklist.md) |
| 灾备演练记录 | [docs/operations/backup-drill-record-2026-12.md](docs/operations/backup-drill-record-2026-12.md) |
| 等保 2.0 三级 73 项检查 | [docs/security/dengbao-2.0-3-level-checklist.md](docs/security/dengbao-2.0-3-level-checklist.md) |
| 敏感字段加密改造 | [docs/security/encrypted-field-rollout.md](docs/security/encrypted-field-rollout.md) |
| 性能基线报告 v1.1 | [docs/perf/baseline-report.md](docs/perf/baseline-report.md) |
| 灰度发布 | [docs/canary-deployment.md](docs/canary-deployment.md) |
| 混沌工程 | [docs/chaos-engineering.md](docs/chaos-engineering.md) |
| 多租户架构评估 | [docs/multi-tenant-evaluation.md](docs/multi-tenant-evaluation.md) |

### 10.4 API 与接口

| 文档 | 链接 |
|---|---|
| API 摘要 | [docs/api/openapi-summary.json](docs/api/openapi-summary.json) |
| API 版本管理 | [docs/api/api-versioning.md](docs/api/api-versioning.md) |
| API 规范 | [docs/standards/api-spec.md](docs/standards/api-spec.md) |

## 十一、团队

| 角色 | 人数 | 备注 |
|---|---|---|
| 项目经理 | 1 | 批次化交付 + 验收 |
| 产品经理 | 1 | PRD V3.x 演进 |
| 前端开发 | 2-3 | Vue 3.5 + 设计器 |
| 后端开发 | 3-4 | 微服务 + 工作流 + 规则引擎 |
| 测试 | 1-2 | 自动化 + 性能 |
| UI 设计 | 1 |  |
| 数据工程师（二期+） | 1 |  |
| AI 工程师（三期+） | 1 | Agent + LLM Provider |
| 运维工程师（SRE） | 1 | Helm / Argo / 监控 |

## 十二、版本与许可

- **当前版本**: v1.3.0-SNAPSHOT（批次 28 完成，2026-07-02）
- **首发版本**: v1.0.0 GA（2026-06-30）
- **文档密级**: 内部受控
- **仓库地址**: `https://gitlab.njydsz.com/ydsz/oursource/ydsz-pmis`
- **许可**: 南京云顶数字科技有限公司内部使用

---

> 本 README 由 PMIS 团队维护，与代码同步更新（v1.3.0_2026-07-02）。
> 任何变更请走 PR + Code Review 流程，详细规范见 [docs/standards/git-workflow.md](docs/standards/git-workflow.md)。
