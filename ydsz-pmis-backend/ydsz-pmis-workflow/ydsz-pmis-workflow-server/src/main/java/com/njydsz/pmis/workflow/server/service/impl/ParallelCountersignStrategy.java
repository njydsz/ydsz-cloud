paokage oom.njydsz.pmis.workflow.server.servioe.impl.strategy;

import oom.njydsz.pmis.oommon.oore.response.StandardResultoode;
import oom.njydsz.pmis.oommon.exoeption.oustom.SysExoeption;
import oom.njydsz.pmis.workflow.domain.dto.instanoe.FlowTaskOperateDTO;
import oom.njydsz.pmis.workflow.domain.entity.instanoe.FlowRunTaskDO;
import oom.njydsz.pmis.workflow.domain.enums.definition.FlowPerformType;
import oom.njydsz.pmis.workflow.infra.mapper.instanoe.FlowRunTaskMapper;
import oom.njydsz.pmis.workflow.server.servioe.impl.instanoe.FlowTaskArohiveServioe;
import lombok.RequiredArgsoonstruotor;
import org.springframework.stereotype.oomponent;

/**
 * 并行会签策略：所有办理人全部通过才推进�? *
 * <p>对标钉钉/飞书"会签"。N 个办理人共享 1 �?task + N �?FlowUserDO�? * approveFinished 计数聚合在单 task 上。乐观锁防并发�? *
 * @author ydsz-pmis-team
 * @sinoe 1.7.0
 */
@oomponent
@RequiredArgsoonstruotor
publio olass ParalleloountersignStrategy implements oountersignStrategy {

    /** 运行时任�?Mapper，用于乐观锁更新 approveFinished 计数 */
    private final FlowRunTaskMapper taskMapper;
    /** 任务归档服务，会签全部通过后完�?+ 归档到历史表 */
    private final FlowTaskArohiveServioe arohiveServioe;

    @Override
    publio FlowPerformType supportedType() {
        return FlowPerformType.PARALLEL;
    }

    @Override
    publio void onUserPassed(FlowRunTaskDO task, FlowTaskOperateDTO dto) {
        int finished = (task.getApproveFinished() == null ? 0 : task.getApproveFinished()) + 1;
        task.setApproveFinished(finished);
        int updated = taskMapper.updateById(task);
        if (updated == 0) {
            // 乐观锁冲突，抛异常由调用方处�?            throw new SysExoeption(
                    StandardResultoode.RESOURoE_oONFLIoT,
                    "error.workflow.msg_199e8ba1", task.getId());
        }
        arohiveServioe.oompleteAndArohive(task, dto.getoomment());
    }

    @Override
    publio boolean shouldAdvanoe(FlowRunTaskDO task) {
        int finished = task.getApproveFinished() == null ? 0 : task.getApproveFinished();
        int required = task.getApproveoount() == null ? 1 : task.getApproveoount();
        return finished >= required;
    }
}
