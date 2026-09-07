# ydsz-common-notify

> 统一消息通知模块（L5 业务服务层）— 7 大渠道（Email/SMS/DingTalk/Feishu/WeCom/Insite/Webhook）+ 模板渲染 + 限流熔断 + DKIM 签名 + 时间窗聚合

提供 `NotifyService` 统一门面，屏蔽底层渠道细节，通过 SPI 机制自动发现所有 `NotifyChannelStrategy` 实现。支持 7 大通知渠道（邮件/短信/钉钉/飞书/企业微信/站内信/Webhook），集成模板引擎（SpEL）、DKIM 邮件签名、SMTP 探活、内容 XSS 净化、渠道降级、限流熔断、消息去重、时间窗聚合、重试队列、死信处理、事务安全发布、审计日志、国际化等企业级能力。

## 模块定位

| 属性 | 值 |
|---|---|
| **层级** | L5 业务服务层 |
| **类型** | 公共依赖库（不独立部署） |
| **作用** | 提供统一的多渠道消息通知发送能力，集成限流、熔断、降级、去重、聚合、模板渲染、重试等全链路保障 |
| **依赖** | common-core、common-util、common-exception、common-redis（可选）、common-thread；spring-boot-starter、spring-boot-starter-mail（可选）；可选依赖 rocketmq-spring-boot-starter、spring-boot-health、micrometer-core、spring-data-redis、jasypt、apache-httpclient5 |
| **版本** | 26.09.01-SNAPSHOT |

## 核心能力

### 1. 统一通知服务门面

| 类 | 说明 |
|---|---|
| `NotifyService` | 统一通知服务接口，业务入口，屏蔽渠道差异 |
| `NotifyServiceImpl` | 默认实现，集成限流器、熔断器、降级管理器、去重服务、偏好管理器、聚合器、审计服务等全部横切关注点 |
| `AsyncNotifyService` | 异步包装器，将发送操作提交到虚拟线程池异步执行，失败回落到重试队列 |
| `TransactionalNotifyPublisher` | 事务安全发布器，保证通知在数据库事务成功提交后才真正发出，避免事务回滚时已外发通知的一致性问题 |
| `NotifyHelper` | 辅助工具类，封装面向业务代码的便捷发送 API |

### 2. 渠道策略 SPI

| 接口/类 | 说明 |
|---|---|
| `NotifyChannelStrategy` | 渠道策略 SPI 接口，所有渠道实现通过容器自动发现并注册 |
| `EmailNotifySender` | 邮件渠道（Spring JavaMailSender，支持 SSL/STARTTLS/DKIM/追踪像素） |
| `SmsNotifySender` | 短信渠道（阿里云 SMS Provider，可扩展为腾讯云/华为云） |
| `DingTalkNotifySender` | 钉钉渠道（含签名算法：HMAC-SHA256 + timestamp） |
| `FeishuNotifySender` | 飞书渠道（Webhook + Token 鉴权） |
| `WeComNotifySender` | 企业微信渠道（应用消息推送） |
| `InsiteNotifySender` | 站内信渠道（WebSocket/轮询推送） |
| `WebhookNotifySender` | 通用 Webhook 渠道（自定义 HTTP 回调） |

渠道枚举：

| 枚举值 | 渠道 | 说明 |
|---|---|---|
| `EMAIL` | 邮件 | SMTP + JavaMailSender，支持 DKIM/追踪/模板渲染 |
| `SMS` | 短信 | 阿里云 SMS，可扩展其他 Provider |
| `DINGTALK` | 钉钉 | 机器人 Webhook + 签名算法 |
| `FEISHU` | 飞书 | 机器人 Webhook + Token 鉴权 |
| `WECOM` | 企业微信 | 应用消息推送 |
| `INSITE` | 站内信 | 站内实时消息 |
| `WEBHOOK` | Webhook | 通用 HTTP 回调，自定义 Headers/Body |

### 3. 模板渲染

| 类 | 说明 |
|---|---|
| `TemplateEngine` | 模板引擎接口，提供 `render(String template, Map vars)` 方法 |
| `SpelTemplateEngine` | 默认实现，SpEL 表达式解析模板变量 |
| `NotifyTemplate` | 模板实体（含 name、content、description 字段） |
| `HtmlTemplateRegistry` | HTML 邮件模板注册表，集中管理可用 HTML 模板 |
| `TemplateHotLoader` | 热加载器，支持从文件系统动态加载/重新加载模板 |
| `TemplateVariableValidator` | 模板变量校验器，渲染前校验引用变量是否齐全 |
| `NotifyTemplateAutoConfiguration` | 模板引擎独立自动配置，与主配置解耦 |

### 4. 限流熔断

| 类 | 说明 |
|---|---|
| `NotifyRateLimiterManager` | 通知限流管理器，按渠道/租户维度滑动窗口限流，委托 `RedisRateLimiter` 实现分布式限流 |
| `NotifyCircuitBreakerRegistry` | 熔断器注册表，为每个渠道/Provider 维护独立的熔断状态机（CLOSED → OPEN → HALF_OPEN） |
| `NotifyCircuitBreaker` | 单个渠道熔断器，基于失败率触发熔断，在下游持续异常时切断请求防止线程池耗尽 |
| `NotifyFallbackManager` | 渠道降级管理器，在首选渠道触发熔断或不可用时，按策略降级到备用渠道 |

### 5. 消息去重与聚合

| 类 | 说明 |
|---|---|
| `NotifyDedupService` | 通知去重服务，基于内容指纹 + 时间窗拦截重复通知，避免同一事件在重试/多渠道下骚扰用户 |
| `NotificationAggregator` | 消息聚合器接口，将短时间窗内同接收方的多条通知合并为一条 |
| `TimeWindowAggregator` | 默认实现，30 秒聚合窗口、单批上限 100 条 |

### 6. 重试队列与死信

| 类 | 说明 |
|---|---|
| `NotifyRetryQueue` | 重试队列接口，承接发送失败的异步重试 |
| `PersistentNotifyRetryQueue` | Redis 持久化实现（支持最大重试次数、批量重试），依赖 `StringRedisTemplate`；不可用时退化为内存实现 |
| `DeadLetterHandler` | 死信处理器接口，承接多次重试仍失败的通知 |
| `InMemoryDeadLetterHandler` | 默认实现，内存存储（进程重启后丢失），业务方可替换为持久化实现 |

### 7. 邮件安全

| 类 | 说明 |
|---|---|
| `DkimSigner` | DKIM 邮件签名器，对出站邮件施加 DKIM 签名，提升投递可信度 |
| `EmailContentSanitizer` | 邮件内容 XSS 净化器，清理 HTML 邮件中的恶意脚本 |
| `NotifyPasswordResolver` | SMTP 密码解析器，支持 Jasypt 加密密码的运行时解密 |
| `EmailSmtpHealthChecker` | SMTP 健康探活器，定期连通 SMTP 服务器以判定邮件渠道可用性 |
| `EmailTrackingService` | 邮件追踪服务，通过埋点像素 + Redis 记录邮件打开与点击行为 |

### 8. 其他横切能力

| 类 | 说明 |
|---|---|
| `NotifyAuditService` | 通知审计服务，记录发送/投递/失败等关键事件 |
| `NotifyMetrics` | 通知指标采集器，聚合发送量/成功率/延迟/渠道分布至 Micrometer |
| `NotifyPreferenceManager` | 用户通知偏好管理器，维护按用户/租户维度的渠道与免打扰偏好 |
| `NotifyI18nService` / `NotifyI18nResolver` | 国际化服务与解析器，按接收方语言偏好渲染通知内容 |

## 接入方式

### 1. POM 引入依赖

```xml
<dependency>
    <groupId>com.njydsz</groupId>
    <artifactId>ydsz-common-notify</artifactId>
</dependency>
```

### 2. 配置启用

```yaml
ydsz:
  notify:
    enabled: true
    email:
      enabled: true
      smtp-host: smtp.example.com
      smtp-port: 465
      from-mail: noreply@example.com
      password: your-password
      ssl:
        enabled: true
    sms:
      provider: aliyun
      endpoint: dysmsapi.aliyuncs.com
      access-key-id: xxx
      access-key-secret: xxx
    dingtalk:
      webhook-url: https://oapi.dingtalk.com/robot/send
      secret: xxx
    feishu:
      webhook-url: https://open.feishu.cn/open-apis/bot/v2/hook/xxx
    ratelimit:
      enabled: true
      default-max-requests: 100
      default-window-seconds: 60
```

### 3. 使用示例 - 发送通知

```java
@Autowired
private NotifyService notifyService;

// 发送到指定渠道
NotifyReceipt receipt = notifyService.send(
    NotifyRequest.builder()
        .channel(NotifyChannel.EMAIL)
        .to("user@example.com")
        .subject("订单确认")
        .templateName("order-confirmation")
        .variable("orderNo", order.getOrderNo())
        .variable("amount", order.getAmount())
        .build()
);
```

### 4. 使用示例 - 事务安全发布

```java
@Autowired
TransactionalNotifyPublisher publisher;

@Transactional
public void createOrder(Order order) {
    orderRepo.save(order);
    // 事务提交后才真正发送通知
    publisher.publish(
        NotifyRequest.builder()
            .channel(NotifyChannel.SMS)
            .to(user.getPhone())
            .templateName("order-created")
            .variable("orderNo", order.getOrderNo())
            .build()
    );
}
```

## 配置项

### NotifyProperties（`ydsz.notify.*`）

| 配置 | 默认值 | 说明 |
|---|---|---|
| `ydsz.notify.enabled` | `true` | 是否启用通知模块 |

### 邮件渠道（`ydsz.notify.email.*`）

| 配置 | 默认值 | 说明 |
|---|---|---|
| `ydsz.notify.email.enabled` | `false` | 是否启用邮件渠道 |
| `ydsz.notify.email.smtp-host` | - | SMTP 服务器地址 |
| `ydsz.notify.email.smtp-port` | `25` | SMTP 服务器端口 |
| `ydsz.notify.email.from-mail` | - | 发件人邮箱 |
| `ydsz.notify.email.password` | - | SMTP 密码（支持 Jasypt 加密） |
| `ydsz.notify.email.auth` | `true` | 是否启用 SMTP 认证 |
| `ydsz.notify.email.connection-timeout` | `5000` | 连接超时（毫秒） |
| `ydsz.notify.email.timeout` | `10000` | 读取超时（毫秒） |
| `ydsz.notify.email.starttls` | `false` | 是否启用 STARTTLS |
| `ydsz.notify.email.ssl.enabled` | `false` | 是否启用 SSL |
| `ydsz.notify.email.encoding` | `UTF-8` | 邮件编码 |

### 短信渠道（`ydsz.notify.sms.*`）

| 配置 | 默认值 | 说明 |
|---|---|---|
| `ydsz.notify.sms.provider` | `aliyun` | 短信提供商（aliyun / tencent / huawei） |
| `ydsz.notify.sms.endpoint` | - | 服务端点 |
| `ydsz.notify.sms.access-key-id` | - | AccessKeyId |
| `ydsz.notify.sms.access-key-secret` | - | AccessKeySecret |

### 钉钉渠道（`ydsz.notify.dingtalk.*`）

| 配置 | 默认值 | 说明 |
|---|---|---|
| `ydsz.notify.dingtalk.webhook-url` | - | 机器人 Webhook 地址 |
| `ydsz.notify.dingtalk.secret` | - | 签名密钥（HMAC-SHA256） |

### 飞书渠道（`ydsz.notify.feishu.*`）

| 配置 | 默认值 | 说明 |
|---|---|---|
| `ydsz.notify.feishu.webhook-url` | - | 机器人 Webhook 地址 |

### 企微渠道（`ydsz.notify.wecom.*`）

| 配置 | 默认值 | 说明 |
|---|---|---|
| `ydsz.notify.wecom.webhook-url` | - | 企业微信机器人地址 |

### 限流配置（`ydsz.notify.ratelimit.*`）

| 配置 | 默认值 | 说明 |
|---|---|---|
| `ydsz.notify.ratelimit.enabled` | `true` | 是否启用发送限流 |
| `ydsz.notify.ratelimit.default-max-requests` | `100` | 默认上限（每窗口请求数） |
| `ydsz.notify.ratelimit.default-window-seconds` | `60` | 限流窗口（秒） |

### 重试队列配置（`ydsz.notify.retry-queue.*`）

| 配置 | 默认值 | 说明 |
|---|---|---|
| `ydsz.notify.retry-queue.persistent` | `false` | 是否使用 Redis 持久化重试队列 |
| `ydsz.notify.retry-queue.max-retries` | `3` | 最大重试次数 |
| `ydsz.notify.retry-queue.capacity` | `1000` | 队列容量 |
| `ydsz.notify.retry-queue.batch-size` | `10` | 批量重试大小 |

### 定时任务配置（`ydsz.notify.scheduler.*`）

| 配置 | 默认值 | 说明 |
|---|---|---|
| `ydsz.notify.scheduler.retry-queue-fixed-delay-ms` | `5000` | 重试队列消费间隔（毫秒） |
| `ydsz.notify.scheduler.aggregate-flush-fixed-delay-ms` | `30000` | 聚合消息刷新间隔（毫秒） |

## 使用示例

### 1. 发送邮件通知

```java
NotifyReceipt receipt = notifyService.send(
    NotifyRequest.builder()
        .channel(NotifyChannel.EMAIL)
        .to("user@example.com")
        .subject("订单支付成功")
        .templateName("order-paid")
        .variable("orderNo", "ORD20260101001")
        .variable("amount", "99.00")
        .build()
);
```

### 2. 发送钉钉机器人消息

```java
notifyService.send(
    NotifyRequest.builder()
        .channel(NotifyChannel.DINGTALK)
        .title("服务器告警")
        .content("CPU 使用率超过 90%")
        .build()
);
```

### 3. 使用异步发送

```java
@Autowired
private AsyncNotifyService asyncNotifyService;

CompletableFuture<NotifyReceipt> future = asyncNotifyService.sendAsync(
    NotifyRequest.builder()
        .channel(NotifyChannel.SMS)
        .to("13800138000")
        .templateName("verify-code")
        .variable("code", "123456")
        .build()
);
```

### 4. 自定义渠道 Provider

```java
@Component
public class TencentCloudSmsProvider implements SmsProvider {
    @Override
    public String send(String phone, String content) { /* 腾讯云 API 调用 */ }
}
```

## SPI 扩展点

| SPI 接口 | 用途 | 实现方 |
|---|---|---|
| `NotifyChannelStrategy` | 自定义渠道（如语音通知、APP 推送），通过容器自动发现注册 | 业务模块实现 |
| `TemplateEngine` | 自定义模板引擎（如 Thymeleaf / Freemarker），替换默认 SpEL 实现 | 业务模块实现 |
| `SmsProvider` | 自定义短信 Provider（如腾讯云/华为云），替换默认阿里云实现 | 业务模块实现 |
| `EmailProvider` | 自定义邮件 Provider，替换默认 SMTP 实现 | 业务模块实现 |
| `DeadLetterHandler` | 自定义死信处理器（如持久化到数据库），替换默认内存实现 | 业务模块实现 |
| `NotificationAggregator` | 自定义消息聚合器，替换默认 30 秒时间窗实现 | 业务模块实现 |

## 健康检查

| 端点 | 说明 | 触发条件 |
|---|---|---|
| `/actuator/health/notify` | 通知模块健康检查（各渠道策略连通性、重试队列积压、熔断状态、SMTP 探活结果等） | `spring-boot-health` 在 classpath + `NotifyHealthIndicator` Bean 存在 |

`NotifyHealthIndicator` 暴露信息：

| 字段 | 说明 |
|---|---|
| `channels.*` | 各渠道连通性（true/false） |
| `smtpAlive` | SMTP 探活结果（邮件渠道） |
| `retryQueueSize` | 重试队列积压量 |
| `circuitBreakers` | 各渠道路由器状态（CLOSED / OPEN / HALF_OPEN） |
| `status` | 综合健康状态（UP / DEGRADED / DOWN） |

降级判定：

- 单个渠道熔断 OPEN → DEGRADED（其他渠道正常时）
- 所有渠道不可用 → DOWN
- 重试队列积压 > 1000 → DEGRADED
- SMTP 探活失败 → DEGRADED（仅限邮件渠道）

## 自动配置类

| 类 | 说明 |
|---|---|
| `NotifyConfiguration` | 核心自动配置，注册 `JavaMailSender` / 渠道 Sender / 限流器 / 熔断器 / 降级管理器 / 去重器 / 偏好管理器 / 模板引擎 / DKIM / 健康检查 / 重试队列 / 死信处理器 / 异步服务 / 发布器 / 全部横切能力 Bean |
| `NotifyTemplateAutoConfiguration` | 模板引擎独立自动配置 |

## 注意事项

1. **渠道按需开启**：邮件渠道默认关闭（`email.enabled=false`），避免引入邮件依赖后自动建 Bean 失败。仅使用短信/钉钉等渠道时无需配置 SMTP。
2. **SMTP 密码加密**：推荐使用 Jasypt 加密 SMTP 密码（`ENC(xxx)` 格式），`NotifyPasswordResolver` 自动解密。明文密码存在泄露风险。
3. **限流依赖 Redis**：分布式限流委托 `RedisRateLimiter`，Redis 不可用时降级为不限流。生产环境建议启用 `ydsz-redis` 支持。
4. **熔断器状态观察**：通过 `/actuator/health/notify` 或 `NotifyMetrics` 指标监控各渠道路由器状态，OPEN 时排查下游服务。
5. **持久化重试队列**：`retry-queue.persistent=true` 需引入 Redis 依赖，否则退化为内存实现（重启丢数据）。持久化场景务必开启。
6. **DKIM 签名前置条件**：DKIM 签名器需要配置域名、DNS 的 TXT 记录和 RSA 私钥，否则邮件可能被判定为垃圾邮件。
7. **事务安全**：使用 `TransactionalNotifyPublisher` 时，发送操作在事务提交后异步执行，不要在发送回调中依赖未提交的事务数据。
8. **定时任务不可少**：`processRetryQueue`（消费重试队列）和 `flushAggregatedMessages`（刷新聚合消息）两个定时任务使用 `@EnableScheduling`，无需额外配置，间隔可调。
9. **多渠道优先级**：`NotifyServiceImpl` 按渠道注册顺序选择首个可用渠道，配合 `NotifyFallbackManager` 实现渠道间的自动降级。

## 变更记录

- **26.09.01**（2026-09-01）：对标 common-jdbc 标准格式重构 README，补全全部章节。
- **26.09.01**（2026-08-20）：新增事务安全发布（`TransactionalNotifyPublisher`）、时间窗聚合（`TimeWindowAggregator`）、消息去重（`NotifyDedupService`）、通知审计（`NotifyAuditService`）、国际化（`NotifyI18nService`/`NotifyI18nResolver`）；扩展渠道覆盖度至 7 大渠道（新增 Webhook/Insite）。
- **26.09.01**（2026-08-05）：拆分为 `core` / `channel` / `template` / `security` / `config` / `aggregate` / `dedup` / `preference` / `i18n` / `metrics` / `fallback` / `tracking` / `audit` / `helper` / `provider` 子模块，架构优化，取消单一扁平包结构。
- **26.09.01**（2026-08-02）：初始版本，提供 `NotifyServiceImpl` + `EmailNotifySender` + `SmsNotifySender` + `SendChain` + `DeadLetterHandler`。
