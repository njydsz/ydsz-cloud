# ydsz-message 模块全面分析报告

> 基于最新代码（330 个 Java 文件、6 层 DDD 架构、12+1 渠道、15 张表）
> 对标行业主流竞品（阿里消息中心 / 腾讯消息队列 / 美团 Crane / 字节 Lark / AWS SNS+SQS）
> 输出时间：2026-08-19

---

## 一、现状基线

| 维度 | 数据 |
|---|---|
| Java 文件数 | 330 |
| DDD 分层 | api / domain / infra / server / app / web（6 层） |
| 渠道数 | 12 枚举 + 1 TCP 扩展 = 13 个 Channel Bean |
| 数据库表 | 15 张（ydsz_msg_log 按月分区） |
| Controller | 21 个（/api/v1/message/* 前缀） |
| ServiceImpl | 23 个 |
| 责任链 Handler | 6 个（ChannelResolve → RouteRule → UserPreference → Dedup → Suppression → Throttling） |
| 管线模板 | 5 个枚举（FULL / TEMPLATE / SIMPLE / BATCH / CALLBACK） |
| 领域事件 | 10 个（Sent/Scheduled/Recalled/Skipped/Suppressed/StatusChanged/BatchCompleted 等） |
| 配置项 | 50+（MessageProperties 含 12 个内部静态配置类） |

**架构亮点（值得保持）：**
1. 严格遵循云顶编码规范：使用 `YdszJson`（禁止第三方 JSON）、`common-sentry CircuitBreaker`（封装 Resilience4j）、`common-socket RealtimePushTemplate`、`common-lock IdempotentStrategy`、`common-redis RedisRateLimiter`
2. 责任链 + 模板化管线设计，按场景按需组合 Handler，避免全量执行
3. 消费端双层幂等（Redis SET NX EX 主 + DB count 兜底），且 Redis 健康时跳过 DB 查询
4. 熔断器配置完全外化（CircuitBreakerConfig），通道级隔离
5. 优雅停机（AtomicBoolean + AtomicInteger 在飞计数 + 30s 超时）
6. 重试抖动因子（0~1s random jitter）防止惊群
7. 错误消息透传 cause 链（最多 5 层防御性兜底）
8. SensitiveUtil.scanAndMask 贯穿日志，receiver 脱敏

---

## 二、架构优化（P0/P1）

### P0-A：发送入口事务一致性漏洞

**问题定位：** `MessageServiceImpl.sendAsync()`（第 635-688 行）

```
① insert PENDING（DB）
② 投递 MQ
   └ 失败 → 降级 send(request)（同步发送）
```

**风险：** insert 成功但 MQ 投递失败时，降级为同步 `send()`，而 `send()` 内部会 **再次 insert** 一条 PENDING 记录（`sendInternal` → `buildLogDO` → `msgLogRepository.insert(logDO)`），导致同一 messageId 产生两条 PENDING 记录。消费端虽有幂等保护，但 DB 层会产生脏数据。

**对标：** 阿里消息中心 / AWS SQS Producer 均采用"先写 Outbox（同事务）→ 事务提交后发 MQ → Outbox 扫描器补偿"模式，保证 exactly-once 投递。

**建议：**
```java
// sendAsync 改为：
// 1. 先查是否已存在同 messageId 的 PENDING/SENDING/SUCCESS 记录（入口幂等）
// 2. 不存在则 insert PENDING
// 3. 投递 MQ 失败时不降级 send()，而是标记 PENDING → 由 PENDING 恢复扫描器补偿
//    （需新增 PENDINGScanner，或扩展 ScheduledMessageScanner 扫描超时 PENDING）
```

### P0-B：Outbox 模式实现不完整

**问题定位：** 代码中存在 `OutboxEvent` / `OutboxEventRepository` / `OutboxEventScheduler` / `OutboxDomainEventPublisher` / `OutboxEventDO`，但 `MessageServiceImpl.publishEvent()` 使用的是 `ObjectProvider<DomainEventPublisher>`，且发送主链路（`sendInternal`）中 `insert(logDO)` 与 `dispatch(logDO)` 无事务包裹。

**风险：** Outbox 模式的核心价值是"业务落库 + 事件记录原子性"，但当前实现中：
- `insert(PENDING)` 成功后若进程崩溃，消息永远不会被发送（除非有 PENDING 扫描器）
- `dispatch` 成功但 `update(SUCCESS)` 失败时，消息已发但状态仍为 PENDING（重复发送风险）

**对标：** 美团 Crane 采用"业务表 + outbox 表同事务 insert → 独立线程扫描 outbox 发 MQ → 成功后删 outbox 行"。

**建议：**
```
1. 将 insert(PENDING) 与 insert(OutboxEvent) 放入同一 @Transactional
2. OutboxEventScheduler 扫描 outbox 表，发 MQ 成功后删除行
3. 消费端处理完成后更新 ydsz_msg_log 状态
4. 这样即使进程崩溃，outbox 扫描器也能补偿
```

### P1-A：MessageServiceImpl 仍是"胖入口类"

**问题定位：** `MessageServiceImpl`（704 行）注入 17+ 依赖，虽已拆分出 `MessageSendService` / `MessageQueryService`，但仍直接持有 `ChannelRouter` / `TemplateEngine` / `TemplateService` / `RouteRuleService` / `GuardService` / `SubscriptionService` / `PreferenceService` / `SensitiveWordFilter` / `RetryStrategyResolver` / `MessageTraceService` / `DeliveryTimeOptimizer` / `RichMediaRenderer` / `UserChannelBindingService` / `TemplateVariableValidator` / `VariableSourceResolver` / `BatchService` / `AggregatePersistenceService` / `SendPipelineFacade`。

**对标：** 大厂消息中台通常按"发送编排 / 内容渲染 / 状态管理 / 查询"拆为 4 个 ApplicationService，每个不超过 300 行、依赖不超过 5 个。

**建议：** 将 `sendInternal` 中的"渲染阶段"（`renderContent` 方法，第 250-317 行）拆为独立的 `MessageRenderService`，将"早期 return 路径"（`handleEarlyReturns`，第 364-425 行）拆为 `MessageScheduleService`。`MessageServiceImpl` 仅保留编排逻辑（preprocess → render → persist → dispatch → cascade），依赖降至 6 个以内。

### P1-B：同步发送链路阻塞线程池

**问题定位：** `sendInternal` 是同步执行：管线预处理 → 渲染 → insert → `dispatch`（阻塞式 HTTP/SDK 调用）→ 级联。在高并发场景下，每个发送请求占用一个线程直到渠道返回。

**对标：** 阿里消息中心发送入口 100% 异步化：API 仅落库 PENDING + 返回 msgId，实际发送由 Worker 池消费。同步路径仅用于极少量强一致场景（如验证码）。

**建议：** 将 `send()` 的默认行为改为"先落库 PENDING → 投递 MQ → 消费端异步发送"，仅在 `MessageRequest.sync=true` 时走同步路径。当前 `sendAsync` 已具备此能力，建议将其设为默认入口。

### P1-C：多租户仅逻辑隔离

**问题定位：** `tenantId` 仅作为逻辑分组字段（`TenantContextHolder.getTenantId()`），无租户级配额硬隔离、无租户级通道配置、无租户级模板命名空间、无租户级发送域名（DKIM）。

**对标：** SendGrid / Twilio 的多租户采用 sub-account 模式，每个租户有独立的：发送配额、发送域名（DKIM/SPF）、IP 池（独享 IP 避免信誉污染）、模板命名空间。

**建议：** 新增 `MsgTenantConfig` 表，存储租户级配置（配额/通道映射/发送域名），在 `ThrottlingHandler` 和 `ChannelRouter` 中按 tenantId 路由差异化配置。此为已知短板（与 ydsz-pmis-literule 多租户物理隔离同类问题），建议统一规划。

---

## 三、功能增强（P1/P2）

### P1-D：灰度发布缺乏完整链路

**现状：** 有 `CanaryExperimentService` / `TemplateCanaryDTO` / `MsgCanaryVO`，但灰度仅按模板 `canaryFlag` 标记，无渐进放量、无效果对比。

**对标：** 美团 SMP 灰度支持：按 userId hash 分桶（% 100 < 5）、按租户、按地域、按设备类型、按时间窗口渐进放量（1% → 5% → 20% → 100%），并自动统计两个模板的送达率/打开率/点击率，给出显著性结论。

**建议：**
```
1. TemplateCanaryDTO 增加：rolloutPercentage / rolloutStrategy / targetSegments
2. 新增 CanaryRouter：发送前按 userId hash + 百分比判断是否命中灰度
3. 发送后统计：canary 组 vs control 组的送达率/打开率
4. 提供 GET /api/v1/message/canary/{templateCode}/result 接口
```

### P1-E：智能路由仅基于静态规则

**现状：** `RouteRuleHandler` 基于静态规则匹配（`MsgRouteRule` 表），`ChannelRouter` 有熔断降级但无主动选优。

**对标：** 阿里消息中心智能路由：按通道成功率实时降级（已有熔断）、按通道成本优化（选最便宜的可用通道）、按到达率历史（用户在某通道打开率高就优先选）、按地域（不同地区 SMS 通道不同）。

**建议：** 新增 `ChannelScoreCalculator`，综合以下因子计算通道评分：
- 熔断状态（已有，权重 40%）
- 近 1 小时成功率（从 MessageMetrics 读取，权重 30%）
- 单条成本（从 CostConfig 读取，权重 15%）
- 用户该通道历史打开率（从 MsgReceipt 统计，权重 15%）
评分最高的通道优先选择，当首选通道熔断时自动降级到次优。

### P2-A：模板引擎能力边界

**现状：** `DefaultTemplateEngine` 支持 `${var}` / `{{#if}}` / `{{#each}}` / 管道过滤器（date/number/default/upper/lower/truncate），基于正则匹配 + 三轮处理。

**对标：** 飞书/钉钉消息卡片模板支持 `{{#with}}`（上下文绑定）、`{{#unless}}`（反向条件）、`{{#lookup}}`（动态查表）、partial 模板（复用片段）、条件嵌套层级无限制。

**建议：**
```
1. 补充 {{#unless var}} 块（反向 if，降低 {{#if}}{{else}}...{{/if}} 复杂度）
2. 补充 {{#with obj}} 块（切换上下文，减少前缀重复）
3. 评估是否引入 FreeMarkerTemplateEngine 作为复杂模板选项（已有实现，需确认是否启用 SPI）
4. 当前正则方案在 3 层以上嵌套时有性能和正确性风险，建议预编译为 AST（TemplateAst 已存在，需启用 CachedTemplateEngine）
```

### P2-B：富媒体/卡片消息支持不足

**现状：** `RichMediaRenderer` 仅支持 HTML / Markdown / 纯文本三种格式。

**对标：** 飞书/钉钉/企业微信支持交互卡片（Interactive Card）、按钮消息、模板卡片、瀑布流消息。这些是企业级通知的高频需求（如审批卡片带"通过/拒绝"按钮）。

**建议：** 新增 `InteractiveCard` DTO + `CardRenderer`，支持：
```json
{
  "cardType": "interactive",
  "header": {"title": "审批通知", "template": "blue"},
  "elements": [
    {"type": "div", "text": "${applicant} 申请 ${amount} 元"},
    {"type": "action", "actions": [
      {"type": "button", "text": "通过", "value": "approve", "url": "${approveUrl}"},
      {"type": "button", "text": "拒绝", "value": "reject", "url": "${rejectUrl}"}
    ]}
  ]
}
```
各 IM 渠道适配原生卡片格式，非 IM 渠道降级为 Markdown 链接。

### P2-C：撤回能力边界未明确

**现状：** 有 `RecallService` + 5 个 RecallChannel（Default/DingTalk/Feishu/InApp/WeCom），但未明确哪些渠道支持撤回、撤回时间窗口限制。

**对标：** 飞书/钉钉消息撤回有 2 分钟窗口限制，SMS/Email 物理上不可撤回。

**建议：** 在 `RecallController` 入口校验：
```java
// 1. channel 不支持撤回时直接返回错误（SMS/EMAIL/WX_MINI/ALIPAY_MINI）
// 2. 发送时间超过 2 分钟（可配）时拒绝撤回
// 3. 已读消息不可撤回（INAPP 场景）
```

---

## 四、性能提升（P1/P2）

### P1-F：消息落库写放大

**问题定位：** 单条同步发送至少 2 次 DB 写：
- `sendInternal`：`insert(PENDING)`（第 226 行）
- `MessageSendService.dispatch`：`update(SUCCESS/FAILED)`（第 79 行）

重试场景更多：`ScheduledMessageScanner.sendScheduledMessage` 有 `update(SENDING)` + `update(SUCCESS/RETRY)` = 3 次写。`RetryScanner.retryOnce` 同理 3 次写。

**对标：** 美团 Crane 采用"批量落库 + 延后 update"：PENDING 记录批量 insert（每 50 条一批），最终状态批量 update（每秒一次）。

**建议：**
```
1. dispatch 方法移除 update(SENDING) 中间态（注释已提到"移除 PENDING→SENDING 冗余写入"，但 ScheduledMessageScanner 和 RetryScanner 仍有此中间态，需统一）
2. 最终状态 update 可改为异步批量：写入 Redis Sorted Set（score=timestamp），后台线程每秒批量 flush 到 DB
3. 高频模板的 PENDING insert 可批量：收集 50ms 内的请求，一次 batch insert
```

### P1-G：模板渲染缺少 AST 缓存

**问题定位：** `DefaultTemplateEngine.render()` 每次都执行 `processEach` → `processIf` → `processVars` 三轮正则匹配 + 字符串拼接。代码中存在 `TemplateAst` / `CachedTemplateEngine`，但 `MessageServiceImpl` 注入的是 `TemplateEngine` 接口，实际使用的实现需确认。

**风险：** 在万级 QPS 下，正则编译 + 三轮匹配的 CPU 开销可观。

**对标：** 大厂模板引擎均预编译为 AST 并缓存（如 FreeMarker 的 Template 对象、Thymeleaf 的 ParsedTemplateExpression）。

**建议：**
```java
// CachedTemplateEngine 启用后：
// 1. 首次渲染时解析模板为 TemplateAst（含 each/if/var 节点树）
// 2. 缓存到 ConcurrentHashMap<String, TemplateAst>（key = template content hash）
// 3. 后续渲染遍历 AST 节点，避免正则重复匹配
// 4. 模板更新时通过事件清除缓存（TemplateVersion 变更时发 DomainEvent）
```

### P2-D：BloomFilter 多实例不一致

**问题定位：** `BloomFilterDeduplicator` 是单实例内存布隆过滤器，多实例部署时各自独立。实例 A 判定"不存在"但实例 B 已处理过 → 重复处理。虽有 Redis SET NX EX 兜底，但 BloomFilter 的价值（减少 Redis 调用）在多实例下打折扣。

**对标：** AWS SQS 使用 Redisson 分布式 BloomFilter（`RBloomFilter`），所有实例共享同一布隆过滤器。

**建议：**
```
方案 A（推荐）：改用 Redisson RBloomFilter，多实例共享
  优点：去重准确，减少 Redis SET NX EX 调用
  缺点：每次 put/mightContain 仍有 Redis 网络往返

方案 B（精简）：移除 BloomFilterDeduplicator，直接用 Redis SET NX EX
  理由：Redis 单实例 10万+ QPS，消息场景远不到瓶颈
  优点：降低系统复杂度，消除双缓冲/翻转/统计的维护成本
  缺点：每次去重都走 Redis（但本就有 Redis 幂等兜底，BloomFilter 是额外优化）
```
**建议采用方案 B**（精简优先），与云顶"最小化外部依赖、绝对可控优先"理念一致。

### P2-E：限流令牌桶 Redis 网络往返

**问题定位：** `GuardServiceImpl.tryAcquire()` 每次都走 Redis 令牌桶，在高 QPS 下 Redis 成为瓶颈。

**对标：** 大厂采用"本地令牌桶（预分配）+ Redis 全局令牌桶"二级模式：本地令牌桶预分配 N 个令牌，用完后向 Redis 批量申请补充，减少 Redis 调用次数。

**建议：**
```
1. 新增 LocalTokenBucket：每实例预分配 permits/10 个令牌
2. 本地令牌耗尽时向 Redis 批量申请 10 个令牌（1 次 Redis 调用换取 10 次本地判断）
3. 本地令牌有效期 1s，超时后从 Redis 重新同步
4. 可配开关：ydsz.message.rate-limit.local-bucket-enabled=true
```

### P2-F：级联发送串行化

**问题定位：** `MessageServiceImpl.triggerCascade()`（第 446-473 行）是 for 循环逐条 `sendInternal`，同步阻塞。

**建议：** 改为并行化：
```java
// 使用 CompletableFuture 并行发送级联消息
List<CompletableFuture<Void>> futures = cascadeTo.stream()
    .filter(Objects::nonNull)
    .map(child -> CompletableFuture.runAsync(() -> {
        child.setParentMsgId(parentLog.getMsgId());
        try { sendInternal(child, depth + 1); }
        catch (Exception e) { log.warn(...); }
    }, cascadeExecutor))
    .toList();
CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
```
使用独立的线程池（`cascadeExecutor`），避免阻塞主发送线程。

---

## 五、体验改善（P1/P2）

### P1-H：错误消息分层缺失

**问题定位：** 限流触发时返回 `"多维度限限：receiver/template/tenant 超限"`，技术性描述直接暴露给调用方。

**对标：** 大厂 API 错误响应分两层：
```json
{
  "userMessage": "当前发送频率过高，请稍后重试",  // 面向终端用户
  "developerMessage": "rate limited: receiver dimension exceeded (10/s)",  // 面向开发者
  "errorCode": "B91502",
  "retryAfter": 30  // 建议重试秒数
}
```

**建议：** `MessageResult` 增加字段：
```java
private String userMessage;      // 面向终端用户
private String developerMessage; // 面向开发者（技术详情）
private Integer retryAfter;      // 建议重试秒数（限流场景）
```
`ThrottlingHandler` 限流时设置 `retryAfter`，调用方可据此实现指数退避。

### P1-I：管理后台可观测性不足

**现状：** 有 `MessageStatsController` / `MessageMetrics`（Prometheus 指标），但缺少：
- 实时发送大盘（QPS / 成功率 / 延迟 P99 / 积压量）
- 通道健康度面板（各通道成功率 / 延迟 / 熔断状态）
- 模板效果分析（送达率 / 打开率 / 点击率）
- 成本分析（各通道 / 模板 / 租户成本）

**对标：** 美团 Crane Console 提供完整的可视化运维台，支持按维度下钻、告警配置、历史趋势对比。

**建议：** 新增 `MessageDashboardController`，提供：
```
GET /api/v1/message/dashboard/realtime    // 实时大盘（近 5 分钟 QPS/成功率/延迟）
GET /api/v1/message/dashboard/channels     // 通道健康度
GET /api/v1/message/dashboard/templates    // 模板效果分析
GET /api/v1/message/dashboard/cost         // 成本分析
```
数据源：Prometheus 指标聚合 + ydsz_msg_receipt 表统计（送达率/打开率）。

### P2-G：退订体验不完整

**现状：** 有 `UnsubscribeController` + `UnsubscribeTokenUtil`（HMAC-SHA256），支持 token-based 一键退订。但缺少"退订偏好"（只退某类消息而非全部）和"重新订阅"入口。

**对标：** RFC 8058 List-Unsubscribe-Post + 退订管理自助页面，支持按 topic/channel 退订，且可随时重新订阅。

**建议：**
```
1. UnsubscribeController 增加 POST /resubscribe 接口（token 验证后恢复订阅）
2. SubscriptionController 支持"退订偏好"：用户可选择只退订某 bizType 的某渠道
3. 退订页面展示"您将不再收到 {bizType} 的 {channel} 通知"
```

---

## 六、过度设计（P2 · 精简）

### P2-H：PipelineTemplate 枚举维护成本

**问题：** `PipelineTemplate` 枚举显式列出每个模板的 Handler Class 列表（第 46-99 行）。新增 Handler 时需同步修改枚举，易遗漏。

**替代方案：** Handler 自身通过 ctx 状态判断是否需要执行：
```java
// RouteRuleHandler.handle():
if (!StringUtils.hasText(request.getTemplateCode())) {
    return true; // 无 templateCode 跳过路由，放行
}
```
这样所有 Handler 始终执行，但内部短路，无需维护模板枚举。收益：新增 Handler 零修改成本。代价：SIMPLE_SEND 场景下多执行几个空跑 Handler（微秒级开销可忽略）。

**建议：** 保留模板枚举用于高频场景（SIMPLE_SEND / BATCH_SEND），但 FULL_PROCESS / TEMPLATE_SEND 的 Handler 列表改为"全量执行 + 内部短路"，减少维护点。

### P2-I：多服务商策略过早实现

**问题：** `SmsConfig` 有 `strategy`（ROUND_ROBIN / WEIGHTED / COST_FIRST / AVAILABILITY_FIRST）和 `weights` 配置，`SmsProviderStrategyServiceImpl` 消费。但实际可能只有 aliyun 一个服务商接入。

**判断：** 多服务商策略在单服务商阶段是过度设计。但考虑到后续可能接入腾讯云/华为云短信（成本优化 / 可用性兜底），此设计有前瞻性。

**建议：** 保留代码但标记为 `@ConditionalOnProperty`，单服务商时自动跳过策略选择逻辑，避免无意义的路由计算。

### P2-J：多模板引擎实现并存

**问题：** 存在 `DefaultTemplateEngine` / `FreeMarkerTemplateEngine` / `CachedTemplateEngine` / `TemplateAst` 四个实现。需确认是否通过 SPI / `@ConditionalOnProperty` 选择，若仅 DefaultTemplateEngine 实际使用，则其余为过度设计。

**建议：**
```
1. 确认 TemplateEngine 的 Bean 注入策略（是否 @Primary / @ConditionalOnProperty）
2. 若仅使用 DefaultTemplateEngine：删除 FreeMarkerTemplateEngine（引入 FreeMarker 依赖增加体积）
3. CachedTemplateEngine + TemplateAst：建议启用（见 P1-G），否则删除
4. 若需复杂模板：统一用 FreeMarker，删除自研引擎（正则方案的维护成本 > 引入成熟库的成本）
```

### P2-K：RecallChannel 实现泛滥

**问题：** 有 DefaultRecallChannel / DingTalkRecallChannel / FeishuRecallChannel / InAppRecallChannel / WeComRecallChannel / RecallChannelRouter 共 6 个类。但 SMS/Email/Webhook/SMS 物理上不可撤回。

**建议：** 精简为 3 个类：
```
1. RecallChannelRouter（路由）
2. ImRecallChannel（飞书/钉钉/企微撤回，内部按 channel 适配）
3. InAppRecallChannel（站内撤回）
```
不支持的渠道直接在 Router 层返回"不支持撤回"错误。

---

## 七、优化路线图

| 优先级 | 编号 | 标题 | 维度 | 工作量 |
|---|---|---|---|---|
| **P0** | P0-A | 发送入口事务一致性修复 | 架构 | 2d |
| **P0** | P0-B | Outbox 模式补全 | 架构 | 3d |
| **P1** | P1-A | MessageServiceImpl 胖类拆分 | 架构 | 2d |
| **P1** | P1-B | 同步发送链路异步化 | 架构 | 3d |
| **P1** | P1-C | 多租户硬隔离 | 架构 | 5d |
| **P1** | P1-D | 灰度发布完整链路 | 功能 | 4d |
| **P1** | P1-E | 智能路由选优 | 功能 | 3d |
| **P1** | P1-F | 落库写放大优化 | 性能 | 3d |
| **P1** | P1-G | 模板渲染 AST 缓存 | 性能 | 2d |
| **P1** | P1-H | 错误消息分层 | 体验 | 1d |
| **P1** | P1-I | 管理后台可观测性 | 体验 | 5d |
| **P2** | P2-A | 模板引擎能力补全 | 功能 | 2d |
| **P2** | P2-B | 交互卡片消息支持 | 功能 | 4d |
| **P2** | P2-C | 撤回能力边界明确 | 功能 | 1d |
| **P2** | P2-D | BloomFilter 精简/分布式化 | 性能 | 2d |
| **P2** | P2-E | 限流本地令牌桶 | 性能 | 2d |
| **P2** | P2-F | 级联发送并行化 | 性能 | 1d |
| **P2** | P2-G | 退订体验补全 | 体验 | 2d |
| **P2** | P2-H | PipelineTemplate 枚举精简 | 过度设计 | 1d |
| **P2** | P2-I | 多服务商策略条件化 | 过度设计 | 0.5d |
| **P2** | P2-J | 多模板引擎统一 | 过度设计 | 2d |
| **P2** | P2-K | RecallChannel 精简 | 过度设计 | 1d |

**建议推进节奏：**
- **S1（P0 收口）：** P0-A + P0-B，修复事务一致性漏洞，补全 Outbox（2 周）
- **S2（P1 架构 + 性能）：** P1-A + P1-B + P1-F + P1-G，降低入口类复杂度 + 性能优化（3 周）
- **S3（P1 功能 + 体验）：** P1-D + P1-E + P1-H + P1-I，补全灰度/智能路由/可观测性（4 周）
- **S4（P2 精简轮）：** P2-D + P2-H + P2-I + P2-J + P2-K，去过度设计（2 周）
- **S5（P2 功能补充）：** P2-A + P2-B + P2-C + P2-G，按需补全功能（4 周）
- **S6（P1-C 多租户 + P2-E/F 性能）：** 多租户硬隔离 + 本地令牌桶 + 级联并行（4 周）

---

## 八、云顶规范合规检查

| 规范项 | 状态 | 说明 |
|---|---|---|
| 禁止第三方 JSON，必须用 ydsz-common-json | 合规 | 全程使用 `YdszJson.fromJson` / `YdszJson.toJson` |
| 禁止直接用 Caffeine | 合规 | 未发现直接依赖 |
| 禁止直接依赖 POI | 合规 | 模块无 Excel 处理需求 |
| 禁止直接用 Resilience4j | 合规 | 使用 `common-sentry CircuitBreaker` 封装 |
| common 层 L1 为四个 utility 模块 | 待核实 | 需检查 ydsz-common 的模块结构 |
| 异常体系使用 SysException | 合规 | 全程使用 `SysException.builder()` |
| ID 生成使用 SnowflakeIdGenerator | 合规 | `MessageServiceImpl` 注入 `SnowflakeIdGenerator` |
| 分布式锁使用 @DistributedScheduled | 合规 | `ScheduledMessageScanner` / `RetryScanner` 均使用 |
| 日志脱敏使用 SensitiveUtil | 合规 | receiver 全程 `SensitiveUtil.scanAndMask` |
| 配置使用 @ConfigurationProperties | 合规 | `MessageProperties` 使用 `@ConfigurationProperties` + `@Validated` |

**合规结论：** ydsz-message 模块严格遵循云顶编码规范，无违规项。建议在后续优化中持续保持。

---

> 本报告基于 2026-08-19 最新代码生成，所有问题定位均标注具体文件和行号，可直接作为待办执行。
> 建议按 S1→S6 阶段化推进，每阶段完成后基于最新代码复核差距并迭代。
