# RemiJson 使用规范指南

> remi-common-json 模块是 remi-cloud 项目唯一的 JSON 序列化/反序列化引擎。  
> 本文档定义全项目统一的 JSON 使用规范、API 选择指南和最佳实践。

---

## 一、核心原则

1. **唯一数据源**：全项目 JSON 操作 **必须且仅能** 使用 `remi-common-json`，**禁止** 引入 Jackson/Gson/Fastjson/org.json 等任何第三方 JSON 库。
2. **最小认知负载**：API 设计遵循「零意外」原则 —— 命名直观、行为可预期、出错有明确消息。
3. **安全优先**：所有反序列化路径默认启用 AutoType 白名单检查，标记安全类型的实体需显式声明 `@JsonClass`。

---

## 二、API 选择指南

### 2.1 RemiJson vs JsonMapper 的选用原则

| 场景 | 推荐 API | 理由 |
|------|---------|------|
| 大多数日常 JSON 操作 | `RemiJson` 静态方法 | 零配置，全局共享默认 Mapper，心智负担最低 |
| 需要非全局配置（如特定命名策略、日期格式） | `JsonMapper.builder()...build()` | 每个 Mapper 实例独立配置，互不影响 |
| 需要自定义序列化器/反序列化器 | 实现 `JsonModule` + `@Component` SPI 注册 | 自动发现，无需手动注册 |
| 对象类型转换（Map→POJO 等） | `RemiJson.convertValue()` | 语义明确，优于 `fromJson(toJson())` 链式调用 |

### 2.2 常用 API 速查

```java
// — 序列化 —
RemiJson.toJson(obj)              // 对象 → JSON 字符串
RemiJson.toJsonBytes(obj)         // 对象 → byte[]（推荐代替 .getBytes(UTF_8)）
RemiJson.format(obj)              // 对象 → 格式化 JSON（调试用）

// — 反序列化 —
RemiJson.fromJson(json, User.class)               // JSON → 对象
RemiJson.fromJson(json, new JsonType<List<User>>(){}) // JSON → 泛型对象
RemiJson.fromJsonBytes(bytes, User.class)          // byte[] → 对象
RemiJson.parseMap(json)                            // JSON → Map<String, Object>
RemiJson.parseArray(json, User.class)              // JSON → List<User>

// — 类型转换（1.1.0 新增）—
RemiJson.convertValue(map, User.class)             // Map → POJO（优先使用）
RemiJson.convertValue(list, new JsonType<List<User>>(){}) // 泛型转换

// — 树模型 —
RemiJson.parseObject(json)        // JSON → ObjectNode
RemiJson.readTree(json)           // JSON → JsonNode
ObjectNode obj = new ObjectNode();
obj.put("name", "value");         // 构建树
obj.getObjectNode("child");       // 获取嵌套对象节点
obj.getArrayNode("items");        // 获取嵌套数组节点
String json = RemiJson.toJson(obj); // 树 → JSON

// — 安全校验 —
RemiJson.isValidJson(json)        // 校验字符串是否为合法 JSON
```

### 2.3 不推荐的反模式

```java
// ❌ 反模式 1：toJson().getBytes() 链式调用
byte[] bytes = RemiJson.toJson(body).getBytes(StandardCharsets.UTF_8);

// ✅ 正确用法
byte[] bytes = RemiJson.toJsonBytes(body);

// ❌ 反模式 2：toJson + fromJson 链式类型转换
User user = RemiJson.fromJson(RemiJson.toJson(map), User.class);

// ✅ 正确用法（1.1.0 新增）
User user = RemiJson.convertValue(map, User.class);

// ❌ 反模式 3：手动 new JSONWriter/JSONReader 操作 JSON
JSONWriter writer = new JSONWriter(...);

// ✅ 正确用法：通过自定义 JsonSerializer SPI 实现
public class MySerializer implements JsonSerializer<MyType> {
    public void serialize(MyType obj, JSONWriter out) { ... }
}

// ❌ 反模式 4：用 MapUtils 做深转换再手动 set
Map<String, Object> ext = RemiJson.parseMap(json);
String val = (String) ext.get("key");

// ✅ 正确用法：优先用 POJO 反序列化
MyDto dto = RemiJson.fromJson(json, MyDto.class);
String val = dto.getKey();
```

---

## 三、树模型 vs POJO 序列化

### 3.1 选择原则

| 场景 | 推荐方式 | 理由 |
|------|---------|------|
| 数据结构固定且已知 | **POJO + 注解** | 编译期类型安全，IDE 自动补全，序列化性能最优 |
| 数据结构动态/半结构化 | **树模型** (ObjectNode/ArrayNode) | 灵活遍历，无需预定义类 |
| HTTP API 响应解析（字段不固定） | **树模型** | 按需取字段，避免定义大量 DTO |
| 配置热更新 / 嵌套配置解析 | **树模型** | 适合深层嵌套遍历 |
| 消息负载（结构固定） | **POJO** | 类型安全，方便后续维护 |

### 3.2 树模型 API 命名规范（1.1.0）

从 1.1.0 起，树模型采用统一小驼峰命名，`getJSONObject()`/`getJSONArray()` 已标记为 `@Deprecated`：

```java
// ✅ 推荐使用（1.1.0+）
ObjectNode child = obj.getObjectNode("child");
ArrayNode items = obj.getArrayNode("items");
ObjectNode item = items.getObjectNode(0);

// ⚠️ 已废弃（仍可用，但编译期有警告）
ObjectNode child = obj.getJSONObject("child");
```

---

## 四、JsonType 泛型引用

涉及 `List`、`Map`、泛型嵌套等场景时，必须使用 `JsonType` 传递完整泛型信息：

```java
// 泛型 List
List<User> users = RemiJson.fromJson(json, new JsonType<List<User>>() {});

// 泛型 Map
Map<String, Order> map = RemiJson.fromJson(json,
        new JsonType<Map<String, Order>>() {});

// 复杂嵌套
Map<String, List<Order>> complex = RemiJson.fromJson(json,
        new JsonType<Map<String, List<Order>>>() {});
```

---

## 五、自定义序列化器/反序列化器

### 5.1 开发规范

1. **必须** 通过 `JsonModule` SPI 注册，不得直接调用 `RemiJson.register()`
2. 序列化器实现 `JsonSerializer<T>`，反序列化器实现 `JsonDeserializer<T>`
3. 将 `JsonModule` 标记为 `@Component` 即可自动注册

### 5.2 标准模板

```java
@Component
public class MyModule implements JsonModule, JsonModule.SpringFactory {

    @Override
    public String getModuleName() { return "my-module"; }

    @Override
    public void setSerializers(ModuleSerializerRegistry registry) {
        registry.addSerializer(MyType.class, new MyTypeSerializer());
    }

    @Override
    public void setDeserializers(ModuleDeserializerRegistry registry) {
        registry.addDeserializer(MyType.class, new MyTypeDeserializer());
    }
}
```

参考示例：`remi-agent-domain` 中的 `AgentJsonModule`，`remi-common-safe` 中的 `SafeJsonModule`。

---

## 六、JSON Schema 验证

### 6.1 推荐使用场景

- **HTTP 任务参数校验**（参考 `HttpJobHandler.HTTP_PARAMS_SCHEMA`）
- **表单数据校验**（参考 `FlowFormEngineService.validateWithJsonSchema`）
- **配置项格式校验**（如 IP 白名单、URL 格式）
- **消息发送参数前置校验**

### 6.2 使用模板

```java
private static final JsonSchema MY_SCHEMA = JsonSchema.object()
    .addProperty("url",
        JsonSchema.string().required().minLength(1)
            .description("目标 URL（必填）"))
    .addProperty("method",
        JsonSchema.string()
            .enumValues("GET", "POST", "PUT")
            .description("HTTP 方法"))
    .addProperty("timeout",
        JsonSchema.integer().minimum(100).maximum(60000)
            .description("超时毫秒"))
    .addRequired("url");

// 校验
ValidationResult result = JsonSchemaValidator.validate(MY_SCHEMA, params);
if (!result.isValid()) {
    throw new IllegalArgumentException("参数校验失败: " + result.getErrors());
}
```

---

## 七、安全最佳实践

### 7.1 AutoType 白名单（自动生效）

- `remi-common-json` 已内置全面的反序列化安全防护（黑名单 + 白名单），业务模块无需额外配置
- 标记为安全的实体类需添加 `@JsonClass` 注解（系统启动时会自动扫描 `com.remisoft` 包）
- 基类（`BaseEntity`、`BaseResponse`、`QueueMessage`）已显式标记 `@JsonClass`

### 7.2 添加新实体到白名单

```java
@JsonClass(description = "用户实体，标记可安全反序列化")
@Data
public class User extends BaseEntity<Long> { ... }
```

### 7.3 显式注册

```java
// 非 com.remisoft 包下的类需显式注册
AutoTypeChecker.addToWhitelist("com.external.SomeDto");

// 包级别注册（仅限受信任的包）
AutoTypeChecker.addWhitelistPackage("com.trusted.partner.dto");
```

---

## 八、配置参考

```yaml
remi:
  json:
    enabled: true                        # 启用 RemiJson 自动配置
    date-format: "yyyy-MM-dd HH:mm:ss"   # 全局日期格式
    naming-strategy: LOWER_CAMEL_CASE    # 命名策略：LOWER_CAMEL_CASE | SNAKE_CASE | KEBAB_CASE | UPPER_CAMEL_CASE | LOWER_CASE
    write-nulls: false                   # 序列化时是否输出 null 值
    pretty-print: false                  # 是否格式化输出
    serialize-enum-using-ordinal: false  # 枚举序列化使用 ordinal
    max-json-size: 104857600             # 最大 JSON 大小（字节），默认 100MB
    max-depth: 500                       # 最大嵌套深度
    max-generic-depth: 50                # 最大泛型深度
    safe-mode: true                      # 启用 AutoType 安全模式
    whitelist-packages:                  # @JsonClass 注解扫描包
      - com.remisoft
    streaming-enabled: true              # 启用流式反序列化
    warmup-enabled: true                 # 启用启动预热
    disable-jackson-auto-configuration: true  # 排除 Jackson 自动配置
```

---

## 九、迁移与兼容性

### 9.1 从 Jackson 迁移

remi-common-json 提供 Jackson 兼容注解层，迁移路径：

| Jackson 注解 | RemiJson 等价注解 | 说明 |
|-------------|-----------------|------|
| `@com.fasterxml.jackson.annotation.JsonProperty` | `@com.remisoft.common.json.annotation.JsonProperty` | 直接替换 import |
| `@com.fasterxml.jackson.annotation.JsonIgnore` | `@com.remisoft.common.json.annotation.JsonIgnore` | 直接替换 import |
| `@JsonFormat` | `@com.remisoft.common.json.annotation.JsonFormat` | 直接替换 import |

### 9.2 从 Fastjson2 迁移

- `JSON.parseObject(json, Class.class)` → `RemiJson.fromJson(json, Class.class)`
- `JSON.toJSONString(obj)` → `RemiJson.toJson(obj)`
- `JSONObject.getJSONObject(key)` → `obj.getObjectNode(key)`
- `JSONArray.getJSONObject(i)` → `array.getObjectNode(i)`

---

## 十、检查清单

Code Review 时检查以下项目：

- [ ] 未引入任何第三方 JSON 库的 import（jackson/gson/fastjson/org.json）
- [ ] 序列化使用 `RemiJson.toJsonBytes()` 而非 `toJson().getBytes()`
- [ ] 类型转换使用 `convertValue()` 而非 `fromJson(toJson())` 链式
- [ ] 树模型使用 `getObjectNode()/getArrayNode()` 新 API
- [ ] 泛型反序列化使用 `JsonType` 传递完整泛型信息
- [ ] 自定义序列化器通过 `JsonModule` SPI 注册
- [ ] 新实体类添加了 `@JsonClass` 注解
- [ ] 未手动 `new JSONWriter()/JSONReader()` 操作底层流

---

> **文档版本**: 1.1.0 | **最后更新**: 2026-08-06 | **维护者**: remi-team
