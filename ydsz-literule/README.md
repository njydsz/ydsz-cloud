# ydsz-literule

> 独立规则引擎微服务 — 基于 DDD 分层的自研规则引擎

## 模块定位

| 属性 | 值 |
|---|---|
| **类型** | 独立微服务（**独立部署、独立 JVM 进程**） |
| **端口** | **9007**（按构建顺序 8/10） |
| **作用** | YDSZ 的规则引擎中心服务，提供规则定义、编排、评估、灰度、回放、审批全生命周期能力；通过 REST API 对内对外提供规则决策服务 |
| **构建顺序** | 8/10（Maven 构建） |
| **JVM 进程** | 独立 JVM 进程，独立端口，注册到 Nacos |
| **服务注册** | Nacos Discovery（服务名 `ydsz-literule`） |
| **配置中心** | Nacos Config（`spring-cloud-starter-alibaba-nacos-config`） |
| **当前版本** | `1.0.0-SNAPSHOT`（项目版本号统一为 1.0.0） |
| **脚手架状态** | ✅ 已包含 `@SpringBootApplication` 启动类、`application.yml` / `bootstrap.yml`，可独立部署 |

## 分层结构（DDD 六层）

```
ydsz-literule/
├── ydsz-literule-api        # 对外 SPI/DTO：Rule / RuleEngine / RuleContext / 表达式引擎 SPI、Feign Client
├── ydsz-literule-domain     # 领域层：VO、枚举、领域事件、@LiteRule 注解、ModelInputProvider、Repository 接口
├── ydsz-literule-infra      # 基础设施：MyBatis Mapper + XML、Entity DO、Repository 实现、决策表 Excel 导入导出
├── ydsz-literule-server     # 应用服务 + 引擎核心：DefaultRuleEngine、LiteExpr、热加载、CEP、回放、审批、SPI Provider 实现
├── ydsz-literule-web        # Web 层：22 个 REST Controller + Spring Boot 启动类 LiteruleApplication
└── ydsz-literule-app        # App 基座：自动配置（LiteRuleAppAutoConfiguration）、健康检查、OpenAPI 预留
```

依赖方向（严格单向）：`web → server → domain → api`，`web → infra`（通过 Spring 自动装配注入 Repository 实现），`app → domain → api`

> **说明**：server 层不直接依赖 infra 层（移除 server → infra 的直接依赖以符合 DDD 分层），infra 层由 web 层引入并通过 Spring 自动装配注入 Repository 实现。

## 核心职责

本模块是 YDSZ 的**轻量级规则引擎**，覆盖规则定义、编排、评估、灰度、回放、审批全生命周期。

### 1. 6 种规则类型

| 类型 | 实现类 | 适用场景 |
|---|---|---|
| **Expression** 表达式 | `ExpressionRule` | 基于 LiteExpr 表达式动态评估，支持 `${var}` 模板渲染与动态严重度 |
| **DecisionTable** 决策表 | `DecisionTableRule` | 二维表规则，支持 Excel 导入导出 |
| **DecisionTree** 决策树 | `DecisionTreeRule` | 多层 if-else 树规则 |
| **Scorecard** 评分卡 | `ScorecardRule` | 多维加权评分 |
| **Script** 脚本 | `ScriptRule` | JSR-223 脚本规则 |
| **Static** 静态 | `StaticRule` | 静态常量规则 |
| **CEP** 复杂事件处理 | `CEPEngine` | 滑动窗口 + 模式匹配（如"30 分钟内 5 次失败"），独立引擎，非 Rule 实现 |

### 2. 核心能力清单

| 能力 | 说明 | 关键类 |
|---|---|---|
| **LiteExpr 表达式引擎** | 2.0.0 起自研，零外部依赖；含词法/语法分析、AST 编译缓存、常量折叠、短路求值、AST 级安全沙箱 | `LiteExprEngine` / `LiteExprCompiler` / `TreeInterpreter` / `LiteExprSandbox` |
| **规则链编排** | 5 种语义：THEN/WHEN/IF/ELIF/SWITCH，支持 DSL + DAG 可视化画布 | `RuleChain` / `RuleChainGraph` / `RuleChainDslParser` |
| **多级缓存** | Caffeine（L1 本地）+ Redis（L2 分布式）装饰器模式 | `CachingRuleConfigProvider` |
| **热加载** | DB / Nacos / Apollo / ZooKeeper / Redis / File 多源动态刷新 | `RuleHotReloader` / `RuleSourceManager` |
| **版本管理** | 版本快照 + Diff + 一键回滚 | `RuleVersionRepository` / `RuleVersionDiffService` |
| **dry-run 仿真** | 不实际执行，只评估结果（不发布事件、不记录统计） | `RuleEngine.dryRun` |
| **正式评估** | 记录统计、发布事件、触发动作分发 | `RuleEngine.evaluate` |
| **多级审批流** | 草稿 → 审核 → 上线，支持 SINGLE/COUNTERSIGN/SEQUENCE 三种审批类型 | `RuleApprovalService` / `ApprovalFlow` |
| **灰度发布** | 按 `canaryRatio` 分流到候选版本，结果标记 `canary=true` | `RuleCanaryRouter` |
| **A/B 测试** | 自动回滚策略 + 效果评估 + 回滚历史 | `ABTestService` / `ABTestAutoRollbackProvider` |
| **规则冲突检测** | 条件重复 / 严重度矛盾 / 命名冲突，可阻塞保存 | `RuleConflictDetector` / `RuleConflictAnalyzer` |
| **执行回放** | 按 traceId / 版本 / 自定义表达式回放，生成差异报告（ADDED/REMOVED/SEVERITY_CHANGED） | `ExecutionReplayService` |
| **规则依赖** | 依赖关系管理 + 级联禁用预览 | `RuleDependencyProvider` |
| **规则模板市场** | 模板分类 / 行业模板 / 一键导入 | `RuleTemplateProvider` |
| **规则包市场** | 规则集打包 / 版本管理 / 批量更新 / 评分 | `RulePackProvider` |
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
| **断点调试** | 规则级/表达式节点级断点、调试会话、单步执行（RESUME/STEP_OVER/STEP_INTO/STEP_OUT/TERMINATE） | `RuleDebugger` / `DebugSession` / `Breakpoint` |
| **多环境隔离** | default/dev/staging/prod，仅放行匹配环境的规则 | `RuleEnvironment` |
| **声明式注解** | `@LiteRule` 标注 Spring Bean 自动注册 | `LiteRuleAnnotationRegistrar` |
| **DSL 解析** | YAML/JSON 规则与规则链 DSL | `RuleDslParser` / `RuleChainDslParser` |
| **自适应阈值** | 指标异常检测 + 阈值动态调整 | `ThresholdProvider` |
| **权限检查** | 规则操作权限校验 | `RulePermissionChecker` |
| **审计日志** | CREATE/UPDATE/TOGGLE/ROLLBACK/APPROVE/REJECT/IMPORT/EXPORT/DRY_RUN/REPLAY 等 13 种操作 | `RuleAuditLogService` |

### 3. 包结构（server 模块）

```
com.njydsz.literule.server
├── approval/        # 多级审批流（SINGLE/COUNTERSIGN/SEQUENCE）
├── audit/           # 审计日志
├── benchmark/       # 压测服务
├── cache/           # 多级缓存（Caffeine L1 + Redis L2）
├── cep/             # 复杂事件处理（CEPEngine / CEPPattern）
├── config/          # 自动配置 + 注解注册 + ABTest + 热加载 + 冲突检测
├── core/            # 引擎核心（DefaultRuleEngine / 熔断 / 超时 / 灰度 / 索引 / 生命周期 / 异步 Trace / Micrometer 指标 / 并行评估 / 结果缓存 / 统计 / Trace 构建）
├── engine/          # 引擎子包（内含 liteexpr/ 子目录）
├── debug/           # 断点调试（RuleDebugger / DebugSession / Breakpoint / 单步执行）
├── distributed/     # 分布式（一致性哈希分片 + Redis 节点注册 + Pub/Sub 广播）
├── dsl/             # DSL 解析（规则 DSL + 规则链 DSL）
├── engine/liteexpr/ # LiteExpr 表达式引擎（词法 / 语法 / 编译缓存 / 求值 / 沙箱 / 函数注册 / 变量注册 / 校验 / 预览 / Trace）
├── expression/      # 表达式规则
├── impl/            # 6 种规则实现
├── orchestrator/    # 规则链编排（RuleChain / RuleChainGraph / GraphValidator / ChainGraphConverter）
├── replay/          # 执行回放
├── sdk/             # LiteRuleClient 客户端构建器
├── security/        # 权限检查
├── spi/             # 配置源 SPI（DB / Nacos / Apollo / ZK / File）+ 动作处理器 + Trace 记录器 + 事实采集 + 模型输入 + 模板 / 包 / 依赖 / 决策表 / 决策树 / 评分卡 / 脚本 Provider
├── testing/         # 业务测试用例（RuleTestRunner / RuleTestReport）
└── version/         # 版本管理（Diff 服务）
```

> 其余目录：`health/`（健康检查）、`json/`、`listener/`、`metrics/`、`search/`（规则搜索）等。

### 4. Web 层 Controller（22 个）

> 所有 Controller 的 `@RequestMapping` 均以 `/v1/rule-engine` 为根前缀。大部分管理类接口挂载在 `/v1/rule-engine/rules` 下，通过子路径区分功能；CEP / Dashboard / Debug / DSL / Audit / Variables 各自使用独立子前缀。

| Controller | `@RequestMapping` | 主要端点 |
|---|---|---|
| `RuleAdminController` | `/v1/rule-engine/rules` | 规则 CRUD / 启停 / 版本 / 回滚 / Dry-run / 表达式校验 / 版本 Diff |
| `RuleABPolicyController` | `/v1/rule-engine/rules` | A/B 测试策略 CRUD / 回滚 |
| `RuleBatchController` | `/v1/rule-engine/rules` | 批量启停 / 批量修改优先级 / 批量修改分类 |
| `RuleCategoryController` | `/v1/rule-engine/rules` | 目录树 CRUD |
| `RuleConflictController` | `/v1/rule-engine/rules` | 冲突检测 / 冲突分析 |
| `RuleDecisionTableController` | `/v1/rule-engine/rules` | 决策表 CRUD / 行级增删改 |
| `RuleDependencyController` | `/v1/rule-engine/rules` | 规则依赖 CRUD / 级联禁用预览 |
| `RuleGraphController` | `/v1/rule-engine/rules` | 规则链画布查询保存 / 表达式函数市场 / 画布 Dry-run |
| `RuleImportExportController` | `/v1/rule-engine/rules` | 规则导入导出（Excel/JSON） |
| `RuleLifecycleController` | `/v1/rule-engine/rules` | 规则状态变更 / 多级审批流（提交/审批/驳回/撤回） |
| `RulePackController` | `/v1/rule-engine/rules` | 规则包市场（发布/安装/搜索/压测） |
| `RuleTemplateController` | `/v1/rule-engine/rules` | 规则模板市场 CRUD / 一键导入 |
| `RuleTraceController` | `/v1/rule-engine/rules` | 执行回放 / 影响预览 |
| `RuleTestCaseController` | `/v1/rule-engine/rules` | 业务测试用例 CRUD / 批量运行 |
| `RuleDslController` | `/v1/rule-engine/dsl` | DSL 校验 / 解析 / 预览 |
| `RuleDslImportExportController` | `/v1/rule-engine/dsl` | DSL 导入导出 |
| `RuleVariableAdminController` | `/v1/rule-engine/variables` | 变量 CRUD / 刷新 |
| `CEPController` | `/v1/rule-engine/cep` | CEP 模式管理 / 事件投递 / 命中查询 / 引擎状态 |
| `CEPTestController` | `/v1/rule-engine/cep` | CEP 模式测试 / 模拟事件流 |
| `RuleAuditLogController` | `/v1/rule-engine/audit` | 审计日志查询（按规则/按时间/最近 N 条） |
| `RuleDashboardController` | `/v1/rule-engine/dashboard` | 概览 / 趋势 / 分布 / Top 规则 / 实时指标 |
| `RuleDebugController` | `/v1/rule-engine/debug` | 断点管理 / 调试会话 / 单步执行（RESUME/STEP_OVER/STEP_INTO/STEP_OUT/TERMINATE） |

## 使用方式

### 1. 构建与部署

本模块是独立微服务，构建产物为 `ydsz-literule-web` 可执行 jar：

```bash
# 全量构建（从 ydsz-cloud 根目录执行）
mvn -pl ydsz-literule -am clean package

# 启动服务
java -jar ydsz-literule-web/target/ydsz-literule-web-1.0.0-SNAPSHOT.jar
```

> **外部服务调用规则引擎**：通过 REST API（`/v1/rule-engine/**`）或 Feign Client（`LiteRuleClient`，已就绪）调用，不直接依赖 server/web 子模块。

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
    List<RuleResult> Response = ruleEngine.evaluate(context);
    // Response 按严重度倒序
    RuleResult top = ruleEngine.topResult(context);
}
```

### 3.1 外部服务通过 REST API 调用（推荐）

外部微服务（如 `ydsz-userinfo` 等）通过 HTTP 调用规则引擎：

```http
POST /v1/rule-engine/rules/dry-run
Content-Type: application/json

{
  "facts": { "margin": 0.08, "threshold": 0.15, "projectName": "XX项目" },
  "scenario": "RISK_CHECK",
  "tenantId": "1",
  "traceId": "req-xxx"
}
```

> **跨服务调用**：`ydsz-literule-api` 已提供 `LiteRuleClient`（`@FeignClient` + `LiteRuleClientFallback`），供其他服务声明式调用。

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
ydsz:
  literule:
    model:
      enabled: true
      timeout-ms: 100
      fallback-on-error: true
```

```java
// 规则表达式：model.score > 0.8 ? 'REJECT' : 'PASS'
```

## 配置

所有配置通过 `LiteRuleProperties` 定义，前缀 `ydsz.literule`。配置文件位于 `ydsz-literule-web/src/main/resources/`（`application.yml` + `bootstrap.yml`，配置已就绪）。

### 核心开关

| 配置 | 默认值 | 说明 |
|---|---|---|
| `ydsz.literule.auto-register-builtin-rules` | `true` | 自动注册内置规则 |
| `ydsz.literule.hot-reload-enabled` | `true` | 规则热加载（监听 `RuleConfigRefreshEvent`） |
| `ydsz.literule.stats-enabled` | `true` | 执行统计 |
| `ydsz.literule.dry-run-enabled` | `true` | dry-run 仿真 |
| `ydsz.literule.sandbox-enabled` | `true` | LiteExpr AST 级安全沙箱 |
| `ydsz.literule.trace-enabled` | `true` | 执行轨迹记录 |
| `ydsz.literule.trace-queue-capacity` | `5000` | 异步 Trace 队列容量 |
| `ydsz.literule.trace-batch-size` | `100` | 异步 Trace 批量写入大小 |
| `ydsz.literule.trace-flush-interval-ms` | `2000` | 异步 Trace 刷新间隔 |
| `ydsz.literule.rule-timeout-ms` | `0` | 单规则超时（0=不限制） |
| `ydsz.literule.canary-enabled` | `true` | 灰度路由 |
| `ydsz.literule.conflict-detection-enabled` | `true` | 规则冲突检测 |
| `ydsz.literule.conflict-detection-block-on-error` | `true` | ERROR 级冲突阻塞保存 |
| `ydsz.literule.environment` | `default` | 多环境隔离（default/dev/staging/prod） |
| `ydsz.literule.annotation-scan-base-packages` | 空 | `@LiteRule` / `@RuleDefinitionMeta` 扫描基包 |
| `ydsz.literule.health-enabled` | `true` | 启用健康检查（`LiteRuleHealthIndicator`） |

### 熔断器

| 配置 | 默认值 | 说明 |
|---|---|---|
| `ydsz.literule.circuit-breaker-error-rate` | `0.5` | 熔断错误率阈值（0~1.0） |
| `ydsz.literule.circuit-breaker-min-evaluations` | `100` | 熔断最小评估次数 |
| `ydsz.literule.circuit-breaker-open-state-ms` | `30000` | OPEN 状态持续时间（对齐 Resilience4j） |

### 多级缓存

| 配置 | 默认值 | 说明 |
|---|---|---|
| `ydsz.literule.cache.enabled` | `true` | 启用多级缓存 |
| `ydsz.literule.cache.l1-ttl-seconds` | `60` | Caffeine L1 TTL |
| `ydsz.literule.cache.l1-max-size` | `1000` | L1 最大条数 |
| `ydsz.literule.cache.l2-enabled` | `true` | 启用 Redis L2（需 Redisson） |
| `ydsz.literule.cache.l2-ttl-seconds` | `300` | Redis L2 TTL |

### 数据源

| 配置 | 默认值 | 说明 |
|---|---|---|
| `ydsz.literule.rule-source.type` | `db` | `db` / `nacos` / `apollo` / `zookeeper` / `redis` / `file` |
| `ydsz.literule.rule-source.watch-enabled` | `true` | 启用 Watch 监听 |
| `ydsz.literule.rule-source.nacos.server-addr` | `127.0.0.1:8848` | Nacos 地址 |
| `ydsz.literule.rule-source.nacos.data-id` | `rule-definitions` | Data ID |
| `ydsz.literule.rule-source.nacos.group` | `DEFAULT_GROUP` | Group |
| `ydsz.literule.rule-source.apollo.namespace` | `rule-engine` | Apollo Namespace |
| `ydsz.literule.rule-source.zookeeper.connect-string` | `127.0.0.1:2181` | ZK 地址 |
| `ydsz.literule.rule-source.zookeeper.path` | `/literule/definitions` | ZK 节点路径 |

### 文件规则源（GitOps）

| 配置 | 默认值 | 说明 |
|---|---|---|
| `ydsz.literule.file-source.enabled` | `false` | 启用 YAML/JSON 文件加载 |
| `ydsz.literule.file-source.location` | `classpath:rules/` | 文件位置 |
| `ydsz.literule.file-source.watch` | `true` | WatchService 监听变更 |

### 分布式执行

| 配置 | 默认值 | 说明 |
|---|---|---|
| `ydsz.literule.distributed.enabled` | `false` | 启用一致性哈希分片 |
| `ydsz.literule.distributed.virtual-nodes` | `150` | 虚拟节点数 |
| `ydsz.literule.distributed.refresh-interval-ms` | `10000` | 节点列表刷新间隔 |
| `ydsz.literule.distributed.heartbeat-timeout-ms` | `30000` | 心跳超时 |
| `ydsz.literule.distributed.heartbeat-interval-ms` | `5000` | 心跳间隔 |

### 规则 + 模型融合

| 配置 | 默认值 | 说明 |
|---|---|---|
| `ydsz.literule.model.enabled` | `false` | 启用模型融合 |
| `ydsz.literule.model.timeout-ms` | `100` | 单模型调用超时 |
| `ydsz.literule.model.fallback-on-error` | `true` | 模型异常降级为纯规则 |
| `ydsz.literule.model.mock-enabled` | `false` | Mock 模型 |
| `ydsz.literule.model.mock-outputs` | 空 | Mock 输出 Map |

### 动态事实采集

| 配置 | 默认值 | 说明 |
|---|---|---|
| `ydsz.literule.fact.enabled` | `false` | 启用动态事实采集 |
| `ydsz.literule.fact.timeout-ms` | `200` | 单 provider 超时 |
| `ydsz.literule.fact.fallback-on-error` | `true` | provider 异常降级 |

### 高性能优化

| 配置 | 默认值 | 说明 |
|---|---|---|
| `ydsz.literule.performance.cache-enabled` | `false` | 评估结果缓存 |
| `ydsz.literule.performance.cache-ttl-seconds` | `300` | 缓存 TTL |
| `ydsz.literule.performance.cache-max-size` | `10000` | 缓存最大条目 |
| `ydsz.literule.performance.parallel-enabled` | `false` | 规则分组并行评估 |
| `ydsz.literule.performance.parallel-pool-size` | CPU 核数 | 并行池大小 |
| `ydsz.literule.performance.parallel-threshold` | `50` | 触发并行的规则数阈值 |
| `ydsz.literule.performance.slow-rule-threshold-ms` | `0` | 慢规则检测阈值（0=关闭） |

### CEP 引擎

| 配置 | 默认值 | 说明 |
|---|---|---|
| `ydsz.literule.cep.enabled` | `true` | 启用 CEP 复杂事件处理引擎 |

### 生命周期管理

| 配置 | 默认值 | 说明 |
|---|---|---|
| `ydsz.literule.lifecycle.enabled` | `true` | 启用退役检测 |
| `ydsz.literule.lifecycle.dormant-min-evaluations` | `1000` | 休眠规则最小评估次数 |
| `ydsz.literule.lifecycle.high-error-rate-threshold` | `0.30` | 高错误率阈值 |
| `ydsz.literule.lifecycle.stale-disabled-days` | `90` | 长期停用天数 |
| `ydsz.literule.lifecycle.low-impact-trigger-rate` | `0.001` | 低影响触发率 |
| `ydsz.literule.lifecycle.min-sample-size` | `500` | 最小样本量 |

> **废弃配置**：`ydsz.literule.evaluator`（2.1.0 起 `@Deprecated`，仅保留 LiteExpr，不再支持引擎切换）

## 数据库

实体 `@TableName` 共映射 **17 张表**（`ydsz_rule_def` / `ydsz_rule_version_history` / `ydsz_rule_template` / `ydsz_rule_test_case` / `ydsz_rule_chain_graph` / `ydsz_rule_dependency` / `ydsz_rule_pack` / `ydsz_rule_pack_install` / `ydsz_rule_variable_def` / `ydsz_rule_execution_trace` / `ydsz_rule_decision_table` / `ydsz_rule_canary_bucket` / `ydsz_rule_scorecard` / `ydsz_rule_decision_tree` / `ydsz_rule_script` / `ydsz_rule_ab_policy` / `ydsz_rule_ab_rollback`），DDL 由各部署环境统一维护，不在仓库内提供 SQL 脚本。

## SPI 扩展点

本模块通过 SPI 反转依赖，避免直接依赖 cronjob / workflow 等业务模块。核心 SPI 由本服务自身实现（作为独立微服务，所有 Provider 的默认/DB 实现都在 server 层）：

| SPI 接口 | 作用 | 实现 |
|---|---|---|
| `RuleConfigProvider` | 规则配置源 | `DbRuleSource`（默认）+ `CachingRuleConfigProvider` 装饰 |
| `RuleSource` | 多源规则加载 | `DbRuleSource` / `NacosRuleSource` / `ApolloRuleSource` / `ZookeeperRuleSource` / `FileRuleSource` |
| `RuleSourceManager` | 规则源管理器 | 聚合多源、热加载调度 |
| `RuleVersionRepository` | 版本仓库 | infra 层接口，server 层 DB 实现 |
| `RuleTemplateProvider` | 模板市场 | server 层 DB 实现 |
| `RuleConflictDetectorProvider` | 冲突检测 | server 层实现 |
| `DecisionTableEvalProvider` | 决策表评估 | API 层接口 |
| `DecisionTableConfigProvider` | 决策表配置 | server 层 DB 实现 |
| `DecisionTreeConfigProvider` | 决策树配置 | server 层 DB 实现 |
| `ScorecardConfigProvider` | 评分卡配置 | server 层 DB 实现 |
| `ScriptConfigProvider` | 脚本配置 | server 层 DB 实现 |
| `RuleChainGraphProvider` | 规则链画布 | server 层 DB 实现 |
| `GraphExecutionProvider` | 画布执行 | server 层实现 |
| `RuleDependencyProvider` | 规则依赖 | server 层 DB 实现 |
| `RuleCategoryProvider` | 目录树 | server 层实现 |
| `RuleSearchProvider` | 规则搜索 | server 层实现 |
| `ABTestAutoRollbackProvider` | A/B 自动回滚 | server 层实现 |
| `RulePackProvider` | 规则包 | server 层 DB 实现 |
| `RuleConfigBroadcaster` | 配置变更广播 | `RedisRuleConfigBroadcaster`（Pub/Sub） |
| `FactProvider` | 动态事实采集 | 业务方实现（可跨服务 Feign 调用 userinfo 等） |
| `ModelInputProvider` | 模型输入 | 业务方实现（可对接外部模型服务） |
| `RuleActionHandler` | 动作处理器 | `DefaultAlertActionHandler` / `CronjobTriggerActionHandler`（optional）/ `WorkflowTriggerActionHandler`（optional） |
| `RuleActionDispatcher` | 动作分发器 | server 层路由分发 |
| `TraceRecorder` | Trace 持久化 | `AsyncTraceRecorder`（委托模式，DB 持久化） |
| `DashboardDataProvider` | 大盘数据 | server 层实现 |
| `ThresholdProvider` | 自适应阈值 | server 层实现 |
| `ReconcileDataProvider` | 对账 | 业务方实现 |
| `BudgetSnapshotProvider` | 预算快照 | 业务方实现（可跨服务 Feign 调用外部系统） |
| `ApprovalRecordRepository` | 审批记录 | infra 层接口，server 层 DB 实现 |
| `LiteRuleClient` | Feign 声明式客户端 | API 层 `@FeignClient` + `LiteRuleClientFallback` |

## 可选联动

server 模块通过 optional 依赖实现跨服务按需联动（规则触发后联动 cronjob / workflow）：

- `ydsz-cronjob-api`（optional）— classpath 存在时装配 `CronjobTriggerActionHandler`，规则触发可联动定时任务
- `ydsz-workflow-api`（optional）— classpath 存在时装配 `WorkflowTriggerActionHandler`，规则触发可联动工作流审批
- `micrometer-registry-prometheus`（optional）— classpath 存在时反射装配 `MicrometerRuleMetrics`，对接 Prometheus 监控

> 作为独立微服务，建议将 cronjob-api / workflow-api 从 optional 改为正式依赖（规则引擎服务本身需要触发定时任务和工作流），并补齐对应 Feign Client 配置。

## 测试

当前测试覆盖情况：

```bash
mvn -pl ydsz-literule -am test
```

> 本服务构建产物为 `ydsz-literule-web` 可执行 jar。

| 子模块 | 测试类数 | 覆盖范围 |
|---|---|---|
| `ydsz-literule-api` | 0 | — |
| `ydsz-literule-app` | 0 | — |
| `ydsz-literule-domain` | 0 | — |
| `ydsz-literule-infra` | 0 | — |
| `ydsz-literule-server` | 2 | 核心引擎（`DefaultRuleEngineCoreTest`）+ 并行评估（`DefaultRuleEngineParallelTest`） |
| `ydsz-literule-web` | 0 | — |

> **现状说明**：评分卡 / 表达式引擎 / 热加载等专项测试、其余规则类型与 LiteExpr 的单元测试**尚未补齐**，是后续优化的重点。

## 版本与变更

- **首发版本**：1.0.0（2026-06-30）
- **当前版本**：`1.0.0-SNAPSHOT`（项目版本号统一为 1.0.0，详见 `.trae/rules/version-policy.md`）
- **变更需走 PR + Code Review**
- **跨服务回归**：任何修改需回归依赖规则引擎的 project / userinfo / agent 等服务（通过 REST API 或 Feign 调用）

---

> 本模块是**独立规则引擎微服务**，独立部署、独立 JVM 进程、注册到 Nacos。
> 自动装配入口：server 模块 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 注册 `LiteRuleAutoConfiguration`；app 模块同路径文件注册 `LiteRuleAppAutoConfiguration`。
