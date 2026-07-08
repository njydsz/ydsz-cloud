# ydsz-pmis-message

> 消息通知引擎（自研大厂级统一通知中心）

## 模块定位

| 属性 | 值 |
|---|---|
| **类型** | 部署单元（独立启动） |
| **端口** | **9004**（按构建顺序 5/8） |
| **服务名** | `ydsz-pmis-message` |
| **构建顺序** | 5/8 |
| **数据库** | PostgreSQL |
| **依赖** | Nacos、PostgreSQL、Redis、RocketMQ、Mail（SMTP） |

## 核心职责

本模块是 PMIS 的**统一通知中心**，从 `ydsz-pmis-system` 拆分出来作为独立大厂级通知引擎。

### 1. 8 大渠道

| 渠道 | 协议 | 默认 Provider |
|---|---|---|
| **SMS** 短信 | 阿里云 / 腾讯云 / 华为云 | `mock`（开发） / `aliyun`（生产） |
| **EMAIL** 邮件 | SMTP（SSL/STARTTLS） | 内置 |
| **PUSH** 推送 | 个推 Getui / 极光 / 友盟 | `mock` / `getui` |
| **IN_APP** 站内 | WebSocket | 内置 |
| **WEBHOOK** 通用 | HTTP/HTTPS | 内置 |
| **DINGTALK** 钉钉 | 群机器人 + 加签 | 内置 |
| **WECOM** 企业微信 | 群机器人 | 内置 |
| **FEISHU** 飞书 | 群机器人 + 加签 | 内置 |

### 2. 核心能力

| 能力 | 实现 |
|---|---|
| 模板管理 | i18n / 版本 / 审核 / 场景 / `${var}` 嵌套变量 |
| 站内通知 | 优先级 / 聚合 / 撤回 / 跳转 |
| 用户偏好 | 免打扰 / 频率上限 / 聚合 / 语言 |
| 订阅管理 | 主题级订阅 / 退订 |
| 消息路由 | 条件路由 / 通道降级 |
| 限流 | Redisson 令牌桶 + Resilience4j |
| 灰度 | 按用户标签 / 比例灰度 |
| 异步 | RocketMQ 生产/消费/死信 + Redis SET NX EX 幂等 |
| 回执 | 送达 / 已读 / 点击 / 失败 / **超时**（5min 主动拉取 + 30min 超时补偿） |
| 监控 | 渠道维度实时统计 + 失败率告警 |

### 3. 关键 Controller

| 路径前缀 | 作用 |
|---|---|
| `/message/send` | 发送消息（同步 / 异步） |
| `/message/template` | 模板管理 |
| `/message/preference` | 用户偏好 |
| `/message/subscription` | 订阅管理 |
| `/message/notification/inbox` | 站内通知收件箱 |
| `/message/canary` | 灰度发布 |
| `/message/stats` | 发送统计 |
| `/message/dead-letter` | 死信队列 |
| `/notification/inbox` | 前端通知中心（V2 路由） |

## 启动顺序

依赖 `common` + `nacos` + `rocketmq`，**应在 `userinfo` / `system` / `project` 之后**启动（业务服务通过 Feign 调用本服务）。

## 目录结构

```
ydsz-pmis-message/
├── pom.xml
└── src/main/
    ├── java/com/njydsz/pmis/message/
    │   ├── MessageApplication.java
    │   ├── controller/        # 模板 / 发送 / 偏好 / 订阅 / 收件箱
    │   ├── service/
    │   │   ├── send/          # 8 大渠道实现
    │   │   ├── template/      # 模板引擎
    │   │   ├── preference/    # 偏好
    │   │   ├── receipt/       # 回执拉取（ReceiptPuller）
    │   │   ├── retry/         # 重试调度
    │   │   ├── aggregate/     # 聚合
    │   │   └── canary/        # 灰度
    │   ├── consumer/          # RocketMQ 消费者
    │   ├── producer/          # RocketMQ 生产者
    │   ├── mapper/
    │   ├── entity/
    │   ├── enums/             # 渠道 / 优先级 / 回执状态
    │   └── config/
    ├── resources/
    │   ├── bootstrap.yml
    │   ├── application.yml
    │   ├── mapper/            # MsgLogMapper / MsgCanaryMapper
    │   └── nacos-config/
    │       ├── ydsz-pmis-message-dev.yaml
    │       ├── ydsz-pmis-message-sit.yaml
    │       └── ydsz-pmis-message-uat.yaml
    └── test/
```

## 配置文件

**RocketMQ**（必需）：

| 变量 | 默认值 | 说明 |
|---|---|---|
| `ROCKETMQ_NAME_SERVER` | `127.0.0.1:9876` | NameServer |
| `ROCKETMQ_PRODUCER_GROUP` | `pmis-message-producer-group` | 生产者组 |
| `ROCKETMQ_CONSUMER_ENABLED` | `true` | 是否启动消费者 |

**邮件 SMTP**（EMAIL 渠道必需）：

| 变量 | 默认值 | 说明 |
|---|---|---|
| `MAIL_HOST` | `smtp.example.com` | SMTP 服务器 |
| `MAIL_PORT` | `465` | SSL 端口 |
| `MAIL_USER` | `no-reply@example.com` | 发件人 |
| `MAIL_PASSWORD` | （必填） | SMTP 密码 / 授权码 |

**短信 / 推送**（生产环境必需）：

| 变量 | 说明 |
|---|---|
| `PMIS_SMS_PROVIDER` | `mock`（默认） / `aliyun` |
| `PMIS_SMS_ALIYUN_AK` / `PMIS_SMS_ALIYUN_SK` / `PMIS_SMS_ALIYUN_SIGN` | 阿里云短信 AK / SK / 签名 |
| `PMIS_PUSH_PROVIDER` | `mock`（默认） / `getui` |
| `PMIS_PUSH_GETUI_APPID` / `PMIS_PUSH_GETUI_APPKEY` / `PMIS_PUSH_GETUI_MASTER` | 个推配置 |

## 启动

```bash
# 1. 启动 RocketMQ
docker run -d --name pmis-rocketmq \
  -p 9876:9876 -p 8080:8080 \
  -e "JAVA_OPT_EXT=-Xms512m -Xmx512m" \
  apache/rocketmq:5.1.0 sh mqbroker -n namesrv:9876

# 2. 启动 message
cd ydsz-pmis-backend
mvn -pl ydsz-pmis-message spring-boot:run
```

## 测试

```bash
mvn -pl ydsz-pmis-message -am test
```

包含的关键测试类：
- `ChannelRouterTest` 渠道路由
- `MessageServiceTest` 发送逻辑
- `RetryScannerTest` 重试扫描
- `ReceiptPullerTest` 回执拉取（10 个用例）
- `TemplateEngineTest` 模板引擎

## Feign 接口

### 被调用（其他服务发消息）

- `MessageFeignClient`（位于 common）→ `/message/send`
- `NotificationPushClient`（位于 common）→ `/notification/push`

## 常见问题

### Q1：RocketMQ 发送失败

检查 NameServer 是否启动（`rocketmq-console` 端口 8080），`ROCKETMQ_NAME_SERVER` 是否可达。

### Q2：模板变量未替换

模板使用 `${var}` 语法嵌套。检查：
1. 模板是否审核通过
2. 变量名是否一致
3. 是否传递了 `Map<String, Object> variables`

### Q3：回执一直 TIMEOUT

渠道不支持主动拉取时，回执状态保持 `NONE`。`ReceiptPuller` 调度器默认 5min 主动拉取，30min 超时补偿。
配置项：`pmis.message.receipt-pull-delay-minutes` / `receipt-timeout-minutes`。

---

> 本模块是**异步消息核心**，所有发送都走 RocketMQ + 死信队列 + 重试扫描。
> 严禁在发送链路做耗时操作（DB 大查询、远程调用等），必须异步化。
