# ydsz-common-util 全面分析报告

> 分析基准：最新代码（2026-08，14,286 行主代码 / 60 个主类 / 19 个测试类 / 15 个包）
> 对标对象：Hutool、Apache Commons（Lang3/Collections/IO）、Guava、Spring Core/Boot 官方规范、美团 Leaf、百度 UidGenerator、Resilience4j/Sentinel、《阿里巴巴 Java 开发手册》、ArchUnit 治理实践

---

## 一、现状概览

### 1.1 模块规模与包结构

| 包 | 主类数 | 定位 | 跨模块采用 |
|---|---|---|---|
| id | 16 | Snowflake + WorkerId 策略链 + Trace | **IdGenerator 97 处 / TracerUtils 21 处（核心热点）** |
| string / collection / bean | 6 | 基础集合与 Bean 映射 | StringUtils 49、MapUtils 13、CollectionUtils 4 |
| security / crypto | 13 | 摘要/国密/AES/SM4 Provider 体系 | DigestUtils 12，CryptoUtils/Sm2Utils **0** |
| concurrent | 6 | 线程池/限流/重试/虚拟线程调度 | RateLimiter **0**、RetryUtils **0**、MeteredThreadPoolExecutor **0**、BoundedVirtualThreadScheduler **0** |
| http / ip | 10 | Servlet 解析/URL 匹配/网段 | ServletRequestUtils/HttpTokenUtils/IpValidator **0**、CidrUtils 1 |
| auth / message / validate / mask / date / io / yaml | 13 | 上下文读取/国际化/校验/脱敏等 | AuthInfoUtils 8、MessageUtils 2，其余基本为 0 |

### 1.2 总体评价

**优点（保持）**：
- 架构守护意识领先多数自研框架：ArchUnit 编译期禁反模式（工具类 final、禁 System.out、禁包循环、依赖白名单）、可选依赖全部 `optional`（TTL/BC/Micrometer 优雅降级）、`additional-spring-configuration-metadata.json` 配置提示齐全。
- SnowflakeIdGenerator 质量高：单 AtomicLong 打包 state 的无锁 CAS、相对 epoch 时间戳、可配 sequenceBits、时钟回拨 park 等待而非自旋、ID 反解析 API、健康检查。
- 加密实现符合现代标准：AES-GCM 随机 IV + 前置 IV + 128bit Tag、常量时间比较（MessageDigest.isEqual）、PBKDF2 默认 60 万次迭代、BC 可选加载。
- StringUtils"不提供 JDK 已有能力"的克制原则（类注释明确边界），优于 Hutool 的全家桶倾向。

**核心问题**：**约 60% 的 API 表面积在全部业务模块中零采用**，同时存在 2 个静默正确性风险（workerId 冲突、gateway 降级）和明显的文档/测试债。

---

## 二、五维度分析

### 2.1 架构优化

**A1（P0）Bean 注册机制不一致，gateway 存在静默降级**
- `SnowflakeIdBean` 用 `@Configuration`（依赖组件扫描），`UtilAutoConfiguration/MessageSourceConfiguration` 用 `@AutoConfiguration`（imports 注册）。两种机制混用违反 Boot 2.7+ 官方规范。
- 实测：system/userinfo/literule 通过 `scanBasePackages={"com.njydsz.xxx","com.njydsz.common"}` 扫到；**gateway 主类在 `com.njydsz.gateway` 且未扫描 common → gateway 内无 SnowflakeIdGenerator Bean → IdGenerator.nextId() 降级为 ThreadLocalRandom 随机数，全程无任何日志告警**（`IdGenerator.getGenerator()` 在 supplier==null 时直接 return null）。97 处调用方对此无感知。
- 修复：① `SnowflakeIdBean` 并入 `UtilAutoConfiguration`（或改为 @AutoConfiguration 并登记 imports）；② 业务服务移除对 `com.njydsz.common` 的全量扫描（第三方库不应靠 scanBasePackages 拉起）；③ IdGenerator 首次降级时打一次 WARN（含调用方栈），杜绝静默。

**A2（P0）静态可变状态 + Supplier 注入的服务定位器模式三处重复**
- `IdGenerator.setGeneratorSupplier` / `ServletRequestUtils.setTrustedProxyConfigSupplier` / `MessageUtils.setMessageSourceProvider` 三套同构代码，且静态状态在测试间残留、@PostConstruct 注册存在 Bean 时序耦合。
- 建议：抽取统一的 `StaticBridge<T>`（注册/读取/降级日志/测试 reset），一处实现三处复用；或评估直接暴露实例 Bean 风格 API 减少静态入口。

**A3（P1）命名转换三处实现、语义不一致**
- `StringUtils.toCamelCase`（其余字母小写化）、`BeanMapper.snakeToCamel/camelToSnake`（保序）、`MapUtils.snakeToCamel/camelToSnake`（转发 BeanMapper）。同名 API 行为不同是典型踩坑源；`toBoolean` 同样在 BeanMapper/MapUtils 重复。
- 建议：收敛到 StringUtils 单一实现（或独立 NamingStyle 工具），其余 @Deprecated 转发，ArchUnit 增加规则禁止 util 内重复实现。

**A4（P1）重复的 ArchUnit 测试**
- `util/UtilArchitectureTest`（@AnalyzeClasses 版）与 `util/arch/UtilArchitectureTest`（ClassFileImporter 版）两份并存，规则漂移风险。保留一份（建议 junit5 集成版），删除另一份。

**A5（P2）util 模块依赖 core/domain/json，边界继续膨胀**
- pom 中 ydsz-common-core、ydsz-common-domain 是必选依赖，util 越来越像"业务上下文读取器"（AuthInfoUtils 直接读 BizContextKeys）。大厂实践中 util 应零业务语义。建议：AuthInfoUtils/MessageUtils 迁往 common-web 或新建 common-context；util 回归纯工具。

### 2.2 功能增强

**F1（P0）WorkerId 分配缺分布式协调，存在静默重复 ID 风险**
- `IpHashWorkerIdAllocator` 仅取 IP **末字节**（0~255）再 `% 1024`（取模无意义）：K8s Pod 重建后 IP 复用、跨集群同末字节，两实例可拿到相同 workerId → **生成的 ID 重复且无任何检测/告警**。这是分布式主键最严重的一类事故源。
- 建议：① IpHash 改为全 IP 字节哈希取模；② 提供 `RedisWorkerIdAllocator`（SETNX + 心跳续期）并作为默认链尾（ydsz-common-redis 已具备条件，文档里"业务方可插入 RedisWorkerIdAllocator"的示例应落地为内置）；③ 启动期注册表校验 workerId 全局唯一，冲突即 fail-fast。对标美团 Leaf-WorkerId/百度 UidGenerator 的 DB/Redis 分配。

**F2（P1）DateUtils 能力过薄导致零采用**
- 缺 Date↔LocalDateTime↔Instant↔时间戳互转、时区转换、相对时间（"3 分钟前"）、中国法定节假日感知的 addBusinessDays（当前只跳周末，国内场景不可直接用）。
- 建议：补齐互转与时区；节假日通过可插拔 `HolidayCalendar` 接口（SPI/Bean 注入）实现，默认仅周末。

**F3（P1）RateLimiter / RetryUtils 对标 Resilience4j 的缺口**
- RateLimiter：缺 `setRate` 动态调速率（类注释自认）、缺预取/平滑突发策略。补 setRate + 配置化工厂。
- RetryUtils：缺总时长上限（maxDuration）、缺 onRetry/onSuccess 事件回调、缺基于 CompletableFuture 的异步重试。补齐后可覆盖 90% 的 Resilience4j Retry 使用场景。

**F4（P1）ValidationUtils/MaskUtils 覆盖面**
- Validation 缺 URL、固定电话、邮编、经纬度、统一社会信用代码大小写容错（当前强制大写才通过 `isSocialCreditCode`，实际应先 trim+upper）。
- Mask 缺地址/护照/自定义保留策略；建议提供 `@Mask` 注解 + Jackson Serializer 集成（业务 DTO 序列化时自动脱敏，这是大厂通用做法，比手动调用工具类防漏）。

**F5（P1）TracerUtils 缺 W3C TraceContext 传播**
- 已有 32 位 traceId/16 位 spanId，但缺 `traceparent` 头的解析（extract）与注入（inject）。补 W3C 格式编解码，便于与 OpenTelemetry/SkyWalking 互通。

**F6（P2）CryptoUtils 算法选择走 System.getProperty("crypto.algorithm")**
- 无 `ydsz.` 命名空间、无 Spring 配置绑定、全局 JVM 属性易被外部覆盖。改为 `ydsz.util.crypto.default-algorithm` 配置项 + metadata。

### 2.3 性能提升

**P1-1 BeanMapper 反射调用可提升一个数量级**
- 每字段 `Method.invoke`（setAccessible 语义 + 装箱）。类注释自认"QPS>10k 建议换框架"。用 `LambdaMetafactory` 生成 setter 调用句柄（JDK 原生、无字节码库依赖），实测普遍 3~8 倍提升；同时缓存下划线↔驼峰的候选 key（当前 toBean 的 map key 为 user_name 时 setter 路径直接丢失，而 record 路径支持——行为不一致且每次精确匹配失败）。
- 顺带修复：setter 路径不支持 `Map<K,V>` 泛型字段（convertValue 只处理了 List），record 路径支持。

**P1-2 MeteredThreadPoolExecutor 每任务做 Micrometer tag 查找**
- `meterRegistry.timer("executor.task.duration","pool.name",poolName)` 每次调用都走 Tags 拼接+registry map 查找。Micrometer 官方建议预持有 Timer/Counter 实例。改造成员字段缓存，热路径零查找。

**P1-3 CidrUtils 缓存"满 1024 全清"抖动**
- `putCache` 达到上限直接 `clear()`：一次清空导致命中率周期性归零；且缓存 key 含请求方 IP，公网场景缓存键空间无限，攻击流量可持续 flush 缓存。改为 LRU（Guava Cache/Caffeine 或 LinkedHashMap+锁）并按 CIDR 而非 (ip,cidr) 缓存掩码结果。

**P2-4 RateLimiter 全局 ReentrantLock 热点**
- 高并发 acquire 全部串行在一把锁上。参考 Resilience4j 的 AtomicReference+CAS 方案或 Guava 的同步块+延迟令牌计算，可显著降低争用。

**P2-5 杂项**
- `DateUtils.parseLocalDate/formatLocalDate` 每次 `ofPattern` 无缓存（Pattern → Formatter 做 ConcurrentHashMap 缓存，Spring 内部即如此）。
- `DigestUtils.sha256Hex(InputStream)` 对 buffer 重复 zero-fill（digest() 内已 fill 一次）。
- Snowflake 默认 sequenceBits=7（12.8 万/s）：文档应给出"高并发主键场景建议 12"的明确引导（当前只在构造器 javadoc 里），避免上线后才发现吞吐不足。

### 2.4 体验改善（开发者体验 / 文档 / 测试）

**E1（P1）文档债需要一次性清理 + CI 门禁**
- 全模块 **97 处 `@return 处理后的结果`** 这类无意义占位符（ExecutorUtils/BeanMapper 重灾区）；多数大文件尾部有 40~90 行填充空行（BeanMapper 86 行、ExecutorUtils 88 行）。
- 建议：一次性脚本清理；引入 Spotless（format + import order + ratcheting 对存量豁免）与 Checkstyle（Javadoc 检查）进 CI——这是大厂公共库标配门禁。

**E2（P1）测试覆盖缺口**
- 60 主类 vs 19 测试类，且关键类缺测：**RateLimiter、RetryUtils、BeanMapper、DateUtils、ValidationUtils、MaskUtils、ExecutorUtils、TracerUtils、IdGenerator、CidrUtils** 均无专测。RateLimiter 的并发语义、RetryUtils 的退避数学、ValidationUtils 的校验位算法恰恰是最需要测试的。
- 建议：核心工具 100% 分支覆盖 + 关键算法 property-based 测试；BeanMapper 与 Snowflake 补 JMH 基准（防性能回退）。

**E3（P2）API 一致性细节**
- 异常消息中英混杂（"iterations 必须 >= 1" vs "permits must be positive"），统一为英文 + 错误码。
- `StringUtils.isEmpty(Object)` 与 `isEmpty(CharSequence)` 重载语义差异（Object 版不判空白）易误用，JavaDoc 已提示但更建议改名（`isEmptyObject`）或拆类。
- HttpTokenUtils 仅识别 `ydsz` 前缀，建议兼容标准 `Bearer`。
- 模块缺 README（使用指南 + 选型决策表：何时用 BeanMapper vs YdszJson、何时用 RetryUtils vs Resilience4j）。

### 2.5 过度设计（收敛与下线）

**OD1（P0）约 60% API 零采用，需要"采用或下线"决策**
- 全业务模块 0 引用的有：DateUtils、FileUtils、IpValidator、ServletRequestUtils、HttpTokenUtils、MaskUtils、ValidationUtils、PwdUtils、CryptoUtils、BeanMapper、RetryUtils、MeteredThreadPoolExecutor、BoundedVirtualThreadScheduler、UrlPathMatcher、Sm2Utils、SequencedCollections 等（共 16+ 类 / 约 5,000+ 行）。
- 作为平台型公共库（对标 Hutool）保留是合理的；但 ydsz-cloud 是自用全家桶，**未使用的 API 每一行都是维护与安全审计成本**（如 PwdUtils 的 BCrypt strength=12 硬编码、弱口令字典维护）。建议按大厂"公共库治理"流程：季度盘点 → 标注 `@apiNote` 试用/稳定/废弃 → 两个季度零采用且无外部开源计划 → 进入 @Deprecated。

**OD2（P1）典型 YAGNI 点逐项处理**
- `BoundedVirtualThreadScheduler`：JDK 21 `Semaphore + ThreadPerTaskExecutor` 三行即可替代，类注释自述"预测未来场景"；且 shutdown() 不等待任务完成（体验反而不如标准库）。**建议下线**（0 采用，删除成本为零）。
- `SequencedCollections`：JDK 21 原生 SequencedCollection API 的包装，增值仅剩 null 防御。0 采用，建议下线。
- `Sm4CbcCryptoProvider`：无认证的 CBC 模式（padding oracle 面），现代规范（NIST SP 800-38D、等保）都导向 GCM。虽然 IV 随机，仍建议 @Deprecated 或 Javadoc 强警示 + 默认不注册。
- `PasswordStrengthChecker` 的 ServiceLoader SPI：为 100 行的内置实现设计插件机制，属过度抽象。若确需可配置，Spring Bean 注入即可（模块已有 Spring 条件装配）。
- `ExecutorUtils` 20+ 静态工厂与 builder 并存：保留 builder + 3 个高频工厂（fixed/virtual/scheduled），其余 @Deprecated 收敛。
- `ExecutorUtils.newVirtualThreadExecutor` 直接 `new ThreadPerTaskExecutor(...)`：ThreadPerTaskExecutor 虽在当前 JDK 可编译，但非 javadoc 承诺的稳定公共 API（注释称规范禁止 Executors 工厂——该规范针对无界队列的 newFixedThreadPool 等，`Executors.newVirtualThreadPerTaskExecutor(factory)` 无此问题）。改回标准入口消除 JDK 升级风险。
- Snowflake `datacenterId = sha256(hostname)%32`：Pod 重建 hostname 变化 → datacenterId 漂移，无持久化；5 bit 空间本来就该显式配置。建议：未显式配置时固定为 0 并 WARN，禁止隐式哈希。

**OD3（P2）双 JSON 转换体系并存**
- Map→Bean 存在 BeanMapper（反射）与 YdszJson（统一 JSON 引擎）两条路，日期格式/命名策略语义不同。明确分工写进 README（轻量 Map 取值用 MapUtils，结构化转换一律 YdszJson，BeanMapper 仅限无 JSON 依赖场景），或长期合并。

---

## 三、落地路线图

### P0（本迭代，正确性/安全收口）
| # | 事项 | 验证方式 |
|---|---|---|
| 1 | Snowflake Bean 注册统一走 AutoConfiguration.imports；修复 gateway 静默降级；IdGenerator 降级打 WARN | gateway actuator/health 出现 snowflake 指标；无 silent fallback |
| 2 | WorkerId：IpHash 全 IP 哈希 + 内置 Redis/DB 校验分配器 + 启动唯一性 fail-fast | 双实例同 workerId 启动被拒绝的集成测试 |
| 3 | FileUtils 吞异常改抛 UncheckedIOException（保留 1 个 `readQuietly` 兼容口） | 单测 + 调用方编译期暴露 |
| 4 | 一次性清理 97 处 @return 占位符与尾部空行；引入 Spotless/Checkstyle CI 门禁 | CI 红线生效 |

### P1（下个迭代，能力补齐与采用率提升）
| # | 事项 |
|---|---|
| 1 | BeanMapper：LambdaMetafactory + snake_case key 支持 + Map 泛型字段 + JMH 基准 |
| 2 | RateLimiter setRate/CAS 化；RetryUtils maxDuration/回调/异步版（均补并发单测） |
| 3 | MeteredThreadPoolExecutor 预缓存 Meter；CidrUtils LRU 化 |
| 4 | DateUtils 增强（互转/时区/HolidayCalendar）；ValidationUtils/MaskUtils 补齐 + @Mask 注解化 |
| 5 | TracerUtils W3C traceparent 编解码；命名转换 API 收敛去重 |
| 6 | 核心类补单测至 100% 分支覆盖；删除重复 ArchUnit 测试 |
| 7 | 模块 README（选型决策表 + 迁移指南） |

### P2（长期治理）
| # | 事项 |
|---|---|
| 1 | 零采用 API 盘点机制（季度）→ 试用/稳定/废弃标注 → BoundedVirtualThreadScheduler/SequencedCollections 下线，Sm4Cbc @Deprecated |
| 2 | util 模块去业务化：AuthInfoUtils/MessageUtils 迁出，util 只依赖 JDK+可选库 |
| 3 | 静态桥接模式统一（StaticBridge）；crypto 配置收敛至 ydsz.* 命名空间 |
| 4 | 双 Bean 转换体系（BeanMapper vs YdszJson）分工定版或合并 |

---

## 附：关键证据位置
- gateway 无 common 扫描：`ydsz-gateway/.../GatewayApplication.java:62`（默认包扫描）
- IpHash 末字节取模：`IpHashWorkerIdAllocator.java#allocate`
- 静默降级无日志：`IdGenerator.java#getGenerator`
- 缓存全清：`CidrUtils.java#putCache`
- 文档占位符统计：`grep -r "处理后的结果" | wc -l` = 97
- 双 ArchUnit：`util/UtilArchitectureTest.java` 与 `util/arch/UtilArchitectureTest.java`
