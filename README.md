# YDSZ PMIS

> 以 WBS 业财一体化为锚点、EVM 挣值管理为预警引擎、双费率（对外报价 + 对内成本）为利润核算基础的**项目全生命周期运营管理系统**。

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1-6DB33F?logo=springboot)](https://spring.io/projects/spring-boot)
[![Spring Cloud](https://img.shields.io/badge/Spring%20Cloud-2025.1-6DB33F?logo=spring)](https://spring.io/projects/spring-cloud)
[![JDK](https://img.shields.io/badge/JDK-21-ED8B00?logo=openjdk)](https://openjdk.org/projects/jdk/21/)
[![Vue](https://img.shields.io/badge/Vue-3.5-4FC08D?logo=vuedotjs)](https://vuejs.org/)
[![TypeScript](https://img.shields.io/badge/TS-5.8-3178C6?logo=typescript)](https://www.typescriptlang.org/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-18-336791?logo=postgresql)](https://www.postgresql.org/)
[![Redis](https://img.shields.io/badge/Redis-8-DC382D?logo=redis)](https://redis.io/)
[![Qiankun](https://img.shields.io/badge/Qiankun-2.10-blue)](https://qiankun.umijs.org/)
[![Vite](https://img.shields.io/badge/Vite-6-646CFF?logo=vite)](https://vitejs.dev/)
[![pnpm](https://img.shields.io/badge/pnpm-9-F69220?logo=pnpm)](https://pnpm.io/)
[![License](https://img.shields.io/badge/license-Proprietary-red)]()

---

## 目录

1. [业务定位](#1-业务定位)
2. [技术架构](#2-技术架构)
3. [模块全景](#3-模块全景)
4. [快速开始](#4-快速开始)
5. [仓库结构](#5-仓库结构)
6. [工程规范](#6-工程规范)
7. [文档导航](#7-文档导航)

---

## 1. 业务定位

串联「**商机 → 立项 → 合同 → 执行 → 回款 → 结项 → 售后**」全生命周期，覆盖软件定制 + 人力外包双业态。

| 能力域 | 核心场景 |
|--------|----------|
| **项目全生命周期** | 商机分级(A/B/C) → WBS 预算编制 → 合同模板/补充/变更/风险 → 执行追踪(工时/采购/费用) → 成本归集 → 收入确认 → 利润核算 → 开票/回款 → 变更管理 → 结项 → 售后(质保/工单/满意度) |
| **EVM 挣值管理** | PV/EV/AC 三量 + CPI/SPI 偏差指数 + EAC/VAC 预测 + 红黄绿三级阈值告警 |
| **双费率利润核算** | 对外 RateCard(职级×技术栈×客户) + 对内 RateInternal(职级×部门) + 双率对比 + 多版本模拟 |
| **自研工作流审批** | BPMN 2.0 + 流程设计器 + 会签/或签/转办/委派/驳回/撤回 + 内嵌审批(iframe) + 第三方 IM 审批同步(钉钉/企微/飞书) |
| **自研规则引擎** | 7 种规则类型(决策表/决策树/评分卡/脚本/CEP 等) + DSL 编排 + A/B 测试 + 断点调试 + 热加载 |
| **自研分布式调度** | Leader 选举 + 分片广播 + DAG 工作流 + 故障自愈 + SSE 实时日志 + GLUE 在线编辑 + 多类型任务 |
| **统一消息中心** | 12 渠道(站内信/邮件/短信/钉钉/企微/飞书/WebSocket 等) + 模板引擎 + 批量流控 + 死信重试 + 智能免打扰 |
| **网盘知识库** | 分片上传/断点续传 + SHA-256 秒传 + 版本控制 + ACL 权限 + 分享链接 + 全文检索 + 在线预览 + Office Online |
| **AI Agent 平台** | 多 LLM Provider + 流式 SSE 对话 + RAG 知识增强(pgvector) + 6 种 Agent 模式 + 工具调用 + 人工审批 |
| **统一安全防护** | XSS/SQL 注入/CSRF 防护 + 18 种数据脱敏 + 多维限流 + 验证码 + API 签名 + TOTP 2FA + 自动封禁 |

---

## 2. 技术架构

### 2.1 技术选型

| 维度 | 选型 |
|------|------|
| **语言 & 框架** | Java 21 + Spring Boot 4.1 + Spring Cloud 2025.1 + Spring Cloud Alibaba 2025.1 |
| **微服务治理** | Nacos (注册/配置) + Sentinel (限流/熔断) + OpenFeign (声明式调用) |
| **数据访问** | MyBatis-Plus 3.5 + PostgreSQL 18 + HikariCP + JSqlParser (数据权限 SQL 改写) |
| **缓存 & 锁** | Redis 8 + Redisson + **自研多级缓存框架** (11 种策略，JMH 基准) |
| **消息队列** | RocketMQ 5.x (主) + 统一 MQ 抽象层适配 7 种引擎 |
| **分布式事务** | Seata 2.5 (AT / TCC / SAGA / Local) |
| **JSON 引擎** | **自研 ydsz-json** (ASM 字节码加速 + 零拷贝反序列化) |
| **任务调度** | **自研 ydsz-cronjob** (Leader 选举 / 分片 / DAG / 自愈 / 连接器) |
| **规则引擎** | **自研 ydsz-literule** (QLExpress + DSL + 决策表 + CEP + A/B 测试) |
| **工作流** | **自研 ydsz-workflow** (BPMN 2.0 + bpmn-js 设计器) |
| **文件存储** | **自研 common-file** SPI 适配 7 种平台 (Local / MinIO / S3 / OSS / COS / OBS / 七牛) |
| **可观测性** | Micrometer + SkyWalking + Sentry + TraceId (MDC 全链路) |
| **前端** | Vue 3.5 + TypeScript 5.8 + Vite 6 + Element Plus 2.10 + Tailwind CSS 3.4 |
| **微前端** | Qiankun 2.10 (1 基座 + 9 子应用) |
| **工程化** | pnpm workspace + Turborepo + Pinia 3.0 + Vue Router 4.5 + Vue I18n 11 |
| **部署** | Docker + Docker Compose + K8s Kustomize / Helm |

### 2.2 公共模块 L1-L6 分层

```
L1 基础设施    json (117 files) ─┐
                core (33 files) ─┤
L2 工具层       util (69 files) ─┤
L3 基础服务     domain (43 files) ─┤  entity hierarchy / exception i18n
                exception (33 files)─┘
L4 基础数据     jdbc (69) / redis (42) / cache (68) / lock (36) / thread (3) / tenant (22)
L5 业务服务     auth (80) / safe (114) / feign (42) / audit (32) / notify (48) / queue (56)
                event (38) / config (28) / seata (18) / socket (24) / netty (16)
                file (52) / docs (20) / excel (95) / search (24) / sentry (18)
L6 应用层       base (28) → web (36) / app (28)
```

> **原则**：上层依赖下层，下层不反向依赖；80+ SPI 扩展点；全部 Spring Boot 3 `AutoConfiguration.imports` 自动装配。

### 2.3 微服务拓扑

| # | 服务 | 端口 | 职责 | DDD 分层 |
|---|------|------|------|----------|
| 1 | **ydsz-gateway** | 9000 | 统一入口 / JWT 鉴权 / 路由转发 / 限流 / CORS | 单体 |
| 2 | **ydsz-userinfo** | 9001 | 用户认证 / 组织架构 / RBAC 权限 / 菜单 | 五层 |
| 3 | **ydsz-system** | 9002 | 系统配置 / 数据字典 / 应用注册 / 变量管理 | 五层 |
| 4 | **ydsz-project** | 9003 | 项目全生命周期 / 预算 / 合同 / EVM / 财务 / 费率 | 五层 |
| 5 | **ydsz-message** | 9004 | 多渠道消息 / 模板 / 批量 / 路由 / 死信 / 偏好 | 五层 |
| 6 | **ydsz-cronjob** | 9005 | 分布式调度 / DAG / 分片 / 自愈 / 告警 / 统计 | 五层 |
| 7 | **ydsz-workflow** | 9006 | BPMN 2.0 流程引擎 / 审批 / 会签 / 委派 / 监控 | 五层 |
| 8 | **ydsz-agent** | 9007 | LLM 对话 / RAG / Agent 编排 / 工具调用 / 护栏 | 五层 |
| 9 | **ydsz-nextwiki** | 9008 | 统一文件管理平台 / 分享 / 检索 / 预览 / 回收站 / AI 摘要 | 五层 |
| 10 | **ydsz-literule** | 9009 | 统一规则管理平台 / DSL 编排 / 决策表 / A/B 测试 / CEP | 五层 |

> **注意**：nextwiki 和 literule 均为独立部署的服务（拥有独立端口和 Web 控制台），同时通过 Feign 接口为其他模块提供能力。literule 额外通过 30+ SPI 接口实现依赖反转，避免业务模块直接依赖规则引擎。**

**跨服务 Feign 调用**：

```
userinfo ←── 所有模块 (用户/组织查询)
system   ←── 所有模块 (配置/字典查询)
message  ←── 所有模块 (通知推送)
workflow ←── project (启动审批)
literule ←── project / workflow (规则评估)
cronjob  ←── literule (规则触发任务)
agent    ←── workflow / cronjob (智能分析)
agent    →── nextwiki (RAG 知识检索)
```

### 2.4 前端微前端架构

```
main (Qiankun 基座) ── 动态路由注册 / 全局状态通信 / hover 按需预加载
 ├── system-web      (系统管理)
 ├── userinfo-web    (用户/组织/角色)
 ├── project-web     (项目管理 — 核心业务)
 ├── workflow-web    (工作流 — bpmn-js 设计器)
 ├── message-web     (消息中心)
 ├── cronjob-web     (任务调度)
 ├── nextwiki-web    (知识库)
 ├── literule-web    (规则引擎)
 └── agent-web       (AI Agent)

comm/ (共享库) ── @core / constants / types / stores / locales / effects / shared-auth
conf/ (构建配置) ── vite / tailwind / tsconfig / node-utils
```

---

## 3. 模块全景

### 3.1 后端模块成熟度

| 等级 | 模块 |
|------|------|
| ⭐⭐⭐⭐⭐ **产品级** | json, core, util, domain, exception, jdbc, redis, cache, lock, auth, safe, feign, notify, queue, file, excel, userinfo, project, workflow, message, cronjob, nextwiki, literule |
| ⭐⭐⭐⭐ **完整** | tenant, audit, event, config, seata, docs, search, sentry, base, web, app, gateway, system, agent |
| ⭐⭐⭐ **基础** | thread, socket, netty |

### 3.2 前端模块成熟度

| 等级 | 模块 |
|------|------|
| ⭐⭐⭐⭐ **完整** | main, project-web |
| ⭐⭐⭐ **基础** | system-web, userinfo-web, workflow-web, message-web, cronjob-web, nextwiki-web, literule-web, agent-web |

> 详细能力现状模型见 [ydsz-module-capability-model.md](ydsz-module-capability-model.md)（基于全量源码深度分析）。

### 3.3 关键数据

| 维度 | 数据 |
|------|------|
| 后端 Java 源文件 | 2500+ |
| REST API 端点 | 200+ |
| Feign 客户端 | 20+ |
| 实体类 | 100+ |
| 前端应用 | 10 (1 基座 + 9 子应用) |
| 前端共享包 | 30+ |
| 数据库表 | 126+ |
| 存储平台 | 7 种 |
| 通知渠道 | 12 种 |
| 消息队列 | 7 种 |

---

## 4. 快速开始

### 4.1 环境要求

| 工具 | 版本 | 说明 |
|------|------|------|
| JDK | 21 | Spring Boot 4.1 要求 |
| Maven | 3.9+ | 后端构建 |
| Node.js | ≥ 20 | 前端构建 |
| pnpm | ≥ 9 | 强制 lockfile 统一 |
| PostgreSQL | 18 | 主数据库 |
| Redis | 8 | 缓存 / 会话 / 分布式锁 |
| Nacos | 2.3.2+ | namespace: `ydsz` |
| Docker | 24+ | 基础设施编排 |

### 4.2 本地启动

```bash
# 1. 环境检查
./deploy/scripts/check-env.sh

# 2. 启动基础设施 (PG / Redis / Nacos / MinIO)
cd deploy/docker
docker compose -f docker-compose.dev.yml up -d

# 3. 初始化数据库
psql -h localhost -U ydsz -d ydsz_pmis -f docs/V1.0.0.sql

# 4. 启动后端（按依赖顺序：gateway → userinfo → system → 其他）
cd ydsz-backend
mvn -pl ydsz-gateway spring-boot:run
mvn -pl ydsz-userinfo spring-boot:run
# ... 依次启动其余模块

# 5. 启动前端
cd ydsz-frontend
pnpm install
pnpm dev
```

访问入口：
- 前端主应用：`http://localhost:5173`
- API 网关：`http://localhost:9000`
- Nacos 控制台：`http://127.0.0.1:8848/nacos` (nacos / nacos)
- MinIO 控制台：`http://127.0.0.1:9101` (minioadmin / minioadmin)

### 4.3 构建命令

```bash
# 后端全量构建
mvn -pl ydsz-backend -am clean package -DskipTests

# 前端全量构建
cd ydsz-frontend && pnpm build

# 类型检查
pnpm check:type

# 代码检查
pnpm lint
```

> 详细部署文档：[deploy/docs/QUICKSTART.md](deploy/docs/QUICKSTART.md) | [deploy/docs/DEPLOY.md](deploy/docs/DEPLOY.md) | [deploy/docs/INFRASTRUCTURE.md](deploy/docs/INFRASTRUCTURE.md)

---

## 5. 仓库结构

```
ydsz-pmis/
├── ydsz-backend/                     # 后端 (Java 21, Maven)
│   ├── ydsz-common/                  # 30 个公共模块 (L1-L6 分层, 不独立部署)
│   ├── ydsz-gateway/                 # API 网关 (9000)
│   ├── ydsz-userinfo/                # 用户中心 (9001)
│   ├── ydsz-system/                  # 系统服务 (9002)
│   ├── ydsz-project/                 # 项目管理 (9003)
│   ├── ydsz-message/                 # 消息中心 (9004)
│   ├── ydsz-cronjob/                 # 分布式调度 (9005)
│   ├── ydsz-workflow/                # 工作流引擎 (9006)
│   ├── ydsz-agent/                   # AI Agent (9007)
│   ├── ydsz-nextwiki/                # 统一文件管理平台 (9008)
│   ├── ydsz-literule/                # 统一规则管理平台 (9009)
│   └── pom.xml                       # 父 POM (版本管理)
├── ydsz-frontend/                    # 前端 (Vue 3, pnpm monorepo)
│   ├── main/                         # Qiankun 基座
│   ├── apps/                         # 9 个微应用
│   ├── comm/                         # 30+ 共享包
│   ├── conf/                         # 构建配置
│   └── bash/                         # 构建脚本
├── deploy/                           # 部署配置
│   ├── docker/                       # Docker Compose
│   ├── helm/                         # Helm Chart
│   ├── k8s/                          # K8s Kustomize
│   ├── scripts/                      # 部署脚本
│   └── docs/                         # 部署文档
├── docs/                             # 项目文档 & SQL 脚本
├── .trae/rules/                      # AI 代码规范
└── README.md
```

---

## 6. 工程规范

### 6.1 强制约束

| 规则 | 说明 |
|------|------|
| **实体命名** | Entity 不以 `DO` 为后缀（例外：AgentDefinitionDO 等 6 个同名冲突类） |
| **禁止通用 CRUD** | 后端禁止 BaseCrudService / BaseCrudController；前端禁止 createCrudApi 工厂 |
| **禁止行内 FQN** | Java 代码必须 import 后用简单类名 |
| **禁止 @SuppressWarnings** | 从根源修复警告，不压制 |
| **版本号统一** | 项目版本号统一为 `1.0.0`，上线前不得变更 |
| **覆盖率非门禁** | JaCoCo 仅本地参考，不作 CI 阻断 |
| **脚本优先 Python** | 禁止 PowerShell（编码/BOM 污染问题） |
| **工作流 PC Only** | ydsz-workflow 永远不适配移动端 |
| **Git 提交信息** | 使用中文 |

### 6.2 架构原则

- **DDD 五层架构**：所有业务模块遵循 `api → domain → infra → server → web` 分层
- **L1-L6 依赖方向**：上层依赖下层，ArchUnit 强制执行
- **依赖反转**：literule 通过 30+ SPI 接口回调业务模块，避免循环依赖
- **统一安全模型**：`@Idempotent`(防重) + `@RateLimit`(限流) + `@Audit`(审计) + `@AuthApiPermission`(权限)
- **Feign 全面容错**：所有 Feign 客户端均有 Fallback 工厂
- **API 显式定义**：每个 API 方法必须显式声明，不使用泛型工厂

> 完整规范见 [.trae/rules/](.trae/rules/)

---

## 7. 文档导航

| 文档 | 说明 |
|------|------|
| [模块能力现状模型](ydsz-module-capability-model.md) | 全量源码深度分析，所有模块的能力评估 |
| [快速启动](deploy/docs/QUICKSTART.md) | 5 分钟本地启动指南 |
| [部署手册](deploy/docs/DEPLOY.md) | 生产环境部署详细步骤 |
| [基础设施](deploy/docs/INFRASTRUCTURE.md) | PG / Redis / Nacos / MinIO 部署 |
| [数据库初始化](docs/V1.0.0.sql) | 全量建表 + 初始化数据 |
| [代码规范](.trae/rules/) | AI 辅助编码规则集 |
| 模块 README | 各模块目录下的 `README.md` |
| API 文档 | 启动后访问 `http://localhost:{port}/doc.html` (Knife4j) |

---

## 团队

| 角色 | 职责 |
|------|------|
| 项目经理 | 批次化交付 + 验收 |
| 产品经理 | PRD 演进 |
| 前端开发 | Vue 3.5 + 微前端 + 设计器 |
| 后端开发 | 微服务 + 自研引擎 (工作流/规则/调度) |
| AI 工程师 | Agent + LLM + RAG |
| 测试 | 自动化 + 性能 |
| 运维 SRE | 部署 / 监控 |

---

## 版本

- **当前版本**：`v1.0.0-SNAPSHOT`（开发阶段）
- **版本策略**：上线前所有版本号统一为 `1.0.0`，正式上线后由架构组统一决策升级
- **最近更新**：2026-08-02 — 基于全量源码深度分析，重构模块能力模型与技术架构文档
- **许可**：南京云顶数字科技有限公司内部使用

---

> 本 README 由 YDSZ 团队维护，与代码同步更新。变更请走 PR + Code Review 流程。