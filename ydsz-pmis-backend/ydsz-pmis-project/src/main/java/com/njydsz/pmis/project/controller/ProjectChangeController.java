package com.njydsz.pmis.project.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.api.R;
import com.njydsz.pmis.project.dto.ProjectChangeCreateDTO;
import com.njydsz.pmis.project.dto.ProjectChangeStatusDTO;
import com.njydsz.pmis.project.entity.ProjectChangeDO;
import com.njydsz.pmis.project.enums.ChangeStatus;
import com.njydsz.pmis.project.service.ProjectChangeService;
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 项目变更 Controller
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Tag(name = "项目变更管理")
@RestController
@RequestMapping("/api/v1/project/change")
@RequiredArgsConstructor
public class ProjectChangeController {

    private final ProjectChangeService service;

    @Operation(summary = "创建项目变更")
    @PostMapping
    public R<Long> create(@Valid @RequestBody ProjectChangeCreateDTO dto) {
        return R.ok(service.create(dto));
    }

    @Operation(summary = "状态迁移")
    @PutMapping("/status")
    public R<Void> changeStatus(@Valid @RequestBody ProjectChangeStatusDTO dto) {
        service.changeStatus(dto);
        return R.ok();
    }

    @Operation(summary = "删除变更")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return R.ok();
    }

    @Operation(summary = "变更详情")
    @GetMapping("/{id}")
    public R<ProjectChangeDO> get(@PathVariable Long id) {
        return R.ok(service.getById(id));
    }

    @Operation(summary = "分页查询")
    @GetMapping("/page")
    public R<Page<ProjectChangeDO>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String changeType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long initiationId) {
        return R.ok(service.page(page, size, keyword, changeType, status, initiationId));
    }

    @Operation(summary = "按项目查询变更列表")
    @GetMapping("/list-by-initiation/{initiationId}")
    public R<List<ProjectChangeDO>> listByInitiation(@PathVariable Long initiationId) {
        return R.ok(service.listByInitiation(initiationId));
    }

    @Operation(summary = "按变更类型聚合")
    @GetMapping("/aggregate/type")
    public R<List<Map<String, Object>>> aggregateByType(@RequestParam(required = false) Long tenantId) {
        return R.ok(service.aggregateByType(tenantId));
    }

    @Operation(summary = "按状态聚合")
    @GetMapping("/aggregate/status")
    public R<List<Map<String, Object>>> aggregateByStatus(@RequestParam(required = false) Long tenantId) {
        return R.ok(service.aggregateByStatus(tenantId));
    }

    @Operation(summary = "统计项目重大变更数")
    @GetMapping("/major-count/{initiationId}")
    public R<Long> countMajor(@PathVariable Long initiationId) {
        return R.ok(service.countMajorByInitiation(initiationId));
    }

    /**
     * 获取某条变更的合法状态迁移列表
     * <p>
     * 前端使用: 进入详情或审批时拉取, 用于即时判断按钮可用性 + 友好文案.
     * 重大变更 (majorFlag=1) 在 UNDER_REVIEW → APPROVED 时需要双审批, 前端应额外提示.
     * </p>
     *
     * @param id 变更 ID
     * @return 合法目标状态码列表 (e.g. ["SUBMITTED", "CANCELLED"])
     */
    @Operation(summary = "获取合法状态迁移列表")
    @GetMapping("/{id}/allowed-transitions")
    public R<List<String>> getAllowedTransitions(@PathVariable Long id) {
        ProjectChangeDO change = service.getById(id);
        if (change == null) {
            return R.ok(List.of());
        }
        ChangeStatus current = ChangeStatus.fromCode(change.getStatus());
        if (current == null || current.isTerminal()) {
            return R.ok(List.of());
        }
        List<String> allowed = Arrays.stream(ChangeStatus.values())
                .filter(s -> current.canTransitTo(s))
                .map(ChangeStatus::getCode)
                .collect(Collectors.toList());
        return R.ok(allowed);
    }

    /**
     * 列出所有 ChangeStatus 状态码 + 中文描述
     * <p>前端使用: 渲染状态下拉 / 字典 / 国际化</p>
     */
    @Operation(summary = "获取所有变更状态字典")
    @GetMapping("/status-dict")
    public R<List<Map<String, String>>> getStatusDict() {
        List<Map<String, String>> list = new ArrayList<>();
        for (ChangeStatus s : ChangeStatus.values()) {
            list.add(Map.of(
                "code", s.getCode(),
                "desc", s.getDesc(),
                "terminal", String.valueOf(s.isTerminal())
            ));
        }
        return R.ok(list);
    }
}
