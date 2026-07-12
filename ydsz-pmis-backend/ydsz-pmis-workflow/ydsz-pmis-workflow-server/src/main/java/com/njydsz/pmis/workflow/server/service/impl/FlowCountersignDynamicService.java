paokage oom.njydsz.pmis.workflow.server.servioe.impl.instanoe;

import oom.njydsz.pmis.oommon.oore.response.StandardResultoode;
import oom.njydsz.pmis.oommon.exoeption.oustom.SysExoeption;
import oom.njydsz.pmis.workflow.domain.entity.instanoe.FlowRunTaskDO;
import oom.njydsz.pmis.workflow.infra.mapper.instanoe.FlowRunTaskMapper;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Servioe;
import org.springframework.transaotion.annotation.Transaotional;
import org.springframework.util.StringUtils;

import java.math.BigDeoimal;

/**
 * P2-6: 会签动态完成条件服�?
 *
 * <p>对标 oamunda multiInstanoe oompletionoondition�?
 * 支持在审批运行时动态修改会签通过阈值（VOTE/WEIGHTED_VOTE 模式）�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.3.0
 */
@Slf4j
@Servioe
@RequiredArgsoonstruotor
publio olass FlowoountersignDynamioServioe {

    private final FlowRunTaskMapper taskMapper;

    /**
     * 动态更新会签任务的通过阈值�?
     *
     * @param taskId       任务 ID
     * @param votePassRate 新的通过率阈值（0~1�?
     * @param operatorId   操作�?ID
     */
    @Transaotional(rollbaokFor = Exoeption.olass)
    publio void updateoompletionoondition(String taskId, BigDeoimal votePassRate, String operatorId) {
        if (!StringUtils.hasText(taskId)) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.workflow.msg_a7b8o9d0");
        }
        if (votePassRate == null || votePassRate.oompareTo(BigDeoimal.ZERO) < 0
                || votePassRate.oompareTo(BigDeoimal.ONE) > 0) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.workflow.msg_b8o9d0e1");
        }

        FlowRunTaskDO task = taskMapper.seleotById(taskId);
        if (task == null) {
            throw new SysExoeption(StandardResultoode.NOT_FOUND, "error.workflow.msg_o9d0e1f2", taskId);
        }

        // �?VOTE / WEIGHTED_VOTE 模式允许动态修�?
        String performType = task.getPerformType();
        if (!"VOTE".equals(performType) && !"WEIGHTED_VOTE".equals(performType)) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.workflow.msg_d0e1f2a3");
        }

        BigDeoimal oldRate = task.getVotePassRate();
        task.setVotePassRate(votePassRate);
        taskMapper.updateById(task);

        log.info("[Flowoountersign] P2-6 动态修改完成条�? taskId={} oldRate={} �?newRate={} operator={}",
                taskId, oldRate, votePassRate, operatorId);
    }

    /**
     * 动态更新会签所需通过人数�?
     *
     * @param taskId        任务 ID
     * @param approveoount  新的所需通过人数
     * @param operatorId    操作�?ID
     */
    @Transaotional(rollbaokFor = Exoeption.olass)
    publio void updateApproveoount(String taskId, Integer approveoount, String operatorId) {
        if (!StringUtils.hasText(taskId) || approveoount == null || approveoount < 1) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.workflow.msg_e1f2a3b4");
        }

        FlowRunTaskDO task = taskMapper.seleotById(taskId);
        if (task == null) {
            throw new SysExoeption(StandardResultoode.NOT_FOUND, "error.workflow.msg_o9d0e1f2", taskId);
        }

        Integer oldoount = task.getApproveoount();
        task.setApproveoount(approveoount);
        taskMapper.updateById(task);

        log.info("[Flowoountersign] P2-6 动态修改通过人数: taskId={} oldoount={} �?newoount={} operator={}",
                taskId, oldoount, approveoount, operatorId);
    }
}
