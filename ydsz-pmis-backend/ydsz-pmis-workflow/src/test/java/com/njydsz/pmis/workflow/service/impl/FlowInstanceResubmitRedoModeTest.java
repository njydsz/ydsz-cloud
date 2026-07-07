package com.njydsz.pmis.workflow.service.impl;

import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.workflow.dto.FlowStartProcessDTO;
import com.njydsz.pmis.workflow.entity.FlowAuditLogDO;
import com.njydsz.pmis.workflow.entity.FlowInstanceDO;
import com.njydsz.pmis.workflow.enums.FlowInstanceStatus;
import com.njydsz.pmis.workflow.mapper.FlowAuditLogMapper;
import com.njydsz.pmis.workflow.mapper.FlowInstanceMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * P1-8: 流程重做（redoMode）单元测试。
 *
 * <p>聚焦测试 {@link FlowInstanceServiceImpl#resubmit(String, String, Map, String, String)}
 * 的 redoMode 路由逻辑和 NEW_INSTANCE 模式的校验逻辑。
 *
 * @author ydsz-pmis-team
 * @since 1.6.0
 */
@DisplayName("P1-8 流程重做 redoMode 测试")
@ExtendWith(MockitoExtension.class)
class FlowInstanceResubmitRedoModeTest {

    @Mock
    private FlowInstanceMapper instanceMapper;
    @Mock
    private FlowAuditLogMapper auditLogMapper;
    @Spy
    @InjectMocks
    private FlowInstanceServiceImpl service;

    // ============================== RESTART 模式路由测试 ==============================

    @Nested
    @DisplayName("RESTART 模式路由（向后兼容）")
    class RestartModeTest {

        @Test
        @DisplayName("redoMode=RESTART → 委托到 4 参数 resubmit")
        void restartMode_delegatesTo4ParamResubmit() {
            // 用 spy 桩值 4 参数 resubmit 返回固定值
            doReturn("inst-1").when(service)
                    .resubmit(eq("inst-1"), eq("u1"), any(), eq("comment"));

            String result = service.resubmit("inst-1", "u1", null, "comment", "RESTART");

            assertEquals("inst-1", result);
            verify(service).resubmit("inst-1", "u1", null, "comment");
        }

        @Test
        @DisplayName("redoMode=null → 默认 RESTART")
        void nullRedoMode_defaultsToRestart() {
            doReturn("inst-1").when(service)
                    .resubmit(eq("inst-1"), eq("u1"), any(), eq("comment"));

            String result = service.resubmit("inst-1", "u1", null, "comment", null);

            assertEquals("inst-1", result);
        }

        @Test
        @DisplayName("redoMode='' → 默认 RESTART")
        void emptyRedoMode_defaultsToRestart() {
            doReturn("inst-1").when(service)
                    .resubmit(eq("inst-1"), eq("u1"), any(), eq("comment"));

            String result = service.resubmit("inst-1", "u1", null, "comment", "");

            assertEquals("inst-1", result);
        }

        @Test
        @DisplayName("redoMode='restart'（小写）→ 正常路由")
        void lowercaseRedoMode_normalized() {
            doReturn("inst-1").when(service)
                    .resubmit(eq("inst-1"), eq("u1"), any(), eq("comment"));

            String result = service.resubmit("inst-1", "u1", null, "comment", "restart");

            assertEquals("inst-1", result);
        }
    }

    // ============================== NEW_INSTANCE 模式校验测试 ==============================

    @Nested
    @DisplayName("NEW_INSTANCE 模式校验")
    class NewInstanceModeTest {

        @Test
        @DisplayName("RUNNING 状态 → 抛 BizException")
        void runningStatus_throwsException() {
            FlowInstanceDO instance = buildInstance("inst-1", "u1", FlowInstanceStatus.RUNNING);
            when(instanceMapper.selectById("inst-1")).thenReturn(instance);

            assertThrows(BizException.class, () ->
                    service.resubmit("inst-1", "u1", null, "comment", "NEW_INSTANCE"));
        }

        @Test
        @DisplayName("SUSPENDED 状态 → 抛 BizException")
        void suspendedStatus_throwsException() {
            FlowInstanceDO instance = buildInstance("inst-1", "u1", FlowInstanceStatus.SUSPENDED);
            when(instanceMapper.selectById("inst-1")).thenReturn(instance);

            assertThrows(BizException.class, () ->
                    service.resubmit("inst-1", "u1", null, "comment", "NEW_INSTANCE"));
        }

        @Test
        @DisplayName("非发起人 → 抛 BizException")
        void wrongInitiator_throwsException() {
            FlowInstanceDO instance = buildInstance("inst-1", "u1", FlowInstanceStatus.COMPLETED);
            when(instanceMapper.selectById("inst-1")).thenReturn(instance);

            assertThrows(BizException.class, () ->
                    service.resubmit("inst-1", "u2", null, "comment", "NEW_INSTANCE"));
        }

        @Test
        @DisplayName("COMPLETED + 正确发起人 → 创建新实例并写审计日志")
        void completedStatus_correctInitiator_createsNewInstance() {
            FlowInstanceDO instance = buildInstance("inst-1", "u1", FlowInstanceStatus.COMPLETED);
            when(instanceMapper.selectById("inst-1")).thenReturn(instance);

            // 桩值 getVariables（内部方法，通过 spy 桩值）
            Map<String, Object> vars = new HashMap<>();
            vars.put("amount", "1000");
            doReturn(vars).when(service).getVariables(eq("inst-1"));

            // 桩值 start 方法返回新实例 ID
            doReturn("inst-2").when(service).start(any(FlowStartProcessDTO.class));

            Map<String, Object> newVars = new HashMap<>();
            newVars.put("amount", "2000");

            String result = service.resubmit("inst-1", "u1", newVars, "重新提交", "NEW_INSTANCE");

            assertEquals("inst-2", result);
            // 验证调用了 start
            verify(service).start(any(FlowStartProcessDTO.class));
            // 验证写入了审计日志
            verify(auditLogMapper).insert((FlowAuditLogDO) any());
        }

        @Test
        @DisplayName("REJECTED + 正确发起人 → 创建新实例")
        void rejectedStatus_correctInitiator_createsNewInstance() {
            FlowInstanceDO instance = buildInstance("inst-1", "u1", FlowInstanceStatus.REJECTED);
            when(instanceMapper.selectById("inst-1")).thenReturn(instance);

            doReturn(new HashMap<>()).when(service).getVariables(eq("inst-1"));
            doReturn("inst-2").when(service).start(any(FlowStartProcessDTO.class));

            String result = service.resubmit("inst-1", "u1", null, null, "NEW_INSTANCE");

            assertEquals("inst-2", result);
            verify(service).start(any(FlowStartProcessDTO.class));
            verify(auditLogMapper).insert((FlowAuditLogDO) any());
        }

        @Test
        @DisplayName("TERMINATED + 正确发起人 → 创建新实例")
        void terminatedStatus_correctInitiator_createsNewInstance() {
            FlowInstanceDO instance = buildInstance("inst-1", "u1", FlowInstanceStatus.TERMINATED);
            when(instanceMapper.selectById("inst-1")).thenReturn(instance);

            doReturn(new HashMap<>()).when(service).getVariables(eq("inst-1"));
            doReturn("inst-2").when(service).start(any(FlowStartProcessDTO.class));

            String result = service.resubmit("inst-1", "u1", null, null, "NEW_INSTANCE");

            assertEquals("inst-2", result);
        }
    }

    // ============================== 辅助方法 ==============================

    /**
     * 构建测试用实例
     */
    private FlowInstanceDO buildInstance(String id, String initiatorId, FlowInstanceStatus status) {
        FlowInstanceDO instance = new FlowInstanceDO();
        instance.setId(id);
        instance.setFlowCode("test_flow");
        instance.setFlowVersion("1");
        instance.setBusinessType("TEST");
        instance.setBusinessId("biz-1");
        instance.setBusinessNo("NO-001");
        instance.setTitle("测试流程");
        instance.setInitiatorId(initiatorId);
        instance.setInitiatorName("测试用户");
        instance.setFlowStatus(status.name());
        instance.setTenantId("1");
        return instance;
    }
}
