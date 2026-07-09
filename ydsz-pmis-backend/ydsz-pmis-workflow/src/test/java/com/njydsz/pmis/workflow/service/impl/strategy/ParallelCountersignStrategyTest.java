package com.njydsz.pmis.workflow.service.impl.strategy;

import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.workflow.dto.FlowTaskOperateDTO;
import com.njydsz.pmis.workflow.entity.FlowRunTaskDO;
import com.njydsz.pmis.workflow.enums.FlowPerformType;
import com.njydsz.pmis.workflow.mapper.FlowRunTaskMapper;
import com.njydsz.pmis.workflow.service.impl.FlowTaskArchiveService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 并行会签策略单元测试
 *
 * <p>验证 PARALLEL 策略：累加 approveFinished、shouldAdvance 阈值判断、乐观锁冲突异常。
 *
 * @author ydsz-pmis-team
 * @since 1.7.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PARALLEL 并行会签策略测试")
class ParallelCountersignStrategyTest {

    @Mock
    private FlowRunTaskMapper taskMapper;
    @Mock
    private FlowTaskArchiveService archiveService;

    @InjectMocks
    private ParallelCountersignStrategy strategy;

    @Test
    @DisplayName("supportedType 返回 PARALLEL")
    void supportedType() {
        assertEquals(FlowPerformType.PARALLEL, strategy.supportedType());
    }

    @Test
    @DisplayName("onUserPassed 累加 approveFinished 并归档")
    void onUserPassed_accumulateAndArchive() {
        FlowRunTaskDO task = new FlowRunTaskDO();
        task.setId("T1");
        task.setApproveFinished(1);
        task.setApproveCount(3);
        when(taskMapper.updateById(any(FlowRunTaskDO.class))).thenReturn(1);
        FlowTaskOperateDTO dto = new FlowTaskOperateDTO();
        dto.setComment("agree");

        strategy.onUserPassed(task, dto);

        assertEquals(2, task.getApproveFinished());
        verify(taskMapper).updateById(task);
        verify(archiveService).completeAndArchive(task, "agree");
    }

    @Test
    @DisplayName("onUserPassed 起始 approveFinished 为 null 时按 0 处理")
    void onUserPassed_nullFinished() {
        FlowRunTaskDO task = new FlowRunTaskDO();
        task.setId("T1");
        task.setApproveFinished(null);
        task.setApproveCount(2);
        when(taskMapper.updateById(any(FlowRunTaskDO.class))).thenReturn(1);

        strategy.onUserPassed(task, new FlowTaskOperateDTO());

        assertEquals(1, task.getApproveFinished());
    }

    @Test
    @DisplayName("乐观锁冲突（updateById 返回 0）抛 RESOURCE_CONFLICT")
    void onUserPassed_optimisticLockConflict() {
        FlowRunTaskDO task = new FlowRunTaskDO();
        task.setId("T1");
        task.setApproveCount(3);
        when(taskMapper.updateById(any(FlowRunTaskDO.class))).thenReturn(0);

        BizException ex = assertThrows(BizException.class,
                () -> strategy.onUserPassed(task, new FlowTaskOperateDTO()));
        assertEquals(BizErrorCode.RESOURCE_CONFLICT.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("shouldAdvance: finished >= count 时返回 true")
    void shouldAdvance_reachedThreshold() {
        FlowRunTaskDO task = new FlowRunTaskDO();
        task.setApproveFinished(3);
        task.setApproveCount(3);
        assertTrue(strategy.shouldAdvance(task));
    }

    @Test
    @DisplayName("shouldAdvance: finished < count 时返回 false")
    void shouldAdvance_notReached() {
        FlowRunTaskDO task = new FlowRunTaskDO();
        task.setApproveFinished(1);
        task.setApproveCount(3);
        assertFalse(strategy.shouldAdvance(task));
    }

    @Test
    @DisplayName("shouldAdvance: count 为 null 时按 1 视为完成")
    void shouldAdvance_nullCount() {
        FlowRunTaskDO task = new FlowRunTaskDO();
        task.setApproveFinished(1);
        task.setApproveCount(null);
        assertTrue(strategy.shouldAdvance(task));
    }

    @Test
    @DisplayName("shouldAdvance: finished/count 都为 null 时按 0/1 → false")
    void shouldAdvance_bothNull() {
        FlowRunTaskDO task = new FlowRunTaskDO();
        assertFalse(strategy.shouldAdvance(task));
    }
}
