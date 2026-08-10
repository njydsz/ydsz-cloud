# ydsz-cloud JSON 编码规范（统一使用 ydsz-common-json）

> **规范等级**：强制（Mandatory）。构建期自动检查，违规即构建失败。
> **适用范围**：ydsz-cloud 全仓库 `src/main` 下的自有代码（`com.njydsz.*` 包）。
> **不适用范围**：第三方依赖内部的 Jackson 使用（传递依赖，本项目不干预）。

---

## 一、核心原则

1. **唯一 JSON 数据源**：项目自有代码的 JSON 序列化 / 反序列化 / 树模型操作，**必须且仅能**使用 `ydsz-common-json`（`com.njydsz.common.json.*`，核心入口 `YdszJson` / `JsonMapper`）。
2. **不干预三方依赖**：Spring / Feign / Redis 客户端等第三方库内部使用 Jackson 属于其实现细节，**禁止**为"消灭 Jackson"而强改传递依赖、排除依赖或替换第三方库行为。`pom.xml` 中的 `optional` / `provided` 的 `jackson-annotations` 声明仅用于编译期注解解析，**不属于违规**。
3. **检查自动化**：本规范由构建期 Checkstyle 规则强制校验（绑定 `validate` 阶段），无需人工 review 把关。
4. **安全优先**：`ydsz-common-json` 已内置 JSON 大小限制、嵌套深度限制、泛型递归深度保护等防护机制。业务在反序列化不可信外部数据（缓存导出/导入、MQ 消息、开放接口入参）时，建议额外做类型校验。

---

## 二、强制规则（红线）

### R1 代码层：禁止 import 第三方 JSON 库

自有代码（`src/main`）**禁止出现**以下任何 import（含 static import）：

| 禁止的包 | 说明 |
|----------|------|
| `com.fasterxml.jackson.*` | Jackson 全家桶（core / databind / annotations / datatype / module） |
| `com.alibaba.fastjson.*` | Fastjson 1.x（存在安全漏洞，根 pom 已 banned） |
| `com.alibaba.fastjson2.*` | Fastjson2 |
| `com.google.gson.*` | Gson |
| `org.json.*` | org.json（JSONObject / JSONArray） |

```java
// ❌ 违规：直接 import Jackson
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.annotation.JsonProperty;

// ✅ 合规：统一使用 ydsz-common-json
import com.njydsz.common.json.YdszJson;
import com.njydsz.common.json.annotation.JsonProperty;
```

> 注释（含 Javadoc `@see` / `{@code}`）中的 Jackson 引用**不违规**，仅用于文档说明。

### R2 代码层：禁止使用第三方 JSON 库的全限定名

禁止在代码中使用 `new com.fasterxml.jackson.databind.ObjectMapper()` 等全限定名绕开 import 检查。

### R3 依赖层：禁止直接声明第三方 JSON 库依赖

业务模块 `pom.xml` **禁止直接声明**（compile / runtime scope）Jackson / Fastjson / Gson 依赖。此规则由根 `pom.xml` 的 `maven-enforcer-plugin`（`enforce-json-ecosystem`）校验。

```xml
<!-- ❌ 违规：业务模块直接声明 -->
<dependency>
    <groupId>com.fasterxml.jackson.core</groupId>
    <artifactId>jackson-databind</artifactId>
</dependency>
```

---

## 三、豁免清单（Allowlist）

以下场景**允许**接触 Jackson / Fastjson2，且不会触发检查：

| 场景 | 允许范围 | 原因 |
|------|----------|------|
| 第三方库内部 | 传递依赖中由 Spring / Feign / Redis / Minio SDK 等引入的 Jackson | 属其实现细节，本项目不干预 |
| 基础设施模块编译期注解解析 | `ydsz-common-*` 部分模块声明 `optional` / `provided` 的 `jackson-annotations` | 仅消除 javac "未知枚举常量" 警告，代码中不直接使用 |
| Javadoc 注释 | `@see com.fasterxml.jackson...` | 仅文档引用 |

> Checkstyle 检查**仅扫描 `src/main`**（`includeTestSourceDirectory=false`），因此测试代码中的基准对比库不受影响。

---

## 四、Jackson → ydsz-common-json API 对照

### 4.1 序列化 / 反序列化

| Jackson | ydsz-common-json | 说明 |
|---------|-----------------|------|
| `new ObjectMapper().writeValueAsString(obj)` | `YdszJson.toJson(obj)` | 对象 → JSON 字符串 |
| `objectMapper.writeValueAsBytes(obj)` | `YdszJson.toJsonBytes(obj)` | 对象 → byte[] |
| `objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(obj)` | `YdszJson.format(obj)` | 格式化输出（调试） |
| `objectMapper.readValue(json, User.class)` | `YdszJson.fromJson(json, User.class)` | JSON → 对象 |
| `objectMapper.readValue(json, new TypeReference<List<User>>(){})` | `YdszJson.fromJson(json, new JsonType<List<User>>(){})` | JSON → 泛型对象 |
| `objectMapper.convertValue(map, User.class)` | `YdszJson.convertValue(map, User.class)` | Map → POJO |
| `objectMapper.readTree(json)` | `YdszJson.readTree(json)` / `parseObject(json)` | JSON → 树模型 |
| `objectMapper.getNodeFactory()` | `ObjectNode` / `ArrayNode` 直接构造 | 构建树 |

### 4.2 注解

| Jackson 注解 | ydsz-common-json 注解 | 说明 |
|--------------|----------------------|------|
| `@JsonProperty` | `@com.njydsz.common.json.annotation.JsonProperty` | 字段映射（命名一致，迁移时改包名即可） |
| `@JsonIgnore` | `@JsonIgnore` | 字段忽略 |
| `@JsonFormat` | `@JsonFormat` | 日期格式 |
| `@JsonInclude` | `@JsonInclude` | 序列化包含策略 |
| `@JsonCreator` | `@JsonCreator` | 反序列化工厂方法 |
| 自定义序列化器 | `JsonSerializer<T>` + `JsonModule` + `@Component` SPI | 可插拔扩展 |

### 4.3 使用规范速查

```java
// ✅ 序列化
String json = YdszJson.toJson(user);
byte[] bytes = YdszJson.toJsonBytes(user);          // 不要 toJson().getBytes()

// ✅ 反序列化
User user = YdszJson.fromJson(json, User.class);
List<User> users = YdszJson.fromJson(json, new JsonType<List<User>>(){});

// ✅ 类型转换（优先于 fromJson(toJson()) 链式调用）
User u = YdszJson.convertValue(map, User.class);

// ✅ 树模型
ObjectNode node = YdszJson.parseObject(json);
String name = node.get("name").asText();
```

---

## 五、检查机制说明

| 检查点 | 机制 | 触发时机 | 扫描范围 |
|--------|------|----------|----------|
| import 第三方 JSON 库 | Checkstyle `IllegalImport`（根 pom 接入） | `mvn validate` / `mvn install` | `src/main` |
| 直接声明第三方 JSON 依赖 | Maven Enforcer `enforce-json-ecosystem` | `mvn validate` | 各模块 `pom.xml` |
| 使用 Fastjson 1.x | Enforcer `bannedDependencies` | `mvn validate` | 依赖树 |

**违规表现**：

```
[ERROR] [ydsz-banned] 禁止在业务代码中直接使用第三方 JSON 库。
JSON 序列化/反序列化必须使用 ydsz-common-json（com.njydsz.common.json.*）。
第三方库内部使用 Jackson 不受影响（传递依赖不在检查范围）。
[ERROR] Failed to execute goal org.apache.maven.plugins:maven-checkstyle-plugin...:check ...
```

**本地快速自检**：

```bash
# 全量检查
./mvnw-local.sh checkstyle:check

# 仅检查指定模块（示例）
./mvnw-local.sh -pl ydsz-agent/ydsz-agent-server checkstyle:check
```

---

## 六、违规处置流程

1. **新增代码违规**：将 Jackson / Fastjson / Gson 用法迁移至 ydsz-common-json，参照第四节对照表。
2. **存量代码违规**：提交 PR 前完成迁移；如确属豁免场景，需在代码注释中说明理由并抄送架构评审。
3. **检查规则调整**：任何豁免调整必须修改本文档与 `checkstyle/checkstyle.xml` 同步，禁止只改代码绕过检查。

---

## 七、FAQ

**Q1：三方库（如 Spring 内部）用 Jackson 序列化，我们的对象会受影响吗？**
不会。第三方库在其内部使用自己的 Jackson 实例，与 ydsz-common-json 互不干扰。项目自有代码统一使用 ydsz-common-json，保证行为一致、可审计。

**Q2：为什么不动三方依赖里的 Jackson？**
Jackson 由 Spring Boot 等框架传递引入，是框架的运行时需求。强行 exclude 可能导致框架功能异常（如 actuator、feign 解码）。本项目策略是"自有代码不直接用，传递依赖不干预"。

**Q3：我的模块需要 `jackson-annotations` 才能编译怎么办？**
声明为 `optional`（或 `provided`）即可，这属于编译期注解解析豁免，代码中不得 import 使用。

**Q4：测试代码里可以用 Jackson 吗？**
业务模块的 `src/test` 同样建议使用 ydsz-common-json；仅 `ydsz-common-json` 模块自身的性能基准测试允许引入 Jackson / Fastjson2 做对比。

---

*配套文件：`checkstyle/checkstyle.xml`（构建期检查规则）、根 `pom.xml`（Checkstyle / Enforcer 插件接入）。*
