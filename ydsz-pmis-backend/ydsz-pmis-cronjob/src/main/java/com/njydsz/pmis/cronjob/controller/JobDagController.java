package com.njydsz.pmis.cronjob.controller;

import com.njydsz.pmis.common.annotation.Idempotent;
import com.njydsz.pmis.common.annotation.IdempotentExempt;

import com.njydsz.pmis.common.annotation.OperationLog;
import com.njydsz.pmis.common.annotation.PrePermission;
import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.common.permission.PermissionCodes;
import com.njydsz.pmis.cronjob.core.dag.DagDefinition;
import com.njydsz.pmis.cronjob.core.dag.DagDefinitionCodec;
import com.njydsz.pmis.cronjob.core.dag.DagDefinitionValidator;
import com.njydsz.pmis.cronjob.dto.JobDagSaveDTO;
import com.njydsz.pmis.cronjob.dto.JobDagTriggerDTO;
import com.njydsz.pmis.cronjob.entity.JobDagDO;
import com.njydsz.pmis.cronjob.service.JobDagService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * DAG 工作流定义 Controller（P2 DAG 增强）。
 *
 * <p>提供 DAG 定义的增删改查、启用/禁用、手动触发等 HTTP 接口。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Tag(name = "DAG工作流定义")
@RestController
@RequestMapping("/cronjob/dag")
@RequiredArgsConstructor
public class JobDagController {

    private final JobDagService jobDagService;
    private final DagDefinitionValidator dagDefinitionValidator;
    private final DagDefinitionCodec dagDefinitionCodec;

    @Operation(summary = "创建 DAG 工作流")
    @PrePermission(PermissionCodes.CRONJOB_DAG_CREATE)
    @OperationLog(module = "任务调度", action = "创建DAG", bizType = "CRONJOB_DAG")
    @Idempotent(key = "job-dag:create-dag", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/")
    public Result<String> createDag(@Valid @RequestBody JobDagSaveDTO dto) {
        return Result.ok(jobDagService.createDag(dto));
    }

    @Operation(summary = "更新 DAG 工作流")
    @PrePermission(PermissionCodes.CRONJOB_DAG_UPDATE)
    @Idempotent(key = "job-dag:update-dag", ttlSeconds = 5, message = "请勿重复提交")
    @PutMapping("/{dagId}")
    public Result<Void> updateDag(@PathVariable String dagId, @Valid @RequestBody JobDagSaveDTO dto) {
        jobDagService.updateDag(dagId, dto);
        return Result.ok();
    }

    @Operation(summary = "删除 DAG 工作流")
    @PrePermission(PermissionCodes.CRONJOB_DAG_DELETE)
    @OperationLog(module = "任务调度", action = "删除DAG", bizType = "CRONJOB_DAG")
    @Idempotent(key = "job-dag:delete-dag", ttlSeconds = 5, message = "请勿重复提交")
    @DeleteMapping("/{dagId}")
    public Result<Void> deleteDag(@PathVariable String dagId) {
        jobDagService.deleteDag(dagId);
        return Result.ok();
    }

    @Operation(summary = "启用 DAG 工作流")
    @PrePermission(PermissionCodes.CRONJOB_DAG_UPDATE)
    @OperationLog(module = "任务调度", action = "启用DAG", bizType = "CRONJOB_DAG")
    @Idempotent(key = "job-dag:enable-dag", ttlSeconds = 5, message = "请勿重复提交")
    @PutMapping("/{dagId}/enable")
    public Result<Void> enableDag(@PathVariable String dagId) {
        jobDagService.enableDag(dagId);
        return Result.ok();
    }

    @Operation(summary = "禁用 DAG 工作流")
    @PrePermission(PermissionCodes.CRONJOB_DAG_UPDATE)
    @OperationLog(module = "任务调度", action = "禁用DAG", bizType = "CRONJOB_DAG")
    @Idempotent(key = "job-dag:disable-dag", ttlSeconds = 5, message = "请勿重复提交")
    @PutMapping("/{dagId}/disable")
    public Result<Void> disableDag(@PathVariable String dagId) {
        jobDagService.disableDag(dagId);
        return Result.ok();
    }

    @Operation(summary = "查询 DAG 工作流详情")
    @PrePermission(PermissionCodes.CRONJOB_DAG_VIEW)
    @GetMapping("/{dagId}")
    public Result<JobDagDO> getDagById(@PathVariable String dagId) {
        return Result.ok(jobDagService.getDagById(dagId));
    }

    @Operation(summary = "根据 KEY 查询 DAG 工作流")
    @PrePermission(PermissionCodes.CRONJOB_DAG_VIEW)
    @GetMapping("/key/{dagKey}")
    public Result<JobDagDO> getDagByKey(@PathVariable String dagKey) {
        return Result.ok(jobDagService.getDagByKey(dagKey));
    }

    @Operation(summary = "查询所有启用的 DAG 工作流")
    @PrePermission(PermissionCodes.CRONJOB_DAG_VIEW)
    @GetMapping("/enabled")
    public Result<List<JobDagDO>> listEnabledDags() {
        return Result.ok(jobDagService.listEnabledDags());
    }

    @Operation(summary = "手动触发 DAG 工作流")
    @PrePermission(PermissionCodes.CRONJOB_DAG_TRIGGER)
    @OperationLog(module = "任务调度", action = "触发DAG", bizType = "CRONJOB_DAG")
    @IdempotentExempt("定时触发接口，无需幂等")
    @PostMapping("/trigger")
    public Result<String> triggerDag(@Valid @RequestBody JobDagTriggerDTO dto) {
        return Result.ok(jobDagService.triggerDag(dto.getDagKey(), dto.getTriggerBy()));
    }

    /**
     * P0-3: 校验 DAG 定义 JSON（可视化编辑器保存前校验）。
     *
     * <p>校验规则：节点完整性、边完整性、无自环、无环、根节点存在、节点类型约束、规模限制。
     * 校验通过返回 true，失败返回错误信息。
     *
     * @param dagDefinitionJson DAG 定义 JSON 字符串
     * @return 校验结果（true=通过）
     */
    @Operation(summary = "校验 DAG 定义")
    @PrePermission(PermissionCodes.CRONJOB_DAG_VIEW)
    @Idempotent(key = "job-dag:validate-dag", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/validate")
    public Result<Boolean> validateDag(@RequestBody String dagDefinitionJson) {
        DagDefinition definition = dagDefinitionCodec.fromJson(dagDefinitionJson);
        dagDefinitionValidator.validate(definition);
        return Result.ok(true);
    }
}
