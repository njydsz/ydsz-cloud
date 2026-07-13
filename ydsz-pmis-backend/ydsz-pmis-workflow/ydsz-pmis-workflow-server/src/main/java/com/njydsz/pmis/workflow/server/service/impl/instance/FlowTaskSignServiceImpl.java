package com.njydsz.pmis.workflow.server.service.impl.instance;

import com.njydsz.pmis.common.core.response.StandardResultCode;
import com.njydsz.pmis.common.exception.custom.SysException;
import com.njydsz.pmis.workflow.domain.dto.FlowTaskOperateDTO;
import com.njydsz.pmis.workflow.domain.entity.FlowRunTaskDO;
import com.njydsz.pmis.workflow.domain.entity.FlowUserDO;
import com.njydsz.pmis.workflow.domain.enums.FlowAssigneeType;
import com.njydsz.pmis.workflow.domain.enums.FlowPerformType;
import com.njydsz.pmis.workflow.domain.enums.FlowSignType;
import com.njydsz.pmis.workflow.domain.enums.FlowTaskStatus;
import com.njydsz.pmis.workflow.infra.mapper.FlowRunTaskMapper;
import com.njydsz.pmis.workflow.infra.mapper.FlowUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

/**
 * 待办任务 — 加签减签类 Service 实现
 *
 * <p>从原 {@code FlowTaskServiceImpl} 拆分，专注审批人动态调整与轻量记录职责：
 * <ul>
 *   <li>前加签：{@link #countersignBefore}</li>
 *   <li>后加签：{@link #countersignAfter}（切换为顺序会签）</li>
 *   <li>减签：{@link #countersignRemove}</li>
 *   <li>追加处理人：{@link #addApprover}</li>
 *   <li>已阅：{@link #markRead}</li>
 *   <li>沟通：{@link #communicate}</li>
 *   <li>暂存待审：{@link #saveDraft}</li>
 * </ul>
 *
 * <p>跨子 Service 共享的任务校验/审计/事件能力委托给 {@link FlowTaskSupport}。
 *
 * @author ydsz-pmis-team
 * @since 1.2.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowTaskSignServiceImpl {

    /** 运行时任务 Mapper，查询/更新加签减签的任务 */
    private final FlowRunTaskMapper taskMapper;
    /** 用户 Mapper，查询加签/追加处理人的用户信息 */
    private final FlowUserMapper userMapper;
    /** 跨子 Service 共享的任务校验/审计/事件辅助 */
    private final FlowTaskSupport support;

    // ============================== 加签（P1-7） ==============================

    /**
     * P1-7: 前加签 — 在当前节点前插入临时审批人
     */
    @Transactional(rollbackFor = Exception.class)
    public void countersignBefore(FlowTaskOperateDTO dto) {
        FlowRunTaskDO task = support.getTaskOrThrow(dto.getTaskId());
        if (FlowTaskStatus.valueOf(task.getTaskStatus()).isFinished()) {
            throw new SysException(StandardResultCode.BAD_REQUEST, "error.workflow.msg_5ac7f16a");
        }
        // 前加签：在当前节点前插入临时审批人
        // 实现：为当前任务新增一个审批人记录到 pmis_flow_user，approveCount+1
        if (dto.getTargetUserId() != null) {
            FlowUserDO fu = new FlowUserDO();
            fu.setTaskId(task.getId());
            fu.setInstanceId(task.getInstanceId());
            fu.setNodeCode(task.getNodeCode());
            fu.setUserType(FlowAssigneeType.USER.name());
            fu.setUserId(String.valueOf(dto.getTargetUserId()));
            fu.setUserName(dto.getTargetUserName());
            fu.setProcessed(0);
            fu.setWeight(1);
            fu.setSignType(FlowSignType.BEFORE.name());
            fu.setTenantId(task.getTenantId());
            fu.setProviderTraceId(task.getProviderTraceId());
            userMapper.insert(fu);
            taskMapper.updateApproveFinished(task.getId(), task.getApproveFinished());
            // approveCount +1
            task.setApproveCount((task.getApproveCount() == null ? 0 : task.getApproveCount()) + 1);
            taskMapper.updateById(task);
        }
        support.audit(task, "COUNTERSIGN_BEFORE", dto.getUserId(), dto.getTargetUserId(), dto.getComment());
        log.info("[Flow] 前加签: taskId={} → 新增审批人={}", task.getId(), dto.getTargetUserId());
        // P2-34: 触发 onTaskCountersigned 事件
        support.fireEvent(l -> l.onTaskCountersigned(task.getId(), dto.getTargetUserId(), "BEFORE"),
                task.getId());
        // P2-35: 发布 Spring 异步事件
        support.publishWorkflowEvent("TASK_COUNTERSIGNED", task.getInstanceId(), task.getId());
    }

    /**
     * P1-7: 后加签 — 在当前节点通过后、下一节点前插入临时审批人
     */
    @Transactional(rollbackFor = Exception.class)
    public void countersignAfter(FlowTaskOperateDTO dto) {
        FlowRunTaskDO task = support.getTaskOrThrow(dto.getTaskId());
        if (FlowTaskStatus.valueOf(task.getTaskStatus()).isFinished()) {
            throw new SysException(StandardResultCode.BAD_REQUEST, "error.workflow.msg_5ac7f16a");
        }
        // P2-29: 后加签真实实现 — 当前审批人通过后，新加签人需要审批，两人都通过后才推进到下一节点
        // 实现方式：
        // 1. 将当前任务切换为顺序会签（performType=SEQUENTIAL）
        // 2. approveCount +1（当前人 + 加签人）
        // 3. 新增审批人写入 pmis_flow_user（processed=0）
        // 这样当前审批人 pass 时，doSequentialPass 检测到 approveFinished < approveCount，
        // 会切换到加签人而非推进到下一节点；加签人 pass 后才真正推进
        if (dto.getTargetUserId() != null) {
            FlowUserDO fu = new FlowUserDO();
            fu.setTaskId(task.getId());
            fu.setInstanceId(task.getInstanceId());
            fu.setNodeCode(task.getNodeCode());
            fu.setUserType(FlowAssigneeType.USER.name());
            fu.setUserId(String.valueOf(dto.getTargetUserId()));
            fu.setUserName(dto.getTargetUserName());
            fu.setProcessed(0);
            fu.setWeight(1);
            fu.setSignType(FlowSignType.AFTER.name());
            fu.setTenantId(task.getTenantId());
            fu.setProviderTraceId(task.getProviderTraceId());
            userMapper.insert(fu);
            // 切换为顺序会签：当前人 pass 后切换到加签人，加签人 pass 后才推进
            task.setPerformType(FlowPerformType.SEQUENTIAL.name());
            task.setApproveCount((task.getApproveCount() == null ? 0 : task.getApproveCount()) + 1);
            taskMapper.updateById(task);
        }
        support.audit(task, "COUNTERSIGN_AFTER", dto.getUserId(), dto.getTargetUserId(), dto.getComment());
        log.info("[Flow] 后加签: taskId={} → 新增审批人={} (切换为顺序会签)",
                task.getId(), dto.getTargetUserId());
        // P2-34: 触发 onTaskCountersigned 事件
        support.fireEvent(l -> l.onTaskCountersigned(task.getId(), dto.getTargetUserId(), "AFTER"),
                task.getId());
        // P2-35: 发布 Spring 异步事件
        support.publishWorkflowEvent("TASK_COUNTERSIGNED", task.getInstanceId(), task.getId());
    }

    /**
     * GAP-P0-3: 并加签 — 动态追加审批人与原审批人并行审批，所有人审完后才推进。
     *
     * <p>对标钉钉/飞书"并加签"。实现方式：
     * <ol>
     *   <li>向 pmis_flow_user 插入新审批人（signType=PARALLEL，processed=0）</li>
     *   <li>approveCount +1</li>
     *   <li>强制切换 performType 为 PARALLEL —— 确保所有人全部通过才推进</li>
     * </ol>
     * 与后加签（SEQUENTIAL 顺序）不同，并加签的加签人与原审批人<b>同时</b>收到待办，
     * 互不阻塞，全部审完后才推进到下一节点。
     */
    @Transactional(rollbackFor = Exception.class)
    public void countersignParallel(FlowTaskOperateDTO dto) {
        FlowRunTaskDO task = support.getTaskOrThrow(dto.getTaskId());
        if (FlowTaskStatus.valueOf(task.getTaskStatus()).isFinished()) {
            throw new SysException(StandardResultCode.BAD_REQUEST, "error.workflow.msg_5ac7f16a");
        }
        if (dto.getTargetUserId() == null) {
            throw new SysException(StandardResultCode.BAD_REQUEST, "error.workflow.msg_2deb2e4f");
        }
        FlowUserDO fu = new FlowUserDO();
        fu.setTaskId(task.getId());
        fu.setInstanceId(task.getInstanceId());
        fu.setNodeCode(task.getNodeCode());
        fu.setUserType(FlowAssigneeType.USER.name());
        fu.setUserId(String.valueOf(dto.getTargetUserId()));
        fu.setUserName(dto.getTargetUserName());
        fu.setProcessed(0);
        fu.setWeight(1);
        fu.setSignType(FlowSignType.PARALLEL.name());
        fu.setTenantId(task.getTenantId());
        fu.setProviderTraceId(task.getProviderTraceId());
        userMapper.insert(fu);
        // 强制切换为并行会签：加签人与原审批人并行审批，所有人全部通过才推进
        task.setPerformType(FlowPerformType.PARALLEL.name());
        task.setApproveCount((task.getApproveCount() == null ? 0 : task.getApproveCount()) + 1);
        taskMapper.updateById(task);
        support.audit(task, "COUNTERSIGN_PARALLEL", dto.getUserId(), dto.getTargetUserId(), dto.getComment());
        log.info("[Flow] 并加签: taskId={} → 新增审批人={} (切换为并行会签)",
                task.getId(), dto.getTargetUserId());
        support.fireEvent(l -> l.onTaskCountersigned(task.getId(), dto.getTargetUserId(), "PARALLEL"),
                task.getId());
        support.publishWorkflowEvent("TASK_COUNTERSIGNED", task.getInstanceId(), task.getId());
    }

    // ============================== GAP-P1: 减签 ==============================

    /**
     * GAP-P1: 减签 — 从会签任务中移除指定审批人
     *
     * <p>对标钉钉/飞书的"减签"功能。从 pmis_flow_user 中删除指定用户，
     * 并更新任务的 approveCount（应到人数）。
     *
     * @param dto 任务操作参数（需含 taskId + userId 为被减签人）
     */
    @Transactional(rollbackFor = Exception.class)
    public void countersignRemove(FlowTaskOperateDTO dto) {
        FlowRunTaskDO task = support.getTaskOrThrow(dto.getTaskId());
        if (FlowTaskStatus.valueOf(task.getTaskStatus()).isFinished()) {
            throw new SysException(StandardResultCode.BAD_REQUEST, "error.workflow.msg_ff1454e4");
        }
        if (dto.getTargetUserId() == null) {
            throw new SysException(StandardResultCode.BAD_REQUEST, "error.workflow.msg_7c4a1bdf");
        }
        // 从 pmis_flow_user 中删除指定用户
        Map<String, Object> deleteMap = new HashMap<>();
        deleteMap.put("instance_id", task.getInstanceId());
        deleteMap.put("node_code", task.getNodeCode());
        deleteMap.put("user_id", String.valueOf(dto.getTargetUserId()));
        int deleted = userMapper.deleteByMap(deleteMap);
        if (deleted == 0) {
            throw new SysException(StandardResultCode.NOT_FOUND,
                    "error.workflow.msg_a39adc9d", dto.getTargetUserId());
        }
        // approveCount -1，但不低于 1
        int currentCount = task.getApproveCount() == null ? 1 : task.getApproveCount();
        task.setApproveCount(Math.max(1, currentCount - 1));
        taskMapper.updateById(task);
        support.audit(task, "COUNTERSIGN_REMOVE", dto.getUserId(), dto.getTargetUserId(), dto.getComment());
        log.info("[Flow] 减签: taskId={} → 移除审批人={} deleted={}",
                task.getId(), dto.getTargetUserId(), deleted);
        support.fireEvent(l -> l.onTaskCountersigned(task.getId(), dto.getTargetUserId(), "REMOVE"),
                task.getId());
        support.publishWorkflowEvent("TASK_COUNTERSIGNED", task.getInstanceId(), task.getId());
    }

    // ============================== GAP-P2: 已阅/沟通 ==============================

    /**
     * GAP-P2: 已阅 — 标记任务已阅（不改变任务状态，仅记录审计日志）
     *
     * @param taskId 任务 ID
     * @param userId 操作人 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void markRead(String taskId, String userId) {
        FlowRunTaskDO task = support.getTaskOrThrow(taskId);
        support.audit(task, "READ", userId, null, null);
        log.info("[Flow] 已阅: taskId={} userId={}", taskId, userId);
    }

    /**
     * GAP-P2: 沟通 — 在任务下添加沟通评论（不改变任务状态）
     *
     * @param dto 任务操作参数（需含 taskId + userId + comment）
     */
    @Transactional(rollbackFor = Exception.class)
    public void communicate(FlowTaskOperateDTO dto) {
        FlowRunTaskDO task = support.getTaskOrThrow(dto.getTaskId());
        support.audit(task, "COMMUNICATE", dto.getUserId(), null, dto.getComment(), dto.getCommentType());
        log.info("[Flow] 沟通: taskId={} userId={} comment={}",
                dto.getTaskId(), dto.getUserId(), dto.getComment());
    }

    // ======================== P0-03: 暂存待审 / 追加处理人 ========================

    /**
     * GAP-P0: 暂存待审 — 审批人保存审批意见草稿（不改变任务主状态）
     *
     * <p>将审批意见保存到任务 comment 字段，任务状态保持 PENDING/CLAIMED 不变，
     * 写审计日志记录 SAVE_DRAFT 操作。对标飞书/钉钉审批的"暂存"功能。
     *
     * @param dto 任务操作参数（需含 taskId + userId + comment）
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveDraft(FlowTaskOperateDTO dto) {
        FlowRunTaskDO task = support.getTaskOrThrow(dto.getTaskId());
        if (FlowTaskStatus.valueOf(task.getTaskStatus()).isFinished()) {
            throw new SysException(StandardResultCode.BAD_REQUEST, "error.workflow.msg_8913103b");
        }
        // 保存审批意见草稿到 comment 字段，不改变任务状态
        task.setComment(dto.getComment());
        taskMapper.updateById(task);
        support.audit(task, "SAVE_DRAFT", dto.getUserId(), null, dto.getComment(), dto.getCommentType());
        log.info("[Flow] 暂存待审: taskId={} userId={}", dto.getTaskId(), dto.getUserId());
    }

    /**
     * GAP-P0: 追加处理人 — 在已有会签任务中追加一个审批人
     *
     * <p>对标 FlowLong 的"追加处理人"功能。向 pmis_flow_user 插入新审批人，
     * approveCount +1，保持当前会签模式不变。比加签更轻量，不改变 performType。
     *
     * @param dto 任务操作参数（需含 taskId + targetUserId + targetUserName）
     */
    @Transactional(rollbackFor = Exception.class)
    public void addApprover(FlowTaskOperateDTO dto) {
        FlowRunTaskDO task = support.getTaskOrThrow(dto.getTaskId());
        if (FlowTaskStatus.valueOf(task.getTaskStatus()).isFinished()) {
            throw new SysException(StandardResultCode.BAD_REQUEST, "error.workflow.msg_511d4aaa");
        }
        if (dto.getTargetUserId() == null) {
            throw new SysException(StandardResultCode.BAD_REQUEST, "error.workflow.msg_2deb2e4f");
        }
        // 向 pmis_flow_user 插入新审批人
        FlowUserDO fu = new FlowUserDO();
        fu.setTaskId(task.getId());
        fu.setInstanceId(task.getInstanceId());
        fu.setNodeCode(task.getNodeCode());
        fu.setUserType(FlowAssigneeType.USER.name());
        fu.setUserId(String.valueOf(dto.getTargetUserId()));
        fu.setUserName(dto.getTargetUserName());
        fu.setProcessed(0);
        fu.setWeight(1); // 默认权重 1
        fu.setSignType(FlowSignType.ADD.name());
        fu.setTenantId(task.getTenantId());
        fu.setProviderTraceId(task.getProviderTraceId());
        userMapper.insert(fu);
        // approveCount +1
        int currentCount = task.getApproveCount() == null ? 1 : task.getApproveCount();
        task.setApproveCount(currentCount + 1);
        taskMapper.updateById(task);
        support.audit(task, "ADD_APPROVER", dto.getUserId(), dto.getTargetUserId(), dto.getComment());
        log.info("[Flow] 追加处理人: taskId={} targetUserId={}", task.getId(), dto.getTargetUserId());
        support.fireEvent(l -> l.onTaskCountersigned(task.getId(), dto.getTargetUserId(), "ADD"),
                task.getId());
        support.publishWorkflowEvent("TASK_ADD_APPROVER", task.getInstanceId(), task.getId());
    }
}
