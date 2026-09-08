package com.njydsz.message.server.channel.impl;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.njydsz.common.feign.MessageRequest;
import com.njydsz.common.feign.MessageResult;
import com.njydsz.common.json.YdszJson;
import com.njydsz.common.notify.channel.EmailMessage;
import com.njydsz.common.notify.channel.EmailNotifySender;
import com.njydsz.common.util.id.SnowflakeIdGenerator;
import com.njydsz.message.server.channel.MessageChannel;

/**
 * 邮件通道实现。
 *
 * <p>委托 ydsz-common-notify 的 {@link EmailNotifySender} 发送邮件，
 * 复用企业级特性（SMTP 健康检查、XSS 清洗、DKIM 签名、发送指标、追踪像素）。
 * 自动识别 HTML（内容含 {@code <}）或纯文本格式。
 *
 * <p><b>P2-14 增强保留：</b>
 *
 * <ul>
 *   <li>HTML 邮件注入追踪像素（已读回执）</li>
 *   <li>支持附件：通过 channelMeta.attachments 传入（Base64 编码）</li>
 *   <li>支持内嵌图片：通过 channelMeta.inlineImages 传入</li>
 *   <li>注入 List-Unsubscribe 头（退订支持）</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Component
public class EmailChannel implements MessageChannel {

  /** 通道类型 */
  private static final String CHANNEL_TYPE = "EMAIL";

  /** 邮件通知发送器（统一由 ydsz-common-notify 提供） */
  private final EmailNotifySender emailNotifySender;

  /** 分布式 ID 生成器 */
  private final SnowflakeIdGenerator snowflakeIdGenerator;

  /**
   * 构造方法。
   *
   * @param emailNotifySender 邮件通知发送器
   * @param snowflakeIdGenerator 分布式 ID 生成器，用于构建 traceId
   */
  public EmailChannel(
      EmailNotifySender emailNotifySender,
      SnowflakeIdGenerator snowflakeIdGenerator) {
    this.emailNotifySender = emailNotifySender;
    this.snowflakeIdGenerator = snowflakeIdGenerator;
  }

  /**
   * 通道类型。
   *
   * @return EMAIL
   */
  @Override
  public String channelType() {
    return CHANNEL_TYPE;
  }

  /**
   * 发送邮件，自动识别 HTML / 纯文本格式。
   *
   * <p>委托 {@link EmailNotifySender#sendEmail(EmailMessage)} 执行实际发送，
   * 自动复用 common-notify 的 SMTP 健康检查、XSS 清洗、DKIM 签名等能力。
   *
   * @param request 消息请求
   * @return 发送结果（含供应商侧追踪 ID）
   */
  @Override
  public MessageResult send(MessageRequest request) {
    if (!emailNotifySender.isEnabled()) {
      return MessageResult.fail(CHANNEL_TYPE, null, "邮件通知未启用", "邮件通知未启用", null);
    }
    if (request.getReceiver() == null || request.getReceiver().isBlank()) {
      return MessageResult.fail(CHANNEL_TYPE, null, "收件人邮箱不能为空", "收件人邮箱不能为空", null);
    }
    try {
      String subject = request.getSubject() == null ? "YDSZ 通知" : request.getSubject();
      String content = request.getContent();
      boolean isHtml = content != null && content.contains("<");

      // 构建 EmailMessage（适配 common-notify 的消息协议）
      EmailMessage.Builder messageBuilder = EmailMessage.builder()
          .to(request.getReceiver())
          .subject(subject)
          .content(content)
          .html(isHtml);

      // P2-14: 解析附件/内嵌图片
      if (request.getChannelMeta() != null) {
        String attachmentsStr = request.getChannelMeta().get("attachments");
        if (StringUtils.hasText(attachmentsStr)) {
          messageBuilder.attachments(createAttachments(attachmentsStr));
        }
        String inlineStr = request.getChannelMeta().get("inlineImages");
        if (StringUtils.hasText(inlineStr)) {
          messageBuilder.inlineResources(createInlineResources(inlineStr));
        }
      }

      EmailMessage emailMessage = messageBuilder.build();
      var result = emailNotifySender.sendEmail(emailMessage);

      String traceId = CHANNEL_TYPE + "-" + snowflakeIdGenerator.nextId();
      if (result.isSuccess()) {
        log.info("[EMAIL] 发送成功: to={} subject={}", request.getReceiver(), subject);
        return MessageResult.ok(CHANNEL_TYPE, traceId);
      } else {
        log.warn("[EMAIL] 发送失败: to={}, reason={}", request.getReceiver(), result.getErrorMessage());
        return MessageResult.fail(CHANNEL_TYPE, traceId, result.getErrorMessage(),
            result.getErrorMessage(), null);
      }
    } catch (Exception e) {
      log.error("[EMAIL] 发送异常: to={} reason={}", request.getReceiver(), e.getMessage(), e);
      return MessageResult.fail(
          CHANNEL_TYPE, null, e.getClass().getSimpleName() + ": " + e.getMessage(),
          e.getClass().getSimpleName() + ": " + e.getMessage(), null);
    }
  }

  /**
   * P2-14: 创建附件列表。
   *
   * <p>attachments 格式为 JSON：[{"name":"file.pdf","data":"base64..."}, ...]
   *
   * @param attachmentsJson 附件 JSON
   * @return 附件列表
   */
  private List<EmailMessage.Attachment> createAttachments(String attachmentsJson) {
    try {
      var attachments = YdszJson.parseArrayNode(attachmentsJson);
      List<EmailMessage.Attachment> result = new ArrayList<>(attachments.size());
      for (int i = 0; i < attachments.size(); i++) {
        var item = attachments.getObjectNode(i);
        String name = item.getString("name");
        String data = item.getString("data");
        if (StringUtils.hasText(name) && StringUtils.hasText(data)) {
          byte[] bytes = Base64.getDecoder().decode(data);
          result.add(EmailMessage.Attachment.builder()
              .filename(name)
              .content(bytes)
              .build());
        }
      }
      return result;
    } catch (Exception e) {
      log.warn("[EMAIL] 附件解析失败: {}", e.getMessage(), e);
      return Collections.emptyList();
    }
  }

  /**
   * P2-14: 创建内嵌资源列表。
   *
   * <p>inlineImages 格式为 JSON：[{"cid":"logo","data":"base64..."}, ...]
   *
   * @param inlineJson 内嵌图片 JSON
   * @return 内嵌资源列表
   */
  private List<EmailMessage.InlineResource> createInlineResources(String inlineJson) {
    try {
      var images = YdszJson.parseArrayNode(inlineJson);
      List<EmailMessage.InlineResource> result = new ArrayList<>(images.size());
      for (int i = 0; i < images.size(); i++) {
        var item = images.getObjectNode(i);
        String cid = item.getString("cid");
        String data = item.getString("data");
        if (StringUtils.hasText(cid) && StringUtils.hasText(data)) {
          byte[] bytes = Base64.getDecoder().decode(data);
          result.add(EmailMessage.InlineResource.builder()
              .contentId(cid)
              .content(bytes)
              .build());
        }
      }
      return result;
    } catch (Exception e) {
      log.warn("[EMAIL] 内嵌图片解析失败: {}", e.getMessage(), e);
      return Collections.emptyList();
    }
  }
}
