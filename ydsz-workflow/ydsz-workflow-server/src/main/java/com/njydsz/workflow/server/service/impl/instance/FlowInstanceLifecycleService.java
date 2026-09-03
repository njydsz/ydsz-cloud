package com.njydsz.workflow.server.service.impl.instance;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.njydsz.common.core.code.YdszResultCode;
import com.njydsz.common.exception.custom.SysException;
import com.njydsz.common.feign.assembler.NameAssembler;
import com.njydsz.common.json.YdszJson;
import com.njydsz.workflow.domain.dto.FlowStartProcessDTO;
import com.njydsz.workflow.domain.repository.FlowAuditLogRepository;
import com.njydsz.workflow.domain.repository.FlowHisTaskRepository;
import com.njydsz.workflow.domain.repository.FlowInstanceRepository;
import com.njydsz.workflow.domain.repository.FlowNodeRepository;
import com.njydsz.workflow.domain.repository.FlowRunTaskRepository;
import com.njydsz.workflow.domain.vo.FlowInstanceVO;
import com.njydsz.workflow.server.engine.impl.DefaultFlowAdvancer;
import com.njydsz.workflow.server.metrics.FlowMetrics;
import com.njydsz.workflow.server.service.FlowAutoTriggerService;
import com.njydsz.workflow.server.service.FlowCcService;
import com.njydsz.workflow.server.service.FlowDefinitionService;
import com.njydsz.workflow.server.service.FlowEventSubscriptionService;
import com.njydsz.workflow.server.service.FlowSubProcessService;
import com.njydsz.workflow.server.service.FlowTaskService;
import com.njydsz.workflow.server.service.FlowTimerService;

/**
 * 流程实例生命周期服务
 *
 * <p>负责流程实例的完整生命周期管理，包含<b>启动、终止、挂起、激活、变量管理、批量操作</b>等所有写操作（带 {@code @Transactional}）。
 *
 * <p><b>核心职责：</b>
 *
 * <ul>
 *   <li><b>启动</b>：{@link #startInstance} — 创建实例并推进到开始节点
 *   <li><b>终止</b>：{@link #terminateInstance} — 强制终止实例
 *   <li><b>挂起/激活</b>：{@link #suspendInstance} / {@link #activateInstance} — 冻结/恢复实例
 *   <li><b>变量管理</b>：{@link #updateVariables} / {@link #getVariables} — 读取/写入流程变量
 *   <li><b>批量操作</b>：{@link #batchStartInstances} / {@link #batchTerminate} — 批量启动/终止实例
 *   <li><b>完成</b>：{@link #complete} — 推进到结束节点
 *   <li><b>撤回</b>：{@link #recall} — 撤回到开始节点或指定历史节点
 *   <li><b>回滚</b>：{@link #rollback} — 撤销已完成的实例
 *   <li><b>重审</b>：{@link #resubmit} — 驳回后快速重审
 * </ul>
 *
 * <p><b>事务边界：</b>所有写方法开启 {@code @Transactional(rollbackFor = Exception.class)}，
 * 确保「实例 + 任务 + 审计日志 + 事件」原子性。
 *
 * <p><b>并发控制：</b>关键操作通过 {@link com.njydsz.common.lock.annotation.YdszDistributedLock} 注解保护。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Service
public class FlowInstanceLifecycleService extends AbstractFlowInstanceLifecycle {

  /**
   * 构造函数
   *
   * @param instanceRepository 流程实例仓储
   * @param definitionService 流程定义服务
   * @param advancer 流程推进引擎
   * @param taskService 流程任务服务
   * @param taskRepository 运行时任务仓储
   * @param nodeRepository 流程节点仓储
   * @param flowMetrics Prometheus 指标
   * @param flowTaskSupport 事件支持组件
   * @param subProcessService 子流程服务
   * @param ccService 抄送服务
   * @param autoTriggerService 自动触发服务
   * @param eventSubscriptionService 事件订阅服务
   * @param auditLogRepository 审计日志仓储
   * @param hisTaskRepository 历史任务仓储
   * @param timerService 定时器服务
   * @param nameAssembler 名称解析门面
   */
  public FlowInstanceLifecycleService(
      FlowInstanceRepository instanceRepository,
      FlowDefinitionService definitionService,
      DefaultFlowAdvancer advancer,
      FlowTaskService taskService,
      FlowRunTaskRepository taskRepository,
      FlowNodeRepository nodeRepository,
      FlowMetrics flowMetrics,
      FlowTaskSupport flowTaskSupport,
      FlowSubProcessService subProcessService,
      FlowCcService ccService,
      FlowAutoTriggerService autoTriggerService,
      FlowEventSubscriptionService eventSubscriptionService,
      FlowAuditLogRepository auditLogRepository,
      FlowHisTaskRepository hisTaskRepository,
      FlowTimerService timerService,
      NameAssembler nameAssembler) {
    super(
        instanceRepository,
        definitionService,
        advancer,
        taskService,
        taskRepository,
        nodeRepository,
        flowMetrics,
        flowTaskSupport,
        subProcessService,
        ccService,
        autoTriggerService,
        eventSubscriptionService,
        auditLogRepository,
        hisTaskRepository,
        timerService,
        nameAssembler);
  }

  // ============================== 子类策略实现 ==============================

  @Override
  protected Map<String, Object> parseVariables(String variable) {
    if (!StringUtils.hasText(variable)) {
      return new HashMap<>(0);