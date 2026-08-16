package com.njydsz.agent.server.agent;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.njydsz.agent.domain.entity.AgentApprovalDO;
import com.njydsz.agent.infra.mapper.AgentApprovalMapper;
import com.njydsz.common.event.api.DomainEvent;
import com.njydsz.common.event.api.DomainEventTypes;
import com.njydsz.common.event.publish.DomainEventPublisher;
import com.njydsz.common.json.YdszJson;
import com.njydsz.common.tenant.TenantContextHolder;
import com.njydsz.common.util.id.SnowflakeIdGenerator;

/**
 * Human-in-the-Loop 审批服务
 *
 * <p>管理 Agent 执行过程中需要人工审批的暂停请求。支持：
 *
 * <ul>
 *   <li>创建审批请求（Agent 执行到需要审批的步骤时暂停）
 *   <li>列出待审批请求
 *   <li>审批通过/拒绝
 *   <li>根据审批结果恢复 Agent 执行（通过领域事件通知订阅方）
 * </ul>
 *
 * <p><b>持久化（P1 优化）</b>：审批请求落库到 {@code ydsz_agent_approval} 表，内存 {@link ConcurrentHashMap}
 * 仅作热点缓存，支持多实例共享与重启恢复；审批结果通过 {@link DomainEventPublisher} 发布 {@code AGENT_APPROVAL_REQUESTED} /
 * {@code AGENT_APPROVAL_RESOLVED} 事件，执行器可订阅事件恢复/中止被暂停的步骤。
 *
 * <h3>对标竞品</h3>
 *
 * <ul>
 *   <li>LangChain HumanInTheLoopCallback
 *   <li>Dify 人工审批节点
 *   <li>Coze 卡片交互
 *   <li>AutoGen UserProxyAgent
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Service
@RequiredArgsConstructor
public class HumanApprovalService {

  private static final Logger LOG = LoggerFactory.getLogger(HumanApprovalService.class);

  /** 内存缓存上限，超过先清理过期项 */
  private static final int MAX_PENDING = 500;

  /** 审批结果事件类型（审批通过/拒绝统一发布，metadata.status 区分） */
  private static final String EVENT_APPROVAL_RESOLVED = "AGENT_APPROVAL_RESOLVED";

  /** 审批请求内存缓存（id → 请求，DB 为准，缓存仅加速热查询） */
  private final ConcurrentMap<String, ApprovalRequest> pendingApprovals = new ConcurrentHashMap<>();

  private final SnowflakeIdGenerator snowflakeIdGenerator;
  private final AgentApprovalMapper approvalMapper;
  private final DomainEventPublisher eventPublisher;

  /**
   * 创建审批请求。
   *
   * <p>内存缓存 + 数据库双写，发布 {@code AGENT_APPROVAL_REQUESTED} 事件， 订阅方（执行器/通知中心）据此感知新审批。
   *
   * @param conversationId 对话 ID
   * @param traceId 执行链路 ID
   * @param stepDescription 当前步骤描述
   * @param context 上下文信息（用户输入、已有结果等）
   * @return 审批请求 ID
   */
  public String requestApproval(
      String conversationId, String traceId, String stepDescription, Map<String, Object> context) {
    if (pendingApprovals.size() >= MAX_PENDING) {
      evictExpired();
    }
    String approvalId = String.valueOf(snowflakeIdGenerator.nextId());
    ApprovalRequest request =
        new ApprovalRequest(approvalId, conversationId, traceId, stepDescription, context);
    pendingApprovals.put(approvalId, request);

    // DB 持久化：多实例共享 + 重启恢复
    try {
      approvalMapper.insert(toDO(request));
    } catch (Exception e) {
      LOG.warn("[HITL] 审批请求落库失败: id={}, error={}", approvalId, e.getMessage());
    }

    // 发布审批请求事件，供执行器/通知中心订阅
    try {
      eventPublisher.publish(
          DomainEvent.builder()
              .aggregateType("AgentApproval")
              .aggregateId(approvalId)
              .eventType(DomainEventTypes.AGENT_APPROVAL_REQUESTED)
              .metadata("status", ApprovalStatus.PENDING.name())
              .metadata("conversationId", conversationId)
              .build());
    } catch (Exception e) {
      LOG.warn("[HITL] 审批请求事件发布失败: id={}, error={}", approvalId, e.getMessage());
    }

    LOG.info(
        "[HITL] 创建审批请求: id={}, convId={}, step={}", approvalId, conversationId, stepDescription);
    return approvalId;
  }

  /** 获取待审批请求列表（按创建时间倒序）。 */
  public List<ApprovalRequest> listPending() {
    try {
      List<AgentApprovalDO> dos =
          approvalMapper.selectList(
              new LambdaQueryWrapper<AgentApprovalDO>()
                  .eq(AgentApprovalDO::getStatus, ApprovalStatus.PENDING.name())
                  .orderByDesc(AgentApprovalDO::getCreatedAt));
      List<ApprovalRequest> result = new ArrayList<>(dos.size());
      for (AgentApprovalDO doItem : dos) {
        result.add(toRequest(doItem));
      }
      return result;
    } catch (Exception e) {
      LOG.warn("[HITL] 查询待审批列表失败，回退内存缓存: {}", e.getMessage());
      return pendingApprovals.values().stream()
          .filter(r -> r.getStatus() == ApprovalStatus.PENDING)
          .toList();
    }
  }

  /** 获取审批请求（内存缓存优先，未命中回查数据库）。 */
  public ApprovalRequest getApproval(String approvalId) {
    ApprovalRequest cached = pendingApprovals.get(approvalId);
    if (cached != null) {
      return cached;
    }
    try {
      AgentApprovalDO doItem = approvalMapper.selectById(approvalId);
      if (doItem == null) {
        return null;
      }
      ApprovalRequest request = toRequest(doItem);
      pendingApprovals.put(approvalId, request);
      return request;
    } catch (Exception e) {
      LOG.warn("[HITL] 查询审批请求失败: id={}, error={}", approvalId, e.getMessage());
      return null;
    }
  }

  /**
   * 审批通过。
   *
   * <p>更新内存与数据库状态为 APPROVED，发布 {@code AGENT_APPROVAL_RESOLVED} 事件， 订阅方可据此恢复被暂停的 Agent 步骤。
   */
  public boolean approve(String approvalId, String approver, String comment) {
    return resolve(approvalId, ApprovalStatus.APPROVED, approver, comment);
  }

  /**
   * 审批拒绝。
   *
   * <p>更新内存与数据库状态为 REJECTED，发布 {@code AGENT_APPROVAL_RESOLVED} 事件， 订阅方可据此中止被暂停的 Agent 步骤或走拒绝分支。
   */
  public boolean reject(String approvalId, String approver, String comment) {
    return resolve(approvalId, ApprovalStatus.REJECTED, approver, comment);
  }

  /** 检查审批状态 */
  public ApprovalStatus getStatus(String approvalId) {
    ApprovalRequest request = getApproval(approvalId);
    return request != null ? request.getStatus() : null;
  }

  /** 清理过期的审批请求（超过 1 小时未处理，状态置为 EXPIRED）。 */
  private void evictExpired() {
    LocalDateTime cutoff = LocalDateTime.now().minusHours(1);
    try {
      approvalMapper.update(
          null,
          new LambdaUpdateWrapper<AgentApprovalDO>()
              .eq(AgentApprovalDO::getStatus, ApprovalStatus.PENDING.name())
              .lt(AgentApprovalDO::getCreatedAt, cutoff)
              .set(AgentApprovalDO::getStatus, ApprovalStatus.EXPIRED.name())
              .set(AgentApprovalDO::getResolvedAt, LocalDateTime.now()));
    } catch (Exception e) {
      LOG.warn("[HITL] 过期审批清理失败: {}", e.getMessage());
    }
    pendingApprovals
        .entrySet()
        .removeIf(
            entry ->
                entry.getValue().getStatus() == ApprovalStatus.PENDING
                    && entry.getValue().getCreatedAt().isBefore(cutoff));
  }

  /** 统一审批决策：更新内存 + DB + 发布解析事件。 */
  private boolean resolve(
      String approvalId, ApprovalStatus newStatus, String approver, String comment) {
    ApprovalRequest request = pendingApprovals.get(approvalId);
    if (request == null) {
      AgentApprovalDO doItem = approvalMapper.selectById(approvalId);
      if (doItem == null || !ApprovalStatus.PENDING.name().equals(doItem.getStatus())) {
        return false;
      }
      request = toRequest(doItem);
      pendingApprovals.put(approvalId, request);
    }
    if (request.getStatus() != ApprovalStatus.PENDING) {
      return false;
    }

    request.setStatus(newStatus);
    request.setApprover(approver);
    request.setComment(comment);
    request.setResolvedAt(LocalDateTime.now());

    try {
      approvalMapper.update(
          null,
          new LambdaUpdateWrapper<AgentApprovalDO>()
              .eq(AgentApprovalDO::getId, approvalId)
              .set(AgentApprovalDO::getStatus, newStatus.name())
              .set(AgentApprovalDO::getApprover, approver)
              .set(AgentApprovalDO::getComment, comment)
              .set(AgentApprovalDO::getResolvedAt, request.getResolvedAt()));
    } catch (Exception e) {
      LOG.warn("[HITL] 审批结果落库失败: id={}, error={}", approvalId, e.getMessage());
    }

    try {
      eventPublisher.publish(
          DomainEvent.builder()
              .aggregateType("AgentApproval")
              .aggregateId(approvalId)
              .eventType(EVENT_APPROVAL_RESOLVED)
              .metadata("status", newStatus.name())
              .metadata("approver", approver != null ? approver : "")
              .build());
    } catch (Exception e) {
      LOG.warn("[HITL] 审批结果事件发布失败: id={}, error={}", approvalId, e.getMessage());
    }

    LOG.info("[HITL] 审批决策: id={}, status={}, approver={}", approvalId, newStatus, approver);
    return true;
  }

  /** 将内存请求对象转换为数据库 DO。 */
  private AgentApprovalDO toDO(ApprovalRequest request) {
    return AgentApprovalDO.builder()
        .id(request.getId())
        .conversationId(request.getConversationId())
        .traceId(request.getTraceId())
        .stepDescription(request.getStepDescription())
        .contextJson(request.getContext() == null ? null : YdszJson.toJson(request.getContext()))
        .status(request.getStatus().name())
        .approver(request.getApprover())
        .comment(request.getComment())
        .tenantId(resolveTenantId())
        .createdAt(request.getCreatedAt())
        .resolvedAt(request.getResolvedAt())
        .build();
  }

  /** 将数据库 DO 转换为内存请求对象。 */
  @SuppressWarnings("unchecked")
  private ApprovalRequest toRequest(AgentApprovalDO doItem) {
    Map<String, Object> context = null;
    if (doItem.getContextJson() != null && !doItem.getContextJson().isBlank()) {
      try {
        context = YdszJson.fromJson(doItem.getContextJson(), Map.class);
      } catch (Exception e) {
        LOG.warn("[HITL] 审批上下文反序列化失败: id={}", doItem.getId());
      }
    }
    ApprovalRequest request =
        new ApprovalRequest(
            doItem.getId(),
            doItem.getConversationId(),
            doItem.getTraceId(),
            doItem.getStepDescription(),
            context);
    request.setStatus(ApprovalStatus.valueOf(doItem.getStatus()));
    request.setApprover(doItem.getApprover());
    request.setComment(doItem.getComment());
    request.setResolvedAt(doItem.getResolvedAt());
    return request;
  }

  /**
   * 解析当前租户 ID（用于审批请求落库隔离）。
   *
   * @return 租户 ID；无租户上下文时返回 null
   */
  private String resolveTenantId() {
    if (TenantContextHolder.isPresent()
        && !TenantContextHolder.isSkipIsolation()
        && !TenantContextHolder.isSuperAdmin()) {
      return TenantContextHolder.getTenantId();
    }
    return null;
  }

  /** 审批状态枚举 */
  public enum ApprovalStatus {
    /** 待审批：Agent 已暂停等待人工决策 */
    PENDING,
    /** 已通过：人工批准，Agent 可继续执行 */
    APPROVED,
    /** 已拒绝：人工驳回，Agent 终止当前步骤 */
    REJECTED,
    /** 已过期：超过 1 小时未完成审批，自动失效（见 evictExpired） */
    EXPIRED
  }

  /** 审批请求 */
  public static class ApprovalRequest {
    /** 审批请求唯一 ID（UUID） */
    private final String id;

    /** 所属对话 ID，用于关联原始会话上下文 */
    private final String conversationId;

    /** 执行链路 ID（TraceId），用于串联可观测性轨迹 */
    private final String traceId;

    /** 待审批步骤的业务描述，展示给审批人判断 */
    private final String stepDescription;

    /** 审批上下文（用户输入、已有执行结果等），供审批人参考 */
    private final Map<String, Object> context;

    /** 请求创建时间，用于过期淘汰判断（超过 1 小时未处理即 EXPIRED） */
    private final LocalDateTime createdAt;

    /** 当前审批状态；volatile 保证多线程可见（审批线程与查询线程并发访问） */
    private volatile ApprovalStatus status;

    /** 审批人标识；volatile 保证多线程可见 */
    private volatile String approver;

    /** 审批意见；volatile 保证多线程可见 */
    private volatile String comment;

    /** 审批完成（通过/拒绝）时间 */
    private volatile LocalDateTime resolvedAt;

    public ApprovalRequest(
        String id,
        String conversationId,
        String traceId,
        String stepDescription,
        Map<String, Object> context) {
      this.id = id;
      this.conversationId = conversationId;
      this.traceId = traceId;
      this.stepDescription = stepDescription;
      this.context = context;
      this.createdAt = LocalDateTime.now();
      this.status = ApprovalStatus.PENDING;
    }

    public String getId() {
      return id;
    }

    public String getConversationId() {
      return conversationId;
    }

    public String getTraceId() {
      return traceId;
    }

    public String getStepDescription() {
      return stepDescription;
    }

    public Map<String, Object> getContext() {
      return context;
    }

    public LocalDateTime getCreatedAt() {
      return createdAt;
    }

    public ApprovalStatus getStatus() {
      return status;
    }

    public String getApprover() {
      return approver;
    }

    public String getComment() {
      return comment;
    }

    public LocalDateTime getResolvedAt() {
      return resolvedAt;
    }

    public void setStatus(ApprovalStatus status) {
      this.status = status;
    }

    public void setApprover(String approver) {
      this.approver = approver;
    }

    public void setComment(String comment) {
      this.comment = comment;
    }

    public void setResolvedAt(LocalDateTime resolvedAt) {
      this.resolvedAt = resolvedAt;
    }
  }
}
