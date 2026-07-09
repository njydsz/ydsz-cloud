package com.njydsz.pmis.workflow.service.impl.strategy;

import com.njydsz.pmis.workflow.dto.FlowTaskOperateDTO;
import com.njydsz.pmis.workflow.entity.FlowRunTaskDO;
import com.njydsz.pmis.workflow.enums.FlowPerformType;
import com.njydsz.pmis.workflow.service.impl.FlowTaskArchiveService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * OR 或签策略单元测试
 *
 * <p>验证 OR 策略的核心行为：一人通过即推进，且完成+归档被调用。
 *
 * @author ydsz-pmis-team
 * @since 1.7.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OR 或签策略测试")
class OrCountersignStrategyTest {

    @Mock
    private FlowTaskArchiveService archiveService;

    @InjectMocks
    private OrCountersignStrategy strategy;

    @Test
    @DisplayName("supportedType 返回 OR")
    void supportedType() {
        assertEquals(FlowPerformType.OR, strategy.supportedType());
    }

    @Test
    @DisplayName("onUserPassed 调用 completeAndArchive")
    void onUserPassed_archive() {
        FlowRunTaskDO task = new FlowRunTaskDO();
        task.setId("T1");
        FlowTaskOperateDTO dto = new FlowTaskOperateDTO();
        dto.setComment("agree");

        strategy.onUserPassed(task, dto);

        verify(archiveService).completeAndArchive(task, "agree");
    }

    @Test
    @DisplayName("shouldAdvance 始终返回 true")
    void shouldAdvance_alwaysTrue() {
        FlowRunTaskDO task = new FlowRunTaskDO();
        assertTrue(strategy.shouldAdvance(task));
        task.setApproveFinished(0);
        task.setApproveCount(5);
        assertTrue(strategy.shouldAdvance(task));
    }

    @Test
    @DisplayName("onUserPassed 传 null comment 时归档 comment 为 null")
    void onUserPassed_nullComment() {
        FlowRunTaskDO task = new FlowRunTaskDO();
        task.setId("T2");
        FlowTaskOperateDTO dto = new FlowTaskOperateDTO();
        dto.setComment(null);

        strategy.onUserPassed(task, dto);

        verify(archiveService).completeAndArchive(task, null);
    }

    @Test
    @DisplayName("when 不需要 stub 验证多次调用")
    void multiCallIndependence() {
        FlowRunTaskDO task1 = new FlowRunTaskDO();
        task1.setId("T1");
        FlowRunTaskDO task2 = new FlowRunTaskDO();
        task2.setId("T2");
        FlowTaskOperateDTO dto = new FlowTaskOperateDTO();
        dto.setComment("ok");

        strategy.onUserPassed(task1, dto);
        strategy.onUserPassed(task2, dto);

        verify(archiveService).completeAndArchive(task1, "ok");
        verify(archiveService).completeAndArchive(task2, "ok");
    }
}
