# ydsz-common-json 模块深度审查与优化建议报告

> **审查范围**：`ydsz-backend/ydsz-common/ydsz-common-json`（约 108 个 Java 文件 / 2.6 万行，**零**测试代码）
> **对标对象**：Jackson (databind+annotations+modules)、Fastjson2、Gson，以及阿里/美团/字节的 Java 研发规范
> **审查维度**：架构优化 / 功能增强 / 性能提升 / 体验改善 / 过度设计清理
> **事实核验**：所有 P0/P1 结论均经独立子核对（文件 + 行号），下述标注 `[#:行号]` 的均为代码直接证据

---

## 0. 模块画像与行业定位

YdszJson 是一个以"**零外部 JSON 库依赖**"为卖点的自研序列化引擎，通过 ASM 字节码生成 + 多级降级 + AutoType 安全模型，替代 Jackson 作为全公司**几乎全部 40+ 后端模块**的统一 JSON 工具。

**已验证事实（取自使用情况调研）**：
- 仓库内业务代码 `YdszJson` 门面类落地 **195 个文件**，是全公司事实标准；`YdszJsonMapper` 模块外**0 引用**，仅有门面走全局单例。
- Jackson/Fastjson/Gson 在业务代码中已**完全清零**（仅 commons-file/minio 因三方 SDK 声明性保留）。
- CI 门禁含 SpotBugs / Checkstyle / SonarQube / OWASP / ArchUnit、**无 JMH / 无单元测试、src/test 目录不存在**（pom 已声明 JUnit5 + JMH 依赖但无一行用例）。

**核心工程债**：性能/安全相关的高级宣称（ASM 加速、零拷贝、SIMD、maxDepth 防栈溢出、合法防非法的 Feature 枚举）多处与代码实现不一致；P0/P1 级正确性 bug 集中在"配置隔离"与"解析健壮性"两处，存在线上数据损坏与 DoS 风险。

---

## 1. 架构优化（稳定性 + 可维护性根基）

### P0 — 配置隔离失灵

| # | 问题 | 证据 | 影响 |
|---|---|---|---|
| 1 | `ThreadLocalSnapshot` 只备份 7 个字段，**不覆盖** `FieldMetadataLoader.NAMING_STRATEGY` 与 `YdszJsonParser.useBigDecimal` | config/YdszJsonConfig.java:338-346；provider/SerializationProvider.java:799-835；FieldMetadataLoader.java:39 | `YdszJsonMapper.toJson(obj, myConfig)` 调用后全局命名策略被覆盖，**当前线程后续 YdszJson.toJson 输出被污染**，多 Mapper 并发不可预测 |
| 2 | `apply()` 显式写全局 `volatile static`（`YdszJsonParser.useBigDecimal`），与同一文件 copyOf javadoc "不影响全局单例" 相悖 | YdszJsonConfig.java:346；YdszJson.java:980 javadoc | 配置模型虚假，文档注释误导 |
| 3 | 单例 setter 随时可变 + Mapper 副本共享 → "全局可变 / ThreadLocal / Mapper 副本" 三轨互串 | YdszJsonConfig.java:74 全局字段；SerializationProvider.java ThreadLocal | Jackson 的不可变 ObjectMapper 模型被破坏，不可复现 bug 温床 |

**建议（按优先级）**：
1. **将 `YdszJsonConfig` 改为不可变值对象**（final 字段 + 私有构造 + `YdszJsonConfig.copyOf(other)` 工厂），消除各处的 `config.setXxx()`；Mapper 使用独立的 `ConfigSnapshot`。
2. 将 `YdszJsonParser.useBigDecimal` 从全局 static 迁移到 `SerializationContext` ThreadLocal，或至少纳入 `ThreadLocalSnapshot` 恢复链路。
3. `apply()` 返回 `AutoCloseable`/try-with-resources，强制业务方在 finally 显式恢复，避免跨语言/异步场景泄漏。

### P0 — 全链路深度限制失效

| # | 问题 | 证据 |
|---|---|---|
| maxDepth | `YdszJsonConfig.maxDepth = 256` 仅被 `JsonHealthIndicator.java:36` 展示 | config/YdszJsonConfig.java:74；health/JsonHealthIndicator.java |
| 递归无 depth | parser/YdszJsonParser.java:279-317 `parseObjectRecursive`/`parseArrayRecursive` 为**纯递归无 depth 参数** | |
| JSONReader 未消费 | DEFAULT_MAX_DEPTH / Feature.LimitDepth（JSONReader.java:36,99）声明但**无解析方法读取** | |
| stream 深度隔离 | stream/JsonParser.java:442 有独立的 `depth > maxDepth` 保护，但与 config.maxDepth 无关，**两处实现各自为政** | |

**影响**：10 万层嵌套 JSON 触发 `StackOverflowError`（Error 级异常，业务 catch Exception 拦不住），构成 DoS 攻击面。

**建议**：
1. 将 `ParserContext` 加入 `depth` 字段，所有入口 `parseObject/parseArray` 递增+校验 > maxDepth 抛 `JsonDeserializationException`。
2. 将 `stream/JsonParser` 的硬编码 maxDepth 改为消费 config 字段，统一口径。
3. 将反序列化的递归改为 Stack+循环实现（或深度阈值内按递归、超限转显式栈），彻底消除栈溢出。
4. `YdszJsonConfig.apply()` 补齐 maxDepth 传播。

### P1 — 解析器家族分裂

| 解析器职责 | 位置 | 问题 |
|---|---|---|
| 树解析 | parser/YdszJsonParser.java | 主路径，但是"命名策略烘焙"与全局入口 |
| 游标 | reader/JSONReader.java | 独立深度限制未生效 |
| 令牌流 | stream/JsonParser + JsonGenerator | 一套独立的 Stream/深度/UNICODE 实现 |
| 零拷贝 | bytecode/ZeroCopyDeserializer.java | 独立且名不符实 |
| 写入器 | writer.JSONWriter / provider.JsonWriter / stream.JsonGenerator | provider.JsonWriter 为**死代码**全模块无实例化 |

**影响**：同一畸形输入（如 `\uXXXX` 截尾、`{"a":1` 截断、深度溢出、最大长度）在不同路径上行为严重不一；错误码/行列号只有部分路径具备；4-5 条路径竞争维护成本。

**建议**（暂不大动，给路线图）：
1. 短期：把 `provider/JsonWriter.java` 删除，把 `stream/JsonParser` 的 UNICODE/深度判定 bug 修掉。
2. 中期：把反序列化主链路收归为"一个 parser + 一个 context"，JSONReader/ZeroCopyDeserializer 作为"tree→bean 的装配层"而非独立 parser。
3. 长期：实现"token-by-token 流式解析器"（P3 Roadmap），统一 InputStream 与 String，解决超大 JSON。

### P1 — 循环依赖 + 上帝类

- **循环依赖**：`YdszJsonParser.parseObject(String,Class)` 反调门面 `YdszJson.toJson/toObject`(parser/YdszJsonParser.java:1266-1267) ↔ `YdszJson` → DeserializationProvider → BeanDeserializerEngine → YdszJsonParser，形成包间环。
- **上帝类**：`YdszJson.java` 1062 行承载 9 类职责；`SerializationProvider.tryFastPathToWriter→tryBeanSerialize→ValueWriter.writeBeanNoAnnotationOptimized` 重复 ASM 调用。

**建议**：
1. 在 Parser/Layer 之间抽 `JsonCodecContext`，打破包环。
2. YdszJson 的 getByPath/getByPointer/merge/diff/validate/isValid 拆走为独立工具门面。

---

## 2. 正确性 / 安全（不丢数据、不被打）

### P2 — 大整数溢出

`YdszJsonParser.java:427` `intValue = intValue * 10 + (chars[pos]-'0')` 与 `JSONReader.java:549` 累乘均无溢出检测，>19 位整数**静默回绕成错误值**。

**建议**：累加中借用 JDK 的 `Math.multiplyAddExact` 或 `Long.compareUnsigned`，溢出抛 `JsonDeserializationException("integer overflow at line X col Y")`。

### P2 — CreatorResolver 写 null 目标

`provider/CreatorResolver.java:115` `:160` 对实例字段做 `field.set(null, convertedValue)`，异常被静默吞掉（行 108-120 / 150-165 无 try 则 NPE）；若 class 含 static 字段会把 JSON 值写入**静态字段**，下一请求读取错误值 → 跨请求污染。

**建议**：
1. 在 `clazz.getDeclaredConstructor()` 成功创建的实例上统一写，禁止 null target。
2. 明确跳过 static / final 字段（或写入 static 时终止并告警）。

### P2 — maxJsonSize 字符数冒充字节数

`YdszJson.java:1052-1061` 检查的是 `String.length()` 而非 UTF-8 字节长度，对中文等非 ASCII 输入**限制精度失真**。且仅门面类部分方法校验；`readTree(618) / isValid(692) / parseObject(path)` 绕过；`toObject(InputStream)` 先 `readAllBytes()` 再校验（行 900），对已读入内存无效。

**建议**：统一在 `JsonParserContext` 层计数**字节**，并强制所有入口经过 `checkSize()`。

### P2 — 异常体系割裂

调用方想统一 catch `YdszJsonException`，但 `reader/JSONReader.java:281,327,749`、`provider/BeanDeserializerEngine.java:395` 等大量位置直接抛 `IllegalStateException/RuntimeException`。

**建议**：自检扫描裸 RuntimeException，统一改写为 `JsonSerializationException` / `JsonDeserializationException`（两者已存在，但没贯通）。

### P3 — AutoType 模型合格，但边界松动

- **合格面**：默认 deny、黑名单覆盖 gadget、多态仅限 permits/sealed，模型优于 fastjson2 同类配置。
- **P2 边界**：`whitelist-packages: ["com.njydsz"]` 包级回退匹配放开**公司下任意类**，新业务类可绕过白名单扫描，属"设计性敞口"（autotype/AutoTypeChecker.java:668、YdszJsonProperties.java:96）。
- **P3**：`getExplicitWhitelist/getAnnotationWhitelist/getExplicitBlacklist` 返回外部可变集合，会绕过 `TYPE_CHECK_CACHE`（AutoTypeChecker.java:628-658）。

**建议**：将默认包级回退改为 opt-in（ydsz.json.whitelist-mode=CLASS_ONLY | PACKAGE_PREFIX 默认 CLASS_ONLY），并缓存命中 immutable 视图返回。

---

## 3. 功能增强（对标 Jackson / JSR-310 / 业务刚需）

### P1 — 常见能力缺失清单

| 缺口 | 对标 Jackson | 证据 |
|---|---|---|
| `GenericHttpMessageConverter` 未实现 → `@RequestBody List<Dto>` 退化为 `List<Map>` | Jackson 无此问题 | spring/JsonHttpMessageConverter.java:137-154 |
| JSON 注解并非真正"同名"：仓库是**自研的 `@com.njydsz.annotation.JsonProperty`**，真实 Jackson 同名注解 import 不会生效 | DX 陷阱 | annotation/JsonProperty.java:29 自研类 |
| `@JsonAnyGetter/@JsonAnySetter/@JsonUnwrapped/@JsonValue/@JsonRootName` 为**死注解**（无消费） | Jackson 全支持 | annotation/*.java 全工程无任何 getAnnotation 消费 |
| 循环引用仅输出不可反序列化的 `{"$ref":"cycle"}` | Jackson `@JsonBackReference/@JsonManagedReference` 完整闭环 | ValueWriter.java:939 |
| 嵌套泛型 `List<List<X>>`、`Map<K,List<X>>` 兜底裸解析 | Jackson TypeReference/TypeFactory | DeserializationProvider.java:260-265 |
| Map 非 String 键反序列化、枚举大小写不敏感 | Jackson 原生支持 | ValueWriter.java:444-448 / 552-557 |
| `@JsonGetter`/`@JsonSetter` 已创建但**消费端什么都没做** | 宣称"支持" | FieldMetadataLoader.java:233-251 |

**建议**：
1. **立刻**实现 `GenericHttpMessageConverter`，这是 `@RestController` 的刚需（否则全局替换 Jackson 后泛型接口全坏）。
2. 注解二选一：要么删掉死注解，要么给一个"实现或删除"冲刺。 `@JsonGetter/@JsonSetter` 给 1-2 周实现期，否则文档改掉。
3. 泛型：`com.njydsz.common.json.TypeFactory` 65-95 已有骨架，推广到所有反序列化入口即可。
4. 循环引用：短期至少 `ref` 反序列化支持（读回引用）；长期可借鉴 Jackson 双向引用。

### P2 — 时间类型、Enum

- `java.util.Date` / `java.sql.Date` import 错配：ValueWriter.java:22 只 import sql.Date，`TYPE_CODE_CACHE` 只注册 sql.Date，导致 java.util.Date 落在 Bean 输出路径（provider/ValueWriter.java:93-96,990；**经核对并非一定输出 {}，但仍属错配**）。
- 枚举 `@JsonValue` 死注解，多态子类型 `@JsonTypeInfo` 解析 `extractTypeValue` 裸 `indexOf("type")` 可被字符串字面量混淆（provider/PolymorphicTypeResolver.java:134-159）。

**建议**：
1. 把日期支持的完整白名单（java.util.Date/sql.Date/LocalDate/LocalDateTime/Instant/OffsetDateTime）统一到 `ValueWriter` 的 `TYPE_CODE_CACHE`，并单测保障。
2. 枚举：先把默认 `name()/ordinal()` 之外加入 `@JsonValue`；`PolymorphicTypeResolver.extractTypeValue` 改为严格 key 解析。
3. 清理 @JsonValue 死注解或兑现。

### P2 — Spring / WebFlux / 自动配置的沉默成本

- `JsonHealthIndicator` 直接 import Spring Boot 4 的 `health.contributor.*`（health/JsonHealthIndicator.java:3-4），**Boot 3 应用健康检查静默缺失**。
- `JsonHttpMessageConverter` 无 `Ordered`/`@AutoConfigureBefore`，与 `MappingJackson2HttpMessageConverter` 媒体类型全重叠（application/json + application/\*+json）的优先级取决于注册顺序，行为不可预期（ HttpStatusMessageConverter.java:101-105,108-111）。
- 自动配置中 `wrap-root-value`、`fail-on-error` 两个配置项在 `JsonConfigBean.init()` 根本没复制（JsonAutoConfiguration.java:134-150），实际 **`YdszJsonConfig.setWrapRootValue()/setFailOnError()` 形同虚设**。
- `module/JsonModuleRegistrar` 只在非空时 `initialize()`（JsonModuleRegistrar.java:34），导致"装了一个 module 忘记初始化"静默无效。

**建议**：
1. HttpMessageConverter 加 `@Order(LOWEST_PRECEDENCE - 10)` 并在 ydsz-starter 与业务侧强制 `exclude jackson-databind`（当前 ydsz-common-web 未 exclude，普遍存在双引擎）。
2. `JsonConfigBean` 完整实现/删除 wrapRootValue、failOnError、maxJsonSize、maxDepth 传递。
3. WebFlux：将 `JsonReactiveUtils`（仅有两个静态 encode）升级为 `spring.boot.JsonReactiveAutoConfiguration`，注册 Encoder/HttpMessageReader；`pom.xml` 注释里的 "JsonReactiveEncoder 类不存在" 需兑现或删除注释。
4. 健康检查：分离 Boot 3/4 适配器，或使用条件注解 兼容 3.x。

### P3 — JSON 规范完整性

- 重复 JSON key 静默 last-win（与 RFC 不冲突但与 Jackson 默认一致即可，无需改）。
- 尾部垃圾不校验、截断 JSON `{"a":1` 静默返回部分结果（parser/YdszJsonParser.java:156-158,247-249）→ 至少关掉"accept trailing data"或在非流式入口校验 EOF。
- `\uXXXX` 在 16KB 缓冲边界抛错不尝试 fillBuffer()（stream/JsonParser.java:254-256）→ 中文文本嵌 \u 转义时被误杀。

**建议**：把"截断 JSON / 截尾 Unicode / 重复 key / 尾部不一致"下的行为统一到 RFC 8259 + 一份业务兼容开关。

---

## 4. 性能提升（让跑得更快）

### P1 — ASM / MethodHandle 边界（重构收益主要点）

AsmBeanCodecGenerator 1990 单个文件，产出的是 getter `INVOKEVIRTUAL` 调用（asm/AsmBeanCodecGenerator.java:412），**非直接字段访问**，因此 ASM 热路径收益 ≈ MethodHandle + JIT 内联，边际收益极小；同时：
- 无 getter 字段被静默 skip（:392-393），导致部分字段丢失序列化而不报错。
- 类名 `beanType.getName()` 全限定名（:299，**非 getSimpleName，早前报告此处有误，经核验**），缓存因 SoftReference/LRU 缺失后重新生成同名类 → `LinkageError`。**但异常后被写入 FAILED 缓存（AsmCodecCache.java:235-237）即永久禁用，并不会反复重试 → LinkageError 不会重复触发，但导致安全降级后该类 ASM 不可用。**
- GraalVmDetector 与 Metaspace 阈值降级（5000/8000/10000）设计合理（asm/AsmBeanCodecGenerator.java:1786-1860）。

**建议**：
1. **用 MethodHandle 替代 ASM**：现代 JDK (17/21) 上 MethodHandle 与 ASM 性能差距可忽略、且维护成本降一个数量级。保留 ASM 作为 opt-in 配置（`json.codec=ASM|MH|REFLECTION`）。
2. 把 `AsmBeanCodecGenerator` 拆为 `AccessorGenerator（反射）` + `AsmGenerator` + `FallbackPolicy`，各自可测。
3. 补 "getter 缺失字段" 的告警或纳入 shouldSkip。

### P1 — 多层缓存合并

至少 6 层 Class-keyed 缓存并存：`AsmCodecCache` / `BeanSerializerCache` / `SerializerCache`（FieldMeta[]+BeanSerializerInfo）/ `SerializerRegistry` / `ZeroCopyDeserializer.CACHE+CONSTRUCTOR_CACHE` / `BeanReader` 内部缓存。职责重叠且无效：
- `AsmCodecCache` LRU+SoftReference 在小对象下 Soft 回收不释放 Metaspace（asm 类由 ClassLoader 强引用无法卸载）。
- `maxSize=1024` 与 Metaspace 阈值 10000 不匹配。
- **并发隐患**：`AsmCodecCache` 使用 `LinkedHashMap(accessOrder=true)` + StampedLock 乐观读（cache/AsmCodecCache.java:53-64），`get()` 会改链表结构 → 乐观读不加锁并发下链表损坏/遍历死循环（成立，已核对）。

**建议**：
1. 合并为"一次元数据加载 → 一条 record → 全局 `ConcurrentHashMap<Class, CodecRecord>`"。Asm/Reflection 产物都视为同一产物的不同表现。
2. StampedLock 用法修复或更换为 `ConcurrentHashMap.computeIfAbsent`。
3. 缓存 key 引入"配置维度"（至少命名策略、useBigDecimal），否则多 Mapper 配置隔离不成立（这是 P0 配置问题的延伸，cache/SerializerCache.java:41 全 Class-keyed）。
4. 命名策略烘焙到 FieldMeta.jsonName（cache/FieldMeta.java:177）后 jsonName 为 final → **多命名策略必须用不同缓存 key**，否则就是数据错乱（此点与 P0#2 同一问题）。

### P2 — 零拷贝名不符实 + 池借不还

`bytecode/ZeroCopyDeserializer.java`：
- 所有入口 `deserialize(String)` 先 `json.toCharArray()` 全量拷贝（:351/414/490/670/818），**零拷贝名不符实**。
- `parseArray` 正常路径 `borrowArrayList()` → `new ArrayList<>(list)` 复制返回，**不调用 returnArrayList()**；池只在异常路径归还 → 池无效且反而引入多一次复制（:1186-1194）。`parseObject` 同理（:1234-1277）。

**建议**：
1. 短期：把 Float/Double 的 UltraFast 自实现（`Math.pow(10,n)` 慢且精度差，:1078）删除，统一委托 JDK。
2. 中期：把"5 级反序列化器 (Single/Two/UltraFast/Fast/Standard)"合并为"JDK 优先 + ASM 代码路径 + 降级反射"，剪除重复代码。
3. 长期：实现 byte[] (`UTF-8`) 直接解析路径（Roadmap P2）才是真正零拷贝方向。

### P2 — StringBuilder / LinkedHashMap 池化反模式

`SerializationContext` 每线程常驻 ~4096 char StringBuilder + JSONWriter 8KB + IdentityHashMap 64 槽（provider/SerializationContext.java:108）；现代 JVM TLAB + 逃逸分析下池化 StringBuilder 基本无收益。

**建议**：把 pool 改成"ThreadLocal 初始容量 hint"而非 ObjectPool；或通过 benchmark 数据决策（见下一节）。

### P2 — StringInterner / BytesUtil / VectorSimdUtil

- 自研 StringInterner 注释称 LRU，实为"只增不减 512 桶固定 O(n) 链长"，对 ≤64 任意长度输入有内存膨胀风险（util/StringInterner.java:15,130-147）。**直接用 `String.intern()` + G1 字符串去重通常更优。**
- VectorSimdUtil 注释"朴素循环 + JIT 自动向量化"，字节码确认**未用 jdk.incubator.Vector API**（bytecode/VectorSimdUtil.java:3-28）；BytesUtil 为纯透传包装（bytecode/BytesUtil.java:36）。

**建议**：
1. StringInterner 删除或替换为 JMH 验证可用的形态。
2. BytesUtil 删除（无价值间接层）；VectorSimdUtil 保留朴素循环但**删掉无依据的 'JIT 自动向量化' 注释**。
3. 若真要用 SIMD，引入 Vector API 做字符串/字节批量比较，并用 VM 参数开关兜底。

### P2 — MetricsHelper 之外的性能噪声

- `estimateThreadLocalMemory` 动态计算（provider/SerializationContext.java:153）仅被 HealthIndicator/CacheStats 调用 → **监控本身不影响热路径但接口存在**。
- NumberUtils（两位查表法 :19-27）属成熟技巧合理。

---

## 5. 体验改善（DX + 文档 + 可观测）

### P0 — src/test 目录不存在，pom 声明 JUnit5 + JMH 但 0 测试用例

无回归保障，上述所有宣称"正确性/性能"完全无法验证。

**建议**：
1. 第 1 周：把 P0/P1 正确性 bug 对应的回归测试补上（命名策略隔离、maxDepth 防栈溢出、整数溢出、CreatorResolver 行为、maxJsonSize 字节截断等）。
2. 第 2 周：建立 JMH benchmark，至少对比 Jackson-databind 2.18 / Fastjson2 2.0.62 / Gson 2.11 on JDK 21，覆盖 toJson/toObject/parseMap/parseArray/bytes 5 个基线。
3. 接入 CI：`backend-ci.yml` 增加 `performance-baseline` 阶段，失败 sonarqube quality gate。

### P1 — 文档与代码不一致

| 文档宣称 | 代码现状 | 位置 |
|---|---|---|
| YdszJsonBenchmark 内置 | src/test 不存在，无 benchmark | pom.xml:132,README:328 |
| "方法级@JsonGetter/@JsonSetter" | 消费端 if 块体为空 | README:3,FieldMetadataLoader.java:233-251 |
| wrapRootValue 可用 | 仅 config/mapper builder/health 提及，无消费 | config/YdszJsonConfig.java:347 |
| 4 个 Feature 枚举 | JSONReader 21 个特性在代码中无实现；JSONWriter 仅 PrettyPrint 生效 | YdszJsonMapper.java |
| "对标 Jackson 兼容注解" | com.njydsz.annotation.* 非 com.fasterxml.jackson.annotation.*，同名克隆易 import 错 | annotation/*.java |

**建议**：文档与代码共同纳入 PR review checklist，每个宣称用行号锚定来源。

### P2 — Mapper Builder 模块外 0 引用

`YdszJsonMapper.builder()` 自第六轮新增，**全仓库仅模块内使用 12 处**。业务代码全走门面类。

**建议**：
- 要么把 Mapper 推到业务（`YdszJsonMapper.builder().namingStrategy().build().toJson(x)`），替代部分"靠 ThreadLocal 单例扩展"的模式；
- 要么明确 Mapper 是未稳定 API、从 README 降级为"内部扩展能力"。

### P2 — HealthIndicator / Metrics

- `YdszJsonMetrics` Bean 无论 `monitoring-enabled` 是否 true 都注册（JsonAutoConfiguration.java:165 只写 System property 无人读）。
- Cache/Gauge 合理（JsonCacheMetrics、YdszJsonMetrics 实现规范 Micrometer）。

**建议**：删除 monitoring-enabled 分支或真正条件化注册；把 safeMode=off 暴露为 Critical（当前仅 Warning 级合理）。

---

## 6. 过度设计清理清单

| 项 | 现状 | 建议 |
|---|---|---|
| YdszJsonSchema（Draft 07 子集，无 $ref/format/uniqueItems，仅代码构建） | 零业务调用，对标 networknt/json-schema-validator 自研性价比低 | 二次评估，无需求则删除或用成熟库 |
| YdszJsonPath | 子集可用但有非标准驼峰回退，零调用 | 保留供高级 API getByPath，或换 Jayway JsonPath |
| JsonPointer / JsonPatch / JsonMergePatch | 短小正确，零调用 | 保留（风险小，RFC 工具库有用） |
| 树模型 tree 9 类 + 动态模型 object 2 类 | API 面冗余双轨 | 收口为 tree +一条 object 快速转换 |
| @JsonAnyGetter/@JsonAnySetter/@JsonUnwrapped/@JsonValue/@JsonRootName | 死注解 | 删除 |
| @YdszJsonField 大量未消费属性 (required/useBeanName/direct/fastMode/jsonDirect/maxDepth/ignoreGetters/ignoreSetters)、@YdszJsonClass 23+11 项未消费 Feature、@JsonIgnoreProperties.ignoreUnknown | 死配置 | 删除 |
| stream.JsonParser + JsonGenerator | 令牌流 API 内部无人使用，仅 JsonGenerator 仍由 ReactiveUtils 承载 | 删除 JsonParser，保留 ReactiveUtils 用到的 Generator 部分 |
| provider.JsonWriter | 全模块无实例化，死代码 | 删除 |
| ValueFormatter.pretty 全路径 | 不识别 Date/Enum/Optional/java.time，Map key 不转义，BigDecimal 用 toString（1E+2 非法 JSON） | 限制为"内部 debug 输出"或大改 |

---

## 7. 路线图示例（落地顺序）

### 第一步：止血（1-2 周，P0+P1 正确性）
1. 补全 ThreadLocalSnapshot（命名策略 + useBigDecimal）
2. 补全 maxDepth 全链路并 StackOverflowError 回归
3. CreatorResolver 写 null 禁止 + 静态字段跳过
4. maxJsonSize 字符数→字节数统一
5. 加 5 个以上单元测试 + 接入 CI（阻止回归）

### 第二步：对齐 Jackson 兼容面（2-4 周，P1 功能）
1. JsonHttpMessageConverter 实现 `GenericHttpMessageConverter`
2. 删除/兑现 @JsonAnyGetter/@JsonAnySetter/@JsonUnwrapped/@JsonValue/@JsonRootName/@JsonGetter/@JsonSetter
3. 泛型递归反序列化（List<List<X>>）
4. ydsz-common-web 强制 exclude jackson-databind，converter 加 Ordered
5. Boot 4 健康检查拆分
6. 同步生成 JMH，写性能基线

### 第三步：性能与精简（1-2 月，P2）
1. 合并 6 层缓存为 1 并修 StampedLock
2. 把 AsmBeanCodecGenerator 拆并为 MethodHandle 主导
3. 删除 ZeroCopyDeserializer 5 级分级 + 修正池借不还
4. 删除/精简 BytesUtil、StringInterner、VectorSimdUtil 无依据注释
5. 收口 stream/ 死 API、tree/object 双轨
6. 发布 1.1.0 changelog，降级 Mapper Builder 为 internal API

### 第四步：长期（季度级，P3 Roadmap）
1. byte[] UTF-8 直接解析路径（真·零拷贝）
2. token-by-token 流式解析器（超大 JSON > 20MB）
3. WebFlux `JsonReactiveAutoConfiguration` 正式兑现
4. 接入 sonar + archunit 强制"所有 JSON 注解必须被消费"规则
5. 可选：Schema / JSONPath 替换为成熟库或删除

---

## 8. 对标分析小结

| 维度 | 目标 | 本模块现状 | 差距 |
|---|---|---|---|
| **构建模型** | Jackson ObjectMapper 不可变 | 全局可变单例 + ThreadLocal + Mapper 副本三轨互串 | P0 未达标 |
| **Feature 位** | 枚举贯通全路径 | 大多数 JSONWriter/JSONReader Feature 静默忽略 | P1 未达标 |
| **流式内核** | databind on streaming core | databind（parser）与 stream 两套平行，且 InputStream 全量入内存 | P2 未达标 |
| **兼容性** | 注解真正同名兼容 | 同名克隆（com.njydsz），真实 Jackson 同名不工作 | P1 陷阱型差距 |
| **性能（ASM vs MethodHandle）** | 或 ASM 或 MethodHandle，按场景 | ASM 写 getter INVOKEVIRTUAL，边际收益 ≈ 0，维护成本极高 | P1 商榷 |
| **安全（AutoType）** | 默认 deny + 类级白名单 + 黑名单 | 模型合格但包级回退大敞口（默认 com.njydsz.* 任意类） | P2 可接受但需降级 |
| **生态** | 丰富的 modules / datatype / 适配器 | module 已搭骨架但初始化静默无效 | P2 |
| **性能数据** | JMH + 定期回归 | 零 JMH、零测试、零对比数据 | P0 |
| **Bugs 修复节拍** | CVE 补丁 < 7 天 | P1 已存在超 2 周未发现 | 流程待建立 |

---

**结论**：YdszJson 作为全公司事实标准，"零外部依赖 + ASM + 多级降级 + AutoType"的工程方向是正确的，但**当前版本最大的风险不是性能，而是正确性隔离与测试缺失**。建议按"先止血、再补齐兼容面、再谈性能重构"的步骤推进，避免在 MethodHandle vs ASM 重构中先丢掉配置隔离的基础。

---

*本报告基于 2026-07-29 代码快照；所有 `[:行号]` 核验已留痕。建议本报告归档到 `docs/`，作为后续每次大版本迭代的入口。*
