package com.njydsz.pmis.message.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.message.dto.CanaryReportVO;
import com.njydsz.pmis.message.entity.MsgLogDO;
import com.njydsz.pmis.message.enums.MessageStatusEnum;
import com.njydsz.pmis.message.enums.ReceiptStatusEnum;
import com.njydsz.pmis.message.mapper.MsgLogMapper;
import com.njydsz.pmis.message.service.CanaryReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * 灰度 A/B 报表服务实现（P1-6）。
 *
 * <p>基于 {@code pmis_msg_log} 的 selectCount 聚合,分别统计：
 * <ul>
 *   <li>实验组（treatment）：{@code canary_key = canaryKey},即命中灰度并切换实验模板/通道的消息</li>
 *   <li>对照组（control）：{@code template_code = canaryKey AND canary = 0 AND canary_key IS NULL},
 *       即未命中灰度、使用基线模板的消息</li>
 * </ul>
 * 每组统计 total/success/failed/retry/dead/delivered/read/clicked 及 successRate/deliveryRate/readRate。
 * 时间范围通过 created_at 区间过滤,null 时默认最近 7 天（灰度实验通常以天/周为单位观察）。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CanaryReportServiceImpl implements CanaryReportService {

    /** 灰度报表默认回溯天数 */
    private static final int DEFAULT_LOOKBACK_DAYS = 7;

    /** 消息日志 Mapper（A/B 聚合统计） */
    private final MsgLogMapper msgLogMapper;

    @Override
    public CanaryReportVO getReport(String canaryKey, LocalDateTime start, LocalDateTime end) {
        if (!StringUtils.hasText(canaryKey)) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "灰度键不能为空");
        }
        LocalDateTime[] range = normalizeRange(start, end);
        LocalDateTime actualStart = range[0];
        LocalDateTime actualEnd = range[1];

        CanaryReportVO.GroupStats treatment = buildTreatmentStats(canaryKey, actualStart, actualEnd);
        CanaryReportVO.GroupStats control = buildControlStats(canaryKey, actualStart, actualEnd);

        CanaryReportVO vo = new CanaryReportVO();
        vo.setCanaryKey(canaryKey);
        vo.setControl(control);
        vo.setTreatment(treatment);
        vo.setStart(actualStart.toString());
        vo.setEnd(actualEnd.toString());
        return vo;
    }

    /**
     * 构建实验组统计：canary_key = canaryKey（命中灰度）。
     */
    private CanaryReportVO.GroupStats buildTreatmentStats(String canaryKey,
                                                          LocalDateTime start, LocalDateTime end) {
        long total = countTreatment(canaryKey, null, null, start, end);
        long success = countTreatment(canaryKey, MessageStatusEnum.SUCCESS, null, start, end);
        long failed = countTreatment(canaryKey, MessageStatusEnum.FAILED, null, start, end);
        long retry = countTreatment(canaryKey, MessageStatusEnum.RETRY, null, start, end);
        long dead = countTreatment(canaryKey, MessageStatusEnum.DEAD, null, start, end);
        long delivered = countTreatmentReceipt(canaryKey, ReceiptStatusEnum.DELIVERED, start, end);
        long read = countTreatmentReceipt(canaryKey, ReceiptStatusEnum.READ, start, end);
        long clicked = countTreatmentReceipt(canaryKey, ReceiptStatusEnum.CLICKED, start, end);
        return assembleStats(total, success, failed, retry, dead, delivered, read, clicked);
    }

    /**
     * 构建对照组统计：template_code = canaryKey AND canary = 0 AND canary_key IS NULL（未命中灰度）。
     */
    private CanaryReportVO.GroupStats buildControlStats(String canaryKey,
                                                        LocalDateTime start, LocalDateTime end) {
        long total = countControl(canaryKey, null, null, start, end);
        long success = countControl(canaryKey, MessageStatusEnum.SUCCESS, null, start, end);
        long failed = countControl(canaryKey, MessageStatusEnum.FAILED, null, start, end);
        long retry = countControl(canaryKey, MessageStatusEnum.RETRY, null, start, end);
        long dead = countControl(canaryKey, MessageStatusEnum.DEAD, null, start, end);
        long delivered = countControlReceipt(canaryKey, ReceiptStatusEnum.DELIVERED, start, end);
        long read = countControlReceipt(canaryKey, ReceiptStatusEnum.READ, start, end);
        long clicked = countControlReceipt(canaryKey, ReceiptStatusEnum.CLICKED, start, end);
        return assembleStats(total, success, failed, retry, dead, delivered, read, clicked);
    }

    /**
     * 实验组按发送状态统计（canary_key = canaryKey）。
     */
    private long countTreatment(String canaryKey, MessageStatusEnum status, String ignored,
                                LocalDateTime start, LocalDateTime end) {
        LambdaQueryWrapper<MsgLogDO> wrapper = new LambdaQueryWrapper<MsgLogDO>()
                .eq(MsgLogDO::getCanaryKey, canaryKey)
                .ge(MsgLogDO::getCreatedAt, start)
                .le(MsgLogDO::getCreatedAt, end);
        if (status != null) {
            wrapper.eq(MsgLogDO::getStatus, status.name());
        }
        Long count = msgLogMapper.selectCount(wrapper);
        return count == null ? 0L : count;
    }

    /**
     * 实验组按回执状态统计（canary_key = canaryKey）。
     */
    private long countTreatmentReceipt(String canaryKey, ReceiptStatusEnum receipt,
                                       LocalDateTime start, LocalDateTime end) {
        Long count = msgLogMapper.selectCount(new LambdaQueryWrapper<MsgLogDO>()
                .eq(MsgLogDO::getCanaryKey, canaryKey)
                .eq(MsgLogDO::getReceiptStatus, receipt.name())
                .ge(MsgLogDO::getCreatedAt, start)
                .le(MsgLogDO::getCreatedAt, end));
        return count == null ? 0L : count;
    }

    /**
     * 对照组按发送状态统计（template_code = canaryKey AND canary = 0 AND canary_key IS NULL）。
     */
    private long countControl(String canaryKey, MessageStatusEnum status, String ignored,
                              LocalDateTime start, LocalDateTime end) {
        LambdaQueryWrapper<MsgLogDO> wrapper = new LambdaQueryWrapper<MsgLogDO>()
                .eq(MsgLogDO::getTemplateCode, canaryKey)
                .eq(MsgLogDO::getCanary, 0)
                .isNull(MsgLogDO::getCanaryKey)
                .ge(MsgLogDO::getCreatedAt, start)
                .le(MsgLogDO::getCreatedAt, end);
        if (status != null) {
            wrapper.eq(MsgLogDO::getStatus, status.name());
        }
        Long count = msgLogMapper.selectCount(wrapper);
        return count == null ? 0L : count;
    }

    /**
     * 对照组按回执状态统计（template_code = canaryKey AND canary = 0 AND canary_key IS NULL）。
     */
    private long countControlReceipt(String canaryKey, ReceiptStatusEnum receipt,
                                     LocalDateTime start, LocalDateTime end) {
        Long count = msgLogMapper.selectCount(new LambdaQueryWrapper<MsgLogDO>()
                .eq(MsgLogDO::getTemplateCode, canaryKey)
                .eq(MsgLogDO::getCanary, 0)
                .isNull(MsgLogDO::getCanaryKey)
                .eq(MsgLogDO::getReceiptStatus, receipt.name())
                .ge(MsgLogDO::getCreatedAt, start)
                .le(MsgLogDO::getCreatedAt, end));
        return count == null ? 0L : count;
    }

    /**
     * 组装分组统计并计算比率。
     */
    private CanaryReportVO.GroupStats assembleStats(long total, long success, long failed,
                                                     long retry, long dead, long delivered,
                                                     long read, long clicked) {
        CanaryReportVO.GroupStats stats = new CanaryReportVO.GroupStats();
        stats.setTotal(total);
        stats.setSuccess(success);
        stats.setFailed(failed);
        stats.setRetry(retry);
        stats.setDead(dead);
        stats.setDelivered(delivered);
        stats.setRead(read);
        stats.setClicked(clicked);
        stats.setSuccessRate(total > 0 ? round2(success * 100.0 / total) : 0.0);
        stats.setDeliveryRate(total > 0 ? round2((delivered + read + clicked) * 100.0 / total) : 0.0);
        stats.setReadRate(total > 0 ? round2((read + clicked) * 100.0 / total) : 0.0);
        return stats;
    }

    /**
     * 规范化时间范围：start 为 null 时取 7 天前，end 为 null 时取当前时间。
     */
    private LocalDateTime[] normalizeRange(LocalDateTime start, LocalDateTime end) {
        LocalDateTime actualEnd = end != null ? end : LocalDateTime.now();
        LocalDateTime actualStart = start != null ? start : actualEnd.minusDays(DEFAULT_LOOKBACK_DAYS);
        return new LocalDateTime[]{actualStart, actualEnd};
    }

    /**
     * 保留两位小数。
     */
    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
