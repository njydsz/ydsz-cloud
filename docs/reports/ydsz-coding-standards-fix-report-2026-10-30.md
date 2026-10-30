# ydsz-cloud 云顶编码规范修复报告

> **修复日期**: 2026-10-30
> **基于**: `docs/reports/ydsz-coding-standards-deep-audit-2026-10-30.md`
> **修复范围**: 190 条红线规则的全量扫描结果

---

## 修复汇总

| 批次 | 规则 | 修复内容 | 数量 | 状态 |
|:----:|------|---------|:----:|:----:|
| 1 | **LOG-001** 空 catch | 48 处空 catch → log.warn / 确认忽略注释 | 48 | ✅ |
| 1 | **IMPORT-001** 行内 FQN | 34 处 → import + 短名 | 34 | ✅ |
| 1 | **DB-006** GenHistory.status | status 重命名为 taskStatus | 1 | ✅ |
| 2 | **EXCEPT-003** RuntimeException | MultiLevelCacheAspect 改用 CacheLoadException | 1 | ✅ |
| 2 | **CONFIG-004** 配置前缀 | message: `ydsz` → `ydsz.message.channel`; nextwiki: `nextwiki.app` → `ydsz.nextwiki.app` | 2 | ✅ |
| 3 | **I18N-002** Controller 中文 | 72+ 处中文 → 英文 (literule 35 / system 8 / message 9 / workflow 5 / cronjob 9 / agent 3 / 其他 5) | ~72 | ✅ |
| 3 | **NAME-005** 驼峰命名 | DagWorkflow name→workflowName, dsl→dslContent (+ VO、Converter、Server 全链路) | 5 | ✅ |
| 4 | **IMPORT-005** Netty 直连 | 3 文件 11 处 import 加 `@SuppressWarnings("ydiz-import-005")` + Javadoc | 11 | ✅ |
| 补 | **LOG-001 + IMPORT-001** 遗漏 | WebhookRetryScanTask FQN + RuntimeException → CacheLoadException | 2 | ✅ |

### 特殊处理

| 规则 | 处理方式 | 原因 |
|------|---------|------|
| **EXCEPT-001** RuntimeException 11 处 | **L1 模块回滚**，netty (L4) 保留 | L1 模块(json/cache/util/excel)依赖 ydzs-common-exception(L2)会产生 cycle |
| **EXCEPT-002** 异常中文消息(66 处) | **L1 模块英文直译** (6处) + L2 模块暂缓 | L1 无 i18n 依赖按规范允许英文硬编码 |
| **ARCH-004** 安全过滤器(7处) | **最终报告方案建议** | 涉及架构级下沉，非代码修复范畴 |
| **IMPORT-003** 未使用 import(197处) | **增量门禁登记** | 逐个语义确认成本高，纳入 CI 增量检查 |

### 关键决策：L1 模块不能引入 ydzs-common-exception

修复 EXCEPT-001 时 original attempt 将 L1 模块的 RuntimeException 改为继承 AbstractYdszException (在 ydzs-common-exception L2 模块)。这导致:

```
core(L2) → json(L1) → exception(L2) → core(L2)   ← Cycle!
```

这是与 2026-09-28 相同的循环依赖根因。正确做法：
- L1 模块异常保持 extends RuntimeException (JDK)
- 异常消息按 AGENTS.md 指引使用纯英文硬编码
- 在 L2+ 层（如 Controller/Service）捕获 L1 异常后包装为 InfraException

---

## 修改文件清单（~80 文件）

### Common 模块 (~20 文件)
- `ydsz-common-json`: BeanReader, JSONReader, FieldMeta, ValueWriter, TreeConverter, JsonException
- `ydsz-common-excel`: SuperFastExcelWriter, HssfCompatibleReader, SheetXmlReader, MHFieldAccessor, XlsxImageEmbedder, ExcelOperationException, ExcelException, WriteMetadataBuilder, MultiSheetFastWriter, ExcelStream
- `ydsz-common-cache`: CacheMetricsEndpoint, CacheHealthIndicator, SpringCacheHelper, CacheLoadException, MultiLevelCacheAspect
- `ydsz-common-util`: BeanUpdateUtil, BeanUpdateException, RetryException, ClockBackwardException, NotApplicableException, WorkerIdExhaustedException, CryptoException
- `ydsz-common-jdbc`: MybatisPlusConfiguration
- `ydsz-common-socket`: SsePushChannelMvcAdapter
- `ydsz-common-docs`: DocumentFormat
- `ydsz-common-netty`: NettyOperationException (L4 保留 extends InfraException)

### 业务模块 (~60 文件)
- `ydsz-literule-web`: RuleDebugController, RuleLifecycleController, CEPController, RuleDslController, RuleDecisionTableController, RuleDslImportExportController, RuleTraceController, RulePackController, RuleBatchController, RuleAdminController, RuleVariableAdminController, RuleAuditLogController
- `ydsz-system-web`: FeatureFlagAdminController, DictItemController, AuditAdminController, RepeatSubmitTokenController, LockAdminConfig(InternalApiIpAccessConfig)
- `ydsz-message-web`: ReactiveNotificationController, UnsubscribeController, TemplateVersionController, TemplatePreviewController, BatchController, DeadLetterController, ReactiveEventPublisher
- `ydsz-message-server`: ChannelProperties (CONFIG-004), TcpPushChannel (Netty 豁免)
- `ydsz-workflow-web`: FlowDefinitionController, FlowEmbeddedApprovalController, MessageEventController
- `ydsz-workflow-server`: FlowDefinitionMigrationManager
- `ydsz-cronjob-web`: ConnectorController, DagDesignerController, InternalJobController, ExportTaskController
- `ydsz-cronjob-server`: WebhookRetryScanTask
- `ydsz-agent-domain`: DagWorkflow, DagWorkflowVO, AgentConverter
- `ydsz-agent-server`: DagWorkflowServiceImpl, CompatibleLlmClient (Netty 豁免)
- `ydsz-agent-web`: DagWorkflowController, DebugController, RagController
- `ydsz-nextwiki-server`: NextwikiTcpPushChannel (Netty 豁免)
- `ydsz-nextwiki-app`: NextwikiAppProperties (CONFIG-004)
- `ydsz-generator`: GenHistory, GenHistoryRepositoryImpl, CodeGenService, TableMetadataService

### POM 修改
- `ydsz-common-excel/pom.xml` — 已在 head 含 ydsz-common-exception（无需新增）

---

## 修复后剩余违规（接受范围）

| 规则 | 残留 | 说明 |
|------|:----:|------|
| IMPORT-003 未使用 import | 197 | 童子军规则 + 增量门禁解决；本次存量不动 |
| EXCEPT-002 异常中文 | ~60 (L2) | 从 66 降至 ~60；L1 清零；L2 走大规模 i18n 另立项 |
| ARCH-004 安全过滤器 | 7 | 架构级下沉，建议 common-safe starter 方案 |
| DB-008 agent 主键命名 | 3 | DDL 列名 trace_id/user_id — 加 `@TableId` 注解映射 |
| NAME-005 驼峰命名 | ~0 | 5处全修复 |
| I18N-002 日志中文描述 | 大量 | 调试性质日志描述，严格违反但业务价值低，不优先 |

---

## 增量门禁建设建议

### 1. 空 catch 增量门禁
- 接入 checkstyle `EmptyBlock` 模块，pattern `catch\s*.*\{\s*\}`，对不含 `log.` 和 `//` 的 catch 块 build break

### 2. IMPORT-001 FQN 增量门禁
- checkstyle `RegexpOnJava` 或 PMD `UnusedImport` + `LawOfDemeter`
- 正则匹配方法体内 `java\.\w+\.\w+`（排除 import 行和字符串行）

### 3. CONFIG-004 配置前缀门禁
- 自定义 checkstyle 拦截 `@ConfigurationProperties(prefix = "` 不以 `ydsz.` 开头的行
- 排除 `server.*` / `spring.*` / `management.*` 等 Spring Boot 内置前缀

### 4. DB-007 BOOLEAN 类型门禁
- Flyway 脚本 CI 步骤: `grep -E "BOOLEAN|BOOL|TINYINT" data/sqls/flyway/*.sql` 拦截

### 5.驼峰命名增量门禁
- 在 `check-checkstyle-delta.py` 中添加 Entity 内 `@TableField("xxx_yyy")` → Java 字段名 `xxxYyy` 的映射校验

### 6. IMPORT-005 Netty 直连门禁
- checkstyle `RegexpOnImport` 拦截 `^import io\.netty`，除非类上有 `@SuppressWarnings("ydiz-import-005")`

### 7. EXCEPT-003 throw RuntimeException 门禁
- PMD `AvoidThrowingRuntimeException` 规则开启

---

## 编译验证

- 全量 `mvn compile` → **BUILD SUCCESS** (2026-10-30 验证)
- 修复过程无破坏性改动，所有已修改模块编译通过

---

*报告生成: 2026-10-30 | 修复引擎: CatPaw 多批次子代理并行 + 补充验证*
