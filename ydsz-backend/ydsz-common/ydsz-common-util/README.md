# ydsz-common-util

YDSZ 通用工具类库 — 99+ 工具类（约 70 个 Java 源文件）覆盖 ID 生成、加密、HTTP、字符串、日期、文件、集合、Bean 拷贝、Spring 等领域。

## 模块定位

| 属性 | 值 |
|---|---|
| **层级** | L2 工具模块层 |
| **类型** | 公共依赖库（不独立部署） |
| **源文件数** | 约 70（99+ 工具类） |
| **零依赖原则** | 核心工具不依赖 Spring（可选集成） |
| **JDK 要求** | Java 21+ |

## 核心能力

### ID 生成

| 类 | 说明 |
|---|---|
| `SnowflakeUtils` | 雪花算法 ID 生成器（分片 CAS 优化、时钟回拨容忍、workerId 自动分配） |
| `WorkerIdRegistry` | WorkerId 注册中心 SPI（支持 Redis/Zookeeper/ETCD 等） |
| `UUIDUtils` | UUID 工具（带连字符 / 不带连字符 / UUID v7） |
| `RandomUtils` | 安全随机数工具 |
| `TracerUtils` | 分布式链路追踪工具（SkyWalking 反射集成，无编译期硬依赖） |

### 加密与安全

| 类 | 说明 |
|---|---|
| `AesUtils` | AES 对称加密（默认 GCM 模式，委托 `AesGcmCrypto` 实现，兼容 ECB/CBC 旧密文解密） |
| `AesGcmCrypto` | AES-GCM 认证加密器（AEAD，每次随机 IV，支持实例化使用） |
| `Rsa2Utils` | RSA2 非对称加解密 + 签名验签（SHA256withRSA、OAEP 填充、分段加解密） |
| `CryptoUtil` / `CryptoSignUtil` | 通用加解密 + 签名工具 |
| `DigestUtils` | SHA-256 / MD5 / HMAC 摘要工具（常量时间比较、PBKDF2 密钥派生） |
| `PwdUtils` | BCrypt（strength=12）/ PBKDF2（600000 次迭代，OWASP 2023）密码哈希 |

### HTTP 工具

| 类 | 说明 |
|---|---|
| `OkHttpUtils` | OkHttp 静态封装（连接池复用、请求级超时、异步请求、连接池监控） |
| `HttpClientFactory` | OkHttpClient 工厂（配置化创建、拦截器注入、Dispatcher 自定义） |
| `OkHttpProperties` | OkHttp 配置属性（超时、连接池、Keep-Alive） |
| `ServletUtils` / `WebFluxUtils` | Servlet / WebFlux 请求工具 |
| `CookieUtils` | Cookie 读写工具 |
| `UrlUtils` | URL 解析与编码解码 |
| `ResponseUtils` | HTTP 响应写入工具 |

### 并发与重试

| 类 | 说明 |
|---|---|
| `ExecutorUtils` | 线程池工厂（Fixed/Cached/Single/CPU-Bound/VirtualThread/Priority/Scheduled） |
| `ContextPropagationUtils` | 线程间上下文传播（MDC 轻量级编码，零 JSON 开销，自定义上下文注册） |
### 字符串与文本

| 类 | 说明 |
|---|---|
| `StringUtils` | 字符串判空 / 分割 / 连接 / 驼峰转换（内置 `PatternCache` LRU 缓存） |
| `CharsetUtils` | 字符集工具 |
| `RegexUtils` | 正则验证（手机 / 邮箱 / 身份证 / IP） |

### 日期与数字

| 类 | 说明 |
|---|---|
| `LocalDateTimeUtils` | 日期时间格式化 / 计算 / 比较 |
| `NumberUtils` / `BigDecimalUtils` | 数字工具（精度计算 / 百分比 / 格式化） |

### 文件与 IO

| 类 | 说明 |
|---|---|
| `FileUtils` | 文件读写 / 复制 / 删除 / 目录遍历（NIO Path API，资源自动管理） |
| `FileTypeUtils` | 文件类型检测（扩展名 + Magic Number） |
| `FileValidator` | 文件校验（大小 / 类型 / 文件名安全） |
| `ImageUtils` | 图片处理（缩放 / 水印 / 格式转换） |
| `IOUtils` | 流读写 / 关闭 / Base64 转换 |

### 集合与对象

| 类 | 说明 |
|---|---|
| `CollectionUtils` / `ListUtils` / `SetUtils` / `MapUtils` | 集合工具（判空 / 分片 / 去重 / 排序） |
| `ArrayUtils` / `SortUtils` | 数组工具 |
| `ObjectUtils` | 对象工具（默认值 / 深拷贝 / 比较） |
| `BeanCopyUtils` | Bean 属性拷贝（LRU 缓存、忽略字段、null 值处理、Lambda 转换器） |
| `BeanUpdateUtil` | Bean PATCH 语义更新（仅复制非 null 属性，用于部分更新） |
| `BeanCopyOptions` / `Converters` | 拷贝选项与类型转换器 |

### Spring 集成

| 类 | 说明 |
|---|---|
| `SpringContextHolder` | ApplicationContext 持有者（静态获取 Bean，支持注入实例方法） |
| `SpringBeanUtils` | Bean 获取 / 注册 / 注入工具（volatile + ReentrantLock 线程安全） |
| `SpringPropertyUtils` | 配置属性读取工具 |

### 健康检查

| 类 | 说明 |
|---|---|
| `UtilHealthIndicator` | 工具模块健康检查（Snowflake、JVM 内存、BeanCopy 缓存、OkHttp 连接池） |
| `SnowflakeHealthIndicator` | Snowflake ID 生成器健康检查（时钟回拨、workerId、ID 生成验证、分片数） |

### 其他工具

| 类 | 说明 |
|---|---|
| `YamlUtils` | YAML 解析工具 |
| `CursorHelper` | 游标分页编码 / 解码（Keyset Pagination） |
| `HashUtils` | 非加密哈希（CRC32 / MurmurHash32 / Base62 编码解码） |
| `ClassUtils` | 类加载器工具 |
| `ExceptionUtils` | 异常堆栈转字符串 |
| `IpAddrUtils` / `IpInfoUtils` | IP 地址工具 |
| `MessageUtils` | 国际化消息工具 |
| `BeanUpdateUtil` | Bean 属性 PATCH 更新（仅非 null） |

## 自动配置

| 配置类 | 激活条件 | 注册 Bean |
|---|---|---|
| `UtilAutoConfiguration` | 总是激活 | `SpringContextHolder`、`OkHttpClient`、`SnowflakeHealthIndicator`、`UtilHealthIndicator`、`OkHttpCleanupBean` |
| `SnowflakeAutoConfiguration` | `ydsz.util.snowflake.enabled=true`（默认激活） | `SnowflakeUtils` |
| `ThreadPoolMonitorAutoConfiguration` | `ydsz.util.threadpool.monitor.enabled=true`（默认激活），Micrometer 可用时自动注册指标 | `ExecutorService` 指标注册器 |

### 配置属性

| 属性 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `ydsz.util.okhttp.connect-timeout` | Long | 5 | 连接超时（秒） |
| `ydsz.util.okhttp.read-timeout` | Long | 30 | 读取超时（秒） |
| `ydsz.util.okhttp.write-timeout` | Long | 30 | 写入超时（秒） |
| `ydsz.util.okhttp.max-idle-connections` | Integer | 50 | 最大空闲连接数 |
| `ydsz.util.okhttp.keep-alive-duration` | Long | 5 | 连接保持时间（分钟） |
| `ydsz.util.snowflake.enabled` | Boolean | true | 是否启用 Snowflake 自动配置 |
| `ydsz.util.snowflake.worker-id` | Long | - | 工作节点 ID（0-31，仅 `worker-id-source=CONFIG` 时生效） |
| `ydsz.util.snowflake.datacenter-id` | Long | - | 数据中心 ID（0-31） |
| `ydsz.util.snowflake.worker-id-source` | String | ENVIRONMENT_VARIABLE | workerId 来源策略（ENVIRONMENT_VARIABLE / CONFIG / INSTANCE_INDEX） |
| `ydsz.util.snowflake.environment-variable-name` | String | YDSZ_SNOWFLAKE_WORKER_ID | 环境变量名 |
| `ydsz.util.threadpool.monitor.enabled` | Boolean | true | 是否启用线程池监控 |

## 安全规范

### 加密

- **AES**：默认使用 AES-256-GCM 模式（认证加密 AEAD），每次加密生成随机 12 字节 IV
- **RSA**：使用 SHA256withRSA 签名算法 + OAEP 填充模式
- **密钥管理**：密钥通过 `setConfiguredKey()` 注入或环境变量配置，禁止硬编码

### 密码

- **BCrypt**：strength=12（OWASP 推荐至少 10），对应 2^12=4096 轮哈希
- **PBKDF2**：600000 次迭代（OWASP 2023 推荐），迭代次数存储在编码密码中，向后兼容
- **常量时间比较**：所有密码验证使用 `MessageDigest.isEqual()` 防止时序攻击

### HTTP

- **SSL 验证**：默认严格验证，`buildInsecureClient()` 需显式启用且禁止生产环境使用
- **路径安全**：`isSafePath()` / `checkAllowDownload()` 防止路径遍历攻击

## 性能优化

| 组件 | 优化策略 |
|---|---|
| `SnowflakeUtils` | 分片 CAS（shardCount=CPU 核心数），减少锁竞争；序列号耗尽时最小 1ms 等待避免 CPU 忙等 |
| `ContextPropagationUtils` | MDC 传播使用控制字符编码替代 JSON 序列化，零解析开销 |
| `BeanCopyUtils` | PropertyDescriptor / Field 缓存（LRU 1024），避免重复反射 |
| `StringUtils` | `PatternCache` 基于 ConcurrentHashMap + LRU 淘汰，无锁并发读 |
| `ExecutorUtils` | 统一线程名前缀 `ydsz-`，有界队列 + CallerRunsPolicy 防止 OOM |
| `FileUtils` | NIO Path API，`Files.copy` 替代手动流拷贝，`try-with-resources` 自动关闭 |
| `OkHttpUtils` | 连接池复用，请求级超时共享连接池，`executeWithTimeout` 含 write 超时 |

## 依赖

```xml
<dependency>
    <groupId>com.njydsz</groupId>
    <artifactId>ydsz-common-util</artifactId>
</dependency>
```

### 可选依赖

以下依赖为可选（`optional=true`），按需引入：

| 依赖 | 用途 |
|---|---|
| `okhttp3` | OkHttpUtils / HttpClientFactory |
| `org.apache.skywalking` | TracerUtils SkyWalking 集成（反射调用，无编译期依赖） |
| `ip2region` | IpAddrUtils IP 地址库 |
| `yauaa` | User-Agent 解析 |
| `micrometer` | 线程池监控指标 |
| `spring-security-crypto` | PwdUtils BCrypt 支持 |
| `hutool-all` | CryptoUtil 等加解密工具 |
| `bcprov-jdk18on` | BouncyCastle 加密支持 |
| `commons-lang3` | StringUtils 等基础工具 |
| `commons-io` | FileUtils / IOUtils |
| `transmittable-thread-local` | ContextPropagationUtils 上下文传播 |
| `mybatis-plus-core` | SnowflakeUtils ID 生成 |
| `reactor-core` / `spring-webflux` | WebFluxUtils 响应式工具 |

## 变更日志

### 1.3.0 — 架构精简（消除过度设计）

**P0 — 删除零引用文件（-21 文件，-5000 行）**

删除在整个项目中零业务调用的工具类，消除维护负担和认知噪音：

- `merge/MergeUtils` + `Merged`：12 种合并变体（735 行），零调用。需要时用 `stream().collect(groupingBy())` 替代
- `function/FunctionUtils`：JDK 原生 API 薄包装（481 行），零调用
- `hash/BloomFilterUtils`：本地布隆过滤器（299 行），common-redis 已有分布式版本
- `saml/SAMLUtils`：SAML 2.0 协议工具（~250 行），当前使用 OAuth2
- `benchmark/UtilBenchmark`：JMH 基准测试，非生产代码
- `compress/CompressUtils` / `captcha/CaptchaUtils` / `money/MoneyUtils`：零调用
- `xml/DOMUtils` / `system/SystemUtils` / `reflect/ReflectUtils`：零调用，Spring/JDK 已有替代
- `ftp/FtpUtils` + `FtpConfig`：零调用
- `ip/MacAddressUtils` / `url/UrlPathUtils`：零调用
- `validate/ValidateUtils` / `encoding/EncodingUtils`：零调用，Spring Validation + JDK Base64 已覆盖
- `string/StringConvertUtils` / `StringFormatterUtils`：零调用
- `id/SequenceUtils`：依赖已删除的 MacAddressUtils，零外部引用

**P1 — 精简 SPI 层**

- 删除 `security/password/` 整个包（4 文件）：`PasswordEncoder` 接口 + `PasswordEncoderFactory` + `Pbkdf2PasswordEncoder` + `PasswordStrengthEvaluator`。零调用，`PwdUtils` 直接使用 BCrypt/PBKDF2。如需 SPI，应使用 Spring Security 的 `PasswordEncoder`
- 删除 `RetrySupport` 类（零业务调用，推荐使用 Spring Retry 或 Resilience4j）

**P2 — 精简 BeanCopyUtils（-300 行）**

- 移除循环引用检测（IdentityHashMap 逻辑）：业务中从未拷贝自引用对象
- 移除 Map ↔ Bean 转换方法：零调用，JSON 模块已提供 `YdszJson.parseMap`
- 移除嵌套深拷贝：零调用，Converter 层应负责复杂转换
- 保留核心能力：基础属性拷贝 + LRU 缓存 + `copyNonNullProperties` + 选项化拷贝 + Lambda 转换器

**P3 — 精简 HashUtils（-200 行）**

- 删除 Base58 编码/解码：零调用，加密货币场景专用
- 删除一致性哈希环（TreeMap 虚拟节点）：零调用，分布式路由应在 Redis/网关层处理
- 保留：CRC32 + MurmurHash32 + Base62 编码/解码

**P4 — 清理 pom.xml 依赖**

移除仅为已删除文件服务的可选依赖：

| 移除依赖 | 原使用文件 |
|---|---|
| `commons-net` | FtpUtils（已删除） |
| `commons-text` | StringFormatterUtils（已删除） |
| `commons-validator` | ValidateUtils（已删除） |
| `commons-collections4` | MergeUtils（已删除） |
| `dom4j` | DOMUtils（已删除） |
| `openpdf` | 无使用（历史遗留） |
| `jmh-core` / `jmh-generator-annprocess` | UtilBenchmark（已删除） |

### 1.2.0

**P0 — 安全与性能修复**

- `SnowflakeUtils`：修复 `tilNextMillis` 序列号耗尽时 offset=0 导致 CPU 忙等；新增 `getShardCount()` 方法
- `AesUtils`：GCM 加解密委托 `AesGcmCrypto`，消除代码重复；`initKey` 使用共享 `SecureRandom` 替代 `getInstanceStrong()`；ECB/CBC 兼容方法补 `validateKey()` 校验
- `OkHttpUtils`：`executeWithTimeout` 增加 write 超时支持；`close()` 增加日志
- `ContextPropagationUtils`：MDC 传播从 JSON 序列化改为控制字符编码，消除性能瓶颈；移除对 `ydsz-common-json` 的编译期依赖

**P1 — 功能增强**

- `PwdUtils`：PBKDF2 迭代次数从 10000 提升至 600000（OWASP 2023 推荐）；BCrypt 强度文档化
- `BeanCopyUtils`：修复 `MAX_CACHE_SIZE` 注释（LRU 淘淘汰，非全量清空）
- `ExecutorUtils`：修复 3 处行内 FQN 违规（`ArrayBlockingQueue`、`PriorityBlockingQueue`、`Future`）
- `FileUtils`：修复 `calculateHash` InputStream 资源泄漏；`downloadFileToLocal` 使用 NIO `Files.copy`

**P2 — 架构优化**

- `TracerUtils`：使用反射解耦 SkyWalking 硬依赖，模块在无 SkyWalking 时正常降级
- `RetrySupport`：已删除（零业务调用，推荐使用 Spring Retry 或 Resilience4j）

**P3 — 健康检查与文档**

- `UtilHealthIndicator`：新增 BeanCopyUtils 缓存状态 + OkHttp 连接池统计
- `SnowflakeHealthIndicator`：新增 shardCount 详情
- README 全面更新
