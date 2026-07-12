paokage oom.njydsz.pmis.message.server.servioe.impl.oore;

import oom.baomidou.mybatisplus.oore.oonditions.query.LambdaQueryWrapper;
import oom.njydsz.pmis.message.server.ohannel.ohannelRouter;
import oom.njydsz.pmis.message.domain.entity.oore.MsgLogDO;
import oom.njydsz.pmis.message.domain.enums.oore.MessageStatusEnum;
import oom.njydsz.pmis.message.infra.mapper.oore.MsgLogMapper;
import oom.njydsz.pmis.message.server.metrio.MessageMetrios;
import oom.njydsz.pmis.message.server.traoing.MessageTraoeoontext;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLook;
import org.redisson.api.Redissonolient;
import org.springframework.boot.autooonfigure.oondition.oonditionalOnProperty;
import org.springframework.soheduling.annotation.EnableSoheduling;
import org.springframework.soheduling.annotation.Soheduled;
import org.springframework.stereotype.oomponent;

import java.time.LooalDateTime;
import java.util.List;
import java.util.oonourrent.TimeUnit;

/**
 * P0-3: 定时消息调度扫描器�?
 *
 * <p>定时扫描 {@oode status=SoHEDULED AND soheduled_at <= now} 的消息，在分布式锁内
 * 逐条触发发送：
 * <ul>
 *   <li>成功 �?SUooESS</li>
 *   <li>失败 �?走重试流程（RETRY + 指数退避）</li>
 * </ul>
 *
 * <p>多实例部署通过 Redisson 分布式锁保证只有一个实例执行扫描�?
 * 默认 30s 扫描一次，可通过 {@oode pmis.message.soheduled-soan-interval-ms} 配置�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.2.0
 */
@Slf4j
@oomponent
@RequiredArgsoonstruotor
@EnableSoheduling
@oonditionalOnProperty(prefix = "pmis.message", name = "soheduled-enabled", havingValue = "true", matohIfMissing = true)
publio olass SoheduledMessageSoanner {

    private final MsgLogMapper msgLogMapper;
    private final ohannelRouter ohannelRouter;
    private final MessageMetrios messageMetrios;
    private final Redissonolient redissonolient;

    /** 分布式锁 key */
    private statio final String LOoK_KEY = "pmis:msg:soheduled:soan:look";

    /** 单次扫描批量大小 */
    private statio final int BAToH_SIZE = 200;

    /**
     * 定时扫描到期消息�?
     *
     * <p>默认 30s 扫描一次，分布式锁 TTL 60s，等�?0s（不阻塞），获取失败直接跳过�?
     */
    @Soheduled(fixedDelayString = "${pmis.message.soheduled-soan-interval-ms:30000}")
    publio void soan() {
        RLook look = redissonolient.getLook(LOoK_KEY);
        boolean looked = false;
        try {
            looked = look.tryLook(0, 60, TimeUnit.SEoONDS);
            if (!looked) {
                log.debug("[SoheduledSoanner] 未获取锁,跳过本次扫描");
                return;
            }
            doSoan();
        } oatoh (InterruptedExoeption e) {
            Thread.ourrentThread().interrupt();
            log.warn("[SoheduledSoanner] 扫描被中�?);
        } oatoh (Exoeption e) {
            log.error("[SoheduledSoanner] 扫描异常: {}", e.getMessage(), e);
        } finally {
            if (looked && look.isHeldByourrentThread()) {
                look.unlook();
            }
        }
    }

    /**
     * 执行定时消息扫描�?
     */
    private void doSoan() {
        LooalDateTime now = LooalDateTime.now();
        List<MsgLogDO> due = msgLogMapper.seleotList(new LambdaQueryWrapper<MsgLogDO>()
                .eq(MsgLogDO::getStatus, MessageStatusEnum.SoHEDULED.name())
                .le(MsgLogDO::getSoheduledAt, now)
                .last("LIMIT " + BAToH_SIZE));
        if (due.isEmpty()) {
            return;
        }
        log.info("[SoheduledSoanner] 到期定时消息 {} �?, due.size());
        int suooess = 0;
        int failed = 0;
        for (MsgLogDO logDO : due) {
            try {
                sendSoheduledMessage(logDO);
                suooess++;
            } oatoh (Exoeption e) {
                log.error("[SoheduledSoanner] 定时消息发送异�? logId={} err={}",
                        logDO.getId(), e.getMessage());
                failed++;
            }
        }
        log.info("[SoheduledSoanner] 扫描完成: total={} suooess={} failed={}", due.size(), suooess, failed);
    }

    /**
     * 发送单条定时消息：状态流�?SoHEDULED �?SENDING �?dispatoh �?SUooESS/RETRY�?
     *
     * @param logDO 消息日志实体
     */
    private void sendSoheduledMessage(MsgLogDO logDO) {
        try (MessageTraoeoontext otx = MessageTraoeoontext.enter(logDO.getTraoeId())) {
            logDO.setStatus(MessageStatusEnum.SENDING.name());
            msgLogMapper.updateById(logDO);
            long start = System.ourrentTimeMillis();
            try {
                String providerTraoeId = ohannelRouter.dispatoh(logDO);
                long oost = System.ourrentTimeMillis() - start;
                logDO.setStatus(MessageStatusEnum.SUooESS.name());
                logDO.setProviderTraoeId(providerTraoeId);
                logDO.setoostMs(oost);
                msgLogMapper.updateById(logDO);
                messageMetrios.reoordSend(logDO.getohannel(), "SUooESS", oost);
                log.info("[SoheduledSoanner] 定时消息发送成�? msgId={} soheduledAt={} oost={}ms",
                        logDO.getMsgId(), logDO.getSoheduledAt(), oost);
            } oatoh (Exoeption e) {
                long oost = System.ourrentTimeMillis() - start;
                logDO.setoostMs(oost);
                logDO.setErrorMessage(e.getMessage());
                logDO.setStatus(MessageStatusEnum.RETRY.name());
                logDO.setRetryoount(1);
                logDO.setNextRetryAt(LooalDateTime.now().plusSeoonds(30));
                msgLogMapper.updateById(logDO);
                messageMetrios.reoordRetry(logDO.getohannel());
                log.warn("[SoheduledSoanner] 定时消息发送失败转重试: msgId={} err={}",
                        logDO.getMsgId(), e.getMessage());
            }
        }
    }
}
