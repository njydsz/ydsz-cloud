package com.njydsz.pmis.project.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.annotation.Idempotent;
import com.njydsz.pmis.common.annotation.PrePermission;
import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.project.dto.ProjectClosureCreateDTO;
import com.njydsz.pmis.project.dto.ProjectClosureStatusDTO;
import com.njydsz.pmis.project.engine.ClosureAdmissionValidator;
import com.njydsz.pmis.project.entity.ProjectClosureDO;
import com.njydsz.pmis.project.service.ProjectClosureService;
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
 * 项目结项 Controller
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Tag(name = "项目结项管理")
@RestController
@RequestMapping("/execution/closure")
@RequiredArgsConstructor
@Validated
public class ProjectClosureController {

    private final ProjectClosureService service;

    /**
     * 创建项目结项
     *
     * @param dto 结项创建参数
     * @return 新建结项 ID
     */
    @Operation(summary = "创建项目结项")
    @PrePermission("closure:project:create")
    @Idempotent(key = "project-closure:create", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping
    public Result<String> create(@Valid @RequestBody ProjectClosureCreateDTO dto) {
        return Result.ok(service.create(dto));
    }

    /**
     * 结项状态迁移
     *
     * @param dto 状态变更参数
     * @return 空结果
     */
    @Operation(summary = "状态迁移")
    @PrePermission("closure:project:status")
    @Idempotent(key = "project-closure:change-status", ttlSeconds = 5, message = "请勿重复提交")
    @PutMapping("/status")
    public Result<Void> changeStatus(@Valid @RequestBody ProjectClosureStatusDTO dto) {
        service.changeStatus(dto);
        return Result.ok();
    }

    /**
     * 删除结项记录
     *
     * @param id 结项 ID
     * @return 空结果
     */
    @Operation(summary = "删除结项记录")
    @PrePermission("closure:project:delete")
    @Idempotent(key = "project-closure:delete", ttlSeconds = 5, message = "请勿重复提交")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        service.delete(id);
        return Result.ok();
    }

    /**
     * 查询结项详情
     *
     * @param id 结项 ID
     * @return 结项实体
     */
    @Operation(summary = "结项详情")
    @PrePermission("closure:project:list")
    @GetMapping("/{id}")
    public Result<ProjectClosureDO> get(@PathVariable String id) {
        return Result.ok(service.getById(id));
    }

    /**
     * 按项目立项 ID 查询结项
     *
     * @param initiationId 项目立项 ID
     * @return 结项实体
     */
    @Operation(summary = "按项目查询结项")
    @PrePermission("closure:project:list")
    @GetMapping("/by-initiation/{initiationId}")
    public Result<ProjectClosureDO> getByInitiation(@PathVariable String initiationId) {
        return Result.ok(service.getByInitiation(initiationId));
    }

    /**
     * 分页查询结项
     *
     * @param page        页码（从 1 开始）
     * @param size        每页大小
     * @param keyword     关键词
     * @param closureType 结项类型
     * @param status      状态过滤
     * @return 分页结果
     */
    @Operation(summary = "分页查询")
    @PrePermission("closure:project:list")
    @GetMapping("/page")
    public Result<Page<ProjectClosureDO>> page(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String closureType,
            @RequestParam(required = false) String status) {
        return Result.ok(service.page(page, size, keyword, closureType, status));
    }

    /**
     * 按结项类型查询列表
     *
     * @param closureType 结项类型，可选
     * @return 结项列表
     */
    @Operation(summary = "按结项类型查询")
    @PrePermission("closure:project:list")
    @GetMapping("/list-by-type")
    public Result<List<ProjectClosureDO>> listByType(@RequestParam(required = false) String closureType) {
        return Result.ok(service.listByType(closureType));
    }

    /**
     * 按结项类型聚合统计
     *
     * @param tenantId 租户 ID，可选
     * @return 各类型数量列表
     */
    @Operation(summary = "按结项类型聚合")
    @PrePermission("closure:project:list")
    @GetMapping("/aggregate/type")
    public Result<List<Map<String, Object>>> aggregateByType(@RequestParam(required = false) String tenantId) {
        return Result.ok(service.aggregateByType(tenantId));
    }

    /**
     * 结项准入校验
     *
     * @param id 结项 ID
     * @return 准入校验结果
     */
    @Operation(summary = "结项准入校验")
    @PrePermission("closure:project:list")
    @GetMapping("/{id}/admission-check")
    public Result<ClosureAdmissionValidator.AdmissionCheck> checkAdmission(@PathVariable String id) {
        return Result.ok(service.checkAdmission(id));
    }
}
