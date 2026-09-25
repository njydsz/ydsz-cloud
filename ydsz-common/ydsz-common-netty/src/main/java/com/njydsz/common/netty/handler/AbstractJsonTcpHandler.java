package com.njydsz.common.netty.handler;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.timeout.IdleStateEvent;
import lombok.extern.slf4j.Slf4j;

import com.njydsz.common.json.YdszJson;

/**
 * JSON 协议 TCP 推送服务端 Handler 抽象基类（P1-1 公共能力下沉）。
 *
 * <p>封装以下通用逻辑，消除业务模块中 TCP 推送 Handler 的重复实现：
 *
 * <ul>
 *   <li>ByteBuf → UTF-8 字符串 → JSON 解析 → {@code Map<String, Object>} 转换</li>
 *   <li>连接生命周期日志（新连接 / 断开）</li>
 *   <li>空闲超时关闭（READER_IDLE / ALL_IDLE → {@code ctx.close()}）</li>
 *   <li>异常关闭（{@code exceptionCaught} → {@code ctx.close()}）</li>
 * </ul>
 *
 * <p><b>适用场景：</b>仅处理 JSON 格式消息、{@code Length(4B) + Payload(JSON)} 帧协议的 TCP 推送服务端。
 * 若自定义二进制/Protobuf 协议，请使用 {@link ChannelInboundHandlerAdapter} 从头实现。
 *
 * <p><b>使用示例：</b>
 *
 * <pre>{@code
 * @Slf4j
 * static class MyPushHandler extends AbstractJsonTcpHandler {
 *
 *   private final MyPushServer server;
 *   private String userId;
 *
 *   MyPushHandler(MyPushServer server) { this.server = server; }
 *
 *   @Override
 *   protected void onJsonMessage(ChannelHandlerContext ctx, Map<String, Object> message) {
 *     String type = (String) message.get("type");
 *     if ("AUTH".equals(type)) {
 *       handleAuth(ctx, message);  // 调用基类提供的认证方法
 *     } else {
 *       log.debug("未知消息类型: {}", type);
 *     }
 *   }
 *
 *   @Override
 *   protected void onAuthenticated(ChannelHandlerContext ctx, String userId) {
 *     this.userId = userId;
 *     server.registerUser(userId, ctx.channel());
 *   }
 *
 *   @Override
 *   protected String getLogPrefix() { return "[MyPush]"; }
 * }
 * }</pre>
 *
 * <p><b>迁移建议：</b>现有 TCP 推送 Handler（如 {@code TcpPushServerHandler}、{@code NextwikiPushServerHandler}）
 * 在下一个迭代中可考虑重构为本类的子类，以减少与 Netty 原生 API 的直接耦合。
 *
 * @author ydsz-team
 * @since 26.09.25
 * @see ChannelInboundHandlerAdapter
 * @see ChannelGroupManager
 */
@Slf4j
public abstract class AbstractJsonTcpHandler extends ChannelInboundHandlerAdapter {

  /** JSON 字段 "type" 键名 */
  protected static final String FIELD_TYPE = "type";

  /** JSON 字段 "userId" 键名 */
  protected static final String FIELD_USER_ID = "userId";

  /** AUTH 认证消息类型标识 */
  protected static final String TYPE_AUTH = "AUTH";

  /** AUTH_ACK 认证响应消息类型标识 */
  protected static final String TYPE_AUTH_ACK = "AUTH_ACK";

  /**
   * 处理已解析的 JSON 消息。
   *
   * <p>子类实现此方法完成业务特定的消息路由（如 AUTH / 订阅 / 心跳响应等）。
   *
   * @param ctx     Channel 上下文
   * @param message 已解析为 Map 的消息体
   */
  protected abstract void onJsonMessage(
      ChannelHandlerContext ctx, Map<String, Object> message);

  /**
   * 获取日志前缀。
   *
   * <p>子类返回模块标识，统一日志格式。示例：{@code "[NextWiki-PUSH]"}。
   *
   * @return 日志前缀字符串
   */
  protected abstract String getLogPrefix();

  /**
   * 处理用户认证成功后的注册逻辑。
   *
   * <p>子类实现此方法将 userId 与 Channel 绑定（如加入分组管理器等）。
   *
   * @param ctx    Channel 上下文
   * @param userId 已认证的用户 ID
   */
  protected void onAuthenticated(ChannelHandlerContext ctx, String userId) {
    // 默认空实现，子类按需覆写
  }

  /**
   * 当新连接建立时的回调。
   *
   * <p>子类可覆写以添加自定义逻辑（如连接限流、白名单校验等）。
   * 默认仅记录 debug 日志。
   *
   * @param ctx Channel 上下文
   */
  protected void onChannelActive(ChannelHandlerContext ctx) {
    log.debug("{} connected: remote={}", getLogPrefix(), ctx.channel().remoteAddress());
  }

  /**
   * 当连接断开时的回调。
   *
   * <p>子类可覆写以添加清理逻辑（如移除用户注册、通知其他服务等）。
   * 默认仅记录 debug 日志。
   *
   * @param ctx Channel 上下文
   */
  protected void onChannelInactive(ChannelHandlerContext ctx) {
    log.debug("{} disconnected: remote={}", getLogPrefix(), ctx.channel().remoteAddress());
  }

  /**
   * 处理 AUTH 认证消息并发送 AUTH_ACK 响应。
   *
   * <p>内置通用认证流程：
   * <ol>
   *   <li>从 message 中提取 {@code userId} 字段</li>
   *   <li>若非空则调用 {@link #onAuthenticated} 并发送 {@code AUTH_ACK(success=true)}</li>
   *   <li>若为空则发送 {@code AUTH_ACK(success=false, message="userId is required")}</li>
   * </ol>
   *
   * <p>如需自定义 token 校验等增强认证，子类可覆写本方法。
   *
   * @param ctx     Channel 上下文
   * @param message AUTH 消息体（应含 userId 字段）
   */
  protected void handleAuth(ChannelHandlerContext ctx, Map<String, Object> message) {
    String userId = message.get(FIELD_USER_ID) != null
        ? message.get(FIELD_USER_ID).toString() : null;
    if (userId != null && !userId.isBlank()) {
      onAuthenticated(ctx, userId);
      writeAuthAck(ctx, true, "ok");
      log.info("{} auth user={}, remote={}", getLogPrefix(), userId, ctx.channel().remoteAddress());
    } else {
      writeAuthAck(ctx, false, "userId is required");
      log.warn("{} auth failed: missing userId, remote={}", getLogPrefix(), ctx.channel().remoteAddress());
    }
  }

  /**
   * 发送 AUTH_ACK 认证响应。
   *
   * <p>子类如需自定义 AUTH_ACK 格式（如携带额外字段），可覆写本方法。
   *
   * @param ctx     Channel 上下文
   * @param success 认证是否成功
   * @param message 提示消息
   */
  protected void writeAuthAck(ChannelHandlerContext ctx, boolean success, String message) {
    Map<String, Object> ack = new HashMap<>(4);
    ack.put(FIELD_TYPE, TYPE_AUTH_ACK);
    ack.put("success", success);
    ack.put("message", message != null ? message : "");
    String json = YdszJson.toJson(ack);
    ctx.writeAndFlush(Unpooled.copiedBuffer(json, StandardCharsets.UTF_8));
  }

  /** {@inheritDoc} */
  @Override
  public final void channelActive(ChannelHandlerContext ctx) {
    onChannelActive(ctx);
  }

  /** {@inheritDoc} */
  @Override
  public final void channelInactive(ChannelHandlerContext ctx) {
    onChannelInactive(ctx);
  }

  /** {@inheritDoc} */
  @Override
  public final void channelRead(ChannelHandlerContext ctx, Object msg) {
    if (!(msg instanceof ByteBuf buf)) {
      return;
    }
    String json = buf.toString(StandardCharsets.UTF_8);
    buf.release();
    try {
      Map<String, Object> data = YdszJson.parseMap(json);
      if (data != null && !data.isEmpty()) {
        onJsonMessage(ctx, data);
      } else {
        log.debug("{} empty message, remote={}", getLogPrefix(), ctx.channel().remoteAddress());
      }
    } catch (Exception e) {
      log.warn("{} parse failed: {}", getLogPrefix(), e.getMessage());
    }
  }

  /** {@inheritDoc} */
  @Override
  public final void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
    if (evt instanceof IdleStateEvent event) {
      switch (event.state()) {
        case READER_IDLE -> {
          log.info("{} read idle timeout, closing: remote={}", getLogPrefix(), ctx.channel().remoteAddress());
          ctx.close();
        }
        case ALL_IDLE -> {
          log.info("{} all idle timeout, closing: remote={}", getLogPrefix(), ctx.channel().remoteAddress());
          ctx.close();
        }
        case WRITER_IDLE -> log.debug("{} write idle: remote={}", getLogPrefix(), ctx.channel().remoteAddress());
        default -> {
          // ignore
        }
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  public final void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
    log.error("{} exception: remote={}, err={}", getLogPrefix(), ctx.channel().remoteAddress(), cause.getMessage());
    ctx.close();
  }
}
