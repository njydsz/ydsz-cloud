# ydsz-common-json 深度优化分析报告

> 分析对象：`ydsz-common-json`（自研 JSON 引擎，替代 Jackson/Fastjson 的内部轮子）
> 分析日期：2026-08-09
> 方法：核心链路静态走读（序列化 / 反序列化 / 解析 / 安全 / Spring 集成）+ 关键缺陷代码实证
> 标杆参照：Jackson、Fastjson2、Gson，以及大厂研发规范（单测门禁、性能基线与灰度）

---

## 0. 模块定位与现状关键信号

| 指标 | 现状 | 行业标杆 |
| --- | --- | --- |
| 代码规模 | ~2.4 万行手写引擎（JSONWriter 1286 / SerializationProvider 1332 / JsonParserUtil 1261 / JSONReader 1172 / ValueWriter 1054 等） | Jackson 模块化、单一职责；Fastjson2 亦高度模块化 |
| 依赖面 | **全 monorepo 318 个文件**引用，0 文件再用 Jackson/Fastjson（已被完全替换） | 核心库通常仅少量模块强依赖 |
| 单元测试 | **核心模块 0 单测**；全仓库仅 **16 个测试文件** | 大厂核心库覆盖率 >70%，CI 门禁硬性卡点 |
| 性能基线 | 无 JMH / 无基准数据 | 性能敏感库必有基准与回归看板 |
| AutoType 安全 | 默认关闭 + 启动期白名单扫描（优于 Fastjson 历史 denylist） | Jackson 默认关闭多态；Fastjson2 亦改白名单 |

**核心结论**：这是一套被全员强依赖、却零测试守护的自研引擎。任何正确性/性能缺陷的**爆炸半径覆盖整个业务系统**，因此"补测试 + 修正确性"的优先级远高于"加功能"。

---

## 1. 现状核心问题（按严重度）

### P0 —— 严重缺陷（正确性 / 安全 / 可用性）

#### ① 反序列化深度限制"声明未落地" → 栈溢出 DoS
- `JSONReader` 声明了 `DEFAULT_MAX_DEPTH = 256`（`reader/JSONReader.java:36`）与 `private static volatile int maxDepth`（`:238`）、`getMaxDepth()`（`:362`）。
- 但**递归主路径完全没有 depth 校验**：
  - `readObjectMap()`（`:1153`）→ `readAnyValue()`（`:1130`）→ `readObjectMap()` 形成无限递归，无 depth 计数器、无 `> maxDepth` 判定。
  - `readArray()`（`:1102`）→ `readArrayElement()`（`:1119`）→ `readAnyValue()` 同理。
  - 全文件唯一的 `depth` 变量（`:658/667/1042/1051`）是**skip 辅助方法里的局部计数器**，仅用于跳过嵌套，并非递归守卫。
- 危害：构造 `{{{{...}}}}` 深嵌套 JSON 即可触发 `StackOverflowError`，属典型拒绝服务。
- 修复：在 `readObjectMap` / `readArray` 递归入口处传入并自增 depth，超过 `effectiveMaxDepth()` 立即抛 `JsonDeserializationException`；并补 JMH/边界测试。

#### ② 继承字段丢失（不遍历父类）→ 实体基类字段静默缺失
- 序列化入口 `SerializationProvider.tryBeanSerialize` 调用 `FieldMetadataLoader.loadFields(clazz)`（`:959/1050`）。
- `loadFields`（`provider/FieldMetadataLoader.java:88`）与反序列化 `BeanReader` 构造函数（`:76`）**均只调用 `clazz.getDeclaredFields()`，不遍历父类**。
- 项目实体普遍继承 `MpBaseEntity`（含 `id / createTime / updateTime` 等），若需将这些字段输出/回填到 JSON，会被整体丢弃。
- 修复：在 `loadFields` 中沿 `getSuperclass()` 递归收集父类 `FieldMeta`（跳过 `Object`），`BeanReader` 同理；并评估现有接口输出是否因该缺陷而"恰好不需要"基类字段——若是，则修复会改变接口契约，需灰度 + 兼容处理。

#### ③ 数字解析指数上界未防护 → 大指数 AIOOBE
- `parser/JsonParserUtil.java`：`POW10` 仅 24 项（`:52`），但指数解析循环仅校验 `exp < 0`（`:466`），**未校验上界**。
- 在 double 路径（非 BigDecimal）`value = expNegative ? value / POW10[exp] : value * POW10[exp]`（`:473`），当 `exp >= 24`（如 `1e30`）直接 `ArrayIndexOutOfBoundsException`。
- 修复：解析指数后若 `exp >= POW10.length` 或在 BigDecimal 关闭时，统一回退到 `BigDecimal`/`Double.parseDouble(numStr)` 字符串解析（溢出分支已有此逻辑，可复用）。

#### ④ JSONWriter 转义缺失 U+2028 / U+2029 与孤立代理 → 嵌入 JS 场景失败
- `writer/JSONWriter.writeStringWithEscape`（`:711`）仅处理 `" \ n r t b f` 及 `c < ' '` 控制字符。
- **未处理 U+2028（行分隔符）/ U+2029（段落分隔符）**：二者在 JSON 合法，但在 `<script>` 内 JS 字符串字面量中非法，会导致页面解析失败 / 潜在的 XSS 注入面。
- **未校验孤立代理对（lone surrogate）**：非法 UTF-16 会被原样写入，产出下游无法解码的字符串。
- 修复：增加可选 `ESCAPE_FOR_JS` 模式（转义 `/`、U+2028/2029 为 `\u2028/\u2029`）；写入前校验代理对合法性。

### P1 —— 一般问题

#### ⑤ 配置 API 设计：static setter 写 ThreadLocal，配置非实例级（与 Jackson 范式冲突）
- `SerializationProvider.setWriteNulls/setPrettyPrint/setDateFormat`（`:262/280/338`）实际写入 `SerializationContext.CONTEXT.get()`（ThreadLocal）。
- 问题：配置是"线程隐式状态"而非"对象持有"，同一线程顺序处理两个不同配置的 `JsonMapper` 会互相污染；与行业主流 `ObjectMapper`（不可变、实例级配置）范式相悖，易踩坑且难调试。
- 修复：将配置收归 `JsonMapper` 实例持有（不可变配置对象），弃用全局 static 副作用 setter。

#### ⑥ 解析链路多层抽象，职责边界模糊
- 存在 `JSONReader`（流式读取）+ `JsonParserUtil`（工具/数字解析）+ `JsonParser`（497 行，被 8 个类引用）三层，命名与职责重叠，维护与排障成本高。
- 建议：明确分层（流式词法读取器 / 对象映射器），收敛重复能力，删除未被实际调用的冗余实现。

#### ⑦ 异常静默降级，字段静默丢失难排查
- `FieldMetadataLoader.loadFields`（`:211`）、`BeanReader` 构造函数（`:99`）等多处 `catch (Exception e) { // skip }` 空处理。
- 反射失败 / 字段不可访问时直接跳过，业务侧拿到"缺字段"的对象而无任何告警。
- 修复：至少 `LOGGER.warn` 记录被跳过的字段；非预期失败应抛出而非静默吞掉。

#### ⑧ 无测试、无基准、无 CI 门禁
- 核心模块 0 单测，全仓库仅 16 测试文件；无 JMH 基线，无法量化"自研是否真的更快"。
- 修复：先建 Round-Trip 正确性测试（对照 Jackson 输出）+ 解析/序列化 JMH 基线 + 安全测试（深嵌套、超大数、恶意输入）。

### P2 —— 过度设计 / 可精简

#### ⑨ 两套字段元数据加载逻辑，行为可能不一致
- 序列化走 `FieldMetadataLoader.loadFields`（含 `@JsonIgnore/@JsonInclude/@JsonProperty/@JsonPropertyOrder` 等丰富处理）；反序列化 `BeanReader` 自写一套 `getDeclaredFields` 循环（注解处理较弱）。
- 同一字段在序列化/反序列化两侧可能被不同规则处理，产生"能写不能读/能读不能写"的隐性不一致。
- 修复：统一为单一 `FieldMetaLoader`，序列化与反序列化共用同一份元数据。

#### ⑩ 命名策略 / 缓存分散在多个 ThreadLocal 与 ConcurrentMap
- `NAMING_STRATEGY`（FieldMetadataLoader）、`CUSTOM_SERIALIZER_CACHE`、`BEAN_SERIALIZER_INFO_CACHE`（SerializationProvider）等散布，生命周期与清理（`.remove()`）易遗漏，存在线程复用导致的残留配置风险。
- 修复：配置与缓存随 `JsonMapper` 实例生命周期管理，减少 ThreadLocal 横切。

---

## 2. 分维度可落地优化建议

### 架构优化
1. **统一字段元数据加载**：单一 `FieldMetaLoader` 同时服务序列化/反序列化，补父类遍历（解决 ②⑨）。
2. **配置模型重构**：`JsonMapper` 持有不可变配置，弃用 static ThreadLocal 副作用 setter（解决 ⑤⑩）。
3. **解析链路收敛**：明确"流式读取器 / 对象映射器"分层，消除功能重叠与死代码（解决 ⑥）。
4. **插件化扩展点**：序列化/反序列化自定义以 `Module` 形式注册（仿 Jackson `SimpleModule`），而非散落的 `CUSTOM_SERIALIZER_CACHE`。

### 功能增强
1. **安全护栏落地**：读/写均生效的深度限制、超大数组/字符串长度上限（内存放大防护）。
2. **转义完善**：U+2028/2029、代理对校验、可选 HTML/JS 转义模式（解决 ④）。
3. **Jackson 注解兼容补全**：`@JsonInclude`、`@JsonFormat`（日期/时区）、`@JsonSubTypes`（多态类型信息）、`@JsonAlias`。
4. **流式/大文件处理**：支持 `InputStream` / `Reader` 直读，避免整段 `char[]` 常驻，支撑大报文场景。
5. **错误信息增强**：解析失败携带 `offset / line / column`，提升排障效率（解决 ⑦ 体验侧）。

### 性能提升
1. **建立 JMH 基线**：量化 vs Jackson / Fastjson2，用数据决策"自研是否达标"。
2. **缓冲区与 ThreadLocal 回收策略复核**：确认 `MAX_BUF_SIZE` 上限下 `ensureCapacity`（`:775`）在超大字符串时不会因"封顶后仍不足"导致越界写（建议不足时显式抛异常而非静默截断）。
3. **反射加速**：字段访问优先 `MethodHandle`；若注释宣称 ASM/字节码生成，应**实装或删除宣称**（避免注释与实现不符）。
4. **数字快速路径**：已知范围整型走 `NumberUtils.parseIntFromChars/parseLongFromChars`，避免中间 `String` 与装箱。

### 体验改善
1. **配置 API 友好化**：提供 `JsonMapper.builder().writeNulls(true).prettyPrint(false).build()` 链式构建。
2. **文档与迁移指南**：明确"已支持 / 不支持"特性清单、与 Jackson 的差异、迁移注意点。
3. **诊断可观测**：首次遇到未支持特性时给出明确 WARN/异常，而非静默跳过。

### 过度设计收敛
1. 消除序列化/反序列化双份字段加载逻辑（⑨）。
2. 清理未被实际调用或仅留声明的抽象层、修正注释与实现不符之处（⑩）。
3. 统一命名策略与缓存的存放与清理，降低 ThreadLocal 残留风险。

---

## 3. 落地路线图（按优先级与节奏）

**Phase 0 — 止血（正确性 + 安全 + 测试基座，1~2 周）**
- 修 ①④（深度限制、转义）→ 直接堵 DoS / 注入面
- 修 ②③（父类字段、指数上界）→ 堵数据正确性
- 建核心 Round-Trip + 安全测试套件（对照 Jackson），CI 卡门禁

**Phase 1 — 架构收敛（2~4 周）**
- 配置模型重构（⑤⑩）、解析链路收敛（⑥）、统一字段元数据（⑨）
- Jackson 注解兼容补全、错误信息增强（⑦）

**Phase 2 — 性能与体验（持续）**
- JMH 基线 + 优化（反射加速 / 流式 / 缓冲策略复核）
- 文档、迁移指南、诊断可观测

---

## 4. 风险提示

- **改动 JSON 核心影响 318 个文件**：任何行为变更必须配回归测试 + 灰度发布；建议先在隔离分支用全量接口契约测试（golden file）比对输出。
- **父类字段修复（②）可能改变现有接口输出**：需先确认业务是否"依赖当前缺失基类字段"的行为；若是，修复会引入契约变更，需评估兼容与灰度。
- **无基准即谈性能优化是空谈**：Phase 2 之前务必先有 JMH 数据，避免"为优化而优化"引入新 bug。

---

## 附：实证证据索引

| 问题 | 文件:行 | 关键证据 |
| --- | --- | --- |
| ① 深度限制未落地 | `reader/JSONReader.java:36,238,362,1153,1130,1102` | 递归路径无 depth 校验；仅 skip 辅助方法含局部 depth 计数器 |
| ② 继承字段丢失 | `provider/FieldMetadataLoader.java:88,154`；`reader/BeanReader.java:76` | 仅 `getDeclaredFields()`，不遍历 `getSuperclass()` |
| ③ 指数越界 | `parser/JsonParserUtil.java:52,466,473` | `POW10[24]`，指数仅查 `exp<0`，double 路径 `POW10[exp]` 越界 |
| ④ 转义缺失 | `writer/JSONWriter.java:711-764` | 仅处理基础转义，无 U+2028/2029、无代理对校验 |
| ⑤ 配置 API | `provider/SerializationProvider.java:262,280,338` | static setter 写 `SerializationContext.CONTEXT`(ThreadLocal) |
| ⑦ 静默吞异常 | `provider/FieldMetadataLoader.java:211`；`reader/BeanReader.java:99` | `catch(Exception e){ // skip }` |
| ⑧ 零测试 | 全仓库 `find -name *Test*.java` = 16；核心模块 0 | 依赖面 318 文件 |
| 依赖面 | 全仓库 grep | 318 文件引用，0 文件用 Jackson/Fastjson |
