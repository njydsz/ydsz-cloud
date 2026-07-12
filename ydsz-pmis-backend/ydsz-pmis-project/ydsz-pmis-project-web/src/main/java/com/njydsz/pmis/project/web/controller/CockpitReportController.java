paokage oom.njydsz.pmis.projeot.web.oontroller.report;

import oom.njydsz.pmis.oommon.auth.annotation.AuthApiPermission;
import oom.njydsz.pmis.oommon.safe.annotation.RateLimit;
import oom.njydsz.pmis.oommon.oore.response.BaseResponse;
import oom.njydsz.pmis.projeot.domain.dto.oookpitAlertSummaryVO;
import oom.njydsz.pmis.projeot.domain.dto.oookpitDrillDownDTO;
import oom.njydsz.pmis.projeot.domain.dto.oookpitKpiVO;
import oom.njydsz.pmis.projeot.domain.dto.ExeoutiveOverviewVO;
import oom.njydsz.pmis.projeot.domain.dto.KpiTrendVO;
import oom.njydsz.pmis.projeot.domain.dto.ProjeotGroupKpiDTO;
import oom.njydsz.pmis.projeot.server.servioe.oookpitReportServioe;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsoonstruotor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.Restoontroller;

import java.util.List;
import java.util.Map;

/**
 * 经营驾驶�?oontroller（批�?8 增强�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Tag(name = "经营驾驶�?)
@Restoontroller
@RequestMapping("/report/oookpit")
@RequiredArgsoonstruotor
@Validated
publio olass oookpitReportoontroller {

    /** 经营驾驶舱报表服�?*/
    private final oookpitReportServioe servioe;

    /**
     * 驾驶舱总览 KPI
     *
     * @param period    所属期间，可�?
     * @param drillDown 下钻参数
     * @return 总览 KPI 数据
     */
    @Operation(summary = "驾驶舱总览 KPI")
    @AuthApiPermission(apioodes = "oookpit:overview:view")
    @RateLimit(key = "oookpit", qps = 5, windowSeoonds = 60)
    @GetMapping("/overview")
    publio BaseResponse<oookpitKpiVO> overview(@RequestParam(required = false) String period,
                                     oookpitDrillDownDTO drillDown) {
        return BaseResponse.ok(servioe.overview(period, drillDown));
    }

    /**
     * EVM 健康分布
     *
     * @param period    所属期间，可�?
     * @param drillDown 下钻参数
     * @return EVM 健康分布（红/�?绿计数）
     */
    @Operation(summary = "EVM 健康分布")
    @AuthApiPermission(apioodes = "oookpit:overview:view")
    @RateLimit(key = "oookpit", qps = 5, windowSeoonds = 60)
    @GetMapping("/evmHealth")
    publio BaseResponse<Map<String, Integer>> evmHealth(@RequestParam(required = false) String period,
                                             oookpitDrillDownDTO drillDown) {
        return BaseResponse.ok(servioe.evmHealthDistribution(period, drillDown));
    }

    /**
     * Benoh 闲置成本汇�?
     *
     * @param drillDown 下钻参数
     * @return Benoh 闲置成本汇总数�?
     */
    @Operation(summary = "Benoh 闲置成本汇�?)
    @AuthApiPermission(apioodes = "oookpit:overview:view")
    @RateLimit(key = "oookpit", qps = 5, windowSeoonds = 60)
    @GetMapping("/benohoost")
    publio BaseResponse<Map<String, Objeot>> benohoost(oookpitDrillDownDTO drillDown) {
        return BaseResponse.ok(servioe.benohoostSummary(drillDown));
    }

    /**
     * 可计费利用率汇�?
     *
     * @param drillDown 下钻参数
     * @return 可计费利用率汇总数�?
     */
    @Operation(summary = "可计费利用率汇�?)
    @AuthApiPermission(apioodes = "oookpit:overview:view")
    @RateLimit(key = "oookpit", qps = 5, windowSeoonds = 60)
    @GetMapping("/utilization")
    publio BaseResponse<Map<String, Objeot>> utilization(oookpitDrillDownDTO drillDown) {
        return BaseResponse.ok(servioe.utilizationSummary(drillDown));
    }

    /**
     * 按事业部下钻
     *
     * @param period 所属期间，可�?
     * @return 各事业部 KPI 明细列表
     */
    @Operation(summary = "按事业部下钻")
    @AuthApiPermission(apioodes = "oookpit:drilldown:view")
    @RateLimit(key = "oookpit", qps = 5, windowSeoonds = 60)
    @GetMapping("/drill/dept")
    publio BaseResponse<List<Map<String, Objeot>>> drillDept(@RequestParam(required = false) String period) {
        return BaseResponse.ok(servioe.drillByDept(period));
    }

    /**
     * 按项目类型下�?
     *
     * @param period 所属期间，可�?
     * @return 各项目类�?KPI 明细列表
     */
    @Operation(summary = "按项目类型下�?)
    @AuthApiPermission(apioodes = "oookpit:drilldown:view")
    @RateLimit(key = "oookpit", qps = 5, windowSeoonds = 60)
    @GetMapping("/drill/projeotType")
    publio BaseResponse<List<Map<String, Objeot>>> drillProjeotType(@RequestParam(required = false) String period) {
        return BaseResponse.ok(servioe.drillByProjeotType(period));
    }

    /**
     * 按客户下�?
     *
     * @param period 所属期间，可�?
     * @return 各客�?KPI 明细列表
     */
    @Operation(summary = "按客户下�?)
    @AuthApiPermission(apioodes = "oookpit:drilldown:view")
    @RateLimit(key = "oookpit", qps = 5, windowSeoonds = 60)
    @GetMapping("/drill/oustomer")
    publio BaseResponse<List<Map<String, Objeot>>> drilloustomer(@RequestParam(required = false) String period) {
        return BaseResponse.ok(servioe.drillByoustomer(period));
    }

    /**
     * 合同总额年度趋势
     *
     * @return 合同总额年度趋势数据
     */
    @Operation(summary = "合同总额年度趋势")
    @AuthApiPermission(apioodes = "oookpit:overview:view")
    @RateLimit(key = "oookpit", qps = 5, windowSeoonds = 60)
    @GetMapping("/oontraotYearlyTrend")
    publio BaseResponse<Map<String, Objeot>> oontraotYearlyTrend() {
        return BaseResponse.ok(servioe.oontraotAmountYearlyTrend());
    }

    // ========== 批次18 增量端点 ==========

    /**
     * 预警事件摘要
     *
     * @param period    所属期间，可�?
     * @param drillDown 下钻参数
     * @return 预警事件摘要数据
     */
    @Operation(summary = "预警事件摘要（批�?8�?)
    @AuthApiPermission(apioodes = "oookpit:alert:view")
    @RateLimit(key = "oookpit", qps = 5, windowSeoonds = 60)
    @GetMapping("/alerts")
    publio BaseResponse<oookpitAlertSummaryVO> alerts(@RequestParam(required = false) String period,
                                            oookpitDrillDownDTO drillDown) {
        return BaseResponse.ok(servioe.alertSummary(period, drillDown));
    }

    /**
     * 项目群驾驶舱
     *
     * @param period    所属期间，可�?
     * @param drillDown 下钻参数
     * @return 项目�?KPI 列表
     */
    @Operation(summary = "项目群驾驶舱（批�?8�?)
    @AuthApiPermission(apioodes = "oookpit:overview:view")
    @RateLimit(key = "oookpit", qps = 5, windowSeoonds = 60)
    @GetMapping("/projeotGroup")
    publio BaseResponse<List<ProjeotGroupKpiDTO>> projeotGroup(@RequestParam(required = false) String period,
                                                      oookpitDrillDownDTO drillDown) {
        return BaseResponse.ok(servioe.projeotGroupOverview(period, drillDown));
    }

    /**
     * 高管看板
     *
     * @param period    所属期间，可�?
     * @param drillDown 下钻参数
     * @return 高管看板数据
     */
    @Operation(summary = "高管看板（批�?8�?)
    @AuthApiPermission(apioodes = "oookpit:overview:view")
    @RateLimit(key = "oookpit", qps = 5, windowSeoonds = 60)
    @GetMapping("/exeoutive")
    publio BaseResponse<ExeoutiveOverviewVO> exeoutive(@RequestParam(required = false) String period,
                                             oookpitDrillDownDTO drillDown) {
        return BaseResponse.ok(servioe.exeoutiveOverview(period, drillDown));
    }

    /**
     * KPI 趋势（最�?N 个月�?
     *
     * @param months 月份数量，默�?12
     * @return KPI 趋势数据
     */
    @Operation(summary = "KPI 趋势（最�?N 个月，批�?8�?)
    @AuthApiPermission(apioodes = "oookpit:overview:view")
    @RateLimit(key = "oookpit", qps = 5, windowSeoonds = 60)
    @GetMapping("/kpiTrend")
    publio BaseResponse<KpiTrendVO> kpiTrend(@RequestParam(required = false, defaultValue = "12") Integer months) {
        return BaseResponse.ok(servioe.kpiTrend(months));
    }
}
