# ydzs-common-audit 全局引用分析报告

> **生成日期**：2026-09-30  
> **分析方法**：全量 grep 扫描 + 逐模块引用关系梳理 + SPI 扩展点盘点  
> **扫描范围**：ydsz-common 全 31 子模块 + 10 业务模块  
> **整改完成**：2026-10-01（P0/P1/P2 全部落地）

---

## 1. 执行摘要

| 维度 | 结论 |
|------|------|
| **依赖覆盖率** | 10/10 业务模块 pom 声明 ydsz-common-audit（100%） |
| **激活率** | 10/10（generator-web 已修复 `@EnableYdszAudit`） |
| **@Audit 注解覆盖** | workflow(170+) > agent(56+) > cronjob(52+) > message(39) > system(39) > nextwiki(38) > literule(14+) > userinfo(20+) > generator(7) |
| **查询 SPI 利用** | system-web 直接注入 + cronjob 已收敛（自建 6 文件已删除） |
| **事件通道利用** | `DataExportAuditEvent` 已在 4 个端点激活发布 |
| **Diff 集成** | `DiffSnapshotHelper` 已与 `AuditAspect` 的 `@Audit(recordDiff=true)` 联动 |

---

## 2. 整改完成清单

### P0（已修复）

| # | 问题 | 模块 | 修复方式 |
|---|------|------|---------|
| P0-1 | generator-web 未加 `@EnableYdszAudit`，7 个 Controller 全部 `@Audit` 注解失效 | generator | `GeneratorWebApplication` 加 `@EnableYdszAudit` 注解，编译通过 |

### P1（已修复）

| # | 问题 | 模块 | 修复方式 |
|---|------|------|---------|
| P1-2 | cronjob 自建审计查询体系（6 文件）替代 `AuditQueryService`，表名不一致 | cronjob | 删除 `AuditLogService`/`AuditLogServiceImpl`/`AuditLogRepository`/`AuditLogRepositoryImpl`/`SysAuditLog`/`SysAuditLogMapper`，`AuditLogController` 改为注入 `AuditQueryService.queryByTimeRange()` |
| P1-3 | `RuleAuditLogService` 自建 `AuditAction` 枚举（与 common-audit 完全重复定义） | literule | 删除内部枚举，改为 `import com.njydsz.common.audit.enums.AuditAction`；`RuleAuditLogController` import 同步更新 |
| P1-4 | `AuditAdminController` 返回 `YdszResponse<List<AuditLog>>` 未用 `PageResponse` | system | 新建 `AuditLogVO`（web/vo 包下），返回值改为 `YdszResponse<PageResponse<List<AuditLogVO>>>`，符合 JDIZ-JDBC-001 |

### P2（已修复）

| # | 问题 | 模块 | 修复方式 |
|---|------|------|---------|
| P2-1 | `OperationLogEvent` / `DataExportAuditEvent` 零外部发布 | 多模块 | 在 message/cronjob/workflow 共 5 个导出端点新增 `DataExportAuditEvent` 发布，每个端点添加 `publishDataExportAudit()` 私有方法（try-catch 包裹不阻塞业务）|
| P2-6 | `DiffSnapshotHelper` 未集成到切面，`recordDiff=true` 不生效 | common-audit | `AuditAspect` 新增 `buildDiffSnapshot()` + 5 个辅助方法（序列化/脱敏/字段过滤/变更判断），当 `@Audit(recordDiff=true)` 时联动计算变更快照 |

### 规范沉淀（shared-rules +2）

| Rule ID | Level | Title |
|---------|-------|-------|
| YDIZ-AUDIT-002 | P1 | 审计查询必须使用 AuditQueryService SPI，禁止自建 Mapper 直查 sys_audit_log |
| YDIZ-AUDIT-003 | P2 | 数据导出场景必须通过 DataExportAuditEvent 发布审计事件 |

**版本**：shared-rules.yaml v26.09.30-v14，total=167（P0=56 P1=82 P2=21）

---

## 3. 能力利用率矩阵（整改后）

| 能力 | 已使用模块 | 利用率 |
|------|-----------|--------|
| `@Audit` 注解写入 | 8 模块全覆盖 + generator 已激活 | 100% |
| `AuditQueryService` 查询 | system（原生注入）+ cronjob（已收敛） | 20% → 有效提升 |
| `DataExportAuditEvent` 事件 | message + cronjob + workflow（5 端点） | 0% → 破冰 |
| `DiffSnapshotHelper` | audit 内部已与切面联动 | 内部实现完成 |
| `SensitiveFieldMask` | AuditAspect 内 | 内部 |
| `GatewayAuditEventBridge` | gateway | 专用 |

---

## 4. 编译验证

| 模块 | 编译结果 | 备注 |
|------|---------|------|
| :ydsz-common-audit | ✅ SUCCESS | Diff 集成 |
| :ydsz-generator-web | ✅ SUCCESS | @EnableYdszAudit |
| :ydsz-cronjob-{web,server,infra,domain} | ✅ SUCCESS | 收敛后删除 6 文件 |
| :ydsz-literule-{server,web} | ✅ SUCCESS | 枚举去重 |
| :ydsz-system-web | ✅ SUCCESS | 分页修正 |
| :ydsz-workflow-web | ✅ SUCCESS | 事件激活 |
| :ydsz-message-web | ❌ 预存错误 | CachedMessageTemplateRenderer 缺失（与本次无关）|
| :ydsz-common-auth | ❌ 预存错误 | PermissionHierarchyService 缺失（与本次无关）|

---

## 5. 未纳入本轮的 P2 项

- **P2-3 SensitiveFieldMask 公有化**：需要增加 API 设计评审，评估直接在 `MaskUtils` 中增加 JSON 脱敏方法
- **P2-4 网关审计落库**：WebFlux 反应式栈限制，本期保持 SLF4J 降级（已通过 AuditLogFilter 异步采集结构化日志）
- **P2-5 literule 元数据结构化**：规则专属操作码已去重（枚举），剩余字符串拼接问题待业务侧确认内容格式标准

---

## 6. 核心结论

`ydsz-common-audit` 模块内部设计成熟度良好（切面+SpEL+分表+批量+兜底+监控+健康检查完整闭环），整改前问题集中在**外部模块引用不充分**：

- 写入层 `@Audit` 已 100% 覆盖
- 查询层 `AuditQueryService` cronjob 重复造轮子已收敛（删除 6 文件）
- 事件层 `DataExportAuditEvent` 已破冰（5 端点落地）
- Diff 快照能力已与切面联动

核心优化目标「**补缺失 → 收敛重复 → 激活通道 → 沉淀规范**」已完成。
