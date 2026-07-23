package com.njydsz.project.web.controller;

import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.project.domain.dto.CreditAssessmentDTO;
import com.njydsz.project.domain.entity.CustomerCreditDO;
import com.njydsz.project.domain.enums.CreditLevel;
import com.njydsz.project.server.service.finance.CustomerCreditService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * 客户信用 Controller
 *
 * <p>负责客户信用评估、等级查询及信用分布统计。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Tag(name = "客户信用")
@RestController
@RequestMapping("/api/project/finance/credit")
@RequiredArgsConstructor
@Validated
public class CustomerCreditController {

    /** 客户信用服务 */
    private final CustomerCreditService service;

    /**
     * 评估客户信用
     *
     * @param dto 信用评估参数
     * @return 客户信用实体
     */
    @Operation(summary = "评估客户信用")
    @AuthApiPermission(apiCodes = "finance:credit:assess")
    @Idempotent(key = "customerCredit:create", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/assess")
    public BaseResponse<CustomerCreditDO> assess(@Valid @RequestBody CreditAssessmentDTO dto) {
        return BaseResponse.success(service.assess(dto));
    }

    /**
     * 获取客户信用
     *
     * @param customerId 客户 ID
     * @return 客户信用实体
     */
    @Operation(summary = "获取客户信用")
    @AuthApiPermission(apiCodes = "finance:credit:list")
    @GetMapping("/customer/{customerId}")
    public BaseResponse<CustomerCreditDO> getByCustomer(@PathVariable String customerId) {
        return BaseResponse.success(service.getByCustomer(customerId));
    }

    /**
     * 查询客户风险画像
     *
     * @param customerId 客户 ID
     * @return 风险画像数据
     */
    @Operation(summary = "客户风险画像")
    @AuthApiPermission(apiCodes = "finance:credit:list")
    @GetMapping("/profile/{customerId}")
    public BaseResponse<Map<String, Object>> profile(@PathVariable String customerId) {
        return BaseResponse.success(service.profile(customerId));
    }

    /**
     * 查询信用分布
     *
     * @return 各信用等级数量列表
     */
    @Operation(summary = "信用分布")
    @AuthApiPermission(apiCodes = "finance:credit:list")
    @GetMapping("/distribution")
    public BaseResponse<List<Map<String, Object>>> distribution() {
        return BaseResponse.success(service.distribution());
    }

    /**
     * 按等级列出客户信用
     *
     * @param level 信用等级
     * @return 客户信用列表
     */
    @Operation(summary = "按等级列出")
    @AuthApiPermission(apiCodes = "finance:credit:list")
    @GetMapping("/byLevel")
    public BaseResponse<List<CustomerCreditDO>> listByLevel(@RequestParam CreditLevel level) {
        return BaseResponse.success(service.listByLevel(level));
    }

    /**
     * 分页查询客户信用
     *
     * @param page    页码（从 1 开始）
     * @param size    每页大小
     * @param keyword 关键词
     * @param level   信用等级过滤
     * @return 分页结果
     */
    @Operation(summary = "分页")
    @AuthApiPermission(apiCodes = "finance:credit:list")
    @GetMapping("/page")
    public BaseResponse<Page<CustomerCreditDO>> page(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String level) {
        return BaseResponse.success(service.page(page, size, keyword, level));
    }
}
