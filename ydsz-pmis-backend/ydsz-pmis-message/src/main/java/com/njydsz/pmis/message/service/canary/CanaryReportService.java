package com.njydsz.pmis.message.service.canary;


import com.njydsz.pmis.message.dto.canary.CanaryReportVO;

import java.time.LocalDateTime;

/**
 * 灰度 A/B 报表服务（P1-6）。
 *
 * <p>基于 {@code pmis_msg_log} 表的 {@code canary_key} / {@code template_code} / {@code canary} 字段
 * 聚合统计对照组与实验组的发送/回执指标,供运营对比实验效果。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public interface CanaryReportService {

    /**
     * 获取灰度 A/B 实验报表。
     *
     * <p>实验组 = {@code canary_key = canaryKey}（命中灰度,已切换实验模板/通道）；
     * 对照组 = {@code template_code = canaryKey AND canary = 0 AND canary_key IS NULL}（未命中,使用基线模板）。
     *
     * @param canaryKey 灰度键（原始模板编码），不可为空
     * @param start     起始时间（含），null 则默认最近 7 天
     * @param end       结束时间（含），null 则当前时间
     * @return A/B 报表（含对照组与实验组统计）
     */
    CanaryReportVO getReport(String canaryKey, LocalDateTime start, LocalDateTime end);
}
