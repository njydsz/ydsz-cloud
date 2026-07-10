package com.njydsz.pmis.message.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.njydsz.pmis.message.channel.ChannelRouter;
import com.njydsz.pmis.message.config.RetryStrategyResolver;
import com.njydsz.pmis.message.constant.MessageConstants;
import com.njydsz.pmis.message.entity.core.MsgLogDO;
import com.njydsz.pmis.message.enums.core.MessageStatusEnum;
import com.njydsz.pmis.message.mapper.core.MsgLogMapper;
import com.njydsz.pmis.message.metric.MessageMetrics;
import com.njydsz.pmis.message.tracing.MessageTraceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 消息重试调度器。
 *
 * <p>定时扫描 {@code status=RETRY AND next_retry_at<=now} 的消息,在分布式锁内重新发送:
 * <ul>
 *   <li>成功 → SUCCESS</li>
 *   <li>失败 + retryCount &lt; MAX → RETRY + 更新 nextRetryAt(指数退避)</li>
 *   <li>失败 + retryCount &gt;= MAX → DEAD</li>
 * </ul>
 *
 * <p>多实例部署通过 Redisson 分布式锁保证只有一个实例执行扫描。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
@EnableScheduling
@ConditionalOnProperty(prefix = "pmis.message", name = "retry-enabled", havingValue = "true", matchIfMissing = true)
public class RetryScanner {

    private final MsgLogMapper msgLogMapper;
    private final ChannelRouter channelRouter;
    private final MessageMetrics messageMetrics;
    private final RedissonClient redissonClient;
    private final RetryStrategyResolver retryStrategyResolver;

    /**
     * 定时扫描重试队列。
     *
     * <p>默认 30s 扫描一次,通过 {@code pmis.message.retry-scan-interval-ms} 配置。
     * 分布式锁 TTL 60s,等待 0s(不阻塞),获取失败直接跳过本次扫描。
     */
    @Scheduled(fixedDelayString = "${pmis.message.retry-scan-interval-ms:30000}")
    public void scan() {
        RLock lock = redissonClient.getLock(MessageConstants.RETRY_SCAN_LOCK_KEY);
        boolean locked = false;
        try {
            locked = lock.tryLock(0, 60, TimeUnit.SECONDS);
            if (!locked) {
                log.debug("[RetryScanner] 未获取锁,跳过本次扫描");
                return;
            }
            doScan();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("[RetryScanner] 扫描被中断");
        } catch (Exception e) {
            log.error("[RetryScanner] 扫描异常: {}", e.getMessage(), e);
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 执行重试扫描:查询到期 RETRY 消息并逐条重试。
     */
    private void doScan() {
        LocalDateTime now = LocalDateTime.now();
        List<MsgLogDO> due = msgLogMapper.selectList(new LambdaQueryWrapper<MsgLogDO>()
                .eq(MsgLogDO::getStatus, MessageStatusEnum.RETRY.name())
                .le(MsgLogDO::getNextRetryAt, now)
                .last("LIMIT " + MessageConstants.RETRY_SCAN_BATCH_SIZE));
        if (due.isEmpty()) {
            return;
        }
        log.info("[RetryScanner] 待重试消息 {} 条", due.size());
        int success = 0;
        int dead = 0;
        int retryAgain = 0;
        for (MsgLogDO logDO : due) {
            try {
                MessageStatusEnum result = retryOnce(logDO);
                if (result == MessageStatusEnum.SUCCESS) {
                    success++;
                } else if (result == MessageStatusEnum.DEAD) {
                    dead++;
                } else {
                    retryAgain++;
                }
            } catch (Exception e) {
                log.error("[RetryScanner] 重试异常: logId={} err={}", logDO.getId(), e.getMessage());
                retryAgain++;
            }
        }
        log.info("[RetryScanner] 扫描完成: total={} success={} dead={} retryAgain={}",
                due.size(), success, dead, retryAgain);
    }

    /**
     * 重试单条消息:状态流转 RETRY → SENDING → dispatch → SUCCESS/RETRY/DEAD。
     *
     * @param logDO 日志实体
     * @return 重试后的状态
     */
    private MessageStatusEnum retryOnce(MsgLogDO logDO) {
        // P1-3: 进入追踪上下文，将 logDO.traceId 写入 MDC，确保重试日志可追溯
        try (MessageTraceContext ctx = MessageTraceContext.enter(logDO.getTraceId())) {
            // ① 流转到 SENDING
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
                log.info("[RetryScanner] 重试成功: logId={} retryCount={}", logDO.getId(), logDO.getRetryCount());
                return MessageStatusEnum.SUCCESS;
            } catch (Exception e) {
                long cost = System.currentTimeMillis() - start;
                int newRetryCount = (logDO.getRetryCount() == null ? 0 : logDO.getRetryCount()) + 1;
                logDO.setRetryCount(newRetryCount);
                logDO.setCostMs(cost);
                logDO.setErrorMessage(e.getMessage());
                // P1-7: 使用可配重试策略替代硬编码常量
                if (retryStrategyResolver.isMaxRetriesReached(newRetryCount, logDO.getChannel())) {
                    // 超过最大重试次数 → DEAD
                    logDO.setStatus(MessageStatusEnum.DEAD.name());
                    msgLogMapper.updateById(logDO);
                    messageMetrics.recordDead(logDO.getChannel());
                    log.warn("[RetryScanner] 重试耗尽转死信: logId={} retryCount={}",
                            logDO.getId(), newRetryCount);
                    return MessageStatusEnum.DEAD;
                }
                // 继续重试,指数退避（P1-7: 策略可配）
                logDO.setStatus(MessageStatusEnum.RETRY.name());
                logDO.setNextRetryAt(retryStrategyResolver.calcNextRetryAt(newRetryCount, logDO.getChannel()));
                msgLogMapper.updateById(logDO);
                messageMetrics.recordRetry(logDO.getChannel());
                log.info("[RetryScanner] 重试失败继续等待: logId={} retryCount={} nextRetryAt={}",
                        logDO.getId(), newRetryCount, logDO.getNextRetryAt());
                return MessageStatusEnum.RETRY;
            }
        }
    }
}
