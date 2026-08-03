# ydsz-common-util 模块优化评估报告

> 评估日期：2026-08-03
> 评估范围：`ydsz-common-util` 模块（`com.njydsz:ydsz-common-util:1.0.0-SNAPSHOT`）
> 评估方法：源码全量阅读（67 个 main 类 / 29 126 行）+ README / 配置元数据 / 测试代码交叉比对 + 全量引用反查 + 行业主流方案横向对标
> 评估基线：JDK 标准库 / Spring Framework 6 / Spring Security 6 / Micrometer ContextPropagation / 美团 Leaf / 百度 UidGenerator / Twitter Snowflake / 阿里《Java 开发手册》/ OWASP 2023 密码存储指南 / NIST SP 800-38D
> **关键约束**：公司内网开发框架，**尽量不依赖外部框架**（不引入 Hutool / Guava / Apache Tika / Thumbnailator / Commons Imaging / BouncyCastle 等）；自研工具类是合理选择，优化重点在于"内部去重 + 删零引用 + 修 Bug + 补测试"，而非"替换为三方库"。JDK 标准库与 Spring（项目基础框架）可继续复用。

---

## 一、TL;DR：核心结论

`ydsz-common-util` 当前**能力覆盖广**（ID / 加密 / HTTP / 集合 / 文件 / Bean / Spring 集成 13 大领域），但**在以下六个维度存在可落地的优化空间**：

| 维度 | 现状 | 问题严重度 | 内网框架优化方向 |
|---|---|---|---|
| **代码膨胀 + 死代码** | 67 类 / 29K 行，22 类零外部引用（~40%），BeanCopy 缓存声明但永不命中 | **P0 严重** | 删零引用类，工具库 < 12K 行，零死代码 |
| **内部重复** | 集合 4400 行内 CollectionUtils/ListUtils/SetUtils 三类各实现一遍 union/intersection/filter；isEmpty 四处重复 | **P0 严重** | 内部去重，同类能力收敛到单一工具类 |
| **文档-代码漂移** | README 提 3 个不存在的 OkHttp 类；6 个孤儿配置项；2 个孤立测试无法编译 | **P0 严重** | README = 模块契约，元数据与代码同源 |
| **正确性 Bug** | ImageUtils 压缩无效；containsAll 空集语义错误；SnowflakeAutoConfiguration 配置前缀写错；RSA verify 抛异常 | **P0 严重** | 零已知 Bug，关键路径有测试 |
| **安全细节** | getClientIp 无可信代理判断；Cookie 无 SameSite；AesUtils 密钥校验与 AesGcmCrypto 不一致 | **P1 重要** | OWASP / NIST 合规，弱算法 @Deprecated |
| **测试覆盖** | 8/67 类有测试（~12%），PwdUtils/Rsa2Utils/SnowflakeAutoConfiguration 零测试 | **P1 重要** | 核心工具 ≥ 80% 行覆盖 |

模块整体**不存在颠覆性架构问题**，自研工具类在内网框架语境下**合理**，主要风险是**长期"做大而全 + 内部重复"导致认知负担 + 维护成本失控**。建议按 P0 → P1 → P2 → P3 分 4 个阶段落地。

---

## 二、事实核查：现状盘点

### 2.1 模块实际规模与包分布

| 包 | 文件数 | 行数 | 外部引用情况 |
|---|---|---|---|
| `string` | 2 | 2 142 | StringUtils 高引用，CharsetUtils **零引用** |
| `collection` | 4 | 4 405 | 仅 CollectionUtils + MapUtils 被引用，ListUtils/SetUtils **零引用** |
| `file` | 5 | 3 129 | FileUtils/FileTypeUtils 部分引用，FileValidator/ImageUtils/MediaType **零引用** |
| `id` | 9 | 2 141 | SnowflakeUtils/TracerUtils/UUIDUtils 高引用 |
| `security` | 5 | 1 531 | AesUtils/PwdUtils/Rsa2Utils 多处引用 |
| `number` | 2 | 1 779 | NumberUtils/BigDecimalUtils **均零引用** |
| `array` | 2 | 1 535 | ArrayUtils/SortUtils **均零引用** |
| `io` | 1 | 1 106 | 仅 1 处外部引用（LocalStorage） |
| `regex` | 1 | 1 124 | **零外部引用**（仅 test） |
| `date` | 1 | 1 234 | **零外部引用**（仅 test） |
| `bytes` | 2 | 1 192 | 仅内部引用 |
| `exception` | 1 | 526 | **零外部引用** |
| `object` | 1 | 419 | **零外部引用** |
| `classloader` | 1 | 477 | 仅 1 处外部引用 |
| `http` | 5 | 1 023 | 多处引用 |
| `auth` | 4 | 933 | 多处引用 |
| `ip` | 2 | 928 | 仅 IpAddrUtils 引用，IpInfoUtils **零引用** |
| `concurrent` | 2 | 727 | ExecutorUtils 4 处，ContextPropagationUtils **零引用** |
| `spring` | 3 | 767 | SpringPropertyUtils/SpringBeanUtils **零外部引用** |
| `bean` | 5 | 769 | BeanCopyUtils/BeanUpdateUtil 高引用 |
| `config` | 2 | 232 | AutoConfiguration |
| `health` | 1 | 130 | — |
| `message` | 1 | 183 | 多处引用 |
| `url` | 1 | 66 | 10+ 处引用 |
| `yaml` | 1 | 75 | 1 处引用 |
| `hash` | 1 | 272 | **零外部引用**（仅 test） |
| 根包 | 2 | 281 | BeanUpdateUtil 7 处引用，CursorHelper **零引用** |
| **合计** | **67** | **29 126** | — |

### 2.2 完全无外部引用的工具类（22 个，~11 798 行，占 ~40%）

| 类 | 行数 | 证据 |
|---|---|---|
| `array.ArrayUtils` / `array.SortUtils` | 842 / 693 | 全库零引用 |
| `bytes.ByteUtils` | 1 138 | 仅 util 内部引用 |
| `exception.ExceptionUtils` | 526 | 全库零引用 |
| `object.ObjectUtils` | 419 | 全库零引用 |
| `string.CharsetUtils` | 396 | 全库零引用 |
| `hash.HashUtils` | 272 | 仅 test |
| `CursorHelper` | 179 | 仅 test |
| `date.LocalDateTimeUtils` | 1 234 | 仅 test |
| `regex.RegexUtils` | 1 124 | 仅 test |
| `number.NumberUtils` / `BigDecimalUtils` | 826 / 953 | 全库零引用 |
| `collection.ListUtils` / `SetUtils` | 1 001 / 856 | 全库零引用 |
| `concurrent.ContextPropagationUtils` | 321 | 全库零引用 |
| `ip.IpInfoUtils` | 341 | 全库零引用 |
| `file.FileValidator` / `ImageUtils` / `MediaType` | 379 / 789 / 378 | 全库零引用 |
| `spring.SpringPropertyUtils` / `SpringBeanUtils` / `SpringContextHolder` | 330 / 241 / 196 | 业务零调用；SpringPropertyUtils 因无 autoconfig 注入 Environment，所有方法必抛 IllegalStateException（死代码） |

### 2.3 README / 元数据 / 测试与代码的漂移

| 漂移类型 | 详情 |
|---|---|
| **幽灵类** | README 第 41-43 行 / 第 134 行 / 第 319 行 / 第 339 行声称的 `OkHttpUtils` / `HttpClientFactory` / `OkHttpProperties` / `OkHttpClient` Bean / `OkHttpCleanupBean` 在 src/main 中**完全不存在**；`UtilAutoConfiguration` 实际只注册 3 个 Bean（SpringContextHolder + 2 个 HealthIndicator） |
| **孤儿配置项** | `additional-spring-configuration-metadata.json` 11 个属性中 6 个为孤儿：5 个 `ydsz.util.okhttp.*`（对应类不存在）+ 1 个 `ydsz.util.threadpool.monitor.enabled`（代码无 `@ConditionalOnProperty` 读取） |
| **方法名错误** | README 第 266 行示例 `BeanUpdateUtil.copyNonNullProperties(...)`，实际方法名为 `copyNonNull` |
| **孤立测试** | `CaptchaUtilsTest` / `EncodingUtilsTest` 引用的 `CaptchaUtils` / `EncodingUtils` 在 main 中不存在，无法编译 |
| **类注释自述漂移** | `UtilAutoConfiguration` 第 16 行 javadoc 称"注册雪花 ID 生成器、Tracer、加密工具、Bean 拷贝器、断言工具"，实际注册的 Bean 与此描述完全不符 |

---

## 三、过度设计 / 内部重复清单

> **定性调整说明**：在"公司内网框架不依赖外部库"约束下，自研 StringUtils / CollectionUtils / FileUtils / ImageUtils 等**不构成"重复造轮子"问题**——这是内网框架的合理选择。本章关注的是**模块内部的重复**（同一逻辑多类各实现一遍）、**与 JDK/Spring 内置能力的重复**（JDK/Spring 是项目基础框架，复用不算引入外部依赖）、以及**过度设计**（声明但未使用的缓存 / 无意义包装）。

### 3.1 模块内部重复（同类能力散布到多个工具类）

| 重复点 | 散布位置 | 重复方法 | 处理建议 |
|---|---|---|---|
| 集合运算 | `CollectionUtils:709-791` / `ListUtils:435-527` / `SetUtils:363-467` | `union` / `intersection` / `difference` / `symmetricDifference` 三类各实现一遍 | 统一到 `CollectionUtils`，ListUtils/SetUtils 仅保留 List/Set 特有方法（如 `partition` / `powerSet`） |
| 集合过滤 | `CollectionUtils:544` / `ListUtils:578` / `SetUtils:538` | `filter` 三份实现 | 同上，统一到 `CollectionUtils.filter` |
| isEmpty 判空 | `ObjectUtils:89-112` / `CollectionUtils:105` / `ArrayUtils` / `StringUtils` | 四处各自实现，`ObjectUtils.isEmpty` 已统一处理 Collection/Map/Array/Iterable/Optional/CharSequence | 保留 `ObjectUtils.isEmpty` 作为唯一入口，其余三类删除 isEmpty |
| 子集截取 | `CollectionUtils.subList:957-975` vs `ListUtils.subList:930-935` | 一个返回 `subList` 视图，一个返回 `new ArrayList` 副本 | 统一语义（建议都返回副本，避免视图陷阱） |
| 前N条 | `CollectionUtils.first:912-922` vs `ListUtils.limit:945-951` | 同上，一个视图一个副本 | 同上 |
| hex 编解码 | `bytes.HexUtils` / `security.AesUtils:166-175` | AesUtils 的 `bytesToHex` / `hexToBytes` 单行委托 HexUtils | 删除 AesUtils 中的委托方法，直接用 HexUtils |
| Spring 上下文 | `spring.SpringBeanUtils` / `spring.SpringContextHolder` | Holder 全部静态方法委托 Utils，两层冗余 | 合并为单个 `ApplicationContextAware` Bean |
| Bean 拷贝 | `bean.BeanCopyUtils` / `BeanUpdateUtil`（根包） | `copyPropertiesWithIgnoreNull` → `BeanUpdateUtil.copyNonNull` → Spring BeanUtils 三层调用 | 合并到 `bean` 包，删根包 BeanUpdateUtil |

### 3.2 与 JDK / Spring 内置能力的重复（可复用，不算引入外部依赖）

| 本模块（行数） | JDK / Spring 内置等价能力 | 处理建议 |
|---|---|---|
| `FileUtils.readAllBytes/writeString/copyFile/listFiles` | JDK 11+ `Files.readAllBytes` / `Files.writeString` / `Files.copy` / `Files.newDirectoryStream` | 删除自研方法，直接委托 `Files.*` |
| `FileUtils.downloadFile` 的 `HttpURLConnection` | JDK 11+ `java.net.http.HttpClient` | 替换为 `HttpClient`（JDK 内置，无外部依赖） |
| `HexUtils` | JDK 17+ `java.util.HexFormat` | 删除自研，直接用 `HexFormat.of()`（项目已是 JDK 17+，见 Rsa2Utils 已用） |
| `FileTypeUtils.detectMimeType` | JDK 7+ `Files.probeContentType(path)` | 保留 magic number 表（`probeContentType` 依赖 OS，内网 Linux 可能不准），但修复 webp/avi/wav 共用 RIFF 魔数问题 |
| `SpringContextHolder` + `SpringBeanUtils` | Spring `ApplicationContextAware` 标准做法 | 合并为一个 Bean，实现 `ApplicationContextAware` |
| `ContextPropagationUtils` | Micrometer ContextPropagation（项目已 optional 依赖 micrometer-core） | 评估：若 Reactor/Spring Async 已用 Micrometer，则删除自研；否则保留自研但修复性能反模式 |

### 3.3 死代码 / 无效缓存 / 过度设计

| 文件:行 | 类型 | 事实 |
|---|---|---|
| `BeanCopyUtils.java:54-73` | 死缓存 | `PROPERTY_CACHE` / `FIELD_CACHE`（synchronized LinkedHashMap LRU 1024）声明后**仅在 `clearCache` / `getCacheStats` 中被引用**，实际拷贝路径直接委托 Spring BeanUtils，缓存永不填充永不命中 |
| `Converters.java:31-32` | ThreadLocal 残留 | `DATE_FORMAT_CACHE` 为 `ThreadLocal<Map<String, SimpleDateFormat>>`，但全类未使用 SimpleDateFormat（均用 DateTimeFormatter），线程池场景内存泄漏 |
| `Converters.java:126-134` | 缓存缺失 | `localDateTimeToString` 每次新建 `DateTimeFormatter`，应缓存 pattern → formatter（`ConcurrentHashMap`） |
| `ExecutorUtils.java:286-297` | 冗余包装 | `RunnableComparatorAdapter` 包装 `Comparator<Runnable>` 为 `Comparator<Runnable>`，无转换逻辑 |
| `ExecutorUtils.java:175-184` | 语义错位 | VirtualThread fallback 后线程名前缀仍是 `virtual-` |
| `BeanCopyOptions` | API 表面积过大 | 同时存在 `@Builder` + `@Data` + `withXxx` 链式方法，`@Data` 的 setter 会绕过 Builder 默认值约束 |
| `ThreadPoolMonitorAutoConfiguration:47-54` | 永真条件 + 零调用 | `@ConditionalOnMissingBean` 作用于同文件内部类 `ThreadPoolMonitor`，外部无代码创建该类型，条件永真；且全库零调用 |
| `ObjectUtils.cast:252-257` | 类型不安全 | `(Class<T>) obj.getClass()` 泛型擦除，javadoc 自承"运行时不会立即抛出 ClassCastException" |
| `SpringPropertyUtils` | 死代码 | 因无 autoconfig 调用 `setEnvironment`，`environment` 永远为 null，所有方法必抛 `IllegalStateException` |

### 3.4 自研 Snowflake vs 行业方案

| 维度 | 本项目 | Twitter Snowflake | 百度 UidGenerator | 美团 Leaf |
|---|---|---|---|---|
| 并发优化 | 分片 AtomicLong + CAS | synchronized | RingBuffer 预分配 | synchronized |
| workerId 分配 | Registry SPI + IP 哈希 + ENV + CONFIG | 手动配置 | DB 自增 / UUID | ZK 顺序节点 |
| 多模式 | 仅 snowflake | 仅 snowflake | 仅 snowflake | segment + snowflake |
| Registry 实现 | **仅 SPI 接口，无内置实现** | N/A | 内置 DB | 内置 ZK |
| 时钟回拨 | 5ms 容忍 + 5000ms 等待 | 无 | 依赖 RingBuffer | 等待下毫秒 |

**结论**：分片 CAS 优化是亮点，但 `WorkerIdRegistry` 无内置实现（业务需自研 Redis/ZK 实现），落地成本高于美团 Leaf。

---

## 四、正确性 Bug 清单（P0）

| 文件:行 | Bug | 影响 |
|---|---|---|
| `ImageUtils.java:681-706` | `compressImage` 的 `quality` 参数**完全未使用**，方法体仅 `ImageIO.write` 写出原图 | 调用方压缩参数无效，输出文件与原图等大 |
| `ListUtils.java:649-659` / `SetUtils.java:609-619` | `containsAll(elements)` 当 `elements` 为 null/空时返回 **false**，与 `Collection.containsAll` 数学语义（空集是任意集合子集，应返回 true）不符 | 业务判空逻辑错误 |
| `SnowflakeAutoConfiguration.java:177` | `environment.getProperty("ydsz.snowflake.datacenterId")` 前缀错误，应为 `ydsz.util.snowflake.datacenter-id` | 该配置项永远读不到，静默回退到主机名哈希 |
| `Rsa2Utils.java:259-270` | `verify` 任何异常（含验签失败）都抛 `RuntimeException`，不返回 `false` | 与"验签失败返回 false"的安全惯例不符，调用方需 try-catch 区分"验签失败"与"真的异常" |
| `MapUtils.java:1033-1044` | `deepCopy` 注释说"不复制 Map（性能优先）"，实际 `new HashMap` 逐层复制 | 注释与实现矛盾，误导调用方 |
| `MapUtils.java:1142-1151` | `toStringObjectMap` 注释说"不复制"，实际 `new LinkedHashMap` 逐条 put | 同上 |
| `CollectionUtils.subList:957-975` vs `ListUtils.subList:930-935` | 同包内同语义，一个返回 `subList` 视图一个返回 `new ArrayList` 副本 | 调用方易踩坑，修改视图影响原集合 |
| `CollectionUtils.first:912-922` vs `ListUtils.limit:945-951` | 同上，一个视图一个副本 | 同上 |
| `ContextPropagationUtils.java:174-182` | `registerContextProvider` 未 synchronized，与 `registerMdcContext` 的 synchronized 不一致 | 并发注册同名 provider 存在竞态 |
| `CaptchaUtilsTest` / `EncodingUtilsTest` | 引用的 `CaptchaUtils` / `EncodingUtils` 类不存在 | 测试无法编译 |

---

## 五、安全合规清单

### 5.1 加密 / 密码

| 文件:行 | 问题 | OWASP / NIST 对照 |
|---|---|---|
| `AesUtils.java:128-132` | `validateKey` 仅校验 `hexKey.length() >= 32`（≥16 字节），与 `AesGcmCrypto` 构造器严格校验 16/24/32 不一致 | 传 20 字节 hex 密钥时 AesUtils 通过但 AesGcmCrypto 抛异常 |
| `AesUtils.java:96,114-123` | 未配置 `configuredKey` 时自动生成随机密钥，进程重启后无法解密旧密文 | 生产环境应强制配置密钥 |
| `DigestUtils.java:138-181` | `md5` / `md5Hex` / `sha1` / `sha1Hex` 未标注 `@Deprecated` | OWASP：MD5/SHA1 不应用于安全场景 |
| `AesUtils.java:229-277` | `decryptECBCompat` / `decryptCBCCompat` 仅 `log.warn` 未 `@Deprecated` | ECB 模式不安全 |
| `PwdUtils.java:187` | `encodeWithSalt` 格式 `salt:hash` **不存储迭代次数**，固定 600000 | 未来调整 `DEFAULT_ITERATIONS` 后旧密码无法验证 |
| `PwdUtils.java:132-148` | `verifyPasswordWithSha256Salt` 单次 SHA-256，未 `@Deprecated` | OWASP：单次哈希不安全 |
| `PwdUtils.java` | 未提供 Argon2 / scrypt | OWASP 2023 首选 Argon2id |
| `Rsa2Utils.java:93-95` | 最小允许 1024 位密钥 | NIST：1024 位已废弃，最小 2048 |
| `Rsa2Utils.java:97-98` | `KeyPairGenerator.initialize(keySize)` 未传 SecureRandom | 使用 JDK 默认，可控性弱 |

### 5.2 Web 安全

| 文件:行 | 问题 |
|---|---|
| `ServletUtils.java:231-268` | `getClientIp` 信任 `X-Forwarded-For` / `X-Real-IP`，无可信代理网段配置；应用层若直接信任做鉴权可被绕过 |
| `CookieUtils.java:71-85` | 未设置 `SameSite` 属性；`setSecure` 依赖 `request.getScheme()`，反代场景失效（应读 `X-Forwarded-Proto`） |
| `WebFluxUtils.java:74-81` | `errorResponse` 返回 HTTP 200 + 业务错误码，与 RESTful 语义不符 |
| `ImageUtils.java:711-736` | `downloadImagesAsync` 失败仅 `log.error` 不抛异常，`CompletableFuture` 永远返回 true，调用方无法感知失败 |

### 5.3 workerId 环境变量名不一致

| 路径 | 读取的环境变量名 |
|---|---|
| `SnowflakeUtils.java:363-364`（静态 getInstance 路径） | `SNOWFLAKE_WORKER_ID` |
| `SnowflakeProperties.java:79`（AutoConfiguration 路径） | `YDSZ_SNOWFLAKE_WORKER_ID` |

两条路径读取的环境变量名**不互通**，手动 `getInstance()` 与 AutoConfiguration 走不同分支会导致 workerId 不一致。

---

## 六、性能反模式清单

| 文件:行 | 问题 | 改进 |
|---|---|---|
| `ListUtils.java:454-466` | `intersection` 去重用 `result.contains`，O(n²) | 用 LinkedHashSet |
| `CollectionUtils.java:1181-1187` | `parallelConvertList` 强制 `parallelStream` 共享 commonPool，无法控制并发度 | 提供 isParallel 开关 |
| `ContextPropagationUtils.java:218-247` | 每次 `wrap` 三次 Map 分配 + 三次遍历 | 复用 ContextSnapshot |
| `SpringBeanUtils.java:211-221` | `containsBean(Class)` 用 try-catch 探测 Bean 是否存在 | 用 `getBeanNamesForType` |
| `UrlUtils.java:258,261` | `replaceAll` 正则替换路径，长 URL 性能差 | 改 `replace` |
| `FileUtils.java:996-1032` | 使用过时 `HttpURLConnection` | Java 11+ `HttpClient` |
| `FileUtils.java:1014` | `url.replaceAll(path + "/", "")`，path 含正则元字符会出错 | 改 `replace` |
| `ImageUtils.java:146-174` | `readLocalFileFast` 用 FileInputStream + Channel 手工循环，比 `Files.readAllBytes` 更慢 | 直接 `Files.readAllBytes` |
| `Converters.java:126-134` | `localDateTimeToString` 每次新建 `DateTimeFormatter` | 缓存 pattern → formatter |
| `ExecutorUtils.java:269` | `PriorityBlockingQueue` 构造参数是初始容量，实际**无界** | 加有界包装或文档标注 |
| `ExecutorUtils.java:175-184` | VirtualThread fallback 后线程名前缀仍是 `virtual-`，语义错位 | fallback 时重置前缀 |

---

## 七、资源泄漏清单

| 文件:行 | 问题 |
|---|---|
| `ImageUtils.java:53` | 静态 `EXECUTOR_SERVICE` 类加载即创建，不自动 shutdown，需手动 `ImageUtils.shutdown()`，易遗忘 |
| `ImageUtils.java:257-274, 375-401` | `Graphics2D` 异常时未 `dispose()`，资源泄漏 |
| `ImageUtils.java:264` | `TYPE_INT_RGB` 无 alpha 通道，PNG 透明背景变黑 |
| `ResponseUtils.java:46-52` | 手动 `close()` Servlet 容器管理的 `ServletOutputStream`，后续 commit 可能失败 |
| `Converters.java:31-32` | `DATE_FORMAT_CACHE` ThreadLocal 残留，线程池场景内存泄漏 |

---

## 八、测试覆盖差距

### 8.1 现状
- src/main：67 类 / 29 126 行
- src/test：10 个测试文件，其中 **2 个孤立测试无法编译**
- 实际可运行测试覆盖 8 类，覆盖率约 **12%**

### 8.2 关键未覆盖类（按风险排序）

| 类 | 行数 | 风险 |
|---|---|---|
| `PwdUtils` | 270 | 密码哈希核心，零测试 |
| `Rsa2Utils` | 350 | 非对称加密，零测试 |
| `AesUtils` | 319 | ECB/CBC 兼容解密、configuredKey 双检锁未测 |
| `SnowflakeAutoConfiguration` | 219 | workerId 解析优先级、Registry 回退未测 |
| `SnowflakeHealthIndicator` | 80 | 零测试 |
| `UUIDUtils` / `RandomUtils` | 835 | v4/v7/ULID/NanoID 未测 |
| `BeanCopyUtils` | 351 | 8 处外部引用，零测试 |
| `ServletUtils` / `UrlUtils` / `CookieUtils` | 700+ | 多处引用，零测试 |
| `ExecutorUtils` | 406 | 4 处引用，零测试 |
| `ContextPropagationUtils` | 321 | 零测试 |

---

## 九、分阶段优化建议

### 阶段一：P0 止血（1-2 周）

#### P0-1 修复正确性 Bug
- `ImageUtils.compressImage`：实际使用 `quality` 参数（ImageWriter + `ImageWriteParam.setCompressionQuality`）
- `ListUtils.containsAll` / `SetUtils.containsAll`：空入参返回 `true`
- `SnowflakeAutoConfiguration:177`：前缀改为 `ydsz.util.snowflake.datacenter-id`
- `Rsa2Utils.verify`：验签失败返回 `false`，仅其他异常抛 `RuntimeException`
- `ContextPropagationUtils.registerContextProvider`：加 `synchronized`
- 删除 `CaptchaUtilsTest` / `EncodingUtilsTest` 两个孤立测试

#### P0-2 文档-代码对齐
- README 删除不存在的 `OkHttpUtils` / `HttpClientFactory` / `OkHttpProperties` / `OkHttpCleanupBean` 相关章节
- `UtilAutoConfiguration` javadoc 改为实际注册的 3 个 Bean
- `additional-spring-configuration-metadata.json` 删除 6 个孤儿属性（5 个 okhttp + 1 个 threadpool.monitor.enabled）
- README 第 266 行方法名改为 `copyNonNull`
- 修正 `MapUtils.deepCopy` / `toStringObjectMap` 注释与实现矛盾

#### P0-3 删除死代码
- 删除 `BeanCopyUtils.PROPERTY_CACHE` / `FIELD_CACHE` 及 `clearCache` / `getCacheStats`（缓存从未生效，暴露监控反而误导）
- 删除 `Converters.DATE_FORMAT_CACHE` ThreadLocal
- 删除 `ExecutorUtils.RunnableComparatorAdapter`
- 评估 `ThreadPoolMonitor`：全库零调用，建议删除或改为 `@ConditionalOnProperty` 显式开启

### 阶段二：P1 收敛（2-4 周）

#### P1-1 删除 / 标注零引用工具
对 22 个零外部引用类逐一评估（**不引入外部库替代，纯删或保留**）：

**直接删除**（无 test 引用、无反射调用可能的纯死代码）：
- `array.ArrayUtils` / `array.SortUtils`（isEmpty 等已被 ObjectUtils/CollectionUtils 覆盖）
- `exception.ExceptionUtils`（JDK `Throwable.getStackTraceString` + SLF4J 已够用）
- `object.ObjectUtils`（isEmpty 已统一到此处，但整体零引用，评估仅保留 isEmpty 后整体删除并入 CollectionUtils）
- `string.CharsetUtils`（JDK `Charset.forName` 已够用）
- `number.NumberUtils` / `BigDecimalUtils`（零引用，保留 BigDecimalUtils 评估，删 NumberUtils）
- `collection.ListUtils` / `SetUtils`（通用方法并入 CollectionUtils，特有方法保留）
- `concurrent.ContextPropagationUtils`（零引用，评估后保留自研或删）
- `ip.IpInfoUtils`（仅 IpAddrUtils 被引用，IpInfoUtils 零引用）
- `file.FileValidator` / `MediaType`（零引用，评估并入 FileTypeUtils 或删）
- `spring.SpringPropertyUtils`（必抛 IllegalStateException 的死代码，直接删）

**保留并补测试**（有 test 引用但无业务引用，属"潜在能力"）：
- `date.LocalDateTimeUtils`
- `regex.RegexUtils`
- `hash.HashUtils`
- `CursorHelper`

**保留自研但修复 + 补测试**（有业务价值但零引用，因 Bug 或未推广）：
- `file.ImageUtils`（修 compress bug + 资源泄漏后保留，不引入 Thumbnailator）

**内敛化**（业务零调用但作为内部实现保留）：
- `bytes.ByteUtils` / `HexUtils` 评估改为 package-private（或 HexUtils 直接用 JDK `HexFormat` 替换）
- `spring.SpringBeanUtils` 与 `SpringContextHolder` 合并为一个

#### P1-2 弱算法 @Deprecated
- `DigestUtils.md5` / `md5Hex` / `sha1` / `sha1Hex` 加 `@Deprecated` 并 javadoc 标注替代方案（sha256/sha512）
- `AesUtils.decryptECBCompat` / `decryptCBCCompat` 加 `@Deprecated`
- `PwdUtils.verifyPasswordWithSha256Salt` / `encodeWithSalt` 加 `@Deprecated`，推荐 `encodePBKDF2`
- **不引入 Argon2**（需 BouncyCastle 外部依赖，违反内网框架约束）；BCrypt strength=12 + PBKDF2 600000 已符合 OWASP 2023

#### P1-3 安全细节修复
- `AesUtils.validateKey` 与 `AesGcmCrypto` 构造器统一为 16/24/32 严格校验
- `Rsa2Utils` 最小密钥长度提至 2048，`KeyPairGenerator` 显式传 SecureRandom
- `ServletUtils.getClientIp` 增加"可信代理网段"配置项（`ydsz.util.ip.trusted-proxies`）
- `CookieUtils.addCookie` 支持 `SameSite` 属性，`setSecure` 读 `X-Forwarded-Proto`
- `PwdUtils.encodeWithSalt` 格式改为 `salt:iterations:hash`（与 `encodePBKDF2` 一致）
- 统一 workerId 环境变量名为 `YDSZ_SNOWFLAKE_WORKER_ID`

#### P1-4 资源泄漏修复
- `ImageUtils` 所有 `createGraphics` 改 try-finally `dispose()`
- `ImageUtils.scaleImage` 根据源图类型选 `TYPE_INT_ARGB`
- `ImageUtils.EXECUTOR_SERVICE` 改为 `@PreDestroy` 或注册 `DisposableBean`
- `ResponseUtils.write` 移除手动 `close()`，交由容器管理
- `ImageUtils.downloadImagesAsync` 失败时 `completeExceptionally`

### 阶段三：P2 架构收敛（4-8 周）

> **核心原则**：不引入外部库，通过"删零引用 + 内部去重 + 复用 JDK/Spring 内置"实现瘦身。

#### P2-1 工具层瘦身（目标 12K 行，从 29K 行↓）
模块从 29K 行 → 12K 行，策略：

| 策略 | 适用对象 | 处理方式 |
|---|---|---|
| **删除零引用类** | 22 个零外部引用类（见 2.2） | 13 个纯死代码直接删；4 个仅 test 引用的评估保留+补测试或删；5 个内敛化为 package-private |
| **内部去重** | 集合 4400 行三类重复 | 统一 union/intersection/filter 到 `CollectionUtils`；`ListUtils`/`SetUtils` 仅保留 List/Set 特有方法 |
| **复用 JDK 内置** | `FileUtils` 的 readAllBytes/writeString/copyFile/listFiles | 删自研，直接委托 `Files.*`（JDK 11+ 内置） |
| **复用 JDK 内置** | `HexUtils` 54 行 | 删自研，直接用 `HexFormat.of()`（JDK 17+ 内置） |
| **复用 JDK 内置** | `FileUtils.downloadFile` 的 `HttpURLConnection` | 替换为 `java.net.http.HttpClient`（JDK 11+ 内置） |
| **复用 Spring 内置** | `SpringContextHolder` + `SpringBeanUtils` 合并 | 单个 `ApplicationContextAware` Bean |
| **保留自研** | StringUtils / CollectionUtils / FileUtils / ImageUtils / FileTypeUtils / IOUtils / ByteUtils | 自研合理，但需修 Bug + 补测试 + 删内部重复方法 |
| **评估删除** | `ContextPropagationUtils`（零引用） | 若 Micrometer 已在类路径（项目已 optional 依赖），评估用 Micrometer ContextPropagation；否则保留自研但修性能反模式 |

**保留的核心自研能力**（内网框架胶水层，约 12K 行）：
- ID 生成：Snowflake（分片 CAS 亮点）/ UUIDUtils / RandomUtils / TracerUtils
- 安全：AesGcmCrypto / AesUtils / PwdUtils / Rsa2Utils / DigestUtils
- Bean：BeanCopyUtils（去死缓存）/ BeanCopyOptions / Converters
- 集合：CollectionUtils（统一入口）/ MapUtils（去重后）
- 字符串：StringUtils（保留，不引入 commons-lang3）
- 文件：FileUtils（委托 JDK NIO）/ FileTypeUtils（修魔数）/ FileValidator
- HTTP：ServletUtils / UrlUtils / CookieUtils / ResponseUtils / WebFluxUtils
- 并发：ExecutorUtils（修 Bug）
- Spring：合并后的 SpringContextHolder
- Auth：AuthInfo / YdszAuthInfo / AuthInfoUtils / RequestHolder
- 其他：YamlUtils / UrlPathUtils / IpAddrUtils / ClassUtils / MessageUtils / CursorHelper

#### P2-2 职责越界剥离
| 类 | 当前位置 | 建议迁移 |
|---|---|---|
| `UtilAutoConfiguration` / `SnowflakeAutoConfiguration` / `ThreadPoolMonitorAutoConfiguration` | util.config | 保留（AutoConfiguration 入口合理） |
| `SnowflakeHealthIndicator` / `UtilHealthIndicator` | util.health / util.id | 合并冗余检查（两者重复暴露 workerId/datacenterId） |
| `WorkerIdRegistry` SPI | util.id | 保留，**补一个基于 Redis 的内置实现**（内网常用 Redis，参考美团 Leaf ZK 实现思路） |
| `YdszAuthInfo` | util.auth | 迁至 `ydsz-common-auth`（业务模型不应在 util） |
| `MessageUtils` | util.message | 依赖 AuthInfoUtils，迁至 `ydsz-common-web` 或 `ydsz-common-i18n` |
| `BeanUpdateUtil` / `CursorHelper`（根包） | util 根包 | `BeanUpdateUtil` 并入 `bean` 包（与 BeanCopyUtils 合并）；`CursorHelper` 并入 `collection` 或删除 |
| `ImageUtils` | util.file | **保留自研**（不引入 Thumbnailator），但修复 compress bug + Graphics2D 泄漏 + TYPE_INT_RGB 透明度 + EXECUTOR_SERVICE 生命周期，并补测试 |
| `FileTypeUtils` | util.file | **保留自研 magic number 表**（`Files.probeContentType` 依赖 OS，内网 Linux 不准），但修复 webp/avi/wav 共用 RIFF 魔数问题 |
| `FileValidator` | util.file | 零引用，评估删除或并入 FileTypeUtils |

#### P2-3 集合工具内部去重（不引入外部库）
- 删除 `ListUtils` / `SetUtils` 中的通用方法（union/intersection/filter/containsAll/subList），统一到 `CollectionUtils`
- `ListUtils` 仅保留 List 特有方法（`partition` / `flatten` / `reverse` / `shuffle`）
- `SetUtils` 仅保留 Set 特有方法（`powerSet` / `cartesianProduct`）
- `CollectionUtils` 仅保留项目特有方法，删除与 `ObjectUtils.isEmpty` 重复的 isEmpty
- `MapUtils` 删除与 CollectionUtils 重复的通用方法，保留 `getString`/`getInteger` 等窄化类型方法
- 统一 `subList` / `first` 语义（建议都返回副本，避免视图陷阱）
- 修复 `ListUtils.containsAll` / `SetUtils.containsAll` 空集语义错误
- 修复 `ListUtils.intersection` O(n²) 性能问题（用 LinkedHashSet 去重）
- 修复 `MapUtils.deepCopy` / `toStringObjectMap` 注释与实现矛盾

### 阶段四：P3 体验与质量（持续）

#### P3-1 测试补全（目标核心类 80% 行覆盖）
优先级：
1. `PwdUtils` / `Rsa2Utils` / `AesUtils`（安全核心）
2. `SnowflakeAutoConfiguration`（workerId 解析优先级、Registry 回退、各 source 分支）
3. `BeanCopyUtils` / `BeanUpdateUtil`（8 处业务引用）
4. `UUIDUtils` / `RandomUtils`（v7/ULID/NanoID 唯一性 + 随机性）
5. `ServletUtils` / `UrlUtils` / `CookieUtils`
6. `ExecutorUtils`（线程池边界、VirtualThread、PriorityQueue）

#### P3-2 可观测性
- `BeanCopyUtils` 删除缓存后，`UtilHealthIndicator` 同步删除 `beanCopy.fieldCacheSize` / `propertyCacheSize` 指标
- `UtilHealthIndicator` 删除 OkHttp 相关检查（类不存在）
- `SnowflakeHealthIndicator` 与 `UtilHealthIndicator` 去重，避免重复暴露 workerId/datacenterId
- `ExecutorUtils` 创建的线程池自动注册到 `ThreadPoolMonitor`（当前需业务手动调用）

#### P3-3 ArchUnit 守护
- 规则：util 模块不得依赖业务模块
- 规则：util 模块不得出现 `@Component` / `@Service` / `@Repository`（仅 `@AutoConfiguration` / `@ConfigurationProperties`）
- 规则：util 模块每个类行数 ≤ 800（当前 StringUtils 1746 / CollectionUtils 1301 超标）

---

## 十、与主流大厂规范对照速查（内网框架语境）

| 维度 | 本项目 | 阿里规范 | Spring 官方 | 内网框架结论 |
|---|---|---|---|---|
| 工具类库规模 | 29K 行 | — | — | 删零引用 + 内部去重后 < 12K 行 |
| 自研工具类 | 大量自研 | 推荐复用 Apache Commons | — | **内网框架自研合理**，重点在去重+测试 |
| 内部重复 | 集合三类各实现一遍 union/intersection | "同类能力单一入口" | — | 需统一到 CollectionUtils |
| 与 JDK 重复 | FileUtils 部分方法与 `Files.*` 重复 | "优先用 JDK 标准库" | — | 复用 JDK 内置（不算外部依赖） |
| 弱算法 | MD5/SHA1 未 `@Deprecated` | 禁用 MD5 | — | 需标注 |
| 密码哈希 | BCrypt strength=12 + PBKDF2 600000 | — | strength=10 | 符合 OWASP，不引入 Argon2（避免 BouncyCastle） |
| AES 模式 | GCM 默认 | — | — | 优于 Hutool 默认 ECB |
| Snowflake | 分片 CAS + Registry SPI | — | — | 优于 Hutool，缺内置 Registry 实现 |
| 文档-代码一致 | README 漂移 8 处 | "文档与代码同步" | — | 需修复 |
| 测试覆盖 | ~12% | 核心 ≥ 80% | — | 需补全 |
| 外部依赖 | commons-lang3/commons-io optional | — | — | **评估移除**（内网框架约束，自研已覆盖） |

---

## 十一、不建议改动的部分（亮点）

以下设计**优于行业平均水平**，应保留：

1. **Snowflake 分片 CAS 优化**：`AtomicLong[] shardStates` + `threadId & shardMask` 路由，无锁并发，优于 Twitter/百度的 synchronized / RingBuffer
2. **AES-GCM 默认**：默认 AEAD 认证加密，每次随机 12 字节 IV，优于 Hutool 默认 ECB
3. **BCrypt strength=12**：高于 Spring Security 默认 10
4. **PBKDF2 600000 迭代**：符合 OWASP 2023
5. **常量时间比较**：`MessageDigest.isEqual` 全链路使用
6. **NanoID / ULID / UUID v7**：覆盖主流短 ID 方案
7. **WorkerIdRegistry SPI + 心跳续约**：设计完整（缺内置实现）

---

## 十二、落地优先级总表

| 优先级 | 任务 | 预估工作量 | 风险 |
|---|---|---|---|
| **P0-1** | 修复 6 个正确性 Bug | 1 人天 | 低 |
| **P0-2** | 文档-代码对齐 | 0.5 人天 | 低 |
| **P0-3** | 删除死代码（缓存/ThreadLocal/冗余包装） | 0.5 人天 | 低 |
| **P1-1** | 删除 13 个零引用类 + 内敛化 5 个 | 1 人天 | 中（需确认无反射调用） |
| **P1-2** | 弱算法 `@Deprecated` | 0.5 人天 | 低 |
| **P1-3** | 安全细节修复（密钥校验/可信代理/SameSite） | 2 人天 | 中 |
| **P1-4** | 资源泄漏修复（ImageUtils 修 Bug 保留自研） | 1.5 人天 | 低 |
| **P2-1** | 工具层瘦身至 12K 行（删零引用 + 复用 JDK/Spring 内置） | 4 人天 | 中（仅删零引用和复用 JDK，不引入外部库） |
| **P2-2** | 职责越界剥离 + ImageUtils/FileTypeUtils 修 Bug 保留 | 3 人天 | 中 |
| **P2-3** | 集合工具内部去重（统一到 CollectionUtils） | 2 人天 | 中 |
| **P2-4** | 评估移除 pom 中 commons-lang3 / commons-io optional 依赖 | 0.5 人天 | 中（需确认无业务直接引用） |
| **P3-1** | 测试补全至 80% | 5 人天 | 低 |
| **P3-2** | 可观测性去重 | 1 人天 | 低 |
| **P3-3** | ArchUnit 守护 | 1 人天 | 低 |

**总计**：约 24 人天，建议分 4 个 Sprint（每 Sprint 1-2 周）。

> **与外部库方案的差异**：原方案（依赖外部库）目标 8K 行、引入 Apache Commons+Guava+Hutool 替代自研；现方案（内网框架约束）目标 12K 行、保留自研但删零引用+内部去重+复用 JDK/Spring 内置。多出的 4K 行是保留 StringUtils/CollectionUtils/FileUtils/ImageUtils 等自研工具的合理代价。

---

## 附录：关键文件路径索引

- 模块根：`ydsz-backend/ydsz-common/ydsz-common-util/`
- 死代码：`BeanCopyUtils.java:54-73` / `Converters.java:31-32` / `ExecutorUtils.java:286-297`
- Bug 高发区：`ImageUtils.java` / `MapUtils.java` / `ListUtils.java` / `SetUtils.java`
- 安全风险：`ServletUtils.java:231-268` / `CookieUtils.java:71-85` / `Rsa2Utils.java:259-270`
- 文档漂移：`README.md:41-43,134,319,339` / `UtilAutoConfiguration.java:16` / `additional-spring-configuration-metadata.json`
- 配置 Bug：`SnowflakeAutoConfiguration.java:177`
