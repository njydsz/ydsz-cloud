package com.njydsz.pmis.execution.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.annotation.PrePermission;
import com.njydsz.pmis.common.api.R;
import com.njydsz.pmis.execution.dto.ProjectClosureCreateDTO;
import com.njydsz.pmis.execution.dto.ProjectClosureStatusDTO;
import com.njydsz.pmis.execution.engine.ClosureAdmissionValidator;
import com.njydsz.pmis.execution.entity.ProjectClosureDO;
import com.njydsz.pmis.execution.service.ProjectClosureService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
@RequestMapping("/api/v1/execution/closure")
@RequiredArgsConstructor
public class ProjectClosureController {

    private final ProjectClosureService service;

    @Operation(summary = "创建项目结项")
    @PrePermission("closure:project:create")
    @PostMapping
    public Result<Long> create(@Valid @RequestBody ProjectClosureCreateDTO dto) {
        return Result.ok(service.create(dto));
    }

    @Operation(summary = "状态迁移")
    @PrePermission("closure:project:status")
    @PutMapping("/status")
    public Result<Void> changeStatus(@Valid @RequestBody ProjectClosureStatusDTO dto) {
        service.changeStatus(dto);
        return Result.ok();
    }

    @Operation(summary = "删除结项记录")
    @PrePermission("closure:project:delete")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return Result.ok();
    }

    @Operation(summary = "结项详情")
    @PrePermission("closure:project:list")
    @GetMapping("/{id}")
    public Result<ProjectClosureDO> get(@PathVariable Long id) {
        return Result.ok(service.getById(id));
    }

    @Operation(summary = "按项目查询结项")
    @PrePermission("closure:project:list")
    @GetMapping("/by-initiation/{initiationId}")
    public Result<ProjectClosureDO> getByInitiation(@PathVariable Long initiationId) {
        return Result.ok(service.getByInitiation(initiationId));
    }

    @Operation(summary = "分页查询")
    @PrePermission("closure:project:list")
    @GetMapping("/page")
    public Result<Page<ProjectClosureDO>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String closureType,
            @RequestParam(required = false) String status) {
        return Result.ok(service.page(page, size, keyword, closureType, status));
    }

    @Operation(summary = "按结项类型查询")
    @PrePermission("closure:project:list")
    @GetMapping("/list-by-type")
    public Result<List<ProjectClosureDO>> listByType(@RequestParam(required = false) String closureType) {
        return Result.ok(service.listByType(closureType));
    }

    @Operation(summary = "按结项类型聚合")
    @PrePermission("closure:project:list")
    @GetMapping("/aggregate/type")
    public Result<List<Map<String, Object>>> aggregateByType(@RequestParam(required = false) Long tenantId) {
        return Result.ok(service.aggregateByType(tenantId));
    }

    @Operation(summary = "结项准入校验")
    @PrePermission("closure:project:list")
    @GetMapping("/{id}/admission-check")
    public Result<ClosureAdmissionValidator.AdmissionCheck> checkAdmission(@PathVariable Long id) {
        return Result.ok(service.checkAdmission(id));
    }
}
