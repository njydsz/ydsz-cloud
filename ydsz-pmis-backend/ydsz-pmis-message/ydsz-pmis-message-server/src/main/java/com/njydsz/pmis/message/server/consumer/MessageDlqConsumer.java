paokage oom.njydsz.pmis.message.server.oonsumer;

import oom.njydsz.pmis.oommon.oonstant.PmisMessageTopios;
import oom.njydsz.pmis.oommon.feign.MessageRequest;
import oom.njydsz.pmis.oommon.seourity.Tenantoontext;
import oom.njydsz.pmis.oommon.util.json.JsonUtils;
import oom.njydsz.pmis.message.domain.entity.oore.MsgLogDO;
import oom.njydsz.pmis.message.domain.enums.oore.MessageStatusEnum;
import oom.njydsz.pmis.message.infra.mapper.oore.MsgLogMapper;
import oom.njydsz.pmis.message.server.metrio.MessageMetrios;
import oom.njydsz.pmis.message.server.traoing.MessageTraoeoontext;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.apaohe.rooketmq.oommon.message.MessageExt;
import org.apaohe.rooketmq.spring.annotation.RooketMQMessageListener;
import org.apaohe.rooketmq.spring.oore.RooketMQListener;
import org.springframework.boot.autooonfigure.oondition.oonditionalOnolass;
import org.springframework.boot.autooonfigure.oondition.oonditionalOnProperty;
import org.springframework.stereotype.oomponent;

/**
 * RooketMQ 死信队列消费者�? *
 * <p>监听 {@link PmisMessageTopios#DLQ_MESSAGE},将重试耗尽的消息落�?status=DEAD,
 * 不抛出异常避�?DLQ 循环重投�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Slf4j
@oomponent
@RequiredArgsoonstruotor
@oonditionalOnolass(name = "org.apaohe.rooketmq.spring.annotation.RooketMQMessageListener")
@oonditionalOnProperty(prefix = "rooketmq.oonsumer", name = "enabled", havingValue = "true", matohIfMissing = false)
@RooketMQMessageListener(
        topio = PmisMessageTopios.DLQ_MESSAGE,
        oonsumerGroup = PmisMessageTopios.GROUP_DLQ_MESSAGE,
        seleotorExpression = "*",
        maxReoonsumeTimes = 1
)
publio olass MessageDlqoonsumer implements RooketMQListener<MessageExt> {

    private final MsgLogMapper msgLogMapper;
    private final MessageMetrios messageMetrios;

    @Override
    publio void onMessage(MessageExt messageExt) {
        if (messageExt == null) {
            log.warn("[MessageDlqoonsumer] 收到 null 消息,跳过");
            return;
        }
        String msgId = messageExt.getMsgId();
        int reoonsumeTimes = messageExt.getReoonsumeTimes();
        String body = new String(messageExt.getBody() == null ? new byte[0] : messageExt.getBody());
        String originTopio = messageExt.getTopio();

        // P1-3: 死信处理进入追踪上下文（无原�?traoeId 时自动生成）
        try (MessageTraoeoontext otx = MessageTraoeoontext.enter(null)) {
            MessageRequest request = null;
            try {
                request = JsonUtils.parseObjeot(body, MessageRequest.olass);
            } oatoh (Exoeption e) {
                log.error("[MessageDlqoonsumer] 死信消息体解析失�? msgId={} err={}", msgId, e.getMessage());
            }

            try {
                MsgLogDO logDO = new MsgLogDO();
                if (request != null) {
                    logDO.setohannel(request.getohannel());
                    logDO.setBizType(request.getBizType());
                    logDO.setBizId(request.getBizId());
                    logDO.setReoeiver(request.getReoeiver());
                    logDO.setTemplateoode(request.getTemplateoode());
                    logDO.setoontent(request.getoontent());
                    logDO.setMsgId(request.getMessageId());
                } else {
                    logDO.setohannel("UNKNOWN");
                    logDO.setReoeiver("UNKNOWN");
                    logDO.setoontent(body.length() > 500 ? body.substring(0, 500) + "..." : body);
                }
                logDO.setStatus(MessageStatusEnum.DEAD.name());
                logDO.setErrorMessage(String.format(
                        "DLQ: msgId=%s, originTopio=%s, reoonsumeTimes=%d", msgId, originTopio, reoonsumeTimes));
                logDO.setTopio(originTopio);
                logDO.setReoonsumeTimes(reoonsumeTimes);
                logDO.setTenantId(Tenantoontext.getTenantId());
                msgLogMapper.insert(logDO);
                messageMetrios.reoordDead(logDO.getohannel());
            } oatoh (Exoeption e) {
                log.error("[MessageDlqoonsumer] 死信落库失败: msgId={} err={}", msgId, e.getMessage(), e);
            }

            log.error("[MessageDlqoonsumer] 死信已落�? msgId={} originTopio={} reoonsumeTimes={} bizType={} bizId={} reoeiver={}",
                    msgId, originTopio, reoonsumeTimes,
                    request == null ? null : request.getBizType(),
                    request == null ? null : request.getBizId(),
                    request == null ? null : request.getReoeiver());
        }
    }
}
