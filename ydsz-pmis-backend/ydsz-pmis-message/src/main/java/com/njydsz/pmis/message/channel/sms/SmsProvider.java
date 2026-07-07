package com.njydsz.pmis.message.channel.sms;

import com.njydsz.pmis.common.feign.MessageRequest;
import com.njydsz.pmis.common.feign.MessageResult;
import com.njydsz.pmis.message.entity.MsgTemplateDO;

/**
 * 短信服务商 SPI 接口。
 *
 * <p>不同服务商（阿里云 / 腾讯云 / Mock 降级）实现此接口，
 * 由 {@link com.njydsz.pmis.message.channel.impl.SmsChannel} 根据
 * {@code pmis.message.sms.provider} 配置选择实际 provider。
 *
 * <p>provider 通过 {@link MessageRequest#getChannelMeta()} 获取渠道元数据
 * （signName / providerKey 等），无需重复查询模板表。
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
}
