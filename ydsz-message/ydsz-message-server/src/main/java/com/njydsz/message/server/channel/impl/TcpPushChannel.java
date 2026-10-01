package com.njydsz.message.server.channel.impl;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.SocketChannel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.njydsz.message.domain.dto.MessageItemRequestDTO;
import com.njydsz.message.domain.vo.MessageSendResultVO;
import com.njydsz.common.json.YdszJson;
import com.njydsz.common.netty.codec.LengthFieldCodec;
import com.njydsz.common.netty.config.NettyProperties;
import com.njydsz.common.netty.handler.AbstractJsonTcpHandler;
import com.njydsz.common.netty.server.AbstractNettyServer;
import com.njydsz.common.util.id.SnowflakeIdGenerator;
import com.njydsz.common.locales.util.I18n;
import com.njydsz.message.server.channel.MessageChannel;

/**
 * TCP 推送通道（基于 common-netty）。
 *
 * <p>P0-5: 引入 common-netty 的 {@link AbstractNettyServer} 作为 TCP 长连接推送通道。 适用于移动端、IoT 设备等不适用
 * WebSocket 的场景。
 *
 * <p>协议格式：Length(4B) + Payload(JSON)
 *
 * <ul>
 *   <li>粘包/半包：通过 {@link LengthFieldCodec} 一站式编解码解决
 *   <li>消息编码：JSON（UTF-8）
 *   <li>连接管理：通过 {@link ChannelGroupManager} 维护 userId → Channel 分组映射
 *   <li>推送方式：单推（按 userId 查找分组）+ 广播（通过 ChannelGroupManager）
 *   <li>空闲检测：通过 IdleStateHandler 自动触发，业务侧处理 {@link IdleStateEvent}
 * </ul>
 *
 * <p>配置项 {@code ydsz.message.tcp-push.enabled=true} 启用， 端口通过 {@code ydsz.message.tcp-push.port}
 * 配置（默认 9123）。
 *
 * @author ydsz-team
 * @since 26.10.01
 */
@Slf4j
@Component
@ConditionalOnClass(AbstractNettyServer.class)
@ConditionalOnProperty(prefix = "ydsz.message.tcp-push", name = "enabled", havingValue = "true")
public class TcpPushChannel extends AbstractNettyServer implements MessageChannel {
  /** Map 初始容量 */
  private static final int MAP_CAPACITY_8 = 8;

  /** TCP 推送端口 */
  private static final int PUSH_PORT = 9123;


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
   *
   * @param properties Netty 配置属性（含 boss/worker 线程数、TCP keepAlive 等）
   * @param snowflakeIdGenerator 分布式 ID 生成器，用于构建推送 traceId
   */
  public TcpPushChannel(NettyProperties properties, SnowflakeIdGenerator snowflakeIdGenerator) {
    super(PUSH_PORT, properties);
    this.pushPort = PUSH_PORT;
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
  public MessageSendResultVO send(MessageItemRequestDTO request) {
    if (request.getReceiver() == null || request.getReceiver().isBlank()) {
      String msg = I18n.message("message.tcp.recipient_required");
      return MessageSendResultVO.fail(CHANNEL_TYPE, null, msg, msg, null);
    }
    String traceId = "PUSH-" + snowflakeIdGenerator.nextId();
    String userId = request.getReceiver();
    String groupKey = USER_GROUP_PREFIX + userId;

    // 通过 ChannelGroupManager 按用户分组推送
    if (channelGroupManager.groupSize(groupKey) == 0) {
      log.warn("[TCP-PUSH] 用户不在线,无法推送: userId={}", userId);
      String offlineMsg = I18n.message("message.tcp.user_offline");
      offlineMsg = offlineMsg.replace("{0}", userId);
      return MessageSendResultVO.fail(CHANNEL_TYPE, null, offlineMsg, offlineMsg, null);
    }
    try {
      // 构建推送消息 JSON
      Map<String, Object> pushData = new HashMap<>(MAP_CAPACITY_8);
      pushData.put("type", "PUSH");
      pushData.put("messageId", request.getMessageId());
      pushData.put("subject", request.getSubject());
      pushData.put("content", request.getContent());
      pushData.put("bizType", request.getBizType());
      pushData.put("bizId", request.getBizId());
      pushData.put("traceId", traceId);
      pushData.put("timestamp", System.currentTimeMillis());
      String json = YdszJson.toJson(pushData);
      ByteBuf buf = Unpooled.copiedBuffer(json, StandardCharsets.UTF_8);
      channelGroupManager.broadcastToGroup(groupKey, buf);
      log.info(
          "[TCP-PUSH] 推送成功: userId={} traceId={} subject={}",
          userId,
          traceId,
          request.getSubject());
      return MessageSendResultVO.ok(CHANNEL_TYPE, traceId);
    } catch (Exception e) {
      log.error("[TCP-PUSH] 推送异常: userId={} err={}", userId, e.getMessage(), e);
      String exMsg = I18n.message("message.tcp.push_error");
      exMsg = exMsg.replace("{0}", e.getMessage());
      return MessageSendResultVO.fail(CHANNEL_TYPE, null, exMsg, exMsg, null);
    }
  }

  /**
   * 注册用户连接。
   *
   * <p>将 Channel 加入用户分组，用于后续定向推送。
   *
   * @param userId 用户 ID
   * @param channel Netty Channel
   */
  void registerUser(String userId, Channel channel) {
    channelGroupManager.addToGroup(USER_GROUP_PREFIX + userId, channel);
    log.info(
        "[TCP-PUSH] 用户连接注册: userId={} channelId={} online={}",
        userId,
        channel.id(),
        channelGroupManager.globalSize());
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
   * TCP 推送服务端 Handler（基于 {@link AbstractJsonTcpHandler} 基类）。
   *
   * <p>封装 ByteBuf → UTF-8 → JSON 解析、空闲超时关闭、异常关闭等通用逻辑。 子类仅需实现认证注册和业务消息路由。
   */
  static class TcpPushServerHandler extends AbstractJsonTcpHandler {

    private final TcpPushChannel server;
    private String userId;

    TcpPushServerHandler(TcpPushChannel server) {
      this.server = server;
    }

    @Override
    protected String getLogPrefix() {
      return "[TCP-PUSH]";
    }

    @Override
    protected void onJsonMessage(ChannelHandlerContext ctx, Map<String, Object> message) {
      String type = (String) message.get("type");
      if (AbstractJsonTcpHandler.TYPE_AUTH.equals(type)) {
        handleAuth(ctx, message);
      } else {
        log.debug("[TCP-PUSH] 收到业务消息: type={}", type);
      }
    }

    @Override
    protected void onAuthenticated(ChannelHandlerContext ctx, String userId) {
      this.userId = userId;
      server.registerUser(userId, ctx.channel());
      // 激活 Session 管理：更新 bizId 使 sessionRepository 可查询到此会话
      server.getSessionRepository().find(s -> s.getChannel().equals(ctx.channel()))
          .forEach(s -> server.getSessionRepository().updateBizId(s.getSessionId(), userId));
    }
  }
}
