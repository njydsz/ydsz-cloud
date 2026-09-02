package com.njydsz.workflow.server.service.impl.instance;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import com.njydsz.common.core.code.YdszResultCode;
import com.njydsz.common.exception.custom.SysException;
import com.njydsz.workflow.domain.repository.FlowAuditLogRepository;
import com.njydsz.workflow.domain.repository.FlowRunTaskRepository;
import com.njydsz.workflow.domain.vo.FlowAuditLogVO;
import com.njydsz.workflow.domain.vo.FlowRunTaskVO;
import com.njydsz.workflow.server.engine.FlowEventContext;
import com.njydsz.workflow.server.engine.FlowEventListener;
import com.njydsz.workflow.server.engine.FlowSensitiveMasker;
import com.njydsz.workflow.server.engine.FlowWorkflowEvent;
import com.njydsz.workflow.server.engine.listener.FlowListenerConfigReader;
import com.njydsz.workflow.server.engine.listener.FlowListenerEventType;
import com.njydsz.workflow.server.engine.listener.FlowListenerPluginExecutor;

/**
 * FlowTask 跨子 Service 共享的辅助组件（任务校验、审计、事件）
 *
 * <p>从原 {@code FlowTaskServiceImpl}（单体实现 1847 行）拆分而来，承担所有 FlowTask 子 Service （{@link
 * FlowTaskQueryServiceImpl} / {@link FlowTaskCompleteServiceImpl} / {@link FlowTaskSignServiceImpl}
 * / {@link FlowTaskBatchServiceImpl}）<b>共享</b>的工具方法， 避免跨子 Service 代码重复，提升复用性。
 *
 * <p><b>拆分原则：</b>
 *
 * <ul>
 *   <li>仅纳入被 <b>2 个及以上</b> 子 Service 引用的方法；单一子 Service 专用的私有方法 仍保留在对应子 Service 内部
 *   <li>本类<b>不依赖任何业务子 Service</b>，避免循环依赖
 *   <li>本类<b>不开启事务</b>（{@code @Transactional} 缺失），所有方法由调用方决定事务边界
 * </ul>
 *
 * <p><b>核心职责：</b>
 *
 * <ul>
 *   <li><b>任务校验</b>：{@link #getTaskOrThrow} — 按主键查任务，缺失时抛 {@link SysException}（{@code NOT_FOUND}）
 *   <li><b>审计日志写入</b>：{@link #audit} — 提供无意见分类 / 带意见分类（{@code AGREE/DISAGREE/SUGGEST/INQUIRE}）
 *       两个重载，统一审计日志格式与脱敏处理
 *   <li><b>事件触发</b>：{@link #fireEvent} — 遍历执行所有 {@link FlowEventListener}，
 *       <b>单监听器失败不影响整体</b>（try-catch 吞掉）
 *   <li><b>Spring 事件发布</b>：{@link #publishWorkflowEvent} — 通过 {@link ApplicationEventPublisher}
 *       异步发布 {@link FlowWorkflowEvent}，支持跨模块解耦
 * </ul>
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li><b>异常降级</b>：所有方法均 try-catch 兜底（除 {@link #getTaskOrThrow}）， 单点失败不影响主流程事务
 *   <li><b>敏感数据脱敏</b>：审计日志写入前通过 {@link FlowSensitiveMasker#mask} 对 {@code comment} 脱敏，避免手机号 / 身份证 /
 *       银行卡等敏感信息写入审计日志
 *   <li><b>ApplicationEventPublisher 可空</b>：测试环境可能未注入， {@link #publishWorkflowEvent} 内做空检查避免 NPE
 *   <li><b>事件监听器空安全</b>：{@link #fireEvent} 对 {@code eventListeners} 列表做空检查， 避免 NPE
 * </ul>
 *
 * <p><b>调用方：</b>
 *
 * <pre>
 *   FlowTaskCompleteServiceImpl ─┐
 *   FlowTaskSignServiceImpl     ─┼─→ FlowTaskSupport (本类)
 *   FlowTaskBatchServiceImpl    ─┤
 *   FlowTaskQueryServiceImpl    ─┘
 * </pre>
 *
 * <p><b>典型使用：</b>
 *
 * <pre>{@code
 * // 1. 任务校验（子 Service 内）
 * FlowRunTaskVO task = flowTaskSupport.getTaskOrThrow(taskId);
 *
 * // 2. 审计日志（PASS 操作）
 * flowTaskSupport.audit(task, "PASS", operatorId, task.getAssigneeId(),
 *         "同意，原因：符合规范", "AGREE");
 *
 * // 3. 触发监听器
 * flowTaskSupport.fireEvent(listener -> listener.onTaskPass(task, operator), taskId);
 *
 * // 4. 发布 Spring 事件
 * flowTaskSupport.publishWorkflowEvent("TASK_PASS", task.getInstanceId(), taskId);
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see FlowTaskServiceImpl FlowTask 门面（拆分入口）
 * @see FlowRunTaskVO 运行时任务视图对象
 * @see FlowAuditLogVO 审计日志视图对象
 * @see FlowEventListener 事件监听器 SPI
 * @see FlowWorkflowEvent Spring 异步事件
 * @see FlowSensitiveMasker 敏感数据脱敏器
 * @see SysException 业务异常
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FlowTaskSupport {

  // ============================== 依赖注入 ==============================

  /** 运行时任务仓储，负责 {@code ydsz_flow_run_task} 表的查询 / 更新 / 状态扭转 */
  private final FlowRunTaskRepository taskRepository;

  /** 审计日志仓储，负责 {@code ydsz_flow_audit_log} 表的写入，承载任务操作审计轨迹 */
  private final FlowAuditLogRepository auditLogRepository;

  /**
   * 事件监听器列表（Spring 自动注入所有 {@link FlowEventListener} 实现）
   *
   * <p>工作流引擎的 SPI 扩展点：业务方实现 {@link FlowEventListener} 即可订阅 任务生命周期事件（创建 / 通过 / 驳回 / 转办 / 委派 / 加签 /
   * 撤回等）。
   */
  private final List<FlowEventListener> eventListeners;

  /**
   * P2-38: 监听器插件执行器（设计器配置的节点级监听器）
   *
   * <p>监听器机制：在 SPI 事件监听器之外，设计上，每个节点可通过
   * ext JSON 配置自己的监听器，引擎按事件类型自动回调。
   */
  private final FlowListenerPluginExecutor listenerPluginExecutor;

  /**
   * P2-35: Spring 事件发布器
   *
   * <p>用于异步发布 {@link FlowWorkflowEvent} 给跨模块监听器（{@code ydsz-message} 通知模块、 {@code ydsz-audit}
   * 审计模块等），通过 {@code @EventListener} 或 {@code @TransactionalEventListener} 订阅。
   *
   * <p><b>可空</b>：测试环境可能未注入，本类内做空检查避免 NPE。
   */
  private final ApplicationEventPublisher eventPublisher;

  /**
   * P0-1: 敏感字段脱敏器
   *
   * <p>对审计日志的 {@code comment} 字段进行脱敏，避免手机号 / 身份证 / 银行卡等 敏感信息通过审计日志泄露。
   */
  private final FlowSensitiveMasker sensitiveMasker;

  // ============================== 任务校验 ==============================

  /**
   * 按主键查询任务，任务不存在时抛出业务异常
   *
   * <p>子 Service 在执行任何写操作前必须先调用本方法获取任务，避免在「任务不存在」 的情况下误更新其他数据。本方法是子 Service 的「准入校验」入口。
   *
   * @param id 任务主键 ID（雪花算法生成的字符串）
   * @return 运行时任务视图对象（{@link FlowRunTaskVO}），一定非空
   * @throws SysException 当任务不存在时抛出，错误码 {@code NOT_FOUND}， 错误信息 key 为 {@code
   *     error.workflow.task.not.found}（i18n 资源键）
   */
  public FlowRunTaskVO getTaskOrThrow(String id) {
    FlowRunTaskVO task = taskRepository.findById(id).orElse(null);
    if (task == null) {
      throw SysException.builder()
          .resultCode(YdszResultCode.NOT_FOUND)
          .key("error.workflow.task.not.found")
          .params(id)
          .build();
    }
    return task;
  }

  // ============================== 审计日志 ==============================

  /**
   * 写审计日志（无意见分类）
   *
   * <p>适用于无需区分意见类型的操作（如「转办 / 委派 / 加签 / 撤回 / 催办」等 流程性操作），内部委托 {@link #audit(FlowRunTaskVO, String,
   * String, String, String, String)}。
   *
   * @param task 任务实体（用于提取 instanceId / nodeCode / tenantId 等上下文）
   * @param action 操作类型（如 {@code PASS/REJECT/TRANSFER/DELEGATE/URGE/CANCEL}）
   * @param operatorId 操作人 ID（执行当前操作的用户）
   * @param targetId 目标人 ID（如转办的目标人 / 委派的代理人，PASS 时为任务 assignee）
   * @param comment 审批意见 / 操作备注（自动脱敏）
   */
  public void audit(
      FlowRunTaskVO task, String action, String operatorId, String targetId, String comment) {
    audit(task, action, operatorId, targetId, comment, null);
  }

  /**
   * P2-42: 审计日志写入（带意见分类）
   *
   * <p>适用于「通过 / 驳回」类需要明确意见分类的操作，便于后续做审批行为分析。 本方法<b>不抛异常</b>，写入失败仅记录 warn 日志，避免审计失败影响主流程。
   *
   * <p><b>审计字段：</b>
   *
   * <ul>
   *   <li>instanceId / taskId / flowCode / businessType / businessId — 从 {@code task} 提取
   *   <li>nodeCode / nodeName — 任务所属节点
   *   <li>action — 操作类型（PASS/REJECT/TRANSFER/DELEGATE/URGE/CANCEL 等）
   *   <li>operatorId / targetId — 操作人 / 目标人
   *   <li>comment — 审批意见（<b>经脱敏处理</b>）
   *   <li>commentType — 意见分类（{@code AGREE/DISAGREE/SUGGEST/INQUIRE}）
   *   <li>operatedAt — 操作时间
   *   <li>tenantId / providerTraceId — 租户 / 链路追踪 ID
   * </ul>
   *
   * @param task 任务实体
   * @param action 操作类型
   * @param operatorId 操作人 ID
   * @param targetId 目标人 ID
   * @param comment 审批意见（自动脱敏）
   * @param commentType 意见分类：{@code AGREE}（同意）/ {@code DISAGREE}（不同意）/ {@code SUGGEST}（建议）/ {@code
   *     INQUIRE}（询问），可空
   */
  public void audit(
      FlowRunTaskVO task,
      String action,
      String operatorId,
      String targetId,
      String comment,
      String commentType) {
    try {
      FlowAuditLogVO log = new FlowAuditLogVO();
      log.setInstanceId(task.getInstanceId());
      log.setTaskId(task.getId());
      log.setFlowCode(task.getFlowCode());
      log.setBusinessType(task.getBusinessType());
      log.setBusinessId(task.getBusinessId());
      log.setNodeCode(task.getNodeCode());
      log.setNodeName(task.getNodeName());
      log.setAction(action);
      log.setOperatorId(operatorId);
      log.setTargetId(targetId);
      log.setComment(sensitiveMasker.mask(comment));
      log.setCommentType(commentType);
      log.setOperatedAt(LocalDateTime.now());
      log.setTenantId(task.getTenantId());
      log.setProviderTraceId(task.getProviderTraceId());
      auditLogRepository.save(log);
    } catch (Exception e) {
      FlowTaskSupport.log.warn("[Flow] 审计日志写入失败: {}", e.getMessage());
    }
  }

  // ============================== 事件 ==============================

  /**
   * 触发所有事件监听器（吞异常，避免单监听器失败影响主流程）
   *
   * <p>遍历 {@link #eventListeners} 列表，依次调用 {@code action} 回调执行业务逻辑。 单个监听器抛出异常时<b>仅记录 warn
   * 日志</b>，<b>不影响其他监听器执行</b>， 也不影响主流程事务。
   *
   * <p><b>典型用法：</b>
   *
   * <pre>{@code
   * // 通知所有监听器「任务通过」
   * flowTaskSupport.fireEvent(listener -> listener.onTaskPass(task, operatorId), taskId);
   * }</pre>
   *
   * @param action 监听器动作（{@link Consumer} 形式，执行业务逻辑）
   * @param taskId 任务 ID（仅用于日志，可空）
   */
  public void fireEvent(Consumer<FlowEventListener> action, String taskId) {
    if (eventListeners == null) {
      return;
    }
    for (FlowEventListener listener : eventListeners) {
      try {
        action.accept(listener);
      } catch (Exception e) {
        log.warn(
            "[Flow] 事件监听器异常: listener={} err={}",
            listener.getClass().getSimpleName(),
            e.getMessage());
      }
    }
  }

  /**
   * P2-38: 触发节点配置的监听器插件
   *
   * <p>从节点 ext JSON 的 {@code listeners} 配置中，筛选匹配当前事件类型的插件，按优先级依次执行。
   *
   * @param nodeExt  节点 ext JSON 字符串
   * @param eventType 事件类型
   * @param instanceId 流程实例 ID
   * @param taskId    任务 ID（可空）
   * @param nodeCode  节点编码（可空）
   * @param variables 流程变量（可空）
   * @param ctx       事件上下文（可空）
   */
  public void firePluginEvent(
      String nodeExt,
      FlowListenerEventType eventType,
      String instanceId,
      String taskId,
      String nodeCode,
      Map<String, Object> variables,
      FlowEventContext ctx) {
    if (listenerPluginExecutor == null) {
      return;
    }
    try {
      listenerPluginExecutor.execute(
          FlowListenerConfigReader.readListeners(nodeExt),
          eventType, instanceId, taskId, nodeCode, variables, ctx);
    } catch (Exception e) {
      log.warn("[Flow] 监听器插件执行失败: node={} event={} err={}",
          nodeCode, eventType, e.getMessage());
    }
  }

  /**
   * P2-35: 发布 Spring 异步事件
   *
   * <p>通过 {@link ApplicationEventPublisher} 发布 {@link FlowWorkflowEvent} 事件， 供跨模块监听器订阅（如 {@code
   * ydsz-message} 通知模块的站内信 / 邮件推送、 {@code ydsz-audit} 审计模块的额外审计字段写入等）。
   *
   * <p><b>事务边界：</b>事件发布不抛异常，发布失败仅记录 warn 日志， 不影响主流程事务（事务回滚不影响事件订阅者）。
   *
   * <p><b>空安全：</b>{@link ApplicationEventPublisher} 在测试环境可能为 null， 本方法内做空检查避免 NPE。
   *
   * @param eventType 事件类型（如 {@code TASK_PASS/TASK_REJECT/INSTANCE_COMPLETE}）
   * @param instanceId 实例 ID（可空）
   * @param taskId 任务 ID（可空）
   */
  public void publishWorkflowEvent(String eventType, String instanceId, String taskId) {
    if (eventPublisher == null) {
      return;
    }
    try {
      eventPublisher.publishEvent(new FlowWorkflowEvent(this, eventType, instanceId, taskId, null));
    } catch (Exception e) {
      log.warn("[Flow] 发布 Spring 事件失败: type={} err={}", eventType, e.getMessage());
    }
  }
}
