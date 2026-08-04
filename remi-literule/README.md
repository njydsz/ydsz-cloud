# remi-literule

> 独立规则引擎微服务 — 基于 DDD 分层的自研规则引擎，对标 Drools + LiteFlow + 滴滴 Newton

## 模块定位

| 属性 | 值 |
|---|---|
| **类型** | 独立微服务（**独立部署、独立 JVM 进程**） |
| **端口** | **9007**（按构建顺序 8/10） |
| **作用** | REMI 的规则引擎中心服务，提供规则定义、编排、评估、灰度、回放、审批全生命周期能力；通过 REST API 对内对外提供规则决策服务 |
| **构建顺序** | 8/10（Maven 构建） |
| **JVM 进程** | 独立 JVM 进程，独立端口，注册到 Nacos |
| **服务注册** | Nacos Discovery（服务名 `remi-literule`） |
| **配置中心** | Nacos Config（`spring-cloud-starter-alibaba-nacos-config`） |
| **当前版本** | `1.0.0-SNAPSHOT`（项目版本号统一为 1.0.0） |
| **脚手架状态** | ✅ 已包含 `@SpringBootApplication` 启动类、`application.yml` / `bootstrap.yml`，可独立部署 |

## 分层结构（DDD 五层）

```
remi-literule/
├── remi-literule-api        # 对外 SPI/DTO：Rule / RuleEngine / RuleContext 等
├── remi-literule-domain     # 领域层：实体 DO、领域事件、注解、ModelInputProvider
├── remi-literule-infra      # 基础设施：MyBatis Mapper、决策表 Excel 导入导出
├── remi-literule-server     # 应用服务 + 引擎核心：DefaultRuleEngine、LiteExpr、热加载、CEP、回放、审批
└── remi-literule-web        # Web 层：7 个 REST Controller
```

依赖方向（严格单向）：`web → server → infra → domain → api`

## 核心职责

本模块是 REMI 的**轻量级规则引擎**，覆盖规则定义、编排、评估、灰度、回放、审批全生命周期。

### 1. 7 种规则类型

| 类型 | 实现类 | 适用场景 |
|---|---|---|
| **Expression** 表达式 | `ExpressionRule` | 基于 LiteExpr 表达式动态评估，支持 `${var}` 模板渲染与动态严重度 |
| **DecisionTable** 决策表 | `DecisionTableRule` | 二维表规则，支持 Excel 导入导出 |
| **CrossDecisionTable** 交叉决策表 | `CrossDecisionTableRule` | 多维交叉决策表 |
| **DecisionTree** 决策树 | `DecisionTreeRule` | 多层 if-else 树规则 |
| **Scorecard** 评分卡 | `ScorecardRule` | 信用评分 / 风险评分多维加权 |
| **Script** 脚本 | `ScriptRule` | JSR-223 脚本规则 |
| **Static** 静态 | `StaticRule` | 静态常量规则 |
| **CEP** 复杂事件处理 | `CEPEngine` | 滑动窗口 + 模式匹配（如"30 分钟内 5 次失败"），独立引擎 |

### 2. 核心能力清单

| 能力 | 说明 | 关键类 |
|---|---|---|
| **LiteExpr 表达式引擎** | 2.0.0 起自研，零外部依赖；含词法/语法分析、AST 编译缓存、常量折叠、短路求值、AST 级安全沙箱 | `LiteExprEvaluator` / `LiteExprCompiler` / `TreeInterpreter` / `LiteExprSandbox` |
| **规则链编排** | 8 种语义：THEN/WHEN/IF/ELIF/SWITCH/FOR/WHILE/BREAK，支持 DSL + DAG 可视化画布 | `RuleChain` / `RuleChainGraph` / `RuleChainDslParser` |
| **多级缓存** | Caffeine（L1 本地）+ Redis（L2 分布式）装饰器模式 | `CachingRuleConfigProvider` |
| **热加载** | DB / Nacos / Apollo / ZooKeeper / Redis / File 多源动态刷新 | `RuleHotReloader` / `RuleSourceManager` |
| **版本管理** | 版本快照 + Diff + 一键回滚 | `RuleVersionRepository` / `RuleVersionDiffService` |
| **dry-run 仿真** | 不实际执行，只评估结果（不发布事件、不记录统计） | `RuleEngine.dryRun` |
| **正式评估** | 记录统计、发布事件、触发动作分发（P1-1 新增 evaluate 端点） | `RuleEngine.evaluate` |
| **多级审批流** | 草稿 → 审核 → 上线，支持 SINGLE/COUNTERSIGN/SEQUENCE 三种审批类型 | `RuleApprovalService` / `ApprovalFlow` |
| **灰度发布** | 按 `canaryRatio` 分流到候选版本，结果标记 `canary=true` | `RuleCanaryRouter` |
| **A/B 测试** | 自动回滚策略 + 效果评估 + 回滚历史 | `ABTestService` / `ABTestAutoRollbackProvider` |
| **规则冲突检测** | 条件重复 / 严重度矛盾 / 命名冲突，可阻塞保存 | `RuleConflictDetector` / `RuleConflictAnalyzer` |
| **执行回放** | 按 traceId / 版本 / 自定义表达式回放，生成差异报告（ADDED/REMOVED/SEVERITY_CHANGED） | `ExecutionReplayService` |
| **规则依赖** | 依赖关系管理 + 级联禁用预览 | `RuleDependencyProvider` |
| **规则模板市场** | 模板分类 / 行业模板 / 一键导入 | `RuleTemplateProvider` |
| **规则包市场** | 规则集打包 / 版本管理 / 批量更新 / 评分 | `RulePackProvider` |
| **断点调试** | IDE 风格在线调试：断点设置 / 单步 / 快照 / 恢复 | `DefaultBreakpointHook` / `BreakpointHook` |
| **业务测试用例** | 独立于 JUnit 的回归测试体系，存储 facts + expected，支持批量运行 | `RuleTestRunner` / `RuleTestCaseDO` |
| **压测** | QPS / P50 / P95 / P99 测量 | `RuleStressTestService` |
| **规则生命周期** | 休眠检测 / 高错误率检测 / 退役建议 / 一键回滚 | `RuleLifecycleService` |
| **规则文档生成** | Markdown / HTML 自动生成 | `RuleDocumentationService` |
| **规则+模型融合** | 规则可引用 `model.xxx`，模型不可用降级为纯规则 | `ModelInputRegistry` / `ModelInputProvider` |
| **动态事实采集** | 评估前从 DB/Redis/HTTP 采集事实，支持超时与降级 | `FactProviderRegistry` / `FactProvider` |
| **动作分发** | 规则触发后联动通知 / cronjob / 工作流 | `RuleActionDispatcher` / `RuleActionHandler` |
| **熔断器** | 每规则独立熔断，CLOSED → OPEN → HALF_OPEN → CLOSED | `RuleCircuitBreaker` |
| **超时控制** | 单规则执行超时，超时返回 not-triggered | `RuleTimeoutExecutor` |
| **索引模式** | 规则数 > 200 时按租户+环境+场景+互斥组+字段倒排自动启用 | `RuleIndexer` |
| **Micrometer 指标** | 评估次数 / 触发次数 / 错误数 / 耗时 P50/P95/P99 / 熔断状态 / 队列大小 | `MicrometerRuleMetrics` |
| **异步 Trace** | 队列缓冲 + 批量写入 + 背压丢弃 | `AsyncTraceRecorder` |
| **分布式执行** | 一致性哈希分片 + Redis Pub/Sub 广播 | `DistributedAutoConfiguration` / `ConsistentHashSharder` |
| **多环境隔离** | default/dev/staging/prod，仅放行匹配环境的规则 | `RuleEnvironment` |
| **声明式注解** | `@LiteRule` 标注 Spring Bean 自动注册 | `LiteRuleAnnotationRegistrar` |
| **DSL 解析** | YAML/JSON 规则与规则链 DSL | `RuleDslParser` / `RuleChainDslParser` |
| **自适应阈值** | 指标异常检测 + 阈值动态调整 | `ThresholdProvider` |
| **权限检查** | 规则操作权限校验 | `RulePermissionChecker` |
| **审计日志** | CREATE/UPDATE/TOGGLE/ROLLBACK/APPROVE/REJECT/IMPORT/EXPORT/DRY_RUN/REPLAY 等 13 种操作 | `RuleAuditLogService` |

### 3. 包结构（server 模块）

```
com.remisoft.literule.server
├── approval/        # 多级审批流（SINGLE/COUNTERSIGN/SEQUENCE）
├── audit/           # 审计日志
├── benchmark/       # 压测服务
├── cache/           # 多级缓存（Caffeine L1 + Redis L2）
├── calc/            # 计算类（信用评分 / 双费率利润）
├── cep/             # 复杂事件处理（CEPEngine / CEPPattern）
├── config/          # 自动配置 + 注解注册 + ABTest + 热加载 + 冲突检测
├── core/            # 引擎核心（DefaultRuleEngine / InferenceEngine / 熔断 / 超时 / 灰度 / 索引 / 生命周期 / 效果评估 / 文档生成 / 断点 / 异步 Trace / Micrometer 指标 / 并行评估 / 结果缓存）
├── distributed/     # 分布式（一致性哈希分片 + Redis 节点注册 + Pub/Sub 广播）
├── dsl/             # DSL 解析（规则 DSL + 规则链 DSL）
├── expr/            # LiteExpr 表达式引擎（词法 / 语法 / 编译缓存 / 求值 / 沙箱 / 函数注册 / 变量注册 / 校验 / 预览 / Trace）
├── impact/          # 影响分析
├── impl/            # 7 种规则实现
├── orchestrator/    # 规则链编排（RuleChain / RuleChainGraph / GraphValidator / ChainGraphConverter）
├── replay/          # 执行回放
├── sdk/             # LiteRuleClient 客户端构建器
├── security/        # 权限检查
├── spi/             # 配置源 SPI（DB / Nacos / Apollo / ZK / File）+ 动作处理器 + Trace 记录器 + 事实采集 + 模型输入 + 模板 / 包 / 依赖 / 决策表 / 决策树 / 评分卡 / 脚本 Provider
├── testing/         # 业务测试用例（RuleTestRunner / RuleTestReport）
├── util/            # 工具（冲突分析）
└── version/         # 版本管理（Diff 服务）
```

### 4. Web 层 Controller（7 个）

| Controller | 路径前缀 | 主要端点 |
|---|---|---|
| `RuleAdminController` | `/ruleEngine/rules` | 规则 CRUD / 启停 / 版本 / 回滚 / Dry-run / 表达式校验 / A-B 测试 / 多级审批 / Trace / 决策表 / 测试用例 / 模板市场 / 冲突检测 / 规则链画布 / 规则依赖 / 目录树 / 规则包市场 / 导入导出 / 批量操作 / 函数市场 / 压测 / 统计（50+ 端点） |
| `BreakpointController` | `/ruleEngine/breakpoints` | 断点设置 / 恢复 / 单步 / 挂起查询 / 快照管理 |
| `CEPController` | `/ruleEngine/cep` | 模式管理 / 事件推送 / 命中查询 / 统计 / 模式测试 |
| `RuleAuditLogController` | `/ruleEngine/audit` | 最近 / 按规则 / 按操作人 / 按操作 / 按时间范围 |
| `RuleDashboardController` | `/ruleEngine/dashboard` | 概览 / 趋势 / 分布 / Top 规则 / 实时 |
| `RuleDslController` | `/ruleEngine/dsl` | 校验 / 解析 / 导入 / 导出 / 预览 |
| `RuleVariableAdminController` | `/ruleEngine/variables` | 变量 CRUD / 刷新 / 可用查询 |

## 使用方式

### 1. 构建与部署

本模块是独立微服务，构建产物为 `remi-literule-web` 可执行 jar：

```bash
# 全量构建
mvn -pl remi-backend/remi-literule -am clean package

# 启动服务
java -jar remi-literule-web/target/remi-literule-web-1.0.0-SNAPSHOT.jar
```

> **外部服务调用规则引擎**：通过 REST API（`/ruleEngine/**`）或 Feign Client（待补齐 `remi-literule-api` 的 Feign 接口）调用，不直接依赖 server/web 子模块。

### 2. 声明式（注解方式，服务内部规则注册）

在实现了 `Rule` 接口的 Spring Bean 上标注 `@LiteRule`，启动时自动注册到引擎：

```java
@LiteRule
@Component
public class OverdueRule implements Rule {
    @Override
    public String getCode() { return "OVERDUE_001"; }

    @Override
    public RuleResult evaluate(RuleContext ctx) {
        boolean overdue = ctx.get("overdueDays", Integer.class) > 30;
        return overdue
            ? RuleResult.triggered("OVERDUE_001", "逾期超 30 天", RuleSeverity.HIGH)
            : RuleResult.notTriggered("OVERDUE_001");
    }
}
```

### 3. 编程式（API 方式，服务内部调用）

```java
@Autowired
private RuleEngine ruleEngine;

public void evaluate(Map<String, Object> facts) {
    RuleContext context = RuleContext.builder()
            .facts(facts)
            .scenario("RISK_CHECK")
            .tenantId("1")
            .traceId(UUID.randomUUID().toString())
            .build();
    List<RuleResult> results = ruleEngine.evaluate(context);
    // results 按严重度倒序
    RuleResult top = ruleEngine.topResult(context);
}
```

### 3.1 外部服务通过 REST API 调用（推荐）

外部微服务（如 `remi-project`）通过 HTTP 调用规则引擎：

```http
POST /ruleEngine/rules/dryRun
Content-Type: application/json

{
  "facts": { "margin": 0.08, "threshold": 0.15, "projectName": "XX项目" },
  "scenario": "RISK_CHECK",
  "tenantId": "1",
  "traceId": "req-xxx"
}
```

> **跨服务调用**：建议在 `remi-literule-api` 补齐 `@FeignClient` 接口（含 FallbackFactory），供 `remi-project` / `remi-userinfo` 等服务声明式调用。当前 api 模块仅含 DTO，尚无 Feign 客户端。

### 4. 表达式规则（LiteExpr）

表达式规则从 `RuleDefinition` 构建，条件表达式返回 boolean，支持 `${var}` 模板渲染：

```java
RuleDefinition def = RuleDefinition.builder()
        .code("MARGIN_LOW")
        .name("毛利率低于阈值")
        .conditionExpression("margin < threshold * 0.15")
        .severityExpression("margin < threshold * 0.05 ? 'CRITICAL' : 'HIGH'")
        .titleTemplate("项目 ${projectName} 毛利率 ${margin | #,##0.00%} 低于阈值")
        .build();
Rule rule = new ExpressionRule(def, evaluator);
ruleEngine.register(rule);
```

### 5. 规则链（DSL）

```yaml
chain:
  name: "项目审批链"
  nodes:
    - id: node1
      rule: RISK_CHECK
      type: THEN
    - id: node2
      rule: PROFIT_CHECK
      depends: node1
      type: IF
    - id: node3
      rule: COMPLIANCE_CHECK
      depends: node1, node2
      type: SWITCH
```

### 6. 规则 + 模型融合

启用后规则表达式可引用 `model.xxx`：

```yaml
remi:
  literule:
    model:
      enabled: true
      timeout-ms: 100
      fallback-on-error: true
```

```java
// 规则表达式：model.riskScore > 0.8 ? 'REJECT' : 'PASS'
```

## 配置

所有配置通过 `LiteRuleProperties` 定义，前缀 `remi.literule`。配置文件位于 `remi-literule-web/src/main/resources/`（`application.yml` + `bootstrap.yml`，待补齐）。

### 核心开关

| 配置 | 默认值 | 说明 |
|---|---|---|
| `remi.literule.auto-register-builtin-rules` | `true` | 自动注册内置规则 |
| `remi.literule.hot-reload-enabled` | `true` | 规则热加载（监听 `RuleConfigRefreshEvent`） |
| `remi.literule.stats-enabled` | `true` | 执行统计 |
| `remi.literule.dry-run-enabled` | `true` | dry-run 仿真 |
| `remi.literule.sandbox-enabled` | `true` | LiteExpr AST 级安全沙箱 |
| `remi.literule.trace-enabled` | `true` | 执行轨迹记录 |
| `remi.literule.trace-queue-capacity` | `5000` | 异步 Trace 队列容量 |
| `remi.literule.trace-batch-size` | `100` | 异步 Trace 批量写入大小 |
| `remi.literule.trace-flush-interval-ms` | `2000` | 异步 Trace 刷新间隔 |
| `remi.literule.rule-timeout-ms` | `0` | 单规则超时（0=不限制） |
| `remi.literule.canary-enabled` | `true` | 灰度路由 |
| `remi.literule.conflict-detection-enabled` | `true` | 规则冲突检测 |
| `remi.literule.conflict-detection-block-on-error` | `true` | ERROR 级冲突阻塞保存 |
| `remi.literule.environment` | `default` | 多环境隔离（default/dev/staging/prod） |
| `remi.literule.annotation-scan-base-packages` | 空 | `@LiteRule` / `@RuleDefinitionMeta` 扫描基包 |

### 熔断器

| 配置 | 默认值 | 说明 |
|---|---|---|
| `remi.literule.circuit-breaker-error-rate` | `0.5` | 熔断错误率阈值（0~1.0） |
| `remi.literule.circuit-breaker-min-evaluations` | `100` | 熔断最小评估次数 |
| `remi.literule.circuit-breaker-open-state-ms` | `30000` | OPEN 状态持续时间（对齐 Resilience4j） |

### 多级缓存

| 配置 | 默认值 | 说明 |
|---|---|---|
| `remi.literule.cache.enabled` | `true` | 启用多级缓存 |
| `remi.literule.cache.l1-ttl-seconds` | `60` | Caffeine L1 TTL |
| `remi.literule.cache.l1-max-size` | `1000` | L1 最大条数 |
| `remi.literule.cache.l2-enabled` | `true` | 启用 Redis L2（需 Redisson） |
| `remi.literule.cache.l2-ttl-seconds` | `300` | Redis L2 TTL |

### 数据源

| 配置 | 默认值 | 说明 |
|---|---|---|
| `remi.literule.rule-source.type` | `db` | `db` / `nacos` / `apollo` / `zookeeper` / `redis` / `file` |
| `remi.literule.rule-source.watch-enabled` | `true` | 启用 Watch 监听 |
| `remi.literule.rule-source.nacos.server-addr` | `127.0.0.1:8848` | Nacos 地址 |
| `remi.literule.rule-source.nacos.data-id` | `rule-definitions` | Data ID |
| `remi.literule.rule-source.nacos.group` | `DEFAULT_GROUP` | Group |
| `remi.literule.rule-source.apollo.namespace` | `rule-engine` | Apollo Namespace |
| `remi.literule.rule-source.zookeeper.connect-string` | `127.0.0.1:2181` | ZK 地址 |
| `remi.literule.rule-source.zookeeper.path` | `/literule/definitions` | ZK 节点路径 |

### 文件规则源（GitOps）

| 配置 | 默认值 | 说明 |
|---|---|---|
| `remi.literule.file-source.enabled` | `false` | 启用 YAML/JSON 文件加载 |
| `remi.literule.file-source.location` | `classpath:rules/` | 文件位置 |
| `remi.literule.file-source.watch` | `true` | WatchService 监听变更 |

### 分布式执行

| 配置 | 默认值 | 说明 |
|---|---|---|
| `remi.literule.distributed.enabled` | `false` | 启用一致性哈希分片 |
| `remi.literule.distributed.virtual-nodes` | `150` | 虚拟节点数 |
| `remi.literule.distributed.refresh-interval-ms` | `10000` | 节点列表刷新间隔 |
| `remi.literule.distributed.heartbeat-timeout-ms` | `30000` | 心跳超时 |
| `remi.literule.distributed.heartbeat-interval-ms` | `5000` | 心跳间隔 |

### 规则 + 模型融合

| 配置 | 默认值 | 说明 |
|---|---|---|
| `remi.literule.model.enabled` | `false` | 启用模型融合 |
| `remi.literule.model.timeout-ms` | `100` | 单模型调用超时 |
| `remi.literule.model.fallback-on-error` | `true` | 模型异常降级为纯规则 |
| `remi.literule.model.mock-enabled` | `false` | Mock 模型 |
| `remi.literule.model.mock-outputs` | 空 | Mock 输出 Map |

### 动态事实采集

| 配置 | 默认值 | 说明 |
|---|---|---|
| `remi.literule.fact.enabled` | `false` | 启用动态事实采集 |
| `remi.literule.fact.timeout-ms` | `200` | 单 provider 超时 |
| `remi.literule.fact.fallback-on-error` | `true` | provider 异常降级 |

### 高性能优化

| 配置 | 默认值 | 说明 |
|---|---|---|
| `remi.literule.performance.cache-enabled` | `false` | 评估结果缓存 |
| `remi.literule.performance.cache-ttl-seconds` | `300` | 缓存 TTL |
| `remi.literule.performance.cache-max-size` | `10000` | 缓存最大条目 |
| `remi.literule.performance.parallel-enabled` | `false` | 规则分组并行评估 |
| `remi.literule.performance.parallel-pool-size` | CPU 核数 | 并行池大小 |

### 生命周期管理

| 配置 | 默认值 | 说明 |
|---|---|---|
| `remi.literule.lifecycle.enabled` | `true` | 启用退役检测 |
| `remi.literule.lifecycle.dormant-min-evaluations` | `1000` | 休眠规则最小评估次数 |
| `remi.literule.lifecycle.high-error-rate-threshold` | `0.30` | 高错误率阈值 |
| `remi.literule.lifecycle.stale-disabled-days` | `90` | 长期停用天数 |
| `remi.literule.lifecycle.low-impact-trigger-rate` | `0.001` | 低影响触发率 |
| `remi.literule.lifecycle.min-sample-size` | `500` | 最小样本量 |

> **废弃配置**：`remi.literule.evaluator`（2.1.0 起 `@Deprecated`，仅保留 LiteExpr，不再支持引擎切换）

## 数据库

SQL 归属见项目级硬约束。本模块相关表分布在两个文件：

- [V1.0.0_literule.sql](../../deploy/sql/modules/V1.0.0_literule.sql) — `remi_rule_def` / `remi_rule_version_history` / `remi_rule_template` / `remi_rule_test_case` / `remi_rule_chain_graph` / `remi_rule_dependency` / `remi_rule_pack` / `remi_rule_pack_install` / `remi_rule_variable_def`
- [V1.0.0_project.sql](../../deploy/sql/modules/V1.0.0_project.sql) — `remi_rule_execution_trace` / `remi_rule_decision_table` / `remi_rule_canary_bucket` / `remi_rule_scorecard` / `remi_rule_decision_tree` / `remi_rule_script` / `remi_rule_ab_policy` / `remi_rule_ab_rollback`（物理 Mapper 在 project 模块，DDL 按硬约束归 literule.sql）

## 前端集成

前端页面位于 `remi-frontend/src/views/execution/rule-engine/`，共 14 个页面：

| 页面 | 功能 |
|---|---|
| `index.vue` | 规则列表 + 编辑 + Dry-run + 模板市场 + AI 生成 + 版本历史 |
| `designer.vue` | 规则链可视化编排画布（SVG + dagre 自动布局） |
| `decision-table-editor.vue` | 决策表编辑器 |
| `decision-tree-editor.vue` | 决策树编辑器 |
| `scorecard-editor.vue` | 评分卡编辑器 |
| `cep-pattern-editor.vue` | CEP 模式编辑器 |
| `dsl-manager.vue` | DSL 管理器 |
| `dependency-graph.vue` | 依赖关系图 |
| `dashboard.vue` | 监控大盘 |
| `traces.vue` | 执行轨迹 |
| `replay.vue` | 执行回放 |
| `audit-log.vue` | 审计日志 |
| `pack-market.vue` | 规则包市场 |
| `variables/index.vue` | 变量管理 |

前端 API 定义在 `remi-frontend/src/api/rule-engine/index.ts`，60+ 端点对应后端 7 个 Controller。

## SPI 扩展点

本模块通过 SPI 反转依赖，避免直接依赖 project / cronjob / workflow 等业务模块。核心 SPI 由本服务自身实现（作为独立微服务，所有 Provider 的默认/DB 实现都在 server 层）：

| SPI 接口 | 作用 | 实现 |
|---|---|---|
| `RuleConfigProvider` | 规则配置源 | `DbRuleSource` + `CachingRuleConfigProvider` 装饰 |
| `RuleVersionRepository` | 版本仓库 | server 层 DB 实现 |
| `RuleTemplateProvider` | 模板市场 | server 层 DB 实现 |
| `RuleConflictDetectorProvider` | 冲突检测 | server 层实现 |
| `DecisionTableEvalProvider` | 决策表评估 | server 层实现 |
| `RuleChainGraphProvider` | 规则链画布 | server 层 DB 实现 |
| `GraphExecutionProvider` | 画布执行 | server 层实现 |
| `RuleDependencyProvider` | 规则依赖 | server 层 DB 实现 |
| `RuleCategoryProvider` | 目录树 | server 层实现 |
| `ABTestAutoRollbackProvider` | A/B 自动回滚 | server 层实现 |
| `RulePackProvider` | 规则包 | server 层 DB 实现 |
| `FactProvider` | 动态事实采集 | 业务方实现（可跨服务 Feign 调用 project/userinfo） |
| `ModelInputProvider` | 模型输入 | 业务方实现（可对接外部模型服务） |
| `RuleActionHandler` | 动作处理器 | `DefaultAlertActionHandler` / `CronjobTriggerActionHandler`（optional）/ `WorkflowTriggerActionHandler`（optional） |
| `TraceRecorder` | Trace 持久化 | `AsyncTraceRecorder`（委托模式，DB 持久化） |
| `DashboardDataProvider` | 大盘数据 | server 层实现 |
| `ThresholdProvider` | 自适应阈值 | server 层实现 |
| `ReconcileDataProvider` | 对账 | 业务方实现 |
| `BudgetSnapshotProvider` | 预算快照 | 业务方实现（可跨服务 Feign 调用 finance/project） |
| `ApprovalRecordRepository` | 审批记录 | server 层 DB 实现 |

## 可选联动

server 模块通过 optional 依赖实现跨服务按需联动（规则触发后联动 cronjob / workflow）：

- `remi-cronjob-api`（optional）— classpath 存在时装配 `CronjobTriggerActionHandler`，规则触发可联动定时任务
- `remi-workflow-api`（optional）— classpath 存在时装配 `WorkflowTriggerActionHandler`，规则触发可联动工作流审批
- `micrometer-registry-prometheus`（optional）— classpath 存在时反射装配 `MicrometerRuleMetrics`，对接 Prometheus 监控

> 作为独立微服务，建议将 cronjob-api / workflow-api 从 optional 改为正式依赖（规则引擎服务本身需要触发定时任务和工作流），并补齐对应 Feign Client 配置。

## 测试

当前测试覆盖情况：

```bash
mvn -pl remi-backend/remi-literule -am test
```

> 本服务构建产物为 `remi-literule-web` 可执行 jar。

| 子模块 | 测试类数 | 覆盖范围 |
|---|---|---|
| `remi-literule-api` | 0 | — |
| `remi-literule-domain` | 0 | — |
| `remi-literule-infra` | 0 | — |
| `remi-literule-server` | 2 | `calc/DualRateProfitCalculatorTest`（双费率利润计算）、`calc/CreditScoreEvaluatorTest`（信用评分） |
| `remi-literule-web` | 0 | — |

> **现状说明**：核心引擎（DefaultRuleEngine / 熔断 / 超时 / 灰度）、6 种规则类型、LiteExpr 表达式、规则链、DSL、缓存、分布式等模块的单元测试**尚未补齐**，是后续优化的重点。

## 版本与变更

- **首发版本**：v1.0.0（2026-06-30）
- **当前版本**：`1.0.0-SNAPSHOT`（项目版本号统一为 1.0.0，详见 `.trae/rules/version-policy.md`）
- **变更需走 PR + Code Review**
- **跨服务回归**：任何修改需回归依赖规则引擎的 project / userinfo / agent 等服务（通过 REST API 或 Feign 调用）

---

> 本模块是**独立规则引擎微服务**，独立部署、独立 JVM 进程、注册到 Nacos。
> 自动装配入口：`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 注册 `LiteRuleAutoConfiguration`。
