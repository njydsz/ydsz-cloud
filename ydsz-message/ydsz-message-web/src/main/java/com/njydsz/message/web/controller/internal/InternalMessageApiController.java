package com.njydsz.message.web.controller.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.base.api.ApiVersion;
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.feign.MessageRequest;
import com.njydsz.common.feign.MessageResult;
import com.njydsz.common.safe.idempotent.annotation.Idempotent;
import com.njydsz.common.safe.ratelimit.annotation.RateLimit;
import com.njydsz.message.domain.dto.MessageSendDTO;
import com.njydsz.message.server.service.core.MessageService;

/**
 * 内部 API Controller（服务间 Feign 调用）
 *
 * <p>为 <b>跨服务 Feign 调用</b> 提供统一 HTTP 入口。端点<b>仅用于服务间通信</b>，不应直接对外暴露。
 *
 * <p><b>接口路径：</b>{@code /api/internal/**}
 *
 * <p><b>安全要求：</b>
 *
 * <ul>
 *   <li>Gateway 应限制 {@code /api/internal/**} 仅允许<b>内部服务 IP</b>调用（白名单），对公网不可访问
 *   <li>消息接收人、模板变量等敏感参数通过 <b>POST body</b> 传输，<b>严禁</b>出现在 URL 中
 *   <li>发送接口启用 {@link RateLimit} 接口级限流（200 QPS）+ {@link Idempotent} 幂等保护（5 秒），
 *       避免重试风暴与重复发送
 * </ul>
 *
 * <p><b>响应契约：</b>返回 {@link YdszResponse} 包装，与 {@code ydsz-message-api} 模块中 {@code
 * MessageSendClient} 的 Feign 声明严格对齐。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see com.njydsz.message.api.client.MessageSendClient Feign Client 接口
 */
@Slf4j
@ApiVersion("26.09.01")
@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
@AuthApiPermission(apiCodes = "message:internal:api")
public class InternalMessageApiController {

  private final MessageService messageService;

  /**
   * 发送多通道消息（邮件 / 短信 / Webhook / 站内信等）。
   *
   * <p>为跨服务 Feign 调用提供统一 HTTP 入口（{@code POST /api/internal/message/send}），端点仅用于服务间通信，不应直接对外暴露。
   * 启用接口级限流（200 QPS）+ 幂等保护（5 秒，按 receiver + templateCode 维度），避免重试风暴与重复发送。
   * 敏感参数（接收人、模板变量）通过 POST body 传输，严禁出现在 URL 中。
   *
   * @param dto 消息发送 DTO（含 channel / receiver / templateCode / content 等字段）
   * @return 成功时返回 traceId；失败时返回错误码 + 用户可读的错误信息
   * @throws com.njydsz.common.core.exception.BusinessException 消息发送失败（通道不可用 / 限流 / 供应商异常等）
   */
  @RateLimit(resource = "message.internalapi.sendMessage", threshold = 200)
  @Idempotent(
      key =
          "'ydsz:message:internal-api:send-message:' + #dto.receiver + ':' + #dto.templateCode",
      ttlSeconds = 5)
  @PostMapping("/message/send")
  public YdszResponse<String> sendMessage(@RequestBody MessageSendDTO dto) {
    MessageRequest request = new MessageRequest();
    BeanUtils.copyProperties(dto, request);
    MessageResult result = messageService.send(request);
    if (result.isSuccess()) {
      return YdszResponse.success(result.getTraceId());
    }
    return YdszResponse.error(result.getErrorCode(), result.getUserMessage());
  }
}
