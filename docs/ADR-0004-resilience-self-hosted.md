# ADR-0004：弹性容错回归平台自研引擎（Resilience4j 全仓移除）

| 项目 | 内容 |
| ---- | ---- |
| 编号 | ADR-0004 |
| 状态 | 已采纳（Accepted） |
| 日期 | 2026-09-01 |
| 决策人 | Marvin |
| 影响范围 | ydsz-common-safe（新增引擎）、sentry、feign、search、message、literule、gateway、根 pom |

## 1. 背景

### 1.1 约束前提

云顶平台为公司内网项目，依据《云顶编码规范》依赖治理条款：**禁止引入竞品第三方库，基础设施能力一律自研**。Resilience4j 属于第三方弹性容错库，同样落入该约束范围。

### 1.2 历史演进

- 平台早期（v1.x）熔断器为自研实现（AtomicReference + CAS 状态机）。
- 2026-08 竞品对标后曾决策"熔断器替换为 Resilience4j"（见 ydsz-common-safe/README 变更记录），形成 10 个 Java 文件 + 8 个 pom 的依赖面。
- 2026-09-01 依赖治理复核中，该决策与内网自研约束冲突，本 ADR 予以纠正：**回归自研，且以"深度完善后的自研引擎"替代，而非简单回退旧实现**。

### 1.3 移除前依赖面（盘点结论）

| 模块 | 使用方式 |
| ---- | ---- |
| ydsz-common-safe | ratelimit/circuitbreaker 封装 + AbstractCircuitBreaker |
| ydsz-common-sentry | CircuitBreaker 包装 + MetricsAutoConfiguration |
| ydsz-common-feign | Resilience4jCircuitBreakerAdapter + FeignConfiguration |
| ydsz-common-search | UnifiedSearchService 手动记录 |
| ydsz-message | ChannelRouter 按通道熔断 |
| ydsz-literule | RuleCircuitBreaker |
| ydsz-gateway | CircuitBreakerGlobalFilter（含 reactor Operator） |
| ydsz-common-jdbc | **纯死依赖**（仅 pom 声明，无任何 Java 使用点） |

实际 API 面收敛：CircuitBreakerRegistry/Config、tryAcquirePermission、onSuccess/onError(duration,unit[,throwable])、executeSupplier、getState（含 FORCED_OPEN）、getMetrics（失败率/慢调用率/平均耗时）、onStateTransition 事件、transitionToForcedOpenState、reset、CircuitBreakerOperator、CallNotPermittedException。

## 2. 决策

新建自研引擎 `com.njydsz.common.safe.resilience`（ydsz-common-safe 模块），全仓移除 `io.github.resilience4j` 依赖，所有调用方迁移至自研 API。

### 2.1 引擎能力矩阵（R4j API → 自研对应）

| Resilience4j API | 自研对应 | 说明 |
| ---- | ---- | ---- |
| CircuitBreakerConfig.Builder | CircuitBreakerConfig.Builder | 百分比语义阈值、COUNT/TIME 双滑动窗口、minimumNumberOfCalls、半开许可数、自动半开开关、recordException 谓词 |
| CircuitBreaker | CircuitBreaker | CLOSED/OPEN/HALF_OPEN/FORCED_OPEN 四态，AtomicReference CAS |
| tryAcquirePermission / acquirePermission | 同名 | OPEN 到期**读时惰性转换**进入 HALF_OPEN |
| onSuccess / onError(duration,unit[,throwable]) | 同名 | 半开计数达标恢复 / 任一失败回退 |
| executeSupplier / execute(Supplier,Supplier) | 同名 | 含降级 Supplier |
| CircuitBreakerRegistry | CircuitBreakerRegistry | computeIfAbsent 幂等创建 |
| getMetrics | getMetrics（Metrics 内部类） | 失败率/慢调用率（无数据返回 -1）/平均耗时 |
| onStateTransition 事件 | CircuitBreakerEvents | CopyOnWriteArrayList 链式订阅 |
| FORCED_OPEN / transitionToForcedOpenState / reset | 同名 | 运维强制隔离 |
| CircuitBreakerOperator（reactor） | 无对应——响应式侧用 `Mono.defer + tryAcquirePermission + doOnSuccess/doOnError` 三段式手动记录（见 gateway CircuitBreakerGlobalFilter） | 引擎保持零响应式依赖 |
| CallNotPermittedException | 同名（RuntimeException） | 供 onErrorResume 精确分流 |

### 2.2 关键设计取舍

1. **惰性半开替代后台调度器**：R4j 依赖后台线程在 OPEN 到期时主动迁移状态；自研引擎在 `tryAcquirePermission`/`canExecute` 读路径上惰性 CAS 转换（OPEN → HALF_OPEN 并重置许可）。收益：零线程开销；代价：无流量时状态标注滞后（可观测性差异，无功能差异）。**若关闭 automaticTransitionFromOpenToHalfOpenEnabled，调用方必须依赖读路径转换，否则熔断器将永久停留 OPEN——sentry wrapper 的 canExecute 已实现读时转换副作用，接入方无需关心。**
2. **滑动窗口 synchronized 临界区**：按秒分桶（TIME_BASED）/环形缓冲（COUNT_BASED），临界区内仅做整数累加，吞吐足以覆盖熔断统计频次。未采用 LongAdder/无锁结构：评估状态需一致性快照，分桶聚合天然要求临界点，复杂度不划算（对比 ADR-0004 评审记录）。
3. **单位语义统一为百分比**：引擎 API 全部采用 0-100 百分比语义（对齐 feign/message 历史配置），调用方若持有 0-1 比例语义须显式换算。

### 2.3 顺带修复的历史缺陷

1. **单位错位暗病（P0）**：sentry `SentryProperties.failureRateThreshold` 与 literule `errorRateThreshold` 均为 0-1 比例语义，但历史代码直传 R4j 百分比 API → **实际阈值被缩小 100 倍**（如 0.3 意图 30%，实际生效 0.3%）。迁移时统一修复：换算 ×100。
2. **feign 指标重复计数**：旧适配器 getSlowCalls 将三值相加导致慢调用重复计数，改为单值 getNumberOfSlowCalls。
3. **feign 平均耗时硬编码 0**：改为真实滑动窗口均值。
4. **jdbc 死依赖**：ydsz-common-jdbc 声明了 resilience4j-circuitbreaker/micrometer 但无任何 Java 使用点，直接删除。

## 3. 迁移清单

| 文件 | 变更 |
| ---- | ---- |
| ydsz-common-safe/resilience/*（新增） | 引擎 6 文件 + 17 单测全绿 |
| ydsz-common-safe ratelimit/CircuitBreaker、AbstractCircuitBreaker | per-resource 委托引擎，保留对外 API（0-1 → ×100 换算闭环） |
| ydsz-common-sentry CircuitBreaker、MetricsAutoConfiguration | 委托引擎；修复单位错位；pom 删 R4j 加 common-safe |
| ydsz-common-feign | 删 Resilience4jCircuitBreakerAdapter/FeignConfiguration，新建 SafeCircuitBreakerAdapter/CircuitBreakerFeignConfiguration；修复指标缺陷 |
| ydsz-common-search UnifiedSearchService | import 换引擎；onError 判空 throwable |
| ydsz-message ChannelRouter | 引擎 Config/Registry |
| ydsz-literule RuleCircuitBreaker | 换引擎；修复单位错位（两处） |
| ydsz-gateway CircuitBreakerGlobalFilter | Mono.defer 三段式替代 CircuitBreakerOperator；状态映射加 FORCED_OPEN |
| 各 pom + 根 pom dependencyManagement | R4j 条目全部移除 |

## 4. 验证

- 自研引擎 17/17 单测通过（`CircuitBreakerTest`，含并发、TIME_BASED、FORCED_OPEN、惰性转换双模式）。
- 全仓 `io.github.resilience4j` Java 源码 import 归零（grep 复核）。
- 全仓 pom 中 R4j 依赖归零（仅保留说明性注释）。
- 全仓编译通过（`mvn compile`）。

## 5. 后续约束

1. **业务模块禁止绕过引擎自建熔断**（《云顶编码规范》既有强制条款继续适用，实现主体由"R4j 直调"变更为"必须复用 sentry 封装或引擎 API"）。
2. 新增弹性能力（Retry/Bulkhead/RateLimiter 引擎化）如需扩展，须先在本 ADR 追加决策记录。
3. 引擎属能力储备资产，按规范 §33.7 承担测试义务：任何行为变更须同步补测试。
