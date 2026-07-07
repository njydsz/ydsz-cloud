package com.njydsz.pmis.workflow.service.impl;

import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.workflow.dto.FlowStartProcessDTO;
import com.njydsz.pmis.workflow.service.FlowInstanceService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * {@link FlowInstanceServiceImpl} P2-6 批量发起流程实例 单元测试。
 *
 * <p>由于 {@link FlowInstanceServiceImpl} 依赖 16+ 个组件，本测试仅 Mock self 代理
 * （{@link FlowInstanceService} 类型自身），验证批量发起的循环逻辑、错误处理、参数校验。
 *
 * <p>覆盖场景：
 * <ul>
 *   <li>参数校验：空列表 / 超过 100 条</li>
 *   <li>全部成功：N 条全部成功</li>
 *   <li>部分失败：部分成功 + 部分失败</li>
 *   <li>全部失败：N 条全部失败</li>
 *   <li>结果结构：successCount / failedCount / instanceIds / failedItems</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("P2-6: 批量发起流程实例 - FlowInstanceServiceImpl")
class FlowInstanceBatchStartServiceImplTest {

    /**
     * Mock self 代理引用 — {@link FlowInstanceServiceImpl#batchStartInstances} 通过
     * self.start() 调用确保独立事务。
     *
     * <p>注意：@InjectMocks 会将 self 字段（类型为 FlowInstanceServiceImpl）匹配到这个 @Mock，
     * 因为 Mockito 按"最大构造函数 + 类型匹配"注入，self 是唯一可注入的同类型 Mock。
     */
    @Mock
    private FlowInstanceServiceImpl self;

    @InjectMocks
    private FlowInstanceServiceImpl service;

    private FlowStartProcessDTO buildDto(String businessId) {
        FlowStartProcessDTO dto = new FlowStartProcessDTO();
        dto.setFlowCode("test_flow");
        dto.setBusinessType("project_initiation");
        dto.setBusinessId(businessId);
        return dto;
    }

    // ============================== 参数校验测试 ==============================

    @Nested
    @DisplayName("参数校验测试")
    class ValidationTest {

        @Test
        @DisplayName("dtos 为 null → BAD_REQUEST")
        void shouldThrowWhenNull() {
            BizException ex = assertThrows(BizException.class,
                    () -> service.batchStartInstances(null));
            assertEquals(BizErrorCode.BAD_REQUEST.getCode(), ex.getCode());
            assertEquals("error.workflow.msg_e4f5a6b7", ex.getErrorMessage());
            verifyNoInteractions(self);
        }

        @Test
        @DisplayName("dtos 为空列表 → BAD_REQUEST")
        void shouldThrowWhenEmpty() {
            BizException ex = assertThrows(BizException.class,
                    () -> service.batchStartInstances(Collections.emptyList()));
            assertEquals(BizErrorCode.BAD_REQUEST.getCode(), ex.getCode());
            assertEquals("error.workflow.msg_e4f5a6b7", ex.getErrorMessage());
            verifyNoInteractions(self);
        }

        @Test
        @DisplayName("dtos 超过 100 条 → BAD_REQUEST")
        void shouldThrowWhenExceedLimit() {
            List<FlowStartProcessDTO> dtos = new ArrayList<>();
            for (int i = 0; i < 101; i++) {
                dtos.add(buildDto("biz_" + i));
            }
            BizException ex = assertThrows(BizException.class,
                    () -> service.batchStartInstances(dtos));
            assertEquals(BizErrorCode.BAD_REQUEST.getCode(), ex.getCode());
            assertEquals("error.workflow.msg_f5a6b7c8", ex.getErrorMessage());
            verifyNoInteractions(self);
        }
    }

    // ============================== 批量发起逻辑测试 ==============================

    @Nested
    @DisplayName("批量发起逻辑测试")
    class BatchStartTest {

        @Test
        @DisplayName("全部成功 → successCount=3, failedCount=0")
        void shouldReturnAllSuccess() {
            List<FlowStartProcessDTO> dtos = List.of(
                    buildDto("biz_1"), buildDto("biz_2"), buildDto("biz_3"));
            when(self.start(any())).thenReturn("inst_1", "inst_2", "inst_3");

            Map<String, Object> result = service.batchStartInstances(dtos);

            assertEquals(3, result.get("successCount"));
            assertEquals(0, result.get("failedCount"));
            @SuppressWarnings("unchecked")
            List<String> instanceIds = (List<String>) result.get("instanceIds");
            assertEquals(List.of("inst_1", "inst_2", "inst_3"), instanceIds);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> failedItems = (List<Map<String, Object>>)
                    result.get("failedItems");
            assertTrue(failedItems.isEmpty());
            verify(self, times(3)).start(any());
        }

        @Test
        @DisplayName("部分失败 → successCount=2, failedCount=1, failedItems 含失败明细")
        void shouldReturnPartialFailure() {
            List<FlowStartProcessDTO> dtos = List.of(
                    buildDto("biz_1"), buildDto("biz_2"), buildDto("biz_3"));
            // 第 1、3 条成功，第 2 条失败
            when(self.start(dtos.get(0))).thenReturn("inst_1");
            when(self.start(dtos.get(1)))
                    .thenThrow(new BizException(BizErrorCode.BAD_REQUEST, "流程定义不存在"));
            when(self.start(dtos.get(2))).thenReturn("inst_3");

            Map<String, Object> result = service.batchStartInstances(dtos);

            assertEquals(2, result.get("successCount"));
            assertEquals(1, result.get("failedCount"));
            @SuppressWarnings("unchecked")
            List<String> instanceIds = (List<String>) result.get("instanceIds");
            assertEquals(List.of("inst_1", "inst_3"), instanceIds);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> failedItems = (List<Map<String, Object>>)
                    result.get("failedItems");
            assertEquals(1, failedItems.size());
            assertEquals(2, failedItems.get(0).get("index"));
            assertEquals("biz_2", failedItems.get(0).get("businessId"));
            assertNotNull(failedItems.get(0).get("reason"));
        }

        @Test
        @DisplayName("全部失败 → successCount=0, failedCount=2")
        void shouldReturnAllFailure() {
            List<FlowStartProcessDTO> dtos = List.of(
                    buildDto("biz_1"), buildDto("biz_2"));
            when(self.start(any()))
                    .thenThrow(new BizException(BizErrorCode.NOT_FOUND, "流程定义不存在"));

            Map<String, Object> result = service.batchStartInstances(dtos);

            assertEquals(0, result.get("successCount"));
            assertEquals(2, result.get("failedCount"));
            @SuppressWarnings("unchecked")
            List<String> instanceIds = (List<String>) result.get("instanceIds");
            assertTrue(instanceIds.isEmpty());
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> failedItems = (List<Map<String, Object>>)
                    result.get("failedItems");
            assertEquals(2, failedItems.size());
        }

        @Test
        @DisplayName("异常 message 为 null 时 reason 回退到异常类名")
        void shouldHandleNullMessage() {
            List<FlowStartProcessDTO> dtos = List.of(buildDto("biz_1"));
            // 抛出 message=null 的异常
            when(self.start(any())).thenThrow(new RuntimeException());

            Map<String, Object> result = service.batchStartInstances(dtos);

            assertEquals(0, result.get("successCount"));
            assertEquals(1, result.get("failedCount"));
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> failedItems = (List<Map<String, Object>>)
                    result.get("failedItems");
            assertEquals("RuntimeException", failedItems.get(0).get("reason"));
        }

        @Test
        @DisplayName("单条成功 → successCount=1")
        void shouldReturnSingleSuccess() {
            List<FlowStartProcessDTO> dtos = List.of(buildDto("biz_1"));
            when(self.start(any())).thenReturn("inst_1");

            Map<String, Object> result = service.batchStartInstances(dtos);

            assertEquals(1, result.get("successCount"));
            assertEquals(0, result.get("failedCount"));
            verify(self, times(1)).start(any());
        }

        @Test
        @DisplayName("结果 Map 包含所有必需字段")
        void shouldContainAllRequiredFields() {
            List<FlowStartProcessDTO> dtos = List.of(buildDto("biz_1"));
            when(self.start(any())).thenReturn("inst_1");

            Map<String, Object> result = service.batchStartInstances(dtos);

            assertTrue(result.containsKey("successCount"));
            assertTrue(result.containsKey("failedCount"));
            assertTrue(result.containsKey("instanceIds"));
            assertTrue(result.containsKey("failedItems"));
        }
    }
}
