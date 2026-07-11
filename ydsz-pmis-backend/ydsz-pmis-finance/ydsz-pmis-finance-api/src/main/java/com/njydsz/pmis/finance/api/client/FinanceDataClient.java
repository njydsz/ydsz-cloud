package com.njydsz.pmis.finance.api.client;
import com.njydsz.pmis.common.feign.FeignClientConstants;
import com.njydsz.pmis.finance.api.fallback.FinanceDataClientFallback;

import com.njydsz.pmis.common.api.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 财务数据查询 Feign 客户端（供 PM 模块跨域查询财务数据）
 *
 * <p>PM 模块的报表/驾驶舱服务通过此客户端查询发票、回款、费用等财务聚合数据，
 * 替代原有的直接注入跨域 Mapper 的方式，实现模块间解耦。
 *
 * <p>所有方法均配 {@link FinanceDataClientFallback} 降级，财务服务不可用时返回零值，
 * 避免报表聚合场景级联失败。
 *
 * @author ydsz-pmis-team
 * @since 2.0.0
 */
@FeignClient(
        name = FeignClientConstants.FINANCE,
        contextId = "financeDataClient",
        fallbackFactory = FinanceDataClientFallback.class
)
public interface FinanceDataClient {

    /**
     * 发票总金额
     *
     * @return 发票总金额
     */
    @GetMapping("/finance/data/invoice/sumAmount")
    Result<BigDecimal> sumInvoiceAmount();

    /**
     * 已分配回款总金额
     *
     * @return 已分配回款总金额
     */
    @GetMapping("/finance/data/payment/sumAllocated")
    Result<BigDecimal> sumAllocatedPayment();

    /**
     * 费用报销总金额
     *
     * @return 费用报销总金额
     */
    @GetMapping("/finance/data/expense/sumAmount")
    Result<BigDecimal> sumExpenseAmount();

    /**
     * 按项目统计独立立项数（有发票记录的）
     *
     * @return 独立立项数
     */
    @GetMapping("/finance/data/invoice/countDistinctInitiation")
    Result<Integer> countDistinctInitiation();

    /**
     * 按部门统计发票金额
     *
     * @return 部门维度发票金额列表
     */
    @GetMapping("/finance/data/invoice/sumByDepartment")
    Result<List<Map<String, Object>>> sumInvoiceByDepartment();

    /**
     * 按项目类型统计发票金额
     *
     * @return 项目类型维度发票金额列表
     */
    @GetMapping("/finance/data/invoice/sumByProjectType")
    Result<List<Map<String, Object>>> sumInvoiceByProjectType();

    /**
     * 按客户统计发票金额
     *
     * @return 客户维度发票金额列表
     */
    @GetMapping("/finance/data/invoice/sumByCustomer")
    Result<List<Map<String, Object>>> sumInvoiceByCustomer();

    /**
     * 按年度统计发票金额
     *
     * @return 年度维度发票金额列表
     */
    @GetMapping("/finance/data/invoice/sumByYear")
    Result<List<Map<String, Object>>> sumInvoiceByYear();

    /**
     * 按最近月份统计发票金额
     *
     * @param limit 月份数量
     * @return 月度维度发票金额列表
     */
    @GetMapping("/finance/data/invoice/sumByRecentMonth")
    Result<List<Map<String, Object>>> sumInvoiceByRecentMonth(@RequestParam("limit") Integer limit);

    /**
     * 按最近月份统计回款金额
     *
     * @param limit 月份数量
     * @return 月度维度回款金额列表
     */
    @GetMapping("/finance/data/payment/aggregateByRecentMonth")
    Result<List<Map<String, Object>>> aggregatePaymentByRecentMonth(@RequestParam("limit") Integer limit);

    /**
     * 按项目查询收入总额
     *
     * @param initiationId 立项 ID
     * @param period       期间（可为空）
     * @return 收入总额
     */
    @GetMapping("/finance/data/revenue/sumByInitiation")
    Result<BigDecimal> sumRevenue(@RequestParam("initiationId") String initiationId,
                                   @RequestParam(value = "period", required = false) String period);

    /**
     * 按项目查询费用总额
     *
     * @param initiationId 立项 ID
     * @param period       期间（可为空）
     * @return 费用总额
     */
    @GetMapping("/finance/data/expense/sumByInitiation")
    Result<BigDecimal> sumExpense(@RequestParam("initiationId") String initiationId,
                                   @RequestParam(value = "period", required = false) String period);

    /**
     * 按项目查询利润快照
     *
     * @param initiationId 立项 ID
     * @param period       期间（可为空）
     * @return 利润快照 Map
     */
    @GetMapping("/finance/data/profitSnapshot/latest")
    Result<Map<String, Object>> latestProfitSnapshot(@RequestParam("initiationId") String initiationId,
                                                       @RequestParam(value = "period", required = false) String period);

    /**
     * 利润快照汇总（全量，按快照时间倒序，最多 200 条）
     *
     * @return 利润快照列表
     */
    @GetMapping("/finance/data/profitSnapshot/summaryAll")
    Result<List<Map<String, Object>>> profitSnapshotSummaryAll();

    /**
     * 利润排名（按毛利率/利润额排序）
     *
     * @param top   Top N
     * @param sortBy 排序字段
     * @param period 期间（可为空）
     * @return 排名列表
     */
    @GetMapping("/finance/data/profitSnapshot/rank")
    Result<List<Map<String, Object>>> profitSnapshotRank(@RequestParam("top") Integer top,
                                                          @RequestParam("sortBy") String sortBy,
                                                          @RequestParam(value = "period", required = false) String period);

    /**
     * 按项目查询收入明细列表
     *
     * @param initiationId 立项 ID
     * @return 收入明细列表
     */
    @GetMapping("/finance/data/revenue/selectByInitiation")
    Result<List<Map<String, Object>>> revenueByInitiation(@RequestParam("initiationId") String initiationId);

    /**
     * 按项目查询收入期间汇总
     *
     * @param initiationId 立项 ID
     * @return 期间汇总列表
     */
    @GetMapping("/finance/data/revenue/sumByPeriod")
    Result<List<Map<String, Object>>> revenueSumByPeriod(@RequestParam("initiationId") String initiationId);
}
