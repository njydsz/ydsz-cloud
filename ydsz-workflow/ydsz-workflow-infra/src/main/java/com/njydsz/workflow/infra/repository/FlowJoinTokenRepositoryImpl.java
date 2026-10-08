package com.njydsz.workflow.infra.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import com.njydsz.workflow.domain.repository.FlowJoinTokenRepository;
import com.njydsz.workflow.infra.entity.FlowJoinTokenEntity;
import com.njydsz.workflow.infra.mapper.FlowJoinTokenMapper;
import com.njydsz.common.auth.util.SecurityUtils;

/**
 * 并行网关 join token 仓储实现（Infra 层）。
 *
 * <p>实现 domain 层 {@link FlowJoinTokenRepository} 接口，封装 {@link FlowJoinTokenMapper} 数据访问细节。
 * DB 作为主存储，确保服务重启后可恢复进行中的 join 状态。
 *
 * <p><b>并发安全：</b>所有计数操作通过 SQL 原子语句（UPDATE SET arrived_count = arrived_count + 1）
 * 保证，不依赖应用层读-改-写。状态流转通过条件 WHERE 实现 CAS 语义。
 *
 * @author ydsz-team
 * @since 26.10.08
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class FlowJoinTokenRepositoryImpl implements FlowJoinTokenRepository {

  private static final String STATUS_PENDING = "PENDING";
  private static final String STATUS_COMPLETED = "COMPLETED";
  private static final String STATUS_CANCELLED = "CANCELLED";

  private final FlowJoinTokenMapper joinTokenMapper;

  /**
   * 初始化 join token：创建记录（分支总数 + 所需到达数）。
   *
   * <p>幂等性：如果已存在有效的 token 记录则跳过初始化，避免重复 fork 场景下覆盖计数。
   *
   * @param instanceId 流程实例 ID
   * @param joinNodeCode join 节点编码
   * @param totalBranches 总分支数
   * @param requiredBranches 所需到达数（0 表示全部分支）
   * @return token ID
   */
  public String initTokens(String instanceId, String joinNodeCode, int totalBranches, int requiredBranches) {
    FlowJoinTokenEntity existing = joinTokenMapper.selectByInstanceAndNode(instanceId, joinNodeCode);
    if (existing != null && STATUS_PENDING.equals(existing.getJoinStatus())) {
      // 已初始化且仍在聚合中：跳过，返回已有 ID（幂等）
      log.debug("[FlowJoinTokenRepo] token 已存在，跳过初始化 instanceId={} nodeId={}",
          instanceId, joinNodeCode);
      return existing.getId();
    }

    FlowJoinTokenEntity entity = new FlowJoinTokenEntity();
    entity.setId(UUID.randomUUID().toString().replace("-", ""));
    entity.setInstanceId(instanceId);
    entity.setJoinNodeCode(joinNodeCode);
    entity.setTotalBranches(totalBranches);
    entity.setRequiredBranches(requiredBranches);
    entity.setArrivedCount(0);
    entity.setJoinStatus(STATUS_PENDING);
    entity.setCreatedBy(resolveUserId());
    entity.setUpdatedBy(resolveUserId());
    entity.setIsDeleted(false);
    entity.setStatus("ENABLED");

    try {
      joinTokenMapper.insert(entity);
      log.debug("[FlowJoinTokenRepo] 初始化 token instanceId={} nodeId={} total={} required={}",
          instanceId, joinNodeCode, totalBranches, requiredBranches);
      return entity.getId();
    } catch (Exception e) {
      log.warn("[FlowJoinTokenRepo] 初始化 token 失败 instanceId={} nodeId={} err={}",
          instanceId, joinNodeCode, e.getMessage());
      return null;
    }
  }

  /**
   * 标记一个分支到达：原子递增到达计数。
   *
   * @param instanceId 流程实例 ID
   * @param joinNodeCode join 节点编码
   * @return 递增后的到达计数；-1 表示 token 不存在或状态异常
   */
  public int arriveToken(String instanceId, String joinNodeCode) {
    try {
      FlowJoinTokenEntity existing = joinTokenMapper.selectByInstanceAndNode(instanceId, joinNodeCode);
      if (existing == null) {
        log.warn("[FlowJoinTokenRepo] arrive 时 token 不存在 instanceId={} nodeId={}",
            instanceId, joinNodeCode);
        return -1;
      }
      int affected = joinTokenMapper.incrementArrivedCountIfPending(existing.getId());
      if (affected == 0) {
        log.warn("[FlowJoinTokenRepo] arrive 失败，token 非 PENDING 状态 instanceId={} nodeId={}",
            instanceId, joinNodeCode);
        return -1;
      }
      return existing.getArrivedCount() + 1;
    } catch (Exception e) {
      log.warn("[FlowJoinTokenRepo] arrive 异常 instanceId={} nodeId={} err={}",
          instanceId, joinNodeCode, e.getMessage());
      return -1;
    }
  }

  /**
   * 查询 join token 是否已满足聚合条件。
   *
   * @param instanceId 流程实例 ID
   * @param joinNodeCode join 节点编码
   * @return true=已满足；false=未满足或记录不存在
   */
  public boolean isComplete(String instanceId, String joinNodeCode) {
    try {
      FlowJoinTokenEntity entity = joinTokenMapper.selectByInstanceAndNode(instanceId, joinNodeCode);
      return entity != null && entity.isComplete();
    } catch (Exception e) {
      log.warn("[FlowJoinTokenRepo] 查询 complete 状态异常 instanceId={} nodeId={} err={}",
          instanceId, joinNodeCode, e.getMessage());
      return false;
    }
  }

  /**
   * 标记 join token 为已完成（CAS）。
   *
   * @param instanceId 流程实例 ID
   * @param joinNodeCode join 节点编码
   * @return true=更新成功；false=状态已变更或记录不存在
   */
  public boolean markCompleted(String instanceId, String joinNodeCode) {
    try {
      FlowJoinTokenEntity entity = joinTokenMapper.selectByInstanceAndNode(instanceId, joinNodeCode);
      if (entity == null) {
        return false;
      }
      int affected = joinTokenMapper.updateStatus(entity.getId(), STATUS_PENDING, STATUS_COMPLETED);
      if (affected > 0) {
        log.debug("[FlowJoinTokenRepo] token 标记为 COMPLETED instanceId={} nodeId={}",
            instanceId, joinNodeCode);
        return true;
      }
      log.debug("[FlowJoinTokenRepo] token 标记 COMPLETED 失败（可能已被更新）instanceId={} nodeId={}",
          instanceId, joinNodeCode);
      return false;
    } catch (Exception e) {
      log.warn("[FlowJoinTokenRepo] 标记 COMPLETED 异常 instanceId={} nodeId={} err={}",
          instanceId, joinNodeCode, e.getMessage());
      return false;
    }
  }

  /**
   * 标记 join token 为已取消（流程终止时）。
   *
   * @param instanceId 流程实例 ID
   * @param joinNodeCode join 节点编码
   * @return 受影响行数
   */
  public int markCancelled(String instanceId, String joinNodeCode) {
    try {
      FlowJoinTokenEntity entity = joinTokenMapper.selectByInstanceAndNode(instanceId, joinNodeCode);
      if (entity == null) {
        return 0;
      }
      return joinTokenMapper.updateStatus(entity.getId(), STATUS_PENDING, STATUS_CANCELLED);
    } catch (Exception e) {
      log.warn("[FlowJoinTokenRepo] 标记 CANCELLED 异常 instanceId={} nodeId={} err={}",
          instanceId, joinNodeCode, e.getMessage());
      return 0;
    }
  }

  /**
   * 取消实例下所有 PENDING 状态的 join token。
   *
   * @param instanceId 流程实例 ID
   * @return 受影响行数
   */
  public int cancelAllPending(String instanceId) {
    try {
      return joinTokenMapper.cancelPendingByInstanceId(instanceId);
    } catch (Exception e) {
      log.warn("[FlowJoinTokenRepo] 批量取消 token 异常 instanceId={} err={}",
          instanceId, e.getMessage());
      return 0;
    }
  }

  /**
   * 检查 join token 是否已初始化（存在有效的 PENDING 记录）。
   *
   * @param instanceId 流程实例 ID
   * @param joinNodeCode join 节点编码
   * @return true=已初始化且为 PENDING 状态
   */
  public boolean isInitialized(String instanceId, String joinNodeCode) {
    try {
      FlowJoinTokenEntity entity = joinTokenMapper.selectByInstanceAndNode(instanceId, joinNodeCode);
      return entity != null && STATUS_PENDING.equals(entity.getJoinStatus());
    } catch (Exception e) {
      log.warn("[FlowJoinTokenRepo] 检查初始化状态异常 instanceId={} nodeId={} err={}",
          instanceId, joinNodeCode, e.getMessage());
      return false;
    }
  }

  /**
   * 查询当前 reached 计数。
   *
   * @param instanceId 流程实例 ID
   * @param joinNodeCode join 节点编码
   * @return 到达计数；-1 表示记录不存在
   */
  public int getArrivedCount(String instanceId, String joinNodeCode) {
    try {
      FlowJoinTokenEntity entity = joinTokenMapper.selectByInstanceAndNode(instanceId, joinNodeCode);
      return entity != null ? entity.getArrivedCount() : -1;
    } catch (Exception e) {
      return -1;
    }
  }

  /**
   * 清理已完成/已取消的历史 token。
   *
   * @param retentionDays 保留天数
   * @param limit 单次清理上限
   * @return 受影响行数
   */
  public int cleanupCompleted(int retentionDays, int limit) {
    try {
      LocalDateTime threshold = LocalDateTime.now().minusDays(retentionDays);
      List<String> terminalStatuses = List.of(STATUS_COMPLETED, STATUS_CANCELLED);
      return joinTokenMapper.deleteCompletedBefore(terminalStatuses, threshold, limit);
    } catch (Exception e) {
      log.warn("[FlowJoinTokenRepo] 清理历史 token 异常 err={}", e.getMessage());
      return 0;
    }
  }

  /**
   * 解析当前操作人 ID。
   *
   * @return 当前操作人 ID 字符串
   */
  private String resolveUserId() {
    String userId = SecurityUtils.getCurrentUserIdOrNull();
    return userId != null ? userId : "0";
  }
}
