package com.njydsz.cronjob.web.controller.dag;

import java.util.List;

import com.njydsz.common.safe.ratelimit.annotation.RateLimit;
import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.*;

import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.common.lock.annotation.IdempotentExempt;
import com.njydsz.common.permission.PermissionCodes;
import com.njydsz.cronjob.domain.dto.dag.JobDagSaveDTO;
import com.njydsz.cronjob.domain.dto.dag.JobDagTriggerDTO;
import com.njydsz.cronjob.domain.entity.dag.JobDag;
import com.njydsz.cronjob.domain.entity.dag.JobDagVersion;
import com.njydsz.cronjob.server.core.dag.DagDefinition;
import com.njydsz.cronjob.server.core.dag.DagDefinitionCodec;
import com.njydsz.cronjob.server.core.dag.DagDefinitionValidator;
import com.njydsz.cronjob.server.service.dag.JobDagService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.audit.enums.AuditType;
import com.njydsz.cronjob.domain.converter.CronjobConverter;
import com.njydsz.cronjob.domain.vo.JobDagVO;
import com.njydsz.cronjob.domain.vo.JobDagVersionVO;

/**
 * DAG 工作流定义 Controller（P2 DAG 增强）。
 *
 * <p>提供 DAG 定义的增删改查、启用/禁用、手动触发等 HTTP 接口。
 *
 * @author ydsz-team
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
    @Idempotent(key = "ydsz:cronjob:JobDagController:createDag:lock", ttlSeconds = 5)
    @Audit(module = "DAG管理", type = AuditType.OPERATION, action = AuditAction.CREATE, content = "'createDag'")
    @RateLimit(resource = "cronjob.jobdag.createDag", threshold = 50)
    @PostMapping("/")
    public BaseResponse<String> createDag(@Valid @RequestBody JobDagSaveDTO dto) {
        return BaseResponse.success(jobDagService.createDag(dto));
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
    @Idempotent(key = "ydsz:cronjob:JobDagController:updateDag:lock", ttlSeconds = 5)
    @Audit(module = "DAG管理", type = AuditType.OPERATION, action = AuditAction.UPDATE, content = "'updateDag'")
    @RateLimit(resource = "cronjob.jobdag.updateDag", threshold = 50)
    @PutMapping("/{dagId}")
    public BaseResponse<Void> updateDag(@PathVariable String dagId, @Valid @RequestBody JobDagSaveDTO dto) {
        jobDagService.updateDag(dagId, dto);
        return BaseResponse.success();
    }

    /**
     * 删除 DAG 工作流。
     *
     * @param dagId DAG ID
     * @return 统一响应结果
     */
    @Operation(summary = "删除 DAG 工作流")
    @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_DAG_DELETE)
    @Idempotent(key = "ydsz:cronjob:JobDagController:deleteDag:lock", ttlSeconds = 5)
    @Audit(module = "DAG管理", type = AuditType.OPERATION, action = AuditAction.DELETE, content = "'deleteDag'")
    @RateLimit(resource = "cronjob.jobdag.deleteDag", threshold = 50)
    @DeleteMapping("/{dagId}")
    public BaseResponse<Void> deleteDag(@PathVariable String dagId) {
        jobDagService.deleteDag(dagId);
        return BaseResponse.success();
    }

    /**
     * 启用 DAG 工作流。
     *
     * @param dagId DAG ID
     * @return 统一响应结果
     */
    @Operation(summary = "启用 DAG 工作流")
    @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_DAG_UPDATE)
    @Idempotent(key = "ydsz:cronjob:JobDagController:enableDag:lock", ttlSeconds = 5)
    @Audit(module = "DAG管理", type = AuditType.OPERATION, action = AuditAction.UPDATE, content = "'enableDag'")
    @RateLimit(resource = "cronjob.jobdag.enableDag", threshold = 50)
    @PutMapping("/{dagId}/enable")
    public BaseResponse<Void> enableDag(@PathVariable String dagId) {
        jobDagService.enableDag(dagId);
        return BaseResponse.success();
    }

    /**
     * 禁用 DAG 工作流。
     *
     * @param dagId DAG ID
     * @return 统一响应结果
     */
    @Operation(summary = "禁用 DAG 工作流")
    @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_DAG_UPDATE)
    @Idempotent(key = "ydsz:cronjob:JobDagController:disableDag:lock", ttlSeconds = 5)
    @Audit(module = "DAG管理", type = AuditType.OPERATION, action = AuditAction.UPDATE, content = "'disableDag'")
    @RateLimit(resource = "cronjob.jobdag.disableDag", threshold = 50)
    @PutMapping("/{dagId}/disable")
    public BaseResponse<Void> disableDag(@PathVariable String dagId) {
        jobDagService.disableDag(dagId);
        return BaseResponse.success();
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
    public BaseResponse<JobDagVO> getDagById(@PathVariable String dagId) {
        return BaseResponse.success(CronjobConverter.INSTANT.entityToVO(jobDagService.getDagById(dagId)));
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
    public BaseResponse<JobDagVO> getDagByKey(@PathVariable String dagKey) {
        return BaseResponse.success(CronjobConverter.INSTANT.entityToVO(jobDagService.getDagByKey(dagKey)));
    }

    /**
     * 查询所有启用的 DAG 工作流。
     *
     * @return 统一响应结果，包含 DAG 列表
     */
    @Operation(summary = "查询所有启用的 DAG 工作流")
    @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_DAG_VIEW)
    @GetMapping("/enabled")
    public BaseResponse<List<JobDagVO>> listEnabledDags() {
        return BaseResponse.success(CronjobConverter.INSTANT.jobDagListToVO(jobDagService.listEnabledDags()));
    }

    /**
     * 手动触发 DAG 工作流。
     *
     * @param dto DAG 触发请求体（dagKey + triggerBy）
     * @return 统一响应结果，包含 DAG 实例 ID
     */
    @Operation(summary = "手动触发 DAG 工作流")
    @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_DAG_TRIGGER)
    @IdempotentExempt("定时触发接口，无需幂等")
    @Audit(module = "DAG管理", type = AuditType.OPERATION, action = AuditAction.CREATE, content = "'triggerDag'")
    @RateLimit(resource = "cronjob.jobdag.triggerDag", threshold = 50)
    @Idempotent(key = "ydsz:cronjob:JobDagController:triggerDag:lock", ttlSeconds = 5)
    @PostMapping("/trigger")
    public BaseResponse<String> triggerDag(@Valid @RequestBody JobDagTriggerDTO dto) {
        return BaseResponse.success(jobDagService.triggerDag(dto.getDagKey(), dto.getTriggerBy()));
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
    @Idempotent(key = "ydsz:cronjob:JobDagController:validateDag:lock", ttlSeconds = 5)
    @Audit(module = "DAG管理", type = AuditType.OPERATION, action = AuditAction.CREATE, content = "'validateDag'")
    @RateLimit(resource = "cronjob.jobdag.validateDag", threshold = 50)
    @PostMapping("/validate")
    public BaseResponse<Boolean> validateDag(@RequestBody String dagDefinitionJson) {
        DagDefinition definition = dagDefinitionCodec.fromJson(dagDefinitionJson);
        dagDefinitionValidator.validate(definition);
        return BaseResponse.success(true);
    }

    /**
     * P1-4: 查询 DAG 版本历史列表。
     *
     * <p>返回指定 DAG 的所有历史版本快照（按版本号降序）。
     *
     * @param dagId DAG ID
     * @return 统一响应结果，包含版本历史列表
     */
    @Operation(summary = "查询 DAG 版本历史")
    @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_DAG_VIEW)
    @GetMapping("/{dagId}/versions")
    public BaseResponse<List<JobDagVersionVO>> listDagVersions(@PathVariable String dagId) {
        return BaseResponse.success(CronjobConverter.INSTANT.jobDagVersionListToVO(jobDagService.listDagVersions(dagId, 50)));
    }

    /**
     * P1-4: 回滚 DAG 到指定版本。
     *
     * @param dagId   DAG ID
     * @param version 目标版本号
     * @return 统一响应结果，包含回滚后的 DAG 定义
     */
    @Operation(summary = "回滚 DAG 到指定版本")
    @AuthApiPermission(apiCodes = PermissionCodes.CRONJOB_DAG_UPDATE)
    @Idempotent(key = "ydsz:cronjob:JobDagController:rollbackDag:lock", ttlSeconds = 5)
    @Audit(module = "DAG管理", type = AuditType.OPERATION, action = AuditAction.CREATE, content = "'rollbackDag'")
    @RateLimit(resource = "cronjob.jobdag.rollbackDag", threshold = 50)
    @PostMapping("/{dagId}/rollback")
    public BaseResponse<JobDagVO> rollbackDag(@PathVariable String dagId,
                                                @RequestParam Integer version) {
        return BaseResponse.success(CronjobConverter.INSTANT.entityToVO(jobDagService.rollbackDag(dagId, version)));
    }
}
