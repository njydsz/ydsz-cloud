<!--
================================================================================
YDSZ PMIS · 项目运营管理系统 · README
--------------------------------------------------------------------------------
项目代号:   YDSZ PMIS
所属公司:   南京云顶数字科技有限公司
版本:       v1.3.0-SNAPSHOT
最近更新:   2026-07-04
维护团队:   PMIS 研发部
文档密级:   内部受控 · 禁止外传

本文件是仓库的入口文档，向新成员/合作方/审核方解释：
  1. 是什么（产品定位 / 业务覆盖）
  2. 包含什么（仓库结构 / 模块拓扑）
  3. 怎么跑（环境要求 / 本地启动 / 测试命令 / 部署流程）
  4. 怎么查（文档导航）

变更需走 PR + Code Review。
================================================================================
-->

# YDSZ PMIS · 项目运营管理系统

> 南京云顶数字科技有限公司 · 软件定制 + 人力外包 双业态 · 业财一体化精细化运营平台
>
> **当前版本**: `v1.3.0-SNAPSHOT` · **最近更新**: 2026-07-04

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.7-6DB33F?logo=springboot)]()
[![Spring Cloud](https://img.shields.io/badge/Spring%20Cloud-2025.1.2-6DB33F?logo=spring)]()
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

## 二、核心数据

| 维度 | 数字 | 说明 |
|---|---|---|
| 后端微服务 | **9 模块（7 部署 + 2 库）** | gateway / userinfo / workflow / project / agent / system / cronjob（部署）+ common / literule（库）+ 父 pom |
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
| **自研工作流 v2** | `pmis_flow_*` 表 + BPMN 2.0 解析 + 设计器（数据 API）+ 表单设计器 + 流程模板 + 通知渠道 + 审批人自动去重 + 流程导入导出 + 50 步模拟运行 + 流程监控仪表盘 |
| **混沌工程** | ChaosService 实验注册 + FeatureFlag 双保险 + 注入统计 + chaos-dashboard 实时监控 |
| **变更-交付-结项闭环** | 5 类变更（范围/成本/合同/人员/进度） + 8 类项目交付物标准化（CD1-CD5 门径） + 3 类结项（正式/预/强制）准入 |
| **国际化（i18n）** | vue-i18n 10 + 中/英文语言包 + 6 个核心页面覆盖 + 语言切换组件 + 集中化文案管理（部分业务页面 i18n 迁移待完成） |

### 3.2 技术能力

| 维度 | 选型 |
|---|---|
| **后端框架** | Spring Boot 4.0.7 + Spring Cloud 2025.1.2 + Spring Cloud Alibaba 2025.1.0.0 |
| **后端治理** | Nacos 2.x（注册/配置）+ Sentinel 1.8.9（限流/熔断）+ Seata 2.5.0（AT 模式分布式事务）+ OpenFeign + RocketMQ 5.x + XXL-JOB 2.4+ |
| **数据** | PostgreSQL 18 + MyBatis-Plus 3.5.16 + Redis 7 + MinIO + 5 张聚合 SQL 视图（零 Java JOIN） |
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
| 后端治理 | Nacos / Sentinel / Seata / OpenFeign | 2.x / 1.8.9 / 2.5.0 / 4.x | 注册/限流/事务/调用 |
| 表达式 | Aviator（literule 模块） | 5.4.3 | 规则引擎表达式 |
| 数据库 | PostgreSQL | 18 | 主库 |
| 缓存/锁 | Redis / Redisson | 7 / 4.6.1 | 会话 / 分布式锁 / 幂等 |
| 消息 | RocketMQ | 5.x（spring-boot-starter 2.3.1） | 异步事件 |
| 调度 | XXL-JOB | 2.4+ | 分布式任务 |
| AI | Spring AI + AgentScope | - | 多智能体编排 |

### 4.2 微服务清单（7 部署单元 + 2 库）

| 模块 | artifactId | 端口 | 职责 |
|---|---|---|---|
| API 网关 | ydsz-pmis-gateway | **9000** | 路由 + 鉴权 + 限流 + CORS |
| 系统基础 | ydsz-pmis-system | **9001** | 文件 / 配置 / 审计 / 通知 / 消息模板 |
| 用户信息 | ydsz-pmis-userinfo | **9002** | 登录 / Token / 2FA / 登录审计 / 二次认证 / RBAC / 部门 / 人员 / 职级 / 字典 / 资源池 / Bench / 员工标签 |
| 项目 | ydsz-pmis-project | **9003** | 商机 / 立项 / 合同 / 变更 / WBS / EVM / 成本 / 收入 / 风险 / 工时 / 发票 / 付款 / 客户信用 / 资源 / Dashboard / Report / 费率 / 交付 / 收尾 / 利润 |
| 调度 | ydsz-pmis-cronjob | **9004** | XXL-JOB 调度 + JobHandler 注册 |
| 工作流 | ydsz-pmis-workflow | **9005** | 自研 `pmis_flow_*` 引擎 + BPMN 2.0 解析 + 模板 + 模拟 |
| AI Agent | ydsz-pmis-agent | **9006** | 5 Agent + 4 编排 + 5 LLM Provider |
| 公共（库） | ydsz-pmis-common | — | 统一响应 / AOP / 注解 / Feign / 敏感数据 / JobHandler / Sentry / I18n / 权限码 / 混沌（不独立部署） |
| 轻量规则引擎（库） | ydsz-pmis-literule | — | 表达式驱动 + 规则链 + 阈值注入 + dry-run（不独立部署） |

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
| Redis | 7 | 缓存 / 会话 / 分布式锁 |
| Nacos | 2.x | namespace `pmis` / group `PMIS_GROUP_{DEV/SIT/UAT/PROD}` |
| Docker | 24+ | Compose 编排基础设施 |

### 5.2 本地启动

**5 分钟快速启动**：参考 [docs/QUICKSTART.md](docs/QUICKSTART.md)
**详细部署手册**：参考 [docs/DEPLOY.md](docs/DEPLOY.md)

```bash
# 0. 环境检查（首次部署前必跑）
./deploy/scripts/check-env.sh   # Linux/macOS
.\deploy\scripts\check-env.bat  # Windows

# 1. 一键启动（基础设施 + 后端 + 前端）
./deploy/scripts/start-all.sh

# 2. 单独启动
