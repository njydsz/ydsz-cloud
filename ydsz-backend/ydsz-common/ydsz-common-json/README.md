# ydsz-common-json

YDSZ 高性能 JSON 引擎 — ASM 字节码加速、LRU 字段缓存、零拷贝反序列化、JIT 自动向量化、Schema 校验、YdszJsonPath 查询、JsonNode 树模型、Optional/UUID 支持、BigDecimal 精度模式、GraalVM 兼容、Spring MVC 集成、单元测试全覆盖。

> **注**：本模块遵循 **ydsz 项目品牌命名规范**——Maven artifactId 为 `ydsz-common-json`，包路径为 `com.njydsz.common.json.*`，主入口类为 [`YdszJson`](file:///d:/Code/ydsz/ydsz-pmis/ydsz-backend/ydsz-common/ydsz-common-json/src/main/java/com/njydsz/common/json/YdszJson.java)，所有注解使用 `@YdszJson*` 前缀。`ydsz` 是项目主品牌标识（pmis 是已废弃的遗留代号），全仓库统一保留 ydsz 品牌。详见下方"模块定位"和"核心能力"章节。

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
| `YdszJsonMapper` | 实例化 Mapper（对标 Jackson ObjectMapper，独立配置副本 / Metrics 回调 / 树模型 / JSONPath / 视图过滤 / convertValue / treeToValue / writeValueAsString / writeValueAsBytes / format） |
| `YdszJsonConfig` | 全局配置（日期格式 / 空值处理 / 命名策略 / BigDecimal 精度模式 / 根名称包裹 / 最大 JSON 大小 / 最大深度） |
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
| `VectorSimdUtil` | 字符数组批量操作（朴素循环 + JIT 自动向量化，不依赖反射） |
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

> **命名约定**：`@YdszJson*` 前缀注解为 YdszJson 专有功能（字段映射、格式化、视图过滤等）；
> `@Json*` 前缀注解（不带 Ydsz）为 Jackson 兼容注解，设计目标是从 Jackson 迁移时
> 注解名无需修改即可使用，降低迁移成本。两套注解命名并行是有意为之的设计决策。

| 注解 | 说明 |
|---|---|
| `@YdszJsonField` | 字段映射（名称 / 格式 / 序列化控制） |
| `@JsonProperty` | JSON 属性名称映射（Jackson 兼容，等价 @YdszJsonField.value） |
| `@JsonIgnore` | 忽略字段（Jackson 兼容，等价 @YdszJsonField.ignore=true） |
| `@JsonFormat` | 日期/数字格式化（Jackson 兼容，等价 @YdszJsonField.format） |
| `@JsonAlias` | 反序列化别名（Jackson 兼容） |
| `@YdszJsonFormat` | 格式化（日期 / 数字） |
| `@JsonInclude` | 属性包含策略（ALWAYS / NON_NULL / NON_EMPTY / NON_DEFAULT，Jackson 兼容） |
| `@JsonIgnoreProperties` | 忽略指定属性（类级别，支持 ignoreUnknown，Jackson 兼容） |
| `@JsonValue` | 枚举值序列化方式（方法级别，Jackson 兼容） |
| `@JsonRawValue` | 原始 JSON 值嵌入（字段值直接写入输出，不转义，Jackson 兼容） |
| `@JsonRootName` | 根名称包裹（配合 wrapRootValue 使用，Jackson 兼容） |
| `@JsonAnyGetter` / `@JsonAnySetter` | 动态属性 Getter / Setter（Jackson 兼容） |
| `@JsonUnwrapped` | 嵌套属性展开（支持 prefix / suffix，Jackson 兼容） |
| `@YdszJsonView` | 视图过滤 |
| `@YdszJsonPropertyOrder` | 字段排序 |
| `@YdszJsonCreator` / `@YdszJsonBuilder` | 构造器 / Builder |
| `@YdszJsonTypeInfo` / `@YdszJsonSubTypes` / `@YdszJsonSubType` | 多态序列化 |
| `@YdszJsonVisibility` | 可见性控制 |
| `@YdszJsonClass` | 类型标记 |

### 高级功能

> **范围说明**：以下高级功能为扩展能力，非核心序列化/反序列化路径。
> 核心场景（toJson/toObject/parseMap/parseArray）不依赖这些功能。
> JSON Schema 校验、JSON Patch (RFC 6902)、JSON Merge Patch (RFC 7396)
> 为独立工具类，可按需使用，不影响核心性能。

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

## Roadmap（后续规划）

| 优先级 | 特性 | 描述 |
|---|---|---|
| P2 | byte[] 直接解析路径 | 当前解析链基于 char[]，网络场景存在 byte[]→String→char[] 双重转换。计划新增 byte[] 直接解析路径，ASCII JSON 直接在 byte[] 上操作，非 ASCII 才转码。对标 FastJSON2 的 JSONReader byte[] 模式。 |
| P3 | 流式增量解析（Streaming Parser） | 当前 YdszJsonParser 需全量加载 JSON 到 char[]，无法处理超大 JSON 流式解析。计划基于 InputStream 实现 token-by-token 增量解析器，对标 Jackson JsonParser。 |
| P3 | Optional / UUID 原生支持 | 序列化时自动展开 Optional、UUID 转字符串。 |
| P3 | Schema/Patch 扩展能力 | JSON Schema 生成、JSON Patch (RFC 6902) 和 Merge Patch 为独立工具类，非核心路径，保留为扩展能力。 |

## 已完成优化记录（第六轮，2026-07-29）

| 级别 | 项 | 描述 |
|---|---|---|
| P0-1 | parseStringField 转义解码修复 | ASM 快速路径 parseStringField 现在正确解码 JSON 转义序列（\n/\"/\\ 等） |
| P0-2 | DeserializationProvider Set 分支修复 | Set 泛型反序列化路径新增 return 语句和 Set 实例化逻辑 |
| P0-3 | AutoTypeChecker 缓存有界化 | TYPE_CHECK_CACHE 从无界 ConcurrentHashMap 改为 LRU 有界缓存（max 4096） |
| P0-4 | parseObject(json, clazz) 类型转换修复 | 非 Map 类型不再直接 cast，委托 YdszJson 反序列化为目标 Bean |
| P1-1 | Jackson 兼容注解 | 新增 @JsonProperty / @JsonIgnore / @JsonFormat 注解，FieldMetadataLoader + FieldMeta 集成 |
| P1-2 | YdszJsonMapper API 补全 | 新增 convertValue/treeToValue/writeValueAsString/writeValueAsBytes/readValue(String,Type)/format |
| P1-3 | 配置项补全 | YdszJsonProperties 新增 wrapRootValue/failOnError 配置项 |
| P1-4 | recordSerialize/recordDeserialize 去重 | 提取 recordOperation 统一方法消除重复 |
| P1-5 | SerializationProvider 代码去重 | 提取 logAsmDowngrade 统一 ASM 降级日志，serializeToBytes 复用 wrapSerializationException |
| P1-6 | JSONWriter.writeCollection 去重 | 提取 writeCollectionElement 消除 List/非List 路径约 40 行重复代码 |
| P1-7 | Engine 层死代码清理 | 删除 SerializerEngine.java + DeserializerEngine.java（纯 Facade 委托，零外部引用） |
| P2-1 | ASM 字段解析性能优化 | 新增 buildFieldPositionMap 单遍扫描方法，O(N*M)→O(N+M) |
| P2-2 | parseNumberFast 幂表优化 | Math.pow(10,n) 替换为预计算 POW10 数组查表 |
| P2-3 | ThreadLocalSnapshot 模板消除 | 提取 withConfig 辅助方法统一 snapshot/apply/restore 模式 |
| P2-4 | FieldMeta 空 catch 块注释 | 12 处空 catch (Throwable/Exception) 块添加注释说明回退策略 |
| P2-5 | STRATEGY_CACHE 逻辑修正 | 补充策略注释 + 修复格式化问题 |
| P2-6 | JsonModuleRegistry Javadoc 修复 | 11 处乱码/截断的 Javadoc 注释修复 |
| P2-7 | JsonAutoConfiguration Javadoc 修正 | "基于 Jackson 二次封装" 修正为 "自研 JSON 引擎" |
| P3-1 | ppackage-private 拼写修正 | 3 处 "ppackage-private" 修正为 "package-private" |
| P3-2 | YdszJsonMapperTest 测试补全 | 新建 20 个测试方法覆盖全量 Mapper API |
| P3-3 | additional-spring-configuration-metadata.json 补全 | 新增 wrap-root-value/failOnError 配置项 |

