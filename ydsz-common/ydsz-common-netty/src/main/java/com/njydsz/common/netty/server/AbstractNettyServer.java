package com.njydsz.common.netty.server;

import java.net.BindException;
import java.net.InetSocketAddress;

import lombok.extern.slf4j.Slf4j;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.handler.traffic.ChannelTrafficShapingHandler;
import io.netty.handler.traffic.GlobalTrafficShapingHandler;
import io.netty.util.internal.logging.InternalLoggerFactory;
import io.netty.util.internal.logging.Slf4JLoggerFactory;

import com.njydsz.common.netty.config.NettyProperties;
import com.njydsz.common.netty.event.ChannelEventDispatcher;
import com.njydsz.common.netty.handler.ChannelGroupManager;
import com.njydsz.common.netty.handler.ConnectionEventHandler;
import com.njydsz.common.netty.handler.ConnectionLimitHandler;
import com.njydsz.common.netty.handler.IdleStateHandlerFactory;
import com.njydsz.common.netty.handler.TrafficMonitoringHandler;
import com.njydsz.common.netty.metric.NettyChannelMetrics;
import com.njydsz.common.netty.pool.NettyEventLoopPool;
import com.njydsz.common.netty.ssl.SslContextFactory;
import com.njydsz.common.netty.transport.NativeTransportDetector;

/**
 * Netty TCP Server 抽象基类。
 *
 * <p>封装 Server 的启动、Pipeline 初始化、SSL/TLS、空闲检测、流量整形、 指标监控、优雅停机等通用逻辑。 子类只需实现 {@link
 * #initChannelPipeline(SocketChannel)} 方法，添加业务 Handler。
 *
 * <p>生命周期通过 {@link NettyServerLifecycle} 与 Spring 容器集成， 随容器启动自动绑定端口，容器关闭时优雅释放资源。
 *
 * <p>使用示例：
 *
 * <pre>{@code
 * @Component
 * public class MyTcpServer extends AbstractNettyServer {
 *     public MyTcpServer(NettyProperties props) { super(8080, props); }
 *
 *     @Override
 *     protected void initChannelPipeline(SocketChannel ch) {
 *         ch.pipeline().addLast(new LengthFieldFrameDecoder());
 *         ch.pipeline().addLast(new MyBusinessHandler());
 *     }
 * }
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public abstract class AbstractNettyServer {

  /** 默认 Page 大小（8KB） */
  private static final int DEFAULT_PAGE_SIZE = 8192;

  /** 默认页拆分阶数（chunkSize = 8KB << 11 = 16MB） */
  private static final int DEFAULT_MAX_ORDER = 11;

  /** 默认写缓冲区低水位线（32KB） */
  private static final int DEFAULT_WRITE_BUFFER_LOW_WATER_MARK = 32 * 1024;

  /** 默认写缓冲区高水位线（64KB） */
  private static final int DEFAULT_WRITE_BUFFER_HIGH_WATER_MARK = 64 * 1024;

  static {
    // 强制 Netty 使用 SLF4J 日志门面
    InternalLoggerFactory.setDefaultFactory(Slf4JLoggerFactory.INSTANCE);
  }

  protected final int port;
  protected final NettyProperties properties;
  protected final ChannelGroupManager channelGroupManager = new ChannelGroupManager();

  private EventLoopGroup bossGroup;
  private EventLoopGroup workerGroup;
  private Channel serverChannel;
  private SslContext sslContext;
  private GlobalTrafficShapingHandler globalTrafficShapingHandler;

  /** 可选依赖 — 指标收集器（由 NettyAutoConfiguration 通过 setter 注入） */
  private NettyChannelMetrics metrics;

  /** 可选依赖 — EventLoop 池（由 NettyAutoConfiguration 通过 setter 注入） */
  private NettyEventLoopPool eventLoopPool;

  /** 可选依赖 — Channel 事件分发器（由 NettyAutoConfiguration 通过 setter 注入） */
  private ChannelEventDispatcher channelEventDispatcher;

  /**
   * 构造 Netty TCP Server。
   *
   * @param port 监听端口
   * @param properties Netty 配置
   */
  protected AbstractNettyServer(int port, NettyProperties properties) {
    this.port = port;
    this.properties = properties;
  }

  /**
   * 启动 TCP Server。
   *
   * <p>分阶段初始化：EventLoop → SSL → 流量整形 → Pipeline 配置 → 端口绑定。
   *
   * @throws InterruptedException 启动被中断
   */
  public void start() throws InterruptedException {
    NettyEventLoopPool pool = getEventLoopPool();

    initEventLoopGroups(pool);
    initSslContext();
    initGlobalTrafficShaping();

    TrafficMonitoringHandler trafficHandler = createTrafficMonitoringHandler();
    ConnectionEventHandler connectionHandler = createConnectionEventHandler();

    ServerBootstrap bootstrap = configureServerBootstrap(pool, trafficHandler, connectionHandler);
    bindServerPort(bootstrap);

    log.info(
        "[Netty-Server] {} 启动成功, 监听端口={}, ssl={}, trafficShaping={}",
        getClass().getSimpleName(),
        port,
        properties.getSsl().isEnabled(),
        properties.getTrafficShaping().isEnabled());
  }

  /**
   * 初始化 EventLoopGroup（共享或隔离模式）。
   *
   * @param pool EventLoop 池
   */
  private void initEventLoopGroups(NettyEventLoopPool pool) {
    if (properties.isSharedEventLoop()) {
      bossGroup = pool.acquireBossGroup(properties.getBossThreads());
      workerGroup = pool.acquireWorkerGroup(properties.getWorkerThreads());
    } else {
      bossGroup = pool.createIsolatedBossGroup(properties.getBossThreads());
      workerGroup = pool.createIsolatedWorkerGroup(properties.getWorkerThreads());
    }
  }

  /** 初始化 SSL/TLS 上下文（如启用）。 */
  private void initSslContext() {
    if (properties.getSsl().isEnabled()) {
      NettyProperties.Ssl ssl = properties.getSsl();
      SslContextFactory.SslStoreConfig keyStore =
          new SslContextFactory.SslStoreConfig(
              ssl.getKeyStore(), ssl.getKeyStorePassword(), ssl.getKeyStoreType());
      SslContextFactory.SslStoreConfig trustStore =
          ssl.getTrustStore() != null
              ? new SslContextFactory.SslStoreConfig(
                  ssl.getTrustStore(), ssl.getTrustStorePassword(), ssl.getTrustStoreType())
              : null;
      sslContext =
          SslContextFactory.createServerContext(keyStore, trustStore, ssl.isNeedClientAuth());
    }
  }

  /** 初始化全局流量整形器（全局模式时生效）。 */
  private void initGlobalTrafficShaping() {
    if (properties.getTrafficShaping().isEnabled() && properties.getTrafficShaping().isGlobal()) {
      globalTrafficShapingHandler =
          new GlobalTrafficShapingHandler(
              workerGroup,
              properties.getTrafficShaping().getWriteLimit(),
              properties.getTrafficShaping().getReadLimit(),
              properties.getTrafficShaping().getCheckIntervalMs());
    }
  }

  /**
   * 创建流量监控 Handler（指标收集器启用时）。
   *
   * @return 监控 Handler，未启用指标时返回 {@code null}
   */
  private TrafficMonitoringHandler createTrafficMonitoringHandler() {
    return metrics != null ? new TrafficMonitoringHandler(metrics) : null;
  }

  /**
   * 创建连接事件监控 Handler（指标收集器启用时）。
   *
   * @return 连接事件 Handler，未启用指标时返回 {@code null}
   */
  private ConnectionEventHandler createConnectionEventHandler() {
    return metrics != null ? new ConnectionEventHandler(metrics) : null;
  }

  /**
   * 配置 ServerBootstrap 参数与 Pipeline 初始化器。
   *
   * @param pool EventLoop 池（用于获取传输类型）
   * @param trafficHandler 流量监控 Handler（可为 {@code null}）
   * @param connectionHandler 连接事件 Handler（可为 {@code null}）
   * @return 已配置的 ServerBootstrap
   */
  private ServerBootstrap configureServerBootstrap(
      NettyEventLoopPool pool,
      TrafficMonitoringHandler trafficHandler,
      ConnectionEventHandler connectionHandler) {

    ServerBootstrap bootstrap = new ServerBootstrap();
    bootstrap
        .group(bossGroup, workerGroup)
        .channel(NativeTransportDetector.getServerSocketChannelClass(pool.getTransportType()))
        .option(ChannelOption.SO_BACKLOG, properties.getSoBacklog())
        .childOption(ChannelOption.SO_KEEPALIVE, properties.isSoKeepAlive())
        .childOption(ChannelOption.TCP_NODELAY, properties.isTcpNoDelay())
        .childOption(ChannelOption.ALLOCATOR, createByteBufAllocator())
        .childOption(ChannelOption.WRITE_BUFFER_WATER_MARK, createWriteBufferWaterMark())
        .childHandler(
            new ServerChannelInitializer(
                trafficHandler,
                connectionHandler,
                channelGroupManager,
                createConnectionLimitHandler()));
    return bootstrap;
  }

  /**
   * 创建 ByteBuf 分配器（基于配置选择内存池或直接内存）。
   *
   * @return ByteBufAllocator 实例
   */
  private ByteBufAllocator createByteBufAllocator() {
    NettyProperties.Allocator allocConfig = properties.getAllocator();
    if (allocConfig.isPooled()) {
      return new PooledByteBufAllocator(
          allocConfig.isPreferDirect(),
          allocConfig.getNumDirectArenas(),
          0,
          DEFAULT_PAGE_SIZE,
          DEFAULT_MAX_ORDER);
    }
    return new UnpooledByteBufAllocator(allocConfig.isPreferDirect());
  }

  /**
   * 创建写缓冲区水位线。
   *
   * @return WriteBufferWaterMark 实例
   */
  private WriteBufferWaterMark createWriteBufferWaterMark() {
    return new WriteBufferWaterMark(
        DEFAULT_WRITE_BUFFER_LOW_WATER_MARK, DEFAULT_WRITE_BUFFER_HIGH_WATER_MARK);
  }

  /**
   * 创建连接数限制 Handler（配置了 maxConnections 时生效）。
   *
   * @return ConnectionLimitHandler 实例，未配置时返回 {@code null}
   */
  private ConnectionLimitHandler createConnectionLimitHandler() {
    int maxConn = properties.getConnectionControl().getMaxConnections();
    return maxConn > 0 ? new ConnectionLimitHandler(maxConn) : null;
  }

  /**
   * 绑定端口并处理启动异常。
   *
   * @param bootstrap 已配置的 ServerBootstrap
   * @throws InterruptedException 绑定被中断
   */
  private void bindServerPort(ServerBootstrap bootstrap) throws InterruptedException {
    try {
      serverChannel = bootstrap.bind(port).sync().channel();
    } catch (Exception e) {
      Throwable cause = e.getCause() != null ? e.getCause() : e;
      if (cause instanceof BindException) {
        log.error("[Netty-Server] {} 端口 {} 已被占用，请检查配置或释放占用进程", getClass().getSimpleName(), port);
      }
      throw e;
    }
  }

  /** 停止 TCP Server（优雅关闭，等待在途消息处理完成）。 */
  public void stop() {
    log.info("[Netty-Server] {} 正在关闭...", getClass().getSimpleName());

    // 先关闭全局流量整形
    if (globalTrafficShapingHandler != null) {
      globalTrafficShapingHandler.release();
    }

    // 关闭 Server Channel（停止接受新连接）
    if (serverChannel != null) {
      try {
        serverChannel.close().sync();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        log.warn("[Netty-Server] Server Channel 关闭被中断");
      }
    }

    // 释放 EventLoopGroup（等待优雅关闭完成）
    NettyEventLoopPool pool = getEventLoopPool();
    if (properties.isSharedEventLoop()) {
      pool.releaseBossGroup();
      pool.releaseWorkerGroup();
    } else {
      pool.shutdownGroup(bossGroup);
      pool.shutdownGroup(workerGroup);
    }

    log.info("[Netty-Server] {} 已关闭", getClass().getSimpleName());
  }

  /**
   * Netty Server Channel 初始化器（从 start() 中拆分的独立内部类，提升可测试性）。
   *
   * <p>添加通用 Handler 链：连接限制 → SSL → 流量整形 → 空闲检测 → 监控 → 业务 Handler。
   */
  private final class ServerChannelInitializer extends ChannelInitializer<SocketChannel> {

    private final TrafficMonitoringHandler trafficHandler;
    private final ConnectionEventHandler connectionHandler;
    private final ChannelGroupManager groupManager;
    private final ConnectionLimitHandler connectionLimitHandler;

    /**
     * 构造初始化器。
     *
     * @param trafficHandler 流量监控 Handler（可为 {@code null}）
     * @param connectionHandler 连接事件 Handler（可为 {@code null}）
     * @param groupManager Channel 组管理器
     * @param connectionLimitHandler 连接数限制 Handler（可为 {@code null}）
     */
    ServerChannelInitializer(
        TrafficMonitoringHandler trafficHandler,
        ConnectionEventHandler connectionHandler,
        ChannelGroupManager groupManager,
        ConnectionLimitHandler connectionLimitHandler) {
      this.trafficHandler = trafficHandler;
      this.connectionHandler = connectionHandler;
      this.groupManager = groupManager;
      this.connectionLimitHandler = connectionLimitHandler;
    }

    @Override
    protected void initChannel(SocketChannel ch) {
      ChannelPipeline pipeline = ch.pipeline();

      // 连接数限制（Pipeline 最前端）
      if (connectionLimitHandler != null) {
        pipeline.addLast("connectionLimit", connectionLimitHandler);
      }

      // SSL/TLS（复用已创建的 SslContext）
      if (sslContext != null) {
        pipeline.addLast("ssl", sslContext.newHandler(ch.alloc()));
      }

      // 全局流量整形
      if (globalTrafficShapingHandler != null) {
        pipeline.addLast("globalTraffic", globalTrafficShapingHandler);
      }

      // 空闲检测
      IdleStateHandlerFactory idleFactory =
          new IdleStateHandlerFactory(
              properties.getIdle().getReaderIdleSeconds(),
              properties.getIdle().getWriterIdleSeconds(),
              properties.getIdle().getAllIdleSeconds());
      pipeline.addLast("idleState", idleFactory.create());

      // Per-Channel 流量整形（非全局模式时）
      if (properties.getTrafficShaping().isEnabled()
          && !properties.getTrafficShaping().isGlobal()) {
        pipeline.addLast(
            "trafficShaping",
            new ChannelTrafficShapingHandler(
                properties.getTrafficShaping().getWriteLimit(),
                properties.getTrafficShaping().getReadLimit(),
                properties.getTrafficShaping().getCheckIntervalMs()));
      }

      // 大文件分块写支持
      pipeline.addLast("chunkedWrite", new ChunkedWriteHandler());

      // 指标监控 — 流量统计
      if (trafficHandler != null) {
        pipeline.addLast("trafficMonitor", trafficHandler);
      }

      // 连接事件监控（后置）
      if (connectionHandler != null) {
        pipeline.addLast("connectionEvent", connectionHandler);
      }

      if (channelEventDispatcher != null) {
        pipeline.addLast("channelEventDispatcher", channelEventDispatcher);
      }

      // 子类自定义 Pipeline
      initChannelPipeline(ch);

      groupManager.add(ch);
    }
  }

  /**
   * 子类实现：初始化 Channel Pipeline（添加业务 Handler）。
   *
   * <p>注意：SSL、空闲检测、流量整形、指标监控等通用 Handler 已由父类自动添加， 子类只需添加业务编解码器和处理器。
   *
   * @param ch SocketChannel
   */
  protected abstract void initChannelPipeline(SocketChannel ch);

  /**
   * 获取 Channel 组管理器（用于广播/分组推送）。
   *
   * @return Channel 组管理器
   */
  public ChannelGroupManager getChannelGroupManager() {
    return channelGroupManager;
  }

  /**
   * 获取监听端口。
   *
   * <p>若 Server 已启动且绑定端口为 0（自动分配），返回实际监听端口； 否则返回构造时配置的端口。
   *
   * @return 端口
   */
  public int getPort() {
    if (serverChannel != null && serverChannel.isActive()) {
      try {
        return ((InetSocketAddress) serverChannel.localAddress()).getPort();
      } catch (ClassCastException | NullPointerException ignored) {
        // 降级返回配置端口
      }
    }
    return port;
  }

  /**
   * 获取 Netty 配置属性。
   *
   * @return 配置属性
   */
  public NettyProperties getProperties() {
    return properties;
  }

  /**
   * 判断 Server 是否已启动。
   *
   * @return true 表示已启动
   */
  public boolean isRunning() {
    return serverChannel != null && serverChannel.isActive();
  }

  /**
   * 设置指标收集器（由 NettyAutoConfiguration 通过 BeanPostProcessor 注入）。
   *
   * @param metrics Netty 指标收集器
   */
  public void setMetrics(NettyChannelMetrics metrics) {
    this.metrics = metrics;
  }

  /**
   * 获取指标收集器。
   *
   * @return 指标收集器（可能为 null）
   */
  public NettyChannelMetrics getMetrics() {
    return metrics;
  }

  /**
   * 设置 EventLoop 池（由 NettyAutoConfiguration 通过 BeanPostProcessor 注入）。
   *
   * @param eventLoopPool EventLoop 池实例
   */
  public void setEventLoopPool(NettyEventLoopPool eventLoopPool) {
    this.eventLoopPool = eventLoopPool;
  }

  /**
   * 获取 EventLoop 池（未注入时创建默认实例）。
   *
   * @return EventLoop 池实例
   */
  protected NettyEventLoopPool getEventLoopPool() {
    if (eventLoopPool == null) {
      eventLoopPool =
          new NettyEventLoopPool(
              properties.getShutdownQuietPeriodSeconds(),
              properties.getShutdownTimeoutSeconds(),
              properties.getNativeTransport());
    }
    return eventLoopPool;
  }

  public void setChannelEventDispatcher(ChannelEventDispatcher channelEventDispatcher) {
    this.channelEventDispatcher = channelEventDispatcher;
  }

  /**
   * 获取全局流量整形 Handler（仅供测试与监控诊断使用）。
   *
   * @return GlobalTrafficShapingHandler 实例（未启用时为 null）
   */
  GlobalTrafficShapingHandler getGlobalTrafficShapingHandler() {
    return globalTrafficShapingHandler;
  }
}
