# ydsz-common-notify

> 统一消息通知公共模块（L5 业务服务层）

提供邮件、短信、企业微信、钉钉、飞书、站内信 6 种通知渠道的统一发送能力，集成 SpEL 模板引擎、Provider 抽象、事务性发布、重试队列、死信处理、渠道熔断、去重、滑动窗口限流、消息聚合、DKIM 签名、邮件追踪、退订与偏好、国际化、健康检查等横切能力，是所有业务模块消息发送的统一入口。

## 模块定位

| 属性 | 值 |
|---|---|
| **层级** | L5 业务服务层 |
| **类型** | 公共依赖库（不独立部署） |
| **作用** | 提供多渠道消息发送、模板渲染、限流熔断、重试死信、降级聚合、安全签名、偏好国际化等能力 |
| **依赖** | common-core、common-util、common-json、common-exception、common-domain；可选依赖 common-redis、spring-boot-starter-mail、micrometer-core、jasypt、httpclient5、spring-boot-health |
| **版本** | 1.0.0 |

## 核心能力

### 1. 通知渠道策略

| 类 | 说明 |
|---|---|
| `NotifyChannelStrategy` | 渠道策略接口，定义 `getChannel`/`send`/`sendTemplate`/`batchSend`/`isEnabled`/`setTemplateEngine`/`sendCard` 方法；新渠道实现该接口注册为 Bean 即可自动接入 |
| `NotifyChannel` | 渠道枚举（EMAIL/SMS/WECOM/DINGTALK/FEISHU/INSITE），含 `code` 与中文名 |
| `EmailNotifySender` | 邮件发送器（SMTP / DKIM 签名 / HTML 内容消毒） |
| `SmsNotifySender` | 短信发送器（基于 `SmsProvider` 抽象） |
| `DingTalkNotifySender` | 钉钉消息发送器（支持 webhook 签名） |
| `FeishuNotifySender` | 飞书消息发送器（支持加密 webhook） |
| `WeComNotifySender` | 企业微信消息发送器 |
| `EmailMessage` / `CardMessage` | 邮件 / 卡片消息载体 |

### 2. Provider 抽象

| 类 | 说明 |
|---|---|
| `EmailProvider` | 邮件 Provider 接口（解耦 SMTP 客户端实现），支持 `getName`/`send`/`isAvailable`/`getPriority`，便于多 SMTP 提供商切换与灰度 |
| `SmsProvider` | 短信 Provider 接口（解耦厂商 SDK），含发送结果与余额查询；内置 `SmsSendResult`/`SmsBalance` 内部类 |
| `AliyunSmsProvider` | 阿里云短信实现（`ydsz.notify.sms.provider=aliyun` 时激活） |

### 3. 通知服务

| 类 | 说明 |
|---|---|
| `NotifyService` | 通知服务接口，提供 `send`/`send(NotifyRequest)`/`sendTemplate`/`batchSend`/`parallelBatchSend` 五种发送模式 |
| `NotifyServiceImpl` | 通知服务实现，整合限流、熔断、降级、去重、指标、审计、偏好、聚合等全部横切关注点 |
| `AsyncNotifyService` | 异步通知服务（基于虚拟线程池并行投递） |
| `TransactionalNotifyPublisher` | 事务性发布器（事务提交后才投递，避免脏发） |
| `NotifyRequest` | 通知请求载体（Builder 模式，封装渠道、接收者、模板、优先级、用户ID、traceId） |
| `NotifySendResult` / `DefaultNotifyResult` | 通知结果模型（成功标志、消息 ID、错误信息、渠道、时间戳） |
| `NotifyHelper` | 通知助手工具类（封装 `sendInApp`/`sendEmail`/`sendTemplate`/`sendSystemAlert` 等便捷方法，自动识别邮箱地址路由至邮件渠道） |
| `NotifyType` / `NotifyPriority` | 通知类型 / 优先级枚举 |
| `NotifyException` | 通知模块异常 |

### 4. 熔断与死信

| 类 | 说明 |
|---|---|
| `NotifyCircuitBreaker` | 渠道级熔断器，基于 `AtomicReference` + CAS 实现线程安全状态机 |
| `NotifyCircuitBreakerRegistry` | 熔断器注册中心，按渠道维护独立熔断器实例，默认 `failureThreshold=5`、`recoveryTimeoutMs=60000` |
| `DeadLetterHandler` | 死信处理接口 |
| `InMemoryDeadLetterHandler` | 内存死信处理器（默认实现） |

熔断状态机：

| 状态 | 行为 |
|---|---|
| `CLOSED` | 正常状态，请求通过；记录连续失败计数，达到阈值后切换到 OPEN |
| `OPEN` | 熔断状态，所有请求被快速拒绝；超过 `recoveryTimeoutMs` 后切换到 HALF_OPEN |
| `HALF_OPEN` | 半开状态，仅允许单个探测请求通过（CAS 保证）；探测成功切换 CLOSED，失败切换回 OPEN |

### 5. 模板引擎

| 类 | 说明 |
|---|---|
| `TemplateEngine` | 模板引擎接口，定义 `render`/`register`/`hasTemplate`/`getTemplate`/`registerAll`/`getAllTemplates` |
| `SpelTemplateEngine` | SpEL 模板引擎实现（`#{variable}` 替换 + 嵌套变量支持） |
| `TemplateVariableValidator` | 模板变量校验器（防注入 + 必填校验） |
| `NotifyTemplate` / `NotifyTemplateProperties` | 模板定义与配置属性 |
| `HtmlTemplateRegistry` | HTML 模板注册中心 |
| `NotifyTemplateAutoConfiguration` | 模板自动配置（`ydsz.notify.template.enabled=true`，默认启用） |

### 6. 重试与去重

| 类 | 说明 |
|---|---|
| `NotifyRetryQueue` | 重试队列接口（`retryBatch`/`getQueueSize`/`getPermanentFailCount`/`getDroppedCount`） |
| `RedisNotifyRetryQueue` | Redis ZSET 重试队列（多实例部署） |
| `PersistentNotifyRetryQueue` | 持久化重试队列（`persistent=true` 时使用 Redis；`persistent=false` 时退化为内存） |
| `NotifyDedupService` | 通知去重服务（基于 Redis，相同内容在时间窗口内只发送一次） |

定时消费：`NotifyConfiguration#processRetryQueue` 每 5 秒批量消费重试队列；`flushAggregatedMessages` 每 30 秒刷新聚合消息缓冲区。

### 7. 限流与聚合

| 类 | 说明 |
|---|---|
| `NotifyRateLimiterManager` | 通知限流管理器，为每个渠道维护独立限流器；默认 100 次/分钟，支持渠道级自定义；使用 `ReentrantLock` 替代 `synchronized` 避免虚拟线程 pinning |
| `SlidingWindowRateLimiter` | 滑动窗口限流器（10 子窗口，单实例内存实现） |
| `NotificationAggregator` | 通知聚合器接口（防止刷屏） |
| `TimeWindowAggregator` | 时间窗口聚合实现（默认 30 秒窗口 / 100 条上限） |

> 多实例部署应使用 `common-redis` 的 `RedisRateLimiter#tryAcquireSlidingWindow()` 实现分布式滑动窗口限流。

### 8. 邮件安全

| 类 | 说明 |
|---|---|
| `DkimSigner` | DKIM 签名器（Base64 RSA 私钥 + 域名 + selector） |
| `EmailContentSanitizer` | 邮件内容消毒（OWASP HTML Sanitizer，防 XSS） |
| `NotifyPasswordResolver` | 密码解析器（`ENC(xxx)` 格式通过 Jasypt 解密） |
| `EmailSmtpHealthChecker` | SMTP 健康探活 |

### 9. 用户偏好与国际化

| 类 | 说明 |
|---|---|
| `NotifyPreferenceManager` / `NotifyPreference` | 用户通知偏好管理（含退订、免打扰时段、渠道开关、类型开关） |
| `NotifyI18nService` / `NotifyI18nResolver` | 通知国际化服务与解析器 |

### 10. 降级与追踪

| 类 | 说明 |
|---|---|
| `NotifyFallbackManager` | 降级管理器（主渠道失败 → 按降级链尝试备用渠道，最多 3 次降级） |
| `EmailTrackingService` | 邮件追踪服务（已读像素 / 点击追踪，基于 Redis） |
| `NotifyTraceContext` | 通知链路上下文（`traceId` 透传，写入 MDC） |

### 11. 事件与指标

| 类 | 说明 |
|---|---|
| `UnifiedAlertEvent` | 统一告警事件（各模块发布后由 NotifyHelper 消费发送） |
| `NotifyAuditService` | 通知审计服务（专用 logger `NOTIFY_AUDIT`，接收者脱敏，落结构化审计日志） |
| `NotifyMetrics` | 通知指标采集（Micrometer，依赖缺失时降级为 no-op） |

### 12. 签名工具（第三方 IM 通信）

| 类 | 说明 |
|---|---|
| `DingTalkSignatureUtil` | 钉钉 webhook 签名工具 |
| `FeishuSignatureUtil` | 飞书 webhook 签名与加密工具 |
| `WeComSignatureUtil` | 企业微信签名工具 |

### 13. 开关注解

| 注解 | 说明 |
|---|---|
| `@EnableYdszNotify` | 通知模块自动装配入口，`@Import(NotifyConfiguration.class)` |

## 接入方式

### 1. POM 引入依赖

```xml
<dependency>
    <groupId>com.njydsz</groupId>
    <artifactId>ydsz-common-notify</artifactId>
</dependency>
```

### 2. 启用通知模块

在启动类或配置类上标注 `@EnableYdszNotify`：

```java
import com.njydsz.common.notify.annotation.EnableYdszNotify;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@EnableYdszNotify
public class MyApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyApplication.class, args);
    }
}
```

> `NotifyConfiguration` 默认 `matchIfMissing = true`，未显式标注 `@EnableYdszNotify` 且类路径存在 `httpclient5` 时也会自动装配。建议显式标注以提升可读性。

### 3. 注入 NotifyService 或 NotifyHelper

```java
import com.njydsz.common.notify.helper.NotifyHelper;
import com.njydsz.common.notify.core.NotifyService;
import jakarta.annotation.Resource;

@Service
public class OrderService {

    @Resource
    private NotifyService notifyService;

    @Resource
    private NotifyHelper notifyHelper;

    public void onOrderPaid(String userEmail, String userId) {
        // 直接使用 NotifyService
        notifyService.send(NotifyChannel.EMAIL, userEmail, "订单支付成功", "您的订单已支付");

        // 或使用 NotifyHelper 便捷方法
        notifyHelper.sendSystemAlert("订单异常", "订单超时未支付", userId, userEmail);
    }
}
```

## 配置项

### 简化配置（推荐）

对于仅需邮件和基础 IM 通知的场景，可使用简化配置前缀 `ydsz.notify-lite`：

| 配置 | 默认值 | 说明 |
|---|---|---|
| `ydsz.notify-lite.enabled` | true | 是否启用通知模块 |
| `ydsz.notify-lite.email.enabled` | true | 是否启用邮件渠道 |
| `ydsz.notify-lite.email.smtp-host` | - | SMTP 主机地址 |
| `ydsz.notify-lite.email.smtp-port` | 465 | SMTP 端口 |
| `ydsz.notify-lite.email.from-mail` | - | 发件人邮箱 |
| `ydsz.notify-lite.email.from-name` | - | 发件人显示名称 |
| `ydsz.notify-lite.email.password` | - | 邮箱密码/授权码 |
| `ydsz.notify-lite.wecom.enabled` | false | 是否启用企业微信 |
| `ydsz.notify-lite.dingtalk.enabled` | false | 是否启用钉钉 |
| `ydsz.notify-lite.feishu.enabled` | false | 是否启用飞书 |

### 完整配置

高级功能（DKIM 签名、邮件追踪、渠道降级等）请使用完整配置前缀 `ydsz.notify`：

| 配置 | 默认值 | 说明 |
|---|---|---|
| `ydsz.notify.enabled` | true | 是否启用通知模块 |
| `ydsz.notify.template.enabled` | true | 是否启用模板引擎 |
| `ydsz.notify.template.base-path` | `classpath:notify-templates/` | 模板文件基础路径 |
| `ydsz.notify.template.cache-enabled` | true | 是否启用模板缓存 |
| `ydsz.notify.email.enabled` | true | 是否启用邮件渠道 |
| `ydsz.notify.email.smtp-host` | - | SMTP 主机地址 |
| `ydsz.notify.email.smtp-port` | 465 | SMTP 端口 |
| `ydsz.notify.email.from-mail` | - | 发件人邮箱 |
| `ydsz.notify.email.from-name` | - | 发件人显示名称 |
| `ydsz.notify.email.password` | - | 邮箱密码/授权码（支持 `ENC(xxx)` Jasypt 加密） |
| `ydsz.notify.email.auth` | true | 是否需要认证 |
| `ydsz.notify.email.starttls` | false | 是否启用 STARTTLS |
| `ydsz.notify.email.html-mode` | true | 是否以 HTML 模式发送 |
| `ydsz.notify.email.encoding` | UTF-8 | 编码格式 |
| `ydsz.notify.email.connection-timeout` | 10000 | 连接超时（ms） |
| `ydsz.notify.email.timeout` | 10000 | 读取超时（ms） |
| `ydsz.notify.email.write-timeout` | 10000 | 写入超时（ms） |
| `ydsz.notify.email.max-attachment-size-mb` | 20 | 最大附件总大小（MB） |
| `ydsz.notify.email.ssl.enabled` | true | 是否启用 SSL |
| `ydsz.notify.email.ssl.protocols` | TLSv1.2 | SSL 协议版本 |
| `ydsz.notify.email.security.password-encrypted` | false | 密码是否已加密 |
| `ydsz.notify.email.security.sanitize-html` | true | 是否启用 HTML 内容 XSS 过滤 |
| `ydsz.notify.email.tracking.enabled` | false | 是否启用已读追踪像素 |
| `ydsz.notify.email.tracking.pixel-base-url` | - | 追踪像素 Base URL |
| `ydsz.notify.email.dkim.enabled` | false | 是否启用 DKIM 签名 |
| `ydsz.notify.email.dkim.domain` | - | DKIM 域名 |
| `ydsz.notify.email.dkim.selector` | default | DKIM 选择器 |
| `ydsz.notify.email.dkim.private-key` | - | DKIM Base64 RSA 私钥 |
| `ydsz.notify.sms.enabled` | false | 是否启用短信渠道 |
| `ydsz.notify.sms.provider` | aliyun | 短信服务提供商 |
| `ydsz.notify.sms.endpoint` | - | API 端点地址 |
| `ydsz.notify.sms.access-key-id` | - | AccessKey ID |
| `ydsz.notify.sms.access-key-secret` | - | AccessKey Secret |
| `ydsz.notify.sms.sign-name` | - | 短信签名 |
| `ydsz.notify.sms.template-code` | - | 默认短信模板编码 |
| `ydsz.notify.sms.templates` | - | 模板映射（key=模板编码，value=模板ID） |
| `ydsz.notify.sms.timeout-ms` | 10000 | 发送超时（ms） |
| `ydsz.notify.sms.retry-count` | 2 | 失败重试次数 |
| `ydsz.notify.wecom.enabled` | false | 是否启用企业微信渠道 |
| `ydsz.notify.wecom.corp-id` | - | 企业 ID |
| `ydsz.notify.wecom.corp-secret` | - | 企业密钥 |
| `ydsz.notify.wecom.agent-id` | - | 应用 ID |
| `ydsz.notify.wecom.webhook-key` | - | Webhook 密钥 |
| `ydsz.notify.wecom.token-refresh-interval` | 7200 | Token 刷新间隔（秒） |
| `ydsz.notify.dingtalk.enabled` | false | 是否启用钉钉渠道 |
| `ydsz.notify.dingtalk.app-key` | - | 应用 Key |
| `ydsz.notify.dingtalk.app-secret` | - | 应用密钥 |
| `ydsz.notify.dingtalk.webhook-url` | - | Webhook 地址 |
| `ydsz.notify.dingtalk.webhook-secret` | - | Webhook 签名密钥 |
| `ydsz.notify.feishu.enabled` | false | 是否启用飞书渠道 |
| `ydsz.notify.feishu.app-id` | - | 应用 ID |
| `ydsz.notify.feishu.app-secret` | - | 应用密钥 |
| `ydsz.notify.feishu.webhook-url` | - | Webhook 地址 |
| `ydsz.notify.feishu.encrypt-key` | - | 加密密钥 |
| `ydsz.notify.insite.enabled` | true | 是否启用站内信渠道 |
| `ydsz.notify.insite.storage-type` | redis | 存储类型 |
| `ydsz.notify.insite.max-queue-size` | 10000 | 最大队列大小 |
| `ydsz.notify.insite.expire-minutes` | 1440 | 过期时间（分钟） |
| `ydsz.notify.fallback.enabled` | false | 是否启用渠道降级 |
| `ydsz.notify.fallback.chains` | - | 降级链映射（key=主渠道，value=备用渠道列表） |
| `ydsz.notify.dedup.enabled` | false | 是否启用去重 |
| `ydsz.notify.dedup.window-seconds` | 300 | 去重时间窗口（秒） |
| `ydsz.notify.dedup.redis-key-prefix` | `notify:dedup:` | Redis Key 前缀 |
| `ydsz.notify.retry-queue.capacity` | 10000 | 队列容量 |
| `ydsz.notify.retry-queue.max-retries` | 5 | 最大重试次数（0-10） |
| `ydsz.notify.retry-queue.batch-size` | 100 | 批量处理大小 |
| `ydsz.notify.retry-queue.persistent` | false | 是否使用 Redis 持久化重试队列（开启后服务重启不丢失） |
| `ydsz.notify.retry-queue.redis-key-prefix` | `notify:retry:` | Redis Key 前缀 |
| `ydsz.notify.rate-limit.enabled` | true | 是否启用限流 |
| `ydsz.notify.rate-limit.default-max-requests` | 100 | 默认最大请求数（每个渠道） |
| `ydsz.notify.rate-limit.default-window-seconds` | 60 | 默认时间窗口（秒） |
| `ydsz.notify.rate-limit.channel-limits` | - | 渠道级限流配置（key=渠道枚举，value=渠道限流配置） |

## 使用示例

### 1. 基础发送

```java
import com.njydsz.common.notify.enums.NotifyChannel;
import com.njydsz.common.notify.core.NotifyService;
import jakarta.annotation.Resource;

@Service
public class AlertService {

    @Resource
    private NotifyService notifyService;

    public void alert(String email) {
        // 简单发送
        notifyService.send(NotifyChannel.EMAIL, email, "系统告警", "CPU 使用率超过 90%");
    }
}
```

### 2. 完整 NotifyRequest（支持优先级、模板、用户偏好）

```java
import com.njydsz.common.notify.core.NotifyRequest;
import com.njydsz.common.notify.enums.NotifyChannel;
import com.njydsz.common.notify.enums.NotifyPriority;
import com.njydsz.common.notify.core.NotifyService;
import jakarta.annotation.Resource;

@Service
public class TemplateNotifyService {

    @Resource
    private NotifyService notifyService;

    public void sendVerificationCode(String phone, String code) {
        NotifyRequest request = NotifyRequest.of(NotifyChannel.SMS, phone, "验证码", "")
                .template("VERIFICATION_CODE", Map.of("code", code))
                .priority(NotifyPriority.P0_URGENT)
                .userId("user-123")
                .traceId("trace-abc")
                .build();
        notifyService.send(request);
    }
}
```

### 3. 批量发送（并行虚拟线程）

```java
import java.util.List;
import java.util.concurrent.CompletableFuture;
import com.njydsz.common.notify.enums.NotifyChannel;
import com.njydsz.common.notify.core.NotifyService;
import com.njydsz.common.notify.core.NotifySendResult;
import jakarta.annotation.Resource;

@Service
public class BroadcastService {

    @Resource
    private NotifyService notifyService;

    public CompletableFuture<NotifySendResult> broadcast(List<String> emails) {
        // 并行批量发送，使用共享虚拟线程池 notifyVirtualThreadExecutor
        return notifyService.parallelBatchSend(NotifyChannel.EMAIL, emails,
                "系统维护通知", "将于今晚 22:00-24:00 进行系统维护");
    }
}
```

### 4. NotifyHelper 便捷方法

```java
import com.njydsz.common.notify.helper.NotifyHelper;
import com.njydsz.common.notify.enums.NotifyChannel;
import jakarta.annotation.Resource;

@Service
public class UserAlertService {

    @Resource
    private NotifyHelper notifyHelper;

    public void onTaskFailed(String userId, String userEmail) {
        // 站内信
        notifyHelper.sendInApp(userId, "审批提醒", "您有一条待审批任务");

        // 邮件
        notifyHelper.sendEmail(userEmail, "任务失败", "定时任务执行失败");

        // 系统告警（自动识别邮箱地址路由至邮件渠道）
        notifyHelper.sendSystemAlert("定时任务失败", "Job: data-sync, Error: timeout",
                userId, userEmail);
    }
}
```

### 5. 完整邮件配置（含 DKIM 与追踪）

```yaml
ydsz:
  notify:
    enabled: true
    email:
      enabled: true
      smtp-host: smtp.exmail.qq.com
      smtp-port: 465
      from-mail: noreply@ydsz.com
      from-name: ydsz项目管理平台
      password: ENC(encrypted-password-string)
      auth: true
      html-mode: true
      ssl:
        enabled: true
        protocols: TLSv1.2
      security:
        password-encrypted: true
        sanitize-html: true
      tracking:
        enabled: true
        pixel-base-url: https://ydsz.example.com/api/notify/track/open
      dkim:
        enabled: true
        domain: ydsz.com
        selector: ydsz
        private-key: ${DKIM_PRIVATE_KEY}
```

### 6. 渠道降级链配置

```yaml
ydsz:
  notify:
    fallback:
      enabled: true
      chains:
        EMAIL:
          - SMS
          - INSITE
        SMS:
          - WECOM
        WECOM:
          - DINGTALK
```

### 7. 限流与去重配置

```yaml
ydsz:
  notify:
    rate-limit:
      enabled: true
      default-max-requests: 100
      default-window-seconds: 60
      channel-limits:
        EMAIL:
          max-requests: 200
          window-seconds: 60
        SMS:
          max-requests: 50
          window-seconds: 60
        WECOM:
          max-requests: 100
          window-seconds: 60
    dedup:
      enabled: true
      window-seconds: 300
    retry-queue:
      persistent: true
      max-retries: 5
      capacity: 10000
```

## SPI 扩展点

| SPI 接口 | 用途 | 实现方 |
|---|---|---|
| `NotifyChannelStrategy` | 通知渠道策略接口，新渠道实现后注册为 Bean 即可自动接入 | 框架内置邮件/短信/企微/钉钉/飞书，业务可扩展站内信等 |
| `EmailProvider` | 邮件 Provider 接口，支持多 SMTP 提供商（腾讯企业邮箱、阿里云邮件推送、AWS SES、SendGrid）切换 | 业务模块按需实现 |
| `SmsProvider` | 短信 Provider 接口，支持多厂商 SDK（阿里云、腾讯云、华为云）快速接入 | 框架内置 `AliyunSmsProvider`，业务可扩展 |
| `TemplateEngine` | 模板引擎接口，支持自定义渲染逻辑（如 Freemarker、Thymeleaf、Velocity） | 框架内置 `SpelTemplateEngine`，业务可扩展 |
| `DeadLetterHandler` | 死信处理接口，支持自定义死信存储（如落库、转人工） | 框架内置 `InMemoryDeadLetterHandler`，业务可扩展 |
| `NotificationAggregator` | 通知聚合器接口，支持自定义聚合策略（如按用户聚合、按主题聚合） | 框架内置 `TimeWindowAggregator`，业务可扩展 |

### 自定义渠道示例

```java
import com.njydsz.common.notify.channel.NotifyChannelStrategy;
import com.njydsz.common.notify.core.NotifySendResult;
import com.njydsz.common.notify.enums.NotifyChannel;
import org.springframework.stereotype.Component;

@Component
public class WebhookNotifySender implements NotifyChannelStrategy {

    @Override
    public NotifyChannel getChannel() {
        // 需先在 NotifyChannel 枚举中扩展
        return NotifyChannel.WECOM;
    }

    @Override
    public NotifySendResult send(String receiver, String title, String content) {
        // 自定义发送逻辑
        return NotifySendResult.success("msg-123", getChannel().getName());
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
```

## 健康检查

| 端点 | 说明 | 触发条件 |
|---|---|---|
| `/actuator/health/notify` | 通知模块健康检查 | `spring-boot-health` 在类路径 + `ydsz.notify.enabled=true` |

`NotifyHealthIndicator` 暴露以下信息：

| 详情字段 | 说明 |
|---|---|
| `module` | 固定为 `notify` |
| `configuredChannels` | 已正确配置的渠道数量 |
| `email` / `sms` / `wecom` / `dingtalk` / `feishu` / `insite` | 渠道状态（`ready` / `misconfigured` / `disabled`） |
| `{channel}_enabled` / `{channel}_ready` | 渠道策略实际启用与就绪状态 |
| `circuit_breakers` | 各渠道熔断器状态（CLOSED/OPEN/HALF_OPEN） |
| `retry_queue_size` | 重试队列当前大小 |
| `retry_queue_permanent_failures` | 永久失败计数 |
| `retry_queue_dropped` | 丢弃计数 |

降级判定：所有渠道均未配置时标记为 DOWN（`reason=no notification channel configured`）；检测异常时标记为 DOWN 并记录 error。

## 注意事项

1. **简化配置**：`ydsz.notify-lite` 提供精简配置入口，适用于仅需邮件和基础 IM 通知的场景；高级功能（DKIM、追踪、降级、去重等）请使用 `ydsz.notify` 完整配置。
2. **重试队列持久化**：`ydsz.notify.retry-queue.persistent=true` 时使用 Redis ZSET 持久化，服务重启不丢失待重试消息；`persistent=false` 时退化为内存队列，重启后丢失。多实例部署必须开启持久化。
3. **限流实现**：`NotifyRateLimiterManager` 默认委托 `common-redis` 的 `RedisRateLimiter#tryAcquireSlidingWindow()` 实现分布式滑动窗口限流。单实例部署可直接使用内存限流。
4. **熔断器默认参数**：默认连续失败 5 次触发熔断，熔断持续 60 秒后半开探测。HALF_OPEN 状态下仅允许单个探测请求通过。
5. **降级链最多 3 次**：`NotifyFallbackManager` 最多尝试 3 个备用渠道，防止无限降级；降级渠道不可用或未启用时自动跳过。
6. **密码加密**：邮件密码支持 `ENC(xxx)` 格式通过 Jasypt 解密，需引入 `jasypt-spring-boot-starter` 依赖；未引入时密码以明文存储。
7. **事务性发布**：`TransactionalNotifyPublisher` 在事务提交后才投递通知，避免事务回滚后通知已发送的脏发问题。
8. **Micrometer 可选**：`NotifyMetrics` 在 `micrometer-core` 不存在时降级为 no-op，不影响功能；建议生产环境引入以保证可观测性。
9. **审计日志独立 Logger**：`NotifyAuditService` 使用专用 logger `NOTIFY_AUDIT`，便于在 logback 中单独配置 appender 与保留策略；接收者标识自动脱敏（保留前 3 位）。
10. **共享虚拟线程池**：`NotifyConfiguration` 注册名为 `notifyVirtualThreadExecutor` 的共享虚拟线程池，供 `parallelBatchSend` 与 `AsyncNotifyService` 使用，JDK 21+ 生效。

## 变更记录

- **v1.0.0**（2026-08-02）：补全接入方式、配置项表、使用示例、SPI 扩展点、健康检查、注意事项章节；完善渠道策略、Provider 抽象、熔断状态机、限流聚合、邮件安全等核心能力描述
