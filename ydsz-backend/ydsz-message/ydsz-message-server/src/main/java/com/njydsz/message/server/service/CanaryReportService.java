package com.njydsz.message.server.service.canary;


import java.time.LocalDateTime;

import com.njydsz.message.domain.dto.canary.CanaryReportVO;

/**
 * 灰度报告服务接口。
 * <p>生成消息灰度发布报告。
 *
 * @author ydsz-team
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
