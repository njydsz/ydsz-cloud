# ydsz-common-json 对标竞品分析与优化建议

> 对标对象：Jackson 2.x（Spring 默认）、FastJSON2（阿里）、Gson（Google）
> 参照规范：阿里巴巴 Java 开发手册、互联网大厂中间件研发规范
> 分析范围：`ydsz-backend/ydsz-common/ydsz-common-json`，约 95 个 Java 文件、26 个子包
> 分析日期：2026-08-02

---

## 一、整体评估

| 维度 | 评分 | 一句话结论 |
|---|---|---|
| 功能广度 | ★★★★☆ | 注解/树模型/Schema/Patch/Pointer/Path/Merge/Module 全覆盖，广度对标 Jackson ~70% |
| 工程深度 | ★★☆☆☆ | 各特性普遍浅尝（Schema 缺 40% 关键字、JsonPath 缺函数、Pointer 不可组合、多态仅 PROPERTY） |
| 性能工程 | ★★☆☆☆ | 优化思路对（ASCII fast path、POW10 表、对象池），但 **ASM 字节码路径疑似从未生效**，性能主张落空 |
| 健康度 | ★☆☆☆☆ | **0 单元测试**、6 套不兼容类型码、多套重复抽象、AutoType 可绕过、缓存并发隐患 |
| 文档质量 | ★★★☆☆ | README 详尽（441 行）但与代码漂移 9 处 |

**总判断**：功能雄心很大、广度对标到位；但**深度与工程化成熟度不足，存在若干"看起来很快/很全、实际没生效或半成品"的过度设计**。当前更像是一个"对标竞品 API 表面积"的快照，而非一个可安全托付生产的高性能引擎。

---

## 二、现状诊断（已逐项验证的关键事实）

### F1. 【头号问题】ASM 字节码路径从未生效

- `AsmBeanCodecGenerator.generateSerializer`（300 行）生成类名 `beanType.getName() + "_ASM_Serializer"`，形如 `com.foo.User_ASM_Serializer`。
- `defineClass`（1649 行）将该类名原样传给 `SecureAsmClassLoader.defineInternal`。
- `defineInternal`（1640 行）强制 `name.startsWith(ASM_CLASS_PREFIX)`，而 `ASM_CLASS_PREFIX = "generated."`（1465 行）。
- 二者永不相交 → 必抛 `SecurityException` → `defineClass` 的 `catch (Throwable)` 包装为 `RuntimeException` → `AsmCodecCache` 记入 `SERIALIZER_FAILED` 缓存并返回 null → 所有 Bean 序列化静默回退到反射路径（`ValueWriter`/`BeanSerializer`）。
- **影响**：README/注释中反复宣传的"ASM 字节码优化、字段访问性能提升 50 倍"实际从未发生。`warmup()`、`JsonWarmupRunner`、`JsonWriter` 构造器预热、Metaspace 监控、类数量阈值降级——这一整套围绕 ASM 的基础设施全部空转。

### F2. 配置仍走 FastJSON1 式全局可变状态

- `JsonConfig` 是单例 + volatile 字段，`apply()` 通过 `SerializationProvider.setXxx()` 静态方法写入 `SerializationContext.CONTEXT` 这个 ThreadLocal。
- `JsonMapper` 声称"实例独立配置"，但底层仍依赖全局 ThreadLocal，靠 `ThreadLocalSnapshot` save/restore 实现"独立"——这是补丁式设计。
- `JsonMapper.configApplied` 是 `volatile` 单标志，多线程下存在可见性/竞态问题：A 线程 apply 后置 true，B 线程并发序列化时该 ThreadLocal 可能已被其他线程覆盖。
- `JsonReader.read()` 每次都 `new ThreadLocalSnapshot + apply + restore`，与 JsonMapper 的 `configApplied` 优化不一致。
- `SerializationContext.reset()` 默认 `failOnError=true`，与 `JsonConfig`/`JsonProperties` 默认 `false` 相反，依赖 `apply()` 传播才一致。

### F3. 6 套不兼容的类型码方案并存

| 来源 | 7 | 8 | 9 | 10 | 13 | 14 |
|---|---|---|---|---|---|---|
| `ValueWriter.TYPE_CODE_*`（准规范） | Short | Byte | Char | Array | Date | BigDecimal |
| `FieldMeta.computeSerializeTypeCode` | char | short | byte | — | date | BigDecimal |
| `AsmBeanCodecGenerator.getTypeCode` | **short** | **byte** | **char** | LocalDateTime | Collection | Map |
| `JsonTypeCode` 枚举 | Short | Byte | Char | Array | Date | BigDecimal |
| `ZeroCopyDeserializer.FieldInfo` | — | — | — | — | — | — |
| `ObjectReader`/`BeanReader` | short/byte/char（两者还不一致） | | | complex | | |

7/8/9 在 `AsmBeanCodecGenerator` 与其余几套**完全相反**。`JsonTypeUtils` 注释声称"统一"，但 ASM 根本不用它——统一是假的。任何跨组件传递 type code 的路径都是 latent bug。

### F4. 多套重复抽象并存

| 概念 | 重复实现 |
|---|---|
| 树模型 | `tree/ObjectNode`+`ArrayNode` 与 `object/JsonObject`+`JsonArray` |
| Serializer 接口 | `api.JsonSerializer`（String 版）与 `serializer.JsonSerializer`（JSONWriter 版），签名不兼容 |
| Deserializer 接口 | `api.JsonDeserializer`（String 版）与 `deserializer.JsonDeserializer`（JSONReader 版） |
| 注册表 | `SerializerRegistry` 与 `JsonModuleRegistry`（内含 serializer/deserializer map） |
| Bean 反序列化器 | `reader/BeanReader`、`reader/ObjectReader`、`bytecode/ZeroCopyDeserializer`（5 个内部 Deserializer） |
| 反序列化路径 | ASM → BeanReader → Creator → Builder → ZeroCopy → Map，共 6 级降级、3 套引擎 |
| JSON Pointer 遍历 | `pointer/JsonPointer` 与 `patch/JsonPatch` 内部重写的 `getByPath/setByPath/removeByPath` |
| 字符串转义 | `tree/TextNode`、`tree/ObjectNode`、`stream/JsonGenerator`、`provider/TypeConverter`、`bytecode/ZeroCopyDeserializer` 5 处，行为不一致（TextNode 漏 `\b`/`\f`/控制字符） |
| 命名策略 | `naming/PropertyNamingStrategy` 与 `annotation/JsonClass.NamingStrategy`（两套枚举、默认值不同） |

### F5. AutoType 安全机制可绕过

- 黑名单质量高（CC1-7、TemplatesImpl、Spring、Log4j2、Shiro、JdbcRowSet 等 gadget 全覆盖）。
- **绕过 1（数组）**：`computeTypeAllowed` 对 `[` 开头的 className 在白名单检查之前直接 `return true`，且黑名单只有 `java.lang.Runtime` 而无 `[Ljava.lang.Runtime;` → **`[Ljava.lang.Runtime;` 同时绕过黑名单与白名单**。
- **绕过 2（包前缀过宽）**：默认 `whitelistPackages=[com.njydsz]`，`addWhitelistPackage` 后任何 `com.njydsz.*` 类自动放行。若攻击者能将恶意类放入该包（classpath 污染/依赖投毒），safeMode 形同虚设。
- **绕过 3（内部类只查一层）**：`A$B` 在黑名单但 `A` 不在时，`A$B$C` 取首个 `$` 前 `A` 不命中 → 通过。

### F6. 缓存并发隐患

- `AsmCodecCache.LruSoftCache`/`LruCache` 在 `StampedLock` 乐观读路径下调用 access-ordered `LinkedHashMap.get()`——`get()` 会重排链表（mutate 内部结构），但乐观读无锁，**与并发写竞争可损坏 LinkedHashMap 内部结构**。这是 JMM 层面的数据竞争。
- `AutoTypeChecker.TYPE_CHECK_CACHE` 是 `synchronizedMap`+access-ordered，全锁下安全但高并发竞争点。
- `util/StringInterner` 快速路径无锁读数组槽与 `synchronized internSlow` 写竞争；Javadoc 声称"LRU 淘汰"但无任何淘汰逻辑（文档与实现不符）。
- `schema/JsonSchemaValidator.PATTERN_CACHE`、`asm/AsmBeanCodecGenerator.FORMATTER_CACHE`、`cache/SerializerCache` 均为**无界 CHM**，Webapp 热部署/动态类加载场景内存泄漏。

### F7. 0 测试覆盖

- `src/test` 目录不存在；POM 声明了 JUnit5 + JMH（test scope）但无任何测试源码。
- `AsmBeanCodecGenerator.resetForTest()`、`AutoTypeChecker.reset()`、`JsonModuleRegistry.clear()` 等"测试用"方法因无测试形同虚设。
- 这也是 F1（ASM 从未生效）能长期存在而未被发现根因——没有一道回归测试能在"ASM 路径是否真正被调用"上提供信号。

### F8. 配置失效与文档漂移

- `JsonAutoConfiguration.JsonConfigBean.init()` 的 Builder 链**遗漏 `wrapRootValue`** → `ydsz.json.wrap-root-value=true` 静默失效。
- `monitoringEnabled` 只 `System.setProperty("ydsz.json.monitoring","true")`，无任何代码读取该属性 → 死配置。
- `JsonProperties.monitoringEnabled` 默认 `true`，但 README/metadata 说 `false`。
- `JsonWarmupRunner` Javadoc 说"异步预热"，实际同步阻塞启动。
- `StringInterner` Javadoc 说"LRU 淘汰"，无淘汰。
- README 注意事项仍写 `@YdszJsonClass`，实际类名 `@JsonClass`。
- spring-configuration-metadata 列出 `LOWER_UNDERSCORE`/`UPPER_SNAKE_CASE` 两个不存在的命名策略常量。

---

## 三、五维度可落地建议

### A. 架构优化（消除全局状态与重复抽象）

**A1. 立即修复 ASM 前缀检查，并加一道 ASM 生效性测试**【P0】
- 现状：F1，ASM 路径从未生效。
- 改法：二选一——
  - (a) 让 `defineInternal` 接受 `beanType.getName()+"_ASM_Serializer"` 这类"宿主包内"类名（移除 `generated.` 前缀硬约束，改为"拒绝黑名单包 + 拒绝已存在类名"的弱校验）；或
  - (b) 将生成类名改为 `generated.` + 某稳定 hash，但需处理 ASM 字节码内对宿主 Bean 的引用（`beanInternalName`）不受影响。
  - 推荐 (a)，改动最小，且与 fastjson2 的"宿主类同包生成"策略一致。
- 配套：加一个单测 `AsmEnabledTest`，断言 `AsmCodecCache.getOrCreateSerializerForType(MyBean.class) != null` 且 `getAsmDowngradeCount()==0`，作为回归闸门。
- 对标：fastjson2 的 ASM 生成类与宿主 Bean 同包，无前缀硬约束。

**A2. 配置从"全局单例 + ThreadLocal"迁移到"实例不可变 + 上下文传参"**【P1】
- 现状：F2，FastJSON1 式反模式。
- 改法：分两步——
  - 第一步（兼容期）：`SerializationProvider` 的静态方法保留但标记 `@Deprecated`，内部改为读取 `SerializationContext` 当前线程实例；`JsonMapper` 序列化时把自己的 `JsonConfig` 注入一个**短生命周期的 context 局部变量**（而非全局 ThreadLocal），消除 `ThreadLocalSnapshot`。
  - 第二步（目标态）：序列化/反序列化方法签名接受 `JsonConfig`/`Feature` 参数（对标 Jackson `ObjectMapper`（持有配置）+ `writeValueAsString` 内部传 `SerializationConfig`）。底层 Writer/Reader 接受配置对象，不再触碰任何 static。
- 收益：真正实现"多 Mapper 实例独立配置"，消除 `configApplied` 竞态。
- 对标：Jackson `ObjectMapper` 是线程安全的有状态对象，配置随实例走，无全局可变状态。

**A3. 收敛重复抽象为单一抽象**【P1】
- 树模型：保留 `tree/JsonNode` 体系（对标 Jackson `JsonNode`），将 `object/JsonObject`/`JsonArray` 改为 `JsonNode` 的便捷视图或直接废弃（标注 `@Deprecated`，提供迁移指南）。fastjson2 的 `JSONObject` 是其特色，但本模块既然已对标 Jackson 树模型，二选一即可。
- Serializer/Deserializer：统一为 `serializer.JsonSerializer`（JSONWriter 版）+ `deserializer.JsonDeserializer`（JSONReader 版），废弃 `api.*`；注解 `@JsonSerialize`/`@JsonDeserialize` 的处理器改为适配新接口。
- 注册表：合并 `SerializerRegistry` 与 `JsonModuleRegistry` 的 serializer/deserializer map 为单一 `SerializerLookup`，对标 Jackson 的 `SerializerProvider`。
- Bean 反序列化器：废弃 `reader/ObjectReader`（疑似半成品，仅 `JSONReader.readArray` 调用），保留 `BeanReader`；`ZeroCopyDeserializer` 评估是否值得保留，若保留则明确其与 `BeanReader` 的分工边界。

**A4. 统一类型码为单一枚举**【P1】
- 现状：F3，6 套方案。
- 改法：以 `ValueWriter.TYPE_CODE_*` 为基准，定义一个 `enum FieldTypeCode`，`FieldMeta`/`AsmBeanCodecGenerator`/`ZeroCopyDeserializer`/`ObjectReader`/`BeanReader` 全部引用同一枚举；删除 `JsonTypeCode`（死枚举）和 `JsonTypeUtils.getTypeCode`（虚假统一）。
- 收益：消除跨组件 type code 传递的 latent bug，降低维护成本。

### B. 功能增强（补齐对标深度）

**B1. 多态反序列化支持 `As.EXTERNAL_PROPERTY`/`EXISTING_PROPERTY`**【P2】
- 现状：`PolymorphicTypeResolver` 仅支持 `As.PROPERTY`。
- 对标：Jackson `@JsonTypeInfo` 全 4 种 `As` 模式。
- 建议：至少补 `EXISTING_PROPERTY`（业务属性复用为类型标识），这是 REST API 多态最常见的场景。

**B2. JSON Schema 补 Draft 07 关键字**【P2】
- 现状：缺 `uniqueItems`/`contains`/`minProperties`/`maxProperties`/`dependencies`/`patternProperties`/`additionalItems`/`propertyNames`/`format`/`$ref`/`definitions`，实际覆盖 ~60%。
- 改法：分两批补齐；`$ref`/`definitions` 优先（复用场景多）。`JsonSchema` 改为不可变（对标 Jackson `JsonSchema`），避免共享/缓存风险。
- 修复 `ValidationResult.valid` 死字段 bug（`isValid()` 忽略入参 `valid`）。

**B3. JsonPath 补标准函数与配置项**【P2】
- 现状：缺 `min`/`max`/`avg`/`sum`/`length`/`keys` 等函数、深层 `..` 谓词、`DEFAULT_PATH_LEAF_TO_NULL` 等配置。
- 改法：引入 `JsonPath.compile(path).read(json, conf)` 两段式 API（对标 Jayway），便于缓存与配置。
- 修复 `evaluateArrayIndex` 对基本类型数组的 `ClassCastException`、`==` 用 double 比较的精度问题。

**B4. JSON Pointer 可组合化**【P3】
- 现状：一次性求值，`patch/JsonPatch` 重写了一套遍历。
- 改法：`JsonPointer` 增加 `append`/`head`/`tail`，`JsonNode.at(JsonPointer)` 集成；`JsonPatch` 复用之，消除重复。

**B5. Record 反序列化支持**【P2】
- 现状：`AsmBeanCodecGenerator` 序列化支持 Record，反序列化显式 `UnsupportedOperationException`——不对称。
- 对标：Jackson 2.12+ 原生支持 Record。
- 建议：至少在反射路径补齐 Record 反序列化（`RecordComponent` API）。

**B6. 补齐缺失注解**【P3】
- 缺 `@JsonMerge`/`@JsonEnumDefaultValue`/`@JsonFilter`/`@JsonIdentityInfo`/`@JsonManagedReference`/`@JsonBackReference`/`@JsonAppend`。
- 建议：按业务实际需要取舍，`@JsonEnumDefaultValue`（枚举反序列化兜底）优先级最高，常见且易实现。

### C. 性能提升（让"快"名副其实）

**C1. 修复 ASM 后重测并对外校准性能声明**【P0，承接 A1】
- ASM 修复后，用模块已声明的 JMH 对比 Jackson/FastJSON2/Gson，给出真实数据。
- 若修复后 ASM 仍不及预期，则**移除 ASM 子系统**（1724 行 + GraalVM 降级 + Metaspace 监控 + 类阈值），改为反射 + `MethodHandle`/`VarHandle` 的稳定高性能路径。运行时字节码生成的复杂度收益比通常不划算（Jackson 不做运行时 ASM，性能仍顶级）。
- 对标：Jackson 不做运行时字节码生成（用编译期 Annotation Processor），FastJSON2 做 ASM 但类型码统一。

**C2. 消除 round-trip 序列化**【P2】
- 现状：`valueToTree`=`toJson`+`readTree`、`convertValue`=`toJson`+`toObject`、`parseObject(json,path,clazz)`=`toJson(value)`+`deserialize`、`BeanDeserializerEngine.deserializeRecord`=`serialize(item)`+再解析。
- 改法：实现"对象→树"和"树→对象"的直接转换（对标 Jackson `valueToTree`/`treeToValue` 不走 String 中转）。

**C3. 实现真正的流式输出**【P2】
- 现状：`JsonHttpMessageConverter.writeStreaming` 与 `serializeToStream` 内部仍生成完整 `byte[]`，注释自承"真正的零内存流式输出需要后续重构"。
- 改法：序列化链增加"写出到 `OutputStream`/`Writer` 的 sink"模式，`JSONWriter` 支持直接写 `byte[]`（UTF-8）到流，不经 `char[]` 中转。对标 Jackson `JsonGenerator` 的 `OutputStream` 后端。

**C4. 修复 LruSoftCache 并发与无界缓存**【P1】
- 改法：access-ordered `LinkedHashMap` 不得在 `StampedLock` 乐观读下 `get`；改为 Caffeine（如可引入）或 `ConcurrentHashMap` + 独立 LRU 链。`PATTERN_CACHE`/`FORMATTER_CACHE`/`SerializerCache` 加上限或改 Caffeine。
- 对标：Jackson 用 `LRUMap`；fastjson2 用 `IdentityHashMap` + 自管。

**C5. InputStream 真流式反序列化**【P3】
- 现状：`toObject(InputStream,...)` 用 `readAllBytes()` 全量读入。
- 建议：`JSONReader` 已是 char[] 流式设计，扩展为 `InputStream`→UTF-8 字节流式解析器，处理大 body 时降低内存峰值。对标 Jackson `readValue(InputStream,...)`。

### D. 体验改善（开发者可用性）

**D1. 修复配置失效项**【P0】
- `JsonConfigBean.init()` Builder 链补 `.wrapRootValue(properties.isWrapRootValue())`。
- `monitoringEnabled` 要么真正读取该属性控制 `JsonMetrics` 注册，要么从配置与文档中删除该字段。

**D2. 修复文档漂移**【P1】
- 逐条修正 F8 中的 9 处文档/代码不一致；建议加一个 `docs-check` 测试断言 README 中的类名/配置项与代码一致。

**D3. 健康检查不要把配置项关闭判为 DOWN**【P1】
- `JsonHealthIndicator` 在 `safeMode=false` 时 `Health.down()`——过于激进，可能引发 K8s liveness 探针重启 Pod。
- 改法：`safeMode=false` 应为 `Health.up().withDetail("safeMode", false).withDetail("warning", "autoType safe mode disabled")`，或拆为独立 health contributor，不污染主健康端点。

**D4. Spring Boot 配置绑定修复**【P1】
- `JsonProperties.namingStrategy` 是接口类型，`ydsz.json.naming-strategy: SNAKE_CASE` 字符串绑定到接口常量需 `Converter`，未提供 → 潜在绑定失败。
- 改法：注册一个 `Converter<String, PropertyNamingStrategy>`，或把字段类型改为 `String` 在 `init()` 里 `valueOf` 解析。

**D5. 错误信息增强**【P2】
- `JsonException(int,String,int)` 把 position 拼进 message，与其他构造器格式不一致——统一为 `getLocation()` 风格（对标 Jackson `JsonProcessingException.getLocation()`）。
- `JsonPatch` 的 "Unknown patch operation: null"（因缺失字段检测失效）应改为明确报 "missing required field: op/path"。

**D6. `JsonObject.put` 契约对齐**【P3】
- `JsonObject.put` 返回 `this`（链式）违反 `Map.put` 返回旧值的语义；`set` 才返回旧值。建议 `put` 对齐 `Map` 契约，链式用 `fluentPut`。

### E. 过度设计收敛（做减法）

**E1. 评估并移除 ASM 子系统（若 C1 后 ASM 仍不达预期）**【P1，承接 C1】
- ASM 子系统 = `asm/AsmBeanCodecGenerator`(1724 行) + `asm/AsmSerializer`/`AsmDeserializer` + `cache/AsmCodecCache` + `GraalVmDetector` + Metaspace 监控 + 类阈值降级 + `JsonWarmupRunner` + `JsonWriter` 预热 + native-image.json。这是模块内最大的复杂度来源。
- 若修复前缀 bug 后 ASM 相对反射的收益不足以证明这套复杂度的合理性，应**整体移除**，改用 `MethodHandle`/`VarHandle` + 字段缓存。运行时字节码生成的工程成本（调试难、GraalVM 兼容、安全管理器、Metaspace 风险）通常不划算——Jackson 顶级性能靠的不是 ASM。
- 这是本模块"过度设计"嫌疑最大的部分。

**E2. 删除死代码与半成品**【P2】
- `reader/ObjectReader`（已被 BeanReader 取代，**已删除**）。
- `type/JsonTypeCode`（死枚举，**已删除**）。
- `JsonTypeUtils.getTypeCode`（虚假统一，未处理）。
- `module/ModuleSerializerRegistry.getOrderedModules()`（**已删除**；`orderedModules` 字段保留未处理）。
- `module/ModuleDeserializerRegistry.getOrderedModules()`（**已删除**）。
- `AsmCodecCache.isEnabled()`（硬编码 return true，未处理）。
- `schema/JsonSchema.boolean_()` 与 `booleanType()`（重复）。
- `annotation/@JsonFormat.locale()`/`timezone()`（明确未实现，要么实现要么删除并在 Javadoc 标注）。
- `monitoringEnabled`（死配置，见 D1）。

**E3. 收敛反序列化降级路径**【P2】
- 现状：ASM → BeanReader → Creator → Builder → ZeroCopy → Map，6 级、3 套引擎。
- 改法：确定主路径（建议 BeanReader + ASM/MethodHandle），其余降级路径明确为"容错兜底"且加日志，避免静默降级掩盖问题。`BeanDeserializerEngine` 每条降级 `catch Exception` 无日志是调试黑洞。

**E4. 引入测试体系**【P0】
- 从 0 到 1：先补三类测试——
  - ASM 生效性（承接 A1，回归闸门）；
  - AutoType 黑白名单（含数组绕过用例，回归闸门）；
  - 序列化/反序列化正确性（基础类型、Bean、泛型、嵌套、循环引用、日期、枚举）。
- 对标：Jackson/FastJSON2 均有数千测试用例；本模块至少先到数百量级，覆盖核心路径。

---

## 四、优先级路线图

| 优先级 | 条目 | 维度 | 预期收益 |
|---|---|---|---|
| **P0** | A1 修复 ASM 前缀检查 + 加生效性测试 | 架构/性能 | 让"快"名副其实，或为 E1 移除决策提供依据 |
| **P0** | E4 引入测试体系（ASM/AutoType/正确性） | 过度设计 | 为后续所有改动提供回归闸门 |
| **P0** | D1 修复配置失效（wrapRootValue/monitoringEnabled） | 体验 | 配置承诺必须兑现 |
| **P1** | C4 修复 LruSoftCache 并发 + 无界缓存 | 性能/安全 | 消除数据竞争与内存泄漏 |
| **P1** | A2 配置去全局化（实例不可变） | 架构 | 真正的多 Mapper 独立，消除竞态 |
| **P1** | A3 收敛重复抽象（树/Serializer/Deserializer/注册表） | 架构 | 降低维护成本，明确边界 |
| **P1** | A4 统一类型码为单一枚举 | 架构 | 消除跨组件 latent bug |
| **P1** | D3 健康检查不再因 safeMode=false 判 DOWN | 体验 | 避免 K8s 误重启 |
| **P1** | D4 Spring 配置绑定 Converter | 体验 | naming-strategy 绑定可用 |
| **P1** | E1 评估移除 ASM（若 C1 后不达预期） | 过度设计 | 大幅降低复杂度 |
| **P2** | B1-B6 功能深度补齐 | 功能 | 对标 Jackson 覆盖率 |
| **P2** | C2 消除 round-trip / C3 真流式输出 | 性能 | 大对象内存与延迟优化 |
| **P2** | E2 删死代码 / E3 收敛降级路径 | 过度设计 | 减负 |
| **P3** | B4 Pointer 可组合 / D5 错误信息 / D6 put 契约 | 功能/体验 | 收尾打磨 |

---

## 五、结论

本模块的**广度对标做得很好**——一个内部团队自研的 JSON 库能覆盖注解、树模型、Schema、Patch、Pointer、Path、Merge、Module、AutoType、GraalVM、Micrometer，野心与执行力都值得肯定。但当前最大的问题不是"功能不够"，而是**"宣称的功能/性能与实际不一致"**：

1. ASM 宣传的 50 倍加速从未生效（前缀检查 bug）；
2. 配置宣称独立实则共享全局 ThreadLocal；
3. StringInterner 宣称 LRU 实则无淘汰；
4. monitoringEnabled 是死配置；
5. wrapRootValue 配置被静默忽略；
6. JsonPath 自动驼峰/下划线转换掩盖字段名 bug。

这些"言行不一"比功能缺失更危险，因为它们会让使用者在错误假设下构建系统。**建议优先级最高的是：修复 ASM + 补测试 + 兑现配置承诺**，先把"宣称即真实"补齐，再谈功能与性能的进一步跃升。在那之前，建议在生产关键路径上**对该模块的"高性能"主张保持审慎**，必要时以 Jackson 作为兜底对照基准。
