# ydsz-common-json

> YDSZ 高性能 JSON 引擎（L2 工具层）— ASM 字节码加速、有界软引用字段缓存、零拷贝反序列化、JIT 自动向量化、Schema 校验、JsonPath 查询、JsonNode 树模型、Jackson 兼容注解

纯 Java 实现的 JSON 引擎，零外部 JSON 库依赖（不引入 Jackson / FastJSON / Gson）。通过 ASM 字节码生成、零拷贝反序列化、ThreadLocal 池优化等技术实现超高性能；通过 Jackson 兼容注解实现平滑迁移。

## 模块定位

| 属性 | 值 |
|---|---|
| **层级** | L2 工具模块层 |
| **类型** | 公共依赖库（不独立部署） |
| **作用** | 提供高性能 JSON 序列化/反序列化、树模型、JSONPath、Schema 校验、JSON Patch/Merge Patch、Jackson 兼容注解、Spring MVC 集成等能力 |
| **依赖** | Lombok；可选依赖 ASM、SLF4J、Spring Boot AutoConfigure、Spring Web、Reactive Streams、Spring Boot Actuator/Health、Micrometer、Jackson Annotations（编译期可见）、Jakarta Validation |
| **版本** | 1.0.0 |

## 核心能力

### 1. 核心 API（根包）

| 类 | 说明 |
|---|---|
| `YdszJson` | JSON 统一入口（静态工具类），提供 `toJson` / `toObject` / `parseMap` / `parseArray` / `fromJson` / `readTree` / `valueToTree` / `warmup` 等方法 |
| `JsonMapper` | 实例化 Mapper（对标 Jackson ObjectMapper），支持 `builder()` 链式 Builder、独立配置副本、`readerFor(Class)` / `writerFor(Class)` 绑定型读写器、`convertValue` / `treeToValue` 等 |
| `JsonReader<T>` | 绑定型 JSON 读取器（对标 Jackson ObjectReader），绑定目标类型后可重复使用 |
| `JsonWriter<T>` | 绑定型 JSON 写入器（对标 Jackson ObjectWriter），绑定 Mapper 配置后可重复使用 |
| `JsonConfig` | 全局配置（日期格式 / 空值处理 / 命名策略 / BigDecimal 精度模式 / 根名称包裹 / 最大 JSON 大小 / 最大深度 / `builder()` / `copyOf()`） |

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
| `@Experimental` | 实验性功能标记（标注 `JsonSchema`、`JsonPatch` 等 RFC 扩展功能，API 尚未稳定，不保证向后兼容） |


### 9. 高级功能（jsonpath / pointer / patch / merge / schema / autotype 包）

> **`@Experimental` 标注**：`JsonSchema`、`JsonPatch` 标注了 `@Experimental`，属于 RFC 扩展功能，API 尚未稳定，不保证向后兼容。建议在非关键路径使用或做好隔离层。

| 类 | 说明 |
|---|---|
| `JsonPointer` | JSON Pointer（RFC 6901） |
| `JsonPath` | JSONPath 查询（递归下降 `$..` / 数组索引 `[0]` / 数组过滤 `[?(@.price > 100)]` / 切片 `[0:5]` / 通配符 `[*]` / 条件表达式 `&&` / `||`；编译结果 LRU 缓存，max 512） |
| `JsonMergePatch` | JSON Merge Patch（RFC 7396，支持 `merge` 合并 + `diff` 计算差异补丁） |
| `JsonPatch` `@Experimental` | JSON Patch（RFC 6902，支持 add/remove/replace/move/copy/test + Builder 链式构建 `applyTo()`） |
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
| `JsonHttpMessageConverter` | Spring MVC HttpMessageConverter（继承 `AbstractGenericHttpMessageConverter`，支持泛型类型 `@RequestBody List<User>`、`@YdszJsonView`、`streamingEnabled` / `maxRequestBodySize` 配置） |
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
| `JsonHealthIndicator` | YdszJson 引擎健康检查，暴露 AutoType SafeMode、配置项、ASM 缓存统计、ThreadLocal 内存估算等 |

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
User user = YdszJson.toObject(json, User.class);

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
| `ydsz.json.max-depth` | 256 | JSON 最大嵌套深度 |
| `ydsz.json.safe-mode` | true | AutoType 安全检查模式（防反序列化漏洞） |
| `ydsz.json.whitelist-packages` | `[com.njydsz]` | AutoType 白名单扫描包列表（支持通配符） |
| `ydsz.json.monitoring-enabled` | true | 是否启用序列化/反序列化监控指标 |
| `ydsz.json.streaming-enabled` | false | 是否启用流式输出（HTTP 响应使用 chunked transfer encoding） |
| `ydsz.json.max-request-body-size` | 10485760 | HTTP 请求体最大大小（字节，默认 10MB） |
| `ydsz.json.warmup-classes` | `[]` | 启动时预热的类列表（全限定类名） |

## 使用示例

### 1. 基本序列化/反序列化

```java
import com.njydsz.common.json.YdszJson;

// 序列化
String json = YdszJson.toJson(user);

// 反序列化
User user = YdszJson.toObject(json, User.class);

// 树操作（Map 形式）
Map<String, Object> root = YdszJson.parseMap(json);
String name = (String) root.get("name");

// 流式序列化（写入 Writer，避免中间 String）
YdszJson.toJson(obj, new StringWriter());

// 从 InputStream 反序列化
User user2 = YdszJson.toObject(inputStream, User.class);

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

### 4. JSONPath 查询与 JSON Patch

```java
import com.njydsz.common.json.jsonpath.JsonPath;
import com.njdsz.common.json.patch.JsonPatch;

// JSONPath 查询
List<String> emails = JsonPath.get(json, "$.users[*].email");

// JSON Patch (RFC 6902)
String result = JsonPatch.builder()
        .replace("/name", "Alice")
        .add("/email", "alice@example.com")
        .remove("/temp")
        .applyTo("{\"name\":\"Bob\",\"temp\":true}");
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

**`JsonHealthIndicator` 暴露信息**：

- `safeMode` — AutoType 安全模式是否开启
- `maxJsonSize` / `maxDepth` — JSON 大小与深度限制
- `namingStrategy` / `circularReferenceStrategy` / `dateFormat` — 全局配置
- `useBigDecimal` / `wrapRootValue` / `failOnError` / `serializeEnumUsingOrdinal` — 配置开关
- `asmLevel` / `asmGeneratedCount` — ASM 字节码生成级别与数量
- `serializerCacheSize` / `codecCacheSize` / `beanSerializerCacheSize` — 缓存统计
- `threadLocalMemoryEstimate` — ThreadLocal 内存估算（字节）

**健康状态规则**：

- AutoType SafeMode 关闭 → DOWN（`warning=AutoType SafeMode is disabled, RCE risk exists`）
- SafeMode 开启 → UP

## 注意事项

1. **零外部 JSON 库依赖**：本模块纯 Java 实现，不引入 Jackson / FastJSON / Gson。`jackson-annotations` 仅作为 `optional` 编译期依赖（消除 Spring Boot 4.x 依赖中的 Jackson 注解警告），运行期不强制。
2. **ASM 字节码生成与 GraalVM 兼容**：热路径生成字节码避免反射开销；`GraalVmDetector` 检测 Native Image 环境后自动降级为反射 + MethodHandle 路径，`AutoTypeWhitelistScanner` 启动时扫描 `@YdszJsonClass` 注解类注册白名单。
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

## 变更记录

- **v1.0.0**（2026-08-02）：对标 common-jdbc 标准格式重构 README，补全全部 9 个章节
- **v1.0.0**（2026-08-02）：基于源码核对修正文档与代码不一致：
  - 删除不存在的 `@YdszJsonField` 注解（代码中未实现，仅在 `@JsonProperty`/`@JsonIgnore`/`@JsonFormat` 的 Javadoc `{@link}` 中残留无效引用）
  - 删除不存在的 `@YdszJsonFormat` 注解（实际格式化注解为 `@JsonFormat`，Jackson 兼容）
  - `SchemaValidator` → `JsonSchemaValidator`（类名修正，影响 Section 9 与使用示例 3）
  - 使用示例 4 import 包名 `com.njdsz` → `com.njydsz`（拼写错误）
  - 补全 `@YdszJsonClass` 类级配置能力说明（ordering/ignores/includes/naming/seeAlso/features 等 14 项属性）
  - 标注 `JsonSchema`、`JsonPatch` 的 `@Experimental` 状态
  - 补全 `JsonSchema` 静态工厂方法列表与 `JsonPath` 支持的 7 种路径表达式
- **v1.0.0**（2026-08-03）：架构优化与 Bug 修复（P0-P2）：
  - **[P0-A1]** 修复 `JsonMapper.restoreConfig` 无限递归导致 `StackOverflowError`（`new JsonMapper().toJson(obj)` 必崩）
  - **[P0-A2]** 移除 `JsonMapper.configApplied` 跨线程误共享优化，改为每次序列化 apply/restore，确保共享 Mapper 多线程配置隔离
  - **[P0-A3]** `ThreadLocalSnapshot` 补全 `namingStrategy` 回滚，修复 `YdszJson.toJson(obj, config)` 单次配置泄漏
  - **[P1-D1]** 删除死代码 `provider/JsonWriter`（含 10000 元素静态数组残留）
  - **[P1-D2]** 删除已 `@Deprecated(forRemoval=true)` 的 `api.JsonSerializer`/`api.JsonDeserializer`，`SerializationProvider.invokeCustomSerializer` 简化为单一调用路径（移除 `Method` 反射双判）
  - **[P1-E1]** 补 16 项 P0/D2 回归测试（`JsonMapperP0RegressionTest` / `CustomSerializerD2Test`）；修复 `pom.xml` 缺失 `<argLine>` 导致测试 fork 启动崩溃
  - **[功能]** `ValueWriter` 注解路径补 `SerializationProvider.isWriteNulls()` 判断，使 `JsonMapper.builder().writeNulls(true)` 对带 `@JsonClass` 的 Bean 生效
  - **[P2-A5]** `JsonConfig` 不可变契约 Javadoc 如实表述；修正 `JsonMapper` 示例中不存在的 `setWriteNulls` 调用
  - **[P2-E2/A4/ASM]** README "LRU" 表述修正为"近似 LRU"；补 Jackson 迁移注意事项（ThreadLocal 配置模型、命名策略缓存、writeNulls 生效范围、响应式限制）
