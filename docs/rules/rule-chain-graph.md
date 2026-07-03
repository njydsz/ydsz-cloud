<!--
  ===========================================================================
  文件名: rule-chain-graph.md
  路径:   docs/rules/rule-chain-graph.md
  作用:   LiteRule 1.4.0+ 可视化规则链编排画布元数据（Rule Chain Graph）的设计目标、
          数据模型、双向转换 API、REST 设计建议与限制说明
  关联:   ydsz-pmis-literule/orchestrator/ChainGraphConverter  /  rule-trace-replay.md  /  rule-conflict-detection.md
  ===========================================================================
-->

# 可视化规则链编排画布元数据

> 适用于 1.4.0 起。P2-1 提供规则链的可视化编排能力：以 `RuleChainGraph` 描述画布节点与连线，承载前端拖拽编辑所需的位置、样式、断点等元数据；通过 `ChainGraphConverter` 与运行时编排模型 `RuleChain` 双向转换，使"画布所见"与"引擎所执行"解耦又可互转。

## 1. 设计目标

- **可视化与执行分离**：`RuleChainGraph` 仅承载画布布局元数据（节点位置、连线、样式），不参与运行时执行；运行时执行仍由 `RuleChain` 驱动，避免布局信息污染执行模型
- **双向可逆**：通过 `ChainGraphConverter` 实现 `RuleChain → Graph`（提取结构骨架）与 `Graph → RuleChain`（还原可执行编排）的双向转换，支撑画布编辑 → 持久化 → 执行的闭环
- **多链类型覆盖**：覆盖 THEN / WHEN / IF / ELIF / SWITCH / FOR / WHILE / BREAK 八种链类型的画布节点提取，每种链类型对应特定的连线语义
- **不承担布局职责**：转换时不自动布局（前端画布渲染时再调用 dagre / elk 等布局算法），仅填充 `nodeId` 与父子关系，后端保持轻量
- **断点调试预留**：节点 DTO 内置 `breakpoint` 字段（P2-3 断点调试），前端可勾选标记，运行时引擎可据此在断点处暂停
- **画布持久化**：支撑规则链版本回放（按 `graphId` 拉取历史画布快照）、规则链导入导出（导出为 JSON，跨环境同步）

## 2. 数据模型

三层模型：`RuleChainGraph`（画布图） → `ChainNodeDTO`（节点） + `ChainEdgeDTO`（连线）。

### 2.1 RuleChainGraph（画布图）

| 字段 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `graphId` | String | - | 画布 ID（全局唯一） |
| `name` | String | - | 画布名称（如"CPI 预警链-2024Q1"） |
| `description` | String | - | 画布描述 |
| `scenario` | String | - | 适用场景（与 `RuleContext.scenario` 对应） |
| `tenantId` | String | - | 租户 ID（多租户隔离） |
| `version` | String | - | 画布版本号（语义化版本，如 1.0.0、1.1.0-SNAPSHOT） |
| `status` | String | `DRAFT` | 画布状态：DRAFT / PUBLISHED / ARCHIVED |
| `nodes` | `List<ChainNodeDTO>` | `[]` | 节点列表 |
| `edges` | `List<ChainEdgeDTO>` | `[]` | 连线列表 |
| `viewport` | Viewport | - | 画布视口（前端缩放和平移状态） |
| `metadata` | `Map<String,Object>` | - | 画布元数据扩展（作者、标签等） |
| `createdAt` | LocalDateTime | - | 创建时间 |
| `updatedAt` | LocalDateTime | - | 最后更新时间 |
| `createdBy` | String | - | 创建人 |
| `updatedBy` | String | - | 最后更新人 |

`Viewport` 内部类：`x` / `y`（视口左上角坐标）/ `zoom`（缩放比例，1.0 = 100%）。

### 2.2 ChainNodeDTO（画布节点）

| 字段 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `nodeId` | String | - | 节点 ID（画布内唯一） |
| `nodeType` | String | - | 节点形态：SINGLE / CHAIN / GROUP |
| `label` | String | - | 节点显示标签（默认取规则名称） |
| `ruleCode` | String | - | 引用的规则编码（nodeType=SINGLE 时必填） |
| `ruleName` | String | - | 引用的规则名称（便于画布展示） |
| `category` | String | - | 规则类别（EVM / COST / BENCH 等，前端按类别着色） |
| `chainType` | String | - | 子链类型（THEN/WHEN/IF/ELIF/SWITCH/FOR/WHILE/BREAK） |
| `parentNodeId` | String | - | 父节点 ID（嵌套链时使用，根节点为 null） |
| `position` | Position | - | 节点位置坐标（画布坐标系，左上角为原点） |
| `size` | Size | - | 节点尺寸（可选，前端可按默认尺寸渲染） |
| `style` | `Map<String,Object>` | - | 节点样式扩展（颜色、图标等，前端自定义） |
| `metadata` | `Map<String,Object>` | - | 业务扩展字段（如分支条件、循环变量名等） |
| `breakpoint` | boolean | `false` | 是否启用断点（P2-3 断点调试，前端可勾选） |

`Position` 内部类：`x` / `y`（像素坐标）。
`Size` 内部类：`width` / `height`（像素尺寸）。

### 2.3 ChainEdgeDTO（画布连线）

| 字段 | 类型 | 说明 |
|------|------|------|
| `edgeId` | String | 边 ID（画布内唯一） |
| `sourceNodeId` | String | 起点节点 ID |
| `targetNodeId` | String | 终点节点 ID |
| `edgeType` | String | 边类型（见下方连线类型表） |
| `label` | String | 边显示标签（如 "amount > 1000" 或 "type=A"） |
| `condition` | String | 条件表达式（IF_BRANCH / WHILE_ITER 时携带） |
| `branchValue` | String | 分支值（SWITCH_BRANCH 时携带，对应分支 key） |
| `style` | `Map<String,Object>` | 边样式扩展（线型、颜色、箭头样式等） |
| `metadata` | `Map<String,Object>` | 业务扩展字段 |

## 3. 节点形态与连线类型

### 3.1 节点形态（nodeType）

| 节点形态 | 含义 | 对应 RuleNode.NodeType | 必填字段 |
|---------|------|----------------------|---------|
| `SINGLE` | 单条规则节点 | `RuleNode.NodeType.SINGLE` | `ruleCode` / `ruleName` / `label` / `category` |
| `CHAIN` | 子链节点（嵌套一条 RuleChain） | `RuleNode.NodeType.CHAIN` | `chainType` / `label` |
| `GROUP` | 规则组节点（包装多个子节点） | `RuleNode.NodeType.GROUP` | `label` |

### 3.2 连线类型（edgeType）

`ChainEdgeDTO.EdgeType` 常量集合：

| 连线类型 | 语义 | 携带字段 | 适用链类型 |
|---------|------|---------|-----------|
| `THEN` | 顺序流：source 执行完毕后执行 target | - | THEN / WHEN |
| `IF_BRANCH` | 条件分支：source 是 IF 节点，target 是分支动作 | `condition` / `label` | IF |
| `SWITCH_BRANCH` | 分支选择：source 是 SWITCH 节点，target 是分支节点 | `branchValue` / `label` | SWITCH |
| `FOR_ITER` | 循环迭代：source 是 FOR 节点，target 是循环体 | 节点 `metadata` 携带 `iterableExpression` / `iterationVar` | FOR |
| `WHILE_ITER` | 条件循环：source 是 WHILE 节点，target 是循环体 | `condition` / `label` | WHILE |
| `DEFAULT_BRANCH` | 默认分支：SWITCH/ELIF 未命中时执行的兜底 | - | SWITCH / ELIF（预留） |
| `GROUP_MEMBER` | 组成员：source 是 GROUP 节点，target 是组成员 | - | GROUP |

## 4. 双向转换 API

`ChainGraphConverter` 提供运行时编排模型 `RuleChain` 与可视化元数据 `RuleChainGraph` 之间的双向转换。

### 4.1 toGraph：RuleChain → RuleChainGraph

```java
// 自动生成 graphId
public static RuleChainGraph toGraph(RuleChain chain)

// 指定 graphId 与画布名称
public static RuleChainGraph toGraph(RuleChain chain, String graphId, String graphName)
```

**转换策略**：

| 链类型 | 节点提取 | 连线生成 |
|--------|---------|---------|
| THEN / WHEN | 节点按顺序转为 SINGLE 子节点 | 相邻子节点间通过 `THEN` 边连接 |
| IF | 每个 action 节点转为 SINGLE 子节点 | 每个 action 通过 `IF_BRANCH` 边连到根，携带 `condition` |
| SWITCH | 每个分支节点转为 SINGLE 子节点 | 每个分支通过 `SWITCH_BRANCH` 边连到根，携带 `branchValue` |
| FOR | 循环体节点转为 SINGLE 子节点，`metadata` 携带 `iterableExpression` / `iterationVar` | 循环体通过 `FOR_ITER` 边连到根 |
| WHILE | 循环体节点转为 SINGLE 子节点 | 循环体通过 `WHILE_ITER` 边连到根，携带 `condition` |
| ELIF | 当前为骨架提取（不展开 `elifBranches`，留空） | 无连线 |
| BREAK | 无子节点 | 无连线 |

> 位置坐标策略：转换时不自动布局，仅填充 `nodeId` 与父子关系。前端画布渲染时再调用 dagre / elk 等布局算法。

### 4.2 toChain：RuleChainGraph → RuleChain

```java
public static RuleChain toChain(RuleChainGraph graph, RuleResolver resolver)
```

**还原规则**：

- 仅支持 SINGLE 节点按画布顺序还原为 THEN 链（最常见场景）
- 复杂链类型（IF / SWITCH / FOR / WHILE）需要前端按业务语义重新编排，后端提供 REST API 由 `RuleAdminService` 直接构造 `RuleChain`
- 画布为空（`nodes` 为空）或无 SINGLE 节点时返回 `null`

### 4.3 RuleResolver 接口

`toChain` 转换时，需要根据 `ruleCode` 解析实际的 `Rule` 实例。调用方需要实现此接口，从规则仓库或缓存中获取 `Rule`：

```java
@FunctionalInterface
public interface RuleResolver {
    /**
     * 根据 ruleCode 解析规则实例
     *
     * @param ruleCode 规则编码
     * @return Rule 实例；未找到返回 null
     */
    Rule resolve(String ruleCode);
}
```

## 5. REST API 设计建议

> 以下端点为设计建议，当前版本未实现（标注"待实现"）。规划在后续版本通过 `RuleChainGraphController` 暴露。

| 方法 | 路径 | 说明 | 状态 |
|------|------|------|------|
| GET | `/api/v1/rule-chain-graphs/{graphId}` | 按 graphId 查询画布图 | 待实现 |
| GET | `/api/v1/rule-chain-graphs?scenario={scenario}` | 按场景查询画布图列表 | 待实现 |
| POST | `/api/v1/rule-chain-graphs` | 新建画布图（请求体为 `RuleChainGraph`） | 待实现 |
| PUT | `/api/v1/rule-chain-graphs/{graphId}` | 更新画布图（节点位置、连线、样式） | 待实现 |
| DELETE | `/api/v1/rule-chain-graphs/{graphId}` | 删除画布图（归档，不物理删除） | 待实现 |
| POST | `/api/v1/rule-chain-graphs/{graphId}/publish` | 发布画布图（DRAFT → PUBLISHED） | 待实现 |
| POST | `/api/v1/rule-chain-graphs/{graphId}/to-chain` | 将画布图还原为 RuleChain（调用 `ChainGraphConverter.toChain`） | 待实现 |

## 6. 使用示例

### 6.1 编程式 toGraph（RuleChain → 画布图）

```java
// 构造 THEN 链
Rule r1 = new ExpressionRule("R-EVM-001", "EVM 红灯数告警", "EVM", "evmRedCount >= 3");
Rule r2 = new ExpressionRule("R-FIN-001", "利润率预警", "FINANCE", "grossMargin < 0.05");
Rule r3 = new ExpressionRule("R-COST-001", "成本超支预警", "COST", "costVariance > 0.1");
RuleChain chain = RuleChain.then(r1, r2, r3);

// 转换为画布图（自动生成 graphId）
RuleChainGraph graph = ChainGraphConverter.toGraph(chain);

// 或指定 graphId 与名称
RuleChainGraph graph2 = ChainGraphConverter.toGraph(chain, "graph-evm-2024q1", "EVM 预警链 2024Q1");

// 前端可进一步设置节点位置（后端转换不含位置）
graph.getNodes().get(1).setPosition(new ChainNodeDTO.Position(120, 80));
graph.getNodes().get(2).setPosition(new ChainNodeDTO.Position(320, 80));
graph.getNodes().get(3).setPosition(new ChainNodeDTO.Position(520, 80));
```

### 6.2 编程式 toChain（画布图 → RuleChain）

```java
// 从持久化加载画布图
RuleChainGraph graph = graphRepository.findByGraphId("graph-evm-2024q1");

// 通过 RuleResolver 从规则仓库解析 Rule 实例
ChainGraphConverter.RuleResolver resolver = ruleCode -> ruleRepository.findByCode(ruleCode);

// 还原为可执行的 RuleChain（仅支持 SINGLE 节点按顺序还原为 THEN 链）
RuleChain chain = ChainGraphConverter.toChain(graph, resolver);
if (chain == null) {
    log.warn("画布图无可还原的 SINGLE 节点");
    return;
}

// 执行还原后的规则链
List<RuleResult> results = chain.evaluate(context, evaluator);
```

### 6.3 复杂链类型的画布转换

```java
// IF 链：条件 + 动作
RuleChain ifChain = RuleChain.ifThen("amount > 1000", actionRule);
RuleChainGraph ifGraph = ChainGraphConverter.toGraph(ifChain);
// ifGraph 含 1 个根 CHAIN(IF) 节点 + 1 个 SINGLE 动作节点
// 动作节点通过 IF_BRANCH 边连到根，condition = "amount > 1000"

// SWITCH 链：分支选择
Map<String, Rule> branches = new LinkedHashMap<>();
branches.put("A", ruleA);
branches.put("B", ruleB);
RuleChain switchChain = RuleChain.switchOn("type", branches);
RuleChainGraph switchGraph = ChainGraphConverter.toGraph(switchChain);
// switchGraph 含 1 个根 CHAIN(SWITCH) 节点 + 2 个 SINGLE 分支节点
// 每个分支通过 SWITCH_BRANCH 边连到根，branchValue = "A" / "B"

// FOR 链：循环
RuleChain forChain = RuleChain.forEach("items", "item", actionRule);
RuleChainGraph forGraph = ChainGraphConverter.toGraph(forChain);
// forGraph 含 1 个根 CHAIN(FOR) 节点 + 1 个 SINGLE 循环体节点
// 循环体节点 metadata 携带 iterableExpression="items" / iterationVar="item"
// 循环体通过 FOR_ITER 边连到根
```

## 7. 与运行时编排的关系

`RuleChainGraph` 与 `RuleChain` 是**可视化元数据**与**运行时执行模型**的分离设计：

| 维度 | RuleChainGraph | RuleChain |
|------|---------------|-----------|
| 定位 | 可视化元数据（画布布局、节点位置、连线样式） | 运行时执行模型（编排语义、条件表达式、求值逻辑） |
| 是否参与执行 | 否 | 是 |
| 字段关注点 | `position` / `size` / `style` / `breakpoint` | `chainType` / `conditionExpression` / `branchMap` / `nodes` |
| 持久化 | 落表 `pmis_rule_chain_graph`（待实现） | 不直接持久化（由规则配置 + 画布图反推） |
| 双向转换 | 通过 `ChainGraphConverter.toChain()` 还原为 `RuleChain` | 通过 `ChainGraphConverter.toGraph()` 提取为画布图 |

**闭环流程**：

```text
    ┌─────────────────┐    toGraph      ┌──────────────────┐
    │   RuleChain      │ ──────────────► │  RuleChainGraph  │
    │ （运行时编排）    │                 │ （可视化元数据）   │
    │                  │ ◄────────────── │                  │
    └─────────────────┘    toChain       └──────────────────┘
           │                                     │
           │ evaluate(context, evaluator)         │ 持久化 / 前端渲染
           ▼                                     ▼
    ┌─────────────────┐                 ┌──────────────────┐
    │  RuleResult     │                 │  画布编辑 / 版本  │
    └─────────────────┘                 └──────────────────┘
```

- **新建链路**：前端画布拖拽编排 → 保存为 `RuleChainGraph` → `toChain()` 还原为 `RuleChain` → 注册到规则引擎
- **回显链路**：已注册的 `RuleChain` → `toGraph()` 提取为画布图 → 前端渲染回显
- **执行链路**：`RuleChain.evaluate(context, evaluator)` 执行规则编排，产出 `RuleResult` 列表（与画布图无关）

## 8. 限制与后续演进

### 8.1 当前限制

1. **ELIF 链仅骨架提取**：`extractElif` 当前实现为空（`elifBranches` 不通过 `getNodes()` 暴露），仅生成根 CHAIN 节点，不展开多分支条件节点；前端可基于 `metadata` 自定义展示
2. **toChain 仅支持 THEN 链还原**：复杂链类型（IF / SWITCH / FOR / WHILE）的还原需要前端按业务语义重新编排，后端不自动反推复杂链结构
3. **不自动布局**：`toGraph` 转换时不计算节点位置坐标（`position` 留空），前端画布渲染时需调用 dagre / elk 等布局算法
4. **FOR 元数据硬编码**：`extractFor` 中 `iterableExpression` / `iterationVar` 当前硬编码为 "items" / "item"（待后续从 `RuleChain` 字段读取）
5. **WHEN 边类型复用 THEN**：当前 WHEN 链也使用 `THEN` 边类型表示并行（前端可基于根节点 `chainType=WHEN` 区分并行与顺序）
6. **无 REST API**：画布图的 CRUD 端点尚未实现，当前仅支持编程式调用

### 8.2 后续演进路径

- **P2-2 画布持久化**：实现 `pmis_rule_chain_graph` 表与 `RuleChainGraphController`，支撑画布图版本管理与历史回放
- **P2-3 断点调试**：基于节点 `breakpoint` 字段，在 `RuleChain.evaluate` 中检测断点节点并暂停执行，配合前端单步调试
- **自动布局算法**：后端集成 dagre / elk 布局算法，在 `toGraph` 时自动计算节点位置，避免前端重复实现
- **ELIF 完整提取**：扩展 `RuleChain` 暴露 `elifBranches` getter，使 `extractElif` 能完整提取多分支条件节点
- **复杂链 toChain 还原**：基于边类型（IF_BRANCH / SWITCH_BRANCH / FOR_ITER / WHILE_ITER）自动反推复杂链结构，减少前端编排负担
- **画布图导入导出**：支持将 `RuleChainGraph` 序列化为 JSON，跨环境同步（开发 → 测试 → 生产）
