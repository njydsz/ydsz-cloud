# ydsz-common-json 过度设计评估报告

> 对标：Jackson 2.x（Spring 默认）、FastJSON2（阿里）、Gson（Google）
> 参照规范：阿里巴巴 Java 开发手册、互联网大厂中间件研发规范
> 评估范围：`ydsz-backend/ydsz-common/ydsz-common-json`（115 文件 / ~29,530 行）
> 评估日期：2026-08-03

---

## 一、核心判断

**该模块存在明显的过度设计——功能广度对标 Jackson ~70%，但实际业务使用率严重不均衡，约 40% 的代码量（~12,000 行）服务于零或极低频使用的功能。同时，运行时 ASM 字节码生成这一最大复杂度来源的投入产出比未经验证。**

| 维度 | 评分 | 结论 |
|---|---|---|
| 功能广度对标 | ★★★★☆ | 注解/树模型/Schema/Patch/Pointer/Path/Merge/Module，广度对标 Jackson ~70% |
| 业务匹配度 | ★★☆☆☆ | 核心序列化高频使用（~140+ 文件），高级特性（Schema/Path/Patch/Pointer/Merge）几乎零使用 |
| 工程复杂度 | ★★☆☆☆ | ASM 字节码生成（~3000+ 行）+ 6 级反序列化降级 + 3 套注册表 + 2 套树模型 |
| 投入产出比 | ★★☆☆☆ | ASM 50 倍性能宣称无基准验证，Schema 缺 40% 关键字，JsonPath 缺标准函数 |
| 健康度 | ★★★☆☆ | 4 个测试（修复后），关键 bug 已修，但测试/代码比 = 1:7375 |
| 依赖管控 | ★★☆☆☆ | L2 工具层反向依赖 Spring Boot、Micrometer、Reactive Streams、Jakarta Validation |

**总判断：该模块像一个"对标 Jackson API 表面积 + FastJSON 性能目标"的雄心项目，但摊子铺得太大，大量高级功能在业务中无使用场景，核心性能主张（ASM 50 倍加速）缺乏 JMH 基准数据佐证。建议做减法——裁撤零使用的高级功能，聚焦核心序列化体验，用数据而非宣称建立性能信用。**

---

## 二、体量与使用率分析

### 2.1 代码体量分布

| 功能类别 | 文件数 | 代码行数（估） | 外部使用量 | 投入产出评级 |
|---|---|---|---|---|
| 核心序列化（reader/writer/provider） | 18 | ~8,000 | 极高（140+ 文件） | ★★★★★ |
| ASM 字节码生成 | 6 | ~3,000 | 内部自动启用 | ★★☆☆☆ |
| 注解定义（29 个） | 29 | ~700 | 中（35+ 文件） | ★★★★☆ |
| 树模型（tree） | 9 | ~1,500 | 低 | ★★☆☆☆ |
| 树模型（object，已 @Deprecated） | 2 | ~1,627 | 中（15 文件仍引用） | ★☆☆☆☆ |
| Spring 集成 | 6 | ~1,200 | 高（自动配置） | ★★★★★ |
| 零拷贝反序列化 | 2 | ~1,500 | 内部降级路径 | ★★☆☆☆ |
| JsonPath | 1 | ~673 | 极低（2-3 文件） | ★☆☆☆☆ |
| JSON Schema | 3 | ~920 | 极低（1 文件） | ★☆☆☆☆ |
| JSON Patch/Pointer/Merge | 3 | ~600 | 极低（1-2 文件） | ★☆☆☆☆ |
| Module 系统 | 4 | ~900 | 极低 | ★☆☆☆☆ |
| 异常/类型/命名/数字工具 | 7 | ~800 | 中 | ★★★★☆ |
| 缓存系统 | 6 | ~800 | 内部 | ★★★☆☆ |
| AutoType 安全 | 2 | ~900 | 自动启用 | ★★★★☆ |
| 监控/健康检查 | 5 | ~500 | 自动 | ★★★☆☆ |
| 其他（配置/SPI/stream） | 12 | ~2,000 | 内部 | ★★★☆☆ |

### 2.2 "高频使用"与"零使用"两极分化

**核心高频路径**（业务刚需，设计合理）：
- `YdszJson.toJson()` / `toObject()` → 140+ 文件直接引用
- `@JsonProperty` / `@JsonIgnore` / `@JsonFormat` 注解 → 35+ 实体类使用
- `JsonHttpMessageConverter` → Spring MVC 自动配置，所有 Controller 透明使用
- `JsonAutoConfiguration` + `JsonProperties` → 统一配置入口

**零/极低使用路径**（过度设计嫌疑）：
- `JsonPath` → 仅 3 个业务文件引用（workflow 表单引擎、config 合并），673 行代码
- `JsonSchema` / `JsonSchemaValidator` → 仅 1 个业务文件（workflow 表单校验），920 行代码
- `JsonPatch` → 仅 1 个文件（audit diff 报告），但内部 JSON Patch 可能根本不需要
- `JsonPointer` → 仅 patch 内部使用
- `JsonMergePatch` → 仅 config 热更新 merge 使用
- `JsonMapper` → Builder API（879 行），仅 3 个文件使用，所有人直接用 `YdszJson` 静态方法
- `JsonModule` 体系 → 3 套注册表 + 1 个 Registry，业务模块几乎无自定义 Module
- `object/JsonObject` + `object/JsonArray` → 已标记 `@Deprecated`，但 15 个业务文件仍引用，1,627 行死代码待清理

---

## 三、行业对标：哪里过度了

### 3.1 ASM 字节码生成——最大复杂度来源

| 对比维度 | ydsz-common-json | Jackson 2.x | FastJSON2 | Gson |
|---|---|---|---|---|
| 运行时字节码生成 | ✅ ASM (1788 行核心) | ❌ 不做运行时生成 | ✅ ASM（10 年打磨） | ❌ 纯反射 |
| 配套基础设施 | Metaspace 监控、类阈值降级、GraalVM 检测、预热 Runner、native-image.json | 无（编译期 Annotation Processor） | IdentityHashMap 缓存 | 无 |
| 性能验证 | **无 JMH 基准数据** | 有完整基准测试 | 有完整基准测试 | 有完整基准测试 |
| GraalVM 兼容 | 需额外降级逻辑 | 天然兼容 | 需额外处理 | 天然兼容 |

**判断**：运行时 ASM 字节码生成是 Jackson 和 Gson 都刻意避免的技术路线——Jackson 的顶级性能来自 JIT 友好的反射代码 + 编译期注解处理器，而非运行时字节码生成。FastJSON2 做了 ASM 但经历了 10 年迭代才稳定。该模块用 ~3,000 行代码 + 6 项配套基础设施来实现一个**无基准数据佐证**的性能路径，投入产出比存疑。

### 3.2 功能对标——"别人有，我也要有"

Jackson 作为 20 年历史的 JSON 库，其功能广度是逐步演进的。该模块在 v1.0 就试图覆盖：
- Schema（Jackson 有独立模块 `jackson-module-jsonSchema`）
- JsonPath（Jackson 不内置，Jayway 是独立库）
- JSON Patch / Merge Patch（Jackson 不内置，有独立库 `json-patch`）
- JsonPointer（Jackson 内置但使用率极低）
- Module 系统（Jackson 核心设计，但业务模块无自定义需求）

**判断**：这些功能在 Jackson 生态中要么是独立可选模块，要么在绝大多数项目中从不使用。一个内部团队自研的 JSON 库不需要在 v1.0 就覆盖这些。

### 3.3 反序列化路径——6 级降级过度

```
ASM → BeanReader → Creator → Builder → ZeroCopy → Map
```

- Jackson 的反序列化核心路径：`BeanDeserializer`（一种）+ `@JsonCreator` 静态工厂
- Gson：`ReflectiveTypeAdapterFactory`（一种）
- ydsz-common-json：6 级降级、3 套独立的引擎实现

每增加一级降级路径，就意味着新增一套代码需要测试、维护、debug。而且降级路径都是 `catch Exception` 无日志静默降级——出问题时根本不知道走了哪条路径。

### 3.4 依赖方向倒置

```
标准分层：      业务模块 → L2 工具层（不依赖框架）
实际现状：      业务模块 → ydsz-common-json → Spring Boot / Micrometer / Web / Reactive
```

一个 L2 工具层模块反向依赖了 Spring Boot Autoconfigure、Spring Web、Micrometer、Reactive Streams、Jakarta Validation，导致：
- 非 Spring 项目无法使用（失去"零外部依赖"的核心卖点）
- 版本升级时耦合度极高（Spring Boot 4.x health 包迁移已造成依赖碎片：同时依赖 actuator 和 health 两个包）
- Jackson 的成功恰恰在于 **core 模块零框架依赖**，其他按需可选

---

## 四、已修复与未修复项（基于 2026-08-02 分析文档对照）

### 已修复 ✅

| 序号 | 问题 | 状态 |
|---|---|---|
| F1 | ASM 类名前缀检查 bug：`generated.` → 后缀检查 `_ASM_Serializer` / `_ASM_Deserializer` | ✅ 已修复（Javadoc 标注旧常量为废弃） |
| F7 | 0 测试覆盖 → 4 个测试（AsmEnabledTest、AutoTypeSecurityTest、YdszJsonBasicTest、TestBean） | ✅ 已补充 |
| E2 | `JsonTypeCode` 死枚举 → 已删除 | ✅ |
| E2 | `ObjectReader` 废弃 → 已删除 | ✅ |
| D1 | `wrapRootValue` Builder 链遗漏 → 已补 | ✅ |
| D3 | HealthIndicator `safeMode=false` 判 DOWN → 已改为 UP + warning detail | ✅ |
| E2 | `getOrderedModules()` → 已删除 | ✅ |
| A3 | `object/JsonObject`、`object/JsonArray` → 已标注 `@Deprecated` | ⚠️ 已标注但未移除 |

### 未修复 / 新发现 ❌

| 序号 | 问题 | 严重度 |
|---|---|---|
| N1 | ASM 性能宣称仍无 JMH 基准数据——README "字段访问性能提升 50 倍"从未被验证 | P0 |
| N2 | 高级功能零使用却占据 ~40% 代码量——Schema/Patch/Pointer/Merge/Module 无业务需求支撑 | P1 |
| N3 | `object/JsonObject` + `object/JsonArray` 共 1,627 行，已标记 `@Deprecated` 但 15 个业务文件仍引用，未清理引用方 | P1 |
| N4 | 6 级反序列化降级中每条 `catch Exception` 无日志——调试黑洞 | P1 |
| N5 | L2 工具层反向依赖 Spring Boot / Micrometer / Web / Reactive | P1 |
| N6 | 4 个测试覆盖 ~29,530 行代码，测试/代码比 = 1:7375（行业标准 ≥ 1:50） | P1 |
| N7 | `namingStrategy` 是接口类型，Spring Boot 字符串绑定需 Converter（未提供） | P1 |
| N8 | `monitoringEnabled` = `System.setProperty` 后无代码读取，死配置 | P2 |
| N9 | `PATTERN_CACHE` / `FORMATTER_CACHE` / `SerializerCache` 均为无界 CHM | P2 |
| N10 | `StringInterner` Javadoc 声称 LRU 淘汰，实际无淘汰逻辑 | P2 |
| N11 | `@JsonFormat.locale()` / `timezone()` 声明但未实现 | P2 |
| N12 | HealthIndicator import `org.springframework.boot.health.contributor.Health`——疑似自定义包或拼写错误 | P3 |
| N13 | `JsonObject.put()` 返回 `this` 违反 `Map.put()` 契约 | P3 |

---

## 五、可落地的优化建议

### 阶段一：止血（P0，2 周内）

#### 1. ASM 性能基准验证 → 决定保留或移除
- **行动**：用模块已声明的 JMH 依赖，编写 3-5 个基准测试对比 ASM vs 反射路径 vs Jackson vs FastJSON2 的序列化/反序列化吞吐量
- **决策门**：
  - 若 ASM 相对反射提升 < 30% → **整体移除 ASM 子系统**（~3,000 行），改用 `MethodHandle`/`VarHandle` + 字段缓存
  - 若 ASM 相对反射提升 > 50% 且接近 FastJSON2 → 保留，但移除 Metaspace 监控/类阈值降级/GraalVM 降级（实际项目中不太可能生成 5,000+ 个 ASM 类）
- **对标**：Jackson 不做运行时 ASM 性能仍然顶级

#### 2. 补充核心路径测试（目标：测试/代码比 ≥ 1:200）
- 序列化正确性：基本类型、Bean、泛型、嵌套、日期、枚举、循环引用（至少 30 个用例）
- 反序列化正确性：同上（至少 30 个用例）
- 边界条件：null、空字符串、超大数字、超深嵌套、超长 JSON
- AutoType 安全：补充分析文档 F5 的 3 个绕过用例

### 阶段二：减负（P1，1 个月内）

#### 3. 裁撤零使用的高级功能

| 功能 | 行数 | 行动 | 理由 |
|---|---|---|---|
| JsonPath | ~673 | 移出为独立可选模块 `ydsz-common-jsonpath` | 仅 2-3 个业务文件使用，非核心 |
| JSON Schema | ~920 | 移出为独立可选模块 `ydsz-common-jsonschema` | 仅 1 个业务文件使用，且仅覆盖 60% Draft 07 |
| JSON Patch / Merge Patch | ~400 | 移出为独立可选模块 `ydsz-common-jsonpatch` | 仅 1-2 个业务文件使用 |
| JSON Pointer | ~200 | 合并入 jsonpatch 模块（仅 patch 使用） | 无独立使用场景 |
| Module 系统 | ~900 | 降级为 SPI 单接口，删除 3 套注册表中的 2 套 | 业务模块无自定义 Module 需求 |
| object/JsonObject + JsonArray | ~1,627 | **彻底删除**，迁移 15 个引用方到 tree/*Node | 已 @Deprecated，不能再拖 |

- **迁移原则**：被移出的功能在独立模块中保持 API 兼容，业务模块按需引入
- **预期效果**：核心模块从 115 文件 / 29,530 行缩减到 ~70 文件 / ~17,000 行

#### 4. 收敛反序列化路径
- 确定主路径：ASM（如果有）→ BeanReader（反射 + MethodHandle）
- 其余降级路径合并为统一的 `FallbackDeserializer`，加 WARN 日志
- 每条降级必须 log 原因和 Bean 类型（当前全部 `catch Exception` 静默）

#### 5. 拆解 Spring 依赖
- 核心模块（`ydsz-common-json-core`）：仅依赖 Lombok + ASM(optional) + SLF4J(optional)
- Spring 集成模块（`ydsz-common-json-spring`）：依赖 spring-boot-autoconfigure + spring-web + micrometer
- 健康检查/指标监控 → 移入 spring 集成模块
- 对标 Jackson：`jackson-core`（零框架依赖）、`jackson-databind`、`jackson-module-*`

#### 6. 修复配置绑定
- 注册 `String → PropertyNamingStrategy` Converter，或 `JsonProperties.namingStrategy` 改为 `String` 类型

### 阶段三：打磨（P2-P3，持续）

#### 7. 缓存安全加固
- `PATTERN_CACHE` / `FORMATTER_CACHE` / `SerializerCache` → 加 Caffeine 或 LinkedHashMap LRU 上限
- `StringInterner` → 要么实现 LRU 淘汰，要么修正 Javadoc

#### 8. 死配置清理
- `monitoringEnabled` → 从配置/文档中移除，或真正接入 `JsonMetrics`
- `@JsonFormat.locale()` / `timezone()` → 实现或标注 `UnsupportedOperationException` + Javadoc

#### 9. 文档与代码一致性
- README 中 `@YdszJsonClass` → `@JsonClass`
- `LOWER_UNDERSCORE` / `UPPER_SNAKE_CASE` → 删除不存在的命名策略常量
- `JsonObject.put()` → 对齐 `Map` 契约或重命名为 `fluentPut()`

---

## 六、目标架构

```
ydsz-common-json-core/          (~70 文件, ~17,000 行)
  ├── 核心序列化/反序列化 (reader/writer/provider)
  ├── 注解定义 (annotation/*)
  ├── 树模型 (tree/*)
  ├── ASM 加速 (asm/*) — 仅当基准验证通过
  ├── AutoType 安全 (autotype/*)
  ├── 零拷贝/缓存 (bytecode/cache)
  ├── 类型系统 (type/naming/number)
  └── 异常体系 (exception/*)
  依赖：Lombok, ASM(optional), SLF4J(optional)

ydsz-common-json-spring/        (~10 文件, ~2,000 行)
  ├── JsonAutoConfiguration
  ├── JsonHttpMessageConverter
  ├── JsonProperties
  ├── JsonMetrics / JsonHealthIndicator
  └── JsonWarmupRunner
  依赖：spring-boot-autoconfigure, spring-web, micrometer(optional)

ydsz-common-jsonpath/           (独立可选, ~673 行)
ydsz-common-jsonschema/         (独立可选, ~920 行)
ydsz-common-jsonpatch/          (独立可选, ~600 行)
```

---

## 七、结论

该模块的核心序列化能力（`YdszJson.toJson/toObject` + 注解 + Spring 集成）是扎实且有业务价值的，140+ 文件的高频使用证明了这一点。问题在于：

1. **摊子铺太大**——在 v1.0 就试图覆盖 Jackson 20 年积累的全部 API 表面积，导致 ~40% 代码服务于零使用场景
2. **核心性能主张未经证实**——ASM 50 倍加速的宣称没有基准数据支撑，这是最危险的"过度设计"
3. **依赖方向倒置**——L2 工具层反向依赖 Spring 全家桶，违反分层原则
4. **测试严重不足**——29,530 行代码仅 4 个测试

**建议按三阶段推进**：先用 JMH 基准决定 ASM 去留（P0），再把零使用高级功能拆为独立可选模块（P1），最后做缓存加固和文档对齐（P2-P3）。最终目标是：核心模块精简到 ~17,000 行，零框架依赖，每个公开 API 都有测试守护，性能数据可复现。
