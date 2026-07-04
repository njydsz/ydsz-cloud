package com.njydsz.pmis.project.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.annotation.Idempotent;
import com.njydsz.pmis.common.annotation.PrePermission;
import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.project.dto.CreditAssessmentDTO;
import com.njydsz.pmis.project.entity.CustomerCreditDO;
import com.njydsz.pmis.project.enums.CreditLevel;
import com.njydsz.pmis.project.service.CustomerCreditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 客户信用 Controller
 *
 * <p>负责客户信用评估、等级查询及信用分布统计。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Tag(name = "客户信用")
@RestController
@RequestMapping("/api/v1/execution/credit")
@RequiredArgsConstructor
@Validated
public class CustomerCreditController {

    private final CustomerCreditService service;

    /**
     * 评估客户信用
     *
     * @param dto 信用评估参数
     * @return 客户信用实体
     */
    @Operation(summary = "评估客户信用")
    @PrePermission("finance:credit:assess")
    @Idempotent(key = "customer-credit:create", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/assess")
    public Result<CustomerCreditDO> assess(@Valid @RequestBody CreditAssessmentDTO dto) {
        return Result.ok(service.assess(dto));
    }

    /**
     * 获取客户信用
     *
     * @param customerId 客户 ID
     * @return 客户信用实体
     */
    @Operation(summary = "获取客户信用")
    @PrePermission("finance:credit:list")
    @GetMapping("/customer/{customerId}")
    public Result<CustomerCreditDO> getByCustomer(@PathVariable @Min(1) LongcustomerId) {
        return Result.ok(service.getByCustomer(customerId));
    }

    /**
     * 查询客户风险画像
     *
     * @param customerId 客户 ID
     * @return 风险画像数据
     */
    @Operation(summary = "客户风险画像")
    @PrePermission("finance:credit:list")
    @GetMapping("/profile/{customerId}")
    public Result<Map<String, Object>> profile(@PathVariable @Min(1) LongcustomerId) {
        return Result.ok(service.profile(customerId));
    }

    /**
     * 查询信用分布
     *
     * @return 各信用等级数量列表
     */
    @Operation(summary = "信用分布")
    @PrePermission("finance:credit:list")
    @GetMapping("/distribution")
    public Result<List<Map<String, Object>>> distribution() {
        return Result.ok(service.distribution());
    }

    /**
     * 按等级列出客户信用
     *
     * @param level 信用等级
     * @return 客户信用列表
     */
    @Operation(summary = "按等级列出")
    @PrePermission("finance:credit:list")
    @GetMapping("/by-level")
    public Result<List<CustomerCreditDO>> listByLevel(@RequestParam CreditLevel level) {
        return Result.ok(service.listByLevel(level));
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
    @PrePermission("finance:credit:list")
    @GetMapping("/page")
    public Result<Page<CustomerCreditDO>> page(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Max(100) int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String level) {
        return Result.ok(service.page(page, size, keyword, level));
    }
}
