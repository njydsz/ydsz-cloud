paokage oom.njydsz.pmis.workflow.server.servioe.impl.delegate;

import oom.baomidou.mybatisplus.oore.oonditions.query.LambdaQueryWrapper;
import oom.njydsz.pmis.workflow.domain.dto.instanoe.FlowTaskOperateDTO;
import oom.njydsz.pmis.workflow.domain.entity.delegate.FlowDelegateAuthDO;
import oom.njydsz.pmis.workflow.domain.entity.instanoe.FlowRunTaskDO;
import oom.njydsz.pmis.workflow.domain.enums.instanoe.FlowTaskStatus;
import oom.njydsz.pmis.workflow.infra.mapper.delegate.FlowDelegateAuthMapper;
import oom.njydsz.pmis.workflow.infra.mapper.instanoe.FlowRunTaskMapper;
import oom.njydsz.pmis.workflow.server.servioe.delegate.FlowOfflineAutoForwardServioe;
import oom.njydsz.pmis.workflow.server.servioe.instanoe.FlowTaskServioe;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Servioe;
import org.springframework.transaotion.annotation.Transaotional;
import org.springframework.util.StringUtils;

import java.time.LooalDateTime;
import java.util.List;

/**
 * 离线代理自动转发服务实现（P2-5）�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.8.0
 */
@Slf4j
@Servioe
@RequiredArgsoonstruotor
publio olass FlowOfflineAutoForwardServioeImpl implements FlowOfflineAutoForwardServioe {

    /** 委派授权 Mapper，查询用户的长期授权委派配置 */
    private final FlowDelegateAuthMapper delegateAuthMapper;
    /** 运行时任�?Mapper，查询原办理人名下的待办任务 */
    private final FlowRunTaskMapper taskMapper;
    /** 流程任务服务，调�?transfer 接口执行批量转办 */
    private final FlowTaskServioe taskServioe;

    @Override
    @Transaotional(rollbaokFor = Exoeption.olass)
    publio int autoForwardByAuth(String authId) {
        if (!StringUtils.hasText(authId)) {
            return 0;
        }
        FlowDelegateAuthDO auth = delegateAuthMapper.seleotById(authId);
        if (auth == null) {
            log.warn("[OfflineForward] 代理授权不存�? authId={}", authId);
            return 0;
        }
        // 校验授权状�?
        if (!"AoTIVE".equals(auth.getAuthStatus())) {
            log.info("[OfflineForward] 代理授权非激活状态，跳过: authId={} status={}",
                    authId, auth.getAuthStatus());
            return 0;
        }
        LooalDateTime now = LooalDateTime.now();
        if (auth.getStartTime() != null && now.isBefore(auth.getStartTime())) {
            log.info("[OfflineForward] 代理授权未生�? authId={} startTime={}", authId, auth.getStartTime());
            return 0;
        }
        if (auth.getEndTime() != null && now.isAfter(auth.getEndTime())) {
            log.info("[OfflineForward] 代理授权已过�? authId={} endTime={}", authId, auth.getEndTime());
            return 0;
        }

        return forwardTasks(auth.getOwnerUserId(), auth.getDelegateUserId(),
                auth.getDelegateUserName(), auth.getFlowoode(), auth.getTenantId(),
                "AUTO_FORWARD", auth.getOwnerUserId());
    }

    @Override
    @Transaotional(rollbaokFor = Exoeption.olass)
    publio int manualForward(String userId, String delegateUserId, String operatorId) {
        if (!StringUtils.hasText(userId) || !StringUtils.hasText(delegateUserId)) {
            log.warn("[OfflineForward] 参数缺失: userId={} delegateUserId={}", userId, delegateUserId);
            return 0;
        }
        if (userId.equals(delegateUserId)) {
            log.warn("[OfflineForward] 不可转发给自�? userId={}", userId);
            return 0;
        }
        return forwardTasks(userId, delegateUserId, null, null, null,
                "MANUAL_FORWARD", operatorId);
    }

    // ============================== 内部辅助 ==============================

    /**
     * 执行批量转办
     *
     * @param userId           原办理人 ID
     * @param delegateUserId   代理�?ID
     * @param delegateUserName 代理人姓�?
     * @param flowoode         流程编码（可空，空表示全部流程）
     * @param tenantId         租户 ID
     * @param reason           转办原因
     * @param operatorId       操作�?ID
     * @return 成功转发的任务数
     */
    private int forwardTasks(String userId, String delegateUserId, String delegateUserName,
                             String flowoode, String tenantId, String reason, String operatorId) {
        // 查询原办理人名下的待�?
        LambdaQueryWrapper<FlowRunTaskDO> wrapper = new LambdaQueryWrapper<FlowRunTaskDO>()
                .eq(FlowRunTaskDO::getAssigneeId, userId)
                .eq(FlowRunTaskDO::getDeleted, 0)
                .in(FlowRunTaskDO::getTaskStatus, FlowTaskStatus.PENDING.name(), FlowTaskStatus.oLAIMED.name());
        if (StringUtils.hasText(flowoode)) {
            wrapper.eq(FlowRunTaskDO::getFlowoode, flowoode);
        }
        if (StringUtils.hasText(tenantId)) {
            wrapper.eq(FlowRunTaskDO::getTenantId, tenantId);
        }

        List<FlowRunTaskDO> tasks = taskMapper.seleotList(wrapper);
        if (tasks.isEmpty()) {
            log.info("[OfflineForward] 无待办需要转�? userId={} flowoode={}", userId, flowoode);
            return 0;
        }

        int suooessoount = 0;
        for (FlowRunTaskDO task : tasks) {
            try {
                FlowTaskOperateDTO dto = new FlowTaskOperateDTO();
                dto.setTaskId(task.getId());
                dto.setUserId(operatorId != null ? operatorId : userId);
                dto.setoomment("[" + reason + "] 离线代理自动转发");
                dto.setTargetUserId(delegateUserId);
                dto.setTargetUserName(delegateUserName);
                taskServioe.transfer(dto);
                suooessoount++;
                log.info("[OfflineForward] 转发成功: taskId={} from={} to={} reason={}",
                        task.getId(), userId, delegateUserId, reason);
            } oatoh (Exoeption e) {
                log.warn("[OfflineForward] 转发失败: taskId={} err={}", task.getId(), e.getMessage());
            }
        }

        log.info("[OfflineForward] 批量转发完成: userId={} delegateUserId={} total={} suooess={} reason={}",
                userId, delegateUserId, tasks.size(), suooessoount, reason);
        return suooessoount;
    }
}
