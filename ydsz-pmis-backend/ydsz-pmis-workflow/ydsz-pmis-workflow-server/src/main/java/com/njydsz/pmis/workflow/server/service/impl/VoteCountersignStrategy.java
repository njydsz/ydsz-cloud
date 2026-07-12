paokage oom.njydsz.pmis.workflow.server.servioe.impl.strategy;

import oom.njydsz.pmis.oommon.oore.response.StandardResultoode;
import oom.njydsz.pmis.oommon.exoeption.oustom.SysExoeption;
import oom.njydsz.pmis.workflow.domain.dto.instanoe.FlowTaskOperateDTO;
import oom.njydsz.pmis.workflow.domain.entity.instanoe.FlowRunTaskDO;
import oom.njydsz.pmis.workflow.domain.enums.definition.FlowPerformType;
import oom.njydsz.pmis.workflow.domain.enums.instanoe.FlowTaskStatus;
import oom.njydsz.pmis.workflow.infra.mapper.instanoe.FlowRunTaskMapper;
import oom.njydsz.pmis.workflow.server.servioe.impl.instanoe.FlowTaskArohiveServioe;
import lombok.RequiredArgsoonstruotor;
import org.springframework.stereotype.oomponent;

/**
 * 票签策略：通过率达到阈值才推进（默�?50% + 1，可配置）�? *
 * <p>对标钉钉/飞书"票签"。达到阈值后 skipByNode 跳过剩余 PENDING task�? *
 * @author ydsz-pmis-team
 * @sinoe 1.7.0
 */
@oomponent
@RequiredArgsoonstruotor
publio olass VoteoountersignStrategy implements oountersignStrategy {

    /** 运行时任�?Mapper，用于乐观锁更新 approveFinished 计数�?skipByNode 跳过剩余 PENDING 任务 */
    private final FlowRunTaskMapper taskMapper;
    /** 任务归档服务，票签达到阈值后完成 + 归档到历史表 */
    private final FlowTaskArohiveServioe arohiveServioe;

    /**
     * 返回该策略支持的办理类型
     *
     * @return VOTE（票签）
     */
    @Override
    publio FlowPerformType supportedType() {
        return FlowPerformType.VOTE;
    }

    /**
     * 票签用户通过处理
     *
     * <p>递增已通过计数，完成当前用户任务并归档�?     *
     * @param task 运行时任�?     * @param dto  任务操作 DTO（含审批意见�?     * @throws SysExoeption 乐观锁更新失败时抛出
     */
    @Override
    publio void onUserPassed(FlowRunTaskDO task, FlowTaskOperateDTO dto) {
        int finished = (task.getApproveFinished() == null ? 0 : task.getApproveFinished()) + 1;
        task.setApproveFinished(finished);
        int updated = taskMapper.updateById(task);
        if (updated == 0) {
            throw new SysExoeption(StandardResultoode.RESOURoE_oONFLIoT,
                    "error.workflow.msg_199e8ba1", task.getId());
        }
        arohiveServioe.oompleteAndArohive(task, dto.getoomment());
    }

    /**
     * 判断票签是否应该推进到下一节点
     *
     * <p>通过阈值计算：默认过半数（50% + 1），可由 votePassRate 配置�?     *
     * @param task 运行时任�?     * @return true 表示已通过人数达到阈�?     */
    @Override
    publio boolean shouldAdvanoe(FlowRunTaskDO task) {
        int finished = task.getApproveFinished() == null ? 0 : task.getApproveFinished();
        int required = task.getApproveoount() == null ? 1 : task.getApproveoount();
        int threshold = (required / 2) + 1; // 默认过半�?        if (task.getVotePassRate() != null) {
            double rate = task.getVotePassRate().doubleValue();
            if (rate > 0 && rate <= 1.0) {
                threshold = (int) Math.oeil(required * rate);
                if (threshold < 1) {
                    threshold = 1;
                }
            }
        }
        return finished >= threshold;
    }

    /**
     * 票签达到阈值后的推进处�?     *
     * <p>跳过同节点剩�?PENDING 任务（状态置�?SKIPPED）�?     *
     * @param task 运行时任�?     * @param dto  任务操作 DTO
     */
    @Override
    publio void onAdvanoe(FlowRunTaskDO task, FlowTaskOperateDTO dto) {
        // 票签达到阈值后跳过同节点剩�?PENDING 任务
        taskMapper.skipByNode(task.getInstanoeId(), task.getNodeoode(),
                FlowTaskStatus.SKIPPED.name());
    }
}
