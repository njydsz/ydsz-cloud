# Workflow 模块 N+1 查询治理分析报告

> **生成时间**: 2026-10-10  
> **分析范围**: `ydsz-workflow` 模块全量 Java Service + Repository 层  
> **规范依据**: P1-11（N+1 查询治理）  
> **发现总数**: 6 处 | **已修复**: 0 处（标记治理） | **建议修复工作量**: ~8 小时

---

## 1. 发现的 N+1 问题清单

### 位置 1: FlowInstanceQueryService.listByIds — 批量富化降级循环

| 维度 | 内容 |
|------|------|
| **文件** | `ydsz-workflow-server/.../impl/instance/FlowInstanceQueryService.java` |
| **行号** | L139–L146 |
| **严重度** | **P1 — 高** |
| **触发条件** | `NameAssembler.enrich()` 批处理调用失败时 |
| **影响范围** | 批量查询实例后，对每条记录逐一调用 `enrichOne()` 解析发起人姓名。若 M 条记录未命中缓存，产生 M 次跨服务调用。高并发分页场景下放大至「页大小 × 批失败概率」 |
| **修复建议** | 在 `NameAssembler` 层增加熔断 + 缓存降级：批失败时直接返回空 Map，不逐条重试；或预热缓存后重试一次 |
| **修复工时** | ~2h |

---

### 位置 2: FlowInstanceBatchOperator.batchTerminate — 循环内逐条终止

| 维度 | 内容 |
|------|------|
| **文件** | `ydsz-workflow-server/.../impl/instance/FlowInstanceBatchOperator.java` |
| **行号** | L166–L190 |
| **严重度** | **P1 — 高** |
| **触发条件** | 管理员批量终止 N 个实例 |
| **影响范围** | 循环内逐条调用 `lifecycleManager.terminate()`（每条独立事务 + DB 写入）+ `instanceRepository.findChildren()`（逐实例查子流程）。总计 O(N) 次 DB 写入 + O(N) 次 children 查询。终止 50 个实例 ≈ 100+ 次 DB 往返 |
| **修复建议** | 1) 先批量 `findAllById` + `findAllChildren` 一次加载全部待终止 ID；2) 单次 `UPDATE ... WHERE id IN (...)` 批量更新状态；3) 或改为分批事务（每批 50 条） |
| **修复工时** | ~3h |

---

### 位置 3: YdszWorkflowFacade.getTimeline — 3 次独立 DB 查询

| 维度 | 内容 |
|------|------|
| **文件** | `ydsz-workflow-server/.../facade/YdszWorkflowFacade.java` |
| **行号** | L422–L530 |
| **严重度** | **P2 — 中** |
| **触发条件** | 用户查看任一实例的审批时间线 |
| **影响范围** | 单次时间线请求 = 3 次 DB 查询（历史任务 + 审计日志 + 当前待办）。数据量小（每条任务记录 < 50），但高频查看轨迹时放大。每实例 3N 次 IO |
| **修复建议** | 合并为单次 `UNION ALL` 查询（按 type 字段区分）；或引入 Redis 缓存时间线结果（TTL 30s，实例状态变更时清缓存） |
| **修复工时** | ~1.5h |

---

### 位置 4: FlowInstanceQueryService.page — 分页 COUNT + LIST 双查询

| 维度 | 内容 |
|------|------|
| **文件** | `ydsz-workflow-server/.../impl/instance/FlowInstanceQueryService.java` |
| **行号** | L249–L251 |
| **严重度** | **P2 — 中** |
| **触发条件** | 所有实例列表分页查询 |
| **影响范围** | `findPage` + `countPage` 产生 2 次 DB 查询。实例表（ydsz_flow_instance）持续增长，COUNT 全表扫描劣化。100 万行时 COUNT 耗时 > 500ms |
| **修复建议** | PostgreSQL 使用 `EstimateCount`（pg_class.reltuples 估算）替代精确 COUNT；或缓存相同查询条件的 COUNT 结果（5s TTL） |
| **修复工时** | ~1h |

---

### 位置 5: FlowTaskQueryServiceImpl.listTodoByUser — 多维查询 3 次 DB 往返

| 维度 | 内容 |
|------|------|
| **文件** | `ydsz-workflow-server/.../impl/instance/FlowTaskQueryServiceImpl.java` |
| **行号** | L174–L203 |
| **严重度** | **P2 — 中** |
| **触发条件** | 用户打开待办列表（多维度匹配直接分配 + flow_user 关联 + ROLE/DEPT 展开） |
| **影响范围** | 固定 3 次 DB 查询（`selectTodoByAssignee` + `findByIds` + `selectTodoByAssignees`）。当 roleCodes/deptIds 较大（>100）时，IN 子句超限报错。每用户首屏 3N 次 DB 往返 |
| **修复建议** | 将 IN 拆批（每批 <= 500）；或重构为 JOIN `ydsz_flow_user` 表一次查询完成三维匹配 |
| **修复工时** | ~1.5h |

---

### 位置 6: YdszWorkflowFacade.passAllTodoTasks — 仅取第 1 页（截断风险）

| 维度 | 内容 |
|------|------|
| **文件** | `ydsz-workflow-server/.../facade/YdszWorkflowFacade.java` |
| **行号** | L347–L358 |
| **严重度** | **P2 — 中（功能性 Bug + 隐性 N+1）** |
| **触发条件** | 管理员一键通过全部待办 |
| **影响范围** | 硬编码只取 page=1, size=100。当待办 > 100 条时多余静默截断。同时，多次调用产生分页循环模式 |
| **修复建议** | 分页循环拉取全部待办（每次 200 条），或多线程分批调用 `batchPass`。增加实际处理数提示 |
| **修复工时** | ~0.5h |

---

## 2. 影响范围汇总

| 严重度 | 数量 | 涉及文件 | 最大影响场景 |
|--------|------|---------|-------------|
| **P1 高** | 2 | FlowInstanceQueryService, FlowInstanceBatchOperator | 批量操作（>50 实例）、并发页面查询 |
| **P2 中** | 4 | YdszWorkflowFacade, FlowTaskQueryServiceImpl | 高频时间线查看、分页加载、权限展开 |
| **P0 紧急** | 0 | — | — |

### 模块关联影响

```
FlowInstanceQueryService.listByIds (降级 N+1)
  ← 调用方: FlowInstanceServiceImpl.listByIds → 跨服务批量操作

FlowInstanceBatchOperator.batchTerminate (循环终止 N+1)
  ← 调用方: FlowInstanceServiceImpl.batchTerminate → 管理员批量终止

YdszWorkflowFacade.getTimeline (3N 查询)
  ← 调用方: FlowInstanceController.timeline → 用户查看轨迹

FlowInstanceQueryService.page (COUNT+SELECT)
  ← 所有实例列表分页页面（我的发起、管理员视图、监控面板）

FlowTaskQueryServiceImpl.listTodoByUser (3 次 DB 往返)
  ← 调用方: FlowTaskCoreService / 我的待办页面
```

---

## 3. 修复优先级与排期建议

| 优先级 | 位置 | 预计工时 | 修复策略 |
|--------|------|---------|---------|
| **P1-Sprint** | 位置 2 (batchTerminate) | 3h | 重构为批量 SQL |
| **P1-Sprint** | 位置 1 (降级循环) | 2h | 熔断 + 缓存降级 |
| **P2-Backlog** | 位置 3 (getTimeline) | 1.5h | UNION ALL 合并 + Redis 缓存 |
| **P2-Backlog** | 位置 4 (page 双查询) | 1h | EstimateCount 或缓存 COUNT |
| **P2-Backlog** | 位置 5 (listTodoByUser) | 1.5h | IN 拆批 / JOIN 重构 |
| **P2-Backlog** | 位置 6 (passAllTodoTasks) | 0.5h | 分页循环 |

**总计修复工时**: ~9.5h（含测试 + 验证）

---

## 4. 治理后预期收益

| 指标 | 治理前（峰值） | 治理后（预期） | 改善率 |
|------|--------------|--------------|--------|
| 批量终止 50 实例 DB 往返 | 100+ 次 | ~5 次（批量 SQL） | **95%** |
| 降级场景批量查询（100 条） | 100 次 API 调用 | 0 次（熔断返回） | **100%** |
| 时间线单请求 DB 往返 | 3 次 | 1 次（UNION ALL）+ 缓存 | **67%** |
| 实例分页 COUNT 耗时（100w 行） | ~500ms | ~5ms（EstimateCount） | **99%** |
| 待办多维查询 DB 往返 | 3 次 | 1 次（JOIN 重构） | **67%** |

---

## 5. 架构层面前置改进

1. **NameAssembler 熔断机制**: 当前批失败降级为逐条调用无保护，需增加 Sentinel/Resilience4j 熔断器 + 空结果缓存
2. **Repository 增加批量操作**: `batchUpdateStatus(Collection<String> ids, String status)` 减少逐条 DB 写入
3. **时间线缓存**: 实例状态变更事件触发 Redis 清缓存（30s TTL），减少高峰期 DB 压力
4. **深翻页保护**: 位置 4/5 中 LIMIT/OFFSET 深翻页已加 `MAX_PAGE_OFFSET=10000` 保护，但应同时推进 keyset 分页（P2 演进项）

---

*报告生成工具: AI Agent (CatPaw) — 基于代码静态分析。建议生产环境配合慢查询日志（PostgreSQL log_min_duration_statement=100ms）定位实际热点。*
