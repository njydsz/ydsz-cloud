package com.njydsz.pmis.execution.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.annotation.PrePermission;
import com.njydsz.pmis.common.api.R;
import com.njydsz.pmis.execution.dto.CreditAssessmentDTO;
import com.njydsz.pmis.execution.entity.CustomerCreditDO;
import com.njydsz.pmis.execution.enums.CreditLevel;
import com.njydsz.pmis.execution.service.CustomerCreditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
public class CustomerCreditController {

    private final CustomerCreditService service;

    @Operation(summary = "评估客户信用")
    @PrePermission("finance:credit:assess")
    @PostMapping("/assess")
    public R<CustomerCreditDO> assess(@Valid @RequestBody CreditAssessmentDTO dto) {
        return R.ok(service.assess(dto));
    }

    @Operation(summary = "获取客户信用")
    @PrePermission("finance:credit:list")
    @GetMapping("/customer/{customerId}")
    public R<CustomerCreditDO> getByCustomer(@PathVariable Long customerId) {
        return R.ok(service.getByCustomer(customerId));
    }

    @Operation(summary = "客户风险画像")
    @PrePermission("finance:credit:list")
    @GetMapping("/profile/{customerId}")
    public R<Map<String, Object>> profile(@PathVariable Long customerId) {
        return R.ok(service.profile(customerId));
    }

    @Operation(summary = "信用分布")
    @PrePermission("finance:credit:list")
    @GetMapping("/distribution")
    public R<List<Map<String, Object>>> distribution() {
        return R.ok(service.distribution());
    }

    @Operation(summary = "按等级列出")
    @PrePermission("finance:credit:list")
    @GetMapping("/by-level")
    public R<List<CustomerCreditDO>> listByLevel(@RequestParam CreditLevel level) {
        return R.ok(service.listByLevel(level));
    }

    @Operation(summary = "分页")
    @PrePermission("finance:credit:list")
    @GetMapping("/page")
    public R<Page<CustomerCreditDO>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String level) {
        return R.ok(service.page(page, size, keyword, level));
    }
}
