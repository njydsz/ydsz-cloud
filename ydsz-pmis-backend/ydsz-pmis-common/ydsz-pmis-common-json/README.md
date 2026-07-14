# ydsz-pmis-common-json

PMIS 高性能 JSON 引擎 — ASM 字节码加速、LRU 字段缓存、零拷贝反序列化、SIMD 向量化解析、Schema 校验、JsonPath 查询、JsonNode 树模型、Spring MVC 集成、340 个测试全覆盖。

## 模块定位

| 属性 | 值 |
|---|---|
| **层级** | L5 业务服务层 |
| **类型** | 公共依赖库（不独立部署） |
| **源文件数** | 91 |
| **测试覆盖** | 340 个测试全部通过 |

## 核心能力

### 核心 API

| 类 | 说明 |
|---|---|
| `YdszJson` | JSON 统一入口（序列化 / 反序列化 / 树操作） |
| `YdszJsonConfig` | 全局配置（日期格式 / 空值处理 / 命名策略） |
| `DeserializationConfig` | 反序列化配置 |

### ASM 字节码加速

| 类 | 说明 |
|---|---|
| `AsmBeanCodecGenerator` | ASM 字节码生成器（运行时生成序列化/反序列化字节码） |
| `AsmSerializer` / `AsmDeserializer` | ASM 生成的序列化器 / 反序列化器 |

### 解析与生成

| 类 | 说明 |
|---|---|
| `JSONReader` / `JsonParser` / `YdszJsonParser` | JSON 解析器（流式 / 事件驱动） |
| `JSONWriter` / `JsonGenerator` | JSON 生成器（流式写入） |
| `BeanSerializer` / `BeanReader` / `ObjectReader` | Bean 序列化 / 反序列化 |

### 字段缓存

| 类 | 说明 |
|---|---|
| `FastReflectCache` | 快速反射缓存 |
| `AsmCodecCache` | ASM Codec 缓存 |
| `LruFieldMetaCache` | LRU 字段元数据缓存 |
| `BeanSerializerCache` / `BeanSerializerInfo` | Bean 序列化器缓存 |
| `SerializerCache` / `SerializerRegistry` | 序列化器注册表 |
| `FieldMeta` | 字段元数据 |
| `YdszJsonContext` / `YdszJsonCacheStats` | 上下文 / 缓存统计 |

### 字节码优化

| 类 | 说明 |
|---|---|
| `ZeroCopyDeserializer` | 零拷贝反序列化器（避免不必要的字符串拷贝） |
| `VectorSimdUtil` | SIMD 向量化解析（JDK 17+ Vector API） |
| `BytesUtils` | 字节工具 |

### 树模型

| 类 | 说明 |
|---|---|
| `JsonNode` | JSON 节点基类 |
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
| `@YdszJsonProperty` / `@YdszJsonField` | 字段映射 |
| `@YdszJsonFormat` | 格式化（日期 / 数字） |
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
| `YdszJsonPath` | JSONPath 查询 |
| `JsonMergePatch` | JSON Merge Patch（RFC 7396） |
| `YdszJsonSchema` / `SchemaValidator` / `ValidationResult` | JSON Schema 校验 |
| `AutoTypeChecker` | AutoType 安全检查（防反序列化漏洞） |

### Provider 与 Module

| 类 | 说明 |
|---|---|
| `YdszSerializationProvider` / `YdszDeserializationProvider` | 序列化 / 反序列化 Provider |
| `BeanSerializer` / `BeanDeserializerEngine` | Bean 引擎 |
| `FieldMetadataLoader` / `CreatorResolver` / `BuilderResolver` | 字段加载 / 构造器解析 / Builder 解析 |
| `PolymorphicTypeResolver` | 多态类型解析 |
| `ValueWriter` / `ValueFormatter` / `TypeConverter` | 值写入 / 格式化 / 转换 |
| `YdszJsonModule` / `YdszJsonModuleRegistry` | 模块系统 |
| `StringInterner` | 字符串驻留池 |

### Spring 集成

| 类 | 说明 |
|---|---|
| `YdszJsonHttpMessageConverter` | Spring MVC HttpMessageConverter |
| `YdszJsonAutoConfiguration` | 自动配置 |
| `YdszJsonProperties` / `YdszJsonModuleRegistrar` | 配置属性 / 模块注册器 |

### 可观测性

| 类 | 说明 |
|---|---|
| `YdszJsonMetrics` / `YdszJsonHealthIndicator` | 指标 / 健康检查 |

## 使用示例

```java
// 序列化
String json = YdszJson.toJSONString(obj);

// 反序列化
User user = YdszJson.parseObject(json, User.class);

// 树操作
ObjectNode root = YdszJson.parseObject(json);
String name = root.getString("name");

// JSONPath 查询
List<String> emails = YdszJsonPath.read(json, "$.users[*].email");
```

## 依赖

```xml
<dependency>
    <groupId>com.njydsz.pmis</groupId>
    <artifactId>ydsz-pmis-common-json</artifactId>
</dependency>
```
