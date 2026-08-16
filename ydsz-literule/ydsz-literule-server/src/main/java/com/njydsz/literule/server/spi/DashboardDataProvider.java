package com.njydsz.literule.server.spi;

import java.util.List;
import com.njydsz.literule.api.dto.RuleDashboardDistributionVO;
import com.njydsz.literule.api.dto.RuleDashboardOverviewVO;
import com.njydsz.literule.api.dto.RuleDashboardRealtimeVO;
import com.njydsz.literule.api.dto.RuleDashboardTopRuleVO;
import com.njydsz.literule.api.dto.RuleDashboardTrendVO;

/**
 * 规则引擎监控大盘数据提供者 SPI
 *
 * <p>由消费方（如 project 模块）提供实现，聚合规则引擎执行指标，
 * 提供概览 / 趋势 / 分布 / Top 规则 / 实时指标 5 类聚合视图。
 * literule 模块的 {@code RuleDashboardController} 通过此接口反转依赖，
 * 避免直接依赖 project 模块的服务实现。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
public interface DashboardDataProvider {

    /**
     * 概览指标（首屏卡片）
     *
     * <p>统计窗口：今日 0:00 ~ 当前时间。
     *
     * @return 概览指标
     */
    RuleDashboardOverviewVO getOverview();

    /**
     * 趋势指标（折线图）
     *
     * @param timeRange 时间范围：24h / 7d / 30d
     * @return 趋势数据（时间序列）
     */
    RuleDashboardTrendVO getTrends(String timeRange);

    /**
     * 分布指标（饼图）
     *
     * <p>统计窗口：今日 0:00 ~ 当前时间。
     *
     * @return 分布数据
     */
    RuleDashboardDistributionVO getDistribution();

    /**
     * Top 规则列表（表格）
     *
     * @param type  排序类型：triggered（最活跃）/ slowest（最慢）/ errorRate（错误率最高）
     * @param limit 返回条数（默认 10）
     * @return Top 规则列表
     */
    List<RuleDashboardTopRuleVO> getTopRules(String type, int limit);

    /**
     * 实时指标（当前 QPS、活跃规则数）
     *
     * @return 实时指标
     */
    RuleDashboardRealtimeVO getRealtime();
}
