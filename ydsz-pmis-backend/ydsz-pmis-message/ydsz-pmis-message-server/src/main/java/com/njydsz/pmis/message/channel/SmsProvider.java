package com.njydsz.pmis.message.server.channel.sms;

import com.njydsz.pmis.common.feign.MessageRequest;
import com.njydsz.pmis.common.feign.MessageResult;
import com.njydsz.pmis.message.domain.entity.template.MsgTemplateDO;

import java.util.List;

/**
 * 短信服务商 SPI 接口。
 *
 * <p>不同服务商（阿里云 / 腾讯云 / Mock 降级）实现此接口，
 * 由 {@link com.njydsz.pmis.message.server.channel.impl.SmsChannel} 根据
 * {@code pmis.message.sms.provider} 配置选择实际 provider。
 *
 * <p>provider 通过 {@link MessageRequest#getChannelMeta()} 获取渠道元数据
 * （signName / providerKey 等），无需重复查询模板表。
 *
 * <p>P0-4 增强：支持批量发送（{@link #batchSend}）和回执查询（{@link #queryReceipt}）。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
public interface SmsProvider {

    /**
     * 服务商标识（如 aliyun / tencent / mock）。
     *
     * @return 服务商标识
     */
    String providerType();

    /**
     * 发送短信。
     *
     * @param request  消息请求（receiver=手机号, params=模板变量, channelMeta=签名/模板Code）
     * @param template 模板实体（含 providerKey=服务商模板ID, signName=签名），可为 null
     * @return 发送结果（含 providerTraceId）
     */
    MessageResult send(MessageRequest request, MsgTemplateDO template);

    /**
     * P0-4: 批量发送短信（同一模板 + 多个手机号）。
     *
     * <p>阿里云 {@code SendBatchSms} 接口支持单次最多 100 个手机号。
     * provider 实现应按上限分批调用，返回每条结果。
     *
     * @param requests 消息请求列表（每条 receiver 为一个手机号）
     * @param template 模板实体
     * @return 发送结果列表（与 requests 一一对应）
     */
    default List<MessageResult> batchSend(List<MessageRequest> requests, MsgTemplateDO template) {
        // 默认实现：逐条发送，provider 可覆盖为原生批量接口
        return requests.stream()
                .map(req -> send(req, template))
                .toList();
    }

    /**
     * P0-4: 查询短信发送回执。
     *
     * <p>通过阿里云 {@code QuerySendDetails} 接口查询指定消息的送达状态。
     *
     * @param providerTraceId 供应商追踪 ID（如 ALIYUN-BizId）
     * @param phone           手机号
     * @return 回执结果：状态（DELIVERED/FAILED/UNKNOWN）+ 错误码 + 错误描述
     */
    default MessageResult queryReceipt(String providerTraceId, String phone) {
        return MessageResult.fail("SMS", "当前 provider 未实现回执查询");
    }
}
