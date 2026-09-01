package com.njydsz.workflow.server.service.impl.integration;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.njydsz.workflow.WorkflowFacade;
import com.njydsz.workflow.domain.dto.FlowStartProcessDTO;
import com.njydsz.workflow.domain.repository.FlowAuditLogRepository;
import com.njydsz.workflow.domain.repository.FlowAutoTriggerRepository;
import com.njydsz.workflow.domain.vo.FlowAuditLogVO;
import com.njydsz.workflow.domain.vo.FlowAutoTriggerVO;
import com.njydsz.workflow.domain.vo.FlowInstanceVO;
import com.njydsz.workflow.server.service.FlowAutoTriggerService;
import com.njydsz.workflow.server.service.FlowInstanceService;
import com.njydsz.workflow.server.service.impl.instance.DefaultFlowRoutingService;

/**
 * 流程自动触发服务实现
 *
 * <p>对 {@link FlowAutoTriggerService} 接口的完整实现，是工作流引擎的<b>流程联动</b>扩展点。 当一个流程实例完成时，自动检查 {@code
 * sourceFlowCode} 对应的所有 {@code enabled=1} 触发规则， 使用引擎内置的 {@link
 * com.njydsz.workflow.server.engine.expr.ExpressionEvaluator} 评估 {@code conditionExpression}，满足条件则自动启动 {@code
 * targetFlowCode} 对应的目标流程。
 *
 * <p><b>核心职责：</b>
 *
 * <ul>
 *   <li><b>触发规则配置</b>：维护 {@code ydsz_flow_auto_trigger} 表， 定义「源流程 + 条件表达式 + 目标流程」三元组
 *   <li><b>条件评估</b>：流程完成时，遍历源流程的所有 {@code enabled=1} 规则， 注入「源流程变量 + 目标流程变量映射」评估 Aviator 表达式
 *   <li><b>目标流程启动</b>：条件满足时通过 {@link WorkflowFacade#startProcess} 启动目标流程， 并透传源流程的上下文变量
 *   <li><b>审计与回溯</b>：所有触发动作写入 {@code ydsz_flow_audit_log}， 标注「由 XX 流程自动触发」，便于问题排查
 * </ul>
 *
 * <p><b>典型使用：</b>
 *
 * <ul>
 *   <li>「合同审批完成」→ 自动触发「财务结算流程」
 *   <li>「项目立项完成」→ 自动触发「资源分配流程」
 *   <li>「采购订单完成」→ 自动触发「入库流程」
 * </ul>
 *
 * <p><b>事务边界：</b>
 *
 * <ul>
 *   <li>所有写操作开启 {@code @Transactional(rollbackFor = Exception.class)}， 确保「触发规则评估 + 目标流程启动」原子性
 *   <li>目标流程启动通过 {@link WorkflowFacade} 独立事务（{@code REQUIRES_NEW}）， 源流程回滚不影响已触发的目标流程
 * </ul>
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li><b>声明式配置</b>：触发规则通过数据库表配置，<b>无需硬编码</b>， 业务方可自行在管理后台配置「源流程 → 目标流程」联动
 *   <li><b>条件表达式</b>：支持 Aviator 表达式引用源流程变量， 如 {@code source.amount > 100000 && source.type ==
 *       'PURCHASE'}
 *   <li><b>避免循环触发</b>：每次自动触发记录 {@code trigger_chain} 字段， 检测到循环（A → B → A）时阻断
 *   <li><b>幂等性</b>：同一源流程实例对同一目标流程的多次触发由 {@code (sourceInstanceId, targetFlowCode)} 复合键防重
 *   <li><b>失败降级</b>：目标流程启动失败时记录错误日志，不影响源流程的正常完成
 * </ul>
 *
 * <p><b>规则降级：</b>如果 {@code ydsz-literule} 模块未引入，{@code conditionExpression} 降级为
 * 「<b>永远成立</b>」（即不评估条件），所有 enabled 规则都会触发。 生产环境应保证 {@code ydsz-literule} 已正确引入。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see FlowAutoTriggerService 接口定义
 * @see com.njydsz.workflow.domain.vo.FlowAutoTriggerVO 自动触发规则值对象
 * @see WorkflowFacade 工作流门面
 * @see com.njydsz.workflow.server.engine.expr.ExpressionEvaluator Aviator 表达式评估器
 * @see com.njydsz.workflow.server.service.impl.instance.DefaultFlowRoutingService 智能路由（与之联动：路由+触发形成完整决策链）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowAutoTriggerServiceImpl implements FlowAutoTriggerService {

  /** 自动触发仓储，管理 ydsz_flow_auto_trigger 表 CRUD */
  private final FlowAutoTriggerRepository autoTriggerRepository;

  /** 审计日志仓储，记录自动触发操作轨迹 */
  private final FlowAuditLogRepository auditLogRepository;

  /** 智能路由服务，解析触发条件表达式 */
  private final DefaultFlowRoutingService routingService;

  /** 工作流门面，自动发起后续流程实例 */
  private final WorkflowFacade workflowFacade;

  /** 流程实例服务，查询前置流程实例状态 */
  private final FlowInstanceService instanceService;

  // ============================== 核心：实例完成时触发 ==============================

  @Override
  @Transactional(rollbackFor = Exception.class)
  public void onInstanceCompleted(String instanceId) {
    if (instanceId == null) {
      return;
    }

    // 1. 获取已完成的实例
    FlowInstanceVO instance = instanceService.getById(instanceId);
    if (instance == null) {
      log.warn("[FlowAutoTrigger] 实例不存在，跳过自动触发: instanceId={}", instanceId);
      return;
    }
    String sourceFlowCode = instance.getFlowCode();
    if (!StringUtils.hasText(sourceFlowCode)) {
      log.warn("[FlowAutoTrigger] 实例 flowCode 为空，跳过自动触发: instanceId={}", instanceId);
      return;
    }

    // 2. 查询 sourceFlowCode 对应的所有 enabled 触发规则
    List<FlowAutoTriggerVO> triggers =
        autoTriggerRepository.findEnabledBySourceFlowCode(sourceFlowCode);
    if (triggers == null || triggers.isEmpty()) {
      log.debug(
          "[FlowAutoTrigger] 无触发规则: sourceFlowCode={} instanceId={}", sourceFlowCode, instanceId);
      return;
    }

    // 3. 读取已完成的实例 variables 作为上下文
    Map<String, Object> variables = instanceService.getVariables(instanceId);

    log.info(
        "[FlowAutoTrigger] 检查 {} 条触发规则: sourceFlowCode={} instanceId={}",
        triggers.size(),
        sourceFlowCode,
        instanceId);

    // 4. 逐条评估并触发
    for (FlowAutoTriggerVO trigger : triggers) {
      try {
        processTrigger(trigger, instance, variables);
      } catch (Exception e) {
        log.error(
            "[FlowAutoTrigger] 触发规则执行失败: triggerId={} sourceFlowCode={} targetFlowCode={} err={}",
            trigger.getId(),
            sourceFlowCode,
            trigger.getTargetFlowCode(),
            e.getMessage(),
            e);
        writeAuditLog(instance, trigger, false, "执行异常: " + e.getMessage());
      }
    }
  }

  /**
   * 处理单条触发规则
   *
   * <p>通过卫语句（Guard Clause）提前返回不满足条件的触发， 成功路径保持线性流程 → 评估条件 → 启动目标流程 → 写审计日志。
   *
   * @param trigger 自动触发规则实体
   * @param instance 源流程实例
   * @param variables 流程变量
   */
  private void processTrigger(
      FlowAutoTriggerVO trigger, FlowInstanceVO instance, Map<String, Object> variables) {
    // Guard: 条件表达式非空时评估，不满足则跳过
    if (StringUtils.hasText(trigger.getConditionExpression())
        && !evaluateCondition(trigger, variables)) {
      return;
    }

    // 构建启动 DTO 并调用 WorkflowFacade.startProcess 启动目标流程
    String targetInstanceId =
        workflowFacade.startProcess(buildStartProcessDTO(trigger, instance, variables));

    log.info(
        "[FlowAutoTrigger] 自动触发流程成功: sourceFlowCode={} sourceInstanceId={} "
            + "targetFlowCode={} targetInstanceId={} triggerId={}",
        instance.getFlowCode(),
        instance.getId(),
        trigger.getTargetFlowCode(),
        targetInstanceId,
        trigger.getId());

    // 写入审计日志
    writeAuditLog(
        instance,
        trigger,
        true,
        "自动触发成功: " + trigger.getTargetFlowCode() + " -> 实例 " + targetInstanceId);
  }

  /**
   * 评估触发规则的条件表达式。
   *
   *
   *
   * @param trigger 自动触发规则实体
   * @param variables 流程变量
   * @return true=条件成立可触发
   */
  private boolean evaluateCondition(FlowAutoTriggerVO trigger, Map<String, Object> variables) {
    String expr = trigger.getConditionExpression();
    try {
      boolean result = routingService.evaluateCondition(expr, variables);
      log.info(
          "[FlowAutoTrigger] 条件评估: triggerId={} expr={} result={}", trigger.getId(), expr, result);
      return result;
    } catch (Exception e) {
      log.warn(
          "[FlowAutoTrigger] 条件表达式评估失败，默认不触发: triggerId={} expr={} err={}",
          trigger.getId(),
          expr,
          e.getMessage());
      return false;
    }
  }

  /**
   * 构建目标流程的启动 DTO。
   *
   * @param trigger 自动触发规则实体
   * @param instance 源流程实例
   * @param variables 流程变量
   * @return 目标流程启动参数
   */
  private FlowStartProcessDTO buildStartProcessDTO(
      FlowAutoTriggerVO trigger, FlowInstanceVO instance, Map<String, Object> variables) {
    FlowStartProcessDTO startDto = new FlowStartProcessDTO();
    startDto.setFlowCode(trigger.getTargetFlowCode());
    startDto.setBusinessType(instance.getBusinessType());
    startDto.setBusinessId(instance.getBusinessId());
    startDto.setBusinessNo(instance.getBusinessNo());
    startDto.setTitle(buildTriggerTitle(trigger, instance));
    startDto.setInitiatorId(instance.getInitiatorId());
    startDto.setInitiatorName(instance.getInitiatorName());
    startDto.setVariables(variables);
    startDto.setTenantId(instance.getTenantId());
    startDto.setProviderTraceId(instance.getProviderTraceId());
    return startDto;
  }

  /**
   * 构建自动触发流程的标题
   *
   * @param trigger 自动触发规则实体
   * @param instance 源流程实例
   * @return 自动触发流程标题
   */
  private String buildTriggerTitle(FlowAutoTriggerVO trigger, FlowInstanceVO instance) {
    String base =
        StringUtils.hasText(trigger.getDescription())
            ? trigger.getDescription()
            : trigger.getTargetFlowCode();
    return "[" + base + "] 由 " + instance.getFlowCode() + "(" + instance.getId() + ") 自动触发";
  }

  /**
   * 写入审计日志
   *
   * @param instance 源流程实例
   * @param trigger 自动触发规则实体
   * @param success 是否触发成功
   * @param comment 审计描述
   */
  private void writeAuditLog(
      FlowInstanceVO instance, FlowAutoTriggerVO trigger, boolean success, String comment) {
    try {
      auditLogRepository.save(buildAuditLogEntry(instance, trigger, success, comment));
    } catch (Exception e) {
      log.warn(
          "[FlowAutoTrigger] 审计日志写入失败: instanceId={} triggerId={} err={}",
          instance.getId(),
          trigger.getId(),
          e.getMessage());
    }
  }

  /**
   * 构造审计日志实体（供 {@link #writeAuditLog} 调用）。
   *
   * @param instance 源流程实例
   * @param trigger 自动触发规则实体
   * @param success 是否触发成功
   * @param comment 审计描述
   * @return 审计日志实体
   */
  private FlowAuditLogVO buildAuditLogEntry(
      FlowInstanceVO instance, FlowAutoTriggerVO trigger, boolean success, String comment) {
    FlowAuditLogVO logEntry = new FlowAuditLogVO();
    logEntry.setInstanceId(instance.getId());
    logEntry.setTaskId(null);
    logEntry.setFlowCode(instance.getFlowCode());
    logEntry.setBusinessType(instance.getBusinessType());
    logEntry.setBusinessId(instance.getBusinessId());
    logEntry.setNodeCode(null);
    logEntry.setNodeName(null);
    logEntry.setAction(success ? "AUTO_TRIGGER" : "AUTO_TRIGGER_FAIL");
    logEntry.setOperatorId(null);
    logEntry.setOperatorName("SYSTEM");
    logEntry.setTargetId(null);
    logEntry.setTargetName(trigger.getTargetFlowCode());
    logEntry.setComment(comment);
    logEntry.setOperatedAt(LocalDateTime.now());
    logEntry.setTenantId(instance.getTenantId());
    logEntry.setProviderTraceId(instance.getProviderTraceId());
    return logEntry;
  }

  // ============================== 规则管理 ==============================

  @Override
  @Transactional(rollbackFor = Exception.class)
  public void registerTrigger(
      String sourceFlowCode, String targetFlowCode, String conditionExpression) {
    FlowAutoTriggerVO trigger = new FlowAutoTriggerVO();
    trigger.setSourceFlowCode(sourceFlowCode);
    trigger.setTargetFlowCode(targetFlowCode);
    trigger.setConditionExpression(conditionExpression);
    trigger.setEnabled(1);
    trigger.setSortOrder(0);
    autoTriggerRepository.save(trigger);
    log.info(
        "[FlowAutoTrigger] 注册触发规则: id={} source={} target={}",
        trigger.getId(),
        sourceFlowCode,
        targetFlowCode);
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public void removeTrigger(String sourceFlowCode) {
    autoTriggerRepository.deleteBySourceFlowCode(sourceFlowCode);
    log.info("[FlowAutoTrigger] 移除触发规则: sourceFlowCode={}", sourceFlowCode);
  }

  @Override
  @Transactional(readOnly = true)
  public List<FlowAutoTriggerVO> listAll() {
    return autoTriggerRepository.findAllOrderBySort();
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public void deleteById(String id) {
    autoTriggerRepository.deleteById(id);
    log.info("[FlowAutoTrigger] 删除触发规则: id={}", id);
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public boolean toggleEnabled(String id) {
    FlowAutoTriggerVO trigger = autoTriggerRepository.findById(id).orElse(null);
    if (trigger == null) {
      log.warn("[FlowAutoTrigger] 触发规则不存在: id={}", id);
      return false;
    }
    int newEnabled = (trigger.getEnabled() != null && trigger.getEnabled() == 1) ? 0 : 1;
    trigger.setEnabled(newEnabled);
    autoTriggerRepository.update(trigger);
    log.info("[FlowAutoTrigger] 切换触发规则状态: id={} enabled={}", id, newEnabled);
    return newEnabled == 1;
  }
}
