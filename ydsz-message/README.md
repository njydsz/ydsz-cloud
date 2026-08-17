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
| 批量发送 | MQ 异步批量（批次任务 + 进度推送） |
| 跨渠道抑制 | bizType + bizId + receiver + channel 维度 |
| 退信处理 | 邮件退信黑名单 + 自动拦截 |
| 配额管理 | Sender 维度配额 / 通道级计数 |
| 敏感词过滤 | DFA 字典树算法 O(n) |
| 监控 | MessageMetrics（Micrometer Counter + Timer）+ MessageHealthIndicator |

### 3. 关键 Controller（均位于 `/api/v1/message` 前缀下，共 21 个）

| 路径前缀 | 作用 |
|---|---|
| `/api/v1/message/send` | 发送消息（同步 / 异步 / 事务消息 / 批量 / 撤回 / 追踪） |
| `/api/v1/message/template` | 模板管理（含版本 / 预览 / 测试） |
| `/api/v1/message/preference` | 用户偏好 |
| `/api/v1/message/subscription` | 订阅管理 / 退订 |
| `/api/v1/message/notifications/inbox` | 站内通知收件箱（前端通知中心） |
| `/api/v1/message/stats` | 发送统计 |
| `/api/v1/message/deadLetter` | 死信队列（驼峰命名） |
| `/api/v1/message/route-rule` | 条件路由规则 |
| `/api/v1/message/user-channels` | 用户渠道设置 |
| `/api/v1/message/feedback` `/trace` `/read-status` `/read-receipt` `/archive/search` `/aggregate` `/recall` | 反馈 / 追踪 / 已读状态 / 回执 / 归档检索 / 聚合 / 撤回 等 |

## 数据库表设计

实体 `@TableName` 共映射 **15 张表**（DDL 由各部署环境统一维护，不在模块内）：

| 业务域 | 表名 | 说明 |
|---|---|---|
| **消息日志** | `ydsz_msg_log` | 消息发送主日志（按月分区） |
| **批量发送** | `ydsz_msg_batch` | 批量发送批次（聚合任务） |
| **聚合** | `ydsz_msg_aggregate` | 站内通知聚合（同类合并） |
| **站内通知** | `ydsz_msg_notification` | 站内收件箱（前端通知中心） |
| **回执** | `ydsz_msg_receipt` | 消息回执（送达/已读/点击/失败/超时） |
| **模板** | `ydsz_msg_template` | 消息模板（含 i18n/场景/审核） |
| | `ydsz_msg_template_version` | 模板版本历史 |
| **用户偏好** | `ydsz_msg_preference` | 用户偏好（免打扰/频率/语言） |
| **订阅** | `ydsz_msg_subscription` | 主题订阅（用户×主题×渠道） |
| **路由规则** | `ydsz_msg_route_rule` | 条件路由 + 通道降级 |
| **追踪** | `ydsz_msg_trace` | 消息全链路追踪 |
| **离线** | `ydsz_msg_offline` | 离线消息 |
| **反馈** | `ydsz_msg_feedback` | 用户反馈 |
| **用户渠道** | `ydsz_msg_user_channel` | 用户×渠道设置 |
| **变量源** | `ydsz_msg_variable_source` | 模板变量数据源 |

## 目录结构

```
ydsz-message/
├── pom.xml
├── ydsz-message-api/          # 对外 API（Feign Client + DTO + Fallback）
│   └── src/main/java/com/njydsz/message/api/
├── ydsz-message-domain/       # 领域层（Entity + DTO + Enum + Constant）
│   └── src/main/java/com/njydsz/message/domain/
│       ├── constant/
│       ├── dto/               # batch / canary / config / core / receipt / template
│       ├── entity/            # batch / canary / config / core / receipt / template
│       └── enums/            # batch / config / core / receipt / template
├── ydsz-message-infra/        # 基础设施层（Mapper + 仓储）
│   └── src/main/java/com/njydsz/message/infra/mapper/
│       ├── batch/
│       ├── canary/
│       ├── config/
│       ├── core/
│       ├── receipt/
│       └── template/
├── ydsz-message-server/       # 服务层（Service + Consumer + Producer + Config + Health）
│   └── src/main/
│       ├── java/com/njydsz/message/server/
│       │   ├── channel/      # 13 个渠道实现（含 TcpPushChannel）+ ChannelRouter
│       │   ├── config/        # AutoConfiguration + Properties + WebSocketConfig
│       │   ├── consumer/      # RocketMQ 消费者（MessageConsumer + BatchMessageConsumer + MessageDlqConsumer）
│       │   ├── filter/        # DFA 敏感词过滤器
│       │   ├── health/        # MessageHealthIndicator
│       │   ├── metric/        # MessageMetrics (Prometheus)
│       │   ├── producer/      # RocketMQ 生产者
│       │   ├── realtime/      # 实时推送服务（委托 common-socket）
│       │   ├── service/       # 76 个 Service 文件（含 23 个 *ServiceImpl）
│       │   │   ├── batch/     # 批量 + 聚合
│       │   │   ├── config/    # 偏好 + 订阅 + 路由 + 变量
│       │   │   ├── core/      # 发送 + 去重 + 限流 + 追踪 + 定时
│       │   │   ├── impl/      # 实现 + 跨渠道抑制 + 退信 + 配额 + DND
│       │   │   ├── receipt/   # 回执 + 撤回
│       │   │   └── template/  # 模板引擎 + 渲染
│       │   ├── template/      # DefaultTemplateEngine + RichMediaRenderer
│       └── resources/
│           ├── META-INF/
│           │   ├── spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
│           │   └── additional-spring-configuration-metadata.json
│           ├── mapper/        # MyBatis XML 映射文件
│           └── bootstrap.yml
├── ydsz-message-web/          # Web 层（Controller + 启动类）
│   └── src/main/java/com/njydsz/message/web/
│       ├── MessageApplication.java   # Spring Boot 启动类
│       └── controller/
│           ├── batch/
│           ├── canary/
│           ├── config/
│           ├── core/
│           ├── notification/
│           ├── receipt/
│           └── template/
└── README.md
```

## 配置文件

### 核心配置（prefix = `ydsz.message`）

| 配置项 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `ydsz.message.health-enabled` | Boolean | true | 是否启用健康检查 |
| `ydsz.message.retry-enabled` | Boolean | true | 是否启用重试扫描器 |
| `ydsz.message.default-priority` | String | NORMAL | 默认发送优先级 |
| `ydsz.message.max-content-length` | Integer | 1048576 | 消息内容最大长度（字符），0=不限 |
| `ydsz.message.message-ttl-seconds` | Long | 3600 | 消息 TTL（秒），0=不检查 |
| `ydsz.message.aggregate-scan-interval-ms` | Long | 60000 | 聚合扫描间隔 |
| `ydsz.message.retry-scan-interval-ms` | Long | 30000 | 重试扫描间隔 |
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
| `ydsz.message.smart-timing.dnd-buffer-seconds` | Long | 60 | DND 缓冲秒数 |
| `ydsz.message.smart-timing.max-defer-hours` | Long | 72 | 最大延迟小时数 |

### 熔断器配置（prefix = `ydsz.message.circuit-breaker`）

| 配置项 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `ydsz.message.circuit-breaker.failure-rate-threshold` | Integer | 50 | 失败率阈值（%） |
| `ydsz.message.circuit-breaker.slow-call-rate-threshold` | Integer | 80 | 慢调用率阈值（%） |
| `ydsz.message.circuit-breaker.slow-call-duration-seconds` | Long | 5 | 慢调用阈值（秒） |
| `ydsz.message.circuit-breaker.wait-duration-in-open-state-seconds` | Long | 30 | 熔断开启持续时间 |
| `ydsz.message.circuit-breaker.sliding-window-size` | Integer | 20 | 滑动窗口大小 |

### 重试策略配置（prefix = `ydsz.message.default-retry-policy`）

| 配置项 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `ydsz.message.default-retry-policy.max-retry-count` | Integer | 3 | 最大重试次数 |
| `ydsz.message.default-retry-policy.base-backoff-ms` | Long | 2000 | 基础退避（毫秒） |
| `ydsz.message.default-retry-policy.backoff-multiplier` | Double | 2.0 | 退避倍率 |
| `ydsz.message.default-retry-policy.max-backoff-ms` | Long | 60000 | 退避上限 |

### 敏感词配置

| 配置项 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `ydsz.message.sensitive-filter-enabled` | Boolean | true | 是否启用敏感词过滤 |
| `ydsz.message.sensitive-words` | String | （空） | 敏感词列表（逗号分隔） |

### 回执配置

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
| `ydsz.message.unsubscribe.secret` | String | （必填） | 退订 token 签名密钥 |
| `ydsz.message.unsubscribe.ttl-days` | Integer | 30 | token 有效期（天） |

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
