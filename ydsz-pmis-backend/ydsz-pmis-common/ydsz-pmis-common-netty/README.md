# ydsz-pmis-common-netty

PMIS Netty 网络通信框架 — TCP Server/Client 抽象、断线重连、心跳空闲检测、SSL/TLS、LengthField 编解码、EventLoop 池管理、Channel 组管理、Micrometer 指标。

## 模块定位

| 属性 | 值 |
|---|---|
| **层级** | L5 业务服务层 |
| **类型** | 公共依赖库（不独立部署） |
| **源文件数** | 12 |

## 核心能力

### Server / Client 抽象

| 类 | 说明 |
|---|---|
| `AbstractNettyServer` | TCP Server 抽象基类（Bootstrap + Pipeline 配置 + 生命周期管理） |
| `NettyServerLifecycle` | Server 生命周期管理（启动 / 停止 / 优雅关闭） |
| `AbstractNettyClient` | TCP Client 抽象基类 |
| `ReconnectHandler` | 断线重连处理器（指数退避重连） |

### 编解码

| 类 | 说明 |
|---|---|
| `LengthFieldFrameDecoder` | Length Field 拆包器（解决粘包/半包问题） |

### 心跳与空闲

| 类 | 说明 |
|---|---|
| `IdleStateHandlerFactory` | 空闲状态处理器工厂（读 / 写 / 读写空闲检测） |
| `ChannelGroupManager` | Channel 组管理（广播 / 在线统计） |

### SSL/TLS

| 类 | 说明 |
|---|---|
| `SslContextFactory` | SSL Context 工厂（证书加载 + 双向认证） |

### 线程池

| 类 | 说明 |
|---|---|
| `NettyEventLoopPool` | EventLoop 线程池管理（Boss / Worker 分离） |

### 可观测性

| 类 | 说明 |
|---|---|
| `NettyChannelMetrics` | Channel 指标采集（连接数 / 读写字节 / 延迟） |

### 配置

| 类 | 说明 |
|---|---|
| `NettyProperties` | Netty 配置属性 |
| `NettyAutoConfiguration` | 自动配置 |

## 配置项

```yaml
pmis:
  netty:
    boss-threads: 1                 # Boss 线程数
    worker-threads: 0               # Worker 线程数（0 = CPU 核数 × 2）
    so-backlog: 1024                # 连接队列
    so-keepalive: true              # TCP KeepAlive
    idle:
      reader-timeout: 60s           # 读空闲超时
      writer-timeout: 30s           # 写空闲超时
      all-timeout: 0                # 读写空闲超时（0 = 不检测）
    reconnect:
      enabled: true                 # 断线重连
      max-retries: 10               # 最大重试次数
      interval: 5s                  # 重试间隔
    ssl:
      enabled: false                # SSL 开关
      cert-path: classpath:cert/server.crt
      key-path: classpath:cert/server.key
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
