package com.njydsz.workflow.server.service.impl.instance;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.njydsz.common.auth.context.AuthContextUtils;
import com.njydsz.common.core.code.YdszResultCode;
import com.njydsz.common.core.context.RequestContext;
import com.njydsz.common.exception.custom.SysException;
import com.njydsz.common.feign.assembler.NameAssembler;
import com.njydsz.common.lock.annotation.YdszDistributedLock;
import com.njydsz.common.security.LoginUser;
import com.njydsz.common.util.collection.MapUtils;
import com.njydsz.workflow.domain.dto.FlowInstanceDTO;
import com.njydsz.workflow.domain.dto.FlowStartProcessDTO;
import com.njydsz.workflow.domain.enums.FlowInstanceStatus;
import com.njydsz.workflow.domain.enums.FlowNodeType;
import com.njydsz.workflow.domain.enums.FlowTaskStatus;
import com.njydsz.workflow.domain.repository.FlowAuditLogRepository;
import com.njydsz.workflow.domain.repository.FlowHisTaskRepository;
import com.njydsz.workflow.domain.repository.FlowInstanceRepository;
import com.njydsz.workflow.domain.repository.FlowNodeRepository;
import com.njydsz.workflow.domain.repository.FlowRunTaskRepository;
import com.njydsz.workflow.domain.vo.FlowInstanceVO;
import com.njydsz.workflow.domain.vo.FlowNodeVO;
import com.njydsz.workflow.infra.converter.WorkflowConverter;
import com.njydsz.workflow.infra.entity.FlowAuditLogDO;
import com.njydsz.workflow.infra.entity.FlowDefinitionDO;
import com.njydsz.workflow.infra.entity.FlowNodeDO;
import com.njydsz.workflow.infra.entity.FlowRunTaskDO;
import com.njydsz.workflow.server.engine.FlowEventContext;
import com.njydsz.workflow.server.engine.FlowEventListener;
import com.njydsz.workflow.server.engine.FlowNodeExt;
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
 * <p><b>并发控制：</b>关键操作通过 {@link YdszDistributedLock} 注解保护。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowInstanceLifecycleService {

  /** P2-3: 默认允许回滚的最大天数 */
  private static final int DEFAULT_ROLLBACK_DAYS = 7;

  /** P2-3: 管理员回滚权限编码 */
  private static final String PERM_INSTANCE_ROLLBACK = "workflow:instance:rollback";

  /** P2-6: 单次批量发起的最大数量限制（防止事务过多） */
  private static final int BATCH_START_MAX_SIZE = 100;

  /** 流程实例仓储，负责 ydsz_flow_instance 的领域持久化 */
  private final FlowInstanceRepository instanceRepository;

  /** 流程定义服务，启动实例时解析流程定义节点和跳转 */
  private final FlowDefinitionService definitionService;

  /** 流程推进引擎，负责节点推进/跳转/网关条件求值 */
  private final DefaultFlowAdvancer advancer;

  /** 流程任务服务，创建/推进/终止任务 */
  private final FlowTaskService taskService;

  /** 运行时任务仓储，负责 ydsz_flow_run_task 的领域持久化 */
  private final FlowRunTaskRepository taskRepository;

  /** 流程节点仓储，负责 ydsz_flow_node 的领域持久化 */
  private final FlowNodeRepository nodeRepository;

  /** P2-3: Prometheus 指标收集（可能为 null：测试环境） */
  private final FlowMetrics flowMetrics;

  /** P2-3: 事件支持组件，统一处理事件监听器调用与 Spring 事件发布 */
  private final FlowTaskSupport flowTaskSupport;

  /** P1-3: 子流程服务（处理 callActivity 子流程启动） */
  private final FlowSubProcessService subProcessService;

  /** GAP-P1: 抄送服务（CC 节点处理） */
  private final FlowCcService ccService;

  /** 流程自动触发服务（实例完成时检查是否需要自动发起下一流程） */
  private final FlowAutoTriggerService autoTriggerService;

  /**
   * P0-1: BPMN 事件订阅服务 — 流程推进到事件捕获节点时创建订阅
   *
   * <p>使用 @Lazy 避免循环依赖：FlowEventSubscriptionServiceImpl → DefaultFlowAdvancer → FlowInstanceService →
   * FlowEventSubscriptionService
   */
  @Lazy
  private final FlowEventSubscriptionService eventSubscriptionService;

  /** 审计日志仓储，负责 ydsz_flow_audit_log 的领域持久化 */
  private final FlowAuditLogRepository auditLogRepository;

  /** 历史任务仓储，负责 ydsz_flow_his_task 的领域持久化 */
  private final FlowHisTaskRepository hisTaskRepository;

  /** MapStruct 转换器，用于 VO ↔ DO 转换 */
  private final WorkflowConverter converter;

  /**
   * P0-2: 定时器服务 — boundaryEvent 含 timer 配置时注册边界定时器自动触发
   *
   * <p>使用 @Lazy 避免循环依赖：FlowTimerServiceImpl → DefaultFlowAdvancer → FlowInstanceService → FlowTimerService
   */
  @Lazy
  private final FlowTimerService timerService;

  /**
   * P0-4: 跨服务名称解析门面，用于读路径兜底富化 initiatorName。
   */
  private final NameAssembler nameAssembler;

  // ============================== 生命周期操作 ==============================

  /**
   * 启动流程实例
   *
   * <p>完整执行链路：
   *
   * <ol>
   *   <li><b>参数校验</b>：{@code flowCode / businessType / businessId} 必填
   *   <li><b>幂等检查</b>：按 {@code (tenantId, businessType, businessId)} 查已存在活跃实例，存在则直接返回其 ID（防重）
   *   <li><b>定义解析</b>：通过 {@link FlowDefinitionService#getPublished} 查询最新已发布流程定义
   *   <li><b>变量策略</b>：合并发起人自选审批人变量
   *   <li><b>实例落库</b>：{@code ydsz_flow_instance} 写入，{@code flowStatus=RUNNING}
   *   <li><b>推进到开始节点</b>：通过 {@link com.njydsz.workflow.server.engine.impl.DefaultFlowAdvancer} 计算下一节点并创建首个待办任务
   *   <li><b>事件触发</b>：发布 {@code onInstanceStart} 事件
   * </ol>
   *
   * @param dto 启动参数 DTO（含 {@code flowCode/businessType/businessId/variables/initiatorId}）
   * @return 流程实例 ID（新建或已存在）
   * @throws SysException {@code BAD_REQUEST} — 参数缺失；{@code NOT_FOUND} — 流程定义未找到
   */
  @Transactional(rollbackFor = Exception.class)
  public String startInstance(FlowStartProcessDTO dto) {
    if (dto == null
        || !StringUtils.hasText(dto.getFlowCode())
        || !StringUtils.hasText(dto.getBusinessType())
        || !StringUtils.hasText(dto.getBusinessId())) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("error.workflow.msg_208e3c66")
          .build();
    }

    // 0. 幂等：同 business 已有活跃实例（RUNNING/SUSPENDED）则直接返回
    String tenantId =
        dto.getTenantId() != null ? dto.getTenantId() : AuthContextUtils.getTenantIdOrDefault();
    FlowInstanceVO existing =
        instanceRepository.findByBusiness(tenantId, dto.getBusinessType(), dto.getBusinessId())
            .orElse(null);
    if (existing != null) {
      log.info(
          "[Flow] 实例已存在: businessType={} businessId={} id={} status={}",
          dto.getBusinessType(),
          dto.getBusinessId(),
          existing.getId(),
          existing.getFlowStatus());
      return existing.getId();
    }

    // 1. 查定义 - 直接查询最新已发布流程定义
    FlowDefinitionDO def =
        definitionService.getPublished(
            dto.getFlowCode(),
            StringUtils.hasText(dto.getVersion()) ? dto.getVersion() : null,
            tenantId);
    if (def == null) {
      throw SysException.builder()
          .resultCode(YdszResultCode.NOT_FOUND)
          .key("error.workflow.msg_add8d012")
          .params(dto.getFlowCode())
          .build();
    }

    // 2. 构建流程实例 DTO
    FlowInstanceDTO instanceDto = new FlowInstanceDTO();
    instanceDto.setFlowCode(def.getFlowCode());
    instanceDto.setFlowName(def.getFlowName());
    instanceDto.setDefinitionId(def.getId());
    instanceDto.setFlowVersion(def.getFlowVersion());
    instanceDto.setBusinessType(dto.getBusinessType());
    instanceDto.setBusinessId(dto.getBusinessId());
    instanceDto.setBusinessNo(dto.getBusinessNo());
    instanceDto.setTitle(
        dto.getTitle() == null ? def.getFlowName() + "-" + dto.getBusinessId() : dto.getTitle());
    instanceDto.setInitiatorId(dto.getInitiatorId());
    instanceDto.setInitiatorName(dto.getInitiatorName());
    instanceDto.setFlowStatus(FlowInstanceStatus.RUNNING.name());
    instanceDto.setActivityStatus(1);
    instanceDto.setStartAt(LocalDateTime.now());
    // GAP-P2: 发起人自选审批人 — 将 nodeAssignees 合并到 variables 中
    Map<String, Object> mergedVars =
        dto.getVariables() == null ? new HashMap<>() : new HashMap<>(dto.getVariables());
    if (dto.getNodeAssignees() != null && !dto.getNodeAssignees().isEmpty()) {
      for (Map.Entry<String, List<Long>> entry : dto.getNodeAssignees().entrySet()) {
        mergedVars.put("_selfSelect_" + entry.getKey(), entry.getValue());
      }
    }
    instanceDto.setVariable(mergedVars.isEmpty() ? null : YdszJson.toJson(mergedVars));
    instanceDto.setProviderTraceId(dto.getProviderTraceId());
    // P1-3: 子流程场景：填充父实例信息
    instanceDto.setParentInstanceId(dto.getParentInstanceId());
    instanceDto.setParentNodeCode(dto.getParentNodeCode());
    FlowInstanceVO savedInstance = instanceRepository.save(instanceDto);
    String instanceId = savedInstance.getId();

    // P2-38: 发起人自选审批人 — _selfSelect_<nodeCode> 变量已合并到 mergedVars
    for (String key : mergedVars.keySet()) {
      if (key != null && key.startsWith("_selfSelect_")) {
        log.info(
            "[Flow] 发起人自选审批人变量: instanceId={} key={} value={}",
            instanceId,
            key,
            mergedVars.get(key));
      }
    }

    // P2-3: 触发 onInstanceStart 事件（统一事件机制）
    fireEvent(l -> l.onInstanceStart(instanceId, mergedVars));

    // P2-3: Prometheus 指标 — 实例创建
    if (flowMetrics != null) {
      flowMetrics.incInstance(def.getFlowCode(), "created");
    }

    // 3. 引擎推进：开始节点 → 下一节点
    try {
      advancer.start(instanceId);
    } catch (Exception e) {
      // P2-3: 触发 onError 事件（统一事件机制）
      fireEvent(l -> l.onError(instanceId, e));
      if (flowMetrics != null) {
        flowMetrics.incError(def.getFlowCode(), "start_error");
      }
      throw e;
    }
    log.info(
        "[Flow] 启动流程: code={} bizId={} instanceId={}",
        dto.getFlowCode(),
        dto.getBusinessId(),
        instanceId);

    return instanceId;
  }

  /**
   * 终止流程实例
   *
   * @param instanceId 实例 ID
   * @param reason 终止原因
   */
  @Transactional(rollbackFor = Exception.class)
  @YdszDistributedLock(key = "'flow:instance:op:' + #instanceId", waitTime = 3, leaseTime = 30)
  public void terminateInstance(String instanceId, String reason) {
    FlowInstanceVO instance = getByIdOrThrow(instanceId);
    if (FlowInstanceStatus.valueOf(instance.getFlowStatus()).isFinished()) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("error.workflow.msg_2246960b")
          .build();
    }
    LocalDateTime now = LocalDateTime.now();
    Long durationMs =
        instance.getStartAt() == null
            ? null
            : Duration.between(instance.getStartAt(), now).toMillis();
    // P2-18: reason 持久化到 variable JSON
    String var = instance.getVariable();
    if (StringUtils.hasText(reason)) {
      try {
        Map<String, Object> m = parseVariables(var);
        m.put("_terminateReason", reason);
        var = YdszJson.toJson(m);
        // 修复 P2-18: 写回 DB（之前仅改局部变量未持久化）
        instanceRepository.updateVariable(instanceId, var);
      } catch (Exception e) {
        log.warn(
            "[Flow] terminate reason 持久化失败: instanceId={} reason={}", instanceId, e.getMessage());
      }
    }
    instanceRepository.updateStatus(
        instanceId, FlowInstanceStatus.TERMINATED.name(), null, null, now, durationMs);
    // 取消所有 PENDING 任务
    taskService.cancelByInstance(instanceId, FlowTaskStatus.CANCELLED.name());
    // P0-1: 取消所有 WAITING 事件订阅
    eventSubscriptionService.cancelByInstance(instanceId, "INSTANCE_TERMINATED: " + reason);
    log.info("[Flow] 终止流程: instanceId={} reason={}", instanceId, reason);
    // P2-3: Prometheus 指标 — 实例终止 + 耗时
    if (flowMetrics != null) {
      flowMetrics.incInstance(instance.getFlowCode(), "terminated");
      flowMetrics.recordInstanceDuration(instance, "TERMINATED");
    }
    // P2-34: 触发 onInstanceTerminated 事件
    fireEvent(l -> l.onInstanceTerminated(instanceId, reason));
    // P2-37: 同时调用携带上下文的重载版本
    FlowEventContext ctx = buildContext(instanceId, null, null, "TERMINATE", instance);
    fireEvent(l -> l.onInstanceTerminated(instanceId, reason, ctx));
    // P2-35: 发布 Spring 异步事件
    publishWorkflowEvent("INSTANCE_TERMINATED", instanceId, null);
  }

  /**
   * 挂起流程实例
   *
   * @param instanceId 实例 ID
   */
  @Transactional(rollbackFor = Exception.class)
  @YdszDistributedLock(key = "'flow:instance:op:' + #instanceId", waitTime = 3, leaseTime = 30)
  public void suspendInstance(String instanceId) {
    FlowInstanceVO instance = getByIdOrThrow(instanceId);
    if (!FlowInstanceStatus.RUNNING.name().equals(instance.getFlowStatus())) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("error.workflow.msg_543fc92f")
          .build();
    }
    instanceRepository.updateStatus(
        instanceId,
        FlowInstanceStatus.SUSPENDED.name(),
        instance.getCurrentNodeCode(),
        instance.getCurrentNodeName(),
        null,
        null);
    // P2-18: 冻结 PENDING/CLAIMED 任务为 FROZEN，禁止办理
    taskRepository.freezeByInstance(instanceId);
    log.info("[Flow] 挂起流程: instanceId={}", instanceId);
    // P2-3: Prometheus 指标
    if (flowMetrics != null) {
      flowMetrics.incInstance(instance.getFlowCode(), "suspended");
    }
    // P2-34: 触发 onInstanceSuspended 事件
    fireEvent(l -> l.onInstanceSuspended(instanceId));
    // P2-35: 发布 Spring 异步事件
    publishWorkflowEvent("INSTANCE_SUSPENDED", instanceId, null);
  }

  /**
   * 激活流程实例
   *
   * @param instanceId 实例 ID
   */
  @Transactional(rollbackFor = Exception.class)
  @YdszDistributedLock(key = "'flow:instance:op:' + #instanceId", waitTime = 3, leaseTime = 30)
  public void activateInstance(String instanceId) {
    FlowInstanceVO instance = getByIdOrThrow(instanceId);
    if (!FlowInstanceStatus.SUSPENDED.name().equals(instance.getFlowStatus())) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("error.workflow.msg_ab594c75")
          .build();
    }
    instanceRepository.updateStatus(
        instanceId,
        FlowInstanceStatus.RUNNING.name(),
        instance.getCurrentNodeCode(),
        instance.getCurrentNodeName(),
        null,
        null);
    // P2-18: 解冻 FROZEN 任务，回到 PENDING 可办理
    taskRepository.unfreezeByInstance(instanceId);
    log.info("[Flow] 激活流程: instanceId={}", instanceId);
    // P2-3: Prometheus 指标
    if (flowMetrics != null) {
      flowMetrics.incInstance(instance.getFlowCode(), "activated");
    }
    // P2-34: 触发 onInstanceActivated 事件
    fireEvent(l -> l.onInstanceActivated(instanceId));
    // P2-35: 发布 Spring 异步事件
    publishWorkflowEvent("INSTANCE_ACTIVATED", instanceId, null);
  }

  // ============================== 变量管理 ==============================

  /**
   * P2-24: 读取实例流程变量
   *
   * @param instanceId 实例 ID
   * @return 变量 Map，无变量返回空 Map
   */
  @Transactional(readOnly = true)
  public Map<String, Object> getVariables(String instanceId) {
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
          .message("error.workflow.msg_fae06125")
          .build();
    }
    FlowInstanceVO instance = instanceRepository.findById(instanceId).orElse(null);
    if (instance == null) {
      throw SysException.builder()
          .resultCode(YdszResultCode.NOT_FOUND)
          .key("error.workflow.msg_67a10717")
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
          .key("error.workflow.msg_67a10717")
          .params(instanceId)
          .build();
    }
    Map<String, Object> map = parseVariables(instance.getVariable());
    map.putAll(variables);
    instanceRepository.updateVariable(instanceId, YdszJson.toJson(map));
    log.info("[Flow] 批量设置变量: instanceId={} keys={}", instanceId, variables.keySet());
  }

  // ============================== 批量操作 ==============================

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
          .message("error.workflow.msg_e4f5a6b7")
          .build();
    }
    if (dtos.size() > 100) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .key("error.workflow.msg_f5a6b7c8")
          .params(dtos.size(), 100)
          .build();
    }

    int successCount = 0;
    List<String> instanceIds = new ArrayList<>();
    List<Map<String, Object>> failedItems = new ArrayList<>();

    for (int i = 0; i < dtos.size(); i++) {
      FlowStartProcessDTO dto = dtos.get(i);
      String businessId = dto != null ? dto.getBusinessId() : null;
      try {
        String instanceId = startInstance(dto);
        successCount++;
        instanceIds.add(instanceId);
        log.info("[Flow] 批量发起第 {} 条成功: businessId={} instanceId={}", i + 1, businessId, instanceId);
      } catch (Exception e) {
        Map<String, Object> fail = new LinkedHashMap<>();
        fail.put("index", i + 1);
        fail.put("businessId", businessId);
        String reason = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
        fail.put("reason", reason);
        failedItems.add(fail);
        log.warn("[Flow] 批量发起第 {} 条失败: businessId={} reason={}", i + 1, businessId, reason);
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

  // ============================== 其他生命周期方法 ==============================

  /**
   * 强制完成（推进到结束节点）
   *
   * @param instanceId 实例 ID
   * @param endNodeCode 终止节点编码
   */
  @Transactional(rollbackFor = Exception.class)
  @YdszDistributedLock(key = "'flow:instance:op:' + #instanceId", waitTime = 3, leaseTime = 30)
  public void complete(String instanceId, String endNodeCode) {
    FlowInstanceVO instance = getByIdOrThrow(instanceId);
    if (FlowInstanceStatus.valueOf(instance.getFlowStatus()).isFinished()) {
      return;
    }
    LocalDateTime now = LocalDateTime.now();
    Long durationMs =
        instance.getStartAt() == null
            ? null
            : Duration.between(instance.getStartAt(), now).toMillis();
    instanceRepository.updateStatus(
        instanceId, FlowInstanceStatus.COMPLETED.name(), endNodeCode, null, now, durationMs);
    taskService.cancelByInstance(instanceId, FlowTaskStatus.SKIPPED.name());
    log.info("[Flow] 流程完成: instanceId={} endNode={}", instanceId, endNodeCode);
    // P2-3: Prometheus 指标 — 实例完成 + 耗时
    if (flowMetrics != null) {
      flowMetrics.incInstance(instance.getFlowCode(), "completed");
      flowMetrics.recordInstanceDuration(instance, "COMPLETED");
    }

    // 业务侧事件：onInstanceCompleted
    fireEvent(l -> l.onInstanceCompleted(instanceId));
    // P2-35: 发布 Spring 异步事件
    publishWorkflowEvent("INSTANCE_COMPLETED", instanceId, null);
    // 自动触发：检查是否需要自动发起下一流程
    try {
      autoTriggerService.onInstanceCompleted(instanceId);
    } catch (Exception e) {
      log.warn("[Flow] 自动触发检查失败: instanceId={} err={}", instanceId, e.getMessage());
    }
  }

  /**
   * P1-1: 撤回到指定历史节点（对标钉钉/飞书"撤回到指定节点"）。
   *
   * @param instanceId 实例 ID
   * @param initiatorId 发起人 ID
   * @param targetNodeCode 目标节点编码（null/空时降级到 {@link #recall(String, String)}）
   * @return 是否撤回成功
   */
  @Transactional(rollbackFor = Exception.class)
  @YdszDistributedLock(key = "'flow:instance:op:' + #instanceId", waitTime = 3, leaseTime = 30)
  public boolean recall(String instanceId, String initiatorId, String targetNodeCode) {
    if (!StringUtils.hasText(targetNodeCode)) {
      return recall(instanceId, initiatorId);
    }

    FlowInstanceVO instance = getByIdOrThrow(instanceId);
    validateRecallPermission(instance, initiatorId);
    List<FlowRunTaskDO> pendingTasks = validateNextTasksAllPending(instanceId);

    // 校验：targetNodeCode 必须在可撤回节点列表中
    validateTargetNodeRecallable(instanceId, targetNodeCode);

    // 取消当前待办并退回到目标节点
    String currentNodeCode = pendingTasks.isEmpty() ? instance.getCurrentNodeCode() : pendingTasks.get(0).getNodeCode();
    taskService.cancelByInstance(instanceId, FlowTaskStatus.CANCELLED.name());
    advanceToTargetNode(instance, currentNodeCode, targetNodeCode);

    log.info("[Flow] 撤回流程到指定节点: instanceId={} initiatorId={} targetNodeCode={}",
        instanceId, initiatorId, targetNodeCode);
    fireRecallEvents(instance);
    return true;
  }

  @Transactional(rollbackFor = Exception.class)
  @YdszDistributedLock(key = "'flow:instance:op:' + #instanceId", waitTime = 3, leaseTime = 30)
  public boolean recall(String instanceId, String initiatorId) {
    FlowInstanceVO instance = getByIdOrThrow(instanceId);
    validateRecallPermission(instance, initiatorId);
    validateNextTasksAllPending(instanceId);

    // 取消当前待办并回退到开始节点的下一节点
    taskService.cancelByInstance(instanceId, FlowTaskStatus.CANCELLED.name());
    try {
      advancer.start(instanceId);
    } catch (Exception e) {
      log.error("[Flow] 撤回后重新推进失败: instanceId={}", instanceId, e);
      throw SysException.builder()
          .resultCode(YdszResultCode.INTERNAL_ERROR)
          .key("error.workflow.msg_3d726320")
          .params(e.getMessage())
          .build();
    }
    log.info("[Flow] 撤回流程: instanceId={} initiatorId={}", instanceId, initiatorId);
    fireRecallEvents(instance);
    return true;
  }

  /**
   * 校验撤回权限：仅发起人可撤回且实例为运行中。
   *
   * @param instance 参数说明
   * @param initiatorId 参数说明
   */
  private void validateRecallPermission(FlowInstanceVO instance, String initiatorId) {
    if (!instance.getInitiatorId().equals(initiatorId)) {
      throw SysException.builder()
          .resultCode(YdszResultCode.FORBIDDEN)
          .message("error.workflow.msg_cc712a3a")
          .build();
    }
    if (!FlowInstanceStatus.RUNNING.name().equals(instance.getFlowStatus())) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("error.workflow.msg_3095a676")
          .build();
    }
  }

  /**
   * 校验下一节点待办全部为 PENDING（未被 CLAIMED/COMPLETED），返回待办列表。
   *
   * @param instanceId 参数说明
   * @return 返回值说明
   */
  private List<FlowRunTaskDO> validateNextTasksAllPending(String instanceId) {
    List<FlowRunTaskDO> pendingTasks = taskRepository.findPendingByInstance(instanceId).stream()
        .map(converter::entityToDO)
        .collect(Collectors.toList());
    boolean anyProcessed = pendingTasks.stream()
        .anyMatch(t -> FlowTaskStatus.CLAIMED.name().equals(t.getTaskStatus())
            || FlowTaskStatus.COMPLETED.name().equals(t.getTaskStatus()));
    if (anyProcessed) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("error.workflow.msg_c55fe642")
          .build();
    }
    return pendingTasks;
  }

  /**
   * 校验目标节点在可撤回节点列表中。
   *
   * @param instanceId 参数说明
   * @param targetNodeCode 参数说明
   */
  private void validateTargetNodeRecallable(String instanceId, String targetNodeCode) {
    List<Map<String, Object>> recallable = hisTaskRepository.listPassedNodes(instanceId);
    Set<String> recallableCodes = new HashSet<>();
    if (recallable != null) {
      for (Map<String, Object> n : recallable) {
        Object code = n.get("nodeCode");
        if (code != null) {
          recallableCodes.add(code.toString());
        }
      }
    }
    if (!recallableCodes.contains(targetNodeCode)) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .key("error.workflow.msg_e5f6a7b8")
          .params(targetNodeCode)
          .build();
    }
  }

  /**
   * 退回到指定目标节点（复用 advancer.advance 的 REJECT 通道）。
   *
   * @param instance 参数说明
   * @param currentNodeCode 参数说明
   * @param targetNodeCode 参数说明
   */
  private void advanceToTargetNode(FlowInstanceVO instance, String currentNodeCode, String targetNodeCode) {
    Map<String, Object> variables = parseVariables(instance.getVariable());
    try {
      advancer.advance(instance, currentNodeCode, "REJECT", targetNodeCode, variables);
    } catch (Exception e) {
      log.error("[Flow] 撤回到指定节点失败: instanceId={} targetNodeCode={}", instance.getId(), targetNodeCode, e);
      throw SysException.builder()
          .resultCode(YdszResultCode.INTERNAL_ERROR)
          .key("error.workflow.msg_3d726320")
          .params(e.getMessage())
          .build();
    }
  }

  /**
   * 触发撤回相关事件（Prometheus + 业务事件 + Spring 事件）。
   *
   * @param instance 参数说明
   */
  private void fireRecallEvents(FlowInstanceVO instance) {
    if (flowMetrics != null) {
      flowMetrics.incInstance(instance.getFlowCode(), "recalled");
    }
    fireEvent(l -> l.onInstanceRecalled(instance.getId(), instance.getInitiatorId()));
    publishWorkflowEvent("INSTANCE_RECALLED", instance.getId(), null);
  }

  /**
   * P2-3: 回滚已完成的流程实例（撤销）
   *
   * @param instanceId 实例 ID
   * @param operatorId 操作人 ID（发起人或管理员）
   * @param reason 回滚原因
   * @param maxRollbackDays 允许回滚的最大天数（&lt;=0 时使用默认值 7）
   * @return 是否回滚成功
   */
  @Transactional(rollbackFor = Exception.class)
  @YdszDistributedLock(key = "'flow:instance:op:' + #instanceId", waitTime = 3, leaseTime = 30)
  public boolean rollback(
      String instanceId, String operatorId, String reason, int maxRollbackDays) {
    FlowInstanceVO instance = getByIdOrThrow(instanceId);

    // 1. 校验：仅 COMPLETED 状态可回滚
    if (!FlowInstanceStatus.COMPLETED.name().equals(instance.getFlowStatus())) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .key("error.workflow.msg_a1b2c3d4")
          .params(instance.getFlowStatus())
          .build();
    }

    // 2. 校验：仅发起人或管理员可回滚
    boolean isInitiator =
        instance.getInitiatorId() != null && instance.getInitiatorId().equals(operatorId);
    boolean isAdmin = false;
    LoginUser user = AuthContextUtils.getCurrentOrNull();
    if (user != null) {
      isAdmin = user.isSuperAdmin() || user.hasPermission(PERM_INSTANCE_ROLLBACK);
    }
    if (!isInitiator && !isAdmin) {
      throw SysException.builder()
          .resultCode(YdszResultCode.FORBIDDEN)
          .message("error.workflow.msg_b2c3d4e5")
          .build();
    }

    // 3. 校验：回滚原因不能为空
    if (!StringUtils.hasText(reason)) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("error.workflow.msg_d4e5f6a7")
          .build();
    }

    // 4. 校验：时间窗口
    int days = maxRollbackDays > 0 ? maxRollbackDays : DEFAULT_ROLLBACK_DAYS;
    if (instance.getEndAt() != null) {
      long elapsedDays = Duration.between(instance.getEndAt(), LocalDateTime.now()).toDays();
      if (elapsedDays > days) {
        throw SysException.builder()
            .resultCode(YdszResultCode.BAD_REQUEST)
            .key("error.workflow.msg_c3d4e5f6")
            .params(days)
            .build();
      }
    }

    // 5. 更新实例状态为 ROLLED_BACK
    LocalDateTime now = LocalDateTime.now();
    Long durationMs =
        instance.getStartAt() == null
            ? null
            : Duration.between(instance.getStartAt(), now).toMillis();
    instanceRepository.updateStatus(
        instanceId,
        FlowInstanceStatus.ROLLED_BACK.name(),
        instance.getCurrentNodeCode(),
        instance.getCurrentNodeName(),
        now,
        durationMs);

    // 6. 记录回滚元信息到 variable JSON
    try {
      Map<String, Object> vars = parseVariables(instance.getVariable());
      Map<String, Object> rollbackInfo = new LinkedHashMap<>();
      rollbackInfo.put("operatorId", operatorId);
      rollbackInfo.put("reason", reason);
      rollbackInfo.put("rolledBackAt", now.toString());
      rollbackInfo.put("byAdmin", isAdmin && !isInitiator);
      vars.put("_rollback", rollbackInfo);
      instanceRepository.updateVariable(instanceId, YdszJson.toJson(vars));
    } catch (Exception e) {
      log.warn("[Flow] 回滚元信息持久化失败: instanceId={} err={}", instanceId, e.getMessage());
    }

    log.info(
        "[Flow] 回滚流程: instanceId={} operatorId={} reason={} isAdmin={}",
        instanceId,
        operatorId,
        reason,
        isAdmin && !isInitiator);

    // 7. Prometheus 指标
    if (flowMetrics != null) {
      flowMetrics.incInstance(instance.getFlowCode(), "recalled");
    }

    // 8. 触发 onInstanceRolledBack 事件（业务侧可执行补偿）
    fireEvent(l -> l.onInstanceRolledBack(instanceId, operatorId, reason));

    // 9. 发布 Spring 异步事件
    publishWorkflowEvent("INSTANCE_ROLLED_BACK", instanceId, null);

    return true;
  }

  /**
   * P2-2 (GAP-10): 驳回后快速重审
   *
   * @param instanceId 被驳回的实例 ID
   * @param initiatorId 发起人 ID
   * @param variables 重审时新增/覆盖的变量（可空）
   * @param comment 重审说明（可选）
   * @return 实例 ID
   */
  @Transactional(rollbackFor = Exception.class)
  @YdszDistributedLock(key = "'flow:instance:op:' + #instanceId", waitTime = 3, leaseTime = 30)
  public String resubmit(
      String instanceId, String initiatorId, Map<String, Object> variables, String comment) {
    FlowInstanceVO instance = getByIdOrThrow(instanceId);
    // 1. 状态校验：仅 REJECTED 可重审
    FlowInstanceStatus status = FlowInstanceStatus.valueOf(instance.getFlowStatus());
    if (status != FlowInstanceStatus.REJECTED) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .key("error.workflow.msg_7f4098fb")
          .params("仅被驳回实例可重审，当前状态=" + instance.getFlowStatus())
          .build();
    }
    // 2. 发起人校验
    if (instance.getInitiatorId() != null
        && !String.valueOf(instance.getInitiatorId()).equals(initiatorId)) {
      throw SysException.builder()
          .resultCode(YdszResultCode.FORBIDDEN)
          .key("error.workflow.msg_d65b2814")
          .params("仅发起人可重审")
          .build();
    }
    // 3. 合并变量（保留历史变量，覆盖新增）
    Map<String, Object> merged = getVariables(instanceId);
    if (merged == null) {
      merged = new HashMap<>();
    }
    if (variables != null && !variables.isEmpty()) {
      merged.putAll(variables);
    }
    // 4. 重置实例状态为 RUNNING，清掉 REJECTED 标记，重置开始时间
    instance.setFlowStatus(FlowInstanceStatus.RUNNING.name());
    instance.setActivityStatus(1);
    instance.setCurrentNodeCode(null);
    instance.setCurrentNodeName(null);
    instance.setStartAt(LocalDateTime.now());
    instance.setEndAt(null);
    instance.setRejectReason(null);
    instance.setVariable(merged.isEmpty() ? null : YdszJson.toJson(merged));
    instanceRepository.save(converter.doToDto(instance));
    // 5. 记录重审审计（保留原轨迹，仅追加一条 RESUBMIT 记录）
    FlowAuditLogDO audit = new FlowAuditLogDO();
    audit.setInstanceId(instanceId);
    audit.setFlowCode(instance.getFlowCode());
    audit.setBusinessType(instance.getBusinessType());
    audit.setBusinessId(instance.getBusinessId());
    audit.setAction("RESUBMIT");
    audit.setOperatorId(initiatorId);
    audit.setOperatorName(instance.getInitiatorName());
    audit.setComment(comment);
    audit.setTenantId(instance.getTenantId());
    audit.setProviderTraceId(instance.getProviderTraceId());
    audit.setOperatedAt(LocalDateTime.now());
    auditLogRepository.save(converter.entityToVO(audit));
    // 6. 从开始节点重新推进（复用 advancer.start，保留 ydsz_flow_user/his_task 历史）
    try {
      advancer.start(instanceId);
    } catch (Exception e) {
      // P2-3: 触发 onError 事件（统一事件机制）
      fireEvent(l -> l.onError(instanceId, e));
      throw e;
    }
    log.info("[Flow] 驳回后快速重审: instanceId={} initiatorId={}", instanceId, initiatorId);
    return instanceId;
  }

  /**
   * P1-8: 流程重做 — 支持 redoMode 指定重做策略。
   *
   * @param instanceId 原实例 ID
   * @param initiatorId 发起人 ID
   * @param variables 重做时新增/覆盖的变量（可空）
   * @param comment 重做说明（可选）
   * @param redoMode 重做模式：RESTART / NEW_INSTANCE
   * @return 实例 ID
   */
  @Transactional(rollbackFor = Exception.class)
  @YdszDistributedLock(key = "'flow:instance:op:' + #instanceId", waitTime = 3, leaseTime = 30)
  public String resubmit(
      String instanceId,
      String initiatorId,
      Map<String, Object> variables,
      String comment,
      String redoMode) {
    String mode = (redoMode == null || redoMode.isBlank()) ? "RESTART" : redoMode.toUpperCase();
    if ("NEW_INSTANCE".equals(mode)) {
      return resubmitAsNewInstance(instanceId, initiatorId, variables, comment);
    }
    // 默认 RESTART 模式：委托到现有 resubmit（向后兼容）
    return resubmit(instanceId, initiatorId, variables, comment);
  }

  /**
   * 设置实例的 dueAt 字段（子流程超时处理）
   *
   * @param instanceId 实例 ID
   * @param dueAt 超时时间（传 null 清除超时标记）
   */
  @Transactional(rollbackFor = Exception.class)
  public void setDueAt(String instanceId, LocalDateTime dueAt) {
    instanceRepository.updateDueAt(instanceId, dueAt);
    log.info("[Flow] 设置实例到期时间: instanceId={} dueAt={}", instanceId, dueAt);
  }

  // ============================== 内部方法 ==============================

  /**
   * 内部方法：创建第一个待办任务（供 DefaultFlowAdvancer 调用）
   *
   * @param instanceId 参数说明
   * @param startNode 参数说明
   * @param variables 参数说明
   * @return 返回值说明
   */
  public String createFirstTask(
      String instanceId, FlowNodeVO startNode, Map<String, Object> variables) {
    FlowInstanceVO instance = getByIdOrThrow(instanceId);
    List<FlowNodeVO> nextNodes =
        advancer.advance(instance, startNode.getNodeCode(), "PASS", null, variables);
    if (nextNodes.isEmpty()) {
      log.warn("[Flow] 流程无下游节点: instanceId={}", instanceId);
      complete(instanceId, startNode.getNodeCode());
      return null;
    }
    for (FlowNodeVO node : nextNodes) {
      taskService.createTask(instanceId, converter.entityToDO(node), variables);
    }
    instanceRepository.updateStatus(
        instanceId,
        instance.getFlowStatus(),
        nextNodes.get(0).getNodeCode(),
        nextNodes.get(0).getNodeName(),
        null,
        null);
    return instanceId;
  }

  /**
   * 内部方法：推进后批量生成任务（供 DefaultFlowAdvancer 调用）
   *
   * @param instanceId 参数说明
   * @param nextNodes 参数说明
   * @param variables 参数说明
   */
  public void generateTasksForNodes(
      String instanceId, List<FlowNodeVO> nextNodes, Map<String, Object> variables) {
    if (nextNodes == null || nextNodes.isEmpty()) {
      return;
    }
    List<FlowNodeDO> doNodes = nextNodes.stream().map(converter::entityToDO).toList();
    for (FlowNodeDO node : doNodes) {
      // P0-2: 优先判断事件捕获节点（boundaryEvent / intermediateCatchEvent）
      if (eventSubscriptionService.isEventCatchNode(node)) {
        String boundaryTaskId = resolveBoundaryTaskId(node, instanceId);
        eventSubscriptionService.createSubscription(instanceId, node, variables, boundaryTaskId);
        // P0-2: 如果 ext.timer 存在，注册边界定时器自动触发（timer boundary 语义）
        scheduleBoundaryTimerIfPresent(node, instanceId, boundaryTaskId);
        // 更新实例当前节点为事件捕获节点（流程在此等待事件触发）
        instanceRepository.updateStatus(
            instanceId, null, node.getNodeCode(), node.getNodeName(), null, null);
        log.info(
            "[Flow] 事件捕获节点等待触发: instanceId={} node={} type={}",
            instanceId,
            node.getNodeCode(),
            node.getNodeType());
        continue;
      }
      if (node.getNodeType().equals(FlowNodeType.CC.getCode())) {
        // GAP-P1: 抄送节点 — 展开接收人并写入 ydsz_flow_cc，然后自动推进到下一节点
        try {
          ccService.handleCcNode(instanceId, node, variables);
          log.info("[Flow] 抄送节点处理完成: instanceId={} node={}", instanceId, node.getNodeCode());
        } catch (Exception e) {
          log.warn(
              "[Flow] 抄送节点处理失败，跳过继续: instanceId={} node={} err={}",
              instanceId,
              node.getNodeCode(),
              e.getMessage());
        }
        // 抄送节点是穿透节点：自动推进到下游
        FlowInstanceVO ccInstance = instanceRepository.findById(instanceId).orElse(null);
        if (ccInstance != null) {
          List<FlowNodeVO> ccNext =
              advancer.advance(ccInstance, node.getNodeCode(), "PASS", null, variables);
          if (!ccNext.isEmpty()) {
            generateTasksForNodes(instanceId, ccNext, variables);
          }
        }
        continue;
      }
      if (node.getNodeType().equals(FlowNodeType.END.getCode())) {
        complete(instanceId, node.getNodeCode());
        return;
      }
      // P1-3 / fix-1: SUBPROCESS 节点或 ext 中含 callActivityFlowCode 的节点触发子流程
      if (node.getNodeType().equals(FlowNodeType.SUBPROCESS.getCode()) || isCallActivity(node)) {
        try {
          FlowInstanceVO instance = instanceRepository.findById(instanceId).orElse(null);
          subProcessService.startSubProcess(instance, node, variables);
          // 子流程启动后，父流程"停在" callActivity 节点，更新 currentNodeCode
          instanceRepository.updateStatus(
              instanceId,
              instance.getFlowStatus(),
              node.getNodeCode(),
              node.getNodeName(),
              null,
              null);
          log.info(
              "[Flow] callActivity 触发子流程: instanceId={} node={}", instanceId, node.getNodeCode());
        } catch (Exception e) {
          log.error(
              "[Flow] callActivity 启动子流程失败: instanceId={} node={} err={}",
              instanceId,
              node.getNodeCode(),
              e.getMessage(),
              e);
          throw SysException.builder()
              .resultCode(YdszResultCode.INTERNAL_ERROR)
              .key("error.workflow.msg_f2bd498c")
              .params(e.getMessage())
              .build();
        }
        continue;
      }
      taskService.createTask(instanceId, node, variables);
    }
  }

  // ============================== 私有辅助方法 ==============================

  private FlowInstanceVO getByIdOrThrow(String id) {
    FlowInstanceVO instance = instanceRepository.findById(id).orElse(null);
    if (instance == null) {
      throw SysException.builder()
          .resultCode(YdszResultCode.NOT_FOUND)
          .key("error.workflow.msg_67a10717")
          .params(id)
          .build();
    }
    return instance;
  }

  /**
   * 解析 variable JSON 为 Map，空值返回空 Map
   *
   * @param variable variable JSON 字符串
   * @return 解析后的 Map，解析失败返回空 Map
   */
  private Map<String, Object> parseVariables(String variable) {
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

  /**
   * NEW_INSTANCE 模式：创建全新实例，复用原实例的 flowCode / businessType / businessId / initiator，
   * 合并原变量与传入变量。原实例保持不变，仅追加一条 REDO_NEW_INSTANCE 审计日志。
   *
   * @param instanceId 参数说明
   * @param initiatorId 参数说明
   * @param variables 参数说明
   * @param comment 参数说明
   * @return 返回值说明
   */
  private String resubmitAsNewInstance(
      String instanceId, String initiatorId, Map<String, Object> variables, String comment) {
    FlowInstanceVO instance = getByIdOrThrow(instanceId);
    // 1. 状态校验：仅非运行态可重做（RUNNING / SUSPENDED 不可）
    FlowInstanceStatus status = FlowInstanceStatus.valueOf(instance.getFlowStatus());
    if (status == FlowInstanceStatus.RUNNING || status == FlowInstanceStatus.SUSPENDED) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .key("error.workflow.msg_c9d0e1f2")
          .params("运行中/挂起的实例不可重做，当前状态=" + instance.getFlowStatus())
          .build();
    }
    // 2. 发起人校验
    if (instance.getInitiatorId() != null
        && !String.valueOf(instance.getInitiatorId()).equals(initiatorId)) {
      throw SysException.builder()
          .resultCode(YdszResultCode.FORBIDDEN)
          .key("error.workflow.msg_d65b2814")
          .params("仅发起人可重做")
          .build();
    }
    // 3. 合并变量（保留原实例变量，覆盖新增）
    Map<String, Object> merged = getVariables(instanceId);
    if (merged == null) {
      merged = new HashMap<>();
    }
    if (variables != null && !variables.isEmpty()) {
      merged.putAll(variables);
    }
    // 4. 构建新实例启动 DTO
    FlowStartProcessDTO dto = new FlowStartProcessDTO();
    dto.setFlowCode(instance.getFlowCode());
    dto.setVersion(instance.getFlowVersion());
    dto.setBusinessType(instance.getBusinessType());
    dto.setBusinessId(instance.getBusinessId());
    dto.setBusinessNo(instance.getBusinessNo());
    dto.setTitle(instance.getTitle());
    dto.setInitiatorId(initiatorId);
    dto.setInitiatorName(instance.getInitiatorName());
    dto.setVariables(merged.isEmpty() ? null : merged);
    dto.setTenantId(instance.getTenantId());
    dto.setProviderTraceId(instance.getProviderTraceId());
    // 5. 启动新实例
    String newInstanceId = startInstance(dto);
    // 6. 在原实例上追加 REDO 审计日志（保留原轨迹，仅追加）
    FlowAuditLogDO audit = new FlowAuditLogDO();
    audit.setInstanceId(instanceId);
    audit.setFlowCode(instance.getFlowCode());
    audit.setBusinessType(instance.getBusinessType());
    audit.setBusinessId(instance.getBusinessId());
    audit.setAction("REDO_NEW_INSTANCE");
    audit.setOperatorId(initiatorId);
    audit.setOperatorName(instance.getInitiatorName());
    String redoComment =
        comment != null && !comment.isBlank()
            ? comment + " → 新实例[" + newInstanceId + "]"
            : "重做为新实例[" + newInstanceId + "]";
    audit.setComment(redoComment);
    audit.setTenantId(instance.getTenantId());
    audit.setProviderTraceId(instance.getProviderTraceId());
    audit.setOperatedAt(LocalDateTime.now());
    auditLogRepository.save(converter.entityToVO(audit));
    log.info("[Flow] 重做为新实例: 原实例={} 新实例={} initiatorId={}", instanceId, newInstanceId, initiatorId);
    return newInstanceId;
  }

  /**
   * P0-2: 解析 boundaryEvent 的 timer 配置并注册边界定时器
   * 
   * <p>BPMN timer event definition 支持三种形式：
   * 
   * <ul>
   * <li>{@code timeDuration} — ISO 8601 持续时间（如 "PT1H30M"），到点触发一次
   * <li>{@code timeDate} — ISO 8601 绝对时间（如 "2026-07-07T10:00:00"），到点触发一次
   * <li>{@code timeCycle} — ISO 8601 循环（如 "R3/PT10M"），目前仅支持首次触发，循环触发待后续实现
   * </ul>
   * 
   * <p>解析失败时不抛异常，仅记录 warn 日志，避免阻塞流程实例创建。
   *
   * @param node 参数说明
   * @param instanceId 参数说明
   * @param boundaryTaskId 参数说明
   */
  private void scheduleBoundaryTimerIfPresent(
      FlowNodeDO node, String instanceId, String boundaryTaskId) {
    if (timerService == null || boundaryTaskId == null) {
      return;
    }
    Map<String, Object> ext = parseExtMap(node);
    if (ext == null) {
      return;
    }
    Object timerObj = ext.get("timer");
    if (!(timerObj instanceof Map<?, ?> timerRaw)) {
      return;
    }
    Duration delay = parseTimerDelay(timerRaw);
    if (delay == null || delay.isNegative() || delay.isZero()) {
      log.warn("[Flow] 边界定时器配置无法解析或已过期，跳过: node={} timer={}", node.getNodeCode(), timerRaw);
      return;
    }
    try {
      timerService.scheduleBoundary(boundaryTaskId, instanceId, node.getNodeCode(), delay);
      log.info(
          "[Flow] 边界定时器已注册: instanceId={} node={} delay={} taskId={}",
          instanceId,
          node.getNodeCode(),
          delay,
          boundaryTaskId);
    } catch (Exception e) {
      log.warn(
          "[Flow] 边界定时器注册失败: instanceId={} node={} err={}",
          instanceId,
          node.getNodeCode(),
          e.getMessage());
    }
  }

  /**
   * P0-2: 解析 BPMN timer 配置为 Duration
   * 
   * <p>优先级：duration > date > cycle（cycle 仅取首次）
   *
   * @param timer 参数说明
   * @return 返回值说明
   */
  private Duration parseTimerDelay(Map<?, ?> timer) {
    Object duration = timer.get("duration");
    if (duration != null) {
      try {
        return Duration.parse(duration.toString()); // ISO 8601, e.g. "PT1H30M"
      } catch (Exception e) {
        log.warn("[Flow] timer.duration 解析失败: {} err={}", duration, e.getMessage());
      }
    }
    Object date = timer.get("date");
    if (date != null) {
      try {
        LocalDateTime target =
            LocalDateTime.parse(date.toString(), DateTimeFormatter.ISO_DATE_TIME);
        Duration d = Duration.between(LocalDateTime.now(), target);
        return d.isNegative() ? null : d;
      } catch (Exception e) {
        log.warn("[Flow] timer.date 解析失败: {} err={}", date, e.getMessage());
      }
    }
    // cycle（如 "R3/PT10M"）暂仅支持首次触发：提取 PT 部分
    Object cycle = timer.get("cycle");
    if (cycle != null) {
      String cycleStr = cycle.toString();
      // 简单提取 PT 片段（"R3/PT10M" → "PT10M"）
      int ptIdx = cycleStr.indexOf("PT");
      if (ptIdx >= 0) {
        try {
          return Duration.parse(cycleStr.substring(ptIdx));
        } catch (Exception e) {
          log.warn("[Flow] timer.cycle 解析失败: {} err={}", cycle, e.getMessage());
        }
      }
    }
    return null;
  }

  /** P0-2: 解析节点 ext JSON 为 Map（容错） */
  private Map<String, Object> parseExtMap(FlowNodeDO node) {
    if (node == null || !StringUtils.hasText(node.getExt())) {
      return Collections.emptyMap();
    }
    try {
      return FlowNodeExt.parseSafe(node.getExt());
    } catch (Exception e) {
      log.warn("[Flow] 节点 ext 解析失败: nodeCode={} err={}", node.getNodeCode(), e.getMessage());
      return Collections.emptyMap();
    }
  }

  /**
   * P0-1: 解析边界事件关联的 userTask ID
   * 
   * <p>boundaryEvent 节点 ext 中 attachedToRef 指向被附着的节点编码，查找该节点的当前 PENDING 任务作为 boundaryTaskId。
   * intermediateCatchEvent 无 attachedToRef，返回 null。
   *
   * @param node 参数说明
   * @param instanceId 参数说明
   * @return 返回值说明
   */
  private String resolveBoundaryTaskId(FlowNodeDO node, String instanceId) {
    if (node == null || !StringUtils.hasText(node.getExt())) {
      return null;
    }
    try {
      Map<String, Object> ext = FlowNodeExt.parseSafe(node.getExt());
      if (ext == null) {
        return null;
      }
      String attachedToRef = (String) ext.get("attachedToRef");
      if (!StringUtils.hasText(attachedToRef)) {
        return null;
      }
      // 查找被附着节点的当前 PENDING 任务
      List<FlowRunTaskDO> tasks = taskRepository.findPendingByNode(instanceId, attachedToRef).stream()
          .map(converter::entityToDO)
          .collect(Collectors.toList());
      return tasks.isEmpty() ? null : tasks.get(0).getId();
    } catch (Exception e) {
      log.warn(
          "[Flow] 解析 boundaryTaskId 失败: nodeCode={} err={}", node.getNodeCode(), e.getMessage());
      return null;
    }
  }

  /**
   * P1-3: 判断节点是否为 callActivity（子流程）
   * 
   * <p>识别条件：节点 ext JSON 中包含 callActivityFlowCode 字段
   *
   * @param node 参数说明
   * @return 返回值说明
   */
  private boolean isCallActivity(FlowNodeDO node) {
    if (node == null || !StringUtils.hasText(node.getExt())) {
      return false;
    }
    try {
      Map<String, Object> ext = FlowNodeExt.parseSafe(node.getExt());
      if (ext == null) {
        return false;
      }
      return ext.containsKey("callActivityFlowCode") || ext.containsKey("subProcessFlowCode");
    } catch (Exception e) {
      log.warn("[FlowInstanceLifecycleService] 节点 ext 解析失败，视为非子流程调用: {}", e.getMessage());
      return false;
    }
  }

  /**
   * 将 {@code Map<?,?>} 强转为 {@code Map<String, Object>}。
   *
   * <p>ext JSON 由业务方配置（节点扩展字段），运行时信任其结构为 Map&lt;String,Object&gt;，因此这里的强转是安全的。
   * 该方法仅用于抑制 unchecked cast 编译警告。
   *
   * @param m 原始 Map
   * @return 强转后的 Map
   */
  // ============================== 事件触发 ==============================

  /**
   * P2-3: 触发事件监听器（委托给 FlowTaskSupport 统一处理）
   *
   * @param action 参数说明
   */
  private void fireEvent(Consumer<FlowEventListener> action) {
    flowTaskSupport.fireEvent(action, null);
  }

  /**
   * P2-3: 发布 Spring 异步事件（委托给 FlowTaskSupport 统一处理）
   *
   * @param eventType 参数说明
   * @param instanceId 参数说明
   * @param taskId 参数说明
   */
  private void publishWorkflowEvent(String eventType, String instanceId, String taskId) {
    flowTaskSupport.publishWorkflowEvent(eventType, instanceId, taskId);
  }

  /**
   * P2-37: 构建事件上下文元数据
   *
   * @param instanceId 实例 ID
   * @param taskId 任务 ID
   * @param operatorId 操作人 ID
   * @param action 操作动作
   * @param instance 流程实例（用于提取 tenantId/traceId，可空）
   * @return 事件上下文
   */
  private FlowEventContext buildContext(
      String instanceId, String taskId, String operatorId, String action, FlowInstanceVO instance) {
    FlowEventContext ctx = new FlowEventContext();
    ctx.setInstanceId(instanceId);
    ctx.setTaskId(taskId);
    ctx.setOperatorId(operatorId);
    ctx.setAction(action);
    ctx.setOperatedAt(LocalDateTime.now());
    if (instance != null) {
      ctx.setTenantId(
          instance.getTenantId() == null ? null : String.valueOf(instance.getTenantId()));
      // P1-5: 优先使用实例的 providerTraceId，回退 RequestContext / MDC 分布式追踪 ID
      String traceId = instance.getProviderTraceId();
      if (traceId == null || traceId.isBlank()) {
        traceId = RequestContext.getTraceId();
        if (traceId == null || traceId.isBlank()) {
          traceId = MDC.get("traceId");
          if (traceId == null) {
            traceId = MDC.get("tid");
          }
        }
      }
      ctx.setTraceId(traceId);
    }
    return ctx;
  }
}
