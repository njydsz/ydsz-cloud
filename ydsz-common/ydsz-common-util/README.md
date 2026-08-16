# ydsz-common-util

> YDSZ 通用工具类库（L2 工具层）— 覆盖 ID 生成、加密/国密、HTTP、字符串、集合/Map、IP、并发、认证上下文、YAML、国际化消息等领域。

> **注意**: 自 v4.1.0 起，线程池创建与监控能力（`ExecutorUtils`、`MeteredThreadPoolExecutor`）已迁移至 `ydsz-common-thread` 模块。
> 本模块仅保留 `RateLimiter`（限流）、`RetryUtils`（重试）等通用并发工具。

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

// 线程池创建
ExecutorService pool = ExecutorUtils.newCpuBoundThreadPool();
ExecutorUtils.shutdownGracefully(pool, 30, TimeUnit.SECONDS);
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
| 生成分布式唯一 ID | `IdGenerator` / `SnowflakeIdGenerator` / `SnowflakeIdBean` | 雪花算法核心 + Spring Bean 包装器，序列号可配置 |
| 加密解密（统一入口） | `CryptoUtils` | AES-256-GCM / SM4-GCM 算法路由，支持 AEAD/AAD |
| 国密算法（SM2/SM3/SM4） | `Sm2Utils` / `Sm3Utils` / `Sm4Utils` | 依赖 bcprov-jdk18on，GM/T 标准 |
| 摘要/签名/HMAC | `DigestUtils` | SHA-256/SHA-512/HMAC-SHA256/PBKDF2 + constantTimeEquals |
| 密码哈希与强度校验 | `PwdUtils` | BCrypt + PBKDF2 + 密码强度评分 |
| HTTP 请求解析 | `ServletRequestUtils` | Servlet 请求封装 |
| 响应渲染 | `HttpResponseUtils` | 基于 YdszJson 序列化的响应写入 |
| Token 提取 | `HttpTokenUtils` | Bearer Token 提取与前缀剥离 |
| 当前请求上下文 | `RequestContextUtils` | 从 Spring RequestContextHolder 获取当前请求/响应 |
| URL 白名单匹配（轻量） | `UrlPathUtils` | 单次线性匹配，Ant 风格 |
| URL 白名单匹配（高性能） | `UrlPathMatcher` | 构建一次复用多次，精确 O(1) + 通配符 |
| 可信代理判定 | `TrustedProxyConfiguration` | 防止 X-Forwarded-For 伪造，内网地址始终可信 |
| 线程池监控 | `ThreadPoolMonitor` | Micrometer 指标自动注册（由 ydsz-common-thread 提供能力） |
| 限流 | `RateLimiter` | 单机令牌桶算法（阻塞/非阻塞/超时模式） |
| 重试 | `RetryUtils` | 固定间隔 + 指数退避（含抖动），可自定义重试条件 |
| 字符串判空/转换/截断 | `StringUtils` | null-safe 判空、驼峰/下划线互转、truncate/abbreviate/normalizeSpace |
| 日期时间 | `DateUtils` | java.time API 封装：日起始/结束、工作日计算、格式化解析 |
| 文件操作 | `FileUtils` | 封装 commons-io：读写、复制、目录操作、扩展名解析 |
| 数据脱敏 | `MaskUtils` | 手机号/身份证/银行卡/邮箱/姓名掩码 |
| 业务校验 | `ValidationUtils` | 手机号/邮箱/身份证/统一社会信用代码等正则+校验码验证 |
| Bean 映射 | `BeanMapper` | Map → Bean / Record 转换（从 MapUtils 独立） |
| 集合判空/转换 | `CollectionUtils` / `MapUtils` | null-safe 判空、类型安全取值 |
| JDK 21 SequencedCollection | `SequencedCollections` | reverse/first/last 兼容 JDK 17/21 |
| Bean PATCH 更新 | `BeanUpdateUtil` | 仅复制非 null 属性 |
| IP 校验/CIDR | `IpValidator` / `CidrUtils` | IPv4/IPv6 校验、内网判断、CIDR 网段计算 |
| 本机网络接口 | `NetworkInterfaceUtils` | 获取本机 IP、主机名、枚举所有非回环 IP |
| 认证信息读取 | `AuthInfoUtils` | 用户 ID、租户 ID、数据权限维度 |
| 国际化消息 | `MessageUtils` | MessageSource 便捷读取 |

### 按包结构查找工具

```
com.njydsz.common.util
├── auth/           认证信息：AuthInfo、AuthInfoUtils、YdszAuthInfo
├── bean/           Bean 映射：BeanMapper（Map→Bean/Record）、BeanUpdateUtil（PATCH 语义）
├── collection/     集合工具：CollectionUtils、MapUtils（聚焦 Map 操作）、SequencedCollections
├── date/           日期时间：DateUtils（java.time API 封装）
├── diff/           字段 diff 工具：DiffCalculator、DiffReport、DiffField、FieldDiff
├── config/         自动配置：UtilAutoConfiguration、MessageSourceConfiguration
├── http/           HTTP 工具：ServletRequestUtils、HttpResponseUtils、HttpTokenUtils、
│                   RequestContextUtils、UrlPathUtils、UrlPathMatcher、TrustedProxyConfiguration
├── io/             文件操作：FileUtils（封装 commons-io）
├── id/             ID 生成：SnowflakeIdGenerator、IdGenerator、RandomUtils、TracerUtils、
│                   WorkerIdAllocator（SPI）、WorkerIdAllocatorChain、SnowflakeProperties、
│                   SnowflakeHealthIndicator、WorkerIdRegistry
├── ip/             IP 工具：IpValidator、CidrUtils（含缓存）、NetworkInterfaceUtils
├── mask/           数据脱敏：MaskUtils
├── message/        国际化：MessageUtils
├── password/       密码工具：PwdUtils、PasswordStrengthChecker（SPI）
├── security/       加密工具：CryptoUtils（统一入口）、CryptoProviderRegistry、DigestUtils、
│                   AesUtils、AesGcmCrypto、国密工具（Sm2Utils/Sm3Utils/Sm4Utils）、BcProvider
├── security/crypto/ 加密提供者：CryptoProvider（SPI）、CryptoProviderRegistry、
│                   AesGcmCryptoProvider、Sm4GcmCryptoProvider、Sm4CbcCryptoProvider
├── string/         字符串：StringUtils
├── validate/       业务校验：ValidationUtils
```

---

## 依赖说明

### 零强制三方依赖原则

| 依赖类型 | 依赖 | 说明 |
|---------|------|------|
| **核心依赖**（强制） | JDK 21、ydsz-common-core、ydsz-common-domain、ydsz-common-json、snakeyaml、slf4j、commons-io、jsr305 | 编译和运行时必需 |
| **可选依赖**（按需） | spring-security-crypto | BCrypt 密码哈希（未引入时 PwdUtils 使用 PBKDF2 降级） |
| | bcprov-jdk18on | 国密算法（SM2/SM3/SM4） |
| | spring-web | Servlet 请求/响应工具、URL 路径匹配 |
| | transmittable-thread-local | TTL 上下文透传线程池 |
| | spring-boot-health | Snowflake 健康检查 |
| | micrometer-core | MeteredThreadPoolExecutor 指标注册 |

> **可选依赖处理**：未引入时，调用对应方法会抛出包含引入指引的 `IllegalStateException`（而非 `NoClassDefFoundError`）。

---

## SPI 扩展点

| SPI 接口 | 用途 | 默认实现 |
|---------|------|---------|
| `WorkerIdAllocator` | 分布式 WorkerId 分配策略（0-1023） | PodOrdinal → IpHash → FilePersisted 链 |
| `PasswordStrengthChecker` | 密码强度评分规则 | `DefaultPasswordStrengthChecker`（长度/多样性/连续重复扣分） |
| `CryptoProvider` | 加密算法提供者 | `AesGcmCryptoProvider`（默认）、`Sm4GcmCryptoProvider`、`Sm4CbcCryptoProvider` |

> **自定义 SPI 实现**：在 `META-INF/services/` 接口全限定名文件中填写实现类全限定名。

---

## 配置项

| 配置 | 默认值 | 说明 |
|------|--------|------|
| `ydsz.util.snowflake.enabled` | `true` | 是否启用 Snowflake 自动配置 |
| `ydsz.util.snowflake.worker-id` | - | WorkerId（0-1023，显式配置最高优先级） |
| `ydsz.util.snowflake.datacenter-id` | - | 数据中心 ID（0-31，未配置时基于主机名哈希） |
| `ydsz.util.snowflake.node-id` | - | 节点标识（用于 PodOrdinal/FilePersisted 策略） |
| `ydsz.util.snowflake.epoch` | `1577836800000` | 起始纪元时间戳（2020-01-01 UTC），@since 4.0.0 |
| `ydsz.util.snowflake.sequence-bits` | `7` | 序列号位数（7-13，决定每毫秒并发能力），@since 4.0.0 |
| `crypto.algorithm`（系统属性） | `AES-256-GCM` | 加密算法标识，可选 `SM4-GCM`/`SM4-CBC`/`AES-256-GCM` |

**WorkerId 解析优先级**：显式配置 → PodOrdinal → IpHash → FilePersisted

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

// 查看所有可用算法
Set<String> algos = CryptoUtils.availableAlgorithms();
```

### RateLimiter — 单机令牌桶限流器

```java
// 每秒 100 个令牌，桶容量 200（允许 2 倍突发）
RateLimiter limiter = RateLimiter.create(100, 200);

// 阻塞式获取
limiter.acquire();

// 带超时非阻塞获取
boolean ok = limiter.tryAcquire(100, TimeUnit.MILLISECONDS);
```

> 分布式场景请使用 Redis + Lua 或 Sentinel。

### RetryUtils — 重试工具

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

### BoundedVirtualThreadScheduler — 有界虚拟线程调度器

```java
// 限制最多 100 个虚拟线程并发
BoundedVirtualThreadScheduler scheduler = new BoundedVirtualThreadScheduler(100);

// 背压模式：超限阻塞提交方（非无限创建线程）
scheduler.submit(() -> httpClient.call(url));

// 获取结果
Future<String> future = scheduler.submitWithResult(() -> httpClient.get(url));
```

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

---

## 安全注意事项

1. **AES 安全**：默认使用 AES-256-GCM（认证加密 AEAD），每次加密生成随机 12 字节 IV；密钥禁止硬编码
2. **AEAD/AAD**：`encryptWithAad` 可将密文与上下文（如 userId）绑定，解密时 AAD 不一致则认证失败，防串用
3. **国密合规**：SM2/SM3/SM4 需 `bcprov-jdk18on`；合规场景使用 `SM4-GCM` 替代 `AES-256-GCM`
4. **密码存储**：BCrypt 推荐强度 12（OWASP 最低 10）；PBKDF2 迭代次数默认 600,000（OWASP 2023 推荐）
5. **时序攻击防护**：所有密码/签名验证使用 `constantTimeEquals`（`MessageDigest.isEqual`）
6. **Snowflake 时钟回拨**：≤ 5ms 循环等待恢复；> 5ms 抛出 `ClockBackwardException`
7. **可信代理**：生产环境必须配置 `TrustedProxyConfiguration`，防止客户端伪造 `X-Forwarded-For` 绕过 IP 控制

---

---

## v4.0.0 变更亮点

### 架构优化
- **BeanMapper 独立**：将 MapUtils 中 700+ 行 Bean 映射逻辑抽取为独立的 `BeanMapper` 类，MapUtils 回归纯 Map 操作职责（单一职责原则）
- **SnowflakeIdGenerator 拆分**：核心算法（`SnowflakeIdGenerator`）与 Spring Bean 包装（`SnowflakeIdBean`）分离，序列号位数可配置（7-13 位）
- **循环导入消除**：`CidrUtils` 与 `IpValidator` 不再相互 import

### 功能增强
- **DateUtils**：16 个 java.time API 方法 — 日起始/结束、工作日计算、格式化解析
- **FileUtils**：10 个文件操作方法 — 读写、复制、目录操作、扩展名安全处理
- **MaskUtils**：6 个脱敏方法 — 手机号/身份证/银行卡/邮箱/姓名掩码
- **ValidationUtils**：8 个校验方法 — 手机号/邮箱/身份证/统一社会信用代码（含校验码）
- **StringUtils 增强**：`truncate`、`abbreviate`、`normalizeSpace`

### 体验改善
- **SpringContextHolder @Deprecated**：标记为过时并文档说明替代方案
- **AuthInfoUtils 日志增强**：`getClaim` 未识别 claim 名输出 debug 日志
- **CidrUtils 缓存**：IP 范围判断结果缓存（ConcurrentHashMap，上限 1024）
- **DigestUtils 缓冲区**：ThreadLocal 缓冲区带复用计数自动重置

> **文档与代码一致性**：本文档严格对齐模块内真实源码。
