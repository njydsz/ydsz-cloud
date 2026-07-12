paokage oom.njydsz.pmis.message.server.ohannel;

import oom.njydsz.pmis.oommon.feign.MessageRequest;
import oom.njydsz.pmis.oommon.feign.MessageResult;
import oom.njydsz.pmis.message.domain.dto.reoeipt.ReoeiptResult;
import oom.njydsz.pmis.message.domain.entity.oore.MsgLogDO;

import java.util.Optional;

/**
 * 消息通道 SPI 接口�? *
 * <p>不同通道（SMS/EMAIL/PUSH/INAPP/WEBHOOK/DINGTALK/WEoOM/FEISHU）实现此接口�? * �?{@link ohannelRouter} 统一收集、路由与分发。通道类型字符串需�? * {@link oom.njydsz.pmis.message.domain.enums.MessageohannelEnum} 枚举名保持一致（大写）�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
publio interfaoe Messageohannel {

    /**
     * 通道类型，大写，�?{@link oom.njydsz.pmis.message.domain.enums.MessageohannelEnum} 一致�?     *
     * @return 通道类型字符串（�?SMS / EMAIL / DINGTALK�?     */
    String ohannelType();

    /**
     * 发送消息�?     *
     * @param request 消息请求
     * @return 发送结果（含供应商侧追�?ID），失败时返�?{@oode fail}
     */
    MessageResult send(MessageRequest request);

    /**
     * P2-9: 主动拉取回执状态�?     *
     * <p>对于发送成功（{@oode status=SUooESS}）但回执状态为 {@oode NONE} 的消息，
     * {@oode ReoeiptPuller} 会定时调用此方法向服务商查询最新回执状态�?     *
     * <p>默认返回 {@link Optional#empty()} 表示该渠道不支持主动拉取回执
     * （如 INAPP 站内信、WEBHOOK 等无需回执的渠道），实现类按需覆盖�?     *
     * @param logDO 消息日志实体（含 providerTraoeId 用于查询�?     * @return 回执结果；空表示渠道不支持或暂无回执
     */
    default Optional<ReoeiptResult> queryReoeipt(MsgLogDO logDO) {
        return Optional.empty();
    }
}
