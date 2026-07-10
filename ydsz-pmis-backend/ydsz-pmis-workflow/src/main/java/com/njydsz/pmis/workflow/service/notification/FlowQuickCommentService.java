package com.njydsz.pmis.workflow.service.notification;

import com.njydsz.pmis.workflow.dto.notification.FlowQuickCommentDTO;
import com.njydsz.pmis.workflow.entity.notification.FlowQuickCommentDO;

import java.util.List;

/**
 * 审批常用语服务接口
 *
 * <p>P1-2: 对标钉钉/飞书审批的"常用语"能力。
 *
 * @author ydsz-pmis-team
 * @since 1.8.0
 */
public interface FlowQuickCommentService {

    /**
     * 查询用户的常用语列表（含系统预设 + 用户自定义）
     *
     * @param userId   用户 ID
     * @param tenantId 租户 ID
     * @return 常用语列表（按 sortNum, useCount 排序）
     */
    List<FlowQuickCommentDO> listByUser(String userId, String tenantId);

    /**
     * 新增用户常用语
     *
     * @param dto     常用语 DTO
     * @param userId  用户 ID
     * @param tenantId 租户 ID
     * @return 新建的常用语 ID
     */
    String create(FlowQuickCommentDTO dto, String userId, String tenantId);

    /**
     * 编辑用户常用语
     *
     * @param dto     常用语 DTO（id 必传）
     * @param userId  用户 ID（校验归属）
     */
    void update(FlowQuickCommentDTO dto, String userId);

    /**
     * 删除用户常用语
     *
     * @param id     常用语 ID
     * @param userId 用户 ID（校验归属，系统预设不可删除）
     */
    void delete(String id, String userId);

    /**
     * 增加使用次数（审批时调用）
     *
     * @param id     常用语 ID
     */
    void incrementUseCount(String id);
}
