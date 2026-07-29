# ydsz-common-json

YDSZ 高性能 JSON 引擎 — ASM 字节码加速、LRU 字段缓存、零拷贝反序列化、JIT 自动向量化、Schema 校验（allOf/anyOf/oneOf）、YdszJsonPath 查询、JsonNode 树模型、Optional/UUID 支持、BigDecimal 精度模式、GraalVM 兼容、Spring MVC 集成、Mapper Builder API、方法级注解（@JsonGetter/@JsonSetter）。

> **注**：本模块遵循 **ydsz 项目品牌命名规范**——Maven artifactId 为 `ydsz-common-json`，包路径为 `com.njydsz.common.json.*`，主入口类为 [`YdszJson`](file:///d:/Code/ydsz/ydsz-pmis/ydsz-backend/ydsz-common/ydsz-common-json/src/main/java/com/njydsz/common/json/YdszJson.java)，所有注解使用 `@YdszJson*` 前缀。`ydsz` 是项目主品牌标识（pmis 是已废弃的遗留代号），全仓库统一保留 ydsz 品牌。详见下方"模块定位"和"核心能力"章节。

## 模块定位

| 属性 | 值 |
|---|---|
| **层级** | L2 工具模块层 |
| **类型** | 公共依赖库（不独立部署） |
| **源文件数** | ~102 |
| **主入口类** | `com.njydsz.common.json.YdszJson` |
| **配置文件** | `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` → `JsonAutoConfiguration` |

## 核心能力

### 核心 API

| 类 | 说明 |
|---|---|
| `YdszJson` | JSON 统一入口（序列化 / 反序列化 / 树操作 / 流式 API / ASM 预热 / 单次配置序列化） |
| `YdszJsonMapper` | 实例化 Mapper（对标 Jackson ObjectMapper，支持 `builder()` 链式 Builder / 独立配置副本 / Metrics 回调 / 树模型 / JSONPath / 视图过滤 / convertValue / treeToValue / writeValueAsString / writeValueAsBytes / format） |
| `YdszJsonConfig` | 全局配置（日期格式 / 空值处理 / 命名策略 / BigDecimal 精度模式 / 根名称包裹 / 最大 JSON 大小 / 最大深度 / `copyOf()` 工厂方法） |
| `MetricsHelper` | 指标监控包装工具（统一序列化/反序列化的指标记录逻辑，null 短路优化） |

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
| `JSONWriter` / `JsonGenerator` | JSON 生成器（流式写入，`toUtf8Bytes()` 零拷贝字节序列化） |
| `BeanSerializer` / `BeanRead` / `ObjectReader` | Bean 序列化 / 反序列化 |
| `SerializationProvider` / `DeserializationProvider` | 序列化/反序列化 Provider（核心实现，`tryFastPathToWriter` 统一快速路径） |

### 字段缓存与上下文

| 类 | 说明 |
|---|---|
| `AsmCodecCache` | ASM Codec 缓存（LRU + SoftReference） |
| `BeanSerializerCache` / `BeanSerializerInfo` | Bean 序列化器缓存 |
| `SerializerCache` / `SerializerRegistry` | 序列化器注册表 |
| `FieldMeta` | 字段元数据（统一类型代码 + `@JsonInclude` 过滤逻辑 + VarHandle 优化） |
| `SerializationContext` | 序列化上下文（合并 5+ ThreadLocal 为单一实例，动态内存估算） |
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
| `@JsonGetter` | 标记 getter 方法为 JSON 序列化属性（Jackson 兼容，方法级别） |
| `@JsonSetter` | 标记 setter 方法为 JSON 反序列化属性（Jackson 兼容，方法级别） |
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
| `YdszJsonSchema` / `SchemaValidator` / `ValidationResult` | JSON Schema 校验（支持 allOf / anyOf / oneOf 组合关键字） |
| `AutoTypeChecker` | AutoType 安全检查（防反序列化漏洞，支持包前缀白名单回退匹配） |

### Provider 与 Module

| 类 | 说明 |
|---|---|
| `SerializationProvider` / `DeserializationProvider` | 序列化 / 反序列化 Provider |
| `BeanSerializer` / `BeanDeserializerEngine` | Bean 引擎 |
| `FieldMetadataLoader` / `CreatorResolver` / `BuilderResolver` | 字段加载（含 @JsonGetter/@JsonSetter 方法级扫描） / 构造器解析 / Builder 解析 |
| `PolymorphicTypeResolver` | 多态类型解析（支持密封类 permitted subclasses） |
| `ValueWriter` / `ValueFormatter` / `TypeConverter` | 值写入（含 Optional / UUID 支持） / 格式化 / 转换 |
| `YdszJsonModule` / `JsonModuleRegistry` / `ModuleSerializerRegistry` / `ModuleDeserializerRegistry` | 模块系统 |
| `StringInterner` | 字符串驻留池 |

### Spring 集成

| 类 | 说明 |
|---|---|
| `JsonHttpMessageConverter` | Spring MVC HttpMessageConverter（支持 @YdszJsonView / streamingEnabled / maxRequestBodySize 配置） |
| `JsonReactiveUtils` | WebFlux 响应式编码工具 |
| `JsonAutoConfiguration` | 自动配置 |
| `YdszJsonProperties` / `JsonModuleRegistrar` | 配置属性 / 模块注册器 |

### 可观测性

| 类 | 说明 |
|---|---|
| `MetricsHelper` | 指标监控包装（统一序列化/反序列化指标记录，null 短路优化） |
| `YdszJsonMetrics` / `JsonMetricsCallback` / `JsonCacheMetrics` / `JsonHealthIndicator` | 指标回调 / 缓存指标 / 健康检查 |

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

### YdszJsonMapper Builder API

```java
import com.njydsz.common.json.YdszJsonMapper;
import com.njydsz.common.json.naming.PropertyNamingStrategy;

// 链式 Builder 创建独立配置的 Mapper
YdszJsonMapper mapper = YdszJsonMapper.builder()
    .namingStrategy(PropertyNamingStrategy.SNAKE_CASE)
    .dateFormat("yyyy-MM-dd HH:mm:ss")
    .writeNulls(true)
    .useBigDecimal(true)
    .build();

String json = mapper.toJson(obj);
User user = mapper.toObject(json, User.class);
```

### JSON Schema 校验（含 allOf/anyOf/oneOf）

```java
import com.njydsz.common.json.schema.YdszJsonSchema;
import com.njydsz.common.json.schema.SchemaValidator;

// allOf：所有 Schema 都必须验证通过
YdszJsonSchema schema = YdszJsonSchema.object()
    .addProperty("name", YdszJsonSchema.string())
    .allOf(
        YdszJsonSchema.object().addProperty("name", YdszJsonSchema.string().required()),
        YdszJsonSchema.object().addProperty("age", YdszJsonSchema.integer().minimum(0))
    );

ValidationResult result = SchemaValidator.validate(schema, data);
if (!result.isValid()) {
    System.err.println("验证失败：" + result.getErrors());
}
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

### 配置项

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `ydsz.json.use-big-decimal` | `false` | 启用 BigDecimal 解析路径（金融场景精度保护） |
| `ydsz.json.monitoring-enabled` | `true` | 启用序列化/反序列化监控指标 |
| `ydsz.json.safe-mode` | `true` | AutoType 安全检查模式 |
| `ydsz.json.whitelist-packages` | `[com.njydsz]` | AutoType 白名单扫描包列表（支持通配符） |
| `ydsz.json.date-format` | （空） | 全局日期格式 |
| `ydsz.json.naming-strategy` | `LOWER_CAMEL_CASE` | 全局命名策略 |
| `ydsz.json.write-nulls` | `false` | 是否输出 null 值 |
| `ydsz.json.pretty-print` | `false` | 是否美化输出 |
| `ydsz.json.wrap-root-value` | `false` | 是否包裹根名称 |
| `ydsz.json.fail-on-error` | `true` | 反序列化失败时是否抛出异常 |
| `ydsz.json.max-json-size` | `20971520` | JSON 最大长度（字节） |
| `ydsz.json.max-depth` | `512` | JSON 最大嵌套深度 |
| `ydsz.json.streaming-enabled` | `false` | 是否启用流式输出（chunked transfer encoding） |
| `ydsz.json.max-request-body-size` | `10485760` | HTTP 请求体最大大小（字节，默认 10MB） |

## GraalVM Native Image 兼容性

本模块通过 `GraalVmDetector` 检测 GraalVM Native Image 环境，在 Native Image 中自动降级为反射模式：

- **ASM 字节码生成**：Native Image 中禁用运行时字节码生成，回退到反射 + MethodHandle 路径
- **VarHandle 优化**：Native Image 中回退到 MethodHandle
- **AutoTypeWhitelistScanner**：启动时扫描 @YdszJsonClass 注解类并注册白名单，避免运行时反射

```java
// 检查是否在 GraalVM Native Image 中运行
if (GraalVmDetector.isNativeImage()) {
    // ASM 不可用，使用反射模式
}
```

## JMH 基准测试

本模块内置 JMH 基准测试套件（`YdszJsonBenchmark`），覆盖序列化/反序列化/Map/Array/Bytes 等 7 个基准测试方法。

```bash
# 运行基准测试
mvn test -Dtest=YdszJsonBenchmark -DskipTests=false
```

> **注意**：项目已全局禁用 JaCoCo 覆盖率检查并移除单元测试代码。JMH 基准测试为性能验证工具，非单元测试。

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
  └─ Provider 层（SerializationProvider / DeserializationProvider）
       ├─ tryFastPathToWriter（统一 Bean/Collection/Map 快速路径）
       ├─ 缓存管理（SerializerCache、AsmCodecCache、FieldMeta、SerializationContext）
       ├─ ASM 字节码生成（AsmBeanCodecGenerator + GraalVmDetector 降级检测）
       ├─ 零拷贝反序列化（ZeroCopyDeserializer + YdszJsonParser）
       ├─ 字段元数据加载（FieldMetadataLoader + @JsonGetter/@JsonSetter 方法级扫描）
       ├─ 多态类型解析（PolymorphicTypeResolver）
       ├─ AutoType 安全检查（AutoTypeChecker + 包前缀白名单回退）
       ├─ 指标监控（MetricsHelper null 短路优化）
       └─ Writer/Formatter/Converter（ValueWriter 含 Optional/UUID 类型代码）
```

## 设计原则

1. **零外部 JSON 库依赖**：纯 Java 实现，不引入 Jackson / FastJSON / Gson。
2. **ASM 优先**：热路径生成字节码，避免反射开销；GraalVM Native Image 中自动降级。
3. **零拷贝反序列化**：直接解析 JSON 到 Bean 字段，跳过 Map 中转。
4. **ThreadLocal 池优化**：`SerializationContext` 合并 5+ ThreadLocal 为单一实例，降低内存碎片。
5. **JIT 友好**：类型代码系统替代 instanceof 链，提高分支预测准确率。
6. **类型安全**：AutoTypeChecker 防反序列化漏洞，支持包前缀白名单回退匹配。
7. **金融级精度**：`useBigDecimal` 配置支持 BigDecimal 解析路径，避免浮点精度丢失。
8. **代码去重**：`tryFastPathToWriter` 统一快速路径，`MetricsHelper` 统一指标包装，消除重复代码。

## Roadmap（后续规划）

| 优先级 | 特性 | 描述 |
|---|---|---|
| P2 | byte[] 直接解析路径 | 当前解析链基于 char[]，网络场景存在 byte[]→String→char[] 双重转换。计划新增 byte[] 直接解析路径，ASCII JSON 直接在 byte[] 上操作，非 ASCII 才转码。 |
| P3 | 流式增量解析（Streaming Parser） | 当前 YdszJsonParser 需全量加载 JSON 到 char[]，无法处理超大 JSON 流式解析。计划基于 InputStream 实现 token-by-token 增量解析器。 |
| P3 | @JsonGetter 计算属性完整支持 | 当前 @JsonGetter/@JsonSetter 方法级注解已创建并扫描，但计算属性（无对应字段的 getter）需要 FieldMeta 支持方法后端，计划后续实现。 |

## 已完成优化记录

### 第七轮（2026-07-30）

| 级别 | 项 | 描述 |
|---|---|---|
| P0-2 | DeserializationProvider captureType 修复 | 删除 `captureType()` unchecked cast，改为 `(T) clazz.cast(result)` 显式类型检查 + `createSet()` 提取 Set 创建逻辑 |
| P0-3 + P2-1 | SerializationProvider 代码去重 | 提取 `tryFastPathToWriter()` 统一 Bean/Collection/Map 快速路径，消除 serialize/serializeToBytes 约 80 行重复代码；`tryFastSerialize` → `tryBeanSerialize` 移除冗余 ASM 调用 |
| P1-1 | YdszJsonMapper Builder API | 新增 `YdszJsonMapper.builder()` 链式 Builder（namingStrategy/dateFormat/writeNulls/prettyPrint/useBigDecimal/wrapRootValue/failOnError/maxJsonSize/maxDepth） |
| P1-2 | DeserializationProvider STRATEGY_CACHE 删除 | 删除零价值的策略缓存（所有非简单类型统一走 BEAN 路径，if-else 链已覆盖简单类型，synchronizedMap 反而是性能瓶颈） |
| P1-3 | SchemaValidator allOf/anyOf/oneOf | YdszJsonSchema 新增 allOf/anyOf/oneOf 字段 + 链式方法 + getter；SchemaValidator 新增 `validateCombinators()` 验证逻辑 |
| P1-4 | serialize(Object,long) ASM 快速路径 | 非 PrettyPrint 场景复用 `serialize(obj)` 标准路径（含 ASM 快速路径），PrettyPrint 走格式化路径 |
| P1-5 | MetricsHelper 提取 | 新建 `MetricsHelper` 统一指标包装逻辑，YdszJson/YdszJsonMapper 委托调用，消除约 100 行重复代码 |
| P1-6 | @JsonGetter/@JsonSetter 注解 | 新建方法级注解（Jackson 兼容），FieldMetadataLoader 新增 `applyMethodAnnotations()` 方法扫描 + `inferFieldNameFromGetter/Setter` 推断逻辑 |
| P2-2 | estimateThreadLocalMemory 动态计算 | 从硬编码值改为基于 `sbPool.capacity()` + `serializingObjects.size()` 动态计算 |
| P2-3 | streamingEnabled/maxRequestBodySize 配置 | YdszJsonProperties 新增两个配置项 + getter/setter，JsonAutoConfiguration 传递到 JsonHttpMessageConverter |
| P2-4 | deserialize(features) 参数文档 | Javadoc 明确 features 参数当前仅用于长度限制检查，其他 Feature 尚未实现 |
| P2-5 | AutoTypeChecker 白名单通配符 | 新增 `WHITELIST_PACKAGE_PREFIXES` 集合 + `addWhitelistPackage()` 方法 + `computeTypeAllowed` 包前缀回退匹配；AutoTypeWhitelistScanner 启动时注册包前缀 |
| P3-1 | GraalVM Native Image 文档 | README 新增 GraalVM 兼容性章节（GraalVmDetector 降级机制 + VarHandle 回退 + 白名单扫描） |
| P3-2 | JMH 基准测试文档 | README 新增 JMH 基准测试章节 |
| P3-3 | 配置元数据补全 | additional-spring-configuration-metadata.json 新增 streaming-enabled / max-request-body-size 配置项 |

### 第六轮（2026-07-29）

| 级别 | 项 | 描述 |
|---|---|---|
| P0-1 | parseStringField 转义解码修复 | ASM 快速路径 parseStringField 现在正确解码 JSON 转义序列 |
| P0-2 | DeserializationProvider Set 分支修复 | Set 泛型反序列化路径新增 return 语句和 Set 实例化逻辑 |
| P0-3 | AutoTypeChecker 缓存有界化 | TYPE_CHECK_CACHE 改为 LRU 有界缓存（max 4096） |
| P0-4 | parseObject(json, clazz) 类型转换修复 | 非 Map 类型委托 YdszJson 反序列化为目标 Bean |
| P1-1 | Jackson 兼容注解 | 新增 @JsonProperty / @JsonIgnore / @JsonFormat 注解 |
| P1-2 | YdszJsonMapper API 补全 | 新增 convertValue/treeToValue/writeValueAsString 等方法 |
| P1-3 | 配置项补全 | YdszJsonProperties 新增 wrapRootValue/failOnError |
| P1-4 | recordSerialize/recordDeserialize 去重 | 提取 recordOperation 统一方法 |
| P1-5 | SerializationProvider 代码去重 | 提取 logAsmDowngrade / wrapSerializationException |
| P1-6 | JSONWriter.writeCollection 去重 | 提取 writeCollectionElement |
| P1-7 | Engine 层死代码清理 | 删除 SerializerEngine + DeserializerEngine |
| P2-1~P2-7 | 性能/代码质量优化 | ASM 字段解析 / 幂表优化 / ThreadLocal 模板 / 空 catch 注释 / Javadoc 修复 |
| P3-1~P3-3 | 工程规范 | 拼写修正 / 测试补全 / 配置元数据 |
