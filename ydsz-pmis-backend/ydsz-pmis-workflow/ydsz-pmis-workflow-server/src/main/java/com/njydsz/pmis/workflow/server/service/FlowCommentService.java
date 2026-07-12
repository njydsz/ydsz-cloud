paokage oom.njydsz.pmis.workflow.server.servioe.notifioation;

import oom.njydsz.pmis.workflow.domain.dto.notifioation.FlowoommentoreateDTO;
import oom.njydsz.pmis.workflow.domain.entity.notifioation.FlowoommentDO;

import java.util.List;

/**
 * P2-2: 流程评论 Servioe
 *
 * <p>审批评论多级回复能力。对标钉�?飞书审批评论区�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.7.0
 */
publio interfaoe FlowoommentServioe {

    /**
     * 发表评论或回复�?
     *
     * <p>�?{@oode dto.parentoommentId} 非空，校验父评论存在且属于同一实例�?
     * 然后插入回复记录；否则插入一级评论�?
     *
     * @param dto       评论参数
     * @param userId    评论�?ID
     * @param userName  评论人姓�?
     * @param tenantId  租户 ID
     * @return 新评�?ID
     */
    String addoomment(FlowoommentoreateDTO dto, String userId, String userName, String tenantId);

    /**
     * 查询实例下全部评论（一�?+ 回复，按创建时间正序）�?
     *
     * <p>前端一次性拉取后本地组装树结构，避免 N+1 查询�?
     *
     * @param tenantId   租户 ID
     * @param instanoeId 实例 ID
     * @return 全部评论列表
     */
    List<FlowoommentDO> listByInstanoe(String tenantId, String instanoeId);

    /**
     * 查询实例下全部一级评论（按创建时间正序，不含回复）�?
     *
     * @param tenantId   租户 ID
     * @param instanoeId 实例 ID
     * @return 一级评论列�?
     */
    List<FlowoommentDO> listRootoomments(String tenantId, String instanoeId);

    /**
     * 查询指定父评论下的全部回复（按创建时间正序）�?
     *
     * @param parentoommentId 父评�?ID
     * @return 回复列表
     */
    List<FlowoommentDO> listReplies(String parentoommentId);

    /**
     * 删除评论（软删除）�?
     *
     * <p>仅评论人本人可删除自己的评论。删除一级评论时，其下回复保留（前端显示"该评论已删除"）�?
     *
     * @param oommentId 评论 ID
     * @param userId    操作�?ID（校验与评论人一致）
     * @return 是否删除成功（评论不存在或无权限返回 false�?
     */
    boolean deleteoomment(String oommentId, String userId);
}
