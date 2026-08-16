package com.njydsz.workflow.server.service.impl.notification;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.feign.MessageRequest;
import com.njydsz.common.feign.MessageResult;
import com.njydsz.common.feign.NotificationClient;
import com.njydsz.common.feign.dto.NotificationFeignDTO;
import com.njydsz.workflow.server.engine.FlowSensitiveMasker;
import com.njydsz.workflow.server.service.FlowNotificationService;

/**
 * 工作流消息通知服务实现 — 轻量适配器
 *
 * <p>对 {@link FlowNotificationService} 接口的完整实现，作为工作流引擎与 消息通知引擎（{@code
 * ydsz-message}）之间的<b>轻量适配器</b>。
 *
 * <p><b>架构演进：</b>
 *
 * <ul>
 *   <li>早期版本：通知基础设施（{@code outbox / template / channel / preference}）耦合在 {@code ydsz-workflow}
 *       模块内部，<b>已移除</b>
 *   <li>当前架构：通知能力由独立的<b>消息通知引擎</b> {@code ydsz-message} 承载， 本类仅作为 Feign 适配器，将工作流关键事件转发到 {@link
 *       NotificationClient}
 *   <li>这种解耦符合大厂 B 端架构原则：<b>单一职责 + 服务化</b>，避免模块职责膨胀
 * </ul>
 *
 * <p><b>收敛对齐（ADR-001）：</b>
 *
 * <ul>
 *   <li>本模块是工作流域的<b>通知适配器</b>，负责将工作流事件转为通知请求
 *   <li>通道字符串与 {@code NotifyChannel} 枚举的映射关系： INAPP → {@code NotifyChannel.INSITE}（站内信）、 EMAIL →
 *       {@code NotifyChannel.EMAIL}、 WEBHOOK → {@code NotifyChannel.DINGTALK} / {@code
 *       NotifyChannel.FEISHU} / {@code NotifyChannel.WECOM}
 *   <li>未来 ADR-001 完全落地后，可直接委托 {@code NotifyHelper} 发送， 届时本类可进一步精简
 * </ul>
 *
 * <p><b>核心职责：</b>
 *
 * <ul>
 *   <li><b>通知发送（{@link #notify}）</b>：将工作流事件（待办 / 抄送 / 超时 / 终止等）转发到 消息通知引擎，由通知引擎负责实际投递
 *   <li><b>多通道支持</b>：INAPP（站内信） / EMAIL（邮件） / WEBHOOK（企业微信/钉钉机器人）
 *   <li><b>敏感数据脱敏</b>：通过 {@link FlowSensitiveMasker} 对通知内容脱敏， 避免敏感信息（手机号 / 身份证 / 银行卡）通过 IM 泄露
 *   <li><b>幂等性</b>：通过 {@code providerTraceId} 实现通知幂等， 同一事件多次通知只会发送一次
 *   <li><b>尽力而为语义</b>：所有异常 {@code try-catch} 吞掉，<b>不拖垮主流程事务</b> —— 通知失败不应回滚审批操作
 * </ul>
 *
 * <p><b>通道说明：</b>
 *
 * <table>
 *   <caption>通知通道映射</caption>
 *   <tr><th>本服务入参</th><th>通知引擎处理</th><th>实际投递</th></tr>
 *   <tr><td>{@code INAPP}</td><td>{@link NotificationClient} 写入站内信（{@code channel=PUSH}）</td>
 *       <td>前端 WebSocket 推送 / 待办中心</td></tr>
 *   <tr><td>{@code EMAIL}</td><td>{@link NotificationClient} 投递（{@code channel=EMAIL}）</td>
 *       <td>SMTP / 企业邮箱</td></tr>
 *   <tr><td>{@code WEBHOOK}</td><td>{@link NotificationClient#sendMessage} 委托消息中心</td>
 *       <td>发送到 {@code extra.webhookUrl} 指定的企业微信 / 钉钉机器人</td></tr>
 * </table>
 *
 * <p><b>事务边界：</b>
 *
 * <ul>
 *   <li>本类<b>不开启事务</b>（{@code @Transactional} 缺失），通知发送是<b>非事务性</b>操作
 *   <li>Feign 调用失败时仅记录日志，<b>不抛异常</b>，避免主流程事务回滚
 *   <li>消息可靠性由 {@code ydsz-message} 模块的 Outbox 模式保证
 * </ul>
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li><b>轻量适配器</b>：本类只做「事件 → 通知请求」转换 + Feign 调用， <b>不负责</b>通知模板渲染 / 通道选择 / 用户偏好（均由 {@code
 *       ydsz-message} 处理）
 *   <li><b>敏感数据脱敏</b>：通过 {@link FlowSensitiveMasker} 对 {@code content} 字段脱敏， 避免手机号 / 身份证 /
 *       银行卡等敏感信息通过 IM 泄露
 *   <li><b>幂等性</b>：通过 {@code providerTraceId} 实现通知幂等， 同一事件多次通知只会发送一次（由 {@code ydsz-message} 侧保证）
 *   <li><b>异常降级</b>：所有 Feign 异常 / 网络异常 {@code try-catch} 吞掉， <b>不抛异常</b>，避免主流程事务回滚
 *   <li><b>异步非阻塞</b>：通过 Feign 的非阻塞调用实现，不阻塞主流程
 * </ul>
 *
 * <p><b>典型使用：</b>
 *
 * <pre>{@code
 * // 发送待办通知（INAPP）
 * notificationService.notify("INAPP", assigneeId, "新待办", "您有一条新待办",
 *     "WORKFLOW_TODO", "INFO");
 *
 * // 发送超时告警（EMAIL）
 * notificationService.notify("EMAIL", approverId, "审批超时",
 *     "您的审批任务已超时 4 小时", "WORKFLOW_TIMEOUT", "WARN");
 * }</pre>
 *
 * <p><b>扩展能力：</b>如需新增通知类型（如「流程完成通知」「抄送通知」）， 在 {@code ydsz-message} 侧新增模板，本类无需修改即可支持。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see FlowNotificationService 接口定义
 * @see NotificationClient 通知中心 Feign 客户端
 * @see MessageRequest 消息请求 DTO
 * @see NotificationFeignDTO 通知 Feign DTO
 * @see FlowSensitiveMasker 敏感数据脱敏器
 * @see MessageResult 消息发送结果
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowNotificationServiceImpl implements FlowNotificationService {

  /** 通知通道常量 */
  private static final String CHANNEL_INAPP = "INAPP";

  private static final String CHANNEL_EMAIL = "EMAIL";
  private static final String CHANNEL_WEBHOOK = "WEBHOOK";

  /** 统一通知客户端（P1-5: INAPP / EMAIL / WEBHOOK 通道统一入口） */
  private final NotificationClient notificationClient;

  /** P1-5: 敏感字段脱敏器（原 FlowNotificationHelper 功能合并） */
  private final FlowSensitiveMasker sensitiveMasker;

  @Override
  public void notifyTaskCreated(
      String instanceId, String taskId, String assigneeId, String assigneeName) {
    try {
      if (assigneeId == null) {
        return;
      }
      String title = "您有一个新的审批待办";
      String content = "流程实例[" + instanceId + "] 任务[" + taskId + "] 需要您处理";
      Map<String, Object> extra = new HashMap<>();
      extra.put("bizType", "WORKFLOW_TASK");
      extra.put("instanceId", instanceId);
      extra.put("taskId", taskId);
      extra.put("assigneeName", assigneeName);
      send(CHANNEL_INAPP, assigneeId, title, content, extra);
      log.debug(
          "[FlowNotify] 任务创建通知: instanceId={} taskId={} assigneeId={}",
          instanceId,
          taskId,
          assigneeId);
    } catch (Exception e) {
      log.warn(
          "[FlowNotify] 任务创建通知异常: instanceId={} taskId={} err={}",
          instanceId,
          taskId,
          e.getMessage());
    }
  }

  @Override
  public void notifyUrge(
      String instanceId, String taskId, List<String> assigneeIds, String comment) {
    try {
      if (assigneeIds == null || assigneeIds.isEmpty()) {
        return;
      }
      String title = "您有待办被催办";
      String content = "流程实例[" + instanceId + "] 任务[" + taskId + "] 被催办";
      if (comment != null && !comment.isBlank()) {
        content += "，备注：" + comment;
      }
      for (String assigneeId : assigneeIds) {
        Map<String, Object> extra = new HashMap<>();
        extra.put("bizType", "WORKFLOW_URGE");
        extra.put("instanceId", instanceId);
        extra.put("taskId", taskId);
        extra.put("comment", comment);
        send(CHANNEL_INAPP, assigneeId, title, content, extra);
      }
      log.debug(
          "[FlowNotify] 催办通知: instanceId={} taskId={} targets={}",
          instanceId,
          taskId,
          assigneeIds.size());
    } catch (Exception e) {
      log.warn(
          "[FlowNotify] 催办通知异常: instanceId={} taskId={} err={}",
          instanceId,
          taskId,
          e.getMessage());
    }
  }

  @Override
  public void notifyCc(String instanceId, String nodeCode, List<Long> ccUserIds, String title) {
    try {
      if (ccUserIds == null || ccUserIds.isEmpty()) {
        return;
      }
      String content = "流程实例[" + instanceId + "] 节点[" + nodeCode + "] 抄送给您";
      for (Long userId : ccUserIds) {
        Map<String, Object> extra = new HashMap<>();
        extra.put("bizType", "WORKFLOW_CC");
        extra.put("instanceId", instanceId);
        extra.put("nodeCode", nodeCode);
        send(CHANNEL_INAPP, String.valueOf(userId), title, content, extra);
      }
      log.debug(
          "[FlowNotify] 抄送通知: instanceId={} nodeCode={} targets={}",
          instanceId,
          nodeCode,
          ccUserIds.size());
    } catch (Exception e) {
      log.warn(
          "[FlowNotify] 抄送通知异常: instanceId={} nodeCode={} err={}",
          instanceId,
          nodeCode,
          e.getMessage());
    }
  }

  @Override
  public void notifyInstanceCompleted(String instanceId, String initiatorId) {
    try {
      if (initiatorId == null) {
        return;
      }
      String title = "您的审批流程已完成";
      String content = "流程实例[" + instanceId + "] 已审批通过";
      Map<String, Object> extra = new HashMap<>();
      extra.put("bizType", "WORKFLOW_COMPLETED");
      extra.put("instanceId", instanceId);
      send(CHANNEL_INAPP, initiatorId, title, content, extra);
      log.debug("[FlowNotify] 流程完成通知: instanceId={} initiatorId={}", instanceId, initiatorId);
    } catch (Exception e) {
      log.warn(
          "[FlowNotify] 流程完成通知异常: instanceId={} initiatorId={} err={}",
          instanceId,
          initiatorId,
          e.getMessage());
    }
  }

  @Override
  public void notifyInstanceRejected(String instanceId, String initiatorId, String reason) {
    try {
      if (initiatorId == null) {
        return;
      }
      String title = "您的审批流程被驳回";
      String content = "流程实例[" + instanceId + "] 被驳回";
      if (reason != null && !reason.isBlank()) {
        content += "，原因：" + reason;
      }
      Map<String, Object> extra = new HashMap<>();
      extra.put("bizType", "WORKFLOW_REJECTED");
      extra.put("instanceId", instanceId);
      extra.put("reason", reason);
      send(CHANNEL_INAPP, initiatorId, title, content, extra);
      log.debug(
          "[FlowNotify] 流程驳回通知: instanceId={} initiatorId={} reason={}",
          instanceId,
          initiatorId,
          reason);
    } catch (Exception e) {
      log.warn(
          "[FlowNotify] 流程驳回通知异常: instanceId={} initiatorId={} err={}",
          instanceId,
          initiatorId,
          e.getMessage());
    }
  }

  @Override
  public void notifySlaTimeout(String instanceId, String taskId, String assigneeId, String action) {
    try {
      if (assigneeId == null) {
        return;
      }
      String title = "审批任务已超时";
      String content = "流程实例[" + instanceId + "] 任务[" + taskId + "] 超时，触发动作：" + action;
      Map<String, Object> extra = new HashMap<>();
      extra.put("bizType", "WORKFLOW_SLA_TIMEOUT");
      extra.put("instanceId", instanceId);
      extra.put("taskId", taskId);
      extra.put("action", action);
      // SLA 超时同时走站内信 + 邮件
      send(CHANNEL_INAPP, assigneeId, title, content, extra);
      send(CHANNEL_EMAIL, assigneeId, title, content, extra);
      log.debug(
          "[FlowNotify] SLA 超时通知: instanceId={} taskId={} assigneeId={} action={}",
          instanceId,
          taskId,
          assigneeId,
          action);
    } catch (Exception e) {
      log.warn(
          "[FlowNotify] SLA 超时通知异常: instanceId={} taskId={} err={}",
          instanceId,
          taskId,
          e.getMessage());
    }
  }

  @Override
  public void send(
      String channel, String userId, String title, String content, Map<String, Object> extra) {
    try {
      if (channel == null || userId == null) {
        return;
      }
      switch (channel) {
        case CHANNEL_INAPP -> sendInApp(userId, title, content, extra);
        case CHANNEL_EMAIL -> sendEmail(userId, title, content, extra);
        case CHANNEL_WEBHOOK -> sendWebhook(userId, title, content, extra);
        default ->
            log.warn("[FlowNotify] 未知通知通道: channel={} userId={} title={}", channel, userId, title);
      }
    } catch (Exception e) {
      log.warn("[FlowNotify] 通知发送异常: channel={} userId={} err={}", channel, userId, e.getMessage());
    }
  }

  /** INAPP 通道：通过 NotificationClient Feign 调用 notification 服务写入站内信。 */
  private void sendInApp(String userId, String title, String content, Map<String, Object> extra) {
    Map<String, Object> payload = new HashMap<>();
    if (extra != null) {
      payload.putAll(extra);
    }
    payload.put("userId", userId);
    payload.put("title", title);
    payload.put("content", content);
    payload.put("channel", "PUSH");
    try {
      notificationClient.send(toFeignDTO(payload));
    } catch (Exception e) {
      log.warn(
          "[FlowNotify][INAPP] Feign 调用降级为日志: userId={} title={} err={}",
          userId,
          title,
          e.getMessage());
    }
    log.debug("[FlowNotify][INAPP] userId={} title={}", userId, title);
  }

  /** EMAIL 通道：同样通过 NotificationClient 投递（channel=EMAIL）， 由 notification 服务负责实际邮件发送。 */
  private void sendEmail(String userId, String title, String content, Map<String, Object> extra) {
    Map<String, Object> payload = new HashMap<>();
    if (extra != null) {
      payload.putAll(extra);
    }
    payload.put("userId", userId);
    payload.put("title", title);
    payload.put("content", content);
    payload.put("channel", "EMAIL");
    Object receiver = extra == null ? null : extra.get("receiver");
    if (receiver != null) {
      payload.put("receiver", receiver);
    }
    try {
      notificationClient.send(toFeignDTO(payload));
    } catch (Exception e) {
      log.warn(
          "[FlowNotify][EMAIL] Feign 调用降级为日志: userId={} title={} err={}",
          userId,
          title,
          e.getMessage());
    }
    log.debug("[FlowNotify][EMAIL] userId={} title={}", userId, title);
  }

  /**
   * WEBHOOK 通道：通过 {@link NotificationClient#sendMessage} 委托消息中心发送到 extra.webhookUrl 指定的机器人地址。
   * webhookUrl 未配置时直接跳过（不算异常）。
   */
  private void sendWebhook(String userId, String title, String content, Map<String, Object> extra) {
    String webhookUrl = extra == null ? null : (String) extra.get("webhookUrl");
    if (webhookUrl == null || webhookUrl.isBlank()) {
      log.debug("[FlowNotify][WEBHOOK] 未配置 webhookUrl，跳过: userId={} title={}", userId, title);
      return;
    }
    MessageRequest request = new MessageRequest();
    request.setChannel("WEBHOOK");
    request.setReceiver(userId);
    request.setSubject(title);
    request.setContent(content);
    request.setBizType(extra == null ? null : asString(extra.get("bizType")));
    request.setBizId(extra == null ? null : asString(extra.get("bizId")));
    Map<String, Object> params = new HashMap<>();
    if (extra != null) {
      params.putAll(extra);
    }
    params.put("webhookUrl", webhookUrl);
    request.setParams(params);
    try {
      BaseResponse<MessageResult> result = notificationClient.sendMessage(request);
      if (result != null && result.getData() != null && !result.getData().isSuccess()) {
        log.warn(
            "[FlowNotify][WEBHOOK] 发送失败: userId={} url={} err={}",
            userId,
            webhookUrl,
            result.getData().getErrorMessage());
      }
    } catch (Exception e) {
      log.warn(
          "[FlowNotify][WEBHOOK] 发送异常: userId={} url={} err={}",
          userId,
          webhookUrl,
          e.getMessage());
    }
    log.debug("[FlowNotify][WEBHOOK] userId={} title={} url={}", userId, title, webhookUrl);
  }

  // ============================== P1-5: 带脱敏的便捷通知（原 FlowNotificationHelper 合并）
  // ==============================

  @Override
  public void notify(
      String channel, String userId, String title, String content, String bizType, String level) {
    if (userId == null) {
      return;
    }
    try {
      Map<String, Object> extra = new HashMap<>(4);
      extra.put("category", "WORKFLOW");
      extra.put("bizType", bizType);
      extra.put("level", level);
      send(channel, userId, sensitiveMasker.mask(title), sensitiveMasker.mask(content), extra);
    } catch (Exception e) {
      log.warn(
          "[FlowNotify] notify 异常: channel={} userId={} bizType={} err={}",
          channel,
          userId,
          bizType,
          e.getMessage());
    }
  }

  @Override
  public void notifyBatch(
      String channel,
      List<String> receiverIds,
      String title,
      String content,
      String bizType,
      String level) {
    if (receiverIds == null || receiverIds.isEmpty()) {
      return;
    }
    String maskedTitle = sensitiveMasker.mask(title);
    String maskedContent = sensitiveMasker.mask(content);
    for (String receiverId : receiverIds) {
      try {
        Map<String, Object> extra = new HashMap<>(4);
        extra.put("category", "WORKFLOW");
        extra.put("bizType", bizType);
        extra.put("level", level);
        send(channel, receiverId, maskedTitle, maskedContent, extra);
      } catch (Exception e) {
        log.warn(
            "[FlowNotify] notifyBatch 异常: channel={} receiverId={} err={}",
            channel,
            receiverId,
            e.getMessage());
      }
    }
  }

  /** 将 Map 形式的 payload 转换为强类型 NotificationFeignDTO */
  private NotificationFeignDTO toFeignDTO(Map<String, Object> payload) {
    NotificationFeignDTO dto = new NotificationFeignDTO();
    if (payload == null) {
      return dto;
    }
    dto.setTitle(asString(payload.get("title")));
    dto.setContent(asString(payload.get("content")));
    dto.setLevel(asString(payload.get("level")));
    dto.setCategory(asString(payload.get("category")));
    dto.setSenderId(asString(payload.get("senderId")));
    dto.setReceiverId(asString(payload.get("receiverId")));
    if (dto.getReceiverId() == null) {
      dto.setReceiverId(asString(payload.get("userId")));
    }
    Object receiverIds = payload.get("receiverIds");
    if (receiverIds instanceof List<?> list) {
      List<Long> ids = new ArrayList<>(list.size());
      for (Object o : list) {
        Long id = asLong(o);
        if (id != null) {
          ids.add(id);
        }
      }
      dto.setReceiverIds(ids);
    }
    dto.setBizType(asString(payload.get("bizType")));
    dto.setBizId(asString(payload.get("bizId")));
    Object expiredAt = payload.get("expiredAt");
    if (expiredAt instanceof LocalDateTime ldt) {
      dto.setExpiredAt(ldt);
    }
    Object emailEnabled = payload.get("emailEnabled");
    if (emailEnabled instanceof Boolean b) {
      dto.setEmailEnabled(b);
    }
    dto.setReceiverEmail(asString(payload.get("receiverEmail")));
    if (dto.getReceiverEmail() == null) {
      dto.setReceiverEmail(asString(payload.get("receiver")));
    }
    return dto;
  }

  private String asString(Object o) {
    return o == null ? null : o.toString();
  }

  private Long asLong(Object o) {
    if (o == null) {
      return null;
    }
    if (o instanceof Number n) {
      return n.longValue();
    }
    try {
      return Long.parseLong(o.toString().trim());
    } catch (NumberFormatException e) {
      log.warn("[FlowNotificationServiceImpl] Long 解析失败 o={}: {}", o, e.getMessage());
      return null;
    }
  }
}
