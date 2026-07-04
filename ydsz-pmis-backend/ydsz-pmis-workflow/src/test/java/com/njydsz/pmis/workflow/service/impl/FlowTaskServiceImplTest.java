package com.njydsz.pmis.workflow.service.impl;

import com.njydsz.pmis.common.api.PageResult;
import com.njydsz.pmis.workflow.dto.FlowTaskOperateDTO;
import com.njydsz.pmis.workflow.entity.FlowNodeDO;
import com.njydsz.pmis.workflow.entity.FlowTaskDO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * FlowTaskServiceImpl 单元测试
 *
 * <p>验证门面 Facade 正确委托到各子 Service。
 *
 * @author ydsz-pmis-team
 * @since 1.5.0
 */
@ExtendWith(MockitoExtension.class)
class FlowTaskServiceImplTest {

    @Mock private FlowTaskQueryServiceImpl queryService;
    @Mock private FlowTaskCompleteServiceImpl completeService;
    @Mock private FlowTaskSignServiceImpl signService;
    @Mock private FlowTaskBatchServiceImpl batchService;

    @InjectMocks
    private FlowTaskServiceImpl taskService;

    // ============ 创建任务 ============

    @Test
    @DisplayName("创建任务 - 委托到 completeService")
    void createTaskShouldDelegateToCompleteService() {
        FlowNodeDO node = new FlowNodeDO();
        Map<String, Object> variables = new HashMap<>();
        when(completeService.createTask(eq(100L), eq(node), eq(variables))).thenReturn(1L);

        Long result = taskService.createTask(100L, node, variables);

        assertThat(result).isEqualTo(1L);
        verify(completeService).createTask(100L, node, variables);
    }

    // ============ 查询 ============

    @Test
    @DisplayName("根据ID查询任务 - 委托到 queryService")
    void getByIdShouldDelegateToQueryService() {
        FlowTaskDO task = new FlowTaskDO();
        when(queryService.getById(1L)).thenReturn(task);

        FlowTaskDO result = taskService.getById(1L);

        assertThat(result).isEqualTo(task);
        verify(queryService).getById(1L);
    }

    // ============ 签收 ============

    @Test
    @DisplayName("签收任务 - 委托到 completeService")
    void claimShouldDelegateToCompleteService() {
        taskService.claim(1L, 100L);

        verify(completeService).claim(1L, 100L);
    }

    // ============ 通过 / 驳回 ============

    @Test
    @DisplayName("通过任务 - 委托到 completeService")
    void passShouldDelegateToCompleteService() {
        FlowTaskOperateDTO dto = new FlowTaskOperateDTO();
        dto.setTaskId(1L);

        taskService.pass(dto);

        verify(completeService).pass(dto);
    }

    @Test
    @DisplayName("驳回任务 - 委托到 completeService")
    void rejectShouldDelegateToCompleteService() {
        FlowTaskOperateDTO dto = new FlowTaskOperateDTO();
        dto.setTaskId(1L);

        taskService.reject(dto);

        verify(completeService).reject(dto);
    }

    // ============ 转办 / 委派 ============

    @Test
    @DisplayName("转办任务 - 委托到 completeService")
    void transferShouldDelegateToCompleteService() {
        FlowTaskOperateDTO dto = new FlowTaskOperateDTO();
        dto.setTaskId(1L);

        taskService.transfer(dto);

        verify(completeService).transfer(dto);
    }

    @Test
    @DisplayName("委派任务 - 委托到 completeService")
    void delegateShouldDelegateToCompleteService() {
        FlowTaskOperateDTO dto = new FlowTaskOperateDTO();
        dto.setTaskId(1L);

        taskService.delegate(dto);

        verify(completeService).delegate(dto);
    }

    // ============ 取消 / 催办 ============

    @Test
    @DisplayName("取消实例任务 - 委托到 completeService")
    void cancelByInstanceShouldDelegateToCompleteService() {
        taskService.cancelByInstance(100L, "CANCELLED");

        verify(completeService).cancelByInstance(100L, "CANCELLED");
    }

    @Test
    @DisplayName("催办 - 委托到 completeService")
    void urgeShouldDelegateToCompleteService() {
        when(completeService.urge(eq(100L), eq(10L), anyString()))
                .thenReturn(List.of("user1"));

        List<String> result = taskService.urge(100L, 10L, "请尽快处理");

        assertThat(result).contains("user1");
        verify(completeService).urge(100L, 10L, "请尽快处理");
    }
}