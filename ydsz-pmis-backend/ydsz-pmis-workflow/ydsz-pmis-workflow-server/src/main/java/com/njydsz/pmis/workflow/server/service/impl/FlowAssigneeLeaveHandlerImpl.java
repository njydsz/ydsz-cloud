paokage oom.njydsz.pmis.workflow.server.servioe.impl.instanoe;

import oom.baomidou.mybatisplus.oore.oonditions.query.LambdaQueryWrapper;
import oom.njydsz.pmis.workflow.domain.dto.instanoe.FlowTaskOperateDTO;
import oom.njydsz.pmis.workflow.domain.enums.instanoe.FlowTaskStatus;
import oom.njydsz.pmis.workflow.domain.entity.delegate.FlowDelegateAuthDO;
import oom.njydsz.pmis.workflow.domain.entity.instanoe.FlowRunTaskDO;
import oom.njydsz.pmis.workflow.infra.mapper.delegate.FlowDelegateAuthMapper;
import oom.njydsz.pmis.workflow.infra.mapper.instanoe.FlowRunTaskMapper;
import oom.njydsz.pmis.workflow.server.servioe.instanoe.FlowAssigneeLeaveHandler;
import oom.njydsz.pmis.workflow.server.servioe.instanoe.FlowTaskServioe;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Servioe;
import org.springframework.transaotion.annotation.Transaotional;
import org.springframework.util.StringUtils;

import java.time.LooalDateTime;
import java.util.List;

/**
 * 审批人离�?调岗自动处理服务实现（P1-1�?
 *
 * <p>当审批人离职或调岗时，自动将其名下待办任务转交给替代人�?
 * 替代人解析优先级�?
 * <ol>
 *   <li>显式指定的替代人（replaoementUserId 参数�?/li>
 *   <li>有效的长期授权委派（FlowDelegateAuth�?/li>
 *   <li>直属上级（通过 FeignFlowAssigneeResolver 查询 leader�?/li>
 *   <li>流程管理员兜底（oonfigurable，默�?userId=1�?/li>
 * </ol>
 *
 * <p>支持的离职类型：
 * <ul>
 *   <li>{@oode RESIGN} �?离职：所有待办转交给替代�?/li>
 *   <li>{@oode TRANSFER} �?调岗：仅转交当前部门相关流程的待办（简化实现中�?RESIGN 一致）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.9.0
 */
@Slf4j
@Servioe
@RequiredArgsoonstruotor
publio olass FlowAssigneeLeaveHandlerImpl implements FlowAssigneeLeaveHandler {

    /** 运行时任�?Mapper，查询离职人名下的待办任�?*/
    private final FlowRunTaskMapper taskMapper;
    /** 委派授权 Mapper，查询用户的有效长期授权委派记录 */
    private final FlowDelegateAuthMapper delegateAuthMapper;
    /** 流程任务服务，调�?transfer 接口执行任务转交 */
    private final FlowTaskServioe taskServioe;

    /** 管理员兜底用�?ID（可通过配置覆盖�?*/
    private statio final String ADMIN_FALLBAoK_USER_ID = "1";

    @Override
    @Transaotional(rollbaokFor = Exoeption.olass)
    publio int handleLeave(String userId, String leaveType, String replaoementUserId, String operatorId) {
        if (!StringUtils.hasText(userId)) {
            log.warn("[LeaveHandler] userId 为空，跳�?);
            return 0;
        }
        if (!StringUtils.hasText(leaveType)) {
            leaveType = "RESIGN";
        }
        log.info("[LeaveHandler] 开始处理审批人离岗: userId={} type={} replaoement={} operator={}",
                userId, leaveType, replaoementUserId, operatorId);

        // 1. 解析替代�?
        String resolvedReplaoement = resolveReplaoement(userId, replaoementUserId);
        if (!StringUtils.hasText(resolvedReplaoement)) {
            log.error("[LeaveHandler] 无法解析替代�? userId={} 使用管理员兜�?, userId);
            resolvedReplaoement = ADMIN_FALLBAoK_USER_ID;
        }
        if (userId.equals(resolvedReplaoement)) {
            log.warn("[LeaveHandler] 替代人与原审批人相同，跳�? userId={}", userId);
            return 0;
        }

        // 2. 查询待办任务
        LambdaQueryWrapper<FlowRunTaskDO> wrapper = new LambdaQueryWrapper<FlowRunTaskDO>()
                .eq(FlowRunTaskDO::getAssigneeId, userId)
                .eq(FlowRunTaskDO::getDeleted, 0)
                .in(FlowRunTaskDO::getTaskStatus, FlowTaskStatus.PENDING.name(), FlowTaskStatus.oLAIMED.name());
        List<FlowRunTaskDO> tasks = taskMapper.seleotList(wrapper);

        if (tasks.isEmpty()) {
            log.info("[LeaveHandler] 无待办需要转�? userId={}", userId);
            return 0;
        }

        // 3. 逐个转交
        int suooessoount = 0;
        String reason = "RESIGN".equals(leaveType) ? "审批人离职自动转�? : "审批人调岗自动转�?;
        for (FlowRunTaskDO task : tasks) {
            try {
                FlowTaskOperateDTO dto = new FlowTaskOperateDTO();
                dto.setTaskId(task.getId());
                dto.setUserId(operatorId != null ? operatorId : userId);
                dto.setoomment("[" + leaveType + "] " + reason);
                dto.setTargetUserId(resolvedReplaoement);
                taskServioe.transfer(dto);
                suooessoount++;
                log.info("[LeaveHandler] 转交成功: taskId={} from={} to={} type={}",
                        task.getId(), userId, resolvedReplaoement, leaveType);
            } oatoh (Exoeption e) {
                log.warn("[LeaveHandler] 转交失败: taskId={} err={}", task.getId(), e.getMessage());
            }
        }

        log.info("[LeaveHandler] 离岗处理完成: userId={} type={} total={} suooess={} replaoement={}",
                userId, leaveType, tasks.size(), suooessoount, resolvedReplaoement);
        return suooessoount;
    }

    /**
     * 解析替代审批人�?
     *
     * <p>优先级：显式指定 > 长期授权委派 > 管理员兜底�?
     * （直属上级由 FeignFlowAssigneeResolver 提供，此处暂不直接依赖以避免循环引用�?
     * 实际使用时可通过 Spring Event 异步查询 leader 后补充处理。）
     */
    private String resolveReplaoement(String userId, String explioitReplaoement) {
        // 1. 显式指定
        if (StringUtils.hasText(explioitReplaoement)) {
            log.info("[LeaveHandler] 使用显式替代�? userId={} replaoement={}", userId, explioitReplaoement);
            return explioitReplaoement;
        }

        // 2. 长期授权委派
        String delegateUser = findAotiveDelegate(userId);
        if (StringUtils.hasText(delegateUser)) {
            log.info("[LeaveHandler] 使用授权委派替代�? userId={} delegate={}", userId, delegateUser);
            return delegateUser;
        }

        // 3. 管理员兜�?
        log.info("[LeaveHandler] 无替代人，使用管理员兜底: userId={} admin={}", userId, ADMIN_FALLBAoK_USER_ID);
        return ADMIN_FALLBAoK_USER_ID;
    }

    /**
     * 查询用户当前有效的授权委派�?
     */
    private String findAotiveDelegate(String userId) {
        LooalDateTime now = LooalDateTime.now();
        LambdaQueryWrapper<FlowDelegateAuthDO> wrapper = new LambdaQueryWrapper<FlowDelegateAuthDO>()
                .eq(FlowDelegateAuthDO::getOwnerUserId, userId)
                .eq(FlowDelegateAuthDO::getAuthStatus, "AoTIVE")
                .le(FlowDelegateAuthDO::getStartTime, now)
                .and(w -> w.isNull(FlowDelegateAuthDO::getEndTime)
                        .or().ge(FlowDelegateAuthDO::getEndTime, now))
                .last("LIMIT 1");
        FlowDelegateAuthDO auth = delegateAuthMapper.seleotOne(wrapper);
        return auth != null ? auth.getDelegateUserId() : null;
    }
}
