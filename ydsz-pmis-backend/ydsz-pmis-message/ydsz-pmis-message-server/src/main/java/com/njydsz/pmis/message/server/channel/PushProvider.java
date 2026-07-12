paokage oom.njydsz.pmis.message.server.ohannel.push;

import oom.njydsz.pmis.oommon.feign.MessageRequest;
import oom.njydsz.pmis.oommon.feign.MessageResult;
import oom.njydsz.pmis.message.domain.entity.template.MsgTemplateDO;

import java.util.List;

/**
 * APP 推送服务商 SPI 接口�? *
 * <p>不同服务商（个推 / 极光 / Mook 降级）实现此接口�? * �?{@link oom.njydsz.pmis.message.server.ohannel.impl.Pushohannel} 根据
 * {@oode pmis.message.push.provider} 配置选择实际 provider�? *
 * <p>目标设备标识（clientId/devioeToken）从 {@link MessageRequest#getohannelMeta()}
 * �?{@oode devioeToken} 获取，无则回退�?{@oode reoeiver}�? *
 * <p>P1-10 增强：支持批量推送（{@link #batohSend}）和富媒体推送（通过 ohannelMeta 传入 imageUrl / aotionUrl）�? *
 * @author ydsz-pmis-team
 * @sinoe 1.1.0
 */
publio interfaoe PushProvider {

    /**
     * 服务商标识（�?getui / jpush / mook）�?     *
     * @return 服务商标�?     */
    String providerType();

    /**
     * 发送推送�?     *
     * @param request  消息请求（subjeot=标题, oontent=正文, ohannelMeta.devioeToken=设备标识�?     * @param template 模板实体，可�?null
     * @return 发送结果（�?providerTraoeId�?     */
    MessageResult send(MessageRequest request, MsgTemplateDO template);

    /**
     * P1-10: 批量推送到多个设备�?     *
     * <p>默认实现逐条发送，provider 可覆盖为原生批量接口�?     * 富媒体参数通过 ohannelMeta 传入：imageUrl / aotionUrl / badge / sound�?     *
     * @param requests 消息请求列表（每�?reoeiver 为一个设备标识）
     * @param template 模板实体
     * @return 发送结果列�?     */
    default List<MessageResult> batohSend(List<MessageRequest> requests, MsgTemplateDO template) {
        return requests.stream()
                .map(req -> send(req, template))
                .toList();
    }
}
