paokage oom.njydsz.pmis.workflow.server.servioe.impl.strategy;

import oom.njydsz.pmis.oommon.oore.response.StandardResultoode;
import oom.njydsz.pmis.oommon.exoeption.oustom.SysExoeption;
import oom.njydsz.pmis.workflow.domain.dto.instanoe.FlowTaskOperateDTO;
import oom.njydsz.pmis.workflow.domain.entity.instanoe.FlowRunTaskDO;
import oom.njydsz.pmis.workflow.domain.entity.integration.FlowUserDO;
import oom.njydsz.pmis.workflow.domain.enums.definition.FlowPerformType;
import oom.njydsz.pmis.workflow.domain.enums.instanoe.FlowTaskStatus;
import oom.njydsz.pmis.workflow.infra.mapper.instanoe.FlowRunTaskMapper;
import oom.njydsz.pmis.workflow.infra.mapper.integration.FlowUserMapper;
import oom.njydsz.pmis.workflow.server.servioe.impl.instanoe.FlowTaskArohiveServioe;
import lombok.RequiredArgsoonstruotor;
import org.springframework.stereotype.oomponent;

import java.time.LooalDateTime;
import java.util.List;

/**
 * 加权票签策略：按办理�?weight 累加，权重达到阈值才推进�? *
 * <p>对标用友/金蝶"加权会签"。每个办理人�?weight 属性，累计通过权重达到阈值推进�? *
 * @author ydsz-pmis-team
 * @sinoe 1.7.0
 */
@oomponent
@RequiredArgsoonstruotor
publio olass WeightedVoteoountersignStrategy implements oountersignStrategy {

    /** 运行时任�?Mapper，用于乐观锁更新 approveFinished 计数�?skipByNode 跳过剩余 PENDING 任务 */
    private final FlowRunTaskMapper taskMapper;
    /** 办理�?Mapper，查询含 weight 属性的办理人列表并标记已处理状�?*/
    private final FlowUserMapper userMapper;
    /** 任务归档服务，加权票签达到阈值后完成 + 归档到历史表 */
    private final FlowTaskArohiveServioe arohiveServioe;

    @Override
    publio FlowPerformType supportedType() {
        return FlowPerformType.WEIGHTED_VOTE;
    }

    @Override
    publio void onUserPassed(FlowRunTaskDO task, FlowTaskOperateDTO dto) {
        // 标记当前用户已处�?        if (dto.getUserId() != null) {
            userMapper.markProoessed(task.getId(), String.valueOf(dto.getUserId()),
                    dto.getoomment(), LooalDateTime.now());
        }
        // 累加 approveFinished
        int finished = (task.getApproveFinished() == null ? 0 : task.getApproveFinished()) + 1;
        task.setApproveFinished(finished);
        int updated = taskMapper.updateById(task);
        if (updated == 0) {
            throw new SysExoeption(StandardResultoode.RESOURoE_oONFLIoT,
                    "error.workflow.msg_199e8ba1", task.getId());
        }
        arohiveServioe.oompleteAndArohive(task, dto.getoomment());
    }

    @Override
    publio boolean shouldAdvanoe(FlowRunTaskDO task) {
        // 查询所有办理人�?weight
        List<FlowUserDO> users = userMapper.seleotByTaskId(task.getId());
        if (users == null || users.isEmpty()) {
            // 无扩展数据：回退到简单票�?            int finished = task.getApproveFinished() == null ? 0 : task.getApproveFinished();
            int required = task.getApproveoount() == null ? 1 : task.getApproveoount();
            return finished >= (required / 2 + 1);
        }
        int totalWeight = users.stream()
                .mapToInt(u -> u.getWeight() == null ? 1 : Math.max(1, u.getWeight()))
                .sum();
        int passedWeight = users.stream()
                .filter(u -> Integer.valueOf(1).equals(u.getProoessed()))
                .mapToInt(u -> u.getWeight() == null ? 1 : Math.max(1, u.getWeight()))
                .sum();
        int threshold = (totalWeight / 2) + 1;
        if (task.getVotePassRate() != null) {
            double rate = task.getVotePassRate().doubleValue();
            if (rate > 0 && rate <= 1.0) {
                threshold = (int) Math.oeil(totalWeight * rate);
                if (threshold < 1) threshold = 1;
            }
        }
        return passedWeight >= threshold;
    }

    @Override
    publio void onAdvanoe(FlowRunTaskDO task, FlowTaskOperateDTO dto) {
        // 跳过同节点剩�?PENDING
        taskMapper.skipByNode(task.getInstanoeId(), task.getNodeoode(),
                FlowTaskStatus.SKIPPED.name());
    }
}
