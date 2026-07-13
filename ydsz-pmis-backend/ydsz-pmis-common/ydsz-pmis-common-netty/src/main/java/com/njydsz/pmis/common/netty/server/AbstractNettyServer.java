package com.njydsz.pmis.common.netty.server;

import com.njydsz.pmis.common.netty.config.NettyProperties;
import com.njydsz.pmis.common.netty.handler.ChannelGroupManager;
import com.njydsz.pmis.common.netty.handler.IdleStateHandlerFactory;
import com.njydsz.pmis.common.netty.pool.NettyEventLoopPool;
import com.njydsz.pmis.common.netty.ssl.SslContextFactory;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.handler.traffic.ChannelTrafficShapingHandler;
import lombok.extern.slf4j.Slf4j;

/**
 * Netty TCP Server 抽象基类。
 *
 * <p>封装 Server 的启动、Pipeline 初始化、SSL/TLS、空闲检测、流量整形、优雅停机等通用逻辑。
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

    protected final int port;
    protected final NettyProperties properties;
    protected final ChannelGroupManager channelGroupManager = new ChannelGroupManager();

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;

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
        bossGroup = NettyEventLoopPool.acquireBossGroup(properties.getBossThreads());
        workerGroup = NettyEventLoopPool.acquireWorkerGroup(properties.getWorkerThreads());

        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .option(ChannelOption.SO_BACKLOG, properties.getSoBacklog())
                .childOption(ChannelOption.SO_KEEPALIVE, properties.isSoKeepAlive())
                .childOption(ChannelOption.TCP_NODELAY, properties.isTcpNoDelay())
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ChannelPipeline pipeline = ch.pipeline();

                        // SSL/TLS
                        if (properties.getSsl().isEnabled()) {
                            SslContext sslContext = SslContextFactory.createServerContext(
                                    properties.getSsl().getKeyStore(),
                                    properties.getSsl().getKeyStorePassword(),
                                    properties.getSsl().getKeyStoreType(),
                                    properties.getSsl().getTrustStore(),
                                    properties.getSsl().getTrustStorePassword(),
                                    properties.getSsl().getTrustStoreType(),
                                    properties.getSsl().isNeedClientAuth());
                            pipeline.addLast("ssl", sslContext.newHandler(ch.alloc()));
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

                        // 注册到 ChannelGroup
                        channelGroupManager.add(ch);
                    }
                });

        serverChannel = bootstrap.bind(port).sync().channel();
        log.info("[Netty-Server] {} 启动成功, 监听端口={}", getClass().getSimpleName(), port);
    }

    /**
     * 停止 TCP Server（优雅关闭）。
     */
    public void stop() {
        log.info("[Netty-Server] {} 正在关闭...", getClass().getSimpleName());
        if (serverChannel != null) {
            serverChannel.close();
        }
        NettyEventLoopPool.releaseBossGroup();
        NettyEventLoopPool.releaseWorkerGroup();
        log.info("[Netty-Server] {} 已关闭", getClass().getSimpleName());
    }

    /**
     * 子类实现：初始化 Channel Pipeline（添加业务 Handler）。
     *
     * <p>注意：SSL、空闲检测、流量整形等通用 Handler 已由父类自动添加，
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
     * 判断 Server 是否已启动。
     *
     * @return true 表示已启动
     */
    public boolean isRunning() {
        return serverChannel != null && serverChannel.isActive();
    }
}
