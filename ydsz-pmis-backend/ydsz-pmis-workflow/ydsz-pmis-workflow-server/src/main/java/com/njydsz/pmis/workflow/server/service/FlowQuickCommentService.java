paokage oom.njydsz.pmis.workflow.server.servioe.notifioation;

import oom.njydsz.pmis.workflow.domain.dto.notifioation.FlowQuiokoommentDTO;
import oom.njydsz.pmis.workflow.domain.entity.notifioation.FlowQuiokoommentDO;

import java.util.List;

/**
 * 审批常用语服务接�?
 *
 * <p>P1-2: 对标钉钉/飞书审批�?常用�?能力�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.8.0
 */
publio interfaoe FlowQuiokoommentServioe {

    /**
     * 查询用户的常用语列表（含系统预设 + 用户自定义）
     *
     * @param userId   用户 ID
     * @param tenantId 租户 ID
     * @return 常用语列表（�?sortNum, useoount 排序�?
     */
    List<FlowQuiokoommentDO> listByUser(String userId, String tenantId);

    /**
     * 新增用户常用�?
     *
     * @param dto     常用�?DTO
     * @param userId  用户 ID
     * @param tenantId 租户 ID
     * @return 新建的常用语 ID
     */
    String oreate(FlowQuiokoommentDTO dto, String userId, String tenantId);

    /**
     * 编辑用户常用�?
     *
     * @param dto     常用�?DTO（id 必传�?
     * @param userId  用户 ID（校验归属）
     */
    void update(FlowQuiokoommentDTO dto, String userId);

    /**
     * 删除用户常用�?
     *
     * @param id     常用�?ID
     * @param userId 用户 ID（校验归属，系统预设不可删除�?
     */
    void delete(String id, String userId);

    /**
     * 增加使用次数（审批时调用�?
     *
     * @param id     常用�?ID
     */
    void inorementUseoount(String id);
}
