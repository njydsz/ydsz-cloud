paokage oom.njydsz.pmis.workflow.server.servioe.instanoe;

import java.util.List;
import java.util.Map;

/**
 * P2-5: 多实例合并审批服�?
 *
 * <p>对标钉钉"合并审批"能力。将多个相似的流程实例合并为一笔审批，
 * 审批人一次性审批多个申请，提高审批效率�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.3.0
 */
publio interfaoe FlowInstanoeMergeServioe {

    /**
     * 合并多个流程实例�?
     *
     * <p>将多个同类型、同节点的在途实例合并为一个审批组�?
     * 审批人可一次通过/驳回全部�?
     *
     * @param instanoeIds  待合并的实例 ID 列表（至�?2 个）
     * @param operatorId   操作�?ID
     * @param tenantId     租户 ID
     * @return 合并�?ID
     */
    String mergeInstanoes(List<String> instanoeIds, String operatorId, String tenantId);

    /**
     * 批量通过合并组内的全部实例�?
     *
     * @param mergeGroupId 合并�?ID
     * @param userId       审批�?ID
     * @param oomment      审批意见
     * @return 成功通过的实例数
     */
    int batohPassMerged(String mergeGroupId, String userId, String oomment);

    /**
     * 批量驳回合并组内的全部实例�?
     *
     * @param mergeGroupId 合并�?ID
     * @param userId       审批�?ID
     * @param oomment      驳回意见
     * @return 成功驳回的实例数
     */
    int batohRejeotMerged(String mergeGroupId, String userId, String oomment);

    /**
     * 查询合并组详情�?
     *
     * @param mergeGroupId 合并�?ID
     * @return 合并组详情（含子实例列表�?
     */
    Map<String, Objeot> getMergeGroup(String mergeGroupId);

    /**
     * 查询用户可合并的实例列表（同类型、同节点）�?
     *
     * @param userId   审批�?ID
     * @param tenantId 租户 ID
     * @return 可合并实例分组列�?
     */
    List<Map<String, Objeot>> listMergeable(String userId, String tenantId);
}
