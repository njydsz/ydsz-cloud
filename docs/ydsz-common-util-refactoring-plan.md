# ydsz-common-util 模块重构方案

> 设计哲学：工具类要做最好，同时预测未来
> 核心原则：补齐短板、统一风格、预留演进空间，不做简单删减

---

## 一、加密模块统一化（Crypto Module Unification）

### 1.1 现状分析

当前存在两套非对称加密 + 三套对称加密 + 三套散列算法，API 风格不一致：

| 算法类 | 密钥输入格式 | 密文输出格式 | IV 管理 | ThreadLocal 池化 |
|--------|-------------|-------------|---------|-----------------|
| `AesUtils` / `AesGcmCrypto` | Hex 字符串 | Hex 字符串 | 内部随机 | Yes |
| `Sm2Utils` | byte[] / KeyObject | Base64 | N/A | Yes |
| `Sm4Utils` | Hex 字符串 | Base64 | 内部随机(GCM) / 外部传入(CBC) | Yes |
| `Sm3Utils` | byte[] / String | Hex / Base64 | N/A | Yes |
| `DigestUtils` | byte[] / InputStream | Hex / Base64 | N/A |(ThreadLocal buffer) |

核心问题：**同一项目中，AES 和 SM4 的调用方需要记忆不同的 API 约定**。

### 1.2 目标架构：CryptoProvider 策略接口

```
┌─────────────────────────────────────────────────────┐
│                   CryptoProvider                     │
│  + encrypt(plaintext, key, aad?) → ciphertext       │
│  + decrypt(ciphertext, key, aad?) → plaintext       │
│  + algorithm() → Algorithm                          │
└─────────────────────────────────────────────────────┘
         ↑                    ↑                    ↑
    AesGcmProvider       Sm4GcmProvider       Sm4CbcProvider
```

### 1.3 具体重构步骤

#### Step 1: 新建 `CryptoProvider` 接口

```java
package com.njydstd.common.util.security.crypto;

/**
 * 对称加密算法统一契约。
 *
 * <p>所有对称加密实现（AES-GCM、SM4-GCM、SM4-CBC）都遵循此接口，
 * 上层业务通过 {@code CryptoProviderRegistry.get(algorithm)} 获取实例，
 * 切换算法时业务代码无需任何改动。
 *
 * <p><b>密文格式统一：</b>Base64(IV || ciphertext || tag)
 *
 * @since 3.0.0
 */
public interface CryptoProvider {

    /**
     * 算法标识（如 "AES-GCM"、"SM4-GCM"）
     */
    String algorithm();

    /**
     * 默认密钥长度（字节）
     */
    int keyLength();

    /**
     * 默认 IV 长度（字节）
     */
    int ivLength();

    /**
     * 生成密码学安全的随机密钥
     *
     * @return 密钥字节数组
     */
    byte[] generateKey();

    /**
     * 生成密码学安全的随机 IV
     *
     * @return IV 字节数组
     */
    byte[] generateIv();

    /**
     * 加密
     *
     * @param plaintext 明文（UTF-8 编码）；不可为 null
     * @param key       密钥字节数组；长度必须等于 {@link #keyLength()}
     * @param aad       可选的附加认证数据（AEAD 模式下用于完整性校验），不需要时传 null
     * @return Base64 编码密文（IV + ciphertext + tag）
     */
    byte[] encrypt(byte[] plaintext, byte[] key, byte[] aad);

    /**
     * 解密
     *
     * @param ciphertext Base64 编码密文（IV + ciphertext + tag）；不可为 null
     * @param key        密钥字节数组
     * @param aad        附加认证数据（必须与加密时一致），无 aad 时传 null
     * @return 明文字节数组
     */
    byte[] decrypt(byte[] ciphertext, byte[] key, byte[] aad);
}
```

#### Step 2: 新建 `AesGcmCryptoProvider`（替代现有 AesGcmCrypto）

```java
package com.njydstd.common.util.security.crypto;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;

/**
 * AES-GCM 128/256 加密提供者。
 *
 * <p>实现 {@link CryptoProvider} 统一契约。内部使用 ThreadLocal Cipher 池化
 * 避免 Provider 查找开销。支持 128 位和 256 位密钥。
 *
 * <p><b>密文格式：</b>Base64(IV(12 bytes) || ciphertext+GCM tag(16 bytes))
 *
 * @since 3.0.0
 */
public final class AesGcmCryptoProvider implements CryptoProvider {

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int IV_LENGTH = 12;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private static final ThreadLocal<Cipher> CIPHER_POOL = ThreadLocal.withInitial(() -> {
        try {
            return Cipher.getInstance(TRANSFORM);
        } catch (Exception e) {
            throw new IllegalStateException("AES/GCM not available", e);
        }
    });

    private final int keyLength;

    public AesGcmCryptoProvider(int keyBits) {
        if (keyBits != 128 && keyBits != 256) {
            throw new IllegalArgumentException("AES key must be 128 or 256 bits");
        }
        this.keyLength = keyBits / 8;
    }

    public AesGcmCryptoProvider() {
        this(256);
    }

    @Override
    public String algorithm() {
        return "AES-" + (keyLength * 8) + "-GCM";
    }

    @Override
    public int keyLength() {
        return keyLength;
    }

    @Override
    public int ivLength() {
        return IV_LENGTH;
    }

    @Override
    public byte[] generateKey() {
        byte[] key = new byte[keyLength];
        SECURE_RANDOM.nextBytes(key);
        return key;
    }

    @Override
    public byte[] generateIv() {
        byte[] iv = new byte[IV_LENGTH];
        SECURE_RANDOM.nextBytes(iv);
        return iv;
    }

    @Override
    public byte[] encrypt(byte[] plaintext, byte[] key, byte[] aad) {
        byte[] iv = generateIv();
        try {
            Cipher cipher = CIPHER_POOL.get();
            cipher.init(Cipher.ENCRYPT_MODE,
                    new SecretKeySpec(key, ALGORITHM),
                    new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            if (aad != null) {
                cipher.updateAAD(aad);
            }
            byte[] ciphertext = cipher.doFinal(plaintext);

            // IV(12) + ciphertext+tag
            return ByteBuffer.allocate(iv.length + ciphertext.length).put(iv).put(ciphertext).array();
        } catch (Exception e) {
            throw new IllegalStateException("AES-GCM encryption failed", e);
        }
    }

    @Override
    public byte[] decrypt(byte[] ciphertext, byte[] key, byte[] aad) {
        // 从头部提取 IV
        byte[] iv = new byte[IV_LENGTH];
        System.arraycopy(ciphertext, 0, iv, 0, IV_LENGTH);
        int ctLen = ciphertext.length - IV_LENGTH;
        byte[] ct = new byte[ctLen];
        System.arraycopy(ciphertext, IV_LENGTH, ct, 0, ctLen);

        try {
            Cipher cipher = CIPHER_POOL.get();
            cipher.init(Cipher.DECRYPT_MODE,
                    new SecretKeySpec(key, ALGORITHM),
                    new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            if (aad != null) {
                cipher.updateAAD(aad);
            }
            return cipher.doFinal(ct);
        } catch (Exception e) {
            throw new IllegalStateException("AES-GCM decryption failed", e);
        }
    }
}
```

#### Step 3: 新建 `Sm4GcmCryptoProvider`（替代 SM4 GCM 部分）

```java
package com.njydstd.common.util.security.crypto;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

/**
 * SM4-GCM 加密提供者。国密合规场景使用。
 * 实现与 AesGcmCryptoProvider 完全一致的 API 契约。
 *
 * @since 3.0.0
 */
public final class Sm4GcmCryptoProvider implements CryptoProvider {
    private static final String ALGORITHM = "SM4";
    private static final String TRANSFORM = "SM4/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int IV_LENGTH = 12;
    private static final int KEY_LENGTH = 16;
    /* ... ThreadLocal<Cipher> 同 AesGcmCryptoProvider 模式 ... */

    @Override
    public String algorithm() { return "SM4-GCM"; }

    @Override
    public int keyLength() { return KEY_LENGTH; }

    // ... 所有方法委派给统一 JCA Cipher 调用
}
```

#### Step 4: 新建 `CryptoProviderRegistry`（算法路由）

```java
package com.njydstd.common.util.security.crypto;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 加密算法注册表——统一的加密入口。
 *
 * <p>业务代码通过以下方式实现算法无关性：
 * <pre>{@code
 *   // 配置项决定使用 AES 还是 SM4，业务代码无需 if-else
 *   String algorithm = config.getCryptoAlgorithm(); // "AES-256-GCM" 或 "SM4-GCM"
 *   CryptoProvider provider = CryptoProviderRegistry.get(algorithm);
 *   byte[] ct = provider.encrypt(plaintext, key, null);
 * }</pre>
 *
 * @since 3.0.0
 */
public final class CryptoProviderRegistry {

    private static final Map<String, CryptoProvider> REGISTRY = new ConcurrentHashMap<>();

    static {
        // JDK 自带 AES，始终可用
        register(new AesGcmCryptoProvider(128));
        register(new AesGcmCryptoProvider(256));
        // BC 国密，按需注册
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) != null) {
            register(new Sm4GcmCryptoProvider());
            register(new Sm4CbcCryptoProvider());
        }
    }

    private CryptoProviderRegistry() {}

    public static void register(CryptoProvider provider) {
        REGISTRY.put(provider.algorithm(), provider);
    }

    public static CryptoProvider get(String algorithm) {
        CryptoProvider provider = REGISTRY.get(algorithm);
        if (provider == null) {
            throw new IllegalArgumentException("Unsupported crypto algorithm: " + algorithm
                    + ". Available: " + REGISTRY.keySet());
        }
        return provider;
    }
}
```

#### Step 5: 新增 `CryptoUtils`（面向业务的高层 API）

```java
package com.njydstd.common.util.security.crypto;

import java.util.Base64;
import java.util.HexFormat;

/**
 * 业务加密工具类——项目中所有加密操作的唯一入口。
 *
 * <p>封装 base64/hex 编解码 + 算法路由，业务方只需：
 * <pre>{@code
 *   // 加密
 *   String ciphertext = CryptoUtils.encrypt("Hello",\Base64.getDecoder().decode(keyB64));
 *   // 解密
 *   String plaintext = CryptoUtils.decrypt(ciphertext, Base64.getDecoder().decode(keyB64));
 * }</pre>
 *
 * <h2>算法选择</h2>
 * <p>通过系统属性 {@code crypto.algorithm} 配置，默认 AES-256-GCM。
 * 国密合规系统可配置为 {@code SM4-GCM}。
 *
 * @since 3.0.0
 */
public final class CryptoUtils {

    private static final HexFormat HEX = HexFormat.of();
    private static volatile CryptoProvider DEFAULT_PROVIDER;

    private CryptoUtils() {}

    /**
     * 使用默认算法加密（Base64 密钥输入 → Base64 密文输出）
     */
    public static String encrypt(String plaintext, byte[] key) {
        byte[] ciphertext = provider().encrypt(
                plaintext.getBytes(StandardCharsets.UTF_8), key, null);
        return Base64.getEncoder().encodeToString(ciphertext);
    }

    /**
     * 使用默认算法解密
     */
    public static String decrypt(String base64Ciphertext, byte[] key) {
        byte[] ciphertext = Base64.getDecoder().decode(base64Ciphertext);
        byte[] plaintext = provider().decrypt(ciphertext, key, null);
        return new String(plaintext, StandardCharsets.UTF_8);
    }

    /**
     * 使用指定 AAD 的 AEAD 加密（用于带上下文的加密场景）
     */
    public static String encryptWithAad(String plaintext, byte[] key, byte[] aad) {
        byte[] ciphertext = provider().encrypt(
                plaintext.getBytes(StandardCharsets.UTF_8), key, aad);
        return Base64.getEncoder().encodeToString(ciphertext);
    }

    /**
     * Hex 编码加密
     */
    public static String encryptHex(String plaintext, String hexKey) {
        return HEX.formatHex(Base64.getDecoder().decode(
                encrypt(plaintext, HEX.parseHex(hexKey))));
    }

    // ... decryptHex / decryptWithAad 等对称方法

    private static CryptoProvider provider() {
        if (DEFAULT_PROVIDER == null) {
            String algo = System.getProperty("crypto.algorithm", "AES-256-GCM");
            DEFAULT_PROVIDER = CryptoProviderRegistry.get(algo);
        }
        return DEFAULT_PROVIDER;
    }

    /**
     * 强制指定算法（用于非默认算法场景，显式控制）
     */
    public static CryptoProvider provider(String algorithm) {
        return CryptoProviderRegistry.get(algorithm);
    }
}
```

### 1.4 迁移路径

| 阶段 | 动作 | 兼容性 |
|------|------|--------|
| Phase 1 | 新增 `crypto` 包下所有新类，现有 `AesUtils`/`Sm2Utils`/`Sm4Utils` 保持不变 | 100% 向下兼容 |
| Phase 2 | 旧类 Javadoc 标注 `@deprecated`，指向 `CryptoUtils` | 编译期 warning |
| Phase 3 | 旧类内部实现改为委派给 `CryptoProviderRegistry`（行为不变） | 无行为差异 |
| Phase 4 | 下下个大版本移除旧类 | 最终清理 |

### 1.5 国密合规扩展点

预测未来需求，`CryptoProvider` 支持以下扩展方式：

```java
// 后量子密码算法（预测未来 2-3 年）
CryptoProviderRegistry.register(new KyberKemProvider());

// 硬件加密模块 HSM（银行/政务场景）
CryptoProviderRegistry.register(new HsmAesProvider(slot));

// KMS 远程加密（多租户密钥隔离场景）
CryptoProviderRegistry.register(new KmsRemoteProvider(kmsClient, keyId));
```

---

## 二、线程池可观测性（Thread Pool Observability）

### 2.1 现状分析

`ExecutorUtils` 当前只负责构造线程池，缺乏治理能力。大厂线上排障三大刚需无法覆盖：

1. **运行时指标监控**（活跃线程数、队列堆积、拒绝次数）
2. **异常溯源**（任务执行失败的线程栈）
3. **优雅停机**（Spring 关闭时等待在途任务）

### 2.2 目标架构

```
┌─────────────────────────────────────────────────────────┐
│              ExecutorUtils.newCpuBoundPool()             │
│                         ↓                                │
│            new MeteredThreadPoolExecutor(...)            │
│                         ↓                                │
│              Micrometer MeterRegistry                    │
│        (active.threads/pool.queue.size/rejected.count)   │
└─────────────────────────────────────────────────────────┘
```

### 2.3 具体重构：新增 `MeteredThreadPoolExecutor`

```java
package com.njydstd.common.util.concurrent;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 可观测线程池执行器——Micrometer 指标自动注册。
 *
 * <p>在 {@link ThreadPoolExecutor} 基础上自动注册以下指标：
 * <ul>
 *   <li>{@code executor.active.threads} — 当前执行任务的线程数</li>
 *   <li>{@code executor.pool.size} — 当前线程池大小</li>
 *   <li>{@code executor.queue.remaining} — 剩余队列容量</li>
 *   <li>{@code executor.queue.size} — 当前队列堆积量</li>
 *   <li>{@code executor.completed.tasks} — 累计完成任务数</li>
 *   <li>{@code executor.rejected.count} — 累计拒绝任务数</li>
 *   <li>{@code executor.task.duration} — 任务执行耗时分布</li>
 * </ul>
 *
 * <p>Tag 体系：{@code pool.name=critical-path}、{pool.type=cpu-bound}
 *
 * <p><b>预期未来能力（预留 Hook）：</b>
 * <ul>
 *   <li>慢任务告警（耗时 > threshold 时输出 warn 日志 + metrics）</li>
 *   <li>队列积压告警（队列深度 > highWaterMark 时触发回调）</li>
 *   <li>线程泄漏检测（任务超预期时间未完成时 dump 线程栈）</li>
 * </ul>
 *
 * @since 3.0.0
 */
public class MeteredThreadPoolExecutor extends ThreadPoolExecutor {

    private final String poolName;
    private final MeterRegistry meterRegistry;
    private final AtomicLong rejectedCount = new AtomicLong();
    private final Timer taskTimer;
    private final Counter failedTasks;

    // 预留慢任务检测 Hook（默认不启用）
    private volatile long slowTaskThresholdMs = Long.MAX_VALUE;

    public MeteredThreadPoolExecutor(String poolName,
                                      int corePoolSize,
                                      int maximumPoolSize,
                                      long keepAliveTime,
                                      TimeUnit unit,
                                      BlockingQueue<Runnable> workQueue,
                                      ThreadFactory threadFactory,
                                      RejectedExecutionHandler handler,
                                      MeterRegistry meterRegistry) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit,
              adaptQueue(workQueue, meterRegistry, poolName),
              threadFactory, wrapHandler(handler, poolName));
        this.poolName = poolName;
        this.meterRegistry = meterRegistry;
        this.taskTimer = Timer.builder("executor.task.duration")
                .tag("pool.name", poolName)
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry);
        this.failedTasks = Counter.builder("executor.failed.tasks")
                .tag("pool.name", poolName)
                .register(meterRegistry);

        // 注册瞬时指标
        Gauge.builder("executor.active.threads", this, ThreadPoolExecutor::getActiveCount)
                .tag("pool.name", poolName).register(meterRegistry);
        Gauge.builder("executor.pool.size", this, p -> p.getPoolSize())
                .tag("pool.name", poolName).register(meterRegistry);
        Gauge.builder("executor.queue.size", this, p -> p.getQueue().size())
                .tag("pool.name", poolName).register(meterRegistry);
        Gauge.builder("executor.completed.tasks", this, p -> p.getCompletedTaskCount())
                .tag("pool.name", poolName).register(meterRegistry);
    }

    /**
     * 启用慢任务检测（超过阈值时输出 warn 日志）
     * <p>Hook 预留：未来可接入告警系统
     */
    public void enableSlowTaskDetection(long thresholdMs) {
        this.slowTaskThresholdMs = thresholdMs;
    }

    @Override
    public void execute(Runnable command) {
        super.execute(new MeteredTask(command));
    }

    @Override
    protected void afterExecute(Runnable r, Throwable t) {
        super.afterExecute(r, t);
        if (t != null && r instanceof MeteredTask mt) {
            failedTasks.increment();
            // 预留 Hook：未来可触发告警回调
            onTaskFailed(mt.task(), t);
        }
    }

    /**
     * 任务执行失败时的回调——子类可覆写以接入告警系统
     */
    protected void onTaskFailed(Runnable originalTask, Throwable cause) {
        // 预留 Hook，默认空实现
    }

    private class MeteredTask implements Runnable {
        private final Runnable delegate;
        private final long startTime;

        MeteredTask(Runnable delegate) {
            this.delegate = delegate;
            this.startTime = System.nanoTime();
        }

        Runnable task() { return delegate; }

        @Override
        public void run() {
            try {
                delegate.run();
            } finally {
                long elapsed = System.nanoTime() - startTime;
                taskTimer.record(elapsed, TimeUnit.NANOSECONDS);
                if (elapsed > slowTaskThresholdMs * 1_000_000L) {
                    logSlowTask(elapsed);
                }
            }
        }
    }
}
```

### 2.4 改造 `ExecutorUtils` 集成可观测性

```java
// 改造后的 ExecutorUtils 方法签名
public final class ExecutorUtils {

    /**
     * 创建 CPU 密集型线程池（Micrometer 自动注册指标）
     *
     * @param poolName 线程池名称（用于区分不同业务池，也是 metrics tag 的 pool.name 值）
     * @param registry Micrometer 全局 Registry（推荐传入 Metrics.globalRegistry）
     */
    public static MeteredThreadPoolExecutor newCpuBoundPool(String poolName, MeterRegistry registry) {
        int cores = Runtime.getRuntime().availableProcessors();
        return new MeteredThreadPoolExecutor(
                poolName,
                cores,
                cores + 1,
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(1024),
                new NamedThreadFactory(poolName),
                new BlockingPolicy(),  // 自定义拒绝策略：阻塞调用者 1s 再拒绝
                registry
        );
    }
}
```

### 2.5 虚拟线程有界调度器（面向 JDK 21+ 预测未来）

```java
/**
 * 有界虚拟线程调度器——解决无节制创建虚拟线程导致的背压问题。
 *
 * <p>JDK 21 的 {@code Executors.newVirtualThreadPerTaskExecutor()} 会为每任务创建虚拟线程，
 * IO 密集型场景下虚拟线程数量可能失控。本调度器在虚拟线程之上叠加有界并发控制，
 * 在保持虚拟线程轻量优势的同时提供背压保障。
 *
 * <p><b>预留未来能力：</b>Semaphore 替换为虚拟线程感知的 BlockingScheduler。
 *
 * @since 3.0.0
 */
public final class BoundedVirtualThreadScheduler {

    private final Semaphore concurrencyLimiter;
    private final ThreadFactory threadFactory;
    private volatile boolean shutdown;

    public BoundedVirtualThreadScheduler(int maxConcurrency) {
        this.concurrencyLimiter = new Semaphore(maxConcurrency);
        this.threadFactory = Thread.ofVirtual().factory();
    }

    /**
     * 提交任务，超过并发上限时阻塞提交方（背压传导）。
     */
    public void submit(Runnable task) throws InterruptedException {
        if (shutdown) throw new RejectedExecutionException("Scheduler is shut down");
        concurrencyLimiter.acquire();
        threadFactory.newThread(() -> {
            try {
                task.run();
            } finally {
                concurrencyLimiter.release();
            }
        }).start();
    }
}
```

### 2.6 Prometheus + Grafana 监控大盘

配合 Micrometer，自然导出以下指标，可构建标准化监控面板：

```
# 线程池黄金三指标
executor_active_threads{pool_name="order-process"}    8
executor_queue_size{pool_name="order-process"}         23
executor_rejected_count_total{pool_name="order-process"} 0

# 告警规则（PromQL）
executor_queue_size > 500        # 队列堆积告警
rate(executor_failed_tasks_total[5m]) > 0.01  # 错误率上升
```

---

## 三、WorkerId 生成 SPI 完成度

### 3.1 现状与问题

| 问题 | 影响 |
|------|------|
| `WorkerIdRegistry` 接口无默认实现 | 任何业务需用雪花ID时都必须自行实现 SPI，无法开箱即用 |
| `@Deprecated heartbeat/release` 未彻底删除 | 接口契约混乱，暗示曾有注册中心协调方案但未完成 |
| `leaseMillis` 配置无续约机制 | 配置项形同虚设 |
| `SnowflakeIdGenerator` 构造器依赖 `ObjectProvider<WorkerIdRegistry>` | 增加不必要的 Spring DI 复杂度 |

### 3.2 目标：WorkerId 自动感知链

```
优先级 1: Pod Ordinal 模式（K8s StatefulSet）
    ↓ 环境变量 HOSTNAME 匹配模式 ^(.*)-(\d+)$
    ↓ 提取 ordinal 作为 workerId
优先级 2: IP 末段哈希模式
    ↓ InetAddress.getLocalHost() → hash → workerId (0-1023)
优先级 3: 时钟回拨保护模式（workerId 冲突风险最低）
    ↓ 使用随机 workerId + 漂移检测
兜底：本地缓存文件（~/.{app}/workerId）
```

### 3.3 重构步骤

#### Step 1: 简化 WorkerIdRegistry 接口

```java
package com.njydstd.common.util.id;

/**
 * WorkerId 分配策略——负责为当前实例分配唯一 workerId（0 ≤ id < 1024）。
 *
 * <p>实现为 SPI 扩展点：K8s 环境使用 Pod Ordinal、虚拟机环境使用 IP 哈希、
 * 开发环境使用本地文件缓存。
 *
 * <p><b>新增约定：</b>所有实现必须保证同一集群内 workerId 全局唯一，
 * 非唯一可能导致 ID 冲突。
 *
 * @since 3.0.0（从 WorkerIdRegistry 重命名）
 */
public interface WorkerIdAllocator {

    /**
     * 分配 workerId。
     *
     * @param nodeId 当前节点标识（通常为 hostname 或 pod name），用于日志和调试
     * @return 分配到的 workerId（0-1023）
     * @throws WorkerIdExhaustedException 当无法分配唯一 workerId 时
     */
    int allocate(String nodeId);

    /**
     * 策略名称（用于日志和监控）
     */
    default String name() {
        return getClass().getSimpleName();
    }
}
```

#### Step 2: 新增 `PodOrdinalWorkerIdAllocator`（K8s 环境默认）

```java
/**
 * 基于 K8s StatefulSet Pod 序号的 WorkerId 分配器。
 *
 * <p>依赖环境变量 HOSTNAME 符合 StatefulSet 命名模式：{@code <statefulset-name>-<ordinal>}。
 * 如 HOSTNAME={@code order-service-0} → workerId=0。
 *
 * <p>自动感知 Pod 重启后序号不变（StatefulSet 保证），保证 workerId 幂等。
 *
 * <p><b>自动检测：</b>仅在 HOSTNAME 匹配模式时启用，否则跳过让位给下个策略。
 *
 * @since 3.0.0
 */
public final class PodOrdinalWorkerIdAllocator implements WorkerIdAllocator {

    private static final Pattern STATEFULSET_PATTERN = Pattern.compile("^(.*)-(\\d+)$");

    @Override
    public int allocate(String nodeId) {
        String hostname = System.getenv("HOSTNAME");
        if (hostname == null) hostname = System.getenv("POD_NAME");
        if (hostname == null) hostname = hostname();

        Matcher m = STATEFULSET_PATTERN.matcher(hostname);
        if (!m.matches()) {
            throw new NotApplicableException("Hostname not match StatefulSet pattern: " + hostname);
        }

        int ordinal = Integer.parseInt(m.group(2));
        if (ordinal >= 1024) {
            throw new WorkerIdExhaustedException("Pod ordinal " + ordinal + " exceeds max workerId 1023");
        }
        return ordinal;
    }

    @Override
    public String name() {
        return {
    }
}
```

#### Step 3: 新增 `IpHashWorkerIdAllocator`（虚拟机/裸机默认）

```java
/**
 * 基于 IP 哈希的 WorkerId 分配器——IP 末段对 1023 取模。
 *
 * <p>同一子网下各节点 IP 末段不同，workerId 大概率唯一。
 * 适用于虚拟机部署、开发机等非 K8s 环境。
 *
 * @since 3.0.0
 */
public final class IpHashWorkerIdAllocator implements WorkerIdAllocator {

    @Override
    public int allocate(String nodeId) {
        try {
            InetAddress localHost = InetAddress.getLocalHost();
            byte[] addr = localHost.getAddress();
            // 取末段对 1023 取模（保证 0-1023）
            int lastOctet = addr[addr.length - 1] & 0xFF;
            return lastOctet % 1024;
        } catch (UnknownHostException e) {
            throw new WorkerIdExhaustedException("Cannot resolve local IP", e);
        }
    }

    @Override
    public String name() {
        return "IpHash";
    }
}
```

#### Step 4: 新增 `CompositeWorkerIdAllocator`（策略链）

```java
/**
 * WorkerId 分配策略链——按优先级尝试各策略，首个成功即返回。
 *
 * <p>内置策略链：PodOrdinal → IpHash → RandomFilePersisted。
 *
 * <p>业务方可通过 {@code WorkerIdAllocatorChain.prepend()} 插入自定义策略。
 *
 * @since 3.0.0
 */
public final class WorkerIdAllocatorChain implements WorkerIdAllocator {

    private final List<WorkerIdAllocator> chain;

    public WorkerIdAllocatorChain(List<WorkerIdAllocator> allocators) {
        this.chain = List.copyOf(allocators);
    }

    public static WorkerIdAllocatorChain defaults() {
        return new WorkerIdAllocatorChain(List.of(
                new PodOrdinalWorkerIdAllocator(),
                new IpHashWorkerIdAllocator(),
                new FilePersistedWorkerIdAllocator()
        ));
    }

    @Override
    public int allocate(String nodeId) {
        for (WorkerIdAllocator allocator : chain) {
            try {
                int id = allocator.allocate(nodeId);
                log.info("WorkerId={} allocated by {}", id, allocator.name());
                return id;
            } catch (NotApplicableException e) {
                log.debug("WorkerIdAllocator {} not applicable: {}", allocator.name(), e.getMessage());
            }
        }
        throw new WorkerIdExhaustedException("No WorkerIdAllocator succeeded");
    }
}
```

#### Step 5: 简化 SnowflakeIdGenerator 构造器

```java
// 改造前
public SnowflakeIdGenerator(@Nullable ObjectProvider<WorkerIdRegistry> registry, WorkerIdConfig config) {
    this.workerId = ... // 复杂的 ObjectProvider 解析 + SPI fallback
}

// 改造后
public SnowflakeIdGenerator(WorkerIdAllocator allocator, String nodeId, int datacenterId) {
    this.workerId = allocator.allocate(nodeId);
    this.datacenterId = datacenterId;
    // ...
}

// 默认构造（自动选择策略链）
public SnowflakeIdGenerator(String nodeId, int datacenterId) {
    this(WorkerIdAllocatorChain.defaults(), nodeId, datacenterId);
}
```

#### Step 6: 改造 Properties

```java
// SnowflakeProperties 中
@ConfigurationProperties(prefix = "ydsz.snowflake")
public class SnowflakeProperties {
    private boolean enabled = true;
    private int datacenterId = 0;
    private String nodeId = "";  // 默认取 HOSTNAME
    // 删除 leaseMillis（不再需要续约机制）
}
```

### 3.4 扩展能力预留

```java
// 未来可扩展：注册中心模式（如 etcd/Redis/Nacos）
public class RedisWorkerIdAllocator implements WorkerIdAllocator {
    // Script: SET workerId:{nodeId} XX PX 30000  // 带 TTL 续约
}

// 未来可扩展：Snowflake-plus（Twitter Snowflake 变体）
// 支持 workerId 10bit + datacenterId 4bit + sequence 12bit = 每毫秒 262144 ID
```

---

## 四、Bean 映射增强（MapUtils.toBean Completion）

### 4.1 现状：能力不足

当前 `MapUtils.toBean` 缺少以下企业级能力：

| 缺失能力 | 影响 |
|---------|------|
| 泛型字段（`List<SubBean>`） | 集合字段只能逐个转换，无法级联 |
| `Optional<T>` 字段 | 无法自动解包 |
| `LocalDateTime` / `BigDecimal` / `UUID` 等常见 JDK 类型 | 需人工补充 |
| `@JsonProperty` 注解映射 | 字段名与 key 不一致时无法映射 |
| 不可变对象（Record / 全参构造器） | 只能无参构造 + setter |
| 循环引用检测 | Map 有自引用时 StackOverflow |

### 4.2 目标：有取舍地增强

**设计取舍**：不追求覆盖所有场景（那是 Jackson/Fastjson2 的职责），而是成为"轻量 Map→Bean 快捷工具"，覆盖 80% 常规映射场景，复杂场景明确导向 JSON convert。

### 4.3 重构步骤

#### Step 1: 扩展 `MapUtils.toBean` 支持泛型

```java
/**
 * 从 Map 重建 Bean，支持嵌套泛型（List<SubBean>、Map<String, SubBean>）。
 *
 * <p>使用示例：
 * <pre>{@code
 *   // 基本场景
 *   User user = MapUtils.toBean(map, User.class);
 *
 *   // 泛型场景：借助 TypeReference
 *   List<Order> orders = MapUtils.convert(genericMap, new TypeReference<List<Order>>() {});
 *
 *   // Optional 字段支持
 *   Profile profile = MapUtils.toBean(map, Profile.class); // Optional<Address> 自动解包
 *
 *   // 不可变对象（Record）支持
 *   Point point = MapUtils.toBean(map, Point.class); // Point(double x, double y) 全参构造
 * }</pre>
 *
 * <p><b>明确不覆盖的能力（应使用 JSON convert）：</b>
 * <ul>
 *   <li>深度递归嵌套（>3 层）— 性能差于 JSON convert</li>
 *   <li>复杂泛型（Map<String, List<Map<String, Object>>>) — 类型擦除后无法推断</li>
 *   <li>自定义反序列化器（@JsonDeserialize）— 本工具不解析注解</li>
 * </ul>
 *
 * @param map   源 Map，key 为 String，value 可为基本类型/Map/List
 * @param clazz 目标类型
 * @throws IllegalArgumentException 当转换失败且无法降级时
 */
public static <T> T toBean(Map<String, Object> map, Class<T> clazz) {
    if (map == null) return null;
    if (clazz.isRecord()) {
        return instantiateRecord(map, clazz);
    }
    T instance = createInstance(clazz);
    for (Field field : getAllFields(clazz)) {
        Object value = map.get(field.getName());
        if (value == null) value = map.get(camelToSnake(field.getName()));
        if (value != null) {
            Object converted = convertValue(value, field.getGenericType());
            setField(instance, field, converted);
        }
    }
    return instance;
}
```

#### Step 2: 新增泛型 TypeReference 支持

```java
/**
 * 泛型类型引用——用于捕获参数化类型信息。
 *
 * <p>用法：
 * <pre>{@code
 *   List<User> users = MapUtils.convert(map.getList("users"), new TypeReference<List<User>>() {});
 * }</pre>
 */
public abstract class TypeReference<T> {
    private final Type type;

    protected TypeReference() {
        Type superClass = getClass().getGenericSuperclass();
        this.type = ((ParameterizedType) superClass).getActualTypeArguments()[0];
    }

    public Type getType() { return type; }

    @SuppressWarnings("unchecked")
    public Class<T> getRawType() {
        if (type instanceof Class<?> c) return (Class<T>) c;
        if (type instanceof ParameterizedType pt) return (Class<T>) pt.getRawType();
        return (Class<T>) Object.class;
    }
}
```

#### Step 3: Record 支持

```java
private static <T> T instantiateRecord(Map<String, Object> map, Class<T> recordClass) {
    RecordComponent[] components = recordClass.getRecordComponents();
    Class<?>[] paramTypes = new Class[components.length];
    Object[] args = new Object[components.length];

    for (int i = 0; i < components.length; i++) {
        paramTypes[i] = components[i].getType();
        String name = components[i].getName();
        Object rawValue = map.get(name);
        if (rawValue == null) rawValue = map.get(camelToSnake(name));
        args[i] = rawValue != null ? convertValue(rawValue, components[i].getGenericType()) : null;
    }

    try {
        Constructor<T> constructor = recordClass.getDeclaredConstructor(paramTypes);
        return constructor.newInstance(args);
    } catch (Exception e) {
        throw new IllegalArgumentException("Cannot create record " + recordClass.getSimpleName(), e);
    }
}
```

#### Step 4: 基本类型转换增强

```java
// 扩展 JAVA_TIME_TYPES 和常见业务类型
private static final Map<Class<?>, Function<Object, Object>> CONVERTERS = Map.ofEntries(
    // 已有基础类型...
    Map.entry(BigDecimal.class, v -> new BigDecimal(v.toString())),
    Map.entry(UUID.class, v -> UUID.fromString(v.toString())),
    Map.entry(LocalDateTime.class, v -> parseDateTime(v)),
    Map.entry(LocalDate.class, v -> LocalDate.parse(v.toString(), DATE_FORMATTER)),
    Map.entry(LocalTime.class, v -> LocalTime.parse(v.toString(), TIME_FORMATTER)),
    Map.entry(Instant.class, v -> Instant.parse(v.toString())),
    Map.entry(Duration.class, v -> Duration.parse(v.toString())),
    Map.entry(YearMonth.class, v -> YearMonth.parse(v.toString()))
);

// 支持 Enum 自动转换
private static Object convertEnum(Object value, Class<?> targetType) {
    if (targetType.isEnum()) {
        if (value instanceofNumber n) {
            // 按 ordinal
            Object[] constants = targetType.getEnumConstants();
            int idx = n.intValue();
            if (idx >= 0 && idx < constants.length) return constants[idx];
        }
        // 按 name
        String strVal = value.toString();
        for (Object c : targetType.getConstants()) {
            if (((Enum<?>) c).name().equalsIgnoreCase(strVal)) return c;
        }
        // 按 @JsonValue / toString
        return Enum.valueOf((Class<Enum>) targetType, strVal.toUpperCase());
    }
    return value;
}
```

#### Step 5: Optional 字段自动解包

```java
// 在 setField 中处理 Optional 字段
private static void setField(Object instance, Field field, Object value) throws Exception {
    field.setAccessible(true);
    Class<?> fieldType = field.getType();
    if (Optional.class.isAssignableFrom(fieldType)) {
        field.set(instance, Optional.ofNullable(value));
    } else {
        field.set(instance, value);
    }
}
```

#### Step 6: 能力边界文档化

```java
/**
 * <h2>能力边界与 JSON convert 选择指南</h2>
 *
 * <table border="1">
 *   <tr><th>场景</th><th>推荐使用</th><th>原因</th></tr>
 *   <tr><td>平坦 Map → POJO</td><td>MapUtils.toBean()</td><td>无需引入 JSON 依赖，零 overhead</td></tr>
 *   <tr><td>多层嵌套（&gt;3 层）</td><td>JSON.toJavaObject()</td><td>JSON 库做了路径缓存，性能更优</td></tr>
 *   <tr><td>复杂泛型（Map&lt;String, List&lt;...&gt;&gt;）</td><td>TypeReference + JSON</td><td></td></tr>
 *   <tr><td>需要自定义注解</td><td>JSON</td><td>完整支持 Jackson/fastjson2 注解</td></tr>
 *   <tr><td>Record 对象</td><td>MapUtils.toBean()</td><td>Record 必走全参构造</td></tr>
 * </table>
 */
```

### 4.4 性能保障：循环引用检测

```java
public static <T> T toBean(Map<String, Object> map, Class<T> clazz) {
    return toBean(map, clazz, new IdentityHashMap<>());
}

private static <T> T toBean(Map<String, Object> map, Class<T> clazz,
                              IdentityHashMap<Object, Object> visited) {
    if (map == null) return null;
    if (visited.containsKey(map)) {
        throw new IllegalArgumentException("Circular reference detected in Map");
    }
    visited.put(map, null);
    // ... 正常转换逻辑
    // 递归调用时传递 visited
}
```

---

## 五、JDK 新特性支持（Future-Ready）

### 5.1 结构化并发（JDK 21+ Structured Concurrency）

```java
package com.njydstd.common.util.concurrent;

import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.StructuredTaskScope.Subtask;

/**
 * JDK 21 结构化并发工具——解决多子任务并发编排问题。
 *
 * <p>传统方案的问题：
 * <ul>
 *   <li>{@code CompletableFuture.allOf(...)} 不能自动取消兄弟任务</li>
 *   <li>{@code ExecutorService.invokeAll()} 不支持子任务作用域隔离</li>
 *   <li>某个子任务 OOM/死锁时难以诊断</li>
 * </ul>
 *
 * <p><b>预测未来场景：</b>
 * <ul>
 *   <li>编排多个数据源并行查询（任何失败取消其他）</li>
 *   <li>并行调用多个 AI Agent（首个成功即返回）</li>
 *   <li>批量请求拆分子任务并汇总</li>
 * </ul>
 *
 * <p>使用示例：
 * <pre>{@code
 *   // 场景 1: 所有子任务成功才视为成功，任一失败则全部取消
 *   try (var scope = Structured ConcurrencyScope.allSuccess()) {
 *     Subtask<User> userTask = scope.fork(() -> userService.getById(id));
 *     Subtask<List<Order>> orderTask = scope.fork(()::getOrders(id));
 *     scope.join();
 *     scope.throwIfFailed();
 *     return new UserProfile(userTask.get(), orderTask.get());
 *   }
 *
 *   // 场景 2: 首个成功即返回，其他取消
 *   try (var scope = StructuredConcurrencyScope.firstSuccess()) {
 *     for (DataSource ds : dataSources) {
 *       scope.fork(() -> ds.query(params));
 *     }
 *     return scope.join().throwIfFailed().result();
 *   }
 * }</pre>
 *
 * @since 3.0.0（依赖 JDK 21+）
 */
public final class StructuredConcurrencyScopes {

    private StructuredConcurrencyScopes() {}

    /**
     * "所有子任务必须成功"模式。
     */
    public static StructuredTaskScope.ShutdownOnFailure allSuccess() {
        return new StructuredTaskScope.ShutdownOnFailure();
    }

    /**
     * "首个成功即返回"模式。
     */
    public static StructuredTaskScope.ShutdownOnSuccess<Object> firstSuccess() {
        return new StructuredTaskScope.ShutdownOnSuccess<>();
    }
}
```

### 5.2 ScopedValue 替代 ThreadLocal

```java
package com.njydstd.common.util.concurrent;

/**
 * ScopedValue 工具——在虚拟线程场景下替代 TransmissibleThreadLocal。
 *
 * <p>TransmittableThreadLocal 的问题：
 * <ul>
 *   <li>虚拟线程场景下 ThreadLocal 变量会在线程池复用中泄漏</li>
 *   <li>内存泄漏风险：忘记 remove() 时 ThreadLocalMap 持续膨胀</li>
 * </ul>
 *
 * <p>ScopedValue 优势：
 * <ul>
 *   <li>作用域限定（withScope 内可见，出作用域自动清除）</li>
 *   <li>与 StructuredConcurrency 天然集成</li>
 *   <li>零泄漏（GC 友好）</li>
 * </ul>
 *
 * <p>预测未来：JDK 21+ 迁移方向。
 *
 * @since 3.0.0
 */
public final class ScopedValues {

    /** 当前请求范围内的 traceId */
    public static final ScopedValue<String> TRACE_ID = ScopedValue.newInstance();

    /** 当前请求范围内的操作者 userId */
    public static final ScopedValue<String> OPERATOR_ID = ScopedValue.newInstance();

    /**
     * 在 ScopedValue 作用域内执行任务。
     *
     * <p>等价于 try-with-resources 模式，出作用域自动清除所有绑定值。
     */
    public static <T> T runWhere(ScopedValue.Bindings<T> bindings, Callable<T> task) {
        return ScopedValue.runWhere(bindings, task);
    }
}
```

### 5.3 集合流式操作增强（SequencedCollection）

```java
/**
 * JDK 21 SequencedCollection 兼容工具。
 *
 * <p>JDK 21 引入 SequencedCollection / SequencedSet / SequencedMap，
 * 统一了 reversed() / getFirst() / getLast() API。
 *
 * <p>本项目工具类在 JDK 21+ 直接委托给原生 API，
 * JDK 17 下提供兼容实现。
 *
 * @since 3.0.0
 */
public final class SequencedCollections {

    /**
     * 获取首元素（JDK 21+ 委托原生 API）。
     */
    @SuppressWarnings("unchecked")
    public static <T> Optional<T> first(Collection<T> coll) {
        if (coll instanceof SequencedCollection<T> sc) {
            return sc.isEmpty() ? Optional.empty() : Optional.of(sc.getFirst());
        }
        // JDK 17 fallback
        return CollectionUtils.findFirst(coll);
    }

    /**
     * 获取末元素。
     */
    @SuppressWarnings("unchecked")
    public static <T> Optional<T> last(Collection<T> coll) {
        if (coll instanceof SequencedCollection<T> sc) {
            return sc.isEmpty() ? Optional.empty() : Optional.of(sc.getLast());
        }
        // JDK 17 fallback
        return CollectionUtils.findLast(coll);
    }
}
```

---

## 六、通用基础设施加固

### 6.1 API 风格一致性检查清单

| 规则 | 现状问题 | 修复动作 |
|------|---------|---------|
| 非空参数统一使用 `Objects.requireNonNull` | Sm2Utils 用 `@NonNull` 注解 + 手工 null 检查 | 全部改为 `Objects.requireNonNull(param, "param must not be null")` |
| 异常统一使用 `IllegalStateException` / `IllegalArgumentException` | `DigestUtils` 使用 RuntimeException 直接包装 | 对齐异常类型规范 |
| 输入输出编码统一为 byte[] + 显式 charset | Sm3Utils 混用 UTF-8 硬编码和 StandardCharsets.UTF_8 | 统一 `StandardCharsets.UTF_8` |
| Builder 模式统一使用 Lombok `@Builder` | `ExecutorUtils.ThreadPoolBuilder` 手写 | 评估是否替换为 Lombok Builder |
| 流式 API 返回不可变集合 | `CollectionUtils.convertList` 返回可变 ArrayList | 提供 `convertListImmutable` 变体 |

### 6.2 文档规范化（预测未来 AI 辅助编程）

```java
/**
 * 类级 Javadoc 统一增加 AI-friendly 标签。
 *
 * <p>预测未来：AI 编码助手（如 GitHub Copilot、CatPaw AI）会根据 Javadoc
 * 的 `@ai.intent` 标签理解工具类用途，从而推荐正确的 Utils 方法。
 */

/**
 * AES-GCM 对称加密工具——项目统一的字符串加密入口。
 *
 * @ai.intent "加密字符串" "AES加密" "密码存储加密" "字段级加密"
 * @ai.example "CryptoUtils.encrypt(secret, key)" → "AQIDBA..."
 * @ai.anti-example "md5(text)" → "密码存储严禁使用 MD5"
 * @ai.severity "security" —— 安全相关，使用时触发额外 review 建议
 */
```

### 6.3 ArchUnit 规则约束（架构守护）

```java
/**
 * 模块间依赖约束测试——确保工具类的层级不被破坏。
 *
 * <p>预测未来：随着代码库增长，reverse dependency 容易被无意引入。
 * ArchUnit 测试在 CI 中守护架构边界。
 */
@AnalyzeClasses(packages = "com.njydsz.common.util")
public class UtilArchitectureTest {

    // 规则 1: util 模块不依赖 auth 业务模块
    @ArchTest
    static final ArchRule util_should_not_depend_on_auth =
            noClasses().that().resideInAPackage("..util..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("..auth..");

    // 规则 2: security 工具类不直接 import 业务 POJO
    @ArchTest
    static final ArchRule security_should_be_domain_free =
            noClasses().that().resideInAPackage("..security..")
                    .should().dependOnClassesThat()
                    .haveNameMatching(".*User$");

    // 规则 3: 所有 Utils 类必须是 final + 私有构造器
    @ArchTest
    static final ArchRule utils_classes_are_utility =
            classes().that().haveSimpleNameEndingWith("Utils")
                    .should().bePrivate()
                    .orShould().beFinal();

    // 规则 4: 加密算法实现必须通过 CryptoProvider 注册
    @ArchTest
    static final ArchRule crypto_provider_must_be_registerped =
            classes().that().implement(CryptoProvider.class)
                    .should().beAnnotatedWith("RegisteredThroughRegistry");
}
```

---

## 七、执行路线图

### Phase 1（1-4 周）：加密统一化 + SPI 完成

- [ ] 新建 `crypto` 包（CryptoProvider + 实现类 + Registry + CryptoUtils）
- [ ] 新建 `WorkerIdAllocator` 接口 + PodOrdinal/IpHash/FilePersisted 实现
- [ ] 简化 `SnowflakeIdGenerator` 构造器
- [ ] `ArchUnit` 基础规则建立

### Phase 2（3-5 周）：Bean 映射增强 + 线程池可观测性

- [ ] `MapUtils.toBean` 泛型/Record/Optional 增强
- [ ] `MeteredThreadPoolExecutor` 实现 + Micrometer 集成
- [ ] Prometheus 监控面板模板

### Phase 3（5-8 周）：JDK 新特性支持 + API 统一化

- [ ] `StructuredConcurrencyScopes` + `ScopedValues`
- [ ] `SequencedCollections` 兼容实现
- [ ] 旧 API 标记 @Deprecated + 委派到新 API
- [ ] 监控大盘 + 告警规则标准化

### Phase 4（持续）：AI-Friendly 文档 + 架构守护升级

- [ ] 全模块 `@ai.intent` / `@ai.example` 标签补齐
- [ ] ArchUnit 规则覆盖率 > 90%
- [ ] 后量子密码算法预留接口（预测 3-5 年后场景）

---

## 八、收益预估（vs 简单删减策略）

| 指标 | 简单删减策略 | 本重构方案 |
|------|-------------|-----------|
| 代码行数 | -30% | +15%（新增能力） |
| 覆盖场景 | 减少（删减后能力缺口扩大） | 扩大 3-5 倍（泛型/国密/监控/虚拟线程） |
| 迁移成本 | 高（需大规模替换调用方） | 低（增量添加、渐进迁移） |
| 未来适应性 | 持续删减导致工具库萎缩 | 可扩展架构支撑 5 年演进 |
| 开发者心智负担 | 不确定该用哪个类 | 统一入口 + AI-friendly 文档 |
| 线上可观测性 | 无变化 | Micrometer 指标 + 告警全覆盖 |
| 国密合规能力 | 无变化 | 一键切换算法 + CryptoProvider 扩展 |
