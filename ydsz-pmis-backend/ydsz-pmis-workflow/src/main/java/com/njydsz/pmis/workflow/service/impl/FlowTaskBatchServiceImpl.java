package com.njydsz.pmis.workflow.service.impl;

import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.workflow.dto.FlowTaskOperateDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 待办任务 — 批量操作 Service 实现
 *
 * <p>从原 {@code FlowTaskServiceImpl} 拆分，专注批量审批职责：
 * <ul>
 *   <li>{@link #batchPass} — 批量审批，逐一委托 {@link FlowTaskCompleteServiceImpl#pass}
 *       执行，{@code @Transactional} 保证原子性</li>
 * </ul>
 *
 * <p>批量操作通过注入完成类子 Service 调用单条 {@code pass}，相比原 {@code FlowTaskServiceImpl}
 * 内部自调用（{@code this.pass}），跨 Bean 调用可正确触发 Spring 事务代理，事务传播
 * （默认 REQUIRED）将每条 {@code pass} 加入批量事务，保证整批原子提交/回滚。
 *
 * @author ydsz-pmis-team
 * @since 1.2.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowTaskBatchServiceImpl {

    /** 单条任务通过由完成类子 Service 承载 */
    private final FlowTaskCompleteServiceImpl completeService;

    /**
     * P2-26: 批量审批 — 对多个任务逐一执行 pass，@Transactional 保证原子性
     *
     * @param taskIds 任务 ID 列表
     * @param userId  操作人 ID
     * @param comment 审批意见
     */
    @Transactional(rollbackFor = Exception.class)
    public void batchPass(List<String> taskIds, String userId, String comment) {
        if (taskIds == null || taskIds.isEmpty()) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.workflow.msg_a02f7864");
        }
        for (String taskId : taskIds) {
            FlowTaskOperateDTO dto = new FlowTaskOperateDTO();
            dto.setTaskId(taskId);
            dto.setUserId(userId);
            dto.setComment(comment);
            dto.setAction("PASS");
            completeService.pass(dto);
        }
        log.info("[Flow] 批量审批: taskIds={} userId={} count={}", taskIds, userId, taskIds.size());
    }

    /**
     * P1-4: 批量驳回 — 对多个任务逐一执行 reject，@Transactional 保证原子性。
     *
     * <p>批量驳回时所有任务使用相同的退回目标节点（targetNodeCode）和审批意见，
     * 任一任务驳回失败则整批回滚。
     *
     * @param taskIds        任务 ID 列表
     * @param userId         操作人 ID
     * @param comment        审批意见
     * @param targetNodeCode 退回目标节点编码（可选，为空时走默认退回逻辑）
     */
    @Transactional(rollbackFor = Exception.class)
    public void batchReject(List<String> taskIds, String userId, String comment,
                            String targetNodeCode) {
        if (taskIds == null || taskIds.isEmpty()) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.workflow.msg_a02f7864");
        }
        for (String taskId : taskIds) {
            FlowTaskOperateDTO dto = new FlowTaskOperateDTO();
            dto.setTaskId(taskId);
            dto.setUserId(userId);
            dto.setComment(comment);
            dto.setAction("REJECT");
            dto.setTargetNodeCode(targetNodeCode);
            completeService.reject(dto);
        }
        log.info("[Flow] 批量驳回: taskIds={} userId={} count={} targetNodeCode={}",
                taskIds, userId, taskIds.size(), targetNodeCode);
    }

    /**
     * P1-4: 批量转办 — 对多个任务逐一执行 transfer，@Transactional 保证原子性。
     *
     * <p>批量转办时所有任务转给同一目标人，任一任务转办失败则整批回滚。
     *
     * @param taskIds       任务 ID 列表
     * @param userId        操作人 ID
     * @param comment       转办说明
     * @param targetUserId  目标人 ID
     * @param targetUserName 目标人姓名
     */
    @Transactional(rollbackFor = Exception.class)
    public void batchTransfer(List<String> taskIds, String userId, String comment,
                              String targetUserId, String targetUserName) {
        if (taskIds == null || taskIds.isEmpty()) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.workflow.msg_a02f7864");
        }
        for (String taskId : taskIds) {
            FlowTaskOperateDTO dto = new FlowTaskOperateDTO();
            dto.setTaskId(taskId);
            dto.setUserId(userId);
            dto.setComment(comment);
            dto.setAction("TRANSFER");
            dto.setTargetUserId(targetUserId);
            dto.setTargetUserName(targetUserName);
            completeService.transfer(dto);
        }
        log.info("[Flow] 批量转办: taskIds={} userId={} count={} targetUserId={}",
                taskIds, userId, taskIds.size(), targetUserId);
    }

    /**
     * P1-4: 批量催办 — 对多个实例逐一执行 urge。
     *
     * <p>批量催办不使用 @Transactional（催办无数据库写操作，仅发送通知），
     * 单个实例催办失败不影响其他实例，失败记录日志后继续。
     *
     * @param instanceIds 实例 ID 列表
     * @param operatorId  操作人 ID
     * @param comment     催办说明
     * @return 成功催办的实例数量
     */
    public int batchUrge(List<String> instanceIds, String operatorId, String comment) {
        if (instanceIds == null || instanceIds.isEmpty()) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.workflow.msg_a02f7864");
        }
        int success = 0;
        for (String instanceId : instanceIds) {
            try {
                completeService.urge(instanceId, operatorId, comment);
                success++;
            } catch (Exception e) {
                log.warn("[Flow] 批量催办单条失败（继续处理其他）: instanceId={} err={}",
                        instanceId, e.getMessage());
            }
        }
        log.info("[Flow] 批量催办: instanceIds={} operatorId={} success={}/{}",
                instanceIds, operatorId, success, instanceIds.size());
        return success;
    }
}
