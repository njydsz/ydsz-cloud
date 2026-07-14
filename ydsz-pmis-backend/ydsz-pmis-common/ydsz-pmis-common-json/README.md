# ydsz-pmis-common-json

PMIS 高性能 JSON 引擎 — ASM 字节码加速、LRU 字段缓存、零拷贝反序列化、SIMD 向量化解析、Schema 校验、JsonPath 查询、JsonNode 树模型、Spring MVC 集成、340 个测试全覆盖。

> **注**：本模块 Maven artifactId 仍为 `ydsz-pmis-common-json`（项目命名空间前缀），但**模块内所有公开 API 均已去 Ydsz 品牌化**——主入口类为 [`Json`](file:///d:/Code/ydsz/ydsz-pmis/ydsz-pmis-backend/ydsz-pmis-common/ydsz-pmis-common-json/src/main/java/com/njydsz/pmis/common/json/Json.java)，所有注解使用 `@Json*` 前缀。详见下方"模块定位"和"核心能力"章节。

## 模块定位

| 属性 | 值 |
|---|---|
| **层级** | L2 工具模块层 |
| **类型** | 公共依赖库（不独立部署） |
| **源文件数** | 91 |
| **测试覆盖** | 340 个测试全部通过 |
| **主入口类** | `com.njydsz.pmis.common.json.Json` |
| **配置文件** | `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` → `JsonAutoConfiguration` |

## 核心能力

### 核心 API

| 类 | 说明 |
|---|---|
| `Json` | JSON 统一入口（序列化 / 反序列化 / 树操作） |
| `JsonConfig` | 全局配置（日期格式 / 空值处理 / 命名策略） |
| `DeserializationConfig` | 反序列化配置 |

### ASM 字节码加速

| 类 | 说明 |
|---|---|
| `AsmBeanCodecGenerator` | ASM 字节码生成器（运行时生成序列化/反序列化字节码） |
| `AsmSerializer` / `AsmDeserializer` | ASM 生成的序列化器 / 反序列化器 |

### 解析与生成

| 类 | 说明 |
|---|---|
| `JSONReader` / `JsonParser` / `JsonParser` | JSON 解析器（流式 / 事件驱动） |
| `JSONWriter` / `JsonGenerator` | JSON 生成器（流式写入） |
| `BeanSerializer` / `BeanReader` / `ObjectReader` | Bean 序列化 / 反序列化 |
| `SerializerEngine` / `DeserializerEngine` | 序列化/反序列化引擎（Facade + 缓存管理） |
| `SerializationProvider` / `DeserializationProvider` | 序列化/反序列化 Provider（核心实现） |

### 字段缓存

| 类 | 说明 |
|---|---|
| `FastReflectCache` | 快速反射缓存 |
| `AsmCodecCache` | ASM Codec 缓存 |
| `LruFieldMetaCache` | LRU 字段元数据缓存 |
| `BeanSerializerCache` / `BeanSerializerInfo` | Bean 序列化器缓存 |
| `SerializerCache` / `SerializerRegistry` | 序列化器注册表 |
| `FieldMeta` | 字段元数据 |
| `JsonContext` / `SerializationContext` / `JsonCacheStats` | 上下文 / 缓存统计 |

### 字节码优化

| 类 | 说明 |
|---|---|
| `ZeroCopyDeserializer` | 零拷贝反序列化器（避免不必要的字符串拷贝） |
| `VectorSimdUtil` | SIMD 向量化解析（JDK 17+ Vector API） |
| `BytesUtil` | 字节工具 |

### 树模型

| 类 | 说明 |
|---|---|
| `JsonNode` | JSON 节点基类 |
| `ObjectNode` / `ArrayNode` / `TextNode` / `NumberNode` / `BooleanNode` / `NullNode` / `MissingNode` | 节点类型 |
| `TreeConverter` | 树 ↔ 对象转换 |

### 类型系统

| 类 | 说明 |
|---|---|
| `JsonType` / `TypeFactory` / `JsonTypeCode` | 类型系统 |
| `PropertyNamingStrategy` | 命名策略（camelCase / snake_case / kebab-case） |
| `NumberUtils` | 数字解析工具 |

### 注解

| 注解 | 说明 |
|---|---|
| `@JsonField` | 字段映射（名称 / 格式 / 序列化控制） |
| `@JsonAlias` | 反序列化别名 |
| `@JsonFormat` | 格式化（日期 / 数字） |
| `@JsonView` | 视图过滤 |
| `@JsonPropertyOrder` | 字段排序 |
| `@JsonCreator` / `@JsonBuilder` | 构造器 / Builder |
| `@JsonTypeInfo` / `@JsonSubTypes` / `@JsonSubType` | 多态序列化 |
| `@JsonVisibility` | 可见性控制 |
| `@JsonClass` | 类型标记 |

### 高级功能

| 类 | 说明 |
|---|---|
| `JsonPointer` | JSON Pointer（RFC 6901） |
| `JsonPath` | JSONPath 查询 |
| `JsonMergePatch` | JSON Merge Patch（RFC 7396） |
| `JsonPatch` | JSON Patch（RFC 6902） |
| `JsonSchema` / `SchemaValidator` / `ValidationResult` | JSON Schema 校验 |
| `AutoTypeChecker` | AutoType 安全检查（防反序列化漏洞） |

### Provider 与 Module

| 类 | 说明 |
|---|---|
| `SerializationProvider` / `DeserializationProvider` | 序列化 / 反序列化 Provider |
| `BeanSerializer` / `BeanDeserializerEngine` | Bean 引擎 |
| `FieldMetadataLoader` / `CreatorResolver` / `BuilderResolver` | 字段加载 / 构造器解析 / Builder 解析 |
| `PolymorphicTypeResolver` | 多态类型解析 |
| `ValueWriter` / `ValueFormatter` / `TypeConverter` | 值写入 / 格式化 / 转换 |
| `JsonModule` / `JsonModuleRegistry` / `ModuleSerializerRegistry` / `ModuleDeserializerRegistry` | 模块系统 |
| `StringInterner` | 字符串驻留池 |

### Spring 集成

| 类 | 说明 |
|---|---|
| `JsonHttpMessageConverter` | Spring MVC HttpMessageConverter（支持 @JsonView） |
| `JsonReactiveUtils` | WebFlux 响应式编码工具 |
| `JsonAutoConfiguration` | 自动配置 |
| `JsonProperties` / `JsonModuleRegistrar` | 配置属性 / 模块注册器 |

### 可观测性

| 类 | 说明 |
|---|---|
| `JsonMetrics` / `JsonMetricsCallback` / `JsonHealthIndicator` | 指标回调 / 指标 / 健康检查 |

### 异常体系

| 类 | 说明 |
|---|---|
| `JsonException` | 顶层异常 |
| `JsonSerializationException` | 序列化异常（继承自 `JsonException`） |
| `JsonDeserializationException` | 反序列化异常（继承自 `JsonException`） |

## 使用示例

```java
import com.njydsz.pmis.common.json.Json;
import com.njydsz.pmis.common.json.tree.ObjectNode;
import com.njydsz.pmis.common.json.jsonpath.JsonPath;

// 序列化
String json = Json.toJson(obj);

// 反序列化
User user = Json.toObject(json, User.class);

// 树操作
Map<String, Object> root = Json.parseMap(json);
String name = (String) root.get("name");

// JSONPath 查询
List<String> emails = JsonPath.read(json, "$.users[*].email");

// 流式序列化（写入 Writer，避免中间 String）
Json.toJson(obj, new StringWriter());

// 从 InputStream 反序列化
User user2 = Json.toObject(inputStream, User.class);
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

可自定义配置项见 `JsonProperties`（位于 `com.njydsz.pmis.common.json.spring.JsonProperties`）。

## 依赖

```xml
<dependency>
    <groupId>com.njydsz.pmis</groupId>
    <artifactId>ydsz-pmis-common-json</artifactId>
</dependency>
```

## 架构层级

```
Json (Facade, 用户接口)
  └─ Engine 层（SerializerEngine / DeserializerEngine）
       ├─ 缓存管理（SerializerCache、AsmCodecCache、FieldMeta、JsonContext）
       └─ 性能监控（serializeCount、serializeTotalNanos）
            └─ Provider 层（SerializationProvider / DeserializationProvider）
                 ├─ ASM 字节码生成（AsmBeanCodecGenerator）
                 ├─ 零拷贝反序列化（ZeroCopyDeserializer + JsonParser）
                 ├─ 字段元数据加载（FieldMetadataLoader）
                 ├─ 多态类型解析（PolymorphicTypeResolver）
                 ├─ AutoType 安全检查（AutoTypeChecker）
                 └─ Writer/Formatter/Converter
```

## 设计原则

1. **零外部 JSON 库依赖**：纯 Java 实现，不引入 Jackson / FastJSON / Gson。
2. **ASM 优先**：热路径生成字节码，避免反射开销。
3. **零拷贝反序列化**：直接解析 JSON 到 Bean 字段，跳过 Map 中转。
4. **ThreadLocal 池**：StringBuilder / JSONWriter / IdentityHashMap 复用，零分配热路径。
5. **JIT 友好**：方法分派路径短，便于 JVM 内联。
6. **类型安全**：AutoTypeChecker 防反序列化漏洞。
