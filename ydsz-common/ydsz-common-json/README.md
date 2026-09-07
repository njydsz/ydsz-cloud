# ydsz-common-json

> 零依赖 JSON 引擎与 Jackson 注解兼容层（L1 工具模块层）

提供自研高性能 JSON 引擎（`YdszJson`）、Jackson 注解双向兼容、JsonPatch / JsonMergePatch 操作、HttpMessageConverter 自动替换 Jackson、多态反序列化、序列化缓存等能力，是所有业务模块的 JSON 序列化基座。

## 模块定位

| 属性 | 值 |
|---|---|
| **层级** | L1 工具模块层 |
| **类型** | 公共依赖库（不独立部署） |
| **作用** | 提供零依赖 JSON 引擎，完全兼容 Jackson 注解体系，替代 Spring Boot 默认 Jackson |
| **依赖** | lombok（provided）、slf4j-api（optional）；可选 spring-boot-autoconfigure、spring-web、jackson-annotations（compileOnly）、jakarta.annotation-api（provided）、jakarta.validation-api（optional） |
| **版本** | 3.0.0 |

## 核心能力

### 1. 自研 JSON 引擎

| 类 | 说明 |
|---|---|
| `YdszJson` | 自研 JSON 核心引擎（零 Jackson 运行时依赖），提供 readValue / writeValueAsString 等标准能力 |
| `JsonMapper` | JSON 门面工具（静态方法入口，封装 YdszJson 实例） |
| `JsonParser` / `JsonParserUtil` | JSON 底层解析器 |
| `JsonReader` / `BeanReader` | JSON 读取器（流式 API） |
| `JSONWriter` / `BeanSerializer` | JSON 写入器（流式 API） |

**性能对标 Jackson**：序列化/反序列化性能与 Jackson 持平（基于 MethodHandle + 字节码缓存），内存占用更低（无 ObjectMapper 全局缓存）。

### 2. Jackson 注解双向兼容

模块提供完整的 Jackson 注解镜像，使业务代码无需改动即可切换引擎：

| 注解 | 兼容行为 |
|---|---|
| `@JsonAlias` | 反序列化别名（多名称映射同一字段） |
| `@JsonClass` | 类型信息注入 |
| `@JsonCreator` / `@JsonValue` | 自定义构造 / 序列化值 |
| `@JsonDeserialize` / `@JsonSerialize` | 自定义序列化器绑定 |
| `@JsonFormat` | 日期 / 数字格式化 |
| `@JsonGetter` / `@JsonSetter` | 逻辑属性名映射 |
| `@JsonIgnore` / `@JsonIgnoreProperties` | 属性忽略 |
| `@JsonInclude` | 序列化包含策略（NON_NULL / NON_EMPTY 等） |
| `@JsonNaming` | 全局命名策略（snake_case / camelCase） |
| `@JsonProperty` / `@JsonPropertyOrder` | 属性名映射 / 排序 |
| `@JsonSubType` / `@JsonSubTypes` / `@TypeInfo` / `@TypeName` | 多态类型处理 |
| `@JsonView` | 视图过滤 |
| `PropertyNamingStrategy` | 自定义命名策略 SPI |

> 20 个注解全部基于 `jackson-annotations` 编译时引用（`compileOnly`），运行时无需 Jackson on classpath。

### 3. JSON 树模型与 Patch 操作

| 类 | 说明 |
|---|---|
| `JsonNode` | JSON 树节点基类 |
| `ObjectNode` / `ArrayNode` / `TextNode` / `NumberNode` / `BooleanNode` / `NullNode` / `MissingNode` | JSON 节点类型 |
| `JsonPatch` | JSON Patch（RFC 6902）实现（add / remove / replace / move / copy / test） |
| `JsonMergePatch` | JSON Merge Patch（RFC 7386）实现 |
| `TreeConverter` | 树模型与 POJO 互转 |

### 4. 序列化增强

| 类 | 说明 |
|---|---|
| `JsonSerializer` / `JsonDeserializer` | 自定义序列化器 SPI |
| `SerializerRegistry` / `BeanSerializerCache` | 序列化器注册表 + 缓存（类元数据缓存避免重复反射） |
| `PolymorphicTypeResolver` | 多态类型解析器 |
| `FieldMetadataLoader` | 字段元数据加载器（缓存字段/方法/注解信息） |
| `TypeConverter` / `ValueFormatter` / `ValueWriter` | 类型转换 / 值格式化 / 值写入 SPI |
| `CreatorResolver` / `BuilderResolver` | 构造器 / Builder 模式解析 |

### 5. Spring 集成

| 类 | 说明 |
|---|---|
| `JsonHttpMessageConverter` | 替换 Spring Boot 默认 `MappingJackson2HttpMessageConverter`，使用 YdszJson 引擎 |
| `JsonProperties` | JSON 配置属性（`ydsz.json.*`） |
| `JsonAutoConfiguration` | 自动配置入口 |
| `JsonModuleRegistrar` | JsonModule 自动注册 |
| `JacksonExclusionEnvironmentPostProcessor` | Spring Boot Environment 后置处理器，排除 Jackson 自动配置 |
| `JsonWarmupRunner` | 启动期预热（触发类加载 + 缓存计算） |
| `JsonConfigViewer` / `JsonConfigViewerMBean` | JMX 配置查看 |

### 6. 缓存与性能

| 类 | 说明 |
|---|---|
| `BeanSerializerCache` / `BeanSerializerInfo` | Bean 序列化器缓存（类级别 ConcurrentHashMap + 软引用） |
| `ClassMetadataCache`（support.cache） | 类元数据缓存（字段/方法/注解） |
| `LRUCache`（support.cache） | LRU 缓存（元数据辅助） |
| `ReflectCache`（support.cache） | 反射缓存（MethodHandle 缓存） |
| `FieldMeta`（cache） | 字段元数据封装 |
| `BoundedLruCache`（util） | 有界 LRU 缓存 |
| `StringInterner`（util） | 字符串驻留器（减少重复字符串内存） |

### 7. 序列化 SPI

| 类 | 说明 |
|---|---|
| `JsonModule` **SPI** | JSON 模块接口（类比 Jackson Module），注册自定义类型序列化 |
| `JsonModuleRegistry` | 模块注册表 |
| `ModuleSerializerRegistry` / `ModuleDeserializerRegistry` | 模块内序列化器注册表 |
| `JsonMetricsCallback` | JSON 指标回调（SPI） |

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
    enabled: true                    # 是否启用 YdszJson 引擎（默认 true）
    replace-jackson: true            # 是否替换 Spring Boot 默认 Jackson HttpMessageConverter
    pretty-print: false              # 是否美化输出
    default-property-inclusion: NON_NULL  # 序列化包含策略
    time-zone: Asia/Shanghai
    date-format: yyyy-MM-dd HH:mm:ss
    warmup-enabled: true             # 是否启用启动期预热
```

### 3. 直接使用

```java
import com.njydsz.common.json.json.JsonMapper;

// 序列化
String json = JsonMapper.toJson(user);

// 反序列化
User user = JsonMapper.fromJson(json, User.class);

// 类型安全反序列化
List<User> users = JsonMapper.fromJson(json, new TypeRef<List<User>>() {});

// JSON Patch
JsonPatch patch = JsonPatch.fromJson(diffArray);
JsonNode result = patch.apply(originalNode);

// JSON Merge Patch
JsonMergePatch mergePatch = JsonMergePatch.fromJson(patchNode);
JsonNode merged = mergePatch.apply(targetNode);
```

### 4. 自定义序列化器

```java
@Component
public class MoneySerializer extends JsonSerializer<Money> {
    @Override
    public void serialize(Money value, JsonGenerator gen) {
        gen.writeNumber(value.getAmount());
    }
}
```

## 配置项

| 配置 | 默认值 | 说明 |
|---|---|---|
| `ydsz.json.enabled` | true | 是否启用 YdszJson 引擎 |
| `ydsz.json.replace-jackson` | true | 是否替换 Jackson HttpMessageConverter |
| `ydsz.json.pretty-print` | false | 是否美化输出 |
| `ydsz.json.default-property-inclusion` | NON_NULL | 序列化包含策略 |
| `ydsz.json.time-zone` | Asia/Shanghai | 时区 |
| `ydsz.json.date-format` | yyyy-MM-dd HH:mm:ss | 日期格式 |
| `ydsz.json.warmup-enabled` | true | 启动期预热 |
| `ydsz.json.typing` | - | 多态类型信息策略 |

## SPI 扩展点

| SPI 接口 | 用途 | 注册方式 |
|---|---|---|
| `JsonModule` | JSON 编解码模块（类比 Jackson Module） | `List<JsonModule>` 自动收集 |
| `JsonSerializer` / `JsonDeserializer` | 自定义序列化器 | `@Component` |
| `PropertyNamingStrategy` | 属性命名策略 | `@Component` |
| `JsonMetricsCallback` | JSON 指标回调 | `@ConditionalOnMissingBean` |
| `TypeConverter` / `ValueFormatter` / `ValueWriter` | 值序列化策略 | SerializerRegistry |
| `CreatorResolver` / `BuilderResolver` | 构造器解析策略 | `@Component` |

## 与 Jackson 的兼容性

| 能力 | Jackson | YdszJson |
|---|---|---|
| 注解反序列化 | ✅ | ✅（完全兼容） |
| 序列化 | ✅ | ✅ |
| JsonNode 树模型 | ✅ | ✅（自研实现） |
| JsonPatch | ✅（外部库） | ✅（内置） |
| JsonMergePatch | ✅（外部库） | ✅（内置） |
| 多态反序列化 | ✅ | ✅ |
| ObjectMapper 自定义 | ✅ | 通过 JsonModule |

**迁移说明**：已使用 Jackson 注解的业务代码无需修改，加入 `ydsz-common-json` 依赖后自动替换。

## 自动配置类

| 类 | 触发条件 |
|---|---|
| `JsonAutoConfiguration` | `ydsz.json.enabled=true` |
| `JacksonExclusionEnvironmentPostProcessor` | `ydsz.json.replace-jackson=true`（排除 Jackson 自动配置） |

## 注意事项

1. **零运行时依赖**：YdszJson 引擎本身不依赖 Jackson；仅注解层 compileOnly 引用。
2. **启动预热**：建议开启 `ydsz.json.warmup-enabled=true`，避免首次请求触发类加载卡顿。
3. **替换 Jackson**：`replace-jackson=true` 后，Spring MVC HttpMessageConverter 使用 YdszJson。如仍需 Jackson（如某些第三方库），请关闭替换。
4. **自定义序列化器**：通过 `JsonModule` SPI 注册，避免全局 ObjectMapper 污染。
5. **多态类型**：`@JsonSubTypes` 配置确保反序列化安全，建议显式声明 type 映射。

## 变更记录

- **3.0.0**（2026-09-01）：重构为零依赖引擎 + Jackson 注解兼容层；内置 JsonPatch / JsonMergePatch；新增序列化缓存（BeanSerializerCache）+ 启动预热（JsonWarmupRunner）；HttpMessageConverter 自动替换 Jackson。
- **26.09.01**（2026-08-02）：初始版本，对标 common-jdbc 标准格式重构 README。
