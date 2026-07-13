package com.njydsz.pmis.message.server.channel.push;

import java.util.List;

import com.njydsz.pmis.common.feign.MessageRequest;
import com.njydsz.pmis.common.feign.MessageResult;
import com.njydsz.pmis.message.domain.entity.template.MsgTemplateDO;

/**
 * APP 推送服务商 SPI 接口。
 *
 * <p>不同服务商（个推 / 极光 / Mock 降级）实现此接口，
 * 由 {@link com.njydsz.pmis.message.server.channel.impl.PushChannel} 根据
 * {@code pmis.message.push.provider} 配置选择实际 provider。
 *
 * <p>目标设备标识（clientId/deviceToken）从 {@link MessageRequest#getChannelMeta()}
 * 的 {@code deviceToken} 获取，无则回退到 {@code receiver}。
 *
 * <p>P1-10 增强：支持批量推送（{@link #batchSend}）和富媒体推送（通过 channelMeta 传入 imageUrl / actionUrl）。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public interface PushProvider {

    /**
     * 服务商标识（如 getui / jpush / mock）。
     *
     * @return 服务商标识
     */
    String providerType();

    /**
     * 发送推送。
     *
     * @param request  消息请求（subject=标题, content=正文, channelMeta.deviceToken=设备标识）
     * @param template 模板实体，可为 null
     * @return 发送结果（含 providerTraceId）
     */
    MessageResult send(MessageRequest request, MsgTemplateDO template);

    /**
     * P1-10: 批量推送到多个设备。
     *
     * <p>默认实现逐条发送，provider 可覆盖为原生批量接口。
     * 富媒体参数通过 channelMeta 传入：imageUrl / actionUrl / badge / sound。
     *
     * @param requests 消息请求列表（每条 receiver 为一个设备标识）
     * @param template 模板实体
     * @return 发送结果列表
     */
    default List<MessageResult> batchSend(List<MessageRequest> requests, MsgTemplateDO template) {
        return requests.stream()
                .map(req -> send(req, template))
                .toList();
    }
}
