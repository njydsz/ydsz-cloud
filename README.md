<!--
================================================================================
YDSZ PMIS · 项目运营管理系统 · README
--------------------------------------------------------------------------------
项目代号:   YDSZ PMIS
所属公司:   南京云顶数字科技有限公司
版本:       v1.3.0-SNAPSHOT
最近更新:   2026-07-08
维护团队:   PMIS 研发部
文档密级:   内部受控 · 禁止外传

本文件是仓库的入口文档，向新成员/合作方/审核方解释：
  1. 是什么（产品定位 / 业务覆盖）
  2. 包含什么（仓库结构 / 模块拓扑）
  3. 怎么跑（环境要求 / 本地启动 / 测试命令 / 部署流程）
  4. 怎么查（文档导航）
  5. **2026-07-08 更新**：端口重排 + Nacos 配置规范统一（详见 4.2 / 11）

变更需走 PR + Code Review。
================================================================================
-->

# YDSZ PMIS · 项目运营管理系统

> 南京云顶数字科技有限公司 · 软件定制 + 人力外包 双业态 · 业财一体化精细化运营平台
>
>- **当前版本**: `v1.3.0-SNAPSHOT` · **最近更新**: 2026-07-06

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.7-6DB33F?logo=springboot)]()
[![Spring Cloud](https://img.shields.io/badge/Spring%20Cloud-2025.1.2-6DB33F?logo=spring)]()
[![SCA](https://img.shields.io/badge/Spring%20Cloud%20Alibaba-2025.1.0.0-FF6A00)]()
[![Java](https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk)]()
[![Vue](https://img.shields.io/badge/Vue-3.5-4FC08D?logo=vuedotjs)]()
[![TS](https://img.shields.io/badge/TypeScript-5.x-3178C6?logo=typescript)]()
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-18-336791?logo=postgresql)]()
[![Redis](https://img.shields.io/badge/Redis-8-DC382D?logo=redis)]()
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
- [十一、子模块快速上手](#十一子模块快速上手)
- [十二、团队](#十二团队)
- [十三、版本与许可](#十三版本与许可)

---

## 一、一句话定位

以 **WBS 业财一体化锚点** 为底座，以 **EVM 挣值管理** 为预警引擎，以 **双费率（对外报价 + 对内成本）** 为利润核算基础，串联「**商机 → 立项 → 合同 → 执行 → 回款 → 结项 → 售后**」全生命周期的项目运营管理系统。

## 二、核心数据

| 维度 | 数字 | 说明 |
|---|---|---|
| 后端微服务 | **8 部署单元 + 2 库** | gateway / userinfo / system / project / message / cronjob / workflow / agent（部署）+ common / literule（库） |
| 后端测试类 | **111 个** | 覆盖 common/userinfo/project/workflow/system/agent/cronjob/literule 8 模块 |
| 前端页面 | **57 个** | 业务页面 + 设计器 + 监控中心 |
| 前端测试 | **7 个测试文件** | vitest 单元测试（composables/store/utils） |
| 数据库初始化 | **`docs/V1.0.0.sql`** | 126 表 + 5 视图（PostgreSQL 18，单文件初始化） |
| 批次交付 | **28 批次** | 批次 1-28 已完成 · 等保测评 / 多租户改造 待评估 |

> **说明**: 测试代码已编写但 CI 流水线当前未强制执行测试（见 P0-A2 待修复项），本地执行 `mvn test` / `pnpm test` 可通过。

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
| **自研工作流 v2** | `pmis_flow_*` 表 + BPMN 2.0 解析 + 设计器（数据 API）+ 表单设计器 + 流程模板 + 通知渠道 + 审批人自动去重 + 流程导入导出 + 50 步模拟运行 + 流程监控仪表盘 ⚠️ **仅适配 PC Web 端，不支持移动端/独立 H5**（见 7.4） |
| **混沌工程** | ChaosService 实验注册 + FeatureFlag 双保险 + 注入统计 + chaos-dashboard 实时监控 |
| **变更-交付-结项闭环** | 5 类变更（范围/成本/合同/人员/进度） + 8 类项目交付物标准化（CD1-CD5 门径） + 3 类结项（正式/预/强制）准入 |
| **国际化（i18n）** | vue-i18n 10 + 中/英文语言包 + 6 个核心页面覆盖 + 语言切换组件 + 集中化文案管理（部分业务页面 i18n 迁移待完成） |

### 3.2 技术能力

| 维度 | 选型 |
|---|---|
| **后端框架** | Spring Boot 4.0.7 + Spring Cloud 2025.1.2 + Spring Cloud Alibaba 2025.1.0.0 |
| **后端治理** | Nacos 2.3.2（注册/配置）+ Sentinel 1.8.9（限流/熔断）+ Seata 2.5.0（AT 模式分布式事务）+ OpenFeign + RocketMQ 5.x + XXL-JOB 2.4+ |
| **数据** | PostgreSQL 18 + MyBatis-Plus 3.5.16 + Redis 8 + MinIO + 5 张聚合 SQL 视图（零 Java JOIN） |
| **可观测** | Sentry（异常，默认关闭需手动启用）+ Logback + TraceId（MDC）+ Actuator（健康检查） |
| **安全** | AES-256 + SM4 字段加密 · 7 种脱敏策略 · TOTP 2FA · DataScope 6 模式 · 操作/登录/导出/敏感 4 类审计 · 二级密码策略 |
| **质量门禁（CI）** | Checkstyle + SpotBugs + FindSecBugs + JaCoCo（覆盖率，配置已就绪）+ OWASP Dependency-Check（默认跳过，CI 中 -DskipDependencyCheck=false 启用） |
| **前端** | Vue 3.5 + TS 5 + Vite 5.4 + Element Plus 2.8 + vxe-table 4 + ECharts 5.5 + Pinia 2 + vue-i18n 10 + bpmn-js + form-create + CodeMirror 6 + vue-grid-layout |
| **前端工程化** | vite-plugin-mock 独立开发 + vite-plugin-pwa + rollup-plugin-visualizer + unplugin-auto-import + ESLint + Prettier + Husky + lint-staged + commitlint |
| **测试** | JUnit 5 + Mockito + AssertJ（后端） / Vitest + Vue Test Utils（前端） |
| **部署** | Docker 镜像 + Docker Compose（dev 环境基础设施编排）|

## 四、技术架构

### 4.1 技术选型速查

| 层 | 关键依赖 | 版本 | 用途 |
|---|---|---|---|
| 前端框架 | Vue / Vite / TS | 3.5 / 5.4 / 5.x | 核心 + 构建 + 类型 |
| 前端 UI | Element Plus / vxe-table / ECharts | 2.8 / 4.12 / 5.5 | 组件库 + 高级表格 + 可视化 |
| 前端设计器 | bpmn-js / form-create / CodeMirror | 17.x / 3.5 / 6.x | 流程设计 + 表单设计 + 表达式编辑 |
| 前端状态 | Pinia / Vue Router / Vue i18n | 2.2 / 4.4 / 10.0 | 状态 + 路由 + 国际化 |
| 前端测试 | vitest | 2.1 | 单元测试 |
| 后端框架 | Spring Boot / Spring Cloud / SCA | 4.0.7 / 2025.1.2 / 2025.1.0.0 | 微服务 |
| 后端 ORM | MyBatis-Plus | 3.5.16 | 持久层 |
| 后端治理 | Nacos / Sentinel / Seata / OpenFeign | 2.3.2 / 1.8.9 / 2.5.0 / 4.x | 注册/限流/事务/调用 |
| 表达式 | Aviator（literule 模块） | 5.4.3 | 规则引擎表达式 |
| 数据库 | PostgreSQL | 18 | 主库 |
| 缓存/锁 | Redis / Redisson | 8 / 4.6.1 | 会话 / 分布式锁 / 幂等 |
| 消息 | RocketMQ | 5.x（spring-boot-starter 2.3.1） | 异步事件 |
| 调度 | XXL-JOB | 2.4+ | 分布式任务 |
| AI | Spring AI + AgentScope | - | 多智能体编排 |

### 4.2 微服务清单（8 部署单元 + 2 库，按 pom.xml 构建顺序排列）

| # | 模块 | artifactId | 端口 | 职责 |
|---|---|---|---|---|
| 1 | API 网关 | ydsz-pmis-gateway | **9000** | 路由 + 鉴权 + 限流 + CORS |
| 2 | 用户信息 | ydsz-pmis-userinfo | **9001** | 登录 / Token / 2FA / 登录审计 / 二次认证 / RBAC / 部门 / 人员 / 职级 / 字典 / 资源池 / Bench / 员工标签 |
| 3 | 系统基础 | ydsz-pmis-system | **9002** | 文件 / 配置 / 审计 |
| 4 | 项目 | ydsz-pmis-project | **9003** | 商机 / 立项 / 合同 / 变更 / WBS / EVM / 成本 / 收入 / 风险 / 工时 / 发票 / 付款 / 客户信用 / 资源 / Dashboard / Report / 费率 / 交付 / 收尾 / 利润 |
| 5 | 消息中心 | ydsz-pmis-message | **9004** | 多渠道发送（SMS/EMAIL/PUSH/IN_APP/WEBHOOK/DINGTALK/WECOM/FEISHU）+ 模板 + 偏好 + 订阅 + 限流 + 撤回 + 聚合 + 回执 |
| 6 | 调度 | ydsz-pmis-cronjob | **9005** | Leader 选举 + DB 行锁 + Redis 分布式锁 + 故障转移 + 租户隔离 + 告警通道 |
| 7 | 工作流 | ydsz-pmis-workflow | **9006** | 自研 `pmis_flow_*` 引擎 + BPMN 2.0 解析 + 模板 + 模拟 ⚠️ **仅 PC 端** |
| 8 | AI Agent | ydsz-pmis-agent | **9007** | 5 Agent + 4 编排 + 5 LLM Provider |
| — | 公共（库） | ydsz-pmis-common | — | 统一响应 / AOP / 注解 / Feign / 敏感数据 / JobHandler / Sentry / I18n / 权限码 / 混沌（不独立部署） |
| — | 轻量规则引擎（库） | ydsz-pmis-literule | — | 表达式驱动 + 规则链 + 阈值注入 + dry-run（不独立部署） |

### 4.3 模块依赖拓扑

```text
gateway → userinfo / project / agent / system / workflow
userinfo → common / literule
project → common / userinfo(Feign) / workflow(Feign) / literule
agent   → common / project(Feign) / literule
system  → common / userinfo(Feign) / project(Feign)
workflow → common / system(Feign)
cronjob  → common.feign(ProjectClient)
literule  → common
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
| Redis | 8 | 缓存 / 会话 / 分布式锁 |
| Nacos | 2.3.2 | namespace `pmis` / group `PMIS_GROUP_{DEV/SIT/UAT/PROD}` |
| Docker | 24+ | Compose 编排基础设施 |

### 5.2 本地启动

**5 分钟快速启动**：参考 [docs/QUICKSTART.md](docs/QUICKSTART.md)
**详细部署手册**：参考 [docs/DEPLOY.md](docs/DEPLOY.md)
**7 大中间件部署**（PG/Redis/Nacos/MinIO/Seata/RocketMQ/XXL-Job）：参考 [docs/INFRASTRUCTURE.md](docs/INFRASTRUCTURE.md)
（全文检索已统一改用 PostgreSQL tsvector，无需 ES）

```bash
# 0. 环境检查（首次部署前必跑）
./deploy/ubuntu/scripts/check-env.sh   # Linux/macOS
.\deploy\windows\scripts\check-env.bat  # Windows

# 1. 一键启动（基础设施 + 后端 + 前端）
./deploy/ubuntu/scripts/start-all.sh
# 或 Windows
.\deploy\windows\scripts\start-all.bat

# 2. 中间件管理（按操作系统）
./deploy/ubuntu/infra-manager.sh status      # Ubuntu
.\deploy\windows\infra-manager.ps1 status    # Windows

# 3. 单独启动
./deploy/ubuntu/scripts/start-all.sh --infra     # 仅基础设施
./deploy/ubuntu/scripts/start-all.sh --backend   # 仅后端
./deploy/ubuntu/scripts/start-all.sh --frontend  # 仅前端

# 4. 停止
./deploy/ubuntu/scripts/stop-all.sh
./deploy/ubuntu/scripts/stop-all.sh --with-infra  # 含基础设施

# 5. 访问
# 前端:       http://localhost:5173
# API 网关:   http://localhost:9000
# Nacos:      http://127.0.0.1:8848/nacos (nacos/nacos)
# MinIO:      http://127.0.0.1:9101 (minioadmin/minioadmin)
# Seata:      http://127.0.0.1:7091 (admin/admin)
# XXL-Job:    http://127.0.0.1:9100/xxl-job-admin (admin/123456)
# ES:         http://127.0.0.1:9200
# RocketMQ:   http://127.0.0.1:8080
```

### 5.3 测试命令

```bash
# 后端 - 9 模块测试
mvn -pl ydsz-pmis-backend -am test

# 前端单元测试
cd ydsz-pmis-frontend && pnpm test

# 前端类型检查
pnpm type-check

# 代码质量检查
mvn checkstyle:check              # Checkstyle
mvn org.jacoco:jacoco-maven-plugin:report   # 覆盖率
mvn -DskipDependencyCheck=false org.owasp:dependency-check-maven:check  # OWASP
```

## 六、仓库结构

```text
ydsz-pmis/
├── ydsz-pmis-backend/          # 后端 8 部署单元 + 2 库
│   ├── ydsz-pmis-common/       # 公共组件库 + nacos-config 共享配置模板（不独立部署）
│   ├── ydsz-pmis-gateway/      # 9000 API 网关
│   ├── ydsz-pmis-literule/     # --  轻量规则引擎 (库, 不独立部署)
│   ├── ydsz-pmis-userinfo/     # 9001 用户信息/RBAC/部门/人员/职级/字典/资源池/Bench/员工标签
│   ├── ydsz-pmis-system/       # 9002 文件/配置/审计
│   ├── ydsz-pmis-project/      # 9003 项目/执行/财务/报表 (商机→售后全生命周期)
│   ├── ydsz-pmis-message/      # 9004 消息中心(多渠道/模板/偏好/订阅/限流/回执)
│   ├── ydsz-pmis-cronjob/      # 9005 分布式任务调度
│   ├── ydsz-pmis-workflow/     # 9006 自研工作流 + BPMN
│   └── ydsz-pmis-agent/        # 9007 AI Agent
├── ydsz-pmis-frontend/         # 前端 (Vue 3.5 + Vite 5.4)
│   ├── src/api/                # 1:1 后端 Controller 封装
│   ├── src/views/              # 57 个业务页面
│   ├── src/components/common/  # 20+ 通用组件 (含 vxe-table 通用列表)
│   ├── src/composables/        # useECharts / useFeatureFlag / useReAuth / useFormDraft / useI18n
│   ├── src/locales/            # 中/英文语言包
│   └── src/mock/               # vite-plugin-mock 独立开发
├── deploy/                     # 部署全套(按环境分子目录)
│   ├── common/                 # 跨环境共享资源(中间件配置模板 + Nacos 共享配置 + SQL)
│   │   ├── conf/               # 7 中间件原生部署配置(postgres/redis/nacos/minio/seata/rocketmq/xxl-job)
│   │   ├── nacos/              # PMIS 共享 Nacos 配置 ydsz-pmis-common.yaml
│   │   └── sql/                # 通用 SQL(XXL-Job PG 表等)
│   ├── docker/                 # Docker 容器化(7 中间件 + docker-compose.dev.yml)
│   ├── k8s/                    # K8S 部署(Kustomize:base + overlays/dev|sit|uat|prod)
│   ├── ubuntu/                 # Ubuntu 原生部署(中间件安装 + systemd)
│   │   ├── install-pmis-infra.sh
│   │   ├── infra-manager.sh
│   │   └── scripts/            # 应用层启停(start-all/stop-all/check-env/import-nacos-config)
│   ├── windows/                # Windows 原生部署(中间件安装 + NSSM)
│   │   ├── install-pmis-infra.ps1
│   │   ├── infra-manager.ps1
│   │   └── scripts/            # 应用层启停(start-all/stop-all/check-env/import-nacos-config)
│   └── .env.example            # 环境变量模板
├── docs/                       # 文档库
│   ├── QUICKSTART.md           # 5 分钟快速启动
│   ├── DEPLOY.md               # 详细部署手册（应用层）
│   ├── INFRASTRUCTURE.md       # 8 中间件 Docker/Windows/Ubuntu 三合一部署
│   └── V1.0.0.sql              # 数据库初始化脚本（126 表 + 5 视图）
├── .github/workflows/          # CI/CD 流水线
│   ├── backend-ci.yml          # 后端 CI（构建 + 质量扫描）
│   ├── frontend-ci.yml         # 前端 CI（lint + build）
│   └── cd-deploy.yml           # CD 部署流水线
└── README.md                   # 本文件
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

### 7.4 平台适配范围（团队共识 · 硬约束）

> 自 2026-07-06 起明确：**本项目自研工作流引擎（`ydsz-pmis-workflow` 模块及其全部前端页面）永远不适配移动端 App 与独立 H5 应用**。

| 维度 | 范围 |
|---|---|
| ✅ 支持 | PC Web（`ydsz-pmis-frontend`，Vue 3.5 + Element Plus，桌面浏览器 ≥ 1280px） |
| ❌ 不支持 | 原生 iOS/Android App、uni-app / Taro 移动端、独立的移动 H5 子应用、PWA 移动模式 |
| 🔁 移动端审批替代方案 | ① 对接企业微信 / 钉钉 / 飞书（已实现 `WeComSignatureUtil` / `DingTalkSignatureUtil` / `FeishuSignatureUtil`）；② 独立「轻审批 H5」应用（仅查询/同意/驳回，不含设计器/监控） |

**为什么不做移动端适配**：

1. **流程设计器强依赖桌面交互**：bpmn-js 拖拽、连线、属性面板、表单设计器（form-create）、表达式编辑器（CodeMirror）均基于鼠标/键盘桌面交互范式，重写成本远超收益。
2. **流程监控/模拟运行信息密度高**：审批中心、流程监控仪表盘、50 步模拟运行等页面的信息密度（多列表格 + 流程图 + 时间线 + 属性抽屉）无法在手机屏下保证可用性。
3. **业务定位决定**：项目运营管理系统（B 端内部工具）天然服务于办公室 PC 场景，移动端需求已通过 IM 审批通道完整覆盖。
4. **避免范围蔓延**：强制边界可防止后续在移动端踩坑（适配层兼容、触控事件、性能、图表缩放等）反复消耗研发资源。

**实施约束**：

- 前端代码中工作流相关页面/组件严禁引入 `vant` / `uni-ui` / `taro-ui` 等移动端 UI 库；
- 后端 `ydsz-pmis-workflow` 模块 API 默认响应 PC 端字段结构（包含完整流程图 JSON、表单 Schema、审批历史），不为移动端裁剪；
- 任何 PR 不得引入「工作流模块移动端适配」相关代码，code review 必须拦截。

### 7.5 电子签章能力范围（团队共识 · 硬约束）

> 自 2026-07-06 起明确：**`ydsz-pmis-workflow` 模块及其全部前端页面永远不会集成电子签章（e-sign）能力**。该决策与 7.4 同级，属于「不会做」清单。

**不集成的范围**（不限于）：

| 维度 | 不集成的内容 |
|---|---|
| ❌ 第三方 SaaS | e签宝 / 法大大 / 上上签 / 契约锁 / DocuSign / Adobe Sign 等的 OpenAPI / SDK |
| ❌ 私有化签章 | 私有化电子签章服务器、签章前置机、SM2/RSA 数字证书组件、PDF/OFD 签章后处理 |
| ❌ 基础设施 | 时间戳服务（TSA）、CA 认证网关、电子证据保全、司法存证 |
| ❌ 数据对象 | 电子合同原文存证、签署轨迹哈希、签章图片、证书链、骑缝章等 |

**为什么工作流不集成电子签章**：

1. **业务定位决定**：项目运营管理系统（B 端内部工具）关注「审批流转」，电子签章属于法务/合同独立业务线，关注「签署生效」与法律效力。两者职责正交，合并会污染领域模型。
2. **合规与法律风险**：电子签章涉及 CA 认证、密评、等保三级、合同法/电子签名法合规审计、证据链保全、不可抵赖性。集成到自研工作流引擎会引入不可控的法律责任（一旦签署无效需由系统方举证）。
3. **避免厂商锁定**：电子签章 SaaS 普遍采用年度授权 + 证书计费 + 私有化部署差异，自研引擎不应承担这部分采购与运维成本。
4. **解耦架构**：签章是合同生命周期的一环，应在「合同管理」（`ydsz-pmis-project` 模块的 `ContractDO` 链路）独立抽象，由合同服务对接电子签章平台，工作流引擎仅作为「审批节点触发方」。

**实施约束**（code review 必查）：

- 后端 `ydsz-pmis-workflow` 模块不得新增 `ElectronicSign*` / `Esign*` / `SignatureCert*` / `PdfSeal*` / `ContractSign*` 等 Controller / Service / Entity / Mapper；
- `ydsz-pmis-workflow/pom.xml` 不得引入任何电子签章相关依赖（如 `esign-sdk` / `fadada-sdk` / `bouncycastle` 签章扩展 / `itextpdf` 签章模块等）；
- 前端工作流相关页面 / 组件不得引入签章相关组件库（如 `vue-esign` / `pdf-lib` 签章插件 / `signature_pad` 在工作流场景的复用等）；
- 权限码（`PermissionCodes`）不得增加 `esign:*` / `contract.sign:*` / `workflow:esign:*` 等命名空间；
- SQL 脚本（`deploy/sql/V*.sql`）不得新增 `pmis_sign_*` / `pmis_cert_*` / `pmis_contract_sign_*` 表；
- 如业务侧确有签署需求，须在 `ydsz-pmis-project` 的合同服务通过「外部跳转 / Webhook 回调」方式对接独立电子签章服务，工作流引擎仅传递 `contractId` + `signStatus` 等轻量状态字段，不持有签署原文或证书数据。

**未来扩展点（不包含在本约束内）**：合同服务（`ydsz-pmis-project`）可按需集成电子签章能力，但必须走独立 RFC + 法务/合规评审，不允许直接绕过本约束。

---

## 八、批次交付总览

| 批次 | 主题 | 关键交付 |
|---|---|---|
| 1-12 | 核心业务 | 14 模块 + 34 业务页面 + 主数据流 |
| 13 | 用户中心强化 | 2FA + Session + 登录审计 + 数据导出审计 + 二次认证 + 6 模块补全 60 测试 |
| 14-15 | 报表与驾驶舱 | EVM 看板 + Cockpit 6 KPI + 高级报表 6 类 + 5 张聚合 SQL 视图 |
| 16 | AI Agent 编排 | 4 策略 + Blackboard + 50 测试类 |
| 17 | JobHandler 重构 | cronjob→execution 跨模块 Feign 化 |
| 18-19 | 工作流 v1.1 + 质量门禁 | Checkstyle + 工作流 110 测试 |
| 20 | 混沌工程 + 金丝雀 | ChaosService + 5 类实验 |
| 21 | literule 规则引擎 | Aviator 表达式 + 规则链 + 阈值注入 + dry-run |
| 22 | 工作流 v2 | 设计器数据 API + 表单引擎 + 通知渠道 + 审批人去重 + 导入导出 + 50 步模拟 |
| 23 | 模板与监控 | 流程模板 + 流程监控仪表盘 + 流程自动触发 |
| 24 | chaos-dashboard | 前端 4 KPI + 2 ECharts + 实验 CRUD + Dry-Run + 5s 轮询 |
| 25 | 售后管理 | 质保期 + 运维工单 P1-P4 SLA + 满意度 9 测试类 |
| 26 | v1.1 优化 | Seata + WebSocket + CI 门禁 + ES + 文件增强 + 报表 + 批量操作等 24 项 |
| 27 | v1.2 优化 | Sentry 接入 + Redis 配置补全 + BFF + 工作流事件联动 + EasyExcel + ErrorBoundary + 暗黑模式 + 限流启用 + RocketMQ + i18n 6 页面 + PWA 等 |
| 28 | v1.3 国际化与代码优化 | i18n 基础设施 + 中英文语言包 + 多模块重构 + literule 计算类迁移 + Nacos 分组统一 + 端口对齐 |

**当前状态**：批次 28 已完成；下一阶段（批次 29+）规划等保测评 / 多租户改造，按业务节奏启动。

## 九、质量与可观测性

| 维度 | 指标 / 工具 | 阈值 / 现状 |
|---|---|---|
| 后端单测 | JUnit 5 + Mockito + AssertJ | 111 测试类，本地 `mvn test` 通过 |
| 后端覆盖率 | JaCoCo | 行覆盖 ≥ 60%，分支覆盖 ≥ 50%（门禁配置就绪，CI 待启用） |
| 前端单测 | Vitest + Vue Test Utils | 7 个测试文件，本地 `pnpm test` 通过 |
| 前端类型 | vue-tsc --noEmit | 0 错 |
| 前端 Lint | ESLint + Prettier | CI 阻断 |
| 静态检查 | Checkstyle（failsOnError=true） | CI 阻断 |
| 静态分析 | SpotBugs + FindSecBugs（Low 阈值） | CI 阻断 |
| 依赖漏洞 | OWASP dependency-check（CVSS ≥ 7 阻断） | 默认跳过，CI 中 -DskipDependencyCheck=false 启用 |
| 异常聚合 | Sentry（前端 + 后端 dynamic import） | 默认关闭，生产环境手动启用 |
| 链路追踪 | TraceId Filter + Logback MDC | 单服务内 TraceId 透传，跨服务追踪待接入 |
| 健康检查 | Spring Boot Actuator | `/actuator/health` 就绪 |
| 日志 | Logback + JSON 结构化（logstash-logback-encoder） | 文件输出，ELK 聚合待接入 |

## 十、文档导航

### 10.1 现有文档

| 文档 | 链接 |
|---|---|
| 5 分钟快速启动 | [docs/QUICKSTART.md](docs/QUICKSTART.md) |
| 详细部署手册 | [docs/DEPLOY.md](docs/DEPLOY.md) |
| 数据库初始化脚本 | [docs/V1.0.0.sql](docs/V1.0.0.sql) |

> **说明**: 项目规范、运维手册、安全合规、API 文档等将在后续批次补齐。当前所有规范与决策记录请参考代码注释与项目记忆（`.trae-cn/memory/projects/`）。

### 10.2 API 文档

- 后端启动后访问 Swagger UI: `http://localhost:{port}/swagger-ui.html`（springdoc-openapi 已集成）
- 前端有 `scripts/openapi-gen.mjs` 脚本可基于 OpenAPI 生成前端 API 客户端

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
| 运维工程师（SRE） | 1 | 部署 / 监控 |

## 十三、版本与许可

- **当前版本**: v1.3.0-SNAPSHOT（批次 28 完成，2026-07-04）
- **首发版本**: v1.0.0 GA（2026-06-30）
- **本次更新（2026-07-08）**:
  - 端口按 pom.xml 构建顺序重排（userinfo 9001, system 9002, project 9003, message 9004, cronjob 9005, workflow 9006, agent 9007）
  - 8 个 bootstrap.yml（Nacos 连接 + 端口 + shared-configs 引用）
  - 21 套 Nacos 配置模板（7 服务 × 3 环境）放置在 `src/main/resources/nacos-config/`
  - `ydsz-pmis-common` 模块下集中维护 Nacos 共享配置 `ydsz-pmis-common.yaml`
  - 10 个子模块独立 README（快速上手 + 配置 + 启动 + 常见问题）
- **文档密级**: 内部受控
- **仓库地址**: `https://gitlab.njydsz.com/ydsz/oursource/ydsz-pmis`
- **许可**: 南京云顶数字科技有限公司内部使用

---

> 本 README 由 PMIS 团队维护，与代码同步更新（v1.3.0_2026-07-06）。
> 任何变更请走 PR + Code Review 流程。
