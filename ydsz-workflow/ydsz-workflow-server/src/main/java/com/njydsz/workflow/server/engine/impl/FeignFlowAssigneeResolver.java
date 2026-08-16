package com.njydsz.workflow.server.engine.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.userinfo.api.client.OrgQueryClient;
import com.njydsz.workflow.server.engine.FlowAssigneeResolver;

/**
 * 基于 Feign 的办理人解析器（P1-5 / P2-2）
 *
 * <p>通过 {@link OrgQueryClient} 调用 userinfo 服务，将 BPMN 中的角色/部门审批人标识 展开为具体用户 ID 列表。覆盖 {@link
 * DefaultFlowAssigneeResolver} 的空实现 （DefaultFlowAssigneeResolver 上有
 * {@code @ConditionalOnMissingBean}，本 Bean 注册后自动让位）。
 *
 * <p>支持的展开能力：
 *
 * <ul>
 *   <li>{@code role:HR} → 调用 userinfo 按 roleCode 查询用户 ID 列表
 *   <li>{@code dept:10} → 调用 userinfo 按 deptId 查询部门负责人
 *   <li>{@code dept:SALES} → 调用 userinfo 按 deptCode 查询部门负责人
 *   <li>{@code leader:1001} → 调用 userinfo 查询用户直属上级（P2-2）
 *   <li>{@code leader:initiator} → 从流程变量取发起人 ID 后查询其直属上级（P2-2）
 *   <li>{@code position:PM} → 调用 userinfo 按 positionCode 查询岗位下用户（P2-2）
 *   <li>{@code multi_leader:N} → 多级上级链式查询，最多 15 级防循环引用（P2-2）
 * </ul>
 *
 * <p>容错策略：Feign 调用失败时返回空列表，由 {@code node.ext.emptyStrategy} 兜底。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FeignFlowAssigneeResolver implements FlowAssigneeResolver {

  /** 组织架构查询 Feign 客户端（注入失败时由 fallback 返回空列表） */
  private final OrgQueryClient orgQueryClient;

  /**
   * 将权限标识展开为具体用户 ID 列表
   *
   * <p>按前缀路由：
   *
   * <ul>
   *   <li>{@code role:xxx} → 调用 {@link OrgQueryClient#listUserIdsByRoleCode}
   *   <li>{@code dept:数字} → 调用 {@link OrgQueryClient#getDeptLeaderByDeptId}
   *   <li>{@code dept:非数字} → 调用 {@link OrgQueryClient#getDeptLeaderByDeptCode}
   *   <li>{@code leader:xxx} → 调用 {@link OrgQueryClient#getLeaderByUserId}（P2-2）
   *   <li>{@code position:xxx} → 调用 {@link OrgQueryClient#listUserIdsByPositionCode}（P2-2）
   * </ul>
   *
   * @param permissionFlag 权限标识，如 role:hr / dept:10 / leader:1001
   * @param variables 流程变量（leader:initiator 时用于解析发起人 ID）
   * @return 用户 ID 列表（空列表表示无法展开，引擎将原样保留）
   */
  @Override
  public List<Long> expandUsers(String permissionFlag, Map<String, Object> variables) {
    if (permissionFlag == null || permissionFlag.isBlank()) {
      return Collections.emptyList();
    }
    String token = permissionFlag.trim();
    try {
      if (token.startsWith("role:")) {
        return expandRole(token.substring("role:".length()).trim());
      }
      if (token.startsWith("dept:")) {
        return expandDept(token.substring("dept:".length()).trim());
      }
      if (token.startsWith("leader:")) {
        // P2-2: leader:userId → 直属上级
        return expandLeader(token.substring("leader:".length()).trim(), variables);
      }
      if (token.startsWith("position:")) {
        // P2-2: position:code → 岗位下所有用户
        return expandPosition(token.substring("position:".length()).trim());
      }
      log.debug("[Flow] 未识别的办理人前缀，不展开: {}", token);
      return Collections.emptyList();
    } catch (Exception e) {
      log.warn("[Flow] 办理人展开异常，回退到 emptyStrategy 兜底: token={} err={}", token, e.getMessage());
      return Collections.emptyList();
    }
  }

  /**
   * 查询用户的角色编码列表（用于待办反查）
   *
   * <p>workflow 待办查询时，对 ROLE 类型的任务，需要反查当前用户拥有的角色编码， 与 task.assigneeId 中存储的 roleCode 进行匹配。
   *
   * @param userId 用户 ID
   * @return 角色编码列表
   */
  @Override
  public List<String> getRoleCodes(String userId) {
    if (userId == null) {
      return Collections.emptyList();
    }
    try {
      BaseResponse<List<String>> resp = orgQueryClient.listRoleCodesByUserId(userId);
      if (resp == null || !resp.isSuccess() || resp.getData() == null) {
        return Collections.emptyList();
      }
      return resp.getData().stream()
          .filter(Objects::nonNull)
          .filter(c -> !c.isBlank())
          .distinct()
          .collect(Collectors.toList());
    } catch (Exception e) {
      log.warn("[Flow] 查询用户角色编码失败: userId={} err={}", userId, e.getMessage());
      return Collections.emptyList();
    }
  }

  /**
   * 查询用户的部门 ID 列表（用于待办反查）
   *
   * <p>调用 {@link OrgQueryClient#listDeptIdsByUserId} 查询用户所属部门。 Feign 调用失败时返回空列表，不影响主流程。
   *
   * @param userId 用户 ID
   * @return 部门 ID 列表（字符串形式）
   */
  @Override
  public List<String> getDeptIds(String userId) {
    if (userId == null) {
      return Collections.emptyList();
    }
    try {
      BaseResponse<List<String>> resp = orgQueryClient.listDeptIdsByUserId(userId);
      if (resp == null || !resp.isSuccess() || resp.getData() == null) {
        return Collections.emptyList();
      }
      return resp.getData().stream()
          .filter(Objects::nonNull)
          .filter(c -> !c.isBlank())
          .distinct()
          .collect(Collectors.toList());
    } catch (Exception e) {
      log.warn("[Flow] 查询用户部门 ID 失败: userId={} err={}", userId, e.getMessage());
      return Collections.emptyList();
    }
  }

  /**
   * P2-2: 展开多级上级（连续 N 级主管）
   *
   * <p>循环调用 {@link OrgQueryClient#getLeaderByUserId} 逐级向上查询。 防御性限制：最多 15 级（避免循环引用导致死循环）。
   *
   * @param userId 起始用户 ID（通常为发起人）
   * @param levels 向上级数（≥1）
   * @param variables 流程变量
   * @return 多级上级用户 ID 列表
   */
  @Override
  public List<Long> expandMultiLeader(String userId, int levels, Map<String, Object> variables) {
    if (userId == null || levels <= 0) {
      return Collections.emptyList();
    }
    int maxLevels = Math.min(levels, 15); // 防御性限制
    List<Long> result = new ArrayList<>(maxLevels);
    String currentUserId = userId;
    Set<String> visited = new HashSet<>();
    visited.add(userId); // 防止自环
    for (int i = 0; i < maxLevels; i++) {
      try {
        BaseResponse<String> resp = orgQueryClient.getLeaderByUserId(currentUserId);
        Long leaderId = extractLong(resp);
        if (leaderId == null) {
          log.debug("[Flow] multi_leader 链路中断: userId={} level={}", currentUserId, i + 1);
          break;
        }
        if (!visited.add(String.valueOf(leaderId))) {
          log.warn("[Flow] multi_leader 检测到循环引用: userId={} leaderId={}", currentUserId, leaderId);
          break;
        }
        result.add(leaderId);
        currentUserId = String.valueOf(leaderId);
      } catch (Exception e) {
        log.warn(
            "[Flow] multi_leader 查询异常: userId={} level={} err={}",
            currentUserId,
            i + 1,
            e.getMessage());
        break;
      }
    }
    log.debug("[Flow] multi_leader 展开: startUserId={} levels={} result={}", userId, levels, result);
    return result;
  }

  // ============================== 内部辅助 ==============================

  /**
   * 展开角色审批人为用户 ID 列表
   *
   * @param roleCode 角色编码
   * @return 用户 ID 列表
   */
  private List<Long> expandRole(String roleCode) {
    if (roleCode == null || roleCode.isBlank()) {
      return Collections.emptyList();
    }
    BaseResponse<List<String>> resp = orgQueryClient.listUserIdsByRoleCode(roleCode);
    if (resp == null || !resp.isSuccess() || resp.getData() == null) {
      log.debug(
          "[Flow] 角色展开返回空: roleCode={} resp={}", roleCode, resp == null ? "null" : resp.getCode());
      return Collections.emptyList();
    }
    return resp.getData().stream()
        .filter(Objects::nonNull)
        .map(this::parseLong)
        .filter(Objects::nonNull)
        .distinct()
        .collect(Collectors.toList());
  }

  /**
   * P2-2: 展开直属上级
   *
   * <p>token 可为：
   *
   * <ul>
   *   <li>数字 userId → 直接查该用户的直属上级
   *   <li>"initiator" → 从流程变量取发起人 ID，再查直属上级
   * </ul>
   *
   * @param token 用户 ID 或 "initiator"
   * @param variables 流程变量（仅在 token=initiator 时使用）
   * @return 直属上级用户 ID 列表（0 或 1 个元素）
   */
  private List<Long> expandLeader(String token, Map<String, Object> variables) {
    if (token == null || token.isBlank()) {
      return Collections.emptyList();
    }
    String userId;
    if ("initiator".equalsIgnoreCase(token)) {
      userId = resolveInitiatorId(variables);
    } else {
      userId = token;
    }
    if (userId == null) {
      return Collections.emptyList();
    }
    Long leaderId = extractLong(orgQueryClient.getLeaderByUserId(userId));
    if (leaderId == null) {
      log.debug("[Flow] 直属上级为空: userId={}", userId);
      return Collections.emptyList();
    }
    List<Long> result = new ArrayList<>(1);
    result.add(leaderId);
    return result;
  }

  /**
   * P2-2: 展开岗位审批人为用户 ID 列表
   *
   * @param positionCode 岗位编码
   * @return 用户 ID 列表
   */
  private List<Long> expandPosition(String positionCode) {
    if (positionCode == null || positionCode.isBlank()) {
      return Collections.emptyList();
    }
    BaseResponse<List<String>> resp = orgQueryClient.listUserIdsByPositionCode(positionCode);
    if (resp == null || !resp.isSuccess() || resp.getData() == null) {
      log.debug(
          "[Flow] 岗位展开返回空: positionCode={} resp={}",
          positionCode,
          resp == null ? "null" : resp.getCode());
      return Collections.emptyList();
    }
    return resp.getData().stream()
        .filter(Objects::nonNull)
        .map(this::parseLong)
        .filter(Objects::nonNull)
        .distinct()
        .collect(Collectors.toList());
  }

  /**
   * 展开部门审批人为部门负责人
   *
   * <p>若 token 为纯数字则按 deptId 查询，否则按 deptCode 查询。 返回单元素列表（部门负责人唯一）。
   *
   * @param deptToken 部门 ID（数字）或部门编码
   * @return 部门负责人用户 ID 列表（0 或 1 个元素）
   */
  private List<Long> expandDept(String deptToken) {
    if (deptToken == null || deptToken.isBlank()) {
      return Collections.emptyList();
    }
    Long leaderId;
    if (deptToken.matches("\\d+")) {
      // 纯数字：按 deptId 查
      BaseResponse<String> resp = orgQueryClient.getDeptLeaderByDeptId(deptToken);
      leaderId = extractLong(resp);
    } else {
      // 非数字：按 deptCode 查
      BaseResponse<String> resp = orgQueryClient.getDeptLeaderByDeptCode(deptToken);
      leaderId = extractLong(resp);
    }
    if (leaderId == null) {
      log.debug("[Flow] 部门负责人为空: deptToken={}", deptToken);
      return Collections.emptyList();
    }
    List<Long> result = new ArrayList<>(1);
    result.add(leaderId);
    return result;
  }

  /**
   * 从流程变量解析发起人 ID
   *
   * @param variables 流程变量
   * @return 发起人 ID，未找到返回 null
   */
  private String resolveInitiatorId(Map<String, Object> variables) {
    if (variables == null || variables.isEmpty()) {
      return null;
    }
    Object initiator = variables.get("initiatorId");
    if (initiator == null) {
      initiator = variables.get("startUserId");
    }
    if (initiator == null) {
      initiator = variables.get("initiator");
    }
    if (initiator == null) {
      return null;
    }
    if (initiator instanceof Number n) {
      return String.valueOf(n.longValue());
    }
    return String.valueOf(initiator);
  }

  /**
   * 从 Result 中安全提取 Long 值
   *
   * <p>Feign 返回 {@code BaseResponse<String>}（ID 已迁移为 String），此处解析为 Long 以匹配 {@link
   * FlowAssigneeResolver#expandUsers} / {@link FlowAssigneeResolver#expandMultiLeader} 的 {@code
   * List<Long>} 返回类型。
   *
   * @param resp Feign 响应
   * @return Long 值，失败或为空时返回 null
   */
  private Long extractLong(BaseResponse<String> resp) {
    if (resp == null || !resp.isSuccess()) {
      return null;
    }
    return parseLong(resp.getData());
  }

  /**
   * 将字符串 ID 安全解析为 Long
   *
   * <p>OrgQueryClient 返回的用户/部门 ID 均为 String 形式（雪花算法字符串）， 此方法将其解析为 Long 以匹配 {@link
   * FlowAssigneeResolver} 接口的 {@code List<Long>} 返回类型。 解析失败时返回 null（不抛异常），由调用方过滤。
   *
   * @param data 字符串 ID
   * @return Long 值，入参为空或解析失败时返回 null
   */
  private Long parseLong(String data) {
    if (data == null || data.isBlank()) {
      return null;
    }
    try {
      return Long.parseLong(data);
    } catch (NumberFormatException e) {
      log.warn("[FeignFlowAssigneeResolver] ID 解析失败 data={}: {}", data, e.getMessage());
      return null;
    }
  }
}
