package com.njydsz.pmis.common.netty.client;

import com.njydsz.pmis.common.netty.config.NettyProperties;
import com.njydsz.pmis.common.netty.handler.IdleStateHandlerFactory;
import com.njydsz.pmis.common.netty.pool.NettyEventLoopPool;
import com.njydsz.pmis.common.netty.ssl.SslContextFactory;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.handler.traffic.ChannelTrafficShapingHandler;
import lombok.extern.slf4j.Slf4j;


/**
 * Netty TCP Client 抽象基类。
 *
 * <p>封装 Client 的连接、Pipeline 初始化、SSL/TLS、空闲检测、流量整形、
 * 断线重连等通用逻辑。子类只需实现 {@link #initChannelPipeline(SocketChannel)} 方法。
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
    private volatile boolean connecting = false;

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
     * @throws InterruptedException 连接被中断
     */
    public void connect() throws InterruptedException {
        if (connecting || (channel != null && channel.isActive())) {
            return;
        }
        connecting = true;
        workerGroup = NettyEventLoopPool.acquireWorkerGroup(properties.getWorkerThreads());

        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(workerGroup)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.SO_KEEPALIVE, properties.isSoKeepAlive())
                .option(ChannelOption.TCP_NODELAY, properties.isTcpNoDelay())
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, properties.getConnectTimeoutMillis())
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ChannelPipeline pipeline = ch.pipeline();

                        // SSL/TLS
                        if (properties.getSsl().isEnabled()) {
                            SslContext sslContext = SslContextFactory.createClientContext(
                                    properties.getSsl().getTrustStore(),
                                    properties.getSsl().getTrustStorePassword(),
                                    properties.getSsl().getTrustStoreType());
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

                        // 子类自定义 Pipeline
                        initChannelPipeline(ch);

                        // 断线重连
                        if (properties.getReconnect().isEnabled()) {
                            pipeline.addLast("reconnect", createReconnectHandler());
                        }
                    }
                });

        ChannelFuture future = bootstrap.connect(host, port).sync();
        channel = future.channel();
        connecting = false;
        log.info("[Netty-Client] {} 连接成功: {}:{}",
                getClass().getSimpleName(), host, port);
    }

    /**
     * 断开连接。
     */
    public void disconnect() {
        log.info("[Netty-Client] {} 正在断开...", getClass().getSimpleName());
        if (channel != null) {
            channel.close();
            channel = null;
        }
        NettyEventLoopPool.releaseWorkerGroup();
    }

    /**
     * 发送消息。
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
                try {
                    connect();
                } catch (Exception e) {
                    log.warn("[Netty-Client] 重连失败: {}", e.getMessage());
                    scheduleReconnect();
                }
            }
        };
    }
}
