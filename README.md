<p align="center">
  <h1 align="center">Ydsz Cloud</h1>
  <p align="center">
    基于 Spring Boot 4 &amp; Spring Cloud 的企业级微服务开发平台
  </p>
</p>

<p align="center">
  <a href="https://www.oracle.com/java/technologies/javase/jdk21-archive-downloads.html"><img src="https://img.shields.io/badge/JDK-21-orange.svg" alt="JDK 21"></a>
  <a href="https://spring.io/projects/spring-boot"><img src="https://img.shields.io/badge/Spring%20Boot-4.1.0-brightgreen.svg" alt="Spring Boot 4.1.0"></a>
  <a href="https://spring.io/projects/spring-cloud"><img src="https://img.shields.io/badge/Spring%20Cloud-2025.1.2-blue.svg" alt="Spring Cloud 2025.1.2"></a>
  <a href="https://github.com/alibaba/spring-cloud-alibaba"><img src="https://img.shields.io/badge/Spring%20Cloud%20Alibaba-2025.1.0.0-ff69b4.svg" alt="Spring Cloud Alibaba"></a>
  <a href="./LICENSE"><img src="https://img.shields.io/badge/License-MIT-yellow.svg" alt="License: MIT"></a>
  <a href="https://maven.apache.org/"><img src="https://img.shields.io/badge/Maven-3.9+-C71A36.svg" alt="Maven 3.9+"></a>
</p>

---

## 项目简介

**Ydsz Cloud** 是一套面向企业级应用的微服务快速开发平台，基于 **Spring Boot 4.1.0**、**Spring Cloud 2025.1.2** 和 **Spring Cloud Alibaba 2025.1.0.0** 构建。平台采用 **DDD（领域驱动设计）** 五层分层架构，内置 **10 大核心模块**（1 网关 + 8 微服务 + 1 公共依赖库），覆盖用户认证、系统管理、流程引擎、消息引擎、任务引擎、规则引擎、网盘引擎、智能引擎等企业级全业务场景。

平台对标 **若依（RuoYi）**、**Pig**、**maku-boot**、**SpringBlade**、**JeecgBoot** 等主流开源快速开发平台，在架构设计、代码质量、工程规范与安全治理方面对齐 **阿里巴巴 Java 开发手册**、**Google Java Style Guide** 等行业标准。

### 核心特性

- **前沿技术栈**：Java 21 虚拟线程 + Spring Boot 4 + Spring Cloud 2025.1.2 + Jakarta EE 10
- **DDD 分层架构**：严格 `api` / `domain` / `infra` / `server` / `web` 五层分离，依赖方向单向收敛
- **自研引擎矩阵**：「规则引擎（对标 Drools + LiteFlow）+ 任务调度（对标 XXL-Job + PowerJob）+ 工作流（BPMN 2.0）+ AI Agent 框架」——四大引擎全部自研，开箱即用
- **多租户隔离**：支持 SINGLE（共享表）、MULTI（字段隔离）、ISOLATE_DB（独立数据库）三种策略
- **全渠道消息**：12 种通知渠道（短信/邮件/Push/企微/钉钉/飞书/微信小程序/支付宝小程序等），支持 DAG 编排与跨渠道抑制
- **安全纵深防御**：JWT + RBAC + 数据权限 + PII 脱敏 + XSS/SQL 注入/CSRF 防护 + 敏感配置加密（AES-256-GCM）
- **生产可观测**：Prometheus + Grafana + Sentry + ELK/Loki + Micrometer Tracing（W3C TraceContext）

---

## 系统架构

---

## 技术选型

| 分层 | 技术 | 版本 | 说明 |
|------|------|------|------|
| **基础框架** | Spring Boot | 4.1.0 | 新一代企业级应用框架 |
| | Spring Cloud | 2025.1.2 | 微服务治理套件 |
| | Spring Cloud Alibaba | 2025.1.0.0 | Nacos / Sentinel / Seata |
| **语言 & 构建** | Java | 21 (LTS) | 虚拟线程 + 模式匹配 |
| | Maven | 3.9+ | 聚合多模块构建 |
| **数据持久化** | MyBatis-Plus | 3.5.16 | 增强 ORM（Spring Boot 4 Starter） |
| | PostgreSQL | 42.7.4 | 主数据库（共享主库） |
| | Druid | 1.2.28 | 连接池 |
| | Dynamic-Datasource | 4.3.1 | 读写分离 |
| **缓存 & 锁** | Redis / Redisson | 4.6.1 | 分布式缓存 / 分布式锁 |
| **消息队列** | RocketMQ Spring | 2.3.1 | 异步消息 / 事务消息 |
| **分布式事务** | Seata | 2.5.0 | AT / TCC / SAGA |
| **流量控制** | Sentinel | 1.8.9 | 限流 / 熔断 / 降级 |
| | Resilience4j | 2.4.0 | 重试 / 舱壁 / 速率限制 |
| **对象存储** | MinIO / 阿里云 OSS / 腾讯云 COS / 华为云 OBS / 七牛 / AWS S3 | — | 7 种存储平台统一抽象 |
| **认证鉴权** | jjwt | 0.12.6 | JWT Token |
| **文档 & API** | SpringDoc + Knife4j | 3.0.3 / 4.5.0 | OpenAPI 3.0 文档 |
| **对象映射** | MapStruct | 1.6.3 | 编译期代码生成 |
| **JSON** | ydsz-common-json<br/>（YdszJson） | 自研 | 零外部依赖 · ASM 字节码 · SIMD 向量化 |
| **监控** | Micrometer + Prometheus + Sentry | — | 指标采集 / 异常追踪 |
| **日志** | Logback + Logstash Encoder | 7.4 | JSON 格式日志输出 |

---

## 模块说明

```
ydsz-cloud/
├── ydsz-common/              # 🧱 公共能力底座（30 子模块，不独立部署）
│   ├── ydsz-common-core      # L1：统一响应 / TraceId / 特性开关
│   ├── ydsz-common-util      # L2：30+ 工具类（加密 / IP / 雪花ID）
│   ├── ydsz-common-json      # L2：高性能 JSON 引擎（ASM / SIMD）
│   ├── ydsz-common-domain    # L3：DDD 基类 / 领域事件
│   ├── ydsz-common-exception # L3：统一异常 / RFC 7807 ProblemDetail
│   ├── ydsz-common-jdbc      # L4：MyBatis-Plus 增强 / 行权限
│   ├── ydsz-common-redis     # L4：Redis 操作封装（9 类 ops）
│   ├── ydsz-common-lock      # L4：分布式锁（可重入/公平/联锁/读写/信号量）/ 幂等
│   ├── ydsz-common-cache     # L4：多策略本地缓存（W-TinyLFU）
│   ├── ydsz-common-thread    # L4：共享线程池
│   ├── ydsz-common-tenant    # L4：多租户隔离
│   ├── ydsz-common-auth      # L5：JWT / RBAC / TOTP 2FA
│   ├── ydsz-common-safe      # L5：脱敏 / XSS / 限流 / CSRF
│   ├── ydsz-common-feign     # L5：OpenFeign + Resilience4j
│   ├── ydsz-common-audit     # L5：操作日志 / Disruptor 批写
│   ├── ydsz-common-file      # L5：7 种存储平台 / 分片 / 秒传
│   ├── ydsz-common-notify    # L5：6 种通知渠道抽象
│   ├── ydsz-common-queue     # L5：6 种 MQ 抽象（Stream/Kafka/Rocket/List/PubSub/Rabbit）
│   ├── ydsz-common-docs      # L5：8 种文档解析 / OCR
│   ├── ydsz-common-excel     # L5：高性能 Excel 读写
│   ├── ydsz-common-netty     # L5：TCP 通信
│   ├── ydsz-common-socket    # L5：WebSocket 集群广播
│   ├── ydsz-common-search    # L5：统一搜索（PG / ES）
│   ├── ydsz-common-event     # L5：事务性 Outbox
│   ├── ydsz-common-config    # L5：敏感配置加密
│   ├── ydsz-common-seata     # L5：Seata 分布式事务
│   ├── ydsz-common-sentry    # L5：统一监控告警
│   ├── ydsz-common-base      # L6：HTTP 公共基座
│   ├── ydsz-common-web       # L6：PC Web 基座（Spring Security）
│   └── ydsz-common-app       # L6：移动端 App 基座（API 签名）
│
├── ydsz-gateway/             # 🚪 API 网关 :9000（WebFlux 反应式）
├── ydsz-userinfo/            # 👤 用户信息中心 :9002（登录 / RBAC / 组织架构 / OAuth2）
├── ydsz-system/              # ⚙️ 系统基础服务 :9001（参数 / 字典 / 多租户）
├── ydsz-nextwiki/            # 📁 网盘知识库 :9003（文件管理 / Office 预览 / WOPI）
├── ydsz-message/             # 📨 消息通知引擎 :9004（12 渠道 / DAG 编排 / 灰度）
├── ydsz-workflow/            # 🔀 工作流引擎 :9005（BPMN 2.0 / DMN 决策表）
├── ydsz-cronjob/             # ⏰ 分布式调度 :9006（Leader 选举 / 分片广播）
├── ydsz-literule/            # 📏 规则引擎 :9007（DSL / 热加载 / A/B 测试）
└── ydsz-agent/               # 🤖 AI 智能体 :9008（ReAct / RAG / Tool Calling）
```

### 各模块能力详述

| 模块 | 核心能力 |
|------|----------|
| **ydsz-gateway** | 路由分发 · JWT 鉴权 · CORS · IP 黑白名单（统一 IpAccessControl） · 灰度路由（权重加权 + 比例分流） · Redis+Lua 令牌桶限流 · WebSocket 握手认证 · API 版本协商 · 网关层 RBAC · W3C 链路追踪 |
| **ydsz-userinfo** | 登录认证（密码 + 验证码 + LDAP/ADFS） · JWT Token · RBAC 6 要素 · 部门树 · OAuth2 授权码 · 登录锁定（5 次/30 min） · 国际化 |
| **ydsz-system** | 系统参数（Redis 缓存 + 穿透防护） · 数据字典（树形 + 版本快照） · OAuth2 应用注册 · 多租户（租户 + 套餐 + 权限） · 全局搜索 |
| **ydsz-nextwiki** | 文件秒传（SHA-256） · 版本控制（20 版本） · 分享 + ACL · 全文搜索 · Office 预览（LibreOffice → PDF） · WOPI（OnlyOffice/Collabora） · ClamAV 病毒扫描 · OCR · AI 摘要 |
| **ydsz-message** | 12 种通知渠道（枚举） · 模板（i18n + 版本） · 用户偏好 · 条件路由 + 通道降级链 · 模板灰度标记 · 敏感词过滤（DFA） · RocketMQ 死信 |
| **ydsz-workflow** | YDSZ-Flow + BPMN 2.0 · 11 种节点类型 · 定时器 · SLA · 设计器 · DMN 决策表 · 审批/委派/评论/嵌入式审批面板 |
| **ydsz-cronjob** | Leader 选举 · 多分区调度 · Cron + 固定频率 + 固定延迟 + API 触发 · 分片广播 · 故障转移 · DAG 编排 · 胶水代码编辑 · 异常自愈 |
| **ydsz-literule** | 6 种规则类型 · 自研 LiteExpr 引擎（AST + 沙箱） · 热加载 · 版本 Diff + 回滚 · Dry-Run 仿真 · A/B 测试 · 规则包/市场 · CEP 引擎 |
| **ydsz-agent** | 6 种 Agent 执行器 · LLM Provider 抽象（OpenAI 兼容） · 同步/流式对话（SSE） · RAG · DAG 编排 · Tool Calling / MCP 工具 · 安全护栏（PII + Prompt 注入检测） |

---

## 对标竞品

为明确 **Ydsz Cloud** 在开源快速开发平台生态中的定位与差异化优势，确立以下 5 个主流项目作为长期对标竞品，用于持续跟踪其架构演进、功能特性与社区活跃度：

| 竞品 | 一句话定位 | 架构形态 | 核心技术栈 | 开源协议 | 仓库地址 |
|------|-----------|----------|------------|----------|----------|
| **若依 RuoYi** | 轻量级权限管理系统，易读易懂、界面简洁美观 | 单体 / 前后端分离（另有独立微服务版 RuoYi-Cloud） | Spring Boot + MyBatis + Shiro（无重度依赖） | MIT | https://gitee.com/y_project/RuoYi |
| **Pig** | 微服务 RBAC 权限管理（企业级快速开发平台） | 微服务（亦支持 `boot` 单体 profile） | Spring Boot 4.0 + Spring Cloud 2025 & Alibaba + Spring Authorization Server（OAuth2） | Apache 2.0 | https://gitee.com/log4j/pig |
| **maku-boot** | 企业级低代码平台，符合信创需求 | 单体（组件化按需引入） | Spring Boot 4.0 + Spring Security 7.0 + MyBatis-Plus + Vue3 + Element-Plus | Apache 2.0 | https://gitee.com/makunet/maku-boot |
| **SpringBlade** | 商业级微服务架构，面向 SaaS 多租户 | 微服务 | Spring Boot 4.1 + Spring Cloud 2025 + Java 21 + Nacos + Sentinel（遵循阿里编码规范） | Apache 2.0 | https://gitee.com/smallc/SpringBlade |
| **JeecgBoot** | 企业级 AI 低代码平台（低代码 + 零代码 + BPM + AI） | 单体 / 微服务 | Spring Boot 4.1 + MyBatis-Plus + Shiro/JWT + Vue3 + Flowable + AI 应用平台 | Apache 2.0 | https://gitee.com/jeecg/JeecgBoot |

**Ydsz Cloud 的差异化优势**：

- **DDD 五层分层架构**：严格的 `api / domain / infra / server / web` 依赖方向单向收敛，竞品多为传统三层或 MVC 结构。
- **四大自研引擎**：规则引擎（对标 Drools + LiteFlow）、分布式调度（对标 XXL-Job + PowerJob）、BPMN 2.0 工作流、AI Agent 框架，全部自研、开箱即用。
- **前沿技术栈**：Java 21 虚拟线程 + Spring Boot 4 + Spring Cloud 2025.1.2 + Jakarta EE 10。
- **全渠道消息与多租户**：12 种通知渠道 DAG 编排、三种多租户隔离策略（SINGLE / MULTI / ISOLATE_DB）。

> 说明：对标竞品用于产品定位与功能演进参考，不代表技术依赖或代码引用。

---

## 快速开始

### 环境要求

| 依赖 | 最低版本 | 说明 |
|------|----------|------|
| JDK | 21 | 推荐 Eclipse Temurin / Amazon Corretto |
| Maven | 3.9+ | 聚合多模块构建 |
| PostgreSQL | 15+ | 共享主数据库 |
| Redis | 7.x | 缓存 / 分布式锁 / 会话 |
| Nacos | 2.4+ | 注册中心 & 配置中心 |
| RocketMQ | 5.x | 消息队列（消息模块必选） |
| MinIO | — | 对象存储（网盘模块必选） |

### 克隆与构建

```bash
# 克隆仓库
git clone http://192.168.31.88:6080/ydszopen/ydsz-cloud.git
cd ydsz-cloud

# 编译全量模块（含单元测试）
mvn clean verify

# 快速构建（跳过测试，加速开发迭代）
mvn clean package -DskipTests
```

### 数据库初始化

> 项目规范**禁止**使用 Flyway / Liquibase 等 schema-migration 框架。数据库 DDL 统一以 SQL 脚本形式管理，存放于各模块的 `deploy/sql/` 目录下。

```bash
# 1. 创建数据库
psql -U postgres -c "CREATE DATABASE ydsz_cloud;"

# 2. 按模块导入初始化脚本（示例）
psql -U postgres -d ydsz_cloud -f ydsz-userinfo/ydsz-userinfo-web/src/main/resources/sql/init.sql
psql -U postgres -d ydsz_cloud -f ydsz-system/ydsz-system-web/src/main/resources/sql/init.sql
# ...依此类推
```

### 本地启动

**启动顺序建议**：Nacos → 中间件 → Gateway → 业务服务（按端口号升序）

```bash
# 启动 Gateway（必须先启动）
cd ydsz-gateway
mvn spring-boot:run

# 启动各业务服务
cd ../ydsz-userinfo/ydsz-userinfo-web && mvn spring-boot:run
cd ../../ydsz-system/ydsz-system-web && mvn spring-boot:run
cd ../../ydsz-nextwiki/ydsz-nextwiki-web && mvn spring-boot:run
cd ../../ydsz-message/ydsz-message-web && mvn spring-boot:run
cd ../../ydsz-workflow/ydsz-workflow-web && mvn spring-boot:run
cd ../../ydsz-cronjob/ydsz-cronjob-web && mvn spring-boot:run
cd ../../ydsz-literule/ydsz-literule-web && mvn spring-boot:run
cd ../../ydsz-agent/ydsz-agent-web && mvn spring-boot:run
```

启动完成后，访问 API 文档：
- **Knife4j 聚合文档**：`http://localhost:9000/doc.html`
- **Nacos 控制台**：`http://localhost:8848/nacos`（默认账密 nacos/nacos）

---

## 开发指南

### 工程结构约定

每个可部署的业务模块遵循标准 DDD 五层结构：

```
ydsz-{module}/
├── pom.xml                          # 父 POM
├── ydsz-{module}-api/               # API 层：Feign Client + DTO
├── ydsz-{module}-domain/            # 领域层：Entity + VO + Repository 接口
├── ydsz-{module}-infra/             # 基础设施层：Repository 实现 + 外部集成
├── ydsz-{module}-server/            # 应用服务层：Service + 事务编排
└── ydsz-{module}-web/               # Web 层：Controller + 启动类 + 配置
```

**依赖方向**：`web → server → domain ← infra`，`api` 层独立对外。

### 代码规范

项目遵循以下编码标准：

- **阿里巴巴 Java 开发手册（泰山版）** —— Java 代码规范基线
- **Google Java Style Guide** —— 补充格式化规则

### 提交规范

提交信息采用 **Conventional Commits** 规范（中文描述）：

```bash
feat: 新增用户批量导入功能
fix: 修复部门树查询死循环问题
refactor: 重构 RBAC 权限校验逻辑
test: 补充消息模块单元测试
docs: 更新 API 接口文档
```

### 分支策略

| 分支 | 用途 |
|------|------|
| `main` | 生产就绪代码，仅通过 PR 合入 |
| `develop` | 开发主线，功能分支的合入目标 |
| `feature/*` | 特性开发分支 |
| `hotfix/*` | 紧急修复分支 |

---

## 质量与安全

### 构建与测试

| 项目 | 说明 |
|------|------|
| **单元测试** | JUnit 5 + Mockito + AssertJ，`mvn verify` 自动执行 |
| **集成测试** | Testcontainers 容器化（PG / Redis），继承 `AbstractIntegrationTest` 基类运行 |

### 安全措施

- **认证鉴权**：JWT + RBAC + 数据权限 + TOTP 2FA
- **传输安全**：HTTPS + CORS 严格策略 + CSRF Token
- **存储安全**：BCrypt 密码哈希 + 敏感配置 AES-256-GCM 加密
- **输入安全**：XSS 过滤 + SQL 注入防护（PreparedStatement） + 参数校验
- **数据安全**：PII 脱敏（手机号/身份证/邮箱） + 行/列级权限
- **运维安全**：IP 白名单/黑名单 + 登录失败锁定 + 验证码 + API 签名验证
- **供应链安全**：依赖版本统一收敛管理（Dependency Management）

---

## 文档

| 文档 | 位置 | 说明 |
|------|------|------|
| 项目 README | `./README.md` | 本文档 |
| MIT 开源协议 | `./LICENSE` | 开源许可协议 |
| Effective POM | `./effective-pom.xml` | 解析后的完整 POM |
| 模块 README | `ydsz-*/README.md` | 各模块详细说明文档 |
| 编码规范 | *(内部 Wiki)* | 团队开发规范 |
| API 文档 | Knife4j 聚合 | 启动后访问 `:9000/doc.html` |

---

## 贡献指南

我们欢迎任何形式的贡献！

### 贡献流程

1. **Fork** 本项目
2. 从 `develop` 分支创建你的特性分支：`git checkout -b feature/amazing-feature`
3. 编写代码，确保通过 `mvn verify` 构建与单元测试
4. 提交变更：`git commit -m 'feat: 新增某某功能'`
5. 推送分支：`git push origin feature/amazing-feature`
6. 提交 **Pull Request** 到 `develop` 分支

### 贡献要求

- 新增代码需通过单元测试验证
- 核心逻辑需补充单元测试
- 新增模块的 README.md 需同步更新
- 数据库变更需提供 SQL 初始化脚本
- PR 需至少一位 Maintainer 审核通过

---

## 开源协议

本项目基于 **[MIT License](./LICENSE)** 开源，允许自由使用、修改、分发，但需保留原始版权声明。

---

## 致谢

本项目在设计与实现过程中，参考并致敬以下优秀开源项目：

- [Spring Boot](https://spring.io/projects/spring-boot) &amp; [Spring Cloud](https://spring.io/projects/spring-cloud) —— Java 微服务生态基石
- [Spring Cloud Alibaba](https://github.com/alibaba/spring-cloud-alibaba) —— 微服务一站式解决方案
- [若依（RuoYi-Cloud）](https://gitee.com/y_project/RuoYi-Cloud) —— 优秀的国产微服务快速开发框架
- [Pig](https://gitee.com/log4j/pig) —— 基于 Spring Cloud 的微服务 RBAC 权限管理系统
- [XXL-Job](https://github.com/xuxueli/xxl-job) &amp; [PowerJob](https://github.com/PowerJob/PowerJob) —— 分布式任务调度标杆
- [Drools](https://www.drools.org/) &amp; [LiteFlow](https://liteflow.cc/) —— 规则引擎领域先驱
- [Flowable](https://www.flowable.com/) &amp; [Camunda](https://camunda.com/) —— BPMN 工作流引擎参考

---

<p align="center">
  <sub>Made with ❤️ by YdszSoft Team</sub>
</p>
