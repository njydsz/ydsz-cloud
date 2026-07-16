package com.njydsz.project.server.service.finance;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 每日自动对账服务（P4-3）
 *
 * <p>对账维度：COST（成本）、REVENUE（收入）、PAYMENT（回款）、
 * INVOICE（开票）、PROFIT（毛利）、LABOR（人力成本）。
 *
 * <p>每天由定时任务触发，跨模块聚合（成本/收入/回款/开票/利润）
 * 校验差异，落库为 OK / WARN / ERROR 记录，差异超阈值触发预警。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface DailyReconcileService {

    /**
     * 运行某天的对账：跨维度聚合
     *
     * @param date 对账日期
     * @return 对账记录条数
     */
    int runDaily(LocalDate date);

    /**
     * 计算单条对账记录：给 expected/actual，返回 status(WARN/ERROR/OK)
     *
     * @param expected 期望值
     * @param actual   实际值
     * @param warnPct  告警百分比阈值
     * @param errorPct 错误百分比阈值
     * @return 状态编码（OK/WARN/ERROR）
     */
    String classify(double expected, double actual, double warnPct, double errorPct);

    /**
     * 落库：按 (date, type, initId) 幂等
     *
     * @param date         对账日期
     * @param type         对账类型
     * @param initiationId 项目立项 ID
     * @param expected     期望值
     * @param actual       实际值
     * @param detail       明细描述
     */
    void upsert(LocalDate date, String type, String initiationId,
                double expected, double actual, String detail);

    /**
     * 按日期范围查询对账记录
     *
     * @param from   起始日期
     * @param to     结束日期
     * @param status 状态过滤（可空）
     * @return 对账记录列表
     */
    List<Map<String, Object>> queryByDateRange(LocalDate from, LocalDate to, String status);

    /**
     * 统计某段时间 ERROR/WARN 数量
     *
     * @param from 起始日期
     * @param to   结束日期
     * @return 状态聚合统计列表
     */
    List<Map<String, Object>> aggregateStatus(LocalDate from, LocalDate to);
}
