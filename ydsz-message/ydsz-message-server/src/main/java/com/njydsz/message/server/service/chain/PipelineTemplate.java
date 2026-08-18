package com.njydsz.message.server.service.chain;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.njydsz.message.server.service.chain.handler.ChannelResolveHandler;
import com.njydsz.message.server.service.chain.handler.DedupHandler;
import com.njydsz.message.server.service.chain.handler.RouteRuleHandler;
import com.njydsz.message.server.service.chain.handler.SuppressionHandler;
import com.njydsz.message.server.service.chain.handler.ThrottlingHandler;
import com.njydsz.message.server.service.chain.handler.UserPreferenceHandler;

/**
 * 管线模板枚举：预定义不同发送场景的 Handler 组合。
 *
 * <p>不同场景对处理链的要求不同，通过模板按需组合 Handler，避免全量执行带来的性能开销。
 *
 * <p><b>模板分类：</b>
 *
 * <ul>
 *   <li>{@link #FULL_PROCESS}：全量处理（默认兜底），包含所有 Handler
 *   <li>{@link #TEMPLATE_SEND}：模板发送场景，包含路由、偏好、去重、限流
 *   <li>{@link #SIMPLE_SEND}：简单直发场景，仅通道校验 + 限流
 *   <li>{@link #BATCH_SEND}：批量发送场景，仅通道校验 + 限流（批量自身有去重）
 *   <li>{@link #INTERNAL_CALLBACK}：内部回调场景，仅通道校验（信任内部调用）
 * </ul>
 *
 * <p>使用方式：
 *
 * <pre>{@code
 *   PipelineTemplate template = facade.resolveTemplate(request);
 *   SendContext ctx = facade.execute(request, template);
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.2.0
 */
public enum PipelineTemplate {

  /**
   * 全量处理模板（默认兜底）。
   *
   * <p>包含所有 Handler，适用于无法明确分类的请求或需要完整校验的场景。 执行顺序：ChannelResolve → RouteRule → UserPreference → Dedup → Suppression → Throttling。
   */
  FULL_PROCESS(
      "full",
      Arrays.asList(
          ChannelResolveHandler.class,
          RouteRuleHandler.class,
          UserPreferenceHandler.class,
          DedupHandler.class,
          SuppressionHandler.class,
          ThrottlingHandler.class)),

  /**
   * 模板发送场景。
   *
   * <p>通过模板编码发送消息，需要路由规则匹配通道、用户偏好校验、去重、限流。 不含跨渠道抑制（模板发送通常是首次发送，不存在多渠道冲突）。
   */
  TEMPLATE_SEND(
      "template",
      Arrays.asList(
          ChannelResolveHandler.class,
          RouteRuleHandler.class,
          UserPreferenceHandler.class,
          DedupHandler.class,
          ThrottlingHandler.class)),

  /**
   * 简单直发场景。
   *
 * <p>直接指定通道和内容发送，无需路由、偏好校验、去重、抑制。 仅执行通道校验 + 限流，追求最小时延。
   */
  SIMPLE_SEND(
      "simple",
      Arrays.asList(
          ChannelResolveHandler.class,
          ThrottlingHandler.class)),

  /**
   * 批量发送场景。
   *
   * <p>批量接口自身已完成去重和抑制，管线侧仅做通道校验 + 限流。 批量场景下逐条执行去重/抑制反而成为瓶颈。
   */
  BATCH_SEND(
      "batch",
      Arrays.asList(
          ChannelResolveHandler.class,
          ThrottlingHandler.class)),

  /**
   * 内部回调场景。
   *
   * <p>内部系统回调（如回执处理、状态同步），信任内部调用源， 仅做最基本的通道校验，不做限流和去重。
   */
  INTERNAL_CALLBACK(
      "callback",
      Collections.singletonList(ChannelResolveHandler.class));

  /** 模板标识（与请求中的 scenario 字段对应） */
  private final String code;

  /** 该模板包含的 Handler 类型列表（有序） */
  private final List<Class<? extends SendHandler>> handlerClasses;

  PipelineTemplate(String code, List<Class<? extends SendHandler>> handlerClasses) {
    this.code = code;
    this.handlerClasses = handlerClasses;
  }

  public String getCode() {
    return code;
  }

  public List<Class<? extends SendHandler>> getHandlerClasses() {
    return handlerClasses;
  }

  /**
   * 根据 code 查找模板。
   *
   * @param code 模板标识
   * @return 匹配的模板，未找到返回 {@link #FULL_PROCESS}
   */
  public static PipelineTemplate fromCode(String code) {
    if (code == null || code.isEmpty()) {
      return FULL_PROCESS;
    }
    for (PipelineTemplate template : values()) {
      if (template.code.equalsIgnoreCase(code)) {
        return template;
      }
    }
    return FULL_PROCESS;
  }
}
