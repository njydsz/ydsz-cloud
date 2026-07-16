# ydsz-pmis-common-netty

PMIS Netty 网络通信框架 — TCP Server/Client 抽象、断线重连、心跳空闲检测、SSL/TLS、LengthField 编解码、EventLoop 池管理、Channel 组管理、Epoll/KQueue 原生传输、Micrometer 指标监控、健康检查。

## 模块定位

| 属性 | 值 |
|---|---|
| **层级** | L5 业务服务层 |
| **类型** | 公共依赖库（不独立部署） |
| **源文件数** | 18 |

## 核心能力

### Server / Client 抽象

| 类 | 说明 |
|---|---|
| `AbstractNettyServer` | TCP Server 抽象基类（Bootstrap + Pipeline 配置 + 生命周期管理 + SslContext 缓存 + 优雅关闭） |
| `NettyServerLifecycle` | Server 生命周期管理（启动 / 停止 / fail-fast / 优雅关闭） |
| `AbstractNettyClient` | TCP Client 抽象基类（CAS 原子连接 + 同步发送） |
| `ReconnectHandler` | 断线重连处理器（指数退避重连 + volatile ctx） |

### 编解码

| 类 | 说明 |
|---|---|
| `LengthFieldFrameDecoder` | Length Field 拆包器（解决粘包/半包问题） |
| `LengthFieldCodec` | Length Field 编解码组合（Decoder + Prepender 一站式） |
| `MessageEncoder<T>` | 消息编码器接口 |
| `MessageDecoder<T>` | 消息解码器接口 |
| `JsonMessageCodec<T>` | JSON 消息编解码器（基于 YdszJson） |

### 心跳与空闲

| 类 | 说明 |
|---|---|
| `IdleStateHandlerFactory` | 空闲状态处理器工厂（读 / 写 / 读写空闲检测） |
| `ChannelGroupManager` | Channel 组管理（广播 / 分组推送 / 空分组自动清理） |

### SSL/TLS

| 类 | 说明 |
|---|---|
| `SslContextFactory` | SSL Context 工厂（证书加载 + 双向认证） |
| `NettySslException` | SSL 异常（区分 server/client 上下文） |

### 线程池与传输

| 类 | 说明 |
|---|---|
| `NettyEventLoopPool` | EventLoop 线程池管理（实例化 + 共享/隔离模式 + 引用计数 + 优雅关闭 await） |
| `NativeTransportDetector` | 原生传输检测器（Epoll/KQueue 自动检测 + NIO 降级） |

### 可观测性

| 类 | 说明 |
|---|---|
| `NettyChannelMetrics` | Channel 指标采集（连接数 / 读写字节 / 消息数 / 重连数 — 9 项指标） |
| `TrafficMonitoringHandler` | 流量监控 Handler（自动统计读写字节和消息数） |
| `ConnectionEventHandler` | 连接事件监控 Handler（自动追踪连接/断开） |
| `NettyHealthIndicator` | Spring Boot Actuator 健康检查 |

### 配置

| 类 | 说明 |
|---|---|
| `NettyProperties` | Netty 配置属性（JSR-303 校验） |
| `NettyAutoConfiguration` | 自动配置（BeanPostProcessor 自动注入 metrics + eventLoopPool） |

## 配置项

```yaml
pmis:
  netty:
    boss-threads: 1                 # Boss 线程数
    worker-threads: 0               # Worker 线程数（0 = CPU 核数 × 2）
    so-backlog: 128                 # 连接队列
    so-keep-alive: true             # TCP KeepAlive
    tcp-no-delay: true              # TCP_NODELAY
    connect-timeout-millis: 5000    # 连接超时
    shared-event-loop: true         # 共享 EventLoopGroup
    shutdown-quiet-period-seconds: 2  # 优雅关闭静默期
    shutdown-timeout-seconds: 15    # 优雅关闭超时
    fail-fast: true                 # Server 启动失败终止应用
    native-transport: auto          # 原生传输（auto/enabled/disabled）
    idle:
      reader-idle-seconds: 60       # 读空闲超时
      writer-idle-seconds: 30       # 写空闲超时
      all-idle-seconds: 0           # 读写空闲超时（0 = 不检测）
    reconnect:
      enabled: true                 # 断线重连
      initial-delay-ms: 1000        # 初始重连延迟
      max-delay-ms: 60000           # 最大重连延迟
      max-retries: -1               # 最大重试次数（-1 = 无限）
    ssl:
      enabled: false                # SSL 开关
      key-store: classpath:keystore.p12
      key-store-password: changeit
      key-store-type: PKCS12
      trust-store: ""               # 双向认证信任库
      trust-store-password: ""
      need-client-auth: false       # 双向认证
    traffic-shaping:
      enabled: false                # 流量整形
      write-limit: 0                # 写限速（bytes/s）
      read-limit: 0                 # 读限速（bytes/s）
      check-interval-ms: 1000       # 检查间隔
      global: false                 # 全局流量整形
```

## 自动配置

| 配置类 | 激活条件 |
|---|---|
| `NettyAutoConfiguration` | Netty 可用时激活 |

## 依赖

```xml
<dependency>
    <groupId>com.njydsz.pmis</groupId>
    <artifactId>ydsz-pmis-common-netty</artifactId>
</dependency>
```
