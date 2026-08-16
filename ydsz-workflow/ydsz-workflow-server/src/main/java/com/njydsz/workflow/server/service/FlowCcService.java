package com.njydsz.workflow.server.service;

import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.workflow.domain.dto.FlowCcQueryDTO;
import com.njydsz.workflow.domain.entity.FlowCc;
import com.njydsz.workflow.domain.entity.FlowNode;
import java.util.List;
import java.util.Map;

/**
 * GAP-P1: 流程抄送服务
 *
 * <p>对标钉钉/飞书的"抄送我的"独立 Tab，提供流程抄送的完整业务能力。
 *
 * <p><b>核心职责：</b>
 *
 * <ul>
 *   <li><b>自动抄送</b>：CC 节点（{@code nodeType=5}）触发时由 {@code DefaultFlowAdvancer} 调用 {@link
 *       #handleCcNode} 写入
 *   <li><b>查询能力</b>：抄送我的分页（{@link #pageMyCc}）/ 未读数（{@code countMyUnread}）/ 实例抄送列表（{@link
 *       #listByInstance}）
 *   <li><b>已读机制</b>：标记已读（{@code markAsRead}）/ 全部标记已读（{@code markAllAsRead}）
 *   <li><b>规则解析</b>：支持 {@code role:xxx} / {@code dept:xxx} / {@code user:xxx} / SpEL 表达式展开
 * </ul>
 *
 * <p><b>GAP-P1 优化点：</b>
 *
 * <ul>
 *   <li>新增 {@link #handleCcNode} — 统一入口，展开 {@code role:/dept:} 权限标识为具体用户列表
 *   <li>新增 {@link #listByInstance} — 查实例维度的抄送记录
 *   <li>分页查询返回 {@code PageResponse}，统一分页响应结构
 * </ul>
 *
 * <p><b>与待办的区别：</b>抄送不阻塞流程推进，仅作通知；本 Service 独立于 {@link FlowTaskService}，由 {@code
 * FlowCcRuleResolver} 解析规则后批量写入。
 *
 * <p><b>性能优化：</b>
 *
 * <ul>
 *   <li>「抄送我的」使用 {@code ydsz_flow_cc} 索引（{@code idx_cc_user}）
 *   <li>未读数查询走 Redis 缓存（{@code ydsz:flow:cc:unread:{userId}}），{@code @CacheEvict} 在 {@code
 *       markAsRead} 时失效
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see com.njydsz.workflow.server.service.impl.FlowCcServiceImpl 实现类
 * @see com.njydsz.workflow.server.resolver.FlowCcRuleResolver 抄送规则解析器
 */
public interface FlowCcService {

  /**
   * 处理抄送节点 — 展开接收人并写入 ydsz_flow_cc
   *
   * <p>解析逻辑：
   *
   * <ol>
   *   <li>从 instanceMapper 获取流程实例（取 flowCode/flowName/businessKey 等冗余字段）
   *   <li>通过 variableStrategy.resolveAssignee() 解析节点的 permissionFlag
   *   <li>按逗号拆分，逐个 token 判断前缀：
   *       <ul>
   *         <li>user: 前缀 → 直接取用户 ID
   *         <li>role:/dept: 前缀 → 通过 assigneeResolver.expandUsers() 展开为用户列表
   *       </ul>
   *   <li>为每个 userId 写入一条 FlowCc（ccType=CC_NODE, readStatus=UNREAD）
   * </ol>
   *
   * @param instanceId 流程实例 ID
   * @param node 抄送节点定义
   * @param variables 流程变量（用于 SpEL 解析）
   */
  void handleCcNode(String instanceId, FlowNode node, Map<String, Object> variables);

  /**
   * 查"抄送我的"分页（便捷方法，使用 DTO 参数）
   *
   * @param tenantId 租户 ID
   * @param userId 接收人 ID
   * @param query 查询条件 DTO
   * @return 抄送记录列表
   */
  List<FlowCc> pageMyCc(String tenantId, String userId, FlowCcQueryDTO query);

  /**
   * 查"抄送我的"总数（便捷方法，使用 DTO 参数）
   *
   * @param tenantId 租户 ID
   * @param userId 接收人 ID
   * @param query 查询条件 DTO
   * @return 总数
   */
  long countMyCc(String tenantId, String userId, FlowCcQueryDTO query);

  /**
   * 查"抄送我的"分页
   *
   * @param userId 接收人 ID
   * @param readStatus 已读状态过滤（UNREAD/READ，可空）
   * @param flowCode 流程编码过滤（可空）
   * @param tenantId 租户 ID
   * @param pageNo 页码（从 1 开始）
   * @param pageSize 每页大小
   * @return 抄送记录分页
   */
  BaseResponse<List<FlowCc>> listCcByUser(
      String userId, String readStatus, String flowCode, String tenantId, int pageNo, int pageSize);

  /**
   * 标记已读
   *
   * @param tenantId 租户 ID（用于权限校验）
   * @param userId 接收人 ID（用于权限校验）
   * @param ccId 抄送记录 ID
   */
  void markRead(String tenantId, String userId, String ccId);

  /**
   * 全部已读
   *
   * @param tenantId 租户 ID
   * @param userId 接收人 ID
   * @return 已标记的记录数
   */
  int markAllRead(String tenantId, String userId);

  /**
   * 未读数
   *
   * @param userId 接收人 ID
   * @param tenantId 租户 ID
   * @return 未读抄送条数
   */
  long countUnread(String userId, String tenantId);

  /**
   * 查实例抄送列表
   *
   * @param instanceId 流程实例 ID
   * @param tenantId 租户 ID
   * @return 抄送记录列表
   */
  List<FlowCc> listByInstance(String instanceId, String tenantId);
}
