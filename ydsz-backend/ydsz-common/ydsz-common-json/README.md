# ydsz-common-json

YDSZ 高性能 JSON 引擎 — ASM 字节码加速、LRU 字段缓存、零拷贝反序列化、SIMD 向量化解析、Schema 校验、YdszJsonPath 查询、JsonNode 树模型、Optional/UUID 支持、BigDecimal 精度模式、GraalVM 兼容、Spring MVC 集成、单元测试全覆盖。

> **注**：本模块 Maven artifactId 仍为 `ydsz-common-json`（项目命名空间前缀），但**模块内所有公开 API 均已去 Ydsz 品牌化**——主入口类为 [`YdszJson`](file:///d:/Code/ydsz/ydsz/ydsz-backend/ydsz-common/ydsz-common-json/src/main/java/com/njydsz/common/json/YdszJson.java)，所有注解使用 `@YdszJson*` 前缀。详见下方"模块定位"和"核心能力"章节。

## 模块定位

| 属性 | 值 |
|---|---|
| **层级** | L2 工具模块层 |
| **类型** | 公共依赖库（不独立部署） |
| **源文件数** | 100 |
| **测试文件数** | 16 |
| **测试方法数** | 199 |
| **主入口类** | `com.njydsz.common.json.YdszJson` |
| **配置文件** | `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` → `JsonAutoConfiguration` |

## 核心能力

### 核心 API

| 类 | 说明 |
|---|---|
| `YdszJson` | JSON 统一入口（序列化 / 反序列化 / 树操作 / 流式 API / ASM 预热 / 单次配置序列化） |
| `YdszJsonConfig` | 全局配置（日期格式 / 空值处理 / 命名策略 / BigDecimal 精度模式 / 最大 JSON 大小 / 最大深度） |
| `DeserializationConfig` | 反序列化配置（AutoType 安全检查委托） |

### ASM 字节码加速

| 类 | 说明 |
|---|---|
| `AsmBeanCodecGenerator` | ASM 字节码生成器（运行时生成序列化/反序列化字节码） |
| `AsmSerializer` / `AsmDeserializer` | ASM 生成的序列化器 / 反序列化器 |
| `GraalVmDetector` | GraalVM Native Image 环境检测（Native Image 中自动降级为反射模式） |

### 解析与生成

| 类 | 说明 |
|---|---|
| `JSONReader` / `YdszJsonParser` (stream) / `YdszJsonParser` (parser) | JSON 解析器（流式 / 事件驱动 / 递归下降） |
| `JSONWriter` / `JsonGenerator` | JSON 生成器（流式写入） |
| `BeanSerializer` / `BeanRead` / `ObjectReader` | Bean 序列化 / 反序列化 |
| `SerializerEngine` / `DeserializerEngine` | 序列化/反序列化引擎（Facade + 缓存管理） |
| `SerializationProvider` / `DeserializationProvider` | 序列化/反序列化 Provider（核心实现） |

### 字段缓存与上下文

| 类 | 说明 |
|---|---|
| `AsmCodecCache` | ASM Codec 缓存（LRU + SoftReference） |
| `BeanSerializerCache` / `BeanSerializerInfo` | Bean 序列化器缓存 |
| `SerializerCache` / `SerializerRegistry` | 序列化器注册表 |
| `FieldMeta` | 字段元数据（统一类型代码 + `@JsonInclude` 过滤逻辑） |
| `SerializationContext` | 序列化上下文（合并 5+ ThreadLocal 为单一实例，降低内存开销） |
| `YdszJsonCacheStats` | 缓存统计 |

### 字节码优化

| 类 | 说明 |
|---|---|
| `ZeroCopyDeserializer` | 零拷贝反序列化器（避免不必要的字符串拷贝） |
| `VectorSimdUtil` | SIMD 向量化解析（JDK 17+ Vector API） |
| `BytesUtil` | 字节工具 |

### 树模型

| 类 | 说明 |
|---|---|
| `JsonNode` | JSON 节点基类（对标 Jackson JsonNode） |
| `ObjectNode` / `ArrayNode` / `TextNode` / `NumberNode` / `BooleanNode` / `NullNode` / `MissingNode` | 节点类型 |
| `TreeConverter` | 树 ↔ 对象转换 |

### 类型系统

| 类 | 说明 |
|---|---|
| `YdszJsonType` / `TypeFactory` / `JsonTypeCode` | 类型系统 |
| `PropertyNamingStrategy` | 命名策略（camelCase / snake_case / kebab-case） |
| `NumberUtils` | 数字解析工具 |

### 注解

| 注解 | 说明 |
|---|---|
| `@YdszJsonField` | 字段映射（名称 / 格式 / 序列化控制） |
| `@JsonAlias` | 反序列化别名 |
| `@YdszJsonFormat` | 格式化（日期 / 数字） |
| `@JsonInclude` | 属性包含策略（ALWAYS / NON_NULL / NON_EMPTY / NON_DEFAULT） |
| `@JsonIgnoreProperties` | 忽略指定属性（类级别，支持 ignoreUnknown） |
| `@JsonValue` | 枚举值序列化方式（方法级别） |
| `@JsonAnyGetter` / `@JsonAnySetter` | 动态属性 Getter / Setter |
| `@JsonUnwrapped` | 嵌套属性展开（支持 prefix / suffix） |
| `@YdszJsonView` | 视图过滤 |
| `@YdszJsonPropertyOrder` | 字段排序 |
| `@YdszJsonCreator` / `@YdszJsonBuilder` | 构造器 / Builder |
| `@YdszJsonTypeInfo` / `@YdszJsonSubTypes` / `@YdszJsonSubType` | 多态序列化 |
| `@YdszJsonVisibility` | 可见性控制 |
| `@YdszJsonClass` | 类型标记 |

### 高级功能

| 类 | 说明 |
|---|---|
| `JsonPointer` | JSON Pointer（RFC 6901） |
| `YdszJsonPath` | JSONPath 查询（递归下降 / 数组过滤 / 切片 / 通配符） |
| `JsonMergePatch` | JSON Merge Patch（RFC 7396） |
| `JsonPatch` | JSON Patch（RFC 6902，支持 add/remove/replace/move/copy/test + Builder） |
| `YdszJsonSchema` / `SchemaValidator` / `ValidationResult` | JSON Schema 校验 |
| `AutoTypeChecker` | AutoType 安全检查（防反序列化漏洞） |

### Provider 与 Module

| 类 | 说明 |
|---|---|
| `SerializationProvider` / `DeserializationProvider` | 序列化 / 反序列化 Provider |
| `BeanSerializer` / `BeanDeserializerEngine` | Bean 引擎 |
| `FieldMetadataLoader` / `CreatorResolver` / `BuilderResolver` | 字段加载 / 构造器解析 / Builder 解析 |
| `PolymorphicTypeResolver` | 多态类型解析 |
| `ValueWriter` / `ValueFormatter` / `TypeConverter` | 值写入（含 Optional / UUID 支持） / 格式化 / 转换 |
| `YdszJsonModule` / `JsonModuleRegistry` / `ModuleSerializerRegistry` / `ModuleDeserializerRegistry` | 模块系统 |
| `StringInterner` | 字符串驻留池 |

### Spring 集成

| 类 | 说明 |
|---|---|
| `JsonHttpMessageConverter` | Spring MVC HttpMessageConverter（支持 @YdszJsonView / MappingJacksonValue 集成） |
| `JsonReactiveUtils` | WebFlux 响应式编码工具 |
| `JsonAutoConfiguration` | 自动配置 |
| `YdszJsonProperties` / `JsonModuleRegistrar` | 配置属性（useBigDecimal / monitoringEnabled 等） / 模块注册器 |

### 可观测性

| 类 | 说明 |
|---|---|
| `YdszJsonMetrics` / `JsonMetricsCallback` / `JsonCacheMetrics` / `JsonHealthIndicator` | 指标回调（null 短路优化） / 缓存指标 / 健康检查 |

### 异常体系

| 类 | 说明 |
|---|---|
| `YdszJsonException` | 顶层异常 |
| `JsonSerializationException` | 序列化异常（继承自 `YdszJsonException`） |
| `JsonDeserializationException` | 反序列化异常（继承自 `YdszJsonException`，含行列号 / 上下文片段） |

## 使用示例

### 基本用法

```java
import com.njydsz.common.json.YdszJson;
import com.njydsz.common.json.tree.ObjectNode;
import com.njydsz.common.json.jsonpath.YdszJsonPath;

// 序列化
String json = YdszJson.toJson(obj);

// 反序列化
User user = YdszJson.toObject(json, User.class);

// 树操作
Map<String, Object> root = YdszJson.parseMap(json);
String name = (String) root.get("name");

// JSONPath 查询
List<String> emails = YdszJsonPath.get(json, "$.users[*].email");

// 流式序列化（写入 Writer，避免中间 String）
YdszJson.toJson(obj, new StringWriter());

// 从 InputStream 反序列化
User user2 = YdszJson.toObject(inputStream, User.class);

// fromJson 别名（与 toJson 对称）
User user3 = YdszJson.fromJson(json, User.class);
```

### Optional 与 UUID 支持

```java
// Optional<String> 序列化为 "value" 或 null
Map<String, Object> data = new HashMap<>();
data.put("name", Optional.of("John"));
data.put("nickname", Optional.empty());
// {"name":"John","nickname":null}

// UUID 序列化为字符串
Map<String, Object> data2 = new HashMap<>();
data2.put("id", UUID.randomUUID());
// {"id":"550e8400-e29b-41d4-a716-446655440000"}
```

### BigDecimal 精度模式

```java
// 启用 BigDecimal 解析（金融场景精度保护）
YdszJsonConfig.getInstance().setUseBigDecimal(true).apply();

Map<String, Object> result = YdszJson.parseMap("{\"price\":123.456}");
// result.get("price") 返回 BigDecimal(123.456)，而非 Double
```

### 单次配置序列化

```java
// 使用临时配置序列化，不影响全局配置
YdszJsonConfig tempConfig = YdszJsonConfig.copyOf(YdszJsonConfig.getInstance());
tempConfig.setWriteNulls(true);
String json = YdszJson.toJson(obj, tempConfig);
// 全局配置不受影响
```

### ASM 预热

```java
// 应用启动时预生成 ASM 字节码，避免首次请求延迟尖峰
YdszJson.warmup(User.class, Order.class, Product.class);
```

### JSON Patch (RFC 6902)

```java
// 使用 Builder 构建并应用 Patch
String result = JsonPatch.builder()
    .replace("/name", "Alice")
    .add("/email", "alice@example.com")
    .remove("/temp")
    .applyTo("{\"name\":\"Bob\",\"temp\":true}");
```

### JSON Merge Patch (RFC 7396)

```java
// 合并两个 JSON
String merged = YdszJson.merge(
    "{\"a\":1,\"b\":2}",
    "{\"b\":3,\"c\":4}");
// {"a":1,"b":3,"c":4}
```

### 树模型操作

```java
// 解析为 JsonNode 树
JsonNode tree = YdszJson.readTree("{\"name\":\"John\",\"age\":30}");
if (tree.isObject()) {
    ObjectNode obj = (ObjectNode) tree;
    String name = obj.get("name").asText();
    int age = obj.get("age").asInt();
}
```

## Spring Boot 自动装配

引入本模块依赖后，Spring Boot 应用会自动装配 `JsonHttpMessageConverter`，无需任何额外配置：

```java
@RestController
@RequestMapping("/users")
public class UserController {

    @GetMapping("/{id}")
    public User getById(@PathVariable Long id) {
        return userService.getById(id);
    }
    // 返回值由 JsonHttpMessageConverter 自动序列化为 JSON
}
```

可自定义配置项见 `YdszJsonProperties`（位于 `com.njydsz.common.json.spring.YdszJsonProperties`）。

## 依赖

```xml
<dependency>
    <groupId>com.njydsz</groupId>
    <artifactId>ydsz-common-json</artifactId>
</dependency>
```

## 架构层级

```
YdszJson (Facade, 用户接口)
  ├─ 序列化入口: toJson / toJsonBytes / format / toJson(Writer) / toJson(OutputStream)
  ├─ 反序列化入口: toObject / parseMap / parseArray / fromJson / toObject(InputStream)
  ├─ 树操作: readTree / valueToTree
  ├─ 高级 API: getByPath / getByPointer / merge / diff / validate / isValid / warmup
  └─ Engine 层（SerializerEngine / DeserializerEngine）
       ├─ 缓存管理（SerializerCache、AsmCodecCache、FieldMeta、SerializationContext）
       └─ 性能监控（serializeCount、serializeTotalNanos、metricsCallback null 短路）
            └─ Provider 层（SerializationProvider / DeserializationProvider）
                 ├─ ASM 字节码生成（AsmBeanCodecGenerator + GraalVmDetector 降级检测）
                 ├─ 零拷贝反序列化（ZeroCopyDeserializer + YdszJsonParser）
                 ├─ 字段元数据加载（FieldMetadataLoader）
                 ├─ 多态类型解析（PolymorphicTypeResolver）
                 ├─ AutoType 安全检查（AutoTypeChecker）
                 └─ Writer/Formatter/Converter（ValueWriter 含 Optional/UUID 类型代码）
```

## 设计原则

1. **零外部 JSON 库依赖**：纯 Java 实现，不引入 Jackson / FastJSON / Gson。
2. **ASM 优先**：热路径生成字节码，避免反射开销；GraalVM Native Image 中自动降级。
3. **零拷贝反序列化**：直接解析 JSON 到 Bean 字段，跳过 Map 中转。
4. **ThreadLocal 池优化**：`SerializationContext` 合并 5+ ThreadLocal 为单一实例，降低内存碎片。
5. **JIT 友好**：类型代码系统替代 instanceof 链，提高分支预测准确率。
6. **类型安全**：AutoTypeChecker 防反序列化漏洞。
7. **金融级精度**：`useBigDecimal` 配置支持 BigDecimal 解析路径，避免浮点精度丢失。
8. **Optional / UUID 原生支持**：序列化时自动展开 Optional、UUID 转字符串。
