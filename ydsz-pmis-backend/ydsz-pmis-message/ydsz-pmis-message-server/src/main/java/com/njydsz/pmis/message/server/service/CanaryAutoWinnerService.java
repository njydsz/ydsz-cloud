package com.njydsz.pmis.message.server.service.canary;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.njydsz.pmis.message.domain.entity.core.MsgLogDO;
import com.njydsz.pmis.message.domain.entity.canary.MsgCanaryDO;
import com.njydsz.pmis.message.infra.mapper.canary.MsgCanaryMapper;
import com.njydsz.pmis.message.infra.mapper.core.MsgLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * A/B 实验自动胜出服务（P2-2）。
 *
 * <p>当 A/B 实验运行达到足够样本量后，自动计算各实验组的转化率,
 * 将胜出方案（送达率/已读率最高）设为正式版本,关闭灰度实验。
 *
 * @author ydsz-pmis-team
 * @since 1.5.0
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
    public void checkAndPromote(MsgCanaryDO canary) {
        if (canary == null || !StringUtils.hasText(canary.getCanaryKey())) {
            return;
        }
        // 查询灰度组消息日志
        List<MsgLogDO> canaryLogs = msgLogMapper.selectList(new LambdaQueryWrapper<MsgLogDO>()
                .eq(MsgLogDO::getCanary, 1)
                .eq(MsgLogDO::getCanaryKey, canary.getCanaryKey())
                .ge(MsgLogDO::getCreatedAt, LocalDateTime.now().minusDays(7)));

        // 查询对照组消息日志
        List<MsgLogDO> controlLogs = msgLogMapper.selectList(new LambdaQueryWrapper<MsgLogDO>()
                .eq(MsgLogDO::getCanary, 0)
                .like(MsgLogDO::getTemplateCode, canary.getCanaryKey())
                .ge(MsgLogDO::getCreatedAt, LocalDateTime.now().minusDays(7)));

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

    private double calculateReadRate(List<MsgLogDO> logs) {
        if (logs.isEmpty()) return 0;
        long read = logs.stream().filter(l -> "READ".equals(l.getReceiptStatus())
                || "CLICKED".equals(l.getReceiptStatus())).count();
        return (double) read / logs.size();
    }
}
