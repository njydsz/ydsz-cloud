package com.njydsz.message.server.channel.impl;

import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.njydsz.common.feign.MessageRequest;
import com.njydsz.common.feign.MessageResult;
import com.njydsz.common.netty.codec.LengthFieldCodec;
import com.njydsz.common.netty.config.NettyProperties;
import com.njydsz.common.netty.server.AbstractNettyServer;
import com.njydsz.common.util.id.SnowflakeIdGenerator;
import com.njydsz.common.json.YdszJson;
import com.njydsz.message.server.channel.MessageChannel;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.CharsetUtil;
import lombok.extern.slf4j.Slf4j;

/**
 * TCP 推送通道（基于 common-netty）。
 *
 * <p>P0-5: 引入 common-netty 的 {@link AbstractNettyServer} 作为 TCP 长连接推送通道。
 * 适用于移动端、IoT 设备等不适用 WebSocket 的场景。
 *
 * <p>协议格式：Length(4B) + Payload(JSON)
 * <ul>
 *   <li>粘包/半包：通过 {@link LengthFieldCodec} 一站式编解码解决</li>
 *   <li>消息编码：JSON（UTF-8）</li>
 *   <li>连接管理：通过 {@link ChannelGroupManager} 维护 userId → Channel 分组映射</li>
 *   <li>推送方式：单推（按 userId 查找分组）+ 广播（通过 ChannelGroupManager）</li>
 *   <li>空闲检测：通过 IdleStateHandler 自动触发，业务侧处理 {@link IdleStateEvent}</li>
 * </ul>
 *
 * <p>配置项 {@code ydsz.message.tcp-push.enabled=true} 启用，
 * 端口通过 {@code ydsz.message.tcp-push.port} 配置（默认 9123）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
@ConditionalOnClass(AbstractNettyServer.class)
@ConditionalOnProperty(prefix = "ydsz.message.tcp-push", name = "enabled", havingValue = "true")
public class TcpPushChannel extends AbstractNettyServer implements MessageChannel {

    /** 通道类型 */
    private static final String CHANNEL_TYPE = "PUSH";

    /** 用户分组前缀 */
    private static final String USER_GROUP_PREFIX = "user:";

    /** TCP 推送服务端口 */
    private final int pushPort;

    /** 分布式 ID 生成器 */
    private final SnowflakeIdGenerator snowflakeIdGenerator;

    /**
     * 构造 TCP 推送通道。
     *
     * @param properties            Netty 配置
     * @param snowflakeIdGenerator 分布式 ID 生成器
     */
    public TcpPushChannel(NettyProperties properties,
                          SnowflakeIdGenerator snowflakeIdGenerator) {
        super(9123, properties);
        this.pushPort = 9123;
        this.snowflakeIdGenerator = snowflakeIdGenerator;
    }

    @Override
    public String channelType() {
        return CHANNEL_TYPE;
    }

    @Override
    protected void initChannelPipeline(SocketChannel ch) {
        // 使用 LengthFieldCodec 一站式编解码（4B 长度 + Payload）
        LengthFieldCodec.addToPipeline(ch.pipeline());
        // 业务 Handler
        ch.pipeline().addLast(new TcpPushServerHandler(this));
    }

    @Override
    public MessageResult send(MessageRequest request) {
        if (request.getReceiver() == null || request.getReceiver().isBlank()) {
            return MessageResult.fail(CHANNEL_TYPE, "推送接收人不能为空");
        }
        String traceId = "PUSH-" + snowflakeIdGenerator.nextId();
        String userId = request.getReceiver();
        String groupKey = USER_GROUP_PREFIX + userId;

        // 通过 ChannelGroupManager 按用户分组推送
        if (channelGroupManager.groupSize(groupKey) == 0) {
            log.warn("[TCP-PUSH] 用户不在线,无法推送: userId={}", userId);
            return MessageResult.fail(CHANNEL_TYPE, "用户不在线: " + userId);
        }
        try {
            // 构建推送消息 JSON
            Map<String, Object> pushData = new HashMap<>(8);
            pushData.put("type", "PUSH");
            pushData.put("messageId", request.getMessageId());
            pushData.put("subject", request.getSubject());
            pushData.put("content", request.getContent());
            pushData.put("bizType", request.getBizType());
            pushData.put("bizId", request.getBizId());
            pushData.put("traceId", traceId);
            pushData.put("timestamp", System.currentTimeMillis());
            String json = YdszJson.toJson(pushData);
            ByteBuf buf = Unpooled.copiedBuffer(json, CharsetUtil.UTF_8);
            channelGroupManager.broadcastToGroup(groupKey, buf);
            log.info("[TCP-PUSH] 推送成功: userId={} traceId={} subject={}",
                    userId, traceId, request.getSubject());
            return MessageResult.ok(CHANNEL_TYPE, traceId);
        } catch (Exception e) {
            log.error("[TCP-PUSH] 推送异常: userId={} err={}", userId, e.getMessage(), e);
            return MessageResult.fail(CHANNEL_TYPE, "推送异常: " + e.getMessage());
        }
    }

    /**
     * 注册用户连接。
     *
     * <p>将 Channel 加入用户分组，用于后续定向推送。
     *
     * @param userId  用户 ID
     * @param channel Netty Channel
     */
    void registerUser(String userId, Channel channel) {
        channelGroupManager.addToGroup(USER_GROUP_PREFIX + userId, channel);
        log.info("[TCP-PUSH] 用户连接注册: userId={} channelId={} online={}",
                userId, channel.id(), channelGroupManager.globalSize());
    }

    /**
     * 获取在线用户数。
     *
     * @return 在线用户数（全局活跃 Channel 数）
     */
    public int getOnlineCount() {
        return channelGroupManager.globalSize();
    }

    /**
     * 获取推送端口。
     *
     * @return 端口号
     */
    public int getPushPort() {
        return pushPort;
    }

    /**
     * TCP 推送服务端 Handler。
     *
     * <p>处理客户端连接/断开、空闲检测、认证消息。
     * 空闲检测由 IdleStateHandler 自动触发，通过 {@link #userEventTriggered} 处理。
     */
    @Slf4j
    static class TcpPushServerHandler extends ChannelInboundHandlerAdapter {

        private final TcpPushChannel server;
        private String userId;

        TcpPushServerHandler(TcpPushChannel server) {
            this.server = server;
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            server.channelGroupManager.add(ctx.channel());
            log.debug("[TCP-PUSH] 新连接: remote={}", ctx.channel().remoteAddress());
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            // ChannelGroupManager.remove 会自动从全局组和业务分组移除，并清理空分组
            server.channelGroupManager.remove(ctx.channel());
            log.debug("[TCP-PUSH] 连接断开: remote={}", ctx.channel().remoteAddress());
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            if (!(msg instanceof ByteBuf buf)) {
                return;
            }
            String json = buf.toString(CharsetUtil.UTF_8);
            try {
                Map<String, Object> data = YdszJson.parseMap(json);
                String type = (String) data.get("type");
                if ("AUTH".equals(type)) {
                    // 认证消息：注册 userId
                    this.userId = (String) data.get("userId");
                    if (userId != null) {
                        server.registerUser(userId, ctx.channel());
                        // 回复认证成功
                        Map<String, Object> ack = new HashMap<>(2);
                        ack.put("type", "AUTH_ACK");
                        ack.put("success", true);
                        ctx.writeAndFlush(Unpooled.copiedBuffer(
                                YdszJson.toJson(ack), CharsetUtil.UTF_8));
                    }
                } else {
                    log.debug("[TCP-PUSH] 收到业务消息: type={}", type);
                }
            } catch (Exception e) {
                log.warn("[TCP-PUSH] 消息解析失败: {}", e.getMessage(), e);
            }
        }

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
            if (evt instanceof IdleStateEvent event) {
                switch (event.state()) {
                    case READER_IDLE -> {
                        log.info("[TCP-PUSH] 读空闲超时,关闭连接: remote={}",
                                ctx.channel().remoteAddress());
                        ctx.close();
                    }
                    case WRITER_IDLE -> {
                        // 写空闲可选择发送心跳保活，此处仅记录
                        log.debug("[TCP-PUSH] 写空闲: remote={}", ctx.channel().remoteAddress());
                    }
                    case ALL_IDLE -> {
                        log.info("[TCP-PUSH] 读写空闲超时,关闭连接: remote={}",
                                ctx.channel().remoteAddress());
                        ctx.close();
                    }
                    default -> {
                        // ignore
                    }
                }
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            log.error("[TCP-PUSH] 连接异常: remote={} err={}",
                    ctx.channel().remoteAddress(), cause.getMessage());
            ctx.close();
        }
    }
}
