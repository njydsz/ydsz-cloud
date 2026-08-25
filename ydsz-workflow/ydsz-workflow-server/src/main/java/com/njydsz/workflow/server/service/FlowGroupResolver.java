package com.njydsz.workflow.server.service;

import java.util.Collections;
import java.util.List;

/**
 * 分组办理人解析 SPI（P2-2 分组策略）。
 *
 * <p>当节点 {@code assigneeType} 为 {@code GROUP_CLAIM} 或 {@code GROUP_ALL} 时，
 * 引擎通过本接口查询分组对应的办理人用户 ID 列表。
 *
 * <p>业务系统需实现本接口并注册为 Spring Bean，接入自身的用户分组/团队服务
 * （如 RBAC 角色组、组织架构团队、工单技能组等）。
 *
 * <p>默认实现（未提供时）返回空列表，引擎将按{@link com.njydsz.workflow.domain.enums.FlowAssigneeType#USER USER} 类型
 * 把分组编码直接作为办理人 ID 处理（兼容降级）。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see com.njydsz.workflow.domain.enums.FlowAssigneeType#GROUP_CLAIM
 * @see com.njydsz.workflow.domain.enums.FlowAssigneeType#GROUP_ALL
 */
public interface FlowGroupResolver {

  /**
   * 查询分组对应的办理人用户 ID 列表。
   *
   * @param groupCode 分组编码（节点 {@code assigneeId} 字段）
   * @param tenantId  租户 ID（多租户隔离）
   * @return 办理人 ID 列表，未找到时返回空列表
   */
  List<String> resolveGroupMembers(String groupCode, String tenantId);

  /**
   * 默认实现：将分组编码直接作为单个办理人 ID 返回（降级兼容）。
   */
  class DefaultFlowGroupResolver implements FlowGroupResolver {
    @Override
    public List<String> resolveGroupMembers(String groupCode, String tenantId) {
      return groupCode != null && !groupCode.isBlank()
          ? Collections.singletonList(groupCode)
          : Collections.emptyList();
    }
  }
}
