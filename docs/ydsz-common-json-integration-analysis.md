# ydsz-common-json 全局引用与集成贯通度分析

> 生成日期：2026-08-03
> 文档版本：v1.1.0（已全部实施）
> 分析范围：ydsz-backend 全仓库（10 个部署单元 + ydsz-common 30 个子模块）
> 分析视角：**调用方视角** —— 其他模块与 JSON 公共模块的集成质量、能力利用率、贯通断点
> 实施状态：✅ 阶段一/二/三全部完成 | ⏳ 阶段四 P2-1 待引擎选型决策

## 与已有文档的关系

本仓库已有两份 `ydsz-common-json` 相关文档，本文与之**互补而非重复**：

| 文档 | 视角 | 核心问题 |
|---|---|---|
| `ydsz-common-json-optimization-report.md` | 模块内部 | 模块本身的架构/功能/性能怎么优化 |
| `ydsz-common-json-overdesign-assessment.md` | 战略选型 | 该不该自研这么多能力（推荐冻结自研） |
| **本文** | **调用方集成** | **其他模块用得对不对、够不够、有没有绕过公共能力** |

**重要前提**：本文识别的集成缺陷（尤其是 P0 手工拼接 JSON）**与引擎选型无关**。无论未来是否按过度设计报告的建议迁移到 Jackson，这些问题都必须先修——它们是调用方代码的缺陷，换引擎不会自动消失，反而会在迁移时被放大。

---

## 一、执行摘要

### 1.1 总体评价

**集成广度优秀，集成深度不足，局部存在正确性缺陷。**

`ydsz-common-json` 作为统一 JSON 底座已经**完成了最难的一步**——全仓库 Jackson / Fastjson / Gson **零残留**，215 个业务文件、47 个 Maven 模块统一收敛到 `YdszJson` 单一入口。这个替换彻底度在同类改造中属于优秀水平，说明基础推广是成功的。

但从集成质量看，存在三个层次的问题：

1. **能力利用率约 24%**：21 项核心能力中，仅 5 项被广泛使用（`toJson` / `parseMap` / `toObject` / `parseArray` / `readTree`），8 项**零引用**。业务侧 90% 的调用集中在最基础的两个方法上。
2. **存在绕过公共能力的手工拼接**：6 处代码直接用字符串拼接生成 JSON，其中 **4 处涉及未转义的外部输入**，会产生非法 JSON 或字段注入——这是真实缺陷，不是风格问题。
3. **治理层贯通缺失**：`JsonSchema` 结构校验、`JsonPatch` 增量补丁、`JsonPointer` 路径定位这类"治理型"能力零使用，而业务侧同时存在大量手写校验逻辑，属于典型的"公共能力未被复用"。

### 1.2 关键量化指标

| 维度 | 数值 | 评价 |
|---|---|---|
| 声明依赖的 Maven 模块 | 47 个 | 覆盖全部 9 大业务域 |
| 实际引用的 Java 文件 | 215 个 | 广度充分 |
| 外部 JSON 库残留 | **0 处** | 迁移彻底，优秀 |
| 核心能力利用率 | **5 / 21 ≈ 24%** | 偏低 |
| 声明依赖但零使用的模块 | **7 个** | 依赖冗余 |
| 隐式传递依赖的模块 | **1 个** | 声明缺失 |
| 手工拼接 JSON 的位置 | **6 处**（4 处含未转义输入） | **P0 缺陷** |
| 实现 `JsonModule` SPI 的模块 | **2 / 9** | 扩展机制未普及 |
| 服务级配置项一致性 | 9 个服务、5 种配置组合 | 无统一基线 |

---

## 二、集成现状全景

### 2.1 集成深度分级

按模块外引用文件数分级：

| 层级 | 引用文件数 | 模块 | 特征 |
|---|---|---|---|
| **深度集成** | 18-31 | `workflow-server`(31)、`message-server`(30)、`cronjob-server`(18) | 流程数据、消息载荷、任务参数重度依赖 JSON |
| **中度集成** | 5-11 | `gateway`(11)、`common-notify`(9)、`common-safe`(8)、`common-socket`(8)、`agent-domain`(8)、`agent-infra`(8)、`literule-server`(7)、`common-auth`(6)、`common-redis`(5)、`common-util`(5)、`common-jdbc`(5)、`common-file`(5)、`common-audit`(5) | 框架适配层与中间件集成 |
| **浅度集成** | 1-4 | `common-domain`(4)、`common-core`(3)、`common-queue`(3)、`common-web`(3)、`common-base`(3)、`project-server`(3) 等 22 个模块 | 仅基础序列化 |
| **零使用** | 0 | `common-app`、`common-docs`、`common-exception`、`cronjob-domain`、`literule-domain`、`message-domain`、`nextwiki-domain` | **依赖冗余** |

### 2.2 四层贯通度评估

| 层级 | 内容 | 状态 | 证据 |
|---|---|---|---|
| **L1 基础序列化层** | `toJson` / `toObject` / `parseMap` | ✅ 全域贯通 | 215 文件覆盖 9 大域 |
| **L2 框架适配层** | Redis / Feign / MyBatis / Netty / WebSocket / HTTP | ✅ 基本贯通 | 6 个适配器实现完备 |
| **L3 SPI 扩展层** | `JsonModule` 自定义序列化器注册 | 🟠 局部贯通 | 仅 `agent`、`safe` 两模块 |
| **L4 数据治理层** | Schema 校验 / Patch / Diff / 流式生成 | ❌ 基本空白 | 0 引用 |

**L2 值得肯定**：适配器层设计得当，且没有出现"各模块自建 JsonUtils"的重复造轮子现象。全仓库未发现任何私有 JSON 工具类，所有集成都通过标准适配器（`YdszJsonRedisSerializer`、`JsonEncoder/Decoder`、`JsonTypeHandler`、`JsonMessageCodec`、`JsonMessageSerializer`、`JsonHttpMessageConverter`）完成。这是架构治理的成果，应保持。

**L3/L4 是贯通断点**，也是"公共能力未充分利用"的主要体现。

---

## 三、P0 缺陷清单（必须立即修复）

### P0-1 手工拼接 JSON 且未转义外部输入（4 处）

这是本次分析发现的**最严重问题**。以下代码绕过 `YdszJson`，直接用字符串拼接构造 JSON，且拼入的内容来自外部输入，未做任何转义。

#### 缺陷点 1：审批意见注入

**位置**：`ydsz-cronjob/ydsz-cronjob-server/.../core/dag/DagInstanceControlService.java:317`

```java
// 现状：comment 为用户提交的审批意见，直接拼接
String resultJson = comment != null ? "{\"comment\":\"" + comment + "\"}" : null;
```

**影响**：
- 用户输入包含 `"` 或 `\` → 生成**非法 JSON**，后续 `parseMap` 解析抛异常，审批结果无法读取
- 用户输入 `x","injected":"y` → **字段注入**，污染 DAG 节点结果数据
- 输入含换行/制表符等控制字符 → 同样产生非法 JSON

**修复**：
```java
String resultJson = comment != null
        ? YdszJson.toJson(Map.of("comment", comment))
        : null;
```

#### 缺陷点 2、3：BPMN 候选人/候选组注入

**位置**：`ydsz-workflow/ydsz-workflow-server/.../engine/BpmnXmlParser.java:311` 与 `:327`

```java
node.setExt("{\"candidateUsers\":\"" + candidateUsers + "\"}");
node.setExt("{\"candidateGroups\":\"" + candidateGroups + "\"}");
```

**影响**：`candidateUsers` / `candidateGroups` 来自上传的 BPMN XML 属性，属于**外部不可信输入**。含引号的用户名会导致流程定义 `ext` 字段损坏，进而使流程节点权限解析失败。

**修复**：
```java
node.setExt(YdszJson.toJson(Map.of("candidateUsers", candidateUsers)));
node.setExt(YdszJson.toJson(Map.of("candidateGroups", candidateGroups)));
```

#### 缺陷点 4：流程跳转来源节点

**位置**：`ydsz-workflow/ydsz-workflow-server/.../service/impl/FlowDefinitionServiceImpl.java:297` 与 `:848`

```java
skip.setExt("{\"sourceRef\":\"" + s.getFromNodeCode() + "\"}");
```

**影响**：`fromNodeCode` 虽多为受控编码，但流程设计器允许自定义节点编码，存在同类风险。两处代码重复，应同时修复。

**修复**：
```java
skip.setExt(YdszJson.toJson(Map.of("sourceRef", s.getFromNodeCode())));
```

### P0-2 半吊子手工转义

**位置**：`ydsz-common/ydsz-common-seata/.../audit/TransactionAuditLogger.java:49-63`

```java
StringBuilder sb = new StringBuilder(256);
sb.append("{\"timestamp\":\"").append(LocalDateTime.now()).append("\"");
sb.append(",\"txName\":\"").append(transactionName).append("\"");   // 未转义
sb.append(",\"type\":\"").append(type).append("\"");                 // 未转义
sb.append(",\"xid\":\"").append(xid).append("\"");                   // 未转义
sb.append(",\"result\":\"").append(result).append("\"");             // 未转义
if (error != null) {
    sb.append(",\"error\":\"").append(error.replace("\"", "\\\"")).append("\"");  // 只转了引号
}
```

**影响**：
- `error` 字段只替换了双引号，**未处理反斜杠**（`\` 会破坏转义序列）、**未处理换行符和控制字符**（JSON 规范要求 `\n` `\r` `\t` 必须转义）。异常堆栈几乎必然包含换行符 → **审计日志大概率是非法 JSON**
- 其余 5 个字段完全未转义
- 该日志用于分布式事务审计，日志损坏会直接影响故障排查

**修复**：
```java
Map<String, Object> audit = new LinkedHashMap<>();
audit.put("timestamp", LocalDateTime.now());
audit.put("txName", transactionName);
audit.put("type", type);
audit.put("xid", xid);
if (branchId != null) {
    audit.put("branchId", branchId);
}
audit.put("result", result);
audit.put("durationMs", durationMs);
if (error != null) {
    audit.put("error", error);
}
auditLog.info(YdszJson.toJson(audit));
```

> 使用 `LinkedHashMap` 保持字段顺序与原实现一致，避免日志采集侧的解析规则变更。

### P0 修复验收标准

1. 全仓库 `grep -rn '"{\\"' --include="*.java"` 在非测试、非常量场景下**零命中**
2. 新增单元测试：审批意见输入 `他说"通过"\n同意` 后，`YdszJson.parseMap(resultJson).get("comment")` 能原样取回
3. 在 CheckStyle / ArchUnit 中加入禁止规则（见 P1-5）

---

## 四、P1 问题清单（架构贯通）

### P1-1 金额解析未启用 BigDecimal 精度模式

**位置**：`ydsz-workflow/ydsz-workflow-web/src/main/resources/application.yml`

`FlowConditionExprServiceImpl.java:161` 使用 `YdszJson.parseMap(conditionJson)` 解析流程条件表达式，而该表达式**包含金额判断**：

```java
// FlowConditionExprServiceImpl.java:637
"{\"logic\":\"AND\",\"groups\":[{\"field\":\"amount\",\"operator\":\"GT\",\"value\":10000,\"valueType\":\"NUMBER\"}]}"
```

`JsonProperties.useBigDecimal` 默认值为 `false`（`JsonProperties.java:102`），意味着数值走 `double` 解析。审批金额条件判断出现精度偏差属于业务事故。

**对比现状**：
- `ydsz-project`：已开启 ✅（`application.yml:15`，注释明确"项目管理涉及预算/成本金额计算"）
- `ydsz-literule`：已开启 ✅
- `ydsz-workflow`：**未开启** ❌ ← 但它做金额条件判断
- 其余 6 个服务：未开启

**修复**：在 `ydsz-workflow-web/application.yml` 的 `ydsz.json` 下增加：
```yaml
    use-big-decimal: true
```

**同时应排查**：`ydsz-message`（消息可能含金额模板变量）、`ydsz-cronjob`（`BillableUtilizationJobHandler` 涉及计费）是否也需开启。

### P1-2 依赖声明与实际使用错配

**冗余声明（7 个模块）**：以下模块 `pom.xml` 声明了 `ydsz-common-json`，但源码中零引用：

```
ydsz-common/ydsz-common-app
ydsz-common/ydsz-common-docs
ydsz-common/ydsz-common-exception
ydsz-cronjob/ydsz-cronjob-domain
ydsz-literule/ydsz-literule-domain
ydsz-message/ydsz-message-domain
ydsz-nextwiki/ydsz-nextwiki-domain
```

**处理建议**：
- `*-domain` 模块：若定位是"为下游 server 模块提供传递依赖"，应显式改为 `<scope>compile</scope>` 并在 pom 中加注释说明意图；否则移除
- `common-app` / `common-docs` / `common-exception`：直接移除，减少编译期依赖图复杂度

**声明缺失（1 个模块）**：`ydsz-literule/ydsz-literule-web` 有 3 个文件使用 `com.njydsz.common.json`，但 `pom.xml` **未声明依赖**，靠 `literule-server` 传递依赖工作。

**风险**：一旦 `literule-server` 调整依赖，`literule-web` 编译直接失败。违反 Maven 依赖显式化原则。

**修复**：在 `ydsz-literule-web/pom.xml` 补充显式声明。

### P1-3 服务级配置基线漂移

9 个服务的 `ydsz.json` 配置组合各不相同，无统一基线：

| 服务 | enabled | date-format | naming | write-nulls | monitoring | safe-mode | use-big-decimal | streaming | warmup |
|---|---|---|---|---|---|---|---|---|---|
| agent | ✅ | ✅ | ✅ | ✅ | ✅ | — | — | — | 5 类 |
| gateway | ✅ | ✅ | ✅ | ✅ | ✅ | — | — | ✅ | 3 类 |
| literule | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | — | 5 类 |
| message | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | — | — | 5 类 |
| nextwiki | ✅ | ✅ | ✅ | ✅ | ✅ | — | — | — | 3 类 |
| system | ✅ | ✅ | ✅ | ✅ | ✅ | — | — | — | 2 类 |
| userinfo | ✅ | ✅ | ✅ | ✅ | ✅ | — | — | — | 2 类 |
| project | — | — | — | — | — | — | ✅ | — | 1 类 |
| workflow | — | — | — | — | — | — | — | ✅ | 8 类 |
| cronjob | ✅ | — | — | — | ✅ | — | — | — | 5 类 |

**说明**：`safe-mode` 默认值已是 `true`（`JsonProperties.java:92`），未显式配置**不构成安全风险**，此处仅为一致性问题。真正有风险的是 `use-big-decimal`（默认 `false`，见 P1-1）。

**问题**：
1. `project` / `workflow` 未配置 `date-format` 与 `naming-strategy`，依赖默认值。一旦公共模块调整默认值，这两个服务行为静默漂移
2. 配置文件位置不统一：`bootstrap.yml`（agent/system/userinfo）、`application.yml`（literule/nextwiki/project/workflow/cronjob）、`config/*-dev.yaml`（gateway/message）三种放法混用，排查成本高

**修复建议**：
- 在 Nacos 共享配置 `ydsz-common.yaml` 中定义**统一 JSON 基线**（`date-format` / `naming-strategy` / `write-nulls` / `monitoring-enabled` / `safe-mode`）
- 各服务本地配置仅保留**服务特有项**（`warmup-classes` / `use-big-decimal` / `streaming-enabled`）
- 统一放置位置：服务私有配置一律放 `application.yml`

### P1-4 HttpMessageConverter 自动装配存在顺序竞态

**涉及文件**：
- `ydsz-common-json/.../spring/boot/JsonAutoConfiguration.java:72-80`
- `ydsz-common-safe/.../config/SafeConfiguration.java:304-308`

**现状**：
```java
// JsonAutoConfiguration
@ConditionalOnMissingBean(JsonHttpMessageConverter.class)
public JsonHttpMessageConverter ydszJsonHttpMessageConverter(...)

// SafeConfiguration
@ConditionalOnMissingBean(XssJsonMessageConverter.class)
public XssJsonMessageConverter xssJsonMessageConverter(...)
```

而 `XssJsonMessageConverter extends JsonHttpMessageConverter`。

**问题**：两个自动配置类之间**没有声明顺序关系**（`JsonAutoConfiguration` 只声明了 `@AutoConfigureBefore(JacksonAutoConfiguration.class)`，`SafeConfiguration` 是裸 `@AutoConfiguration`）。因此：

- 若 `SafeConfiguration` 先装配 → 注册 `XssJsonMessageConverter`（它 is-a `JsonHttpMessageConverter`）→ JSON 侧条件命中，不再注册 → **链上 1 个 converter**（期望行为）
- 若 `JsonAutoConfiguration` 先装配 → 注册父类型 Bean → Safe 侧检查子类型不存在 → 也注册 → **链上 2 个 converter**

第二种情况下，虽然 `XssJsonMessageConverter` 实现了 `Ordered` 可争取优先级，但转换器链中存在两个功能重叠的 Bean 本身就是隐患，且行为依赖装配顺序，不同 Spring Boot 版本或类路径变化都可能翻转。

**修复**：在 `SafeConfiguration` 上显式声明顺序：
```java
@AutoConfiguration(before = JsonAutoConfiguration.class)
```
或在 `XssAutoConfiguration` / `SafeConfiguration` 加 `@AutoConfigureBefore(JsonAutoConfiguration.class)`，确保 XSS 版本始终优先注册。

**验收**：启动任一开启 `ydsz.safe.xss.enabled=true` 的服务，断言 `RequestMappingHandlerAdapter` 的 converter 链中 `JsonHttpMessageConverter` 类型实例**有且仅有 1 个**。

### P1-5 缺少架构约束，反模式会持续复发

P0 的手工拼接问题能在 6 处同时出现，说明**没有自动化拦截机制**。

**修复建议**：引入 ArchUnit 测试（放在 `ydsz-common-json` 或独立的架构测试模块）：

```java
@AnalyzeClasses(packages = "com.njydsz", importOptions = ImportOption.DoNotIncludeTests.class)
class JsonUsageArchTest {

    @ArchTest
    static final ArchRule 禁止外部JSON库 = noClasses()
            .should().dependOnClassesThat()
            .resideInAnyPackage("com.fasterxml.jackson..", "com.alibaba.fastjson..", "com.google.gson..")
            .because("统一使用 YdszJson 作为 JSON 底座");
}
```

手工拼接的检测用 CheckStyle 正则规则更合适：

```xml
<module name="RegexpSinglelineJava">
    <property name="format" value="&quot;\{\\&quot;.*\\&quot;\s*:\s*\\&quot;&quot;\s*\+"/>
    <property name="message" value="禁止手工拼接 JSON 字符串，请使用 YdszJson.toJson()"/>
    <property name="ignoreComments" value="true"/>
</module>
```

> 需排除 `agent-domain` 下的自定义 `JsonSerializer` 实现（`ChatMessageSerializer` 等），它们通过 `out.write("{\"role\":")` 手写序列化是**合理用法**——那是序列化器内部实现，字段名为硬编码常量，值通过 `out.writeString()` 走引擎转义。

---

## 五、P2 改进项（能力利用率提升）

### P2-1 JsonSchema 能力闲置，业务侧重复手写校验

`ydsz-common-json` 内部有 138 处 `JsonSchema` 相关实现，但业务侧**仅 1 个文件引用**。同时以下场景存在手写校验：

| 场景 | 位置 | 现状 |
|---|---|---|
| 流程表单校验 | `workflow-server/.../form/FlowFormValidator.java` | 手写字段校验 |
| 消息模板变量校验 | `message-server/.../template/TemplateVariableValidator.java` | 手写变量校验 |
| 规则 DSL 解析校验 | `literule-server/.../dsl/RuleDslParser.java` | 手写结构校验 |
| HTTP 任务参数校验 | `cronjob-server/.../handler/HttpJobHandler.java` | 无结构校验 |

**建议**：**不建议全面推广**。结合过度设计评估报告的结论（推荐冻结自研引擎），此处应做**二选一决策**：

- **若维持自研引擎**：选 1 个场景试点（推荐 `HttpJobHandler` 的任务参数校验，改动面小、收益直接），验证 `JsonSchema` 实现的成熟度后再决定是否推广
- **若按过度设计报告迁移 Jackson**：直接引入 `networknt/json-schema-validator` 等成熟库，**不要**在自研 Schema 上继续投入

无论哪条路径，**当前状态（自研了却不用）是最差的**——维护成本已付出，收益为零。

### P2-2 JsonModule SPI 仅 2/9 模块使用

`JsonModule` 是模块提供的**标准扩展点**，用于集中注册自定义序列化器。当前仅 `agent`、`safe` 两模块使用，其余模块的自定义序列化需求散落在各处 `YdszJson.register()` 调用中（4 个文件）。

**建议**：将散落的 `YdszJson.register()` 调用收敛到各自模块的 `XxxJsonModule` 实现中，通过 Spring Bean 自动发现注册。收益：
- 注册时机统一（避免静态代码块与 Spring 生命周期的竞态）
- 便于测试（模块可独立构造）
- 与 `agent` / `safe` 的做法保持一致

### P2-3 代码规范瑕疵

| 位置 | 问题 | 修复 |
|---|---|---|
| `JsonAutoConfiguration.java:72-80` | 该方法块整体顶格，与文件其余部分 4 空格缩进不一致，明显是后期手工插入未格式化 | 重新格式化 |
| `ydsz-project-web/application.yml:14-15` | 注释在 2 空格缩进、配置项在 4 空格缩进，视觉上像脱离了 `json:` 块（实际解析正确） | 注释对齐到 4 空格 |

---

## 六、落地路线图

### 阶段一：止血（建议 1 周内）

| 项 | 内容 | 工作量 |
|---|---|---|
| P0-1 | 修复 4 处未转义拼接（cronjob 1 处、workflow 3 处） | 0.5 天 |
| P0-2 | 重写 `TransactionAuditLogger` 审计日志构造 | 0.5 天 |
| P1-1 | `workflow` 开启 `use-big-decimal`，排查 message/cronjob | 0.5 天 |
| — | 补充对应单元测试（特殊字符输入用例） | 1 天 |

**验收**：特殊字符审批意见、含引号的 BPMN 候选人、带换行的异常堆栈，三类输入下产出的 JSON 均可被 `YdszJson.parseMap` 正确解析还原。

### 阶段二：固化约束（建议 2 周内）

| 项 | 内容 | 工作量 |
|---|---|---|
| P1-5 | 引入 CheckStyle 拼接检测规则 + ArchUnit 外部库禁令 | 1 天 |
| P1-4 | 修复 HttpMessageConverter 装配顺序，补充断言测试 | 0.5 天 |
| P1-2 | 清理 7 个冗余依赖，补全 `literule-web` 显式声明 | 0.5 天 |

**验收**：CI 流水线能够拦截新增的手工拼接代码；`mvn dependency:analyze` 无 "Used undeclared" 与 "Unused declared" 告警。

### 阶段三：配置治理（建议 1 个月内）

| 项 | 内容 | 工作量 |
|---|---|---|
| P1-3 | Nacos 定义统一 JSON 基线，各服务收敛为差异化配置 | 2 天 |
| P2-3 | 代码格式与 YAML 缩进修正 | 0.5 天 |

**验收**：9 个服务的 `ydsz.json` 本地配置仅保留 `warmup-classes` 等服务特有项；公共项统一由 Nacos 下发。

### 阶段四：能力决策（与过度设计报告联动）

阶段四**不建议独立推进**，应先完成 `ydsz-common-json-overdesign-assessment.md` 中的选型决策（是否冻结自研引擎），再决定：

- 维持自研 → 执行 P2-1 试点、P2-2 SPI 收敛
- 迁移 Jackson → 跳过 P2-1/P2-2，直接按迁移方案走，本文的 P0/P1 修复成果**可完整继承**（因为都是调用方代码的正确性修复）

---

## 七、结论

`ydsz-common-json` 的**推广是成功的**——零外部 JSON 库残留、215 文件统一收敛、6 个框架适配器完备、无重复造轮子，这些是扎实的架构治理成果，应予肯定。

真正需要正视的是三点：

1. **有 4 处真实缺陷正在生产运行**（未转义拼接），与引擎选型无关，必须立即修复
2. **贯通断点在 L3/L4 层**（SPI 扩展、数据治理），而非基础层
3. **24% 的能力利用率**需要的不是"推广更多能力"，而是**决策该砍掉哪些**——这一点与过度设计评估报告的结论一致：当前"自研了却不用"是投入产出比最差的状态

建议将阶段一、二作为**无条件执行项**（正确性与工程约束），阶段三、四则纳入更大的引擎选型决策一并推进。

---

## 附录：实施完成追踪（2026-08-03）

| 编号 | 内容 | 状态 | 涉及文件 |
|---|---|---|---|
| **P0-1** | 4 处手工拼接 JSON 修复 | ✅ 已修复 | DagInstanceControlService.java, BpmnXmlParser.java, FlowDefinitionServiceImpl.java |
| **P0-2** | TransactionAuditLogger 审计日志重写 | ✅ 已修复 | TransactionAuditLogger.java |
| **P1-1** | workflow 开启 use-big-decimal + 排查 | ✅ 已修复 | workflow-web/application.yml |
| **P1-2** | 冗余依赖清理 + 缺失声明补全 | ✅ 已修复 | 8 个 pom.xml |
| **P1-3** | Nacos 统一 JSON 配置基线 | ✅ 基线文档已出 | docs/ydsz-json-nacos-baseline.md |
| **P1-4** | HttpMessageConverter 装配顺序 | ✅ 已修复 | SafeConfiguration.java |
| **P1-5** | ArchUnit R29 + CheckStyle 配置 | ✅ 已部署 | ArchitectureRulesTest.java, checkstyle.xml, root pom.xml |
| **P2-2** | JsonModule SPI 收敛 + 死代码清理 | ✅ 已修复 | XssJsonConfig.java（删除） |
| **P2-3** | 代码格式修正 | ✅ 已修复 | JsonAutoConfiguration.java, project-web/application.yml |
| **P2-1** | JsonSchema 试点 | ⏳ 待引擎选型决策 | — |

### 约束机制清单

| 机制 | 位置 | 覆盖范围 |
|---|---|---|
| ArchUnit R29 | common-base/.../ArchitectureRulesTest.java | 编译期类路径扫描（禁止外部 JSON 库） |
| CheckStyle R1 | checkstyle.xml → maven-checkstyle-plugin validate 阶段 | 源码级正则（禁止手工拼接 JSON） |
| CheckStyle R2-R4 | checkstyle.xml | System.out/printStackTrace/@SuppressWarnings |

