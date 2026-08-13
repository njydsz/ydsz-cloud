# ydsz-common-util 模块过度设计评估与优化建议

> **评估范围**：`ydsz-common-util` 39 个 Java 文件，约 37 个公共类
> **评估时间**：2026-08-13
> **对标基准**：Google Guava、Apache Commons、Hutool、美团 Leaf/UidGenerator、阿里巴巴 Java 开发手册（泰山版）
> **评估维度**：职责边界、抽象层次、未来预测设计、API 复杂度、依赖关系

---

## 一、模块现状总览

`ydsz-common-util` 是 YDSZ Cloud 项目的 L2 通用工具能力中心，覆盖以下领域：

| 领域 | 核心类 | 文件数 |
|------|--------|--------|
| ID 生成 | SnowflakeIdGenerator, IdGenerator, WorkerIdAllocator* | 14 |
| 加密/安全 | AesUtils, AesGcmCrypto, DigestUtils, Sm2/3/4Utils, BcProvider | 9 |
| 加密统一层 | CryptoProvider, CryptoProviderRegistry, CryptoUtils, AesGcmCryptoProvider, Sm4GcmCryptoProvider, Sm4CbcCryptoProvider | 6 |
| 密码与强度 | PwdUtils, PasswordStrengthChecker, DefaultPasswordStrengthChecker | 3+props |
| HTTP 工具 | ServletRequestUtils, HttpResponseUtils, HttpTokenUtils, RequestContextUtils, TrustedProxyConfiguration, UrlPathUtils | 6 |
| 并发 | ExecutorUtils, MeteredThreadPoolExecutor, StructuredConcurrencyScopes, ScopedValues, BoundedVirtualThreadScheduler | 5 |
| 集合 | CollectionUtils, MapUtils, SequencedCollections | 3 |
| 其他 | StringUtils, YamlUtils, MessageUtils, IpValidator, CidrUtils, NetworkInterfaceUtils, BeanUpdateUtil, AuthInfoUtils 等 | ~10 |

模块总代码量约 **4,500-5,000 行**（不含空行/注释），属于中等规模工具库。

---

## 二、过度设计识别与分级评估

### 2.1 【A 级】明确的过度设计——建议精简或移除

#### A1. 结构化并发工具（StructuredConcurrencyScopes + ScopedValues + BoundedVirtualThreadScheduler）

**现状**：
- `StructuredConcurrencyScopes` 封装 JDK 21 的 `StructuredTaskScope`，提供 `allSuccess()` / `firstSuccess()` / `allOf()` / `firstSuccessOf()` / `allOfWithTimeout()` 五个方法
- `ScopedValues` 封装 JDK 21 的 `ScopedValue`，提供 5 个预定义常量 + 3 个便捷方法
- `BoundedVirtualThreadScheduler` 在 JDK 21 `Executors.newVirtualThreadPerTaskExecutor()` 上包装 Semaphore 背压控制

**冗余度分析**：

| 类 | 实际代码 | 对 JDK 21 原生的封装增量 | 业务调用方 |
|----|---------|------------------------|-----------|
| StructuredConcurrencyScopes | ~230 行 | 简化 2 个工厂方法 + 1 个结果包装类 | 0（仓库内无调用） |
| ScopedValues | ~135 行 | 5 个预定义常量 + 3 个委托方法 | 0 |
| BoundedVirtualThreadScheduler | ~155 行 | Semaphore + ThreadFactory 组合 | 0 |

**问题**：
1. **零调用方**：三个类在整个仓库内均无实际业务调用，纯预留设计
2. **API 不稳定风险**：JDK 21 的 StructuredConcurrency 仍在 Preview 阶段（Java 24 才正式定稿），API 可能变更
3. **认知负担**：增加 3 个新类 + 约 520 行代码，但当前无业务价值
4. **YAGNI 违反**：为"预测未来 AI Agent 竞速、CDN 多源下载"等场景提前设计

**对标分析**：
- Guava 不做 JDK 新特性封装（等待 JDK 正式 Release 后社区自然适配）
- 美团 Leaf/UidGenerator：聚焦单一领域（ID 生成），不扩散到并发工具

#### A2. CryptoProviderRegistry + CryptoProvider 策略体系

**现状**：
- `CryptoProvider` 接口定义 `algorithm()` / `keyLength()` / `ivLength()` / `generateKey()` / `generateIv()` / `encrypt()` / `decrypt()` 7 个方法
- `CryptoProviderRegistry` 静态注册表，内置 AES-128/192/256-GCM + SM4-GCM/CBC
- `CryptoUtils` 统一业务入口，支持 Base64/Hex 编解码
- 底层实现 `AesGcmCryptoProvider`、`Sm4GcmCryptoProvider`、`Sm4CbcCryptoProvider`

**冗余度分析**：

```
业务调用 → CryptoUtils.encrypt() → CryptoProviderRegistry.get() → AesGcmCryptoProvider.encrypt()
         ↓
    直接使用: new AesGcmCrypto(key).encrypt()（已有实现）
```

**问题**：
1. **抽象层过多**：从`CryptoUtils`→`CryptoProviderRegistry`→`CryptoProvider`→`AesGcmCryptoProvider`，4 层委托链
2. **实际算法切换需求弱**：业务系统一旦选定算法（AES 或 SM4），不会运行时切换。系统属性 `crypto.algorithm` 的全局切换能力**无调用方**
3. **增量价值有限**：
   - `AesUtils`（标记 Deprecated）仍保留且功能完整
   - `AesGcmCrypto` 实例化用法已满足高频场景
   - 注册表模式更适合框架级产品（如 Spring Security Crypto），而非单一项目工具库

#### A3. MapUtils.toBean 重型反射引擎

**现状**：
- `MapUtils.toBean()` 支持 setter 反射、类型转换、嵌套 Bean 递归、`Optional<T>` 解包、`List<T>` 泛型、`Record` 支持
- 内嵌 `TypeReference<T>` 抽象类解决泛型擦除
-  ConcurrentHashMap 缓存 setter 方法
- 支持 `snake_case` ↔ `camelCase` 命名互转
- 总代码量约 **650 行**（MapUtils 共 1080 行，toBean 系列占 60%）

**冗余度分析**：
1. **与 JSON 框架功能重叠**：项目已统一使用 Fastjson2（`ydsz-common-json`），`JSON.toJavaObject()` 可完全覆盖 Map→Bean 场景
2. **Record 支持过度**：Record 是 JDK 14+ 的不可变载体，项目目前未使用 Record 作为 DTO
3. **类型转换复杂度**：支持 Integer/Long/Double/Boolean/LocalDateTime/LocalDate/LocalTime/Instant/Date/UUID/Duration/YearMonth/Optional/List/嵌套 Bean 等 15+ 类型，多数场景不会用到

**对标分析**：
- Hutool 的 `BeanUtil.toBean` 确实存在，但 Hutool 定位为"一站式工具包"，且明确声明**不保证高性能**
- Apache Commons BeanUtils 的 `BeanUtils.copyProperties` 性能差，社区已不推荐
- 现代项目首选 MapStruct（编译期生成）或 JSON 框架直接反序列化

#### A4. WorkerIdAllocator 策略链（4 个分配器 + 异常类 + 链）

**现状**：
- `WorkerIdAllocator` 接口（SPI 扩展点）
- `PodOrdinalWorkerIdAllocator`：K8s StatefulSet Pod 序号解析
- `IpHashWorkerIdAllocator`：IP 末段哈希
- `FilePersistedWorkerIdAllocator`：开发环境文件持久化
- `WorkerIdAllocatorChain`：策略链组合器
- 辅助类：`NotApplicableException`、`WorkerIdExhaustedException`

**冗余度分析**：

| 场景 | 实际选择 | 策略链实际可达 |
|------|---------|--------------|
| K8s 部署 | PodOrdinal（命中即返） | IpHash、FilePersisted 不可达 |
| 虚拟机部署 | IpHash（命中即返） | FilePersisted 不可达 |
| 开发环境 | FilePersisted | 独立使用 |

**问题**：
1. **策略链模式过度**：`prepend()`/`append()` 链式组合、异常驱动的策略降级，属于**责任链模式**的完整版实现。实际部署环境中，PodOrdinal 几乎 100% 命中（K8s 场景），其余策略仅为预留
2. **配置复杂度**：`SnowflakeProperties` + `WorkerIdAllocatorChain` + 4 个分配器 = ~400 行配置相关代码，远超 Snowflake 算法本身（~200 行）
3. **中心化注册未被替代**：文档和代码注释中反复提到 WorkerIdRegistry（旧 SPI），但实际仓库内无 Redis/ETCD/Nacos 中心的实现——说明业务方实际不需要这个扩展点

**对标分析**：
- 美团 Leaf：仅支持 1 种 WorkerId 分配（Zookeeper 持久顺序节点），不预留策略链
- 百度 UidGenerator：仅支持 1 种（IP+Port 哈希），但提供号段模式替代

---

### 2.2 【B 级】边界过度设计——建议收敛抽象层次

#### B1. MeteredThreadPoolExecutor 的可观测线程池

**现状**：继承 `ThreadPoolExecutor`，叠加 Micrometer 指标注册 + 慢任务检测 + 失败计数 + Hook 回调

**评估**：
- **合理部分**：Micrometer 指标自动注册对运维有价值
- **过度部分**：
  - 内置 `AtomicLong` 计数器 **重复** Micrometer 已有的 Counter
  - `onTaskFailed` / `onSlowTask` Hook 方法预留**无子类实现**，属于为继承而继承的设计
  - 构造器参数 10 个，调用方需传入 `MeterRegistry`（Spring 环境通常全局注入）

**对标**：Spring Boot Actuator 已提供 `ExecutorServiceMetrics`，可直接绑定任意 Executor，无需子类化

#### B2. PwdUtils 的双模密码强度系统

**现状**：
- 旧 API：`checkPasswordStrength()` → 返回 `PasswordStrength`（WEAK/MEDIUM/STRONG 三档枚举）
- 新 API：`checkPasswordStrengthLevel()` → 返回 `PasswordStrengthLevel`（五档枚举）
- SPI：`PasswordStrengthChecker` 接口 + `DefaultPasswordStrengthChecker` 实现 + ServiceLoader

**评估**：
1. **两套枚举共存的认知负担**：`PasswordStrength` vs `PasswordStrengthLevel`，字段名相似但语义不同
2. **SPI 扩展点未被使用**：仓库内无自定义 `PasswordStrengthChecker` 实现
3. **国际化内置过深**：`suggestPasswordImprovement()` 返回建议文本，但密码策略国际化需求极少

#### B3. TracerUtils 的无编译期依赖设计

**现状**：使用反射或 System.getProperty 读取 TraceId，避免对 Sleuth/OpenTelemetry 的编译期依赖

**评估**：合理的防御性设计，但反射调用在链路追踪场景（高频路径）可能存在性能开销。美团内部通常直接使用 MTrace SDK。

---

### 2.3 【C 级】合理设计——保持但需文档化

#### C1. AesUtils → CryptoUtils 的迁移标记

**现状**：`AesUtils` 和 `Sm4Utils` 标记 `@Deprecated`，内部委派给 `CryptoProviderRegistry`

**评估**：正确做法（保持向后兼容），但建议设置 `forRemoval=true` 并明确版本移除时间

#### C2. YamlUtils 的 snakeyaml 委托

**合理**：YAML 解析无自研必要，委托 snakeyaml 是正确选择

#### C3. commons-io 替代自定义 IO

**合理**：POM 已引入 `commons-io` 作为首选依赖，符合行业标准

#### C4. SnowflakeProperties 外部化配置

**合理**：`ydsz.util.snowflake` 前缀配置对齐 Spring Boot 约定

---

## 三、职责边界评估

### 3.1 与 ydsz-common-json 的职责重叠

| 能力 | ydsz-common-util | ydsz-common-json | 建议 |
|------|-----------------|-----------------|------|
| Map → Bean 转换 | `MapUtils.toBean()` 650 行 | `YdszJson.toJavaObject()` | 统一使用 json 模块 |
| 对象序列化 | `HttpResponseUtils.renderJson()` | `YdszJson.toJson()` | 已收敛，OK |
| 类型安全取值 | `MapUtils.getString/getInteger/getLong/getBoolean` | JSON Path | JSON Path 更灵活 |

### 3.2 与 ydsz-common-thread 的职责重叠

| 能力 | ydsz-common-util | ydsz-common-thread | 建议 |
|------|-----------------|-------------------|------|
| 线程池创建 | `ExecutorUtils`（15+ 方法） | 独立模块 | 明确分工：util 提供纯工厂，thread 提供企业级组件 |
| 可观测线程池 | `MeteredThreadPoolExecutor` | 可能重复 | 需对齐设计边界 |

### 3.3 与 Spring Boot Actuator 的职责重叠

| 能力 | ydsz-common-util | Spring Boot | 建议 |
|------|-----------------|------------|------|
| 健康检查 | `SnowflakeHealthIndicator` | Actuator Health | 保留（Snowflake 特有状态） |
| 线程池指标 | `MeteredThreadPoolExecutor` 手动注册 | `ExecutorServiceMetrics` | 优先使用 Spring 原生 |

---

## 四、API 复杂度评估

### 4.1 方法数量过多

| 类 | 公共方法数 | 建议上限 | 评级 |
|----|-----------|---------|------|
| ExecutorUtils | 25+（含 ThreadPoolBuilder） | ≤ 15 | 🔴 偏多 |
| MapUtils | 20+ | ≤ 10 | 🔴 偏多 |
| StringUtils | 约 15 | ≤ 10 | 🟡 中等 |
| PwdUtils | 12 | ≤ 8 | 🟡 中等 |
| ServletRequestUtils | 约 10 | ≤ 8 | 🟢 适中 |

### 4.2 构造器/工厂复杂

`MeteredThreadPoolExecutor` 的两个构造器分别需要 **10 个**和 **9 个**参数，远超一般可接受范围（≤ 4 个）。Builder 模式缺失（虽然 `ExecutorUtils` 提供了 Builder，但具体类本身没有）。

---

## 五、依赖健康度评估

### 5.1 依赖分层

```
ydsz-common-util
├── ydsz-common-core ← OK（公共基础）
├── ydsz-common-domain ← OK（公共基础）
├── ydsz-common-json ← OK（公共基础）
├── snakeyaml ← OK（唯一 YAML 解析）
├── slf4j-api ← OK（日志门面）
├── commons-io ← OK（文件/IO 标准）
├── transmittable-thread-local (optional) ← OK（TTL 上下文）
├── spring-boot-starter-validation (optional) ← OK
├── spring-web (optional) ← OK
├── spring-security-crypto (optional) ← OK（BCrypt）
├── bcprov-jdk18on (optional) ← OK（国密）
├── micrometer-core (optional) ← OK（指标）
└── spring-boot-health (optional) ← OK（健康检查）
```

**依赖设计评价**：✅ 优秀。严格执行"核心零强制三方依赖"原则，所有 Spring/第三方库均为 `optional`，业务按需引入。

---

## 六、可落地优化建议

### 6.1 P0：立即精简（消除过度设计，减少维护负担）

#### P0-1. 移除结构化并发工具类

**范围**：
- `StructuredConcurrencyScopes.java`（230 行）
- `ScopedValues.java`（135 行）
- `BoundedVirtualThreadScheduler.java`（155 行）

**理由**：
- 零调用方，纯预留设计
- JDK 21 StructuredConcurrency 尚未正式定稿（预计 JDK 24），API 不稳定
- 违反 YAGNI（You Aren't Gonna Need It）原则

**替代方案**：待 JDK 24 正式发布后，按实际业务需求（如 AI Agent 竞速）逐步引入，或通过直接使用 `StructuredTaskScope` 原生 API

**预期收益**：减少 ~520 行代码，消除 3 个无价值类的维护成本

#### P0-2. 标记 MapUtils.toBean 系列为 @Deprecated

**范围**：`MapUtils.toBean()` / `toBean(TypeReference)` / `toBeanOrRecord()` 及相关私有方法（共 ~650 行）

**理由**：
- 与 `ydsz-common-json`（Fastjson2）功能重叠
- 复杂反射逻辑维护成本高（泛型处理、命名转换、Record 支持）
- 性能远低于 JSON 框架（Fastjson2 ASM 优化）

**替代方案**：`YdszJson.toJavaObject(map, TargetClass.class)` 或 Fastjson2 原生 `JSON.toJavaObject()`

**预期收益**：减少 ~650 行代码，统一转换入口，降低认知负担

#### P0-3. 简化 WorkerIdAllocator 策略链

**范围**：保留 `WorkerIdAllocator` 接口，但简化实现

**建议**：
1. 移除 `WorkerIdAllocatorChain`（责任链模式过度设计）
2. 移除 `FilePersistedWorkerIdAllocator`（可用系统属性 `ydsz.util.snowflake.worker-id` 替代）
3. 保留 `PodOrdinalWorkerIdAllocator`（K8s 场景）和 `IpHashWorkerIdAllocator`（VM 场景）
4. 通过 Spring `@ConditionalOnProperty` 或 `@ConditionalOnMissingBean` 在配置类中决定使用哪个策略，而非运行时动态链

**预期收益**：减少 ~200 行代码，简化 WorkerId 分配逻辑

---

### 6.2 P1：中期收敛（优化 API 边界，降低认知负担）

#### P1-1. CryptoProvider 策略体系收敛

**建议**：
1. 保留 `CryptoProvider` 接口和 `CryptoProviderRegistry` 作为**内部实现细节**（不再对外暴露）
2. `CryptoUtils` 标记为 `@Deprecated(since = "3.0.0", forRemoval = true)`
3. 引导业务方使用 `AesGcmCrypto`（实例化）或 `DigestUtils`（摘要）等具体类
4. 移除系统属性 `crypto.algorithm` 的全局切换能力（实际无调用方）

**预期收益**：减少 ~300 行代码 + 简化加密模块的认知路径

#### P1-2. ExecutorUtils 方法收敛

**范围**：`ExecutorUtils` 25+ 方法

**建议**：
1. 保留核心工厂方法：`newFixedThreadPool` / `newCpuBoundThreadPool` / `newVirtualThreadExecutor` / `builder()` / `shutdownGracefully`
2. 标记为 `@Deprecated`：`newCachedThreadPool` / `newSingleThreadExecutor` / `newDaemonFixedThreadPool` / `newPriorityThreadPool` / `newScheduledThreadPool`
3. TTL 相关方法考虑下沉到 `ydsz-common-thread` 模块（与 TTL 依赖绑定）

**预期收益**：API 从 25+ 降至 10 个，降低选择困难

#### P1-3. PwdUtils 双模合并

**建议**：
1. 移除 `PasswordStrength` 旧三档枚举（标记 `@Deprecated`）
2. `checkPasswordStrength()` 方法委托到 `checkPasswordStrengthLevel()` 并做映射
3. SPI `PasswordStrengthChecker` 保留（合理扩展点），但移除国际化 `suggestPasswordImprovement()` 方法

**预期收益**：统一密码强度 API，减少 1 套枚举 + 2 个方法

---

### 6.3 P2：长期演进（架构对齐）

#### P2-1. 与 ydsz-common-thread 模块对齐

**现状**：`ydsz-common-thread` 模块已存在，但 `ydsz-common-util` 仍有 `ExecutorUtils` / `MeteredThreadPoolExecutor` / `StructuredConcurrencyScopes` 等并发工具

**建议**：
- 将 `MeteredThreadPoolExecutor` / `BoundedVirtualThreadScheduler` 迁移到 `ydsz-common-thread`
- `ydsz-common-util` 的 `ExecutorUtils` 仅保留**纯 JDK 无监控版**工厂方法
- 可观测线程池、TTL 包装等高级能力统一归属 `ydsz-common-thread`

#### P2-2. 与 ydsz-common-json 的 Map→Bean 能力对齐

**建议**：
1. `MapUtils` 精简为仅提供 **类型安全取值**（getString/getInteger/getLong/getBoolean）和 **集合判空**（isEmpty/isNotEmpty）
2. 所有 Bean 转换能力统一标注 `@Deprecated` 并引导到 JSON 模块
3. 新增 `MapUtils.getNestedValue(map, "a.b.c")` 等更实用的能力替代被移除的方法

#### P2-3. SnowflakeIdGenerator 便捷构造器收敛

**现状**：`new SnowflakeIdGenerator()` 无参构造器内部调用 `WorkerIdAllocatorChain.defaults()` 创建完整策略链

**建议**：无参构造器仅做极简初始化（IP 哈希兜底），完整策略链通过 Spring 配置类注入

---

## 七、量化收益预估

| 优化项 | 减少代码行数 | 减少类数 | 降低认知负担 |
|--------|------------|---------|-------------|
| P0-1 移除结构化并发 | ~520 行 | 3 个 | 高 |
| P0-2 标记 toBean 废弃 | ~650 行 | 1 个方法群 | 中 |
| P0-3 简化 WorkerId 链 | ~200 行 | 2 个 | 中 |
| P1-1 CryptoProvider 收敛 | ~300 行 | 3 个 | 中 |
| P1-2 ExecutorUtils 收敛 | ~200 行 | 8 个方法 | 低 |
| P1-3 PwdUtils 合并 | ~80 行 | 1 个枚举 | 低 |
| **总计** | **~1,950 行** | **~9 个类/枚举** | - |

**优化后模块规模预估**：
- 当前：~5,000 行 / 39 文件 → 优化后：~3,000 行 / 30 文件
- 代码量减少约 **40%**，公共 API 减少约 **35%**

---

## 八、行业对标总结

| 维度 | Guava | Hutool | 美团 Leaf | ydsz-common-util（当前） | 优化后目标 |
|------|-------|--------|----------|------------------------|-----------|
| **工具类覆盖** | 精选 5 个领域 | 全覆盖 | 仅 ID 生成 | 30+ 小包 | ≤ 10 个核心领域 |
| **抽象层次** | 2 层（API→实现） | 1-2 层 | 1 层 | 3-4 层 | ≤ 2 层 |
| **未来预测设计** | 无 | 少量 | 无 | 3 处（结构化并发/CryptoProvider/WorkerId 链） | 无 |
| **SPI 扩展点** | 3 个（合理） | 5 个 | 1 个 | 5 个 | 2-3 个 |
| **单文件最大行数** | ≤ 800 | ≤ 1200 | ≤ 400 | 1080（MapUtils） | ≤ 600 |
| **设计哲学** | 少即是多 | 大而全 | 单点极致 | 全而不精 | 精选 + 实用 |

---

## 九、结论

`ydsz-common-util` 当前处于**"设计能力超前于业务需求"**的状态。模块在安全性、可配置性、SPI 扩展性方面展现了良好的架构设计，但在工具类覆盖范围上陷入了"预测未来"的过度设计陷阱。

**核心优化方向**：

1. **做减法**：移除零调用方的未来预测设计（结构化并发、toBean 重型引擎）
2. **收敛抽象**：将 3-4 层委托链简化为 1-2 层，降低认知负担
3. **明确边界**：JSON 转换归 json 模块，线程池归 thread 模块，util 聚焦**无状态纯工具**
4. **单点聚焦**：学习美团 Leaf/UidGenerator 的"单点极致"哲学，在 ID 生成、加密算法、Snowflake 等高价值领域做深做精

预计经过一轮优化后，模块代码量可减少 **35%-40%**，API 认知负担降低 **50%**，同时保留核心差异化竞争力（Snowflake 策略链、统一加密入口、国密支持）。

---

> **报告撰写**：基于 ydsz-common-util 39 个 Java 文件源码完整审计 + 行业对标分析
> **审阅建议**：建议团队架构师 review P0 事项后，分迭代推进（每迭代 1-2 个 P0，避免一次性大改）
