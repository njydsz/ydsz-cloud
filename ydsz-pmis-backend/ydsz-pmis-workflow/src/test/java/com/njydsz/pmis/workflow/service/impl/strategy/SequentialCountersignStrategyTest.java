package com.njydsz.pmis.workflow.service.impl.strategy;

import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.workflow.dto.FlowTaskOperateDTO;
import com.njydsz.pmis.workflow.entity.FlowRunTaskDO;
import com.njydsz.pmis.workflow.entity.FlowUserDO;
import com.njydsz.pmis.workflow.enums.FlowPerformType;
import com.njydsz.pmis.workflow.mapper.FlowRunTaskMapper;
import com.njydsz.pmis.workflow.mapper.FlowUserMapper;
import com.njydsz.pmis.workflow.service.impl.FlowTaskArchiveService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 顺序会签策略单元测试
 *
 * <p>验证 SEQUENTIAL 策略：累加计数、未完成时切换下一个人、完成时归档。
 *
 * @author ydsz-pmis-team
 * @since 1.7.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SEQUENTIAL 顺序会签策略测试")
class SequentialCountersignStrategyTest {

    @Mock
    private FlowRunTaskMapper taskMapper;
    @Mock
    private FlowUserMapper userMapper;
    @Mock
    private FlowTaskArchiveService archiveService;

    @InjectMocks
    private SequentialCountersignStrategy strategy;

    @Test
    @DisplayName("supportedType 返回 SEQUENTIAL")
    void supportedType() {
        assertEquals(FlowPerformType.SEQUENTIAL, strategy.supportedType());
    }

    @Test
    @DisplayName("onUserPassed 未完成时切换下一个人且不归档")
    void onUserPassed_switchNextUser() {
        FlowRunTaskDO task = new FlowRunTaskDO();
        task.setId("T1");
        task.setInstanceId("I1");
        task.setNodeCode("N1");
        task.setApproveFinished(1);
        task.setApproveCount(3);
        when(taskMapper.updateById(any(FlowRunTaskDO.class))).thenReturn(1);

        FlowUserDO next = new FlowUserDO();
        next.setUserId("U2");
        next.setUserName("user2");
        when(userMapper.selectUnprocessedByInstanceAndNode("I1", "N1"))
                .thenReturn(List.of(next));

        strategy.onUserPassed(task, new FlowTaskOperateDTO());

        assertEquals(2, task.getApproveFinished());
        verify(taskMapper).updateAssignee("T1", "U2", "user2", "USER");
        verify(archiveService, never()).completeAndArchive(any(), anyString());
    }

    @Test
    @DisplayName("onUserPassed 全部完成时调用归档")
    void onUserPassed_allCompleted_archive() {
        FlowRunTaskDO task = new FlowRunTaskDO();
        task.setId("T1");
        task.setInstanceId("I1");
        task.setNodeCode("N1");
        task.setApproveFinished(2);
        task.setApproveCount(3);
        when(taskMapper.updateById(any(FlowRunTaskDO.class))).thenReturn(1);

        FlowTaskOperateDTO dto = new FlowTaskOperateDTO();
        dto.setComment("done");
        strategy.onUserPassed(task, dto);

        assertEquals(3, task.getApproveFinished());
        verify(archiveService).completeAndArchive(task, "done");
        verify(taskMapper, never()).updateAssignee(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("onUserPassed 无下一个人时仅累加不切换")
    void onUserPassed_noNextUser() {
        FlowRunTaskDO task = new FlowRunTaskDO();
        task.setId("T1");
        task.setInstanceId("I1");
        task.setNodeCode("N1");
        task.setApproveFinished(1);
        task.setApproveCount(3);
        when(taskMapper.updateById(any(FlowRunTaskDO.class))).thenReturn(1);
        when(userMapper.selectUnprocessedByInstanceAndNode(anyString(), anyString()))
                .thenReturn(List.of());

        strategy.onUserPassed(task, new FlowTaskOperateDTO());

        assertEquals(2, task.getApproveFinished());
        verify(taskMapper, never()).updateAssignee(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("乐观锁冲突抛 RESOURCE_CONFLICT")
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
    @DisplayName("shouldAdvance: finished >= count")
    void shouldAdvance_reached() {
        FlowRunTaskDO task = new FlowRunTaskDO();
        task.setApproveFinished(3);
        task.setApproveCount(3);
        assertTrue(strategy.shouldAdvance(task));
    }

    @Test
    @DisplayName("shouldAdvance: finished < count")
    void shouldAdvance_notReached() {
        FlowRunTaskDO task = new FlowRunTaskDO();
        task.setApproveFinished(2);
        task.setApproveCount(3);
        assertFalse(strategy.shouldAdvance(task));
    }

    @Test
    @DisplayName("shouldAdvance: count 为 null 时按 1 处理")
    void shouldAdvance_nullCount() {
        FlowRunTaskDO task = new FlowRunTaskDO();
        task.setApproveFinished(1);
        task.setApproveCount(null);
        assertTrue(strategy.shouldAdvance(task));
    }
}
