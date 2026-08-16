package com.njydsz.message.server.service.chain;

import java.time.LocalDateTime;

import lombok.Data;

import com.njydsz.common.feign.MessageResult;
import com.njydsz.message.domain.entity.config.MsgPreference;
import com.njydsz.message.domain.entity.config.MsgRouteRule;

/**
 * 消息发送管线上下文（贯穿整个发送链路）。
 *
 * <p>承载预处理阶段各个 Handler 产生的中间状态：最终通道、接收人、模板编码、 路由规则匹配结果、用户偏好等。任一 Handler 校验失败时设置 {@link
 * #errorResult}， 管线短路退出。
 *
 * @author ydsz-team
 * @since 1.1.0
 */
@Data
public class SendContext {

  /** 最终通道（路由/灰度可能覆盖原始通道） */
  private String channel;

  /** 最终接收人（通道绑定解析后） */
  private String receiver;

  /** 业务类型 */
  private String bizType;

  /** 最终模板编码（灰度可能覆盖原始模板） */
  private String templateCode;

  /** 消息优先级 */
  private String priority;

  /** 路由规则匹配结果 */
  private MsgRouteRule matchedRule;

  /** 用户偏好（DND/locale/digest） */
  private MsgPreference preference;

  /** 灰度命中标志 */
  private int canaryFlag;

  /** 灰度 key（用于日志） */
  private String canaryKeyForLog;

  /** 去重 key */
  private String dedupKey;

  /** 发送时间（DND 可能被延迟到指定时间） */
  private LocalDateTime scheduledAt;

  /** 非 null 表示预处理失败，调用方应直接返回此结果 */
  private MessageResult errorResult;
}
