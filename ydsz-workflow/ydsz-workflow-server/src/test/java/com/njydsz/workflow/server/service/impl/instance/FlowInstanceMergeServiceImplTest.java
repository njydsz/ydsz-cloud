package com.njydsz.workflow.server.service.impl.instance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.njydsz.common.exception.custom.SysException;
import com.njydsz.common.redis.service.ops.RedisCollectionOps;
import com.njydsz.common.redis.service.ops.RedisHashOps;
import com.njydsz.common.util.id.SnowflakeIdGenerator;
import com.njydsz.workflow.domain.repository.FlowInstanceRepository;
import com.njydsz.workflow.domain.vo.FlowInstanceVO;
import com.njydsz.workflow.server.cache.CacheKeyBuilder;
import com.njydsz.workflow.server.service.FlowTaskService;
import com.njydsz.workflow.WorkflowFacade;

@ExtendWith(MockitoExtension.class)
class FlowInstanceMergeServiceImplTest {

  @InjectMocks
  private FlowInstanceMergeServiceImpl mergeService;

  @Mock
  private SnowflakeIdGenerator snowflakeIdGenerator;

  @Mock
  private FlowInstanceRepository instanceRepository;

  @Mock
  private FlowTaskService taskService;

  @Mock
  private WorkflowFacade workflowFacade;

  @Mock
  private RedisCollectionOps redisCollectionOps;

  @Mock
  private CacheKeyBuilder cacheKeyBuilder;

  @Mock
  private RedisHashOps redisHashOps;

  @Test
  @DisplayName("mergeInstances - insufficient count throws exception")
  void mergeInstances_insufficientCount_throwsException() {
    List<String> singleInstance = List.of("inst-001");

    assertThatThrownBy(() -> mergeService.mergeInstances(singleInstance, "user-1", "tenant-1"))
        .isInstanceOf(SysException.class);
  }

  @Test
  @DisplayName("mergeInstances - empty list throws exception")
  void mergeInstances_emptyList_throwsException() {
    assertThatThrownBy(() -> mergeService.mergeInstances(new ArrayList<>(), "user-1", "tenant-1"))
        .isInstanceOf(SysException.class);
  }

  @Test
  @DisplayName("mergeInstances - flowCode mismatch throws exception")
  void mergeInstances_flowCodeMismatch_throwsException() {
    List<String> instanceIds = List.of("inst-001", "inst-002");

    FlowInstanceVO instance1 = new FlowInstanceVO();
    instance1.setId("inst-001");
    instance1.setFlowCode("FLOW_A");
    instance1.setFlowStatus("RUNNING");

    FlowInstanceVO instance2 = new FlowInstanceVO();
    instance2.setId("inst-002");
    instance2.setFlowCode("FLOW_B");
    instance2.setFlowStatus("RUNNING");

    when(instanceRepository.findAllById(Set.of("inst-001", "inst-002")))
        .thenReturn(List.of(instance1, instance2));

    assertThatThrownBy(() -> mergeService.mergeInstances(instanceIds, "user-1", "tenant-1"))
        .isInstanceOf(SysException.class);
  }
}
