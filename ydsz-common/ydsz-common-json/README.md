# ydsz-common-json

> YDSZ 高性能 JSON 引擎（L2 工具层）— 零依赖、char[] 零拷贝、FNV-1a 哈希字段匹配、递归下降解析、JsonNode 树模型、Jackson 兼容注解

纯 Java 实现的 JSON 引擎，零外部 JSON 库依赖（不引入 Jackson / FastJSON / Gson）。通过 char[] 直接操作、零拷贝反序列化、ThreadLocal 对象池、FNV-1a 哈希字段匹配、快速数值解析等技术实现高性能；通过 Jackson 兼容注解实现平滑迁移。

**YdszJson 的架构设计兼具 Jackson 的"配置不可变"哲学和 Fastjson2 的"静态入口便利"。** `YdszJson` 作为静态入口提供 `toJson` / `toObject` 等零配置开箱即用体验，与 FastJSON 的静态工具风格一脉相承；而底层 `JsonConfig` 采用 `final` 字段构建不可变配置，配合 `JsonMapper.copyOf()` 以"副本 + 不可变替换"方式替代运行期可变状态，实现与 Jackson 相同的线程安全语义。两层 API 共享同一委托链（`YdszJson` → `JsonMapper` → `Engine` → `Provider` → `Parser`），行为完全一致，用户可根据场景自由选择而无需担心序列化行为分歧。

## 模块定位

| 属性 | 值                                                                                                    |
|---|------------------------------------------------------------------------------------------------------|
| **层级** | L2 工具模块层                                                                                             |
| **类型** | 公共依赖库（不独立部署）                                                                                         |
| **作用** | 提供高性能 JSON 序列化/反序列化、树模型、Jackson 兼容注解、Spring MVC 集成等能力                                                |
| **依赖** | Lombok；可选依赖 SLF4J、Spring Boot AutoConfigure、Spring Web、Jackson Annotations（编译期可见）、Jakarta Validation |
| **版本** | 1.0.0                                                                                                |

## 功能成熟度总览

> 以下标签标注每个功能域的 API 稳定性与生产就绪度：

| 标签 | 含义 | 使用建议 |
|---|---|---|
| **Stable** | 生产就绪，API 稳定，向后兼容 | 放心在任何场景使用 |
| **Beta** | 功能完整但 API 可能有调整 | 推荐使用，关注升级变更日志 |
| **Deprecated** | 已废弃，将在下个主版本移除 | 停止使用，迁移到替代方案 |

| 功能域 | 成熟度 | 备注 |
|---|---|---|
| 核心序列化/反序列化 | **Stable** | 含基本类型/嵌套对象/集合/泛型 |
| 注解体系（@JsonProperty/@JsonIgnore/@JsonFormat/@JsonInclude 等常用注解） | **Stable** | 80%+ Jackson 兼容 |
| Tree 模型（JsonNode/ObjectNode/ArrayNode） | **Stable** | |
| 命名策略（SNAKE_CASE/KEBAB_CASE/LOWER_CASE） | **Stable** | |
| Spring Boot 集成（JsonAutoConfiguration/JsonHttpMessageConverter） | **Stable** | |
| Module 系统（JsonModule SPI） | **Beta** | |
| @JsonCreator 构造器模式 | **Beta** | |
| JSON Patch (RFC 6902) / Merge Patch (RFC 7396) | **Beta** | v1.2.0 新增 |
| TypeRef 泛型工厂 | **Beta** | v1.2.0 新增 |
| JSON Schema 校验（JsonSchemaValidator，Draft-07 子集） | **Deprecated** | v1.2.3 起 `@Deprecated`，计划 2.0.0 移除；完整规范支持请迁移 networknt/json-schema-validator |
| @JsonBuilder 构造器模式 | **Deprecated** | 推荐使用 @JsonCreator + 静态工厂方法 |
| @JsonView 视图过滤 | **Stable** | 序列化层（ValueWriter/Formatter）与 MVC 层完整支持；字段裁剪统一用 @JsonView + toJson(obj, viewClass)（见 JsonView 注解文档） |
| @JsonUnwrapped | **未提供** | 推荐将嵌套对象序列化为子对象结构 |
| @JsonRawValue | **未提供** | 推荐手动构建后序列化 |
| @JsonAlias | **Stable** | v1.2.2 恢复支持：反序列化多命名兼容（如 user_id/userId），序列化仍输出主名称 |
| @JsonAnyGetter/@JsonAnySetter | **未提供** | 推荐显式定义字段提升可维护性 |
| @JsonEnumDefaultValue | **未提供** | 推荐 Controller 层手动处理 |
| @JsonVisibility | **未提供** | 推荐使用 @JsonIgnore |
| @JsonRootName | **未提供** | 推荐使用统一 Response 包装类 |

> 注：标记为「未提供」的注解类未在本模块发布（README 历史遗留描述），
> 若确有需要可参考 Jackson 对应注解并提交 Issue 评估补齐。

## 核心能力

### 1. 核心 API（根包）

| 类 | 说明 |
|---|---|
| `YdszJson` | JSON 统一入口（静态工具类），提供 `toJson` / `toObject` / `parseMap` / `parseArray` / `fromJson` / `readTree` / `valueToTree` / `warmup` / `format` / `convertValue` / `patch` / `mergePatch` / `toJson(OutputStream)` / `toJson(Writer)` 等方法 |
| `JsonMapper` | 实例化 Mapper（对标 Jackson ObjectMapper），支持 `builder()` 链式 Builder、独立配置副本、`convertValue` / `treeToValue` 等 |
| `JsonConfig` | 全局配置（日期格式 / 空值处理 / 命名策略 / BigDecimal 精度模式 / 根名称包裹 / 最大 JSON 大小 / 最大深度 / 泛型递归深度上限 / `builder()` / `copyOf()` / `install()` 不可变安装） |

### 2. 解析与生成（parser / writer / reader 包）

| 类 | 说明 |
|---|---|
| `JSONReader` | JSON 解析器（流式 / 事件驱动 / 递归下降，直接解析到 Bean 字段，无需 Map 中转） |
| `JsonParserUtil` (parser) | JSON 通用解析工具（parseObject/parseArray/parseNumber，JIT 优化 + 循环展开） |
| `JSONWriter` / `BeanSerializer` | JSON 生成器（流式写入，`toUtf8Bytes()` 字节序列化）、Bean 序列化器 |
| `BeanReader` | Bean 反序列化读取器（字段哈希缓存 O(1) 匹配，直接 char[] 解析） |
| `BeanDeserializerEngine` | Bean 反序列化引擎 |

### 3. Provider 与字段缓存（provider / cache 包）

| 类 | 说明 |
|---|---|
| `SerializationProvider` / `DeserializationProvider` | 序列化/反序列化 Provider（核心实现，`tryFastPathToWriter` 统一快速路径） |
| `BeanSerializerCache` / `BeanSerializerInfo` | Bean 序列化器缓存（含 `hasAnnotations` 标记，避免重复扫描） |
| `SerializerCache` / `SerializerRegistry` | 序列化器注册表 |
| `FieldMeta` | 字段元数据（统一类型代码 + `@JsonInclude` 过滤逻辑 + VarHandle 优化） |
| `FieldMetadataLoader` | 字段元数据加载（含父类字段遍历，修复继承字段静默丢失） |
| `SerializationContext` | 序列化上下文（合并多 ThreadLocal 为单一实例） |

### 4. 树模型（tree 包）

| 类 | 说明 |
|---|---|
| `JsonNode` | JSON 节点基类（对标 Jackson JsonNode） |
| `ObjectNode` / `ArrayNode` / `TextNode` / `NumberNode` / `BooleanNode` / `NullNode` / `MissingNode` | 节点类型 |
| `TreeConverter` | 树 ↔ 对象转换 |
| `JsonPatch` / `JsonMergePatch` | JSON Patch (RFC 6902) / Merge Patch (RFC 7396) 实现 |

### 4.1 JSON Schema 校验（schema 包，Draft-07 子集）

| 类 | 说明 |
|---|---|
| `JsonSchemaValidator` / `ValidationResult` | 轻量 Schema 校验器（F-5：**仅支持 Draft-07 子集**；v1.2.3 起 `@Deprecated`，计划 2.0.0 移除） |

> **能力边界明示**：仅支持 `type` / `required` / `properties` / `minimum` / `maximum` /
> `minLength` / `maxLength` / `pattern` / `enum` / `nullable` 关键字；
> **不支持** `$ref`、`allOf` / `oneOf`、`if/then`、`format`、`items` 数组逐项校验。
> 若需完整 Draft-07/2020-12 支持，请引入 `networknt/json-schema-validator` 依赖。

### 5. 类型系统（type / naming / number 包）

| 类 | 说明 |
|---|---|
| `JsonType` / `TypeFactory` / `TypeRef` | 类型系统（类型代码 + 泛型工厂方法） |
| `PropertyNamingStrategy` | 命名策略（`LOWER_CAMEL_CASE` / `UPPER_CAMEL_CASE` / `SNAKE_CASE` / `KEBAB_CASE`） |
| `NumberUtils` | 数字解析工具 |

### 6. 注解（annotation 包）

> **命名约定**：所有注解统一使用 `@Json*` 前缀，命名与 Jackson 兼容，从 Jackson 迁移时注解名无需修改。

#### 字段级注解

| 注解 | 说明 |
|---|---|
| `@JsonProperty` | 字段重命名与访问控制（`value` 名称 / `required` 必需 / `defaultValue` 默认值 / `access` 访问模式 `AUTO`·`READ_ONLY`·`WRITE_ONLY`·`READ_WRITE`，如 `@JsonProperty(value="user_id", required=true)`） |
| `@JsonIgnore` | 字段忽略（字段级，对标 Jackson `@JsonIgnore`） |
| `@JsonFormat` | 日期/数字格式化（`pattern` / `shape` / `locale` / `timezone` / `lenient` 宽松解析，如 `@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")`） |
| `@JsonInclude` | 属性包含策略（ALWAYS / NON_NULL / NON_EMPTY / NON_DEFAULT） |

#### 方法级注解

| 注解 | 说明 |
|---|---|
| `@JsonGetter` / `@JsonSetter` | 方法级 getter/setter 标记 |
| `@JsonValue` | 枚举值序列化方式（方法级，序列化时输出该方法的返回值） |

#### 类级注解

| 注解 | 说明 |
|---|---|
| `@JsonClass` | 类级配置（字段排序 `ordering` / 忽略字段 `ignores` / 包含字段 `includes` / 命名策略 `naming` / 输出 null `writeNulls` / 输出类名 `writeClassName` / 日期格式 `dateFormat` / 枚举序号 `serializeEnumUsingOrdinal`） |
| `@JsonPropertyOrder` | 类级字段排序（指定顺序数组 `{"id","name"}` 或 `alphabetic=true` 字母序） |
| `@JsonNaming` | 类级命名策略 |
| `@JsonIgnoreProperties` | 类级字段忽略 |
| `@JsonSerialize` / `@JsonDeserialize` | 自定义序列化器/反序列化器（`using = XxxSerializer.class`，需实现 `JsonSerializer` / `JsonDeserializer` 接口） |

#### 构造器注解

| 注解 | 说明 |
|---|---|
| `@JsonCreator` | 构造器/工厂方法标记（`defaultCreator` 默认构造 / `parameterNames` 参数名映射 / `enable` 启用 / `mode` 模式 `DEFAULT`·`PROPERTIES`·`DELEGATING`） |

#### 多态类型注解

| 注解 | 说明 |
|---|---|
| `@JsonTypeInfo` | 多态类型标识（`property` 类型键名 / `visible` 是否保留 / `use` 标识方式 `Id.NAME`·`CLASS`·`MINIMAL_CLASS`·`NONE` / `include` 包含结构 `As.PROPERTY`·`WRAPPER_ARRAY`·`WRAPPER_OBJECT`） |
| `@JsonSubTypes` / `@JsonSubType` | 子类型注册（`value` 子类型类 / `name` 类型名） |
| `@JsonTypeName` | 子类型逻辑名称（标注在子类上，优先于 `@JsonSubType.name()`） |

### 7. Module 系统（module 包）

| 类 | 说明 |
|---|---|
| `JsonModule` | 模块接口（参考 Jackson Module，可插拔的序列化/反序列化扩展机制） |
| `JsonModuleRegistry` / `ModuleSerializerRegistry` / `ModuleDeserializerRegistry` | 模块注册表（v1.2.3 起 `JsonModuleRegistry` 支持 JDK ServiceLoader SPI 自动发现非 Spring 环境模块） |
| `JsonModuleRegistrar` | Spring 环境模块注册器 |

### 8. Spring 集成（spring 包）

| 类 | 说明 |
|---|---|
| `JsonHttpMessageConverter` | Spring MVC HttpMessageConverter（继承 `AbstractGenericHttpMessageConverter`，支持泛型类型 `@RequestBody List<User>`、`@JsonView`、`maxRequestBodySize` 配置） |
| `JsonProperties` | 配置属性类（`ydsz.json.*`） |

### 9. 异常体系（exception 包）

| 类 | 说明 |
|---|---|
| `JsonException` | 顶层异常 |
| `JsonSerializationException` | 序列化异常（继承自 `JsonException`，含字段路径 fieldPath） |
| `JsonDeserializationException` | 反序列化异常（继承自 `JsonException`，含行列号 / 上下文片段） |

### 10. 自动配置（spring.boot 包）

| 配置类 | 激活条件 | 注册的 Bean |
|---|---|---|
| `JsonAutoConfiguration` | `ydsz.json.enabled=true`（默认启用），`JsonConfig` 在类路径 | `JsonConfigBean`（全局配置初始化 + 模块注册）、`JsonHttpMessageConverter`、`namingStrategyConverter` |

| 属性类 | 前缀 | 说明 |
|---|---|---|
| `JsonProperties` | `ydsz.json` | 全局 JSON 配置（日期格式 / 命名策略 / 空值处理 / BigDecimal 模式 / 最大深度 / 最大 JSON 大小等） |

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
    max-json-size: 10485760                    # JSON 最大长度（字节，默认 10MB）
    max-depth: 256                             # JSON 最大嵌套深度
    max-generic-depth: 64                      # 泛型递归深度上限
    max-request-body-size: 10485760            # HTTP 请求体最大大小（字节，默认 10MB）
    fail-on-error: false                       # 反序列化失败时是否抛出异常
    serialize-enum-using-ordinal: false        # 枚举是否使用序号序列化
    circular-reference-strategy: REF           # 循环引用处理策略：REF / IGNORE / ERROR
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

// 流式序列化（写入 OutputStream，避免中间 String）
YdszJson.toJson(obj, outputStream);

// 流式序列化（写入 Writer）
YdszJson.toJson(obj, new StringWriter());

// 从 InputStream 反序列化
User user2 = YdszJson.toObject(inputStream, User.class);

// 字节数组反序列化（UTF-8）
User user3 = YdszJson.fromJsonBytes(jsonBytes, User.class);
```

### 2. 泛型类型支持

```java
import com.njydsz.common.json.JsonType;
import com.njydsz.common.json.YdszJson;

// 方式1：使用 JsonType 匿名内部类
List<User> users = YdszJson.fromJson(json, new JsonType<List<User>>() {});

// 方式2：使用便捷方法（v1.2.0 新增）
List<User> users2 = YdszJson.fromJson(json, List.class, User.class);

// 方式3：使用 TypeRef 工厂方法（v1.2.0 新增）
Map<String, User> userMap = YdszJson.fromJson(json, new JsonType<Map<String, User>>() {});
```

### 3. JsonMapper Builder API

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

### 4. JSON Patch / Merge Patch (RFC 6902 / 7396)

```java
import com.njydsz.common.json.YdszJson;

// JSON Patch (RFC 6902)：REST PATCH 局部更新
String patched = YdszJson.patch(
    existingJson,
    "[{\"op\":\"replace\",\"path\":\"/name\",\"value\":\"newName\"}]"
);

// JSON Patch 应用到对象（返回新对象）
User patched = YdszJson.applyPatch(patchJson, existingUser, User.class);

// JSON Merge Patch (RFC 7396)
String merged = YdszJson.mergePatch(
    "{\"name\":\"Bob\",\"age\":30}",
    "{\"age\":31,\"email\":\"bob@example.com\"}"
);
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

### 6. 树模型操作

```java
import com.njydsz.common.json.YdszJson;
import com.njydsz.common.json.tree.JsonNode;
import com.njydsz.common.json.tree.ObjectNode;
import com.njydsz.common.json.tree.ArrayNode;

// 解析 JSON 为树
JsonNode root = YdszJson.readTree(json);

// 解析为 ObjectNode（获取对象节点）
ObjectNode objNode = YdszJson.parseObject(json);
String name = objNode.getString("name");

// 解析为 ArrayNode（获取数组节点）
ArrayNode arrNode = YdszJson.parseArrayNode(json);

// 对象转树
JsonNode tree = YdszJson.valueToTree(obj);
```

### 7. 格式化输出

```java
import com.njydsz.common.json.YdszJson;

// 格式化对象为美化 JSON
String prettyJson = YdszJson.format(obj);

// 格式化已有的 JSON 字符串（解析失败时返回原字符串）
String formatted = YdszJson.format(compactJson);
```

## SPI 扩展点

| SPI 接口 | 用途 | 实现方 |
|---|---|---|
| `JsonModule` | 可插拔的序列化/反序列化扩展机制（参考 Jackson Module），为指定类型注册自定义 Serializer/Deserializer | 业务模块实现 `JsonModule.SpringFactory` 标记接口后注册为 Spring Bean 即可自动发现 |
| `JsonSerializer<T>` | 自定义序列化器（通过 `@JsonSerialize(using = ...)` 注解指定） | 业务模块实现 |
| `JsonDeserializer<T>` | 自定义反序列化器（通过 `@JsonDeserialize(using = ...)` 注解指定） | 业务模块实现 |

> **注意**：`JsonModule` SPI 在 Spring 环境中通过 `@Component` 注解标注实现类即可被自动发现（同类型重复注册会自动去重）。在非 Spring 环境中，v1.2.3 起支持 JDK ServiceLoader 标准发现机制——在 jar 内提供
> `META-INF/services/com.njydsz.common.json.module.JsonModule` 文件（每行一个实现类全限定名）即可自动注册；也可使用 `SerializerRegistry.getInstance().register()` 手动注册。

## 使用注意事项

1. **Spring MVC 泛型类型支持**：`JsonHttpMessageConverter` 继承 `AbstractGenericHttpMessageConverter`，正确支持 `@RequestBody List<User>` 等泛型类型反序列化。

2. **Jackson 迁移**：`@JsonProperty` / `@JsonIgnore` / `@JsonFormat` / `@JsonInclude` 等 Jackson 兼容注解无需修改即可使用。迁移步骤：`ObjectMapper` → `JsonMapper`，`readValue/readTree/writeValueAsString` → `toObject/readTree/toJson`。
   - **迁移注意事项**：
     - `JsonMapper` 实例可安全共享（线程安全），配置通过 `ThreadLocalSnapshot` 在每次序列化时 apply/restore，与 Jackson `ObjectMapper`（配置不可变 + 显式传参）模型在 ThreadLocal 实现下的等价做法。
     - **命名策略在字段元数据加载时缓存**：`@JsonNaming` / `JsonConfig.namingStrategy` 对一个类的首次序列化生效并缓存 `jsonName`，后续切换命名策略对该已缓存类无效。如需不同命名策略，应在首次序列化前设置，或对不同命名使用不同 Bean 类型。
     - **`writeNulls` 配置生效范围**：`JsonMapper.builder().writeNulls(true)` 对带 `@JsonClass` 注解的 Bean 生效（走 ValueWriter 注解路径）；无注解的 Bean 走 `writeBeanNoAnnotationOptimized` 快速路径，null 字段始终省略。如需全局 writeNulls 对所有 Bean 生效，请在 Bean 上加 `@JsonClass`。

3. **ThreadLocal 池优化**：`SerializationContext` 合并多 ThreadLocal 为单一实例，降低内存碎片。

4. **长生命周期线程的 ThreadLocal 清理**（v1.2.3 新增 `YdszJson.cleanupThread()`）：MQ 消费者、定时任务、RPC 工作线程等复用线程，建议在每轮任务处理结束时调用 `YdszJson.cleanupThread()`，一次性释放序列化上下文、深度覆盖等全部 ThreadLocal 状态，防止跨任务配置残留：

   ```java
   @RabbitListener(queues = "order-queue")
   public void onMessage(String message) {
       try {
           Order order = YdszJson.fromJson(message, Order.class);
           orderService.process(order);
       } finally {
           YdszJson.cleanupThread(); // 任务边界清理，防止线程复用导致配置串扰
       }
   }
   ```

5. **多 Mapper 深度配置隔离**（v1.2.3 修复）：`JsonMapper.builder().maxDepth(n)` 的深度配置此前会经全局静态字段传播、导致不同 Mapper 实例互相覆盖；现改为线程级调用覆盖实现实例隔离。兼容说明：`JSONReader.setMaxDepth()` 运行期临时调参语义保留——仅当 Mapper 自定义了与全局不同的深度时才生效覆盖，未自定义深度的 Mapper 继续读取全局静态值。

6. **循环引用处理**：默认 `REF` 策略（自动检测并处理循环引用），可配置为 `IGNORE`（忽略）或 `ERROR`（抛出异常）。

7. **配置不可变推荐**：自 v1.0.0 起 `JsonConfig.install(newConfig)` 替代旧 `setInstance` 模式。业务侧仍可通过 `JsonMapper.builder()` 创建独立配置副本（不影响全局单例）。`install()` 内部同步做 `instance = newConfig; instance.apply()`，确保可见性与一致性。

8. **序列化异常路径追踪**：`JsonSerializationException.getMessage()` 自动在消息末尾附加 `[fieldPath: user.address.street]`，可直接定位嵌套序列化失败根因。`getFieldPath()` 返回原始路径字符串，供日志框架归类。

9. **高级功能治理观测制度**：标记为 `@Beta` API 的功能处于观测期（默认 2 个次要版本）。期内不承诺向后兼容——API 变更、移除或行为调整均不触发 major 版本递增。调用方须在升级前阅读 Release Notes，并在观测期结束前完成迁移或提出反馈。

10. **安全建议**：`ydsz-common-json` 已内置 JSON 大小限制、嵌套深度限制、泛型递归深度保护等防护机制。业务在反序列化不可信外部数据（缓存导出/导入、MQ 消息、开放接口入参）时，建议额外做类型校验。

## 性能优化技术

| 优化技术 | 说明 |
|---------|------|
| char[] 直接操作 | 避免 StringBuilder 中间分配 |
| 零拷贝反序列化 | 直接解析 JSON 到 Bean 字段，无需 Map 中转 |
| FNV-1a 字段哈希 | O(1) 字段匹配（与 Jackson 同量级；差异请以 JMH 基准实测为准） |
| ThreadLocal 对象池 | StringBuilder / JSONWriter 复用，减少 GC |
| ASCII 快速路径 | byte[] → char[] 跳过 UTF-8 解码 |
| 分级 StringBuilder | 小/中/大 JSON 预分配合适容量 |
| 不可变配置 + 原子替换 | 线程安全的配置管理 |

## 最新变更

### v1.2.3（性能与正确性修复）

> 本轮变更对标 Jackson / FastJSON2 实践与互联网大厂研发规范，经 239 项单元测试全量回归。

| 优先级 | 变更 | 说明 |
|------|------|------|
| P0 | 多 Mapper 深度配置隔离修复 | `JsonMapper` 的 `maxDepth` / `maxGenericDepth` / `useBigDecimal` 此前经全局静态字段传播导致多实例互相覆盖；现以线程级调用覆盖实现实例隔离，并修复 mapper 级 `useBigDecimal` 从不生效的缺陷。`JSONReader.setMaxDepth()` 运行期临时调参语义保留（详见「使用注意事项」第 5 条） |
| P0 | `BoundedLruCache` 读路径去写锁 | 读操作改为 `ConcurrentHashMap` 无锁读，写路径在锁内维护淘汰顺序（近似 LRU），消除热路径全互斥；淘汰语义权衡已在类注释说明 |
| P0 | 默认路径 `ThreadLocalSnapshot` 热分配消除 | 配置未变化时序列化跳过快照保存/恢复（空操作快速判定），降低默认路径 GC 压力 |
| P0 | 补齐无界缓存上限 | `FieldMetadataLoader` 的 `JSON_VALUE_METHOD_CACHE` / `COMPUTED_PROPERTIES_CACHE` 与 `TypeFactory.typeCache` 由无界 Map 改为 `BoundedLruCache`（上限 1024），消除长期运行内存泄漏风险 |
| P1 | ThreadLocal 生命周期治理 | 新增 `YdszJson.cleanupThread()` 统一清理 API（MQ / 定时任务 / RPC 线程在任务边界调用）；读取器对象池增加 64K 字符缓冲上限，防止超大报文撑爆池 |
| P1 | Jackson 注解兼容桥（`JacksonAnnotationBridge`） | classpath 存在 `jackson-annotations` 时自动识别 Jackson 同名注解（`@JsonProperty` / `@JsonIgnore` / `@JsonAlias` / `@JsonIgnoreProperties` / `@JsonValue` 等），读写双向生效，**原生注解优先**；依赖缺失时零开销降级。解决 Jackson 迁移期 import 错包静默失效问题 |
| P2 | `JsonModuleRegistry` 支持 ServiceLoader SPI | 非 Spring 环境通过 `META-INF/services` 自动发现模块；同类双注册（SPI + Spring Bean）自动去重 |
| P2 | `JsonSchemaValidator` 标注 `@Deprecated` | 仅覆盖 Draft-07 高频子集（10 个关键字），完整规范支持请迁移 networknt/json-schema-validator；计划 2.0.0 移除 |

## 版本兼容性

| 版本 | 兼容性说明 |
|------|-----------|
| v1.0.0 → v1.1.0 | ⚠️ `JsonConfig.getInstance()` 标记 `@Deprecated`，推荐使用 `JsonConfig.copyOf()` / `install()` |
| v1.1.0 → v1.2.0 | ✅ 向后兼容，无破坏性变更 |
| v1.2.2 → v1.2.3 | ✅ 向后兼容：新增 `YdszJson.cleanupThread()` 等 API；多 Mapper 深度隔离为缺陷修复（原先多实例深度互相覆盖属未定义行为）；`JsonSchemaValidator` 标注 `@Deprecated` |
| v1.2.0 → 未来版本 | 标注 `@Beta` 的 API 可能破坏性变更；标注 `@Deprecated` 的 API 将在下个主版本移除 |

### 与父 POM 版本对照（E-3）

模块 `ydsz-common-json` 不声明独立 `<version>`，随父 POM（`ydsz-common` / `ydsz-cloud`）发布：

| 本文档版本 | 父 POM 版本 | 说明 |
|------|-----------|------|
| v1.0.0 ~ v1.2.0 | `1.0.0-SNAPSHOT` | 功能版本在 README「最新变更」维护，制品版本统一由父 POM 控制 |

> 若需独立发布模块版本，可启用 Maven flatten 插件在父 POM 统一管理。

---

*文档更新日期：2026-08-15 | 功能版本：v1.2.3 | 审计方法：全量源码静态走读 + 实际代码证据交叉验证 + 239 项单元测试回归*
