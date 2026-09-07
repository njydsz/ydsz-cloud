# ydsz-common-util

> 通用工具集（L1 工具模块层）— 雪花 ID / 国密算法 / Bean 工具 / 网络工具 / 安全工具

提供雪花 ID 多策略生成、国密 SM2/SM3/SM4 + AES-GCM 加解密、Bean 映射/对比、IP/CIDR 校验、HTTP 上下文、密码强度校验、脱敏/掩码、重试工具、Diff 计算等 70+ 实用工具类，是所有业务模块的工具类基座。

## 模块定位

| 属性 | 值 |
|---|---|
| **层级** | L1 工具模块层 |
| **类型** | 公共依赖库（不独立部署） |
| **作用** | 提供通用工具能力：ID 生成、加解密、Bean 工具、网络工具、校验工具 |
| **依赖** | ydsz-common-json；slf4j-api、spring-web、commons-io、spring-security-crypto、bcprov-jdk18on；可选 micrometer-core、spring-boot-autoconfigure、spring-boot-health |
| **版本** | 2.0.0 |

## 核心能力

### 1. 雪花 ID 生成器

| 类 | 说明 |
|---|---|
| `SnowflakeIdGenerator` | 标准雪花 ID 生成器（64 位 long），封装 IdWorker 暴露 `nextId()` / `nextStringId()` |
| `IdGenerator` | ID 生成器接口，解耦具体实现 |
| `WorkerIpHash` | WorkerId 分配策略（IP Hash，默认策略） |
| `WorkerPodOrdinal` | WorkerId 分配策略（K8s Pod 序号，StatefulSet 场景） |
| `WorkerIdAllocator` **SPI** | WorkerId 分配器接口，业务方可扩展自定义策略 |
| `SnowflakeHealthIndicator` | 雪花 ID 健康检查 |
| `ClockBackwardException` | 时钟回拨异常 |
| `WorkerIdExhaustedException` | WorkerId 耗尽异常 |

**WorkerId 分配策略链**：PodOrdinal（优先，适配 K8s StatefulSet）→ IpHash（兜底，hash(IP) % 1024）。

### 2. 国密与通用加解密

| 类 | 说明 |
|---|---|
| `AesGcmCryptoProvider` | AES-GCM 对称加解密（256 位） |
| `Sm4GcmCryptoProvider` | SM4-GCM 对称加密（国密） |
| `Sm2Utils` | SM2 椭圆曲线公钥算法（签名/验签/加解密/密钥交换，国密） |
| `Sm3Utils` | SM3 哈希算法（国密） |
| `DigestUtils` | 摘要工具（SHA-256 / SHA-512 / MD5） |
| `HexUtils` | Hex 编解码 |
| `CryptoProvider` | 加密提供者接口（策略模式） |
| `CryptoRegistry` | 加密算法注册表 |
| `KeyProvider` / `KeyProviderRegistry` | 密钥提供者 SPI |
| `CryptoProperties` | 加密配置属性（`ydsz.crypto.*`） |
| `CryptoAutoConfiguration` | 加密自动配置 |
| `BcProvider` | BouncyCastle 提供者单例 |

### 3. Bean 与集合工具

| 类 | 说明 |
|---|---|
| `BeanMapper` | Bean 属性拷贝（基于 Spring BeanUtils + 自定义扩展，支持不同名映射） |
| `BeanUpdateUtil` | Bean 更新工具（仅拷贝非 null 字段） |
| `CollectionUtils` | 集合工具（安全 toMap / 分组 / 扁平化 / 交集差集） |
| `MapUtils` | Map 工具（安全 get / 多级 key / 过滤 / 转换） |

### 4. Diff 对比工具

| 类 | 说明 |
|---|---|
| `DiffCalculator` | Diff 计算器入口，计算两个对象字段差异 |
| `DiffReport` | 差异报告（含变更字段列表 + 格式化输出） |
| `DiffField` | 单个字段差异描述（fieldName / oldValue / newValue） |
| `DiffValueFormatter` | 差异值格式化策略接口 |
| `FieldDiff` | 字段级差异信息 |

### 5. 网络与安全工具

| 类 | 说明 |
|---|---|
| `IpValidator` | IP 地址校验（IPv4 / IPv6） |
| `CidrUtils` | CIDR 工具（网段匹配、子网判断） |
| `NetworkInterfaceUtils` | 网卡工具（获取本地 IP / MAC） |
| `UrlPathUtils` / `UrlPathMatcher` | URL 路径工具（路径匹配、Ant 风格） |
| `HttpResponseUtils` / `HttpTokenUtils` / `RequestContextUtils` | HTTP 响应封装、Token 工具、请求上下文 |
| `ServletRequestUtils` | Servlet 请求工具（获取 IP / User-Agent） |
| `TrustedProxyConfiguration` / `TrustedProxyProperties** | 可信代理配置 |

### 6. 字符串与校验工具

| 类 | 说明 |
|---|---|
| `StringUtils` | 字符串工具（truncate / snakeCase / camelCase / isBlank 增强） |
| `MaskUtils` | 脱敏工具（手机号 / 身份证 / 银行卡 / 邮箱 / 姓名 / 地址） |
| `MessageUtils` | 国际化消息工具 |
| `RandomUtils` | 随机数工具（雪花 ID 相关） |
| `ValidationUtils` | 校验工具（组合 Bean Validation） |
| `PwdUtils` | 密码工具（生成随机密码） |

### 7. IO 与文件工具

| 类 | 说明 |
|---|---|
| `FileUtils` | 文件工具（安全读取 / 写入 / 复制 / 文件名校验） |
| `TempFileManager` / `TempFileProperties** | 临时文件管理（自动清理） |

### 8. 并发与重试

| 类 | 说明 |
|---|---|
| `RetryUtils` | 重试工具（指数退避 + 异常类型匹配 + 自定义策略） |
| `RetryException` | 重试耗尽异常 |

### 9. 日期工具

| 类 | 说明 |
|---|---|
| `DateUtils` | 日期工具（parse / format / 时区转换 / 相对时间计算） |

### 10. 国际化

| 类 | 说明 |
|---|---|
| `MessageSourceConfiguration` | MessageSource 自动配置（i18n 资源包统一加载） |
| `StaticBridge` | 静态桥接（解耦 MessageSource 静态访问） |

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
      datacenter-id: 1         # 数据中心 ID（0~31）
      worker-id-auto: true     # 是否自动分配 WorkerId
      health-check-enabled: true
  crypto:
    enabled: true
    default-algorithm: AES_GCM  # AES_GCM / SM4_GCM
    key: ${CRM_CRYPTO_KEY}      # 加密密钥（Base64）
```

### 3. 直接使用

```java
import com.njydsz.common.util.id.SnowflakeIdGenerator;
import com.njydsz.common.util.security.crypto.CryptoUtils;
import com.njydsz.common.util.bean.BeanMapper;

// 雪花 ID
Long id = snowflakeIdGenerator.nextId();

// 加解密
String encrypted = CryptoUtils.encrypt("sensitive data");
String plain = CryptoUtils.decrypt(encrypted);

// Bean 拷贝
BeanMapper.copy(source, target);
DiffReport diff = DiffCalculator.compare(oldObj, newObj);
```

## 配置项

### SnowflakeProperties（`ydsz.util.snowflake.*`）

| 配置 | 默认值 | 说明 |
|---|---|---|
| `ydsz.util.snowflake.datacenter-id` | 1 | 数据中心 ID（0~31） |
| `ydsz.util.snowflake.worker-id-auto` | true | 是否自动分配 WorkerId |
| `ydsz.util.snowflake.worker-id` | - | 手动指定 WorkerId（自动分配失败时使用） |
| `ydsz.util.snowflake.health-check-enabled` | true | 是否启用健康检查 |

### CryptoProperties（`ydsz.crypto.*`）

| 配置 | 默认值 | 说明 |
|---|---|---|
| `ydsz.crypto.enabled` | true | 是否启用加解密自动配置 |
| `ydsz.crypto.default-algorithm` | AES_GCM | 默认算法（AES_GCM / SM4_GCM） |
| `ydsz.crypto.key` | - | AES/SM4 加密密钥（Base64 编码） |
| `ydsz.crypto.sm2-private-key` | - | SM2 私钥（Base64） |
| `ydsz.crypto.sm2-public-key` | - | SM2 公钥（Base64） |

### TempFileProperties（`ydsz.util.temp-file.*`）

| 配置 | 默认值 | 说明 |
|---|---|---|
| `ydsz.util.temp-file.base-dir` | `java.io.tmpdir` | 临时文件存放目录 |
| `ydsz.util.temp-file.retention-minutes` | 60 | 临时文件保留时间（分钟） |
| `ydsz.util.temp-file.cleanup-enabled` | true | 是否启用定时清理 |

## SPI 扩展点

| SPI 接口 | 用途 | 注册方式 |
|---|---|---|
| `WorkerIdAllocator` **SPI** | WorkerId 分配策略（PodOrdinal → IpHash） | `@Component` |
| `KeyProvider` **SPI** | 密钥提供者（支持多后端：KMS / 本地配置） | `@Component` + `KeyProviderRegistry` |
| `CryptoProvider` | 加密算法扩展 | `CryptoRegistry.register()` |

## 健康检查

| 端点 | 说明 | 触发条件 |
|---|---|---|
| `/actuator/health/snowflake` | 雪花 ID 健康检查（WorkerId 分配状态） | `ydsz.util.snowflake.health-check-enabled=true` |

`SnowflakeHealthIndicator` 暴露信息：

- `worker_id` — 当前节点 WorkerId
- `datacenter_id` — 当前数据中心 ID
- `allocator_strategy` — 使用的分配策略
- `status` — UP / DOWN（时钟回拨时 DOWN）

## 自动配置类

| 类 | 触发条件 |
|---|---|
| `UtilAutoConfiguration` | 工具类基础 Bean 注册 |
| `CryptoAutoConfiguration` | `ydsz.crypto.enabled=true` 时注册加解密相关 Bean |
| `MessageSourceConfiguration` | 国际化 MessageSource 自动装配 |

## 注意事项

1. **WorkerId 分配**：同一服务多实例需确保 WorkerId 唯一。K8s StatefulSet 优先用 PodOrdinal，虚拟机/物理机用 IpHash。
2. **时钟回拨**：`ClockBackwardException` 发生后 ID 生成器将拒绝服务（fail-closed），业务侧需捕获并降级。
3. **加密密钥安全**：生产环境请使用 KMS 管理密钥（`KeyProvider` SPI），避免明文配置。
4. **SM2/SM3/SM4 国密**：国密算法依赖 BouncyCastle Provider，首次调用会自动注册。
5. **Bean 拷贝性能**：`BeanMapper` 基于 Spring BeanUtils，不适用于高性能批量拷贝场景（请使用 MapStruct）。

## 变更记录

- **26.09.01**（2026-09-01）：架构精简重构，移除 LUR/Weighted/弱引用等未落地能力；统一工具类分层（id/security/crypto/bean/collection/http/string/io）；新增 Diff 工具链；解密密 SPI 扩展（`KeyProvider` / `CryptoProvider`）；雪花 ID 健康检查。
- **26.09.01**（2026-08-02）：初始版本，对标 common-jdbc 标准格式重构 README。
