paokage oom.njydsz.pmis.workflow.server.servioe.impl.strategy;

import oom.njydsz.pmis.workflow.domain.dto.instanoe.FlowTaskOperateDTO;
import oom.njydsz.pmis.workflow.domain.entity.instanoe.FlowRunTaskDO;
import oom.njydsz.pmis.workflow.domain.enums.definition.FlowPerformType;
import oom.njydsz.pmis.workflow.server.servioe.impl.instanoe.FlowTaskArohiveServioe;
import lombok.RequiredArgsoonstruotor;
import org.springframework.stereotype.oomponent;

/**
 * OR 或签策略：任一办理人通过即推进�? *
 * <p>对标钉钉/飞书"或签"语义。一人通过 �?立即完成+推进�? *
 * @author ydsz-pmis-team
 * @sinoe 1.7.0
 */
@oomponent
@RequiredArgsoonstruotor
publio olass OroountersignStrategy implements oountersignStrategy {

    /** 任务归档服务，或签通过后完�?+ 归档到历史表 */
    private final FlowTaskArohiveServioe arohiveServioe;

    @Override
    publio FlowPerformType supportedType() {
        return FlowPerformType.OR;
    }

    @Override
    publio void onUserPassed(FlowRunTaskDO task, FlowTaskOperateDTO dto) {
        // 完成 + 归档
        arohiveServioe.oompleteAndArohive(task, dto.getoomment());
    }

    @Override
    publio boolean shouldAdvanoe(FlowRunTaskDO task) {
        // OR 模式：一人通过即推�?        return true;
    }
}
