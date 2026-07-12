package com.njydsz.pmis.cronjob.web.controller.dag;

import com.njydsz.pmis.common.annotation.Idempotent;
import com.njydsz.pmis.common.annotation.IdempotentExempt;

import com.njydsz.pmis.common.annotation.OperationLog;
import com.njydsz.pmis.common.auth.annotation.AuthApiPermission;
import com.njydsz.pmis.common.core.response.BaseResponse;
import com.njydsz.pmis.common.permission.PermissionCodes;
import com.njydsz.pmis.cronjob.server.core.dag.DagDefinition;
import com.njydsz.pmis.cronjob.server.core.dag.DagDefinitionCodec;
import com.njydsz.pmis.cronjob.server.core.dag.DagDefinitionValidator;
import com.njydsz.pmis.cronjob.domain.dto.dag.JobDagSaveDTO;
import com.njydsz.pmis.cronjob.domain.dto.dag.JobDagTriggerDTO;
import com.njydsz.pmis.cronjob.domain.entity.dag.JobDagDO;
import com.njydsz.pmis.cronjob.server.service.dag.JobDagService;
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

    /** DAG 工作流服务 */
    private final JobDagService jobDagService;
    /** DAG 定义校验器（校验节点/边/环等） */
    private final DagDefinitionValidator dagDefinitionValidator;
    /** DAG 定义 JSON 编解码器 */
    private final DagDefinitionCodec dagDefinitionCodec;

    /**
     * 创建 DAG 工作流。
     *
     * @param dto DAG 保存请求体
     * @return 统一响应结果，包含新增 DAG ID
     */
    @Operation(summary = "创建 DAG 工作流")
    @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_DAG_CREATE)
    @OperationLog(module = "任务调度", action = "创建DAG", bizType = "CRONJOB_DAG")
    @Idempotent(key = "jobDag:createDag", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/")
    public BaseResponse<String> createDag(@Valid @RequestBody JobDagSaveDTO dto) {
        return BaseResponse.ok(jobDagService.createDag(dto));
    }

    /**
     * 更新 DAG 工作流。
     *
     * @param dagId DAG ID
     * @param dto   DAG 保存请求体
     * @return 统一响应结果
     */
    @Operation(summary = "更新 DAG 工作流")
    @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_DAG_UPDATE)
    @Idempotent(key = "jobDag:updateDag", ttlSeconds = 5, message = "请勿重复提交")
    @PutMapping("/{dagId}")
    public BaseResponse<Void> updateDag(@PathVariable String dagId, @Valid @RequestBody JobDagSaveDTO dto) {
        jobDagService.updateDag(dagId, dto);
        return BaseResponse.ok();
    }

    /**
     * 删除 DAG 工作流。
     *
     * @param dagId DAG ID
     * @return 统一响应结果
     */
    @Operation(summary = "删除 DAG 工作流")
    @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_DAG_DELETE)
    @OperationLog(module = "任务调度", action = "删除DAG", bizType = "CRONJOB_DAG")
    @Idempotent(key = "jobDag:deleteDag", ttlSeconds = 5, message = "请勿重复提交")
    @DeleteMapping("/{dagId}")
    public BaseResponse<Void> deleteDag(@PathVariable String dagId) {
        jobDagService.deleteDag(dagId);
        return BaseResponse.ok();
    }

    /**
     * 启用 DAG 工作流。
     *
     * @param dagId DAG ID
     * @return 统一响应结果
     */
    @Operation(summary = "启用 DAG 工作流")
    @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_DAG_UPDATE)
    @OperationLog(module = "任务调度", action = "启用DAG", bizType = "CRONJOB_DAG")
    @Idempotent(key = "jobDag:enableDag", ttlSeconds = 5, message = "请勿重复提交")
    @PutMapping("/{dagId}/enable")
    public BaseResponse<Void> enableDag(@PathVariable String dagId) {
        jobDagService.enableDag(dagId);
        return BaseResponse.ok();
    }

    /**
     * 禁用 DAG 工作流。
     *
     * @param dagId DAG ID
     * @return 统一响应结果
     */
    @Operation(summary = "禁用 DAG 工作流")
    @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_DAG_UPDATE)
    @OperationLog(module = "任务调度", action = "禁用DAG", bizType = "CRONJOB_DAG")
    @Idempotent(key = "jobDag:disableDag", ttlSeconds = 5, message = "请勿重复提交")
    @PutMapping("/{dagId}/disable")
    public BaseResponse<Void> disableDag(@PathVariable String dagId) {
        jobDagService.disableDag(dagId);
        return BaseResponse.ok();
    }

    /**
     * 查询 DAG 工作流详情。
     *
     * @param dagId DAG ID
     * @return 统一响应结果，包含 DAG 定义
     */
    @Operation(summary = "查询 DAG 工作流详情")
    @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_DAG_VIEW)
    @GetMapping("/{dagId}")
    public BaseResponse<JobDagDO> getDagById(@PathVariable String dagId) {
        return BaseResponse.ok(jobDagService.getDagById(dagId));
    }

    /**
     * 根据 KEY 查询 DAG 工作流。
     *
     * @param dagKey DAG 唯一 KEY
     * @return 统一响应结果，包含 DAG 定义
     */
    @Operation(summary = "根据 KEY 查询 DAG 工作流")
    @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_DAG_VIEW)
    @GetMapping("/key/{dagKey}")
    public BaseResponse<JobDagDO> getDagByKey(@PathVariable String dagKey) {
        return BaseResponse.ok(jobDagService.getDagByKey(dagKey));
    }

    /**
     * 查询所有启用的 DAG 工作流。
     *
     * @return 统一响应结果，包含 DAG 列表
     */
    @Operation(summary = "查询所有启用的 DAG 工作流")
    @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_DAG_VIEW)
    @GetMapping("/enabled")
    public BaseResponse<List<JobDagDO>> listEnabledDags() {
        return BaseResponse.ok(jobDagService.listEnabledDags());
    }

    /**
     * 手动触发 DAG 工作流。
     *
     * @param dto DAG 触发请求体（dagKey + triggerBy）
     * @return 统一响应结果，包含 DAG 实例 ID
     */
    @Operation(summary = "手动触发 DAG 工作流")
    @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_DAG_TRIGGER)
    @OperationLog(module = "任务调度", action = "触发DAG", bizType = "CRONJOB_DAG")
    @IdempotentExempt("定时触发接口，无需幂等")
    @PostMapping("/trigger")
    public BaseResponse<String> triggerDag(@Valid @RequestBody JobDagTriggerDTO dto) {
        return BaseResponse.ok(jobDagService.triggerDag(dto.getDagKey(), dto.getTriggerBy()));
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
    @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_DAG_VIEW)
    @Idempotent(key = "jobDag:validateDag", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/validate")
    public BaseResponse<Boolean> validateDag(@RequestBody String dagDefinitionJson) {
        DagDefinition definition = dagDefinitionCodec.fromJson(dagDefinitionJson);
        dagDefinitionValidator.validate(definition);
        return BaseResponse.ok(true);
    }
}
