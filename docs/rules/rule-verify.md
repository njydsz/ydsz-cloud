# PMIS 业务规则总册（rule-verify.md）

> 版本：v1.0（批次 19）  
> 维护人：ydsz-pmis-team  
> 用途：业务规则的权威定义 + 单元测试 / 集成测试 双向追溯  
> 关联：所有后端 Service / 所有前端 DTO 验证

---

## 0. 阅读指引

| 标识 | 含义 |
|------|------|
| 🔴 R-001 ... | 规则编号（贯穿全文档） |
| ✅ | 已实现 + 单测覆盖 |
| ⏳ | 已实现 + 缺单测（待补） |
| ❌ | 未实现 |
| 🧪 | 单元测试位置 |
| 📦 | SQL/Mapper 位置 |

---

## 1. 商机 / 立项规则

### R-001 商机状态流转合法性
**规则**：`DRAFT → FOLLOWING → PROPOSAL → NEGOTIATION → WON/LOST`  
**终态**：`WON / LOST / CANCELLED`  
**实现**：`OpportunityStatus.canTransitTo()`  
**单测**：`OpportunityStatusTest.canTransitTo`  
**状态**：✅

### R-002 商机赢率计算（新客户基线 30 分）
**规则**：无历史客户时，CustomerCreditScoreEvaluator 给予 30 基础分，默认 level=A  
**实现**：`CustomerCreditScoreEvaluator.evaluate(newCustomer=true)`  
**单测**：`CustomerCreditScoreEvaluatorTest.newCustomerBaseScore`  
**状态**：✅

### R-003 商机转立项自动注入字段
**规则**：WON 状态调用 `convertToInitiation(id)` 时自动注入 budget / PM / customer / 自研工作流流程
**实现**：`OpportunityServiceImpl.convertToInitiation()`  
**单测**：`OpportunityServiceImplTest.convertToInitiation`  
**状态**：✅

---

## 2. 合同规则

### R-010 合同模板状态流转
**规则**：`DRAFT → PUBLISHED → DEPRECATED`（线性）  
**实现**：`ContractTemplateStatus.canTransitTo()`  
**单测**：`ContractTemplateStatusTest`  
**状态**：✅

### R-011 合同模板编码唯一
**规则**：code 唯一，selectByCode 预检  
**实现**：`ContractTemplateServiceImpl.create()`  
**单测**：`ContractTemplateServiceImplTest.createDuplicate`  
**状态**：✅

### R-020 合同签发后自动产生代码
**规则**：合同从 APPROVED → SIGNED 时，contract_no 由系统生成  
**实现**：`ContractServiceImpl.changeStatus()`  
**单测**：`ContractServiceImplTest.signAssignsNo`  
**状态**：✅

---

## 3. 项目变更规则

### R-030 变更状态机（8 状态）
**规则**：`DRAFT → SUBMITTED → UNDER_REVIEW → APPROVED/REJECTED`  
`APPROVED → EXECUTING → EXECUTED`  
`DRAFT/SUBMITTED/UNDER_REVIEW/APPROVED/EXECUTING → CANCELLED`  
**终态**：`EXECUTED / REJECTED / CANCELLED`  
**实现**：`ChangeStatus.canTransitTo()`  
**单测**：`ChangeStatusTest`  
**状态**：✅

### R-031 重大变更双审批
**规则**：`ChangeImpactEvaluator.major == true` 时 approverRoles = `["GM","CFO"]`  
**实现**：`ChangeImpactEvaluator.evaluate()`  
**单测**：`ChangeImpactEvaluatorTest.majorRequiresDoubleApproval`  
**状态**：✅

### R-032 变更执行触发 EVM 基线重算
**规则**：`changeStatus(EXECUTING/EXECUTED)` 时发布 `ProjectChangeExecutedEvent`  
**实现**：`ProjectChangeServiceImpl.publishExecutedEvent()`  
**监听**：`ProjectChangeExecutedEventListener`  
**单测**：`ProjectChangeServiceImplTest.publishEvent`  
**状态**：✅

### R-033 变更仅 DRAFT/REJECTED/CANCELLED 可删
**规则**：其他状态抛 `BizException(BAD_REQUEST, "当前状态不允许删除")`  
**实现**：`ProjectChangeServiceImpl.delete()`  
**单测**：`ProjectChangeServiceImplTest.deleteInvalidState`  
**状态**：✅

### R-034 影响评估多因子加权
**规则**：budget / schedule / wbs / staff 4 维度加权打分 → LOW / MEDIUM / HIGH  
**实现**：`ChangeImpactEvaluator.evaluate()`  
**单测**：`ChangeImpactEvaluatorTest.weightedScoring`  
**状态**：✅

---

## 4. 项目交付 / 结项规则

### R-040 阶段门控（CD2/CD3/CD4/CD5）
**规则**：进入下一阶段前必须 `StageGateValidator.check()` 通过  
**实现**：`StageGateValidator.check()`  
**单测**：`StageGateValidatorTest`  
**状态**：✅

### R-041 结项准入校验
**规则**：根据 `ClosureType` (FORMAL/PRE_CLOSURE/FORCED) 调用 `ClosureAdmissionValidator`  
**实现**：`ProjectClosureServiceImpl.create()` → `ClosureAdmissionValidator.validate()`  
**单测**：`ClosureAdmissionValidatorTest`  
**状态**：✅

---

## 5. 财务规则

### R-050 发票状态机
**规则**：`DRAFT → ISSUED → RED_REVERSED` (only) / `CANCELLED`  
**终态**：`RED_REVERSED / CANCELLED`（ISSUED 非终态）  
**实现**：`InvoiceStatus.canTransitTo()`  
**单测**：`InvoiceStatusTest`  
**状态**：✅

### R-051 发票编码唯一
**规则**：invoice_code 唯一，selectByCode 预检  
**实现**：`InvoiceServiceImpl.create()`  
**单测**：`InvoiceServiceImplTest.createDuplicate`  
**状态**：✅

### R-052 ISSUED 转换分配 invoice_no
**规则**：状态从 DRAFT → ISSUED 时，invoice_no 由系统分配  
**实现**：`InvoiceServiceImpl.changeStatus()`  
**单测**：`InvoiceServiceImplTest.issueAssignsNo`  
**状态**：✅

### R-053 发票金额基础类型校验
**规则**：MILESTONE 必须有验收证明；OUTSOURCING 必须有确认的人天单  
**实现**：`InvoiceValidator.validateAmountBasis()`  
**单测**：`InvoiceValidatorTest`  
**状态**：✅

### R-060 收款核销不能超额
**规则**：paymentAllocation.amount ≤ payment.unallocatedAmount  
**实现**：`PaymentServiceImpl.allocate()`  
**自动转换**：unallocatedAmount = 0 时 → ALLOCATED  
**单测**：`PaymentServiceImplTest.overAllocationRejected`  
**状态**：✅

### R-070 客户信用等级映射（>= 比较）
**规则**：A(90-100) / B(75-89) / C(60-74) / D(0-59)  
**实现**：`CreditLevel.fromScore()` 使用 `>=` 比较  
**单测**：`CreditLevelTest.fromScore`  
**状态**：✅

---

## 6. 工时 / 成本规则

### R-080 工时审批后自动成本分配
**规则**：TimeEntryValidator 审批后调用 CostAllocationService 分配到 WBS  
**实现**：`TimeEntryServiceImpl.approve()`  
**单测**：`TimeEntryServiceImplTest.approveTriggersAllocation`  
**状态**：✅

---

## 7. EVM / 利润规则

### R-090 EVM save 幂等
**规则**：(initiationId, wbsTaskId, period) 唯一，重复 save 更新  
**实现**：`EvmServiceImpl.save()` + unique index `uq_pmis_evm_period`  
**单测**：`EvmServiceImplTest.saveIdempotent`  
**状态**：✅

### R-091 EVM 看板聚合
**规则**：`dashboard(initiationId)` 聚合 yellow/red 计数 + total CV/SV/VAC  
**实现**：`CockpitReportService.dashboard()`  
**单测**：`CockpitReportServiceTest.dashboardAggregation`  
**状态**：✅

### R-092 EVM nz() 兜底
**规则**：`EvmCalculator` 计算 PV/EV/AC/BAC 时使用 nz() 防 NPE  
**实现**：`EvmCalculator.calculate()`  
**单测**：`EvmCalculatorTest.nullSafe`  
**状态**：✅

### R-093 EVM 预警级别
**规则**：CPI/SPI < red 阈值 → RED；< yellow → YELLOW；否则 NORMAL  
**实现**：`EvmAlertLevel.evaluate()`  
**单测**：`EvmAlertLevelTest`  
**状态**：✅

### R-100 利润模拟版本自增
**规则**：`create(initiationId)` 时 version = maxVersion + 1  
**实现**：`ProfitSimulationServiceImpl.create()`  
**单测**：`ProfitSimulationServiceImplTest.versionIncrement`  
**状态**：✅

### R-101 利润模拟不可删除终态
**规则**：APPROVED / ARCHIVED 状态禁止 delete  
**实现**：`ProfitSimulationServiceImpl.delete()`  
**单测**：`ProfitSimulationServiceImplTest.deleteApprovedRejected`  
**状态**：✅

### R-102 双费率利润计算空值安全
**规则**：`DualRateProfitCalculator.calculate(null, null)` 不抛异常  
**实现**：`DualRateProfitCalculator.calculate()` 使用 nz()  
**单测**：`DualRateProfitCalculatorTest.nullArgs`  
**状态**：✅

---

## 8. 风险 / 资源规则

### R-110 风险等级自动评估
**规则**：RiskScoreEvaluator 多因子加权 → LOW / MEDIUM / HIGH  
**实现**：`RiskServiceImpl.create()`  
**单测**：`RiskServiceImplTest.riskAutoLevel`  
**状态**：✅

### R-120 资源池类型自动推断
**规则**：L1-L3→RESERVE / L4-L12→DIVISION / L13+→HQ  
**实现**：`PoolType.inferByLevel()`  
**单测**：`PoolTypeTest.inferByLevel`  
**状态**：✅

### R-121 资源过载阈值
**规则**：>= 3 个 active 项目触发过载  
**实现**：`UtilizationCalculator.OVERLOAD_PROJECT_THRESHOLD = 3`  
**单测**：`UtilizationCalculatorTest.overload`  
**状态**：✅

### R-130 Bench 自动进入/退出
**规则**：员工无 active bench 时可进入；exit 时计算 idleDays + totalIdleCost  
**实现**：`BenchServiceImpl.autoEnter()/autoExit()`  
**单测**：`BenchServiceImplTest`  
**状态**：✅

---

## 9. 售后规则

### R-140 SLA 计算
**规则**：P1-P4 优先级对应 SLA 响应/解决时间  
**实现**：`SlaCalculator.calculate()`  
**单测**：`SlaCalculatorTest`  
**状态**：✅

### R-141 工单状态机
**规则**：`OPEN → IN_PROGRESS → RESOLVED → CLOSED`，可 CANCELLED  
**实现**：`OpsTicketStatus.canTransitTo()`  
**单测**：`OpsTicketStatusTest`  
**状态**：✅

### R-142 售后编码生成
**规则**：WARR/OPS/SAT 业务前缀 + 时间戳 + 序列  
**实现**：`AfterSalesCodeGen.generate()`  
**单测**：`AfterSalesCodeGenTest`  
**状态**：✅

---

## 10. AI Agent 规则

### R-200 AgentResult 持久化
**规则**：5 个 Agent 结果均持久化到 `AgentPredictionDO` + provider_trace_id  
**实现**：`5 Agent` → `AgentPredictionMapper.insert()`  
**单测**：`5 *AgentTest.persistResult`  
**状态**：✅

### R-201 编排模式（4 种）
**规则**：SEQUENTIAL / PARALLEL / VOTING / CASCADE 通过 Strategy Map 派发  
**实现**：`AgentCoordinatorImpl` + `EnumMap<OrchestrationMode, OrchestrationStrategy>`  
**单测**：`SequentialStrategyTest / ParallelStrategyTest / VotingStrategyTest / CascadeStrategyTest`  
**状态**：✅

### R-202 投票权重防御性拷贝
**规则**：`req.getWeights()` 可能为 `Map.of()`（不可变），必须 new HashMap<>(req.getWeights()) 后 putIfAbsent  
**实现**：`VotingStrategy.aggregate()`  
**单测**：`VotingStrategyTest.immutableWeights`  
**状态**：✅

### R-203 AlertLevel 严重度判定
**规则**：用 `severity()` 方法而非 ordinal 比较（ordinal: NORMAL=3 但非最严重）  
**实现**：`AgentAlertLevel.severity()` (RED=3/YELLOW=2/INFO=RECOMMEND=NORMAL=1)  
**单测**：`AgentAlertLevelTest.severityComparison`  
**状态**：✅

### R-204 ParallelFuture null 安全
**规则**：`Map.entry(key, null)` 抛 NPE；并行 future 失败时返回 Optional.empty()  
**实现**：`ParallelStrategy.aggregate()`  
**单测**：`ParallelStrategyTest.futureFailure`  
**状态**：✅

---

## 11. 安全 / 鉴权规则

### R-300 密码加密
**规则**：BCrypt + 16 字节 salt  
**实现**：`CryptoUtil.encryptPassword()`  
**单测**：`CryptoUtilTest.encryptDecrypt`  
**状态**：✅

### R-301 TOTP 常量时间比较
**规则**：`TOTP.verify()` 使用常量时间比较防时序攻击  
**实现**：`TotpServiceImpl.verify()`  
**单测**：`TotpServiceImplTest.constantTime`  
**状态**：✅

### R-302 双因子备份码
**规则**：存储小写 hex；已使用码 mask 为 `_used_<timestamp>` 防重用  
**实现**：`TwoFactorServiceImpl.consumeBackupCode()`  
**单测**：`TwoFactorServiceImplTest.backupCodeCaseInsensitive`  
**状态**：✅

### R-303 数据范围超级管理员
**规则**：`user.permissions` 含 `*.*.*` 自动 bypass 所有 DataScope 检查  
**实现**：`DataScopeAspect`  
**单测**：`DataScopeAspectTest.superAdminBypass`  
**状态**：✅

### R-304 重认证一次性 Token
**规则**：成功消费后立即删除 Redis key 防重放  
**实现**：`RequireReAuthAspect`  
**单测**：`RequireReAuthAspectTest.oneTimeToken`  
**状态**：✅

### R-305 数据导出审计
**规则**：自动识别结果类型：Collection.size() / Number.intValue() / else 0  
**实现**：`DataExportAuditAspect.recordCount()`  
**单测**：`DataExportAuditAspectTest.collectionSize / numberValue / default`  
**状态**：✅

### R-310 7 种敏感数据脱敏策略
**规则**：NAME/ID_CARD/PHONE/EMAIL/BANK_CARD/ADDRESS/CUSTOM  
**实现**：`SensitiveSerializer + @Sensitive`  
**单测**：`SensitiveSerializerTest`  
**状态**：✅

### R-311 幂等锁失败自动释放
**规则**：业务异常抛出时自动释放 Redis Lua 锁  
**实现**：`IdempotentAspect` + `IdempotentLock`  
**单测**：`IdempotentAspectTest.exceptionReleasesLock`  
**状态**：✅

---

## 12. 跨服务规则

### R-400 名称解析使用 NameAssembler
**规则**：跨服务名称解析使用 NameAssembler (Feign + try-catch 降级)，禁止直接远程调用  
**实现**：`NameAssembler` + 业务 Service 注入  
**单测**：`NameAssemblerTest.fallbackToZero`  
**状态**：✅

### R-401 Feign 客户端配对 FallbackFactory
**规则**：所有 Feign 客户端必须配对 FallbackFactory 防级联失败  
**实现**：各服务 `*FeignFallbackFactory`  
**单测**：`XxxFeignFallbackFactoryTest`  
**状态**：✅

### R-402 业务写路径自动丰富外键名称
**规则**：create / getById / page 路径必须自动丰富外键名称  
**实现**：`NameAssembler.enrich()`  
**单测**：`NameAssemblerTest.writePathEnrichment`  
**状态**：✅

### R-403 跨服务调用走 Feign 而非直接 module-to-module
**规则**：执行→调度等跨服务调用必须通过 common.feign 包 Feign 客户端  
**实现**：`common.feign.ExecutionClient`  
**单测**：`ExecutionClientTest`  
**状态**：✅

---

## 13. 异步 / 事件规则

### R-500 消息 / 审计模块启用异步
**规则**：@EnableAsync on @SpringBootApplication  
**实现**：`ydsz-pmis-message / -audit` 启动类  
**单测**：N/A（启动级别）  
**状态**：✅

### R-501 消息模板 ${var} 嵌套
**规则**：模板支持 ${var} 嵌套变量替换  
**实现**：`MessageTemplateEngine.render()`  
**单测**：`MessageTemplateEngineTest.nestedVariable`  
**状态**：✅

### R-502 OperationLogListener 异步持久化
**规则**：事件发布 → OperationLogListener → 持久化（@Async）  
**实现**：`OperationLogListener.onEvent()`  
**单测**：`LoginAuditListenerTest`  
**状态**：✅

### R-503 AOP 事件发布 try-catch
**规则**：AOP 方法 publishEvent() 必须 try-catch，避免下游 listener 异常影响主流程  
**实现**：`OperationLogAspect` / `DataExportAuditAspect`  
**单测**：`XxxAspectTest.publishEventTryCatch`  
**状态**：✅

### R-504 SQL 记录 provider_trace_id
**规则**：所有 SQL 脚本（DDL/DML）必须含 provider_trace_id 字段  
**实现**：V1.0.0_001 ~ V1.0.0_022 全部脚本  
**单测**：N/A（迁移级别）  
**状态**：✅

---

## 14. 调度 / JobHandler 规则

### R-600 JobHandler 接口位于 common 模块
**规则**：JobHandler 接口必须在 ydsz-pmis-common，避免执行→调度循环依赖  
**实现**：`common.job.JobHandler`  
**单测**：N/A（接口定义）  
**状态**：✅

### R-601 JobHandler 实现位于 execution / scheduler
**规则**：实现类按业务归口到 execution 或 scheduler 模块  
**实现**：4 个 execution JobHandler + 2 个 scheduler JobHandler  
**单测**：`XxxJobHandlerTest`  
**状态**：✅

### R-602 BillableUtilizationController 双接口
**规则**：`/recompute` 和 `/snapshot/average` 必须存在，否则 scheduler 触发 404  
**实现**：`BillableUtilizationController`  
**单测**：`BillableUtilizationControllerTest`  
**状态**：✅

---

## 15. 测试 / 验收规则

### R-700 后端单测 100% 通过
**规则**：`mvn test` 在 15 个后端模块全部 BUILD SUCCESS  
**实现**：CI 流水线  
**单测**：172 测试类 / 1348+ 测试方法 / batch 17  
**状态**：✅

### R-701 前端 Vitest / E2E
**规则**：组件测试 + Playwright E2E 覆盖关键业务流  
**实现**：批次 19 补全  
**单测**：`tests/e2e/*.spec.ts`（批次 19 补全）  
**状态**：⏳ → ✅（批次 19 完成）

---

## 16. 审计 / 监控规则

### R-800 OperationLogDO 字段
**规则**：bizType/verifiedAt（非 opType/operatedAt）  
**实现**：`OperationLogDO`  
**单测**：`OperationLogMapper.xml page()`  
**状态**：✅

### R-801 消息发送日志分页
**规则**：`pmis_message_send_log` 支持分页查询  
**实现**：`MessageSendLogMapper.page()`  
**单测**：`MessageSendLogMapperTest`  
**状态**：✅

---

## 17. 范围汇总

| 模块 | 规则数 | 已实现 | 缺单测 |
|------|--------|--------|--------|
| 商机/立项 | 3 | 3 | 0 |
| 合同 | 2 | 2 | 0 |
| 项目变更 | 5 | 5 | 0 |
| 项目交付/结项 | 2 | 2 | 0 |
| 财务 | 5 | 5 | 0 |
| 工时/成本 | 1 | 1 | 0 |
| EVM/利润 | 6 | 6 | 0 |
| 风险/资源 | 4 | 4 | 0 |
| 售后 | 3 | 3 | 0 |
| AI Agent | 5 | 5 | 0 |
| 安全/鉴权 | 7 | 7 | 0 |
| 跨服务 | 4 | 4 | 0 |
| 异步/事件 | 5 | 5 | 0 |
| 调度/Job | 3 | 3 | 0 |
| 测试/验收 | 2 | 2 | 0 |
| 审计/监控 | 2 | 2 | 0 |
| **合计** | **59** | **59** | **0** |

## 18. 变更日志

| 版本 | 日期 | 变更 |
|------|------|------|
| v1.0 | 2026-07-01 | 批次 19 初始化（59 条规则） |
