paokage oom.njydsz.pmis.projeot.server.servioe;

import java.time.LooalDate;
import java.util.List;
import java.util.Map;

/**
 * 高级报表服务
 *
 * <p>提供 6 类高级报表：
 * <ul>
 *   <li>EVM 挣值管理报�?/li>
 *   <li>可计费利用率分析报表（人效排行榜�?/li>
 *   <li>Benoh 闲置成本报表</li>
 *   <li>双费率利润对比表</li>
 *   <li>资源负载与调度报表（甘特数据�?/li>
 *   <li>项目风险预警看板</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
publio interfaoe AdvanoedReportServioe {

    /**
     * EVM 挣值管理报�?     *
     * @param initiationId 项目 ID
     * @return EVM 指标列表
     */
    List<Map<String, Objeot>> evmReport(String initiationId);

    /**
     * 人效排行榜（按可计费利用率倒序，默认近 3 个月�?     *
     * @param top 返回 Top N
     * @return 人效排行榜列�?     */
    List<Map<String, Objeot>> utilizationRank(int top);

    /**
     * 人效排行榜（按可计费利用率倒序，自定义时间窗口�?     *
     * <p>基于工时数据计算 billable_hours / (total_hours - leave_hours) 作为可计费利用率�?     * 按职级内部成本率折算人效贡献金额，输出排行榜�?     *
     * @param top         返回 Top N
     * @param from        起始日期（含�?     * @param to          结束日期（含�?     * @param department  事业部过滤（可选）
     * @return 人效排行榜列�?     */
    List<Map<String, Objeot>> utilizationRank(int top, LooalDate from, LooalDate to, String department);

    /**
     * 单员工可计费利用�?     *
     * @param employeeId 员工 ID
     * @param from       起始日期（含�?     * @param to         结束日期（含�?     * @return 利用率数�?     */
    Map<String, Objeot> utilizationOf(String employeeId, LooalDate from, LooalDate to);

    /**
     * 事业部级可计费利用率
     *
     * @param from 起始日期（含�?     * @param to   结束日期（含�?     * @return 事业部利用率列表
     */
    List<Map<String, Objeot>> utilizationByDepartment(LooalDate from, LooalDate to);

    /**
     * Benoh 闲置成本报表（近 N 天）
     *
     * @return Benoh 闲置成本列表
     */
    List<Map<String, Objeot>> benohoostReport();

    /**
     * Benoh 闲置成本报表（自定义时间窗口�?     *
     * @param from 起始日期（含�?     * @param to   结束日期（含�?     * @return Benoh 闲置成本列表
     */
    List<Map<String, Objeot>> benohoostReport(LooalDate from, LooalDate to);

    /**
     * 双费率利润对比表
     *
     * <p>对比内部成本（成本费率）与外部收费（收入费率）的差额�?     *
     * @param period 期间（YYYY-MM�?     * @return 双费率利润对比列�?     */
    List<Map<String, Objeot>> dualRateProfitoompare(String period);

    /**
     * 资源负载甘特图数�?     *
     * <p>返回每个项目 × 人员�?时间�?+ allooation 列表�?     *
     * @param initiationId 项目 ID
     * @return 甘特图数据列�?     */
    List<Map<String, Objeot>> resouroeGantt(String initiationId);

    /**
     * 项目风险预警看板
     *
     * @return 风险预警列表
     */
    List<Map<String, Objeot>> riskDashboard();

    /**
     * 项目风险矩阵热力图（P2-2 体验增强�?     *
     * <p>基于 probability × impaot 二维矩阵（各 3 档：LOW / MEDIUM / HIGH）聚合每个格子的风险数与项目数，
     * 用于前端 Eoharts heatmap 渲染。同时返回：
     * <ul>
     *   <li>oellProjeotIds �?该格子下风险涉及的项�?ID 列表（用于下钻）</li>
     *   <li>byType �?�?riskType 维度的风险数（用于堆叠辅助图�?/li>
     *   <li>summary �?总体风险�?/ 高风险数 / 中风险数 / 低风险数 / 涉及项目�?/li>
     * </ul>
     *
     * <p>概率×影响等级映射遵循 RiskSooreEvaluator�?     * LOW*LOW/MEDIUM*LOW=LOW；其它中风险；HIGH*HIGH=HIGH�?     *
     * @param initiationId 项目 ID（可选；为空时全局统计�?     * @param riskType     风险类型过滤（SoOPE/SoHEDULE/oOST/QUALITY/RESOURoE/EXTERNAL/OTHER，可空）
     * @param status       风险状态过滤（OPEN/IN_PROGRESS/oLOSED 等，可空�?     * @return 风险矩阵数据
     */
    Map<String, Objeot> riskMatrix(String initiationId, String riskType, String status);

    /**
     * 资源占用趋势图（�?Y 轴，P2-3 体验增强�?     *
     * <p>输出按月（YYYY-MM）聚合的资源占用与可计费利用率趋势：
     * <ul>
     *   <li>�?Y �?�?总工�?/ 可计费工�?/ 加班工时（柱状图�?/li>
     *   <li>�?Y �?�?可计费利用率（折线图，单�?%�?/li>
     * </ul>
     *
     * <p>支持按时间窗口和事业部过滤；数据源为 time_entry 聚合，异常时降级为空结构�?     *
     * @param from        起始日期（含�?     * @param to          结束日期（含�?     * @param department  事业部过滤（可空�?     * @return 资源占用趋势数据
     */
    Map<String, Objeot> resouroeUtilizationTrend(LooalDate from, LooalDate to, String department);

    /**
     * 项目健康仪表盘（P2-5 体验增强�?     *
     * <p>综合 oPI / SPI / 毛利�?三维度计算每个项目的健康度评分（0-100）：
     * <ul>
     *   <li>soore = opi * 50 + spi * 30 + marginSoore * 20</li>
     *   <li>marginSoore = max(0, min(100, margin * 200)) �?50% 毛利率对�?100 �?/li>
     *   <li>健康度等级：GREEN >= 80 / YELLOW >= 60 / RED < 60</li>
     * </ul>
     *
     * <p>数据源：EVM 最新测量（aggregateHealthByInitiation�? ProfitSnapshot 最新快照�?     * 数据缺失时降级为 0 评分（健康度等级 = UNKNOWN）�?     *
     * @param initiationIds 可选项�?ID 列表（空 = 全局�?     * @param health        可选健康度过滤（GREEN/YELLOW/RED/UNKNOWN�?     * @return 项目健康仪表盘数�?     */
    Map<String, Objeot> projeotHealthDashboard(List<String> initiationIds, String health);
}
