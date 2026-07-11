package com.njydsz.pmis.sales.web.controller;

import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.sales.infra.mapper.ContractMapper;
import com.njydsz.pmis.sales.infra.mapper.OpportunityMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 商务数据查询 Controller（内部接口）
 *
 * <p>供 PM/Finance 模块通过 {@link com.njydsz.pmis.sales.api.client.SalesDataClient} 跨域调用，
 * 暴露合同/商机等聚合数据查询能力。
 *
 * @author ydsz-pmis-team
 * @since 2.0.0
 */
@Slf4j
@RestController
@RequestMapping("/sales/data")
@RequiredArgsConstructor
@Tag(name = "商务数据查询", description = "内部跨域数据查询接口")
public class SalesDataController {

    private final ContractMapper contractMapper;
    private final OpportunityMapper opportunityMapper;

    @GetMapping("/contract/sumAmount")
    @Operation(summary = "合同总金额")
    public Result<BigDecimal> sumContractAmount() {
        try {
            return Result.ok(nz(contractMapper.sumAllAmount()));
        } catch (Exception e) {
            log.error("[SalesData] sumContractAmount 失败: {}", e.getMessage());
            return Result.ok(BigDecimal.ZERO);
        }
    }

    @GetMapping("/contract/sumByInitiation")
    @Operation(summary = "按项目查询合同金额")
    public Result<BigDecimal> sumContractAmountByInitiation(@RequestParam("initiationId") String initiationId) {
        try {
            return Result.ok(nz(contractMapper.sumByInitiation(initiationId)));
        } catch (Exception e) {
            log.error("[SalesData] sumContractAmountByInitiation 失败: {}", e.getMessage());
            return Result.ok(BigDecimal.ZERO);
        }
    }

    @GetMapping("/contract/sumByCustomer")
    @Operation(summary = "按客户统计合同金额")
    public Result<List<Map<String, Object>>> sumContractByCustomer() {
        try {
            return Result.ok(contractMapper.sumByCustomer());
        } catch (Exception e) {
            log.error("[SalesData] sumContractByCustomer 失败: {}", e.getMessage());
            return Result.ok(List.of());
        }
    }

    @GetMapping("/contract/sumByYear")
    @Operation(summary = "按年度统计合同金额")
    public Result<List<Map<String, Object>>> sumContractByYear() {
        try {
            return Result.ok(contractMapper.sumByYear());
        } catch (Exception e) {
            log.error("[SalesData] sumContractByYear 失败: {}", e.getMessage());
            return Result.ok(List.of());
        }
    }

    @GetMapping("/contract/sumByRecentMonth")
    @Operation(summary = "按最近月份统计合同金额")
    public Result<List<Map<String, Object>>> sumContractByRecentMonth(@RequestParam("limit") Integer limit) {
        try {
            return Result.ok(contractMapper.sumByRecentMonth(limit));
        } catch (Exception e) {
            log.error("[SalesData] sumContractByRecentMonth 失败: {}", e.getMessage());
            return Result.ok(List.of());
        }
    }

    @GetMapping("/opportunity/count")
    @Operation(summary = "商机总数")
    public Result<Integer> countOpportunities() {
        try {
            return Result.ok(opportunityMapper.selectCount(null).intValue());
        } catch (Exception e) {
            log.error("[SalesData] countOpportunities 失败: {}", e.getMessage());
            return Result.ok(0);
        }
    }

    @GetMapping("/contract/sumByProjectType")
    @Operation(summary = "按项目类型统计合同金额")
    public Result<List<Map<String, Object>>> sumContractByProjectType() {
        try {
            return Result.ok(contractMapper.sumByProjectType());
        } catch (Exception e) {
            log.error("[SalesData] sumContractByProjectType 失败: {}", e.getMessage());
            return Result.ok(List.of());
        }
    }

    private BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
