package com.remisoft.message.server.service.canary;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.remisoft.message.domain.entity.canary.MsgCanary;
import com.remisoft.message.domain.entity.core.MsgLog;
import com.remisoft.message.infra.mapper.canary.MsgCanaryMapper;
import com.remisoft.message.infra.mapper.core.MsgLogMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 灰度自动胜出服务。
 * <p>基于统计数据自动选出灰度胜出版本并放量。
 *
 * @author remi-team
 * @since 1.0.0
 */


@Slf4j
@Component
@RequiredArgsConstructor
public class CanaryAutoWinnerService {

    private final MsgCanaryMapper canaryMapper;
    private final MsgLogMapper msgLogMapper;

    /** 最小样本量（每组至少 100 条才计算胜出） */
    private static final int MIN_SAMPLE_SIZE = 100;

    /**
     * 检查并执行自动胜出。
     *
     * @param canary 灰度配置
     */
    public void checkAndPromote(MsgCanary canary) {
        if (canary == null || !StringUtils.hasText(canary.getCanaryKey())) {
            return;
        }
        // 查询灰度组消息日志
        List<MsgLog> canaryLogs = msgLogMapper.selectList(new LambdaQueryWrapper<MsgLog>()
                .eq(MsgLog::getCanary, 1)
                .eq(MsgLog::getCanaryKey, canary.getCanaryKey())
                .ge(MsgLog::getCreatedAt, LocalDateTime.now().minusDays(7)));

        // 查询对照组消息日志
        List<MsgLog> controlLogs = msgLogMapper.selectList(new LambdaQueryWrapper<MsgLog>()
                .eq(MsgLog::getCanary, 0)
                .like(MsgLog::getTemplateCode, canary.getCanaryKey())
                .ge(MsgLog::getCreatedAt, LocalDateTime.now().minusDays(7)));

        if (canaryLogs.size() < MIN_SAMPLE_SIZE || controlLogs.size() < MIN_SAMPLE_SIZE) {
            log.info("[CanaryAutoWinner] 样本量不足,跳过: canaryKey={} canary={} control={}",
                    canary.getCanaryKey(), canaryLogs.size(), controlLogs.size());
            return;
        }

        double canaryReadRate = calculateReadRate(canaryLogs);
        double controlReadRate = calculateReadRate(controlLogs);

        log.info("[CanaryAutoWinner] A/B 对比: canaryKey={} canaryReadRate={} controlReadRate={}",
                canary.getCanaryKey(), canaryReadRate, controlReadRate);

        // 灰度组已读率 > 对照组 5% 以上,自动胜出
        if (canaryReadRate > controlReadRate * 1.05) {
            log.info("[CanaryAutoWinner] 灰度组胜出! 提升为正式版本: canaryKey={}", canary.getCanaryKey());
            canary.setStatus("DISABLED");
            canaryMapper.updateById(canary);
        } else if (controlReadRate > canaryReadRate * 1.05) {
            log.info("[CanaryAutoWinner] 对照组胜出,关闭灰度: canaryKey={}", canary.getCanaryKey());
            canary.setStatus("DISABLED");
            canaryMapper.updateById(canary);
        }
    }

    private double calculateReadRate(List<MsgLog> logs) {
        if (logs.isEmpty()) return 0;
        long read = logs.stream().filter(l -> "READ".equals(l.getReceiptStatus())
                || "CLICKED".equals(l.getReceiptStatus())).count();
        return (double) read / logs.size();
    }
}
