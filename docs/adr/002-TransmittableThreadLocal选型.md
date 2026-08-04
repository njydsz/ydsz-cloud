# ADR-002: TransmittableThreadLocal 选型

**状态**: 已采纳  
**日期**: 2024-Q3  
**决策者**: ydsz-team

## 背景

`RequestContext` 需要在请求处理链中传递 `userId`、`tenantId`、`traceId` 等上下文信息。传统 `ThreadLocal` 在以下场景会丢失上下文：

1. **异步任务**：`@Async` 或手动创建的线程池中无法获取父线程上下文
2. **CompletableFuture**：链式异步操作中的 ThreadLocal 传递
3. **响应式编程**：WebFlux / Reactor 场景

## 决策

采用阿里巴巴开源的 **TransmittableThreadLocal (TTL)** 作为请求上下文的底层实现。

**核心能力**：
- 线程池场景下自动捕获和传播 ThreadLocal 值
- 配合 `TtlExecutors` 包装线程池即可使用，无需业务代码修改
- 支持 `TtlRunnable` / `TtlCallable` 手动包装

**使用方式**（自动模式）：
```java
// 线程池包装（全局一次）
ExecutorService executor = TtlExecutors.getTtlExecutorService(originalExecutor);

// 业务代码无需感知 TTL
RequestContext.setUserId("user123");
executor.submit(() -> {
    String userId = RequestContext.getUserId(); // 自动传播
});
```

## 替代方案

| 方案 | 优点 | 缺点 | 结论 |
|------|------|------|------|
| JDK InheritableThreadLocal | JDK 内置 | 线程池复用后值不再更新，传递丢失 | 不采用 |
| 手动传递（方法参数） | 无依赖 | 侵入性强，所有方法签名需增加参数 | 不采用 |
| TTL | 自动传播，零侵入 | 需引入 alibaba 依赖 | **采用** |

## 后果

- 正面：异步场景下上下文自动传播，业务代码无需感知
- 负面：增加 alibaba TTL 依赖；需要团队了解 TTL 的基本使用方式
- 风险：Java Agent 方式虽性能最优但运维复杂，当前使用包装器方式足够
