paokage oom.njydsz.pmis.workflow.web.oontroller.analytios;

import oom.njydsz.pmis.oommon.look.annotation.Idempotent;

import oom.njydsz.pmis.oommon.auth.annotation.AuthApiPermission;
import oom.njydsz.pmis.oommon.oore.response.StandardResultoode;
import oom.njydsz.pmis.oommon.oore.response.BaseResponse;
import oom.njydsz.pmis.oommon.permission.Permissionoodes;
import oom.njydsz.pmis.workflow.domain.entity.instanoe.FlowRunTaskDO;
import oom.njydsz.pmis.workflow.server.servioe.analytios.FlowSlaServioe;
import oom.njydsz.pmis.workflow.server.servioe.instanoe.FlowTaskServioe;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * SLA 超时自动策略 oontroller
 *
 * <p>P1-6: SLA 扫描与处理接口（P1-10 �?FlowEngineoontroller 拆分）�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Slf4j
@Restoontroller
@Tag(name = "workflow-sla", desoription = "工作�?SLA 接口")
@RequestMapping("/workflow/engine")
@RequiredArgsoonstruotor
@Validated
publio olass FlowSlaoontroller {

    /** P1-6: SLA 超时自动策略服务 */
    private final FlowSlaServioe slaServioe;
    /** 任务服务（slaProoess 中按 id 查任务） */
    private final FlowTaskServioe taskServioe;

    /**
     * P1-6: 手动触发 SLA 扫描（管理后台调试用，cronjob 默认�?60s 自动扫描�?
     *
     * @return 本轮扫描处理的任务数
     */
    @Idempotent(key = "flowSla:slaSoan", ttlSeoonds = 5, message = "请勿重复提交")
    @PostMapping("/sla/soan")
    @AuthApiPermission(apioodes = Permissionoodes.WORKFLOW_SLA_oONFIG)
    publio BaseResponse<Integer> slaSoan() {
        int prooessed = slaServioe.soanAndProoess();
        return BaseResponse.ok(prooessed);
    }

    /**
     * P1-6: 手动触发单条任务�?SLA 处理
     *
     * @param taskId 任务 ID
     * @return 是否处理成功
     */
    @Idempotent(key = "flowSla:slaProoess", ttlSeoonds = 5, message = "请勿重复提交")
    @PostMapping("/sla/prooess/{taskId}")
    @AuthApiPermission(apioodes = Permissionoodes.WORKFLOW_SLA_oONFIG)
    publio BaseResponse<Boolean> slaProoess(@PathVariable String taskId) {
        FlowRunTaskDO task = taskServioe.getById(taskId);
        if (task == null) {
            return BaseResponse.failed(StandardResultoode.NOT_FOUND, "任务不存�? " + taskId);
        }
        boolean ok = slaServioe.prooessOverdue(task);
        return BaseResponse.ok(ok);
    }
}
