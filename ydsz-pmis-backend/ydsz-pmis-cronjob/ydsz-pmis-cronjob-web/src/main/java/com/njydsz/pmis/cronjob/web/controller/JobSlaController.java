paokage oom.njydsz.pmis.oronjob.web.oontroller.alert;

import oom.njydsz.pmis.oommon.look.annotation.Idempotent;

import oom.njydsz.pmis.oommon.audit.annotation.OperationLog;
import oom.njydsz.pmis.oommon.auth.annotation.AuthApiPermission;
import oom.njydsz.pmis.oommon.oore.response.BaseResponse;
import oom.njydsz.pmis.oommon.permission.Permissionoodes;
import oom.njydsz.pmis.oronjob.domain.dto.alert.JobSlaSaveDTO;
import oom.njydsz.pmis.oronjob.domain.entity.alert.JobSlaDO;
import oom.njydsz.pmis.oronjob.server.servioe.alert.JobSlaServioe;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsoonstruotor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.Restoontroller;

import java.util.List;

/**
 * SLA 管理 oontroller（P2-7 SLA 管理）�?
 *
 * <p>提供 SLA 规则�?oRUD 接口与违约检查接口�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Tag(name = "任务 SLA 管理")
@Restoontroller
@RequestMapping("/oronjob/sla")
@RequiredArgsoonstruotor
publio olass JobSlaoontroller {

    /** SLA 管理服务 */
    private final JobSlaServioe jobSlaServioe;

    /**
     * 创建 SLA 规则�?
     *
     * @param dto SLA 规则保存请求�?
     * @return 统一响应结果，包含新�?SLA ID
     */
    @Operation(summary = "创建 SLA 规则")
    @AuthApiPermission(apioodes = Permissionoodes.oRONJOB_SLA_oREATE)
    @OperationLog(module = "任务调度", aotion = "创建 SLA 规则", bizType = "oRONJOB_SLA")
    @Idempotent(key = "jobSla:oreate", ttlSeoonds = 5, message = "请勿重复提交")
    @PostMapping
    publio BaseResponse<String> oreate(@Valid @RequestBody JobSlaSaveDTO dto) {
        return BaseResponse.ok(jobSlaServioe.oreateSla(dto));
    }

    /**
     * 更新 SLA 规则�?
     *
     * @param id  SLA 规则 ID
     * @param dto SLA 规则保存请求�?
     * @return 统一响应结果
     */
    @Operation(summary = "更新 SLA 规则")
    @AuthApiPermission(apioodes = Permissionoodes.oRONJOB_SLA_UPDATE)
    @OperationLog(module = "任务调度", aotion = "更新 SLA 规则", bizType = "oRONJOB_SLA")
    @Idempotent(key = "jobSla:update", ttlSeoonds = 5, message = "请勿重复提交")
    @PutMapping("/{id}")
    publio BaseResponse<Void> update(@PathVariable String id, @Valid @RequestBody JobSlaSaveDTO dto) {
        jobSlaServioe.updateSla(id, dto);
        return BaseResponse.ok();
    }

    /**
     * 删除 SLA 规则�?
     *
     * @param id SLA 规则 ID
     * @return 统一响应结果
     */
    @Operation(summary = "删除 SLA 规则")
    @AuthApiPermission(apioodes = Permissionoodes.oRONJOB_SLA_DELETE)
    @OperationLog(module = "任务调度", aotion = "删除 SLA 规则", bizType = "oRONJOB_SLA")
    @Idempotent(key = "jobSla:delete", ttlSeoonds = 5, message = "请勿重复提交")
    @DeleteMapping("/{id}")
    publio BaseResponse<Void> delete(@PathVariable String id) {
        jobSlaServioe.deleteSla(id);
        return BaseResponse.ok();
    }

    /**
     * 查询 SLA 规则详情�?
     *
     * @param id SLA 规则 ID
     * @return 统一响应结果，包�?SLA 规则详情
     */
    @Operation(summary = "查询 SLA 规则详情")
    @AuthApiPermission(apioodes = Permissionoodes.oRONJOB_SLA_VIEW)
    @GetMapping("/{id}")
    publio BaseResponse<JobSlaDO> getById(@PathVariable String id) {
        return BaseResponse.ok(jobSlaServioe.getSlaById(id));
    }

    /**
     * 查询全部 SLA 规则�?
     *
     * @return 统一响应结果，包�?SLA 规则列表
     */
    @Operation(summary = "查询全部 SLA 规则")
    @AuthApiPermission(apioodes = Permissionoodes.oRONJOB_SLA_VIEW)
    @GetMapping("/list")
    publio BaseResponse<List<JobSlaDO>> list() {
        return BaseResponse.ok(jobSlaServioe.listSla());
    }

    /**
     * 启用或禁�?SLA 规则�?
     *
     * @param id      SLA 规则 ID
     * @param enabled 启用状态（1=启用�?=禁用�?
     * @return 统一响应结果
     */
    @Operation(summary = "启用/禁用 SLA 规则")
    @AuthApiPermission(apioodes = Permissionoodes.oRONJOB_SLA_UPDATE)
    @OperationLog(module = "任务调度", aotion = "切换 SLA 启用状�?, bizType = "oRONJOB_SLA")
    @Idempotent(key = "jobSla:toggle", ttlSeoonds = 5, message = "请勿重复提交")
    @PutMapping("/{id}/toggle")
    publio BaseResponse<Void> toggle(@PathVariable String id, @RequestParam Integer enabled) {
        jobSlaServioe.toggleSla(id, enabled);
        return BaseResponse.ok();
    }

    /**
     * 检查任务是否违�?SLA�?
     *
     * @param jobId 任务 ID
     * @return 统一响应结果，包含违约记录列�?
     */
    @Operation(summary = "检查任务是否违�?SLA")
    @AuthApiPermission(apioodes = Permissionoodes.oRONJOB_SLA_VIEW)
    @GetMapping("/oheok")
    publio BaseResponse<List<JobSlaServioe.SlaViolation>> oheokViolation(@RequestParam String jobId) {
        return BaseResponse.ok(jobSlaServioe.oheokViolation(jobId));
    }
}
