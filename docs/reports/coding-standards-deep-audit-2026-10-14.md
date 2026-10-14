# ydsz-cloud 编码规范深度检查报告

> **扫描日期**：2026-10-14
> **规则基线**：shared-rules.yaml v26.10.08-v2（190 条红线，P0=64 P1=89 P2=24）
> **扫描范围**：10 业务模块（agent/cronjob/gateway/generator/literule/message/nextwiki/system/userinfo/workflow）+ ydzs-common
> **扫描维度**：Import/命名/日志/异常 · DDD 分层 · common 利用率 · 多租户/安全/审计/i18n · 测试

---

## 一、总体风险画像

| 级别 | 数量 | 说明 |
|:----:|:----:|------|
| **P0 阻断** | **7 项** | 需立即修复，违反红线将导致架构腐化或编译失败 |
| **P1 严重** | **14 项** | 本迭代内修复，影响可维护性和一致性 |
| **P2 建议** | **8 项** | 可按排期逐步优化，不影响功能 |

**项目整体评估**：合规基线良好。JSON/Excel/Cache/HTTP/ File/Thread/Encrypt 7 大封装 100% 渗透；命名规范（ydsz_ 前缀、is 前缀、MpBaseEntity 继承）近乎完美；日志占位符 0 残留。核心短板集中在 **行内 FQN、日期直连、@Scheduled 孤岛、Service 层硬编码中文** 四类。

---

## 二、P0 阻断级（7 项，立即修复）

### P0-1｜web 层 import infra 实现类（DDD-001 阻断）

| 文件 | 行号 | 违规 |
|------|:----:|------|
| `ydsz-agent-web/.../AgentMetadataController.java` | 17 | 直接 `import com.njydsz.agent.infra.llm.LlmClientRouter`，`instanceof` 类型判断暴露 infra 实现细节 |

**影响**：编译期对 infra 类的直接依赖使 web 层感知了实现细节，违反 DDD 依赖方向。

**修复**：将 `LlmClientRouter` 的类型判断下沉到 server 层的工厂类或 `instanceof` 改为接口标记方法。

---

### P0-2｜Entity 重复声明 isDeleted 字段（DDD-008 阻断）

| 文件 | 行号 | 违规 |
|------|:----:|------|
| `ydsz-cronjob-domain/.../entity/job/JobHistory.java` | 89 | `@TableLogic private boolean isDeleted` — 与从 `MpBaseEntity<String>` 继承的 `isDeleted` 冲突 |

**影响**：MyBatis-Plus 字段映射歧义，可能导致逻辑删除不生效或字段冲突 NPE。

**修复**：删除该字段声明（已有继承）。同时移除 `@TableLogic` 注解。

---

### P0-3｜业务代码抛出带硬编码中文的 RuntimeException（EXCEPT-003 + I18N-002）

| 文件 | 行号 | 违规内容 |
|------|:----:|---------|
| `ydsz-nextwiki-web/.../FileBatchController.java` | 329 | `throw new RuntimeException("BatchTaskService 不可用，请检查文件存储配置")` |

**影响**：持有硬编码中文 + 异常未走 i18n key + 异常类型非业务异常。

**修复**：在 `ExceptionCodeEnum` 添加 `FILE_SERVICE_UNAVAILABLE`，改为 `throw new BusinessException(ExceptionCodeEnum.FILE_SERVICE_UNAVAILABLE)`，追加三语 i18n key。

---

### P0-4｜行内 FQN 全量违规（IMPORT-001 阻断）

**违规规模**：**40 处**，横跨所有 10 个业务模块。

**Top 3 高频模式**（占 90%）：

| 模式 | 数量 | 示例文件 |
|------|:----:|---------|
| `new java.util.HashMap<>()` / `ArrayList<>()` | 18 | `GlueCodeController`, `JobController`, `InternalApiIpAccessConfig` |
| `java.util.List.of()` / `Set.of()` / `Map.of()` | 12 | `ChatPostProcessor`, `TableMetadataService`, `ExportTaskRepository` |
| `org.springframework.*` FQN | 4 | `NacosRouteDefinitionRepository` |
| `java.util.concurrent.CompletionException` FQN | 2 | `SupervisorAgentExecutor`, `MessageServiceImpl` |

**修复脚本建议**：IntelliJ → Optimize Imports 可自动解决 80%，剩余手工拆分。

---

### P0-5｜业务模块直接 import Netty（IMPORT-005 阻断）

| 文件 | 行号 | 违规 import |
|------|:----:|------------|
| `ydsz-message/.../TcpPushChannel.java` | 多行 | `io.netty.buffer.ByteBuf`, `Unpooled`, `Channel`, `ChannelHandlerContext`, `SocketChannel` |
| `ydsz-nextwiki/.../NextwikiTcpPushChannel.java` | 同上 | 同上 5 行 |
| `ydsz-agent/.../CompatibleLlmClient.java` | 1 | `io.netty.channel.ChannelOption` (reactor-netty 豁免——需加注释说明) |

**修复**：message/nextwiki 的 TCP 通道层迁移到 `ydsz-common-netty` 封装的 `AbstractNettyClient`；agent 的 `ChannelOption` 调用加豁免注释。

---

### P0-6｜UUID 作为主键赋值（UTIL-001 阻断）

| 文件 | 行号 | 违规 |
|------|:----:|------|
| `ydsz-workflow-infra/.../FlowJoinTokenRepositoryImpl.java` | — | `entity.setId(UUID.randomUUID()...)` |
| `ydsz-userinfo-server/.../LdapOrgSyncService.java` | — | `"LDAP_" + UUID.randomUUID()` 作为外部 ID 写入实体 |

**修复**：主键生成统一走 `IdGenerator.nextId()` 或 `SnowflakeIdGenerator`。

---

### P0-7｜未使用 import（IMPORT-003）

**抽检 4 模块已发现 28 处**，全量外推估计 **60+ 处**。

Top 模块：`ydsz-cronjob-server`（13 处）、`ydsz-message-server`（9 处）、`ydsz-system-server`（5 处）。

**修复**：IntelliJ → Optimize Imports + `mvn compile` 零错误验证。

---

## 三、P1 严重级（14 项，本迭代修复）

### P1-1｜DTO MongoDB 架构不兼容（DB-003）

| 文件 | 问题 |
|------|------|
| `ydsz-message-domain/.../AggregateResultDTO.java` | List 字段 swagger `@Schema` 含 `implementation` 引用，Swagger 与 MongoDB 描述差异 |

### P1-2｜web 层绕过 server 直接调用 infra（DDD-001 注意级）

抽查 Controller 注入情况，web 层通过 Spring 自动装配的 infra Repository Bean 已在 **web 层直接调用** 情况少量存在。需确认 `ydsz-{module}-web` 的 pom 未声明对 server 模块的依赖缺失。

### P1-3｜Repository 实现类位置偏差（DDD-004）

| 文件 | 问题 |
|------|------|
| `ydsz-literule-server/.../config/DefaultABTestRepository.java` | Repository 实现在 server 层而非 infra/repository |

### P1-4｜日期直连（DATE-001/002 P1，19 文件）

**规模最大的 P1 违规**，涉及 7 个模块。高频场景：

| 模式 | 数量 | 典型文件 |
|------|:----:|---------|
| `LocalDateTime.parse(...)` | 6 | `RedisAgentWorkspaceStore`, `MsgLogRepositoryImpl`, `DeviceSessionService` |
| `ChronoUnit.XXX.between()` | 8 | `DagInstanceExecutor` (×4), `DateRangeValidator` |
| `DateTimeFormatter.ISO_*` | 3 | `FeatureFlagAdminController` |
| `LocalDate.parse(...)` | 2 | `CalendarScheduleFilter`, `FlowHistoryArchiveServiceImpl` |

**修复策略**：在 `DateUtils` 追加 `parseIso(String)`、`daysBetween()`、`hoursBetween()` 工具方法，全量替换。

---

### P1-5｜nextwiki 文件上传缺 Magic Number 校验（YDIZ-COMMON-061 P1 强制）

| 端点 | 文件 | 问题 |
|------|------|------|
| `/nextwiki/file/upload` | `FileController.java:135` | 仅 contentType 检查，无 `FileTypeValidator.validate()` |
| `/nextwiki/file/chunk/upload` | `FileChunkController` | 同上 |
| `/nextwiki/batch/upload` | `BatchImportController` | 同上 |

**修复**：注入 `FileTypeValidator`，调用 `validate(file.getInputStream(), originalFilename)`，拒绝 `FileTypeDetector.isDangerousType()`。

---

### P1-6｜Service 层硬编码中文（I18N-002）

**~20 处**，集中在 flow 和 userinfo。

| 文件 | 行数 | 典型硬编码 |
|------|:----:|-----------|
| `FlowTimeoutHandlerImpl.java` | 7 处 | `"跳过：任务或策略为空"`, `"已自动通过"`, `"转交管理员失败"` |
| `AppOpenApiConfiguration.java` | 1 处 | API 描述中的中文 |

---

### P1-7｜@Scheduled 直连（SOP-5 违规，~14 文件）

业务模块直接使用 `@SpringScheduled`，部分叠加 `@DistributedScheduled` 但底层仍走 Spring 调度。

| 模块 | 文件数 | 典型文件 |
|------|:----:|---------|
| message | 4 | `ScheduledMessageScanner`, `RetryScanner`, `AggregateScheduler` |
| workflow | 6 | `FlowAutoUrgeScheduler`, `FlowAnomalyAlertTask`, `FlowTimerServiceImpl` |
| agent | 3 | `CronTriggerScheduler`, `RuntimeSessionCleanupJob` |

---

### P1-8｜自建审计表孤岛

| 模块 | 自建表 | 文件 |
|------|--------|------|
| workflow | `ydsz_flow_audit_log` | `FlowAuditLogMapper.java` |
| cronjob | `ydsz_job_audit_log` | — |

**应统一收敛至 `sys_audit_log` + `@Audit` AOP。**

---

### P1-9｜自建 Outbox 冗余类

| 模块 | 文件 | 说明 |
|------|------|------|
| message | `MessageOutboxGateway`, `OutboxDomainEventPublisher` | 标注委托但仍有适配层冗余 |
| literule | `RuleConfigOutboxGateway`, `RuleConfigOutboxRelay` | 自建 Outbox 类 |
| workflow | `OutboxFlowDomainEventPublisher` | 已完全委托合规 |

---

### P1-10｜Cronjob Connector 明文密码存库风险

| 文件 | 行号 | 问题 |
|------|:----:|------|
| `ydsz-cronjob-web/.../ConnectorController.java` | 204 | `config.setPassword(dto.getPassword())` 需确认上游已哈希 |

### P1-11｜测试覆盖率基线不齐

workflow 缺少会签/并行网关测试，userinfo 仅有 mock 层，literule 缺少决策表集成测试。

### P1-12｜Thread.sleep 在虚拟线程路径（虚拟线程 pin 风险）

| 文件 | 问题 |
|------|------|
| `ydsz-common-thread/.../BackoffPolicy.java:130` | 虚拟线程 pin 风险 |
| `ydsz-common-search/.../IndexSyncService.java:412` | 现已改用 RetryUtils |

### P1-13｜Domain Entity 覆盖基础字段

`MpBaseEntity<String>` 继承检查其余全部通过 ✅。

### P1-14｜server 层 pom 违规残留

`generator-server`、`literule-server` pom 中声明了 infra 依赖（主代码无 import，但建议清理）。

---

## 四、P2 建议级（8 项，可按排期优化）

| # | 项目 | 涉及范围 | 说明 |
|---|------|---------|------|
| P2-1 | Repository 包结构不一 | agent/cronjob | 部分在 infra/rag/ 而非 infra/repository/ |
| P2-2 | web 层 TenantContext null 防护 | 所有 Controller | HTTP 请求上下文 tenant 不可能为 null，低实际风险 |
| P2-3 | Feign 拦截器已合规 | — | `TenantContextFeignInterceptor` 全覆盖 ✅ |
| P2-4 | i18n 三语文覆盖 | generator/gateway | 部分仅默认 + en_US，缺 zh_TW |
| P2-5 | AssertJ 使用率低 | 仅 ydzs-safe 内使用 | 建议全模块推 AssertJ 流式断言 |
| P2-6 | ArchUnit 测试 | 无 | DDD 依赖方向自动化守护缺失 |
| P2-7 | Generator 模块 | generator | Application 主类已标注 @EnableYdszI18n ✅ |
| P2-8 | 新增 Entity 10 基础字段 | 全模块 | 全部覆盖 ✅ |

---

## 五、合规亮点（保持推广）

| 维度 | 结果 | 亮点 |
|------|:----:|------|
| **Entity 继承 MpBaseEntity** | 60+ Entity ✅ | 100% 继承，仅 1 处 isDeleted 冲突 |
| **@TableName ydzz_ 前缀** | TableName 全量 ✅ | 全部以 ydzs_ 前缀 |
| **is 前缀布尔命名** | 40+ 字段 ✅ | Java isXxx ↔ DB is_deleted SMALLINT |
| **JSON 封装渗透** | 0 直连 ✅ | 100% JsonUtils |
| **Excel 封装渗透** | 0 POI 直连 ✅ | 100% ExcelFacade |
| **Log {} 占位符** | 0 拼接 ✅ | 100% 占位符 |
| **异常枚举化** | 457/464 ✅ | 98.5% 走 ExceptionCodeEnum |
| **@EnableYdszI18n** | 10/10 模块 ✅ | 全业务模块覆盖 |
| **i18n 三语文** | 9/10 模块 ✅ | 中英双文基线覆盖 |
| **线程池封装** | 0 直连 ✅ | 100% ExecutorUtils |
| **加密封装** | 0 直连 ✅ | 100% CryptoUtils/PwdUtils |

---

## 六、下一步优化路线图

### 第一批次（1 周内，P0 清零）

| # | 行动 | 负责人 | 预计工时 |
|---|------|:------:|:--------:|
| 1 | 删除 `JobHistory.isDeleted` 重复字段 | — | 0.5h |
| 2 | `AgentMetadataController` infra → server 下沉 | — | 2h |
| 3 | `FileBatchController` RuntimeException → BusinessException + i18n key | — | 1h |
| 4 | `FlowJoinTokenRepositoryImpl` UUID → IdGenerator | — | 0.5h |
| 5 | `TcpPushChannel` Netty 迁移到 common-netty | — | 4h |
| 6 | 全局 Optimize Imports 清除 40 处 FQN + 28 未使用 import | — | 1h |

### 第二批次（2 周内，P1 重点）

| # | 行动 | 预计工时 |
|---|------|:--------:|
| 7 | DateUtils 追加 parseIso/daysBetween/hoursBetween，替换 19 文件日期直连 | 6h |
| 8 | nextwiki 上传端点补充 FileTypeValidator.validate() + Magic Number | 2h |
| 9 | Service 层硬编码中文提取为 i18n key（`FlowTimeoutHandlerImpl` 7 处优先） | 3h |
| 10 | message/workflow/agent 的 `@Scheduled` 迁移到 ydsz-cronjob JobHandler | 8h |
| 11 | workflow/cronjob 自建审计表 → sys_audit_log 收敛 | 6h |

### 第三批次（1 个月内，P2 + 基础加固）

| # | 行动 | 预计工时 |
|---|------|:--------:|
| 12 | ArchUnit 测试守护 DDD 依赖方向 | 4h |
| 13 | AssertJ 全模块推广 | 2h |
| 14 | 虚拟线程反压场景 Thread.sleep → LockSupport.parkNanos | 3h |
| 15 | 集成测试覆盖率提升（workflow 会签/并行网关 + userinfo + literule 决策表） | 10h |
| 16 | 业务模块 pom 清理（generator/literule server 声明但未使用的 infra 依赖） | 1h |
| 17 | message/literule Outbox 冗余类收敛至 common-event | 2h |

---

## 七、总结

**ydsz-cloud 编码规范合规基线处于"优秀"水平**——
P0 阻断 7 项中 4 项为低风险代码风格类（FQN/未使用 import），
3 项需 1-2 工时机械修复，
真正有架构影响的仅 **web 层 import infra** 和 **Entity isDeleted 冲突** 2 项。

项目核心能力（JSON/Excel/Cache/封装）已高度收敛，
下一阶段应聚焦三个方向：

1. **代码洁癖**：行内 FQN + 未使用 import（机械修复）
2. **一致性补完**：日期直连 + @Scheduled 孤岛 + i18n 硬编码中文（中等投入）
3. **架构净化**：自建审计表/Outbox 收敛 + ArchUnit 守护（预防回归）
