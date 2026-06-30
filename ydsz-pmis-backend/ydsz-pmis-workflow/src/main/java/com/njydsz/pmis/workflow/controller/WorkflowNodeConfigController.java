package com.njydsz.pmis.workflow.controller;

import com.njydsz.pmis.common.api.R;
import com.njydsz.pmis.workflow.entity.WorkflowNodeConfigDO;
import com.njydsz.pmis.workflow.service.WorkflowNodeConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 流程节点配置 Controller
 */
@Tag(name = "工作流 - 节点配置")
@RestController
@RequestMapping("/api/v1/workflow/node-config")
@RequiredArgsConstructor
public class WorkflowNodeConfigController {

    private final WorkflowNodeConfigService nodeConfigService;

    @Operation(summary = "新增/更新节点配置")
    @PostMapping
    public R<Long> saveOrUpdate(@RequestBody WorkflowNodeConfigDO config) {
        return R.ok(nodeConfigService.saveOrUpdate(config));
    }

    @Operation(summary = "删除节点配置")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        nodeConfigService.delete(id);
        return R.ok();
    }

    @Operation(summary = "查询流程的节点配置列表")
    @GetMapping("/by-process")
    public R<List<WorkflowNodeConfigDO>> listByProcessKey(@RequestParam String processKey,
                                                          @RequestParam(required = false) Long tenantId) {
        return R.ok(nodeConfigService.listByProcessKey(processKey, tenantId));
    }

    @Operation(summary = "查询单个节点配置")
    @GetMapping("/by-node")
    public R<WorkflowNodeConfigDO> getByNode(@RequestParam String processKey,
                                             @RequestParam String nodeId,
                                             @RequestParam(required = false) Long tenantId) {
        return R.ok(nodeConfigService.getByNode(processKey, nodeId, tenantId));
    }
}
