# RETE 算法评估报告

## 1. 评估背景

LiteRule 规则引擎当前采用"线性遍历 + 索引过滤"的评估策略：
- 规则按优先级排序注册
- 评估时通过 RuleIndexer 按租户/环境/场景/字段过滤候选规则集
- 逐条评估候选规则的条件表达式
- 已有 EvaluationResultCache 做 LRU+TTL 结果缓存

RETE 算法是经典的前向推理规则匹配算法（Charles Forgy, 1979），通过编译规则为 Alpha/Beta/Production 网络来避免重复条件求值。

## 2. RETE 核心机制

### 2.1 Alpha Network
- 对每个原子条件（如 `amount > 10000`）创建 Alpha 节点
- 输入事实通过 Alpha Network 时，每个节点只做单条件测试
- 相同条件的 Alpha 节点共享，避免重复计算

### 2.2 Beta Network
- 将多个 Alpha 节点的输出通过 Join 节点组合
- Beta 节点维护部分匹配结果（Working Memory）
- 当事实增删时，增量更新 Beta 网络

### 2.3 Production Nodes
- 叶子节点，对应规则触发动作
- 当 Beta 网络有完整匹配时激活

## 3. 适用性分析

### 3.1 PMIS 规则引擎特征

| 特征 | PMIS 场景 | RETE 最优场景 |
|------|----------|--------------|
| 事实更新模式 | 每请求一次性设置，评估期间不变 | 频繁增量增删（工作内存模式） |
| 规则量级 | 百级（100-500 条） | 千级以上（Drools 典型场景） |
| 条件共享度 | 中等（部分规则引用相同字段） | 高（大量规则共享相同条件） |
| 评估模式 | 同步阻塞，单次评估返回结果 | 持续推理，多次事实更新 |
| 延迟要求 | 毫秒级（<50ms） | 秒级可接受 |

### 3.2 结论

**不推荐引入完整 RETE 网络**，原因：

1. **增量更新优势无法发挥**：PMIS 每次评估都是全新 facts，RETE 的 Beta 网络 Working Memory 无法跨请求复用，反而增加了网络构建开销。

2. **规则量级不达标**：RETE 的性能优势在 1000+ 规则时才显著（网络构建的常数开销被大量共享节点分摊）。PMIS 百级规则场景下，现有索引过滤已足够。

3. **实现复杂度过高**：完整 RETE 需要 Alpha/Beta/Production 节点管理、Working Memory 维护、条件编译、节点失效等机制，代码量预计 3000+ 行，维护成本远超性能收益。

4. **现有优化已覆盖核心场景**：
   - RuleIndexer：按租户/环境/场景/字段倒排索引
   - EvaluationResultCache：LRU+TTL 结果缓存
   - ExpressionCache：单次评估内表达式缓存
   - ParallelRuleEvaluator：并行评估

## 4. 替代方案：条件共享优化（RETE Alpha Network 轻量实现）

### 4.1 核心思路
取 RETE Alpha Network 的"条件共享"思想，在单次评估内：
1. 解析候选规则的条件表达式，提取原子条件
2. 对每个唯一原子条件求值一次，缓存结果
3. 同一原子条件被多条规则引用时，从缓存读取

### 4.2 实现组件
- `ConditionSharingOptimizer`：条件共享优化器
- 在 `DefaultRuleEngine.evaluate()` 中，候选规则确定后、逐条评估前调用

### 4.3 性能预期
- 当 N 条规则共享 M 个原子条件（N >> M）时，求值次数从 O(N) 降为 O(M)
- 对 100 条规则、20 个唯一原子条件的典型场景，表达式解析开销减少约 70%

### 4.4 使用方式
```java
// 在 DefaultRuleEngine 中集成
ConditionSharingOptimizer optimizer = new ConditionSharingOptimizer();
optimizer.optimize(candidateRules, context);
// 后续规则评估时，RuleContext.getExpressionCache() 中已有预计算结果
```

## 5. 未来演进路径

如规则量级增长到 1000+ 且条件共享度高，可考虑：
1. **Alpha 网络持久化**：将原子条件编译为 DAG，跨请求复用
2. **条件预编译**：注册时解析条件为 AST，运行时仅做树遍历
3. **增量评估**：支持 facts 部分更新，仅重评估受影响的规则

---
*评估人: ydsz-pmis-team*
*评估日期: 2026-07-11*
*版本: 2.1.0*
