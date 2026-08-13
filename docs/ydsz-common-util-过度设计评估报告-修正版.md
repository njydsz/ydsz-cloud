# ydsz-common-util 模块过度设计评估与优化建议（修正版）

> **评估范围**：`ydsz-common-util` 39 个 Java 文件，约 37 个公共类
> **评估时间**：2026-08-13（修正）
> **对标基准**：Google Guava、Apache Commons、Hutool、美团 Leaf/UidGenerator、阿里巴巴 Java 开发手册（泰山版）
> **评估维度**：职责边界、API 一致性、依赖合理性、维护成本、与已有能力的重叠

---

## 一、评估原则澄清

### 1.1 工具类模块的定位

`ydsz-common-util` 是公司级内部工具类库，其核心价值在于：

- **能力储备**：提供经过验证的标准化实现，供业务方按需选用
- **内部实现优先**：不依赖外部服务，保证可用性和可控性
- **长期完善**：工具类需要持续打磨，当前未充分引用不代表没有价值

### 1.2 过度设计的判断标准修正

| 之前判断标准 | 修正后判断标准 |
|-------------|---------------|
| "当前无调用方 = 过度设计" | "能力是否可被已有实现（JDK/已依赖库）完全替代" |
| "层次太多 = 过度设计" | "抽象层次是否带来清晰的扩展点或隔离性" |
| "功能太多 = 过度设计" | "职责边界是否清晰，是否存在重复实现" |

**核心原则**：工具类模块中「未被当前业务引用」不等于「过度设计」。真正的问题是 **与已有能力的重复实现** 和 **API 不一致性**。

---

## 二、真正需要关注的优化点

### 2.1 【P0】与已依赖第三方库的功能重叠

#### P0-1. StringUtils 与 commons-lang3 的双系统

**现状**：
- 模块自研 StringUtils（约 200 行公共方法）
- 项目已依赖 `commons-lang3`（pom.xml 未直接引入但可选）
- 两套 StringUtils 并存

**风险**：
- 团队成员面临"该用哪个"的选择困惑
- 相同功能两套实现，修复 bug 需同步两处

**建议**：
- 方案 A：StringUtils 保留公司特有扩展（如中文相关、业务定制方法），标准方法标记 `@Deprecated` 引导到 commons-lang3
- 方案 B：如公司规范明确"内部工具优先自研"，则确保 StringUtils 能力完全覆盖 commons-lang3 常用方法，不留功能缺口

#### P0-2. MapUtils.toBean 与 ydsz-common-json 的重叠

**现状**：
- `MapUtils.toBean()`：~650 行，支持 setter 反射、类型转换、泛型、Record
- `ydsz-common-json`（Fastjson2）：`JSON.toJavaObject()` 已覆盖 Map→Bean 场景

**评估**：
- 工具类提供 `toBean` 是合理的（Hutool 也有 `BeanUtil.toBean`）
- 但当前实现过于复杂，维护成本高

**建议**：
- 保留 `toBean` 作为工具类能力（合理存在）
- 简化实现：移除 Record 支持、泛型 List 转换等低频场景（或标记为 `@Deprecated`）
- 明确文档说明：`toBean` 适用于简单场景，复杂场景使用 JSON 框架

---

### 2.2 【P1】API 一致性问题

#### P1-1. PwdUtils 的双模密码强度系统

**现状**：
- 旧 API：`checkPasswordStrength()` → `PasswordStrength`（三档枚举）
- 新 API：`checkPasswordStrengthLevel()` → `PasswordStrengthLevel`（五档枚举）

**问题**：
- 两套枚举语义相似但不同，调用方困惑
- 两个方法共存在一个类中，违反"一种能力一个入口"原则

**建议**：
- `checkPasswordStrength()` 标记 `@Deprecated(since = "3.0", forRemoval = true)`
- 保留新五档枚举作为唯一出口
- 如旧三档枚举有调用方，做内部映射（五档 → 三档）

#### P1-2. AesUtils 与 CryptoUtils 的迁移路径

**现状**：
- `AesUtils` 标记 `@Deprecated`，内部委派给 `CryptoProviderRegistry`
- `CryptoUtils` 作为新的统一入口

**问题**：
- 两套 API 并存，调用方需要选择
- `@Deprecated` 未设置 `forRemoval` 标记和替代说明

**建议**：
- 为 `AesUtils` 每个方法添加 `@Deprecated(since = "3.0", forRemoval = true)` 和 `@see CryptoUtils` 指引
- 在 README 中明确迁移时间表

#### P1-3. ExecutorUtils 方法过多

**现状**：`ExecutorUtils` 提供 25+ 方法，覆盖 Fixed/Cached/Single/Virtual/Scheduled/Priority/ThreadFactory 等多种场景

**问题**：
- 方法过多导致选择困难
- 部分方法实际无人使用（newCachedThreadPool、newSingleThreadExecutor 等）

**评估**：
- 工具类提供丰富的线程池创建方法是合理的
- 但需要更好的组织和文档

**建议**：
- 按场景分组（在 JavaDoc 中）：
  - **基础线程池**：newFixedThreadPool / newCpuBoundThreadPool
  - **特殊线程池**：newVirtualThreadExecutor / newScheduledThreadPool
  - **便捷包装**：TTL 系列
  - **辅助工具**：shutdownGracefully / submitWithTimeout
- 添加"快速选择指南"到类 JavaDoc

---

### 2.3 【P2】职责边界需要澄清

#### P2-1. ydsz-common-util 与 ydsz-common-thread 的边界

**现状**：两模块都涉及线程池

**建议**：
- 在各自模块的 README 中明确分工：
  - `ydsz-common-util`：纯 JDK 工厂方法，无监控能力
  - `ydsz-common-thread`：企业级线程池组件（可观测、可配置、有生命周期管理）
- `MeteredThreadPoolExecutor` 归属需要明确（当前在 util，但能力偏 thread）

#### P2-2. WorkerIdAllocator 策略链是否属于 util

**现状**：4 个 WorkerId 分配器 + 策略链 + 异常类

**评估**：
- Snowflake 算法是工具类的核心能力之一
- WorkerId 分配策略是 Snowflake 的合理组成部分
- 但策略链模式（prepend/append/责任链）增加了复杂度

**建议**：
- 保留 `WorkerIdAllocator` 接口和常见实现
- `WorkerIdAllocatorChain` 简化为工厂方法或配置类，去掉责任链的灵活性（实际不需要运行时动态组合）
- 保留 PodOrdinal/IpHash/FilePersisted 三个实现，足够覆盖 K8s/VM/开发环境

---

## 三、当前设计的合理性确认

以下设计经重新评估，确认**不属于过度设计**：

### 3.1 结构化并发工具（StructuredConcurrencyScopes + ScopedValues + BoundedVirtualThreadScheduler）

**合理性**：
- JDK 21+ 是公司技术栈的选择方向
- 结构化并发是 Java 并发的未来标准
- 作为工具类库，提前封装标准化的使用模式是合理的
- 有调用方后可以直接使用，无需再二次封装

**保持建议**：无需移除，但建议加上 `@since 3.0 (JDK 21+)` 和 `@sealed` 相关说明

### 3.2 CryptoProvider 统一加密入口

**合理性**：
- 公司要求"内部实现不依赖外部"，统一加密入口是合理的架构决策
- 策略模式（CryptoProviderRegistry）提供了清晰的扩展点
- 国密/国际算法切换虽然当前无运行时需求，但为合规场景预留了能力

**保持建议**：保留架构，补充文档说明使用场景

### 3.3 WorkerIdAllocator 策略链

**合理性**：
- 不同部署环境（K8s/VM/开发）需要不同的 WorkerId 分配策略
- 策略模式提供了清晰的扩展点
- 自定义 WorkerIdAllocator 实现可以通过 prepend 插入到链中

**保持建议**：保留，但简化实现（见 P2-2）

### 3.4 MeteredThreadPoolExecutor（可观测线程池）

**合理性**：
- 提供 Micrometer 指标自动注册 + 慢任务检测
- 作为工具类库，提供开箱即用的可观测线程池是增值能力

**保持建议**：保留，但明确与 ydsz-common-thread 的边界

---

## 四、真正的冗余与风险

### 4.1 与 Hutool/Guava 的能力重叠分析

由于公司策略是"内部实现不依赖外部"，以下能力的自研是**合理冗余**：

| 能力 | 外部已有 | 内部自研 | 合理性 |
|------|---------|---------|--------|
| Hutool BeanUtil | 有 | MapUtils.toBean | 合理（内部可控） |
| Guava Strings | 有 | StringUtils | 合理（无外部依赖） |
| commons-io FileUtils | 有 | 已委托 commons-io | 最佳实践 |
| Hutool DateUtils | 有 | 无（用 JDK time） | 合理 |

### 4.2 真正需要治理的问题

#### 问题 1：文档与代码一致性

从 `UTIL_GUIDELINES.md` 可以看到，早期文档中提到的 `SnowflakeUtils`、`JcaCipherPool`、`Rsa2Utils` 等类从未实现。虽然已在 2026-08-09 治理，但需要持续保持文档同步。

#### 问题 2：@Deprecated 标记不完整

部分已废弃方法缺少完整的 Javadoc 指引：

```java
// 当前
@Deprecated
public static String xxx() { ... }

// 建议
@Deprecated(since = "3.0", forRemoval = true)
@see CryptoUtils#encrypt(String, byte[])
public static String xxx() { ... }
```

#### 问题 3：可选依赖的职责明确

`spring-security-crypto`、`bcprov-jdk18on`、`spring-web` 等为 optional 依赖。需要确保：
- 未引入依赖时，调用方能获得清晰的错误提示（而非 NoClassDefFoundError）
- 有 `@ConditionalOnClass` 或运行时检查

---

## 五、优化建议矩阵（修正版）

| 优先级 | 类别 | 具体事项 | 预估工时 | 风险 |
|--------|------|---------|---------|------|
| 🔴 P0 | 一致性 | PwdUtils 双模合并（标记旧三档枚举废弃） | 1d | 低 |
| 🔴 P0 | 一致性 | AesUtils 完善 @Deprecated 标记（forRemoval + @see） | 0.5d | 低 |
| 🔴 P0 | 文档 | README 增加"能力地图"与"快速选择指南" | 1d | 低 |
| 🟡 P1 | 简化 | MapUtils.toBean 精简（移除 Record/泛型List 转换，保留核心能力） | 2d | 低 |
| 🟡 P1 | 边界 | 明确 util 与 thread 模块的职责边界 | 0.5d | 低 |
| 🟡 P1 | 简化 | WorkerIdAllocatorChain 简化（去掉责任链灵活性，保留组合能力） | 1d | 低 |
| 🟡 P1 | 健壮性 | 可选依赖的运行时检查与友好错误提示 | 1d | 低 |
| 🟢 P2 | 文档 | 统一 API 命名规范（方法名、参数顺序） | 2d | 低 |
| 🟢 P2 | 工具类 | 补充核心方法的单元测试覆盖 | 3d | 低 |
| ⚪ P3 | 能力 | 新增实用工具（如 RetryUtils、RateLimiter 按需） | 按需 | - |

---

## 六、行业对标再思考

### 6.1 工具库的定位差异

| 库 | 定位 | 能力范围 | 设计哲学 |
|----|------|---------|---------|
| Guava | 精选工具 | 5 个核心领域 | 少而精，只做什么都不做 |
| Apache Commons | 独立模块 | 40+ 独立发布 | 按需引入，不强耦合 |
| Hutool | 一站式 | 150+ 包 | 大而全，内部可替代外部 |
| ydsz-common-util | 公司级工具中心 | 精选 + 适度扩展 | 内部可控，按需扩展 |

**ydsz-common-util 的定位更接近 Hutool**——作为公司内部工具中心，提供一站式的工具能力，减少业务方对外部库的依赖。这个定位是合理的。

### 6.2 公司内部工具库的价值

1. **依赖可控**：外部库升级可能导致兼容性问题，内部库完全可控
2. **定制能力**：可以根据公司需求定制（如国密算法、中文处理）
3. **统一标准**：避免业务方各自引入不同版本的工具库
4. **长期维护**：积累公司基础设施资产

---

## 七、结论（修正版）

`ydsz-common-util` 当前**不存在严重的过度设计问题**。

模块在安全加密、分布式 ID 生成、并发工具等方面的能力储备是**合理的**，符合公司内部工具类模块的定位。工具类模块的能力不需要"被引用"才有价值，它的价值在于提供**经过验证的标准化实现**。

**真正需要关注的是**：

1. **API 一致性**：新旧 API 并行带来的认知负担（PwdUtils、AesUtils）
2. **文档完整性**：帮助业务方快速找到合适的工具方法
3. **职责边界**：与兄弟模块（json、thread）的分工明确
4. **实现精简**：个别方法过于复杂（MapUtils.toBean），可以适度精简

**核心建议是"保持能力，优化体验"**：

1. **保留**：结构化并发、CryptoProvider 策略体系、WorkerId 策略链——这些是合理的能力储备
2. **优化**：完善 @Deprecated 标记、统一 API 风格、补充文档指引
3. **精简**：MapUtils.toBean 的过度复杂实现（而非移除 toBean 能力本身）
4. **澄清**：与兄弟模块的职责边界

---

> **报告修正说明**：初版報告錯誤地使用"當前調用頻率"作為判斷標準，忽略了工具类模块"能力储备"的定位。本次修正以"职责边界清晰"和"API 一致性"为核心评估维度。
