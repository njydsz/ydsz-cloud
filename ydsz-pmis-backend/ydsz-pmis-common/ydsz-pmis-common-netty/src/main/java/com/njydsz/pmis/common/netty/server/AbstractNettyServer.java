package com.njydsz.pmis.common.netty.server;

import java.net.BindException;
import com.njydsz.pmis.common.netty.config.NettyProperties;
import com.njydsz.pmis.common.netty.handler.ChannelGroupManager;
import com.njydsz.pmis.common.netty.handler.ConnectionEventHandler;
import com.njydsz.pmis.common.netty.handler.IdleStateHandlerFactory;
import com.njydsz.pmis.common.netty.handler.TrafficMonitoringHandler;
import com.njydsz.pmis.common.netty.metric.NettyChannelMetrics;
import com.njydsz.pmis.common.netty.pool.NettyEventLoopPool;
import com.njydsz.pmis.common.netty.ssl.SslContextFactory;
import com.njydsz.pmis.common.netty.transport.NativeTransportDetector;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.handler.traffic.ChannelTrafficShapingHandler;
import io.netty.handler.traffic.GlobalTrafficShapingHandler;
import io.netty.util.internal.logging.InternalLoggerFactory;
import io.netty.util.internal.logging.Slf4JLoggerFactory;
import lombok.extern.slf4j.Slf4j;

/**
 * Netty TCP Server 抽象基类。
 *
 * <p>封装 Server 的启动、Pipeline 初始化、SSL/TLS、空闲检测、流量整形、
 * 指标监控、优雅停机等通用逻辑。
 * 子类只需实现 {@link #initChannelPipeline(SocketChannel)} 方法，添加业务 Handler。
 *
 * <p>生命周期通过 {@link NettyServerLifecycle} 与 Spring 容器集成，
 * 随容器启动自动绑定端口，容器关闭时优雅释放资源。
 *
 * <p>使用示例：
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
 * @author ydsz-pmis-team
 * @since 1.3.0
 */
@Slf4j
public abstract class AbstractNettyServer {

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

    /**
     * 构造 Netty TCP Server。
     *
     * @param port       监听端口
     * @param properties Netty 配置
     */
    protected AbstractNettyServer(int port, NettyProperties properties) {
        this.port = port;
        this.properties = properties;
    }

    /**
     * 启动 TCP Server。
     *
     * @throws InterruptedException 启动被中断
     */
    public void start() throws InterruptedException {
        NettyEventLoopPool pool = getEventLoopPool();

        if (properties.isSharedEventLoop()) {
            bossGroup = pool.acquireBossGroup(properties.getBossThreads());
            workerGroup = pool.acquireWorkerGroup(properties.getWorkerThreads());
        } else {
            bossGroup = pool.createIsolatedBossGroup(properties.getBossThreads());
            workerGroup = pool.createIsolatedWorkerGroup(properties.getWorkerThreads());
        }

        // SSL Context 一次性创建（避免每连接重建）
        if (properties.getSsl().isEnabled()) {
            sslContext = SslContextFactory.createServerContext(
                    properties.getSsl().getKeyStore(),
                    properties.getSsl().getKeyStorePassword(),
                    properties.getSsl().getKeyStoreType(),
                    properties.getSsl().getTrustStore(),
                    properties.getSsl().getTrustStorePassword(),
                    properties.getSsl().getTrustStoreType(),
                    properties.getSsl().isNeedClientAuth());
        }

        // 全局流量整形（限制整个 Server 总带宽）
        if (properties.getTrafficShaping().isEnabled() && properties.getTrafficShaping().isGlobal()) {
            globalTrafficShapingHandler = new GlobalTrafficShapingHandler(
                    workerGroup,
                    properties.getTrafficShaping().getWriteLimit(),
                    properties.getTrafficShaping().getReadLimit(),
                    properties.getTrafficShaping().getCheckIntervalMs());
        }

        // 可复用的监控 Handler（@Sharable）
        TrafficMonitoringHandler trafficHandler =
                metrics != null ? new TrafficMonitoringHandler(metrics) : null;
        ConnectionEventHandler connectionHandler =
                metrics != null ? new ConnectionEventHandler(metrics) : null;

        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(bossGroup, workerGroup)
                .channel(NativeTransportDetector.getServerSocketChannelClass(
                        pool.getTransportType()))
                .option(ChannelOption.SO_BACKLOG, properties.getSoBacklog())
                .childOption(ChannelOption.SO_KEEPALIVE, properties.isSoKeepAlive())
                .childOption(ChannelOption.TCP_NODELAY, properties.isTcpNoDelay())
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ChannelPipeline pipeline = ch.pipeline();

                        // SSL/TLS（复用已创建的 SslContext）
                        if (sslContext != null) {
                            pipeline.addLast("ssl", sslContext.newHandler(ch.alloc()));
                        }

                        // 全局流量整形
                        if (globalTrafficShapingHandler != null) {
                            pipeline.addLast("globalTraffic", globalTrafficShapingHandler);
                        }

                        // 空闲检测
                        IdleStateHandlerFactory idleFactory = new IdleStateHandlerFactory(
                                properties.getIdle().getReaderIdleSeconds(),
                                properties.getIdle().getWriterIdleSeconds(),
                                properties.getIdle().getAllIdleSeconds());
                        pipeline.addLast("idleState", idleFactory.create());

                        // Per-Channel 流量整形（非全局模式时）
                        if (properties.getTrafficShaping().isEnabled()
                                && !properties.getTrafficShaping().isGlobal()) {
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
                        initChannelPipeline(ch);

                        // 指标监控 — 连接事件统计
                        if (connectionHandler != null) {
                            pipeline.addLast("connectionEvent", connectionHandler);
                        }

                        // 注册到 ChannelGroup
                        channelGroupManager.add(ch);
                    }
                });

        // 端口绑定（P2-6: 友好错误提示）
        try {
            serverChannel = bootstrap.bind(port).sync().channel();
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause instanceof BindException) {
                log.error("[Netty-Server] {} 端口 {} 已被占用，请检查配置或释放占用进程",
                        getClass().getSimpleName(), port);
            }
            throw e;
        }
        log.info("[Netty-Server] {} 启动成功, 监听端口={}, ssl={}, trafficShaping={}",
                getClass().getSimpleName(), port,
                properties.getSsl().isEnabled(),
                properties.getTrafficShaping().isEnabled());
    }

    /**
     * 停止 TCP Server（优雅关闭，等待在途消息处理完成）。
     */
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
     * 子类实现：初始化 Channel Pipeline（添加业务 Handler）。
     *
     * <p>注意：SSL、空闲检测、流量整形、指标监控等通用 Handler 已由父类自动添加，
     * 子类只需添加业务编解码器和处理器。
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
     * @return 端口
     */
    public int getPort() {
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
            eventLoopPool = new NettyEventLoopPool(
                    properties.getShutdownQuietPeriodSeconds(),
                    properties.getShutdownTimeoutSeconds(),
                    properties.getNativeTransport());
        }
        return eventLoopPool;
    }
}
