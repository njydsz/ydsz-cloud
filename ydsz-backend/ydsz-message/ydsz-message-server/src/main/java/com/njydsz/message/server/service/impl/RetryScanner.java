package com.njydsz.message.server.service.impl.core;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.common.lock.core.DistributedLocker;
import com.njydsz.message.domain.constant.MessageConstants;
import com.njydsz.message.domain.entity.core.MsgLogDO;
import com.njydsz.message.domain.enums.core.MessageStatusEnum;
import com.njydsz.message.infra.mapper.core.MsgLogMapper;
import com.njydsz.message.server.channel.ChannelRouter;
import com.njydsz.message.server.config.RetryStrategyResolver;
import com.njydsz.message.server.metric.MessageMetrics;
import com.njydsz.message.server.tracing.MessageTraceContext;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

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
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
@EnableScheduling
@ConditionalOnProperty(prefix = "ydsz.message", name = "retry-enabled", havingValue = "true", matchIfMissing = true)
public class RetryScanner {

    private final MsgLogMapper msgLogMapper;
    private final ChannelRouter channelRouter;
    private final MessageMetrics messageMetrics;
    private final DistributedLocker distributedLocker;
    private final RetryStrategyResolver retryStrategyResolver;

    /**
     * 定时扫描重试队列。
     *
     * <p>默认 30s 扫描一次,通过 {@code ydsz.message.retry-scan-interval-ms} 配置。
     * 分布式锁 TTL 60s,等待 0s(不阻塞),获取失败直接跳过本次扫描。
     */
    @Scheduled(fixedDelayString = "${ydsz.message.retry-scan-interval-ms:30000}")
    public void scan() {
        String lockValue = null;
        try {
            lockValue = distributedLocker.tryLock(MessageConstants.RETRY_SCAN_LOCK_KEY, 0, 60, TimeUnit.SECONDS);
            if (lockValue == null) {
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
            if (lockValue != null) {
                distributedLocker.unlock(MessageConstants.RETRY_SCAN_LOCK_KEY, lockValue);
            }
        }
    }

    /**
     * 执行重试扫描:查询到期 RETRY 消息并逐条重试。
     */
    private void doScan() {
        LocalDateTime now = LocalDateTime.now();
        // P2-3: 使用 MyBatis-Plus 分页替代 .last("LIMIT ...")
        Page<MsgLogDO> page = new Page<>(1, MessageConstants.RETRY_SCAN_BATCH_SIZE);
        Page<MsgLogDO> duePage = msgLogMapper.selectPage(page, new LambdaQueryWrapper<MsgLogDO>()
                .eq(MsgLogDO::getStatus, MessageStatusEnum.RETRY.name())
                .le(MsgLogDO::getNextRetryAt, now));
        List<MsgLogDO> due = duePage.getRecords();
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
                log.error("[RetryScanner] 重试异常: logId={} err={}", logDO.getId(), e.getMessage(), e);
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
                LocalDateTime nextRetry = retryStrategyResolver.calcNextRetryAt(newRetryCount, logDO.getChannel());
                // GAP-7: 加入随机抖动因子（0~1s），避免多实例同时重试导致惊群效应
                long jitterMs = ThreadLocalRandom.current().nextLong(0, 1000);
                nextRetry = nextRetry.plusNanos(jitterMs * 1_000_000L);
                logDO.setNextRetryAt(nextRetry);
                msgLogMapper.updateById(logDO);
                messageMetrics.recordRetry(logDO.getChannel());
                log.info("[RetryScanner] 重试失败继续等待: logId={} retryCount={} nextRetryAt={}",
                        logDO.getId(), newRetryCount, logDO.getNextRetryAt());
                return MessageStatusEnum.RETRY;
            }
        }
    }
}
