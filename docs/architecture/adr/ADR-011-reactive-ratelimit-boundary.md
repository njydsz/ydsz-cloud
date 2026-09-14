# ADR-011：响应式限流定界（gateway RateLimitProperties vs common-safe ratelimit）

**状态：** 已接受
**日期：** 2026-09-14
**决策者：** ydsz-team

## 背景与问题陈述

gateway 自建 `RateLimitProperties`（响应式令牌桶，WebFlux `ReactiveStringRedisTemplate` 实现）与 common-safe 的 ratelimit 能力（Servlet AOP 限流，阻塞式 Redis）同名且能力表面相似，守护脚本 E1 命中，评审无从判断。

## 决策驱动因素

* **技术栈互斥**：gateway 为 WebFlux 响应式栈，common-safe 限流为 Servlet AOP（阻塞 RedisTemplate）——运行时无法混用
* common-safe 限流经 `ydsz-common-safe` 自动装配注入业务服务方法级；网关限流发生在路由前置过滤器层，语义是"入口全局限流"而非"方法级熔断限流"
* 两类限流的键粒度（per-IP / per-User）、令牌桶参数存储结构均不同

## 考虑的选项

* **收敛至 common-safe：** 需 common-safe 增加响应式 API，且网关"路由前置全局限流"与"方法级 AOP 限流"语义强行合一
* **维持双轨（本 ADR）：** 场景不同，各自保留，重命名消歧

## 决策结果

**双轨保留，边界成文 + 重命名消歧：**

| 场景 | 必须使用 |
|---|---|
| Servlet 业务服务方法级限流（AOP 注解） | common-safe ratelimit |
| 网关路由前置全局限流（per-IP / per-User 令牌桶） | gateway 自有 `GatewayRateLimitProperties` |

1. gateway `RateLimitProperties` **重命名为 `GatewayRateLimitProperties`**（消除与 common-safe 的 E1 同名命中）
2. gateway `GatewayHealthIndicator` 保留独立实现（WebFlux 栈无法继承 common-web Servlet 栈基类 `AbstractModuleHealthIndicator`，守护脚本豁免）
3. 后续网关新增能力优先评估"common 是否有响应式等价物"，无则按本 ADR 逻辑定界并记录

### 正面后果

* E1 同名命中消除，评审有据
* 响应式/Servlet 栈边界成文，避免未来误把阻塞封装引入网关

### 负面后果

* 限流配置语义存在两套（接受，技术栈互斥是根因）

## 关联

* `ydsz-common-safe` ratelimit 包
* 守护规则：`scripts/check-common-reuse.py` KNOWN_EXCEPTIONS
* ADR-009（公共能力收敛总纲）
