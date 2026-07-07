package com.njydsz.pmis.workflow.service;

import com.njydsz.pmis.common.api.PageResult;
import com.njydsz.pmis.workflow.dto.FlowCcQueryDTO;
import com.njydsz.pmis.workflow.entity.FlowCcDO;
import com.njydsz.pmis.workflow.entity.FlowNodeDO;

import java.util.List;
import java.util.Map;

/**
 * GAP-P1: 流程抄送服务
 *
 * <p>对标钉钉/飞书的"抄送我的"独立 Tab。
 * 对外暴露：抄送节点处理 / 分页查询 / 未读数 / 已读标记 / 实例抄送列表；
 * 对内由 {@code DefaultFlowAdvancer} 在 CC 节点触发时调用 {@link #handleCcNode} 写入。
 *
 * <p>GAP-P1 优化点：
 * <ul>
 *   <li>新增 {@link #handleCcNode} — 统一入口，展开 role:/dept: 权限标识为具体用户列表</li>
 *   <li>新增 {@link #listByInstance} — 查实例维度的抄送记录</li>
 *   <li>分页查询返回 {@link PageResult}，统一分页响应结构</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.2.0
 */
public interface FlowCcService {

    /**
     * 处理抄送节点 — 展开接收人并写入 pmis_flow_cc
     *
     * <p>解析逻辑：
     * <ol>
     *   <li>从 instanceMapper 获取流程实例（取 flowCode/flowName/businessKey 等冗余字段）</li>
     *   <li>通过 variableStrategy.resolveAssignee() 解析节点的 permissionFlag</li>
     *   <li>按逗号拆分，逐个 token 判断前缀：
     *     <ul>
     *       <li>user: 前缀 → 直接取用户 ID</li>
     *       <li>role:/dept: 前缀 → 通过 assigneeResolver.expandUsers() 展开为用户列表</li>
     *     </ul>
     *   </li>
     *   <li>为每个 userId 写入一条 FlowCcDO（ccType=CC_NODE, readStatus=UNREAD）</li>
     * </ol>
     *
     * @param instanceId 流程实例 ID
     * @param node       抄送节点定义
     * @param variables  流程变量（用于 SpEL 解析）
     */
    void handleCcNode(String instanceId, FlowNodeDO node, Map<String, Object> variables);

    /**
     * 查"抄送我的"分页（便捷方法，使用 DTO 参数）
     *
     * @param tenantId 租户 ID
     * @param userId   接收人 ID
     * @param query    查询条件 DTO
     * @return 抄送记录列表
     */
    List<FlowCcDO> pageMyCc(String tenantId, String userId, FlowCcQueryDTO query);

    /**
     * 查"抄送我的"总数（便捷方法，使用 DTO 参数）
     *
     * @param tenantId 租户 ID
     * @param userId   接收人 ID
     * @param query    查询条件 DTO
     * @return 总数
     */
    long countMyCc(String tenantId, String userId, FlowCcQueryDTO query);

    /**
     * 查"抄送我的"分页
     *
     * @param userId     接收人 ID
     * @param readStatus 已读状态过滤（UNREAD/READ，可空）
     * @param flowCode   流程编码过滤（可空）
     * @param tenantId   租户 ID
     * @param pageNo     页码（从 1 开始）
     * @param pageSize   每页大小
     * @return 抄送记录分页
     */
    PageResult<FlowCcDO> listCcByUser(String userId, String readStatus, String flowCode,
                                      String tenantId, int pageNo, int pageSize);

    /**
     * 标记已读
     *
     * @param tenantId 租户 ID（用于权限校验）
     * @param userId   接收人 ID（用于权限校验）
     * @param ccId     抄送记录 ID
     */
    void markRead(String tenantId, String userId, String ccId);

    /**
     * 全部已读
     *
     * @param tenantId 租户 ID
     * @param userId   接收人 ID
     * @return 已标记的记录数
     */
    int markAllRead(String tenantId, String userId);

    /**
     * 未读数
     *
     * @param userId   接收人 ID
     * @param tenantId 租户 ID
     * @return 未读抄送条数
     */
    long countUnread(String userId, String tenantId);

    /**
     * 查实例抄送列表
     *
     * @param instanceId 流程实例 ID
     * @param tenantId   租户 ID
     * @return 抄送记录列表
     */
    List<FlowCcDO> listByInstance(String instanceId, String tenantId);
}
