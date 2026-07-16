package com.njydsz.project.web.controller.execution;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

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

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.project.domain.dto.ApprovalDTO;
import com.njydsz.project.domain.dto.PurchaseCreateDTO;
import com.njydsz.project.domain.entity.PurchaseDO;
import com.njydsz.project.server.service.PurchaseService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * 采购成本 Controller
 *
 * <p>负责采购单创建、审批、状态迁移及分页查询；受预算强管控约束。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Tag(name = "采购成本")
@RestController
@RequestMapping("/api/project/execution/purchase")
@RequiredArgsConstructor
@Validated
public class PurchaseController {

    /** 采购服务 */
    private final PurchaseService service;

    /**
     * 创建采购单
     *
     * @param dto 采购单创建参数
     * @return 新建采购单 ID
     */
    @Operation(summary = "创建采购单")
    @AuthApiPermission(apiCodes = "execution:purchase:create")
    @Idempotent(key = "purchase:create", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping
    public BaseResponse<String> create(@Valid @RequestBody PurchaseCreateDTO dto) {
        return BaseResponse.ok(service.create(dto));
    }

    /**
     * 采购单状态迁移
     *
     * @param dto 审批/状态变更参数
     * @return 空结果
     */
    @Operation(summary = "状态迁移")
    @AuthApiPermission(apiCodes = "execution:purchase:status")
    @Idempotent(key = "purchase:update", ttlSeconds = 5, message = "请勿重复提交")
    @PutMapping("/status")
    public BaseResponse<Void> changeStatus(@Valid @RequestBody ApprovalDTO dto) {
        service.changeStatus(dto);
        return BaseResponse.ok();
    }

    /**
     * 删除采购单
     *
     * @param id 采购单 ID
     * @return 空结果
     */
    @Operation(summary = "删除")
    @AuthApiPermission(apiCodes = "execution:purchase:delete")
    @Idempotent(key = "purchase:delete", ttlSeconds = 5, message = "请勿重复提交")
    @DeleteMapping("/{id}")
    public BaseResponse<Void> delete(@PathVariable String id) {
        service.delete(id);
        return BaseResponse.ok();
    }

    /**
     * 查询采购单详情
     *
     * @param id 采购单 ID
     * @return 采购单实体
     */
    @Operation(summary = "详情")
    @AuthApiPermission(apiCodes = "execution:purchase:list")
    @GetMapping("/{id}")
    public BaseResponse<PurchaseDO> get(@PathVariable String id) {
        return BaseResponse.ok(service.getById(id));
    }

    /**
     * 分页查询采购单
     *
     * @param page         页码（从 1 开始）
     * @param size         每页大小
     * @param keyword      关键词
     * @param status       状态过滤
     * @param initiationId 项目立项 ID
     * @return 分页结果
     */
    @Operation(summary = "分页")
    @AuthApiPermission(apiCodes = "execution:purchase:list")
    @GetMapping("/page")
    public BaseResponse<Page<PurchaseDO>> page(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String initiationId) {
        return BaseResponse.ok(service.page(page, size, keyword, status, initiationId));
    }
}
