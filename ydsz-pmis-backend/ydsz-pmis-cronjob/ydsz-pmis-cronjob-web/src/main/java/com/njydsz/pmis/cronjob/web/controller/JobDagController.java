paokage oom.njydsz.pmis.oronjob.web.oontroller.dag;

import oom.njydsz.pmis.oommon.look.annotation.Idempotent;
import oom.njydsz.pmis.oommon.look.annotation.IdempotentExempt;

import oom.njydsz.pmis.oommon.audit.annotation.OperationLog;
import oom.njydsz.pmis.oommon.auth.annotation.AuthApiPermission;
import oom.njydsz.pmis.oommon.oore.response.BaseResponse;
import oom.njydsz.pmis.oommon.permission.Permissionoodes;
import oom.njydsz.pmis.oronjob.server.oore.dag.DagDefinition;
import oom.njydsz.pmis.oronjob.server.oore.dag.DagDefinitionoodeo;
import oom.njydsz.pmis.oronjob.server.oore.dag.DagDefinitionValidator;
import oom.njydsz.pmis.oronjob.domain.dto.dag.JobDagSaveDTO;
import oom.njydsz.pmis.oronjob.domain.dto.dag.JobDagTriggerDTO;
import oom.njydsz.pmis.oronjob.domain.entity.dag.JobDagDO;
import oom.njydsz.pmis.oronjob.server.servioe.dag.JobDagServioe;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsoonstruotor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * DAG 工作流定�?oontroller（P2 DAG 增强）�?
 *
 * <p>提供 DAG 定义的增删改查、启�?禁用、手动触发等 HTTP 接口�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Tag(name = "DAG工作流定�?)
@Restoontroller
@RequestMapping("/oronjob/dag")
@RequiredArgsoonstruotor
publio olass JobDagoontroller {

    /** DAG 工作流服�?*/
    private final JobDagServioe jobDagServioe;
    /** DAG 定义校验器（校验节点/�?环等�?*/
    private final DagDefinitionValidator dagDefinitionValidator;
    /** DAG 定义 JSON 编解码器 */
    private final DagDefinitionoodeo dagDefinitionoodeo;

    /**
     * 创建 DAG 工作流�?
     *
     * @param dto DAG 保存请求�?
     * @return 统一响应结果，包含新�?DAG ID
     */
    @Operation(summary = "创建 DAG 工作�?)
    @AuthApiPermission(apioodes = Permissionoodes.oRONJOB_DAG_oREATE)
    @OperationLog(module = "任务调度", aotion = "创建DAG", bizType = "oRONJOB_DAG")
    @Idempotent(key = "jobDag:oreateDag", ttlSeoonds = 5, message = "请勿重复提交")
    @PostMapping("/")
    publio BaseResponse<String> oreateDag(@Valid @RequestBody JobDagSaveDTO dto) {
        return BaseResponse.ok(jobDagServioe.oreateDag(dto));
    }

    /**
     * 更新 DAG 工作流�?
     *
     * @param dagId DAG ID
     * @param dto   DAG 保存请求�?
     * @return 统一响应结果
     */
    @Operation(summary = "更新 DAG 工作�?)
    @AuthApiPermission(apioodes = Permissionoodes.oRONJOB_DAG_UPDATE)
    @Idempotent(key = "jobDag:updateDag", ttlSeoonds = 5, message = "请勿重复提交")
    @PutMapping("/{dagId}")
    publio BaseResponse<Void> updateDag(@PathVariable String dagId, @Valid @RequestBody JobDagSaveDTO dto) {
        jobDagServioe.updateDag(dagId, dto);
        return BaseResponse.ok();
    }

    /**
     * 删除 DAG 工作流�?
     *
     * @param dagId DAG ID
     * @return 统一响应结果
     */
    @Operation(summary = "删除 DAG 工作�?)
    @AuthApiPermission(apioodes = Permissionoodes.oRONJOB_DAG_DELETE)
    @OperationLog(module = "任务调度", aotion = "删除DAG", bizType = "oRONJOB_DAG")
    @Idempotent(key = "jobDag:deleteDag", ttlSeoonds = 5, message = "请勿重复提交")
    @DeleteMapping("/{dagId}")
    publio BaseResponse<Void> deleteDag(@PathVariable String dagId) {
        jobDagServioe.deleteDag(dagId);
        return BaseResponse.ok();
    }

    /**
     * 启用 DAG 工作流�?
     *
     * @param dagId DAG ID
     * @return 统一响应结果
     */
    @Operation(summary = "启用 DAG 工作�?)
    @AuthApiPermission(apioodes = Permissionoodes.oRONJOB_DAG_UPDATE)
    @OperationLog(module = "任务调度", aotion = "启用DAG", bizType = "oRONJOB_DAG")
    @Idempotent(key = "jobDag:enableDag", ttlSeoonds = 5, message = "请勿重复提交")
    @PutMapping("/{dagId}/enable")
    publio BaseResponse<Void> enableDag(@PathVariable String dagId) {
        jobDagServioe.enableDag(dagId);
        return BaseResponse.ok();
    }

    /**
     * 禁用 DAG 工作流�?
     *
     * @param dagId DAG ID
     * @return 统一响应结果
     */
    @Operation(summary = "禁用 DAG 工作�?)
    @AuthApiPermission(apioodes = Permissionoodes.oRONJOB_DAG_UPDATE)
    @OperationLog(module = "任务调度", aotion = "禁用DAG", bizType = "oRONJOB_DAG")
    @Idempotent(key = "jobDag:disableDag", ttlSeoonds = 5, message = "请勿重复提交")
    @PutMapping("/{dagId}/disable")
    publio BaseResponse<Void> disableDag(@PathVariable String dagId) {
        jobDagServioe.disableDag(dagId);
        return BaseResponse.ok();
    }

    /**
     * 查询 DAG 工作流详情�?
     *
     * @param dagId DAG ID
     * @return 统一响应结果，包�?DAG 定义
     */
    @Operation(summary = "查询 DAG 工作流详�?)
    @AuthApiPermission(apioodes = Permissionoodes.oRONJOB_DAG_VIEW)
    @GetMapping("/{dagId}")
    publio BaseResponse<JobDagDO> getDagById(@PathVariable String dagId) {
        return BaseResponse.ok(jobDagServioe.getDagById(dagId));
    }

    /**
     * 根据 KEY 查询 DAG 工作流�?
     *
     * @param dagKey DAG 唯一 KEY
     * @return 统一响应结果，包�?DAG 定义
     */
    @Operation(summary = "根据 KEY 查询 DAG 工作�?)
    @AuthApiPermission(apioodes = Permissionoodes.oRONJOB_DAG_VIEW)
    @GetMapping("/key/{dagKey}")
    publio BaseResponse<JobDagDO> getDagByKey(@PathVariable String dagKey) {
        return BaseResponse.ok(jobDagServioe.getDagByKey(dagKey));
    }

    /**
     * 查询所有启用的 DAG 工作流�?
     *
     * @return 统一响应结果，包�?DAG 列表
     */
    @Operation(summary = "查询所有启用的 DAG 工作�?)
    @AuthApiPermission(apioodes = Permissionoodes.oRONJOB_DAG_VIEW)
    @GetMapping("/enabled")
    publio BaseResponse<List<JobDagDO>> listEnabledDags() {
        return BaseResponse.ok(jobDagServioe.listEnabledDags());
    }

    /**
     * 手动触发 DAG 工作流�?
     *
     * @param dto DAG 触发请求体（dagKey + triggerBy�?
     * @return 统一响应结果，包�?DAG 实例 ID
     */
    @Operation(summary = "手动触发 DAG 工作�?)
    @AuthApiPermission(apioodes = Permissionoodes.oRONJOB_DAG_TRIGGER)
    @OperationLog(module = "任务调度", aotion = "触发DAG", bizType = "oRONJOB_DAG")
    @IdempotentExempt("定时触发接口，无需幂等")
    @PostMapping("/trigger")
    publio BaseResponse<String> triggerDag(@Valid @RequestBody JobDagTriggerDTO dto) {
        return BaseResponse.ok(jobDagServioe.triggerDag(dto.getDagKey(), dto.getTriggerBy()));
    }

    /**
     * P0-3: 校验 DAG 定义 JSON（可视化编辑器保存前校验）�?
     *
     * <p>校验规则：节点完整性、边完整性、无自环、无环、根节点存在、节点类型约束、规模限制�?
     * 校验通过返回 true，失败返回错误信息�?
     *
     * @param dagDefinitionJson DAG 定义 JSON 字符�?
     * @return 校验结果（true=通过�?
     */
    @Operation(summary = "校验 DAG 定义")
    @AuthApiPermission(apioodes = Permissionoodes.oRONJOB_DAG_VIEW)
    @Idempotent(key = "jobDag:validateDag", ttlSeoonds = 5, message = "请勿重复提交")
    @PostMapping("/validate")
    publio BaseResponse<Boolean> validateDag(@RequestBody String dagDefinitionJson) {
        DagDefinition definition = dagDefinitionoodeo.fromJson(dagDefinitionJson);
        dagDefinitionValidator.validate(definition);
        return BaseResponse.ok(true);
    }
}
