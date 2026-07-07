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
    public void batchPass(List<Long> taskIds, String userId, String comment) {
        if (taskIds == null || taskIds.isEmpty()) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.workflow.msg_a02f7864");
        }
        for (Long taskId : taskIds) {
            FlowTaskOperateDTO dto = new FlowTaskOperateDTO();
            dto.setTaskId(taskId);
            dto.setUserId(userId);
            dto.setComment(comment);
            dto.setAction("PASS");
            completeService.pass(dto);
        }
        log.info("[Flow] 批量审批: taskIds={} userId={} count={}", taskIds, userId, taskIds.size());
    }
}
