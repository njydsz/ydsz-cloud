package com.njydsz.project.web.controller;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.project.infra.mapper.ContractMapper;
import com.njydsz.project.infra.mapper.OpportunityMapper;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 商务数据查询 Controller（内部接口）
 *
 * <p>sales/finance 模块已合并到 project 模块，原 Feign 跨域调用已下线，
 * 现直接暴露合同/商机等聚合数据查询能力供同进程调用。
 *
 * @author ydsz-team
 * @since 2.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/project/sales/data")
@RequiredArgsConstructor
@Tag(name = "商务数据查询", description = "内部跨域数据查询接口")
public class SalesDataController {

    private final ContractMapper contractMapper;
    private final OpportunityMapper opportunityMapper;

    @GetMapping("/contract/sumAmount")
    @Operation(summary = "合同总金额")
    public BaseResponse<BigDecimal> sumContractAmount() {
        try {
            return BaseResponse.ok(nz(contractMapper.sumAllAmount()));
        } catch (Exception e) {
            log.error("[SalesData] sumContractAmount 失败: {}", e.getMessage());
            return BaseResponse.ok(BigDecimal.ZERO);
        }
    }

    @GetMapping("/contract/sumByInitiation")
    @Operation(summary = "按项目查询合同金额")
    public BaseResponse<BigDecimal> sumContractAmountByInitiation(@RequestParam("initiationId") String initiationId) {
        try {
            return BaseResponse.ok(nz(contractMapper.sumByInitiation(initiationId)));
        } catch (Exception e) {
            log.error("[SalesData] sumContractAmountByInitiation 失败: {}", e.getMessage());
            return BaseResponse.ok(BigDecimal.ZERO);
        }
    }

    @GetMapping("/contract/sumByCustomer")
    @Operation(summary = "按客户统计合同金额")
    public BaseResponse<List<Map<String, Object>>> sumContractByCustomer() {
        try {
            return BaseResponse.ok(contractMapper.sumByCustomer());
        } catch (Exception e) {
            log.error("[SalesData] sumContractByCustomer 失败: {}", e.getMessage());
            return BaseResponse.ok(List.of());
        }
    }

    @GetMapping("/contract/sumByYear")
    @Operation(summary = "按年度统计合同金额")
    public BaseResponse<List<Map<String, Object>>> sumContractByYear() {
        try {
            return BaseResponse.ok(contractMapper.sumByYear());
        } catch (Exception e) {
            log.error("[SalesData] sumContractByYear 失败: {}", e.getMessage());
            return BaseResponse.ok(List.of());
        }
    }

    @GetMapping("/contract/sumByRecentMonth")
    @Operation(summary = "按最近月份统计合同金额")
    public BaseResponse<List<Map<String, Object>>> sumContractByRecentMonth(@RequestParam("limit") Integer limit) {
        try {
            return BaseResponse.ok(contractMapper.sumByRecentMonth(limit));
        } catch (Exception e) {
            log.error("[SalesData] sumContractByRecentMonth 失败: {}", e.getMessage());
            return BaseResponse.ok(List.of());
        }
    }

    @GetMapping("/opportunity/count")
    @Operation(summary = "商机总数")
    public BaseResponse<Integer> countOpportunities() {
        try {
            return BaseResponse.ok(opportunityMapper.selectCount(null).intValue());
        } catch (Exception e) {
            log.error("[SalesData] countOpportunities 失败: {}", e.getMessage());
            return BaseResponse.ok(0);
        }
    }

    @GetMapping("/contract/sumByProjectType")
    @Operation(summary = "按项目类型统计合同金额")
    public BaseResponse<List<Map<String, Object>>> sumContractByProjectType() {
        try {
            return BaseResponse.ok(contractMapper.sumByProjectType());
        } catch (Exception e) {
            log.error("[SalesData] sumContractByProjectType 失败: {}", e.getMessage());
            return BaseResponse.ok(List.of());
        }
    }

    private BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
