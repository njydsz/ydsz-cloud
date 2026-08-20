package com.njydsz.workflow.domain.gateway;

import java.util.List;
import java.util.Map;

/**
 * 名称查询客户端（外部依赖抽象接口）。
 *
 * <p>抽象用户/组织名称查询能力，domain 层通过本接口获取用户名称、组织名称等，
 * infra 层提供适配器实现（Feign 调用用户中心/组织中心服务）。
 *
 * <p><b>架构合规说明（v2.23 DDD 分层规范）：</b>外部依赖抽象接口置于 {@code domain/gateway/} 包下、
 * 以 {@code Client} 结尾（符合 §34.2.1 表格：gateway/ 外部依赖抽象接口）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface NameServiceClient {

  /**
   * 根据用户 ID 查询用户名称。
   *
   * @param userId 用户 ID
   * @return 用户名称，未找到返回 null
   */
  String getUserName(String userId);

  /**
   * 批量查询用户名称。
   *
   * @param userIds 用户 ID 列表
   * @return 用户 ID → 用户名称映射
   */
  Map<String, String> getUserNames(List<String> userIds);

  /**
   * 根据用户 ID + 名称类型获取展示名称。
   *
   * <p>不同场景下可能需要不同格式的名称（如简称、全称、英文名等）。
   *
   * @param userId 用户 ID
   * @param nameType 名称类型（SHORT / FULL / ENGLISH 等）
   * @return 名称，未找到返回 null
   */
  String getUserNameByType(String userId, String nameType);
}
