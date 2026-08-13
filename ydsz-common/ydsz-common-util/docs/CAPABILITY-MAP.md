# ydsz-common-util 能力全景图

> 本文档列出 `ydsz-common-util` 模块所有的公共工具类，按功能域分组，便于快速定位所需能力。

---

## 1. ID 生成（`id/`）

| 类 | 用途 | 使用方式 |
|----|------|---------|
| `IdGenerator` | 静态门面，快速生成 Snowflake ID | `IdGenerator.nextId()` |
| `SnowflakeIdGenerator` | Spring Bean 封装，支持配置和自定义 | `@Autowired` 注入或 `new SnowflakeIdGenerator()` |
| `WorkerIdAllocator` | WorkerId 分配策略 SPI | 实现接口 + `META-INF/services` 注册 |
| `WorkerIdAllocatorChain` | 策略链（PodOrdinal → IpHash → FilePersisted） | 链式组合 |
| `SnowflakeHealthIndicator` | Spring Boot Actuator 健康检查 | 自动配置 |
| `SnowflakeProperties` | 配置绑定（前缀 `ydsz.util.snowflake`） | `@ConfigurationProperties` |

## 2. 加密与摘要（`security/`）

### 2.1 统一加密入口

| 类 | 用途 |
|----|------|
| `CryptoUtils` | AES/SM4 统一入口（推荐新代码使用） |
| `CryptoProviderRegistry` | 算法注册表 |
| `CryptoProvider` | 加密算法 SPI |

### 2.2 国密算法

| 类 | 算法 | 依赖 |
|----|------|------|
| `Sm2Utils` | SM2 椭圆曲线公钥加密 | bcprov-jdk18on |
| `Sm3Utils` | SM3 消息摘要 | bcprov-jdk18on |
| `Sm4Utils` | SM4 分组对称加密 | bcprov-jdk18on |

### 2.3 摘要与密钥派生

| 类 | 用途 |
|----|------|
| `DigestUtils` | SHA-256 / SHA-512 / HMAC / PBKDF2 |
| `HexUtils` | Hex 编解码 |
| `BcProvider` | BouncyCastle 提供者注册 |

### 2.4 已废弃

| 类 | 废弃版本 | 替代 |
|----|---------|------|
| `AesUtils` | 3.0.0 | `CryptoUtils` |
| `AesGcmCrypto` | 3.0.0 | `CryptoUtils.encrypt/decrypt` |

## 3. 密码安全（`password/`）

| 类 | 用途 |
|----|------|
| `PwdUtils` | BCrypt / PBKDF2 哈希、密码强度评分 |
| `PasswordStrengthChecker` | 密码强度 SPI |
| `DefaultPasswordStrengthChecker` | 默认实现（长度/多样性/连续重复扣分） |

## 4. HTTP 工具（`http/`）

| 类 | 用途 |
|----|------|
| `ServletRequestUtils` | 请求参数解析、IP 提取 |
| `HttpResponseUtils` | 统一响应渲染 |
| `HttpTokenUtils` | Bearer Token 提取 |
| `RequestContextUtils` | 请求上下文工具 |
| `UrlPathMatcher` | URL 路径匹配 |
| `UrlPathUtils` | URL 路径解析 |
| `TrustedProxyConfiguration` | 可信代理配置 |

## 5. 并发工具（`concurrent/`）

| 类 | 用途 |
|----|------|
| `ExecutorUtils` | 线程池创建（Fixed / Virtual / Scheduled / TTL） |
| `MeteredThreadPoolExecutor` | Micrometer 指标自动注册线程池 |
| `BoundedVirtualThreadScheduler` | 有界虚拟线程调度器 |
| `StructuredConcurrencyScopes` | JDK 21 结构化并发作用域 |
| `RetryUtils` | 重试工具（固定延迟 / 指数退避） |
| `RateLimiter` | 令牌桶限流器 |
| `ScopedValues` | JDK 21 ScopedValue 封装 |

## 6. 集合工具（`collection/`）

| 类 | 用途 |
|----|------|
| `CollectionUtils` | null-safe 判空、分页、分组 |
| `MapUtils` | 类型安全取值、Map ↔ Bean 转换 |
| `SequencedCollections` | JDK SequencedCollection 适配 |

## 7. 字符串工具（`string/`）

| 类 | 用途 |
|----|------|
| `StringUtils` | 判空、驼峰/下划线互转、截断、掩码 |

## 8. IP 工具（`ip/`）

| 类 | 用途 |
|----|------|
| `IpValidator` | IPv4/IPv6 校验 |
| `CidrUtils` | CIDR 网段判断 |
| `NetworkInterfaceUtils` | 本机网卡信息 |

## 9. 认证与上下文（`auth/`）

| 类 | 用途 |
|----|------|
| `AuthInfo` | 认证信息接口 |
| `YdszAuthInfo` | 默认实现 |
| `AuthInfoUtils` | 便捷读取工具 |

## 10. 其他

| 类 | 用途 |
|----|------|
| `BeanUpdateUtil` | Bean PATCH 更新（仅复制非 null 属性） |
| `MessageUtils` | 国际化消息读取 |
| `SpringContextHolder` | Spring 静态上下文 |
| `YamlUtils` | YAML ↔ JSON 互转 |

---

> **文档更新时间**：2026-08-13
