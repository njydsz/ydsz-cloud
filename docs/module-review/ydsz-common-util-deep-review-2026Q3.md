# ydsz-common-util 深度审查报告（2026 Q3 增量版）

> 分析基准：最新代码（2026-08-15，61 个主类 / 15 个子包）
> 关联文档：`docs/ydsz-common-util-optimization-report.md`（以下简称"基线报告"）
> 对标对象：NIST SP 800-38D、OWASP Crypto Guidelines、《密码 GM/T 系列标准》、Resilience4j、Guava、Hutool、ArchUnit 治理实践、美团 Leaf

---

## 0. 本报告定位

基线报告（`docs/ydsz-common-util-optimization-report.md`）已覆盖了模块的主要分析维度与落地路线图。本次增量审查基于最新全量代码扫描，侧重三个目标：

1. **修正基线报告中已实施的改进项**（BeanMapper、RateLimiter、RetryUtils 部分能力已补齐）
2. **补充基线报告未覆盖的新深度发现**（crypto 子包 API 契约违反、密钥擦除缺失、IdGenerator DCL 并发细节、concurrent 包内部质量重评）
3. **更新优先级路线图**（按最新代码状态重新排序）

---

## 一、对基线报告的修正

### 1.1 BeanMapper — LambdaMetafactory 已实施

基线报告 P1-1 建议"用 LambdaMetafactory 生成 setter 调用句柄"。**最新代码已实施该方案**：

- 核心结构：`SETTER_INVOKER_CACHE`（`Method → BiConsumer<Object,Object>` 的 Lambda 句柄缓存）
- 实现路径：`createSetterInvoker(Method setter)` 内部通过 `LambdaMetafactory` 将反射 setter 转为直接调用，性能已接近原生 setter
- 原有建议的"提升一个数量级"目标已实现，JMH 基准建议保留（防回退）

**残留问题**（基线报告 P1-1 顺带修复项）：
- `convertValue` 仍未处理 `Map<K,V>` 泛型字段（只处理了 `List`）——setter 路径与 record 路径行为不一致
- 86 行尾部填充空行未清理

### 1.2 RateLimiter — setRate 已具备

基线报告 F3 建议"补 setRate"。**最新代码的 `RateLimiter` 已提供 `setRate(double newPermitsPerSecond)` 方法**，使用 `volatile intervalNanos` 原子替换。

**残留能力缺口**：
- 不支持动态修改桶容量（仅可调速率）
- 基线报告 P2-4 建议的"全局 ReentrantLock 热点"优化（CAS 化）仍有价值，但优先级下调——单机令牌桶在绝大多数业务场景已足够

### 1.3 RetryUtils — maxDuration / 回调已具备

基线报告 F3 建议"补 maxDuration、事件回调"。**最新代码的 `RetryConfig` 已包含 `maxDuration`、`onRetry`（`Consumer<Integer>`）字段**，且指数退避已实现 jitter（`ThreadLocalRandom`）。

**残留能力缺口**：
- 缺 `onSuccess` / `onError` 终态回调
- 缺基于 `CompletableFuture` 的异步重试
- 缺 `RetryConfig` 的 `retryOn(Predicate)` 之外的"结果重试"（`retryOnResult(Predicate<T>)`）

---

## 二、新增深度发现

### 2.1 🔴 Sm4CbcCryptoProvider AAD 参数静默忽略 — API 契约违反

**发现**：`Sm4CbcCryptoProvider` 实现 `CryptoProvider` 接口，接口契约要求 `encrypt(byte[], byte[], byte[] aad)` 与 `decrypt(byte[], byte[], byte[] aad)` 支持 AEAD Associated Data。但 CBC 模式本身不支持 AAD，当前实现选择**完全忽略该参数**，且不抛异常、不打日志。

**后果**：调用方调用 `CryptoUtils.encryptWithAad("数据", key, userId.getBytes())` 选择 SM4-CBC 时，AAD 完全不生效——密文与上下文**未绑定**，串用攻击无感知。这是典型的"接口参数静默失效"，违反 fail-fast 原则。

**严重度**：高（安全契约违反）。

**建议**（二选一）：
1. **推荐**：在 `Sm4CbcCryptoProvider.encrypt/decrypt` 中，当 `aad != null && aad.length > 0` 时抛出 `UnsupportedOperationException("SM4-CBC does not support AAD; use SM4-GCM instead")`，强制调用方感知
2. 或从 `CryptoProvider` 接口移除 `aad` 参数，拆分 `AeadCryptoProvider` 与 `SymmetricCryptoProvider` 两个接口

### 2.2 🔴 密钥擦除系统性缺失

**发现**：所有加密 Provider（AesGcmCryptoProvider、Sm4GcmCryptoProvider、Sm4CbcCryptoProvider）以及 Sm2Utils 内部，密钥 / 私钥使用后**均未显式擦除**：

| 类 | 密钥类型 | 擦除状态 |
|---|---|---|
| AesGcmCryptoProvider | `SecretKeySpec` + `byte[] key` | 不清零 |
| Sm4GcmCryptoProvider | `SecretKeySpec` + `byte[] key` | 不清零 |
| Sm4CbcCryptoProvider | `SecretKeySpec` + `byte[] key` | 不清零 |
| Sm2Utils | `PrivateKey`（BC 实现） | 不清零 |
| DigestUtils | `byte[]` 缓冲区 | `Arrays.fill` 清零（唯一合规） |

**原因分析**：`byte[] key` 是方法传入的引用，Provider 内部无法确定调用方是否仍需使用，贸然清零可能破坏调用方逻辑。`SecretKeySpec` 对象本身未实现 `Destroyable` 接口（JDK 标准限制）。

**建议**：
- 文档层面：在 `CryptoProvider` 接口 Javadoc 中明确"调用方负责密钥生命周期管理，使用后请自行 `Arrays.fill(key, (byte) 0)` 清零"
- API 层面：提供 `CryptoUtils.destroyKey(byte[] key)` 工具方法（内部 `Arrays.fill`），便于调用方一键清零
- 高安全场景：可考虑使用 `Destroyable` 密钥包装器 + `try-with-resources` 模式

### 2.3 🟡 DigestUtils 自研迭代哈希方法暴露 — 密码学误用风险

**发现**：`DigestUtils.digest(byte[] input, String algorithm, byte[] salt, int iterations)` 公开暴露自研迭代哈希逻辑。代码注释明确标注"非标准 PBKDF2/bcrypt/scrypt，不可用于密码存储"，但方法签名是 `public static`。

**风险**：业务方看到 `digest(input, algorithm, salt, iterations)` 签名时，直觉判断这是标准 KDF，可能误用于密码存储——尤其是不了解密码学细节的开发者。

**建议**：
- 方案一（保守）：重命名为 `digestIterativeRaw` 并在方法名中体现"非标准"
- 方案二（严格）：访问降级为包级私有（`static` → 移入具体使用处），外部调用方改用标准 `pbkdf2()` 方法
- 方案三（教育）：Javadoc 顶部增加 ⚠️ 警告块 + 链接到 OWASP Password Storage Cheat Sheet

### 2.4 🟡 IdGenerator 的 DCL 并发细节分析

**发现**：`IdGenerator.getGenerator()` 使用标准的 DCL（Double-Checked Locking）模式：

```java
private static volatile SnowflakeIdGenerator cached;
private static volatile long lastFailureMillis;
// ...
private static SnowflakeIdGenerator getGenerator() {
    SnowflakeIdGenerator gen = cached;
    if (gen != null) { return gen; }
    Supplier<SnowflakeIdGenerator> supplier = generatorSupplier;
    if (supplier == null) { warnFallback("..."); return null; }
    long now = System.currentTimeMillis();
    if (now - lastFailureMillis < FAILURE_COOLDOWN_MILLIS) { return null; }
    synchronized (IdGenerator.class) {
        gen = cached;
        if (gen != null) { return gen; }
        if (System.currentTimeMillis() - lastFailureMillis < FAILURE_COOLDOWN_MILLIS) { return null; }
        try {
            gen = supplier.get();
            if (gen == null) { warnFallback(...); return null; }
            cached = gen;
            return gen;
        } catch (Exception e) {
            lastFailureMillis = System.currentTimeMillis();
            log.warn("...", e.getMessage());
            return null;
        }
    }
}
```

**并发安全性评估**：✅ 正确。`volatile` 保证 `cached` 的 happens-before 语义；`synchronized` 保证原子性；冷却期避免频繁 getBean。

**增量发现**：`warnFallback` 方法每次降级都打日志，在高频调用无 Bean 的场景（如 gateway）会产生**日志风暴**。基线报告已提到这一点，补充建议：
- `warnFallback` 增加 60s 去冷却（与 `FAILURE_COOLDOWN_MILLIS` 对齐）
- 或使用 `log.warn` + `AtomicBoolean firstFlag` 保证首次仅打一次

### 2.5 🟡 Cipher/Signature ThreadLocal 池化的健壮性

**发现**：多个加密类使用 `ThreadLocal<Cipher>` / `ThreadLocal<Signature>` 做池化：

| 类 | 池化对象 | 处理方式 |
|---|---|---|
| AesGcmCryptoProvider | `ThreadLocal<Cipher>` | ✅ 每次 `cipher.init()` 重置状态 |
| Sm4GcmCryptoProvider | `ThreadLocal<Cipher>` | ✅ 同上 |
| Sm4CbcCryptoProvider | `ThreadLocal<Cipher>` | ✅ 同上 |
| Sm2Utils | `ThreadLocal<Cipher>` + `ThreadLocal<Signature>` | ✅ 每次 `init/initSign` 重置 |

**安全性**：✅ 正确。JCA Cipher 每次 `init()` 会重置内部状态，ThreadLocal 复用仅避免重复创建开销。但需注意：
- JDK 21 虚拟线程场景下 ThreadLocal 的内存占用问题（虚拟线程数量极大时）
- 建议未来评估 `Cipher` 是否为轻量级对象（部分 JCA Provider 内部维护大量状态，池化收益低；BC 的 SM2/SM4 Cipher 相对轻量）

### 2.6 🟢 SnowflakeIdGenerator CAS 算法重评

**发现**：基线报告已评价"质量高"。增量代码扫描确认其 CAS 实现的健壮性：

```java
public long nextId() {
    for (;;) {
        long currentState = state.get();           // 单字段原子读取 timestamp+sequence
        long lastTimestamp = extractTimestamp(currentState);
        long lastSequence = extractSequence(currentState);
        long timestamp = resolveTimestamp(lastTimestamp);
        long sequence;
        if (timestamp == lastTimestamp) {
            sequence = lastSequence + 1;
            if (sequence > sequenceMask) {
                timestamp = tilNextMillis(lastTimestamp);  // 等待下一毫秒
                sequence = 0;
            }
        } else {
            sequence = 0;
        }
        long nextState = packState(timestamp, sequence);
        if (state.compareAndSet(currentState, nextState)) {  // CAS 提交
            return composeId(timestamp, sequence);
        }  // CAS 失败重试
    }
}
```

**设计精妙点**：
- 单 `AtomicLong state` 打包 timestamp + sequence，**一次 CAS 完成双字段原子更新**（避免多字段同步）
- `packState` / `extractTimestamp` / `extractSequence` 纯位运算，无锁、无等待
- 时钟回拨使用 `LockSupport.parkNanos()`（非忙等，节省 CPU）

**与美团 Leaf / 百度 UidGenerator 对标**：
| 维度 | ydsz Snowflake | 美团 Leaf-snowflake | 百度 UidGenerator |
|---|---|---|---|
| WorkerId 分配 | PodOrdinal+IpHash 链 | DB + 心跳续期 | IP 注册 + 过期 |
| 时钟回拨容忍 | 5ms 内 park 等待 | 5ms 内拒绝 | 5ms 内拒绝 |
| 序列号位数 | 可配 7-13 | 固定 10 | 可配 |
| ID 反解析 | ✅ 内置 | ❌ | ✅ |
| 健康检查 | ✅ | ❌ | ❌ |

**结论**：ydsz 实现质量达到开源领先水平；唯一缺失的 WorkerId 分布式协调已在基线报告 F1 中作为 P0 列出。

### 2.7 🟢 concurrent 包内部质量被基线报告低估

基线报告将 `RateLimiter 0 采用、RetryUtils 0 采用`列为"过度设计"候选。增量代码扫描后需要**重评其内部质量**——"零采用"可能反映的是推广不足，而非工具本身质量差：

| 类 | 代码质量评估 | 对标 |
|---|---|---|
| **ExecutorUtils** | ⭐⭐⭐⭐ Builder + 工厂混合，20+ 方法覆盖全面；但 builder 内部队列类型枚举扩展性好 | 优于 Hutool ThreadUtil |
| **MeteredThreadPoolExecutor** | ⭐⭐⭐⭐⭐ 模板方法 Hook（onTaskFailed/onSlowTask）设计优雅；Micrometer 集成规范 | 对标 Micrometer Gauge 最佳实践 |
| **BoundedVirtualThreadScheduler** | ⭐⭐⭐ Semaphore 背压经典模式；但 JDK 21 原生 Semaphore + Executors 可替代 | 基线报告 OD2 建议下线结论保留 |
| **RateLimiter** | ⭐⭐⭐⭐ 令牌桶标准实现；ReentrantLock + Condition 精确纳秒等待；支持动态调速率 | 性能不及 Guava RateLimiter 的无锁算法，但功能更全 |
| **RetryUtils** | ⭐⭐⭐⭐ Builder + 指数退避 + 抖动 + 时长上限 + 条件判断；缺异步版 | 功能覆盖 Resilience4j Retry 70% |

**结论**：ExecutorUtils、MeteredThreadPoolExecutor、RateLimiter、RetryUtils 质量优良，建议**加强推广而非下线**。BoundedVirtualThreadScheduler 可下线。

---

## 三、按子包的完整质量矩阵

| 子包 | 主类数 | 核心职责 | 内部质量 | 采用率 | 评价 |
|---|---|---|---|---|---|
| **id** | 13 | Snowflake + WorkerId 策略 + Trace | ⭐⭐⭐⭐⭐ | 高（97+ 处） | 模块第一公民，CAS 实现优秀 |
| **security** | 5 | SHA/HMAC/PBKDF2/SM2/SM3 | ⭐⭐⭐⭐ | 中（12 处） | ThreadLocal 池化合理，密钥擦除缺失 |
| **security/crypto** | 6 | AES-GCM/SM4-GCM/SM4-CBC Provider | ⭐⭐⭐⭐ | 零 | SPI 设计优雅，SM4-CBC 的 AAD 有契约违反 |
| **concurrent** | 5 | 线程池/限流/重试/调度 | ⭐⭐⭐⭐ | 零 | 质量优于基线报告预期，建议推广 |
| **http** | 7 | Servlet/URL/Response/Token | ⭐⭐⭐ | 中 | 功能完备但缺 HTTP Client 封装 |
| **ip** | 3 | IPv4/IPv6/CIDR/网卡 | ⭐⭐⭐⭐ | 中（1 处） | 正则预编译合理，缓存 LRU 化待实施 |
| **string** | 1 | 判空/转换/截断 | ⭐⭐⭐⭐ | 高（49 处） | 克制设计、零冗余 |
| **collection** | 3 | 判空/SequencedCollection | ⭐⭐⭐ | 中（17 处） | SequencedCollections 建议下线 |
| **bean** | 2 | Map→Bean/Record | ⭐⭐⭐⭐ | 中 | LambdaMetafactory 已实施 |
| **password** | 3 | BCrypt/PBKDF2/强度校验 | ⭐⭐⭐⭐ | 零 | OWASP 合规，SPI 略过度 |
| **auth** | 3 | 认证上下文读取 | ⭐⭐⭐ | 中（8 处） | 业务语义过重，建议迁出 |
| **validate** | 1 | 业务校验正则集 | ⭐⭐⭐ | 零 | 算法正确，覆盖面可扩 |
| **mask** | 1 | 数据脱敏 | ⭐⭐⭐ | 零 | 硬编码脱敏位数，缺注解集成 |
| **date** | 1 | java.time 封装 | ⭐⭐⭐ | 零 | 节假日感知缺失 |
| **io** | 1 | 文件操作封装 | ⭐⭐⭐⭐ | 零 | UncheckedIOException 处理正确 |
| **yaml** | 1 | JSON↔YAML 互转 | ⭐⭐ | 零 | SnakeYAML 反序列化 RCE 风险 |
| **message** | 1 | i18n MessageSource | ⭐⭐⭐ | 中（2 处） | DCL 非线程安全、Locale 覆盖窄 |

---

## 四、更新后的优先级路线图

### P0（本迭代，正确性/安全收口）

| # | 事项 | 来源 | 验证方式 |
|---|---|---|---|
| 1 | Sm4CbcCryptoProvider AAD 抛异常或拆分接口 | 本次 §2.1 | 编译期 + 集成测试调用方感知 |
| 2 | IpHash 全 IP 哈希 + Redis/DB WorkerId 分配器内置 | 基线报告 F1 | 双实例同 workerId 启动被拒绝 |
| 3 | Snowflake 注册统一走 AutoConfiguration.imports；gateway 静默降级打 WARN | 基线报告 A1 | gateway actuator/health 出现 snowflake 指标 |
| 4 | 密钥擦除 CryptoUtils.destroyKey + 接口文档明确生命周期 | 本次 §2.2 | 文档审查 |
| 5 | DigestUtils 自研迭代哈希方法重命名/访问控制 | 本次 §2.3 | 无新外部调用方引用 |

### P1（下个迭代，能力补齐与质量提升）

| # | 事项 | 来源 |
|---|---|---|
| 1 | BeanMapper Map 泛型字段支持 + 尾部空行清理 | 本次 §1.1 |
| 2 | RateLimiter CAS 化（降低锁热点）| 基线报告 P2-4 |
| 3 | MeteredThreadPoolExecutor 预缓存 Timer/Counter | 基线报告 P1-2 |
| 4 | CidrUtils LRU 化（替代 1024 全清）| 基线报告 P1-3 |
| 5 | concurrent 包推广：common-base 模板层示范采用 ExecutorUtils + Metered | 本次 §2.7 |
| 6 | TracerUtils W3C traceparent 编解码 | 基线报告 F5 |
| 7 | RetryUtils 异步版 + 结果重试策略 | 本次 §1.3 |
| 8 | DateUtils 增强（互转/时区/HolidayCalendar）| 基线报告 F2 |
| 9 | ValidationUtils/MaskUtils 补齐 + @Mask 注解化 | 基线报告 F4 |
| 10 | 核心类补单测（RateLimiter 并发语义、RetryUtils 退避数学、ValidationUtils 校验位）| 基线报告 E2 |
| 11 | 命名转换 API 收敛去重（StringUtils vs BeanMapper vs MapUtils）| 基线报告 A3 |

### P2（长期治理）

| # | 事项 | 来源 |
|---|---|---|
| 1 | BoundedVirtualThreadScheduler / SequencedCollections / Sm4CbcCryptoProvider(@Deprecated) 下线 | 基线报告 OD2 |
| 2 | util 去业务化：AuthInfoUtils/MessageUtils 迁往 common-web 或新建 common-context | 基线报告 A5 |
| 3 | 零采用 API 季度盘点 → 试用/稳定/废弃标注机制 | 基线报告 OD1 |
| 4 | crypto 配置收敛至 `ydsz.util.crypto.default-algorithm` | 基线报告 F6 |
| 5 | 静态桥接模式统一（StaticBridge）；IdGenerator/ServletRequestUtils/MessageUtils 复用 | 基线报告 A2 |
| 6 | 双 Bean 转换体系（BeanMapper vs YdszJson）分工定版 | 基线报告 OD3 |

---

## 五、关键证据位置（增量）

| 发现 | 文件位置 |
|---|---|
| AAD 静默忽略 | `security/crypto/Sm4CbcCryptoProvider.java#encrypt`（`aad` 参数未使用） |
| AAD 静默忽略 | `security/crypto/Sm4CbcCryptoProvider.java#decrypt`（`aad` 参数未使用） |
| 密钥擦除缺失 | `security/crypto/AesGcmCryptoProvider.java#encrypt`（`key` 传入后不清零） |
| 自研哈希暴露 | `security/DigestUtils.java#digest(byte[],String,byte[],int)` |
| IdGenerator DCL | `id/IdGenerator.java#getGenerator()` |
| Snowflake CAS | `id/SnowflakeIdGenerator.java#nextId()` |
| Cipher ThreadLocal 池 | `security/crypto/AesGcmCryptoProvider.java#CIPHER_POOL` |
| Builder Hook 模板方法 | `concurrent/MeteredThreadPoolExecutor.java#onTaskFailed#onSlowTask` |
| IdGenerator warnFallback 去冷 | `id/IdGenerator.java#warnFallback`（每次调用都打日志） |

---

## 六、总结

ydsz-common-util 模块在基线报告之后的演进中已主动实施了多项改进（BeanMapper LambdaMetafactory、RateLimiter setRate、RetryUtils maxDuration），体现了良好的治理响应速度。

本次增量审查新增的 **P0 事项 3 项**（AAD 契约违反、密钥擦除文档规范、自研哈希访问控制）与基线报告原有的 **P0 事项** 共同构成当前迭代的正确性/安全收口重点。

值得基调性调整的是：**concurrent 包的内部质量显著优于"零采用=过度设计"的线性推断**——其精细的 Builder、模板方法 Hook、Jitter 退避、有界并发等设计，体现的是对标 Resilience4j 的产品意识。建议治理方向从"下线"调整为"推广+补全（异步重试、CAS 化限流器）"。
