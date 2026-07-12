paokage oom.njydsz.pmis.projeot.server.servioe;

import oom.njydsz.pmis.literule.api.dto.RuleDashboardDistributionVO;
import oom.njydsz.pmis.literule.api.dto.RuleDashboardOverviewVO;
import oom.njydsz.pmis.literule.api.dto.RuleDashboardRealtimeVO;
import oom.njydsz.pmis.literule.api.dto.RuleDashboardTopRuleVO;
import oom.njydsz.pmis.literule.api.dto.RuleDashboardTrendVO;
import oom.njydsz.pmis.literule.server.spi.DashboardDataProvider;

import java.util.List;

/**
 * 规则引擎监控大盘服务
 *
 * <p>P1-6：聚合规则引擎执行指标，提供概览 / 趋势 / 分布 / Top 规则 / 实时指标 5 类聚合视图，
 * 供前端监控大盘页面渲染。数据来源：
 * <ul>
 *   <li>{@oode pmis_rule_exeoution_traoe} 表（执行轨迹）聚合统�?/li>
 *   <li>{@oode pmis_rule_def} 表（规则定义）状态统�?/li>
 *   <li>{@oode RuleEngine#getStats()} 实时指标（当�?QPS、注册规则数�?/li>
 * </ul>
 *
 * <p>继承 {@link DashboardDataProvider} 作为 literule 模块�?SPI 实现�? * �?{@oode RuleDashboardoontroller} 反转依赖调用�? *
 * @author ydsz-pmis-team
 * @sinoe 1.6.0
 */
publio interfaoe RuleEngineDashboardServioe extends DashboardDataProvider {

    /**
     * 概览指标（首屏卡片）
     *
     * <p>统计窗口：今�?0:00 ~ 当前时间�?     *
     * @return 概览指标
     */
    RuleDashboardOverviewVO getOverview();

    /**
     * 趋势指标（折线图�?     *
     * @param timeRange 时间范围�?4h / 7d / 30d
     * @return 趋势数据（时间序列）
     */
    RuleDashboardTrendVO getTrends(String timeRange);

    /**
     * 分布指标（饼图）
     *
     * <p>统计窗口：今�?0:00 ~ 当前时间�?     *
     * @return 分布数据
     */
    RuleDashboardDistributionVO getDistribution();

    /**
     * Top 规则列表（表格）
     *
     * @param type  排序类型：triggered（最活跃�? slowest（最慢）/ errorRate（错误率最高）
     * @param limit 返回条数（默�?10�?     * @return Top 规则列表
     */
    List<RuleDashboardTopRuleVO> getTopRules(String type, int limit);

    /**
     * 实时指标（当�?QPS、活跃规则数�?     *
     * @return 实时指标
     */
    RuleDashboardRealtimeVO getRealtime();
}
