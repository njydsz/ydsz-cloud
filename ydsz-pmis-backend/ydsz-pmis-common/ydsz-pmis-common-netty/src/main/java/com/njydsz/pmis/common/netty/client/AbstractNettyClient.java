package com.njydsz.pmis.common.netty.client;

import java.util.concurrent.atomic.AtomicBoolean;

import com.njydsz.pmis.common.netty.config.NettyProperties;
import com.njydsz.pmis.common.netty.event.ChannelEventDispatcher;
import com.njydsz.pmis.common.netty.event.MessageDispatcher;
import com.njydsz.pmis.common.netty.handler.ConnectionEventHandler;
import com.njydsz.pmis.common.netty.handler.IdleStateHandlerFactory;
import com.njydsz.pmis.common.netty.handler.TrafficMonitoringHandler;
import com.njydsz.pmis.common.netty.metric.NettyChannelMetrics;
import com.njydsz.pmis.common.netty.pool.NettyEventLoopPool;
import com.njydsz.pmis.common.netty.ssl.SslContextFactory;
import com.njydsz.pmis.common.netty.transport.NativeTransportDetector;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.handler.traffic.ChannelTrafficShapingHandler;
import lombok.extern.slf4j.Slf4j;

/**
 * Netty TCP Client 抽象基类。
 *
 * <p>封装 Client 的连接、Pipeline 初始化、SSL/TLS、空闲检测、流量整形、
 * 断线重连、指标监控等通用逻辑。子类只需实现 {@link #initChannelPipeline(SocketChannel)} 方法。
 *
 * <p>内置指数退避断线重连机制，通过 {@link NettyProperties.Reconnect} 配置控制。
 *
 * @author ydsz-pmis-team
 * @since 1.3.0
 */
@Slf4j
public abstract class AbstractNettyClient {

    protected final String host;
    protected final int port;
    protected final NettyProperties properties;

    private EventLoopGroup workerGroup;
    private Channel channel;
    private final AtomicBoolean connecting = new AtomicBoolean(false);
    private SslContext sslContext;

    /** 可选依赖 — 指标收集器（由 NettyAutoConfiguration 通过 setter 注入） */
    private NettyChannelMetrics metrics;

    /** 可选依赖 — EventLoop 池（由 NettyAutoConfiguration 通过 setter 注入） */
    private NettyEventLoopPool eventLoopPool;

    /**
     * 构造 Netty TCP Client。
     *
     * @param host       目标主机
     * @param port       目标端口
     * @param properties Netty 配置
     */
    protected AbstractNettyClient(String host, int port, NettyProperties properties) {
        this.host = host;
        this.port = port;
        this.properties = properties;
    }

    /**
     * 连接远端服务器。
     *
     * <p>使用 CAS 保证连接操作的原子性，避免多线程同时触发重复连接。
     *
     * @throws InterruptedException 连接被中断
     */
    public void connect() throws InterruptedException {
        // P0-5: CAS 原子保护，避免并发重复连接
        if (!connecting.compareAndSet(false, true)) {
            return;
        }
        try {
            if (channel != null && channel.isActive()) {
                return;
            }

            NettyEventLoopPool pool = getEventLoopPool();
            if (properties.isSharedEventLoop()) {
                workerGroup = pool.acquireWorkerGroup(properties.getWorkerThreads());
            } else {
                workerGroup = pool.createIsolatedWorkerGroup(properties.getWorkerThreads());
            }

            // SSL Context 一次性创建（避免每连接重建）
            if (properties.getSsl().isEnabled() && sslContext == null) {
                sslContext = SslContextFactory.createClientContext(
                        properties.getSsl().getTrustStore(),
                        properties.getSsl().getTrustStorePassword(),
                        properties.getSsl().getTrustStoreType());
            }

            // 可复用的监控 Handler（@Sharable）
            TrafficMonitoringHandler trafficHandler =
                    metrics != null ? new TrafficMonitoringHandler(metrics) : null;
            ConnectionEventHandler connectionHandler =
                    metrics != null ? new ConnectionEventHandler(metrics) : null;

            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(workerGroup)
                    .channel(NativeTransportDetector.getSocketChannelClass(
                            pool.getTransportType()))
                    .option(ChannelOption.SO_KEEPALIVE, properties.isSoKeepAlive())
                    .option(ChannelOption.TCP_NODELAY, properties.isTcpNoDelay())
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, properties.getConnectTimeoutMillis())
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ChannelPipeline pipeline = ch.pipeline();

                            // SSL/TLS（复用已创建的 SslContext）
                            if (sslContext != null) {
                                pipeline.addLast("ssl", sslContext.newHandler(ch.alloc(), host, port));
                            }

                            // 空闲检测
                            IdleStateHandlerFactory idleFactory = new IdleStateHandlerFactory(
                                    properties.getIdle().getReaderIdleSeconds(),
                                    properties.getIdle().getWriterIdleSeconds(),
                                    properties.getIdle().getAllIdleSeconds());
                            pipeline.addLast("idleState", idleFactory.create());

                            // 流量整形
                            if (properties.getTrafficShaping().isEnabled()) {
                                pipeline.addLast("trafficShaping", new ChannelTrafficShapingHandler(
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

                            // 子类自定义 Pipeline
                            if (connectionHandler != null) {
                                pipeline.addLast("connectionEvent", connectionHandler);
                            }

                            if (channelEventDispatcher != null) {
                                pipeline.addLast("channelEventDispatcher", channelEventDispatcher);
                            }

                            // åç±»èªå®ä¹ Pipeline
                            initChannelPipeline(ch);

                            if (messageDispatcher != null) {
                                pipeline.addLast("messageDispatcher", messageDispatcher);
                            }

                            // æ­çº¿éè¿
                                pipeline.addLast("reconnect", createReconnectHandler());
                            }
                        }
                    });

            ChannelFuture future = bootstrap.connect(host, port).sync();
            channel = future.channel();
            log.info("[Netty-Client] {} 连接成功: {}:{}", getClass().getSimpleName(), host, port);
        } finally {
            connecting.set(false);
        }
    }

    /**
     * 断开连接。
     */
    public void disconnect() {
        log.info("[Netty-Client] {} 正在断开...", getClass().getSimpleName());
        if (channel != null) {
            try {
                channel.close().sync();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("[Netty-Client] Channel 关闭被中断");
            }
            channel = null;
        }
        NettyEventLoopPool pool = getEventLoopPool();
        if (properties.isSharedEventLoop()) {
            pool.releaseWorkerGroup();
        } else {
            pool.shutdownGroup(workerGroup);
        }
    }

    /**
     * 发送消息（异步）。
     *
     * @param message 消息对象
     * @return ChannelFuture
     */
    public ChannelFuture send(Object message) {
        if (channel == null || !channel.isActive()) {
            throw new IllegalStateException("Channel 未连接");
        }
        return channel.writeAndFlush(message);
    }

    /**
     * 同步发送消息（阻塞等待发送完成，带超时）。
     *
     * @param message  消息对象
     * @param timeoutMs 超时时间（毫秒）
     * @return true 表示发送成功
     * @throws InterruptedException 等待被中断
     */
    public boolean sendSync(Object message, long timeoutMs) throws InterruptedException {
        ChannelFuture future = send(message);
        return future.await(timeoutMs) && future.isSuccess();
    }

    /**
     * 判断是否已连接。
     *
     * @return true 表示已连接
     */
    public boolean isConnected() {
        return channel != null && channel.isActive();
    }

    /**
     * 子类实现：初始化 Channel Pipeline（添加业务 Handler）。
     *
     * @param ch SocketChannel
     */
    protected abstract void initChannelPipeline(SocketChannel ch);

    /**
     * 创建断线重连处理器（子类可覆写以自定义重连逻辑）。
     *
     * @return ReconnectHandler 实例
     */
    protected ReconnectHandler createReconnectHandler() {
        NettyProperties.Reconnect rc = properties.getReconnect();
        return new ReconnectHandler(rc.getInitialDelayMs(), rc.getMaxDelayMs(), rc.getMaxRetries()) {
            @Override
            protected void doReconnect() {
                if (metrics != null) {
                    metrics.incrementReconnectAttempts();
                }
                try {
                    connect();
                    if (isConnected() && metrics != null) {
                        metrics.incrementReconnectSuccesses();
                    }
                } catch (Exception e) {
                    log.warn("[Netty-Client] 重连失败: {}", e.getMessage());
                    scheduleReconnect();
                }
            }
        };
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
            eventLoopPool = new NettyEventLoopPool(
                    properties.getShutdownQuietPeriodSeconds(),
                    properties.getShutdownTimeoutSeconds(),
                    properties.getNativeTransport());
        }
        return eventLoopPool;
    }

    public void setChannelEventDispatcher(ChannelEventDispatcher channelEventDispatcher) {
        this.channelEventDispatcher = channelEventDispatcher;
    }

    public void setMessageDispatcher(MessageDispatcher messageDispatcher) {
        this.messageDispatcher = messageDispatcher;
    }
}
