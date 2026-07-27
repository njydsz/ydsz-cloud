package com.njydsz.message.server.service.impl.batch;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.njydsz.common.lock.core.DistributedLocker;
import com.njydsz.message.domain.constant.MessageConstants;
import com.njydsz.message.domain.entity.batch.MsgAggregate;
import com.njydsz.message.domain.enums.batch.AggregateBatchStatusEnum;
import com.njydsz.message.infra.mapper.batch.MsgAggregateMapper;
import com.njydsz.message.server.service.batch.AggregateService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 聚合批次调度器。
 *
 * <p>定时扫描 PENDING 且到期的批次,流转为 READY 后触发 {@link AggregateService#flushDue} 发送。
 *
 * <p>P2-4: 接入 Redisson 分布式锁,保证多实例部署时同一时刻只有一个实例执行扫描,
 * 避免重复流转状态、重复发送聚合消息。锁等待 0s(不阻塞),TTL 60s,
 * 获取失败直接跳过本次扫描,由下一个周期接管。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
@EnableScheduling
@ConditionalOnProperty(prefix = "ydsz.message", name = "aggregate-enabled", havingValue = "true", matchIfMissing = true)
public class AggregateScheduler {

    private final MsgAggregateMapper msgAggregateMapper;
    private final AggregateService aggregateService;
    private final DistributedLocker distributedLocker;

    /**
     * 定时扫描聚合批次:将 PENDING 且 scheduled_send_at<=now 的批次置 READY,再 flushDue 发送。
     *
     * <p>分布式锁 TTL 60s,等待 0s(不阻塞),获取失败直接跳过本次扫描。
     */
    @Scheduled(fixedDelayString = "${ydsz.message.aggregate-scan-interval-ms:60000}")
    public void scan() {
        String lockValue = null;
        try {
            lockValue = distributedLocker.tryLock(MessageConstants.AGGREGATE_SCAN_LOCK_KEY, 0, 60, TimeUnit.SECONDS);
            if (lockValue == null) {
                log.debug("[AggregateScheduler] 未获取锁,跳过本次扫描");
                return;
            }
            doScan();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("[AggregateScheduler] 扫描被中断");
        } catch (Exception e) {
            log.error("[AggregateScheduler] 扫描异常: {}", e.getMessage(), e);
        } finally {
            if (lockValue != null) {
                distributedLocker.unlock(MessageConstants.AGGREGATE_SCAN_LOCK_KEY, lockValue);
            }
        }
    }

    /**
     * 执行聚合批次扫描与发送。
     */
    private void doScan() {
        LocalDateTime now = LocalDateTime.now();
        List<MsgAggregate> due = msgAggregateMapper.selectList(new LambdaQueryWrapper<MsgAggregate>()
                .eq(MsgAggregate::getBatchStatus, AggregateBatchStatusEnum.PENDING.name())
                .le(MsgAggregate::getScheduledSendAt, now));
        if (due.isEmpty()) {
            return;
        }
        for (MsgAggregate batch : due) {
            msgAggregateMapper.update(null, new LambdaUpdateWrapper<MsgAggregate>()
                    .eq(MsgAggregate::getId, batch.getId())
                    .eq(MsgAggregate::getBatchStatus, AggregateBatchStatusEnum.PENDING.name())
                    .set(MsgAggregate::getBatchStatus, AggregateBatchStatusEnum.READY.name()));
        }
        int sent = aggregateService.flushDue();
        log.debug("[AggregateScheduler] 流转 {} 个到期批次,发送 {} 个", due.size(), sent);
    }
}
