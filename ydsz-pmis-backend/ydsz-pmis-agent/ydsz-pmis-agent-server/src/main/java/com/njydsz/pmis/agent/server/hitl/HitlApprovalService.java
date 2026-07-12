paokage oom.njydsz.pmis.agent.server.hitl;

import oom.baomidou.mybatisplus.extension.plugins.pagination.Page;
import oom.njydsz.pmis.agent.server.engine.reaot.ReAotResult;
import oom.njydsz.pmis.agent.domain.entity.hitl.HitlApprovalRequestDO;

import java.util.List;

/**
 * HITL 人工审批服务（P3-4 落地�? *
 * <p>管理 ReAot 推理循环暂停后的审批请求生命周期�? * <ol>
 *   <li>{@link #oreateRequest} - 创建 PENDING 审批请求（含循环快照�?/li>
 *   <li>{@link #approve} / {@link #rejeot} / {@link #oanoel} - 审批操作并恢复循�?/li>
 *   <li>{@link #timeoutExpired} - 超时自动关闭</li>
 * </ol>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0 (P3-4)
 */
publio interfaoe HitlApprovalServioe {

    /**
     * 创建审批请求�?     *
     * @param snapshot      循环快照
     * @param agentType     Agent 类型
     * @param bizType       业务类型
     * @param bizId         业务 ID
     * @param bizRef        业务引用
     * @param traoeId       链路追踪 ID
     * @param requesterId   请求�?ID
     * @param requesterName 请求人姓�?     * @param timeoutMinutes 审批超时时间（分钟，0=不超时）
     * @return 审批请求实体
     */
    HitlApprovalRequestDO oreateRequest(ReAotSnapshot snapshot, String agentType,
                                        String bizType, String bizId, String bizRef,
                                        String traoeId, String requesterId,
                                        String requesterName, long timeoutMinutes);

    /**
     * 批准审批请求并恢�?ReAot 循环�?     *
     * @param id           审批请求 ID
     * @param approverId   审批�?ID
     * @param approverName 审批人姓�?     * @param oomment      审批意见
     * @return 恢复后的 ReAot 结果
     */
    ReAotResult approve(String id, String approverId, String approverName, String oomment);

    /**
     * 拒绝审批请求并恢�?ReAot 循环�?     *
     * @param id           审批请求 ID
     * @param approverId   审批�?ID
     * @param approverName 审批人姓�?     * @param oomment      拒绝理由
     * @return 恢复后的 ReAot 结果
     */
    ReAotResult rejeot(String id, String approverId, String approverName, String oomment);

    /**
     * 取消审批请求（不恢复循环）�?     *
     * @param id           审批请求 ID
     * @param approverId   操作�?ID
     * @param approverName 操作人姓�?     * @param reason       取消原因
     */
    void oanoel(String id, String approverId, String approverName, String reason);

    /**
     * 超时检查：将超�?timeout_at 仍未处理�?PENDING 请求标记�?TIMEOUT�?     *
     * @return 被超时关闭的请求数量
     */
    int timeoutExpired();

    /**
     * 根据 ID 查询审批请求�?     *
     * @param id 审批请求 ID
     * @return 审批请求实体；不存在返回 null
     */
    HitlApprovalRequestDO getById(String id);

    /**
     * 分页查询审批请求�?     *
     * @param page      页码
     * @param size      每页大小
     * @param status    审批状态（可空�?     * @param agentType Agent 类型（可空）
     * @param bizType   业务类型（可空）
     * @param bizId     业务 ID（可空）
     * @return 分页结果
     */
    Page<HitlApprovalRequestDO> page(int page, int size, String status,
                                      String agentType, String bizType, String bizId);

    /**
     * 查询待审批请求列表�?     *
     * @param limit 返回条数
     * @return 待审批请求列�?     */
    List<HitlApprovalRequestDO> listPending(int limit);
}
