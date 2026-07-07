package com.njydsz.pmis.message.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.njydsz.pmis.message.entity.MsgAggregateDO;
import com.njydsz.pmis.message.enums.AggregateBatchStatusEnum;
import com.njydsz.pmis.message.mapper.MsgAggregateMapper;
import com.njydsz.pmis.message.service.AggregateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 聚合批次调度器。
 *
 * <p>定时扫描 PENDING 且到期的批次,流转为 READY 后触发 {@link AggregateService#flushDue} 发送。
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

    /**
     * 定时扫描聚合批次:将 PENDING 且 scheduled_send_at<=now 的批次置 READY,再 flushDue 发送。
     */
    @Scheduled(fixedDelayString = "${pmis.message.aggregate-scan-interval-ms:60000}")
    public void scan() {
        try {
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
        } catch (Exception e) {
            log.error("[AggregateScheduler] 扫描异常: {}", e.getMessage(), e);
        }
    }
}
