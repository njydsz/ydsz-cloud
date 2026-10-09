package com.njydsz.workflow.server.service.impl.instance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collection;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.njydsz.workflow.domain.dto.FlowStartProcessDTO;
import com.njydsz.workflow.domain.vo.FlowBatchStartResultVO;
import com.njydsz.workflow.domain.vo.FlowInstanceVO;
import com.njydsz.workflow.server.service.impl.instance.FlowInstanceBatchOperator;
import com.njydsz.workflow.server.service.impl.instance.FlowInstanceLifecycleManager;
import com.njydsz.workflow.server.service.impl.instance.FlowInstanceQueryService;
import com.njydsz.workflow.server.service.impl.instance.FlowInstanceVariableManager;

@ExtendWith(MockitoExtension.class)
class FlowInstanceServiceImplTest {

    @InjectMocks
    private FlowInstanceServiceImpl flowInstanceService;

    @Mock
    private FlowInstanceLifecycleManager lifecycleManager;

    @Mock
    private FlowInstanceBatchOperator batchOperator;

    @Mock
    private FlowInstanceQueryService queryService;

    @Mock
    private FlowInstanceVariableManager variableManager;

    @Nested
    @DisplayName("start")
    class Start {

        @Test
        @DisplayName("start - delegates to lifecycleManager and returns instanceId")
        void start_delegatesToLifecycleManager_returnsInstanceId() {
            FlowStartProcessDTO dto = new FlowStartProcessDTO();
            dto.setBusinessType("LEAVE");
            dto.setBusinessId("lv-001");

            when(lifecycleManager.start(dto)).thenReturn("inst-001");

            String result = flowInstanceService.start(dto);

            assertThat(result).isEqualTo("inst-001");
            verify(lifecycleManager).start(dto);
        }
    }

    @Nested
    @DisplayName("terminate")
    class Terminate {

        @Test
        @DisplayName("terminate - delegates to lifecycleManager with correct parameters")
        void terminate_delegatesToLifecycleManager() {
            flowInstanceService.terminate("inst-001", "紧急终止");

            verify(lifecycleManager).terminate("inst-001", "紧急终止");
        }
    }

    @Nested
    @DisplayName("batchTerminate")
    class BatchTerminate {

        @Test
        @DisplayName("batchTerminate - delegates to batchOperator and returns count")
        void batchTerminate_delegatesToBatchOperator_returnsCount() {
            List<String> instanceIds = List.of("inst-001", "inst-002");
            when(batchOperator.batchTerminate(instanceIds, "批量终止")).thenReturn(2);

            int result = flowInstanceService.batchTerminate(instanceIds, "批量终止");

            assertThat(result).isEqualTo(2);
            verify(batchOperator).batchTerminate(instanceIds, "批量终止");
        }
    }

    @Nested
    @DisplayName("suspend / activate / complete")
    class LifecycleDelegate {

        @Test
        @DisplayName("suspend - delegates to lifecycleManager")
        void suspend_delegatesToLifecycleManager() {
            flowInstanceService.suspend("inst-001");

            verify(lifecycleManager).suspend("inst-001");
        }

        @Test
        @DisplayName("activate - delegates to lifecycleManager")
        void activate_delegatesToLifecycleManager() {
            flowInstanceService.activate("inst-001");

            verify(lifecycleManager).activate("inst-001");
        }

        @Test
        @DisplayName("complete - delegates to lifecycleManager with endNodeCode")
        void complete_delegatesToLifecycleManager() {
            flowInstanceService.complete("inst-001", "END_NODE");

            verify(lifecycleManager).complete("inst-001", "END_NODE");
        }
    }

    @Nested
    @DisplayName("query methods delegate to queryService")
    class QueryDelegate {

        @Test
        @DisplayName("getById - delegates to queryService and returns VO")
        void getById_delegatesToQueryService() {
            FlowInstanceVO vo = new FlowInstanceVO();
            vo.setId("inst-001");
            when(queryService.getById("inst-001")).thenReturn(vo);

            FlowInstanceVO result = flowInstanceService.getById("inst-001");

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo("inst-001");
        }

        @Test
        @DisplayName("listByIds - delegates to queryService")
        void listByIds_delegatesToQueryService() {
            Collection<String> ids = List.of("inst-001", "inst-002");
            List<FlowInstanceVO> expected = List.of(new FlowInstanceVO());
            when(queryService.listByIds(ids)).thenReturn(expected);

            List<FlowInstanceVO> result = flowInstanceService.listByIds(ids);

            assertThat(result).hasSize(1);
            verify(queryService).listByIds(ids);
        }

        @Test
        @DisplayName("getByBusiness - delegates to queryService")
        void getByBusiness_delegatesToQueryService() {
            FlowInstanceVO vo = new FlowInstanceVO();
            when(queryService.getByBusiness("LEAVE", "lv-001")).thenReturn(vo);

            FlowInstanceVO result = flowInstanceService.getByBusiness("LEAVE", "lv-001");

            assertThat(result).isNotNull();
        }
    }

    @Nested
    @DisplayName("batchStartInstances")
    class BatchStart {

        @Test
        @DisplayName("batchStartInstances - delegates to batchOperator and returns result VO")
        void batchStartInstances_delegatesToBatchOperator() {
            List<FlowStartProcessDTO> dtos = List.of(new FlowStartProcessDTO());
            FlowBatchStartResultVO expected = new FlowBatchStartResultVO();
            expected.setSuccessCount(1);
            expected.setFailedCount(0);
            expected.setInstanceIds(List.of("inst-new"));
            when(batchOperator.batchStartInstances(dtos)).thenReturn(expected);

            FlowBatchStartResultVO result = flowInstanceService.batchStartInstances(dtos);

            assertThat(result).isNotNull();
            assertThat(result.getSuccessCount()).isEqualTo(1);
            verify(batchOperator).batchStartInstances(dtos);
        }
    }
}
