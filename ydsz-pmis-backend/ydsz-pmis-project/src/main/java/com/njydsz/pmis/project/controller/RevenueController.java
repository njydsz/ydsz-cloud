package com.njydsz.pmis.project.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.annotation.Idempotent;
import com.njydsz.pmis.common.annotation.PrePermission;
import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.project.dto.RevenueCreateDTO;
import com.njydsz.pmis.project.entity.RevenueDO;
import com.njydsz.pmis.project.service.RevenueService;
import io.swagger.v3.oas.annotations.Operation;
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

import java.util.List;
import java.util.Map;

/**
 * 收入确认 Controller
 *
 * <p>负责收入录入、确认、状态迁移及按项目/合同/周期的聚合查询。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Tag(name = "收入确认")
@RestController
@RequestMapping("/execution/revenue")
@RequiredArgsConstructor
@Validated
public class RevenueController {

    private final RevenueService service;

    /**
     * 录入收入
     *
     * @param dto 收入创建参数
     * @return 新建收入记录 ID
     */
    @Operation(summary = "录入收入")
    @PrePermission("execution:revenue:create")
    @Idempotent(key = "revenue:create", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping
    public Result<Long> create(@Valid @RequestBody RevenueCreateDTO dto) {
        return Result.ok(service.create(dto));
    }

    /**
     * 确认收入
     *
     * @param id          收入记录 ID
     * @param confirmedBy 确认人 ID
     * @return 空结果
     */
    @Operation(summary = "确认收入")
    @PrePermission("execution:revenue:update")
    @Idempotent(key = "revenue:update", ttlSeconds = 5, message = "请勿重复提交")
    @PutMapping("/{id}/confirm")
    public Result<Void> confirm(@PathVariable String id, @RequestParam String confirmedBy) {
        service.confirm(id, confirmedBy);
        return Result.ok();
    }

    /**
     * 冲销收入
     *
     * @param id 收入记录 ID
     * @return 空结果
     */
    @Operation(summary = "冲销收入")
    @PrePermission("execution:revenue:update")
    @PutMapping("/{id}/reverse")
    public Result<Void> reverse(@PathVariable String id) {
        service.reverse(id);
        return Result.ok();
    }

    /**
     * 删除收入
     *
     * @param id 收入记录 ID
     * @return 空结果
     */
    @Operation(summary = "删除")
    @PrePermission("execution:revenue:delete")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        service.delete(id);
        return Result.ok();
    }

    /**
     * 查询收入详情
     *
     * @param id 收入记录 ID
     * @return 收入实体
     */
    @Operation(summary = "详情")
    @PrePermission("execution:revenue:list")
    @GetMapping("/{id}")
    public Result<RevenueDO> get(@PathVariable String id) {
        return Result.ok(service.getById(id));
    }

    /**
     * 分页查询收入
     *
     * @param page         页码（从 1 开始）
     * @param size         每页大小
     * @param keyword      关键词
     * @param status       状态过滤
     * @param contractId   合同 ID
     * @param initiationId 项目立项 ID
     * @param period       所属期间（YYYY-MM）
     * @return 分页结果
     */
    @Operation(summary = "分页")
    @PrePermission("execution:revenue:list")
    @GetMapping("/page")
    public Result<Page<RevenueDO>> page(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String contractId,
            @RequestParam(required = false) Long initiationId,
            @RequestParam(required = false) String period) {
        return Result.ok(service.page(page, size, keyword, status, contractId, initiationId, period));
    }

    /**
     * 按合同汇总收入
     *
     * @param contractId 合同 ID
     * @return 汇总结果列表
     */
    @Operation(summary = "按合同汇总")
    @PrePermission("execution:revenue:list")
    @GetMapping("/aggregate/by-contract")
    public Result<List<Map<String, Object>>> sumByContract(@RequestParam String contractId) {
        return Result.ok(service.sumByContract(contractId));
    }

    /**
     * 按期间汇总收入
     *
     * @param initiationId 项目立项 ID
     * @return 汇总结果列表
     */
    @Operation(summary = "按期间汇总")
    @PrePermission("execution:revenue:list")
    @GetMapping("/aggregate/by-period")
    public Result<List<Map<String, Object>>> sumByPeriod(@RequestParam Long initiationId) {
        return Result.ok(service.sumByPeriod(initiationId));
    }
}
