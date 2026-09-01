# ydsz-common-util

> YDSZ 通用工具类库（L1 工具层）— 覆盖 ID 生成、加密/国密、HTTP、字符串、集合/Map、IP、并发、认证上下文、国际化消息等领域。

> **注意**: 自 1.0.0 起，线程池创建与监控能力（`ExecutorUtils`、`MeteredThreadPoolExecutor`）已迁移至 `ydsz-common-thread` 模块；
> 限流能力已收敛至 `ydsz-common-safe` 的 `TokenBucketLimiter`（本模块重复实现已删除）；
> 本模块并发包仅保留 `RetryUtils`（轻量重试，@Experimental 能力储备——平台级熔断/重试标准为 Resilience4j，见根 pom 说明）。

---

## 快速开始

### 1. 引入依赖

```xml
<dependency>
    <groupId>com.njydsz</groupId>
    <artifactId>ydsz-common-util</artifactId>
</dependency>
```

### 2. 基础使用

```java
// 生成分布式 ID
long id = IdGenerator.nextId();

// AES-GCM 加密（Base64 输出）
String ciphertext = CryptoUtils.encrypt("敏感数据", keyBytes);
String plaintext = CryptoUtils.decrypt(ciphertext, keyBytes);

// 密码哈希（BCrypt）
String hashed = PwdUtils.hashPasswordBCrypt("userPassword123");
boolean valid = PwdUtils.verifyPasswordBCrypt("userPassword123", hashed);

// IP 校验
boolean ok = IpValidator.validIpv4("192.168.1.1");
boolean internal = IpValidator.isInternalIp("10.0.0.1");
```

### 3. 启用 Snowflake 自动配置

```yaml
ydsz:
  util:
    snowflake:
      enabled: true                 # 默认启用
      worker-id: 1                  # 可选：显式指定（0-1023，最高优先级）
      datacenter-id: 0              # 可选：数据中心 ID（0-31）
      epoch: 1577836800000          # 可选：起始纪元（默认 2020-01-01 UTC）
```

### 4. 选择加密算法

```java
// 通过系统属性切换算法（默认 AES-256-GCM）
System.setProperty("crypto.algorithm", "SM4-GCM");

// 或运行时指定算法
CryptoProvider sm4 = CryptoUtils.provider("SM4-GCM");
byte[] ciphertext = sm4.encrypt(plaintextBytes, keyBytes, null);
```

---

## 能力地图

### 按场景查找工具

| 业务场景 | 推荐入口类 | 一句话说明 |
|---------|-----------|-----------|
| 生成分布式唯一 ID | `IdGenerator` / `SnowflakeIdGenerator` | 雪花算法核心 + 静态门面，序列号可配置（`SnowflakeIdBean` 已 @Deprecated，由自动配置替代） |
| 加密解密（统一入口） | `CryptoUtils` | AES-256-GCM / SM4-GCM 算法路由，支持 AEAD/AAD 与密钥标识 API（KeyProvider SPI） |
| 国密算法（SM2/SM3） | `Sm2Utils` / `Sm3Utils` | 依赖 bcprov-jdk18on，GM/T 标准；SM4 由 `CryptoProvider` 提供（SM4-GCM） |
| 摘要/签名/HMAC | `DigestUtils` | SHA-256/SHA-512/HMAC-SHA256/PBKDF2 + constantTimeEquals |
| Hex 编解码 | `HexUtils` | 字节数组 ↔ Hex 字符串安全转换（JDK `HexFormat`），替代各自实现的 `bytesToHex` / `hexToBytes` |
| 密码哈希与强度校验 | `PwdUtils` | BCrypt + PBKDF2 + 密码强度评分 |
| HTTP 请求解析 | `ServletRequestUtils` | Servlet 请求封装 |
| 响应渲染 | `HttpResponseUtils` | 基于 YdszJson 序列化的响应写入 |
| Token 提取 | `HttpTokenUtils` | Bearer Token 提取与前缀剥离 |
| 当前请求上下文 | `RequestContextUtils` | 从 Spring RequestContextHolder 获取当前请求/响应 |
| URL 白名单匹配（轻量） | `UrlPathUtils` | 单次线性匹配，Ant 风格 |
| URL 白名单匹配（高性能） | `UrlPathMatcher` | 构建一次复用多次，精确 O(1) + 通配符 |
| 可信代理判定 | `TrustedProxyConfiguration` | 防止 X-Forwarded-For 伪造，内网地址始终可信 |
| 限流 | `ydsz-common-safe` 的 `TokenBucketLimiter` | 已收敛至 safe 模块（本模块重复实现已删除，避免双实现漂移） |
| 重试 | `RetryUtils` | @Experimental 能力储备：固定间隔 + 指数退避（含抖动），重试耗尽抛出 `RetryException`；平台级标准为 Resilience4j |
| 字符串判空/转换/截断 | `StringUtils` | null-safe 判空、驼峰/下划线互转、truncate/abbreviate/normalizeSpace |
| 日期时间 | `DateUtils` | java.time API 封装：日起始/结束、工作日计算、格式化解析 |
| 文件操作 | `FileUtils` | 封装 commons-io：读写、复制、目录操作、扩展名解析 |
| 临时文件管理 | `TempFileManager` | 临时文件跟踪 / TTL 兜底清理 / 优雅停机清理（`AutoCloseable`，由自动配置注册） |
| 数据脱敏 | `MaskUtils` | 手机号/身份证/银行卡/邮箱/姓名掩码 |
| 业务校验 | `ValidationUtils` | 手机号/邮箱/身份证/统一社会信用代码等正则+校验码验证 |
| Bean 映射 | `BeanMapper` | Map → Bean / Record 转换（从 MapUtils 独立） |
| Bean PATCH 更新 | `BeanUpdateUtil` | 仅复制非 null 属性 |
| 集合判空/转换 | `CollectionUtils` / `MapUtils` | null-safe 判空、类型安全取值 |
| JDK 21 SequencedCollection | `SequencedCollections` | reverse/first/last 兼容 JDK 17/21 |
| IP 校验/CIDR | `IpValidator` / `CidrUtils` | IPv4/IPv6 校验、内网判断、CIDR 网段计算 |
| 本机网络接口 | `NetworkInterfaceUtils` | 获取本机 IP、主机名、枚举所有非回环 IP |
| 字段 diff 对比 | `DiffCalculator` | Bean 字段变更对比，生成 `DiffReport`（含 `FieldDiff` 列表） |
| 国际化消息 | `MessageUtils` | MessageSource 便捷读取 |

### 按包结构查找工具

```
com.njydsz.common.util
├── api/            标记注解：@Experimental（实验性 API 标识）
├── bean/           Bean 映射：BeanMapper（Map→Bean/Record）、BeanUpdateUtil（PATCH 语义）
├── collection/     集合工具：CollectionUtils、MapUtils（聚焦 Map 操作）、SequencedCollections
├── concurrent/     并发工具：RetryUtils（重试，@Experimental 储备）、RetryException
├── config/         自动配置：UtilAutoConfiguration、MessageSourceConfiguration、
│                   CryptoAutoConfiguration、StaticBridge（静态工具→Spring Bean 桥接器）
├── date/           日期时间：DateUtils（java.time API 封装，@Experimental 储备）
├── diff/           字段 diff 工具：DiffCalculator、DiffReport、DiffField、FieldDiff、DiffValueFormatter（@Experimental 储备）
├── http/           HTTP 工具：ServletRequestUtils、HttpResponseUtils、HttpTokenUtils、
│                   RequestContextUtils、UrlPathUtils、UrlPathMatcher、TrustedProxyConfiguration、TrustedProxyProperties
├── id/             ID 生成：SnowflakeIdGenerator、IdGenerator、RandomUtils、TracerUtils、
│                   WorkerIdAllocator（SPI）、WorkerIdAllocatorChain、
│                   PodOrdinalWorkerIdAllocator、IpHashWorkerIdAllocator、
│                   SnowflakeProperties、SnowflakeIdBean（@Deprecated）、SnowflakeHealthIndicator、
│                   ClockBackwardException、WorkerIdExhaustedException、NotApplicableException
├── internal/       内部 API：proxy/（CoreConstants、RequestContextProxy、TraceIdGeneratorProxy、ParsedTraceparent）
├── io/             文件操作：FileUtils（封装 commons-io）、TempFileManager、TempFileProperties
├── ip/             IP 工具：IpValidator、CidrUtils（含缓存）、NetworkInterfaceUtils
├── mask/           数据脱敏：MaskUtils（@Experimental 储备）
├── message/        国际化：MessageUtils
├── password/       密码工具：PwdUtils、PasswordStrengthChecker（SPI）、DefaultPasswordStrengthChecker
├── security/       加密工具：CryptoUtils（统一入口）、DigestUtils、HexUtils、
│                   Sm2Utils/Sm3Utils、BcProvider
├── security/crypto/ 加密提供者：CryptoProvider（SPI）、KeyProvider（密钥来源 SPI）、
│                   CryptoProviderRegistry、KeyProviderRegistry、
│                   AesGcmCryptoProvider、Sm4GcmCryptoProvider、CryptoAutoConfiguration、
│                   CryptoProperties、CryptoException
├── string/         字符串：StringUtils
├── validate/       业务校验：ValidationUtils（@Experimental 储备）
```

---

## 能力生命周期状态

工具库保有按需供给的能力储备是健康常态（Guava/Hutool 同样如此），治理目标是让每块能力
**可发现、可信、不重复**。以下状态标注与源码 `@Experimental` 注解同源（2026-09-01 审计基准，
全仓引用数为现状描述而非价值判断）：

| 状态 | 判定标准 | 包/能力 |
|------|---------|---------|
| **稳定** | 有真实调用方 + 有测试锁定 | `id`（全仓 126 引用）、`security`/`crypto`（38，含 KeyProvider SPI）、`string`（46）、`message`（25）、`http`（15）、`collection`（15）、`io`/FileUtils（5）、`ip`（2）、`password`（1） |
| **实验/储备** | @Experimental 标注，当前平台内无消费方；启用前须确认测试覆盖 | `diff`（字段级对比，审计日志场景）、`bean`/BeanMapper（与 common-json 的可替代性待 ADR 确认）、`concurrent`/RetryUtils（平台级标准为 Resilience4j）、`mask`、`validate`、`date` |
| **已收敛/移除** | 平台内重复实现，能力归属唯一化 | `RateLimiter` → `ydsz-common-safe` 的 `TokenBucketLimiter`；线程池 → `ydsz-common-thread` |

**可观测性**（Micrometer，引入 micrometer-core 即自动注册）：

- `ydsz.util.id.degraded` — IdGenerator 降级累计次数（非 0 需告警：降级随机数可能与 Snowflake 主键空间冲突）
- `ydsz.util.tempfile.tracked` — 当前受跟踪临时文件数（持续增长说明泄漏）

---

## 依赖说明

### 零强制三方依赖原则

| 依赖类型 | 依赖 | 说明 |
|---------|------|------|
| **核心依赖**（强制） | JDK 21、slf4j-api、commons-io、jsr305、jakarta.servlet-api（provided） | 编译和运行时必需 |
| **可选依赖**（按需） | ydsz-common-json | JSON 工具（`Optional` 引入，未引入时 JSON 相关方法不可用） |
| | spring-security-crypto | BCrypt 密码哈希（未引入时 PwdUtils 使用 PBKDF2 降级） |
| | bcprov-jdk18on | 国密算法（SM2/SM3/SM4） |
| | spring-web | Servlet 请求/响应工具、URL 路径匹配 |
| | spring-boot-health | Snowflake 健康检查 |
| | spring-boot-autoconfigure | 自动装配类条件注册（`@ConditionalOnClass`） |
| | micrometer-core | 运行指标（`ydsz.util.id.degraded` / `ydsz.util.tempfile.tracked` Gauge） |

> **可选依赖处理**：未引入时，调用对应方法会抛出包含引入指引的 `IllegalStateException`（而非 `NoClassDefFoundError`）。

---

## SPI 扩展点

| SPI 接口 | 用途 | 默认实现 |
|---------|------|---------|
| `WorkerIdAllocator` | 分布式 WorkerId 分配策略（0-1023） | `WorkerIdAllocatorChain` → `PodOrdinalWorkerIdAllocator` → `IpHashWorkerIdAllocator` 链 |
| `PasswordStrengthChecker` | 密码强度评分规则 | `DefaultPasswordStrengthChecker`（长度/多样性/连续重复扣分） |
| `CryptoProvider` | 加密算法提供者 | `AesGcmCryptoProvider`（默认）、`Sm4GcmCryptoProvider` |
| `KeyProvider` | 密钥来源（配置中心/KMS/Vault 收敛入口） | 无（业务方声明 Bean 即由 `CryptoAutoConfiguration` 自动注册） |

> **自定义 SPI 实现**：
> - `WorkerIdAllocator` / `PasswordStrengthChecker`：在 `META-INF/services/` 接口全限定名文件中填写实现类全限定名
> - `CryptoProvider`：通过 `CryptoProviderRegistry` 编程注册
> - `KeyProvider`：声明为 Spring Bean（自动装配注册），或 `KeyProviderRegistry.register()` 手动注册

---

## 配置项

| 配置 | 默认值 | 说明 |
|------|--------|------|
| `ydsz.util.snowflake.enabled` | `true` | 是否启用 Snowflake 自动配置 |
| `ydsz.util.snowflake.worker-id` | - | WorkerId（0-1023，显式配置最高优先级） |
| `ydsz.util.snowflake.datacenter-id` | - | 数据中心 ID（0-31，未配置时依次查系统属性/环境变量、旧版前缀（已弃用）、主机名哈希） |
| `ydsz.util.snowflake.node-id` | - | 节点标识（用于 PodOrdinal 策略与排障） |
| `ydsz.util.snowflake.epoch` | `1577836800000` | 起始纪元时间戳（2020-01-01 UTC），集群内必须一致 |
| `ydsz.util.snowflake.sequence-bits` | `7` | 序列号位数（1-13，决定每毫秒并发能力：7=128/ms，13=8192/ms） |
| `ydsz.util.trusted-proxies` | 空（仅内网/回环可信） | 可信代理 IP 集合；**K8s 集群内必须收敛为入口网关 IP**（所有 Pod IP 均为内网，默认策略下任意 Pod 可伪造 X-Forwarded-For） |
| `ydsz.util.tempfile.retention` | `24h` | 临时文件 TTL 兜底清理阈值 |
| `ydsz.util.tempfile.cleanup-interval` | `10m` | TTL 清理任务执行间隔 |
| `ydsz.util.crypto.default-algorithm` | `AES-256-GCM` | 默认加密算法（系统属性 `crypto.algorithm` 优先级更高） |

**WorkerId 解析优先级**：显式配置 → PodOrdinal → IpHash

---

## 关键能力详解

### CryptoUtils — 统一加密入口

所有加密操作的唯一入口，封装算法路由、Base64/Hex 编解码、AEAD/AAD 支持：

```java
// 基础加解密（Base64）
String ciphertext = CryptoUtils.encrypt("明文", keyBytes);
String plaintext = CryptoUtils.decrypt(ciphertext, keyBytes);

// Hex 编码模式
String hexCipher = CryptoUtils.encryptHex("明文", hexKey);
String plaintext = CryptoUtils.decryptHex(hexCipher, hexKey);

// AEAD：密文绑定上下文（防串用）
String cipher = CryptoUtils.encryptWithAad("数据", keyBytes, userId.getBytes());
String data = CryptoUtils.decryptWithAad(cipher, keyBytes, userId.getBytes());

// 密钥标识 API（KeyProvider SPI，密钥来源收敛）
String cipher = CryptoUtils.encryptWithKeyId("数据", "user-profile-v3");
String data = CryptoUtils.decryptWithKeyId(cipher, "user-profile-v3");

// 密钥来源实现（声明为 Spring Bean 即自动注册）
@Component
public class ConfigCenterKeyProvider implements KeyProvider {
  public byte[] getKey(String keyId) {
    return Base64.getDecoder().decode(configClient.get("crypto.keys." + keyId));
  }
}

// 查看所有可用算法
Set<String> algos = CryptoUtils.availableAlgorithms();
```

### RetryUtils — 重试工具（@Experimental 能力储备）

```java
// 固定间隔重试
String result = RetryUtils.executeWithRetry(() -> httpClient.call(), 3, Duration.ofSeconds(2));

// 指数退避（含抖动）
String data = RetryUtils.executeWithBackoff(() -> externalApi.fetch(),
    RetryConfig.builder()
        .maxRetries(5)
        .initialDelay(Duration.ofMillis(100))
        .maxDelay(Duration.ofSeconds(10))
        .multiplier(2.0)
        .retryOn(e -> e instanceof SocketTimeoutException)
        .build());
```

> 重试耗尽后抛出 `RetryException`（包装最后一次异常），调用方无需强制捕获。

### UrlPathMatcher — 高性能 URL 路径匹配

```java
// 构建一次，复用多次（推荐 @Bean 或 static 字段）
UrlPathMatcher matcher = UrlPathMatcher.of(List.of(
    "/api/public/**", "/login", "/health"
));

// O(1) 精确匹配 + 通配符匹配
boolean allowed = matcher.matches(requestPath);
boolean isExact = matcher.matchesExact(requestPath);
```

### DiffCalculator — 字段变更对比

```java
// 对比两个 Bean 的字段变更
DiffReport report = DiffCalculator.calculate(oldUser, newUser);
for (FieldDiff diff : report.getDiffs()) {
    log.info("字段 {}: {} → {}", diff.getFieldName(), diff.getOldValue(), diff.getNewValue());
}
```

### StaticBridge — 静态工具→Spring Bean 桥接器

```java
// 在静态工具类中声明桥接器
private static final StaticBridge<MessageSource> MESSAGE_SOURCE_BRIDGE = new StaticBridge<>();

// 由 AutoConfiguration 注入 Supplier
MESSAGE_SOURCE_BRIDGE.registerSupplier(() -> applicationContext.getBean(MessageSource.class));

// 在静态方法中安全获取 Bean
MessageSource ms = MESSAGE_SOURCE_BRIDGE.get();
```

> 采用 DCL + `AtomicReference` 缓存成功结果，Supplier 返回 null 或异常时不缓存自动重试。

---

## 安全注意事项

1. **AES 安全**：默认使用 AES-256-GCM（认证加密 AEAD），每次加密生成随机 12 字节 IV；密钥禁止硬编码
2. **AEAD/AAD**：`encryptWithAad` 可将密文与上下文（如 userId）绑定，解密时 AAD 不一致则认证失败，防串用
3. **国密合规**：SM2/SM3/SM4 需 `bcprov-jdk18on`；合规场景使用 `SM4-GCM` 替代 `AES-256-GCM`
4. **密码存储**：BCrypt 推荐强度 12（OWASP 最低 10）；PBKDF2 迭代次数默认 600,000（OWASP 2023 推荐）
5. **时序攻击防护**：所有密码/签名验证使用 `constantTimeEquals`（`MessageDigest.isEqual`）
6. **Snowflake 时钟回拨**：≤ 5ms 循环等待恢复；> 5ms 抛出 `ClockBackwardException`
7. **Snowflake WorkerId 耗尽**：当 WorkerId 分配超出 0-1023 范围时抛出 `WorkerIdExhaustedException`
8. **可信代理**：生产环境必须配置 `TrustedProxyConfiguration`，防止客户端伪造 `X-Forwarded-For` 绕过 IP 控制
9. **HexUtils 线程安全**：基于 JDK `HexFormat` 实例（线程安全），所有方法均为无状态纯函数

---

## 变更记录

- **1.0.0**（2026-09-01，治理轮）：
  - P0：`TraceIdGeneratorProxy` 降级 SecureRandom 静态化 + 降级 TraceId hex 格式 bug 修复（原 append(int) 产生十进制串）；`TempFileManager` 重构为 `AutoCloseable` + TTL 兜底清理；pom 清理（snakeyaml / transmittable-thread-local 移除）
  - P1：`RateLimiter` 删除（收敛至 safe 的 `TokenBucketLimiter`）；datacenterId 配置命名空间统一（旧前缀保留兼容告警）；TrustedProxy 自动装配 Bean；IdGenerator 降级计数 + Micrometer 指标；Javadoc 漂移清理
  - P2：反射桥接启动自检（`verifyBinding`，见 `docs/ADR-0002-trace-contract-sinking.md`）；`KeyProvider` 密钥来源 SPI；配置元数据补全（trusted-proxies / tempfile / crypto / sequence-bits 调优 hints）；能力生命周期状态章节
  - 测试补齐：8 个测试类 59 个用例（Snowflake 并发唯一性/时钟回拨注入、AES-GCM 往返/AAD 篡改、KeyProvider SPI、TempFileManager TTL、RetryUtils、降级路径）
- **1.0.0**（2026-08-17）：
  - README 对齐源码：补全 `HexUtils`、`DiffCalculator`/`DiffReport`/`FieldDiff`/`DiffValueFormatter`、`StaticBridge`、`RetryException`、`WorkerIdExhaustedException`、`NotApplicableException`、`@Experimental`、`TempFileManager`、`PodOrdinalWorkerIdAllocator`、`IpHashWorkerIdAllocator`、`WorkerIdAllocatorChain` 文档
  - 修正依赖说明：移除不存在的 `ydsz-common-core`、`ydsz-common-domain` 核心依赖声明，修正 `ydsz-common-json` 为可选依赖
  - 新增 "字段 diff 对比"、"→Spring Bean 桥接器" 使用示例
- **1.0.0**（2026-08-02）：线程池创建与监控能力迁移至 `ydsz-common-thread`
- **1.0.0**：架构优化（BeanMapper 独立、SnowflakeIdGenerator 拆分、循环导入消除）、功能增强（DateUtils/FileUtils/MaskUtils/ValidationUtils/StringUtils 增强）
