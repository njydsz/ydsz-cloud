package com.njydsz.pmis.message.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.njydsz.pmis.message.constant.MessageConstants;
import com.njydsz.pmis.message.entity.MsgAggregateDO;
import com.njydsz.pmis.message.enums.AggregateBatchStatusEnum;
import com.njydsz.pmis.message.mapper.MsgAggregateMapper;
import com.njydsz.pmis.message.service.AggregateService;
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
 * 聚合批次调度器。
 *
 * <p>定时扫描 PENDING 且到期的批次,流转为 READY 后触发 {@link AggregateService#flushDue} 发送。
 *
 * <p>P2-4: 接入 Redisson 分布式锁,保证多实例部署时同一时刻只有一个实例执行扫描,
 * 避免重复流转状态、重复发送聚合消息。锁等待 0s(不阻塞),TTL 60s,
 * 获取失败直接跳过本次扫描,由下一个周期接管。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
@EnableScheduling
@ConditionalOnProperty(prefix = "pmis.message", name = "aggregate-enabled", havingValue = "true", matchIfMissing = true)
public class AggregateScheduler {

    private final MsgAggregateMapper msgAggregateMapper;
    private final AggregateService aggregateService;
    private final RedissonClient redissonClient;

    /**
     * 定时扫描聚合批次:将 PENDING 且 scheduled_send_at<=now 的批次置 READY,再 flushDue 发送。
     *
     * <p>分布式锁 TTL 60s,等待 0s(不阻塞),获取失败直接跳过本次扫描。
     */
    @Scheduled(fixedDelayString = "${pmis.message.aggregate-scan-interval-ms:60000}")
    public void scan() {
        RLock lock = redissonClient.getLock(MessageConstants.AGGREGATE_SCAN_LOCK_KEY);
        boolean locked = false;
        try {
            locked = lock.tryLock(0, 60, TimeUnit.SECONDS);
            if (!locked) {
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
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 执行聚合批次扫描与发送。
     */
    private void doScan() {
        LocalDateTime now = LocalDateTime.now();
        List<MsgAggregateDO> due = msgAggregateMapper.selectList(new LambdaQueryWrapper<MsgAggregateDO>()
                .eq(MsgAggregateDO::getBatchStatus, AggregateBatchStatusEnum.PENDING.name())
                .le(MsgAggregateDO::getScheduledSendAt, now));
        if (due.isEmpty()) {
            return;
        }
        for (MsgAggregateDO batch : due) {
            msgAggregateMapper.update(null, new LambdaUpdateWrapper<MsgAggregateDO>()
                    .eq(MsgAggregateDO::getId, batch.getId())
                    .eq(MsgAggregateDO::getBatchStatus, AggregateBatchStatusEnum.PENDING.name())
                    .set(MsgAggregateDO::getBatchStatus, AggregateBatchStatusEnum.READY.name()));
        }
        int sent = aggregateService.flushDue();
        log.debug("[AggregateScheduler] 流转 {} 个到期批次,发送 {} 个", due.size(), sent);
    }
}
