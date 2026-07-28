package com.njydsz.message.server.service.impl.canary;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.njydsz.common.core.response.BaseResultCode;
import com.njydsz.common.exception.custom.SysException;
import com.njydsz.message.domain.dto.canary.CanaryReportVO;
import com.njydsz.message.domain.entity.core.MsgLog;
import com.njydsz.message.domain.enums.core.MessageStatusEnum;
import com.njydsz.message.domain.enums.receipt.ReceiptStatusEnum;
import com.njydsz.message.infra.mapper.core.MsgLogMapper;
import com.njydsz.message.server.service.canary.CanaryReportService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 灰度报告服务实现。
 *
 * <p>生成消息灰度发布报告：渠道成功率、用户反馈分布、A/B 桶效果对比、核心指标（送达/点击/转化）。
 *
 * <p>支持导出 CSV / 推送至 Dashboard，供产品/运营评估灰度放量策略。
 *
 * @author ydsz-team
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
            throw new SysException(BaseResultCode.BAD_REQUEST, "灰度键不能为空");
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
     * <p>D-1: 从8次selectCount改为2次selectMaps GROUP BY查询。
     */
    private CanaryReportVO.GroupStats buildTreatmentStats(String canaryKey,
                                                          LocalDateTime start, LocalDateTime end) {
        // D-1: 按status分组统计，1次查询
        Map<String, Long> statusCounts = countGroupByStatus(true, canaryKey, start, end);
        // D-1: 按receipt_status分组统计，1次查询
        Map<String, Long> receiptCounts = countGroupByReceipt(true, canaryKey, start, end);
        return assembleStatsFromMaps(statusCounts, receiptCounts);
    }

    /**
     * 构建对照组统计：template_code = canaryKey AND canary = 0 AND canary_key IS NULL（未命中灰度）。
     * <p>D-1: 从8次selectCount改为2次selectMaps GROUP BY查询。
     */
    private CanaryReportVO.GroupStats buildControlStats(String canaryKey,
                                                        LocalDateTime start, LocalDateTime end) {
        Map<String, Long> statusCounts = countGroupByStatus(false, canaryKey, start, end);
        Map<String, Long> receiptCounts = countGroupByReceipt(false, canaryKey, start, end);
        return assembleStatsFromMaps(statusCounts, receiptCounts);
    }

    /**
     * D-1: 按status分组统计（GROUP BY），1次查询替代N次selectCount。
     *
     * @param isTreatment true=实验组(canary_key), false=对照组(template_code+canary=0)
     */
    private Map<String, Long> countGroupByStatus(boolean isTreatment, String canaryKey,
                                                  LocalDateTime start, LocalDateTime end) {
        QueryWrapper<MsgLog> wrapper =
                new QueryWrapper<>();
        wrapper.select("status, count(1) as cnt");
        wrapper.ge("created_at", start);
        wrapper.le("created_at", end);
        wrapper.groupBy("status");
        if (isTreatment) {
            wrapper.eq("canary_key", canaryKey);
        } else {
            wrapper.eq("template_code", canaryKey)
                    .eq("canary", 0)
                    .isNull("canary_key");
        }
        List<Map<String, Object>> maps = msgLogMapper.selectMaps(wrapper);
        Map<String, Long> result = new HashMap<>();
        for (Map<String, Object> map : maps) {
            String status = String.valueOf(map.get("status"));
            long cnt = map.get("cnt") != null ? Long.parseLong(String.valueOf(map.get("cnt"))) : 0;
            result.put(status, cnt);
        }
        return result;
    }

    /**
     * D-1: 按receipt_status分组统计（GROUP BY），1次查询替代N次selectCount。
     */
    private Map<String, Long> countGroupByReceipt(boolean isTreatment, String canaryKey,
                                                   LocalDateTime start, LocalDateTime end) {
        QueryWrapper<MsgLog> wrapper =
                new QueryWrapper<>();
        wrapper.select("receipt_status, count(1) as cnt");
        wrapper.ge("created_at", start);
        wrapper.le("created_at", end);
        wrapper.groupBy("receipt_status");
        if (isTreatment) {
            wrapper.eq("canary_key", canaryKey);
        } else {
            wrapper.eq("template_code", canaryKey)
                    .eq("canary", 0)
                    .isNull("canary_key");
        }
        List<Map<String, Object>> maps = msgLogMapper.selectMaps(wrapper);
        Map<String, Long> result = new HashMap<>();
        for (Map<String, Object> map : maps) {
            String receipt = String.valueOf(map.get("receipt_status"));
            long cnt = map.get("cnt") != null ? Long.parseLong(String.valueOf(map.get("cnt"))) : 0;
            result.put(receipt, cnt);
        }
        return result;
    }

    /**
     * D-1: 从分组Map组装统计结果。
     */
    private CanaryReportVO.GroupStats assembleStatsFromMaps(Map<String, Long> statusCounts,
                                                             Map<String, Long> receiptCounts) {
        long total = statusCounts.values().stream().mapToLong(Long::longValue).sum();
        long success = statusCounts.getOrDefault(MessageStatusEnum.SUCCESS.name(), 0L);
        long failed = statusCounts.getOrDefault(MessageStatusEnum.FAILED.name(), 0L);
        long retry = statusCounts.getOrDefault(MessageStatusEnum.RETRY.name(), 0L);
        long dead = statusCounts.getOrDefault(MessageStatusEnum.DEAD.name(), 0L);
        long delivered = receiptCounts.getOrDefault(ReceiptStatusEnum.DELIVERED.name(), 0L);
        long read = receiptCounts.getOrDefault(ReceiptStatusEnum.READ.name(), 0L);
        long clicked = receiptCounts.getOrDefault(ReceiptStatusEnum.CLICKED.name(), 0L);
        return assembleStats(total, success, failed, retry, dead, delivered, read, clicked);
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
