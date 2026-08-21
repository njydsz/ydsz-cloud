# ydsz-message

> 消息通知引擎（自研大厂级统一通知中心）

## 模块定位

| 属性 | 值 |
|---|---|
| **类型** | 部署单元（独立启动） |
| **端口** | **9004**（按构建顺序 5/10） |
| **服务名** | `ydsz-message` |
| **构建顺序** | 5/10 |
| **数据库** | PostgreSQL |
| **依赖** | Nacos、PostgreSQL、Redis、RocketMQ、Mail（SMTP） |

## 核心职责

本模块是 YDSZ 的**统一通知中心**，采用 5 层 DDD 架构，从 `ydsz-system` 拆分出来作为独立大厂级通知引擎。

### 1. 12 种通知渠道（`MessageChannelEnum`）

| 渠道 | 协议 | Provider |
|---|---|---|
| **SMS** 短信 | 阿里云短信 | `mock`（开发） / `aliyun`（生产） |
| **EMAIL** 邮件 | SMTP（SSL/STARTTLS） | 内置（支持 DKIM/退信追踪） |
| **PUSH** 推送 | 个推 Getui / 自研 TCP（common-netty） | `mock` / `getui` |
| **INAPP** 站内 | WebSocket（common-socket） | 内置 |
| **WEBHOOK** 通用 | HTTP/HTTPS | 内置 |
| **DINGTALK** 钉钉 | 群机器人 + 加签 | 内置 |
| **DINGTALK_WORK** 钉钉企业应用 | 应用消息 | 内置 |
| **WECOM** 企业微信 | 群机器人 | 内置 |
| **WECOM_APP** 企业微信应用 | 应用消息 | 内置 |
| **FEISHU** 飞书 | 群机器人 + 加签 | 内置 |
| **WX_MINI** 微信小程序 | 订阅消息 | `mock` / 内置 |
| **ALIPAY_MINI** 支付宝小程序 | 模板消息 | `mock` / 内置 |

> 说明：TCP 长连接推送是 PUSH 渠道的底层扩展（`TcpPushChannel`，`ydsz.message.tcp-push.enabled=true`），并非独立枚举渠道；渠道实现 Bean 共 13 个（含 TcpPushChannel），由 `ChannelRouter` 路由。

### 2. 核心能力

| 能力 | 实现 |
|---|---|
| 模板管理 | i18n / 版本 / 审核 / 场景 / `${var}` 嵌套变量 + `{{#if}}` 条件 + `{{#each}}` 循环 + 管道过滤器 |
| 站内通知 | 优先级 / 聚合 / 撤回 / 跳转 |
| 用户偏好 | 免打扰 / 频率上限 / 聚合 / 语言 |
| 订阅管理 | 主题级订阅 / 退订（退订中心） |
| 消息路由 | 条件路由 / 通道降级 / 多级降级链 |
| 限流 | Redis 令牌桶（Redisson）+ Resilience4j / receiver/template/tenant 多维限流 |
| 灰度 | 模板灰度标记（canaryFlag，按用户标签 / 比例） |
| 异步 | RocketMQ 生产/消费/死信 + Redis SET NX EX 幂等 |
| 回执 | 送达 / 已读 / 点击 / 失败 / 超时（5min 主动拉取 + 30min 超时补偿） |
| 智能定时 | 用户活跃度画像 + DND 免打扰 + 时区感知 |
| 批量发送 | MQ 异步批量（批次任务 + 进度推送 / SSE 实时推送） |
| 跨渠道抑制 | bizType + bizId + receiver + channel 维度 |
| 退信处理 | 邮件退信黑名单 + 自动拦截 |
| 配额管理 | Sender 维度配额 / 通道级计数 |
| 敏感词过滤 | DFA 字典树算法 O(n) |
| 监控 | MessageMetrics（Micrometer Counter + Timer）+ MessageHealthIndicator |

### 3. 关键 Controller（均位于 `/api/v1/message` 前缀下，共 25 个）

| 路径前缀 | Controller | 作用 |
|---|---|---|
| `/api/v1/message` | `MessageController` | 发送消息（同步 / 异步 / 事务消息 / 批量 / 取消定时 / 发送日志分页 / 批次进度查询） |
| `/api/v1/message/batch` | `BatchController` | 批量发送（提交批次 / 进度轮询 / SSE 实时推送） |
| `/api/v1/message/template` | `TemplateController` | 模板管理（CRUD / 审核） |
| `/api/v1/message/template/preview` | `TemplatePreviewController` | 模板预览渲染（按模板编码预览 / 自定义内容预览） |
| `/api/v1/message/template/version` | `TemplateVersionController` | 模板版本管理（版本历史 / 回滚 / 预览 / 试发） |
| `/api/v1/message/preference` | `PreferenceController` | 用户偏好（增删改查） |
| `/api/v1/message/subscription` | `SubscriptionController` | 订阅管理（订阅 / 退订 / 按主题+通道查询） |
| `/api/v1/message/unsubscribe` | `UnsubscribeController` | 退订中心（token 一键退订 / 预览 / 恢复订阅 / 退订记录查询） |
| `/api/v1/message/notifications` | `NotificationController` | 站内通知（发送 / 收件箱 / 已读 / 撤回 / 删除 / WebSocket 单推 / 广播 / Feign 单播） |
| `/api/v1/message/stats` | `MessageStatsController` | 发送统计（总览 / 通道 / 回执 / 转化漏斗 / 成本看板） |
| `/api/v1/message/dead-letter` | `DeadLetterController` | 死信队列（分页查询 / 手动重发） |
| `/api/v1/message/route-rule` | `RouteRuleController` | 条件路由规则（CRUD / 启用查询） |
| `/api/v1/message/user-channels` | `UserChannelBindingController` | 用户渠道绑定（增删改查 / 我的绑定） |
| `/api/v1/message/feedback` | `MessageFeedbackController` | 用户反馈（评分提交 / 平均评分 / 降频判定） |
| `/api/v1/message/trace` | `MessageTraceController` | 消息全链路追踪（按 msgId / traceId / 业务单据查询） |
| `/api/v1/message/read-status` | `ReadStatusController` | 已读状态同步（单条已读 / 批量已读 / 通知已读 / 全部已读 / 未读计数） |
| `/api/v1/message/read-receipt` | `ReadReceiptController` | 已读回执（短信短链跳转回调） |
| `/api/v1/message/archive/search` | `MessageArchiveController` | 归档检索（PostgreSQL 全文搜索） |
| `/api/v1/message/aggregate` | `AggregateController` | 站内通知聚合（分页查询 / 强制刷新 / 到期刷新） |
| `/api/v1/message/recall` | `RecallController` | 消息撤回（站内通知 / 按日志ID / 按消息ID / 批量撤回） |
| `/api/v1/message/receipt` | `ReceiptController` | 送达回执（服务商回调 / 按日志ID查询） |
| `/api/v1/message/retry` | `RetryPreviewController` | 重试策略预览（预设档位时间线 / 全部预设对比 / 可用预设列表） |
| `/api/v1/message/canary` | `CanaryController` | 模板灰度发布（创建实验 / 分配实验桶） |
| `/api/v1/message/health` | `SystemHealthController` | 健康检查（整体状态 / 各通道详细状态） |
| `/api/v1/message/ops` | `OpsController` | 运维操作（模板缓存统计 / 缓存清除 / BloomFilter 统计） |

## 数据库表设计

实体 `@TableName` 共映射 **18 张表**（DDL 由各部署环境统一维护，不在模块内）：

| 业务域 | 表名 | 说明 |
|---|---|---|
| **消息日志** | `ydsz_msg_log` | 消息发送主日志（按月分区） |
| **批量发送** | `ydsz_msg_batch` | 批量发送批次（聚合任务） |
| **聚合** | `ydsz_msg_aggregate` | 站内通知聚合（同类合并） |
| **站内通知** | `ydsz_msg_notification` | 站内收件箱（前端通知中心） |
| **回执** | `ydsz_msg_receipt` | 消息回执（送达/已读/点击/失败/超时） |
| **模板** | `ydsz_msg_template` | 消息模板（含 i18n/场景/审核） |
| | `ydsz_msg_template_version` | 模板版本历史 |
| **灰度** | `ydsz_msg_canary` | 模板灰度发布标记 |
| **用户偏好** | `ydsz_msg_preference` | 用户偏好（免打扰/频率/语言） |
| **订阅** | `ydsz_msg_subscription` | 主题订阅（用户×主题×渠道） |
| **路由规则** | `ydsz_msg_route_rule` | 条件路由 + 通道降级 |
| **追踪** | `ydsz_msg_trace` | 消息全链路追踪 |
| **离线** | `ydsz_msg_offline` | 离线消息 |
| **反馈** | `ydsz_msg_feedback` | 用户反馈 |
| **用户渠道** | `ydsz_msg_user_channel` | 用户×渠道设置 |
| **变量源** | `ydsz_msg_variable_source` | 模板变量数据源 |
| **租户配置** | `ydsz_msg_tenant_config` | 租户级通道配置 |
| **Outbox 事件** | `ydsz_msg_outbox` | 领域事件 Outbox（事务消息） |

## 目录结构

```
ydsz-message/
├── pom.xml                          # 父 POM（6 模块）
├── ydsz-message-api/                # 对外 API（Feign Client + DTO + Fallback）
│   └── src/main/java/com/njydsz/message/api/
│       ├── client/
│       └── fallback/
├── ydsz-message-app/                # 应用配置层（自动配置 + 健康检查 + OpenAPI）
│   └── src/main/java/com/njydsz/message/app/
│       ├── config/                  # MessageAppAutoConfiguration
│       ├── health/                  # MessageAppHealthIndicator
│       └── openapi/                 # MessageAppOpenApiConfiguration
├── ydsz-message-domain/             # 领域层（DTO + VO + Enum + Constant + Event + Query + Repository 接口）
│   └── src/main/java/com/njydsz/message/domain/
│       ├── constant/                # MessageConstants
│       ├── dto/                     # 34 个 DTO
│       ├── enums/                   # batch / config / core / receipt / template
│       ├── event/                   # 领域事件（MessageSentEvent / OutboxEvent 等 10 个）
│       ├── query/                   # 13 个查询对象
│       ├── repository/              # 18 个 Repository 接口
│       └── vo/                      # 23 个 VO
├── ydsz-message-infra/              # 基础设施层（Entity + Mapper + 仓储实现）
│   └── src/main/java/com/njydsz/message/infra/
│       ├── converter/               # MessageConverter
│       ├── entity/                  # 18 个 DO（@TableName）
│       ├── mapper/                  # batch / canary / config / core / receipt / template
│       └── repository/              # 18 个 RepositoryImpl
│   └── resources/
│       └── mapper/                  # MyBatis XML 映射文件
├── ydzs-message-server/             # 服务层（Service + Channel + Consumer + Producer + Config + Health）
│   └── src/main/
│       ├── java/com/njydsz/message/server/
│       │   ├── channel/             # 13 个渠道实现 + ChannelRouter + RecallChannel
│       │   ├── config/              # MessageProperties + ChannelProperties + 自动配置
│       │   ├── consumer/            # RocketMQ 消费者 + 去重 + 死信消费者
│       │   ├── event/               # 领域事件发布者 + Outbox 调度器 + 死信告警
│       │   ├── filter/              # DFA 敏感词过滤器
│       │   ├── health/              # MessageHealthIndicator + RedisHealthStatus
│       │   ├── listener/            # 跨模块事件监听 + 事务性领域事件监听
│       │   ├── metric/              # MessageMetrics (Prometheus)
│       │   ├── producer/            # RocketMQ 生产者 + 事务监听器
│       │   ├── realtime/            # 实时推送 + 离线消息服务
│       │   ├── search/              # 模板搜索 Provider
│       │   ├── service/             # 87 个 Service 文件（含 24 个 *ServiceImpl / Impl）
│       │   │   ├── chain/           # 发送管道（SendPipeline + 7 个 Handler）
│       │   │   ├── config/          # 偏好 + 订阅 + 路由 + 变量 + 灰度 + 租户配置
│       │   │   ├── core/            # 发送 + 渲染 + 查询 + 统计 + 追踪 + 健康
│       │   │   ├── impl/            # 实现 + 跨渠道抑制 + 退信 + 配额 + DND + 调度器
│       │   │   ├── retry/           # 重试预设 + 重试预览
│       │   │   └── batch/           # 批量 + 聚合
│       │   ├── template/            # 模板引擎 + AST 缓存 + 富文本渲染
│       │   ├── token/               # 退订 token
│       │   └── tracing/             # 链路追踪
│       └── resources/
│           └── META-INF/
│               ├── spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
│               └── additional-spring-configuration-metadata.json
├── ydzs-message-web/                # Web 层（Controller + 启动类 + 异常处理）
│   └── src/main/
│       ├── java/com/njydsz/message/web/
│       │   ├── MessageApplication.java      # Spring Boot 启动类
│       │   ├── controller/                 # 25 个 Controller（扁平化，按 Java 包组织）
│       │   └── handler/                    # MessageExceptionHandler（全局异常处理）
│       └── resources/
│           ├── bootstrap.yml               # 引导配置（Nacos 连接）
│           ├── config/
│           │   ├── ydsz-message-common.yaml # 公共配置（端口/Mail/通道/JSON/RocketMQ 等）
│           │   ├── ydsz-message-dev.yaml    # DEV 环境配置（线程池/日志级别）
│           │   ├── ydsz-message-sit.yaml    # SIT 环境配置
│           │   └── ydsz-message-uat.yaml    # UAT 环境配置
│           └── docs/
│               └── 配置说明.md
└── README.md
```

## 配置文件

### 核心配置（prefix = `ydsz.message`，绑定 `MessageProperties`）

| 配置项 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `ydsz.message.enabled` | Boolean | true | 消息中心总开关（`MessageAppAutoConfiguration` 条件注入） |
| `ydsz.message.channel-enabled` | Map<String, Boolean> | — | 通道全局开关（key=通道大写名） |
| `ydsz.message.default-priority` | String | NORMAL | 默认发送优先级 |
| `ydsz.message.default-async` | Boolean | false | 默认异步发送开关（高并发场景建议开启） |
| `ydsz.message.default-delivery-guarantee` | String | AT_LEAST_ONCE | 投递保证级别（AT_LEAST_ONCE/AT_MOST_ONCE/EXACTLY_ONCE） |
| `ydsz.message.aggregate-scan-interval-ms` | Long | 60000 | 聚合扫描间隔（毫秒，≥1000） |
| `ydsz.message.retry-scan-interval-ms` | Long | 30000 | 重试扫描间隔（毫秒，≥5000） |
| `ydsz.message.message-ttl-seconds` | Long | 3600 | 消息 TTL（秒），0=不检查 |
| `ydsz.message.max-content-length` | Integer | 1048576 | 消息内容最大长度（字符），0=不限 |
| `ydsz.message.mark-all-read-batch-size` | Integer | 500 | markAllRead 分批大小 |
| `ydsz.message.suppress-window-seconds` | Long | 300 | 通道抑制窗口（秒） |
| `ydsz.message.sender-daily-limit` | Long | 10000 | 单发送人每日上限（0=不限） |
| `ydsz.message.sender-hourly-limit` | Long | 1000 | 单发送人每小时上限（0=不限） |
| `ydsz.message.global-daily-limit` | Integer | 0 | 全局每日上限（0=不限） |
| `ydsz.message.global-hourly-limit` | Integer | 0 | 全局每小时上限（0=不限） |

### 限流配置（prefix = `ydsz.message.rate-limit`）

| 配置项 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `ydsz.message.rate-limit.receiver-enabled` | Boolean | true | receiver 维度限流 |
| `ydsz.message.rate-limit.receiver-permits` | Integer | 10 | receiver 每秒令牌数 |
| `ydsz.message.rate-limit.template-enabled` | Boolean | true | template 维度限流 |
| `ydsz.message.rate-limit.template-permits` | Integer | 100 | template 每秒令牌数 |
| `ydsz.message.rate-limit.tenant-enabled` | Boolean | true | tenant 维度限流 |
| `ydsz.message.rate-limit.tenant-permits` | Integer | 1000 | tenant 每秒令牌数 |

### 去重配置（prefix = `ydsz.message.dedup`）

| 配置项 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `ydsz.message.dedup.enabled` | Boolean | true | 是否启用智能去重 |
| `ydsz.message.dedup.ttl-seconds` | Integer | 60 | 去重窗口（秒） |

### 智能定时配置（prefix = `ydsz.message.smart-timing`）

| 配置项 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `ydsz.message.smart-timing.enabled` | Boolean | true | 是否启用智能定时 |
| `ydsz.message.smart-timing.urgent-bypass-dnd` | Boolean | true | URGENT 绕过 DND |
| `ydsz.message.smart-timing.disruptive-channels` | List<String> | [SMS,PUSH,DINGTALK,WECOM,FEISHU,WX_MINI,ALIPAY_MINI] | DND 生效的打扰型通道列表 |
| `ydsz.message.smart-timing.dnd-buffer-seconds` | Long | 60 | DND 缓冲秒数 |
| `ydsz.message.smart-timing.max-defer-hours` | Long | 72 | 最大延迟小时数 |

### 熔断器配置（prefix = `ydsz.message.circuit-breaker`）

| 配置项 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `ydsz.message.circuit-breaker.failure-rate-threshold` | Integer | 50 | 失败率阈值（%，1-100） |
| `ydsz.message.circuit-breaker.slow-call-rate-threshold` | Integer | 80 | 慢调用率阈值（%，1-100） |
| `ydsz.message.circuit-breaker.slow-call-duration-seconds` | Long | 5 | 慢调用阈值（秒，≥1） |
| `ydsz.message.circuit-breaker.wait-duration-in-open-state-seconds` | Long | 30 | 熔断开启持续时间（≥5） |
| `ydsz.message.circuit-breaker.permitted-number-of-calls-in-half-open-state` | Integer | 3 | 半开状态允许探测数（≥1） |
| `ydsz.message.circuit-breaker.sliding-window-size` | Integer | 20 | 滑动窗口大小（≥10） |
| `ydsz.message.circuit-breaker.minimum-number-of-calls` | Integer | 10 | 最小调用数（≥5） |

### 重试策略配置（prefix = `ydsz.message.default-retry-policy`）

| 配置项 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `ydsz.message.default-retry-policy.max-retry-count` | Integer | 3 | 最大重试次数 |
| `ydsz.message.default-retry-policy.base-backoff-ms` | Long | 2000 | 基础退避（毫秒） |
| `ydsz.message.default-retry-policy.backoff-multiplier` | Double | 2.0 | 退避倍率 |
| `ydsz.message.default-retry-policy.max-backoff-ms` | Long | 60000 | 退避上限 |

> 按通道覆盖：`ydsz.message.channel-retry-policies`（Map<String, RetryPolicy>），key 为通道大写名，未命中通道回退到上述默认策略。

### 敏感词配置（prefix = `ydsz.message.sensitive-filter`）

| 配置项 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `ydsz.message.sensitive-filter.enabled` | Boolean | true | 是否启用敏感词过滤 |
| `ydsz.message.sensitive-filter.words` | String | （空） | 敏感词列表（逗号分隔） |

### 回执配置（prefix = `ydsz.message`，回执相关项）

| 配置项 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `ydsz.message.receipt-pull-enabled` | Boolean | true | 是否启用回执拉取 |
| `ydsz.message.receipt-pull-scan-interval-ms` | Long | 120000 | 拉取扫描间隔 |
| `ydsz.message.receipt-pull-delay-minutes` | Long | 5 | 拉取延迟（分钟） |
| `ydsz.message.receipt-timeout-minutes` | Long | 30 | 超时阈值（分钟） |

### 死信告警配置（prefix = `ydsz.message.dead-letter-alert`）

| 配置项 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `ydsz.message.dead-letter-alert.enabled` | Boolean | true | 是否启用死信告警 |
| `ydsz.message.dead-letter-alert.threshold` | Integer | 10 | 告警阈值 |
| `ydsz.message.dead-letter-alert.window-minutes` | Integer | 60 | 统计窗口（分钟） |
| `ydsz.message.dead-letter-alert.cooldown-minutes` | Integer | 30 | 告警冷却（分钟） |

### 退订配置（prefix = `ydsz.message.unsubscribe`）

| 配置项 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `ydsz.message.unsubscribe.enabled` | Boolean | true | 是否启用退订中心 |
| `ydsz.message.unsubscribe.secret` | String | （内置默认值） | 退订 token 签名密钥（Base64，建议≥32字节） |
| `ydsz.message.unsubscribe.ttl-days` | Integer | 30 | token 有效期（天） |
| `ydsz.message.unsubscribe.base-url` | String | — | 退订链接 base URL |

### 成本配置（prefix = `ydsz.message.cost`）

| 配置项 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `ydsz.message.cost.enabled` | Boolean | true | 是否启用成本追踪 |
| `ydsz.message.cost.unit-prices` | Map<String, BigDecimal> | SMS=0.045, EMAIL=0.001, PUSH=0.0001, 其余=0 | 通道单条成本（元） |

### 服务商配置

| 配置项 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `ydsz.message.sms.provider` | String | mock | 短信服务商（aliyun/mock） |
| `ydsz.message.sms.strategy` | String | ROUND_ROBIN | 多服务商策略（ROUND_ROBIN/WEIGHTED/COST_FIRST/AVAILABILITY_FIRST） |
| `ydsz.message.sms.weights` | String | aliyun:5,tencent:3 | 多服务商权重（WEIGHTED 策略时生效） |
| `ydsz.message.sms.aliyun.access-key-id` | String | — | 阿里云 AccessKey ID |
| `ydsz.message.sms.aliyun.access-key-secret` | String | — | 阿里云 AccessKey Secret |
| `ydsz.message.sms.aliyun.sign-name` | String | — | 阿里云短信签名 |
| `ydsz.message.sms.aliyun.endpoint` | String | dysmsapi.aliyuncs.com | 阿里云 SMS 端点 |
| `ydsz.message.push.provider` | String | mock | 推送服务商（getui/mock） |
| `ydsz.message.push.getui.app-id` | String | — | 个推 AppID |
| `ydsz.message.push.getui.app-key` | String | — | 个推 AppKey |
| `ydsz.message.push.getui.master-secret` | String | — | 个推 MasterSecret |
| `ydsz.message.wx-mini.provider` | String | mock | 微信小程序服务商（wechat/mock） |
| `ydsz.message.alipay-mini.provider` | String | mock | 支付宝小程序服务商（alipay/mock） |

### 消息归档配置（prefix = `ydsz.message.archive`）

| 配置项 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `ydsz.message.archive.es-enabled` | Boolean | false | 是否启用 Elasticsearch 归档 |

### 群机器人 / Webhook 通道配置（prefix = `ydsz`，绑定 `ChannelProperties`）

| 配置项 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `ydsz.webhook.default-url` | String | （空） | 默认 Webhook URL |
| `ydsz.webhook.secret` | String | （空） | Webhook HMAC 签名密钥 |
| `ydsz.channel.dingtalk.default-token` | String | （空） | 钉钉群机器人 Token |
| `ydsz.channel.dingtalk.secret` | String | （空） | 钉钉群机器人加签密钥 |
| `ydsz.channel.dingtalk-work.enabled` | Boolean | false | 钉钉工作通知开关 |
| `ydsz.channel.dingtalk-work.app-key` | String | — | 钉钉工作通知 AppKey |
| `ydsz.channel.dingtalk-work.app-secret` | String | — | 钉钉工作通知 AppSecret |
| `ydsz.channel.dingtalk-work.agent-id` | Long | — | 钉钉工作通知 AgentId |
| `ydsz.channel.wechat-work.default-key` | String | （空） | 企业微信群机器人 Key |
| `ydsz.channel.wecom-app.enabled` | Boolean | false | 企业微信应用消息开关 |
| `ydsz.channel.wecom-app.corp-id` | String | — | 企业微信 CorpID |
| `ydsz.channel.wecom-app.corp-secret` | String | — | 企业微信应用 Secret |
| `ydsz.channel.wecom-app.agent-id` | Integer | — | 企业微信应用 AgentId |
| `ydsz.channel.feishu.default-hook` | String | （空） | 飞书群机器人 Hook |
| `ydsz.channel.feishu.secret` | String | （空） | 飞书群机器人加签密钥 |

## 启动顺序

依赖 `common` + `nacos` + `rocketmq`，**应在 `userinfo` / `system` 之后**启动（业务服务通过 Feign 调用本服务）。

## 常见问题

### Q1：RocketMQ 发送失败

检查 NameServer 是否启动（`rocketmq-console` 端口 8080），`ROCKETMQ_NAME_SERVER` 是否可达。

### Q2：模板变量未替换

模板使用 `${var}` 语法嵌套，支持 `{{#if}}` 条件和 `{{#each}}` 循环。检查：
1. 模板是否审核通过
2. 变量名是否一致
3. 是否传递了 `Map<String, Object> variables`

### Q3：回执一直 TIMEOUT

渠道不支持主动拉取时，回执状态保持 `NONE`。`ReceiptPuller` 调度器默认 5min 主动拉取，30min 超时补偿。
配置项：`ydsz.message.receipt-pull-delay-minutes` / `receipt-timeout-minutes`。

---

> 本模块是**异步消息核心**，所有发送都走 RocketMQ + 死信队列 + 重试扫描。
> 严禁在发送链路做耗时操作（DB 大查询、远程调用等），必须异步化。
