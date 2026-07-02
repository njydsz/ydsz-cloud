package com.njydsz.pmis.project.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.project.entity.OpportunityFollowDO;
import com.njydsz.pmis.project.dto.OpportunityFollowDTO;

/**
 * 商机跟进服务
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public interface OpportunityFollowService {

    /**
     * 记录一次商机跟进。
     *
     * @param dto 跟进记录参数
     * @return 跟进记录 ID
     */
    Long record(OpportunityFollowDTO dto);

    /**
     * 分页查询商机跟进记录。
     *
     * @param page          页码（从 1 开始）
     * @param size          每页大小
     * @param opportunityId 商机 ID，可空
     * @return 分页结果
     */
    Page<OpportunityFollowDO> page(int page, int size, Long opportunityId);
}
