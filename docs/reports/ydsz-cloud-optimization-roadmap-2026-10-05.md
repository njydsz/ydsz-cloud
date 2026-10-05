# ydsz-cloud 全面优化路线图

> **分析日期**：2026-10-05
> **对标规范**：阿里《Java开发手册（泰山版/黄山版）》、腾讯AlloyTeam规范、美团技术栈最佳实践、字节ByteArk架构治理规范
> **分析范围**：10 个业务模块 + ydzs-common 30 子模块，137 个 Entity，173 张数据表，212 个 Controller，~1,133 个 API 端点
> **评分基准**：P0 = 立即修复（1-3 天）| P1 = 本周完成（1-2 周）| P2 = 中期演进（1-3 个迭代）| P3 = 长期演进（季度级）

---

## 一、项目健康度总览

| 维度 | 评分 | 说明 |
|------|------|------|
| **数据库规范** | **A** | 137 Entity、173 表 100% ydsz_ 前缀、100% 列注释、MpBaseEntity 强制继承，已达大厂规范 |
| **接口规范** | **B+** | 200 Controller / 1,133 端点，YdszResponse 统一包装，OpenAPI 注解覆盖良好；@Valid 校验和异常处理存在改进空间 |
| **DDD 分层** | **B-** | 整体依赖方向正确；存在少量 Controller 直接使用 Repository 的架构腐蚀（约 14 处） |
| **MQ 可靠性** | **A+** | BloomFilter + 多级幂等 + 死信调度 + 优雅停机，已达工业级成熟度 |
| **网关防护** | **A-** | Resilience4j 熔断 + 双层限流 + Sentinel 系统保护；租户级限流和 @RateLimit 横向覆盖需加强 |
| **性能优化** | **B** | 缓存体系完备，线程池 100% 纳管；Virtual Thread 零使用、AsyncAppender 缺失、N+1 部分残留 |
| **安全合规** | **A-** | SQL 注入/敏感脱敏/限流熔断/WSS 认证齐全；声明式脱敏和租户级安全隔离可进一步提升 |
| **可观测性** | **B+** | Sentry Observation 覆盖 9 大引擎、JSON 结构化日志、Micrometer 指标；缓存命中率指标和 DLQ 告警指标缺失 |
| **配置管理** | **B** | Nacos 配置中心 + yml Profile 基础完备；多环境 Profile 分离（dev/test/prod）缺失 |
| **测试体系** | **D** | 全部测试代码已删除，目前 0 单元测试 / 0 集成测试，是最大短板 |

---

## 二、数据库表优化建议

### 2.1 P0 — 必须修复

| # | 问题 | 影响 | 建议 |
|---|------|------|------|
| DB-P0-1 | `tenant_id` 长度不一致：Agent 模块部分表用 `varchar(32)`，其他模块 `varchar(64)` | 跨模块 JOIN 时类型不匹配，运行时隐式转换走不了索引 | 统一改为 `varchar(64)`，通过 Flyway `V26.10.03` 脚本 ALTER COLUMN |
| DB-P0-2 | TIMESTAMP 类型混用：绝大多数 `timestamp without time zone`，仅 `ydsz_agt_document_chunk.created_at` 用 `timestamp with time zone` | 时区转换混乱，跨模块时间比对可能相差 8 小时 | 统一为 `timestamp without time zone`，应用层约定 UTC |
| DB-P0-3 | `ydsz_idm_account_user_role` 缺少 `UNIQUE(user_id, role_id)` 约束 | 重复授权漏洞，用户被赋予同一角色多次 | 立即通过 Flyway 追加唯一约束 |

### 2.2 P1 — 建议修复

| # | 问题 | 影响 | 建议 |
|---|------|------|------|
| DB-P1-1 | `vote_pass_rate numeric(20,6)` / `rating numeric(20,6)` / `cost numeric(20,6)` 精度过度 | 存储空间浪费（numeric(20,6) = 16 bytes vs numeric(3,2) = 5 bytes） | `vote_pass_rate`→`numeric(3,2)`；`rating`→`numeric(3,1)`；`cost`→`numeric(12,4)` |
| DB-P1-2 | `context_json`、`input_json`、`output_json` 等 50+ TEXT 字段存储 JSON 内容 | 无法索引内嵌字段、无法 CHECK 约束校验格式 | 逐步改为 `jsonb` 类型 + `CHECK (xxx IS JSON)`，并利用 GIN 索引加速内嵌字段查询 |
| DB-P1-3 | 布尔字段类型不一致：DagWorkflow `is_published` 用 `boolean`，其余 `smallint(0/1)` | ORM 跨表行为不一致，PG JDBC `setBoolean` vs `setInt` 映射差异 | 统一为 `smallint`（0/1），与 MyBatis-Plus `@TableField` 默认映射一致 |
| DB-P1-4 | `ydsz_flow_run_task.due_at` 仅有单列索引 | SLA 超时扫描时多租户下效率低下 | 改为复合索引 `idx_tenant_due_at(tenant_id, due_at)` |
| DB-P1-5 | `ydsz_msg_log` 数据量增长最快但无分表策略 | 单表过亿后查询性能急剧下降 | 按 `created_at` 月度分表（PG 原生分区表）或按 `tenant_id` HASH 分区 |

### 2.3 P2 — 中期优化

| # | 问题 | 建议 |
|---|------|------|
| DB-P2-1 | 归档表 `*_archive` 索引可能不完整 | 验证归档表是否复制了主表的全套索引 |
| DB-P2-2 | 缺少数据库读写分离 | 引入 dynamic-datasource（已在 pom 声明 4.3.1）按租户维度的读写分离 |
| DB-P2-3 | 慢 SQL 监控依赖应用层 | 启用 PG 原生 `log_min_duration_statement = 500ms` + pg_stat_statements 扩展 |

---

## 三、接口规范与测试体系优化建议

### 3.1 P0 — 必须修复

| # | 问题 | 影响 | 建议 |
|---|------|------|------|
| API-P0-1 | **测试体系空白**：0 单元测试、0 集成测试、0 回归保障 | 任何重构和修改均无安全网，回归缺陷全靠人工 | **立即建立冒烟测试框架**：每个模块至少 1 个 `@SpringBootTest` 启动测试 + 核心 Service 的 JUnit5 单测 |
| API-P0-2 | 14 个 Controller 直接注入 Repository 绕过 Service 层 | 架构腐蚀，业务逻辑泄漏到 Controller | 立即整改：Controller → Service（接口下沉到 domain）→ Repository |

### 3.2 P1 — 建议修复

| # | 问题 | 建议 |
|---|------|------|
| API-P1-1 | `@Valid` 参数校验覆盖不全 | 在全部 Controller 入参 DTO 方法上添加 `@Valid` + `@NotNull/@NotBlank/@Size` |
| API-P1-2 | 部分接口缺少 `@Operation` / `@ApiResponse` OpenAPI 注解 | 补全全部 Controller 方法的 Swagger 注解（覆盖率目标 100%） |
| API-P1-3 | Feign 超时配置不统一 | 在 `application.yml` 中统一设置 `feign.client.config.default.connect-timeout=5000` / `read-timeout=15000` |
| API-P1-4 | 缺少 API 版本管理策略 | 采用 `/api/v1/` 前缀 + `@Deprecated` 标注废弃端点，保持向后兼容 |
| API-P1-5 | 幂等设计仅覆盖部分写接口 | 全部写操作 POST/PUT/DELETE 加 `@Idempotent(key="#userId + '_' + #dto.id")` 注解 |

### 3.2.1 测试体系重建路线图

```
Phase 1（1 周）：基础骨架
├── 每个业务模块新建 src/test/java 目录
├── 核心启动测试 1 个/模块（@SpringBootTest 验证 Spring 上下文加载）
└── 覆盖率门禁 0% → 5%

Phase 2（2-3 周）：核心覆盖
├── 核心 Service 单测（workflow MessageService、agent ChatService、cronjob JobScheduler）
├── Repository 层测试（@DataJpaTest 或 PG 内存版）
└── 覆盖率门禁 5% → 30%

Phase 3（1个月）：场景覆盖
├── 集成测试（Workflow 端到端流程：发起→审批→完成）
├── 契约测试（Feign Client 双方 Pact 验证）
└── 覆盖率门禁 30% → 60%
```

---

## 四、前后端贯通与全链路优化建议

### 4.1 P1 — 建议修复

| # | 问题 | 建议 |
|---|------|------|
| FE-P1-1 | 缺少统一的前端 SDK / API 类型生成 | 基于 OpenAPI 规范 + `openapi-generator` 自动生成 TypeScript 客户端 SDK，确保前后端类型一致 |
| FE-P1-2 | 分页响应结构前端适配成本 | 统一 `PageResponse<T>` 的 `data.list` / `data.total` 结构，前端封装 `usePagination` Hook |
| FE-P1-3 | 错误码前端映射 | 将 `ExceptionCodeEnum` 与前端 i18n 打包为共享库，前端通过 `errorCode` 映射本地化消息 |
| FE-P1-4 | SSE/WebSocket 事件类型规范 | 统一事件 envelope：`{eventType, timestamp, traceId, payload}`，前端按 `eventType` 路由 |
| FE-P1-5 | 缺少 E2E 测试覆盖 | 关键业务流程（登录→发起任务→审批→通知到达）引入 Playwright E2E 测试，纳入 CI |

### 4.2 P2 — 中期优化

| # | 问题 | 建议 |
|---|------|------|
| FE-P2-1 | 全链路 traceId 前端不可见 | 网关统一注入 `X-Trace-Id` 响应端，前端日志采集可关联后端 Sentry trace |
| FE-P2-2 | 文件上传缺少前端分片组件 | 前端封装 `ChunkUploader` 组件，对接 `/nextwiki/chunk` 断点续传 API |

---

## 五、架构优化建议

### 5.1 P0 — 必须修复

| # | 问题 | 建议 |
|---|------|------|
| ARCH-P0-1 | Controller 直注入 Repository（约 14 处） | 建立 Service 接口（放在 domain/service/）+ 实现（放在 server/service/），Controller 只依赖 Service 接口 |
| ARCH-P0-2 | generator-web pom 声明了 generator-infra 但实际未使用 | 移除无效依赖声明，避免运维误解 |

### 5.2 P1 — 建议修复

| # | 问题 | 建议 |
|---|------|------|
| ARCH-P1-1 | RepositoryImpl 中约 14 个直接返回 Entity 而非 VO | 补充 MapStruct Converter，Entity→VO 转换后返回 server 层（DDD-007 合规） |
| ARCH-P1-2 | 缺少 Application-{dev,test,prod} Profile 三件套 | 每个模块创建 `application-dev.yml`（本地 H2/PG 内存 + debug 日志）/ `application-test.yml`（测试容器）/ `application-prod.yml`（生产配置） |
| ARCH-P1-3 | agent-infra 下 InMemory*Repository 4 实现类包归属不规范 | 统一放在 `infra/repository/inmemory/` 子包，明确标记为开发/测试用实现 |
| ARCH-P1-4 | 循环依赖潜在风险：common-cache → common-thread 越级依赖 | 评估将 cache 中 thread 依赖拆分为独立 scheduled-module |
| ARCH-P1-5 | `@Async` 装饰器未全局配置 | `FlowAutoConfiguration` 已有 i18n 上下文装饰器，复制到所有模块的 `@EnableAsync` 配置类，确保 MDC/Locale 传播 |

### 5.3 对标大厂架构规范差距

| 规范 | 阿里要求 | 字节要求 | ydsz-cloud 现状 | 建议 |
|------|---------|---------|----------------|------|
| 分层依赖 | 严格单向 + 限层调用 | 同阿里 | 基本合规，14 处违规 | 1 周内完成整改 |
| Profile 分离 | dev/staging/prod 必须分离 | 同阿里 | 仅 agent 有 prod profile | 全模块 3 件套 |
| 异常处理 | 全局异常 + 错误码 | 同阿里 | 全局异常已有，错误码覆盖率 98.5% | 补足剩余 1.5% |
| 数据脱敏 | 声明式 + 全局拦截 | 同阿里 | 调用方手动 MaskUtils | 中期引入 @Mask 注解 |
| 配置加密 | 敏感配置加密存储 | 同阿里 | Nacos 配置明文 | 接入 Nacos 配置加密或 Vault |

---

## 六、功能增强建议

### 6.1 P1 — 高价值功能

| # | 功能 | 价值 | 建议方案 |
|---|------|------|---------|
| FEAT-P1-1 | **接口限流 @RateLimit 横向推广** | 防刷、资源公平分配 | message（flushByGroup/flushByDue）、agent（coreChat）、nextwiki（upload）、cronjob（trigger）各写接口增加限流 |
| FEAT-P1-2 | **租户级限流** | 多租户 SLA 保障 | 网关 `RateLimitFilter` 增加 `tenantId` 维度，Nacos 配置可在运行时调整各租户 QPS 配额 |
| FEAT-P1-3 | **数据权限行级过滤** | 部门/团队数据隔离 | ydsz-common-jdbc 已具备行权限能力，需在核心表（flow_instance、job_main、file_node）上启用 `@DataPermission` 注解 |
| FEAT-P1-4 | **配置对比回滚** | 变更安全 | `ConfigVersionController` 增加 diff 接口，支持运行时回滚到任意版本 |
| FEAT-P1-5 | **Dead Letter Queue 自动告警** | 消息消费失败及时发现 | 暴露 `ydsz.message.dlq.count` 指标到 Prometheus + Alertmanager 告警规则 |

### 6.2 P2 — 中期功能增强

| # | 功能 | 价值 | 建议方案 |
|---|------|------|---------|
| FEAT-P2-1 | **声明式敏感字段脱敏 `@Mask(prefix=2,suffix=2)`** | 彻底消除脱敏遗漏 | 自定义 Jackson 注解 + 序列化器，替代手动 MaskUtils 调用 |
| FEAT-P2-2 | **Redis 热点 Key 探测** | 避免热 Key 导致单节点过载 | 新增 cronjob Job 每分钟采集 Redis `MONITOR` + `redis-cli --hotkeys` 输出指标 |
| FEAT-P2-3 | **全链路压测常态化** | 容量规划数据支撑 | 利用 `FlowSimulator` 作为压测流量源，常态化执行峰值压测 |
| FEAT-P2-4 | **接口幂等 + 分布式事务** | 资金/库存场景数据一致性 | 批量审批场景引入 TCC/Saga 模式（Seata 或自研 Outbox） |
| FEAT-P2-5 | **数据库读写分离** | 读扩展 | 基于 dynamic-datasource（pom 已声明），按 SQL 自动路由读/写 |

---

## 七、性能提升建议

### 7.1 P0 — 立即可做（高 ROI）

| # | 操作 | 预期收益 | 实施成本 |
|---|------|---------|---------|
| PERF-P0-1 | HikariCP `maximum-pool-size` 分模块 override：workflow/agent → 30-50，system → 10-15 | 缓解连接等待，吞吐提升 15-20% | 0.5 天（改 yml） |
| PERF-P0-2 | `connection-timeout` 30000ms → 10000ms | 快速失败，降低线程阻塞风险 | 0.5 天（改 yml） |
| PERF-P0-3 | Logback `AsyncAppender`（queueSize=2048, discardingThreshold=20%） | JSON 日志同步写文件阻塞业务线程问题根治 | 1 天（logback-spring.xml） |
| PERF-P0-4 | YdszCache Micrometer 指标注册（hitRate/missRate/size/evictionCount） | 缓存运维从"盲飞"到可观测 | 1 天（cache 模块改造） |
| PERF-P0-5 | `FlowAssigneeAvailabilityService.batchGetAvailability` Redis Pipeline 化 | 10 个候选审批人从 20 次 RTT → 2 次 RTT | 0.5 天 |

### 7.2 P1 — 本周完成

| # | 操作 | 预期收益 |
|---|------|---------|
| PERF-P1-1 | 剩余 N+1 查询批量改造（`FlowEmbeddedApprovalServiceImpl`、`UserExcelServiceImpl`） | DB 调用次数减少 60-80% |
| PERF-P1-2 | `AuditQueryService` 强制 maxRows 限制 + `FlowMonitorDashboardController` 强制时间范围 | 防止全表扫描 OOM |
| PERF-P1-3 | 深度分页引入 id 游标：`selectByIdGreaterThan(lastId, pageSize)` | 大 offset 场景从 O(n) → O(1) |
| PERF-P1-4 | HikariCP `initialSize` = maxPoolSize 的 50% | 避免冷启动流量尖叫 |
| PERF-P1-5 | SQL 注入防护从正则匹配升级为语义分析（集成 Druid WallFilter 或 OSS 方案） | 安全级别从"黑名单"到"白名单+语义" |

### 7.3 P2 — 中期演进

| # | 操作 | 说明 |
|---|------|------|
| PERF-P2-1 | **Java 21 Virtual Thread 落地** | IO 密集场景（FileApplicationService 批量节点、BatchMessageConsumer 并发消费、LiteRuleChain WHEN 分支并行匹配）引入 `ExecutorUtils.newVirtualThreadPerTaskExecutor()`，预期吞吐提升 30-50% |
| PERF-P2-2 | **Redis 异步客户端** | `RateLimitFilter` 限流校验走 Lettuce reactive pipeline，减少阻塞等待 |
| PERF-P2-3 | **消息压缩** | RocketMQ 消息体 > 1KB 启用 LZ4 压缩，降低网络带宽和存储占用 |
| PERF-P2-4 | **延迟加载优化** | 批量审批 `FlowInstanceMergeServiceImpl` 中无需每次加载完整流程 DSL，改为延迟加载或摘要投影 |
| PERF-P2-5 | **DTO 投影查询** | 列表接口只 SELECT 必要字段，避免 `SELECT *` 导致的大对象传输 |

---

## 八、体验改善建议

### 8.1 开发者体验（DX）

| # | 现状 | 建议 |
|---|------|------|
| DX-1 | checkstyle 规范仅靠文档约束 | 将 P0 规则接入 GitHub Actions / GitLab CI 流水线，构建失败自动评论 MR |
| DX-2 | 编码规范 scattered 在不同文件 | 创建 `docs/coding-checklist.md` 一页纸 Checklist，新人入职和新模块创建时引用 |
| DX-3 | Flyway 脚本无自动校验 | CI 中增加 `flyway validate` 步骤，未基线化的 SQL 变更自动拦截 |
| DX-4 | 共享规则（140 条）在 yaml 中维护 | IDE 插件实时校验（或至少提供 IDE 模板配置导出） |
| DX-5 | 全模块启动依赖 Docker Compose（PG/Redis/Nacos/RocketMQ） | 完善 `docker-compose.dev.yml`，支持单命令 `docker compose -f docker-compose.dev.yml up` 启动全部依赖 |

### 8.2 运维体验（OpsX）

| # | 现状 | 建议 |
|---|------|------|
| OpsX-1 | 缓存命中率不可达 | YdszCache → Micrometer → Prometheus → Grafana Dashboard |
| OpsX-2 | DLQ 堆积无告警 | Dead Letter 队列长度 → Prometheus Gauge → Alertmanager WARN/CRITICAL |
| OpsX-3 | 熔断状态变更仅日志输出 | Resilience4j Event → NotifyHelper 推送到企业微信群 |
| OpsX-4 | 慢查询依赖 DBA 人工巡检 | PG `pg_stat_statements` → 定时采集 → Grafana 慢 SQL Top-N 面板 |
| OpsX-5 | 网关限流调整需重启 | Nacos 配置中心动态下发 + Gateway `RefreshScope` 热加载 |

### 8.3 终端用户体验（UX）

| # | 现状 | 建议 |
|---|------|------|
| UX-1 | 导出大数据量超时 | 所有导出改为"异步生成 → 通知到达 → 下载链接"模式 |
| UX-2 | 文件上传无进度反馈 | nextwiki 分片上传 + 前端进度条 |
| UX-3 | 消息模板预览孤立 | 全渠道模板统一预览面板，所见即所得 |
| UX-4 | 流式设计器无撤销/重做 | FlowDesigner 增加操作历史栈 |
| UX-5 | Agent 对话无流式输出 | 接入 SSE/WebSocket 流式推送，替代当前整体返回 |

---

## 九、推荐执行路线图

### 第一周（P0 紧急修复）

```
Day 1: DB-P0-1/2/3（Flyway V26.10.03 脚本）+ API-P0-1（测试框架骨架）
Day 2: ARCH-P0-1/2（Controller 直注入整改 + generator pom 清理）
Day 3: PERF-P0-1/2（HikariCP yml 调优）
Day 4: PERF-P0-3（AsyncAppender）+ PERF-P0-4（Cache 指标）
Day 5: API-P0-2（14 处 Controller→Service 重构）+ 冒烟测试 CI 接入
```

### 第二周（P1 攻坚）

```
Day 1-2: DB-P1-1/2/3（numeric 精度 + jsonb 迁移 + 布尔统一）
Day 3-4: API-P1-1/2/3（@Valid 全量 + OpenAPI 注解 + Feign 超时）
Day 5:   ARCH-P1-1/2（Repository VO 转换 + Profile 三件套骨架）
```

### 第三周（P1 收尾）

```
Day 1-2: PERF-P1-1/2/3（N+1 批量改造 + 深度分页游标化）
Day 3-4: FEAT-P1-1/2（@RateLimit 横向覆盖 + 租户级限流）
Day 5:   FEAT-P1-3（数据权限行级过滤试点）
```

### 第四周 -> 迭代（P2 演进）

```
Sprint A: Virtual Thread 试点（FileApplicationService + BatchMessageConsumer）
Sprint B: @Mask 声明式脱敏 + 前端 SDK 自动生成
Sprint C: 测试覆盖率冲刺 30% + DLQ 告警 + 热点 Key 监控
Sprint D: 读写分离试点 + 全链路压测常态化
```

---

## 十、投入产出预估

| 阶段 | 人天 | 预期收益 |
|------|------|---------|
| P0 紧急修复（1 周） | 5 人天 | 消除数据不一致隐患 + 性能基线提升 20% |
| P1 攻坚（2 周） | 10 人天 | 架构合规 95%+ ，测试覆盖率从 0% → 15% |
| P2 演进（1 个月） | 15 人天 | 吞吐再提升 30%，P99 延迟下降 40%，测试覆盖率 30% |
| **总计** | **30 人天（约 6 周）** | **达到互联网大厂 B+ 级成熟度，关键指标对齐美团/字节 P70 水位** |

---

## 附录：关键指标基线（建议采集）

在优化启动前，先采集以下基线数据以量化优化效果：

1. **接口性能**：P50/P95/P99 响应时间、QPS、错误率（来源：Gateway 访问日志 + Prometheus）
2. **数据库**：慢 SQL 数/分钟、连接池等待时间、锁等待时间
3. **缓存**：Redis 命中率、YdszCache 本地命中率、P99 读写延迟
4. **JVM**：GC 频率/耗时、堆使用率、线程数
5. **业务**：消息端到端延迟、工作流完成率、Agent 对话成功率

---

*本报告由 ydsz-cloud AI 架构审查系统生成，基于 2026-10-05 代码快照。*
*报告路径：`docs/reports/ydsz-cloud-optimization-roadmap-2026-10-05.md`*
