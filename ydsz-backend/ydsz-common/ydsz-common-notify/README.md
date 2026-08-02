# ydsz-common-notify

YDSZ 统一通知服务 — 5 种通知渠道（邮件 / 短信 / 企微 / 钉钉 / 飞书）、SpEL 模板引擎、Provider 抽象、事务性发布、重试队列、死信处理、熔断、去重、滑动窗口限流、聚合（时间窗口）、DKIM 签名、邮件追踪、退订与偏好、国际化、健康检查。

## 模块定位

| 属性 | 值 |
|---|---|
| **层级** | L5 业务服务层 |
| **类型** | 公共依赖库（不独立部署） |
| **源文件数** | 56 |

## 核心能力

### 通知渠道

| 类 | 说明 |
|---|---|
| `NotifyChannelStrategy` | 渠道策略接口 |
| `EmailNotifySender` | 邮件发送器（SMTP / DKIM 签名） |
| `SmsNotifySender` | 短信发送器 |
| `DingTalkNotifySender` | 钉钉消息发送器 |
| `FeishuNotifySender` | 飞书消息发送器 |
| `WeComNotifySender` | 企业微信消息发送器 |
| `NotifyChannel` | 渠道枚举 |
| `EmailMessage` / `CardMessage` | 邮件 / 卡片消息载体 |

### Provider 抽象

| 类 | 说明 |
|---|---|
| `EmailProvider` | 邮件 Provider 接口（解耦 SMTP 客户端实现） |
| `SmsProvider` | 短信 Provider 接口（解耦厂商 SDK） |
| `AliyunSmsProvider` | 阿里云短信实现 |

### 通知服务

| 类 | 说明 |
|---|---|
| `NotifyService` / `NotifyServiceImpl` | 通知服务接口与实现 |
| `AsyncNotifyService` | 异步通知服务 |
| `TransactionalNotifyPublisher` | 事务性发布器（事务提交后才投递，避免脏发） |
| `NotifyRequest` | 通知请求载体 |
| `NotifySendResult` / `DefaultNotifyResult` | 通知结果模型（发送成功标志、消息 ID、错误信息、渠道、时间戳） |
| `NotifyHelper` | 通知助手工具类（封装常用通知发送、结果构建、异常包装等便捷方法） |
| `NotifyType` / `NotifyPriority` | 通知类型 / 优先级枚举 |
| `NotifyException` | 通知模块异常 |

### 熔断与死信

| 类 | 说明 |
|---|---|
| `NotifyCircuitBreaker` | 渠道级熔断器 |
| `NotifyCircuitBreakerRegistry` | 熔断器注册表 |
| `DeadLetterHandler` | 死信处理接口 |
| `InMemoryDeadLetterHandler` | 内存死信处理器（默认） |

### 模板引擎

| 类 | 说明 |
|---|---|
| `TemplateEngine` | 模板引擎接口 |
| `SpelTemplateEngine` | SpEL 模板引擎（`#{variable}` 替换 + 嵌套变量） |
| `TemplateVariableValidator` | 模板变量校验器（防注入 / 必填校验） |
| `NotifyTemplate` / `NotifyTemplateProperties` | 模板定义 |
| `HtmlTemplateRegistry` | HTML 模板注册 |
| `NotifyTemplateAutoConfiguration` | 模板自动配置 |

### 重试与去重

| 类 | 说明 |
|---|---|
| `NotifyRetryQueue` | 重试队列接口 |
| `RedisNotifyRetryQueue` | Redis 重试队列（ZSET + 定时轮询） |
| `PersistentNotifyRetryQueue` | 持久化重试队列 |
| `NotifyDedupService` | 通知去重服务 |

### 限流与聚合

| 类 | 说明 |
|---|---|
| `SlidingWindowRateLimiter` | 滑动窗口限流器 |
| `NotifyRateLimiterManager` | 通知限流管理器 |
| `NotificationAggregator` | 通知聚合器接口（防止刷屏） |
| `TimeWindowAggregator` | 时间窗口聚合实现 |

### 邮件安全

| 类 | 说明 |
|---|---|
| `DkimSigner` | DKIM 签名器 |
| `EmailContentSanitizer` | 邮件内容消毒（HTML 清洗，防 XSS） |
| `NotifyPasswordResolver` | 密码解析器（加密存储 → 解密使用） |
| `EmailSmtpHealthChecker` | SMTP 健康检查 |

### 用户偏好与国际化

| 类 | 说明 |
|---|---|
| `NotifyPreferenceManager` / `NotifyPreference` | 用户通知偏好管理（含退订） |
| `NotifyI18nService` / `NotifyI18nResolver` | 通知国际化服务与解析器 |

### 降级与追踪

| 类 | 说明 |
|---|---|
| `NotifyFallbackManager` | 降级管理器（主渠道失败 → 备用渠道） |
| `EmailTrackingService` | 邮件追踪服务（打开 / 点击追踪） |
| `NotifyTraceContext` | 通知链路上下文（traceId 透传） |

### 事件与指标

| 类 | 说明 |
|---|---|
| `UnifiedAlertEvent` | 统一告警事件（跨渠道聚合告警） |
| `NotifyAuditService` | 通知审计服务（落 `ydsz_operation_log`） |
| `NotifyMetrics` | 通知指标采集 |
| `NotifyHealthIndicator` | 健康检查 |

### 开关注解

| 注解 | 说明 |
|---|---|
| `@EnableYdszNotify` | 通知模块自动装配入口 |

## 配置项

```yaml
ydsz:
  notify:
    channels:
      email:
        enabled: true
        host: smtp.example.com
        port: 465
        username: noreply@example.com
        dkim:
          enabled: true
          domain: example.com
          selector: ydsz
      sms:
        enabled: true
        provider: aliyun
      dingtalk:
        enabled: false
        webhook: ${DINGTALK_WEBHOOK}
      feishu:
        enabled: false
        webhook: ${FEISHU_WEBHOOK}
      wecom:
        enabled: false
        webhook: ${WECOM_WEBHOOK}
    retry:
      max-attempts: 3
      backoff: 30s
    rate-limit:
      default-qps: 10
    dedup:
      enabled: true
      window: 60s
    fallback:
      enabled: true
    aggregator:
      enabled: false
      window: 30s
```

## 自动配置

| 配置类 | 激活条件 |
|---|---|
| `NotifyConfiguration` | 总是激活 |
| `NotifyTemplateAutoConfiguration` | 总是激活 |
| `NotifyProperties` | 总是激活 |

## 依赖

```xml
<dependency>
    <groupId>com.njydsz</groupId>
    <artifactId>ydsz-common-notify</artifactId>
</dependency>
```
