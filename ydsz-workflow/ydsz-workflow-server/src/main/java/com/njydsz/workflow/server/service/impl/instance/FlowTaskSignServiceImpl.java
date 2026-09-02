package com.njydsz.workflow.server.service.impl.instance;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.njydsz.common.core.code.YdszResultCode;
import com.njydsz.common.exception.custom.SysException;
import com.njydsz.workflow.domain.dto.FlowTaskOperateDTO;
import com.njydsz.workflow.domain.enums.FlowAssigneeType;
import com.njydsz.workflow.domain.enums.FlowPerformType;
import com.njydsz.workflow.domain.enums.FlowSignType;
import com.njydsz.workflow.domain.enums.FlowTaskStatus;
import com.njydsz.workflow.domain.repository.FlowRunTaskRepository;
import com.njydsz.workflow.domain.repository.FlowUserRepository;
import com.njydsz.workflow.domain.vo.FlowRunTaskVO;
import com.njydsz.workflow.domain.vo.FlowUserVO;

/**
 * 待办任务 — 加签减签 / 已阅 / 沟通 / 追加处理人 / 暂存待审 子服务实现
 *
 * <p>从原 {@code FlowTaskServiceImpl} 拆分出来的子服务，专注审批人<b>动态调整</b>与<b>轻量交互</b>职责，
 * 是工作流引擎「<b>灵活扩展</b>」能力的标准实现，加签减签方案。
 *
 * <p><b>核心职责：</b>
 *
 * <ul>
 *   <li><b>前加签（{@link #countersignBefore}）</b>：在当前节点前插入临时审批人，需先签后流
 *   <li><b>后加签（{@link #countersignAfter}）</b>：当前审批人通过后切换到加签人，加签人通过后才推进（顺序会签）
 *   <li><b>并加签（{@link #countersignParallel}）</b>：加签人与原审批人并行审批，全部通过才推进（并行会签）
 *   <li><b>减签（{@link #countersignRemove}）</b>：从会签任务中移除指定审批人，{@code approveCount} 减 1
 *   <li><b>追加处理人（{@link #addApprover}）</b>：在已有会签任务中追加一个审批人，<b>不改变</b>{@code performType}
 *   <li><b>已阅（{@link #markRead}）</b>：标记任务已阅，<b>不</b>改变任务状态，仅记录审计日志
 *   <li><b>沟通（{@link #communicate}）</b>：在任务下添加沟通评论，<b>不</b>改变任务状态
 *   <li><b>暂存待审（{@link #saveDraft}）</b>：审批人保存审批意见草稿到 {@code comment} 字段，<b>不</b>改变任务状态
 * </ul>
 *
 * <p><b>加签类型对比：</b>
 *
 * <table>
 *   <caption>加签类型差异</caption>
 *   <tr><th>类型</th><th>加签人位置</th><th>会签模式</th><th>典型场景</th></tr>
 *   <tr><td>前加签（{@code BEFORE}）</td><td>当前节点前</td><td>并行（当前人前）</td><td>需要专家先审</td></tr>
 *   <tr><td>后加签（{@code AFTER}）</td><td>当前节点后</td><td>并行（切换为 {@code PARALLEL}）</td><td>需要当前主管复核</td></tr>
 *   <tr><td>并加签（{@code PARALLEL}）</td><td>当前节点并行</td><td>并行（切换为 {@code PARALLEL}）</td><td>需要多部门会审</td></tr>
 *   <tr><td>追加处理人（{@code ADD}）</td><td>当前节点并行</td><td>保持原 {@code performType}</td><td>会签中临时增加审批人</td></tr>
 * </table>
 *
 * <p><b>事务边界：</b>所有写方法开启 {@code @Transactional(rollbackFor = Exception.class)}， 「审批人写入 + 任务更新 +
 * 审计日志 + 事件发布」原子性。
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li><b>审计追溯</b>：所有加签减签操作写入 {@code ydsz_flow_audit_log}，标注 {@code COUNTERSIGN_*} 类型
 *   <li><b>事件发布</b>：加签完成后触发 {@code onTaskCountersigned} 回调 + Spring 异步事件 {@code TASK_COUNTERSIGNED}
 *   <li><b>PC Web only</b>：依赖审批中心 UI（Element Plus 组件），根据项目硬约束仅支持 PC Web
 *   <li><b>状态校验</b>：所有加签减签操作要求任务处于<b>未完成</b>状态，已完成任务不可加签减签
 * </ul>
 *
 * <p><b>跨子服务共享：</b>任务校验 / 审计 / 事件能力委托给 {@link FlowTaskSupport}，避免代码重复。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see FlowTaskSupport 跨子服务共享辅助
 * @see FlowTaskOperateDTO 任务操作参数 DTO
 * @see FlowSignType 加签类型枚举
 * @see FlowPerformType 会签模式枚举
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowTaskSignServiceImpl {

  /** 运行时任务仓储，查询/更新加签减签的任务 */
  private final FlowRunTaskRepository taskRepository;

  /** 用户仓储，写入/查询流程用户 */
  private final FlowUserRepository userRepository;

  private final FlowTaskSupport support;

  // ============================== 加签（P1-7） ==============================

  /**
   * 前加签：在当前节点前插入临时审批人
   *
   * <p>前加签功能。执行链路：
   *
   * <ol>
   *   <li>查询任务并校验状态（<b>未完成</b>）
   *   <li>向 {@code ydsz_flow_user} 插入新审批人（{@code signType=BEFORE}）
   *   <li>{@code approveCount +1}（总应到人数 +1）
   *   <li>写审计日志 + 触发 {@code onTaskCountersigned} 回调 + Spring 异步事件
   * </ol>
   *
   * <p><b>注意：</b>前加签的实现当前仅插入新审批人记录，<b>未自动切换会签模式</b>， 适用于「单签场景下临时加签」。如需「多签场景下加签」，建议改用 {@link
   * #countersignParallel}。
   *
   * @param dto 操作参数（{@code taskId} / {@code userId} / {@code targetUserId} / {@code targetUserName}
   *     / {@code comment}）
   * @throws SysException {@code BAD_REQUEST} — 任务已完成 / 任务 ID 无效
   */
  @Transactional(rollbackFor = Exception.class)
  public void countersignBefore(FlowTaskOperateDTO dto) {
    FlowRunTaskVO task = support.getTaskOrThrow(dto.getTaskId());
    if (FlowTaskStatus.valueOf(task.getTaskStatus()).isFinished()) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("error.workflow.task.already.handled")
          .build();
    }
    // 前加签：在当前节点前插入临时审批人
    // 实现：为当前任务新增一个审批人记录到 ydsz_flow_user，approveCount+1
    if (dto.getTargetUserId() != null) {
      FlowUserVO fu = new FlowUserVO();
      fu.setTaskId(task.getId());
      fu.setInstanceId(task.getInstanceId());
      fu.setNodeCode(task.getNodeCode());
      fu.setUserType(FlowAssigneeType.USER.name());
      fu.setUserId(String.valueOf(dto.getTargetUserId()));
      fu.setUserName(dto.getTargetUserName());
      fu.setProcessed(0);
      fu.setWeight(1);
      fu.setSignType(FlowSignType.BEFORE.name());
      fu.setTenantId(task.getTenantId());
      fu.setProviderTraceId(task.getProviderTraceId());
      userRepository.save(fu);
      taskRepository.updateApproveFinished(task.getId(), task.getApproveFinished());
      // approveCount +1
      task.setApproveCount((task.getApproveCount() == null ? 0 : task.getApproveCount()) + 1);
      taskRepository.update(task);
    }
    support.audit(
        task, "COUNTERSIGN_BEFORE", dto.getUserId(), dto.getTargetUserId(), dto.getComment());
    log.info("[Flow] 前加签: taskId={} → 新增审批人={}", task.getId(), dto.getTargetUserId());
    // P2-34: 触发 onTaskCountersigned 事件
    support.fireEvent(
        l -> l.onTaskCountersigned(task.getId(), dto.getTargetUserId(), "BEFORE"), task.getId());
    // P2-35: 发布 Spring 异步事件
    support.publishWorkflowEvent("TASK_COUNTERSIGNED", task.getInstanceId(), task.getId());
  }

  /**
   * 后加签：在当前审批人通过后、下一节点前插入临时审批人
   *
   * <p>后加签功能。<b>关键实现：</b>
   *
   * <ol>
   *   <li>向 {@code ydsz_flow_user} 插入新审批人（{@code signType=AFTER}）
   *   <li><b>强制切换任务会签模式为 {@code PARALLEL}（并行会签）</b>，{@code approveCount +1}
   *   <li>当当前审批人和加签人都通过后才推进到下一节点
   * </ol>
   *
   * <p><b>适用场景：</b>需要「主管先审 → 专家再审 → 下一节点」的两段式审批。
   *
   * @param dto 操作参数（{@code taskId} / {@code userId} / {@code targetUserId} / {@code targetUserName}
   *     / {@code comment}）
   * @throws SysException {@code BAD_REQUEST} — 任务已完成 / 任务 ID 无效
   */
  @Transactional(rollbackFor = Exception.class)
  public void countersignAfter(FlowTaskOperateDTO dto) {
    FlowRunTaskVO task = support.getTaskOrThrow(dto.getTaskId());
    if (FlowTaskStatus.valueOf(task.getTaskStatus()).isFinished()) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("error.workflow.task.already.handled")
          .build();
    }
    // P2-29: 后加签真实实现 — 当前审批人通过后，新加签人需要审批，两人都通过后才推进到下一节点
    // 实现方式：
    // 1. 将当前任务切换为并行会签（performType=PARALLEL）
    // 2. approveCount +1（当前人 + 加签人）
    // 3. 新增审批人写入 ydsz_flow_user（processed=0）
    // 这样当前审批人和加签人都通过后才推进到下一节点
    if (dto.getTargetUserId() != null) {
      FlowUserVO fu = new FlowUserVO();
      fu.setTaskId(task.getId());
      fu.setInstanceId(task.getInstanceId());
      fu.setNodeCode(task.getNodeCode());
      fu.setUserType(FlowAssigneeType.USER.name());
      fu.setUserId(String.valueOf(dto.getTargetUserId()));
      fu.setUserName(dto.getTargetUserName());
      fu.setProcessed(0);
      fu.setWeight(1);
      fu.setSignType(FlowSignType.AFTER.name());
      fu.setTenantId(task.getTenantId());
      fu.setProviderTraceId(task.getProviderTraceId());
      userRepository.save(fu);
      // 切换为并行会签：当前人和加签人都通过后才推进
      task.setPerformType(FlowPerformType.PARALLEL.name());
      task.setApproveCount((task.getApproveCount() == null ? 0 : task.getApproveCount()) + 1);
      taskRepository.update(task);
    }
    support.audit(
        task, "COUNTERSIGN_AFTER", dto.getUserId(), dto.getTargetUserId(), dto.getComment());
    log.info("[Flow] 后加签: taskId={} → 新增审批人={} (切换为并行会签)", task.getId(), dto.getTargetUserId());
    // P2-34: 触发 onTaskCountersigned 事件
    support.fireEvent(
        l -> l.onTaskCountersigned(task.getId(), dto.getTargetUserId(), "AFTER"), task.getId());
    // P2-35: 发布 Spring 异步事件
    support.publishWorkflowEvent("TASK_COUNTERSIGNED", task.getInstanceId(), task.getId());
  }

  /**
   * GAP-P0-3: 并加签 — 动态追加审批人与原审批人并行审批，所有人审完后才推进。
   *
   * <p>并加签实现方式：
   *
   * <ol>
   * <li>向 ydsz_flow_user 插入新审批人（signType=PARALLEL，processed=0）
   * <li>approveCount +1
   * <li>强制切换 performType 为 PARALLEL —— 确保所有人全部通过才推进
   * </ol>
   *
   * 与后加签（PARALLEL 并行）不同，并加签的加签人与原审批人<b>同时</b>收到待办， 互不阻塞，全部审完后才推进到下一节点。
   *
   * @param dto 任务操作 DTO（含 taskId/targetUserId 等）
   */
  @Transactional(rollbackFor = Exception.class)
  public void countersignParallel(FlowTaskOperateDTO dto) {
    FlowRunTaskVO task = support.getTaskOrThrow(dto.getTaskId());
    if (FlowTaskStatus.valueOf(task.getTaskStatus()).isFinished()) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("error.workflow.task.already.handled")
          .build();
    }
    if (dto.getTargetUserId() == null) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("error.workflow.sign.target.user.required")
          .build();
    }
    FlowUserVO fu = new FlowUserVO();
    fu.setTaskId(task.getId());
    fu.setInstanceId(task.getInstanceId());
    fu.setNodeCode(task.getNodeCode());
    fu.setUserType(FlowAssigneeType.USER.name());
    fu.setUserId(String.valueOf(dto.getTargetUserId()));
    fu.setUserName(dto.getTargetUserName());
    fu.setProcessed(0);
    fu.setWeight(1);
    fu.setSignType(FlowSignType.PARALLEL.name());
    fu.setTenantId(task.getTenantId());
    fu.setProviderTraceId(task.getProviderTraceId());
    userRepository.save(fu);
    // 强制切换为并行会签：加签人与原审批人并行审批，所有人全部通过才推进
    task.setPerformType(FlowPerformType.PARALLEL.name());
    task.setApproveCount((task.getApproveCount() == null ? 0 : task.getApproveCount()) + 1);
    taskRepository.update(task);
    support.audit(
        task, "COUNTERSIGN_PARALLEL", dto.getUserId(), dto.getTargetUserId(), dto.getComment());
    log.info("[Flow] 并加签: taskId={} → 新增审批人={} (切换为并行会签)", task.getId(), dto.getTargetUserId());
    support.fireEvent(
        l -> l.onTaskCountersigned(task.getId(), dto.getTargetUserId(), "PARALLEL"), task.getId());
    support.publishWorkflowEvent("TASK_COUNTERSIGNED", task.getInstanceId(), task.getId());
  }

  // ============================== GAP-P1: 减签 ==============================

  /**
   * 减签：从会签任务中移除指定审批人
   *
   * <p>减签功能。执行链路：
   *
   * <ol>
   *   <li>校验任务状态（<b>未完成</b>）
   *   <li>从 {@code ydsz_flow_user} 中按 {@code (instanceId, nodeCode, userId)} 复合键删除
   *   <li>{@code approveCount -1}（不低于 1，避免除零）
   *   <li>写审计日志 + 触发事件
   * </ol>
   *
   * <p><b>幂等性：</b>被减签人不存在时抛 {@code NOT_FOUND} 异常，<b>不</b>做静默处理， 由调用方决定是否忽略。
   *
   * @param dto 操作参数（{@code taskId} / {@code userId} 为操作人 / {@code targetUserId} 为被减签人）
   * @throws SysException {@code BAD_REQUEST} — 任务已完成 / 缺少被减签人 ID； {@code NOT_FOUND} — 被减签人不存在
   */
  @Transactional(rollbackFor = Exception.class)
  public void countersignRemove(FlowTaskOperateDTO dto) {
    FlowRunTaskVO task = support.getTaskOrThrow(dto.getTaskId());
    if (FlowTaskStatus.valueOf(task.getTaskStatus()).isFinished()) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("error.workflow.task.already.handled")
          .build();
    }
    if (dto.getTargetUserId() == null) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("error.workflow.sign.target.user.required")
          .build();
    }
    // 从 ydsz_flow_user 中删除指定用户
    int deleted = userRepository.deleteByInstanceAndNodeAndUser(
        task.getInstanceId(), task.getNodeCode(), String.valueOf(dto.getTargetUserId()));
    if (deleted == 0) {
      throw SysException.builder()
          .resultCode(YdszResultCode.NOT_FOUND)
          .key("error.workflow.sign.user.not.found")
          .params(dto.getTargetUserId())
          .build();
    }
    // approveCount -1，但不低于 1
    int currentCount = task.getApproveCount() == null ? 1 : task.getApproveCount();
    task.setApproveCount(Math.max(1, currentCount - 1));
    taskRepository.update(task);
    support.audit(
        task, "COUNTERSIGN_REMOVE", dto.getUserId(), dto.getTargetUserId(), dto.getComment());
    log.info(
        "[Flow] 减签: taskId={} → 移除审批人={} deleted={}", task.getId(), dto.getTargetUserId(), deleted);
    support.fireEvent(
        l -> l.onTaskCountersigned(task.getId(), dto.getTargetUserId(), "REMOVE"), task.getId());
    support.publishWorkflowEvent("TASK_COUNTERSIGNED", task.getInstanceId(), task.getId());
  }

  // ============================== GAP-P2: 已阅/沟通 ==============================

  /**
   * 已阅：标记任务已阅（<b>不改变</b>任务状态，仅记录审计日志）
   *
   * <p>已阅功能。适用于「审批人收到待办后先查看详情，但暂不操作」的场景。 区别于「标记已读」（消息中心），这里是「任务已阅」（审批中心）。
   *
   * @param taskId 任务 ID
   * @param userId 操作人 ID
   * @throws SysException {@code NOT_FOUND} — 任务 ID 无效
   */
  @Transactional(rollbackFor = Exception.class)
  public void markRead(String taskId, String userId) {
    FlowRunTaskVO task = support.getTaskOrThrow(taskId);
    support.audit(task, "READ", userId, null, null);
    log.info("[Flow] 已阅: taskId={} userId={}", taskId, userId);
  }

  /**
   * 沟通：在任务下添加沟通评论（<b>不改变</b>任务状态）
   *
   * <p>区别于「审批意见」（{@code comment} 用于 pass/reject 操作），沟通是审批过程中的<b>非正式</b>交流， 写入审计日志的 {@code COMMENT}
   * 字段，类型由 {@code commentType} 区分（如 {@code @mention}）。
   *
   * @param dto 操作参数（{@code taskId} / {@code userId} / {@code comment} / {@code commentType}）
   * @throws SysException {@code NOT_FOUND} — 任务 ID 无效
   */
  @Transactional(rollbackFor = Exception.class)
  public void communicate(FlowTaskOperateDTO dto) {
    FlowRunTaskVO task = support.getTaskOrThrow(dto.getTaskId());
    support.audit(
        task, "COMMUNICATE", dto.getUserId(), null, dto.getComment(), dto.getCommentType());
    log.info(
        "[Flow] 沟通: taskId={} userId={} comment={}",
        dto.getTaskId(),
        dto.getUserId(),
        dto.getComment());
  }

  // ======================== P0-03: 暂存待审 / 追加处理人 ========================

  /**
   * 暂存待审：审批人保存审批意见草稿（<b>不改变</b>任务主状态）
   *
   * <p>暂存功能。审批人可以在不确定是否通过 / 驳回时， 先填写意见保存为草稿，事后再次打开任务时自动回填意见，避免重复填写。
   *
   * <p>任务状态保持 {@code PENDING/CLAIMED} 不变，写审计日志记录 {@code SAVE_DRAFT} 操作。
   *
   * @param dto 操作参数（{@code taskId} / {@code userId} / {@code comment} / {@code commentType}）
   * @throws SysException {@code BAD_REQUEST} — 任务已完成； {@code NOT_FOUND} — 任务 ID 无效
   */
  @Transactional(rollbackFor = Exception.class)
  public void saveDraft(FlowTaskOperateDTO dto) {
    FlowRunTaskVO task = support.getTaskOrThrow(dto.getTaskId());
    if (FlowTaskStatus.valueOf(task.getTaskStatus()).isFinished()) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("error.workflow.task.already.handled")
          .build();
    }
    // 保存审批意见草稿到 comment 字段，不改变任务状态
    task.setComment(dto.getComment());
    taskRepository.update(task);
    support.audit(
        task, "SAVE_DRAFT", dto.getUserId(), null, dto.getComment(), dto.getCommentType());
    log.info("[Flow] 暂存待审: taskId={} userId={}", dto.getTaskId(), dto.getUserId());
  }

  /**
   * 追加处理人：在已有会签任务中追加一个审批人
   *
   * <p>追加处理人功能。区别于「并加签」：
   *
   * <ul>
   *   <li>追加处理人：<b>不改变</b>{@code performType}，适用于「原会签模式追加」
   *   <li>并加签：<b>强制切换为</b>{@code PARALLEL}，无论原模式是什么
   * </ul>
   *
   * <p>执行链路：
   *
   * <ol>
   *   <li>校验任务状态（<b>未完成</b>）
   *   <li>向 {@code ydsz_flow_user} 插入新审批人（{@code signType=ADD}，{@code weight=1}）
   *   <li>{@code approveCount +1}
   *   <li>写审计日志 + 触发事件
   * </ol>
   *
   * @param dto 操作参数（{@code taskId} / {@code userId} 为操作人 / {@code targetUserId} / {@code
   *     targetUserName}）
   * @throws SysException {@code BAD_REQUEST} — 任务已完成 / 缺少目标用户 ID
   */
  @Transactional(rollbackFor = Exception.class)
  public void addApprover(FlowTaskOperateDTO dto) {
    FlowRunTaskVO task = support.getTaskOrThrow(dto.getTaskId());
    if (FlowTaskStatus.valueOf(task.getTaskStatus()).isFinished()) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("error.workflow.task.already.handled")
          .build();
    }
    if (dto.getTargetUserId() == null) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("error.workflow.sign.target.user.required")
          .build();
    }
    // 向 ydsz_flow_user 插入新审批人
    FlowUserVO fu = new FlowUserVO();
    fu.setTaskId(task.getId());
    fu.setInstanceId(task.getInstanceId());
    fu.setNodeCode(task.getNodeCode());
    fu.setUserType(FlowAssigneeType.USER.name());
    fu.setUserId(String.valueOf(dto.getTargetUserId()));
    fu.setUserName(dto.getTargetUserName());
    fu.setProcessed(0);
    fu.setWeight(1); // 默认权重 1
    fu.setSignType(FlowSignType.ADD.name());
    fu.setTenantId(task.getTenantId());
    fu.setProviderTraceId(task.getProviderTraceId());
    userRepository.save(fu);
    // approveCount +1
    int currentCount = task.getApproveCount() == null ? 1 : task.getApproveCount();
    task.setApproveCount(currentCount + 1);
    taskRepository.update(task);
    support.audit(task, "ADD_APPROVER", dto.getUserId(), dto.getTargetUserId(), dto.getComment());
    log.info("[Flow] 追加处理人: taskId={} targetUserId={}", task.getId(), dto.getTargetUserId());
    support.fireEvent(
        l -> l.onTaskCountersigned(task.getId(), dto.getTargetUserId(), "ADD"), task.getId());
    support.publishWorkflowEvent("TASK_ADD_APPROVER", task.getInstanceId(), task.getId());
  }
}
