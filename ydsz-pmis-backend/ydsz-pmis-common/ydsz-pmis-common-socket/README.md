# ydsz-pmis-common-socket

PMIS WebSocket 实时推送框架 — 集群广播、离线消息存储、在线用户管理、认证拦截、消息限流、实时推送模板、Micrometer 指标。

## 模块定位

| 属性 | 值 |
|---|---|
| **层级** | L5 业务服务层 |
| **类型** | 公共依赖库（不独立部署） |
| **源文件数** | 16 |

## 核心能力

### WebSocket 核心

| 类 | 说明 |
|---|---|
| `WebSocketAutoConfiguration` | WebSocket 自动配置 |
| `WebSocketProperties` | 配置属性 |
| `WebSocketConstants` | 常量定义 |

### 集群支持

| 类 | 说明 |
|---|---|
| `WebSocketClusterAutoConfiguration` | 集群自动配置 |
| `WebSocketClusterPublisher` | 集群消息发布器（Redis Pub-Sub 跨节点广播） |
| `WebSocketClusterSubscriber` | 集群消息订阅器 |
| `WebSocketClusterMessage` | 集群消息实体 |

### 在线用户管理

| 类 | 说明 |
|---|---|
| `OnlineUserService` | 在线用户服务（用户 ↔ Session 映射） |
| `WebSocketSessionEventListener` | Session 事件监听器（连接 / 断开 → 上下线通知） |

### 离线消息

| 类 | 说明 |
|---|---|
| `OfflineMessageStore` | 离线消息存储接口 |
| `RedisOfflineMessageStore` | Redis 离线消息存储（List + 过期） |

### 实时推送

| 类 | 说明 |
|---|---|
| `RealtimePushTemplate` | 实时推送模板接口 |
| `DefaultRealtimePushTemplate` | 默认推送实现（单播 / 广播 / 群组推送） |

### 安全与限流

| 类 | 说明 |
|---|---|
| `WebSocketAuthInterceptor` | 认证拦截器（Handshake 阶段 Token 校验） |
| `WebSocketRateLimiter` | 消息限流器（防消息洪泛） |

### 可观测性

| 类 | 说明 |
|---|---|
| `WebSocketMetrics` | WebSocket 指标（连接数 / 消息数 / 延迟） |

## 配置项

```yaml
pmis:
  socket:
    enabled: true
    path: /ws                       # WebSocket 端点路径
    allowed-origins: ["*"]          # 允许的 Origin
    auth:
      enabled: true                 # 认证开关
      token-param: token            # Token 参数名
    cluster:
      enabled: true                 # 集群广播
      channel: pmis:ws:cluster      # Redis 集群频道
    offline-message:
      enabled: true                 # 离线消息存储
      max-messages: 100             # 最大离线消息数
      expire: 7d                    # 离线消息过期
    rate-limit:
      enabled: true
      max-messages-per-second: 10   # 每秒最大消息数
```

## 自动配置

| 配置类 | 激活条件 |
|---|---|
| `WebSocketAutoConfiguration` | 总是激活 |
| `WebSocketClusterAutoConfiguration` | Redis 可用时激活 |

## 依赖

```xml
<dependency>
    <groupId>com.njydsz.pmis</groupId>
    <artifactId>ydsz-pmis-common-socket</artifactId>
</dependency>
```
