package com.njydsz.message.server.service.impl.core;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.njydsz.common.lock.core.DistributedLocker;
import com.njydsz.message.domain.entity.core.MsgLogDO;
import com.njydsz.message.domain.enums.core.MessageStatusEnum;
import com.njydsz.message.infra.mapper.core.MsgLogMapper;
import com.njydsz.message.server.channel.ChannelRouter;
import com.njydsz.message.server.metric.MessageMetrics;
import com.njydsz.message.server.tracing.MessageTraceContext;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * P0-3: 定时消息调度扫描器。
 *
 * <p>定时扫描 {@code status=SCHEDULED AND scheduled_at <= now} 的消息，在分布式锁内
 * 逐条触发发送：
 * <ul>
 *   <li>成功 → SUCCESS</li>
 *   <li>失败 → 走重试流程（RETRY + 指数退避）</li>
 * </ul>
 *
 * <p>多实例部署通过 Redisson 分布式锁保证只有一个实例执行扫描。
 * 默认 30s 扫描一次，可通过 {@code ydsz.message.scheduled-scan-interval-ms} 配置。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
@EnableScheduling
@ConditionalOnProperty(prefix = "ydsz.message", name = "scheduled-enabled", havingValue = "true", matchIfMissing = true)
public class ScheduledMessageScanner {

    private final MsgLogMapper msgLogMapper;
    private final ChannelRouter channelRouter;
    private final MessageMetrics messageMetrics;
    private final DistributedLocker distributedLocker;

    /** 分布式锁 key */
    private static final String LOCK_KEY = "ydsz:msg:scheduled:scan:lock";

    /** 单次扫描批量大小 */
    private static final int BATCH_SIZE = 200;

    /**
     * 定时扫描到期消息。
     *
     * <p>默认 30s 扫描一次，分布式锁 TTL 60s，等待 0s（不阻塞），获取失败直接跳过。
     */
    @Scheduled(fixedDelayString = "${ydsz.message.scheduled-scan-interval-ms:30000}")
    public void scan() {
        String lockValue = null;
        try {
            lockValue = distributedLocker.tryLock(LOCK_KEY, 0, 60, TimeUnit.SECONDS);
            if (lockValue == null) {
                log.debug("[ScheduledScanner] 未获取锁,跳过本次扫描");
                return;
            }
            doScan();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("[ScheduledScanner] 扫描被中断");
        } catch (Exception e) {
            log.error("[ScheduledScanner] 扫描异常: {}", e.getMessage(), e);
        } finally {
            if (lockValue != null) {
                distributedLocker.unlock(LOCK_KEY, lockValue);
            }
        }
    }

    /**
     * 执行定时消息扫描。
     */
    private void doScan() {
        LocalDateTime now = LocalDateTime.now();
        List<MsgLogDO> due = msgLogMapper.selectList(new LambdaQueryWrapper<MsgLogDO>()
                .eq(MsgLogDO::getStatus, MessageStatusEnum.SCHEDULED.name())
                .le(MsgLogDO::getScheduledAt, now)
                .last("LIMIT " + BATCH_SIZE));
        if (due.isEmpty()) {
            return;
        }
        log.info("[ScheduledScanner] 到期定时消息 {} 条", due.size());
        int success = 0;
        int failed = 0;
        for (MsgLogDO logDO : due) {
            try {
                sendScheduledMessage(logDO);
                success++;
            } catch (Exception e) {
                log.error("[ScheduledScanner] 定时消息发送异常: logId={} err={}",
                        logDO.getId(), e.getMessage());
                failed++;
            }
        }
        log.info("[ScheduledScanner] 扫描完成: total={} success={} failed={}", due.size(), success, failed);
    }

    /**
     * 发送单条定时消息：状态流转 SCHEDULED → SENDING → dispatch → SUCCESS/RETRY。
     *
     * @param logDO 消息日志实体
     */
    private void sendScheduledMessage(MsgLogDO logDO) {
        try (MessageTraceContext ctx = MessageTraceContext.enter(logDO.getTraceId())) {
            logDO.setStatus(MessageStatusEnum.SENDING.name());
            msgLogMapper.updateById(logDO);
            long start = System.currentTimeMillis();
            try {
                String providerTraceId = channelRouter.dispatch(logDO);
                long cost = System.currentTimeMillis() - start;
                logDO.setStatus(MessageStatusEnum.SUCCESS.name());
                logDO.setProviderTraceId(providerTraceId);
                logDO.setCostMs(cost);
                msgLogMapper.updateById(logDO);
                messageMetrics.recordSend(logDO.getChannel(), "SUCCESS", cost);
                log.info("[ScheduledScanner] 定时消息发送成功: msgId={} scheduledAt={} cost={}ms",
                        logDO.getMsgId(), logDO.getScheduledAt(), cost);
            } catch (Exception e) {
                long cost = System.currentTimeMillis() - start;
                logDO.setCostMs(cost);
                logDO.setErrorMessage(e.getMessage());
                logDO.setStatus(MessageStatusEnum.RETRY.name());
                logDO.setRetryCount(1);
                logDO.setNextRetryAt(LocalDateTime.now().plusSeconds(30));
                msgLogMapper.updateById(logDO);
                messageMetrics.recordRetry(logDO.getChannel());
                log.warn("[ScheduledScanner] 定时消息发送失败转重试: msgId={} err={}",
                        logDO.getMsgId(), e.getMessage());
            }
        }
    }
}
