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
 * @since 1.0.0
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
      return new HashMap<>();
    }
    try {
      Map<String, Object> map = YdszJson.parseMap(variable);
      return map == null ? new HashMap<>() : map;
    } catch (Exception e) {
      log.warn("[Flow] 解析 variable JSON 失败，返回空 Map: {}", e.getMessage());
      return new HashMap<>();
    }
  }

  @Override
  @Transactional(readOnly = true)
  protected Map<String, Object> getVariables(String instanceId) {
    FlowInstanceVO instance = instanceRepository.findById(instanceId).orElse(null);
    if (instance == null || !StringUtils.hasText(instance.getVariable())) {
      return Collections.emptyMap();
    }
    try {
      Map<String, Object> map = YdszJson.parseMap(instance.getVariable());
      return map == null ? Collections.emptyMap() : map;
    } catch (Exception e) {
      log.warn("[Flow] 解析 variable JSON 失败: instanceId={} err={}", instanceId, e.getMessage());
      return Collections.emptyMap();
    }
  }

  @Override
  protected FlowInstanceVO saveInstance(FlowInstanceVO instance) {
    return instanceRepository.save(toDto(instance));
  }

  // ============================== 委托方法（保持向后兼容） ==============================

  /**
   * 启动流程实例
   *
   * @param dto 启动参数 DTO
   * @return 流程实例 ID
   */
  public String startInstance(FlowStartProcessDTO dto) {
    return doStartInstance(dto);
  }

  /**
   * 终止流程实例
   *
   * @param instanceId 实例 ID
   * @param reason 终止原因
   */
  public void terminateInstance(String instanceId, String reason) {
    doTerminateInstance(instanceId, reason);
  }

  /**
   * 挂起流程实例
   *
   * @param instanceId 实例 ID
   */
  public void suspendInstance(String instanceId) {
    doSuspendInstance(instanceId);
  }

  /**
   * 激活流程实例
   *
   * @param instanceId 实例 ID
   */
  public void activateInstance(String instanceId) {
    doActivateInstance(instanceId);
  }

  /**
   * 强制完成（推进到结束节点）
   *
   * @param instanceId 实例 ID
   * @param endNodeCode 终止节点编码
   */
  public void complete(String instanceId, String endNodeCode) {
    doCompleteInstance(instanceId, endNodeCode);
  }

  /**
   * 撤回到指定历史节点
   *
   * @param instanceId 实例 ID
   * @param initiatorId 发起人 ID
   * @param targetNodeCode 目标节点编码
   * @return 是否撤回成功
   */
  public boolean recall(String instanceId, String initiatorId, String targetNodeCode) {
    return doRecallInstance(instanceId, initiatorId, targetNodeCode);
  }

  /**
   * 撤回流程（回退到开始节点的下一节点）
   *
   * @param instanceId 实例 ID
   * @param initiatorId 发起人 ID
   * @return 是否撤回成功
   */
  public boolean recall(String instanceId, String initiatorId) {
    return doRecallInstance(instanceId, initiatorId);
  }

  /**
   * 回滚已完成的流程实例
   *
   * @param instanceId 实例 ID
   * @param operatorId 操作人 ID
   * @param reason 回滚原因
   * @param maxRollbackDays 允许回滚的最大天数
   * @return 是否回滚成功
   */
  public boolean rollback(
      String instanceId, String operatorId, String reason, int maxRollbackDays) {
    return doRollbackInstance(instanceId, operatorId, reason, maxRollbackDays);
  }

  /**
   * 驳回后快速重审
   *
   * @param instanceId 被驳回的实例 ID
   * @param initiatorId 发起人 ID
   * @param variables 重审时新增/覆盖的变量
   * @param comment 重审说明
   * @return 实例 ID
   */
  public String resubmit(
      String instanceId, String initiatorId, Map<String, Object> variables, String comment) {
    return doResubmitInstance(instanceId, initiatorId, variables, comment);
  }

  /**
   * 流程重做 — 支持 redoMode 指定重做策略
   *
   * @param instanceId 原实例 ID
   * @param initiatorId 发起人 ID
   * @param variables 重做时新增/覆盖的变量
   * @param comment 重做说明
   * @param redoMode 重做模式：RESTART / NEW_INSTANCE
   * @return 实例 ID
   */
  public String resubmit(
      String instanceId,
      String initiatorId,
      Map<String, Object> variables,
      String comment,
      String redoMode) {
    return doResubmitInstance(instanceId, initiatorId, variables, comment, redoMode);
  }

  /**
   * 设置实例的 dueAt 字段
   *
   * @param instanceId 实例 ID
   * @param dueAt 超时时间
   */
  public void setDueAt(String instanceId, LocalDateTime dueAt) {
    doSetDueAt(instanceId, dueAt);
  }

  // ============================== 变量管理（Service 独有） ==============================

  /**
   * P2-24: 合并写入单个变量并持久化
   *
   * @param instanceId 实例 ID
   * @param key 变量名
   * @param value 变量值
   */
  @Transactional(rollbackFor = Exception.class)
  public void updateVariables(String instanceId, String key, Object value) {
    if (!StringUtils.hasText(key)) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("error.workflow.instance.variable.key.required")
          .build();
    }
    FlowInstanceVO instance = instanceRepository.findById(instanceId).orElse(null);
    if (instance == null) {
      throw SysException.builder()
          .resultCode(YdszResultCode.NOT_FOUND)
          .key("error.workflow.instance.not.found")
          .params(instanceId)
          .build();
    }
    Map<String, Object> map = parseVariables(instance.getVariable());
    map.put(key, value);
    instanceRepository.updateVariable(instanceId, YdszJson.toJson(map));
    log.info("[Flow] 设置变量: instanceId={} key={}", instanceId, key);
  }

  /**
   * P2-24: 批量合并写入变量并持久化
   *
   * @param instanceId 实例 ID
   * @param variables 变量 Map
   */
  @Transactional(rollbackFor = Exception.class)
  public void updateVariables(String instanceId, Map<String, Object> variables) {
    if (variables == null || variables.isEmpty()) {
      return;
    }
    FlowInstanceVO instance = instanceRepository.findById(instanceId).orElse(null);
    if (instance == null) {
      throw SysException.builder()
          .resultCode(YdszResultCode.NOT_FOUND)
          .key("error.workflow.instance.not.found")
          .params(instanceId)
          .build();
    }
    Map<String, Object> map = parseVariables(instance.getVariable());
    map.putAll(variables);
    instanceRepository.updateVariable(instanceId, YdszJson.toJson(map));
    log.info("[Flow] 批量设置变量: instanceId={} keys={}", instanceId, variables.keySet());
  }

  // ============================== 批量操作（Service 独有） ==============================

  /**
   * P2-6: 批量发起流程实例。
   *
   * <p>每个 {@link FlowStartProcessDTO} 通过 {@link #startInstance} 独立事务发起，单个失败不影响其他实例。
   * 返回成功发起的 instanceId 列表 + 失败项明细。
   *
   * @param dtos 流程启动参数列表（不能为空，最多 100 条）
   * @return Map 包含：
   *     <ul>
   *       <li>{@code successCount} (int) — 成功发起数
   *       <li>{@code failedCount} (int) — 失败数
   *       <li>{@code instanceIds} (List&lt;String&gt;) — 成功发起的实例 ID 列表
   *       <li>{@code failedItems} (List&lt;Map&gt;) — 失败项明细，每项含 index / businessId / reason
   *     </ul>
   * @throws SysException 当 dtos 为空或超过 100 条时
   */
  public Map<String, Object> batchStartInstances(List<FlowStartProcessDTO> dtos) {
    if (dtos == null || dtos.isEmpty()) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("error.workflow.batch.empty")
          .build();
    }
    if (dtos.size() > BATCH_START_MAX_SIZE) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .key("error.workflow.batch.size.exceeded")
          .params(dtos.size(), BATCH_START_MAX_SIZE)
          .build();
    }

    int successCount = 0;
    List<String> instanceIds = new ArrayList<>(dtos.size());
    List<Map<String, Object>> failedItems = new ArrayList<>(dtos.size());

    for (int i = 0; i < dtos.size(); i++) {
      FlowStartProcessDTO dto = dtos.get(i);
      String businessId = dto != null ? dto.getBusinessId() : null;
      try {
        String instanceId = startInstance(dto);
        successCount++;
        instanceIds.add(instanceId);
        log.info(
            "[Flow] 批量发起第 {} 条成功: businessId={} instanceId={}",
            i + 1,
            businessId,
            instanceId);
      } catch (Exception e) {
        Map<String, Object> fail = new LinkedHashMap<>();
        fail.put("index", i + 1);
        fail.put("businessId", businessId);
        String reason = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
        fail.put("reason", reason);
        failedItems.add(fail);
        log.warn(
            "[Flow] 批量发起第 {} 条失败: businessId={} reason={}", i + 1, businessId, reason);
      }
    }

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("successCount", successCount);
    result.put("failedCount", failedItems.size());
    result.put("instanceIds", instanceIds);
    result.put("failedItems", failedItems);
    log.info(
        "[Flow] 批量发起完成: total={} success={} failed={}",
        dtos.size(),
        successCount,
        failedItems.size());
    return result;
  }

  /**
   * P1-8: 批量终止流程实例（含子流程级联终止）
   *
   * <p>终止指定实例列表，同时级联终止所有关联的子流程实例。
   * 每个 terminate 在独立事务中执行，单个失败不影响其它。
   *
   * @param instanceIds 实例 ID 列表
   * @param reason 终止原因
   * @return 实际终止的实例数（含级联子流程）
   */
  public int batchTerminate(List<String> instanceIds, String reason) {
    if (instanceIds == null || instanceIds.isEmpty()) {
      return 0;
    }
    int count = 0;
    for (String instanceId : instanceIds) {
      try {
        terminateInstance(instanceId, reason);
        count++;
        // 级联终止子流程实例
        List<FlowInstanceVO> children = instanceRepository.findRunningChildrenByParentId(instanceId);
        for (FlowInstanceVO child : children) {
          try {
            terminateInstance(child.getId(), "级联终止: " + reason);
            count++;
          } catch (Exception e) {
            log.warn(
                "[Flow] 级联终止子流程失败: parentId={} childId={} err={}",
                instanceId,
                child.getId(),
                e.getMessage());
          }
        }
      } catch (Exception e) {
        log.warn("[Flow] 批量终止实例失败: instanceId={} err={}", instanceId, e.getMessage());
      }
    }
    log.info("[Flow] 批量终止完成: requested={} actual={}", instanceIds.size(), count);
    return count;
  }
}
