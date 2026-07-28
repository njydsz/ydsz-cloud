# PMIS 公共能力使用审计报告

> 生成时间：2026-07-29
> 审计范围：9 个业务模块 × 12 个公共模块能力

## 1. 公共模块依赖覆盖率

| 公共模块 | project | system | workflow | message | cronjob | literule | agent | userinfo | nextwiki | 覆盖率 |
|---------|---------|--------|----------|---------|---------|----------|-------|----------|----------|--------|
| common-cache | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | 9/9 (100%) |
| common-event | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | 9/9 (100%) |
| common-notify | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | 9/9 (100%) |
| common-search | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | 9/9 (100%) |
| common-queue | ✅ | ❌ | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ | ✅ | 7/9 (78%) |
| common-thread | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | 9/9 (100%) |
| common-tenant | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | 9/9 (100%) |
| common-audit | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | 9/9 (100%) |
| common-sentry | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | 9/9 (100%) |
| common-redis | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | 9/9 (100%) |
| common-excel | ✅ | ✅ | ❌ | ✅ | ✅ | ✅ | ❌ | ✅ | ✅ | 7/9 (78%) |
| common-file | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ | 2/9 (22%) |

**说明**：
- `common-queue`：userinfo 和 system 不使用 MQ，属于合理不接入
- `common-excel`：workflow 和 agent 无导出需求，属于合理不接入
- `common-file`：仅 project 和 nextwiki 有文件存储需求，属于合理不接入

## 2. 错误码体系覆盖率

| 模块 | ResultCode 枚举 | 编码区间 | 状态 |
|------|----------------|---------|------|
| userinfo | UserInfoResultCode | B30xxx-B32xxx | ✅ 已有 |
| nextwiki | NextwikiExceptionCode | Wxxxxx | ✅ 已有 |
| project | ProjectResultCode | B40xxx-B44xxx | ✅ 新增 |
| system | SystemResultCode | B90xxx-B93xxx | ✅ 新增 |
| workflow | WorkflowResultCode | B70xxx-B75xxx | ✅ 新增 |
| message | MessageResultCode | B91xxx-B94xxx | ✅ 新增 |
| cronjob | CronjobResultCode | B92xxx-B93xxx | ✅ 新增 |
| literule | LiteruleResultCode | B93xxx-B93xxx | ✅ 新增 |
| agent | AgentResultCode | B94xxx-B94xxx | ✅ 新增 |

## 3. 架构规范达标情况

| 规范项 | 达标率 | 详情 |
|-------|--------|------|
| HealthIndicator 继承 AbstractModuleHealthIndicator | 9/9 (100%) | 全部继承 |
| Metrics 继承 AbstractModuleMetrics | 8/9 (89%) | literule 使用双轨制（已知技术债务） |
| SearchProvider 实现 | 9/9 (100%) | cronjob 有 2 个 |
| @Cacheable 使用 common-cache | 3/9 (33%) | workflow/nextwiki/system 使用，其余模块无缓存需求 |
| 事件发布（common-event） | 6/9 (67%) | workflow/userinfo/nextwiki/project/system/message 发布事件 |
| 线程池统一管理（common-thread） | 9/9 (100%) | 已清除所有 Executors fallback |
| Converter 方法命名规范 | 9/9 (100%) | postDtoToEntity/putDtoToEntity/entityToVO |
| ArchUnit 规则覆盖 | 25 条 | R1-R25 全覆盖 |

## 4. 前端公共包覆盖

| 公共包 | 覆盖率 | 说明 |
|-------|--------|------|
| @ydsz/shared-auth | 9/9 (100%) | 9 个子应用全部引用 |
| @ydsz/shared-api | 9/9 (100%) | 9 个子应用全部添加依赖 |
| @ydsz/types | 9/9 (100%) | 统一类型定义 |
| createCrudApi 使用 | 5/9 (56%) | system-web(4文件) + userinfo-web(2文件) 已迁移 |

## 5. 已知技术债务

| 编号 | 描述 | 影响 | 优先级 |
|------|------|------|--------|
| TD-1 | literule MicrometerRuleMetrics 双轨制 | 代码冗余 | P3 |
| TD-2 | 3 个模块(cronjob/literule/agent)未发布领域事件 | 事件驱动不完整 | P2 |
| TD-3 | 前端 4 个子应用的 CRUD API 未迁移到 createCrudApi | 代码重复 | P2 |
| TD-4 | ScheduledExecutorService 仍使用 Executors（common-thread 不支持） | 无法统一管理 | P3 |

---

*本报告由 `deploy/scripts/check-architecture-compliance.sh` 自动验证*
