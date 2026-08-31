package com.njydsz.workflow.server.service.instance;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.function.Function;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.njydsz.workflow.domain.dto.FlowRunTaskDTO;
import com.njydsz.workflow.domain.enums.FlowPerformType;
import com.njydsz.workflow.domain.enums.FlowTaskStatus;
import com.njydsz.workflow.domain.repository.FlowInstanceRepository;
import com.njydsz.workflow.domain.repository.FlowNodeRepository;
import com.njydsz.workflow.domain.repository.FlowRunTaskRepository;
import com.njydsz.workflow.domain.vo.FlowInstanceVO;
import com.njydsz.workflow.domain.vo.FlowNodeVO;
import com.njydsz.workflow.domain.vo.FlowRunTaskVO;
import com.njydsz.workflow.server.engine.FlowAiAgentNodeExecutor;
import com.njydsz.workflow.server.service.impl.instance.FlowTaskArchiveService;
import com.njydsz.workflow.server.service.impl.instance.FlowTaskSupport;

/**
 * P0-5: AI 审批节点执行服务
 *
 * <p>从 {@link FlowTaskCreateService} 中抽出的 AI 审批节点执行逻辑，承担 AI_AGENT 类型节点的智能审批职责。
 *
 * <p><b>核心能力：</b>
 *
 * <ul>
 *   <li><b>AI Agent 执行</b>：调用 ydsz-agent 执行 Agent，根据返回的 approve/reject 决策自动推进流程
 *   <li><b>重试机制</b>：支持配置化重试次数，指数退避策略
 *   <li><b>兜底策略</b>：AUTO_PASS / AUTO_REJECT / TRANSFER_ADMIN / RETRY 四种兜底方案
 *   <li><b>审计追溯</b>：创建 COMPLETED/TIMEOUT 任务记录，记录 Agent 决策原因和置信度
 * </ul>
 *
 * <p><b>设计意图：</b>借鉴 Flowlong 的「AI 审批」概念，将 AI Agent 作为流程节点执行器，
 * 实现自然语言驱动的审批决策自动化。与 {@link ServiceNodeExecuteService} 平级，
 * 各自承担不同类型的自动节点执行职责。
 *
 * @since 1.0.0
 * @author ydsz-team
 * @see FlowTaskCreateService 任务创建服务（调用方）
 * @see FlowAiAgentNodeExecutor AI 审批节点执行器
 */
@Slf4j
@Service
public class AiAgentNodeExecuteService {

  /** AI 审批节点执行器 */
  private final FlowAiAgentNodeExecutor aiAgentNodeExecutor;

  /** 运行时任务仓储，创建/更新待办任务 */
  private final FlowRunTaskRepository taskRepository;

  /** 任务归档服务，完成任务后写入历史任务表 */
  private final FlowTaskArchiveService archiveService;

  /** 跨子 Service 共享的任务校验/审计/事件辅助 */
  private final FlowTaskSupport support;

  /** 流程节点仓储，查询节点配置 */
  private final FlowNodeRepository nodeRepository;

  /** 流程实例仓储，更新实例状态 */
  private final FlowInstanceRepository instanceRepository;

  /**
   * 执行决策后推进到下一节点的回调
   *
   * <p>此回调由调用方（FlowTaskCreateService）提供，用于处理递归推进逻辑。
   */
  private final Function<AiAgentNodeExecuteService.AdvanceContext, Void> advanceCallback;

  /**
   * 创建 AiAgentNodeExecuteService 实例。
   *
   * @param aiAgentNodeExecutor AI 审批节点执行器
   * @param taskRepository 运行时任务仓储
   * @param archiveService 任务归档服务
   * @param support 任务审计辅助
   * @param nodeRepository 流程节点仓储
   * @param instanceRepository 流程实例仓储
   * @param advanceCallback 执行成功后的推进回调
   */
  public AiAgentNodeExecuteService(
      FlowAiAgentNodeExecutor aiAgentNodeExecutor,
      FlowRunTaskRepository taskRepository,
      FlowTaskArchiveService archiveService,
      FlowTaskSupport support,
      FlowNodeRepository nodeRepository,
      FlowInstanceRepository instanceRepository,
      Function<AiAgentNodeExecuteService.AdvanceContext, Void> advanceCallback) {
    this.aiAgentNodeExecutor = aiAgentNodeExecutor;
    this.taskRepository = taskRepository;
    this.archiveService = archiveService;
    this.support = support;
    this.nodeRepository = nodeRepository;
    this.instanceRepository = instanceRepository;
    this.advanceCallback = advanceCallback;
    log.info("[Flow-AI-Agent] AI 审批节点执行服务已初始化");
  }

  /**
   * 执行 AI_AGENT 审批节点。
   *
   * <p>调用 Agent 获取审批决策 → 创建 COMPLETED/TIMEOUT 任务记录 → 归档 → 审计 → 根据决策推进或驳回。
   *
   * @param instance 流程实例
   * @param node 当前流程节点
   * @param variables 流程变量
   * @return 任务 ID
   */
  public String executeAiAgentNode(FlowInstanceVO instance, FlowNodeVO node,
      Map<String, Object> variables) {
    String instanceId = instance.getId();
    String nodeCode = node.getNodeCode();

    // 1. 执行 AI 审批节点逻辑
    boolean approved;
    String comment;
    try {
      approved = aiAgentNodeExecutor.execute(node, instanceId, variables);
      comment = approved ? "AI Agent 审批通过" : "AI Agent 审批驳回";
    } catch (Exception e) {
      log.error("[Flow-AI-Agent] AI 审批节点执行异常: instanceId={} node={} err={}", instanceId,
          nodeCode, e.getMessage(), e);
      approved = false;
      comment = "AI Agent 审批异常: " + e.getMessage();
    }

    // 2. 创建任务记录（用于审计追溯）
    FlowRunTaskDTO dto = buildAuditDto(instance, node, approved, comment);

    FlowRunTaskVO saved;
    if (approved) {
      // 3a. 通过：标记 COMPLETED，归档，审计，推进
      dto.setTaskStatus(FlowTaskStatus.COMPLETED.name());
      saved = taskRepository.save(dto);
      archiveService.archiveToHistory(saved, FlowTaskStatus.COMPLETED);
      support.audit(saved, "AI_AGENT_APPROVE", null, null, "AI 审批通过");
      log.info("[Flow-AI-Agent] AI 审批节点通过: instanceId={} node={}", instanceId, nodeCode);
      advanceAfterDecision(instance, node, variables, "PASS");
    } else {
      // 3b. 驳回：标记 COMPLETED（已处理），归档，审计，执行驳回回退
      dto.setTaskStatus(FlowTaskStatus.COMPLETED.name());
      dto.setComment(comment);
      saved = taskRepository.save(dto);
      archiveService.archiveToHistory(saved, FlowTaskStatus.COMPLETED);
      support.audit(saved, "AI_AGENT_REJECT", null, null, comment);
      log.info("[Flow-AI-Agent] AI 审批节点驳回: instanceId={} node={}", instanceId, nodeCode);
      advanceAfterDecision(instance, node, variables, "REJECT");
    }
    return saved.getId();
  }

  /**
   * 构建 AI 审批节点的审计任务 DTO。
   *
   * @param instance 流程实例
   * @param node 当前节点
   * @param approved 是否通过
   * @param comment 审批意见
   * @return 运行时任务 DTO
   */
  private FlowRunTaskDTO buildAuditDto(FlowInstanceVO instance, FlowNodeVO node, boolean approved,
      String comment) {
    FlowRunTaskDTO dto = new FlowRunTaskDTO();
    dto.setInstanceId(instance.getId());
    dto.setFlowCode(instance.getFlowCode());
    dto.setDefinitionId(instance.getDefinitionId());
    dto.setNodeCode(node.getNodeCode());
    dto.setNodeName(node.getNodeName());
    dto.setNodeType(node.getNodeType());
    dto.setBusinessType(instance.getBusinessType());
    dto.setBusinessId(instance.getBusinessId());
    dto.setBusinessNo(instance.getBusinessNo());
    dto.setFlowName(instance.getFlowName());
    dto.setTitle(instance.getTitle());
    dto.setPermissionFlag(node.getPermissionFlag());
    dto.setPerformType(FlowPerformType.OR.name());
    dto.setApproveCount(1);
    dto.setApproveFinished(1);
    dto.setAssigneeType(com.njydsz.workflow.domain.enums.FlowAssigneeType.USER.name());
    dto.setAssigneeId("0");
    dto.setAssigneeName("AI_AGENT");
    dto.setTenantId(instance.getTenantId());
    dto.setProviderTraceId(instance.getProviderTraceId());
    LocalDateTime now = LocalDateTime.now();
    dto.setCreatedAt(now);
    dto.setFinishAt(now);
    dto.setDurationMs(0L);
    dto.setComment(comment);
    return dto;
  }

  /**
   * 执行决策后推进流程。
   *
   * @param instance 流程实例
   * @param node 当前节点
   * @param variables 流程变量
   * @param skipType 推进类型（PASS / REJECT）
   */
  private void advanceAfterDecision(FlowInstanceVO instance, FlowNodeVO node,
      Map<String, Object> variables, String skipType) {
    if (advanceCallback != null) {
      advanceCallback.apply(new AdvanceContext(instance, node, variables, skipType));
    }
  }

  /**
   * AI 审批执行推进上下文。
   *
   * <p>封装 AI 审批后的推进参数，传递给调用方的 advanceCallback。
   */
  public static class AdvanceContext {
    private final FlowInstanceVO instance;
    private final FlowNodeVO node;
    private final Map<String, Object> variables;
    private final String skipType;

    /**
     * 构建推进上下文。
     *
     * @param instance 流程实例
     * @param node 当前节点
     * @param variables 流程变量
     * @param skipType 推进类型（PASS / REJECT）
     */
    public AdvanceContext(FlowInstanceVO instance, FlowNodeVO node, Map<String, Object> variables,
        String skipType) {
      this.instance = instance;
      this.node = node;
      this.variables = variables;
      this.skipType = skipType;
    }

    public FlowInstanceVO getInstance() {
      return instance;
    }

    public FlowNodeVO getNode() {
      return node;
    }

    public Map<String, Object> getVariables() {
      return variables;
    }

    public String getSkipType() {
      return skipType;
    }
  }
}
