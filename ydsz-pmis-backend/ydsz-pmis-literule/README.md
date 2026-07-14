# ydsz-pmis-literule

> 轻量规则引擎（Library） — 基于 DDD 分层的自研规则引擎，对标 Drools + LiteFlow + 滴滴 Newton

## 模块定位

| 属性 | 值 |
|---|---|
| **类型** | 库（**不独立部署**） |
| **作用** | 被 `ydsz-pmis-project` / `ydsz-pmis-userinfo` 等业务服务依赖 |
| **构建顺序** | 3/10（Maven 构建第 3 个） |
| **JVM 进程** | 无（仅作为 jar 依赖） |
| **启动方式** | 通过 `AutoConfiguration.imports` 被宿主服务自动装配，无 `@SpringBootApplication` |
| **当前版本** | `1.0.0-SNAPSHOT`（与 parent `ydsz-pmis-parent` 对齐） |

## 分层结构（DDD 五层）

```
ydsz-pmis-literule/
├── ydsz-pmis-literule-api        # 对外 SPI/DTO：Rule / RuleEngine / RuleContext 等
├── ydsz-pmis-literule-domain     # 领域层：实体 DO、领域事件、注解、ModelInputProvider
├── ydsz-pmis-literule-infra      # 基础设施：MyBatis Mapper、决策表 Excel 导入导出
├── ydsz-pmis-literule-server     # 应用服务 + 引擎核心：DefaultRuleEngine、LiteExpr、热加载、CEP、回放、审批
└── ydsz-pmis-literule-web        # Web 层：7 个 REST Controller
```

依赖方向（严格单向）：`web → server → infra → domain → api`

## 核心职责

本模块是 PMIS 的**轻量级规则引擎**，覆盖规则定义、编排、评估、灰度、回放、审批全生命周期。

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
com.njydsz.pmis.literule.server
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

### 1. Maven 依赖

宿主服务（如 `ydsz-pmis-project`）引入 server 子模块：

```xml
<dependency>
  <groupId>com.njydsz.pmis</groupId>
  <artifactId>ydsz-pmis-literule-server</artifactId>
  <version>${project.version}</version>
</dependency>
<!-- 如需 REST Controller，再引入 web 子模块 -->
<dependency>
  <groupId>com.njydsz.pmis</groupId>
  <artifactId>ydsz-pmis-literule-web</artifactId>
  <version>${project.version}</version>
</dependency>
```

> **注意**：`ydsz-pmis-literule` 是父 pom（packaging=pom），不可直接作为依赖。需引用具体子模块。

### 2. 声明式（注解方式）

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

### 3. 编程式（API 方式）

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
pmis:
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

所有配置通过 `LiteRuleProperties` 定义，前缀 `pmis.literule`。本模块作为库，配置由宿主服务的 `application.yml` 提供。

### 核心开关

| 配置 | 默认值 | 说明 |
|---|---|---|
| `pmis.literule.auto-register-builtin-rules` | `true` | 自动注册内置规则 |
| `pmis.literule.hot-reload-enabled` | `true` | 规则热加载（监听 `RuleConfigRefreshEvent`） |
| `pmis.literule.stats-enabled` | `true` | 执行统计 |
| `pmis.literule.dry-run-enabled` | `true` | dry-run 仿真 |
| `pmis.literule.sandbox-enabled` | `true` | LiteExpr AST 级安全沙箱 |
| `pmis.literule.trace-enabled` | `true` | 执行轨迹记录 |
| `pmis.literule.trace-queue-capacity` | `5000` | 异步 Trace 队列容量 |
| `pmis.literule.trace-batch-size` | `100` | 异步 Trace 批量写入大小 |
| `pmis.literule.trace-flush-interval-ms` | `2000` | 异步 Trace 刷新间隔 |
| `pmis.literule.rule-timeout-ms` | `0` | 单规则超时（0=不限制） |
| `pmis.literule.canary-enabled` | `true` | 灰度路由 |
| `pmis.literule.conflict-detection-enabled` | `true` | 规则冲突检测 |
| `pmis.literule.conflict-detection-block-on-error` | `true` | ERROR 级冲突阻塞保存 |
| `pmis.literule.environment` | `default` | 多环境隔离（default/dev/staging/prod） |
| `pmis.literule.annotation-scan-base-packages` | 空 | `@LiteRule` / `@RuleDefinitionMeta` 扫描基包 |

### 熔断器

| 配置 | 默认值 | 说明 |
|---|---|---|
| `pmis.literule.circuit-breaker-error-rate` | `0.5` | 熔断错误率阈值（0~1.0） |
| `pmis.literule.circuit-breaker-min-evaluations` | `100` | 熔断最小评估次数 |
| `pmis.literule.circuit-breaker-open-state-ms` | `30000` | OPEN 状态持续时间（对齐 Resilience4j） |

### 多级缓存

| 配置 | 默认值 | 说明 |
|---|---|---|
| `pmis.literule.cache.enabled` | `true` | 启用多级缓存 |
| `pmis.literule.cache.l1-ttl-seconds` | `60` | Caffeine L1 TTL |
| `pmis.literule.cache.l1-max-size` | `1000` | L1 最大条数 |
| `pmis.literule.cache.l2-enabled` | `true` | 启用 Redis L2（需 Redisson） |
| `pmis.literule.cache.l2-ttl-seconds` | `300` | Redis L2 TTL |

### 数据源

| 配置 | 默认值 | 说明 |
|---|---|---|
| `pmis.literule.rule-source.type` | `db` | `db` / `nacos` / `apollo` / `zookeeper` / `redis` / `file` |
| `pmis.literule.rule-source.watch-enabled` | `true` | 启用 Watch 监听 |
| `pmis.literule.rule-source.nacos.server-addr` | `127.0.0.1:8848` | Nacos 地址 |
| `pmis.literule.rule-source.nacos.data-id` | `rule-definitions` | Data ID |
| `pmis.literule.rule-source.nacos.group` | `DEFAULT_GROUP` | Group |
| `pmis.literule.rule-source.apollo.namespace` | `rule-engine` | Apollo Namespace |
| `pmis.literule.rule-source.zookeeper.connect-string` | `127.0.0.1:2181` | ZK 地址 |
| `pmis.literule.rule-source.zookeeper.path` | `/literule/definitions` | ZK 节点路径 |

### 文件规则源（GitOps）

| 配置 | 默认值 | 说明 |
|---|---|---|
| `pmis.literule.file-source.enabled` | `false` | 启用 YAML/JSON 文件加载 |
| `pmis.literule.file-source.location` | `classpath:rules/` | 文件位置 |
| `pmis.literule.file-source.watch` | `true` | WatchService 监听变更 |

### 分布式执行

| 配置 | 默认值 | 说明 |
|---|---|---|
| `pmis.literule.distributed.enabled` | `false` | 启用一致性哈希分片 |
| `pmis.literule.distributed.virtual-nodes` | `150` | 虚拟节点数 |
| `pmis.literule.distributed.refresh-interval-ms` | `10000` | 节点列表刷新间隔 |
| `pmis.literule.distributed.heartbeat-timeout-ms` | `30000` | 心跳超时 |
| `pmis.literule.distributed.heartbeat-interval-ms` | `5000` | 心跳间隔 |

### 规则 + 模型融合

| 配置 |