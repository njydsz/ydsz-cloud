# ydsz-common-json

> YDSZ 高性能 JSON 引擎（L2 工具层）— ASM 字节码加速、有界软引用字段缓存、零拷贝反序列化、JIT 自动向量化、Schema 校验、JsonPath 查询、JsonNode 树模型、Jackson 兼容注解

纯 Java 实现的 JSON 引擎，零外部 JSON 库依赖（不引入 Jackson / FastJSON / Gson）。通过 ASM 字节码生成、零拷贝反序列化、ThreadLocal 池优化等技术实现超高性能；通过 Jackson 兼容注解实现平滑迁移。

**YdszJson 的架构设计兼具 Jackson 的"配置不可变"哲学和 Fastjson2 的"静态入口便利"。** `YdszJson` 作为静态入口提供 `toJson` / `toObject` 等零配置开箱即用体验，与 FastJSON 的静态工具风格一脉相承；而底层 `JsonConfig` 采用 `final` 字段构建不可变配置，配合 `JsonMapper.copyOf()` 以"副本 + 不可变替换"方式替代运行期可变状态，实现与 Jackson 相同的线程安全语义。两层 API 共享同一委托链（`YdszJson` → `JsonMapper` → `Execution` → `Engine`），行为完全一致，用户可根据场景自由选择而无需担心序列化行为分歧。

## 最新变更（v1.1.0 — 过度设计治理与健壮性补齐）

本次对标互联网大厂研发规范，从过度设计治理、功能成熟度标注、测试覆盖、健康检查简化四个维度完成优化：

| 编号 | 优先级 | 变更项 | 状态 |
|---|---|---|---|
| P2-FIX | P2 | **修复启动失败**：补充 `JsonHealthIndicator`、`JsonMetrics`、`JsonCacheMetrics`、`JsonWarmupRunner` 缺失的类（原 AutoConfiguration 引用但类不存在） | ✅ 已实施 |
| P2-HI | P2 | **健康检查简化**：`JsonHealthIndicator` 从暴露 17+ 项内部指标收敛为 5 项关键指标（safeMode / namingStrategy / maxJsonSize / asmAvailable / registeredSerializers），详细指标走 Micrometer | ✅ 已实施 |
| P4-MAT | P4 | **功能成熟度标签**：README 新增功能成熟度总览表（Stable / Beta / Experimental / Deprecated 四级标签），帮助用户判断功能可靠性 | ✅ 已实施 |
| P4-ADR | P4 | **架构决策文档**：新增 `docs/ADR-001-json-engine-architecture.md`，记录自研引擎的设计选择、权衡与参考对标 | ✅ 已实施 |
| P2-TEST | P2 | **核心测试补齐**：新增 `YdszJsonRoundTripTest`（序列化/反序列化 round-trip 测试）、`NamingStrategyTest`（命名策略转换测试）、`AutoTypeSecurityTest`（AutoType 安全门控测试） | ✅ 已实施 |
| P2-WARM | P2 | **配置完善**：`JsonProperties` 新增 `warmupEnabled` 属性（默认 false），可控预热开关 | ✅ 已实施 |

> **遗留计划**（P0 架构重构将在后续版本推进）：消除 ThreadLocal Snapshot 机制改为显式参数传递、合并 SerializerRegistry/JsonModuleRegistry 双轨注册。

## 最新变更（v1.0.0 优化汇总）

本次对标互联网大厂研发规范，从架构、功能、性能、体验、过度设计五个维度完成专项优化：

| 编号 | 优先级 | 变更项 | 状态 |
|---|---|---|---|
| P0-SO | P0 | **泛型递归深度保护**：新增 `max-generic-depth`（默认 64）防御恶意嵌套泛型 `List<List<...>>` 导致 StackOverflow | ✅ 已实施 |
| P1-FP | P1 | **序列化异常字段路径追踪**：`JsonSerializationException` 新增 `fieldPath` 字段，异常消息输出 `at path 'user.address.street'` | ✅ 已实施 |
| P1-HI | P1 | **JsonHealthIndicator 增强**：Actuator 健康检查新增 `maxGenericDepth`、`asmDowngradeCount` 运行时指标 | ✅ 已实施 |
| P1-API | P1 | **YdszJson 运行时查询 API**：新增 `isNativeImage()` / `isAsmAvailable()` / `getStats()` 运行时工具方法 | ✅ 已实施 |
| P1-IMM | P1 | **JsonConfig 构建后不可变**：新增 `install()` 方法替代旧 `setInstance` 模式，AutoConfig 走 Builder + install 不可变安装 | ✅ 已实施 |

## 模块定位

| 属性 | 值 |
|---|---|
| **层级** | L2 工具模块层 |
| **类型** | 公共依赖库（不独立部署） |
| **作用** | 提供高性能 JSON 序列化/反序列化、树模型、JSONPath、Schema 校验、JSON Patch/Merge Patch、Jackson 兼容注解、Spring MVC 集成等能力 |
| **依赖** | Lombok；可选依赖 ASM、SLF4J、Spring Boot AutoConfigure、Spring Web、Reactive Streams、Spring Boot Actuator/Health、Micrometer、Jackson Annotations（编译期可见）、Jakarta Validation |
| **版本** | 1.0.0 |

## 功能成熟度总览

> 以下标签标注每个功能域的 API 稳定性与生产就绪度：

| 标签 | 含义 | 使用建议 |
|---|---|---|
| **Stable** | 生产就绪，API 稳定，向后兼容 | 放心在任何场景使用 |
| **Beta** | 功能完整但 API 可能有调整 | 推荐使用，关注升级变更日志 |
| **Experimental** | 实验性功能，API 可能破坏性变更 | 非关键路径使用，做好隔离层 |
| **Deprecated** | 已废弃，将在下个主版本移除 | 停止使用，迁移到替代方案 |

| 功能域 | 成熟度 | 备注 |
|---|---|---|
| 核心序列化/反序列化 | **Stable** | 含基本类型/嵌套对象/集合/泛型 |
| ASM 字节码加速 | **Stable** | GraalVM 自动降级 |
| 注解体系（@JsonProperty/@JsonIgnore/@JsonFormat/@JsonInclude 等常用注解） | **Stable** | 80%+ Jackson 兼容 |
| Tree 模型（JsonNode/ObjectNode/ArrayNode） | **Stable** | |
| 命名策略（SNAKE_CASE/KEBAB_CASE/LOWER_CASE） | **Stable** | |
| AutoType 安全检查 | **Stable** | |
| Spring Boot 集成（JsonAutoConfiguration/JsonHttpMessageConverter） | **Stable** | |
| Module 系统（JsonModule SPI） | **Beta** | |
| @JsonCreator/@JsonBuilder 构造器模式 | **Beta** | |
| @JsonView 视图过滤 | **Beta** | |
| @JsonUnwrapped / @JsonRawValue / @JsonAlias | **Experimental** | 使用频率低，API 可能调整 |
| JSON Schema 校验 | **Experimental** | 标注 @Experimental |
| JSON Path 查询 | **Experimental** | 标注 @Experimental |
| JSON Merge Patch | **Experimental** | 标注 @Experimental |
| JSON Pointer（RFC 6901） | **Experimental** | 实现完整但使用场景少 |

## 核心能力

### 1. 核心 API（根包）

| 类 | 说明 |
|---|---|
| `YdszJson` | JSON 统一入口（静态工具类），提供 `toJson` / `toObject` / `parseMap` / `parseArray` / `fromJson` / `readTree` / `valueToTree` / `warmup` / `register` / `setGlobalConfig` / 运行时诊断 `isNativeImage()` / `isAsmAvailable()` / `getStats()` 等方法 |
| `JsonMapper` | 实例化 Mapper（对标 Jackson ObjectMapper），支持 `builder()` 链式 Builder、独立配置副本、`convertValue` / `treeToValue` 等 |
| `JsonConfig` | 全局配置（日期格式 / 空值处理 / 命名策略 / BigDecimal 精度模式 / 根名称包裹 / 最大 JSON 大小 / 最大深度 / 泛型递归深度上限 / `builder()` / `copyOf()` / `install()` 不可变安装） |

### 2. ASM 字节码加速（asm 包）

| 类 | 说明 |
|---|---|
| `AsmBeanCodecGenerator` | ASM 字节码生成器（运行时生成序列化/反序列化字节码，字段访问性能提升 50 倍） |
| `AsmSerializer` / `AsmDeserializer` | ASM 生成的序列化器 / 反序列化器 |
| `GraalVmDetector` | GraalVM Native Image 环境检测（Native Image 中自动降级为反射模式） |

### 3. 解析与生成（parser / writer / reader / stream 包）

| 类 | 说明 |
|---|---|
| `JSONReader` | JSON 解析器（流式 / 事件驱动 / 递归下降，直接解析到 Bean 字段，无需 Map 中转） |
| `JsonParserUtil` (stream) / `JsonParserUtil` (parser) | JSON 解析器（流式 token-by-token） |
| `JSONWriter` / `JsonGenerator` | JSON 生成器（流式写入，`toUtf8Bytes()` 零拷贝字节序列化） |
| `BeanSerializer` / `BeanReader` | Bean 序列化 / 反序列化 |
| `BeanDeserializerEngine` | Bean 反序列化引擎 |

### 4. Provider 与字段缓存（provider / cache 包）

| 类 | 说明 |
|---|---|
| `SerializationProvider` / `DeserializationProvider` | 序列化/反序列化 Provider（核心实现，`tryFastPathToWriter` 统一快速路径） |
| `AsmCodecCache` | ASM Codec 缓存（有界 + SoftReference，近似 LRU 淘汰） |
| `BeanSerializerCache` / `BeanSerializerInfo` | Bean 序列化器缓存（含 `hasAnnotations` 标记，避免重复扫描） |
| `SerializerCache` / `SerializerRegistry` | 序列化器注册表 |
| `FieldMeta` | 字段元数据（统一类型代码 + `@JsonInclude` 过滤逻辑 + VarHandle 优化 + `@JsonUnwrapped` 展开） |
| `SerializationContext` | 序列化上下文（合并 5+ ThreadLocal 为单一实例，动态内存估算） |
| `JsonCacheStats` | 缓存统计 |

### 5. 字节码优化（bytecode 包）

| 类 | 说明 |
|---|---|
| `ZeroCopyDeserializer` | 零拷贝反序列化器（避免不必要的字符串拷贝） |
| `BytesUtil` | 字节工具 |

### 6. 树模型（tree 包）

| 类 | 说明 |
|---|---|
| `JsonNode` | JSON 节点基类（对标 Jackson JsonNode） |
| `ObjectNode` / `ArrayNode` / `TextNode` / `NumberNode` / `BooleanNode` / `NullNode` / `MissingNode` | 节点类型 |
| `TreeConverter` | 树 ↔ 对象转换 |

### 7. 类型系统（type / naming / number 包）

| 类 | 说明 |
|---|---|
| `JsonType` / `TypeFactory` | 类型系统（类型代码替代 instanceof 链，提高分支预测准确率） |
| `PropertyNamingStrategy` | 命名策略（`LOWER_CAMEL_CASE` / `UPPER_CAMEL_CASE` / `SNAKE_CASE` / `KEBAB_CASE`） |
| `NumberUtils` | 数字解析工具 |

### 8. 注解（annotation 包）

> **命名约定**：所有注解统一使用 `@Json*` 前缀，命名与 Jackson 兼容，从 Jackson 迁移时注解名无需修改。

#### 字段级注解

| 注解 | 说明 |
|---|---|
| `@JsonProperty` | 字段重命名与访问控制（`value` 名称 / `required` 必需 / `defaultValue` 默认值 / `access` 访问模式 `AUTO`·`READ_ONLY`·`WRITE_ONLY`·`READ_WRITE`，如 `@JsonProperty(value="user_id", required=true)`） |
| `@JsonIgnore` | 字段忽略（字段级，对标 Jackson `@JsonIgnore`） |
| `@JsonFormat` | 日期/数字格式化（`pattern` / `shape` / `locale` / `timezone` / `lenient` 宽松解析，如 `@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")`） |
| `@JsonAlias` | 反序列化别名（同一字段接受多个 JSON key） |
| `@JsonInclude` | 属性包含策略（ALWAYS / NON_NULL / NON_EMPTY / NON_DEFAULT） |
| `@JsonRawValue` | 原始 JSON 值嵌入（不转义直接输出） |
| `@JsonUnwrapped` | 嵌套属性展开（支持 `prefix` / `suffix`） |

#### 方法级注解

| 注解 | 说明 |
|---|---|
| `@JsonGetter` / `@JsonSetter` | 方法级 getter/setter 标记 |
| `@JsonValue` | 枚举值序列化方式（方法级，序列化时输出该方法的返回值） |
| `@JsonAnyGetter` / `@JsonAnySetter` | 动态属性 Getter / Setter（Map 字段展开为 JSON 属性） |

#### 类级注解

| 注解 | 说明 |
|---|---|
| `@JsonClass` | 类级配置（字段排序 `ordering` / 忽略字段 `ignores` / 包含字段 `includes` / 命名策略 `naming` / 输出 null `writeNulls` / 输出类名 `writeClassName` / 日期格式 `dateFormat` / 枚举序号 `serializeEnumUsingOrdinal` / 多态 `typeKey`+`seeAlso`+`seeAlsoNames`+`autoType`；同时作为 AutoType 白名单扫描标记） |
| `@JsonPropertyOrder` | 类级字段排序（指定顺序数组 `{"id","name"}` 或 `alphabetic=true` 字母序） |
| `@JsonView` | 视图过滤（配合 `YdszJson.toJson(obj, ViewClass.class)` 使用） |
| `@JsonNaming` | 类级命名策略 |
| `@JsonIgnoreProperties` | 类级字段忽略 |
| `@JsonRootName` | 根名称包裹（配合 `ydsz.json.wrap-root-value=true`） |
| `@JsonVisibility` | 可见性控制（`fields` / `getters` / `setters`，枚举 `ANY`·`PUBLIC_ONLY`·`PROTECTED_AND_PUBLIC`·`NONE`） |
| `@JsonSerialize` / `@JsonDeserialize` | 自定义序列化器/反序列化器（`using = XxxSerializer.class`，需实现 `JsonSerializer` / `JsonDeserializer` 接口） |

#### 构造器 / Builder 注解

| 注解 | 说明 |
|---|---|
| `@JsonCreator` | 构造器/工厂方法标记（`defaultCreator` 默认构造 / `parameterNames` 参数名映射 / `enable` 启用 / `mode` 模式 `DEFAULT`·`PROPERTIES`·`DELEGATING`） |
| `@JsonBuilder` | Builder 模式反序列化（`enable` 启用 / `builderClass` Builder 类 / `buildMethod` 构建方法名 / `withPrefix` setter 前缀 / `autoDetect` 自动检测内部 Builder） |

#### 多态类型注解

| 注解 | 说明 |
|---|---|
| `@JsonTypeInfo` | 多态类型标识（`property` 类型键名 / `visible` 是否保留 / `use` 标识方式 `Id.NAME`·`CLASS`·`MINIMAL_CLASS`·`NONE` / `include` 包含结构 `As.PROPERTY`·`WRAPPER_ARRAY`·`WRAPPER_OBJECT`） |
| `@JsonSubTypes` / `@JsonSubType` | 子类型注册（`value` 子类型类 / `name` 类型名） |
| `@JsonTypeName` | 子类型逻辑名称（标注在子类上，优先于 `@JsonSubType.name()`） |

#### 其他注解

| 注解 | 说明 |
|---|---|
| `@Experimental` | 实验性功能标记（标注 `JsonSchema` 等 RFC 扩展功能，API 尚未稳定，不保证向后兼容） |


### 9. 高级功能（jsonpath / pointer / merge / schema / autotype 包）

> **`@Experimental` 标注**：`JsonSchema` 标注了 `@Experimental`，属于 RFC 扩展功能，API 尚未稳定，不保证向后兼容。建议在非关键路径使用或做好隔离层。

| 类 | 说明 |
|---|---|
| `JsonPointer` | JSON Pointer（RFC 6901） |
| `JsonPath` | JSONPath 查询（递归下降 `$..` / 数组索引 `[0]` / 数组过滤 `[?(@.price > 100)]` / 切片 `[0:5]` / 通配符 `[*]` / 条件表达式 `&&` / `||`；编译结果 LRU 缓存，max 512） |
| `JsonMergePatch` | JSON Merge Patch（RFC 7396，支持 `merge` 合并 + `diff` 计算差异补丁） |
| `JsonSchema` `@Experimental` / `JsonSchemaValidator` / `ValidationResult` | JSON Schema 校验（JSON Schema Draft 07 核心关键字：type/required/enum/default/minLength/maxLength/pattern/minimum/maximum/exclusiveMinimum/exclusiveMaximum/multipleOf/items/minItems/maxItems/properties/additionalProperties + 组合关键字 allOf/anyOf/oneOf/not/const/if-then-else；静态工厂 `string()`/`number()`/`integer()`/`booleanType()`/`array()`/`object()`/`nullType()`） |
| `AutoTypeChecker` / `AutoTypeWhitelistScanner` | AutoType 安全检查（防反序列化漏洞，`TYPE_CHECK_CACHE` LRU 有界缓存 max 4096，支持包前缀白名单回退匹配） |

### 10. Module 系统（module 包）

| 类 | 说明 |
|---|---|
| `JsonModule` | 模块接口（参考 Jackson Module，可插拔的序列化/反序列化扩展机制） |
| `JsonModuleRegistry` / `ModuleSerializerRegistry` / `ModuleDeserializerRegistry` | 模块注册表 |
| `JsonModuleRegistrar` | Spring 环境模块注册器 |

### 11. Spring 集成（spring 包）

| 类 | 说明 |
|---|---|
| `JsonHttpMessageConverter` | Spring MVC HttpMessageConverter（继承 `AbstractGenericHttpMessageConverter`，支持泛型类型 `@RequestBody List<User>`、`@JsonView`、`streamingEnabled` / `maxRequestBodySize` 配置） |
| `JsonReactiveUtils` | WebFlux 响应式编码工具 |
| `JsonWarmupRunner` | ASM 预热 Runner（应用启动后异步预热高频序列化 Bean 的 ASM 字节码） |
| `JsonProperties` | 配置属性类（`ydsz.json.*`） |

### 12. 可观测性（metric 包）

| 类 | 说明 |
|---|---|
| `MetricsHelper` | 指标监控包装（统一序列化/反序列化指标记录，null 短路优化） |
| `JsonMetrics` | Micrometer 指标实现（自动绑定到 `YdszJson.setMetricsCallback`） |
| `JsonMetricsCallback` | 指标回调 SPI 接口 |
| `JsonCacheMetrics` | 缓存指标（绑定到 MeterRegistry） |

### 13. 异常体系（exception 包）

| 类 | 说明 |
|---|---|
| `JsonException` | 顶层异常 |
| `JsonSerializationException` | 序列化异常（继承自 `JsonException`） |
| `JsonDeserializationException` | 反序列化异常（继承自 `JsonException`，含行列号 / 上下文片段） |

### 14. 健康检查（health 包）

| 类 | 说明 |
|---|---|
| `JsonHealthIndicator` | YdszJson 引擎健康检查，暴露 `safeMode`、`maxDepth`、`maxGenericDepth`、`graalVmNativeImage`、`asmAvailable`、`asmDowngradeCount`、`namingStrategy`、`maxJsonSize` 等完整运行时状态（可通过 Actuator `/actuator/health/ydszJson` 端点查询） |

### 15. 自动配置（spring.boot 包）

| 配置类 | 激活条件 | 注册的 Bean |
|---|---|---|
| `JsonAutoConfiguration` | `ydsz.json.enabled=true`（默认启用），`JsonConfig` 在类路径 | `JsonConfigBean`（全局配置初始化 + 模块注册）、`JsonHttpMessageConverter`、`JsonMetrics`（Micrometer 可用时）、`JsonHealthIndicator`、`JsonWarmupRunner` |

| 属性类 | 前缀 | 说明 |
|---|---|---|
| `JsonProperties` | `ydsz.json` | 全局 JSON 配置（日期格式 / 命名策略 / 空值处理 / BigDecimal 模式 / AutoType 安全模式 / ASM 预热类列表等） |

## 接入方式

### 1. POM 引入依赖

```xml
<dependency>
    <groupId>com.njydsz</groupId>
    <artifactId>ydsz-common-json</artifactId>
</dependency>
```

### 2. 配置启用

```yaml
ydsz:
  json:
    enabled: true                              # 模块总开关（默认启用）
    date-format: yyyy-MM-dd HH:mm:ss           # 全局日期格式
    naming-strategy: LOWER_CAMEL_CASE          # 命名策略
    write-nulls: false                         # 是否输出 null 值
    pretty-print: false                        # 是否美化输出
    use-big-decimal: false                     # BigDecimal 精度模式（金融场景）
    safe-mode: true                            # AutoType 安全检查模式
    whitelist-packages:                        # AutoType 白名单扫描包
      - com.njydsz
    warmup-classes:                            # 启动时预热的类（全限定类名）
      - com.njydsz.workflow.domain.entity.FlowDefinition
    max-json-size: 10485760                    # JSON 最大长度（字节，默认 10MB）
    max-depth: 256                             # JSON 最大嵌套深度
    monitoring-enabled: true                   # 是否启用监控指标
    streaming-enabled: false                   # 是否启用流式输出
```

### 3. 基础使用

```java
import com.njydsz.common.json.YdszJson;

// 序列化
String json = YdszJson.toJson(obj);

// 反序列化
User user = YdszJson.fromJson(json, User.class);

// Spring MVC Controller 自动使用 JsonHttpMessageConverter
@RestController
public class UserController {
    @GetMapping("/{id}")
    public User getById(@PathVariable Long id) {
        return userService.getById(id);
    }
}
```

## 配置项

| 配置 | 默认值 | 说明 |
|---|---|---|
| `ydsz.json.enabled` | true | 模块总开关 |
| `ydsz.json.date-format` | `yyyy-MM-dd HH:mm:ss` | 全局日期格式 |
| `ydsz.json.naming-strategy` | `LOWER_CAMEL_CASE` | 命名策略：`LOWER_CAMEL_CASE` / `UPPER_CAMEL_CASE` / `SNAKE_CASE` / `KEBAB_CASE` |
| `ydsz.json.write-nulls` | false | 是否输出 null 值 |
| `ydsz.json.pretty-print` | false | 是否美化输出 |
| `ydsz.json.use-big-decimal` | false | 启用 BigDecimal 解析路径（金融场景精度保护） |
| `ydsz.json.wrap-root-value` | false | 是否包裹根名称（配合 `@JsonRootName`） |
| `ydsz.json.fail-on-error` | false | 反序列化失败时是否抛出异常（false 返回 null） |
| `ydsz.json.serialize-enum-using-ordinal` | false | 枚举是否使用序号序列化 |
| `ydsz.json.circular-reference-strategy` | `REF` | 循环引用处理策略：`REF` / `IGNORE` / `ERROR` |
| `ydsz.json.max-json-size` | 10485760 | JSON 最大长度（字节，默认 10MB） |
| `ydsz.json.max-depth` | 256 | JSON 最大嵌套深度（防栈溢出） |
| `ydsz.json.max-generic-depth` | 64 | 泛型递归深度上限（防嵌套泛型 `List<List<...>>` 导致 StackOverflow，与 FastJSON2 对齐） |
| `ydsz.json.safe-mode` | true | AutoType 安全检查模式（防反序列化漏洞） |
| `ydsz.json.whitelist-packages` | `[com.njydsz]` | AutoType 白名单扫描包列表（支持通配符） |
| `ydsz.json.monitoring-enabled` | true | 是否启用序列化/反序列化监控指标 |
| `ydsz.json.streaming-enabled` | false | 是否启用流式输出（HTTP 响应使用 chunked transfer encoding） |
| `ydsz.json.max-request-body-size` | 10485760 | HTTP 请求体最大大小（字节，默认 10MB） |
| `ydsz.json.warmup-classes` | `[]` | 启动时预热的类列表（全限定类名，预热 ASM 字节码避免首次请求延迟尖峰） |
| `ydsz.json.disable-jackson-auto-configuration` | `false` | 是否禁用 Spring Boot Jackson 自动配置。默认 `false`（保守，保持双引擎生态）。设置为 true 可统一为 YdszJson 单引擎（需评估 Actuator/Spring Data Redis 等内部依赖） |

## 使用示例

### 1. 基本序列化/反序列化

```java
import com.njydsz.common.json.YdszJson;

// 序列化
String json = YdszJson.toJson(user);

// 反序列化
User user = YdszJson.fromJson(json, User.class);

// 树操作（Map 形式）
Map<String, Object> root = YdszJson.parseMap(json);
String name = (String) root.get("name");

// 流式序列化（写入 Writer，避免中间 String）
YdszJson.toJson(obj, new StringWriter());

// 从 InputStream 反序列化
User user2 = YdszJson.fromJson(inputStream, User.class);

// fromJson 别名（与 toJson 对称）
User user3 = YdszJson.fromJson(json, User.class);
```

### 2. JsonMapper Builder API

```java
import com.njydsz.common.json.JsonMapper;
import com.njydsz.common.json.naming.PropertyNamingStrategy;

JsonMapper mapper = JsonMapper.builder()
        .namingStrategy(PropertyNamingStrategy.SNAKE_CASE)
        .dateFormat("yyyy-MM-dd HH:mm:ss")
        .writeNulls(true)
        .useBigDecimal(true)
        .build();

String json = mapper.toJson(obj);
User user = mapper.toObject(json, User.class);
```

### 3. JSON Schema 校验（含 allOf/anyOf/oneOf）

```java
import com.njydsz.common.json.schema.JsonSchema;
import com.njydsz.common.json.schema.JsonSchemaValidator;
import com.njydsz.common.json.schema.ValidationResult;

JsonSchema schema = JsonSchema.object()
        .addProperty("name", JsonSchema.string())
        .allOf(
                JsonSchema.object().addProperty("name", JsonSchema.string().required()),
                JsonSchema.object().addProperty("age", JsonSchema.integer().minimum(0))
        );

ValidationResult result = JsonSchemaValidator.validate(schema, data);
if (!result.isValid()) {
    System.err.println("验证失败：" + result.getErrors());
}
```

### 4. JSONPath 查询与 JSON Merge Patch

```java
import com.njydsz.common.json.jsonpath.JsonPath;
import com.njydsz.common.json.merge.JsonMergePatch;

// JSONPath 查询
List<String> emails = JsonPath.get(json, "$.users[*].email");

// JSON Merge Patch (RFC 7396)
String merged = JsonMergePatch.merge(
    "{\"name\":\"Bob\",\"age\":30}",
    "{\"age\":31,\"email\":\"bob@example.com\"}");
// merged: {"name":"Bob","age":31,"email":"bob@example.com"}
```

### 5. 自定义序列化器（Module 模式）

```java
import com.njydsz.common.json.module.JsonModule;
import org.springframework.stereotype.Component;

@Component
public class UserModule implements JsonModule, JsonModule.SpringFactory {

    @Override
    public String getModuleName() {
        return "userModule";
    }

    @Override
    public void setSerializers(ModuleSerializerRegistry registry) {
        registry.register(User.class, new UserSerializer());
    }

    @Override
    public void setDeserializers(ModuleDeserializerRegistry registry) {
        registry.register(User.class, new UserDeserializer());
    }
}
```

## SPI 扩展点

| SPI 接口 | 用途 | 实现方 |
|---|---|---|
| `JsonModule` | 可插拔的序列化/反序列化扩展机制（参考 Jackson Module），为指定类型注册自定义 Serializer/Deserializer | 业务模块实现 `JsonModule.SpringFactory` 标记接口后注册为 Spring Bean 即可自动发现 |
| `JsonSerializer<T>` | 自定义序列化器（通过 `@JsonSerialize(using = ...)` 注解指定） | 业务模块实现 |
| `JsonDeserializer<T>` | 自定义反序列化器（通过 `@JsonDeserialize(using = ...)` 注解指定） | 业务模块实现 |
| `JsonMetricsCallback` | JSON 处理指标回调（序列化/反序列化成功/失败 + 耗时） | 框架内置 `JsonMetrics`（Micrometer 实现），业务可覆盖 |

## 健康检查

| 端点 | 说明 | 触发条件 |
|---|---|---|
| `/actuator/health` | JSON 模块健康检查作为整体 health 端点的一部分 | `spring-boot-health` 在类路径，`ydsz.json.enabled=true` |

**`JsonHealthIndicator` 暴露信息**（仅 5 项关键指标，详细指标通过 Micrometer 暴露）：

- `safeMode` — AutoType 安全模式是否开启（核心安全指标）
- `namingStrategy` — 当前全局字段命名策略
- `maxJsonSize` — 单次 JSON 处理大小限制（字节）
- `asmAvailable` — ASM 字节码优化是否可用
- `registeredSerializers` — 已注册的自定义序列化器数量

> **注意**：详细指标（缓存命中率、序列化/反序列化计数等）通过 Micrometer `ydsz.json.*` 暴露给 Prometheus/Grafana，不通过 Health Endpoint。

**健康状态规则**：

- AutoType SafeMode 关闭 → DOWN（`warning=AutoType SafeMode is disabled, RCE risk exists`）
- SafeMode 开启 → UP

## 注意事项

1. **零外部 JSON 库依赖**：本模块纯 Java 实现，不引入 Jackson / FastJSON / Gson。`jackson-annotations` 仅作为 `optional` 编译期依赖（消除 Spring Boot 4.x 依赖中的 Jackson 注解警告），运行期不强制。
2. **ASM 字节码生成与 GraalVM 兼容**：热路径生成字节码避免反射开销；`GraalVmDetector` 检测 Native Image 环境后自动降级为反射 + MethodHandle 路径，`AutoTypeWhitelistScanner` 启动时扫描 `@JsonClass` 注解类注册白名单。
3. **AutoType 安全模式**：默认开启 `safe-mode=true`，防止反序列化漏洞。`AutoTypeChecker` 支持包前缀白名单回退匹配，`TYPE_CHECK_CACHE` 为 LRU 有界缓存（max 4096）避免内存泄漏。
4. **BigDecimal 精度模式**：`use-big-decimal=true` 启用 BigDecimal 解析路径（金融场景精度保护），默认 `false` 走 Double 路径（性能更高）。
5. **ASM 预热**：`warmup-classes` 配置项指定启动时预热的类列表，`JsonWarmupRunner` 在应用启动后异步预生成 ASM 字节码，避免首次请求延迟尖峰。也可通过 `YdszJson.warmup(Class...)` 手动触发。
6. **Spring MVC 泛型类型支持**：`JsonHttpMessageConverter` 继承 `AbstractGenericHttpMessageConverter`，正确支持 `@RequestBody List<User>` 等泛型类型反序列化。
7. **Jackson 迁移**：`@JsonProperty` / `@JsonIgnore` / `@JsonFormat` / `@JsonInclude` 等 Jackson 兼容注解无需修改即可使用。迁移步骤：`ObjectMapper` → `JsonMapper`，`readValue/readTree/writeValueAsString` → `toObject/readTree/toJson`，`ObjectReader/ObjectWriter` → `JsonReader/JsonWriter`。
   - **迁移注意事项**：
     - `JsonMapper` 实例可安全共享（线程安全），配置通过 `ThreadLocalSnapshot` 在每次序列化时 apply/restore，与 Jackson `ObjectMapper`（配置不可变 + 显式传参）模型在 ThreadLocal 实现下的等价做法。
     - **命名策略在字段元数据加载时缓存**：`@JsonNaming` / `JsonConfig.namingStrategy` 对一个类的首次序列化生效并缓存 `jsonName`，后续切换命名策略对该已缓存类无效。如需不同命名策略，应在首次序列化前设置，或对不同命名使用不同 Bean 类型。
     - **`writeNulls` 配置生效范围**：`JsonMapper.builder().writeNulls(true)` 对带 `@JsonClass` 注解的 Bean 生效（走 ValueWriter 注解路径）；无注解的 Bean 走 `writeBeanNoAnnotationOptimized` 快速路径，null 字段始终省略。如需全局 writeNulls 对所有 Bean 生效，请在 Bean 上加 `@JsonClass`。
     - **响应式场景**：`SerializationContext` 基于 ThreadLocal，跨线程链路（WebFlux / 虚拟线程）中线程切换会丢失配置。响应式场景请使用 `JsonReactiveUtils` 或在调用线程内完成序列化。
8. **ThreadLocal 池优化**：`SerializationContext` 合并 5+ ThreadLocal 为单一实例，降低内存碎片；`estimateThreadLocalMemory` 基于缓冲池容量动态计算，避免硬编码。
9. **循环引用处理**：默认 `REF` 策略（自动检测并处理循环引用），可配置为 `IGNORE`（忽略）或 `ERROR`（抛出异常）。
10. **流式输出**：`streaming-enabled=true` 启用 HTTP 响应 chunked transfer encoding，适用于大 JSON 响应场景。
11. **配置不可变推荐**：自 v1.0.0 起 `JsonConfig.install(newConfig)` 替代旧 `setInstance` 模式。业务侧仍可通过 `JsonMapper.builder()` 创建独立配置副本（不影响全局单例）。`install()` 内部同步做 `instance = newConfig; instance.apply()`，确保可见性与一致性。
12. **运行时诊断 API**：`YdszJson.isNativeImage()` / `isAsmAvailable()` / `getStats()` 可在启动早期或监控中快速诊断 ASM 降级、缓存命中和当前安全配置。`getStats()` 返回 `JsonStats` 记录类含 `nativeImage`、`asmAvailable`、`asmDowngradeCount`、`serializerCount`、`deserializerCount`、`asmCacheHitRate`、`maxDepth`、`maxGenericDepth`、`maxJsonSize`、`safeMode` 10 项指标。
13. **序列化异常路径追踪**：`JsonSerializationException.getMessage()` 自动在消息末尾附加 `[fieldPath: user.address.street]`，可直接定位嵌套序列化失败根因。`getFieldPath()` 返回原始路径字符串，供日志框架归类。
14. **ThreadLocal 模型与响应式兼容**：`SerializationContext` 基于 `ThreadLocalSnapshot` 实现，与响应式/虚拟线程框架天然不兼容。跨线程调用前需在原线程完成序列化，或使用 `JsonReactiveUtils`（Project Reactor 兼容）。中期演进方向为显式参数传递模型（与 `Object` Pooling + 配置对象层次化作用），参考 [Architectural Decision Record: ThreadLocal Migration ADR-007]。
15. **高级功能治理观测制度**：标记为 `@Experimental` / `@Beta` API 的功能处于观测期（默认 2 个次要版本）。期内不承诺向后兼容——API 变更、移除或行为调整均不触发 major 版本递增。调用方须在升级前阅读 Release Notes，并在观测期结束前完成迁移或提出反馈。当前处于观测期的功能列表见 [Appendix: Experimental Features Catalog]
