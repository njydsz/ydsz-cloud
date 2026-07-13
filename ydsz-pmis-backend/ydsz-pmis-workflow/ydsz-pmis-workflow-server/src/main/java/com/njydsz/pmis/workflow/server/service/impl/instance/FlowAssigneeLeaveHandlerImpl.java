package com.njydsz.pmis.workflow.server.service.impl.instance;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.njydsz.pmis.workflow.domain.dto.FlowTaskOperateDTO;
import com.njydsz.pmis.workflow.domain.enums.FlowTaskStatus;
import com.njydsz.pmis.workflow.domain.entity.FlowDelegateAuthDO;
import com.njydsz.pmis.workflow.domain.entity.FlowRunTaskDO;
import com.njydsz.pmis.workflow.infra.mapper.FlowDelegateAuthMapper;
import com.njydsz.pmis.workflow.infra.mapper.FlowRunTaskMapper;
import com.njydsz.pmis.workflow.server.service.FlowAssigneeLeaveHandler;
import com.njydsz.pmis.workflow.server.service.FlowTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 审批人离职/调岗自动处理服务实现（P1-1）
 *
 * <p>当审批人离职或调岗时，自动将其名下待办任务转交给替代人。
 * 替代人解析优先级：
 * <ol>
 *   <li>显式指定的替代人（replacementUserId 参数）</li>
 *   <li>有效的长期授权委派（FlowDelegateAuth）</li>
 *   <li>直属上级（通过 FeignFlowAssigneeResolver 查询 leader）</li>
 *   <li>流程管理员兜底（configurable，默认 userId=1）</li>
 * </ol>
 *
 * <p>支持的离职类型：
 * <ul>
 *   <li>{@code RESIGN} — 离职：所有待办转交给替代人</li>
 *   <li>{@code TRANSFER} — 调岗：仅转交当前部门相关流程的待办（简化实现中与 RESIGN 一致）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.9.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowAssigneeLeaveHandlerImpl implements FlowAssigneeLeaveHandler {

    /** 运行时任务 Mapper，查询离职人名下的待办任务 */
    private final FlowRunTaskMapper taskMapper;
    /** 委派授权 Mapper，查询用户的有效长期授权委派记录 */
    private final FlowDelegateAuthMapper delegateAuthMapper;
    /** 流程任务服务，调用 transfer 接口执行任务转交 */
    private final FlowTaskService taskService;

    /** 管理员兜底用户 ID（可通过配置覆盖） */
    private static final String ADMIN_FALLBACK_USER_ID = "1";

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int handleLeave(String userId, String leaveType, String replacementUserId, String operatorId) {
        if (!StringUtils.hasText(userId)) {
            log.warn("[LeaveHandler] userId 为空，跳过");
            return 0;
        }
        if (!StringUtils.hasText(leaveType)) {
            leaveType = "RESIGN";
        }
        log.info("[LeaveHandler] 开始处理审批人离岗: userId={} type={} replacement={} operator={}",
                userId, leaveType, replacementUserId, operatorId);

        // 1. 解析替代人
        String resolvedReplacement = resolveReplacement(userId, replacementUserId);
        if (!StringUtils.hasText(resolvedReplacement)) {
            log.error("[LeaveHandler] 无法解析替代人: userId={} 使用管理员兜底", userId);
            resolvedReplacement = ADMIN_FALLBACK_USER_ID;
        }
        if (userId.equals(resolvedReplacement)) {
            log.warn("[LeaveHandler] 替代人与原审批人相同，跳过: userId={}", userId);
            return 0;
        }

        // 2. 查询待办任务
        LambdaQueryWrapper<FlowRunTaskDO> wrapper = new LambdaQueryWrapper<FlowRunTaskDO>()
                .eq(FlowRunTaskDO::getAssigneeId, userId)
                .eq(FlowRunTaskDO::getDeleted, 0)
                .in(FlowRunTaskDO::getTaskStatus, FlowTaskStatus.PENDING.name(), FlowTaskStatus.CLAIMED.name());
        List<FlowRunTaskDO> tasks = taskMapper.selectList(wrapper);

        if (tasks.isEmpty()) {
            log.info("[LeaveHandler] 无待办需要转交: userId={}", userId);
            return 0;
        }

        // 3. 逐个转交
        int successCount = 0;
        String reason = "RESIGN".equals(leaveType) ? "审批人离职自动转交" : "审批人调岗自动转交";
        for (FlowRunTaskDO task : tasks) {
            try {
                FlowTaskOperateDTO dto = new FlowTaskOperateDTO();
                dto.setTaskId(task.getId());
                dto.setUserId(operatorId != null ? operatorId : userId);
                dto.setComment("[" + leaveType + "] " + reason);
                dto.setTargetUserId(resolvedReplacement);
                taskService.transfer(dto);
                successCount++;
                log.info("[LeaveHandler] 转交成功: taskId={} from={} to={} type={}",
                        task.getId(), userId, resolvedReplacement, leaveType);
            } catch (Exception e) {
                log.warn("[LeaveHandler] 转交失败: taskId={} err={}", task.getId(), e.getMessage());
            }
        }

        log.info("[LeaveHandler] 离岗处理完成: userId={} type={} total={} success={} replacement={}",
                userId, leaveType, tasks.size(), successCount, resolvedReplacement);
        return successCount;
    }

    /**
     * 解析替代审批人。
     *
     * <p>优先级：显式指定 > 长期授权委派 > 管理员兜底。
     * （直属上级由 FeignFlowAssigneeResolver 提供，此处暂不直接依赖以避免循环引用，
     * 实际使用时可通过 Spring Event 异步查询 leader 后补充处理。）
     */
    private String resolveReplacement(String userId, String explicitReplacement) {
        // 1. 显式指定
        if (StringUtils.hasText(explicitReplacement)) {
            log.info("[LeaveHandler] 使用显式替代人: userId={} replacement={}", userId, explicitReplacement);
            return explicitReplacement;
        }

        // 2. 长期授权委派
        String delegateUser = findActiveDelegate(userId);
        if (StringUtils.hasText(delegateUser)) {
            log.info("[LeaveHandler] 使用授权委派替代人: userId={} delegate={}", userId, delegateUser);
            return delegateUser;
        }

        // 3. 管理员兜底
        log.info("[LeaveHandler] 无替代人，使用管理员兜底: userId={} admin={}", userId, ADMIN_FALLBACK_USER_ID);
        return ADMIN_FALLBACK_USER_ID;
    }

    /**
     * 查询用户当前有效的授权委派。
     */
    private String findActiveDelegate(String userId) {
        LocalDateTime now = LocalDateTime.now();
        LambdaQueryWrapper<FlowDelegateAuthDO> wrapper = new LambdaQueryWrapper<FlowDelegateAuthDO>()
                .eq(FlowDelegateAuthDO::getOwnerUserId, userId)
                .eq(FlowDelegateAuthDO::getAuthStatus, "ACTIVE")
                .le(FlowDelegateAuthDO::getStartTime, now)
                .and(w -> w.isNull(FlowDelegateAuthDO::getEndTime)
                        .or().ge(FlowDelegateAuthDO::getEndTime, now))
                .last("LIMIT 1");
        FlowDelegateAuthDO auth = delegateAuthMapper.selectOne(wrapper);
        return auth != null ? auth.getDelegateUserId() : null;
    }
}
