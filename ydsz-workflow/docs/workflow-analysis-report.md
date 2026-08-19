# ydsz-workflow 模块对标分析报告

> 基于最新代码（截至 2026-08-19）的全面分析，对标 Activiti / Flowable / Camunda / LiteFlow / Drools 及国内互联网大厂自研工作流规范，从架构优化、功能增强、性能提升、体验改善、过度设计五个维度输出可落地建议。

---

## 一、模块现状概览

| 维度 | 现状 |
|---|---|
| **代码规模** | 307 个 Java 文件 / 13,678 行（含测试） |
| **架构** | DDD 五层（api / app / domain / infra / server / web） |
| **核心引擎** | 自研 YDSZ-Flow v2 + BPMN 2.0 解析器（零外部依赖） |
| **Service 接口** | 37 个，ServiceImpl 20+ |
| **Controller** | 11 个 |
| **数据库表** | 21 张（流程定义/实例/任务/抄送/委派/审计等） |
| **依赖中间件** | Nacos / PostgreSQL / Redis / Seata（启用但未配 TC） |
| **关键能力** | 排他/包容/并行网关 + N/M join、多节点同退、SLA、自动催办、流程迁移、敏感脱敏、集群缓存广播、Prometheus 指标、SQL 防火墙 |
| **测试文件** | 5 个测试类（核心引擎 0 覆盖） |

### 已落地的设计亮点

1. **BPMN 2.0 自研解析**：基于 JDK DOM，禁用 DOCTYPE/外部实体（防 XXE），零第三方依赖
2. **流程定义缓存 + 集群广播失效**：基于 ydsz-common-cache（STRIPED），通过 Redis Pub/Sub 广播 evict
3. **分布式锁精细化**：实例操作锁 `flow:instance:op:{instanceId}`、任务操作锁 `flow:task:op:{taskId}`，waitTime/leaseTime 分级配置
4. **网关语义完整**：排他网关互斥、包容网关全匹配 + 默认出边、并行网关 join + N/M 聚合 + Redis 降级
5. **多租户 MULTI 模式**：tenant_id / company_id / dept_id 三级隔离
6. **SQL 安全加固**：防火墙（DDL/无 WHERE/多语句）、慢 SQL、SQL 审计、分页上限
7. **审计日志分区表**：ydsz_flow_audit_log 按月分区，pg_partman 归档

---

## 二、五维度问题清单（按 P0/P1/P2 优先级）

### 维度 1：架构优化

#### P0 级（编译/运行级缺陷，必须立即修复）

**A1. FlowProperties 缺失字段导致编译错误**

- **位置**：`FlowDefinitionCacheService.java:75-77, 82-84`
- **现象**：调用 `properties.getDefinitionCacheTtlMinutes()` 和 `properties.getDefinitionCacheMaxSize()`，但 `FlowProperties` 类中并无此二字段，application.yml 中也无对应配置
- **影响**：模块**无法通过编译**，或为重构残留（注释说"P1-2: 硬编码值迁移至 YAML"，但 YAML 和 Properties 都未补）
- **修复**：在 FlowProperties 中补 `definitionCacheTtlMinutes`（默认 60）和 `definitionCacheMaxSize`（默认 1000）字段，并在 application.yml `ydsz.flow` 下补配置

**A2. 所有 12 个 Mapper XML 的 resultMap type 引用错误**

- **位置**：`ydsz-workflow-infra/src/main/resources/mapper/*.xml`（全部 12 个文件第 5 行）
- **现象**：resultMap type 引用 `com.njydsz.workflow.domain.entity.FlowInstance` 等不存在路径，实际实体在 `com.njydsz.workflow.infra.entity.FlowInstanceDO`
- **影响**：依赖 MyBatis typeAlias 兜底（推测 nacos 共享配置中可能配了 alias），但当前 application.yml 未见 `mybatis-plus.type-aliases-package`，存在运行时映射失败风险
- **修复**：统一将 XML 中 type 改为完整路径 `com.njydsz.workflow.infra.entity.FlowInstanceDO`，或在 application.yml 显式声明 `mybatis-plus.type-aliases-package: com.njydsz.workflow.infra.entity`

**A3. application.yml warmup-classes 类路径全部错误**

- **位置**：`ydsz-workflow-web/src/main/resources/application.yml:53-61`
- **现象**：`ydsz.json.warmup-classes` 列出 8 个类（如 `com.njydsz.workflow.domain.entity.FlowDefinition`），但该包下并无 entity 子目录
- **影响**：YdszJson ASM 预热失效，首次请求延迟尖峰未消除
- **修复**：将类路径改为 `com.njydsz.workflow.infra.entity.FlowDefinitionDO` 等

**A4. 测试代码引用不存在的方法**

- **位置**：`FlowGraphValidatorTest.java:61-62`
- **现象**：调用 `skip.setSourceNodeCode(source)` 和 `skip.setTargetNodeCode(target)`，但 FlowSkipDO 仅有 `nextNodeCode` 字段，sourceRef 存于 ext JSON
- **影响**：测试类**无法编译**，CI 流水线必然失败
- **修复**：重写测试 createSkip 方法，使用 `skip.setNextNodeCode(target)` 并通过 ext JSON 设置 sourceRef，或改造 FlowSkipDO 增加 transient 字段供测试构造

**A5. README doc drift 严重**

- **位置**：`ydsz-workflow/README.md`
- **矛盾清单**：
  - 第 196 行"当前模块暂无单元测试" ↔ 实际 5 个测试类
  - 第 134 行"实体无 DO 后缀" ↔ 实际 FlowSkipDO/FlowInstanceDO 等全带 DO 后缀
  - 第 43 行 FlowTaskController 路径 `/api/v1/workflow/task` ↔ 实际 `@RequestMapping("/api/v1/workflow/engine")`
  - 第 124 行"5 模块 DDD 架构" ↔ pom.xml 实际 6 个子模块
- **修复**：以代码事实为准，全面重写 README

#### P1 级

**A6. Seata 配置不完整（过度启用）**

- **位置**：`application.yml:41-46` vs `:93-106`
- **现象**：`ydsz.seata.enabled: true` + `tcc-enabled: true` + `saga-enabled: true`，但 `seata:` 客户端配置全部注释
- **影响**：开启开关但无 TC 地址，运行时只能降级 LOCAL 模式——属于"配置过度启用、运行时降级"的反模式
- **修复**：明确工作流场景是否真需要分布式事务（多数审批场景用本地事务+消息队列补偿即可）；如不需要，将 seata.enabled 设为 false

**A7. App 端模块依赖矛盾**

- **位置**：`ydsz-workflow-server/pom.xml:24-28`
- **现象**：引入 `ydsz-workflow-app`，pom 注释说"App 端基座模块（移动端接口能力）"，但 README 明确"本模块永远不适配移动端"
- **影响**：引入无用依赖，增加构建体积和潜在安全面
- **修复**：移除该依赖；如 app 模块确有共用代码，应抽到 common 层

**A8. Aviator 表达式引擎安全加固缺失**

- **位置**：`AviatorExpressionEvaluator.java:34, 54`
- **现象**：直接使用 `AviatorEvaluator.execute(expression, variables, true)`，默认开启反射特性（可访问 Java 类），未限制 FEATURE_SET
- **影响**：恶意流程定义可通过表达式注入调用 `Runtime.getRuntime().exec(...)`，安全风险
- **对标**：Camunda 8 默认禁用反射，仅允许 FEEL 表达式
- **修复**：构造 `AviatorEvaluatorInstance`，调用 `disableFeature(Feature.NewInstance)` / `disableFeature(Feature.Module)`，并加表达式长度上限（如 4KB）和超时（如 200ms）

**A9. FlowSkipDO 设计反常（sourceRef 存 ext JSON）**

- **位置**：`FlowSkipDO.java:87` + `FlowSkipUtils.java:38-53`
- **现象**：source_node_code 无独立列，sourceRef 冗余存储在 ext JSON 中，每次查询需解析 JSON
- **影响**：违反"关系数据用关系列存储"原则，已用 sourceRefIndexCache 缓解但仍增加内存占用
- **修复**：DDL 加 `source_node_code` 列，BpmnSkipParser 写入时同时落列与 ext JSON，FlowSkipUtils 优先读列、降级读 ext（向前兼容历史数据）

#### P2 级

**A10. 依赖版本未集中管理**：Aviator 5.4.3 直接写死在子 pom，应迁到 ydsz-cloud 父 pom 的 dependencyManagement 统一管控

**A11. 多套事件机制并存**：Spring ApplicationEvent / FlowEventListener / FlowQueuePublisher 三套机制并存，职责边界不清，应明确"领域内事件用 Spring Event，跨模块异步用 Queue"

**A12. 循环依赖严重**：DefaultFlowAdvancer、FlowInstanceLifecycleManager、FlowTaskPassService 等多处用 @Lazy 注入——说明 Bean 间循环依赖普遍，应通过抽取中间 Bean 或事件驱动解耦

---

### 维度 2：功能增强

#### P0 级（对标竞品核心差距）

**F1. CEP（复杂事件处理）能力缺失**

- **现象**：流程定时器仅支持 cron 触发，缺时间窗口、序列模式、滑动窗口等 CEP 能力
- **对标**：Drools CEP、Camunda 8 消息事件 + 定时事件组合
- **修复**：调用 ydsz-literule 已规划但未落地的 CEP 能力，或工作流引擎自实现简化版（基于 Redis ZSET 时间窗口）

**F2. 真正的断点调试缺失**

- **现象**：当前仅有流程图高亮回放，无变量查看、单步执行、条件断点
- **对标**：Activiti Eclipse Debugger、Camunda Operate 单步调试
- **修复**：实现 FlowDebugAdvancer，支持在指定节点 setBreakpoint，到达时挂起实例并暴露变量查看 API

#### P1 级

**F3. 流程定义冲突检测深度不足**

- **现象**：FlowGraphValidator 仅做图结构校验（连通性/起止/孤立节点），未做条件互斥/重叠检测
- **对标**：Drools PHREAK 静态分析、Camunda 流程定义 lint
- **修复**：在 FlowDefinitionPublishManager 部署前增加条件冲突预检（同一排他网关下条件表达式交集/补集分析）

**F4. 多租户物理隔离缺失**

- **现象**：所有租户共享 schema，仅靠 tenant_id 列隔离
- **对标**：大厂标准（阿里钉钉、字节飞书）支持 schema 级 / 数据库级隔离
- **修复**：分级支持（基础版逻辑隔离、企业版 schema 隔离、旗舰版 DB 隔离），由 FlowProperties.tenantMode 切换

**F5. 规则流画布执行后端缺失**

- **现象**：literule 已识别短板，ydsz-workflow 也未对接
- **修复**：与 literule 联合规划，工作流节点支持"调用规则流"类型

**F6. 流程版本迁移 dry run 不明确**

- **位置**：`FlowInstanceMigrationServiceImpl`
- **现象**：README 说支持 dry run 预览，但代码需明确 dryRun 模式不写库且返回完整影响报告
- **修复**：在 migrate 方法签名增加 `boolean dryRun` 参数，dryRun=true 时不执行 update，仅返回节点映射表 + 影响实例数

**F7. 缺流程定义 diff 工具**

- **现象**：版本对比仅显示版本号，无节点/边变更可视化
- **修复**：新增 FlowDefinitionDiffService，输出 JSON diff（新增节点 / 删除节点 / 修改条件 / 调整办理人）

**F8. 流程模板市场缺失**

- **现象**：FlowPresetTemplateLibrary 硬编码，无独立可管理的模板库
- **对标**：钉钉流程模板中心、飞书审批模板市场
- **修复**：模板独立为 ydsz_flow_template_market 表，支持分类、标签、评分、复制

#### P2 级

**F9. AI 辅助能力薄弱**：service/impl/ai 目录存在但实现度未知，对标 Camunda 8 AI 流程生成应增强

**F10. DMN 决策表集成度待验证**：service/impl/dmn 目录存在，DMN 与流程联动机制需明确（如决策节点调用 DMN 表）

**F11. SLA 智能预测缺失**：当前 SLA 是事后告警，应基于历史数据做超时概率预测

**F12. 审批人工作量预测缺失**：批量转办时未基于历史负载预测审批人空闲度

---

### 维度 3：性能提升

#### P1 级

**P1. 流程定义缓存全量列表扫描**

- **位置**：`FlowDefinitionCacheService.java:152-160, 163-168, 198-205`
- **现象**：getStartNode / getNodeByCode 通过 `getAllNodes().stream().filter(...)` 遍历，getSkipsByNextNode 也是 stream filter
- **影响**：节点数 >100 时（复杂流程）每次推进多轮 O(n) 扫描
- **修复**：在 loadNodes 时构建 `Map<String, FlowNodeDO> nodeByCode` 索引（已有 skipSourceRefIndexCache 模式可参考）

**P2. 批量任务通过逐条处理**

- **位置**：`FlowTaskBatchServiceImpl.batchPass`
- **现象**：循环调用单条 pass，每条独立事务 + 锁 + 通知
- **影响**：批量 100 条审批需 100 次锁竞争 + 100 次事务提交
- **修复**：批量化 SQL（批量 update task status、批量 insert audit log），单次事务提交，异步通知

**P3. 审计日志跨分区扫描风险**

- **位置**：`FlowAuditLogMapper` 查询未显式带时间范围
- **影响**：查询跨月数据时 PostgreSQL 串行扫描所有分区
- **修复**：Mapper 强制要求时间范围参数，应用层校验非空

**P4. updateStatus 使用 dynamic <set>**

- **位置**：`FlowInstanceMapper.xml:58-71`
- **现象**：每个字段 `<if>` 判断，SQL 解析开销
- **修复**：拆分为专用方法 `updateStatusOnly` / `updateNodeAndStatus` / `updateEndInfo`

#### P2 级

**P5. 流程实例 page 查询未走只读副本**：pom 引入 dynamic-datasource 但未见 @DS 注解使用，应将只读查询标记 `@DS("slave")`

**P6. 高频访问的 FlowTemplate 缺应用层二级缓存**：模板列表查询频繁，可加 Caffeine（应通过 common-cache 包装）二级缓存

**P7. 审计日志、事件订阅发布同步写库**：应走 FlowQueuePublisher 异步化，降低主流程响应时间

**P8. 缺 JMH 性能基准测试**：无法量化优化的实际收益，建议为 FlowAdvancer.advance / BpmnXmlParser.parse 编写基准

---

### 维度 4：体验改善

#### P0 级

**E1. README 与代码严重不一致**（详见 A5）——影响新成员上手，必须按代码事实重写

**E2. Controller 类注释路径错误**

- **位置**：`FlowTaskController.java:96` 类注释
- **现象**：注释说路径 `/api/v1/workflow/task`，实际 `@RequestMapping("/api/v1/workflow/engine")`
- **修复**：核对所有 Controller 类注释中的路径描述

#### P1 级

**E3. 错误码 i18n key 不规范**

- **现象**：`error.workflow.msg_67a10717` / `error.workflow.msg_560bf118` 等哈希化 key
- **影响**：阅读维护困难，grep 定位低效
- **修复**：改为业务描述性 key，如 `workflow.instance.not_found` / `workflow.definition.start_node_missing`
- **进度**（已完成）：
  - `messages_zh_CN.properties` / `messages_en_US.properties` 中 2 个哈希 key 已重命名为描述性 key：
    - `error.workflow.msg_6e66716d` → `error.workflow.node.not.found`（目标节点不存在）
    - `error.workflow.msg_241f4a79` → `error.workflow.reject.target.not.found`（退回目标节点不存在）
  - `DefaultFlowAdvancer.java` 中 6 处引用已同步更新
- **待后续专项**：剩余 ~60 个硬编码哈希 key 散落在 ~40 个 Java 文件中，均无 properties 文件对应条目。
  需逐一定义业务语义并补全中英双语 message，建议作为独立 i18n 专项分批推进。
  高频文件：`BpmnXmlParser.java`（9 处）、`FlowDefinitionDesignManager.java`（10 处）、`FlowDelegateAuthServiceImpl.java`（18 处）

**E4. OpenAPI 文档不完整**

- **现象**：Controller 有 @Operation 但缺 @Parameter / @ApiResponse / @ExampleResource
- **影响**：Swagger UI 生成的文档可用性差，前端联调成本高
- **修复**：补全 @Parameter 描述、@ApiResponse 错误码映射、示例 payload

**E5. 缺管理后台可视化操作引导**

- **现象**：FlowMonitorDashboardController 提供数据但无操作引导
- **修复**：异常实例页面提供"诊断 + 一键修复"按钮（调用 batchMarkError / 一键终止）

**E6. 缺流程实例诊断工具**

- **现象**：selectRunningWithoutTask 找出的异常实例只返回 ID 列表
- **修复**：新增 FlowInstanceDiagnoseService，对异常实例输出诊断报告（卡住节点 / 缺失任务原因 / 推荐操作）

#### P2 级

**E7. 测试用例命名不统一**：shouldThrow_whenXxx / shouldPass_whenXxx 应统一为 should_<预期>_when_<条件>

**E8. 缺前后端字段映射文档**：仅 README 提到 PC 端，缺 VO 字段与前端组件的映射表

---

### 维度 5：过度设计

#### P0 级

**O1. Seata TCC + Saga 同时启用**（详见 A6）——典型过度设计，多数审批场景本地事务 + 消息队列补偿即可

**O2. FlowSensitiveMasker 正则定义矛盾**

- **位置**：`FlowSensitiveMasker.java:54-70`
- **现象**：类注释明确"委托 SensitiveUtil 不再重复定义 Pattern"，但 SENSITIVE_KEY_PATTERNS 仍在本类定义 12 个 Pattern
- **影响**：注释与代码事实不符，维护时易遗漏一处
- **修复**：将 SENSITIVE_KEY_PATTERNS 迁到 common-safe 的 SensitiveUtil，本类只调用

#### P1 级

**O3. FlowTaskService 双层门面**

- **现象**：FlowTaskServiceImpl（门面）→ FlowTaskCompleteServiceImpl（门面）→ FlowTaskCreateService / ClaimService / PassService / RejectService / OperateService / UrgeService / TimeoutService（7 个子 Service）
- **影响**：三层委托链路，调用栈深，调试困难
- **修复**：压缩为两层，FlowTaskServiceImpl 直接委托 7 个子 Service，删除中间 CompleteServiceImpl

**O4. FlowInstanceService 同样双层门面**

- **现象**：FlowInstanceServiceImpl → FlowInstanceLifecycleManager / FlowInstanceBatchOperator / FlowInstanceQueryService / FlowInstanceVariableManager
- **修复**：同 O3

**O5. 三套事件机制并存**（详见 A11）

#### P2 级

**O6. 三个事件类职责重叠**：FlowEventContext / FlowEventListener / FlowWorkflowEvent 应合并为统一 Event 抽象

**O7. 过多 @Lazy 注入**（详见 A12）

**O8. FlowUrgeLimiter 单独类**：限流应统一用 common-safe 的 @RateLimit 注解

**O9. CountersignStrategy 策略模式实现过少**：仅 2 个实现（OrCountersignStrategy / ParallelCountersignStrategy），可考虑枚举 + 函数式接口简化

---

### 维度 6：测试与质量保障

#### P0 级

**T1. 核心引擎 0 测试覆盖**

- **未覆盖关键类**：
  - `DefaultFlowAdvancer`（流程推进器，763 行）
  - `FlowInstanceLifecycleManager`（实例生命周期）
  - `FlowTaskCompleteServiceImpl` 及其 7 个子 Service
  - `BpmnXmlParser`（BPMN 解析器）
  - `FlowDefinitionCacheService`（缓存服务）
  - 21 个 ServiceImpl 大多无测试
- **修复**：按 P0 优先级补单测，先覆盖 DefaultFlowAdvancer（推进核心）和 BpmnXmlParser（解析核心），目标行覆盖率 >70%

**T2. 现有测试无法编译**（详见 A4）

#### P1 级

**T3. 缺集成测试**：无 SpringBootTest 启动测试，无法验证容器装配、Mapper XML 加载

**T4. 缺并发场景测试**：分布式锁、join 令牌聚合、批量审批等并发场景无验证

**T5. 缺契约测试**：与 userinfo 等模块的 Feign 调用无契约测试

---

## 三、对标竞品能力差距矩阵

| 能力 | ydsz-workflow | Activiti 7 | Flowable 7 | Camunda 8 | LiteFlow | Drools | 大厂标准 |
|---|---|---|---|---|---|---|---|
| BPMN 2.0 解析 | ✅ 自研 | ✅ | ✅ | ✅ | ❌ | ❌ | ✅ |
| 排他/包容/并行网关 | ✅ | ✅ | ✅ | ✅ | 部分 | ❌ | ✅ |
| N/M join 聚合 | ✅ | 部分 | 部分 | ✅ | ❌ | ❌ | ✅ |
| 多节点同退 | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ✅（钉钉/飞书） |
| 断点调试 | ❌ | ✅ | ✅ | ✅ | ❌ | ❌ | ✅ |
| CEP | ❌ | 部分 | 部分 | ✅ | ❌ | ✅ | ✅ |
| DMN 决策表 | 部分 | ✅ | ✅ | ✅ | ❌ | ✅ | ✅ |
| 流程迁移 | ✅ | 部分 | 部分 | ✅ | ❌ | ❌ | ✅ |
| 历史归档分区 | ✅ | 部分 | 部分 | ✅ | ❌ | ❌ | ✅ |
| 多租户物理隔离 | ❌ | ❌ | ❌ | ✅ | ❌ | ❌ | ✅ |
| AI 辅助 | 部分 | ❌ | ❌ | ✅ | ❌ | 部分 | ✅ |
| SLA 智能预测 | ❌ | ❌ | ❌ | ✅ | ❌ | ❌ | ✅ |
| 表达式引擎安全 | ❌（Aviator 默认反射） | ✅ | ✅ | ✅（FEEL） | ✅ | ✅ | ✅ |

---

## 四、阶段化落地执行路径

### 阶段 S1（1-2 周）：编译可启动性修复（P0 必做）

1. 修复 FlowProperties 缺失字段（A1）
2. 修复 Mapper XML type 引用（A2）
3. 修复 application.yml warmup-classes（A3）
4. 修复 FlowGraphValidatorTest 编译错误（A4）
5. 重写 README 消除 doc drift（A5）
6. 修复 FlowSensitiveMasker 注释与代码矛盾（O2）
7. 修复 FlowTaskController 类注释路径（E2）
8. 修复 Aviator 安全加固（A8）

### 阶段 S2（2-3 周）：核心引擎测试补齐 + 性能优化（P0+P1）

1. 为 DefaultFlowAdvancer / BpmnXmlParser / FlowInstanceLifecycleManager 补单测（T1）
2. 流程定义缓存建索引（P1）
3. 批量任务批量化 SQL（P2）
4. 审计日志强制时间范围（P3）
5. 错误码 i18n key 规范化（E3）
6. OpenAPI 文档补全（E4）
7. 移除 App 端模块依赖（A7）
8. 关闭未使用的 Seata（A6）

### 阶段 S3（3-4 周）：功能补强（P1）

1. CEP 简化版实现（F1）
2. 断点调试器（F2）
3. 流程定义冲突检测（F3）
4. 多租户 schema 隔离（F4）
5. 流程版本迁移 dry run 明确化（F6）
6. 流程定义 diff 工具（F7）
7. 流程模板市场（F8）

### 阶段 S4（2-3 周）：架构治理 + 过度设计收口（P1+P2）

1. 压缩 FlowTaskService 双层门面（O3）
2. 压缩 FlowInstanceService 双层门面（O4）
3. 统一事件机制（A11 / O5 / O6）
4. 消除循环依赖（A12）
5. 依赖版本集中管理（A10）
6. FlowSkipDO 加 source_node_code 列（A9）

### 阶段 S5（持续）：体验完善 + 大厂对标（P2）

1. 管理后台操作引导（E5）
2. 流程实例诊断工具（E6）
3. SLA 智能预测（F11）
4. 审批人工作量预测（F12）
5. AI 辅助流程生成（F9）
6. JMH 性能基准测试（P8）

---

## 五、关键风险与建议

### 风险一：代码-文档-测试三方不一致严重

**现象**：README 描述、Mapper XML type、application.yml warmup-classes、测试代码多处与生产代码事实脱节
**根因**：重构过程中"代码先行、文档测试未跟进"
**建议**：建立"PR 必须同步更新 README + 测试"的 CI 卡点（Checkstyle + Markdown lint + 测试编译校验）

### 风险二：核心引擎无测试兜底

**现象**：DefaultFlowAdvancer 等关键类 0 测试覆盖，重构无安全网
**建议**：S1 阶段必须先补单测再继续功能迭代，否则任何变更都是高风险

### 风险三：过度设计与实际需求不匹配

**现象**：Seata 双模式启用、双层门面、三套事件机制
**建议**：遵循 ydsz 项目"最小化外部依赖、绝对可控优先于峰值性能"理念，按实际场景裁剪

### 风险四：Aviator 表达式注入风险

**现象**：流程定义中表达式可调用任意 Java 类
**建议**：立即在 S1 阶段修复（A8），并增加表达式白名单（仅允许流程变量访问 + 基础运算）

---

## 六、云顶规范符合性核查

| 规范项 | 符合性 | 说明 |
|---|---|---|
| 禁止第三方 JSON | ✅ | 全部使用 ydsz-common-json 的 YdszJson |
| 禁止直接用 Caffeine | ✅ | 使用 ydsz-common-cache 的 YdszCache |
| 禁止直接依赖 POI | N/A | 模块无 Excel 处理 |
| 公共模块 L1-L6 分层 | ✅ | 依赖 common-util / common-cache / common-json 等 |
| entity 命名（无 DO 后缀） | ❌ | 实际 FlowSkipDO 等带 DO 后缀，与 README 描述矛盾 |
| 错误码使用 SysException | ✅ | 全部通过 SysException.builder() 抛出 |
| 多租户隔离 | ✅ | MULTI 模式 + TenantContextHolder |

**注**：entity 命名规范需明确——README 说"无 DO 后缀"，实际代码"有 DO 后缀"，二者必居其一，需团队确认后统一。

---

## 附录：核查命令清单

```bash
# 核查 FlowProperties 缺失字段
grep -rn "definitionCacheTtlMinutes\|definitionCacheMaxSize" ydsz-workflow/

# 核查 Mapper XML type 引用
grep -rn 'type="com.njydsz.workflow.domain.entity' ydsz-workflow/ydsz-workflow-infra/src/main/resources/mapper/

# 核查测试代码不存在的方法
grep -rn "setSourceNodeCode\|setTargetNodeCode" ydsz-workflow/

# 核查 application.yml warmup-classes
grep -A 10 "warmup-classes" ydsz-workflow/ydsz-workflow-web/src/main/resources/application.yml

# 核查 Aviator 安全配置
grep -n "AviatorEvaluator" ydsz-workflow/ydsz-workflow-server/src/main/java/com/njydsz/workflow/server/engine/expr/AviatorExpressionEvaluator.java
```

---

> **报告生成时间**：2026-08-19
> **核查代码版本**：ydsz-workflow trunk @ 2026-08-18
> **核查方式**：基于实际最新代码（交叉核对文档与代码，捕捉 doc drift 与虚构条目）
> **后续动作**：建议按 S1 → S2 → S3 → S4 → S5 顺序推进，每个阶段完成后形成常态化迭代闭环
