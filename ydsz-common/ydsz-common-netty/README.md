# ydsz-common-netty

> Netty 网络通信框架（L5 业务服务层）

提供 TCP Server/Client 抽象、断线重连、心跳空闲检测、SSL/TLS、LengthField 编解码、EventLoop 池管理、Channel 组管理、Epoll/KQueue 原生传输、连接控制、流量整形、Micrometer 指标监控、Actuator 端点、健康检查能力，是 YDSZ 项目网络通信的统一基座。

## 模块定位

| 属性 | 值 |
|---|---|
| **层级** | L5 业务服务层 |
| **类型** | 公共依赖库（不独立部署） |
| **作用** | 提供 TCP Server/Client 抽象、编解码、SSL/TLS、断线重连、连接控制、流量整形、指标监控等能力 |
| **依赖** | common-core、common-util、common-exception、common-json、netty-all；provided 依赖 spring-boot-autoconfigure、micrometer-core；可选依赖 spring-boot-actuator、spring-boot-health、spring-boot-configuration-processor |
| **版本** | 2.0.0 |

## 核心能力

### 1. Server / Client 抽象

| 类 | 说明 |
|---|---|
| `AbstractNettyServer` | TCP Server 抽象基类，封装 Bootstrap + Pipeline 配置 + 生命周期管理 + SslContext 缓存 + 优雅关闭；子类实现 `initChannelPipeline(SocketChannel)` |
| `NettyServerLifecycle` | Server 生命周期管理（随 Spring 容器启动/停止，支持 fail-fast：任一 Server 启动失败终止应用） |
| `AbstractNettyClient` | TCP Client 抽象基类，CAS 原子保护连接、同步发送；子类实现 `initChannelPipeline(SocketChannel)` |
| `ReconnectHandler` | 断线重连处理器，指数退避重连 + volatile ctx |

### 2. 编解码

| 类 | 说明 |
|---|---|
| `LengthFieldFrameDecoder` | LengthField 拆包器，解决 TCP 粘包/半包问题（默认最大帧 1MB） |
| `LengthFieldCodec` | LengthField 编解码组合（Decoder + LengthFieldPrepender 一站式），协议格式：4 字节长度 + Payload |
| `JsonMessageCodec<T>` | JSON 消息编解码器，基于 `YdszJson` |

协议格式：

```
+--------+------------------+
| Length  | Payload          |
| 4 bytes | Length bytes     |
+--------+------------------+
```

### 3. 心跳与空闲

| 类 | 说明 |
|---|---|
| `IdleStateHandlerFactory` | 空闲状态处理器工厂，支持读 / 写 / 读写空闲检测 |
| `ChannelGroupManager` | Channel 组管理（全局广播 / 业务分组推送 / 空分组自动清理） |
| `TrafficMonitoringHandler` | 流量监控 Handler，自动统计读写字节和消息数 |
| `ConnectionEventHandler` | 连接事件监控 Handler，自动追踪连接/断开 |

### 4. 消息处理与事件分发

| 类 | 说明 |
|---|---|
| `ChannelEventDispatcher` | Channel 事件分发器（连接 / 断开 / 异常等事件分发到监听器） |
| `ChannelEventListener` | Channel 事件监听器 SPI 接口，业务侧实现订阅 Channel 生命周期事件 |
| `NettyPipelineDiagnostics` | Pipeline 诊断工具，运行时打印 Handler 链结构与事件传播路径，辅助排查 Handler 顺序问题 |

> 说明：`@MessageHandler` 注解 / `MessageDispatcher` 分发器已在 v2.0.0 **移除**（v1.1.0 起标注 Deprecated，无活跃消费者）。消息处理统一使用 `SimpleChannelInboundHandler` 推荐模式。

### 5. SSL/TLS

| 类 | 说明 |
|---|---|
| `SslContextFactory` | SSL Context 工厂，证书加载 + 双向认证（server/client 上下文分离） |

### 5.1 异常处理

| 类 | 说明 |
|---|---|
| `NettyException` | Netty 模块统一异常，封装 Server/Client/SSL/Transport 各层错误，错误码 B01055 |

### 6. 连接控制

| 类 | 说明 |
|---|---|
| `ConnectionLimitHandler` | 连接限制 Handler，超过 `ydsz.netty.connection-control.max-connections` 时拒绝新连接（0 表示不限制） |
| `ConnectionMetrics` | 连接级指标采集，与 `NettyChannelMetrics` 协作提供更细粒度的连接维度监控 |

### 7. 线程池与传输

| 类 | 说明 |
|---|---|
| `NettyEventLoopPool` | EventLoop 线程池管理，支持共享/隔离模式、引用计数、优雅关闭 await |
| `NativeTransportDetector` | 原生传输检测器，自动检测 Epoll（Linux）/ KQueue（macOS），不匹配时降级到 NIO |

### 8. 可观测性

| 类 | 说明 |
|---|---|
| `NettyChannelMetrics` | Channel 指标采集（9 项 Micrometer 指标） |
| `NettyHealthIndicator` | Spring Boot Actuator 健康检查 |
| `NettyActuatorEndpoint` | Actuator 端点 `/netty`，运行时查询 Server 状态、EventLoop 引用计数、Channel 列表 |

### 9. 配置

| 类 | 说明 |
|---|---|
| `NettyProperties` | Netty 配置属性（JSR-303 校验，前缀 `ydsz.netty.*`） |
| `NettyAutoConfiguration` | 自动配置，通过 `BeanPostProcessor` 自动注入 metrics + eventLoopPool 到所有 Server/Client |

## 接入方式

### 1. POM 引入依赖

```xml
<dependency>
    <groupId>com.njydsz</groupId>
    <artifactId>ydsz-common-netty</artifactId>
</dependency>
```

### 2. 配置启用

```yaml
ydsz:
  netty:
    boss-threads: 1
    worker-threads: 0               # 0 = CPU 核数 × 2
    so-backlog: 128
    so-keep-alive: true
    tcp-no-delay: true
    connect-timeout-millis: 5000
    shared-event-loop: true
    fail-fast: true
    native-transport: AUTO           # AUTO / ENABLED / DISABLED
    allocator:
      pooled: true                   # 是否使用 PooledByteBufAllocator
      prefer-direct: true           # 是否优先直接内存
      num-direct-arenas: 0           # 直接内存竞技场数（0 = CPU 核数 × 2）
    connection-control:
      max-connections: 0             # 最大连接数（0 = 不限制）
    idle:
      reader-idle-seconds: 60
      writer-idle-seconds: 30
    reconnect:
      enabled: true
      initial-delay-ms: 1000
      max-delay-ms: 60000
      max-retries: -1                # -1 = 无限重试
```

### 3. 实现 TCP Server

```java
import com.njydsz.common.netty.server.AbstractNettyServer;
import com.njydsz.common.netty.config.NettyProperties;
import com.njydsz.common.netty.codec.LengthFieldCodec;
import io.netty.channel.socket.SocketChannel;
import org.springframework.stereotype.Component;

@Component
public class MyTcpServer extends AbstractNettyServer {

    public MyTcpServer(NettyProperties props) {
        super(8080, props);
    }

    @Override
    protected void initChannelPipeline(SocketChannel ch) {
        LengthFieldCodec.addToPipeline(ch.pipeline());
        ch.pipeline().addLast(new MyBusinessHandler());
    }
}
```

### 4. 实现 TCP Client

```java
import com.njydsz.common.netty.client.AbstractNettyClient;
import com.njydsz.common.netty.config.NettyProperties;
import com.njydsz.common.netty.codec.LengthFieldCodec;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.socket.SocketChannel;
import org.springframework.stereotype.Component;

@Component
public class MyTcpClient extends AbstractNettyClient {

    public MyTcpClient(NettyProperties props) {
        super("127.0.0.1", 8080, props);
    }

    @Override
    protected void initChannelPipeline(SocketChannel ch) {
        LengthFieldCodec.addToPipeline(ch.pipeline());
        ch.pipeline().addLast(new MyBusinessHandler());
    }

    /**
     * 业务 Handler 示例 — 处理服务端下发的消息。
     *
     * <p>推荐使用 SimpleChannelInboundHandler 或 ChannelInboundHandlerAdapter，
     * 在 channelRead 中按消息 type 字段做 switch 分发。
     */
    static class MyBusinessHandler extends ChannelInboundHandlerAdapter {
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            // 处理业务消息...
        }
    }
}
```

## 配置项

| 配置 | 默认值 | 说明 |
|---|---|---|
| `ydsz.netty.enabled` | true | 是否启用 Netty 模块 |
| `ydsz.netty.boss-threads` | 1 | Boss 线程数（接受连接） |
| `ydsz.netty.worker-threads` | 0 | Worker 线程数（0 = CPU 核数 × 2） |
| `ydsz.netty.so-backlog` | 128 | 连接队列大小 |
| `ydsz.netty.so-keep-alive` | true | TCP KeepAlive |
| `ydsz.netty.tcp-no-delay` | true | TCP_NODELAY |
| `ydsz.netty.connect-timeout-millis` | 5000 | 连接超时（毫秒） |
| `ydsz.netty.shared-event-loop` | true | 是否共享 EventLoopGroup（true=全局复用，false=独立） |
| `ydsz.netty.shutdown-quiet-period-seconds` | 2 | 优雅关闭静默期（秒） |
| `ydsz.netty.shutdown-timeout-seconds` | 15 | 优雅关闭超时（秒） |
| `ydsz.netty.fail-fast` | true | Server 启动失败时是否终止应用 |
| `ydsz.netty.native-transport` | AUTO | 原生传输模式（AUTO / ENABLED / DISABLED） |
| `ydsz.netty.allocator.pooled` | true | 是否使用 PooledByteBufAllocator |
| `ydsz.netty.allocator.prefer-direct` | true | 是否优先分配直接内存（堆外） |
| `ydsz.netty.allocator.num-direct-arenas` | 0 | 直接内存竞技场数（0 = CPU 核数 × 2） |
| `ydsz.netty.connection-control.max-connections` | 0 | 最大连接数限制（0 = 不限制） |
| `ydsz.netty.idle.reader-idle-seconds` | 60 | 读空闲超时（秒，0=不检测） |
| `ydsz.netty.idle.writer-idle-seconds` | 30 | 写空闲超时（秒，0=不检测） |
| `ydsz.netty.idle.all-idle-seconds` | 0 | 全双工空闲超时（秒，0=不检测） |
| `ydsz.netty.reconnect.enabled` | true | 断线重连开关 |
| `ydsz.netty.reconnect.initial-delay-ms` | 1000 | 初始重连延迟（毫秒） |
| `ydsz.netty.reconnect.max-delay-ms` | 60000 | 最大重连延迟（毫秒） |
| `ydsz.netty.reconnect.max-retries` | -1 | 最大重试次数（-1=无限） |
| `ydsz.netty.ssl.enabled` | false | SSL/TLS 开关 |
| `ydsz.netty.ssl.key-store` | - | 密钥库路径 |
| `ydsz.netty.ssl.key-store-password` | - | 密钥库密码 |
| `ydsz.netty.ssl.key-store-type` | PKCS12 | 密钥库类型（PKCS12 / JKS） |
| `ydsz.netty.ssl.trust-store` | - | 信任库路径（双向认证） |
| `ydsz.netty.ssl.trust-store-password` | - | 信任库密码 |
| `ydsz.netty.ssl.trust-store-type` | PKCS12 | 信任库类型 |
| `ydsz.netty.ssl.need-client-auth` | false | 是否要求客户端认证（双向认证） |
| `ydsz.netty.traffic-shaping.enabled` | false | 流量整形开关 |
| `ydsz.netty.traffic-shaping.write-limit` | 0 | 写限速（bytes/s，0=不限） |
| `ydsz.netty.traffic-shaping.read-limit` | 0 | 读限速（bytes/s，0=不限） |
| `ydsz.netty.traffic-shaping.check-interval-ms` | 1000 | 检查间隔（毫秒） |
| `ydsz.netty.traffic-shaping.global` | false | 是否全局流量整形（true=限制整个 Server 总带宽） |

> 说明：`ydsz.netty.dispatcher.enabled`（MessageDispatcher 注解扫描）配置**不存在**（分发器已移除）。

## 使用示例

### 1. 消息处理推荐模式

**推荐：使用 `SimpleChannelInboundHandler<T>` + 策略模式（默认）**

```java
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

public class MyBusinessHandler extends SimpleChannelInboundHandler<MyMessage> {

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, MyMessage msg) {
        switch (msg.getType()) {
            case "AUTH" -> handleAuth(ctx, msg);
            case "PING" -> handlePing(ctx, msg);
            case "ORDER" -> handleOrder(ctx, msg);
            default -> log.warn("未知消息类型: {}", msg.getType());
        }
    }

    private void handleAuth(ChannelHandlerContext ctx, MyMessage msg) { ... }
    private void handlePing(ChannelHandlerContext ctx, MyMessage msg) { ... }
    private void handleOrder(ChannelHandlerContext ctx, MyMessage msg) { ... }
}
```

**适用场景：** 消息类型固定、业务逻辑集中在同一 Handler 内。性能最优（无反射/MethodHandle 开销），代码可读性高。

> 说明：`@MessageHandler` 注解分发模式已移除，请使用上述 `SimpleChannelInboundHandler` + switch 策略模式。

### 2. Channel 事件监听

```java
import com.njydsz.common.netty.event.ChannelEventListener;
import io.netty.channel.Channel;
import org.springframework.stereotype.Component;

@Component
public class MyChannelEventListener implements ChannelEventListener {

    @Override
    public void onConnect(Channel channel) {
        log.info("新连接: {}", channel.remoteAddress());
    }

    @Override
    public void onDisconnect(Channel channel) {
        log.info("连接断开: {}", channel.remoteAddress());
    }

    @Override
    public void onException(Channel channel, Throwable cause) {
        log.error("连接异常: {}", channel.remoteAddress(), cause);
    }
}
```

### 3. Channel 分组广播

```java
// 向指定业务组广播消息
server.getChannelGroupManager().broadcastToGroup("room-123", message);

// 向所有连接广播
server.getChannelGroupManager().broadcastGlobal(message);

// 查询业务分组列表
Set<String> groups = server.getChannelGroupManager().getGroupKeys();
```

### 4. SSL/TLS 配置（双向认证）

```yaml
ydsz:
  netty:
    ssl:
      enabled: true
      key-store: classpath:server-keystore.p12
      key-store-password: changeit
      key-store-type: PKCS12
      trust-store: classpath:server-truststore.p12
      trust-store-password: changeit
      need-client-auth: true       # 双向认证
```

### 5. 流量整形配置

```yaml
ydsz:
  netty:
    traffic-shaping:
      enabled: true
      write-limit: 1048576         # 1 MB/s 写限速
      read-limit: 1048576          # 1 MB/s 读限速
      check-interval-ms: 1000
      global: true                 # 限制整个 Server 总带宽
```

## SPI 扩展点

| SPI 接口 | 用途 | 实现方 |
|---|---|---|
| `AbstractNettyServer` | TCP Server 抽象基类，业务继承实现自定义 Pipeline | 业务模块实现 |
| `AbstractNettyClient` | TCP Client 抽象基类，业务继承实现自定义 Pipeline | 业务模块实现 |
| `ChannelEventListener` | Channel 事件监听器，业务实现订阅连接/断开/异常事件 | 业务模块实现 |
| `JsonMessageCodec<T>` | JSON 消息编解码器，业务可扩展自定义编解码 | 框架内置 JSON 实现 |

## 健康检查

| 端点 | 说明 | 触发条件 |
|---|---|---|
| `/actuator/health/netty` | Netty 健康检查 | `spring-boot-health` 在类路径，`ydsz.netty.enabled=true` |
| `/actuator/netty` | Netty Actuator 端点（JSON 状态查询） | `spring-boot-actuator` 在类路径 |

健康检查暴露信息：

- **Server 列表**：每个 Server 的 `running` / `port` / `activeChannels` / `ssl` / `businessGroups`
- **EventLoop 池**：`bossRefCount` / `workerRefCount` / `bossGroupActive` / `workerGroupActive`
- **连接控制**：`maxConnections` / `currentConnections`（超限时连接被拒绝）
- **指标摘要**：`activeChannels` / `totalBytesRead` / `totalBytesWritten`

健康判定：所有 Server 都 `running=true` 时为 UP，任一 Server 未运行时为 DOWN。

### Micrometer 指标

| 指标 | 类型 | 说明 |
|---|---|---|
| `ydsz.netty.channels.active` | Gauge | 活跃 Channel 数 |
| `ydsz.netty.bytes.read.total` | Gauge | 累计读取字节数 |
| `ydsz.netty.bytes.written.total` | Gauge | 累计写入字节数 |
| `ydsz.netty.connections.total` | Counter | 累计连接数 |
| `ydsz.netty.disconnections.total` | Counter | 累计断开数 |
| `ydsz.netty.messages.received` | Counter | 消息接收数 |
| `ydsz.netty.messages.sent` | Counter | 消息发送数 |
| `ydsz.netty.reconnect.attempts` | Counter | 重连尝试次数 |
| `ydsz.netty.reconnect.successes` | Counter | 重连成功次数 |

## 注意事项

1. **EventLoop 共享**：`shared-event-loop=true`（默认）时所有 Server/Client 复用同一 EventLoopGroup，引用计数管理；高并发独立部署场景可设为 false 隔离。
2. **原生传输**：`native-transport=AUTO` 自动检测 Epoll（Linux）/ KQueue（macOS），不匹配降级 NIO；强制启用 `ENABLED` 在不支持平台会启动失败。
3. **fail-fast**：`fail-fast=true` 时任一 Server 启动失败（如端口占用）会终止应用；生产环境推荐保持 true，测试环境可关闭。
4. **SSL 证书缓存**：`SslContext` 在 Server/Client 启动时一次性创建并缓存，证书热更新需重启应用。
5. **断线重连退避**：`ReconnectHandler` 采用指数退避算法，`delay = min(initialDelayMs × 2^n, maxDelayMs)`，避免重连风暴。
6. **流量整形**：`global=true` 使用 `GlobalTrafficShapingHandler` 限制 Server 总带宽；`false` 使用 `ChannelTrafficShapingHandler` 限制单连接带宽。
7. **粘包处理**：默认使用 4 字节 LengthField 协议；自定义协议需在 `initChannelPipeline` 中替换为合适的 FrameDecoder。
8. **Netty 日志**：`AbstractNettyServer` 静态初始化块强制 Netty 使用 SLF4J 日志门面，与项目日志体系统一。
9. **优雅关闭**：`shutdown-quiet-period-seconds` + `shutdown-timeout-seconds` 控制 EventLoopGroup 优雅关闭，确保已接受连接处理完成。
10. **PooledByteBufAllocator**：默认启用池化分配器（`allocator.pooled=true`）减少 GC；`prefer-direct=true` 优先堆外内存适合大流量场景；如果观察到堆外内存泄漏可设为 `false` 回退到堆内存分配。
11. **连接限制**：`connection-control.max-connections` 超过上限时 `ConnectionLimitHandler` 直接拒绝新连接，防止服务过载。

## 变更记录

- **v2.0.0**（2026-08-16）：移除 `MessageDispatcher` / `@MessageHandler`（原 v1.1.0 标记 @Deprecated，无活跃消费者），推荐使用 `SimpleChannelInboundHandler` + switch 策略模式；新增 `allocator`（ByteBuf 分配器）、`connection-control`（连接数限制）配置段；新增 `ConnectionLimitHandler`、`ConnectionMetrics`、`NettyPipelineDiagnostics`、`NettyActuatorEndpoint`；provided 依赖 `micrometer-core` 改为通过 `@ConditionalOnClass` 可选装配
- **v1.1.0**（2026-08-16）：`MessageDispatcher` / `@MessageHandler` 标记 @Deprecated（计划 v2.0.0 移除）
- **v1.0.0**（2026-08-02）：对标 common-jdbc 标准格式重构 README
