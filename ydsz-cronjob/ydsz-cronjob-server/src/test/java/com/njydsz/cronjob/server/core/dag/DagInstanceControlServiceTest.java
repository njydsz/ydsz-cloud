package com.njydsz.cronjob.server.core.dag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.njydsz.cronjob.domain.dag.DagInstanceStatus;
import com.njydsz.cronjob.domain.dag.DagNodeStatus;
import com.njydsz.cronjob.domain.repository.DagInstanceRepository;
import com.njydsz.cronjob.domain.repository.DagNodeInstanceRepository;
import com.njydsz.cronjob.domain.repository.JobDagRepository;
import com.njydsz.cronjob.domain.repository.JobRepository;
import com.njydsz.cronjob.domain.vo.JobDagInstanceVO;
import com.njydsz.cronjob.domain.vo.JobDagNodeInstanceVO;
import com.njydsz.cronjob.domain.vo.JobVO;

/**
 * {@link DagInstanceControlService} 单元测试。
 *
 * <p>覆盖 DAG 实例控制服务的四个核心操作：暂停、恢复、取消、重试节点。
 * 每个操作包含"委托成功"和"边界/异常"两类场景，验证 service 正确委托到 Repository 和
 * Executor，并按预期返回 true/false。
 *
 * <p>所有依赖（Repository/Codec/Executor）通过 @Mock 注入，确保纯单元测试不触及数据库。
 *
 * @author ydsz-smoke-test
 * @since 26.10.12
 */
@ExtendWith(MockitoExtension.class)
class DagInstanceControlServiceTest {

  /** 被测对象：所有依赖通过 @Mock 注入 */
  @InjectMocks
  private DagInstanceControlService dagInstanceControlService;

  @Mock
  private DagInstanceRepository dagInstanceRepository;

  @Mock
  private DagNodeInstanceRepository dagNodeInstanceRepository;

  @Mock
  private JobDagRepository jobDagRepository;

  @Mock
  private JobRepository jobRepository;

  @Mock
  private DagDefinitionCodec dagDefinitionCodec;

  @Mock
  private DagInstanceExecutor dagInstanceExecutor;

  /** 通用测试常量：DAG 实例 ID */
  private static final String INSTANCE_ID = "inst-001";

  // ============================== pause ==============================

  @Nested
  @DisplayName("pause — 暂停 DAG 实例")
  class Pause {

    @Test
    @DisplayName("pause - RUNNING 状态实例暂停成功返回 true，并委托 markPaused")
    void pause_runningInstance_returnsTrue() {
      when(dagInstanceRepository.markPaused(INSTANCE_ID)).thenReturn(1);

      boolean result = dagInstanceControlService.pause(INSTANCE_ID);

      assertThat(result).isTrue();
      verify(dagInstanceRepository).markPaused(INSTANCE_ID);
    }

    @Test
    @DisplayName("pause - 非 RUNNING 或不存在返回 false，不抛出异常")
    void pause_nonRunningOrNotFound_returnsFalse() {
      when(dagInstanceRepository.markPaused(INSTANCE_ID)).thenReturn(0);

      boolean result = dagInstanceControlService.pause(INSTANCE_ID);

      assertThat(result).isFalse();
      verify(dagInstanceRepository).markPaused(INSTANCE_ID);
    }
  }

  // ============================== resume ==============================

  @Nested
  @DisplayName("resume — 恢复 DAG 实例")
  class Resume {

    @Test
    @DisplayName("resume - PAUSED 状态实例恢复成功返回 true，并派发挂起节点")
    void resume_pausedInstance_returnsTrueAndRedelivers() {
      when(dagInstanceRepository.markResumed(INSTANCE_ID)).thenReturn(1);

      boolean result = dagInstanceControlService.resume(INSTANCE_ID);

      assertThat(result).isTrue();
      verify(dagInstanceRepository).markResumed(INSTANCE_ID);
      verify(dagInstanceExecutor).redeliverPendingNodes(INSTANCE_ID);
    }

    @Test
    @DisplayName("resume - 非 PAUSED 状态返回 false，不派发节点")
    void resume_nonPaused_returnsFalseAndDoesNotRedeliver() {
      when(dagInstanceRepository.markResumed(INSTANCE_ID)).thenReturn(0);

      boolean result = dagInstanceControlService.resume(INSTANCE_ID);

      assertThat(result).isFalse();
      verify(dagInstanceRepository).markResumed(INSTANCE_ID);
      verify(dagInstanceExecutor, never()).redeliverPendingNodes(anyString());
    }
  }

  // ============================== cancel ==============================

  @Nested
  @DisplayName("cancel — 取消 DAG 实例")
  class Cancel {

    @Test
    @DisplayName("cancel - RUNNING 状态实例取消成功，跳过未完成节点并返回 true")
    void cancel_runningInstance_returnsTrueAndSkipsPendingNodes() {
      JobDagInstanceVO instance = buildInstance(INSTANCE_ID, DagInstanceStatus.RUNNING.name());
      when(dagInstanceRepository.findById(INSTANCE_ID)).thenReturn(Optional.of(instance));
      when(dagInstanceRepository.markCanceled(eq(INSTANCE_ID), any(LocalDateTime.class), eq(0L)))
          .thenReturn(1);
      when(dagNodeInstanceRepository.findByDagInstanceId(INSTANCE_ID))
          .thenReturn(List.of(buildNode("node-1", DagNodeStatus.PENDING.name())));

      boolean result = dagInstanceControlService.cancel(INSTANCE_ID);

      assertThat(result).isTrue();
      verify(dagInstanceRepository).markCanceled(eq(INSTANCE_ID), any(LocalDateTime.class), eq(0L));
      verify(dagNodeInstanceRepository).markSkipped("node-1");
    }

    @Test
    @DisplayName("cancel - 实例不存在返回 false")
    void cancel_instanceNotFound_returnsFalse() {
      when(dagInstanceRepository.findById(INSTANCE_ID)).thenReturn(Optional.empty());

      boolean result = dagInstanceControlService.cancel(INSTANCE_ID);

      assertThat(result).isFalse();
      verify(dagInstanceRepository).findById(INSTANCE_ID);
      verify(dagInstanceRepository, never()).markCanceled(anyString(), any(LocalDateTime.class), any());
    }

    @Test
    @DisplayName("cancel - 已终态实例取消返回 false（markCanceled CAS 失败）")
    void cancel_terminalInstance_returnsFalse() {
      JobDagInstanceVO instance = buildInstance(INSTANCE_ID, DagInstanceStatus.RUNNING.name());
      when(dagInstanceRepository.findById(INSTANCE_ID)).thenReturn(Optional.of(instance));
      // 模拟 CAS 失败（0 行受影响，因实际状态已非 RUNNING/PAUSED）
      when(dagInstanceRepository.markCanceled(eq(INSTANCE_ID), any(LocalDateTime.class), eq(0L)))
          .thenReturn(0);

      boolean result = dagInstanceControlService.cancel(INSTANCE_ID);

      assertThat(result).isFalse();
    }
  }

  // ============================== retryNode ==============================

  @Nested
  @DisplayName("retryNode — 重试失败节点")
  class RetryNode {

    @Test
    @DisplayName("retryNode - FAILED 节点重试成功返回 true，重置为 PENDING 并插入新节点实例")
    void retryNode_failedNode_returnsTrueAndInsertsRetryNode() {
      JobDagInstanceVO instance = buildInstance(INSTANCE_ID, DagInstanceStatus.RUNNING.name());
      JobDagNodeInstanceVO failedNode =
          buildNodeWithJobKey("node-1", "extract-job", DagNodeStatus.FAILED.name());

      when(dagInstanceRepository.findById(INSTANCE_ID)).thenReturn(Optional.of(instance));
      when(dagNodeInstanceRepository.findByDagInstanceId(INSTANCE_ID))
          .thenReturn(List.of(failedNode));
      when(dagDefinitionCodec.fromJson(anyString())).thenReturn(DagDefinition.empty());

      boolean result = dagInstanceControlService.retryNode(INSTANCE_ID, "extract-job");

      assertThat(result).isTrue();
      // 验证：原 FAILED 节点被打上 SKIPPED 标记
      verify(dagNodeInstanceRepository).markSkipped("node-1");
      // 验证：插入一条新的 PENDING 节点
      verify(dagNodeInstanceRepository).insert(any(JobDagNodeInstanceVO.class));
    }

    @Test
    @DisplayName("retryNode - 实例不存在返回 false，不触发任何更新")
    void retryNode_instanceNotFound_returnsFalse() {
      when(dagInstanceRepository.findById(INSTANCE_ID)).thenReturn(Optional.empty());

      boolean result = dagInstanceControlService.retryNode(INSTANCE_ID, "extract-job");

      assertThat(result).isFalse();
      verify(dagInstanceRepository).findById(INSTANCE_ID);
      verify(dagNodeInstanceRepository, never()).markSkipped(anyString());
      verify(dagNodeInstanceRepository, never()).insert(any(JobDagNodeInstanceVO.class));
    }

    @Test
    @DisplayName("retryNode - 实例已终态返回 false，无法重试")
    void retryNode_terminalInstance_returnsFalse() {
      JobDagInstanceVO instance = buildInstance(INSTANCE_ID, DagInstanceStatus.SUCCESS.name());
      when(dagInstanceRepository.findById(INSTANCE_ID)).thenReturn(Optional.of(instance));

      boolean result = dagInstanceControlService.retryNode(INSTANCE_ID, "extract-job");

      assertThat(result).isFalse();
      verify(dagNodeInstanceRepository, never()).markSkipped(anyString());
    }

    @Test
    @DisplayName("retryNode - 非 FAILED 状态的节点不重试返回 false")
    void retryNode_nonFailedNode_returnsFalse() {
      JobDagInstanceVO instance = buildInstance(INSTANCE_ID, DagInstanceStatus.RUNNING.name());
      // 节点为 PENDING 而非 FAILED
      JobDagNodeInstanceVO pendingNode =
          buildNodeWithJobKey("node-1", "extract-job", DagNodeStatus.PENDING.name());

      when(dagInstanceRepository.findById(INSTANCE_ID)).thenReturn(Optional.of(instance));
      when(dagNodeInstanceRepository.findByDagInstanceId(INSTANCE_ID))
          .thenReturn(List.of(pendingNode));

      boolean result = dagInstanceControlService.retryNode(INSTANCE_ID, "extract-job");

      assertThat(result).isFalse();
      verify(dagNodeInstanceRepository, never()).markSkipped(anyString());
      verify(dagNodeInstanceRepository, never()).insert(any(JobDagNodeInstanceVO.class));
    }
  }

  // ============================== 工具方法 ==============================

  /**
   * 构建 JobDagInstanceVO，设置 id 和 instanceStatus。
   *
   * @param id          实例 ID
   * @param status      实例状态字符串
   * @return 构建好的 JobDagInstanceVO
   */
  private JobDagInstanceVO buildInstance(String id, String status) {
    JobDagInstanceVO vo = new JobDagInstanceVO();
    vo.setId(id);
    vo.setInstanceStatus(status);
    vo.setStartedAt(null);
    return vo;
  }

  /**
   * 构建 JobDagNodeInstanceVO，仅设置 id 和 nodeStatus。
   *
   * @param id         节点实例 ID
   * @param status     节点状态字符串
   * @return 构建好的 JobDagNodeInstanceVO
   */
  private JobDagNodeInstanceVO buildNode(String id, String status) {
    JobDagNodeInstanceVO vo = new JobDagNodeInstanceVO();
    vo.setId(id);
    vo.setNodeStatus(status);
    return vo;
  }

  /**
   * 构建 JobDagNodeInstanceVO，设置 id、jobKey 和 nodeStatus。
   *
   * @param id         节点实例 ID
   * @param jobKey     任务 KEY
   * @param status     节点状态字符串
   * @return 构建好的 JobDagNodeInstanceVO
   */
  private JobDagNodeInstanceVO buildNodeWithJobKey(String id, String jobKey, String status) {
    JobDagNodeInstanceVO vo = new JobDagNodeInstanceVO();
    vo.setId(id);
    vo.setJobKey(jobKey);
    vo.setNodeStatus(status);
    return vo;
  }
}
