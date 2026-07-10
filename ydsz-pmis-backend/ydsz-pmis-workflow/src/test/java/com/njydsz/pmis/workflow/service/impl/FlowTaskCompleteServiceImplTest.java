package com.njydsz.pmis.workflow.service.impl;

import com.njydsz.pmis.workflow.dto.instance.FlowTaskOperateDTO;
import com.njydsz.pmis.workflow.entity.definition.FlowNodeDO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * FlowTaskCompleteServiceImpl 门面单元测试
 *
 * <p>验证门面类（Facade）将所有方法委托给对应专门服务，且 @Transactional 注解已生效。
 *
 * @author ydsz-pmis-team
 * @since 1.7.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("FlowTaskCompleteServiceImpl 门面测试")
class FlowTaskCompleteServiceImplTest {

    @Mock
    private FlowTaskCreateService createService;
    @Mock
    private FlowTaskClaimService claimService;
    @Mock
    private FlowTaskPassService passService;
    @Mock
    private FlowTaskRejectService rejectService;
    @Mock
    private FlowTaskOperateService operateService;
    @Mock
    private FlowTaskUrgeService urgeService;
    @Mock
    private FlowTaskTimeoutService timeoutService;

    @InjectMocks
    private FlowTaskCompleteServiceImpl facade;

    @Test
    @DisplayName("createTask(3 参) → 委托 createService")
    void createTask_3args() {
        FlowNodeDO node = new FlowNodeDO();
        Map<String, Object> vars = Map.of("k", "v");
        when(createService.createTask("I1", node, vars)).thenReturn("T1");

        String result = facade.createTask("I1", node, vars);

        assertEquals("T1", result);
        verify(createService).createTask("I1", node, vars);
    }

    @Test
    @DisplayName("createTask(4 参) → 委托 createService")
    void createTask_4args() {
        FlowNodeDO node = new FlowNodeDO();
        Map<String, Object> vars = Map.of("k", "v");
        List<String> assignees = List.of("U1", "U2");
        when(createService.createTask(anyString(), any(), any(), anyList())).thenReturn("T2");

        String result = facade.createTask("I1", node, vars, assignees);

        assertEquals("T2", result);
        verify(createService).createTask("I1", node, vars, assignees);
    }

    @Test
    @DisplayName("claim → 委托 claimService")
    void claim() {
        facade.claim("T1", "U1");
        verify(claimService).claim("T1", "U1");
    }

    @Test
    @DisplayName("pass → 委托 passService")
    void pass() {
        FlowTaskOperateDTO dto = new FlowTaskOperateDTO();
        dto.setTaskId("T1");
        facade.pass(dto);
        verify(passService).pass(dto);
    }

    @Test
    @DisplayName("reject → 委托 rejectService")
    void reject() {
        FlowTaskOperateDTO dto = new FlowTaskOperateDTO();
        dto.setTaskId("T1");
        facade.reject(dto);
        verify(rejectService).reject(dto);
    }

    @Test
    @DisplayName("transfer → 委托 operateService")
    void transfer() {
        FlowTaskOperateDTO dto = new FlowTaskOperateDTO();
        dto.setTaskId("T1");
        facade.transfer(dto);
        verify(operateService).transfer(dto);
    }

    @Test
    @DisplayName("delegate → 委托 operateService")
    void delegate() {
        FlowTaskOperateDTO dto = new FlowTaskOperateDTO();
        dto.setTaskId("T1");
        facade.delegate(dto);
        verify(operateService).delegate(dto);
    }

    @Test
    @DisplayName("jump → 委托 operateService")
    void jump() {
        FlowTaskOperateDTO dto = new FlowTaskOperateDTO();
        dto.setTaskId("T1");
        facade.jump(dto);
        verify(operateService).jump(dto);
    }

    @Test
    @DisplayName("retract → 委托 operateService 并返回结果")
    void retract() {
        when(operateService.retract("H1", "U1", "comment")).thenReturn("T2");
        String result = facade.retract("H1", "U1", "comment");
        assertEquals("T2", result);
        verify(operateService).retract("H1", "U1", "comment");
    }

    @Test
    @DisplayName("urge → 委托 urgeService 并返回被催办人列表")
    void urge() {
        List<String> urged = List.of("U1", "U2");
        when(urgeService.urge("I1", "U1", "comment")).thenReturn(urged);

        List<String> result = facade.urge("I1", "U1", "comment");

        assertEquals(urged, result);
        verify(urgeService).urge("I1", "U1", "comment");
    }

    @Test
    @DisplayName("urgeByNode → 委托 urgeService")
    void urgeByNode() {
        List<String> urged = List.of("U1");
        when(urgeService.urgeByNode("I1", "N1", "U1", "comment")).thenReturn(urged);

        List<String> result = facade.urgeByNode("I1", "N1", "U1", "comment");

        assertEquals(urged, result);
        verify(urgeService).urgeByNode("I1", "N1", "U1", "comment");
    }

    @Test
    @DisplayName("timeoutTask → 委托 timeoutService")
    void timeoutTask() {
        facade.timeoutTask("T1", "reason");
        verify(timeoutService).timeoutTask("T1", "reason");
    }

    @Test
    @DisplayName("suspendTask → 委托 timeoutService")
    void suspendTask() {
        facade.suspendTask("T1", "U1", "reason");
        verify(timeoutService).suspendTask("T1", "U1", "reason");
    }

    @Test
    @DisplayName("activateTask → 委托 timeoutService")
    void activateTask() {
        facade.activateTask("T1", "U1");
        verify(timeoutService).activateTask("T1", "U1");
    }

    @Test
    @DisplayName("cancelByInstance → 委托 timeoutService")
    void cancelByInstance() {
        facade.cancelByInstance("I1", "CANCELLED");
        verify(timeoutService).cancelByInstance("I1", "CANCELLED");
    }
}
