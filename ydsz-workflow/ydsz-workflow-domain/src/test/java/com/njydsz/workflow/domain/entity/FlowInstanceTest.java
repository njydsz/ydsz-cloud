package com.njydsz.workflow.domain.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;

import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.workflow.domain.enums.FlowInstanceStatus;
import com.njydsz.workflow.domain.event.FlowInstanceResumedEvent;
import com.njydsz.workflow.domain.event.FlowInstanceSuspendedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * {@link FlowInstance} 充血模型领域行为单元测试。
 *
 * <p>验证：状态流转、终态判定、驳回/回滚、超期检测、领域事件收集等行为逻辑。
 * 纯内存测试，无需 Spring 容器或数据库。
 *
 * @author ydsz-team
 * @since 26.09.26
 * @see FlowInstanceStatus 状态枚举测试
 */
@Tag("unit")
@DisplayName("FlowInstance 充血模型领域行为")
class FlowInstanceTest {

  private FlowInstance instance;

  @BeforeEach
  void setUp() {
    instance = new FlowInstance();
    instance.setId("inst-test-001");
    instance.setFlowCode("project-approval");
    instance.setFlowName("项目审批");
    instance.setBusinessType("PROJECT");
    instance.setBusinessId("biz-001");
    instance.setInitiatorId("user-123");
    instance.setFlowStatus(FlowInstanceStatus.RUNNING.name());
    instance.setStartAt(LocalDateTime.now().minusHours(1));
    instance.setActivityStatus(1);
  }

  @Nested
  @DisplayName("transitTo 状态流转")
  class TransitToTest {

    @Test
    @DisplayName("RUNNING 到 COMPLETED 流转成功并自动计算耗时")
    void runningToCompleted_success() {
      instance.transitTo(FlowInstanceStatus.COMPLETED, "approver-1");
      assertThat(instance.getFlowStatus()).isEqualTo(FlowInstanceStatus.COMPLETED.name());
      assertThat(instance.getEndAt()).isNotNull();
      assertThat(instance.getDurationMs()).isGreaterThan(0);
    }

    @Test
    @DisplayName("RUNNING 到 TERMINATED 流转成功")
    void runningToTerminated_success() {
      instance.transitTo(FlowInstanceStatus.TERMINATED, "admin-1");
      assertThat(instance.getFlowStatus()).isEqualTo(FlowInstanceStatus.TERMINATED.name());
    }

    @Test
    @DisplayName("RUNNING 到 SUSPENDED 流转成功且 endAt 为空")
    void runningToSuspend_success() {
      instance.transitTo(FlowInstanceStatus.SUSPENDED, "operator-1");
      assertThat(instance.getFlowStatus()).isEqualTo(FlowInstanceStatus.SUSPENDED.name());
      assertThat(instance.getEndAt()).as("挂起状态不计入终态，endAt 应为空").isNull();
    }

    @Test
    @DisplayName("终态 COMPLETED 到 RUNNING 流转非法应抛出异常")
    void completedToRunning_invalid() {
      instance.setFlowStatus(FlowInstanceStatus.COMPLETED.name());
      instance.setEndAt(LocalDateTime.now());
      assertThatThrownBy(() -> instance.transitTo(FlowInstanceStatus.RUNNING, "user-x"))
          .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("COMPLETED 到 ROLLED_BACK 回滚合法")
    void completedToRolledBack_valid() {
      instance.setFlowStatus(FlowInstanceStatus.COMPLETED.name());
      instance.setEndAt(LocalDateTime.now());
      instance.transitTo(FlowInstanceStatus.ROLLED_BACK, "admin-1");
      assertThat(instance.getFlowStatus()).isEqualTo(FlowInstanceStatus.ROLLED_BACK.name());
    }
  }

  @Nested
  @DisplayName("isFinished 终态判定")
  class IsFinishedTest {

    @Test
    @DisplayName("COMPLETED、TERMINATED、REJECTED、ROLLED_BACK 为终态")
    void finishedStates() {
      FlowInstanceStatus[] finished = {
          FlowInstanceStatus.COMPLETED,
          FlowInstanceStatus.TERMINATED,
          FlowInstanceStatus.REJECTED,
          FlowInstanceStatus.ROLLED_BACK
      };
      for (FlowInstanceStatus status : finished) {
        instance.setFlowStatus(status.name());
        assertThat(instance.isFinished()).as(status + " 应为终态").isTrue();
      }
    }

    @Test
    @DisplayName("RUNNING、SUSPENDED、ERROR、DRAFT 非终态")
    void nonFinishedStates() {
      FlowInstanceStatus[] nonFinished = {
          FlowInstanceStatus.RUNNING,
          FlowInstanceStatus.SUSPENDED,
          FlowInstanceStatus.ERROR,
          FlowInstanceStatus.DRAFT
      };
      for (FlowInstanceStatus status : nonFinished) {
        instance.setFlowStatus(status.name());
        assertThat(instance.isFinished()).as(status + " 应为非终态").isFalse();
      }
    }
  }

  @Nested
  @DisplayName("canSuspend / canActivate 激活状态判定")
  class ActivityTest {

    @Test
    @DisplayName("仅 RUNNING 状态可挂起")
    void onlyRunningCanSuspend() {
      instance.setFlowStatus(FlowInstanceStatus.RUNNING.name());
      assertThat(instance.canSuspend()).isTrue();
      instance.setFlowStatus(FlowInstanceStatus.SUSPENDED.name());
      assertThat(instance.canSuspend()).isFalse();
    }

    @Test
    @DisplayName("仅 SUSPENDED 状态可恢复")
    void onlySuspendedCanActivate() {
      instance.setFlowStatus(FlowInstanceStatus.SUSPENDED.name());
      assertThat(instance.canActivate()).isTrue();
      instance.setFlowStatus(FlowInstanceStatus.RUNNING.name());
      assertThat(instance.canActivate()).isFalse();
    }
  }

  @Nested
  @DisplayName("isOverdue 超期检测")
  class OverdueTest {

    @Test
    @DisplayName("dueAt 为未来时间时不超期")
    void futureDue_notOverdue() {
      instance.setDueAt(LocalDateTime.now().plusDays(7));
      assertThat(instance.isOverdue()).isFalse();
    }

    @Test
    @DisplayName("dueAt 为过去时间时超期")
    void pastDue_isOverdue() {
      instance.setDueAt(LocalDateTime.now().minusHours(1));
      assertThat(instance.isOverdue()).isTrue();
    }

    @Test
    @DisplayName("dueAt 为 null 时不超期")
    void nullDue_notOverdue() {
      instance.setDueAt(null);
      assertThat(instance.isOverdue()).isFalse();
    }
  }

  @Nested
  @DisplayName("reject 驳回业务方法")
  class RejectTest {

    @Test
    @DisplayName("驳回方法设置 rejectReason 并流转到 REJECTED")
    void reject_setsReasonAndStatus() {
      instance.reject("材料不齐全", "approver-2");
      assertThat(instance.getRejectReason()).isEqualTo("材料不齐全");
      assertThat(instance.getFlowStatus()).isEqualTo(FlowInstanceStatus.REJECTED.name());
    }
  }

  @Nested
  @DisplayName("rollback 回滚业务方法")
  class RollbackTest {

    @Test
    @DisplayName("回滚 COMPLETED 实例到 ROLLED_BACK")
    void rollback_completedToRolledBack() {
      instance.setFlowStatus(FlowInstanceStatus.COMPLETED.name());
      instance.setEndAt(LocalDateTime.now());
      instance.rollback("admin-x");
      assertThat(instance.getFlowStatus()).isEqualTo(FlowInstanceStatus.ROLLED_BACK.name());
    }

    @Test
    @DisplayName("回滚 RUNNING 实例流转合法（状态机允许 RUNNING 到 ROLLED_BACK）")
    void rollback_running_valid() {
      // 状态机 RUNNING_TRANSITIONS 规则明确允许 ROLLED_BACK 目标
      assertThatCode(() -> instance.rollback("admin-x")).doesNotThrowAnyException();
      assertThat(instance.getFlowStatus()).isEqualTo(FlowInstanceStatus.ROLLED_BACK.name());
    }
  }

  @Nested
  @DisplayName("popDomainEvents 领域事件收集")
  class DomainEventTest {

    @Test
    @DisplayName("transitTo SUSPENDED 后产生 FlowInstanceSuspendedEvent")
    void suspend_emitsEvent() {
      instance.transitTo(FlowInstanceStatus.SUSPENDED, "operator-1");
      var events = instance.popDomainEvents();
      assertThat(events).hasSize(1);
      assertThat(events.get(0)).isInstanceOf(FlowInstanceSuspendedEvent.class);
    }

    @Test
    @DisplayName("popDomainEvents 后清空事件列表避免重复发布")
    void popDomainEvents_clearsAfterRetrieval() {
      instance.transitTo(FlowInstanceStatus.SUSPENDED, "operator-1");
      instance.popDomainEvents();
      var second = instance.popDomainEvents();
      assertThat(second).isEmpty();
    }

    @Test
    @DisplayName("SUSPENDED 到 RUNNING 流转产生 FlowInstanceResumedEvent")
    void resumeFromSuspended_emitsResumedEvent() {
      instance.setFlowStatus(FlowInstanceStatus.SUSPENDED.name());
      instance.transitTo(FlowInstanceStatus.RUNNING, "operator-1");
      var events = instance.popDomainEvents();
      assertThat(events).hasSize(1);
      assertThat(events.get(0)).isInstanceOf(FlowInstanceResumedEvent.class);
    }
  }

  @Nested
  @DisplayName("isSubProcess 子流程判定")
  class SubProcessTest {

    @Test
    @DisplayName("parentInstanceId 非空且非空白为子流程")
    void subProcess_detection() {
      instance.setParentInstanceId("parent-inst-001");
      assertThat(instance.isSubProcess()).isTrue();
      instance.setParentInstanceId(null);
      assertThat(instance.isSubProcess()).isFalse();
      instance.setParentInstanceId("  ");
      assertThat(instance.isSubProcess()).isFalse();
    }
  }
}
