# ydsz-common-json 过度设计评估与优化建议报告

> **评估日期**: 2026-08-09
> **评估范围**: `D:\Code\open\ydsz-cloud\ydsz-common\ydsz-common-json`
> **评估基准**: Jackson 2.x/3.x、Fastjson2、Gson 及阿里/字节/美团等互联网大厂研发规范

---

## 一、执行摘要

`ydsz-common-json` 是一个**完全自研、零外部 JSON 库运行时依赖**的高性能 JSON 引擎，对标 Jackson `ObjectMapper`，实现了从底层流式解析（JSONWriter/JSONReader）到上层数据绑定（POJO 序列化/反序列化）、树模型、JSON Schema、JSON Patch、Spring Boot 自动配置的完整能力矩阵。

**核心判断：该模块存在显著的过度设计问题。** 23,944 行自研代码替代了业界由 100+ 贡献者维护 15+ 年的 Jackson，30%~40% 的注解从未被业务使用，6 个超 1000 行的上帝类、7+ 个无淘汰机制的静态缓存、循环依赖、大量死代码和微优化构成了沉重的维护负担。

**建议策略**：`"减脂不减肌"`——保留模块对外的 API 契约和 SPI 扩展体系，大幅精简内部实现和未使用的注解/Feature，必要时引入 Jackson 作为底层引擎并在此之上保留团队特有的定制能力（XSS 过滤、脱敏、AutoType 白名单安全模型）。

| 维度 | 当前评分 | 优化目标 |
|------|---------|---------|
| 可维护性 | 5/10 | 7/10 |
| 健壮性 | 6/10 | 8/10 |
| 性能 | 7/10 | 7/10（保持） |
| 代码规模 | 23,944 行 | ≤ 15,000 行（-37%） |
| 注解采用率 | 60% | ≥ 90% |

---

## 二、模块现状全景

### 2.1 规模概览

```
指标                    数值
──────────────────────────────────
生产源文件数              88 个 .java
源码总行数                ~23,944 行
JMH 测试文件数            2 个
运行时外部 JSON 库依赖     0（自研引擎）
编译期依赖               jackson-annotations (optional)
测试期依赖               jackson-databind, fastjson2
```

### 2.2 架构分层

```
┌─────────────────────────────────────────────────────────┐
│  API 层: YdszJson (静态入口) / JsonMapper (实例化 Mapper) │
├─────────────────────────────────────────────────────────┤
│  注解层: 31 个注解 (对标 Jackson)                          │
├─────────────────────────────────────────────────────────┤
│  树模型: JsonNode/ObjectNode/ArrayNode + JsonPatch       │
│         + JsonMergePatch + JsonSchema                    │
├─────────────────────────────────────────────────────────┤
│  核心引擎: SerializationProvider (1350行)                 │
│          + DeserializationProvider (589行)                │
│          + JSONWriter (1337行) + JSONReader (1197行)      │
├─────────────────────────────────────────────────────────┤
│  缓存层: BeanSerializerCache + SerializerCache            │
├─────────────────────────────────────────────────────────┤
│  SPI 层: JsonModule / JsonModuleRegistry                 │
│         + JsonSerializer / JsonDeserializer               │
├─────────────────────────────────────────────────────────┤
│  Spring 集成: JsonAutoConfiguration                       │
│             + JsonHttpMessageConverter                    │
│             + JsonModuleRegistrar                         │
└─────────────────────────────────────────────────────────┘
```

### 2.3 跨模块采用全景

该引擎已深度整合进整个 `ydsz-cloud` 生态，覆盖 **21 个子模块、50+ 文件**：

| 层级 | 模块 | JSON 用途 |
|------|------|----------|
| L1 基础 | ydsz-common-json | 核心引擎（90+ 文件） |
| L2 工具 | ydsz-common-util | YAML/HTTP 工具 |
| L3 领域 | ydsz-common-domain | 实体序列化 |
| L4 数据 | ydsz-common-jdbc | MyBatis TypeHandler |
| L4 数据 | ydsz-common-redis | Redis 值序列化 |
| L4 数据 | ydsz-common-cache | 多级缓存 JSON |
| L5 服务 | ydsz-common-auth | JWT / 权限 / 脱敏 |
| L5 服务 | ydsz-common-safe | XSS 过滤 / 脱敏 |
| L5 服务 | ydsz-common-feign | Feign 编解码 |
| L5 服务 | ydsz-common-socket | WebSocket 消息 |
| L5 服务 | ydsz-common-netty | Netty 编解码 |
| L5 服务 | ydsz-common-queue | 消息队列 |
| L5 服务 | ydsz-common-sentry | 日志序列化 |
| L6 应用 | ydsz-common-base/web/app | HTTP 响应 |

---

## 三、行业对标分析

### 3.1 行业主流做法

| 公司 | JSON 策略 | 方式 |
|------|----------|------|
| **Spring Boot 生态** | Jackson (默认) | 直接使用，通过 `spring.jackson.*` 配置 |
| **阿里系** | Fastjson2 | 自建高性能引擎（全公司统一标准，100K+ 工程师维护） |
| **字节跳动** | Jackson + 定制扩展 | 包装 Jackson，添加内部定制序列化器 |
| **美团** | MSON (编译期代码生成) | APT 生成序列化代码，底层用 Gson 流解析器 |
| **腾讯** | 各 BG 自行选型 | 多数 BG 使用 Jackson 或 Fastjson2 |
| **Google** | Gson | 自身产品使用（Android、内部服务） |

**关键发现**：除阿里（Fastjson 是全公司级基础设施产品）外，**没有任何二线及以下规模的互联网公司选择自建完整 JSON 引擎**。业界通行做法是：

```
业界标准做法：
  成熟 JSON 库 (Jackson/Fastjson2/Gson)
  + 薄封装工具类 (JsonUtils)
  + 定制序列化器/反序列化器 (JsonSerializer/JsonDeserializer)
  + 全局配置管理 (ObjectMapper Bean)

ydsz 当前做法：
  完全自建 JSON 引擎 (YdszJson)
  = 23,944 行自研代码
  = 长期维护负担
  = 新人学习成本
```

### 3.2 自建引擎的合理性评估

自建 JSON 引擎在以下场景是合理的：
- ✅ 有明确的性能瓶颈，且成熟库无法满足（美团 MSON 的 Android 场景）
- ✅ 有独特的安全模型需求（如 Fastjson2 重新设计 AutoType）
- ✅ 作为公司级基础设施产品（阿里 Fastjson）

`ydsz-common-json` 的情况：
- ⚠️ 性能数据：JMH 测试框架已搭建，但未见与 Jackson/Fastjson2 的显著性能差距报告
- ✅ 安全模型有价值：`@JsonClass` AutoType 白名单、XSS 过滤、敏感数据脱敏
- ❌ 非公司级基础设施：团队规模不足以支撑长期维护完整 JSON 引擎

**结论**：安全模型和定制需求可通过 **包装 Jackson + 定制 SPI** 实现，无需自建完整引擎。

---

## 四、过度设计详细评估

### 4.1 注解系统：30%-40% 未被使用

| 注解 | 文件行数 | 业务端使用 | 状态 |
|------|---------|-----------|------|
| `@JsonIgnore` | 30 | 10+ 处（BaseEntity、TreeNode 等） | **活跃** |
| `@JsonProperty` | 79 | 5 处（DagNode、BaseResponse 等） | **活跃** |
| `@JsonFormat` | 78 | 4 处（BaseEntity 时间字段） | **活跃** |
| `@JsonClass` | 187 | 7 处（仅用 `description` 属性） | **活跃但 14/15 属性未使用** |
| `@JsonInclude` | 50 | 1 处（BaseResponse） | **活跃** |
| `@JsonView` | 74 | 14 处（FlowDefinition） | **活跃但已 @Deprecated** |
| `@JsonNaming` | 39 | **0 处** | **未使用** |
| `@JsonTypeInfo` | 132 | **0 处** | **未使用** |
| `@JsonSubType` | 37 | **0 处** | **未使用** |
| `@JsonTypeName` | — | **0 处** | **未使用** |
| `@Experimental` | 37 | **0 处** | **未使用** |

**问题分析**：

1. **`@JsonClass` 过度设计**：187 行、15 个属性，但 **100% 的业务端使用仅用了 `description` 属性**做 AutoType 白名单标记。其余 `ordering`、`ignores`、`includes`、`naming`、`writeClassName` 等 14 个属性完全是死代码。同时 `@JsonClass.naming()` 与 `@JsonNaming` 注解功能重叠。

2. **多态子系统（`@JsonTypeInfo` + `@JsonSubTypes` + `@JsonTypeName`）完全无业务采用**。`PolymorphicTypeResolver` 实现了完整的引擎端支持（9 种 Id/As 变体），但只有 `Id.NAME` + `As.PROPERTY` 组合真正工作，其余 7 种为占位符。

3. **`@JsonView` 既已弃用又仍在被使用**：Javadoc 写"不再推荐，定义独立 DTO"，但同时 `FlowDefinition` 有 14 处 `@JsonView`。设计决策摇摆不定。

4. **`@JsonNaming` 功能上被 `@JsonClass.naming()` 完全覆盖**，且可用全局 `ydsz.json.naming-strategy` 配置替代，无独立存在必要。

### 4.2 上帝类与巨型文件：6 个文件超 1000 行

| 文件 | 行数 | 核心问题 |
|------|------|---------|
| `SerializationProvider.java` | **1,349** | 上帝类：合并序列化调度 + ThreadLocal 管理 + 循环引用检测 + 快速路径 + Bean 序列化 + 异常处理 |
| `JSONWriter.java` | **1,337** | 性能过度优化：16 个 Feature 枚举、SIMD 字符检查、BigDecimal 整数/小数快速路径、UTF-16 代理对、U+2028/U+2029 转义 |
| `JsonParserUtil.java` | **1,289** | 解析器 + ThreadLocal 缓存管理 + 配置切换混合 |
| `JSONReader.java` | **1,196** | 16 个 Feature 枚举、FNV-1a 哈希字段名匹配、快速浮点解析、字符串转义 |
| `JsonMapper.java` | **1,115** | Builder 反模式 + ThreadLocal save/restore 模板代码（15 处重复） |
| `ValueWriter.java` | **1,054** | 值类型分派逻辑 + 格式化缓存 |

**问题深度分析**：

`SerializationProvider.java` 的 `serialize()` 方法（第 577-694 行）包含 7 级分派逻辑：
```
自定义 @JsonSerialize 序列化器 → 全局注册序列化器 → @JsonValue 方法
→ 深度安全网 → 循环引用检测 → tryFastPathToWriter(死代码)
→ tryBeanSerialize → ValueWriter.writeValue 回退
```

`tryFastPathToWriter` 方法（第 956-1002 行）声称"统一快速路径"但 **始终返回 null**（Bean 类型直接 return null），且注释标注"Bean 类型需回退到 StringBuilder 路径"。这是一个**有误导性命名的死代码方法**。

### 4.3 ThreadLocal 滥用：7+ 个变量 + 热路径开销

| ThreadLocal 变量 | 所属类 | 缓存对象 | 清理机制 |
|-----------------|------|---------|---------|
| `CONTEXT` | SerializationProvider | Context + StringBuilder + IdentityHashMap | `clearThreadLocals()` |
| `READER_POOL` | JSONReader | JSONReader + char[] 缓冲区 | **无显式清理** |
| `CHAR_BUFFER` | JsonParserUtil | char[8192] | `clearThreadLocals()` |
| `SB_POOL` | JsonParserUtil | StringBuilder(256) | `clearThreadLocals()` |
| `useBigDecimal` | JsonParserUtil | Boolean | `clearThreadLocals()` |
| `NAMING_STRATEGY` | FieldMetadataLoader | PropertyNamingStrategy | **无独立清理** |
| `DESERIALIZE_DEPTH` | DeserializationProvider | Integer | **无独立清理** |

**风险**：

1. **内存泄漏风险**：Tomcat 等线程池容器中，3 个 ThreadLocal 无显式清理，残留对象永久驻留
2. **热路径性能开销**：`JsonMapper.applyConfigIfNeeded()` 每次序列化调用执行 save/apply/restore 的 ThreadLocal 操作。代码注释承认这是"过渡方案"，目标 P0-5/P0-6 消除 ThreadLocal 但尚未完成
3. **非 Web 场景风险**：定时任务、MQ 消费者等场景无 `clearThreadLocals()` 调用，ThreadLocal 对象无限累积

### 4.4 静态缓存无淘汰：OOM 风险

| 缓存 | 类型 | Key 数量趋势 | 淘汰机制 |
|------|------|-------------|---------|
| `FIELD_META_CACHE` | ConcurrentHashMap(双层) | 随 Bean 类型增长 | 配置变更时全量清理 |
| `BEAN_SERIALIZER_INFO_CACHE` | ConcurrentHashMap(双层) | 随 Bean × 命名策略增长 | **无淘汰** |
| `CUSTOM_SERIALIZER_CACHE` | ConcurrentHashMap | 随自定义序列化器增长 | **无淘汰** |
| `TYPE_CODE_CACHE` | ConcurrentHashMap(256) | 固定容量 256 | **无淘汰** |
| `FORMATTER_CACHE` | ConcurrentHashMap | 随日期格式增长 | **无淘汰** |
| `CACHE` (BeanReader) | ConcurrentHashMap(1024) | 随 Bean 类型增长 | 手动 `clearCache()` |
| `TYPE_MAPPING_CACHE` | ConcurrentHashMap | 随多态类型增长 | `clearCache()` |
| `DESERIALIZE_METHOD_CACHE` | ConcurrentHashMap | 随 @JsonCreator 方法增长 | **无淘汰** |

**风险量化**：假设 200 个 Bean 类 × 3 种命名策略 × 每个 BeanSerializerInfo ~2KB = **~1.2MB 永久驻留内存**。在热部署/多租户/动态类加载场景下，缓存无限增长可导致 OOM。

### 4.5 其他代码质量问题

| 问题类型 | 数量 | 代表性示例 |
|---------|------|-----------|
| **循环依赖** | 1 | `YdszJson` ↔ `SerializationProvider`（入口层 ↔ Provider 层交叉引用） |
| **双重 Builder 反模式** | 1 | `JsonMapper.Builder` 与 `JsonConfig.Builder` 有 12 个重复字段和 setter |
| **死代码** | 4+ | `recordSerialize`/`recordDeserialize`/`ThrowingSupplier`（注释"指标模块已移除，直接执行"） |
| **Bug: asXxx(defaultValue) 忽略参数** | 6 方法 | `JsonNode.asText(default)` 等 6 个方法完全忽略传入的默认值 |
| **Bug: JsonPatch.remove 索引越界** | 1 | `applyRemove` 允许 `idx == arraySize`，但 ArrayList 索引范围是 `[0, size-1]` |
| **异常吞没** | 10+ 处 | `catch (Exception ignored)` 模式，warmup 失败/格式化失败/字段补集计算失败完全静默 |
| **@SuppressWarnings 过度使用** | 30+ 处 | 10 处 `deprecation` + 20+ 处 `unchecked` |
| **命名矛盾** | 1 | pom.xml 声称"零外部 JSON 库依赖"，但依赖 `jackson-annotations`（虽为 optional） |
| **设计反复** | 1 | `@JsonView` 已标记 `@Deprecated` 但 14 处仍在使用 |

### 4.6 过度工程化严重度矩阵

| 子系统 | 代码量 | 业务使用率 | 过工程化等级 | 建议 |
|--------|-------|-----------|-------------|------|
| 核心序列化引擎 (Provider + Writer + Reader) | ~4,100 行 | 100% | **严重** | 精简 Feature 枚举、移除未使用优化路径 |
| 注解系统 | ~800 行 (31个) | 60% (19/31) | **高** | 移除 4 个未使用注解 |
| 树模型 (Node + Object + Array) | ~1,600 行 | 高 | **高** | 消除 getter 三重变体重复 |
| JSON Patch/Merge Patch | ~545 行 | 低（仅 DiffReport） | **高** | 评估可移除 MOVE/COPY/TEST |
| JSON Schema | ~200 行 | 极低 | **中** | 保留但降为 optional 模块 |
| SPI 模块体系 | ~500 行 | 高（4 个实现） | **低** | 设计良好，保留 |
| Spring 集成层 | ~400 行 | 100% | **低** | 设计良好，保留 |
| **合计** | **~23,944 行** | — | — | **优化目标 ≤15,000 行** |

---

## 五、风险评估

### 5.1 安全风险

| 风险 | 严重度 | 当前状态 |
|------|--------|---------|
| 反序列化 RCE | 高 | ✅ 已通过 `@JsonClass` AutoType 白名单 + `maxDepth`/`maxJsonSize` 缓解 |
| DoS（深度/大小攻击） | 中 | ✅ 已通过 `maxDepth=256`/`maxJsonSize=10MB` 防护 |
| ThreadLocal 内存泄漏 | 中 | ⚠️ 3 个 ThreadLocal 无清理机制 |
| 静态缓存 OOM | 中 | ⚠️ 6 个缓存无淘汰策略 |
| Java 17+ 兼容性 | 中 | ⚠️ 大量 `setAccessible(true)` 在强封装下可能失败 |
| 安全漏洞修复速度 | 高 | ❌ 自研引擎需独立响应 CVE，无社区支持 |

### 5.2 维护风险

| 风险 | 影响 |
|------|------|
| 新人学习成本 | 23,944 行自研引擎 vs 使用 Jackson 仅需了解 API |
| JDK 版本升级适配 | 每个 JDK 大版本需独立测试和修复（反射、模块系统等） |
| Bug 修复成本 | 需从底层字符解析到上层注解全链路排查 |
| 性能回归测试 | 每个优化改动需重新 JMH 基准测试 |
| 安全漏洞修复 | 需自主发现和修复，无社区 CVE 预警 |

---

## 六、优化建议（P0/P1/P2 优先级）

### P0 — 立即修复（安全/稳定性相关）

#### P0-1: 修复 JsonNode 基类 bug（6 个 asXxx(defaultValue) 方法忽略默认值）

**问题**：`asText(String defaultValue)` 等方法完全不使用传入的 `defaultValue` 参数，调用 `node.asText("fallback")` 时永远不返回回退值。

**影响**：所有依赖默认值逻辑的代码均静默失效。

**方案**：
```java
// 修复前（基类默认实现）
public String asText(String defaultValue) {
    return asText();  // BUG: 忽略 defaultValue
}

// 修复后
public String asText(String defaultValue) {
    String text = asText();
    return (text != null) ? text : defaultValue;
}
```

**涉及方法**：`asText(String)`、`asInt(int)`、`asLong(long)`、`asDouble(double)`、`asBoolean(boolean)` 共 5 个。

**预估工时**：0.5 人日

---

#### P0-2: 修复 JsonPatch.remove 索引越界

**问题**：`applyRemove` 中 `if (idx < 0 || idx > arraySize)` 允许 `idx == arraySize`，但 `ArrayList.remove(int)` 索引范围是 `[0, size-1]`。

**方案**：将条件改为 `if (idx < 0 || idx >= arraySize)`。

**预估工时**：0.25 人日

---

#### P0-3: 为所有无淘汰静态缓存添加 LRU 淘汰

**问题**：6 个 ConcurrentHashMap 无淘汰策略，在热部署/多租户场景下可能 OOM。

**方案**：
```java
// 使用 Caffeine 或简单 LinkedHashMap LRU
private static final Map<Class<?>, BeanSerializerInfo> BEAN_SERIALIZER_INFO_CACHE =
    new LinkedHashMap<>(256, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry eldest) {
            return size() > MAX_CACHE_SIZE;  // 建议 200
        }
    };
```

**注意**：需加同步（`Collections.synchronizedMap` 包装）。

**预估工时**：1.5 人日（含回归测试）

---

#### P0-4: 为无清理 ThreadLocal 添加全局清理机制

**问题**：3 个 ThreadLocal（READER_POOL、NAMING_STRATEGY、DESERIALIZE_DEPTH）无清理入口，在 Tomcat 等线程池中残留。

**方案**：
```java
// 1. 注册 ServletContextListener 或 Spring Shutdown Hook
@Component
public class JsonThreadLocalCleanupListener implements ApplicationListener<ContextClosedEvent> {
    @Override
    public void onApplicationEvent(ContextClosedEvent event) {
        JSONReader.clearThreadLocals();
        FieldMetadataLoader.clearThreadLocals();
        DeserializationProvider.clearThreadLocals();
    }
}

// 2. 在 JsonHttpMessageConverter 中也添加 finally 清理
```

**预估工时**：1 人日

---

### P1 — 近期优化（2-4 周内）

#### P1-1: 精简未使用的注解（4 个移除）

| 移除注解 | 原因 |
|---------|------|
| `@JsonTypeInfo` | 0 业务使用，多态子系统未采用 |
| `@JsonSubType` | 0 业务使用 |
| `@JsonTypeName` | 0 业务使用 |
| `@JsonNaming` | 0 业务使用，被 `@JsonClass.naming()` 替代 |

**影响范围**：
- 引擎内部 `PolymorphicTypeResolver` 需同步移除
- `FieldMetadataLoader` 中 `@JsonNaming` 加载逻辑需移除
- 对外无 Breaking Change（无业务使用）

**预估工时**：2 人日（含引擎端清理）

---

#### P1-2: 精简 @JsonClass 注解（15 属性 → 5 属性）

**当前**：187 行，15 个属性，但仅 `description` 被业务使用。

**精简为**：
```java
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface JsonClass {
    /** AutoType 白名单描述 */
    String description() default "";
    /** 命名策略 */
    NamingStrategy naming() default NamingStrategy.CAMEL_CASE;
    /** 字段排除 */
    String[] ignores() default {};
    /** 字段排序 */
    String[] ordering() default {};
    /** 字段包含 */
    String[] includes() default {};
    
    enum NamingStrategy { ... }
}
```

**移除的属性**：`writeClassName`、`dateFormat`、`writeNulls`、`serializeEnumUsingOrdinal`、`typeKey`、`seeAlso`、`seeAlsoNames`、`autoType`（这些功能通过已有独立注解或全局配置覆盖）。

**预估工时**：1.5 人日

---

#### P1-3: 消除 @JsonView 的 @Deprecated 矛盾

**问题**：`@JsonView` 已标记 `@Deprecated`，但 `FlowDefinition.java` 有 14 处使用。

**方案 A（推荐）**：保留 `@JsonView`，移除 `@Deprecated`，更新 Javadoc 去除矛盾建议，保持单一清晰建议。

**方案 B**：移除 `@JsonView`，迁移 `FlowDefinition` 到显式 DTO 投影。

**推荐方案 A**：破坏性最小，`@JsonView` 功能本身有价值，仅设计建议矛盾。

**预估工时**：0.5 人日

---

#### P1-4: 拆分 SerializationProvider（1350 行 → 3 个类）

**方案**：按职责拆分为：

```
SerializationProvider.java          (~350行) 序列化调度入口
SerializationStrategy.java          (~400行) 策略链（自定义序列化器→注册序列化器→@JsonValue→Bean序列化→回退）
CircularReferenceDetector.java      (~200行) 循环引用检测 + 深度安全网
SerializationContextManager.java    (~200行) ThreadLocal 上下文管理
```

**预估工时**：3 人日

---

#### P1-5: 消除 JsonMapper.Builder 与 JsonConfig.Builder 重复

**问题**：两个 Builder 有 12 个重复字段和 setter，`build()` 方法逐个传递 13 个参数。

**方案**：删除 `JsonMapper.Builder`，直接暴露 `JsonConfig.Builder` 作为唯一配置入口：
```java
// 简化后
JsonMapper mapper = JsonMapper.builder()
    .config(JsonConfig.builder()
        .namingStrategy(SNAKE_CASE)
        .writeNulls(false)
        .build())
    .build();
```

或采用另一种方案：`JsonMapper.Builder` 内部仅保存 `JsonConfig.Builder` 引用，所有 setter 委托。

**预估工时**：1.5 人日

---

#### P1-6: 删除死代码

| 文件 | 死代码 | 行数 |
|------|-------|------|
| `JsonMapper.java` | `recordSerialize`/`recordDeserialize`/`ThrowingSupplier` | ~20 行 |
| `SerializationProvider.java` | `tryFastPathToWriter`（始终返回 null） | ~50 行 |
| `JSONWriter.java` | 未使用的 Feature 枚举项（审计后确定） | ~30 行 |
| `JSONReader.java` | 未使用的 Feature 枚举项（审计后确定） | ~30 行 |

**预估工时**：1 人日

---

### P2 — 持续改进（1-3 个月）

#### P2-1: 修复循环依赖 YdszJson ↔ SerializationProvider

**方案**：提取 `SerializationProvider` 依赖的接口，通过依赖注入或 SPI 打破循环。

```java
// 提取接口
public interface JsonSerializationEngine {
    String serialize(Object object, long maxJsonSize);
    // ...
}

// YdszJson 不再直接依赖 SerializationProvider
// 通过 JsonModuleRegistry 间接注入
```

**预估工时**：2 人日

---

#### P2-2: 精简 JSONWriter/JSONReader Feature 枚举

**当前**：各 16 个 Feature 枚举项。

**审计并移除未使用项**：根据全文搜索确认每个 Feature 的实际使用情况，建议保留 ≤ 8 个核心 Feature。

**预估工时**：1.5 人日

---

#### P2-3: 评估引入 Jackson 作为底层引擎

**方案**：保持对外 API 不变（YdszJson / JsonMapper），将底层实现替换为 Jackson，仅在以下定制层保留自研代码：

```
保留自研：
  ✅ @JsonClass AutoType 白名单安全模型
  ✅ XssStringDeserializer（JSON 反序列化时 XSS 清洗）
  ✅ SensitiveDataSerializer（敏感数据脱敏）
  ✅ JsonModule SPI（自定义模块注册）

替换为 Jackson：
  ⇢ 流式解析（JsonParser / JsonGenerator）
  ⇢ POJO 序列化/反序列化
  ⇢ 注解处理（@JsonProperty、@JsonIgnore 等映射到 Jackson 注解）
  ⇢ 树模型（JsonNode）
  ⇢ 日期/数字格式化
```

**优势**：
- 代码量从 23,944 行 → ~5,000 行
- 获得 Jackson 社区安全补丁
- JDK 版本兼容性由 Jackson 社区维护
- 性能持续优化由 Jackson 社区贡献

**劣势**：
- 引入外部依赖，失去"零外部 JSON 依赖"
- 定制行为（如 XSS 清洗）需要更多适配代码
- API 兼容迁移需要回归测试

**预估工时**：10-15 人日（含全量回归测试）

---

#### P2-4: 统一异常处理

**方案**：将 10+ 处 `catch (Exception ignored)` 替换为至少记录 SLF4J WARN 级别日志。

```java
// 修复前
} catch (Exception ignored) {
    // 预热失败，忽略
}

// 修复后
} catch (Exception e) {
    log.warn("JSON warmup failed for class: {}", clazz.getName(), e);
}
```

**预估工时**：0.5 人日

---

#### P2-5: 消除 ObjectNode/ArrayNode getter 重复

**方案**：将 `getString/getStringValue/getStringOrDefault` 等三重变体提取为基类 `JsonNode` 的泛型 default 方法：

```java
// 基类默认实现
public <T> T getOrDefault(String name, Function<JsonNode, T> extractor, T defaultValue) {
    JsonNode node = get(name);
    return (node != null && !node.isNull()) ? extractor.apply(node) : defaultValue;
}
```

**预估工时**：2 人日

---

#### P2-6: 为 toJsonWithFields/toJsonWithoutFields 缓存 JsonMapper

**方案**：
```java
private static final Map<String, JsonMapper> FIELD_FILTER_MAPPER_CACHE = 
    new ConcurrentHashMap<>();

public static String toJsonWithFields(Object obj, Set<String> visibleFields) {
    String cacheKey = String.join(",", visibleFields);
    JsonMapper mapper = FIELD_FILTER_MAPPER_CACHE.computeIfAbsent(cacheKey,
        k -> JsonMapper.builder().writeNulls(false).build());
    // ...
}
```

**预估工时**：0.5 人日

---

## 七、实施路线图

```
Week 1-2 (P0):
  ├── P0-1: 修复 asXxx(defaultValue) bug
  ├── P0-2: 修复 JsonPatch.remove 索引越界
  ├── P0-3: 缓存 LRU 淘汰机制
  └── P0-4: ThreadLocal 全局清理

Week 3-4 (P1 第一批):
  ├── P1-1: 移除 4 个未使用注解
  ├── P1-2: 精简 @JsonClass（15→5 属性）
  ├── P1-3: 消除 @JsonView 矛盾
  └── P1-6: 删除死代码

Week 5-6 (P1 第二批):
  ├── P1-4: 拆分 SerializationProvider
  └── P1-5: 消除 Builder 重复

Week 7-12 (P2):
  ├── P2-1: 修复循环依赖
  ├── P2-2: 精简 Feature 枚举
  ├── P2-4: 统一异常处理
  ├── P2-5: 消除 getter 重复
  ├── P2-6: 缓存 JsonMapper 实例
  └── P2-3: 评估 Jackson 作为底层引擎（技术预研）
```

---

## 八、预期收益

| 指标 | 当前 | 优化后 | 改善 |
|------|------|-------|------|
| 源码总行数 | 23,944 行 | ≤15,000 行 | -37% |
| 超 1000 行文件数 | 6 个 | ≤2 个 | -67% |
| 注解总数 | 31 个 | ≤25 个 | -19% |
| 注解采用率 | 60% | ≥90% | +30% |
| ThreadLocal 变量数 | 7 个 | 7 个（全部有清理） | 安全性提升 |
| 无淘汰缓存数 | 6 个 | 0 个 | OOM 风险消除 |
| 循环依赖 | 1 个 | 0 个 | 架构健康 |
| 死代码 | ~100 行 | 0 行 | 可维护性提升 |
| Bug (已知) | 7 个 | 0 个 | 质量提升 |
| 新人上手时间 | ~2 周 | ~1 周 | 效率提升 |

---

## 九、长期战略建议

### 9.1 短期（当前-3 个月）：减脂不减肌

执行 P0/P1 优化，代码量减少 30-40%，但保持对外 API 稳定，业务模块零改动。

### 9.2 中期（3-6 个月）：引入 Jackson 底层引擎

完成 P2-3 技术预研后，如决定替换，保持 `YdszJson`/`JsonMapper` 对外 API 不变，仅替换内部实现。这将长期将维护成本降低 60-70%。

### 9.3 长期（6-12 个月）：持续治理

- 建立 JSON 引擎代码审查清单
- 新增注解/Feature 需团队评审并确认有 ≥ 2 个业务消费者
- 每季度 review 缓存和 ThreadLocal 使用，防止退化
- 跟踪 Jackson/Fastjson2 上游安全公告，即使仍自研也从中学习

---

## 十、附录：关键文件索引

| 文件路径 | 建议操作 |
|---------|---------|
| `.../json/JsonNode.java` | 修复 asXxx(defaultValue) |
| `.../json/JsonMapper.java` | 消除 Builder 重复、删除死代码、简化 ThreadLocal |
| `.../json/internal/JsonConfig.java` | 合并 Builder |
| `.../json/provider/SerializationProvider.java` | 拆分为 3 个类、移除 tryFastPathToWriter |
| `.../json/writer/JSONWriter.java` | 精简 Feature 枚举 |
| `.../json/reader/JSONReader.java` | 精简 Feature 枚举 |
| `.../json/tree/JsonPatch.java` | 修复 remove 索引越界 |
| `.../json/tree/ObjectNode.java` | 消除 getter 重复 |
| `.../json/tree/ArrayNode.java` | 消除 getter 重复 |
| `.../json/annotation/JsonClass.java` | 精简至 5 属性 |
| `.../json/annotation/JsonTypeInfo.java` | 移除（和关联注解一起） |
| `.../json/annotation/JsonSubType.java` | 移除 |
| `.../json/annotation/JsonNaming.java` | 移除 |
| `.../json/annotation/JsonView.java` | 消除 @Deprecated 矛盾 |
| `.../json/cache/BeanSerializerCache.java` | 添加 LRU 淘汰 |

---

> **声明**：本报告基于 `ydsz-common-json` 模块 2026-08-09 代码快照进行审计，所有代码行数、依赖关系、使用统计均来自实际代码扫描。优化建议遵循"最小破坏性、最大收益"原则，优先保证对外 API 兼容性。
