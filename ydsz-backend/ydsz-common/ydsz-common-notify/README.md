# ydsz-common-notify

PMIS 统一通知服务 — 5 种通知渠道（邮件 / 短信 / 企微 / 钉钉 / 飞书）、SpEL 模板引擎、重试队列、去重、滑动窗口限流、DKIM 签名、邮件追踪、健康检查。

## 模块定位

| 属性 | 值 |
|---|---|
| **层级** | L5 业务服务层 |
| **类型** | 公共依赖库（不独立部署） |
| **源文件数** | 44 |

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

### 通知服务

| 类 | 说明 |
|---|---|
| `NotifyService` / `NotifyServiceImpl` | 通知服务接口与实现 |
| `AsyncNotifyService` | 异步通知服务 |
| `NotifySendResult` / `DefaultNotifyResult` | 发送结果 |
| `NotifyType` | 通知类型枚举 |

### 模板引擎

| 类 | 说明 |
|---|---|
| `TemplateEngine` | 模板引擎接口 |
| `SpelTemplateEngine` | SpEL 模板引擎（`#{variable}` 替换） |
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

### 限流

| 类 | 说明 |
|---|---|
| `SlidingWindowRateLimiter` | 滑动窗口限流器 |
| `NotifyRateLimiterManager` | 通知限流管理器 |

### 邮件安全

| 类 | 说明 |
|---|---|
| `DkimSigner` | DKIM 签名器 |
| `EmailContentSanitizer` | 邮件内容消毒（HTML 清洗） |
| `NotifyPasswordResolver` | 密码解析器（加密存储 → 解密使用） |
| `EmailSmtpHealthChecker` | SMTP 健康检查 |

### 用户偏好与国际化

| 类 | 说明 |
|---|---|
| `NotifyPreferenceManager` / `NotifyPreference` | 用户通知偏好管理 |
| `NotifyI18nService` | 通知国际化服务 |

### 降级与追踪

| 类 | 说明 |
|---|---|
| `NotifyFallbackManager` | 降级管理器（主渠道失败 → 备用渠道） |
| `EmailTrackingService` | 邮件追踪服务（打开 / 点击追踪） |
| `EmailQueueService` | 邮件队列服务 |

### 事件与指标

| 类 | 说明 |
|---|---|
| `UnifiedAlertEvent` | 统一告警事件 |
| `NotifyMetrics` | 通知指标采集 |
| `NotifyHealthIndicator` | 健康检查 |

## 配置项

```yaml
pmis:
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
          selector: pmis
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
```

## 自动配置

| 配置类 | 激活条件 |
|---|---|
| `NotifyConfiguration` | 总是激活 |
| `NotifyTemplateAutoConfiguration` | 总是激活 |

## 依赖

```xml
<dependency>
    <groupId>com.njydsz</groupId>
    <artifactId>ydsz-common-notify</artifactId>
</dependency>
```
