package com.njydsz.pmis.sales.web.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.annotation.Idempotent;
import com.njydsz.pmis.common.annotation.PrePermission;
import com.njydsz.pmis.common.core.response.BaseResponse;
import com.njydsz.pmis.sales.domain.dto.OpportunityCreateDTO;
import com.njydsz.pmis.sales.domain.dto.OpportunityStatusDTO;
import com.njydsz.pmis.sales.domain.dto.OpportunityUpdateDTO;
import com.njydsz.pmis.sales.domain.entity.OpportunityDO;
import com.njydsz.pmis.sales.server.service.opportunity.OpportunityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 商机 Controller
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Tag(name = "商机管理", description = "商机管理相关接口")
@RestController
@RequestMapping("/opportunity")
@RequiredArgsConstructor
@Validated
public class OpportunityController {

    /** 商机服务 */
    private final OpportunityService service;

    /**
     * 创建商机。
     *
     * @param dto 商机创建参数
     * @return 统一响应结果，包含商机 ID
     */
    @Operation(summary = "创建商机")
    @PrePermission("project:opportunity:create")
    @Idempotent(key = "opportunity:create", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping
    public BaseResponse<String> create(@Valid @RequestBody OpportunityCreateDTO dto) {
        return BaseResponse.ok(service.create(dto));
    }

    /**
     * 更新商机。
     *
     * @param dto 商机更新参数
     * @return 统一响应结果
     */
    @Operation(summary = "更新商机")
    @PrePermission("project:opportunity:update")
    @Idempotent(key = "opportunity:update", ttlSeconds = 5, message = "请勿重复提交")
    @PutMapping("/{id}")
    public BaseResponse<Void> update(@Parameter(description = "商机ID") @PathVariable String id,
                               @Valid @RequestBody OpportunityUpdateDTO dto) {
        dto.setId(id);
        service.update(dto);
        return BaseResponse.ok();
    }

    /**
     * 变更商机状态。
     *
     * @param dto 状态变更参数
     * @return 统一响应结果
     */
    @Operation(summary = "变更状态")
    @PrePermission("project:opportunity:update")
    @Idempotent(key = "opportunity:changeStatus", ttlSeconds = 5, message = "请勿重复提交")
    @PutMapping("/status")
    public BaseResponse<Void> changeStatus(@Valid @RequestBody OpportunityStatusDTO dto) {
        service.changeStatus(dto);
        return BaseResponse.ok();
    }

    /**
     * 删除商机。
     *
     * @param id 商机 ID
     * @return 统一响应结果
     */
    @Operation(summary = "删除商机")
    @PrePermission("project:opportunity:delete")
    @Idempotent(key = "opportunity:delete", ttlSeconds = 5, message = "请勿重复提交")
    @DeleteMapping("/{id}")
    public BaseResponse<Void> delete(@Parameter(description = "商机ID") @PathVariable String id) {
        service.delete(id);
        return BaseResponse.ok();
    }

    /**
     * 查询商机详情。
     *
     * @param id 商机 ID
     * @return 统一响应结果，包含商机详情
     */
    @Operation(summary = "商机详情")
    @PrePermission("project:opportunity:list")
    @GetMapping("/{id}")
    public BaseResponse<OpportunityDO> get(@Parameter(description = "商机ID") @PathVariable String id) {
        return BaseResponse.ok(service.getById(id));
    }

    /**
     * 分页查询商机列表。
     *
     * @param page     页码（从 1 开始）
     * @param size     每页大小
     * @param keyword  关键词（编号/名称），可空
     * @param status   状态过滤，可空
     * @param level    分级过滤，可空
     * @param ownerId  负责人 ID 过滤，可空
     * @return 统一响应结果，包含商机分页数据
     */
    @Operation(summary = "分页查询")
    @PrePermission("project:opportunity:list")
    @GetMapping("/page")
    public BaseResponse<Page<OpportunityDO>> page(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") @Min(1) int page,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @Parameter(description = "关键词") @RequestParam(required = false) String keyword,
            @Parameter(description = "状态") @RequestParam(required = false) String status,
            @Parameter(description = "分级") @RequestParam(required = false) String level,
            @Parameter(description = "负责人ID") @RequestParam(required = false) String ownerId) {
        return BaseResponse.ok(service.page(page, size, keyword, status, level, ownerId));
    }

    /**
     * 评估并更新商机赢率。
     *
     * @param id             商机 ID
     * @param customerCredit 客户信用等级（可选）
     * @param hasHistory     是否有历史合作（默认 false）
     * @return 统一响应结果，包含评估后的赢率
     */
    @Operation(summary = "评估并更新赢率")
    @PrePermission("project:opportunity:evaluate")
    @Idempotent(key = "opportunity:evaluateWinRate", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/{id}/evaluateWinrate")
    public BaseResponse<BigDecimal> evaluateWinRate(@Parameter(description = "商机ID") @PathVariable String id,
                                         @Parameter(description = "客户信用") @RequestParam(required = false) String customerCredit,
                                         @Parameter(description = "是否有历史合作") @RequestParam(defaultValue = "false") boolean hasHistory) {
        return BaseResponse.ok(service.evaluateWinRate(id, customerCredit, hasHistory));
    }

    /**
     * 按状态聚合计数。
     *
     * @param tenantId 租户 ID，可空
     * @return 统一响应结果，包含各状态对应的数量列表
     */
    @Operation(summary = "按状态聚合")
    @PrePermission("project:opportunity:list")
    @GetMapping("/aggregate/status")
    public BaseResponse<List<Map<String, Object>>> aggregateByStatus(@Parameter(description = "租户ID") @RequestParam(required = false) String tenantId) {
        return BaseResponse.ok(service.aggregateByStatus(tenantId));
    }

    /**
     * 按分级聚合计数。
     *
     * @param tenantId 租户 ID，可空
     * @return 统一响应结果，包含各分级对应的数量列表
     */
    @Operation(summary = "按分级聚合")
    @PrePermission("project:opportunity:list")
    @GetMapping("/aggregate/level")
    public BaseResponse<List<Map<String, Object>>> aggregateByLevel(@Parameter(description = "租户ID") @RequestParam(required = false) String tenantId) {
        return BaseResponse.ok(service.aggregateByLevel(tenantId));
    }

    /**
     * 商机转立项自动化（WON -> CONVERTED + 创建预立项草稿）。
     *
     * @param id        商机 ID
     * @param sponsorId 发起人 ID（可选）
     * @param pmId      项目经理 ID（可选）
     * @return 统一响应结果，包含预立项草稿 ID
     */
    @Operation(summary = "商机转立项自动化(WON -> CONVERTED + 创建预立项草稿)")
    @PrePermission("project:opportunity:convert")
    @Idempotent(key = "opportunity:convertToInitiation", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/{id}/convertToInitiation")
    public BaseResponse<String> convertToInitiation(@Parameter(description = "商机ID") @PathVariable String id,
                                        @Parameter(description = "发起人ID") @RequestParam(required = false) String sponsorId,
                                        @Parameter(description = "项目经理ID") @RequestParam(required = false) String pmId) {
        return BaseResponse.ok(service.convertToInitiation(id, sponsorId, pmId));
    }
}
