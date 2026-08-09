# ydsz-common-util

> YDSZ 通用工具类库（L2 工具层）— 覆盖 ID 生成、加密/国密、HTTP、字符串、集合/Map、IP、并发、认证上下文、YAML、国际化消息等领域。

> ⚠️ **文档与代码一致性说明**：本文档严格对齐 `ydsz-common-util` 模块内真实存在的源码（39 个 Java 文件 / 约 37 个公共类）。早期版本 README / UTIL_GUIDELINES 中描述的 `SnowflakeUtils`、`JcaCipherPool`、`Rsa2Utils`、`ChaCha20Utils`、`Ed25519Utils`、`DateUtils`、`RequestHolder`、`BeanCopyUtils`、`ServletUtils`、`CookieUtils`、`UrlUtils`、`ContextPropagationUtils` 等类**从未在模块中实现**，属文档失配，已全部从本文档移除。

## 模块定位

| 属性 | 值 |
|---|---|
| **层级** | L2 工具模块层 |
| **类型** | 公共依赖库（不独立部署） |
| **作用** | 提供 ID 生成、加密/国密、HTTP、字符串、集合/Map、IP、并发、认证上下文、YAML、国际化消息等通用工具能力 |
| **依赖** | `ydsz-common-core`、`ydsz-common-domain`、`ydsz-common-json`、`snakeyaml`、`slf4j`、`commons-io`；可选依赖 `spring-security-crypto`、`bcprov-jdk18on`、`spring-web`、`spring-boot-starter-validation`、`transmittable-thread-local`、`spring-boot-health`（均为 `optional`） |
| **版本** | 1.0.0 |

> **零强制三方依赖原则**：核心工具仅依赖 JDK + 上述公共依赖；`spring-security-crypto`、`bcprov-jdk18on`、`spring-web` 等为 `optional`，未引入时对应能力（BCrypt、国密、Servlet 相关工具）会在调用时降级或抛出 `NoClassDefFoundError`。

## 核心能力

### 1. ID 生成（id 包）

| 类 | 说明 |
|---|---|
| `SnowflakeIdGenerator` | Snowflake ID 生成器 **Spring Bean**（构造器注入 `SnowflakeProperties` + `ObjectProvider<WorkerIdRegistry>`，`@Primary` `@ConditionalOnProperty(prefix="ydsz.util.snowflake", name="enabled", matchIfMissing=true)`） |
| `IdGenerator` | 静态门面（`nextId()` / `nextIdStr()`），内部委托 `SnowflakeIdGenerator`，失败有冷却重试；非 Spring 环境可用 `new SnowflakeIdGenerator()` 便捷构造器 |
| `WorkerIdRegistry` | WorkerId 注册中心 **SPI 接口**（仅 `acquire(nodeId)` + 默认 `type()`），用于多实例 WorkerId 唯一分配；**当前为预留扩展点，仓库内无默认实现** |
| `SnowflakeProperties` | Snowflake 配置属性（`ydsz.util.snowflake` 前缀） |
| `SnowflakeHealthIndicator` | Snowflake 健康检查指示器（由 `UtilAutoConfiguration` 注册） |
| `ClockBackwardException` | 时钟回拨超过容忍阈值时抛出 |
| `TracerUtils` | 分布式链路追踪工具（TraceId / SpanId 读写，无编译期硬依赖） |
| `RandomUtils` | 安全随机数工具（基于 `SecureRandom` / `ThreadLocalRandom`） |

### 2. 加密与安全（security 包）

| 类 | 说明 |
|---|---|
| `AesUtils` | AES 对称加密工具（默认 GCM 模式；静态 `encrypt/decrypt`，按 hex key 缓存 `AesGcmCrypto` 实例） |
| `AesGcmCrypto` | AES-GCM 认证加密器（AEAD，每次随机 12 字节 IV，支持实例化使用与可选 AAD） |
| `DigestUtils` | 摘要工具：SHA-256 / SHA-512 / HMAC-SHA256 / PBKDF2；常量时间比较 `constantTimeEquals` / `verifyDigest`，盐生成 `genSaltHex` |
| `Sm2Utils` | SM2 椭圆曲线公钥密码（密钥对生成、加解密、签名验签，GM/T 0003-2012） |
| `Sm3Utils` | SM3 密码杂凑算法（256 位摘要，GM/T 0004-2012） |
| `Sm4Utils` | SM4 分组密码（128 位密钥，GCM / CBC 模式，GM/T 0002-2012） |
| `BcProvider` | 内部工具类：幂等注册 BouncyCastle `BC` Provider（供国密类使用，外部勿直接调用） |

> 国密工具类通过 JCA Provider = `"BC"` 委托给 BouncyCastle，运行时需在 classpath 包含 `bcprov-jdk18on`。国密合规场景下，推荐使用 SM2/SM3/SM4 替代对应 RSA/SHA/AES。

### 3. 密码与强度（password 包，与 security 平级）

| 类 | 说明 |
|---|---|
| `PwdUtils` | BCrypt（`hashPasswordBCrypt` / `verifyPasswordBCrypt`）+ PBKDF2（`encodePBKDF2*`）+ 密码强度（`checkPasswordStrength` / `checkPasswordStrengthLevel` / `suggestPasswordImprovement`） |
| `PasswordStrengthChecker` | 密码强度 **SPI 接口**（可通过 `ServiceLoader` 扩展，支持业务自定义密码策略） |
| `DefaultPasswordStrengthChecker` | 默认密码强度校验器（长度 / 多样性 / 连续重复扣分，中英文国际化） |

### 4. HTTP 工具（http 包）

| 类 | 说明 |
|---|---|
| `ServletRequestUtils` | Servlet 请求解析（请求头 / 参数 / URL、Ajax / JSON 判断、可信代理 `isTrustedProxy`、URL 编解码） |
| `HttpResponseUtils` | 响应渲染（`renderString` / `renderObject` / `renderJson` / `renderError`，JSON 经 `YdszJson` 序列化并转义） |
| `HttpTokenUtils` | Token 提取与前缀剥离（`getToken` / `hasToken` / `stripPrefix`） |
| `RequestContextUtils` | 基于 Spring `RequestContextHolder` 获取当前请求 / 响应 |
| `TrustedProxyConfiguration` | 可信代理 IP 配置（Spring Bean 注入，`isTrusted(ip)`） |
| `UrlPathUtils` | URL 路径匹配（`matchAny` / `isIgnoreUrl`） |

### 5. 并发（concurrent 包）

| 类 | 说明 |
|---|---|
| `ExecutorUtils` | 线程池工厂（`newFixedThreadPool` / `newCachedThreadPool` / `newSingleThreadExecutor` / `newCpuBoundThreadPool` / `newVirtualThreadExecutor` / `newScheduledThreadPool` / `newPriorityThreadPool` / `newCustomThreadPool`）+ 流式 `ThreadPoolBuilder` + `submitWithTimeout` / `shutdownGracefully` 辅助方法 |

### 6. 字符串（string 包）

| 类 | 说明 |
|---|---|
| `StringUtils` | 字符串判空 / 空白（`isEmpty` / `isNotEmpty` / `isBlank` / `isNotBlank` / `hasText`）、驼峰/下划线互转（`toCamelCase` / `toUnderScoreCase`）、`format`（SLF4J 风格）、`defaultIfBlank` / `removeStart` / `startsWithIgnoreCase` |

> 本模块 `StringUtils` **不提供**脱敏方法；数据脱敏请使用 `ydsz-common-safe` 的 `SensitiveUtils`。

### 7. 集合与对象（collection 包 / bean 包）

| 类 | 说明 |
|---|---|
| `CollectionUtils` | 集合判空（`isEmpty` / `isNotEmpty`，支持 Collection / Map / Iterable）、`listToMap` / `listToGroup` / `convertList` / `filter` / `findFirst` / `findLast` |
| `MapUtils` | 类型安全取值（`getString` / `getInteger` / `getLong` / `getBoolean`）、`toBean` / `safeCastList` / `safeCastMap` / `getListOfMaps` / `getMapFromList` / `toStringObjectMap` |
| `BeanUpdateUtil` | Bean PATCH 语义更新（`copyNonNull(source, target, ignoreProperties...)`，仅复制非 null 属性，用于部分更新） |

### 8. IP 地址（ip 包）

| 类 | 说明 |
|---|---|
| `IpValidator` | IP 格式校验（`validIpv4` / `validIpv6` / `validIp`）、内网 / 私网判断（`isInternalIp` / `isPrivateIp`）、`getIpType` / `normalizeIp` / `normalizeIpv6`（解析零网络 IO） |
| `CidrUtils` | CIDR 网段判断（`isInRange` / `isIpv4InRange` / `isIpv6InRange`）、`ipToLong` / `longToIp` / 网段计算 |
| `NetworkInterfaceUtils` | 本机网络信息（`getHostIp` / `getHostName` / `listLocalIps`） |

### 9. Spring 集成（spring 包）

| 类 | 说明 |
|---|---|
| `SpringContextHolder` | `ApplicationContext` 持有者（静态 `getBean` / `isInitialized`） |

### 10. 认证信息（auth 包）

| 类 | 说明 |
|---|---|
| `AuthInfo` / `YdszAuthInfo` | 认证信息载体接口与实现 |
| `AuthInfoUtils` | 认证上下文工具（`getAuthInfo` / `getTenantId` / `getUniqueId` / `getAccessToken` / `getClaim` / `getUserLanguage` / 数据权限维度等） |

> 本项目**无** `RequestHolder` 类；访问当前请求上下文请用 `AuthInfoUtils`（认证信息）或 `RequestContextUtils`（Servlet 请求）。

### 11. 其他工具

| 类 | 说明 |
|---|---|
| `YamlUtils` | YAML ↔ JSON 互转（`jsonToYaml` / `yamlToJson`，基于 snakeyaml） |
| `MessageUtils` | 国际化消息工具（`getMessage` 多重载） |

### 12. 自动配置（config 包）

| 配置类 | 激活条件 | 注册的 Bean |
|---|---|---|
| `UtilAutoConfiguration` | 总是激活 | `SpringContextHolder`、`SnowflakeHealthIndicator` |

| 属性类 | 前缀 | 说明 |
|---|---|---|
| `SnowflakeProperties` | `ydsz.util.snowflake` | Snowflake `workerId` / `datacenterId` 配置，含来源策略枚举 `WorkerIdSource`（`ENVIRONMENT_VARIABLE` / `CONFIG`） |

## 接入方式

### 1. POM 引入依赖

```xml
<dependency>
    <groupId>com.njydsz</groupId>
    <artifactId>ydsz-common-util</artifactId>
</dependency>
```

### 2. 配置启用

```yaml
ydsz:
  util:
    snowflake:
      enabled: true                 # 启用 Snowflake 自动配置（默认启用）
      worker-id-source: ENVIRONMENT_VARIABLE   # workerId 来源策略
      worker-id: 1                  # 仅当 worker-id-source=CONFIG 时生效
      datacenter-id: 0
```

> 环境变量名固定为 `YDSZ_SNOWFLAKE_WORKER_ID`（`SnowflakeProperties.WORKER_ID_ENV_VAR`），无对应可配置属性项。

> 线程池监控由 `ydsz-common-thread` 模块统一提供；本模块 `ExecutorUtils` 仅提供静态工厂方法，业务自行管理生命周期。

### 3. 基础使用

```java
import com.njydsz.common.util.id.SnowflakeIdGenerator;
import com.njydsz.common.util.security.AesGcmCrypto;

// 生成分布式 ID（Spring 注入）
@Autowired
private SnowflakeIdGenerator idGenerator;
long id = idGenerator.nextId();

// 或非 Spring 环境
long id2 = new SnowflakeIdGenerator().nextId();

// AES-GCM 加密（实例化用法，适合高频场景）
byte[] key = AesUtils.initKey();           // 32 字节密钥
AesGcmCrypto crypto = new AesGcmCrypto(key);
String ciphertext = crypto.encrypt("plaintext");
String plaintext = crypto.decrypt(ciphertext);
```

## 配置项

| 配置 | 默认值 | 说明 |
|---|---|---|
| `ydsz.util.snowflake.enabled` | `true` | 是否启用 Snowflake 自动配置 |
| `ydsz.util.snowflake.worker-id` | - | 工作节点 ID（0-31，仅 `worker-id-source=CONFIG` 时生效） |
| `ydsz.util.snowflake.datacenter-id` | - | 数据中心 ID（0-31，未配置时基于主机名哈希自动计算） |
| `ydsz.util.snowflake.worker-id-source` | `ENVIRONMENT_VARIABLE` | workerId 来源策略：`ENVIRONMENT_VARIABLE` / `CONFIG` |
| `ydsz.util.snowflake.lease-millis` | `300000` | WorkerId 租约时间（毫秒），**仅在使用 `WorkerIdRegistry` 时生效** |

> **workerId 解析优先级**：容器中存在 `WorkerIdRegistry` Bean → 注册中心 `acquire(nodeId)`；否则若 `worker-id-source=CONFIG` 且显式配置了 `worker-id` → 使用配置值；否则基于 系统属性 `ydsz.snowflake.workerId` / 环境变量 `YDSZ_SNOWFLAKE_WORKER_ID` / `HOSTNAME`·`INSTANCE_INDEX`·`POD_INDEX` 哈希 / 本机 IP 哈希 自动计算。
> **datacenterId 解析优先级**：配置 > 基于主机名哈希自动计算。
> 注册中心仅负责分配 `workerId`，**不**自动启动心跳续约（2.0.0 起 `WorkerIdRegistry` 已简化为仅 `acquire`），应用关闭时由实现方自行释放。

## 使用示例

### 1. Snowflake ID 生成

```java
import com.njydsz.common.util.id.SnowflakeIdGenerator;
import com.njydsz.common.util.id.IdGenerator;

// 方式 A：Spring 注入
@Autowired
private SnowflakeIdGenerator idGenerator;
long id = idGenerator.nextId();

// 方式 B：静态门面（内部委托 SnowflakeIdGenerator）
long idB = IdGenerator.nextId();
String idStr = IdGenerator.nextIdStr();

// ID 解析（拆分为 timestamp / datacenterId / workerId / sequence）
long timestamp   = SnowflakeIdGenerator.parseTimestamp(id);
long datacenterId = SnowflakeIdGenerator.parseDatacenterId(id);
long workerId    = SnowflakeIdGenerator.parseWorkerId(id);
long sequence    = SnowflakeIdGenerator.parseSequence(id);

// 起始纪元（2020-01-01 UTC）
long epoch = SnowflakeIdGenerator.getEpoch();
```

### 2. AES-GCM 加密

```java
import com.njydsz.common.util.security.AesUtils;
import com.njydsz.common.util.security.AesGcmCrypto;

// 静态工具（内部按 hex key 缓存 AesGcmCrypto 实例）
String hexKey = AesUtils.initHexKey();          // 生成 32 字节密钥的 hex
String ct = AesUtils.encrypt("敏感数据", hexKey);
String pt = AesUtils.decrypt(ct, hexKey);

// 实例化用法（高频场景，避免重复构造；可附带 AAD）
byte[] key = AesUtils.initKey();
AesGcmCrypto crypto = new AesGcmCrypto(key);
String ct2 = crypto.encrypt("敏感数据", aadBytes);
String pt2 = crypto.decrypt(ct2, aadBytes);
```

### 3. 国密算法（SM2 / SM3 / SM4）

```java
import com.njydsz.common.util.security.*;

// SM4-GCM 加密
String sm4Key = Sm4Utils.initHexKey();
String ct = Sm4Utils.encryptGcm("敏感数据（国密）", sm4Key);
String pt = Sm4Utils.decryptGcm(ct, sm4Key);
// SM4-CBC（需显式 IV）
String iv = Sm4Utils.generateIvHex();
String cbc = Sm4Utils.encryptCbc("敏感数据（国密）", sm4Key, iv);

// SM3 摘要
String sm3Hash = Sm3Utils.digestHex("Hello SM3");

// SM2 密钥对 + 加解密 + 签名验签
java.security.KeyPair sm2Kp = Sm2Utils.generateKeyPair();
String sm2Ct = Sm2Utils.encrypt("敏感数据", sm2Kp.getPublic());
String sm2Pt = Sm2Utils.decrypt(sm2Ct, sm2Kp.getPrivate());
String sig = Sm2Utils.sign("重要数据", sm2Kp.getPrivate());
boolean ok = Sm2Utils.verify("重要数据", sig, sm2Kp.getPublic());
```

### 4. 摘要与签名（DigestUtils）

```java
import com.njydsz.common.util.security.DigestUtils;

String sha = DigestUtils.sha256Hex("Hello");
String hmac = DigestUtils.hmacSha256Hex("data", "secret");
String derived = DigestUtils.pbkdf2Hex("password".toCharArray(),
        DigestUtils.genSalt(16), 600_000, 256);
boolean eq = DigestUtils.constantTimeEquals(expected, actual);   // 防时序攻击
```

### 5. 密码哈希与强度

```java
import com.njydsz.common.util.password.PwdUtils;

// BCrypt 哈希与校验
String hashed = PwdUtils.hashPasswordBCrypt("userPassword123");
boolean valid = PwdUtils.verifyPasswordBCrypt("userPassword123", hashed);

// PBKDF2（可配置迭代次数与盐）
String saltHex = PwdUtils.generateSalt();
String pbk = PwdUtils.encodePBKDF2WithAutoSalt("userPassword123".toCharArray());

// 密码强度（SPI，可自定义实现）
var level = PwdUtils.checkPasswordStrengthLevel("abc123");
String suggestion = PwdUtils.suggestPasswordImprovement("abc123", Locale.CHINESE);
```

### 6. Bean PATCH 更新

```java
import com.njydsz.common.util.bean.BeanUpdateUtil;

// 仅复制非 null 属性（用于 PUT / PATCH 接口）
BeanUpdateUtil.copyNonNull(updateDTO, entity, "id", "createdAt");
```

### 7. IP 与请求工具

```java
import com.njydsz.common.util.ip.IpValidator;
import com.njydsz.common.util.ip.CidrUtils;
import com.njydsz.common.util.http.ServletRequestUtils;

boolean ok4 = IpValidator.validIpv4("192.168.1.1");
boolean internal = IpValidator.isInternalIp("10.0.0.1");
boolean inRange = CidrUtils.isInRange("192.168.1.5", "192.168.1.0/24");

String ua = ServletRequestUtils.getHeader(request, "User-Agent");
boolean ajax = ServletRequestUtils.isAjaxRequest(request);
boolean trusted = ServletRequestUtils.isTrustedProxy(request);
```

### 8. 自定义 WorkerId 注册中心

```java
import com.njydsz.common.util.id.WorkerIdRegistry;
import org.springframework.stereotype.Component;

@Component   // 仅在需要分布式 WorkerId 分配时提供；不提供则回退到 ENV/CONFIG/IP 哈希
public class RedisWorkerIdRegistry implements WorkerIdRegistry {

    @Override
    public long acquire(String nodeId) {
        // 基于 nodeId 哈希取模 / Redis SETNX / 容器序号映射，返回 0-31
        return resolvedWorkerId;
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
| `WorkerIdRegistry` | 分布式 WorkerId 注册中心，多实例场景唯一分配，避免 ID 冲突 | 业务模块实现（如 Redis / Zookeeper / ETCD / Nacos）；**未实现时回退到 ENV / CONFIG / IP 哈希策略** |
| `PasswordStrengthChecker` | 密码强度策略（长度 / 多样性 / 连续重复等） | `ServiceLoader` 加载业务自定义实现；默认 `DefaultPasswordStrengthChecker` |

## 健康检查

| 端点 | 说明 | 触发条件 |
|---|---|---|
| `/actuator/health` | Util 模块健康检查作为整体 health 端点的一部分 | `spring-boot-health` 在 classpath |

**`SnowflakeHealthIndicator` 暴露信息**：

- `workerId` / `datacenterId` / `lastTimestamp` / `currentTimestamp`

**健康状态规则**：

- `SnowflakeIdGenerator` Bean 不存在（禁用或未配置）→ `UNKNOWN`
- `workerId` 超出 `[0, 31]` → `DOWN`
- 检测到时钟回拨（`currentTimestamp < lastTimestamp`）→ `DOWN`
- 无异常 → `UP`

## 注意事项

1. **零强制三方依赖**：核心工具仅依赖 JDK + 公共依赖；可选依赖（`spring-security-crypto`、`bcprov-jdk18on`、`spring-web`、`spring-boot-starter-validation`、`transmittable-thread-local`）按需引入，未引入时对应工具类调用会降级或抛出 `NoClassDefFoundError`。
2. **AES 安全规范**：默认使用 AES-256-GCM 模式（认证加密 AEAD），每次加密生成随机 12 字节 IV；密钥由调用方传入，禁止硬编码。`AesUtils` 内部按 hex key 缓存 `AesGcmCrypto` 实例，避免高频场景重复构造。
3. **国密依赖**：SM2 / SM3 / SM4 需要 `bcprov-jdk18on` 在 classpath；`BcProvider` 在首次使用时幂等注册 `BC` Provider。
4. **密码哈希规范**：BCrypt 由 `spring-security-crypto` 提供；PBKDF2 迭代次数可配置（建议遵循 OWASP 最新建议）；所有密码验证使用常量时间比较（`constantTimeEquals` / `MessageDigest.isEqual()`）防止时序攻击。
5. **Snowflake 时钟回拨**：≤ 5ms 循环等待恢复；> 5ms 抛出 `ClockBackwardException` 强制报错。内部时间戳存储为相对 `EPOCH`（2020-01-01 UTC）的毫秒数，ID 寿命延长至约 2090 年；`parseTimestamp` / `getLastTimestamp` 会自动加回 `EPOCH` 得到绝对时间。
6. **ExecutorUtils 线程池规范**：统一线程名前缀 `ydsz-`，有界队列 + `CallerRunsPolicy` 防止 OOM；支持虚拟线程（`newVirtualThreadExecutor`，JDK 21+，`isVirtualThreadSupported()` 可探测）。
7. **IP 解析零网络 IO**：`IpValidator` 对 IPv4 字面量使用 `InetAddress.getByAddress`（不触发 DNS），仅对主机名才解析；`isTrustedProxy` 判断可信代理请结合 `TrustedProxyConfiguration`。
8. **MapUtils 类型安全**：`getString` / `getInteger` 等方法对缺失 / 类型不符返回 `null`，调用方需判空；批量转换使用 `toBean` / `safeCastList`。

## 变更记录

- **v2.1.0（2026-08-09）**：文档与代码一致性治理 + 代码健壮性修复
  - **代码修复（P0/P1）**：修正 Snowflake `EPOCH` 未减去导致 ID 时序/寿命错误（P0）；`HttpResponseUtils.renderError` 改用 `YdszJson` 序列化并转义（P1）；`IpValidator` 内网判断改为零 DNS 解析（P1）；`IdGenerator` 失败冷却重试避免永久降级（P1）；`AesUtils` 按 hex key 缓存 `AesGcmCrypto` 实例（P1）；`SnowflakeIdGenerator` 构造器注入 `SnowflakeProperties` + `ObjectProvider<WorkerIdRegistry>`，支持注册中心优先解析（P1）。
  - **代码修复（P2/P3）**：删除从未实现的死代码 `JcaCipherPool`（P2）；`Sm2Utils` / `Sm3Utils` 内联 ThreadLocal 池 + 统一 `BcProvider` 注册，修正失实文档（P2）；`DigestUtils` 摘要后清零缓冲区（P2）；新增 `BcProvider` 统一幂等注册 BouncyCastle Provider（P2）；`MapUtils` setter 缓存 key 由类名修正为 `Class<?>`（P3）。
  - **文档对齐**：本文档与 `UTIL_GUIDELINES.md` 重写为基于真实 39 文件的准确描述，移除对 `SnowflakeUtils` / `JcaCipherPool` / `Rsa2Utils` / `ChaCha20Utils` / `Ed25519Utils` / `DateUtils` / `RequestHolder` / `BeanCopyUtils` 等未实现类的引用；纠正 `RandomUtils` "已移除" 的虚假陈述（实际仍在广泛使用）。
- **v1.0.0**：`ydsz-common-util` 初始发布，提供上述真实工具类能力。

> 早期文档中提到的 `SnowflakeUtils` 静态单例废弃、`ChaCha20Utils` / `Ed25519Utils` / `Rsa2Utils` 废弃迁移、`JcaCipherPool` 池化、`DateUtils` 废弃等条目均为**文档层面的规划描述，模块从未包含这些类**，本次已统一校正。
