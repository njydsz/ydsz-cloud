# ydsz-common-json 模块优化建议报告

> 对标行业主流竞品（Jackson / Fastjson2 / Gson / JSON-P / Hutool）与互联网大厂研发规范，基于 `ydsz-common-json` 最新源码的全面分析。
>
> 分析时间：2026-08-03 ｜ 模块版本：1.0.0 ｜ 源码规模：约 95 个 Java 文件

---

## 一、执行摘要

`ydsz-common-json` 是一个**对标 Jackson/Fastjson2 的自研高性能 JSON 引擎**，技术选型有明确意图：零外部 JSON 库依赖、ASM 字节码加速、零拷贝反序列化、Jackson 兼容注解、RFC 6901/6902/7396/JsonPath/Schema 扩展、Spring Boot 自动装配。整体设计**雄心很大、骨架完整**，在 ASM 直写、FNV-1a 字段哈希、ThreadLocal 合并、AutoType 安全门控等方向上确实对标了 Fastjson2 的核心优化思路。

但精读源码后，结论是：**性能优化与功能广度做得很激进，正确性与工程稳健性欠账严重**。当前版本存在一批会导致**数据损坏、安全绕过、静默错误**的 P0 缺陷，主要集中在解析器转义处理、BeanSerializer 降级路径、JsonPatch/Schema/Pointer 的 RFC 实现三个区域。同时存在明显的过度设计痕迹：三套类型码体系并存、双套树模型 API、大量 `@Deprecated` 方法仍留在主入口类、千行级上帝类。

**核心判断**：模块已具备"能用"的形态，但离"敢上生产关键路径"还有一段距离。建议按 **P0 热修复 → P1 正确性补齐 → P2 架构收敛** 的节奏推进，优先堵住数据损坏与安全漏洞，再谈功能扩展与性能压榨。

---

## 二、模块现状画像

### 2.1 架构分层

```
YdszJson (静态入口) / JsonMapper (实例入口) / JsonConfig (配置)
        │
        ├── 序列化：SerializationProvider → JSONWriter + BeanSerializer + ASM
        ├── 反序列化：DeserializationProvider → JSONReader + BeanReader + BeanDeserializerEngine + ZeroCopyDeserializer
        ├── 解析生成：JsonParserUtil / JsonParser(stream) / JsonGenerator(stream)
        ├── 字段元数据：FieldMeta + FieldMetadataLoader + CreatorResolver + BuilderResolver + PolymorphicTypeResolver
        ├── 缓存：AsmCodecCache(LRU+Soft) / BeanSerializerCache / SerializerCache / SerializerRegistry
        ├── 安全：AutoTypeChecker + AutoTypeWhitelistScanner
        ├── 树模型：tree.JsonNode 体系 + object.JsonObject/JsonArray (已废弃并存)
        ├── RFC 扩展：JsonPointer / JsonPath / JsonPatch / JsonMergePatch / JsonSchema
        ├── 扩展：Module SPI (JsonModule + Registry + Registrar)
        ├── Spring：JsonHttpMessageConverter / JsonAutoConfiguration / JsonWarmupRunner / JsonHealthIndicator
        └── 可观测：MetricsHelper + JsonMetrics(Micrometer) + JsonCacheMetrics
```

### 2.2 设计亮点（值得保留）

1. **ASM 直写 buf/pos**（`AsmBeanCodecGenerator.emitFieldSerializationLoop`）—— 消除 `writer.write` 调用开销，对标 Fastjson2 JIT 内联。
2. **5 级反序列化器分级**（`ZeroCopyDeserializer` Single/Two/UltraFast/Fast/Standard）—— 按字段数/复杂度选策略，思路对标 Fastjson2 反射→MethodHandle→ASM 三级加速。
3. **FNV-1a 字段名哈希**（`JSONReader.readFieldNameHash`）—— 免 String 分配，对标 Jackson `_hashSeed`。
4. **SerializationContext 合并 ThreadLocal** —— 将原 11+ 个 ThreadLocal 收敛为单一实例，降低内存碎片与泄漏面。
5. **AutoTypeChecker 黑名单覆盖 80+ gadget** —— 含 ysoserial/marshalsec/Log4Shell，递归检查内部类与数组元素类型，防绕过意识到位。
6. **JsonHttpMessageConverter 双重防护**（Content-Length 预检 + 流式计数）—— 防 chunked 伪造绕过。
7. **GraalVM 自动降级**（`GraalVmDetector`）—— Native Image 环境自动回退反射路径。
8. **JsonDeserializationException 内置 line/column/contextSnippet** —— 错误定位体验优于 Fastjson2。

### 2.3 对标差距速览

| 维度 | ydsz-common-json | Jackson | Fastjson2 | 差距判断 |
|---|---|---|---|---|
| 解析正确性 | 转义/溢出/EOF 多处 P0 bug | 成熟稳定 | 成熟稳定 | **落后，关键路径不可用** |
| ASM 字节码 | 已实现，单方法 390 行 | 无（反射优化） | 已实现 | 持平，但可维护性差 |
| 注解覆盖 | 29 个，Target 普遍残缺 | 完整 | 完整 | 落后，与 JsonCreator 协同失效 |
| 树模型 | JsonNode + 废弃 JsonObject 双套 | JsonNode 单套 | JSONObject 单套 | **过度设计，需收敛** |
| RFC 扩展 | Patch/Pointer/Path/Schema/Merge 全有 | 全有 | 部分 | 覆盖度持平，但正确性多处 P0 |
| 线程安全模型 | ThreadLocal + Snapshot | 无状态配置 | 无状态配置 | **落后，泄漏风险高** |
| Spring 集成 | Converter 无优先级、WebFlux 仅工具 | 完整 Builder/Encoder/Decoder | 完整 | 落后，可能被 Jackson 抢占 |
| 错误信息 | 有 line/col，但异常分类混乱 | JsonLocation + 精确异常 | 有 | 中等落后 |

---

## 三、五维度可落地优化建议

### 3.1 架构优化

#### A1. 收敛配置模型：单例 + Builder + ThreadLocal Override 三套并存 → 单一不可变配置

**现状**：`JsonConfig` 同时维护 `volatile` 单例（`getInstance()`）、Builder 构建的不可变实例、`THREAD_LOCAL_OVERRIDE` 三套机制。`JsonMapper` 每次序列化都走 `applyConfigIfNeeded()` → `ThreadLocalSnapshot` save/apply/restore，注释中甚至记录过"误写 `restoreConfig(snapshot)` 导致无限递归爆栈"的历史 bug。这是典型的**用 ThreadLocal 模拟无状态**的反模式，复杂且易错。

**建议**：
- 对标 Jackson `ObjectMapper` 的**不可变配置 + 显式传递**模型。`JsonMapper` 持有 final `JsonConfig`，序列化时不再操作 ThreadLocal，而是把配置作为参数传入 `SerializationProvider.serialize(obj, config)`。
- `JsonConfig` 字段全部改 final，移除 `volatile` 单例与 `THREAD_LOCAL_OVERRIDE`。全局默认配置通过 `JsonMapper.getDefault()` 提供。
- `SerializationContext` 保留 ThreadLocal 仅用于缓冲池（StringBuilder/JSONWriter/IdentityHashMap），与配置解耦。

**预期收益**：消除配置污染与泄漏面，序列化路径少一次 snapshot save/restore，可读性大幅提升。
**改动范围**：大（涉及 SerializationProvider/DeserializationProvider/JsonMapper/JsonConfig 全链路）。

#### A2. 收敛树模型：废弃 `object.JsonObject/JsonArray`，统一到 `tree.JsonNode` 体系

**现状**：`object.JsonObject` 继承 `LinkedHashMap`、`object.JsonArray` 继承 `ArrayList`，已 `@Deprecated(since="1.0.0", forRemoval=true)` 但仍在源码，且 `YdszJson.object()`/`YdszJson.array()` 仍在返回它们。`object.JsonObject.getObject` 还在做"toJson 再 toObject"的双重重序列化。双套 API 并存是迁移未完成的技术债。

**建议**：
- 设置一个版本的删除窗口（如 1.1.0），期间 `YdszJson.object()`/`array()` 改为返回 `ObjectNode`/`ArrayNode`。
- 将 `object.JsonObject/JsonArray` 标记 `@Deprecated(forRemoval=true)` 并在编译期输出告警，下个版本直接删除。
- 文档提供 `JsonObject.get("k")` → `ObjectNode.get("k").asText()` 的迁移对照表。

**预期收益**：消除双套 API 维护成本，避免新业务误用废弃 API。
**改动范围**：中（需排查业务侧调用点，但 API 对称易迁移）。

#### A3. 拆分上帝类：`SerializationProvider`（1037 行）/ `AsmBeanCodecGenerator`（1200+ 行）/ `ZeroCopyDeserializer`（1370+ 行）

**现状**：`SerializationProvider` 同时承担 ThreadLocal 管理、主流程编排、ASM 快速路径、Bean 快速路径、自定义序列化器分派、@JsonValue 处理、快照机制、内部 BeanSerializerInfo 八项职责。`AsmBeanCodecGenerator.emitFieldSerializationLoop` 单方法 390 行。`ZeroCopyDeserializer` 内 5 个 Deserializer 重复字段名扫描循环，缺陷易复现难修复。

**建议**：
- `SerializationProvider` 拆为 `CustomSerializerDispatcher`、`FastPathResolver`、`ThreadLocalAccessor` 三个内聚类。
- `AsmBeanCodecGenerator` 按字段类型抽 `emitStringField`/`emitNumberField`/`emitObjectField` 等私有方法，单方法控制在 80 行内。
- `ZeroCopyDeserializer` 抽公共 `scanFieldName(chars, pos)` 工具方法，5 个 Deserializer 共用，顺便修复字段名 `\\` 转义 bug。

**预期收益**：可维护性、可测试性显著提升，缺陷修复不再"按 5 份重复改"。
**改动范围**：大（纯重构，不改行为，需配套回归测试）。

#### A4. 收敛类型码体系：三套并存 → 单一 `FieldTypeCode`

**现状**：`JsonTypeUtils.getTypeCode`(1-6)、`AsmBeanCodecGenerator.getTypeCode`(1-15)、`ZeroCopyDeserializer.FieldInfo.computeTypeCode`(1-6) 三套 int 类型码并存，`FieldTypeCode` 枚举虽已存在并提供了 `fromLegacy(code, source)` 兼容方法，但调用点并未统一。注释称"已统一"实际未统一。

**建议**：以 `FieldTypeCode` 为唯一来源，所有 `getTypeCode` 返回枚举，旧 int 入口走 `fromLegacy` 适配，标注 `@Deprecated`，两个版本后删除。

**预期收益**：消除分支判断不一致导致的序列化/反序列化不对称 bug。
**改动范围**：中。

#### A5. 收敛命名策略：`JsonClass.NamingStrategy` 枚举 vs `PropertyNamingStrategy` 接口 → 统一

**现状**：`@JsonClass` 的 `NamingStrategy` 是枚举，`PropertyNamingStrategy` 是接口（含 LOWER_CAMEL_CASE 等常量），两套不互通，转换需手写映射。同时 `PropertyNamingStrategy.SNAKE_CASE`/`KEBAB_CASE` 对连续大写处理错误（`userID` → `user_i_d`）。

**建议**：`@JsonClass.naming` 直接复用 `PropertyNamingStrategy` 常量；`PropertyNamingStrategy` 实现参考 Jackson `PropertyNamingStrategies` 的状态机写法修正连续大写。

**预期收益**：API 一致性 + 命名转换正确性。
**改动范围**：中。

---

### 3.2 功能增强

#### B1. 补齐注解 Target，使 `@JsonCreator` 真正可用

**现状**：`@JsonProperty` 仅 `FIELD`、`@JsonAlias` 仅 `FIELD`、`@JsonSerialize`/`@JsonDeserialize` 仅 `TYPE`、`@JsonIgnoreProperties` 仅 `TYPE`。无法支持构造器参数级注解（与 `@JsonCreator` 配合失效），无法支持字段级自定义 serializer。

**建议**：统一补 `PARAMETER`/`METHOD`/`FIELD`（按注解语义合理扩展）。对标 Jackson，`@JsonProperty` 应支持 `FIELD`/`METHOD`/`PARAMETER`，`@JsonSerialize`/`@JsonDeserialize` 应支持 `FIELD`/`METHOD`/`TYPE`。

**预期收益**：`@JsonCreator(properties={"id","name"})` + 参数级 `@JsonProperty` 的不可变对象反序列化真正可用。
**改动范围**：小（仅改 `@Target`，但需验证 CreatorResolver/BuilderResolver 能读取参数注解）。

#### B2. 补齐 Module SPI：`setupModule(SetupContext)` + `getSerializer(Type)` + ServiceLoader 自动发现

**现状**：`JsonModule` 仅 `getModuleName()`/`setSerializers`/`setDeserializers`，无 `setupModule` 生命周期钩子；`getSerializer` 只接受 `Class` 不接受 `Type`（无法注册泛型序列化器如 `List<User>`）；自动发现仅靠 Spring Bean，非 Spring 环境无 `ServiceLoader` 路径；`ModuleSerializerRegistry.orderedModules` 是死代码（`register(type,ser,module)` 从未被调用）。

**建议**：
- 增加 `setupModule(SetupContext ctx)`，对标 Jackson `Module.setupModule`，支持在注册时拿到上下文做更灵活配置。
- `getSerializer`/`getDeserializer` 增加 `Type` 重载。
- 在 `META-INF/services/com.njydsz.common.json.module.JsonModule` 走 `ServiceLoader` 自动发现，Spring 环境叠加 Bean 发现。
- 删除 `orderedModules` 死代码。

**预期收益**：扩展性对齐 Jackson，非 Spring 场景（SDK/CLI）开箱可用。
**改动范围**：中。

#### B3. 补齐树模型 API：`deepCopy` / `getNodeType` / `ObjectNode.put(BigDecimal)` / `fields()` 返回 Iterator

**现状**：`JsonNode` 缺 `deepCopy()`、`getNodeType()`（返回枚举）、`withArray`/`withObject`、`fieldNames()` 返回 `Set`；`ObjectNode` 缺 `put(String,BigDecimal/BigInteger/byte[])`、`putNull`、`retain`/`remove(Collection)`、`fields()` 返回 `Iterator<Entry>`；`ObjectNode.set == put`（Jackson 语义 set=覆盖、put=插入，此处混淆）。

**建议**：对标 Jackson `JsonNode`/`ObjectNode` 补全方法。新增 `JsonNodeType` 枚举。

**预期收益**：树模型 API 完整度对齐 Jackson，迁移成本降低。
**改动范围**：中。

#### B4. 拆分 `NumberNode` 为 IntNode/LongNode/DoubleNode/DecimalNode

**现状**：`NumberNode` 单类承载所有数值，`equals` 直接用 `Number.equals`，导致 `new NumberNode(1).equals(new NumberNode(1L))` 为 false（`Integer.equals(Long)` 恒 false），破坏树相等性。`NumberNode(Number)` 允许 null，`asInt()` 直接 `value.intValue()` NPE 而 `asInt(int def)` 检查 null，同字段两种行为。

**建议**：拆分为 `IntNode`/`LongNode`/`DoubleNode`/`DecimalNode`（对标 Jackson），或至少在 `equals` 内做数值归一比较（`intValue() == longValue()`）。

**预期收益**：树相等性正确，`JsonPatch.test` / Schema `const` 校验不再误判。
**改动范围**：中。

#### B5. 补齐专用异常类型 + 修复默认错误码

**现状**：RFC 操作（Patch/Pointer/Schema/Path）抛 `IllegalArgumentException`/`IllegalStateException`/`JsonException`，调用方无法精确 catch。`JsonSerializationException(String)` 默认错误码硬编码 `UNSUPPORTED_TYPE`(2003)，应为 `SERIALIZATION_ERROR`(2005)，所有单参构造异常被误分类。`JsonDeserializationException(String)` 同病。异常码为散落 magic number，无 `ErrorCode` 枚举。

**建议**：
- 新增 `ErrorCode` 枚举统一错误码。
- 新增 `JsonPatchException`/`JsonPointerException`/`JsonSchemaException`/`JsonPathException`，RFC 操作专用。
- 修复 `JsonSerializationException(String)`/`JsonDeserializationException(String)` 默认码。

**预期收益**：可观测性（指标可按错误码聚合）+ 调用方可精确捕获。
**改动范围**：中。

#### B6. WebFlux 声明式集成：实现 `JsonHttpMessageReader/Writer` 注册到 `CodecConfigurer`

**现状**：`JsonReactiveUtils` 仅工具类，无 `HttpMessageWriter/Reader` 注册，WebFlux 非声明式集成。Jackson 有 `Jackson2JsonEncoder/Decoder` 自动注册。

**建议**：实现 `JsonEncoder`/`JsonDecoder`（实现 `org.springframework.core.codec.Encoder/Decoder`），通过 `WebFluxConfigurer.configureHttpMessageCodecs` 注册。

**预期收益**：WebFlux 场景开箱即用，对齐 Spring Boot Jackson 体验。
**改动范围**：中。

---

### 3.3 性能提升

#### C1. 重构 `parseValue` 返回新 pos，消除嵌套解析 O(N²) 双扫描

**现状**：`JsonParserUtil.parseObject/parseArray` 调用 `parseValue` 后，因 `parseValue` 不返回新 pos，必须再走 `getValueEndFast`→`getValueEndPosition` 二次扫描定位结束位置。嵌套对象/数组/字符串全部双扫。`ZeroCopyDeserializer.parseValue/parseValueWithFieldInfo` 同样不返回 pos，调用方靠 `skipToNext` 二次扫描整个嵌套值，深度嵌套 O(N²)。Fastjson2/Jackson 均返回新 pos。

**建议**：`parseValue` 改为返回 `(value, newPos)`（用内部 holder 或拆成 `parseValue`+`getCurrentPos`），消除 `getValueEndFast`/`skipToNext` 二次扫描。

**预期收益**：嵌套解析性能 30-50% 提升。
**改动范围**：中（涉及解析器核心，需配套回归测试）。

#### C2. `StringInterner` 去 synchronized + 实现真 LRU

**现状**：`StringInterner.internSlow` 是 `synchronized` 方法，高并发解析热路径锁竞争；`hash` 仅取首 2+末 2 字符，同长度同首尾串高碰撞；文档声称有 LRU 实际未实现，无淘汰导致内存泄漏。

**建议**：改 `ConcurrentHashMap` + FNV-1a 全量 hash；LRU 用 `LinkedHashMap(accessOrder=true)` 包一层 `Collections.synchronizedMap` 或 Caffeine。

**预期收益**：高并发吞吐提升 + 防内存泄漏。
**改动范围**：中。

#### C3. `JSONWriter.writeCollection` 加 `RandomAccess` 判断

**现状**：`writeCollection`/`writeCollectionWithSerializer` 对 `instanceof List` 一律 `list.get(i)`，LinkedList 退化为 O(N²)。

**建议**：`if (list instanceof RandomAccess)` 走 `get(i)`，否则走 `Iterator`。

**预期收益**：LinkedList 序列化 O(N²)→O(N)。
**改动范围**：小。

#### C4. `JsonGenerator.escapeString` 去 `String.format` + 缓冲化

**现状**：控制字符用 `String.format("\\u%04x")`，每字符一次 `Formatter` 分配；生成器无缓冲，每个 `writer.write('{')` 即一次 IO。

**建议**：内建 `char[]` 缓冲，控制字符直接 `buf[pos++]='\\'; buf[pos++]='u'; ...` 直写；批量 flush。

**预期收益**：流式写入吞吐 5-10x。
**改动范围**：中。

#### C5. `AsmCodecCache.LruSoftCache` 改真 LRU

**现状**：`put` 时 `ConcurrentHashMap.keySet().iterator().next()` 删首元素，但 CHM 无访问序，实为随机淘汰，命中率低于真 LRU；`map.size()>maxSize` 与 remove 间有竞态。Jackson 用 `LRUMap`(LinkedHashMap+synchronized)。

**建议**：改 `Collections.synchronizedMap(new LinkedHashMap<>(cap, 0.75f, true))`（accessOrder=true）或直接用 Caffeine。

**预期收益**：缓存命中率提升，ASM 字节码重生成减少。
**改动范围**：小。

#### C6. `ValueWriter` 日期格式器改 ConcurrentHashMap 缓存

**现状**：`cachedDateFormat`/`cachedFormatter` 是 volatile 单槽，多 `@JsonFormat` pattern 交替时反复覆盖重编译 `ofPattern`。

**建议**：改 `ConcurrentHashMap<String,DateTimeFormatter>`。

**预期收益**：多日期格式场景吞吐提升。
**改动范围**：小。

#### C7. `AutoTypeChecker` 移入 `computeIfAbsent` + `TYPE_CHECK_CACHE` 换 ConcurrentHashMap

**现状**：`ZeroCopyDeserializer.getDeserializer` 每次 cache 查找前都执行 `AutoTypeChecker.checkType`，应移入 `computeIfAbsent`；`TYPE_CHECK_CACHE` 是 `synchronizedMap` + `computeIfAbsent` 非原子，高并发下可能 CME 或重复计算。

**建议**：cache 查找走 `computeIfAbsent`，checkType 在 absent 分支内执行；`synchronizedMap` 换 `ConcurrentHashMap`。

**预期收益**：热路径性能 + 并发安全。
**改动范围**：小。

#### C8. `JsonSchema` 编译缓存

**现状**：`JsonSchemaValidator` 每次 `validate` 重新遍历 schema 树，`PATTERN_CACHE` 仅缓存 Pattern 对象，schema 结构未编译，重复校验开销大。

**建议**：schema 首次 `validate` 时编译为校验器对象（解析 `$ref`、预编译 pattern、缓存子 schema），后续校验走编译态。

**预期收益**：重复校验性能显著提升。
**改动范围**：大。

---

### 3.4 体验改善（DX / 可观测性）

#### D1. P0 热修复：解析器转义与降级路径的数据损坏

**现状**（详见第四节 P0 清单）：
- `JsonParserUtil.parseStringFast` 遇 `\\` 仅置 `hasEscape` 不跳下一字符，`\"` 被误判为结束引号，`"a\"b"` → `a"`。
- `BeanSerializer.write` default 分支对嵌套 Bean/Collection/Map/Date/BigDecimal 一律 `value.toString()`，产出非法 JSON（`Foo{x=1}`），GraalVM/ASM 降级路径直接踩坑。
- `NumberUtils.parseInt/parseLong` 无溢出检测，`"2147483648"` 静默回绕。

**建议**：见第四节 P0 清单逐条修复。这些是**数据损坏级**缺陷，优先级最高。

**预期收益**：消除数据损坏，关键路径可用。
**改动范围**：小-中。

#### D2. Spring 集成：Converter 优先级 + Jackson 自动配置前置

**现状**：`JsonHttpMessageConverter` 未通过 `WebMvcConfigurer.configureMessageConverters` 前置或 `@Order`，Spring Boot 默认 `MappingJackson2HttpMessageConverter` 可能排前导致 YdszJson 形同虚设。`JsonAutoConfiguration` 未 `@AutoConfigureBefore(JacksonAutoConfiguration.class)`，两套 JSON 栈共存。

**建议**：
- 提供 `WebMvcConfigurer` 将 YdszJson converter 置首。
- `JsonAutoConfiguration` 加 `@AutoConfigureBefore(JacksonAutoConfiguration.class)`，并在文档引导用户 `spring.autoconfigure.exclude=...JacksonAutoConfiguration`。
- 健康检查增加 `converterActive` 指标，暴露当前实际生效的 JSON Converter。

**预期收益**：确保 YdszJson 真正生效，避免"引入了却没用"的隐性失效。
**改动范围**：中。

#### D3. ThreadLocal 请求级清理

**现状**：`SerializationProvider.serialize` finally 仅 `serializingObjects.clear()`，未 `CONTEXT.remove()`；`JsonHttpMessageConverter` 请求结束未调 `clearThreadLocals()`。Tomcat 线程池每线程常驻 ~16KB（4096 StringBuilder + JSONWriter + IdentityHashMap），200 线程≈3.2MB 不释放。`YdszJson.cleanup()`/`scopedContext()` 虽存在但已 `@Deprecated`，业务侧无所适从。

**建议**：
- 在 `JsonHttpMessageConverter` 或加 `OncePerRequestFilter`/`HandlerInterceptor` afterCompletion 调 `SerializationProvider.clearThreadLocals()`。
- 取消 `cleanup()`/`scopedContext()` 的 `@Deprecated`，或在文档明确推荐用法（非 Spring 环境用 `scopedContext()` try-with-resources）。

**预期收益**：消除线程池泄漏。
**改动范围**：小。

#### D4. 降级链异常透传 + 上下文链

**现状**：`BeanDeserializerEngine` 多级降级 `catch(Exception){}` 全吞噬；ZeroCopy 失败后 `clazz.cast(parseObject(json))` 对 Bean 必抛 ClassCastException 且丢根因；`deserializeBeanListWithZeroCopy` 元素失败静默填 null，数据丢失无感知。`CreatorResolver`/`BuilderResolver` 同样 `catch(Exception){return null}` 或 `return clazz.cast(map)`（对 Bean 必 CCE）。`TypeConverter.parseIntValue` 解析失败返回 0/false，金融场景静默数据错误。

**建议**：
- 每级 catch 包装 `JsonDeserializationException` 带 phase/path，禁止静默 null。
- 列表失败元素记录到指标而非静默填 null。
- `TypeConverter` 解析失败抛异常，或增加 `ydsz.json.strict-number-parse` 开关（默认 false 兼容，true 抛异常）。

**预期收益**：可观测性 + 正确性，金融场景可用。
**改动范围**：中。

#### D5. 错误定位增强：补 token 类型 / SourceLocation

**现状**：异常仅含 position（部分有 line/column），无 token 类型/SourceLocation。Jackson 有 `JsonLocation`。

**建议**：异常增加 `tokenType`（如 `VALUE_STRING`/`FIELD_NAME`）与 `sourceSnippet`（出错位置前后 20 字符），对标 Jackson `JsonLocation` + `JsonParseException`。

**预期收益**：排障效率。
**改动范围**：中。

---

### 3.5 过度设计收敛

#### E1. 清理 `YdszJson` 上的 `@Deprecated` 方法群

**现状**：`YdszJson` 上 `getByPath`/`getByPointer`/`merge`/`diff`/`isValid`/`cleanup`/`scopedContext`/`setExcludedFields`/`clearExcludedFields`/`toJson(obj,config)`/`parseObjectToJsonObject`/`parseArrayToJsonArray`/`valueToTree`/`createGenerator` 等 14+ 个方法标 `@Deprecated(since="1.0.0", forRemoval=true)`。一个 1.0.0 版本的库，主入口类上废弃方法占比近 30%，说明 API 设计在发布前未收敛。

**建议**：
- `valueToTree`/`scopedContext` 这类**语义正确、无替代品**的方法，取消 `@Deprecated`（`JsonMapper.valueToTree` 未废弃，说明方向认可）。
- `getByPath`/`getByPointer`/`merge`/`diff`/`isValid` 这类**有 `JsonMapper` 替代**的方法，设定 1.1.0 删除窗口，期间编译告警。
- `parseObjectToJsonObject`/`parseArrayToJsonArray` 配合 E2 一起删。

**预期收益**：API 表面清晰，新用户不误用废弃方法。
**改动范围**：小（需排查业务调用点）。

#### E2. 删除废弃 `object.JsonObject/JsonArray`（与 A2 配套）

详见 A2。

#### E3. 合并 `parseObject`/`parseObjectRecursive` 等重复方法

**现状**：`JsonParserUtil.parseObject` 与 `parseObjectRecursive` 几乎重复，仅初始容量不同（16 vs 64）。

**建议**：合并为单一方法，初始容量参数化。

**预期收益**：消除重复代码维护成本。
**改动范围**：小。

#### E4. 清理死代码：`ModuleSerializerRegistry.orderedModules` / `SerializerCache.putBeanSerializerInfo` / `BeanSerializerInfo` 命名冲突

**现状**：`ModuleSerializerRegistry.orderedModules` 是 package-private `register(type,ser,module)` 从未被调用；`SerializerCache.putBeanSerializerInfo` 似无人调用；`provider/SerializationProvider$BeanSerializerInfo`（validFields+hasAnnotations）与 `cache/BeanSerializerInfo`（clazz+SerializedField[]）结构不同却同名。

**建议**：删除死代码；`provider` 内部类重命名为 `BeanSerializerProfile` 或 `CachedBeanMeta` 消除歧义。

**预期收益**：降低认知负担。
**改动范围**：小。

#### E5. 评估 RFC 扩展的"是否自研"必要性

**现状**：自研了 JsonPath/JsonPatch/JsonMergePatch/JsonPointer/JsonSchema 五个 RFC 扩展，且多处 P0 正确性 bug（详见第四节）。这些功能在生态里有成熟实现（Jayway JsonPath、Jakarta JSON-P、networknt json-schema-validator）。

**建议**：评估"零外部依赖"这一核心约束的边界。若约束不可破，则必须补齐这些 P0 并补充完整 RFC 测试套件（如 JSON-Schema-Test-Suite）；若约束可放宽，可考虑 RFC 扩展层引入成熟库，核心序列化保持自研。

**预期收益**：聚焦核心能力，降低自研 RFC 实现的维护风险。
**改动范围**：需产品级决策。

---

## 四、P0 热修复清单（最高优先级）

以下为会导致**数据损坏、安全绕过、静默错误**的 P0 缺陷，建议作为热修复立即落地。

| # | 类:方法 | 问题 | 影响 | 修复 | 改动 |
|---|---|---|---|---|---|
| P0-1 | `JsonParserUtil.parseStringFast` L323-348 | fast path 遇 `\\` 仅置 `hasEscape` 不跳下一字符，`\"` 误判为结束引号 | `"a\"b"` → `a"`，数据损坏 | 加 `pos++` 跳被转义字符 | 小 |
| P0-2 | `BeanSerializer.write` default 分支 L232-254 | 嵌套 Bean/Collection/Map/Date/BigDecimal 一律 `value.toString()` | 产出非法 JSON（`Foo{x=1}`），GraalVM/ASM 降级路径踩坑 | `value.toString()` → 委托 `writeValueInline` | 小 |
| P0-3 | `NumberUtils.parseInt/parseLong` L207-348 通用路径；`JSONReader.readInt/readLong`；`ZeroCopyDeserializer.parseIntDirect/parseLongDirect` | 无溢出检测 | `"2147483648"` 静默回绕 | 累加前 `if (r > MAX/10 \|\| (r==MAX/10 && d>MAX%10))` 回退 BigDecimal/Long | 中 |
| P0-4 | `JsonParserUtil.parseNumberFast` L416-497 整数分支 | `intValue*10+digit` 无 long 溢出检测 | 19 位以上静默回绕 | 同 P0-3 | 中 |
| P0-5 | `JsonPatch.apply` "replace" L83,162-165 | replace 复用 `setByPath`，数组 `setByPath` 执行 `List.add(idx,value)`（插入） | 数组 replace 结果多一个元素，违反 RFC 6902 | replace 单独走 `List.set` | 小 |
| P0-6 | `JsonPatch.apply` L56-106 | 循环中第 N 个 op 失败时前 N-1 个已 mutate target，无 rollback | 非原子，违反 RFC 6902 隐含语义 | 先深拷 target 再 apply，失败抛 `JsonPatchException` 不污染原对象 | 中 |
| P0-7 | `JsonSchemaValidator.matchesType("integer")` L367-368 | 仅认 Integer/Long，Short/Byte/BigInteger/AtomicInteger 判 false | 校验绕过 | 扩展为 `Integer/Long/Short/Byte/BigInteger/AtomicInteger` | 小 |
| P0-8 | `JsonSchemaValidator.validateString` pattern L213 | 用 `.matches()`（全匹配），JSON Schema `pattern` 语义是 partial match | 合法数据被误判 | `.matches()` → `.matcher(str).find()` | 小 |
| P0-9 | `JsonPath.evaluateProperty` L182-188,348-353 | 隐式驼峰/下划线回退，`$.user_name` 命中 key `userName` | 绕过基于精确字段名的字段过滤/脱敏 | 删除隐式回退，或显式 opt-in 配置 | 中 |
| P0-10 | `NumberNode.equals` L96-104 | `new NumberNode(1).equals(new NumberNode(1L))` 为 false | 破坏树相等性，JsonPatch.test / Schema const 误判 | equals 内数值归一比较，或拆分 IntNode/LongNode（B4） | 中 |
| P0-11 | `JsonPointer.head()/tail()` L153-176 | 双重反转义，`/a~1b` → head 返回 `/a/b`（非法） | 指针运算错误 | tokens 保留原始转义形式，访问时再 unescape | 小 |
| P0-12 | `JsonNode.path(String)` L103-124 | 直接 `split("/")`，不做 `~0`/`~1` 反转义 | 与同包 `JsonPointer` 语义冲突 | 改为单字段访问（Jackson 语义），路径访问走 `at(JsonPointer)` | 小 |
| P0-13 | `TreeConverter.convertToJsonNode` L62 | `(String) entry.getKey()` 无校验 | 非 String key 抛 ClassCastException | 校验 key 类型，非 String 抛带上下文异常 | 小 |
| P0-14 | `AutoTypeChecker.computeTypeAllowed` + `AutoTypeWhitelistScanner` L62 | `addWhitelistPackage("com.njydsz")` 注册整包前缀，`startsWith` 放行全包任意类 | 若包内存在 JNDI/反射入口类，`@type` 指向即可绕过 | 移除自动注册基础包，改为仅精确类名 + 显式 opt-in 包前缀 | 小 |

> **注**：P0-1/2/3 是"数据损坏"级，P0-9/14 是"安全绕过"级，建议合并为一个热修复版本紧急发布。

---

## 五、P1 正确性补齐清单

| # | 类:方法 | 问题 | 修复 | 改动 |
|---|---|---|---|---|
| P1-1 | `JSONReader.readObjectMap` L846-863 / `readArray` L806-819 | skipWhitespace 后 `buf[pos]` 未做 `pos<len` 检查 | EOF 时 AIOOBE | 加边界检查 | 小 |
| P1-2 | `JSONReader.readStringWithEscape` L544-572 | `\uXXXX` 不处理代理对，emoji 拆成两个孤立 char | 合并代理对（与 `ZeroCopyDeserializer.parseString` L1146 对齐） | 中 |
| P1-3 | `JsonParser.fillBuffer` L447-467 | bufferPos==0 且 buffer 满 时无扩容，单 token>16KB 死循环；`\u` 跨缓冲区边界直接抛异常 | 几何扩容 + 跨边界 `\u` 处理 | 中 |
| P1-4 | `JsonParserUtil.parseValue` L301-309 / `JSONReader.skipValue` L774-776 | `t/f/n` 不校验后续 `rue/alse/null`，`tXYZ` 当作 true | 补完整 token 校验 | 小 |
| P1-5 | `JsonParser.nextToken` L202-207 | `,`/`:` 递归 nextToken 不计入 depth | 恶意 `[[[,]...` 绕过 maxDepth 栈溢出 | 递归调用计入 depth | 小 |
| P1-6 | `JSONReader.matchField` L364-381 | 字段名部分匹配时 pos 处于字段名中间调 skipValue | 状态错乱 | 校验完整匹配后再 skip | 小 |
| P1-7 | `BeanSerializer.writeAnyGetterProperties` L276-313 | bean 无常规字段时（first==true）在 `}` 前插逗号 | 输出 `{,"k":v}` | 修正 first 标志判断 | 小 |
| P1-8 | `ZeroCopyDeserializer` 5 个 Deserializer 字段名扫描 | `while(chars[nameEnd]!='"')` 不处理 `\\` 转义 | 含 `\"` 字段名错位 | 抽公共 `scanFieldName` 处理转义（与 A3 配套） | 中 |
| P1-9 | `JsonMergePatch.merge` L53-58 | patch 为 null/空串时返回 target，但 RFC 规定 patch 为 null 时结果为 null（删除语义） | 把"null 字符串"与"JSON null"混淆 | 区分 null 字符串与 JSON null | 小 |
| P1-10 | `JsonPatch.test` L96 | 用 `Objects.equals`，JSON `1`(Integer) 与 `1.0`(Double) 判不等 | RFC 6902 test 应按数值相等 | 数值归一比较 | 小 |
| P1-11 | `JsonSchema` additionalProperties | 仅 `JsonSchema` 类型无 boolean 重载；validateObject 先判 additionalProperties 再判 patternProperties，顺序与 RFC 相反 | 支持 `boolean false`；顺序改为先 patternProperties 后 additionalProperties | 中 |
| P1-12 | `JsonSerializationException(String)` / `JsonDeserializationException(String)` | 默认错误码错（硬编码 UNSUPPORTED_TYPE / TYPE_MISMATCH） | 所有单参构造异常被误分类 | 修正默认码（与 B5 配套） | 小 |
| P1-13 | `PropertyNamingStrategy.SNAKE_CASE`/`KEBAB_CASE` L64-75,86-97 | `userID` → `user_i_d` | 连续大写处理错误 | 参考 Jackson 状态机重写 | 中 |
| P1-14 | `JsonPath.matchesFilter` L309-327 | 先 split `&&` 再 split `||`，`a \|\| b && c` 被解析为 `(a\|\|b) && c` | 运算符优先级错（应 `&&` 优先于 `\|\|`） | 用递归下降解析器替代 split | 中 |
| P1-15 | `JsonPath.compare` L370-376 | 用 `==` 比 double | 浮点等值不可靠 | 用 `Double.compare` 或 `BigDecimal` | 小 |
| P1-16 | `NumberNode(Number value)` L37-39 | 允许 null，`asInt()` 直接 `value.intValue()` NPE 而 `asInt(int def)` 检查 null | 同字段两种行为 | 构造器拒绝 null 或统一 null 检查 | 小 |
| P1-17 | `ValidationResult` L27-63 | `new ValidationResult(true)` 后 `addError`，`isValid()` 返回 false 但 `toString()` 仍输出 `valid=true` | 状态不一致 | addError 时同步更新 valid 字段 | 小 |

---

## 六、落地路线图

### 阶段一：热修复（1-2 周，目标：堵住数据损坏与安全漏洞）
- 全部 14 项 P0
- 配套回归测试：每个 P0 一个反例用例（`"a\"b"`、`2147483648`、`/a~1b` 等）
- 发布 `1.0.1-hotfix`

### 阶段二：正确性补齐（2-4 周，目标：RFC 扩展可用、降级链可观测）
- 全部 17 项 P1
- D2（Spring Converter 优先级 + Jackson 前置）
- D3（ThreadLocal 请求级清理）
- D4（降级链异常透传）
- B1（注解 Target 补齐）、B5（异常体系）
- 引入 JSON-Schema-Test-Suite 跑 Schema 校验正确性

### 阶段三：架构收敛（4-8 周，目标：降低维护成本）
- A1（配置模型收敛）
- A2（树模型收敛）
- A3（拆分上帝类）
- A4（类型码收敛）
- A5（命名策略收敛）
- E1-E4（废弃 API 与死代码清理）

### 阶段四：性能与功能（持续）
- C1-C8（性能优化）
- B2-B4、B6（功能增强）
- D5（错误定位增强）
- E5（RFC 自研边界评估）

---

## 七、风险提示

1. **当前版本不建议用于金融/计费等强一致场景**：P0-3/4 整数溢出静默回绕、P1-12 异常码误分类、TypeConverter 解析失败返回 0，叠加起来在数值敏感场景会产生难以定位的数据错误。
2. **AutoType 包前缀白名单需立即收紧**：P0-14 是潜在 RCE 入口，在多业务方共享 `com.njydsz` 基础包时风险尤其高。
3. **Spring 集成可能隐性失效**：D2 未处理前，引入 `ydsz-common-json` 不等于启用 YdszJson，建议在健康检查增加 `converterActive` 指标确认实际生效。
4. **RFC 扩展（Patch/Schema/Path）正确性不足**：在用于 API 网关的请求校验、配置补丁等场景前，必须先完成 P0-5~P0-13 修复并跑通标准测试套件。

---

_本报告基于 `ydsz-common-json` 1.0.0 源码逐文件精读得出，所有发现均具体到类:方法/行号，可直接据此排期。_
