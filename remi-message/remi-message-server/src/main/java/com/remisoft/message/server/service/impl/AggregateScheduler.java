package com.remisoft.message.server.service.impl.batch;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.remisoft.common.lock.annotation.DistributedScheduled;
import com.remisoft.message.domain.entity.batch.MsgAggregate;
import com.remisoft.message.domain.enums.batch.AggregateBatchStatusEnum;
import com.remisoft.message.infra.mapper.batch.MsgAggregateMapper;
import com.remisoft.message.server.service.batch.AggregateService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 聚合批次调度器。
 *
 * <p>定时扫描 PENDING 且到期的批次,流转为 READY 后触发 {@link AggregateService#flushDue} 发送。
 *
 * <p>通过 {@link DistributedScheduled} 注解保证多实例部署时同一时刻只有一个实例执行扫描,
 * 避免重复流转状态、重复发送聚合消息。锁等待 0s(非阻塞),TTL 300s,
 * 获取失败直接跳过本次扫描,由下一个周期接管。
 *
 * @author remi-team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
@EnableScheduling
@ConditionalOnProperty(prefix = "remi.message", name = "aggregate-enabled", havingValue = "true", matchIfMissing = true)
public class AggregateScheduler {

    private final MsgAggregateMapper msgAggregateMapper;
    private final AggregateService aggregateService;

    /**
     * 定时扫描聚合批次:将 PENDING 且 scheduled_send_at<=now 的批次置 READY,再 flushDue 发送。
     *
     * <p>分布式锁通过 {@link DistributedScheduled} 注解自动管理,获取失败直接跳过本次扫描。
     */
    @Scheduled(fixedDelayString = "${remi.message.aggregate-scan-interval-ms:60000}")
    @DistributedScheduled(lockKey = "message:aggregate-scan", leaseTime = 60)
    public void scan() {
        try {
            doScan();
        } catch (Exception e) {
            log.error("[AggregateScheduler] 扫描异常: {}", e.getMessage(), e);
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
