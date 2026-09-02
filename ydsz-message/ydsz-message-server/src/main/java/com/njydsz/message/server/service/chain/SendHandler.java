package com.njydsz.message.server.service.chain;

import com.njydsz.common.feign.MessageRequest;

/**
 * 消息发送管线处理器接口。
 *
 * <p>每个 Handler 负责发送前的一个校验/处理步骤，按顺序串联成管线。 Handler 通过设置 {@link SendContext#setErrorResult} 触发管线短路。
 *
 * <p>对标 Spring Security Filter Chain / Netty Pipeline 模式， 支持运行时动态编排（通过配置调整 Handler 顺序或开关）。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public interface SendHandler {

  /**
   * 执行处理逻辑。
   *
   * <p>实现要求：
   *
   * <ul>
   *   <li>校验通过时返回 true，管线继续执行下一个 Handler
   *   <li>校验失败时设置 {@code ctx.setErrorResult(...)} 并返回 false，管线终止
   *   <li>不应抛出异常（fatal 异常由管线框架统一捕获并转换为错误结果）
   * </ul>
   *
   * @param request 原始消息请求
   * @param ctx 管线上下文（各 Handler 共享）
   * @return true 表示通过，false 表示短路
   */
  boolean handle(MessageRequest request, SendContext ctx);

  /**
   * Handler 执行顺序（升序，值越小越先执行）。
   *
   * @return 执行顺序
   */
  int order();

  /**
   * Handler 名称（用于监控与日志）。
   *
   * @return 可读名称
   */
  default String name() {
    return this.getClass().getSimpleName();
  }
}
