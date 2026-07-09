package com.njydsz.pmis.workflow.service.impl.strategy;

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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * FOREACH 循环策略单元测试
 *
 * <p>验证 FOREACH_PARALLEL 策略：每条 task 独立完成、pending 数为 0 时推进。
 *
 * @author ydsz-pmis-team
 * @since 1.7.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("FOREACH_PARALLEL 循环策略测试")
class ForeachCountersignStrategyTest {

    @Mock
    private FlowRunTaskMapper taskMapper;
    @Mock
    private FlowTaskArchiveService archiveService;

    @InjectMocks
    private ForeachCountersignStrategy strategy;

    @Test
    @DisplayName("supportedType 返回 FOREACH_PARALLEL")
    void supportedType() {
        assertEquals(FlowPerformType.FOREACH_PARALLEL, strategy.supportedType());
    }

    @Test
    @DisplayName("onUserPassed 独立归档当前 task")
    void onUserPassed_independentArchive() {
        FlowRunTaskDO task = new FlowRunTaskDO();
        task.setId("T1");
        FlowTaskOperateDTO dto = new FlowTaskOperateDTO();
        dto.setComment("agree");

        strategy.onUserPassed(task, dto);

        verify(archiveService).completeAndArchive(task, "agree");
    }

    @Test
    @DisplayName("shouldAdvance pending=0 时返回 true（全部完成）")
    void shouldAdvance_allCompleted() {
        FlowRunTaskDO task = new FlowRunTaskDO();
        task.setInstanceId("I1");
        task.setNodeCode("N1");
        when(taskMapper.countPendingByNode("I1", "N1")).thenReturn(0);

        assertTrue(strategy.shouldAdvance(task));
    }

    @Test
    @DisplayName("shouldAdvance pending>0 时返回 false（还有人未完成）")
    void shouldAdvance_pendingExists() {
        FlowRunTaskDO task = new FlowRunTaskDO();
        task.setInstanceId("I1");
        task.setNodeCode("N1");
        when(taskMapper.countPendingByNode("I1", "N1")).thenReturn(2);

        assertFalse(strategy.shouldAdvance(task));
    }

    @Test
    @DisplayName("onUserPassed 传 null comment 时归档 comment 为 null")
    void onUserPassed_nullComment() {
        FlowRunTaskDO task = new FlowRunTaskDO();
        task.setId("T1");

        strategy.onUserPassed(task, new FlowTaskOperateDTO());

        verify(archiveService).completeAndArchive(task, null);
    }
}
