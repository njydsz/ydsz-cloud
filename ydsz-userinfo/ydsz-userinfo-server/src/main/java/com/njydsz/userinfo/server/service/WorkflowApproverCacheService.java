package com.njydsz.userinfo.server.service;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.njydsz.common.json.YdszJson;
import com.njydsz.common.redis.service.ops.RedisStringOps;

/**
 * 工作流审批人缓存服务
 *
 * <p>为工作流引擎高频调用的审批人展开查询接口添加 Redis 缓存，减少数据库压力。
 *
 * <p><b>缓存策略：</b>
 *
 * <ul>
 *   <li>RoleDO:xxx → 用户列表：TTL 5 分钟
 *   <li>position:xxx → 用户列表：TTL 5 分钟
 *   <li>leader:xxx → 上级 ID：TTL 5 分钟
 *   <li>dept:xxx → 部门负责人：TTL 10 分钟（变更频率低）
 * </ul>
 *
 * <p><b>缓存失效触发：</b>
 *
 * <ul>
 *   <li>用户角色分配变更 → 失效 RoleDO 相关缓存
 *   <li>用户岗位/上级变更 → 失效 position/leader 相关缓存
 *   <li>部门负责人变更 → 失效 dept 相关缓存
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowApproverCacheService {

  /** 角色→用户列表缓存 TTL（秒）：5 分钟 */
  private static final long CACHE_TTL_ROLE_USERS = 300;

  /** 岗位→用户列表缓存 TTL（秒）：5 分钟 */
  private static final long CACHE_TTL_POSITION_USERS = 300;

  /** 用户→上级缓存 TTL（秒）：5 分钟 */
  private static final long CACHE_TTL_LEADER = 300;

  /** 部门→负责人缓存 TTL（秒）：10 分钟 */
  private static final long CACHE_TTL_DEPT_LEADER = 600;

  private final RedisStringOps redisStringOps;
  private final UserAccountService userAccountService;
  private final DepartmentService departmentService;

  /**
   * 按角色编码查询用户 ID 列表（带缓存）
   *
   * @param roleCode 角色编码
   * @return 用户 ID 列表
   */
  public List<String> listUserIdsByRoleCode(String roleCode) {
    if (roleCode == null || roleCode.isBlank()) {
      return Collections.emptyList();
    }

    String cacheKey = "userinfo:workflow:RoleDO:" + roleCode;

    // 尝试从缓存获取
    try {
      String cached = redisStringOps.get(cacheKey, String.class);
      if (cached != null) {
        List<String> result = YdszJson.fromJson(cached, List.class, String.class);
        if (result != null) {
          return result;
        }
      }
    } catch (Exception e) {
      log.warn("Workflow cache read failed for RoleDO: {}", roleCode);
    }

    // 查询数据库
    List<String> userIds = userAccountService.listUserIdsByRoleCode(roleCode);

    // 写入缓存
    try {
      redisStringOps.set(
          cacheKey, YdszJson.toJson(userIds), Duration.ofSeconds(CACHE_TTL_ROLE_USERS));
    } catch (Exception e) {
      log.warn("Workflow cache write failed for RoleDO: {}", roleCode);
    }

    return userIds;
  }

  /**
   * 按岗位编码查询用户 ID 列表（带缓存）
   *
   * @param positionCode 岗位编码
   * @return 用户 ID 列表
   */
  public List<String> listUserIdsByPositionCode(String positionCode) {
    if (positionCode == null || positionCode.isBlank()) {
      return Collections.emptyList();
    }

    String cacheKey = "userinfo:workflow:position:" + positionCode;

    try {
      String cached = redisStringOps.get(cacheKey, String.class);
      if (cached != null) {
        List<String> result = YdszJson.fromJson(cached, List.class, String.class);
        if (result != null) {
          return result;
        }
      }
    } catch (Exception e) {
      log.warn("Workflow cache read failed for position: {}", positionCode);
    }

    List<String> userIds = userAccountService.listUserIdsByPositionCode(positionCode);

    try {
      redisStringOps.set(
          cacheKey, YdszJson.toJson(userIds), Duration.ofSeconds(CACHE_TTL_POSITION_USERS));
    } catch (Exception e) {
      log.warn("Workflow cache write failed for position: {}", positionCode);
    }

    return userIds;
  }

  /**
   * 查询用户直属上级 ID（带缓存）
   *
   * @param userId 用户 ID
   * @return 上级用户 ID
   */
  public String getLeaderByUserId(String userId) {
    if (userId == null || userId.isBlank()) {
      return null;
    }

    String cacheKey = "userinfo:workflow:leader:" + userId;

    try {
      String cached = redisStringOps.get(cacheKey, String.class);
      if (cached != null) {
        // 特殊值 "__NULL__" 表示无上级
        if ("__NULL__".equals(cached)) {
          return null;
        }
        return cached;
      }
    } catch (Exception e) {
      log.warn("Workflow cache read failed for leader: {}", userId);
    }

    String leaderId = userAccountService.getLeaderByUserId(userId);

    try {
      // null 值使用占位符缓存，防止缓存穿透
      redisStringOps.set(
          cacheKey, leaderId != null ? leaderId : "__NULL__", Duration.ofSeconds(CACHE_TTL_LEADER));
    } catch (Exception e) {
      log.warn("Workflow cache write failed for leader: {}", userId);
    }

    return leaderId;
  }

  /**
   * 按部门 ID 查询部门负责人（带缓存）
   *
   * @param deptId 部门 ID
   * @return 部门负责人用户 ID
   */
  public String getDeptLeaderByDeptId(String deptId) {
    if (deptId == null || deptId.isBlank()) {
      return null;
    }

    String cacheKey = "userinfo:workflow:deptLeader:id:" + deptId;

    try {
      String cached = redisStringOps.get(cacheKey, String.class);
      if (cached != null) {
        return "__NULL__".equals(cached) ? null : cached;
      }
    } catch (Exception e) {
      log.warn("Workflow cache read failed for deptLeader: {}", deptId);
    }

    String leaderId = departmentService.getDeptLeaderByDeptId(deptId);

    try {
      redisStringOps.set(
          cacheKey,
          leaderId != null ? leaderId : "__NULL__",
          Duration.ofSeconds(CACHE_TTL_DEPT_LEADER));
    } catch (Exception e) {
      log.warn("Workflow cache write failed for deptLeader: {}", deptId);
    }

    return leaderId;
  }

  /**
   * 按部门编码查询部门负责人（带缓存）
   *
   * @param deptCode 部门编码
   * @return 部门负责人用户 ID
   */
  public String getDeptLeaderByDeptCode(String deptCode) {
    if (deptCode == null || deptCode.isBlank()) {
      return null;
    }

    String cacheKey = "userinfo:workflow:deptLeader:code:" + deptCode;

    try {
      String cached = redisStringOps.get(cacheKey, String.class);
      if (cached != null) {
        return "__NULL__".equals(cached) ? null : cached;
      }
    } catch (Exception e) {
      log.warn("Workflow cache read failed for deptLeader code: {}", deptCode);
    }

    String leaderId = departmentService.getDeptLeaderByDeptCode(deptCode);

    try {
      redisStringOps.set(
          cacheKey,
          leaderId != null ? leaderId : "__NULL__",
          Duration.ofSeconds(CACHE_TTL_DEPT_LEADER));
    } catch (Exception e) {
      log.warn("Workflow cache write failed for deptLeader code: {}", deptCode);
    }

    return leaderId;
  }

  /**
   * 失效工作流审批人相关缓存
   *
   * <p>用户角色分配变更时调用。
   *
   * @param roleCode 角色编码（为 null 时失效全部角色相关缓存）
   */
  public void evictRoleCache(String roleCode) {
    try {
      if (roleCode != null) {
        redisStringOps.del("userinfo:workflow:RoleDO:" + roleCode);
      } else {
        redisStringOps.delByPattern("userinfo:workflow:RoleDO:*");
      }
    } catch (Exception e) {
      log.warn("Failed to evict RoleDO cache: {}", e.getMessage());
    }
  }

  /**
   * 失效用户岗位/上级相关缓存
   *
   * @param userId 用户 ID
   */
  public void evictUserCache(String userId) {
    try {
      if (userId != null) {
        redisStringOps.del("userinfo:workflow:leader:" + userId);
      }
      redisStringOps.delByPattern("userinfo:workflow:position:*");
    } catch (Exception e) {
      log.warn("Failed to evict user cache: {}", e.getMessage());
    }
  }

  /**
   * 失效部门负责人相关缓存
   *
   * @param deptId 部门 ID
   */
  public void evictDeptLeaderCache(String deptId) {
    try {
      if (deptId != null) {
        redisStringOps.del("userinfo:workflow:deptLeader:id:" + deptId);
      } else {
        redisStringOps.delByPattern("userinfo:workflow:deptLeader:*");
      }
    } catch (Exception e) {
      log.warn("Failed to evict dept leader cache: {}", e.getMessage());
    }
  }

  /**
   * 清除全部工作流审批人缓存
   *
   * <p>全量刷新或系统维护时调用。
   */
  public void evictAllWorkflowCache() {
    try {
      redisStringOps.delByPattern("userinfo:workflow:*");
      log.info("All workflow approver cache evicted");
    } catch (Exception e) {
      log.warn("Failed to evict all workflow cache: {}", e.getMessage());
    }
  }
}
