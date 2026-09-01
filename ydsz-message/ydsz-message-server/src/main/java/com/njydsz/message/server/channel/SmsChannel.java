package com.njydsz.message.server.channel.impl;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.njydsz.common.feign.MessageRequest;
import com.njydsz.common.feign.MessageResult;
import com.njydsz.common.tenant.TenantContextHolder;
import com.njydsz.message.domain.dto.ReceiptResultDTO;
import com.njydsz.message.domain.enums.receipt.ReceiptStatusEnum;
import com.njydsz.message.domain.vo.MsgLogVO;
import com.njydsz.message.domain.vo.MsgTemplateVO;
import com.njydsz.message.server.channel.MessageChannel;
import com.njydsz.message.server.channel.sms.SmsProvider;
import com.njydsz.message.server.config.MessageProperties;
import com.njydsz.message.server.service.TemplateService;

/**
 * 短信通道门面（替换原 MockSmsChannel）。
 *
 * <p>实现 {@link MessageChannel} SPI，内部根据 {@code ydsz.message.sms.provider} 配置选择实际 {@link
 * SmsProvider}（aliyun / mock），无匹配时降级到 mock。
 *
 * <p>模板元数据（signName / providerKey）解析顺序：
 *
 * <ol>
 *   <li>优先从 {@link MessageRequest#getChannelMeta()} 获取（上游填充）
 *   <li>回退到 {@link TemplateService} 按 templateCode 查询模板
 * </ol>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Component
public class SmsChannel implements MessageChannel {

  private static final String CHANNEL_TYPE = "SMS";

  private final List<SmsProvider> providers;
  private final MessageProperties messageProperties;
  private final TemplateService templateService;

  public SmsChannel(
      List<SmsProvider> providers,
      MessageProperties messageProperties,
      TemplateService templateService) {
    this.providers = providers != null ? providers : List.of();
    this.messageProperties = messageProperties;
    this.templateService = templateService;
  }

  @Override
  public String channelType() {
    return CHANNEL_TYPE;
  }

  @Override
  public MessageResult send(MessageRequest request) {
    if (request.getReceiver() == null || request.getReceiver().isBlank()) {
      return MessageResult.fail(CHANNEL_TYPE, null, "接收人手机号不能为空", "接收人手机号不能为空", null);
    }
    SmsProvider provider = selectProvider();
    MsgTemplateVO template = resolveTemplate(request);
    MessageResult result = provider.send(request, template);
    log.info(
        "[SmsChannel] provider={} status={} phone={}",
        provider.providerType(),
        result.getStatus(),
        request.getReceiver());
    return result;
  }

  /**
   * P0-4: 批量发送短信（委托给 provider 的原生批量接口）。
   *
   * @param requests 消息请求列表
   * @return 发送结果列表
   */
  public List<MessageResult> batchSend(List<MessageRequest> requests) {
    if (requests == null || requests.isEmpty()) {
      return List.of();
    }
    SmsProvider provider = selectProvider();
    MsgTemplateVO template = resolveTemplate(requests.get(0));
    List<MessageResult> results = provider.batchSend(requests, template);
    log.info(
        "[SmsChannel] 批量发送: provider={} count={} success={}",
        provider.providerType(),
        requests.size(),
        results.stream().filter(MessageResult::isSuccess).count());
    return results;
  }

  /**
   * P0-4: 查询短信回执（委托给 provider）。
   *
   * @param logVO 消息日志 VO（含 providerTraceId 和 receiver 手机号，用于查询回执）
   * @return 短信回执结果（DELIVERED/FAILED）；无法查询时返回 Optional.empty()
   */
  @Override
  public Optional<ReceiptResultDTO> queryReceipt(MsgLogVO logVO) {
    SmsProvider provider = selectProvider();
    if (!"aliyun".equals(provider.providerType())) {
      return Optional.empty();
    }
    String traceId = logVO.getProviderTraceId();
    String phone = logVO.getReceiver();
    if (!StringUtils.hasText(traceId) || !StringUtils.hasText(phone)) {
      return Optional.empty();
    }
    MessageResult result = provider.queryReceipt(traceId, phone);
    if ("SUCCESS".equals(result.getStatus())) {
      return Optional.of(ReceiptResultDTO.of(ReceiptStatusEnum.DELIVERED, traceId));
    } else if ("FAILED".equals(result.getStatus())) {
      return Optional.of(ReceiptResultDTO.of(ReceiptStatusEnum.FAILED, result.getUserMessage()));
    }
    // UNKNOWN 状态不更新回执,返回 empty
    return Optional.empty();
  }

  /**
   * 根据配置选择 provider，无匹配时降级到 mock。
   *
   * @return 短信服务商
   */
  private SmsProvider selectProvider() {
    String target =
        messageProperties.getSms() != null
                && StringUtils.hasText(messageProperties.getSms().getProvider())
            ? messageProperties.getSms().getProvider()
            : "mock";
    return providers.stream()
        .filter(p -> target.equalsIgnoreCase(p.providerType()))
        .findFirst()
        .orElseGet(
            () ->
                providers.stream()
                    .filter(p -> "mock".equalsIgnoreCase(p.providerType()))
                    .findFirst()
                    .orElseThrow(
                        () ->
                            new IllegalStateException("无可用 SMS provider，请检查 SmsProvider Bean 注册")));
  }

  /**
   * 解析模板元数据：优先从 channelMeta 获取，回退到 TemplateService 查询。
   *
   * @param request 消息请求
   * @return 模板实体（含 signName / providerKey），均无时返回 null
   */
  private MsgTemplateVO resolveTemplate(MessageRequest request) {
    Map<String, String> meta = request.getChannelMeta();
    if (meta != null
        && (StringUtils.hasText(meta.get("signName"))
            || StringUtils.hasText(meta.get("providerKey")))) {
      MsgTemplateVO t = new MsgTemplateVO();
      if (StringUtils.hasText(meta.get("signName"))) {
        t.setSignName(meta.get("signName"));
      }
      if (StringUtils.hasText(meta.get("providerKey"))) {
        t.setProviderKey(meta.get("providerKey"));
      }
      return t;
    }
    if (templateService != null && StringUtils.hasText(request.getTemplateCode())) {
      try {
        MsgTemplateVO vo =
            templateService.loadByCodeAndChannel(
                request.getTemplateCode(),
                CHANNEL_TYPE,
                null,
                TenantContextHolder.getTenantId());
        if (vo == null) {
          return null;
        }
        MsgTemplateVO templateVO = new MsgTemplateVO();
        templateVO.setSignName(vo.getSignName());
        templateVO.setProviderKey(vo.getProviderKey());
        return templateVO;
      } catch (Exception e) {
        log.debug(
            "[SmsChannel] 模板查询失败,忽略: code={} err={}", request.getTemplateCode(), e.getMessage());
      }
    }
    return null;
  }
}
