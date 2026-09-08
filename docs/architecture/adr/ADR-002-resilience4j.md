# ADR-002：Resilience4j 替代自研熔断

**状态：** 接受
**日期：** 2025-01-20
**决策者：** ydsz-team

## 背景与问题陈述

ydsz-cloud 系统内部存在大量 HTTP/RPC 调用链路（gateway → app → domain → infra → 外部服务），以及定时任务中的外部依赖（短信通道、AI 服务、第三方接口）。历史方案为各模块各自实现简单的 try-catch 降级，缺乏统一的熔断、限流、舱壁保护能力。需引入成熟的弹性框架保障系统可用性。

## 决策驱动因素

* 函数式 API 轻量，不依赖 Spring Cloud 重量级注解
* 与 Reactor/WebFlux 兼容性好，响应式链路透传无侵入
* 社区活跃度高，Spring Boot 官方推荐替代 Hystrix
* 可组合模式（熔断 + 限流 + 舱壁 + 重试），降低多框架并存复杂度
* 对 GraalVM 原生镜像友好（相比 Sentinel Agent 方案）
* 编码规范 YDIZ-RES-001 要求弹性保护与业务代码分离

## 考虑的选项

* **自研熔断器：** 完全可控但开发维护成本高，边界条件覆盖不全
* **阿里 Sentinel：** 阿里生态完整但 Agent 模式侵入性强，API 模式与 Resilience4j 功能重叠但社区中文支持不如预期
* **Spring Cloud Circuit Breaker Adapter：** 抽象层统一但仅为适配器，实际依赖 Hystrix/Resilience4j/Reactive Resilience4j，增加间接依赖
* **Resilience4j：** 原生函数式、可组合、Reactor 兼容、轻量

## 决策结果

选择 **Resilience4j**，因为其函数式 API 轻量且可组合，与 Spring Boot 3.x 和 Reactor 生态天然契合，同时避免引入重量级 Agent 或适配器层。

### 正面后果

* 熔断器 + RateLimiter + Bulkhead + TimeLimiter 一体化，无需引入多个框架
* 函数式 API 使弹性保护与业务代码解耦，符合 YDIZ-RES-001 规范
* 通过 Actuator + Micrometer 原生暴露熔断指标，与 Sentry 监控打通
* 响应式场景下 Reactor 操作符直接组合，无 ThreadLocal 泄漏风险
* 社区持续维护，Spring Boot 官方示例支持

### 负面后果

* Resilience4j 不提供服务级 Dashboard，需自行集成 Prometheus + Grafana 面板
* 与传统 Hystrix 注解 `@HystrixCommand` 完全不兼容，需全面迁移已有 try-catch 降级代码
* 细粒度限流（用户/租户维度）需要自行封装，框架仅提供全局级别

## 各选项详细对比

### 自研熔断器

* **优点：** 完全可控，可针对业务场景定制
* **缺点：** 开发周期长；半开/全开/断开状态机边界条件多；缺乏社区验证；新项目重造轮子引入风险
* **评价：** 团队无自研中间件传统，维护和交接成本不可控

### 阿里 Sentinel

* **优点：** 控制台成熟、阿里生态集成度高、热点参数限流等特色功能
* **缺点：** Agent 模式与字节码增强耦合，升级成本高；与 Seata AT 的 datasource proxy 存在潜在冲突；API 模式并无明显优势
* **评价：** 阿里技术栈场景下优选，但 ydsz-cloud 非纯阿里生态

### Spring Cloud Circuit Breaker Adapter

* **优点：** 抽象层屏蔽底层实现，理论可切换
* **缺点：** 增加一层间接依赖；实际生产中几乎不会切换底层框架；调试链路拉长
* **评价：** 适配器模式在此场景下收益有限

### Resilience4j

* **优点：** 轻量、函数式、可组合、Reactor 原生支持、指标开箱即用
* **缺点：** Dashboards 生态弱于 Sentinel/Hystrix
* **评价：** 综合评分最高，最契合 ydsz-cloud 技术栈

## 合规性对照

- [云顶编码规范 YDIZ-RES-001] — 弹性保护与业务代码逻辑分离，通过函数式包装器实现
- [云顶编码规范 YDIZ-RES-002] — 熔断器配置集中管理，不允许散落在业务代码中硬编码阈值
- [云顶编码规范 YDIZ-OBS-001] — 熔断指标通过 Actuator 暴露，纳入 Sentry 监控体系

## 参考

* [ADR-006](./ADR-006-observability.md) — 日志与可观测性标准化（Resilience4j 指标纳入监控）
* [Resilience4j 官方文档](https://resilience4j.readme.io/)
* [Spring Boot 3.x 弹性框架选型指南]()
