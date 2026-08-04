# remi-common-util

> REMI 通用工具类库（L2 工具层）— 99+ 工具类覆盖 ID 生成、加密、HTTP、字符串、日期、文件、集合、Bean 拷贝、Spring 集成等领域

## 模块定位

| 属性 | 值 |
|---|---|
| **层级** | L2 工具模块层 |
| **类型** | 公共依赖库（不独立部署） |
| **作用** | 提供 ID 生成、加密、HTTP、字符串、日期、文件、集合、Bean 拷贝、Spring 集成等通用工具能力 |
| **依赖** | remi-common-core、remi-common-json、snakeyaml；可选依赖 SkyWalking、ip2region、yauaa、Micrometer、Hutool、BouncyCastle、Spring Security Crypto、MyBatis-Plus Core、TransmittableThreadLocal、Spring Web/WebFlux |
| **版本** | 1.0.0 |

## 核心能力

### 1. ID 生成（id 包）

| 类 | 说明 |
|---|---|
| `SnowflakeUtils` | 雪花算法 ID 生成器（分片 CAS 优化、时钟回拨容忍、workerId 自动分配），单例静态工具类 |
| `WorkerIdRegistry` | WorkerId 注册中心 SPI（支持 Redis/Zookeeper/ETCD 等），含心跳续约机制 |
| `UUIDUtils` | UUID 工具（带连字符 / 不带连字符 / UUID v7） |
| `RandomUtils` | 安全随机数工具（基于 `SecureRandom`） |
| `TracerUtils` | 分布式链路追踪工具（SkyWalking 反射集成，无编译期硬依赖） |

### 2. 加密与安全（security 包）

| 类 | 说明 |
|---|---|
| `AesUtils` | AES 对称加密（默认 GCM 模式，委托 `AesGcmCrypto` 实现，兼容 ECB/CBC 旧密文解密） |
| `AesGcmCrypto` | AES-GCM 认证加密器（AEAD，每次随机 12 字节 IV，支持实例化使用） |
| `Rsa2Utils` | RSA2 非对称加解密 + 签名验签（SHA256withRSA、OAEP 填充、分段加解密） |
| `DigestUtils` | SHA-256 / MD5 / HMAC 摘要工具（常量时间比较、PBKDF2 密钥派生） |
| `PwdUtils` | BCrypt（strength=12）/ PBKDF2（600000 次迭代，OWASP 2023）密码哈希 |

### 3. HTTP 工具（http 包）

| 类 | 说明 |
|---|---|
| `ServletUtils` / `WebFluxUtils` | Servlet / WebFlux 请求工具 |
| `CookieUtils` | Cookie 读写工具 |
| `UrlUtils` | URL 解析与编码解码 |
| `ResponseUtils` | HTTP 响应写入工具 |

### 4. 并发与上下文（concurrent 包）

| 类 | 说明 |
|---|---|
| `ExecutorUtils` | 线程池工厂（Fixed/Cached/Single/CPU-Bound/VirtualThread/Priority/Scheduled） |
| `ContextPropagationUtils` | 线程间上下文传播（MDC 轻量级控制字符编码，零 JSON 开销，自定义上下文注册） |

### 5. 字符串与文本（string / regex 包）

| 类 | 说明 |
|---|---|
| `StringUtils` | 字符串判空 / 分割 / 连接 / 驼峰转换（内置 `PatternCache` LRU 缓存） |
| `CharsetUtils` | 字符集工具 |
| `RegexUtils` | 正则验证（手机 / 邮箱 / 身份证 / IP） |

### 6. 日期与数字（date / number 包）

| 类 | 说明 |
|---|---|
| `LocalDateTimeUtils` | 日期时间格式化 / 计算 / 比较 |
| `NumberUtils` / `BigDecimalUtils` | 数字工具（精度计算 / 百分比 / 格式化） |

### 7. 文件与 IO（file / io 包）

| 类 | 说明 |
|---|---|
| `FileUtils` | 文件读写 / 复制 / 删除 / 目录遍历（NIO Path API，资源自动管理） |
| `FileTypeUtils` | 文件类型检测（扩展名 + Magic Number） |
| `FileValidator` | 文件校验（大小 / 类型 / 文件名安全） |
| `ImageUtils` | 图片处理（缩放 / 水印 / 格式转换） |
| `IOUtils` | 流读写 / 关闭 / Base64 转换 |
| `MediaType` | 媒体类型常量 |

### 8. 集合与对象（collection / array / object / bean 包）

| 类 | 说明 |
|---|---|
| `CollectionUtils` / `ListUtils` / `SetUtils` / `MapUtils` | 集合工具（判空 / 分片 / 去重 / 排序） |
| `ArrayUtils` / `SortUtils` | 数组工具与排序工具 |
| `ObjectUtils` | 对象工具（默认值 / 深拷贝 / 比较） |
| `BeanCopyUtils` | Bean 属性拷贝（忽略字段、null 值处理、Lambda 转换器） |
| `BeanUpdateUtil` | Bean PATCH 语义更新（仅复制非 null 属性，用于部分更新） |
| `BeanCopyOptions` / `Converters` / `PropertyConverter` | 拷贝选项与类型转换器 |
| `BeanCopyException` | 拷贝异常 |

### 9. Spring 集成（spring 包）

| 类 | 说明 |
|---|---|
| `SpringContextHolder` | ApplicationContext 持有者（静态获取 Bean） |
| `SpringBeanUtils` | Bean 获取 / 注册 / 注入工具（volatile + ReentrantLock 线程安全） |
| `SpringPropertyUtils` | 配置属性读取工具 |

### 10. 认证信息（auth 包）

| 类 | 说明 |
|---|---|
| `AuthInfo` / `YdszAuthInfo` | 认证信息载体接口与实现 |
| `AuthInfoUtils` | 认证信息工具 |
| `RequestHolder` | 请求持有者（Servlet Request 静态访问） |

### 11. 其他工具（散布于多包）

| 类 | 说明 |
|---|---|
| `YamlUtils` | YAML 解析工具（基于 snakeyaml） |
| `CursorHelper` | 游标分页编码 / 解码（Keyset Pagination） |
| `HashUtils` | 非加密哈希（CRC32 / MurmurHash32 / Base62 编码解码） |
| `ClassUtils` | 类加载器工具 |
| `ExceptionUtils` | 异常堆栈转字符串 |
| `IpAddrUtils` / `IpInfoUtils` | IP 地址工具（基于 ip2region） |
| `MessageUtils` | 国际化消息工具 |
| `ByteUtils` / `HexUtils` | 字节与十六进制工具 |

### 12. 健康检查（health 包）

| 类 | 说明 |
|---|---|
| `UtilHealthIndicator` | 工具模块健康检查（Snowflake 状态、JVM 内存） |
| `SnowflakeHealthIndicator` | Snowflake ID 生成器健康检查（时钟回拨、workerId、ID 生成验证、分片数） |

### 13. 自动配置（config 包）

| 配置类 | 激活条件 | 注册的 Bean |
|---|---|---|
| `UtilAutoConfiguration` | 总是激活 | `SpringContextHolder`、`SnowflakeHealthIndicator`、`UtilHealthIndicator` |
| `SnowflakeAutoConfiguration` | `remi.util.snowflake.enabled=true`（默认启用） | 构造期自动初始化 `SnowflakeUtils` |

| 属性类 | 前缀 | 说明 |
|---|---|---|
| `SnowflakeProperties` | `remi.util.snowflake` | Snowflake workerId/datacenterId 配置，含来源策略枚举 `WorkerIdSource` |

## 接入方式

### 1. POM 引入依赖

```xml
<dependency>
    <groupId>com.remisoft</groupId>
    <artifactId>remi-common-util</artifactId>
</dependency>
```

### 2. 配置启用

```yaml
remi:
  util:
    snowflake:
      enabled: true                     # 启用 Snowflake 自动配置（默认启用）
      worker-id-source: ENVIRONMENT_VARIABLE   # workerId 来源策略
      environment-variable-name: REMI_SNOWFLAKE_WORKER_ID
```

> 线程池监控由 `remi-common-thread` 模块统一提供（配置驱动的池自动绑定 Micrometer 指标 + `ThreadHealthIndicator`）；本模块 `ExecutorUtils` 仅提供静态工厂方法，业务自行管理生命周期。

### 3. 基础使用

```java
import com.remisoft.common.util.id.SnowflakeUtils;
import com.remisoft.common.util.security.AesGcmCrypto;
import com.remisoft.common.util.bean.BeanCopyUtils;

// 生成分布式 ID
long id = SnowflakeUtils.getInstance().nextId();

// AES-GCM 加密
AesGcmCrypto crypto = new AesGcmCrypto(keyBytes);
String ciphertext = crypto.encrypt("plaintext");

// Bean 属性拷贝
BeanCopyUtils.copyProperties(source, target);
```

## 配置项

| 配置 | 默认值 | 说明 |
|---|---|---|
| `remi.util.snowflake.enabled` | true | 是否启用 Snowflake 自动配置 |
| `remi.util.snowflake.worker-id` | - | 工作节点 ID（0-31，仅 `worker-id-source=CONFIG` 时生效） |
| `remi.util.snowflake.datacenter-id` | - | 数据中心 ID（0-31，未配置时基于主机名哈希自动计算） |
| `remi.util.snowflake.worker-id-source` | `ENVIRONMENT_VARIABLE` | workerId 来源策略：`ENVIRONMENT_VARIABLE` / `CONFIG` / `INSTANCE_INDEX` |
| `remi.util.snowflake.environment-variable-name` | `REMI_SNOWFLAKE_WORKER_ID` | 环境变量名 |
| `remi.util.snowflake.lease-millis` | `300000` | WorkerId 租约时间（毫秒），仅在使用 `WorkerIdRegistry` 时生效 |

> workerId 解析优先级：分布式注册中心（`WorkerIdRegistry` Bean）> 配置策略（ENV/CONFIG/INSTANCE_INDEX）> 基于 IP 哈希自动计算。datacenterId 解析优先级：配置文件 > 环境变量 `REMI_SNOWFLAKE_DATACENTER_ID` > 基于主机名哈希自动计算。注册中心获取 WorkerId 后会自动启动心跳续约，应用关闭时自动释放。
>
> 线程池监控统一由 `remi-common-thread` 模块提供（Micrometer 指标前缀 `executor.*`）；本模块不重复实现线程池监控。

## 使用示例

### 1. Snowflake ID 生成

```java
import com.remisoft.common.util.id.SnowflakeUtils;

// 自动配置后即可使用（Spring 容器启动时由 SnowflakeAutoConfiguration 初始化）
long id = SnowflakeUtils.getInstance().nextId();

// 手动初始化（覆盖自动配置）
SnowflakeUtils.init(1L, 0L);

// ID 解析（拆分为 timestamp / datacenterId / workerId / sequence）
long timestamp   = SnowflakeUtils.parseTimestamp(id);
long datacenterId = SnowflakeUtils.parseDatacenterId(id);
long workerId    = SnowflakeUtils.parseWorkerId(id);
long sequence    = SnowflakeUtils.parseSequence(id);
```

### 2. AES-GCM 加密

```java
import com.remisoft.common.util.security.AesGcmCrypto;
import java.security.SecureRandom;

byte[] key = new byte[32];
new SecureRandom().nextBytes(key);
AesGcmCrypto crypto = new AesGcmCrypto(key);

String ciphertext = crypto.encrypt("敏感数据");
String plaintext = crypto.decrypt(ciphertext);
```

### 3. 密码哈希

```java
import com.remisoft.common.util.security.PwdUtils;

// 加密密码（BCrypt，strength=12）
String hashed = PwdUtils.encrypt("userPassword123");

// 验证密码
boolean valid = PwdUtils.matches("userPassword123", hashed);
```

### 4. Bean 拷贝与 PATCH 更新

```java
import com.remisoft.common.util.bean.BeanCopyUtils;
import com.remisoft.common.util.bean.BeanUpdateUtil;

// 全量拷贝
BeanCopyUtils.copyProperties(source, target);

// PATCH 更新（仅复制非 null 属性，用于 PUT/PATCH 接口）
BeanUpdateUtil.copyNonNull(updateDTO, entity);
```

### 5. 自定义 WorkerId 注册中心

```java
import com.remisoft.common.util.id.WorkerIdRegistry;
import org.springframework.stereotype.Component;

@Component
public class RedisWorkerIdRegistry implements WorkerIdRegistry {

    @Override
    public long acquire(String nodeIp, long leaseMillis) {
        // Redis SETNX 实现 workerId 分配
        return redisTemplate.opsForValue().setIfAbsent("snowflake:worker:" + nodeIp, ...);
    }

    @Override
    public boolean heartbeat(long workerId, String nodeIp) {
        // 续约租约
        return true;
    }

    @Override
    public void release(long workerId, String nodeIp) {
        // 释放 workerId
    }

    @Override
    public String type() {
        return "Redis";
    }
}
```

## SPI 扩展点

| SPI 接口 | 用途 | 实现方 |
|---|---|---|
| `WorkerIdRegistry` | 分布式 WorkerId 注册中心，用于多实例场景下 WorkerId 唯一分配，避免 ID 冲突 | 业务模块实现（如基于 Redis / Zookeeper / ETCD），未实现时回退到 ENV/CONFIG/IP 哈希策略 |

## 健康检查

| 端点 | 说明 | 触发条件 |
|---|---|---|
| `/actuator/health` | Util 模块健康检查作为整体 health 端点的一部分 | `spring-boot-health` 在类路径 |

**`UtilHealthIndicator` 暴露信息**：

- `snowflake.initialized` / `snowflake.workerId` / `snowflake.datacenterId` / `snowflake.lastTimestamp` — SnowflakeUtils 初始化状态
- `jvm.availableProcessors` / `jvm.maxMemoryMB` / `jvm.usedMemoryMB` / `jvm.memoryUsagePercent` / `jvm.memoryWarning` — JVM 运行时基础指标

**`SnowflakeHealthIndicator` 暴露信息**：

- `workerId` / `datacenterId` / `shardCount` / `lastTimestamp` / `currentTimestamp`

**健康状态规则**：

- SnowflakeUtils 未初始化或时钟回拨 → DOWN
- JVM 内存使用率 > 85% → UP（带 `memoryWarning=true` 详情）
- 无异常 → UP

## 注意事项

1. **零依赖原则**：核心工具不依赖 Spring，可选依赖（SkyWalking、ip2region、yauaa、Micrometer、Hutool、BouncyCastle、Spring Security Crypto、MyBatis-Plus Core、TransmittableThreadLocal、Spring Web/WebFlux）按需引入，未引入时对应工具类调用会降级或抛出 `NoClassDefFoundError`。
2. **AES 安全规范**：默认使用 AES-256-GCM 模式（认证加密 AEAD），每次加密生成随机 12 字节 IV；密钥通过 `setConfiguredKey()` 注入或环境变量配置，禁止硬编码。`AesUtils` 兼容 ECB/CBC 旧密文解密，但新加密一律走 GCM。
3. **密码哈希规范**：BCrypt strength=12（OWASP 推荐至少 10），PBKDF2 600000 次迭代（OWASP 2023 推荐），所有密码验证使用 `MessageDigest.isEqual()` 常量时间比较防止时序攻击。
4. **SnowflakeUtils 单例初始化**：通过 `SnowflakeUtils.init(workerId, datacenterId)` 初始化，重复调用抛出 `IllegalStateException`。自动配置捕获此异常并跳过（用于手动初始化场景）。时钟回拨容忍 5ms 以内直接等待，超过 5 秒抛出 `ClockBackwardException`。
5. **BeanCopyUtils 使用规范**：基于 Spring BeanUtils 委托实现，深拷贝请使用 JSON 序列化/反序列化或 `Cloneable`。
6. **ContextPropagationUtils 性能优化**：MDC 传播使用控制字符（`\u0001` / `\u0002`）编码替代 JSON 序列化，零解析开销。自定义上下文通过 `registerContextProvider()` 注册。
7. **ExecutorUtils 线程池规范**：统一线程名前缀 `remi-`，有界队列 + `CallerRunsPolicy` 防止 OOM，支持 VirtualThread（JDK 21+）。
8. **路径安全**：`FileUtils.isSafePath()` / `checkAllowDownload()` 防止路径遍历攻击，禁止 `..` 跨目录访问。
9. **ip2region 离线库**：`IpAddrUtils` 依赖 `ip2region.xdb` 离线数据库（位于 `src/main/resources`），首次调用时加载到内存。

## 变更记录

- **v1.0.0**（2026-08-02）：对标 common-jdbc 标准格式重构 README，补全全部 9 个章节
