package com.njydsz.pmis.project.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.annotation.Idempotent;
import com.njydsz.pmis.common.annotation.PrePermission;
import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.project.dto.ProfitSimulationCreateDTO;
import com.njydsz.pmis.project.dto.SimulationStatusDTO;
import com.njydsz.pmis.project.entity.ProfitSimulationDO;
import com.njydsz.pmis.project.service.ProfitSimulationService;
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
 * 利润测算 Controller
 *
 * <p>负责利润测算版本的创建、状态迁移、多版本对比及分页查询。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Tag(name = "利润测算")
@RestController
@RequestMapping("/api/v1/execution/profit-simulation")
@RequiredArgsConstructor
@Validated
public class ProfitSimulationController {

    private final ProfitSimulationService service;

    /**
     * 创建测算版本
     *
     * @param dto 测算版本创建参数
     * @return 新建测算版本 ID
     */
    @Operation(summary = "创建测算版本")
    @PrePermission("execution:simulation:create")
    @Idempotent(key = "profit-simulation:create", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping
    public Result<Long> create(@Valid @RequestBody ProfitSimulationCreateDTO dto) {
        return Result.ok(service.create(dto));
    }

    /**
     * 测算版本状态迁移
     *
     * @param dto 状态变更参数
     * @return 空结果
     */
    @Operation(summary = "状态迁移")
    @PrePermission("execution:simulation:approve")
    @PutMapping("/status")
    public Result<Void> changeStatus(@Valid @RequestBody SimulationStatusDTO dto) {
        service.changeStatus(dto);
        return Result.ok();
    }

    /**
     * 删除测算版本
     *
     * @param id 测算版本 ID
     * @return 空结果
     */
    @Operation(summary = "删除")
    @PrePermission("execution:simulation:delete")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable @Min(1) Long id) {
        service.delete(id);
        return Result.ok();
    }

    /**
     * 查询测算版本详情
     *
     * @param id 测算版本 ID
     * @return 测算版本实体
     */
    @Operation(summary = "详情")
    @PrePermission("execution:simulation:list")
    @GetMapping("/{id}")
    public Result<ProfitSimulationDO> get(@PathVariable @Min(1) Long id) {
        return Result.ok(service.getById(id));
    }

    /**
     * 按项目查询所有测算版本
     *
     * @param initiationId 项目立项 ID
     * @return 测算版本列表
     */
    @Operation(summary = "按项目查询所有版本")
    @PrePermission("execution:simulation:list")
    @GetMapping("/by-initiation")
    public Result<List<ProfitSimulationDO>> listByInitiation(@RequestParam Long initiationId) {
        return Result.ok(service.listByInitiation(initiationId));
    }

    /**
     * 多版本对比
     *
     * @param initiationId 项目立项 ID
     * @return 对比结果列表
     */
    @Operation(summary = "多版本对比")
    @PrePermission("execution:simulation:list")
    @GetMapping("/compare")
    public Result<List<Map<String, Object>>> compare(@RequestParam Long initiationId) {
        return Result.ok(service.compare(initiationId));
    }

    /**
     * 分页查询测算版本
     *
     * @param page         页码（从 1 开始）
     * @param size         每页大小
     * @param initiationId 项目立项 ID
     * @param scenarioType 场景类型
     * @param status       状态过滤
     * @return 分页结果
     */
    @Operation(summary = "分页")
    @PrePermission("execution:simulation:list")
    @GetMapping("/page")
    public Result<Page<ProfitSimulationDO>> page(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) Long initiationId,
            @RequestParam(required = false) String scenarioType,
            @RequestParam(required = false) String status) {
        return Result.ok(service.page(page, size, initiationId, scenarioType, status));
    }
}
