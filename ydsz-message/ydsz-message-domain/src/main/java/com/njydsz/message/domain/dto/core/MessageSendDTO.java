package com.njydsz.message.domain.dto.core;

import com.njydsz.common.safe.annotation.Xss;
import java.util.Map;
import lombok.Data;

/**
 * 消息直接发送 DTO — 业务方调用消息中心的单条发送入口
 *
 * <p>支持两种发送模式：
 *
 * <ul>
 *   <li><b>模板发送</b>：指定 {@code templateCode} + {@code params}，由模板引擎渲染最终内容
 *   <li><b>直接发送</b>：指定 {@code content} 直接发送原始内容（不走模板，适合动态内容场景）
 * </ul>
 *
 * <p>发送后由 {@code MessageService.send()} 统一走渠道路由 → 限流 → 发送 → 回执 → 重试 全链路。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see com.njydsz.message.domain.enums.core.MessageChannelEnum 消息通道枚举
 */
@Data
public class MessageSendDTO {

  /** 通道 */
  @Xss private String channel;

  /** 模板编码 */
  @Xss private String templateCode;

  /** 接收人 */
  @Xss private String receiver;

  /** 模板参数(用于占位符渲染) */
  private Map<String, Object> params;

  /** 直接发送的内容(不走模板) */
  private String content;

  /** 邮件主题(仅 EMAIL) */
  @Xss private String subject;

  /** 业务类型 */
  @Xss private String bizType;

  /** 业务单据 ID */
  @Xss private String bizId;

  /** 发送优先级 */
  @Xss private String priority;

  /** 消息唯一标识(用于幂等去重) */
  @Xss private String messageId;

  /** 触发发送的用户 ID */
  @Xss private String senderId;

  /** 聚合组 */
  @Xss private String messageGroup;

  /** 语言区域 */
  @Xss private String locale;
}
