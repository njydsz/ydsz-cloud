# ydsz-common-util 模块优化完善建议

> **模块定位**：公司级内部工具类能力中心，提供零强制三方依赖的标准化实现，供业务方按需选用
> **评估时间**：2026-08-13
> **核心原则**：保持能力储备、优化使用体验、明确职责边界、完善文档测试

---

## 一、近期优化（1-2 周可完成）

### 1.1 API 一致性治理

#### 1.1.1 PwdUtils 密码强度系统统一

**现状**：两套密码强度枚举并存

```java
// 旧 API（三档）
public enum PasswordStrength { WEAK, MEDIUM, STRONG }
public static PasswordStrength checkPasswordStrength(String password)

// 新 API（五档）
public enum PasswordStrengthLevel { VERY_WEAK, WEAK, MEDIUM, STRONG, VERY_STRONG }
public static PasswordStrengthLevel checkPasswordStrengthLevel(String password)
```

**方案**：
- `PasswordStrength` 枚举标记 `@Deprecated(since = "3.0", forRemoval = true)`
- `checkPasswordStrength()` 内部委托到 `checkPasswordStrengthLevel()` 并映射
- README 中更新示例，引导使用五档枚举

**影响范围**：需排查业务方是否直接引用旧枚举（当前仓库内无业务方直接引用）

#### 1.1.2 AesUtils 的 @Deprecated 治理

**现状**：类级别标记 `@Deprecated(since = "3.0.0", forRemoval = false)`，但：
- 部分方法（如 `base64Encode/decode`）标记为 `@Deprecated` 指向 HexUtils
- `initKey()` / `generateSecureKey()` 等密钥生成方法未标记废弃

**方案**：
- 保留类级别 `@Deprecated`，`forRemoval` 改为 `true`
- 密钥生成方法保留不废弃（仍被业务方使用，且无直接替代）
- 移除内部方法上的嵌套 `@Deprecated` 标记，统一由类级别说明覆盖

#### 1.1.3 ExecutorUtils 方法组织优化

**现状**：25+ 方法平铺，调用方难以快速定位

**方案**：不减少方法，而是在类 JavaDoc 中增加分组索引：

```java
/**
 * 快速选择指南：
 * ┌─────────────────────────────────────────────────────────────┐
 * │ 场景                │ 推荐方法                              │
 * ├─────────────────────────────────────────────────────────────┤
 * │ 固定大小线程池       │ newFixedThreadPool(n)                │
 * │ CPU 密集型线程池     │ newCpuBoundThreadPool()              │
 * │ IO 密集型（虚拟线程）│ newVirtualThreadExecutor()            │
 * │ 自定义配置           │ builder().corePoolSize().build()     │
 * │ 优雅关闭             │ shutdownGracefully(executor, 30, s)  │
 * │ TTL 上下文透传       │ toTtlThreadPool(executor)            │
 * └─────────────────────────────────────────────────────────────┘
 */
```

---

### 1.2 文档体系完善

#### 1.2.1 README 重构

**现状**：单文件 380+ 行，能力清单、配置项、示例混杂

**方案**（分层文档）：

```
README.md                      ← 精简至 150 行：定位 + 快速开始 + 能力地图
├── docs/
│   ├── QUICK-REFERENCE.md     ← API 速查表（按场景索引）
│   ├── CRYPTO-GUIDE.md        ← 加密模块使用指南
│   ├── CONCURRENCY-GUIDE.md   ← 并发工具使用指南
│   └── MIGRATION-3.0.md       ← 3.0 版本迁移说明
```

**README 新增"能力地图"**：

```
ydsz-common-util 能力地图
┌──────────────────────────────────────────────────────────────────┐
│ 类别        │ 入口类                    │ 一句话说明              │
├──────────────────────────────────────────────────────────────────┤
│ ID 生成     │ SnowflakeIdGenerator      │ 分布式唯一 ID（雪花）   │
│ ID 生成     │ IdGenerator               │ 静态门面，非 Spring 可用│
│ 加密        │ CryptoUtils               │ AES/SM4 统一入口        │
│ 摘要签名    │ DigestUtils               │ SHA-256/HMAC/PBKDF2     │
│ 密码        │ PwdUtils                  │ BCrypt + 强度校验       │
│ HTTP 请求   │ ServletRequestUtils       │ 请求头/参数解析         │
│ 响应渲染    │ HttpResponseUtils         │ JSON/XML 统一响应       │
│ 线程池      │ ExecutorUtils             │ 线程池工厂 + 优雅关闭   │
│ 可观测线程池│ MeteredThreadPoolExecutor │ Micrometer 指标自动注册 │
│ 并发编排    │ StructuredConcurrencyScopes│ JDK 21 结构化并发      │
│ 上下文传递  │ ScopedValues              │ JDK 21 虚拟线程安全     │
│ 集合        │ CollectionUtils / MapUtils│ 类型安全 + 转换         │
│ IP 地址     │ IpValidator / CidrUtils   │ 校验/CIDR/内网判断      │
│ 认证上下文  │ AuthInfoUtils             │ 数据权限维度读取        │
└──────────────────────────────────────────────────────────────────┘
```

#### 1.2.2 可选依赖运行时提示增强

**现状**：`spring-security-crypto`、`bcprov-jdk18on` 等为 optional，未引入时调用会抛 `NoClassDefFoundError`

**方案**：在工具类入口处增加友好的运行时检查

```java
// PwdUtils 示例
public static String hashPasswordBCrypt(String rawPassword) {
    if (!isBcryptAvailable()) {
        throw new IllegalStateException(
            "BCrypt 需要 spring-security-crypto 依赖。请在 pom.xml 中添加：\n" +
            "<dependency>\n" +
            "  <groupId>org.springframework.security</groupId>\n" +
            "  <artifactId>spring-security-crypto</artifactId>\n" +
            "</dependency>"
        );
    }
    return BCRYPT_ENCODER.encode(rawPassword);
}

private static boolean isBcryptAvailable() {
    try {
        Class.forName("org.springframework.crypto.bcrypt.BCryptPasswordEncoder");
        return true;
    } catch (ClassNotFoundException e) {
        return false;
    }
}
```

**涉及类**：
- `PwdUtils`（BCrypt 依赖 spring-security-crypto）
- `Sm2Utils` / `Sm3Utils` / `Sm4Utils`（国密依赖 bcprov-jdk18on）
- `HttpUtils` / `ServletRequestUtils`（部分依赖 spring-web）

---

### 1.3 单元测试补全

#### 1.3.1 当前测试覆盖情况

从 `pom.xml` 可见已有 Testing 依赖：
- `junit-jupiter`
- `assertj-core`
- `archunit-junit5`

需确认测试文件分布。

#### 1.3.2 优先补充测试的核心类

| 类 | 优先级 | 测试要点 |
|----|--------|---------|
| `DigestUtils` | P0 | SHA-256 标准向量、HMAC 标准向量、PBKDF2 已知输出 |
| `IpValidator` | P0 | IPv4/IPv6 边界、内网/私网 IP 判断 |
| `PwdUtils` | P0 | BCrypt roundtrip、PBKDF2 已知输出、密码强度边界 |
| `StringUtils` | P1 | 空值边界、驼峰/下划线互转、format 占位符 |
| `MapUtils`（核心方法） | P1 | getString/getInteger 类型转换、isEmpty |
| `IdGenerator` | P1 | 单线程递增 ID、解析字段往返 |
| `ExecutorUtils` | P2 | 线程池创建后 shutdownGracefully 正常 |
| `SnowflakeIdGenerator` | P2 | 并发安全（多线程 nextId 无重复） |

---

## 二、中期完善（1-2 个月）

### 2.1 能力补充

#### 2.1.1 MapUtils 保留核心能力、简化重叠部分

**保留**：
- 类型安全取值（getString/getInteger/getLong/getBoolean/getMap/getList）
- 集合归一化（toStringObjectMap/safeCastMap/safeCastList）
- 嵌套解析（getListOfMaps/getMapFromList）

**简化**：
- `toBean` 保留标记 `@Deprecated(since = "3.0")`，引导到 JSON 框架
- `toBeanOrRecord` / `TypeReference` / `convertToList` / `convertOptional`：标记 `@Deprecated` 或直接移除
- 移除 MapUtils 中 Record 相关代码（当前无调用方）

**预期**：MapUtils 从 1080 行精简至 400-500 行

#### 2.1.2 WorkerIdAllocator 策略链简化

**保留**：
- `WorkerIdAllocator` 接口（SPI 扩展点）
- `PodOrdinalWorkerIdAllocator`（K8s 场景）
- `IpHashWorkerIdAllocator`（VM 场景）

**简化**：
- 移除 `WorkerIdAllocatorChain` 责任的链灵活性
- 将策略选择下沉到 Spring 配置类：

```java
@Configuration
public class SnowflakeConfig {
    
    @Bean
    @Profile("kubernetes")
    public WorkerIdAllocator podOrdinalAllocator() {
        return new PodOrdinalWorkerIdAllocator();
    }
    
    @Bean
    @Profile("default")
    public WorkerIdAllocator ipHashAllocator() {
        return new IpHashWorkerIdAllocator();
    }
}
```

- `FilePersistedWorkerIdAllocator` 标记 `@Deprecated`，用系统属性 `ydsz.util.snowflake.worker-id` 替代

#### 2.1.3 补充实用工具能力

**新增 RetryUtils**（参考 resilience4j 简化版）：

```java
public final class RetryUtils {
    
    public static <T> T executeWithRetry(Callable<T> action, int maxRetries, long delayMs);
    
    public static <T> T executeWithExponentialBackoff(Callable<T> action, RetryConfig config);
    
    @Data
    @Builder
    public static class RetryConfig {
        private int maxRetries;
        private Duration initialDelay;
        private Duration maxDelay;
        private double multiplier;
        private Predicate<Throwable> retryOn;
    }
}
```

**新增 FileTypeValidator**（文件类型白名单校验）：

```java
public final class FileTypeValidator {
    
    /**
     * 通过魔数判断文件类型，防止扩展名伪造
     */
    public static boolean isValidFileType(InputStream input, Set<String> allowedTypes);
    
    /**
     * 检测压缩炸弹（Zip Bomb）
     */
    public static boolean isZipBomb(Path file, double thresholdRatio);
}
```

---

### 2.2 性能优化

#### 2.2.1 SnowflakeIdGenerator 微优化

| 优化点 | 方案 | 预期收益 |
|--------|------|---------|
| 消除伪共享 | `state` 字段使用 `@Contended`（JDK 21+）或手动 padding | 16+ 核机器提升 5-15% |
| 减少 native 调用 | `System.currentTimeMillis()` 缓存到 volatile 字段，1ms 精度内复用 | 减少 10-20% native 调用 |
| 序列号溢出优化 | 使用位运算 `& SEQUENCE_MASK` 替代取模 | 微优化 |

#### 2.2.2 DigestUtils ThreadLocal 缓冲区安全性

**现状**：`STREAM_BUFFER` 使用 `ThreadLocal<byte[]>()`，在 `sha256Hex(InputStream)` 中使用后清零。

**优化**：考虑使用 `SoftReference` 或对象池替代 ThreadLocal，减少线程池场景下的内存占用。

#### 2.2.3 MapUtils setter 缓存优化

**现状**：`SETTER_CACHE` 使用无界 `ConcurrentHashMap`，可能随类数量增长占用内存。

**优化**：使用 `LinkedHashMap` 实现 LRU 淘汰（上限 256 个类）：

```java
private static final Map<Class<?>, Map<String, Method>> SETTER_CACHE = 
    Collections.synchronizedMap(new LinkedHashMap<>(256, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Class<?>, Map<String, Method>> eldest) {
            return size() > 256;
        }
    });
```

---

### 2.3 测试体系建设

#### 2.3.1 ArchUnit 架构测试扩展

**现状**：已有 `UtilArchitectureTest`（P3-2）

**扩展规则**：

```java
// 工具类必须是 final + 私有构造器
classes().that().areAnnotatedWith(UtilityClass.class)
    .should().beFinal()
    .andShould().haveOnlyPrivateConstructors()

// Utils 类不能是 abstract
classes().that().haveSimpleNameEndingWith("Utils")
    .should().notBeAbstract()

// Utils 类不能有实例字段
classes().that().haveSimpleNameEndingWith("Utils")
    .should().haveOnlyFinalOrStaticFields()
```

#### 2.3.2 性能基准测试（JMH）

对核心类建立 JMH 基准测试，防止性能回退：

```@BenchmarkMode(Mode.Throughput)
@Warmup(iterations = 3)
@Measurement(iterations = 5)
public class SnowflakeBenchmark {
    
    private SnowflakeIdGenerator generator;
    
    @Benchmark
    public long testNextId() {
        return generator.nextId();
    }
}
```

---

## 三、长期演进（按业务需求驱动）

### 3.1 与兄弟模块的职责对齐

| 能力 | ydsz-common-util | ydsz-common-json | ydsz-common-thread | 分工建议 |
|------|-----------------|-----------------|-------------------|---------|
| Map→Bean | MapUtils.toBean（精简废弃） | YdszJson.toJavaObject | - | JSON 模块为主 |
| 线程池工厂 | ExecutorUtils | - | - | util 保留纯工厂 |
| 可观测线程池 | MeteredThreadPoolExecutor | - | 企业级封装 | thread 模块为主 |
| 结构化并发 | StructuredConcurrencyScopes | - | - | util 保留（JDK 21 封装） |

### 3.2 版本演进策略

| 版本 | 计划 |
|------|------|
| 3.1.x | API 一致性治理 + 文档完善 + 测试补全 |
| 3.2.x | MapUtils 精简 + 新增 RetryUtils |
| 4.0.x | 移除已标记 @Deprecated forRemoval=true 的 API |

### 3.3 业务推广建议

1. **新业务接入指南**：在 wiki 中发布《工具类模块接入最佳实践》
2. **定期评审**：每季度评审新增工具方法，确保与已有能力不重叠
3. **使用统计**：通过 CI 扫描各业务模块的工具类引用，了解使用热度

---

## 四、优化收益预估

| 维度 | 当前 | 优化后 |
|------|------|--------|
| 文档完备度 | README 单文件 380 行 | 分层文档 + 场景索引 |
| API 一致性 | 3 处新旧 API 并行 | 统一入口 + 完整迁移指引 |
| 可选依赖容错 | NoClassDefFoundError | 友好错误提示 |
| 核心类测试覆盖 | 部分覆盖 | 核心类 80%+ |
| MapUtils 复杂度 | 1080 行 | 400-500 行 |
| 新增能力 | - | RetryUtils + FileTypeValidator |

---

## 五、执行计划

```
第 1 周（API 一致性）
├── PwdUtils 双模合并
├── AesUtils @Deprecated 治理
└── ExecutorUtils 文档索引

第 2 周（文档 + 测试）
├── README 重构 + 能力地图
├── 可选依赖运行时检查
└── DigestUtils / IpValidator 测试补充

第 3-4 周（能力补充）
├── MapUtils 简化（移除 Record/泛型 List）
├── WorkerIdAllocator 策略链简化
└── RetryUtils 实现

第 5-6 周（性能 + 测试）
├── Snowflake 微优化
├── ArchUnit 扩展
└── JMH 基准测试建立

第 7-8 周（稳定化）
├── 业务方反馈收集
├── 文档校对
└── 3.1.0 版本发布
```

---

> **核心理念**：工具类模块的价值在于"能力储备 + 标准化实现"。优化方向是**让业务方更方便地找到和使用合适的工具**，而非缩小能力范围。
