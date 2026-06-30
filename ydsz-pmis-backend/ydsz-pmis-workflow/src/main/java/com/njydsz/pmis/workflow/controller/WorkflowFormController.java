package com.njydsz.pmis.workflow.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.api.R;
import com.njydsz.pmis.workflow.entity.WorkflowFormDO;
import com.njydsz.pmis.workflow.service.WorkflowFormService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 流程表单 Controller
 */
@Tag(name = "工作流 - 表单")
@RestController
@RequestMapping("/api/v1/workflow/form")
@RequiredArgsConstructor
public class WorkflowFormController {

    private final WorkflowFormService formService;

    @Operation(summary = "新增表单")
    @PostMapping
    public R<Long> create(@RequestBody WorkflowFormDO form) {
        return R.ok(formService.create(form));
    }

    @Operation(summary = "更新表单")
    @PutMapping
    public R<Void> update(@RequestBody WorkflowFormDO form) {
        formService.update(form);
        return R.ok();
    }

    @Operation(summary = "删除表单")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        formService.delete(id);
        return R.ok();
    }

    @Operation(summary = "表单详情")
    @GetMapping("/{id}")
    public R<WorkflowFormDO> getById(@PathVariable Long id) {
        return R.ok(formService.getById(id));
    }

    @Operation(summary = "按 formKey 查询")
    @GetMapping("/by-key")
    public R<WorkflowFormDO> getByFormKey(@RequestParam String formKey) {
        return R.ok(formService.getByFormKey(formKey));
    }

    @Operation(summary = "按流程定义 KEY 查询")
    @GetMapping("/by-process")
    public R<List<WorkflowFormDO>> listByProcessKey(@RequestParam String processKey) {
        return R.ok(formService.listByProcessKey(processKey));
    }

    @Operation(summary = "分页查询表单")
    @GetMapping("/page")
    public R<Page<WorkflowFormDO>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String processKey,
            @RequestParam(required = false) String status) {
        return R.ok(formService.page(page, size, keyword, processKey, status));
    }
}
