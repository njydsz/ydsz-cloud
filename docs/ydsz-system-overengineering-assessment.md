# ydsz-system 模块过度设计评估报告

> **评估日期**：2026-08-20  
> **评估依据**：《云顶编码规范》第 35 章（业务模块过度设计防范规范 v2.23）  
> **对标基准**：Spring 官方最佳实践、阿里巴巴 Java 开发手册、领域驱动设计（DDD）社区共识  
> **评估范围**：ydsz-api / ydsz-domain / ydsz-infra / ydsz-server / ydsz-web 五个子模块  
> **修复状态**：✅ 全部修复完成（2026-08-20）

---

## 一、评估结论摘要

| 维度 | 违规数 | 最高严重等级 | 综合评价 | 修复状态 |
|------|--------|-------------|----------|----------|
| 35.1 Controller 响应包装 | 0 | — | ✅ 合规 | — |
| 35.2 版本快照异步化 | 1 | 建议 P3 | ⚠️ 轻微偏差 | ✅ 已修复 |
| 35.3 Repository 接口精简 | 0 | — | ✅ 合规 | — |
| 35.4 缓存失效规范 | 1 | **强制 P2** | ❌ 需整改 | ✅ 已修复 |
| 35.5 策略模式替代回调 | 0 | — | ✅ 合规 | — |
| 35.6 指标精简与 AOP 化 | 1 | 建议 P3 | ⚠️ 轻微偏差 | ✅ 已注释说明 |
| 35.7 接口/类合并 | 0 | — | ✅ 合规 | — |
| **合计** | **3** | **P2（强制）** | **整体良好** | **✅ 全部完成** |

---

## 二、修复记录

### 2.1 P2-1：缓存失效三重叠加 ✅ 已修复

**问题描述**：`CrossModuleEventListener` 在单实例写操作中同时执行本地缓存失效（`evict()`）和跨实例 Pub/Sub 通知，与 Service 层的 `@CacheEvict` 构成三重叠加失效。

**修复方案**：将 `CrossModuleEventListener` 职责精简为"跨实例缓存失效消息转发器"，移除本地 `evict()` 调用。

**修改文件**：`ydsz-system-server/src/main/java/com/njydsz/system/server/listener/CrossModuleEventListener.java`

**修改内容**：
- 移除 `CacheManager` 依赖注入
- 移除 `evict()` 私有方法
- 三个事件处理方法（`onConfigChanged`、`onDictTypeChanged`、`onVariableChanged`）仅保留 `invalidationPublisher.publishEviction()` 跨实例通知
- 更新类注释说明新的架构定位

**修复后缓存失效链路**：
1. 写操作 Service 方法通过 `@CacheEvict` 在本地实例精准失效（第一层）
2. 监听器接收领域事件后向 Redis Pub/Sub 发布消息
3. 其他实例的 `CacheInvalidationSubscriber` 接收消息后清除各自本地缓存

---

### 2.2 P3-1：TransactionSynchronization 匿名回调具名化 ✅ 已修复

**问题描述**：`ConfigBatchServiceImpl` 第 142-151 行使用匿名 `TransactionSynchronization` 内部类散布缓存失效和事件发布逻辑，不符合规范 35.5.1。

**修复方案**：提取为具名内部类 `ConfigBatchTransactionSynchronization`，封装事务提交后的缓存失效与事件发布逻辑。

**修改文件**：`ydsz-system-server/src/main/java/com/njydsz/system/server/service/impl/ConfigBatchServiceImpl.java`

**修改内容**：
- 移除匿名内部类（原第 142-151 行）
- 新增具名内部类 `ConfigBatchTransactionSynchronization`，实现 `TransactionSynchronization` 接口
- 通过构造函数传递 `configGroups` 和 `dtos` 参数
- `afterCommit()` 方法封装缓存失效和事件发布逻辑

---

### 2.3 P3-2：SystemMetrics 继承设计说明 ✅ 已注释说明

**问题描述**：`SystemMetrics` 仅使用 3 个 Counter 指标，却继承约 365 行的 `SentryMetricsAdapter` 重型基类。

**修复方案**：短期保留继承设计（符合规范 35.6.2 强制要求），在类注释中说明合规依据和长期优化方向。

**修改文件**：`ydsz-system-server/src/main/java/com/njydsz/system/server/metrics/SystemMetrics.java`

**修改内容**：
- 更新类注释，添加"架构合规说明（v2.23 过度设计评估）"章节
- 说明当前继承 `SentryMetricsAdapter` 符合规范 35.6.2 强制要求
- 指出长期优化方向：待规范委员会评估通过后，可改为组合方式持有 `SentryService`

---

## 三、逐项分析（修复前状态）

### 3.1 Controller 响应包装统一性 ✅ 合规

全部 13 个 Controller 统一使用 `YdszResponse<T>` 返回，分页场景正确使用 `YdszResponse<PageResponse<List<XXXVO>>>>` 嵌套模式。

### 3.2 版本快照异步化 ⚠️ 已修复

主路径已正确使用 `@TransactionalEventListener(AFTER_COMMIT)`，但批量操作中使用了匿名 `TransactionSynchronization`，已提取为具名内部类。

### 3.3 Repository 接口精简 ✅ 合规

全部 8 个 Repository 接口方法均有对应调用方和实现，无僵尸方法。

### 3.4 缓存失效规范 ❌ 已修复

原三重叠加问题已修复，`CrossModuleEventListener` 不再执行本地缓存失效。

### 3.5 策略模式替代回调 ✅ 合规

`RollbackStrategy` 接口 + 3 个具名实现类，无 lambda 散布。

### 3.6 指标精简与 AOP 化 ⚠️ 已说明

指标精简到位（仅 3 个 Counter），继承设计已通过注释说明合规性。

### 3.7 接口/类合并 ✅ 合规

Service 接口均有合理的多实现/多调用方，无需合并。

---

## 四、行业竞品对标

### 4.1 缓存架构对标

| 产品/框架 | 缓存一致性策略 | ydsz-system（修复后）对比 |
|-----------|--------------|-------------------------|
| Spring Cache 官方 | `@CacheEvict` + TTL（单实例）/ Redis Pub/Sub（多实例可选） | ✅ 架构一致 |
| Alibaba Tair 推荐 | CacheEvict + 延迟双删 或 订阅 Binlog 补偿 | ✅ Pub/Sub 机制满足需求 |
| Netflix Dynomite | 主缓存 + 多区域异步复制（最终一致） | ✅ TTL 设计与之类似 |
| AWS ElastiCache | 写穿策略 + TTL 兜底 | ✅ 架构理念一致 |

### 4.2 DDD 分层对标

ydsz-system 的 DDD 分层架构（api/domain/infra/server/web）设计完善，符合 DDD 社区最佳实践。

---

## 五、修改文件清单

| 文件 | 修改类型 | 说明 |
|------|---------|------|
| `CrossModuleEventListener.java` | 重构 | 移除本地缓存失效，职责精简为跨实例消息转发器 |
| `ConfigBatchServiceImpl.java` | 重构 | 匿名内部类提取为具名内部类 `ConfigBatchTransactionSynchronization` |
| `SystemMetrics.java` | 注释更新 | 添加架构合规说明和长期优化方向 |

---

## 六、验证清单

- [x] 缓存失效链路：单实例仅 `@CacheEvict` 生效，多实例通过 Pub/Sub 同步
- [x] 监听器异常处理：try-catch 吞掉仅日志告警，符合规范 35.2.2
- [x] 具名内部类：`ConfigBatchTransactionSynchronization` 职责单一、可测试
- [x] 注释规范：所有修改文件均添加规范引用注释
- [x] 无回归：修改仅涉及缓存失效和回调封装，不影响业务逻辑

---

*本报告由 CatPaw AI 评估生成，修复代码符合《云顶编码规范》第 35 章要求。*
