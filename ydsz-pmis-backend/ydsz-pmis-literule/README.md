# ydsz-pmis-literule

> 轻量规则引擎（Library）

## 模块定位

| 属性 | 值 |
|---|---|
| **类型** | 库（**不独立部署**） |
| **作用** | 被 `ydsz-pmis-project` / `ydsz-pmis-userinfo` 等业务服务依赖 |
| **构建顺序** | 3/10（Maven 构建第 3 个） |
| **JVM 进程** | 无（仅作为 jar 依赖） |

## 核心职责

本模块是 PMIS 的**轻量级规则引擎**，对标 Aviator + Drools + LiteFlow 组合。

### 1. 4 大规则类型

| 类型 | 适用场景 |
|---|---|
| **Expression** 表达式 | Aviator / QLExpress 表达式驱动的简单规则 |
| **Script** 脚本 | Groovy / Nashorn 多语言脚本规则 |
| **DecisionTable** 决策表 | Excel 导入的二维表规则 |
| **DecisionTree** 决策树 | 多层 if-else 树规则 |
| **Scorecard** 评分卡 | 信用评分 / 风险评分等多维加权 |
| **CEP** 复杂事件处理 | 滑动窗口 + 模式匹配（如"30 分钟内 5 次失败"） |

### 2. 核心能力

| 能力 | 说明 |
|---|---|
| **Aviator 表达式** | 高性能表达式引擎（Aviator 5.4.3 / QLExpress 3.3.1 双引擎可切换） |
| **Groovy / Nashorn** | 脚本引擎（JSR-223） |
| **规则链编排** | DSL 声明式规则链 + DAG 可视化 |
| **热加载** | Nacos / Apollo / DB / File 多源动态刷新 |
| **版本管理** | 灰度 / canary / 回滚 |
| **dry-run 仿真** | 不实际执行，只评估结果 |
| **规则审批** | 草稿 → 审核 → 上线 流程化 |
| **效果分析** | 命中率 / 通过率 / 拒绝率 / 业务指标 |
| **A/B 测试** | 灰度发布按比例分流 |
| **自适应阈值** | 指标异常检测 + 阈值动态调整 |
| **CEP 模式** | 时间窗口 + 模式匹配（CEPEngine） |
| **链路追踪** | 规则执行轨迹（TraceRecorder） |
| **规则文档** | 自动生成 Markdown 文档 |
| **规则推荐** | LLM 辅助生成规则（OpenAI 兼容） |
| **对账** | 与业务系统对账（ReconcileDataProvider SPI） |
| **分布式** | Redis Pub/Sub 广播 + 一致性哈希分片 |

### 3. 包结构

```
com.njydsz.pmis.literule
├── adaptive/        # 自适应阈值
├── agent/           # ReAct Agent 执行
├── ai/              # LLM 规则推荐
├── annotation/      # @LiteRule
├── api/             # Rule / RuleEngine / RuleContext 等 SPI
├── approval/        # 规则审批流
├── benchmark/       # 性能压测（JMH）
├── cache/           # 多级缓存（Caffeine L1 + Redis L2）
├── calc/            # 计算类（信用评分 / 双费率利润）
├── cep/             # 复杂事件处理
├── config/          # 自动配置 + 注解注册 + ABTest + 热加载
├── core/            # 引擎核心（RuleEngine / InferenceEngine / 熔断 / 超时）
├── distributed/     # 分布式（Redis 节点注册 / 分片 / 广播）
├── dsl/             # DSL 解析
├── event/           # 规则刷新事件
├── excel/           # 决策表 Excel 导入导出
├── expr/            # 表达式求值（Aviator / QLExpress）
├── impl/            # 6 种规则实现
├── model/           # 模型输入
├── orchestrator/    # 规则链编排
├── security/        # 权限检查
├── spi/             # 配置源 SPI（Nacos / Apollo / DB / File / ZK）
└── ...
```

## 使用方式

### 1. Maven 依赖

```xml
<dependency>
  <groupId>com.njydsz.pmis</groupId>
  <artifactId>ydsz-pmis-literule</artifactId>
  <version>${project.version}</version>
</dependency>
```

### 2. 声明式（注解方式）

```java
@LiteRule(
    code = "RISK_CHECK",
    name = "项目风险检查",
    expression = "cost > budget * 0.9",
    severity = RuleSeverity.HIGH
)
public boolean checkProjectRisk(@LiteRuleContext ProjectContext ctx) {
    return AviatorEvaluator.execute("cost > budget * 0.9", ctx.toMap());
}
```

### 3. 编程式（API 方式）

```java
@Autowired
private RuleEngine ruleEngine;

public void evaluate(RuleContext context) {
    RuleResult result = ruleEngine.evaluate("RISK_CHECK", context);
    if (result.isHit()) {
        // 触发业务逻辑
    }
}
```

### 4. 规则链（DSL）

```yaml
# rule-chain.yaml
chain:
  name: "项目审批链"
  nodes:
    - id: node1
      rule: RISK_CHECK
    - id: node2
      rule: PROFIT_CHECK
      depends: node1
    - id: node3
      rule: COMPLIANCE_CHECK
      depends: node1, node2
```

## 配置

`LiteRuleProperties`：

| 配置 | 默认值 | 说明 |
|---|---|---|
| `pmis.literule.enabled` | `true` | 是否启用 |
| `pmis.literule.source-type` | `nacos` | 配置源类型 |
| `pmis.literule.expression-engine` | `aviator` | `aviator` / `qlexpress` |
| `pmis.literule.cache-ttl-seconds` | `300` | 缓存 TTL |
| `pmis.literule.trace-enabled` | `true` | 启用链路追踪 |
| `pmis.literule.canary-enabled` | `true` | 启用灰度 |
| `pmis.literule.circuit-breaker.failure-threshold` | `50` | 熔断失败率 |

## 测试

本模块自带完善的单元测试 + JMH 基准测试：

```bash
# 单元测试
mvn -pl ydsz-pmis-literule -am test

# 性能基准
mvn -pl ydsz-pmis-literule -am test -Pbenchmark
```

测试覆盖：
- 6 种规则类型
- 表达式求值
- 规则链编排
- 灰度 / 熔断 / 超时
- 自适应阈值
- 分布式广播
- A/B 测试
- LLM 规则推荐
- 性能压测（JMH）

## 版本与变更

- **首发版本**：v1.0.0（2026-06-30）
- **当前版本**：v1.3.0-SNAPSHOT
- **变更需走 PR + Code Review**

---

> 本模块是**纯库**，不包含 `@SpringBootApplication` 启动类。
> 任何修改都需跨服务回归（project / userinfo / agent 等）。
