# ADR-006：日志与可观测性标准化

**状态：** 部分接受
**日期：** 2025-02-20
**决策者：** ydsz-team

## 背景与问题陈述

ydsz-cloud 多模块架构下，日志格式不统一、Trace 链路断层、Metrics 分散是过去三个月线上故障排查的主要痛点。Metrics 方面已有 `ydsz-common-sentry` 模块支撑，Logging 方面已实现统一 trace appender，但 OpenTelemetry Tracing 仍在引入中。需明确 Metrics、Logging、Tracing 三个维度的标准化方案，并解决各技术选型对 GraalVM 原生镜像的兼容性影响。

## 决策驱动因素

* Metrics 需要与 Sentry 监控面板直接打通，减少接入成本
* Tracing 需跨服务全链路覆盖，保证 gateway → app → infra 端到端追踪
* Logging 统一 JSON 格式后，便于 ELK / Loki 等日志平台结构化摄入
* OpenTelemetry 是 CNCF 标准，避免单 vendor lock-in
* GraalVM 原生镜像对 Agent 类方案的兼容性风险需提前评估
* 编码规范 YDIZ-OBS-001~003 已分别对 Metrics/Logs/Tracing 给出规范

## 考虑的选项

* **全量 SkyWalking Agent：** 字节码增强，但侵入性强、版本耦合、原生镜像兼容性差
* **OpenTelemetry SDK（当前方案）：** CNCF 标准，手动埋点 + 自动 Instrumentation 结合
* **Spring Boot Actuator + Micrometer 全量：** Metrics 覆盖强，但 Tracing 需额外集成
* **混合方案（Metrics/Sentry + Logging/JSON + Tracing/OTel）：** 各层采用最成熟工具

## 决策结果

选择 **混合方案：Metrics（Micrometer + Prometheus）+ Tracing（OpenTelemetry）+ Logging（统一 JSON）**，因为各层采用最成熟工具组合，与 CNCF 生态对齐，同时规避全量 Agent 方案和单框架锁定风险。当前 Tracing 模块（OpenTelemetry）仍在引入中，故状态标记为部分接受。

### 正面后果

* Metrics 通过 Micrometer 统一暴露，与 Sentry 面板和 Prometheus 天然打通
* OpenTelemetry 符合 CNCF 标准，无单 vendor lock-in 风险
* Logging 统一 JSON 后，ELK/Loki 等平台可自动化结构化解析
* 各层工具可独立演进，降低单一依赖升级风险
* 编码规范 YDIZ-OBS 系列已覆盖三种信号的埋点标准

### 负面后果

* 多工具并存增加运维和版本兼容管理复杂度
* OpenTelemetry SDK 引入对 GraalVM native-image 的兼容性需后续验证（类加载/反射限制）
* 手动代码埋点量较大，团队需培训 OpenTelemetry Instrumentation 使用
* Metrics/Logging/Tracing 三个系统的关联分析需要统一 traceId 贯穿
* SkyWalking Agent 方式虽然侵入本方案更轻量，但已明确不采纳

## 各选项详细对比

### 全量 SkyWalking Agent

* **优点：** 零代码埋点、APM 全功能覆盖（Metric/Trace/Log 统一）；Java Agent 模式接入简单
* **缺点：** 字节码增强侵入性强，与 Seata AT / MyBatis-Plus 的增强代理存在潜在冲突；版本耦合（SkyWalking 升级需同步所有 Agent）；对 GraalVM 原生镜像完全不支持；网络拓扑存储依赖 ElasticSearch 或 BanyanDB，增加基础设施压力
* **评价：** 虽然接入简单，但侵入性与版本耦合风险不符合中台架构长期可维护目标

### OpenTelemetry SDK

* **优点：** CNCF 标准；手动埋点可控性强；自动 Instrumentation 支持主流框架；导出器生态丰富（Jaeger、Zipkin、Prometheus 等）
* **缺点：** 手动埋点量大；GraalVM 兼容性需 native-image 配置文件（ reflect-config、resource-config 等）；SDK 仍在快速演进中，minor 版本可能存在 breaking change
* **评价：** 标准化程度最高，中长期收益最大

### Spring Boot Actuator + Micrometer 全量

* **优点：** Spring Boot 原生集成，Metrics 覆盖开箱即用；Micrometer 抽象层支持多后端
* **缺点：** Actuator 不自带 Tracing 能力，需额外引入 OpenTelemetry/SkyWalking；Logging 标准化需要自行封装
* **评价：** Metrics 层优选，但无法单独满足 Tracing 和 Logging 需求

### 混合方案（当前决策）

* **优点：** 各层采用最成熟工具；灵活独立演进；编码规范已覆盖三层标准
* **缺点：** 多工具并存增加运维复杂度；跨系统 traceId 贯穿需要协议对齐
* **评价：** 最契合 ydsz-cloud 现状，可控且有充分社区支持

## 合规性对照

- [云顶编码规范 YDIZ-OBS-001] — Metrics 通过 Micrometer 暴露，纳入 Sentry 监控体系
- [云顶编码规范 YDIZ-OBS-002] — Logging 统一 JSON 格式，traceId 必须贯穿全链路
- [云顶编码规范 YDIZ-OBS-003] — Tracing 使用 OpenTelemetry SDK，禁止全量 Agent 模式
- [云顶编码规范 YDIZ-OBS-004] — 三种可观测性信号的 traceId 贯通要求

## 参考

* [ADR-002](./ADR-002-resilience4j.md) — Resilience4j 熔断指标通过 Micrometer 暴露
* [OpenTelemetry 官方文档](https://opentelemetry.io/)
* `ydsz-common-sentry/` — Metrics 模块源码
